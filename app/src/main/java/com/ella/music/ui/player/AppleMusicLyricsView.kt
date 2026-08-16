package com.ella.music.ui.player

import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** A native, independently implemented focus-lyrics renderer. */
@Composable
internal fun AppleMusicLyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    isPaused: Boolean = !isPlaying,
    brightenAllLinesWhenPaused: Boolean = true,
    pageVisible: Boolean = true,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    translationFontFamily: FontFamily? = fontFamily,
    fontWeight: FontWeight,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    lyricTextAlign: Int,
    contentColor: Color,
    wordLiftEnabled: Boolean = true,
    onLineClick: (LyricLine) -> Unit,
    onLineDoubleClick: () -> Unit,
    onLineLongClick: (LyricLine) -> Unit,
    topContentPadding: Dp = 72.dp,
    bottomContentPadding: Dp = 132.dp,
    lineSpacing: Dp = 25.dp,
    focusOffsetRatio: Float = 0.24f,
    nonCurrentLineBlurEnabled: Boolean = true,
    userScrollEnabled: Boolean = true,
    reserveExtraLyricSpace: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pronunciationBelow by remember(context) { SettingsManager.getInstance(context).lyricPronunciationBelow }
        .collectAsState(initial = false)
    val sustainThresholdMs by remember(context) {
        SettingsManager.getInstance(context).appleMusicLyricsSustainThresholdMs
    }.collectAsState(initial = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS)
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            BasicText(
                text = "♪",
                style = TextStyle(fontSize = 28.sp, color = contentColor.copy(alpha = 0.58f), fontFamily = fontFamily)
            )
        }
        return
    }

    // Some files use a shared 00:00 timestamp for static credits / instrumental notices. They
    // are not a scrolling timeline: render every row as a readable, centered card instead of
    // pinning the first row to the normal lyric focus offset.
    val singleTimestampTimeline = lyrics.firstOrNull()?.timeMs?.let { timestamp ->
        lyrics.all { it.timeMs == timestamp }
    } == true
    if (singleTimestampTimeline) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(lineSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                lyrics.forEach { line ->
                    AppleMusicLyricLine(
                        line = line,
                        active = true,
                        paused = true,
                        distance = 0,
                        userScrolling = true,
                        nonCurrentLineBlurEnabled = false,
                        currentPositionMs = Long.MIN_VALUE,
                        showTranslation = showTranslation,
                        showPronunciation = showPronunciation,
                        pronunciationBelow = pronunciationBelow,
                        fontFamily = fontFamily,
                        translationFontFamily = translationFontFamily,
                        fontWeight = fontWeight,
                        fontScale = fontScale,
                        secondaryFontScale = secondaryFontScale,
                        primaryTextSizeSp = primaryTextSizeSp,
                        secondaryTextSizeSp = secondaryTextSizeSp,
                        defaultTextAlign = TextAlign.Center,
                        contentColor = contentColor,
                        wordLiftEnabled = false,
                        sustainThresholdMs = sustainThresholdMs,
                        reserveExtraLyricSpace = false,
                        onClick = { onLineClick(line) },
                        onDoubleClick = onLineDoubleClick,
                        onLongClick = { onLineLongClick(line) }
                    )
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    var trailingLineHeightPx by remember(lyrics) { mutableIntStateOf(0) }
    var hasPositionedScroll by remember(lyrics) { mutableStateOf(false) }
    var deferAutoScroll by remember { mutableStateOf(false) }
    LaunchedEffect(userDragging) {
        if (userDragging) {
            deferAutoScroll = true
        } else if (deferAutoScroll) {
            // ConePlayer keeps the user's reading position briefly before returning to the
            // current line. Its LyricView uses a 2-second delayed recenter message.
            delay(MANUAL_SCROLL_RECENTER_DELAY_MS)
            deferAutoScroll = false
        }
    }
    val parkedPositionMs = remember { mutableLongStateOf(currentPositionMs) }
    val parkedCurrentIndex = remember { mutableIntStateOf(currentIndex) }
    LaunchedEffect(pageVisible) {
        parkedPositionMs.longValue = currentPositionMs
        parkedCurrentIndex.intValue = currentIndex
    }
    val renderIsPlaying = isPlaying && pageVisible
    val renderPositionMs = if (pageVisible) currentPositionMs else parkedPositionMs.longValue
    val renderCurrentIndex = if (pageVisible) currentIndex else parkedCurrentIndex.intValue
    var keepLinesSharp by remember { mutableStateOf(!renderIsPlaying) }
    LaunchedEffect(userDragging, renderIsPlaying) {
        when {
            !renderIsPlaying -> keepLinesSharp = true
            userDragging -> keepLinesSharp = true
            else -> {
                delay(MANUAL_SCROLL_BLUR_RESUME_DELAY_MS)
                keepLinesSharp = false
            }
        }
    }
    val interludes = remember(lyrics) { lyrics.interludes() }
    var smoothPositionMs by remember { mutableLongStateOf(renderPositionMs) }
    LaunchedEffect(renderPositionMs, renderIsPlaying) {
        // The player position is sampled less often than this frame-driven renderer. When a new
        // sample arrives slightly behind the extrapolated display position, restarting from that
        // sample makes the karaoke sheen visibly travel over the same glyph a second time. Keep
        // minor playback regressions on the current frame clock; large jumps are real seeks.
        val anchorPositionMs = if (
            shouldIgnoreMinorPlaybackRegression(
                currentUiPositionMs = smoothPositionMs,
                nextPositionMs = renderPositionMs,
                isPlaying = renderIsPlaying
            )
        ) {
            smoothPositionMs
        } else {
            renderPositionMs
        }
        val anchorFrameNs = withFrameNanos { it }
        smoothPositionMs = anchorPositionMs
        while (renderIsPlaying) {
            val frameNs = withFrameNanos { it }
            smoothPositionMs = anchorPositionMs + ((frameNs - anchorFrameNs) / 1_000_000L)
        }
    }
    val activeInterlude = interludes.firstOrNull { it.isActiveAt(smoothPositionMs) }
    val activeIndex = renderCurrentIndex.coerceIn(0, lyrics.lastIndex)
    val scrollTargetIndex = activeInterlude?.let { interlude ->
        interlude.nextLineIndex + interludes.count { it.nextLineIndex < interlude.nextLineIndex }
    } ?: activeIndex + interludes.count { it.nextLineIndex <= activeIndex }
    LaunchedEffect(pageVisible, scrollTargetIndex, userDragging, deferAutoScroll, trailingLineHeightPx) {
        if (!pageVisible || userDragging || deferAutoScroll) return@LaunchedEffect
        // Do not issue the first scroll before LazyColumn has a viewport; that was making the
        // focus line land under the page header until the user manually scrolled.
        val viewportHeight = snapshotFlow {
            listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        }.filter { it > 0 }.first()
        val desiredItemOffset = viewportHeight * focusOffsetRatio

        if (!hasPositionedScroll) {
            // Initial positioning should not fly through the whole song when the player is
            // restored in the middle of a track.
            listState.scrollToItem(scrollTargetIndex, -desiredItemOffset.toInt())
            hasPositionedScroll = true
            return@LaunchedEffect
        }

        // Measure the target row after it enters the viewport. Average-height prediction is
        // especially inaccurate for TTML x-bg and LRC pronunciation rows, which made the mini
        // lyric overshoot and then visibly correct itself backwards.
        listState.animateScrollToItem(scrollTargetIndex, -desiredItemOffset.toInt())
        val layoutInfo = listState.layoutInfo
        val targetItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == scrollTargetIndex }
        if (targetItem != null) {
            val exactItemOffset = resolveAppleMusicLyricsFocusOffset(
                viewportHeightPx = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset,
                focusOffsetRatio = focusOffsetRatio,
                itemHeightPx = targetItem.size
            )
            val correction = targetItem.offset - exactItemOffset
            if (abs(correction) > CONE_SCROLL_VISIBILITY_THRESHOLD_PX) {
                listState.scroll { scrollBy(correction.toFloat()) }
            }
        }
    }
    val defaultTextAlign = when (lyricTextAlign) {
        SettingsManager.PLAYER_LYRIC_ALIGN_CENTER -> TextAlign.Center
        SettingsManager.PLAYER_LYRIC_ALIGN_RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val trailingLineHeight = with(LocalDensity.current) { trailingLineHeightPx.toDp() }
        // The regular fixed bottom inset is too short for the final line to reach the same
        // focus offset as every other line. Reserve the remaining viewport after that line so
        // the final lyric can still scroll to the focus position rather than pinning to the bottom.
        val trailingFocusPadding = resolveAppleMusicLyricsTrailingPadding(
            viewportHeight = maxHeight,
            focusOffsetRatio = focusOffsetRatio,
            trailingLineHeight = trailingLineHeight,
            minimumBottomPadding = bottomContentPadding
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topContentPadding, bottom = trailingFocusPadding),
            verticalArrangement = Arrangement.spacedBy(lineSpacing),
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier.fillMaxSize()
        ) {
            lyrics.forEachIndexed { index, line ->
                interludes.firstOrNull { it.nextLineIndex == index }?.let { interlude ->
                    item(key = "interlude-${interlude.startMs}-${interlude.endMs}") {
                        AppleMusicInterlude(
                            interlude = interlude,
                            positionMs = smoothPositionMs,
                            contentColor = contentColor,
                            textAlign = lyrics[if (interlude.nextLineIndex == 0) 0 else interlude.nextLineIndex - 1]
                                .duetTextAlign(defaultTextAlign)
                        )
                    }
                }
                item(key = "${line.timeMs}-$index") {
                    val duetActive = line.isDuetLine() && line.isActiveAt(smoothPositionMs)
                    val lineIsActive = activeInterlude == null && (index == activeIndex || duetActive)
                    AppleMusicLyricLine(
                        line = line,
                        active = lineIsActive,
                        paused = isPaused && brightenAllLinesWhenPaused,
                        distance = (index - activeIndex).coerceIn(-4, 4),
                        userScrolling = userDragging || keepLinesSharp,
                        nonCurrentLineBlurEnabled = nonCurrentLineBlurEnabled && renderIsPlaying,
                        // Do not invalidate every retained LazyColumn row for every playback tick.
                        // Only the active (or simultaneous duet) line needs a changing karaoke position.
                        currentPositionMs = if (lineIsActive) smoothPositionMs else Long.MIN_VALUE,
                        showTranslation = showTranslation,
                        showPronunciation = showPronunciation,
                        pronunciationBelow = pronunciationBelow,
                        fontFamily = fontFamily,
                        translationFontFamily = translationFontFamily,
                        fontWeight = fontWeight,
                        fontScale = fontScale,
                        secondaryFontScale = secondaryFontScale,
                        primaryTextSizeSp = primaryTextSizeSp,
                        secondaryTextSizeSp = secondaryTextSizeSp,
                        defaultTextAlign = defaultTextAlign,
                        contentColor = contentColor,
                        wordLiftEnabled = wordLiftEnabled,
                        sustainThresholdMs = sustainThresholdMs,
                        reserveExtraLyricSpace = reserveExtraLyricSpace,
                        onClick = { onLineClick(line) },
                        onDoubleClick = onLineDoubleClick,
                        onLongClick = { onLineLongClick(line) },
                        modifier = if (index == lyrics.lastIndex) {
                            Modifier.onSizeChanged { trailingLineHeightPx = it.height }
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

/**
 * Leaves enough scrollable space after the final lyric for its top edge to reach the same
 * focus offset used by the rest of the list.  A fixed bottom inset only works for short
 * viewports or short final rows; translated and wrapped final rows otherwise stop at the
 * system navigation area.
 */
internal fun resolveAppleMusicLyricsTrailingPadding(
    viewportHeight: Dp,
    focusOffsetRatio: Float,
    trailingLineHeight: Dp,
    minimumBottomPadding: Dp
): Dp {
    val clampedFocusRatio = focusOffsetRatio.coerceIn(0f, 1f)
    val requiredPadding = (
        viewportHeight * (1f - clampedFocusRatio) - trailingLineHeight
    ).coerceAtLeast(0.dp)
    return maxOf(minimumBottomPadding, requiredPadding)
}

internal fun resolveAppleMusicLyricsFocusOffset(
    viewportHeightPx: Int,
    focusOffsetRatio: Float,
    itemHeightPx: Int
): Int {
    val preferredOffset = (viewportHeightPx * focusOffsetRatio.coerceIn(0f, 1f)).roundToInt()
    val maximumOffset = (viewportHeightPx - itemHeightPx).coerceAtLeast(0)
    return preferredOffset.coerceIn(0, maximumOffset)
}

private fun LyricLine.isDuetLine(): Boolean = agent.equals("v1", true) || agent.equals("v2", true)

private fun LyricLine.isActiveAt(positionMs: Long): Boolean {
    val timedEnd = endMs ?: words.maxOfOrNull { it.endMs } ?: backgroundEndMs ?: timeMs + 4_000L
    return positionMs in timeMs until timedEnd.coerceAtLeast(timeMs + 1L)
}

private const val MANUAL_SCROLL_BLUR_RESUME_DELAY_MS = 3_000L
private const val MANUAL_SCROLL_RECENTER_DELAY_MS = 2_000L
private const val CONE_SCROLL_VISIBILITY_THRESHOLD_PX = 0.75f
