// Adapted from RawS Music, Copyright 2024–2026 RawSMusic Contributors, Apache-2.0.
package com.ella.music.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.Song
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.RawWaveformCache
import com.ella.music.data.SettingsManager

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class ImmersiveProgressStyle(val value: Int) {
    Classic(0),
    Waveform(1),
    Seconds(2),
    MusicSpine(3);

    companion object {
        fun from(value: Int): ImmersiveProgressStyle = entries.firstOrNull { it.value == value } ?: Classic
    }
}

/**
 * reference player keeps the playing clock alive while a new track identity is being committed.  The
 * renderer may briefly publish the new duration/position before the play-state callback arrives;
 * that is a handoff, not a pause.  Keep the last stable playing state for this short boundary so
 * the waveform is not collapsed and expanded again on every track change.
 */
@Composable
private fun rememberTrackHandoffPlaying(
    trackKey: String,
    isPlaying: Boolean,
): Boolean {
    var observedTrackKey by remember { mutableStateOf(trackKey) }
    var observedPlaying by remember { mutableStateOf(isPlaying) }
    var handoffPlaying by remember { mutableStateOf(false) }
    val trackChanged = observedTrackKey != trackKey

    // Capture the previous track's state before replacing the observed identity.  This is a
    // composition boundary operation, not a second animation clock.
    SideEffect {
        if (trackChanged) {
            observedTrackKey = trackKey
            // Publish the handoff synchronously as well as from LaunchedEffect. A transport
            // callback can publish the new duration and a transient paused state in adjacent
            // frames; waiting for the coroutine to start made pauseMorph briefly collapse the
            // waveform on every track change.
            if (observedPlaying) handoffPlaying = true
        } else {
            observedPlaying = isPlaying
        }
    }

    LaunchedEffect(trackKey) {
        val keepClockAlive = trackChanged && observedPlaying
        handoffPlaying = keepClockAlive
        if (keepClockAlive) {
            delay(550L)
            if (observedTrackKey == trackKey) handoffPlaying = false
        }
    }

    // LaunchedEffect starts after this composition has committed. Include the previous playing
    // sample synchronously as well; otherwise the first frame of a transport commit sees the new
    // track's temporary paused flag and pauseMorph shrinks the whole bar for one vsync. reference player's
    // existing motion clock never enters a paused state on that identity boundary.
    return isPlaying || handoffPlaying || (trackChanged && observedPlaying)
}

/** Fixed-width timeline: one bar represents one second instead of compressing a whole track. */
@Composable
fun ImmersiveSecondProgressBar(
    currentSong: Song?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    colors: ImmersiveWaveformColors,
    onSeekStart: () -> Unit,
    onSeekStop: (Float) -> Unit,
    allowTapSeek: Boolean = true,
    onPreview: (Float?) -> Unit = {},
    horizontalExtension: Dp = 0.dp,
    modifier: Modifier = Modifier,
    scaleAnimationEnabled: Boolean = true,
    densityPercent: Int = SettingsManager.DEFAULT_PLAYER_WAVEFORM_DENSITY,
    peakHeightPercent: Int = SettingsManager.DEFAULT_PLAYER_WAVEFORM_PEAK_HEIGHT,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    var isDragging by remember { mutableStateOf(false) }
    var dragSecond by remember { mutableFloatStateOf(0f) }
    val densityValue = LocalDensity.current.density
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val effectiveDurationMs = totalDurationMs.takeIf { it > 0L }
        ?: currentSong?.duration?.takeIf { it > 0L }
        ?: 0L
    val totalSeconds = (effectiveDurationMs / 1000f).coerceAtLeast(1f)
    val currentSecond = (currentPositionMs / 1000f).coerceIn(0f, totalSeconds)
    val endSnapSeconds = min(1f, totalSeconds * 0.004f)
    val context = LocalContext.current.applicationContext
    val songMotionKey = remember(currentSong?.path, currentSong?.fileSize, currentSong?.dateModified, effectiveDurationMs) {
        "${currentSong?.path}|${currentSong?.fileSize}|${currentSong?.dateModified}|$effectiveDurationMs"
    }
    val waveformSongKey = remember(
        currentSong?.path,
        currentSong?.fileSize,
        currentSong?.dateModified,
        0,
        0,
        0
    ) {
        "${currentSong?.path}|${currentSong?.fileSize}|${currentSong?.dateModified}|" +
            "${0}|${0}|${0}"
    }
    val trackHandoffPlaying = rememberTrackHandoffPlaying(songMotionKey, isPlaying)
    val timelineSecond = if (trackHandoffPlaying && totalSeconds - currentSecond <= endSnapSeconds) {
        totalSeconds
    } else {
        currentSecond
    }
    var lastMotionKey by remember { mutableStateOf(songMotionKey) }
    var lastTargetSecond by remember { mutableFloatStateOf(timelineSecond) }
    val shouldSnapSecond = lastMotionKey != songMotionKey || abs(timelineSecond - lastTargetSecond) > 2.4f
    val settledSecond by animateFloatAsState(
        targetValue = timelineSecond,
        animationSpec = tween(
            durationMillis = if (shouldSnapSecond) 0 else if (trackHandoffPlaying) 150 else 180,
            easing = FastOutSlowInEasing
        ),
        label = "secondTimelineMotion"
    )
    SideEffect {
        lastMotionKey = songMotionKey
        lastTargetSecond = timelineSecond
    }
    // Pause preserves the decoded envelope; only the whole timeline scales to 95% (unless the
    // user disabled the scale animation, in which case it always stays at full size).
    val pauseMorph = 0f
    val animatedPauseScale by animateFloatAsState(
        targetValue = WaveformProgressTuning.pauseScaleTarget(
            scaleAnimationEnabled = scaleAnimationEnabled,
            playing = trackHandoffPlaying,
            dragging = isDragging,
        ),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "secondTimelinePauseScale"
    )
    // Density only changes the per-second pitch; the one-bucket-per-second PCM envelope is reused.
    val barStepDp = WaveformProgressTuning.secondBarStepDp(densityPercent)
    val barWidthDp = WaveformProgressTuning.secondBarWidthDp(densityPercent)
    val peakHeightScale = WaveformProgressTuning.peakHeightScale(peakHeightPercent)
    val needleAlpha by animateFloatAsState(
        targetValue = if (isDragging) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (isDragging) 180 else 140,
            easing = FastOutSlowInEasing
        ),
        label = "secondTimelineNeedle"
    )
    var pendingSeekSecond by remember(songMotionKey) { mutableStateOf<Float?>(null) }
    // One native PCM bucket maps to one displayed second, preserving the dynamics of long tracks.
    val waveformSampleCount = ceil(totalSeconds).toInt()
        .coerceIn(32, RawWaveformCache.MAX_SAMPLE_COUNT)
    val cachedPcmWaveform = remember(waveformSongKey, waveformSampleCount) {
        RawWaveformCache.tryReadCached(context, currentSong, waveformSampleCount)
    }
    // Never show another track's envelope while the new track is being scanned.
    var staticPcmWaveform by remember(waveformSongKey, waveformSampleCount) {
        mutableStateOf(cachedPcmWaveform ?: RawWaveformCache.placeholder(waveformSampleCount, style = 1))
    }
    val waveformRevision by RawWaveformCache.revision.collectAsState()
    LaunchedEffect(waveformSongKey, waveformSampleCount, waveformRevision) {
        RawWaveformCache.peekAvailable(currentSong, waveformSampleCount)?.let { staticPcmWaveform = it }
    }
    LaunchedEffect(waveformSongKey, waveformSampleCount, effectiveDurationMs) {
        if (cachedPcmWaveform != null) {
            staticPcmWaveform = cachedPcmWaveform
        } else {
            repeat(3) { attempt ->
                val result = withContext(Dispatchers.IO) {
                    RawWaveformCache.loadOrScanResult(context, currentSong, waveformSampleCount)
                }
                if (result.isReal) { staticPcmWaveform = result.values; return@LaunchedEffect }
                if (attempt < 2) delay(1500L * (attempt + 1))
            }
        }
    }
    LaunchedEffect(pendingSeekSecond, timelineSecond, songMotionKey) {
        val pending = pendingSeekSecond ?: return@LaunchedEffect
        if (abs(timelineSecond - pending) <= 0.18f) {
            pendingSeekSecond = null
        } else {
            delay(520)
            if (pendingSeekSecond == pending) pendingSeekSecond = null
        }
    }
    LaunchedEffect(songMotionKey) {
        pendingSeekSecond = null
    }
    val displaySecond = if (isDragging) {
        dragSecond
    } else {
        pendingSeekSecond ?: settledSecond
    }
    SideEffect { onPreview(if (isDragging) displaySecond / totalSeconds else null) }
    val latestDisplaySecond by rememberUpdatedState(displaySecond)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .extendTimelineHorizontally(horizontalExtension)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(76.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(effectiveDurationMs, widthPx, touchSlop, densityValue, barStepDp) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Main)
                        if (effectiveDurationMs <= 0L || widthPx <= 1) return@awaitEachGesture
                        val barStep = barStepDp * densityValue
                        val startSecond = latestDisplaySecond
                        val start = down.position
                        var lastPosition = start
                        var lastSecond = startSecond
                        var axis = PlayerTimelineGestureAxis.Undecided
                        var started = false
                        var finishedNormally = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                lastPosition = change.position
                                val deltaX = change.position.x - start.x
                                if (axis == PlayerTimelineGestureAxis.Undecided && change.isConsumed) {
                                    axis = PlayerTimelineGestureAxis.VerticalScene
                                }
                                if (axis == PlayerTimelineGestureAxis.Undecided) {
                                    axis = resolvePlayerTimelineGestureAxis(
                                        dx = deltaX,
                                        dy = change.position.y - start.y,
                                        touchSlop = touchSlop,
                                    )
                                    if (axis == PlayerTimelineGestureAxis.HorizontalSeek) {
                                        started = true
                                        isDragging = true
                                        dragSecond = startSecond
                                        onSeekStart()
                                    }
                                }
                                if (axis == PlayerTimelineGestureAxis.HorizontalSeek) {
                                    lastSecond = (startSecond - deltaX / barStep)
                                        .coerceIn(0f, totalSeconds)
                                    dragSecond = lastSecond
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    finishedNormally = true
                                    break
                                }
                            }
                        } finally {
                            if (started) {
                                pendingSeekSecond = lastSecond
                                isDragging = false
                                onSeekStop((lastSecond / totalSeconds).coerceIn(0f, 1f))
                            } else if (finishedNormally && allowTapSeek && axis == PlayerTimelineGestureAxis.Undecided) {
                                val tapSecond = resolveSecondTimelineTapSecond(
                                    currentSecond = startSecond,
                                    tapX = lastPosition.x,
                                    widthPx = widthPx.toFloat(),
                                    barStepPx = barStep,
                                    totalSeconds = totalSeconds,
                                )
                                pendingSeekSecond = tapSecond
                                onSeekStart()
                                onSeekStop((tapSecond / totalSeconds).coerceIn(0f, 1f))
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .graphicsLayer {
                        val pauseScale = if (scaleAnimationEnabled) animatedPauseScale else 1f
                        scaleX = pauseScale
                        scaleY = pauseScale
                    },
            ) {
            val step = (barStepDp - pauseMorph * 1.25f) * density
            val barWidth = (barWidthDp - pauseMorph * 0.55f) * density
            val capScale = max(1f, peakHeightScale)
            val heightCap = size.height * WaveformProgressTuning.peakCap(
                WaveformProgressTuning.SECOND_HEIGHT_CAP,
                peakHeightScale,
                WaveformProgressTuning.SECOND_MAX_HEIGHT_CAP,
            )
            val centerX = size.width * 0.5f
            val visible = (size.width / step).toInt() + 5
            val firstSecond = floor(displaySecond).toInt() - visible / 2
            val segmentCount = ceil(totalSeconds).toInt().coerceAtLeast(1)
            val waveform = staticPcmWaveform
            for (index in 0 until visible) {
                val second = firstSecond + index
                if (second !in 0 until segmentCount) continue
                val segmentEnd = min(second + 1f, totalSeconds)
                val segmentLength = (segmentEnd - second).coerceIn(0f, 1f)
                val segmentCenter = second + segmentLength * 0.5f
                val x = centerX + (segmentCenter - displaySecond) * step
                val waveformIndex = second.coerceIn(0, waveform.lastIndex)
                // Preserve actual RMS differences. A power curve suppresses low-energy
                // windows instead of lifting them into the same visual band as peaks.
                val realAmplitude = waveform[waveformIndex]
                    .coerceIn(0f, 1f)
                    .pow(2.15f)
                val amplitude = realAmplitude
                val liveHeight = (7.5f * density + amplitude * size.height * 0.88f * peakHeightScale)
                    .coerceAtMost(size.height * WaveformProgressTuning.SECOND_LIVE_HEIGHT_CAP * capScale)
                val idleHeight = (5.5f * density + amplitude * 7.0f * density).coerceAtMost(size.height * 0.26f)
                val height = liveHeight * (1f - pauseMorph) + idleHeight * pauseMorph
                val edgeAlpha = edgeFade(x = x, width = size.width, fade = step * 1.9f)
                val activeAlpha = if (isDragging || trackHandoffPlaying) 1f else 0.68f
                val heightScale = 0.90f + 0.10f * edgeAlpha
                val easedHeight = (height * heightScale).coerceAtMost(heightCap)
                val segmentBarWidth = (barWidth * segmentLength.coerceAtLeast(0.35f)).coerceAtLeast(density)
                val left = x - segmentBarWidth * 0.5f
                val right = x + segmentBarWidth * 0.5f
                val top = (size.height - easedHeight) * 0.5f
                val radius = CornerRadius(segmentBarWidth * 0.5f, segmentBarWidth * 0.5f)
                val playedColor = colors.played.copy(alpha = colors.played.alpha * edgeAlpha * activeAlpha)
                val remainingColor = colors.remaining.copy(alpha = colors.remaining.alpha * edgeAlpha * activeAlpha)
                when {
                    centerX <= left -> {
                        drawRoundRect(
                            color = remainingColor,
                            topLeft = Offset(left, top),
                            size = Size(segmentBarWidth, easedHeight),
                            cornerRadius = radius
                        )
                    }
                    centerX >= right -> {
                        drawRoundRect(
                            color = playedColor,
                            topLeft = Offset(left, top),
                            size = Size(segmentBarWidth, easedHeight),
                            cornerRadius = radius
                        )
                    }
                    else -> {
                        clipRect(left = left, top = 0f, right = centerX, bottom = size.height) {
                            drawRoundRect(
                                color = playedColor,
                                topLeft = Offset(left, top),
                                size = Size(segmentBarWidth, easedHeight),
                                cornerRadius = radius
                            )
                        }
                        clipRect(left = centerX, top = 0f, right = right, bottom = size.height) {
                            drawRoundRect(
                                color = remainingColor,
                                topLeft = Offset(left, top),
                                size = Size(segmentBarWidth, easedHeight),
                                cornerRadius = radius
                            )
                        }
                    }
                }
            }
            if (needleAlpha > 0.001f) {
                val needleWidth = (1.42f + needleAlpha * 0.34f) * density
                val needleHeight = size.height * 1.34f
                val needleTop = (size.height - needleHeight) * 0.5f
                drawRoundRect(
                    color = colors.needle.copy(alpha = 0.98f * needleAlpha),
                    topLeft = Offset(centerX - needleWidth * 0.5f, needleTop),
                    size = Size(needleWidth, needleHeight),
                    cornerRadius = CornerRadius(needleWidth, needleWidth)
                )
            }
        }
        }

    }
}

private fun Modifier.extendTimelineHorizontally(extension: Dp): Modifier {
    if (extension <= 0.dp) return this
    return layout { measurable, constraints ->
        val extraPx = extension.roundToPx().coerceAtLeast(0)
        val viewportWidth = constraints.maxWidth.coerceAtLeast(constraints.minWidth)
        val expandedWidth = (viewportWidth + extraPx * 2).coerceAtLeast(viewportWidth)
        val placeable = measurable.measure(
            constraints.copy(minWidth = expandedWidth, maxWidth = expandedWidth)
        )
        // The old implementation reported the expanded child width to the parent and then placed
        // that same child at -extraPx. Inside the immersive player's centred/padded column this
        // changed the layout coordinate system itself, so the seconds timeline was effectively
        // shifted left and one edge could be clipped. Keep the parent's viewport width stable and
        // let only the child paint symmetrically into the surrounding 30dp immersive padding.
        layout(viewportWidth, placeable.height) {
            placeable.place(-extraPx, 0)
        }
    }
}

private fun edgeFade(x: Float, width: Float, fade: Float): Float {
    if (fade <= 0f) return 1f
    val left = (x / fade).coerceIn(0f, 1f)
    val right = ((width - x) / fade).coerceIn(0f, 1f)
    val raw = min(left, right)
    return raw * raw * (3f - 2f * raw)
}

internal data class ImmersiveClimaxSegment(
    val startFraction: Float,
    val endFraction: Float,
    val confidence: Float
)

data class ImmersiveWaveformColors(
    val played: Color,
    val remaining: Color,
    val climaxPlayed: Color,
    val climaxRemaining: Color,
    val needle: Color,
    val time: Color
)

@Composable
internal fun ImmersiveWaveformProgressBar(
    currentSong: Song?,
    currentPositionMs: Long,
    totalDurationMs: Long,
    isPlaying: Boolean,
    colors: ImmersiveWaveformColors,
    climaxEnabled: Boolean,
    waveformBarCount: Int = 160,
    onSeekStart: () -> Unit,
    onSeekStop: (Float) -> Unit,
    allowTapSeek: Boolean = true,
    onPreview: (Float?) -> Unit = {},
    modifier: Modifier = Modifier,
    barWidthDp: Float = 2.35f,
    gapDp: Float = 0.56f,
    scaleAnimationEnabled: Boolean = true,
    peakHeightPercent: Int = SettingsManager.DEFAULT_PLAYER_WAVEFORM_PEAK_HEIGHT,
) {
    var widthPx by remember { mutableIntStateOf(1) }
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val rawFraction = if (totalDurationMs > 0L) {
        currentPositionMs.toFloat() / totalDurationMs.toFloat()
    } else {
        0f
    }.coerceIn(0f, 1f)
    val endSnapFraction = if (totalDurationMs > 0L) {
        (min(1000L, (totalDurationMs * 0.004f).toLong()).toFloat() / totalDurationMs).coerceAtLeast(0f)
    } else {
        0f
    }
    val seedKey = remember(
        currentSong?.path,
        currentSong?.fileSize,
        currentSong?.dateModified,
        0,
        totalDurationMs,
    ) {
        buildString {
            append(currentSong?.path.orEmpty())
            append('|')
            append(currentSong?.fileSize ?: 0L)
            append('|')
            append(currentSong?.dateModified ?: 0L)
            append('|')
            append(0)
            append('|')
            append(totalDurationMs)
        }
    }
    val trackHandoffPlaying = rememberTrackHandoffPlaying(seedKey, isPlaying)
    val realFraction = if (trackHandoffPlaying && 1f - rawFraction <= endSnapFraction) 1f else rawFraction
    var pendingSeekFraction by remember(seedKey) { mutableStateOf<Float?>(null) }
    LaunchedEffect(pendingSeekFraction, realFraction, seedKey) {
        val pending = pendingSeekFraction ?: return@LaunchedEffect
        if (abs(realFraction - pending) <= 0.006f) {
            pendingSeekFraction = null
        } else {
            delay(520)
            if (pendingSeekFraction == pending) pendingSeekFraction = null
        }
    }
    LaunchedEffect(seedKey) {
        pendingSeekFraction = null
    }
    // Pause preserves the decoded envelope; only the whole timeline scales to 95% (unless the
    // user disabled the scale animation, in which case it always stays at full size).
    val pauseMorph = 0f
    val animatedPauseScale by animateFloatAsState(
        targetValue = WaveformProgressTuning.pauseScaleTarget(
            scaleAnimationEnabled = scaleAnimationEnabled,
            playing = trackHandoffPlaying,
            dragging = isDragging,
        ),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "immersiveWaveformPauseScale"
    )
    val peakHeightScale = WaveformProgressTuning.peakHeightScale(peakHeightPercent)
    val displayFraction = if (isDragging) {
        dragFraction
    } else {
        pendingSeekFraction ?: realFraction
    }

    SideEffect { onPreview(if (isDragging) displayFraction else null) }
    val context = LocalContext.current.applicationContext
    val waveformSongKey = remember(
        currentSong?.path,
        currentSong?.fileSize,
        currentSong?.dateModified,
        0,
        0,
        0
    ) {
        "${currentSong?.path}|${currentSong?.fileSize}|${currentSong?.dateModified}|" +
            "${0}|${0}|${0}"
    }
    // Climax analysis needs enough temporal resolution for beat/onset structure. Visible bar count
    // remains independent, so raising the offline analysis resolution does not make the UI denser.
    val analysisSampleCount = remember(totalDurationMs) {
        if (totalDurationMs > 0L) {
            (totalDurationMs / 100L).toInt().coerceIn(480, 3_600)
        } else {
            480
        }
    }
    val visibleWaveformBarCount = waveformBarCount.coerceIn(32, RawWaveformCache.MAX_SAMPLE_COUNT)
    val initialWaveform = remember(waveformSongKey, analysisSampleCount) {
        RawWaveformCache.tryReadCached(context, currentSong, analysisSampleCount)
    }
    var waveform by remember(waveformSongKey, analysisSampleCount) {
        mutableStateOf(initialWaveform ?: RawWaveformCache.placeholder(analysisSampleCount))
    }
    var previousWaveform by remember { mutableStateOf<FloatArray?>(null) }
    val waveformSwap = remember { Animatable(1f) }
    val waveformRevision by RawWaveformCache.revision.collectAsState()
    LaunchedEffect(waveformSongKey, analysisSampleCount, waveformRevision) {
        RawWaveformCache.peekAvailable(currentSong, analysisSampleCount)?.let { waveform = it }
    }
    LaunchedEffect(waveformSongKey, analysisSampleCount, totalDurationMs) {
        if (initialWaveform != null) return@LaunchedEffect
        var result: RawWaveformCache.LoadResult? = null
        for (attempt in 0..2) {
            result = withContext(Dispatchers.IO) { RawWaveformCache.loadOrScanResult(context, currentSong, analysisSampleCount) }
            if (result.isReal) break
            if (attempt < 2) delay(1500L * (attempt + 1))
        }
        val nextWaveform = result?.takeIf { it.isReal }?.values ?: return@LaunchedEffect
        previousWaveform = waveform
        waveform = nextWaveform
        waveformSwap.snapTo(0f)
        waveformSwap.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        )
        previousWaveform = null
    }
    val climaxSegments = remember(seedKey, waveform, totalDurationMs, climaxEnabled) {
        if (climaxEnabled) analyzeImmersiveClimax(
            peaks = waveform,
            durationMs = totalDurationMs,
            preferredBpm = 0,
        ).segments else emptyList()
    }

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp)
                .onSizeChanged { widthPx = it.width.coerceAtLeast(1) }
                .pointerInput(totalDurationMs, widthPx) {
                    awaitEachGesture {
                        val down = awaitFirstDown(
                            requireUnconsumed = false,
                            pass = PointerEventPass.Main
                        )
                        if (totalDurationMs <= 0L || widthPx <= 1) return@awaitEachGesture

                        val start = down.position
                        var lastPosition = start
                        var axis = PlayerTimelineGestureAxis.Undecided
                        var seekStarted = false
                        var lastFraction = (start.x / widthPx.toFloat()).coerceIn(0f, 1f)
                        var finishedNormally = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull()
                                    ?: break
                                lastPosition = change.position
                                if (axis == PlayerTimelineGestureAxis.Undecided && change.isConsumed) {
                                    axis = PlayerTimelineGestureAxis.VerticalScene
                                }
                                if (axis == PlayerTimelineGestureAxis.Undecided) {
                                    axis = resolvePlayerTimelineGestureAxis(
                                        dx = change.position.x - start.x,
                                        dy = change.position.y - start.y,
                                        touchSlop = viewConfiguration.touchSlop,
                                    )
                                    if (axis == PlayerTimelineGestureAxis.HorizontalSeek) {
                                        seekStarted = true
                                        isDragging = true
                                        onSeekStart()
                                    }
                                }
                                if (axis == PlayerTimelineGestureAxis.HorizontalSeek) {
                                    lastFraction = (change.position.x / widthPx.toFloat())
                                        .coerceIn(0f, 1f)
                                    dragFraction = lastFraction
                                    change.consume()
                                }
                                if (!change.pressed) {
                                    finishedNormally = true
                                    break
                                }
                            }
                        } finally {
                            if (seekStarted) {
                                pendingSeekFraction = lastFraction
                                isDragging = false
                                onSeekStop(lastFraction)
                            } else if (finishedNormally && allowTapSeek &&
                                axis == PlayerTimelineGestureAxis.Undecided
                            ) {
                                val tapFraction = (lastPosition.x / widthPx.toFloat())
                                    .coerceIn(0f, 1f)
                                pendingSeekFraction = tapFraction
                                onSeekStart()
                                onSeekStop(tapFraction)
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .graphicsLayer {
                        val pauseScale = if (scaleAnimationEnabled) animatedPauseScale else 1f
                        scaleX = pauseScale
                        scaleY = pauseScale
                    },
            ) {
                val barWidth = barWidthDp.dp.toPx().coerceAtLeast(1f)
                val gap = gapDp.dp.toPx().coerceAtLeast(0.5f)
                drawRoundedWaveform(
                    peaks = waveform,
                    previousPeaks = previousWaveform,
                    waveformSwapProgress = waveformSwap.value,
                    progress = displayFraction,
                    pauseMorph = pauseMorph,
                    colors = colors,
                    climaxSegments = if (climaxEnabled) climaxSegments else emptyList(),
                    barWidth = barWidth,
                    gap = gap,
                    visibleBarCount = visibleWaveformBarCount,
                    isDragging = isDragging,
                    peakHeightScale = peakHeightScale
                )
            }
        }

    }
}

private fun DrawScope.drawRoundedWaveform(
    peaks: FloatArray,
    previousPeaks: FloatArray?,
    waveformSwapProgress: Float,
    progress: Float,
    pauseMorph: Float,
    colors: ImmersiveWaveformColors,
    climaxSegments: List<ImmersiveClimaxSegment>,
    barWidth: Float,
    gap: Float,
    visibleBarCount: Int,
    isDragging: Boolean,
    peakHeightScale: Float = 1f
) {
    if (peaks.isEmpty() || size.width <= 0f || size.height <= 0f) return

    @Suppress("UNUSED_VARIABLE")
    val legacyPitch = barWidth + gap
    val visibleBars = visibleBarCount.coerceIn(8, RawWaveformCache.MAX_SAMPLE_COUNT)
    val actualStep = size.width / visibleBars.toFloat()
    // Density is now an explicit visible-bar count. Keep a consistent 70/30 bar/gap ratio
    // so 100/160/200/280 remain visibly distinct without changing climax analysis.
    val actualBarWidth = (actualStep * 0.70f).coerceAtLeast(0.55f * density)
    val centerY = size.height * 0.5f
    val maxHalfHeight = size.height * 0.50f
    val minHalfHeight = 2.4f * density
    val progressX = size.width * progress.coerceIn(0f, 1f)

    fun baseBarValue(index: Int): Float {
        val srcIndex = ((index + 0.5f) / visibleBars.toFloat() * peaks.size).toInt().coerceIn(0, peaks.lastIndex)
        val currentValue = peaks[srcIndex].coerceIn(0f, 1f)
        val previousValue = previousPeaks?.takeIf { it.isNotEmpty() }?.let { oldPeaks ->
            val oldIndex = ((index + 0.5f) / visibleBars.toFloat() * oldPeaks.size)
                .toInt()
                .coerceIn(0, oldPeaks.lastIndex)
            oldPeaks[oldIndex].coerceIn(0f, 1f)
        } ?: currentValue
        val swap = waveformSwapProgress.coerceIn(0f, 1f)
        val live = emphasizeWaveValue(previousValue + (currentValue - previousValue) * swap)
        val idle = 0.020f + when (index % 4) {
            0 -> 0.006f
            1 -> 0.014f
            2 -> 0.010f
            else -> 0.016f
        }
        val m = pauseMorph.coerceIn(0f, 1f)
        return live * (1f - m) + idle * m
    }

    fun climaxColorFor(centerX: Float, base: Color): Color {
        if (climaxSegments.isEmpty()) return base
        val fraction = (centerX / size.width).coerceIn(0f, 1f)
        val hit = climaxSegments.firstOrNull { fraction in it.startFraction..it.endFraction } ?: return base
        val confidence = hit.confidence.coerceIn(0f, 1f)
        val target = if (centerX <= progressX) colors.climaxPlayed else colors.climaxRemaining
        return lerpColor(base, target, 0.35f + 0.45f * confidence)
    }

    val radius = CornerRadius(actualBarWidth * 0.5f, actualBarWidth * 0.5f)
    for (i in 0 until visibleBars) {
        val x = i * actualStep + (actualStep - actualBarWidth) * 0.5f
        val centerX = x + actualBarWidth * 0.5f
        val shaped = baseBarValue(i)
        val halfHeight = WaveformProgressTuning.waveformBarHalfHeight(
            minHalfHeight = minHalfHeight,
            maxHalfHeight = maxHalfHeight,
            shaped = shaped,
            heightScale = peakHeightScale,
            canvasHeight = size.height,
        )
        val top = centerY - halfHeight
        val barHeight = halfHeight * 2f
        val left = x
        val right = x + actualBarWidth
        val playedColor = climaxColorFor(centerX, colors.played)
        val remainingColor = climaxColorFor(centerX, colors.remaining)

        when {
            progressX <= left -> {
                drawRoundRect(
                    color = remainingColor,
                    topLeft = Offset(left, top),
                    size = Size(actualBarWidth, barHeight),
                    cornerRadius = radius
                )
            }
            progressX >= right -> {
                drawRoundRect(
                    color = playedColor,
                    topLeft = Offset(left, top),
                    size = Size(actualBarWidth, barHeight),
                    cornerRadius = radius
                )
            }
            else -> {
                clipRect(left = left, top = 0f, right = progressX, bottom = size.height) {
                    drawRoundRect(
                        color = playedColor,
                        topLeft = Offset(left, top),
                        size = Size(actualBarWidth, barHeight),
                        cornerRadius = radius
                    )
                }
                clipRect(left = progressX, top = 0f, right = right, bottom = size.height) {
                    drawRoundRect(
                        color = remainingColor,
                        topLeft = Offset(left, top),
                        size = Size(actualBarWidth, barHeight),
                        cornerRadius = radius
                    )
                }
            }
        }
    }

    val dragFocus = if (isDragging) 1f else 0f
    val needleWidth = (1.48f + dragFocus * 0.36f) * density
    val needleHeight = size.height * 1.36f
    val needleTop = (size.height - needleHeight) * 0.5f
    drawRoundRect(
        color = colors.needle.copy(alpha = 0.98f),
        topLeft = Offset((progressX - needleWidth * 0.5f).coerceIn(0f, size.width - needleWidth), needleTop),
        size = Size(needleWidth, needleHeight),
        cornerRadius = CornerRadius(needleWidth, needleWidth)
    )
}

private fun emphasizeWaveValue(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return (0.05f + 0.95f * clamped.pow(1.7f)).coerceIn(0f, 1f)
}

private fun lerpColor(start: Color, end: Color, t: Float): Color {
    val clamped = t.coerceIn(0f, 1f)
    return Color(
        red = start.red + (end.red - start.red) * clamped,
        green = start.green + (end.green - start.green) * clamped,
        blue = start.blue + (end.blue - start.blue) * clamped,
        alpha = start.alpha + (end.alpha - start.alpha) * clamped
    )
}
