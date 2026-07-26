package com.ella.music.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.roundToInt
import kotlin.random.Random

internal class LyricVideoParticleEffect(
    textBitmap: Bitmap,
    private val destX: Float,
    private val destY: Float,
    private val totalFrames: Int = DISSOLVE_FRAMES
) {
    companion object {
        const val DISSOLVE_FRAMES = 18
        private const val SAMPLE_SIZE = 4
        private const val PARTICLE_DRAW_SIZE = 4f
    }

    private data class Particle(
        var x: Float,
        var y: Float,
        var vx: Float,
        var vy: Float,
        var alpha: Float,
        var scale: Float,
        val color: Int
    )

    private val particles: List<Particle>
    private var currentFrame = 0
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        val list = mutableListOf<Particle>()
        val w = textBitmap.width
        val h = textBitmap.height
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val pixel = textBitmap.getPixel(
                    x.coerceAtMost(w - 1),
                    y.coerceAtMost(h - 1)
                )
                if ((pixel ushr 24) > 40) {
                    list.add(
                        Particle(
                            x = destX + x.toFloat(),
                            y = destY + y.toFloat(),
                            vx = Random.nextFloat() * 6f - 3f,
                            vy = Random.nextFloat() * -6f - 2f,
                            alpha = 1f,
                            scale = 1f,
                            color = pixel
                        )
                    )
                }
                x += SAMPLE_SIZE
            }
            y += SAMPLE_SIZE
        }
        particles = list
    }

    val isFinished: Boolean get() = currentFrame >= totalFrames

    fun advanceFrame() {
        if (isFinished) return
        currentFrame++
        val progress = currentFrame.toFloat() / totalFrames
        for (p in particles) {
            p.x += p.vx
            p.y += p.vy
            p.vy -= 0.1f
            p.alpha = (1f - progress).coerceIn(0f, 1f)
            p.scale = (1f - progress * 0.7f).coerceIn(0.3f, 1f)
        }
    }

    fun draw(canvas: Canvas) {
        if (particles.isEmpty()) return
        for (p in particles) {
            if (p.alpha <= 0.01f) continue
            val a = (p.alpha * 255).roundToInt().coerceIn(0, 255)
            paint.color = (p.color and 0x00FFFFFF) or (a shl 24)
            val half = PARTICLE_DRAW_SIZE * p.scale / 2f
            canvas.drawRect(p.x - half, p.y - half, p.x + half, p.y + half, paint)
        }
    }

    fun reset() {
        currentFrame = 0
    }
}
