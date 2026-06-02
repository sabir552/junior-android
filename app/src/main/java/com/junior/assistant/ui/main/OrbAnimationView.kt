package com.junior.assistant.ui.main

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class OrbAnimationView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    enum class State { IDLE, LISTENING, SPEAKING, THINKING, ACTIVE }

    private var currentState = State.IDLE
    private val paint        = Paint(Paint.ANTI_ALIAS_FLAG)
    private var rotationAngle = 0f
    private var waveOffset    = 0f
    private var amplitude     = 0.3f

    // FIX: idle pulse — was always stuck at 1f
    private var scale       = 1f
    private var pulseDir    = 1f
    private var pulseTime   = 0f

    private val idleColors   = intArrayOf(0xFFB71C1C.toInt(), 0xFF880E4F.toInt())
    private val activeColors = intArrayOf(0xFFFF1744.toInt(), 0xFFD500F9.toInt())

    fun setState(state: State) { currentState = state; invalidate() }
    fun setAmplitude(amp: Float) { amplitude = amp.coerceIn(0f, 1f); invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width  / 2f
        val cy = height / 2f

        // Idle pulse: scale 1.0 → 1.15 → 1.0 over ~1500ms at 60fps
        if (currentState == State.IDLE) {
            pulseTime += 0.04f   // ~1500ms cycle
            scale = 1f + 0.075f * sin(pulseTime).toFloat()
        } else {
            scale = 1f + amplitude * 0.12f
        }

        val radius = (minOf(width, height) / 2f) * scale

        // Background glow
        paint.shader = RadialGradient(cx, cy, radius * 1.6f,
            intArrayOf(0x33FF1744.toInt(), 0x00050505), null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius * 1.6f, paint)

        // Core orb gradient
        val colors = if (currentState == State.IDLE) idleColors else activeColors
        paint.shader = RadialGradient(
            cx - radius * 0.3f, cy - radius * 0.3f, radius, colors, null, Shader.TileMode.CLAMP)
        canvas.drawCircle(cx, cy, radius, paint)

        // Rotating arcs
        paint.shader      = null
        paint.style       = Paint.Style.STROKE
        paint.strokeWidth = 4f
        paint.color = if (currentState == State.SPEAKING) 0xFFE040FB.toInt() else 0xFFFF1744.toInt()
        val speed = if (currentState == State.SPEAKING) 4f else 1.5f
        for (i in 0..2) {
            canvas.save()
            canvas.rotate(rotationAngle + i * 40f, cx, cy)
            val r = radius * 0.9f
            canvas.drawArc(cx - r, cy - r, cx + r, cy + r, 0f, 270f, false, paint)
            canvas.restore()
        }
        rotationAngle = (rotationAngle + speed) % 360f

        // Wave rings
        paint.strokeWidth = 2f
        for (i in 0..2) {
            val r = radius * (0.7f + i * 0.15f) * (1f + amplitude * 0.2f)
            canvas.drawCircle(cx, cy, r, paint)
        }
        waveOffset = (waveOffset + 3f) % 360f

        paint.style  = Paint.Style.FILL
        paint.shader = null

        postInvalidateDelayed(16)   // ~60fps
    }
}
