package com.ella.music.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.Rect
import kotlin.math.roundToInt
import kotlin.random.Random

internal interface LyricVideoDissolveEffect {
    val isFinished: Boolean
    fun advanceFrame()
    fun draw(canvas: Canvas)
    fun reset()
}

internal class LyricVideoNeonDissolve(
    private val textBitmap: Bitmap,
    private val destX: Float,
    private val destY: Float,
    private val totalFrames: Int = 18
) : LyricVideoDissolveEffect {
    private var currentFrame = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.rgb(0x00, 0xF2, 0xFE), PorterDuff.Mode.SRC_IN)
    }

    override val isFinished: Boolean get() = currentFrame >= totalFrames

    override fun advanceFrame() {
        if (!isFinished) currentFrame++
    }

    override fun draw(canvas: Canvas) {
        if (textBitmap.isRecycled) return
        val progress = (currentFrame.toFloat() / totalFrames).coerceIn(0f, 1f)
        // Neon flicker pattern: erratic blink dips
        val flicker = when (currentFrame) {
            2, 3 -> 0.35f
            6 -> 0.2f
            7 -> 0.9f
            10, 11 -> 0.15f
            13 -> 0.8f
            else -> 1f
        }
        val alpha = ((1f - progress) * flicker).coerceIn(0f, 1f)
        if (alpha <= 0.01f) return

        val alphaInt = (alpha * 255).roundToInt().coerceIn(0, 255)
        val glowAlphaInt = (alpha * 160).roundToInt().coerceIn(0, 255)

        // Draw neon outer glow offset
        glowPaint.alpha = glowAlphaInt
        canvas.drawBitmap(textBitmap, destX - 2f, destY, glowPaint)
        canvas.drawBitmap(textBitmap, destX + 2f, destY, glowPaint)
        canvas.drawBitmap(textBitmap, destX, destY - 2f, glowPaint)
        canvas.drawBitmap(textBitmap, destX, destY + 2f, glowPaint)

        // Draw main text
        paint.alpha = alphaInt
        canvas.drawBitmap(textBitmap, destX, destY, paint)
    }

    override fun reset() {
        currentFrame = 0
    }
}

internal class LyricVideoGlitchDissolve(
    private val textBitmap: Bitmap,
    private val destX: Float,
    private val destY: Float,
    private val totalFrames: Int = 18
) : LyricVideoDissolveEffect {
    private var currentFrame = 0
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.argb(180, 255, 30, 80), PorterDuff.Mode.SRC_IN)
    }
    private val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = PorterDuffColorFilter(Color.argb(180, 0, 230, 255), PorterDuff.Mode.SRC_IN)
    }
    private val normalPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override val isFinished: Boolean get() = currentFrame >= totalFrames

    override fun advanceFrame() {
        if (!isFinished) currentFrame++
    }

    override fun draw(canvas: Canvas) {
        if (textBitmap.isRecycled) return
        val progress = (currentFrame.toFloat() / totalFrames).coerceIn(0f, 1f)
        val baseAlpha = (1f - progress).coerceIn(0f, 1f)
        if (baseAlpha <= 0.01f) return

        val w = textBitmap.width
        val h = textBitmap.height
        val sliceH = 16
        val random = Random(currentFrame * 7919)
        var y = 0

        while (y < h) {
            val curSliceH = minOf(sliceH, h - y)
            val srcRect = Rect(0, y, w, y + curSliceH)
            val jitter = if (random.nextFloat() > 0.4f) {
                (random.nextFloat() * 24f - 12f) * (1f + progress)
            } else 0f

            val rAlpha = ((baseAlpha * 0.7f) * 255).roundToInt().coerceIn(0, 255)
            val cAlpha = ((baseAlpha * 0.7f) * 255).roundToInt().coerceIn(0, 255)
            val nAlpha = (baseAlpha * 255).roundToInt().coerceIn(0, 255)

            redPaint.alpha = rAlpha
            cyanPaint.alpha = cAlpha
            normalPaint.alpha = nAlpha

            val dstY = destY + y
            // Chromatic split slices
            val dstRed = Rect((destX + jitter - 6f).roundToInt(), dstY.roundToInt(), (destX + jitter - 6f + w).roundToInt(), (dstY + curSliceH).roundToInt())
            val dstCyan = Rect((destX + jitter + 6f).roundToInt(), dstY.roundToInt(), (destX + jitter + 6f + w).roundToInt(), (dstY + curSliceH).roundToInt())
            val dstNorm = Rect((destX + jitter).roundToInt(), dstY.roundToInt(), (destX + jitter + w).roundToInt(), (dstY + curSliceH).roundToInt())

            canvas.drawBitmap(textBitmap, srcRect, dstRed, redPaint)
            canvas.drawBitmap(textBitmap, srcRect, dstCyan, cyanPaint)
            canvas.drawBitmap(textBitmap, srcRect, dstNorm, normalPaint)

            y += curSliceH
        }
    }

    override fun reset() {
        currentFrame = 0
    }
}

internal class LyricVideoFadeDissolve(
    private val textBitmap: Bitmap,
    private val destX: Float,
    private val destY: Float,
    private val totalFrames: Int = 18
) : LyricVideoDissolveEffect {
    private var currentFrame = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override val isFinished: Boolean get() = currentFrame >= totalFrames

    override fun advanceFrame() {
        if (!isFinished) currentFrame++
    }

    override fun draw(canvas: Canvas) {
        if (textBitmap.isRecycled) return
        val progress = (currentFrame.toFloat() / totalFrames).coerceIn(0f, 1f)
        val alpha = (1f - progress).coerceIn(0f, 1f)
        if (alpha <= 0.01f) return
        paint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        canvas.drawBitmap(textBitmap, destX, destY, paint)
    }

    override fun reset() {
        currentFrame = 0
    }
}
