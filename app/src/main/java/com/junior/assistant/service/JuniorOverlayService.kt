package com.junior.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.*
import android.widget.ImageButton
import com.junior.assistant.R
import com.junior.assistant.ui.main.MainActivity
import com.junior.assistant.ui.main.OrbAnimationView

class JuniorOverlayService : Service() {

    companion object {
        const val CHANNEL_ID = "junior_overlay_channel"
        private const val NOTIF_ID = 1
        var isRunning = false
    }

    private var windowManager: WindowManager? = null
    private var overlayView: View? = null

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        // FIX: must call startForeground() — was completely missing
        createChannel()
        startForeground(NOTIF_ID, buildNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.getStringExtra("action") == "SHOW_OVERLAY") showOverlay()
        return START_STICKY
    }

    private fun showOverlay() {
        if (overlayView != null) return  // Already showing
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        // FIX: full overlay implementation was missing
        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_orb, null)

        val dp = resources.displayMetrics.density
        val size = (160 * dp).toInt()

        val params = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.CENTER }

        // Tap → open MainActivity
        overlayView?.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            })
        }

        // Close button
        overlayView?.findViewById<ImageButton>(R.id.overlayCloseBtn)?.setOnClickListener {
            removeOverlay()
        }

        // Drag support
        var initialX = 0; var initialY = 0
        var touchX = 0f;  var touchY = 0f

        overlayView?.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x; initialY = params.y
                    touchX = event.rawX;  touchY = event.rawY
                    false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - touchX).toInt()
                    params.y = initialY + (event.rawY - touchY).toInt()
                    windowManager?.updateViewLayout(overlayView, params)
                    true
                }
                else -> false
            }
        }

        overlayView?.let { windowManager?.addView(it, params) }

        // Animate orb in idle state
        overlayView?.findViewById<OrbAnimationView>(R.id.overlayOrb)
            ?.setState(OrbAnimationView.State.IDLE)
    }

    private fun removeOverlay() {
        overlayView?.let { windowManager?.removeView(it) }
        overlayView = null
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        overlayView?.let { runCatching { windowManager?.removeView(it) } }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL_ID, "Junior Overlay",
            NotificationManager.IMPORTANCE_LOW).apply {
            description = "Junior floating assistant orb"
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Junior")
            .setContentText("Tap to open Junior")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(pi)
            .build()
    }
}
