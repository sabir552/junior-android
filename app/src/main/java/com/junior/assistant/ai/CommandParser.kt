package com.junior.assistant.ai

import com.junior.assistant.model.AppCommand

object CommandParser {
    // FIX: returns AppCommand? (null = no command, let Gemini handle as conversation)
    fun parse(text: String): AppCommand? {
        val t = text.lowercase().trim()
        return when {
            // ── App Open ──
            containsAny(t, "youtube", "you tube") && isOpen(t) ->
                AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to "youtube"))
            t.contains("whatsapp") && isOpen(t) ->
                AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to "whatsapp"))
            t.contains("instagram") && isOpen(t) ->
                AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to "instagram"))
            t.contains("spotify") && isOpen(t) ->
                AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to "spotify"))
            t.contains("netflix") && isOpen(t) ->
                AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to "netflix"))
            t.contains("chrome") && isOpen(t) ->
                AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to "chrome"))
            t.contains("settings") && isOpen(t) ->
                AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to "settings"))
            t.contains("telegram") && isOpen(t) ->
                AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to "telegram"))
            t.contains("maps") && isOpen(t) ->
                AppCommand(AppCommand.OPEN_APP, mapOf("app_name" to "maps"))

            // ── App Close ──
            containsAny(t, "band kar", "bnd kar", "close", "baap kar") ->
                AppCommand(AppCommand.CLOSE_APP)

            // ── Prime Call ──
            (containsAny(t, "close friend", "mere dost", "meri jaan", "my love") && t.contains("call")) ||
            (t.contains("prime") && t.contains("call")) ->
                AppCommand(AppCommand.PRIME_CALL, mapOf("index" to "0"))
            containsAny(t, "second contact", "doosra contact") && t.contains("call") ->
                AppCommand(AppCommand.PRIME_CALL, mapOf("index" to "1"))

            // ── WhatsApp ──
            t.contains("whatsapp") && containsAny(t, "karo", "call", "msg", "message") ->
                AppCommand(AppCommand.WHATSAPP_CALL)

            // ── Regular Call ──
            t.contains("call") || t.contains("phone karo") ->
                AppCommand(AppCommand.CALL)

            // ── Prime Message ──
            containsAny(t, "close friend", "meri jaan", "my love") &&
            containsAny(t, "message", "msg") ->
                AppCommand(AppCommand.PRIME_MSG, mapOf("index" to "0"))

            // ── SMS ──
            containsAny(t, "sms", "message bhejo", "msg bhejo", "message karo") ->
                AppCommand(AppCommand.SMS)

            // ── Answer / End Call ──
            containsAny(t, "uthao", "answer", "pick up", "accept") ->
                AppCommand(AppCommand.ANSWER_CALL)
            containsAny(t, "kat do", "reject", "mat uthao", "end call", "hang up") ->
                AppCommand(AppCommand.END_CALL)

            // ── Volume ──
            containsAny(t, "volume badhao", "volume up", "awaaz badhao", "louder") ->
                AppCommand(AppCommand.VOLUME_UP)
            containsAny(t, "volume kam karo", "volume down", "awaaz kam karo", "quieter") ->
                AppCommand(AppCommand.VOLUME_DOWN)

            // ── Flashlight ──
            containsAny(t, "torch on", "flashlight on", "torch jala", "torch chalu") ->
                AppCommand(AppCommand.FLASHLIGHT_ON)
            containsAny(t, "torch off", "flashlight off", "torch band", "torch bujha") ->
                AppCommand(AppCommand.FLASHLIGHT_OFF)

            // ── WiFi ──
            containsAny(t, "wifi on", "wi-fi on", "wifi chalu", "wifi kholo") ->
                AppCommand(AppCommand.WIFI_ON)
            containsAny(t, "wifi off", "wi-fi off", "wifi band karo") ->
                AppCommand(AppCommand.WIFI_OFF)

            // ── Bluetooth ──
            containsAny(t, "bluetooth on", "bluetooth chalu", "bt on") ->
                AppCommand(AppCommand.BLUETOOTH_ON)
            containsAny(t, "bluetooth off", "bluetooth band", "bt off") ->
                AppCommand(AppCommand.BLUETOOTH_OFF)

            // No command matched → return null (Gemini handles as conversation)
            else -> null
        }
    }

    private fun isOpen(t: String) = containsAny(t, "kholo", "open", "start", "launch", "chalu")
    private fun containsAny(t: String, vararg tokens: String) = tokens.any { t.contains(it) }
}
