package com.junior.assistant.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class PowerButtonReceiver : BroadcastReceiver() {

    companion object {
        private var lastPressTime = 0L
        private const val DOUBLE_PRESS_MS = 600L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_SCREEN_OFF && action != Intent.ACTION_SCREEN_ON) return

        val now = System.currentTimeMillis()
        if (now - lastPressTime < DOUBLE_PRESS_MS) {
            // Double press confirmed → launch overlay
            lastPressTime = 0L
            val svc = Intent(context, JuniorOverlayService::class.java).apply {
                putExtra("action", "SHOW_OVERLAY")
            }
            context.startForegroundService(svc)
        } else {
            lastPressTime = now
        }
    }
}
