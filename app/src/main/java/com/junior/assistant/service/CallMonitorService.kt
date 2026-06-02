package com.junior.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.provider.ContactsContract
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager

class CallMonitorService : Service() {

    companion object {
        private const val CHANNEL_ID = "junior_call_monitor"
        private const val NOTIF_ID   = 2
    }

    private lateinit var telephonyManager: TelephonyManager

    @Suppress("DEPRECATION")
    private val phoneListener = object : PhoneStateListener() {
        override fun onCallStateChanged(state: Int, phoneNumber: String?) {
            when (state) {
                TelephonyManager.CALL_STATE_RINGING -> {
                    // FIX: resolve caller name and broadcast to MainActivity
                    val callerName = phoneNumber
                        ?.let { resolveCallerName(it) }
                        ?: phoneNumber
                        ?: "Unknown"
                    sendBroadcast(Intent("com.junior.INCOMING_CALL").apply {
                        putExtra("INCOMING_CALL", true)
                        putExtra("CALLER_NAME", callerName)
                    })
                }
                TelephonyManager.CALL_STATE_IDLE -> {
                    sendBroadcast(Intent("com.junior.CALL_ENDED"))
                }
                TelephonyManager.CALL_STATE_OFFHOOK -> { /* call in progress, no action */ }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        // FIX: must call startForeground() — was missing, caused crash on API 28+
        createChannel()
        startForeground(NOTIF_ID, buildNotification())

        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
        @Suppress("DEPRECATION")
        telephonyManager.listen(phoneListener, PhoneStateListener.LISTEN_CALL_STATE)
    }

    override fun onDestroy() {
        super.onDestroy()
        @Suppress("DEPRECATION")
        telephonyManager.listen(phoneListener, PhoneStateListener.LISTEN_NONE)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /** Resolve last 7+ digits of incoming number against Contacts */
    private fun resolveCallerName(number: String): String {
        val digits = number.replace(Regex("[^0-9]"), "")
        val suffix = if (digits.length >= 7) digits.takeLast(7) else digits
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val proj = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        return try {
            contentResolver.query(uri, proj, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val name   = cursor.getString(0) ?: continue
                    val phone  = cursor.getString(1)?.replace(Regex("[^0-9]"), "") ?: continue
                    if (phone.endsWith(suffix)) return name
                }
                null
            } ?: number
        } catch (e: Exception) { number }
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Junior Call Monitor",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "Monitors incoming calls for Junior"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification() = Notification.Builder(this, CHANNEL_ID)
        .setContentTitle("Junior")
        .setContentText("Monitoring calls...")
        .setSmallIcon(android.R.drawable.ic_menu_call)
        .build()
}
