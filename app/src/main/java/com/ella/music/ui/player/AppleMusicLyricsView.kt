package com.ella.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
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

/** A native, independently implemented focus-lyrics renderer. */
@Composable
internal fun AppleMusicLyricsView(
    lyrics: List<LyricLine>,
    currentIndex: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
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
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pronunciationBelow by remember(context) { SettingsManager.getInstance(context).lyricPronunciationBelow }
        .collectAsState(initial = false)
    if (lyrics.isEmpty()) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            BasicText(
                text = "♪",
                style = TextStyle(fontSize = 28.sp, color = contentColor.copy(alpha = 0.58f), fontFamily = fontFamily)
            )
        }
        return
    }

    val listState = rememberLazyListState()
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    val scrollSpring = remember { Animatable(0f) }
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
    var keepLinesSharp by remember { mutableStateOf(!isPlaying) }
    LaunchedEffect(userDragging, isPlaying) {
        when {
            !isPlaying -> keepLinesSharp = true
            userDragging -> keepLinesSharp = true
            else -> {
                delay(MANUAL_SCROLL_BLUR_RESUME_DELAY_MS)
                keepLinesSharp = false
            }
        }
    }
    val interludes = remember(lyrics) { lyrics.interludes() }
    var smoothPositionMs by remember { mutableLongStateOf(currentPositionMs) }
    LaunchedEffect(currentPositionMs, isPlaying) {
        val anchorPositionMs = currentPositionMs
        val anchorFrameNs = withFrameNanos { it }
        smoothPositionMs = anchorPositionMs
        while (isPlaying) {
            val frameNs = withFrameNanos { it }
            smoothPositionMs = anchorPositionMs + ((frameNs - anchorFrameNs) / 1_000_000L)
        }
    }
    val activeInterlude = interludes.firstOrNull { it.isActiveAt(smoothPositionMs) }
    val activeIndex = currentIndex.coerceIn(0, lyrics.lastIndex)
    val scrollTargetIndex = activeInterlude?.let { interlude ->
        interlude.nextLineIndex + interludes.count { it.nextLineIndex < interlude.nextLineIndex }
    } ?: activeIndex + interludes.count { it.nextLineIndex <= activeIndex }
    LaunchedEffect(scrollTargetIndex, userDragging, deferAutoScroll, trailingLineHeightPx) {
        if (userDragging || deferAutoScroll) return@LaunchedEffect
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
            scrollSpring.snapTo(0f)
            hasPositionedScroll = true
            return@LaunchedEffect
        }

        // ConePlayer does not restart a fixed-duration list animation for each lyric. It changes
        // every row's spring target (damping 1.25, stiffness 200) and lets the retained velocity
        // carry the content into place. Drive the LazyColumn with the same overdamped spring and
        // correct the distance after variable-height rows have entered the viewport.
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

            val animationStart = scrollSpring.value
            var appliedValue = animationStart
            listState.scroll {
                scrollSpring.animateTo(
                    targetValue = animationStart + distance,
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
                        distance = (index - activeIndex).coerceIn(-4, 4),
                        userScrolling = userDragging || keepLinesSharp,
                        nonCurrentLineBlurEnabled = nonCurrentLineBlurEnabled,
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
