package com.ella.music.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast
import androidx.core.content.FileProvider
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
    val backgroundColors: List<Int>
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
    includeTranslation: Boolean = true
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
        includeTranslation = includeTranslation
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
    includeTranslation: Boolean = true
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
            includeTranslation = includeTranslation
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
    includeTranslation: Boolean = true
): LyricShareCardContent {
    val blocks = lines
        .filter { it.sharePrimaryText().isNotBlank() }
        .mapNotNull { it.toShareLyricBlock(includeTranslation = includeTranslation) }
        .ifEmpty { listOf(ShareLyricBlock("\u266a", emptyList())) }
        .take(SHARE_CARD_MAX_BLOCKS)

    return LyricShareCardContent(
        title = song?.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.lyric_share_unknown_song),
        artist = song?.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.lyric_share_unknown_artist),
        annotation = annotation.trim(),
        footerText = lyricShareFooter(context, customInfo),
        blocks = blocks,
        backgroundColors = backgroundColors
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
    includeTranslation: Boolean
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
        includeTranslation = includeTranslation
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

internal fun LyricLine.sharePrimaryText(): String {
    return text.trim().ifBlank {
        backgroundText?.trim().orEmpty()
    }
}

internal fun LyricLine.toShareLyricBlock(includeTranslation: Boolean = true): ShareLyricBlock? {
    val primary = sharePrimaryText().takeIf { it.isNotBlank() } ?: return null
    val secondary = listOfNotNull(
        translation?.trim()?.takeIf { includeTranslation && it.isNotBlank() },
        backgroundText?.trim()?.takeIf { it.isNotBlank() && it != primary },
        backgroundTranslation?.trim()?.takeIf { includeTranslation && it.isNotBlank() }
    ).distinct()
    return ShareLyricBlock(primary = primary, secondary = secondary)
}

private fun lyricShareFooter(context: Context, customInfo: String): String {
    val normalized = customInfo.trim().removePrefix("@").trim()
    return if (normalized.isBlank()) {
        context.getString(R.string.lyric_share_footer_default)
    } else {
        context.getString(R.string.lyric_share_footer_custom, normalized)
    }
}
