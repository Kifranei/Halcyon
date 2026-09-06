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
import kotlin.math.abs

// Full lyrics already collapse inactive x-bg. The mini preview must do the same:
// alpha(0) still occupies layout, which left blank rows around backing-vocal lines.
internal const val MINI_LYRICS_RESERVE_EXTRA_LYRIC_SPACE = false

/** Per-row visibility used by the fixed mini-lyric preview window. */
internal data class MiniLyricLinePresentation(
    val showPrimaryText: Boolean,
    val showTranslation: Boolean,
    val showPronunciation: Boolean,
    val showBackgroundText: Boolean = false
)

internal data class MiniLyricWindowItem(
    val sourceIndex: Int,
    val line: LyricLine,
    val presentation: MiniLyricLinePresentation
)

/**
 * Builds the small, deterministic line window used below the album cover. The visible rows are
 * deliberately different for top and centered alignment so secondary text does not create a
 * blank/overfull row around the active lyric.
 */
internal fun buildMiniLyricWindow(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    verticalAlignment: Int
): List<MiniLyricWindowItem> {
    val candidates = lyrics.mapIndexedNotNull { index, line ->
        line.takeIf { it.hasMiniLyric() }?.let { index to it }
    }
    if (candidates.isEmpty()) return emptyList()
    val anchor = candidates.indices.minByOrNull { position ->
        abs(candidates[position].first - currentIndex.coerceAtLeast(0))
    } ?: 0
    val hasTranslation = showTranslation && candidates.any { !it.second.translation.isNullOrBlank() }
    val hasPronunciation = showPronunciation && candidates.any { !it.second.pronunciation.isNullOrBlank() }
    val center = verticalAlignment == SettingsManager.PLAYER_MINI_LYRIC_VERTICAL_ALIGN_CENTER
    val full = MiniLyricLinePresentation(
        showPrimaryText = true,
        showTranslation = hasTranslation,
        showPronunciation = hasPronunciation
    )
    val primaryOnly = MiniLyricLinePresentation(
        showPrimaryText = true,
        showTranslation = false,
        showPronunciation = false
    )
    val translationOnly = MiniLyricLinePresentation(
        showPrimaryText = false,
        showTranslation = true,
        showPronunciation = false
    )
    val primaryTranslation = MiniLyricLinePresentation(
        showPrimaryText = true,
        showTranslation = hasTranslation,
        showPronunciation = false
    )
    val primaryPronunciation = MiniLyricLinePresentation(
        showPrimaryText = true,
        showTranslation = false,
        showPronunciation = hasPronunciation
    )
    val specs: List<Pair<Int, MiniLyricLinePresentation>> = when {
        center && hasTranslation && hasPronunciation -> listOf(
            -1 to primaryTranslation,
            0 to full,
            1 to primaryPronunciation
        )
        center && hasTranslation -> listOf(
            -1 to full,
            0 to full,
            1 to full
        )
        center && hasPronunciation -> listOf(
            -1 to primaryPronunciation,
            0 to full,
            1 to primaryPronunciation
        )
        center -> listOf(
            -2 to primaryOnly,
            -1 to primaryOnly,
            0 to primaryOnly,
            1 to primaryOnly,
            2 to primaryOnly
        )
        hasTranslation && hasPronunciation -> listOf(
            -1 to translationOnly,
            0 to full,
            1 to full
        )
        hasTranslation -> listOf(
            -1 to translationOnly,
            0 to full,
            1 to full,
            2 to primaryOnly
        )
        hasPronunciation -> listOf(
            -1 to primaryOnly,
            0 to full,
            1 to full,
            2 to primaryPronunciation
        )
        else -> listOf(
            -1 to primaryOnly,
            0 to primaryOnly,
            1 to primaryOnly,
            2 to primaryOnly,
            3 to primaryOnly
        )
    }
    return specs.mapNotNull { (offset, presentation) ->
        candidates.getOrNull(anchor + offset)?.let { (sourceIndex, line) ->
            MiniLyricWindowItem(sourceIndex, line, presentation)
        }
    }
}

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
    // Both player presentations use the retained full lyric window. Keeping one list lets the
    // spring-driven scroll carry velocity across line changes instead of rebuilding a short
    // neighbor list (which makes wrapping jump with no elastic transition).
    legacyWindow: Boolean = false,
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
    val miniVerticalAlign by settingsManager.playerMiniLyricVerticalAlign.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_MINI_LYRIC_VERTICAL_ALIGN
    )
    val safeIndex = currentIndex.takeIf { it in lyrics.indices }
        ?: lyrics.indexOfFirst { it.hasMiniLyric() }.takeIf { it >= 0 }
        ?: return
    val miniWindow = if (legacyWindow) {
        null
    } else {
        remember(
            lyrics,
            currentIndex,
            showTranslation,
            showPronunciation,
            miniVerticalAlign
        ) {
            buildMiniLyricWindow(
                lyrics = lyrics,
                currentIndex = currentIndex,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                verticalAlignment = miniVerticalAlign
            )
        }
    }
    if (miniWindow != null && miniWindow.isEmpty()) return
    // The shared full window keeps the list identity stable. Include the lyric list itself in the
    // key so a resident player cannot keep the first song's preview forever after the queue
    // advances.
    val previewLyrics = remember(legacyWindow, miniWindow, lyrics) {
        miniWindow?.map(MiniLyricWindowItem::line) ?: lyrics
    }
    val previewCurrentIndex = if (legacyWindow) {
        safeIndex
    } else {
        miniWindow!!.indexOfFirst { it.sourceIndex == currentIndex }
            .takeIf { it >= 0 }
            ?: miniWindow.indexOfFirst { it.presentation.showPrimaryText }
                .takeIf { it >= 0 }
            ?: 0
    }
    // When only the main line shows (e.g. Chinese with no translation/pronunciation), tighten the
    // line gap so the preview fits ~5 lines instead of ~4.
    val visiblePartCount = previewLyrics.getOrNull(previewCurrentIndex)
        ?.miniVisiblePartCount(showTranslation, showPronunciation) ?: 1
    val singleLinePreview = compact || visiblePartCount <= 1
    val denseMultiPartPreview = !compact && visiblePartCount >= 3
    // In a cramped floating window, shrink the type so long (e.g. English) lines fit the narrow
    // width instead of overflowing, and take less vertical room.
    val primarySizeSp = miniPrimarySize * if (compact) 0.816f else 1f
    val secondarySizeSp = miniSecondarySize * if (compact) 0.80f else 1f
    AppleMusicLyricsView(
        lyrics = previewLyrics,
        currentIndex = previewCurrentIndex,
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
        bottomContentPadding = when {
            legacyWindow && compact -> 20.dp
            legacyWindow -> 86.dp
            else -> 0.dp
        },
        focusOffsetRatio = when {
            legacyWindow && compact -> 0.02f
            legacyWindow -> 0.12f
            compact -> 0.02f
            miniVerticalAlign == SettingsManager.PLAYER_MINI_LYRIC_VERTICAL_ALIGN_CENTER -> 0.50f
            else -> 0.12f
        },
        contentColor = contentColor,
        onLineClick = onLineClick,
        onLineDoubleClick = onLineDoubleClick,
        onLineLongClick = {},
        wordLiftEnabled = wordLiftEnabled,
        nonCurrentLineBlurEnabled = false,
        // The mini preview is tap-to-open only; don't let it scroll on drag.
        userScrollEnabled = false,
        reserveExtraLyricSpace = MINI_LYRICS_RESERVE_EXTRA_LYRIC_SPACE,
        linePresentation = if (legacyWindow) {
            null
        } else {
            { index, _ -> miniWindow?.getOrNull(index)?.presentation }
        },
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
