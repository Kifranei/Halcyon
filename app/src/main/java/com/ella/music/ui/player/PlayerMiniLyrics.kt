package com.ella.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import top.yukonga.miuix.kmp.basic.Text

// Full lyrics already collapse inactive x-bg. The mini preview must do the same:
// alpha(0) still occupies layout, which left blank rows around backing-vocal lines.
internal const val MINI_LYRICS_RESERVE_EXTRA_LYRIC_SPACE = false

// Keep the player layout stable when TTML background/translation layers appear or disappear.
// Extra lyric layers are clipped/scrolled inside this viewport instead of moving transport controls.
internal fun miniLyricsPreviewHeight(
    line: LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    compact: Boolean = false
) = when (line?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1) {
    0, 1 -> if (compact) 150.dp else 186.dp
    2 -> if (compact) 154.dp else 202.dp
    3 -> if (compact) 168.dp else 220.dp
    else -> if (compact) 176.dp else 232.dp
}

/**
 * Fixed compact viewport for cramped floating windows. TTML background layers remain inside this
 * area so the transport controls below never shift when the active lyric changes.
 */
internal fun miniLyricsCompactHeight(
    line: LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean
) = when (line?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1) {
    0, 1 -> 40.dp
    2 -> 64.dp
    else -> 84.dp
}

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
    compact: Boolean = false,
    contentColor: Color = Color.White,
    wordLiftEnabled: Boolean = true,
    onLineClick: (LyricLine) -> Unit = {},
    onLineDoubleClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val miniScale by settingsManager.playerMiniLyricScale.collectAsState(initial = 100)
    val miniPrimarySize by settingsManager.playerMiniLyricPrimarySize.collectAsState(initial = 19)
    val miniSecondarySize by settingsManager.playerMiniLyricSecondarySize.collectAsState(initial = 16)
    val miniLineSpacing by settingsManager.playerMiniLyricLineSpacing.collectAsState(initial = 7)
    val miniTextAlign by settingsManager.playerMiniLyricTextAlign.collectAsState(
        initial = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT
    )
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
    val primarySizeSp = miniPrimarySize * if (compact) 0.816f else 1f
    val secondarySizeSp = miniSecondarySize * if (compact) 0.80f else 1f
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
        // Match the 1.2.0 preview density at 100%, while keeping the control accurate to 1%.
        fontScale = miniScale.coerceIn(50, 150) / 100f * 0.92f,
        secondaryFontScale = 1f,
        lyricTextAlign = miniTextAlign,
        primaryTextSizeSp = primarySizeSp,
        secondaryTextSizeSp = secondarySizeSp,
        topContentPadding = 0.dp,
        bottomContentPadding = if (compact) 20.dp else 86.dp,
        focusOffsetRatio = if (compact) 0.02f else 0.12f,
        contentColor = contentColor,
        onLineClick = onLineClick,
        onLineDoubleClick = onLineDoubleClick,
        onLineLongClick = {},
        wordLiftEnabled = wordLiftEnabled,
        nonCurrentLineBlurEnabled = false,
        // The mini preview is tap-to-open only; don't let it scroll on drag.
        userScrollEnabled = false,
        reserveExtraLyricSpace = MINI_LYRICS_RESERVE_EXTRA_LYRIC_SPACE,
        lineSpacing = when {
            singleLinePreview || denseMultiPartPreview -> miniLineSpacing.coerceAtMost(4).dp
            else -> miniLineSpacing.dp
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
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
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
