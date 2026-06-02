package com.junior.assistant.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.telecom.TelecomManager
import androidx.lifecycle.*
import com.junior.assistant.model.AppCommand
import com.junior.assistant.service.AccessibilityHelperService
import kotlinx.coroutines.*
import org.json.JSONArray

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val _commandResult = MutableLiveData<String?>()
    val commandResult: LiveData<String?> = _commandResult
    fun clearResult() { _commandResult.value = null }

    private val appPackages = mapOf(
        "youtube"    to "com.google.android.youtube",
        "whatsapp"   to "com.whatsapp",
        "instagram"  to "com.instagram.android",
        "spotify"    to "com.spotify.music",
        "netflix"    to "com.netflix.mediaclient",
        "chrome"     to "com.android.chrome",
        "gmail"      to "com.google.android.gm",
        "maps"       to "com.google.android.apps.maps",
        "telegram"   to "org.telegram.messenger",
        "snapchat"   to "com.snapchat.android",
        "twitter"    to "com.twitter.android",
        "facebook"   to "com.facebook.katana",
        "discord"    to "com.discord",
        "linkedin"   to "com.linkedin.android",
        "zoom"       to "us.zoom.videomeetings",
        "meet"       to "com.google.android.apps.tachyon",
        "paytm"      to "net.one97.paytm",
        "phonepe"    to "com.phonepe.app",
        "gpay"       to "com.google.android.apps.nbu.paisa.user",
        "settings"   to "com.android.settings",
        "calculator" to "com.google.android.calculator",
        "calendar"   to "com.google.android.calendar",
        "clock"      to "com.google.android.deskclock"
    )

    fun executeCommand(cmd: AppCommand, prefs: SharedPreferences, onResult: (String?) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val ctx = getApplication<Application>()
            val result: String? = when (cmd.type) {
                AppCommand.OPEN_APP      -> openApp(ctx, cmd.params["app_name"] ?: "")
                AppCommand.CLOSE_APP     -> { AccessibilityHelperService.instance?.closeCurrentApp(); "App band!" }
                AppCommand.CALL          -> makeCall(ctx, cmd.params["name"] ?: cmd.params["number"] ?: "")
                AppCommand.PRIME_CALL    -> callPrime(ctx, prefs, cmd.params["index"]?.toIntOrNull() ?: 0)
                AppCommand.PRIME_MSG     -> msgPrime(ctx, prefs, cmd.params["index"]?.toIntOrNull() ?: 0)
                AppCommand.SMS           -> "Kisko SMS karna hai aur kya likhna hai?"
                AppCommand.WHATSAPP_MSG  -> "Kisko WhatsApp karna hai?"
                AppCommand.WHATSAPP_CALL -> "Kisko WhatsApp call karna hai?"
                AppCommand.ANSWER_CALL   -> { acceptCall(ctx); null }
                AppCommand.END_CALL      -> { rejectCall(ctx); null }
                AppCommand.VOLUME_UP     -> { adjustVolume(ctx, true);  "Volume badha diya! 🔊" }
                AppCommand.VOLUME_DOWN   -> { adjustVolume(ctx, false); "Volume kam kar diya! 🔉" }
                AppCommand.FLASHLIGHT_ON -> { toggleTorch(ctx, true);  "Torch on! 🔦" }
                AppCommand.FLASHLIGHT_OFF-> { toggleTorch(ctx, false); "Torch off." }
                AppCommand.WIFI_ON       -> { openWifiSettings(ctx);   "WiFi settings khol rahi hoon." }
                AppCommand.WIFI_OFF      -> { openWifiSettings(ctx);   "WiFi settings khol rahi hoon." }
                AppCommand.BLUETOOTH_ON  -> { openBtSettings(ctx);     "Bluetooth settings khol rahi hoon." }
                AppCommand.BLUETOOTH_OFF -> { openBtSettings(ctx);     "Bluetooth settings khol rahi hoon." }
                else -> null
            }
            withContext(Dispatchers.Main) { onResult(result) }
        }
    }

    // ── App Control ────────────────────────────────────────
    private fun openApp(ctx: Context, name: String): String {
        val pkg = appPackages[name.lowercase()]
            ?: ctx.packageManager.getInstalledApplications(0)
                .firstOrNull { ctx.packageManager.getApplicationLabel(it)
                    .toString().lowercase().contains(name.lowercase()) }
                ?.packageName
            ?: return "Ye app nahi mila: $name"
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg)
            ?: return "$name install nahi hai."
        ctx.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "${name.replaceFirstChar { it.uppercase() }} khol diya! ✅"
    }

    // ── Calls ──────────────────────────────────────────────
    private fun makeCall(ctx: Context, nameOrNumber: String): String {
        val number = if (nameOrNumber.matches(Regex("[+\\d ]+"))) nameOrNumber
                     else resolveNumber(ctx, nameOrNumber)
                         ?: return "$nameOrNumber ka number nahi mila contacts mein."
        ctx.startActivity(Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "Call kar rahi hoon $nameOrNumber ko! 📞"
    }

    private fun callPrime(ctx: Context, prefs: SharedPreferences, idx: Int): String {
        val contacts = loadPrimeContacts(prefs)
        val c = contacts.getOrNull(idx) ?: return "Prime contact number ${idx+1} nahi mila."
        return makeCall(ctx, c.second)
    }

    private fun msgPrime(ctx: Context, prefs: SharedPreferences, idx: Int): String {
        val contacts = loadPrimeContacts(prefs)
        val c = contacts.getOrNull(idx) ?: return "Prime contact nahi mila."
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("smsto:${c.second}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return "${c.first} ko message kar rahi hoon! 💬"
    }

    fun acceptCall(ctx: Context) {
        runCatching {
            (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).acceptRingingCall()
        }
    }

    fun rejectCall(ctx: Context) {
        runCatching {
            @Suppress("DEPRECATION")
            (ctx.getSystemService(Context.TELECOM_SERVICE) as TelecomManager).endCall()
        }
    }

    // ── System Controls ────────────────────────────────────
    private fun adjustVolume(ctx: Context, up: Boolean) {
        val am = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.adjustStreamVolume(
            AudioManager.STREAM_MUSIC,
            if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER,
            AudioManager.FLAG_SHOW_UI
        )
    }

    private fun toggleTorch(ctx: Context, on: Boolean) {
        runCatching {
            val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            cm.setTorchMode(cm.cameraIdList[0], on)
        }
    }

    private fun openWifiSettings(ctx: Context) {
        ctx.startActivity(Intent(android.provider.Settings.ACTION_WIFI_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun openBtSettings(ctx: Context) {
        ctx.startActivity(Intent(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ── Helpers ────────────────────────────────────────────
    private fun resolveNumber(ctx: Context, name: String): String? =
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?",
            arrayOf("%$name%"), null
        )?.use { if (it.moveToFirst()) it.getString(0) else null }

    private fun loadPrimeContacts(prefs: SharedPreferences): List<Pair<String,String>> {
        val json = prefs.getString("prime_contacts_json", null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it); Pair(o.getString("name"), o.getString("number"))
            }
        }.getOrDefault(emptyList())
    }
}
