package com.ella.music.ui.player

import android.media.audiofx.Visualizer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
internal fun AudioVisualizer(
    enabled: Boolean,
    audioSessionId: Int,
    isPlaying: Boolean,
    positionMs: Long,
    opacity: Float = 1f,
    accent: Color,
    /** False where the portrait cover is covered by another surface (e.g. landscape overlay). */
    coverOverlayAllowed: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val blurRadius by settingsManager.audioVisualizerBlur.collectAsState(initial = 0)
    val style by settingsManager.audioVisualizerStyle.collectAsState(
        initial = SettingsManager.DEFAULT_AUDIO_VISUALIZER_STYLE
    )
    val heightPercent by settingsManager.audioVisualizerHeight.collectAsState(
        initial = SettingsManager.DEFAULT_AUDIO_VISUALIZER_HEIGHT
    )
    val coneStyle = style in setOf(SettingsManager.AUDIO_VISUALIZER_STYLE_PARTICLES,
        SettingsManager.AUDIO_VISUALIZER_STYLE_STRINGS, SettingsManager.AUDIO_VISUALIZER_STYLE_CLASSIC_BARS,
        SettingsManager.AUDIO_VISUALIZER_STYLE_WATER_RIPPLE)
    val coverStyle = style == SettingsManager.AUDIO_VISUALIZER_STYLE_COVER_OVERLAY
    val defaultStyle = !coneStyle && !coverStyle && style != SettingsManager.AUDIO_VISUALIZER_STYLE_RAWS_SPECTRUM
    val fallbackCoverHost = remember { PlayerCoverVisualizerHost() }
    val coverHost = LocalPlayerCoverVisualizerHost.current ?: fallbackCoverHost
    val coverMotion = coverHost.motion
    val coverSlotsActive = coverStyle && coverOverlayAllowed
    SideEffect {
        coverHost.active = coverSlotsActive
        coverHost.playing = isPlaying
        coverHost.opacity = opacity
        coverHost.blurRadiusDp = blurRadius
    }
    DisposableEffect(coverHost) {
        onDispose { coverHost.active = false }
    }
    val centered = remember(audioSessionId, style) { CenteredSpectrumMotion() }
    var centeredTargets by remember { mutableStateOf(FloatArray(64)) }
    val motion = remember(audioSessionId, style) { ConeVisualizerMotion() }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var frame by remember { mutableIntStateOf(0) }
    var levels by remember { mutableStateOf<List<Float>>(emptyList()) }
    var visualizerFailed by remember { mutableStateOf(false) }
    val playingState by rememberUpdatedState(isPlaying)
    // Release the Visualizer and stop capturing FFT while the player surface is hidden but resident;
    // otherwise it keeps polling audio and redrawing the spectrum behind the visible screen.
    val surfaceActive = LocalPlayerSurfaceActive.current

    LaunchedEffect(enabled, audioSessionId, surfaceActive, coneStyle, defaultStyle, coverStyle) {
        levels = emptyList()
        centeredTargets = FloatArray(64)
        coverMotion.reset()
        visualizerFailed = false
        if (!enabled || audioSessionId <= 0 || !surfaceActive) return@LaunchedEffect
        val visualizer = runCatching {
            Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]
                    .coerceAtMost(if (coneStyle || defaultStyle || coverStyle) 1024 else 512)
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                this.enabled = true
            }
        }.onFailure { visualizerFailed = true }.getOrNull() ?: return@LaunchedEffect

        val buffer = ByteArray(visualizer.captureSize)
        val samplingRateHz = visualizer.samplingRate / 1000
        var smoothedLevels = emptyList<Float>()
        var lastCaptureNanos = 0L
        try {
            while (isActive) {
                if (playingState) {
                    if (visualizer.getFft(buffer) == Visualizer.SUCCESS) {
                        if (defaultStyle) {
                            centeredTargets = centeredSpectrumTargets(buffer, samplingRateHz)
                        } else if (coverStyle) {
                            val now = System.nanoTime()
                            val seconds = if (lastCaptureNanos == 0L) 1f / 60f
                                else (now - lastCaptureNanos) / 1_000_000_000f
                            lastCaptureNanos = now
                            coverMotion.submit(rawsArtworkSpectrumTargets(buffer, samplingRateHz), seconds)
                        } else {
                            smoothedLevels = if (coneStyle) coneSpectrumTargets(buffer) else mapFftToLogBars(buffer, smoothedLevels, barCount = 64)
                            levels = smoothedLevels
                        }
                    }
                } else {
                    smoothedLevels = emptyList()
                    levels = emptyList()
                    lastCaptureNanos = 0L
                    delay(120L)
                    continue
                }
                delay(if (coneStyle || defaultStyle || coverStyle) 16L else 50L)
            }
        } finally {
            runCatching { visualizer.enabled = false }
            visualizer.release()
        }
    }

    val latestLevels by rememberUpdatedState(levels)
    val silentLevels = remember { List(64) { 0f } }
    LaunchedEffect(coneStyle, surfaceActive, isPlaying, motion) {
        if (!coneStyle || !surfaceActive || !isPlaying) { motion.reset(); frame++; return@LaunchedEffect }
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L) motion.advance(latestLevels, viewport.width.toFloat(), viewport.height.toFloat(),
                    (now - previous) / 1_000_000_000f, style)
                previous = now
                frame++
            }
        }
    }
    val currentCenteredTargets by rememberUpdatedState(centeredTargets)
    LaunchedEffect(defaultStyle, surfaceActive, isPlaying, centered) {
        if (!defaultStyle || !surfaceActive || !isPlaying) { centered.reset(); frame++; return@LaunchedEffect }
        var previous = 0L
        while (isActive) {
            withFrameNanos { now ->
                if (previous != 0L) centered.advance(currentCenteredTargets, (now - previous) / 1_000_000_000f)
                previous = now
                frame++
            }
        }
    }
    // RawS display clock: after a pause the columns release to rest, then the loop stops.
    LaunchedEffect(coverStyle, surfaceActive, isPlaying, coverMotion) {
        if (!coverStyle || !surfaceActive) return@LaunchedEffect
        var previous = 0L
        while (isActive && (isPlaying || coverMotion.hasVisibleEnergy())) {
            withFrameNanos { now ->
                if (previous != 0L) coverMotion.advance((now - previous) / 1_000_000_000f, isPlaying)
                previous = now
                coverHost.frame++
            }
        }
        coverHost.frame++
    }
    // A visible cover slot draws the cover style inside the artwork instead of here.
    if (coverSlotsActive && coverHost.visibleCovers > 0) return
    // Every style shares the flow curve's default height (25% of the player surface), scaled
    // by the user's visualizer-height percentage. Styles draw relative to this box.
    val visualizerModifier = modifier.fillMaxWidth().fillMaxHeight(audioVisualizerHeightFraction(heightPercent))
    Canvas(modifier = visualizerModifier
        .then(if (blurRadius > 0) Modifier.blur(blurRadius.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
        .onSizeChanged { viewport = it }.graphicsLayer {
        alpha = (if (isPlaying) 1f else 0.42f) * opacity.coerceIn(0f, 1f)
    }) {
        if (coneStyle) {
            @Suppress("UNUSED_VARIABLE") val frameDependency = frame
            motion.draw(this, style, accent)
        } else if (coverStyle) {
            @Suppress("UNUSED_VARIABLE") val frameDependency = coverHost.frame
            drawRawSArtworkSpectrum(coverMotion.levels, isPlaying)
        } else {
            val displayLevels = if (levels.size == 64) levels else silentLevels
            if (style == SettingsManager.AUDIO_VISUALIZER_STYLE_RAWS_SPECTRUM) drawRawSSpectrum(displayLevels, accent)
            else {
                @Suppress("UNUSED_VARIABLE") val frameDependency = frame
                drawBetterLyricsSpectrumCurve(centered.levels, accent)
            }
        }
    }
}

/** RawS Music-inspired mirrored FFT columns, adapted to Halcyon's Media3 visualizer stream. */
private fun DrawScope.drawRawSSpectrum(levels: List<Float>, accent: Color) {
    if (levels.isEmpty() || size.width <= 0f || size.height <= 0f) return
    val slotWidth = size.width / levels.size
    val barWidth = (slotWidth * 0.38f).coerceAtLeast(0.75f * density)
    val axisY = size.height * 0.55f
    val maximumHalfHeight = size.height * 0.43f
    val minimumHalfHeight = 1.1f * density
    val radius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)

    levels.forEachIndexed { index, rawLevel ->
        val shaped = sqrt(rawLevel.coerceIn(0f, 1f))
        val halfHeight = minimumHalfHeight + maximumHalfHeight * shaped
        val x = (index + 0.5f) * slotWidth
        drawRoundRect(
            color = accent.copy(alpha = 0.12f),
            topLeft = Offset(x - barWidth * 0.7f, axisY - halfHeight - density),
            size = Size(barWidth * 1.4f, halfHeight * 2f + density * 2f),
            cornerRadius = radius
        )
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to accent.copy(alpha = 0.62f),
                0.5f to Color.White.copy(alpha = 0.86f),
                1f to accent.copy(alpha = 0.62f),
                startY = axisY - maximumHalfHeight,
                endY = axisY + maximumHalfHeight
            ),
            topLeft = Offset(x - barWidth * 0.5f, axisY - halfHeight),
            size = Size(barWidth, halfHeight * 2f),
            cornerRadius = radius
        )
    }
}

/** One continuous Catmull-Rom envelope, with the bottom-anchored gradient of BetterLyrics. */
private fun DrawScope.drawBetterLyricsSpectrumCurve(levels: FloatArray, accent: Color) {
    if (levels.size < 2 || size.width <= 0f || size.height <= 0f) return
    val spacing = size.width / levels.lastIndex
    // The canvas is already the visualizer box (25% of the player surface at 100% height),
    // so the envelope uses its full height and scales with the visualizer-height setting.
    val height = size.height
    fun point(index: Int): Offset {
        val i = index.coerceIn(0, levels.lastIndex)
        return Offset(i * spacing, size.height - height * levels[i])
    }
    val curve = Path().apply {
        val first = point(0)
        moveTo(first.x, first.y)
        for (index in 0 until levels.lastIndex) {
            val before = point(index - 1)
            val start = point(index)
            val end = point(index + 1)
            val after = point(index + 2)
            val c1 = start + (end - before) / 6f
            val c2 = end - (after - start) / 6f
            cubicTo(c1.x, c1.y, c2.x, c2.y, end.x, end.y)
        }
        lineTo(size.width, size.height)
        lineTo(0f, size.height)
        close()
    }
    drawPath(curve, Brush.verticalGradient(
        colors = listOf(accent.copy(alpha = 0f), accent.copy(alpha = .65f)),
        startY = 0f, endY = size.height
    ))
}

private fun mapFftToLogBars(
    fft: ByteArray,
    previous: List<Float>,
    barCount: Int
): List<Float> {
    val binCount = fft.size / 2
    if (binCount <= 2) return List(barCount) { 0.06f }

    return List(barCount) { index ->
        val startRatio = index.toFloat() / barCount
        val endRatio = (index + 1f) / barCount
        val startBin = (1f + (binCount - 2) * startRatio * startRatio)
            .toInt()
            .coerceIn(1, binCount - 1)
        val endBin = (1f + (binCount - 2) * endRatio * endRatio)
            .toInt()
            .coerceIn(startBin, binCount - 1)

        var peak = 0f
        for (bin in startBin..endBin) {
            val real = fft[bin * 2].toFloat()
            val imag = fft[bin * 2 + 1].toFloat()
            peak = max(peak, sqrt(real * real + imag * imag))
        }

        val db = 20f * (ln(peak.coerceAtLeast(1f)) / ln(10f))
        val normalized = ((db - 16f) / 36f).coerceIn(0f, 1f)
        val shaped = 0.06f + sqrt(normalized) * 0.94f
        val old = previous.getOrNull(index) ?: 0.06f
        if (shaped > old) {
            old * 0.42f + shaped * 0.58f
        } else {
            old * 0.84f + shaped * 0.16f
        }
    }
}
