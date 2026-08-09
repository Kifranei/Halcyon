package com.ella.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import top.yukonga.miuix.kmp.basic.Text

// Keep the player layout stable when TTML background/translation layers appear or disappear.
// Extra lyric layers are clipped/scrolled inside this viewport instead of moving transport controls.
internal fun miniLyricsPreviewHeight(
    compact: Boolean = false
) = if (compact) 154.dp else 202.dp

/**
 * Fixed compact viewport for cramped floating windows. TTML background layers remain inside this
 * area so the transport controls below never shift when the active lyric changes.
 */
internal fun miniLyricsCompactHeight() = 64.dp

@Composable
internal fun MiniLyricsPreview(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    currentPositionMs: Long,
    isPlaying: Boolean,
    isPaused: Boolean = !isPlaying,
    fontFamily: FontFamily? = null,
    translationFontFamily: FontFamily? = fontFamily,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    fontScale: Float = 1f,
    secondaryFontScale: Float = 1f,
    lyricTextAlign: Int = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT,
    compact: Boolean = false,
    contentColor: Color = Color.White,
    wordLiftEnabled: Boolean = true,
    onLineClick: (LyricLine) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val safeIndex = currentIndex.takeIf { it in lyrics.indices }
        ?: lyrics.indexOfFirst { it.hasMiniLyric() }.takeIf { it >= 0 }
        ?: return
    // When only the main line shows (e.g. Chinese with no translation/pronunciation), tighten the
    // line gap so the preview fits ~5 lines instead of ~4.
    val visiblePartCount = lyrics.getOrNull(safeIndex)
        ?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1
    val singleLinePreview = compact || visiblePartCount <= 1
    val denseMultiPartPreview = !compact && visiblePartCount >= 3
    // In a cramped floating window, shrink the type so long (e.g. English) lines fit the narrow
    // width instead of overflowing, and take less vertical room.
    val primarySizeSp = if (compact) 15.5f else 19f
    val secondarySizeSp = if (compact) 12.8f else 15.5f
    AppleMusicLyricsView(
        lyrics = lyrics,
        currentIndex = safeIndex,
        currentPositionMs = currentPositionMs,
        isPlaying = isPlaying,
        isPaused = isPaused,
        // Pausing the cover page should keep the mini lyric focused on the current line. The
        // full lyrics page intentionally reveals all rows while paused for easier reading.
        brightenAllLinesWhenPaused = false,
        showTranslation = showTranslation,
        showPronunciation = showPronunciation,
        fontFamily = fontFamily,
        translationFontFamily = translationFontFamily,
        fontWeight = fontWeight,
        fontScale = fontScale * 0.92f,
        secondaryFontScale = secondaryFontScale,
        lyricTextAlign = lyricTextAlign,
        primaryTextSizeSp = primarySizeSp,
        secondaryTextSizeSp = secondarySizeSp,
        topContentPadding = 0.dp,
        bottomContentPadding = if (compact) 20.dp else 86.dp,
        focusOffsetRatio = if (compact) 0.02f else 0.12f,
        contentColor = contentColor,
        onLineClick = onLineClick,
        onLineDoubleClick = {},
        onLineLongClick = {},
        wordLiftEnabled = wordLiftEnabled,
        nonCurrentLineBlurEnabled = false,
        // The mini preview is tap-to-open only; don't let it scroll on drag.
        userScrollEnabled = false,
        reserveExtraLyricSpace = true,
        lineSpacing = when {
            singleLinePreview -> 4.dp
            denseMultiPartPreview -> 4.dp
            else -> 7.dp
        },
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
internal fun MiniNoLyricsPreview(
    contentColor: Color,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .playerNoIndicationClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = stringResource(R.string.player_no_lyrics),
            color = contentColor.copy(alpha = 0.68f),
            fontSize = 19.sp,
            fontWeight = fontWeight,
            textAlign = TextAlign.Center
        )
    }
}

internal fun LyricLine.hasMiniLyric(): Boolean {
    return !pronunciation.isNullOrBlank() ||
        text.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() } != null ||
        !translation.isNullOrBlank() ||
        backgroundText?.takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() } != null ||
        !backgroundTranslation.isNullOrBlank()
}

internal fun LyricLine.miniVisiblePartCount(
    showTranslation: Boolean,
    showPronunciation: Boolean
): Int {
    var count = 0
    if (showPronunciation && !pronunciation.isNullOrBlank()) count++
    if (text.isNotBlank() && !text.isMusicSymbolOnly()) count++
    if (showTranslation && !translation.isNullOrBlank()) count++
    if (!backgroundText.isNullOrBlank() && !backgroundText.isMusicSymbolOnly()) count++
    if (showTranslation && !backgroundTranslation.isNullOrBlank()) count++
    return count
}

internal fun String.isMusicSymbolOnly(): Boolean {
    val cleaned = trim()
    if (cleaned.isEmpty()) return true
    return cleaned.all { char ->
        char.isWhitespace() ||
            char in setOf('♪', '♫', '♬', '♩', '♭', '♮', '♯', '☆', '★', '·', '.', '。', '…')
    }
}
