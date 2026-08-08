package com.ella.music

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.primaryEndMs
import java.io.File
import java.io.FileOutputStream

internal fun captureVideoFrame(
    context: Context,
    source: Uri,
    positionMs: Long,
    includeCaptions: Boolean,
    lyrics: List<LyricLine>,
    captionStyle: MusicVideoCaptionStyle = MusicVideoCaptionStyle(),
    includeTranslation: Boolean = true
): Boolean {
    val frame = decodeVideoFrame(context, source, positionMs) ?: return false
    val result = if (includeCaptions) {
        frame.withCaptionOverlay(lyrics, positionMs, captionStyle, includeTranslation)
    } else {
        frame
    }
    return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Halcyon_MV_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Halcyon")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val output = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        context.contentResolver.openOutputStream(output)?.use { stream ->
            result.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } ?: return false
        context.contentResolver.update(output, ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }, null, null)
        true
    }.getOrDefault(false).also {
        if (result !== frame) result.recycle()
        frame.recycle()
    }
}

internal fun captureVideoFrameFile(
    context: Context,
    source: Uri,
    positionMs: Long,
    includeCaptions: Boolean,
    lyrics: List<LyricLine>,
    captionStyle: MusicVideoCaptionStyle = MusicVideoCaptionStyle(),
    includeTranslation: Boolean = true
): File? {
    val frame = decodeVideoFrame(context, source, positionMs) ?: return null
    val result = if (includeCaptions) {
        frame.withCaptionOverlay(lyrics, positionMs, captionStyle, includeTranslation)
    } else {
        frame
    }
    val dir = File(context.cacheDir, "music_video_capture").apply { mkdirs() }
    val file = File(dir, "halcyon_mv_${System.currentTimeMillis()}.png")
    return runCatching {
        FileOutputStream(file).use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file
    }.getOrNull().also {
        if (result !== frame) result.recycle()
        frame.recycle()
    }
}

private fun decodeVideoFrame(context: Context, source: Uri, positionMs: Long): Bitmap? = runCatching {
    android.media.MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, source)
        retriever.getFrameAtTime(positionMs.coerceAtLeast(0L) * 1_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
    }
}.getOrNull()

private fun Bitmap.withCaptionOverlay(
    lyrics: List<LyricLine>,
    position: Long,
    style: MusicVideoCaptionStyle,
    includeTranslation: Boolean
): Bitmap {
    val line = lyrics.activeCaptionLineAt(position) ?: return this
    val target = copy(config ?: Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(target)
    val text = line.text.trim()
    if (text.isBlank()) {
        target.recycle()
        return this
    }
    val primaryPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = style.textColorArgb
        textSize = (target.height * 0.055f * style.scale).coerceAtLeast(24f)
        typeface = style.resolvedTypeface(bold = style.bold)
        textAlign = android.graphics.Paint.Align.CENTER
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
    }
    val secondaryPaint = android.graphics.Paint(primaryPaint).apply {
        textSize *= 0.76f
        typeface = style.resolvedTypeface(bold = false)
        alpha = 224
    }
    val maxWidth = target.width * 0.88f
    val primaryRows = text.wrapForCapture(primaryPaint, maxWidth)
    val translationRows = if (includeTranslation) {
        line.translation?.trim()?.takeIf { it.isNotBlank() }
            ?.wrapForCapture(secondaryPaint, maxWidth)
            .orEmpty()
    } else {
        emptyList()
    }
    val lineSpacing = primaryPaint.textSize * 1.16f
    val secondarySpacing = secondaryPaint.textSize * 1.16f
    val totalTextHeight = primaryRows.size * lineSpacing + translationRows.size * secondarySpacing
    val centerX = target.width * style.positionX.coerceIn(0f, 1f)
    val centerY = target.height * style.positionY.coerceIn(0f, 1f)
    var top = centerY - totalTextHeight / 2f
    val backgroundColor = Color.argb(
        (style.backgroundAlpha.coerceIn(0f, 1f) * 255f).toInt(),
        Color.red(style.backgroundColorArgb),
        Color.green(style.backgroundColorArgb),
        Color.blue(style.backgroundColorArgb)
    )
    val backgroundPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = backgroundColor
    }
    primaryRows.forEach { row ->
        val baseline = top - primaryPaint.fontMetrics.ascent
        drawCaptureCaptionRow(canvas, row, centerX, baseline, primaryPaint, backgroundPaint)
        top += lineSpacing
    }
    translationRows.forEach { row ->
        val baseline = top - secondaryPaint.fontMetrics.ascent
        drawCaptureCaptionRow(canvas, row, centerX, baseline, secondaryPaint, backgroundPaint)
        top += secondarySpacing
    }
    return target
}

private fun String.wrapForCapture(
    paint: android.graphics.Paint,
    maxWidth: Float
): List<String> {
    val rawTokens = trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    if (rawTokens.isEmpty()) return emptyList()
    val splitSingleToken = rawTokens.size == 1 && paint.measureText(rawTokens.single()) > maxWidth
    val tokens = if (splitSingleToken) {
        rawTokens.single().map(Char::toString)
    } else {
        rawTokens
    }
    val separator = if (rawTokens.size == 1) "" else " "
    return buildList {
        var row = ""
        tokens.forEach { token ->
            val candidate = if (row.isEmpty()) token else "$row$separator$token"
            if (paint.measureText(candidate) <= maxWidth || row.isEmpty()) {
                row = candidate
            } else {
                add(row)
                row = token
            }
        }
        if (row.isNotEmpty()) add(row)
    }.ifEmpty { listOf(this) }
}

private fun drawCaptureCaptionRow(
    canvas: android.graphics.Canvas,
    text: String,
    centerX: Float,
    baseline: Float,
    textPaint: android.graphics.Paint,
    backgroundPaint: android.graphics.Paint
) {
    val horizontalPadding = textPaint.textSize * 0.28f
    val metrics = textPaint.fontMetrics
    val left = centerX - textPaint.measureText(text) / 2f - horizontalPadding
    val right = centerX + textPaint.measureText(text) / 2f + horizontalPadding
    canvas.drawRoundRect(
        left,
        baseline + metrics.ascent - textPaint.textSize * 0.18f,
        right,
        baseline + metrics.descent + textPaint.textSize * 0.18f,
        textPaint.textSize * 0.18f,
        textPaint.textSize * 0.18f,
        backgroundPaint
    )
    canvas.drawText(text, centerX, baseline, textPaint)
}

private fun MusicVideoCaptionStyle.resolvedTypeface(bold: Boolean): android.graphics.Typeface {
    val base = when (fontFamily) {
        1 -> android.graphics.Typeface.SERIF
        2 -> android.graphics.Typeface.MONOSPACE
        else -> android.graphics.Typeface.DEFAULT
    }
    return android.graphics.Typeface.create(base, if (bold) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
}

private fun List<LyricLine>.activeCaptionLineAt(position: Long): LyricLine? {
    val captionLines = filter { it.text.isNotBlank() }
    if (captionLines.isEmpty()) return null
    val index = captionLines.indexOfLast { it.timeMs <= position }
    if (index < 0) return null
    val line = captionLines[index]
    val next = captionLines.getOrNull(index + 1)
    return line.takeIf { position < line.primaryEndMs(next) }
}
