package com.ella.music.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max
import kotlin.math.roundToInt

internal fun renderLyricShareCardBitmap(
    content: LyricShareCardContent,
    layout: LyricShareCardLayout,
    cover: Bitmap?
): Bitmap {
    val bitmap = Bitmap.createBitmap(layout.canvasWidth, layout.adaptiveCanvasHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawShareBackground(
        canvas = canvas,
        width = layout.canvasWidth,
        height = layout.adaptiveCanvasHeight,
        colors = content.backgroundColors
    )
    drawShareHeader(canvas, layout, cover)
    drawShareLyrics(canvas, layout)
    drawShareFooter(canvas, layout)
    return bitmap
}

private fun drawShareHeader(
    canvas: Canvas,
    layout: LyricShareCardLayout,
    cover: Bitmap?
) {
    drawShareHeaderCover(canvas, cover, layout.coverRect)

    var textTop = layout.songInfoTop + 6f
    val textLeft = layout.coverRect.right + 28f
    drawLayout(canvas, layout.titleLayout, textLeft, textTop)
    textTop += layout.titleLayout.height + 10f
    layout.annotationLayout?.let {
        drawLayout(canvas, it, textLeft, textTop)
        textTop += it.height + 12f
    }
    drawLayout(canvas, layout.artistLayout, textLeft, textTop)
}

private fun drawShareHeaderCover(canvas: Canvas, cover: Bitmap?, rect: RectF) {
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(44, 0, 0, 0)
        setShadowLayer(24f, 0f, 10f, Color.argb(72, 0, 0, 0))
    }
    canvas.drawRoundRect(rect, 30f, 30f, shadowPaint)
    if (cover != null) {
        drawRoundedCover(canvas, cover, rect, 30f)
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                rect.left,
                rect.top,
                rect.right,
                rect.bottom,
                intArrayOf(
                    Color.argb(120, 255, 255, 255),
                    Color.argb(34, 255, 255, 255)
                ),
                floatArrayOf(0f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRoundRect(rect, 30f, 30f, placeholderPaint)
        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(208, 255, 255, 255)
            textSize = 42f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val baseline = rect.centerY() - (notePaint.descent() + notePaint.ascent()) / 2f
        canvas.drawText("\u266a", rect.centerX(), baseline, notePaint)
    }
}

private fun drawShareLyrics(
    canvas: Canvas,
    layout: LyricShareCardLayout
) {
    var y = layout.lyricsTop
    layout.lyricBlocks.forEach { block ->
        drawLayout(canvas, block.primary.layout, layout.safePadding, y)
        y += block.primary.layout.height + block.primary.gapAfter
        block.secondary.forEach { secondary ->
            drawLayout(canvas, secondary.layout, layout.safePadding, y)
            y += secondary.layout.height + secondary.gapAfter
        }
        y += block.gapAfter
    }
}

private fun drawShareFooter(
    canvas: Canvas,
    layout: LyricShareCardLayout
) {
    canvas.drawText(layout.footerText, layout.safePadding, layout.viaTextBaseline, layout.footerPaint)
}

private fun drawShareBackground(
    canvas: Canvas,
    width: Int,
    height: Int,
    colors: List<Int>
) {
    val fallbackColors = listOf(
        Color.rgb(69, 78, 110),
        Color.rgb(36, 61, 92),
        Color.rgb(19, 25, 34)
    )
    val picked = colors.filter { Color.alpha(it) > 0 }.ifEmpty { fallbackColors }
    val c1 = picked.first()
    val c2 = picked.getOrElse(1) { c1 }
    val c3 = picked.last()

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            width.toFloat(),
            height * 0.92f,
            intArrayOf(
                c1.lightenForShare(1.12f),
                c2.lightenForShare(1.04f),
                c3.darkenForShare(0.74f)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), this)
    }

    val blobSlots = listOf(
        ShareColorBlob(width * 0.14f, height * 0.16f, width * 0.76f, 112, 1.12f),
        ShareColorBlob(width * 0.84f, height * 0.24f, width * 0.62f, 96, 1.08f),
        ShareColorBlob(width * 0.46f, height * 0.86f, width * 0.82f, 108, 1.10f),
        ShareColorBlob(width * 0.18f, height * 0.68f, width * 0.60f, 82, 1.08f),
        ShareColorBlob(width * 0.76f, height * 0.72f, width * 0.58f, 84, 1.06f),
        ShareColorBlob(width * 0.50f, height * 0.38f, width * 0.66f, 74, 1.10f),
        ShareColorBlob(width * 0.58f, height * 0.08f, width * 0.48f, 68, 1.10f)
    )
    picked.forEachIndexed { index, color ->
        val slot = blobSlots[index % blobSlots.size]
        drawShareColorBlob(
            canvas = canvas,
            cx = slot.cx,
            cy = slot.cy,
            radius = slot.radius,
            color = color.lightenForShare(slot.lightenFactor),
            alpha = slot.alpha
        )
    }

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            width * 0.38f,
            height * 0.42f,
            width * 0.82f,
            intArrayOf(
                Color.argb(42, 255, 255, 255),
                Color.argb(0, 255, 255, 255)
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), this)
    }

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            0f,
            height.toFloat(),
            intArrayOf(
                Color.argb(46, 4, 7, 12),
                Color.argb(12, 4, 7, 12),
                Color.argb(68, 4, 7, 12)
            ),
            floatArrayOf(0f, 0.5f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), this)
    }

    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(10, 0, 0, 0)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), this)
    }
}

private data class ShareColorBlob(
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val alpha: Int,
    val lightenFactor: Float
)

private fun drawShareColorBlob(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    radius: Float,
    color: Int,
    alpha: Int
) {
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            cx,
            cy,
            radius,
            intArrayOf(
                Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color)),
                Color.argb(0, Color.red(color), Color.green(color), Color.blue(color))
            ),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius, this)
    }
}

private fun drawRoundedCover(canvas: Canvas, cover: Bitmap, rect: RectF, radius: Float) {
    val path = Path().apply {
        addRoundRect(rect, radius, radius, Path.Direction.CW)
    }
    val save = canvas.save()
    canvas.clipPath(path)
    val cropped = cover.centerCropScaled(rect.width().roundToInt(), rect.height().roundToInt())
    canvas.drawBitmap(
        cropped,
        rect.left,
        rect.top,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
    )
    cropped.recycle()
    canvas.restoreToCount(save)
}

private fun Bitmap.centerCropScaled(width: Int, height: Int): Bitmap {
    val scale = max(width / this.width.toFloat(), height / this.height.toFloat())
    val scaledWidth = (this.width * scale).toInt().coerceAtLeast(width)
    val scaledHeight = (this.height * scale).toInt().coerceAtLeast(height)
    val scaled = Bitmap.createScaledBitmap(this, scaledWidth, scaledHeight, true)
    val left = ((scaledWidth - width) / 2).coerceAtLeast(0)
    val top = ((scaledHeight - height) / 2).coerceAtLeast(0)
    val result = Bitmap.createBitmap(scaled, left, top, width, height)
    if (scaled !== this && scaled !== result) {
        scaled.recycle()
    }
    return result
}

private fun drawLayout(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
    val save = canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restoreToCount(save)
}
