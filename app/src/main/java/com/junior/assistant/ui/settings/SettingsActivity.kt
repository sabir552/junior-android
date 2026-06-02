package com.junior.assistant.ui.settings

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.junior.assistant.R
import com.junior.assistant.databinding.ActivitySettingsBinding
import com.junior.assistant.service.AccessibilityHelperService
import org.json.JSONArray
import org.json.JSONObject

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences
    private val primeContacts = mutableListOf<PrimeContact>()
    private lateinit var primeAdapter: PrimeContactAdapter

    private val models = listOf(
        "Native Audio (Warm Voice)"   to "models/gemini-2.5-flash-native-audio-preview-12-2025",
        "Flash Live (Recommended ✅)" to "models/gemini-3.1-flash-live-preview",
        "Pro Audio Dialog"            to "models/gemini-2.5-flash-preview-native-audio-dialog"
    )
    private val voices = listOf("Aoede","Charon","Kore","Fenrir","Puck","Leda","Orus","Zephyr")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        prefs = getSharedPreferences("junior_prefs", Context.MODE_PRIVATE)
        loadCurrentSettings(); setupModelSpinner(); setupVoiceSpinner()
        setupPersonalityRadio(); setupPrimeContacts(); setupAccessibilityStatus()
        binding.saveButton.setOnClickListener { saveSettings() }
    }

    private fun loadCurrentSettings() {
        binding.apiKeyInput.setText(prefs.getString("api_key", ""))
        binding.userNameInput.setText(prefs.getString("user_name", ""))
    }

    private fun setupModelSpinner() {
        binding.modelSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, models.map { it.first })
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        val saved = prefs.getString("gemini_model", models[1].second)
        binding.modelSpinner.setSelection(models.indexOfFirst { it.second == saved }.coerceAtLeast(0))
    }

    private fun setupVoiceSpinner() {
        binding.voiceSpinner.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_item, voices)
            .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
        binding.voiceSpinner.setSelection(
            voices.indexOf(prefs.getString("gemini_voice","Aoede")).coerceAtLeast(0))
    }

    private fun setupPersonalityRadio() {
        when (prefs.getString("personality_mode","gf")) {
            "professional" -> binding.radioPersonality.check(R.id.radioProfessional)
            "assistant"    -> binding.radioPersonality.check(R.id.radioAssistant)
            else           -> binding.radioPersonality.check(R.id.radioGf)
        }
    }

    private fun setupPrimeContacts() {
        prefs.getString("prime_contacts_json", null)?.let { json ->
            runCatching {
                val arr = JSONArray(json)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    primeContacts.add(PrimeContact(o.getString("name"), o.getString("number")))
                }
            }
        }
        primeAdapter = PrimeContactAdapter(primeContacts) { idx ->
            primeContacts.removeAt(idx); primeAdapter.notifyItemRemoved(idx); savePrimeContacts()
        }
        binding.primeContactsRecycler.apply {
            layoutManager = LinearLayoutManager(this@SettingsActivity); adapter = primeAdapter
        }
        binding.addPrimeContactBtn.setOnClickListener { showAddContactDialog() }
    }

    private fun showAddContactDialog() {
        val view = layoutInflater.inflate(R.layout.dialog_add_prime_contact, null)
        AlertDialog.Builder(this).setTitle("Add Prime Contact").setView(view)
            .setPositiveButton("Add") { _, _ ->
                val name   = view.findViewById<EditText>(R.id.dialogNameInput).text.toString().trim()
                val number = view.findViewById<EditText>(R.id.dialogNumberInput).text.toString().trim()
                if (name.isNotEmpty() && number.isNotEmpty()) {
                    primeContacts.add(PrimeContact(name, number))
                    primeAdapter.notifyItemInserted(primeContacts.size - 1); savePrimeContacts()
                }
            }.setNegativeButton("Cancel", null).show()
    }

    private fun savePrimeContacts() {
        val arr = JSONArray()
        primeContacts.forEach { arr.put(JSONObject().put("name",it.name).put("number",it.number)) }
        prefs.edit().putString("prime_contacts_json", arr.toString()).apply()
    }

    private fun setupAccessibilityStatus() {
        val enabled = AccessibilityHelperService.isEnabled(this)
        binding.accessibilityStatus.apply {
            text = if (enabled) "✅ Accessibility Enabled" else "❌ Accessibility Disabled — tap to enable"
            setTextColor(if (enabled) 0xFF00E676.toInt() else 0xFFFF1744.toInt())
            setOnClickListener {
                if (!enabled) startActivity(android.content.Intent(
                    android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        }
    }

    private fun saveSettings() {
        val personality = when (binding.radioPersonality.checkedRadioButtonId) {
            R.id.radioProfessional -> "professional"; R.id.radioAssistant -> "assistant"; else -> "gf"
        }
        prefs.edit()
            .putString("api_key",          binding.apiKeyInput.text.toString().trim())
            .putString("user_name",        binding.userNameInput.text.toString().trim())
            .putString("gemini_model",     models[binding.modelSpinner.selectedItemPosition].second)
            .putString("gemini_voice",     voices[binding.voiceSpinner.selectedItemPosition])
            .putString("personality_mode", personality)
            .apply()
        savePrimeContacts()
        Toast.makeText(this, "✅ Saved! Restart app to apply.", Toast.LENGTH_SHORT).show()
        finish()
    }
}
