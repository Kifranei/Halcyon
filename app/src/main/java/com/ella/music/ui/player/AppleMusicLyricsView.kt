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
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
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
import com.ella.music.data.model.isSingingAt
import com.ella.music.data.model.LyricLine
import com.ella.music.data.parser.needsPhoneticAnnotation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.delay
import kotlin.math.abs

/** A lightweight player position getter, read only by the visible lyrics' frame loop. */
internal val LocalPlayerLyricPositionProvider =
    androidx.compose.runtime.staticCompositionLocalOf<(() -> Long)?> { null }

private fun Modifier.lyricViewportEdges(enabled: Boolean, scrollable: Boolean): Modifier = then(
    when {
        !enabled -> Modifier.clipToBounds()
        // Keep short transitions at the song header and playback controls, outside the perspective plane.
        scrollable -> Modifier.miniLyricsEdgeFeather(topEdgeHeight = 16.dp, bottomEdgeHeight = 16.dp)
        else -> Modifier.miniLyricsEdgeFeather()
    }
)

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
    /**
     * #727: when both are set, dragging the opening blank lyric row reports scrub positions
     * through this preview channel (null clears it) instead of live-seeking on every move;
     * [onOpeningLyricCommit] fires once with the final position when the gesture ends.
     */
    onOpeningLyricPreview: ((Long?) -> Unit)? = null,
    onOpeningLyricCommit: ((Long) -> Unit)? = null,
    topContentPadding: Dp = 72.dp,
    bottomContentPadding: Dp = 132.dp,
    lineSpacing: Dp = 25.dp,
    focusOffsetRatio: Float = 0.24f,
    focusOffsetNudgeDp: Dp = 0.dp,
    focusOffsetDp: Dp? = null,
    useFocusLeadingPadding: Boolean = true,
    nonCurrentLineBlurEnabled: Boolean = true,
    edgeFeatherEnabled: Boolean = true,
    perspectiveEffectEnabled: Boolean = false,
    perspectiveYAngle: Int = 25,
    userScrollEnabled: Boolean = true,
    reserveExtraLyricSpace: Boolean = false,
    singleLine: Boolean = false,
    followWordFocus: Boolean = false,
    showBackgroundText: Boolean = true,
    linePresentation: ((Int, LyricLine) -> MiniLyricLinePresentation?)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Seed the collectors from the resident player's already-loaded values. These pages enter
    // composition on demand, and a cold DataStore collector only delivers a frame later.
    val residentPreferences = LocalAppleMusicLyricsViewPreferences.current
    val sustainMotion by remember(context) { com.ella.music.data.SettingsManager.getInstance(context).lyricSustainMotion }
        .collectAsState(initial = residentPreferences?.sustainMotion ?: true)
    val sustainTailMode by remember(context) { com.ella.music.data.SettingsManager.getInstance(context).appleMusicLyricsSustainTailMode }
        .collectAsState(initial = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_TAIL_MODE)
    val pronunciationBelow by remember(context) { SettingsManager.getInstance(context).lyricPronunciationBelow }
        .collectAsState(initial = residentPreferences?.pronunciationBelow ?: false)
    val dimmedOpacityTenths by remember(context) { SettingsManager.getInstance(context).playerLyricDimmedOpacity }
        .collectAsState(initial = residentPreferences?.dimmedOpacityTenths ?: SettingsManager.DEFAULT_PLAYER_LYRIC_DIMMED_OPACITY)
    val unsungLineOpacityTenths by remember(context) { SettingsManager.getInstance(context).playerLyricUnsungOpacity }
        .collectAsState(initial = residentPreferences?.unsungOpacityTenths ?: SettingsManager.DEFAULT_PLAYER_LYRIC_UNSUNG_OPACITY)
    val blockAlign by remember(context) { SettingsManager.getInstance(context).playerLyricBlockAlign }
        .collectAsState(initial = residentPreferences?.blockAlign ?: SettingsManager.PLAYER_LYRIC_BLOCK_ALIGN_TOP)
    val fauxWordTimingEnabled by remember(context) { SettingsManager.getInstance(context).playerLyricFauxWordTiming }
        .collectAsState(initial = residentPreferences?.fauxWordTiming ?: false)
    // #781 「逐行歌词逐字化」: line-timed rows can be swept like real karaoke once each line
    // carries synthesized per-word timings.
    val renderedLyrics = remember(lyrics, fauxWordTimingEnabled) {
        if (fauxWordTimingEnabled) lyrics.withPlayerFauxWordTiming() else lyrics
    }
    val sustainThresholdMs by remember(context) {
        SettingsManager.getInstance(context).appleMusicLyricsSustainThresholdMs
    }.collectAsState(initial = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS)
    val nonCurrentLineBlurPercent by remember(context) {
        SettingsManager.getInstance(context).lyricNonCurrentBlurPercent
    }.collectAsState(initial = residentPreferences?.nonCurrentLineBlurPercent ?: 40)
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
    }.collectAsState(initial = residentPreferences?.pauseCurrentOnly ?: true)
    val revealAllLinesWhilePaused = brightenAllLinesWhenPaused ?: !pauseCurrentOnly
    if (renderedLyrics.isEmpty()) {
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
    val singleTimestampTimeline = renderedLyrics.firstOrNull()?.timeMs?.let { timestamp ->
        renderedLyrics.all { it.timeMs == timestamp }
    } == true
    if (singleTimestampTimeline) {
        Box(modifier = modifier.fillMaxSize().lyricViewportEdges(edgeFeatherEnabled, userScrollEnabled).playerLyricPerspective(perspectiveEffectEnabled, perspectiveYAngle, lyricTextAlign), contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(lineSpacing),
                modifier = Modifier.fillMaxWidth()
            ) {
                renderedLyrics.forEachIndexed { index, line ->
                    val presentation = linePresentation?.invoke(index, line)
                    val openingScrub = line.openingScrubHandlers(onOpeningLyricPreview, onOpeningLyricCommit)
                    AppleMusicLyricLine(
                        line = line,
                        active = true,
                        paused = true,
                        distance = 0,
                        userScrolling = true,
                        nonCurrentLineBlurEnabled = false,
                        currentPositionMs = Long.MIN_VALUE,
                        showTranslation = presentation?.showTranslation ?: showTranslation,
                        showPronunciation = presentation?.showPronunciation ?: showPronunciation,
                        pronunciationBelow = pronunciationBelow,
                        inactiveOpacityBase = dimmedOpacityTenths / 1000f,
                        untimedLineAlpha = unsungLineOpacityTenths / 1000f,
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
                        showBackgroundText = presentation?.showBackgroundText ?: showBackgroundText,
                        showPrimaryText = presentation?.showPrimaryText ?: true,
                        onClick = { onLineClick(line) },
                        onDoubleClick = effectiveLineDoubleClick,
                        onLongClick = { onLineLongClick(line) },
                        onWordClick = if (wordSeekEnabled && !line.isOpeningMetadata) {
                            { positionMs -> onLineClick(line.copy(timeMs = positionMs)) }
                        } else null,
                        onTapFraction = openingScrub?.onFraction ?: line.openingSeekHandler(onLineClick),
                        onDragEnd = openingScrub?.onRelease,
                        touchFeedbackEnabled = touchFeedbackEnabled
                    )
                }
            }
        }
        return
    }

    val interludes = remember(renderedLyrics) { renderedLyrics.interludes() }
    val backingSpans = remember(renderedLyrics) { backingVocalSpans(renderedLyrics) }
    val initialActiveIndex = currentIndex.coerceIn(0, renderedLyrics.lastIndex)
    val initialActiveInterlude = interludes.firstOrNull { it.isActiveAt(currentPositionMs) }
    fun hasVisibleBackground(index: Int): Boolean {
        val line = renderedLyrics.getOrNull(index) ?: return false
        val presentation = linePresentation?.invoke(index, line)
        return showBackgroundText &&
            (presentation?.showBackgroundText ?: true) &&
            line.text.isNotBlank() &&
            !line.backgroundText.isNullOrBlank()
    }
    val initialBackgroundFocusIndex = activeBackingVocalIndex(backingSpans, initialActiveIndex, currentPositionMs)
        ?.takeIf { hasVisibleBackground(it) }
    val initialScrollTargetIndex = resolveAppleMusicLyricsScrollTargetIndex(
        activeLyricIndex = initialActiveIndex,
        activeInterlude = initialActiveInterlude,
        interludes = interludes,
        backgroundFocusLineIndex = initialBackgroundFocusIndex
    )
    // Start at the currently playing row. Waiting for the first post-layout effect while the
    // state still points at item 0 makes the lyric page flash the beginning of the song first.
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollTargetIndex
    )
    val userDragging by listState.interactionSource.collectIsDraggedAsState()
    var trailingLineHeightPx by remember(renderedLyrics) { mutableIntStateOf(0) }
    var hasPositionedScroll by remember(renderedLyrics) { mutableStateOf(false) }
    // #781 歌词行垂直位置: measured row-block heights let the center/bottom/main-lyric anchors
    // land exactly; an unmeasured far target falls back to the row-height estimate below.
    val lyricBlockHeightsPx = remember(renderedLyrics) { mutableStateMapOf<Int, Int>() }
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
    val renderIsPlaying = isPlaying && pageVisible
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
    val smoothPositionState = remember { mutableLongStateOf(currentPositionMs) }
    var smoothPositionMs by smoothPositionState
    val latestCurrentPositionMs by rememberUpdatedState(currentPositionMs)
    val latestLivePositionProvider by rememberUpdatedState(LocalPlayerLyricPositionProvider.current)
    val latestPlaying by rememberUpdatedState(renderIsPlaying)

    LaunchedEffect(currentPositionMs, pageVisible, renderIsPlaying) {
        if (!pageVisible || !renderIsPlaying) {
            smoothPositionMs = currentPositionMs
        }
    }

    LaunchedEffect(currentIndex) {
        val line = renderedLyrics.getOrNull(currentIndex) ?: return@LaunchedEffect
        val startMs = line.timeMs
        val endMs = line.endMs
            ?: line.words.maxOfOrNull { it.endMs }
            ?: line.backgroundEndMs
            ?: (startMs + 4_000L)
        val sampled = latestLivePositionProvider?.invoke() ?: latestCurrentPositionMs
        smoothPositionMs = when {
            sampled in startMs until endMs.coerceAtLeast(startMs + 1L) -> sampled
            smoothPositionMs < startMs -> startMs
            else -> smoothPositionMs
        }
    }
    // Keep one frame-clock loop for the lifetime of this lyric list. Keying it on the 10 Hz
    // player sample (or word-lift) cancelled interpolation every tick and made the karaoke
    // fill jump like a slideshow.
    LaunchedEffect(renderedLyrics, pageVisible, renderIsPlaying) {
        // A retained/paused lyrics page must not keep a frame-clock coroutine alive. Snap to the
        // latest sample so reopening starts from the correct line, then let the active playing
        // page resume the smooth karaoke clock below.
        if (!pageVisible || !renderIsPlaying) {
            smoothPositionMs = latestCurrentPositionMs
            return@LaunchedEffect
        }
        var lastFrameNs = 0L
        while (true) {
            val frameNs = withFrameNanos { it }
            // PlayerScreen's progress value is throttled to 250 ms for ordinary UI. Using it
            // with a 150 ms interpolation ceiling freezes the sweep between every two ticks.
            // MediaController's getter maintains its own real-time position (including speed,
            // buffering and seeks); this does not run the ViewModel's heavyweight update loop.
            val livePosition = latestLivePositionProvider?.invoke()
            val sampled = livePosition ?: latestCurrentPositionMs
            val playing = latestPlaying
            if (lastFrameNs == 0L) {
                lastFrameNs = frameNs
                smoothPositionMs = sampled
                continue
            }
            val dtMs = (frameNs - lastFrameNs) / 1_000_000L
            lastFrameNs = frameNs
            smoothPositionMs = lyricFramePositionMs(
                livePositionMs = livePosition,
                displayMs = smoothPositionMs,
                sampledMs = sampled,
                frameDeltaMs = dtMs,
                playing = playing
            )
        }
    }
    // Reading the frame clock in this composable's own body recomposed the whole lyric view --
    // and rebuilt the LazyColumn's interval content for every line of the song -- on every one
    // of the 120 ticks a second. Derive the interlude instead: it changes a handful of times per
    // song, and the per-frame reads below now sit inside the item bodies that actually need them.
    val activeInterlude by remember(interludes) {
        derivedStateOf { interludes.firstOrNull { it.isActiveAt(smoothPositionMs) } }
    }
    val activeIndex = currentIndex.coerceIn(0, renderedLyrics.lastIndex)
    val backgroundFocusIndex by remember(backingSpans, activeIndex, showBackgroundText, linePresentation) {
        derivedStateOf {
            activeBackingVocalIndex(backingSpans, activeIndex, smoothPositionMs)?.takeIf { hasVisibleBackground(it) }
        }
    }
    val scrollTargetIndex = resolveAppleMusicLyricsScrollTargetIndex(
        activeLyricIndex = activeIndex,
        activeInterlude = activeInterlude,
        interludes = interludes,
        backgroundFocusLineIndex = backgroundFocusIndex
    )
    val density = LocalDensity.current
    val focusOffsetNudgePx = with(density) { focusOffsetNudgeDp.toPx() }
    val focusOffsetPx = focusOffsetDp?.let { with(density) { it.toPx() } }
    val autoScrollShift = remember(listState) { LyricAutoScrollShift() }
    LaunchedEffect(
        pageVisible, scrollTargetIndex, userDragging, deferAutoScroll, focusOffsetNudgePx,
        focusOffsetPx, focusOffsetRatio, blockAlign, lyricBlockHeightsPx[scrollTargetIndex]
    ) {
        if (!pageVisible || userDragging || deferAutoScroll) return@LaunchedEffect
        // Do not issue the first scroll before LazyColumn has a viewport; that was making the
        // focus line land under the page header until the user manually scrolled.
        val viewportHeight = snapshotFlow {
            listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset
        }.filter { it > 0 }.first()
        val focusAnchorPx = if (focusOffsetPx != null) {
            focusOffsetPx - focusOffsetNudgePx
        } else {
            viewportHeight * focusOffsetRatio - focusOffsetNudgePx
        }
        // #781 歌词行垂直位置: the anchor inside the current row-block (top/center/bottom/main
        // lyric center) shifts where the item top must land on the focus line.
        val anchorInsidePx = if (blockAlign == SettingsManager.PLAYER_LYRIC_BLOCK_ALIGN_TOP) {
            0f
        } else {
            val targetLine = renderedLyrics.getOrNull(scrollTargetIndex)
            val secondaryRowPx = estimatePlayerLyricSecondaryRowPx(
                density, secondaryTextSizeSp, fontScale, secondaryFontScale
            )
            val primaryRowPx = estimatePlayerLyricPrimaryRowPx(density, primaryTextSizeSp, fontScale)
            val sections = targetLine?.let { target ->
                val targetPresentation = linePresentation?.invoke(scrollTargetIndex, target)
                estimatePlayerLyricBlockSectionsPx(
                    line = target,
                    showPronunciation = targetPresentation?.showPronunciation ?: showPronunciation,
                    showTranslation = targetPresentation?.showTranslation ?: showTranslation,
                    pronunciationBelow = pronunciationBelow,
                    showBackground = hasVisibleBackground(scrollTargetIndex),
                    secondaryRowPx = secondaryRowPx,
                    backgroundRowPx = secondaryRowPx
                )
            } ?: (0f to 0f)
            resolvePlayerLyricBlockAnchorPx(
                blockAlign = blockAlign,
                measuredBlockPx = lyricBlockHeightsPx[scrollTargetIndex],
                aboveSectionPx = sections.first,
                belowSectionPx = sections.second,
                primaryRowPx = primaryRowPx
            )
        }
        val desiredItemOffset = (focusAnchorPx - anchorInsidePx).coerceAtLeast(0f)

        if (!hasPositionedScroll) {
            // Initial positioning should not fly through the whole song when the player is
            // restored in the middle of a track.
            listState.scrollToItem(scrollTargetIndex, -desiredItemOffset.toInt())
            hasPositionedScroll = true
            return@LaunchedEffect
        }

        // Move the layout target once; each visible row follows with its own retained spring.
        // A single LazyColumn spring moves all lines as a rigid block and cannot produce the wave.
        repeat(LYRIC_SCROLL_CORRECTION_PASSES) {
            val info = listState.layoutInfo
            val items = info.visibleItemsInfo
            if (items.isEmpty()) return@repeat
            val target = items.firstOrNull { it.index == scrollTargetIndex }
            if (target == null) {
                listState.scrollToItem(scrollTargetIndex, -desiredItemOffset.toInt())
            } else {
                val distance = target.offset - desiredItemOffset
                if (abs(distance) <= LYRIC_SCROLL_VISIBILITY_THRESHOLD_PX) return@LaunchedEffect
                if (!listState.isScrollInProgress) {
                    autoScrollShift.record(distance)
                    listState.dispatchRawDelta(distance)
                }
            }
            androidx.compose.runtime.withFrameNanos { }
        }
    }
    val defaultTextAlign = when (lyricTextAlign) {
        SettingsManager.PLAYER_LYRIC_ALIGN_CENTER -> TextAlign.Center
        SettingsManager.PLAYER_LYRIC_ALIGN_RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize().lyricViewportEdges(edgeFeatherEnabled, userScrollEnabled).playerLyricPerspective(perspectiveEffectEnabled, perspectiveYAngle, lyricTextAlign)) {
        val trailingLineHeight = with(LocalDensity.current) { trailingLineHeightPx.toDp() }
        // The first lyric has no preceding rows that LazyColumn can scroll through. Reserve its
        // focus offset as actual leading content so 00:00 lyrics land at the same visual anchor
        // instead of sticking to the top edge of compact/immersive lyric viewports.
        val leadingFocusPadding = if (useFocusLeadingPadding) {
            resolveAppleMusicLyricsLeadingPadding(
                viewportHeight = maxHeight,
                focusOffsetRatio = focusOffsetRatio,
                focusOffsetNudge = focusOffsetNudgeDp,
                minimumTopPadding = topContentPadding,
                fixedFocusOffset = focusOffsetDp
            )
        } else {
            topContentPadding
        }
        // The mini preview is a bounded, non-scrollable line window. Adding enough trailing
        // padding to scroll the final row to the normal focus offset leaves a large blank tail
        // under the lyrics (and pushes the waveform/action area down). Only the full, scrollable
        // lyrics page needs that final-row affordance.
        val trailingFocusPadding = if (userScrollEnabled) {
            resolveAppleMusicLyricsTrailingPadding(
                viewportHeight = maxHeight,
                focusOffsetRatio = focusOffsetRatio,
                focusOffsetNudge = focusOffsetNudgeDp,
                trailingLineHeight = trailingLineHeight,
                minimumBottomPadding = bottomContentPadding,
                fixedFocusOffset = focusOffsetDp
            )
        } else {
            bottomContentPadding
        }
        androidx.compose.runtime.CompositionLocalProvider(
            LocalReferenceLyricMotion provides sustainMotion,
            LocalLyricSustainTailExtended provides (sustainTailMode != SettingsManager.APPLE_MUSIC_LYRICS_SUSTAIN_TAIL_IMMEDIATE)
        ) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = leadingFocusPadding, bottom = trailingFocusPadding),
            verticalArrangement = Arrangement.spacedBy(lineSpacing),
            userScrollEnabled = userScrollEnabled,
            modifier = Modifier.fillMaxSize()
        ) {
            renderedLyrics.forEachIndexed { index, line ->
                interludes.firstOrNull { it.nextLineIndex == index }?.let { interlude ->
                    item(key = "interlude-${interlude.startMs}-${interlude.endMs}") {
                        Box(Modifier.referenceLyricRowMotion(
                            targetY = { listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                it.key == "interlude-${interlude.startMs}-${interlude.endMs}"
                            }?.offset?.toFloat() },
                            enabled = pageVisible && !userDragging && !listState.isScrollInProgress,
                            distance = index - activeIndex,
                            maxTravelPx = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset).toFloat(),
                            enterShift = autoScrollShift
                        )) {
                        AppleMusicInterlude(
                            interlude = interlude,
                            positionMs = smoothPositionMs,
                            contentColor = contentColor,
                            textAlign = line.duetTextAlign(defaultTextAlign),
                            touchFeedbackEnabled = touchFeedbackEnabled,
                            onSeek = { positionMs ->
                                onLineClick(LyricLine(timeMs = positionMs, text = ""))
                            }
                        )
                        }
                    }
                }
                item(key = "${line.timeMs}-$index") {
                    val followingLine = remember(renderedLyrics, index) {
                        (index + 1 until renderedLyrics.size).firstOrNull { renderedLyrics[it].timeMs > line.timeMs }?.let(renderedLyrics::get)
                    }
                    val simultaneous = !line.isTtml && (line.words.isNotEmpty() ||
                        renderedLyrics.getOrNull(index - 1)?.timeMs == line.timeMs || renderedLyrics.getOrNull(index + 1)?.timeMs == line.timeMs)
                    val duetActive by remember(line, followingLine, simultaneous) {
                        derivedStateOf {
                            (line.isTtml || line.isDuetLine() || simultaneous) &&
                                line.isSingingAt(smoothPositionMs, nextLine = followingLine)
                        }
                    }
                    val lineIsActive = activeInterlude == null && (index == activeIndex || duetActive)
                    val presentation = linePresentation?.invoke(index, line)
                    val openingScrub = line.openingScrubHandlers(onOpeningLyricPreview, onOpeningLyricCommit)
                    AppleMusicLyricLine(
                        line = line,
                        active = lineIsActive,
                        paused = isPaused && revealAllLinesWhilePaused,
                        distance = (index - activeIndex).coerceIn(-4, 4),
                        userScrolling = userDragging || keepLinesSharp,
                        // Compact previews soften only at their viewport edges. A row's timeline
                        // distance must not blur it while it is still inside the visible area.
                        nonCurrentLineBlurEnabled = nonCurrentLineBlurEnabled && isPlaying && userScrollEnabled,
                        nonCurrentLineBlurPercent = nonCurrentLineBlurPercent,
                        // Do not invalidate every retained LazyColumn row for every playback tick.
                        // Only the active (or simultaneous duet) line needs a changing karaoke position.
                        currentPositionMs = Long.MIN_VALUE,
                        currentPositionState = smoothPositionState.takeIf { lineIsActive },
                        showTranslation = presentation?.showTranslation ?: showTranslation,
                        showPronunciation = presentation?.showPronunciation ?: showPronunciation,
                        pronunciationBelow = pronunciationBelow,
                        inactiveOpacityBase = dimmedOpacityTenths / 1000f,
                        untimedLineAlpha = unsungLineOpacityTenths / 1000f,
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
                        showBackgroundText = presentation?.showBackgroundText ?: showBackgroundText,
                        showPrimaryText = presentation?.showPrimaryText ?: true,
                        onClick = { onLineClick(line) },
                        onDoubleClick = effectiveLineDoubleClick,
                        onLongClick = { onLineLongClick(line) },
                        onWordClick = if (wordSeekEnabled && !line.isOpeningMetadata) {
                            { positionMs -> onLineClick(line.copy(timeMs = positionMs)) }
                        } else null,
                        onTapFraction = openingScrub?.onFraction ?: line.openingSeekHandler(onLineClick),
                        onDragEnd = openingScrub?.onRelease,
                        touchFeedbackEnabled = touchFeedbackEnabled,
                        modifier = Modifier.onSizeChanged { size ->
                            if (index == renderedLyrics.lastIndex) trailingLineHeightPx = size.height
                            if (lyricBlockHeightsPx[index] != size.height) {
                                lyricBlockHeightsPx[index] = size.height
                            }
                        }.referenceLyricRowMotion(
                            targetY = {
                                listState.layoutInfo.visibleItemsInfo.firstOrNull {
                                    it.key == "${line.timeMs}-$index"
                                }?.offset?.toFloat()
                            },
                            enabled = pageVisible && !userDragging && !listState.isScrollInProgress,
                            distance = index - activeIndex,
                            maxTravelPx = (listState.layoutInfo.viewportEndOffset - listState.layoutInfo.viewportStartOffset).toFloat(),
                            enterShift = autoScrollShift
                        )
                    )
                }
            }
        }
        }
    }
}

/**
 * [AppleMusicLyricsView]'s own display preferences, collected once by the resident PlayerScreen.
 *
 * The full lyric pages (immersive-cover lyrics, the Apple Music lyrics session) enter composition
 * only when opened, and each view's cold DataStore collector delivers the stored value one frame
 * late. With `lyricSustainMotion` off, that first frame rendered every inactive row at the
 * reference 0.98 scale before animating it down to 0.91 -- the lyrics visibly jumped size on
 * entry. Seeding the collectors from here keeps the first frame on the stored values.
 */
internal data class AppleMusicLyricsViewPreferences(
    val sustainMotion: Boolean,
    val pronunciationBelow: Boolean,
    val nonCurrentLineBlurPercent: Int,
    val pauseCurrentOnly: Boolean,
    val dimmedOpacityTenths: Int,
    val focusPosition: Int,
    val blockAlign: Int,
    val unsungOpacityTenths: Int,
    val fauxWordTiming: Boolean
)

internal val LocalAppleMusicLyricsViewPreferences =
    compositionLocalOf<AppleMusicLyricsViewPreferences?> { null }

@Composable
internal fun rememberAppleMusicLyricsViewPreferences(
    settingsManager: SettingsManager
): AppleMusicLyricsViewPreferences? {
    val flow = remember(settingsManager) {
        combine(
            listOf<Flow<Any>>(
                settingsManager.lyricSustainMotion,
                settingsManager.lyricPronunciationBelow,
                settingsManager.lyricNonCurrentBlurPercent,
                settingsManager.lyricPauseCurrentOnly,
                settingsManager.playerLyricDimmedOpacity,
                settingsManager.playerLyricFocusPosition,
                settingsManager.playerLyricBlockAlign,
                settingsManager.playerLyricUnsungOpacity,
                settingsManager.playerLyricFauxWordTiming
            )
        ) { values ->
            AppleMusicLyricsViewPreferences(
                sustainMotion = values[0] as Boolean,
                pronunciationBelow = values[1] as Boolean,
                nonCurrentLineBlurPercent = values[2] as Int,
                pauseCurrentOnly = values[3] as Boolean,
                dimmedOpacityTenths = values[4] as Int,
                focusPosition = values[5] as Int,
                blockAlign = values[6] as Int,
                unsungOpacityTenths = values[7] as Int,
                fauxWordTiming = values[8] as Boolean
            )
        }
    }
    val preferences by flow.collectAsState(initial = null)
    return preferences
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

/**
 * #727: paired scrub callbacks for the opening metadata row. `onFraction` previews each
 * position through the player seek bar; `onRelease` commits the last previewed position once
 * and clears the preview. The shared `lastDragMs` lives in the closure — a lyric set holds at
 * most one opening row and both callbacks always run on the gesture thread.
 */
internal class OpeningLyricScrub(
    val onFraction: (Float) -> Unit,
    val onRelease: () -> Unit
)

private fun LyricLine.openingScrubHandlers(
    onPreview: ((Long?) -> Unit)?,
    onCommit: ((Long) -> Unit)?
): OpeningLyricScrub? {
    if (onPreview == null || onCommit == null) return null
    if (resolveOpeningLyricSeekPosition(this, 0f) == null) return null
    var lastDragMs: Long? = null
    return OpeningLyricScrub(
        onFraction = { fraction ->
            resolveOpeningLyricSeekPosition(this, fraction)?.let { positionMs ->
                lastDragMs = positionMs
                onPreview(positionMs)
            }
        },
        onRelease = {
            lastDragMs?.let(onCommit)
            lastDragMs = null
            onPreview(null)
        }
    )
}

internal fun resolveOpeningLyricSeekPosition(line: LyricLine, fraction: Float): Long? {
    val endMs = line.endMs?.takeIf { line.isOpeningMetadata && it > line.timeMs } ?: return null
    return line.timeMs + ((endMs - line.timeMs) * fraction.coerceIn(0f, 1f)).toLong()
}

internal fun resolveAppleMusicLyricsLeadingPadding(
    viewportHeight: Dp,
    focusOffsetRatio: Float,
    minimumTopPadding: Dp,
    focusOffsetNudge: Dp = 0.dp,
    fixedFocusOffset: Dp? = null
): Dp = maxOf(
    minimumTopPadding,
    ((fixedFocusOffset ?: (viewportHeight * focusOffsetRatio.coerceIn(0f, 1f))) - focusOffsetNudge).coerceAtLeast(0.dp)
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
    focusOffsetNudge: Dp = 0.dp,
    fixedFocusOffset: Dp? = null
): Dp {
    val offset = fixedFocusOffset ?: (viewportHeight * focusOffsetRatio.coerceIn(0f, 1f))
    val requiredPadding = (
        viewportHeight - offset + focusOffsetNudge - trailingLineHeight
    ).coerceAtLeast(0.dp)
    return maxOf(minimumBottomPadding, requiredPadding)
}

/**
 * #781 歌词行垂直位置: single-line height of one stacked secondary slot (pronunciation /
 * translation), matching [AppleMusicLyricLine]'s secondary style — 1.28 line-height plus the
 * stacked-row spacing that rides along as padding.
 */
internal fun estimatePlayerLyricSecondaryRowPx(
    density: androidx.compose.ui.unit.Density,
    secondaryTextSizeSp: Float,
    fontScale: Float,
    secondaryFontScale: Float
): Float = with(density) {
    (secondaryTextSizeSp * fontScale * secondaryFontScale * 1.28f).sp.toPx() +
        AppleMusicLyricSecondaryRowSpacing.toPx()
}

/** Single-line primary row height, matching the player list's 1.18 line-height factor. */
internal fun estimatePlayerLyricPrimaryRowPx(
    density: androidx.compose.ui.unit.Density,
    primaryTextSizeSp: Float,
    fontScale: Float
): Float = with(density) { (primaryTextSizeSp * fontScale * 1.18f).sp.toPx() }

/**
 * Estimated height of the secondary sections above and below [line]'s primary row, mirroring
 * [AppleMusicLyricLine]'s layout order: a stacked pronunciation row sits above the original
 * unless [pronunciationBelow] moves it under, inline ruby lives inside the primary row, and
 * the translation and active backing-vocal sections always sit below.
 *
 * @return above-section px to below-section px.
 */
internal fun estimatePlayerLyricBlockSectionsPx(
    line: LyricLine,
    showPronunciation: Boolean,
    showTranslation: Boolean,
    pronunciationBelow: Boolean,
    showBackground: Boolean,
    secondaryRowPx: Float,
    backgroundRowPx: Float
): Pair<Float, Float> {
    val pronunciation = line.pronunciation.orEmpty()
    var abovePx = 0f
    var belowPx = 0f
    if (showPronunciation && pronunciation.isNotBlank()) {
        val annotatableBase = line.text.ifBlank { line.backgroundText.orEmpty() }
            .needsPhoneticAnnotation()
        val timedKanaReading = line.pronunciationWords.any { it.endMs > it.startMs } &&
            line.pronunciationWords.all { isInlineRubyPronunciation(it.text) }
        val inlineRuby = (annotatableBase || timedKanaReading) &&
            (line.pronunciationWords.isNotEmpty() || isInlineRubyPronunciation(pronunciation))
        if (!inlineRuby) {
            if (pronunciationBelow) belowPx += secondaryRowPx else abovePx += secondaryRowPx
        }
    }
    if (showTranslation && !line.translation.isNullOrBlank()) belowPx += secondaryRowPx
    if (showBackground) {
        belowPx += backgroundRowPx *
            if (showTranslation && !line.backgroundTranslation.isNullOrBlank()) 2f else 1f
    }
    return abovePx to belowPx
}

/**
 * Distance from the current row-block's top edge to the anchor point selected by
 * [blockAlign]. A measured block height wins over the single-line estimate so wrapped rows
 * still center correctly; for [SettingsManager.PLAYER_LYRIC_BLOCK_ALIGN_MAIN] the primary
 * row's center is block-center shifted by half the above/below section imbalance — exactly
 * `(pronunciation height - translation height) / 2` when one stacked row sits on each side.
 */
internal fun resolvePlayerLyricBlockAnchorPx(
    blockAlign: Int,
    measuredBlockPx: Int?,
    aboveSectionPx: Float,
    belowSectionPx: Float,
    primaryRowPx: Float
): Float {
    val measuredPx = measuredBlockPx?.takeIf { it > 0 }?.toFloat()
    val blockPx = measuredPx ?: (aboveSectionPx + primaryRowPx + belowSectionPx)
    return when (blockAlign) {
        SettingsManager.PLAYER_LYRIC_BLOCK_ALIGN_CENTER -> blockPx / 2f
        SettingsManager.PLAYER_LYRIC_BLOCK_ALIGN_BOTTOM -> blockPx
        SettingsManager.PLAYER_LYRIC_BLOCK_ALIGN_MAIN ->
            measuredPx?.let { it / 2f + (aboveSectionPx - belowSectionPx) / 2f }
                ?: (aboveSectionPx + primaryRowPx / 2f)
        else -> 0f
    }
}

internal fun resolveAppleMusicLyricsScrollTargetIndex(
    activeLyricIndex: Int,
    activeInterlude: AppleMusicInterlude?,
    interludes: List<AppleMusicInterlude>,
    backgroundFocusLineIndex: Int? = null
): Int {
    activeInterlude?.let { interlude ->
        return interlude.nextLineIndex + interludes.count { it.nextLineIndex < interlude.nextLineIndex }
    }
    val sourceIndex = backgroundFocusLineIndex ?: activeLyricIndex
    return sourceIndex + interludes.count { it.nextLineIndex <= sourceIndex }
}

/** Use the live controller clock when available; retained/non-player views keep safe interpolation. */
internal fun lyricFramePositionMs(
    livePositionMs: Long?,
    displayMs: Long,
    sampledMs: Long,
    frameDeltaMs: Long,
    playing: Boolean,
    seekThresholdMs: Long = 1_500L,
    backwardToleranceMs: Long = PLAYER_POSITION_BACKWARD_DRIFT_TOLERANCE_MS
): Long {
    val live = livePositionMs?.coerceAtLeast(0L)
    if (live != null) {
        if (!playing) return live
        // Real seeks land far from the last authoritative sample; anything within the window
        // is engine jitter. Clamp it so a stuttering live read cannot rewind the karaoke
        // sweep by more than the drift tolerance (#727).
        if (abs(live - sampledMs) > seekThresholdMs) return live
        return live.coerceAtLeast(displayMs - backwardToleranceMs)
    }
    return nextSmoothLyricPositionMs(
        displayMs = displayMs,
        sampledMs = sampledMs,
        frameDeltaMs = frameDeltaMs,
        playing = playing
    )
}

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
        // A late/stalled sample must not let the render clock sing future words.
        // Keep at most one sampling interval plus scheduling tolerance of interpolation.
        delta < 0L -> predicted.coerceAtMost(sampledMs + backwardToleranceMs.coerceIn(0L, 150L))
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
private const val LYRIC_SCROLL_VISIBILITY_THRESHOLD_PX = 0.75f
private const val LYRIC_SCROLL_CORRECTION_PASSES = 2
