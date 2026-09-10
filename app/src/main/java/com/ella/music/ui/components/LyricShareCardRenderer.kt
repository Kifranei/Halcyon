package com.ella.music.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.text.StaticLayout
import android.text.TextPaint
import com.ella.music.ui.player.createAppleFlowFrameBitmap
import com.ella.music.ui.player.scaledForFlowSource
import kotlin.math.roundToInt

internal fun renderLyricShareCardBitmap(
    content: LyricShareCardContent,
    layout: LyricShareCardLayout,
    cover: Bitmap?
): Bitmap {
    val bitmap = Bitmap.createBitmap(layout.canvasWidth, layout.adaptiveCanvasHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val cardRect = RectF(0f, 0f, layout.canvasWidth.toFloat(), layout.adaptiveCanvasHeight.toFloat())
    val cardPath = Path().apply {
        addRoundRect(cardRect, layout.cardRadius, layout.cardRadius, Path.Direction.CW)
    }
    val save = canvas.save()
    canvas.clipPath(cardPath)
    drawShareBackground(canvas, layout, content.backgroundColors)
    drawShareFlowBackground(canvas, layout, cover, content.backgroundColors)
    drawShareLyrics(canvas, layout)
    drawShareChin(canvas, layout, cover)
    canvas.restoreToCount(save)
    return bitmap
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

private fun drawShareChin(
    canvas: Canvas,
    layout: LyricShareCardLayout,
    cover: Bitmap?
) {
    val chinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0x52, 0, 0, 0)
    }
    val (chinTop, chinBottom) = if (layout.style == LyricShareCardStyle.LegacyTopMetadata) {
        0f to layout.chinHeight
    } else {
        layout.chinTop to layout.adaptiveCanvasHeight.toFloat()
    }
    canvas.drawRect(
        0f,
        chinTop,
        layout.canvasWidth.toFloat(),
        chinBottom,
        chinPaint
    )
    drawShareHeaderCover(canvas, cover, layout.coverRect, layout.coverRadius)
    val textLeft = layout.coverRect.right + APPLE_MUSIC_SHARE_STICKER_COVER_TEXT_GAP_DP *
        appleMusicShareCardScale(layout.canvasWidth)
    drawLayout(canvas, layout.titleLayout, textLeft, layout.titleTop)
    drawLayout(canvas, layout.artistLayout, textLeft, layout.artistTop)
    canvas.drawText(layout.footerText, textLeft, layout.footerBaseline, layout.footerPaint)
}

private fun drawShareHeaderCover(
    canvas: Canvas,
    cover: Bitmap?,
    rect: RectF,
    radius: Float
) {
    if (cover != null) {
        drawRoundedCover(canvas, cover, rect, radius)
    } else {
        val placeholderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(36, 255, 255, 255)
        }
        canvas.drawRoundRect(rect, radius, radius, placeholderPaint)
        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(208, 255, 255, 255)
            textSize = rect.height() * 0.42f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(
                android.graphics.Typeface.DEFAULT,
                android.graphics.Typeface.BOLD
            )
        }
        val baseline = rect.centerY() - (notePaint.descent() + notePaint.ascent()) / 2f
        canvas.drawText("\u266a", rect.centerX(), baseline, notePaint)
    }
}

private fun drawShareFlowBackground(
    canvas: Canvas,
    layout: LyricShareCardLayout,
    cover: Bitmap?,
    colors: List<Int>
) {
    if (cover == null || cover.isRecycled) return
    val source = cover.scaledForFlowSource()
    val base = colors.firstOrNull { Color.alpha(it) > 0 } ?: Color.rgb(42, 46, 72)
    val flow = runCatching {
        createAppleFlowFrameBitmap(
            cover = source,
            viewportW = layout.canvasWidth,
            viewportH = layout.adaptiveCanvasHeight,
            timeMs = 18_000L,
            densityDpi = 420,
            blur = 60f,
            washPrimaryArgb = Color.argb(
                86,
                (Color.red(base) * 0.72f).roundToInt(),
                (Color.green(base) * 0.72f).roundToInt(),
                (Color.blue(base) * 0.72f).roundToInt()
            ),
            washSecondaryArgb = Color.argb(46, 0, 0, 0)
        )
    }.getOrNull()
    if (flow == null) {
        if (source !== cover) source.recycle()
        return
    }
    canvas.drawBitmap(
        flow,
        null,
        RectF(0f, 0f, layout.canvasWidth.toFloat(), layout.adaptiveCanvasHeight.toFloat()),
        Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    )
    if (flow !== source) flow.recycle()
    if (source !== cover && source !== flow) source.recycle()
}

private fun drawShareBackground(
    canvas: Canvas,
    layout: LyricShareCardLayout,
    colors: List<Int>
) {
    val width = layout.canvasWidth.toFloat()
    val height = layout.adaptiveCanvasHeight.toFloat()
    val fallback = Color.rgb(66, 66, 66)
    val picked = colors.filter { Color.alpha(it) > 0 }.ifEmpty { listOf(fallback) }
        .map { it.ensureShareCardContrast() }
    val c1 = picked.first()
    val c2 = picked.getOrElse(1) { c1 }
    val c3 = picked.last()
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = LinearGradient(
            0f,
            0f,
            0f,
            height,
            intArrayOf(c1, c2, c3.darkenForShare(0.88f)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, width, height, this)
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
    val scale = maxOf(width / this.width.toFloat(), height / this.height.toFloat())
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
