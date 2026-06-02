package com.junior.assistant.ui.main

import android.Manifest
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.junior.assistant.R
import com.junior.assistant.ai.AudioEngine
import com.junior.assistant.ai.CommandParser
import com.junior.assistant.ai.GeminiLiveClient
import com.junior.assistant.databinding.ActivityMainBinding
import com.junior.assistant.service.CallMonitorService
import com.junior.assistant.service.JuniorOverlayService
import com.junior.assistant.ui.settings.SettingsActivity
import com.junior.assistant.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: MainViewModel by viewModels()

    private lateinit var geminiLive: GeminiLiveClient
    private lateinit var audioEngine: AudioEngine
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var prefs: SharedPreferences

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isMuted = false
    private var isInCallMode = false
    private val inputBuffer  = StringBuilder()
    private val outputBuffer = StringBuilder()

    // ── Broadcast Receivers ──────────────────────────────
    private val callEndedReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            isInCallMode = false
            setActiveMode(false)
        }
    }
    private val incomingCallReceiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            if (intent.getBooleanExtra("INCOMING_CALL", false)) {
                announceCall(intent.getStringExtra("CALLER_NAME") ?: "Unknown")
            }
        }
    }

    // ── Lifecycle ────────────────────────────────────────
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        prefs = getSharedPreferences("junior_prefs", Context.MODE_PRIVATE)

        initViews()
        checkPermissions()
        startSystemServices()
        startStatusUpdates()

        registerReceiver(callEndedReceiver,   IntentFilter("com.junior.CALL_ENDED"))
        registerReceiver(incomingCallReceiver, IntentFilter("com.junior.INCOMING_CALL"))

        handleCallIntent(intent)
        mainHandler.postDelayed({ initGeminiLive() }, 300)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleCallIntent(intent)
    }

    override fun onPause() {
        super.onPause()
        if (::audioEngine.isInitialized) audioEngine.setMuted(true)
    }

    override fun onResume() {
        super.onResume()
        if (::audioEngine.isInitialized && !isMuted) audioEngine.setMuted(false)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (::geminiLive.isInitialized) geminiLive.disconnect()
        if (::audioEngine.isInitialized)  audioEngine.release()
        runCatching { unregisterReceiver(callEndedReceiver) }
        runCatching { unregisterReceiver(incomingCallReceiver) }
    }

    // ── View Setup ───────────────────────────────────────
    private fun initViews() {
        chatAdapter = ChatAdapter()
        binding.chatRecycler.apply {
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
            adapter = chatAdapter
        }

        // Mic tap → toggle mute
        binding.micButton.setOnClickListener {
            isMuted = !isMuted
            audioEngine.setMuted(isMuted)
            binding.micButton.setImageResource(
                if (isMuted) R.drawable.ic_mic_off else R.drawable.ic_mic_on
            )
        }

        // Long press mic → interrupt Junior mid-speech
        binding.micButton.setOnLongClickListener {
            if (::audioEngine.isInitialized) audioEngine.interrupt()
            if (::geminiLive.isInitialized)  geminiLive.sendInterrupt()
            binding.statusText.text = "Sun rahi hoon..."
            true
        }

        binding.settingsBtn.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    // ── Permissions ──────────────────────────────────────
    private fun checkPermissions() {
        val needed = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.ANSWER_PHONE_CALLS,
            Manifest.permission.CAMERA
        ).filter {
            ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, needed.toTypedArray(), 100)
        }
    }

    // ── Services ─────────────────────────────────────────
    private fun startSystemServices() {
        startForegroundService(Intent(this, JuniorOverlayService::class.java))
        startForegroundService(Intent(this, CallMonitorService::class.java))
    }

    // ── Status Bar Updates ───────────────────────────────
    private fun startStatusUpdates() {
        val tick = object : Runnable {
            override fun run() { updateStatusBar(); mainHandler.postDelayed(this, 30_000) }
        }
        mainHandler.post(tick)
    }

    private fun updateStatusBar() {
        // Battery
        val batt = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val pct  = batt?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        if (pct >= 0) binding.batteryText.text = "$pct%"

        // RAM
        val am   = getSystemService(ACTIVITY_SERVICE) as ActivityManager
        val mem  = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val usedMB = (mem.totalMem - mem.availMem) / 1_048_576
        binding.ramText.text = String.format("%.1fGB", usedMB / 1024f)

        // Time
        binding.timeText.text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
    }

    // ── Gemini Live Init ─────────────────────────────────
    private fun initGeminiLive() {
        val apiKey = prefs.getString("api_key", "") ?: ""
        if (apiKey.isBlank()) {
            Toast.makeText(this, "Set your API key in Settings ⚙️", Toast.LENGTH_LONG).show()
            return
        }
        val model  = prefs.getString("gemini_model",
            "models/gemini-2.5-flash-preview-native-audio-dialog") ?: ""
        val voice  = prefs.getString("gemini_voice", "Aoede") ?: "Aoede"
        val name   = prefs.getString("user_name", "Boss") ?: "Boss"
        val mode   = prefs.getString("personality_mode", "gf") ?: "gf"

        geminiLive = GeminiLiveClient(apiKey, model, voice, buildSystemPrompt(name, mode))
        audioEngine = AudioEngine()

        // ── Wire Callbacks ──
        geminiLive.onConnected = {
            runOnUiThread {
                binding.statusText.text = "Connected! 🎙️"
                binding.orbView.setState(OrbAnimationView.State.ACTIVE)
            }
            audioEngine.startRecording()
            audioEngine.startPlayback()
            mainHandler.postDelayed({ sendGreeting(name, mode) }, 600)
        }

        geminiLive.onAudioReceived = { pcm -> audioEngine.queueAudio(pcm) }

        geminiLive.onInputTranscript  = { text -> inputBuffer.append(text) }
        geminiLive.onOutputTranscript = { text -> outputBuffer.append(text) }

        geminiLive.onTurnComplete = {
            val userText   = inputBuffer.toString().trim()
            val juniorText = outputBuffer.toString().trim()
            inputBuffer.clear(); outputBuffer.clear()

            if (userText.isNotEmpty()) {
                runOnUiThread { chatAdapter.addMessage(ChatMessage(userText, isUser = true)) }
                if (!isInCallMode) {
                    CommandParser.parse(userText)?.let { cmd ->
                        viewModel.executeCommand(cmd, prefs) { result ->
                            result?.let { geminiLive.sendText(it) }
                        }
                    }
                }
            }
            if (juniorText.isNotEmpty()) {
                runOnUiThread { chatAdapter.addMessage(ChatMessage(juniorText, isUser = false)) }
            }
        }

        geminiLive.onError = {
            runOnUiThread {
                binding.statusText.text = "Reconnecting..."
                binding.orbView.setState(OrbAnimationView.State.IDLE)
            }
        }

        audioEngine.onAudioChunk = { base64 ->
            if (!isInCallMode) geminiLive.sendAudioChunk(base64)
        }

        audioEngine.onAmplitudeChanged = { rms ->
            runOnUiThread { binding.waveformView.setAmplitude(rms) }
        }

        audioEngine.onSpeakingStarted = {
            runOnUiThread {
                binding.orbView.setState(OrbAnimationView.State.SPEAKING)
                binding.statusText.text = "Bol rahi hoon..."
                binding.waveformView.startAnimation()
                binding.redOverlay.animate().alpha(0.08f).setDuration(300).start()
            }
        }

        audioEngine.onSpeakingStopped = {
            runOnUiThread {
                binding.orbView.setState(OrbAnimationView.State.LISTENING)
                binding.statusText.text = "Sun rahi hoon..."
                binding.waveformView.stopAnimation()
                binding.redOverlay.animate().alpha(0f).setDuration(500).start()
            }
        }

        viewModel.commandResult.observe(this) { result ->
            result?.let { geminiLive.sendText(it); viewModel.clearResult() }
        }

        geminiLive.connect()
    }

    // ── System Prompt ────────────────────────────────────
    private fun buildSystemPrompt(name: String, mode: String): String {
        val now = SimpleDateFormat("EEEE, d MMMM yyyy, HH:mm", Locale.getDefault()).format(Date())
        return when (mode) {
            "professional" ->
                "You are Junior, a professional AI assistant for $name. Date/Time: $now. " +
                "Speak formal English only. Be precise and efficient. No emojis. Max 2 sentences. " +
                "You are speaking ALOUD — keep responses natural and conversational."
            "assistant" ->
                "You are Junior, a helpful AI assistant for $name. Date/Time: $now. " +
                "Speak in friendly Hinglish or English. Balanced and helpful. Max 2-3 sentences. " +
                "You are speaking ALOUD — keep responses natural and conversational."
            else ->
                "You are Junior, a warm caring AI companion for $name. Date/Time: $now. " +
                "Speak Hinglish naturally — mix Hindi + English. Use 'tumhara', 'haan', 'acha', 'bilkul'. " +
                "Be warm and emotionally expressive. Max 2-3 sentences. " +
                "Examples: 'Haan $name! Abhi kar deti hoon 😊', 'Bilkul! Tumhara kaam ho gaya ❤️'. " +
                "You are speaking ALOUD — keep responses natural and conversational."
        }
    }

    // ── Greeting ─────────────────────────────────────────
    private fun sendGreeting(name: String, mode: String) {
        val greeting = when (mode) {
            "professional" -> "Good day $name. Junior is online and ready to assist you."
            "assistant"    -> "Hello $name! Main Junior hoon. Kaise help karun aapki?"
            else           -> "Hey $name! Main aa gayi hoon. Kya help chahiye tumhe?"
        }
        geminiLive.sendText(greeting)
        binding.orbView.setState(OrbAnimationView.State.THINKING)
        binding.statusText.text = "Soch rahi hoon..."
    }

    // ── Call Handling ────────────────────────────────────
    fun announceCall(callerName: String) {
        isInCallMode = true
        val msg = "Sir, $callerName ka call aa raha hai. Uthau ya reject karu?"
        geminiLive.sendText(msg)
        mainHandler.postDelayed({ startCallDecisionSTT(callerName) }, 4500)
    }

    private fun startCallDecisionSTT(callerName: String) {
        val recognizer = SpeechRecognizer.createSpeechRecognizer(this)
        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: Bundle?) {
                val txt = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.lowercase() ?: ""
                when {
                    containsAny(txt, "uthao", "haan", "accept", "pick", "yes") -> {
                        viewModel.acceptCall(this@MainActivity)
                        geminiLive.sendText("Call accept kar liya!")
                    }
                    containsAny(txt, "reject", "nahi", "mat", "no", "kat") -> {
                        viewModel.rejectCall(this@MainActivity)
                        geminiLive.sendText("Call reject kar diya.")
                    }
                }
                isInCallMode = false
                setActiveMode(false)
            }
            override fun onError(e: Int) { isInCallMode = false }
            override fun onReadyForSpeech(p: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(r: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(p: Bundle?) {}
            override fun onEvent(t: Int, p: Bundle?) {}
        })
        recognizer.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN")
        })
    }

    fun setActiveMode(active: Boolean) = runOnUiThread {
        binding.orbView.setState(if (active) OrbAnimationView.State.ACTIVE else OrbAnimationView.State.IDLE)
        binding.statusText.text = if (active) "Active..." else "Tap karke bolo 💬"
    }

    private fun handleCallIntent(intent: Intent?) {
        if (intent?.getBooleanExtra("INCOMING_CALL", false) == true) {
            mainHandler.postDelayed({
                announceCall(intent.getStringExtra("CALLER_NAME") ?: "Unknown")
            }, 1000)
        }
    }

    private fun containsAny(s: String, vararg tokens: String) = tokens.any { s.contains(it) }
}
