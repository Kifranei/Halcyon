package com.ella.music.ui.components

import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import com.ella.music.R
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import java.io.File
import java.io.FileOutputStream

internal data class ShareLyricBlock(
    val primary: String,
    val secondary: List<String>
)

internal data class LyricShareCardContent(
    val title: String,
    val artist: String,
    val annotation: String,
    val footerText: String,
    val blocks: List<ShareLyricBlock>,
    val backgroundColors: List<Int>,
    val appendEllipsis: Boolean = false,
    val style: LyricShareCardStyle = LyricShareCardStyle.Current
)

/** The two built-in card arrangements exposed by the lyric-share picker. */
enum class LyricShareCardStyle {
    Current,
    LegacyTopMetadata
}

/** The independently selectable lyric fields and card arrangement in the share picker. */
data class LyricShareOptions(
    val includeOriginal: Boolean = true,
    val includeTranslation: Boolean = true,
    val includePronunciation: Boolean = true,
    val appendEllipsis: Boolean = false,
    val style: LyricShareCardStyle = LyricShareCardStyle.Current
)

fun shareLyricCard(
    context: Context,
    song: Song?,
    line: LyricLine,
    cover: Bitmap?,
    backgroundColors: List<Int>,
    annotation: String = "",
    customInfo: String = "",
    shareTypeface: android.graphics.Typeface? = null,
    includeOriginal: Boolean = true,
    includeTranslation: Boolean = true,
    includePronunciation: Boolean = true,
    appendEllipsis: Boolean = false,
    style: LyricShareCardStyle = LyricShareCardStyle.Current
) {
    shareLyricCard(
        context = context,
        song = song,
        lines = listOf(line),
        cover = cover,
        backgroundColors = backgroundColors,
        annotation = annotation,
        customInfo = customInfo,
        shareTypeface = shareTypeface,
        includeOriginal = includeOriginal,
        includeTranslation = includeTranslation,
        includePronunciation = includePronunciation,
        appendEllipsis = appendEllipsis,
        style = style
    )
}

fun shareLyricCard(
    context: Context,
    song: Song?,
    lines: List<LyricLine>,
    cover: Bitmap?,
    backgroundColors: List<Int>,
    annotation: String = "",
    customInfo: String = "",
    shareTypeface: android.graphics.Typeface? = null,
    includeOriginal: Boolean = true,
    includeTranslation: Boolean = true,
    includePronunciation: Boolean = true,
    appendEllipsis: Boolean = false,
    style: LyricShareCardStyle = LyricShareCardStyle.Current
) {
    runCatching {
        val bitmap = createLyricShareCard(
            context = context,
            song = song,
            lines = lines,
            cover = cover,
            backgroundColors = backgroundColors,
            annotation = annotation,
            customInfo = customInfo,
            shareTypeface = shareTypeface,
            includeOriginal = includeOriginal,
            includeTranslation = includeTranslation,
            includePronunciation = includePronunciation,
            appendEllipsis = appendEllipsis,
            style = style
        )
        val uri = writeLyricShareCard(context, bitmap)
        bitmap.recycle()
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra("com.mocharealm.compound.EXTRA_SOURCE_NAME", context.getString(R.string.app_name))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "${context.getString(R.string.app_name)} Lyric Card", uri)
        }
        context.startActivity(Intent.createChooser(shareIntent, context.getString(R.string.lyric_share_chooser_title)))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.lyric_share_failed), Toast.LENGTH_SHORT).show()
    }
}

internal fun buildLyricShareCardContent(
    context: Context,
    song: Song?,
    lines: List<LyricLine>,
    backgroundColors: List<Int>,
    annotation: String,
    customInfo: String,
    includeOriginal: Boolean = true,
    includeTranslation: Boolean = true,
    includePronunciation: Boolean = true,
    appendEllipsis: Boolean = false,
    style: LyricShareCardStyle = LyricShareCardStyle.Current
): LyricShareCardContent {
    val blocks = lines
        .mapNotNull {
            it.toShareLyricBlock(
                includeOriginal = includeOriginal,
                includeTranslation = includeTranslation,
                includePronunciation = includePronunciation
            )
        }
        .take(SHARE_CARD_MAX_BLOCKS)

    return LyricShareCardContent(
        title = song?.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.lyric_share_unknown_song),
        artist = song?.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.lyric_share_unknown_artist),
        annotation = annotation.trim(),
        footerText = lyricShareFooter(context, customInfo),
        blocks = blocks,
        backgroundColors = backgroundColors,
        appendEllipsis = appendEllipsis,
        style = style
    )
}

private fun createLyricShareCard(
    context: Context,
    song: Song?,
    lines: List<LyricLine>,
    cover: Bitmap?,
    backgroundColors: List<Int>,
    annotation: String,
    customInfo: String,
    shareTypeface: android.graphics.Typeface?,
    includeOriginal: Boolean,
    includeTranslation: Boolean,
    includePronunciation: Boolean,
    appendEllipsis: Boolean,
    style: LyricShareCardStyle
): Bitmap {
    val content = buildLyricShareCardContent(
        context = context,
        song = song,
        lines = lines,
        // The caller already supplies the exact palette used by the player background. Re-sampling
        // the cover here can pick a different dominant region and make preview/export disagree
        // with the Apple-style player surface.
        backgroundColors = backgroundColors,
        annotation = annotation,
        customInfo = customInfo,
        includeOriginal = includeOriginal,
        includeTranslation = includeTranslation,
        includePronunciation = includePronunciation,
        appendEllipsis = appendEllipsis,
        style = style
    )
    val layout = calculateLyricShareLayout(content, shareTypeface = shareTypeface)
    return renderLyricShareCardBitmap(content, layout, cover)
}

private fun writeLyricShareCard(context: Context, bitmap: Bitmap): Uri {
    val dir = File(context.cacheDir, "lyric_share").apply {
        deleteRecursively()
        mkdirs()
    }
    val file = File(dir, "halcyon_lyric_${System.currentTimeMillis()}.png")
    FileOutputStream(file).use { output ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
    }
    return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Save a lyric card into Pictures/Halcyon and make it visible to gallery applications. */
fun saveLyricCardToPictures(
    context: Context,
    song: Song?,
    lines: List<LyricLine>,
    cover: Bitmap?,
    backgroundColors: List<Int>,
    annotation: String = "",
    customInfo: String = "",
    shareTypeface: android.graphics.Typeface? = null,
    exportFolderUri: String = "",
    options: LyricShareOptions = LyricShareOptions()
): Boolean = runCatching {
    val bitmap = createLyricShareCard(
        context = context,
        song = song,
        lines = lines,
        cover = cover,
        backgroundColors = backgroundColors,
        annotation = annotation,
        customInfo = customInfo,
        shareTypeface = shareTypeface,
        includeOriginal = options.includeOriginal,
        includeTranslation = options.includeTranslation,
        includePronunciation = options.includePronunciation,
        appendEllipsis = options.appendEllipsis,
        style = options.style
    )
    val resolver = context.contentResolver
    val customRoot = exportFolderUri.trim()
        .takeIf(String::isNotBlank)
        ?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) }
    if (customRoot != null) {
        val file = customRoot.createFile(
            "image/png",
            "halcyon_lyric_${System.currentTimeMillis()}.png"
        ) ?: error("Unable to create lyric card in selected folder")
        try {
            resolver.openOutputStream(file.uri)?.use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encode failed" }
            } ?: error("Selected folder output stream unavailable")
        } finally {
            bitmap.recycle()
        }
        return@runCatching true
    }
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "halcyon_lyric_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Halcyon")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("MediaStore insert returned null")
    try {
        resolver.openOutputStream(uri)?.use { output ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) { "PNG encode failed" }
        } ?: error("MediaStore output stream unavailable")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(
                uri,
                ContentValues().apply { put(MediaStore.Images.Media.IS_PENDING, 0) },
                null,
                null
            )
        }
    } catch (error: Throwable) {
        resolver.delete(uri, null, null)
        throw error
    } finally {
        bitmap.recycle()
    }
    true
}.getOrElse {
    Toast.makeText(context, context.getString(R.string.lyric_share_save_failed), Toast.LENGTH_SHORT).show()
    false
}

internal fun copySelectedLyricText(
    lines: List<LyricLine>,
    options: LyricShareOptions
): String = lines.mapNotNull { line ->
    line.toShareLyricBlock(
        includeOriginal = options.includeOriginal,
        includeTranslation = options.includeTranslation,
        includePronunciation = options.includePronunciation
    )?.let { block ->
        buildList {
            block.primary.takeIf(String::isNotBlank)?.let(::add)
            block.secondary.filter(String::isNotBlank).forEach(::add)
        }.joinToString("\n")
    }
}.filter(String::isNotBlank).joinToString("\n")

internal fun LyricLine.sharePrimaryText(): String {
    return text.trim().ifBlank {
        backgroundText?.trim().orEmpty()
    }
}

internal fun LyricLine.toShareLyricBlock(
    includeOriginal: Boolean = true,
    includeTranslation: Boolean = true,
    includePronunciation: Boolean = true
): ShareLyricBlock? {
    val original = if (includeOriginal) {
        buildList {
            text.trim().takeIf(String::isNotBlank)?.let(::add)
            backgroundText?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        }
    } else {
        emptyList()
    }
    val translations = if (includeTranslation) {
        listOfNotNull(
            translation?.trim()?.takeIf(String::isNotBlank),
            backgroundTranslation?.trim()?.takeIf(String::isNotBlank)
        )
    } else {
        emptyList()
    }
    val pronunciation = if (includePronunciation) {
        listOfNotNull(pronunciation?.trim()?.takeIf(String::isNotBlank))
    } else {
        emptyList()
    }
    val fields = (original + translations + pronunciation).distinct()
    return fields.firstOrNull()?.let { primary ->
        ShareLyricBlock(primary = primary, secondary = fields.drop(1))
    }
}

internal fun LyricLine.shareLyricFieldTexts(
    includeOriginal: Boolean,
    includeTranslation: Boolean,
    includePronunciation: Boolean
): List<String> = toShareLyricBlock(
    includeOriginal = includeOriginal,
    includeTranslation = includeTranslation,
    includePronunciation = includePronunciation
).let { block ->
    if (block == null) emptyList() else listOf(block.primary) + block.secondary
}

private fun lyricShareFooter(context: Context, customInfo: String): String {
    val normalized = customInfo.trim().removePrefix("@").trim()
    return if (normalized.isBlank()) {
        context.getString(R.string.lyric_share_footer_default)
    } else {
        context.getString(R.string.lyric_share_footer_custom, normalized)
    }
}
