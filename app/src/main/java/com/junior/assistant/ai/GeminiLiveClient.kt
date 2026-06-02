package com.junior.assistant.ai

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import okhttp3.*
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class GeminiLiveClient(
    private val apiKey: String,
    private val model: String,
    private val voice: String,
    private val systemPrompt: String
) {
    companion object {
        private const val TAG = "GeminiLiveClient"
        private const val SESSION_RENEW_MS = 540_000L  // 9 minutes
        private const val KEEPALIVE_MS    = 8_000L     // 8 seconds
        // FIX: was v1alpha — corrected to v1beta (current endpoint)
        private const val WS_BASE = "wss://generativelanguage.googleapis.com/ws/" +
            "google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent"
    }

    private var webSocket: WebSocket? = null
    @Volatile private var isConnected = false
    private var isUserDisconnecting = false

    private val mainHandler = Handler(Looper.getMainLooper())
    private val keepaliveRunner = object : Runnable {
        override fun run() {
            if (isConnected) {
                // Send 1024 bytes of silence as keepalive every 8 seconds
                val silence = Base64.encodeToString(ByteArray(1024), Base64.NO_WRAP)
                sendAudioChunk(silence)
                mainHandler.postDelayed(this, KEEPALIVE_MS)
            }
        }
    }

    var onConnected: (() -> Unit)? = null
    var onAudioReceived: ((ByteArray) -> Unit)? = null
    var onInputTranscript: ((String) -> Unit)? = null
    var onOutputTranscript: ((String) -> Unit)? = null
    var onTurnComplete: (() -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)  // Infinite — required for WebSocket streams
        .build()

    fun connect() {
        isUserDisconnecting = false
        val request = Request.Builder().url("$WS_BASE?key=$apiKey").build()
        webSocket = httpClient.newWebSocket(request, object : WebSocketListener() {

            override fun onOpen(webSocket: WebSocket, response: Response) {
                isConnected = true
                sendSetupMessage()
                mainHandler.post(keepaliveRunner)
                // Session renewal: close and reconnect after 9 minutes
                mainHandler.postDelayed({
                    if (isConnected && !isUserDisconnecting) {
                        Log.d(TAG, "Session renewal: reconnecting")
                        webSocket.close(1000, "Session renewal")
                    }
                }, SESSION_RENEW_MS)
                onConnected?.invoke()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleServerMessage(text)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                isConnected = false
                mainHandler.removeCallbacks(keepaliveRunner)
                onError?.invoke(t.message ?: "Connection failed")
                if (!isUserDisconnecting) reconnectAfterDelay()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                isConnected = false
                mainHandler.removeCallbacks(keepaliveRunner)
                // FIX: also auto-reconnect on clean close (not just onFailure)
                if (!isUserDisconnecting) reconnectAfterDelay()
            }
        })
    }

    private fun reconnectAfterDelay() {
        thread {
            Thread.sleep(3000)
            if (!isUserDisconnecting) connect()
        }
    }

    private fun sendSetupMessage() {
        val msg = JSONObject().put("setup", JSONObject()
            .put("model", model)
            .put("system_instruction", JSONObject()
                .put("parts", JSONArray().put(JSONObject().put("text", systemPrompt))))
            .put("generation_config", JSONObject()
                .put("response_modalities", JSONArray().put("AUDIO"))
                .put("speech_config", JSONObject()
                    .put("voice_config", JSONObject()
                        .put("prebuilt_voice_config", JSONObject()
                            .put("voice_name", voice))))
                .put("temperature", 0.9))
            .put("output_audio_transcription", JSONObject())
            .put("input_audio_transcription", JSONObject()))
        webSocket?.send(msg.toString())
    }

    fun sendAudioChunk(pcmBase64: String) {
        if (!isConnected) return
        val msg = JSONObject().put("realtime_input", JSONObject()
            .put("media_chunks", JSONArray().put(JSONObject()
                .put("mime_type", "audio/pcm;rate=16000")
                .put("data", pcmBase64))))
        webSocket?.send(msg.toString())
    }

    fun sendText(text: String) {
        if (!isConnected) return
        val msg = JSONObject().put("client_content", JSONObject()
            .put("turns", JSONArray().put(JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", text)))))
            .put("turn_complete", true))
        webSocket?.send(msg.toString())
    }

    fun sendInterrupt() {
        if (!isConnected) return
        val msg = JSONObject().put("client_content", JSONObject()
            .put("turns", JSONArray())
            .put("turn_complete", true))
        webSocket?.send(msg.toString())
    }

    private fun handleServerMessage(jsonStr: String) {
        try {
            val json = JSONObject(jsonStr)
            val sc = json.optJSONObject("serverContent") ?: return

            // Decode and deliver audio chunks
            sc.optJSONObject("modelTurn")?.optJSONArray("parts")?.let { parts ->
                for (i in 0 until parts.length()) {
                    val data = parts.getJSONObject(i)
                        .optJSONObject("inlineData")?.optString("data")
                    if (!data.isNullOrEmpty()) {
                        onAudioReceived?.invoke(Base64.decode(data, Base64.DEFAULT))
                    }
                }
            }

            // FIX: transcripts are nested {text:"..."} objects — NOT direct strings
            sc.optJSONObject("inputTranscription")?.optString("text")
                ?.takeIf { it.isNotEmpty() }?.let { onInputTranscript?.invoke(it) }

            sc.optJSONObject("outputTranscription")?.optString("text")
                ?.takeIf { it.isNotEmpty() }?.let { onOutputTranscript?.invoke(it) }

            if (sc.optBoolean("turnComplete", false)) onTurnComplete?.invoke()

        } catch (e: Exception) {
            Log.e(TAG, "Server message parse error: ${e.message}")
        }
    }

    fun disconnect() {
        isUserDisconnecting = true
        mainHandler.removeCallbacks(keepaliveRunner)
        webSocket?.close(1000, "User disconnect")
        isConnected = false
    }
}
