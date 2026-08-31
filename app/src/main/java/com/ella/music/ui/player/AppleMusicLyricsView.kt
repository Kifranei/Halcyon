package com.ella.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.rememberUpdatedState
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
    brightenAllLinesWhenPaused: Boolean? = null,
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
    onLineDoubleClick: (() -> Unit)? = null,
    onLineLongClick: (LyricLine) -> Unit,
    topContentPadding: Dp = 72.dp,
    bottomContentPadding: Dp = 132.dp,
    lineSpacing: Dp = 25.dp,
    focusOffsetRatio: Float = 0.24f,
    focusOffsetNudgeDp: Dp = 0.dp,
    nonCurrentLineBlurEnabled: Boolean = true,
    userScrollEnabled: Boolean = true,
    reserveExtraLyricSpace: Boolean = false,
    singleLine: Boolean = false,
    followWordFocus: Boolean = false,
    showBackgroundText: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pronunciationBelow by remember(context) { SettingsManager.getInstance(context).lyricPronunciationBelow }
        .collectAsState(initial = false)
    val sustainThresholdMs by remember(context) {
        SettingsManager.getInstance(context).appleMusicLyricsSustainThresholdMs
    }.collectAsState(initial = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS)
    val nonCurrentLineBlurPercent by remember(context) {
        SettingsManager.getInstance(context).lyricNonCurrentBlurPercent
    }.collectAsState(initial = 40)
    val wordSeekEnabled by remember(context) {
        SettingsManager.getInstance(context).lyricWordSeekEnabled
    }.collectAsState(initial = false)
    val effectiveLineDoubleClick = onLineDoubleClick.takeIf {
        lyricLineDoubleTapEnabled(wordSeekEnabled)
    }
    val touchFeedbackEnabled by remember(context) {
        SettingsManager.getInstance(context).lyricTouchFeedbackEnabled
    }.collectAsState(initial = false)
    val pauseCurrentOnly by remember(context) {
        SettingsManager.getInstance(context).lyricPauseCurrentOnly
    }.collectAsState(initial = true)
    val revealAllLinesWhilePaused = brightenAllLinesWhenPaused ?: !pauseCurrentOnly
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
                        singleLine = singleLine,
                        followWordFocus = followWordFocus,
                        showBackgroundText = showBackgroundText,
                        onClick = { onLineClick(line) },
                        onDoubleClick = effectiveLineDoubleClick,
                        onLongClick = { onLineLongClick(line) },
                        onWordClick = if (wordSeekEnabled && !line.isOpeningMetadata) {
                            { positionMs -> onLineClick(line.copy(timeMs = positionMs)) }
                        } else null,
                        onTapFraction = line.openingSeekHandler(onLineClick),
                        touchFeedbackEnabled = touchFeedbackEnabled
                    )
                }
            }
        }
        return
    }

    val interludes = remember(lyrics) { lyrics.interludes() }
    val initialActiveIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    val initialActiveInterlude = interludes.firstOrNull { it.isActiveAt(currentPositionMs) }
    val initialScrollTargetIndex = resolveAppleMusicLyricsScrollTargetIndex(
        activeLyricIndex = initialActiveIndex,
        activeInterlude = initialActiveInterlude,
        interludes = interludes
    )
    // Start at the currently playing row. Waiting for the first post-layout effect while the
    // state still points at item 0 makes the lyric page flash the beginning of the song first.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollTargetIndex
    )
    val scrollSpring = remember { Animatable(0f) }
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
    var smoothPositionMs by remember { mutableLongStateOf(renderPositionMs) }
    LaunchedEffect(renderCurrentIndex) {
        val lineStartMs = lyrics.getOrNull(renderCurrentIndex)?.timeMs ?: return@LaunchedEffect
        if (smoothPositionMs < lineStartMs) {
            smoothPositionMs = renderPositionMs.coerceAtLeast(lineStartMs)
        }
    }
    val latestRenderPositionMs by rememberUpdatedState(renderPositionMs)
    val latestPlaying by rememberUpdatedState(renderIsPlaying)
    // Keep one frame-clock loop for the lifetime of this lyric list. Keying it on the 10 Hz
    // player sample (or word-lift) cancelled interpolation every tick and made the karaoke
    // fill jump like a slideshow.
    LaunchedEffect(lyrics) {
        var lastFrameNs = 0L
        while (true) {
            val frameNs = withFrameNanos { it }
            val sampled = latestRenderPositionMs
            val playing = latestPlaying
            if (lastFrameNs == 0L) {
                lastFrameNs = frameNs
                smoothPositionMs = sampled
                continue
            }
            val dtMs = (frameNs - lastFrameNs) / 1_000_000L
            lastFrameNs = frameNs
            smoothPositionMs = nextSmoothLyricPositionMs(
                displayMs = smoothPositionMs,
                sampledMs = sampled,
                frameDeltaMs = dtMs,
                playing = playing
            )
        }
    }
    val activeInterlude = interludes.firstOrNull { it.isActiveAt(smoothPositionMs) }
    val activeIndex = renderCurrentIndex.coerceIn(0, lyrics.lastIndex)
    val renderedScrollTargetIndex = resolveAppleMusicLyricsScrollTargetIndex(
        activeLyricIndex = activeIndex,
        activeInterlude = activeInterlude,
        interludes = interludes
    )
    // Keep only the lightweight list position synchronized while this retained page is hidden.
    // Karaoke rendering remains parked, but the page is ready on the correct row before it is
    // brought on screen again.
    val playbackActiveIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    val playbackActiveInterlude = interludes.firstOrNull { it.isActiveAt(currentPositionMs) }
    val playbackScrollTargetIndex = resolveAppleMusicLyricsScrollTargetIndex(
        activeLyricIndex = playbackActiveIndex,
        activeInterlude = playbackActiveInterlude,
        interludes = interludes
    )
    val scrollTargetIndex = if (pageVisible) renderedScrollTargetIndex else playbackScrollTargetIndex
    val focusOffsetNudgePx = with(LocalDensity.current) { focusOffsetNudgeDp.toPx() }
    LaunchedEffect(pageVisible, scrollTargetIndex, userDragging, deferAutoScroll, focusOffsetNudgePx) {
        if (userDragging || deferAutoScroll) return@LaunchedEffect
        // Do not issue the first scroll before LazyColumn has a viewport; that was making the
        // focus line land under the page header until the user manually scrolled.
        val viewportHeight = snapshotFlow {
            listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        }.filter { it > 0 }.first()
        val desiredItemOffset = (
            viewportHeight * focusOffsetRatio - focusOffsetNudgePx
        ).coerceAtLeast(0f)

        if (!hasPositionedScroll) {
            // Initial positioning should not fly through the whole song when the player is
            // restored in the middle of a track.
            listState.scrollToItem(scrollTargetIndex, -desiredItemOffset.toInt())
            scrollSpring.snapTo(0f)
            hasPositionedScroll = true
            return@LaunchedEffect
        }

        if (!pageVisible) {
            listState.scrollToItem(scrollTargetIndex, -desiredItemOffset.toInt())
            scrollSpring.snapTo(0f)
            return@LaunchedEffect
        }

        // ConePlayer does not restart a fixed-duration list animation for each lyric. It changes
        // every row's spring target (damping 1.25, stiffness 200) and lets the retained velocity
        // carry the content into place. Drive the LazyColumn with the same overdamped spring and
        // correct the distance after variable-height rows have entered the viewport.
        // Jumping with scrollToItem on every line change was the post-1.2.4 stutter.
        repeat(CONE_SCROLL_CORRECTION_PASSES) {
            val layoutInfo = listState.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            if (visibleItems.isEmpty()) return@repeat
            val targetItem = visibleItems.firstOrNull { it.index == scrollTargetIndex }
            val distance = if (targetItem != null) {
                targetItem.offset - desiredItemOffset
            } else {
                val firstItem = visibleItems.first()
                val averageItemExtent = visibleItems.sumOf { it.size }.toFloat() / visibleItems.size +
                    layoutInfo.mainAxisItemSpacing
                firstItem.offset - desiredItemOffset +
                    (scrollTargetIndex - firstItem.index) * averageItemExtent
            }
            if (abs(distance) <= CONE_SCROLL_VISIBILITY_THRESHOLD_PX) return@LaunchedEffect

            // A line change can cancel the preceding spring while it still carries position and
            // velocity. Reusing that stale Animatable state makes the list travel past the new
            // row and then visibly pull the entire lyric block backwards. Keep the spring motion,
            // but restart each measured correction from zero so only the current distance is
            // applied to LazyColumn.
            scrollSpring.snapTo(0f)
            var appliedValue = 0f
            listState.scroll {
                scrollSpring.animateTo(
                    targetValue = distance,
                    animationSpec = spring(
                        dampingRatio = CONE_SCROLL_DAMPING_RATIO,
                        stiffness = CONE_SCROLL_STIFFNESS,
                        visibilityThreshold = CONE_SCROLL_VISIBILITY_THRESHOLD_PX
                    )
                ) {
                    val consumed = scrollBy(value - appliedValue)
                    appliedValue += consumed
                }
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
        // The first lyric has no preceding rows that LazyColumn can scroll through. Reserve its
        // focus offset as actual leading content so 00:00 lyrics land at the same visual anchor
        // instead of sticking to the top edge of compact/immersive lyric viewports.
        val leadingFocusPadding = resolveAppleMusicLyricsLeadingPadding(
            viewportHeight = maxHeight,
            focusOffsetRatio = focusOffsetRatio,
            focusOffsetNudge = focusOffsetNudgeDp,
            minimumTopPadding = topContentPadding
        )
        // The regular fixed bottom inset is too short for the final line to reach the same
        // focus offset as every other line. Reserve the remaining viewport after that line so
        // the final lyric can still scroll to the focus position rather than pinning to the bottom.
        val trailingFocusPadding = resolveAppleMusicLyricsTrailingPadding(
            viewportHeight = maxHeight,
            focusOffsetRatio = focusOffsetRatio,
            focusOffsetNudge = focusOffsetNudgeDp,
            trailingLineHeight = trailingLineHeight,
            minimumBottomPadding = bottomContentPadding
        )
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = leadingFocusPadding, bottom = trailingFocusPadding),
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
                                .duetTextAlign(defaultTextAlign),
                            touchFeedbackEnabled = touchFeedbackEnabled,
                            onSeek = { positionMs ->
                                onLineClick(LyricLine(timeMs = positionMs, text = ""))
                            }
                        )
                    }
                }
                item(key = "${line.timeMs}-$index") {
                    val duetActive = line.isDuetLine() && line.isActiveAt(smoothPositionMs)
                    val lineIsActive = activeInterlude == null && (index == activeIndex || duetActive)
                    AppleMusicLyricLine(
                        line = line,
                        active = lineIsActive,
                        paused = isPaused && revealAllLinesWhilePaused,
                        distance = (index - activeIndex).coerceIn(-4, 4),
                        userScrolling = userDragging || keepLinesSharp,
                        nonCurrentLineBlurEnabled = nonCurrentLineBlurEnabled && renderIsPlaying,
                        nonCurrentLineBlurPercent = nonCurrentLineBlurPercent,
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
                        singleLine = singleLine,
                        followWordFocus = followWordFocus,
                        showBackgroundText = showBackgroundText,
                        onClick = { onLineClick(line) },
                        onDoubleClick = effectiveLineDoubleClick,
                        onLongClick = { onLineLongClick(line) },
                        onWordClick = if (wordSeekEnabled && !line.isOpeningMetadata) {
                            { positionMs -> onLineClick(line.copy(timeMs = positionMs)) }
                        } else null,
                        onTapFraction = line.openingSeekHandler(onLineClick),
                        touchFeedbackEnabled = touchFeedbackEnabled,
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

internal fun lyricLineDoubleTapEnabled(wordSeekEnabled: Boolean): Boolean = !wordSeekEnabled

private fun LyricLine.openingSeekHandler(
    onLineClick: (LyricLine) -> Unit
): ((Float) -> Unit)? {
    if (resolveOpeningLyricSeekPosition(this, 0f) == null) return null
    return { fraction ->
        resolveOpeningLyricSeekPosition(this, fraction)?.let { positionMs ->
            onLineClick(copy(timeMs = positionMs))
        }
    }
}

internal fun resolveOpeningLyricSeekPosition(line: LyricLine, fraction: Float): Long? {
    val endMs = line.endMs?.takeIf { line.isOpeningMetadata && it > line.timeMs } ?: return null
    return line.timeMs + ((endMs - line.timeMs) * fraction.coerceIn(0f, 1f)).toLong()
}

internal fun resolveAppleMusicLyricsLeadingPadding(
    viewportHeight: Dp,
    focusOffsetRatio: Float,
    minimumTopPadding: Dp,
    focusOffsetNudge: Dp = 0.dp
): Dp = maxOf(
    minimumTopPadding,
    (viewportHeight * focusOffsetRatio.coerceIn(0f, 1f) - focusOffsetNudge).coerceAtLeast(0.dp)
)

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
    minimumBottomPadding: Dp,
    focusOffsetNudge: Dp = 0.dp
): Dp {
    val clampedFocusRatio = focusOffsetRatio.coerceIn(0f, 1f)
    val requiredPadding = (
        viewportHeight * (1f - clampedFocusRatio) + focusOffsetNudge - trailingLineHeight
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

internal fun resolveAppleMusicLyricsScrollTargetIndex(
    activeLyricIndex: Int,
    activeInterlude: AppleMusicInterlude?,
    interludes: List<AppleMusicInterlude>
): Int = activeInterlude?.let { interlude ->
    interlude.nextLineIndex + interludes.count { it.nextLineIndex < interlude.nextLineIndex }
} ?: activeLyricIndex + interludes.count { it.nextLineIndex <= activeLyricIndex }

internal fun nextSmoothLyricPositionMs(
    displayMs: Long,
    sampledMs: Long,
    frameDeltaMs: Long,
    playing: Boolean,
    seekThresholdMs: Long = 1_500L,
    backwardToleranceMs: Long = PLAYER_POSITION_BACKWARD_DRIFT_TOLERANCE_MS
): Long {
    if (!playing) return sampledMs
    if (frameDeltaMs > seekThresholdMs) return sampledMs
    val predicted = displayMs + frameDeltaMs.coerceAtLeast(0L)
    val delta = sampledMs - predicted
    return when {
        abs(delta) > seekThresholdMs -> sampledMs
        delta < 0L && -delta <= backwardToleranceMs -> predicted
        delta > 80L -> predicted + (delta / 4L)
        else -> predicted
    }
}

private fun LyricLine.isDuetLine(): Boolean = agent.equals("v1", true) || agent.equals("v2", true)

private fun LyricLine.isActiveAt(positionMs: Long): Boolean {
    val timedEnd = endMs ?: words.maxOfOrNull { it.endMs } ?: backgroundEndMs ?: timeMs + 4_000L
    return positionMs in timeMs until timedEnd.coerceAtLeast(timeMs + 1L)
}

private const val MANUAL_SCROLL_BLUR_RESUME_DELAY_MS = 3_000L
private const val MANUAL_SCROLL_RECENTER_DELAY_MS = 2_000L
private const val CONE_SCROLL_DAMPING_RATIO = 1.25f
private const val CONE_SCROLL_STIFFNESS = 200f
private const val CONE_SCROLL_VISIBILITY_THRESHOLD_PX = 0.75f
private const val CONE_SCROLL_CORRECTION_PASSES = 2
