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
    lyrics: List<LyricLine>
): Boolean {
    val frame = decodeVideoFrame(context, source, positionMs) ?: return false
    val result = if (includeCaptions) frame.withCaptionOverlay(lyrics, positionMs) else frame
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
    lyrics: List<LyricLine>
): File? {
    val frame = decodeVideoFrame(context, source, positionMs) ?: return null
    val result = if (includeCaptions) frame.withCaptionOverlay(lyrics, positionMs) else frame
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

private fun Bitmap.withCaptionOverlay(lyrics: List<LyricLine>, position: Long): Bitmap {
    val line = lyrics.activeCaptionLineAt(position) ?: return this
    val target = copy(config ?: Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(target)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = (target.height * 0.055f).coerceAtLeast(24f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
    }
    val text = line.text.trim()
    if (text.isBlank()) return this
    val maxWidth = target.width * 0.88f
    val words = text.split(Regex("\\s+")).filter { it.isNotBlank() }
        .let { tokens -> if (tokens.size == 1) tokens.single().map(Char::toString) else tokens }
    val rows = buildList {
        var row = ""
        words.forEach { word ->
            val candidate = if (row.isEmpty()) word else "$row $word"
            if (paint.measureText(candidate) <= maxWidth || row.isEmpty()) {
                row = candidate
            } else {
                add(row)
                row = word
            }
        }
        if (row.isNotEmpty()) add(row)
    }.ifEmpty { listOf(text) }
    val baseline = target.height - target.height * 0.08f - paint.textSize * (rows.size - 1) * 1.14f
    rows.forEachIndexed { index, row ->
        canvas.drawText(
            row,
            (target.width - paint.measureText(row)) / 2f,
            baseline + paint.textSize * index * 1.14f,
            paint
        )
    }
    return target
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
