package com.junior.assistant.ui.main

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.sin

class WaveformView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val barCount = 20
    private val barHeights = FloatArray(barCount) { 4f }
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFF1744.toInt()
        style = Paint.Style.FILL
    }
    private var targetAmplitudes = FloatArray(barCount) { 4f }
    private var animating = false

    fun setAmplitude(rms: Float) {
        for (i in 0 until barCount) {
            targetAmplitudes[i] = (4f + rms * 36f * (0.6f + 0.4f * sin(i * 0.6f)))
        }
    }

    fun startAnimation() {
        animating = true
        invalidate()
    }

    fun stopAnimation() {
        animating = false
        for (i in 0 until barCount) barHeights[i] = 4f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!animating) return

        val barWidth = width / (barCount * 1.6f)
        val spacing = barWidth * 0.6f
        val maxHeight = height.toFloat()

        for (i in 0 until barCount) {
            barHeights[i] += (targetAmplitudes[i] - barHeights[i]) * 0.3f
            val x = i * (barWidth + spacing) + spacing / 2
            val h = barHeights[i].coerceAtMost(maxHeight)
            paint.alpha = (150 + (h / maxHeight * 105)).toInt()
            canvas.drawRoundRect(x, maxHeight - h, x + barWidth, maxHeight, 4f, 4f, paint)
        }
        postInvalidateDelayed(16)
    }
}