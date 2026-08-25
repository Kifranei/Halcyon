package com.ella.music.ui.player

import android.media.audiofx.Visualizer
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

@Composable
internal fun AudioVisualizer(
    enabled: Boolean,
    audioSessionId: Int,
    isPlaying: Boolean,
    positionMs: Long,
    opacity: Float = 1f,
    accent: Color,
    modifier: Modifier = Modifier
) {
    if (!enabled) return
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val style by settingsManager.audioVisualizerStyle.collectAsState(
        initial = SettingsManager.DEFAULT_AUDIO_VISUALIZER_STYLE
    )
    var levels by remember { mutableStateOf<List<Float>>(emptyList()) }
    var visualizerFailed by remember { mutableStateOf(false) }
    val playingState by rememberUpdatedState(isPlaying)
    // Release the Visualizer and stop capturing FFT while the player surface is hidden but resident;
    // otherwise it keeps polling audio and redrawing the spectrum behind the visible screen.
    val surfaceActive = LocalPlayerSurfaceActive.current

    LaunchedEffect(enabled, audioSessionId, surfaceActive) {
        levels = emptyList()
        visualizerFailed = false
        if (!enabled || audioSessionId <= 0 || !surfaceActive) return@LaunchedEffect
        val visualizer = runCatching {
            Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1].coerceAtMost(512)
                scalingMode = Visualizer.SCALING_MODE_NORMALIZED
                this.enabled = true
            }
        }.onFailure { visualizerFailed = true }.getOrNull() ?: return@LaunchedEffect

        val buffer = ByteArray(visualizer.captureSize)
        var smoothedLevels = emptyList<Float>()
        try {
            while (isActive) {
                if (playingState) {
                    if (visualizer.getFft(buffer) == Visualizer.SUCCESS) {
                        smoothedLevels = mapFftToLogBars(buffer, smoothedLevels, barCount = 64)
                        levels = smoothedLevels
                    }
                } else {
                    smoothedLevels = emptyList()
                    levels = emptyList()
                    delay(120L)
                    continue
                }
                delay(50L)
            }
        } finally {
            runCatching { visualizer.enabled = false }
            visualizer.release()
        }
    }

    Canvas(modifier = modifier.graphicsLayer { alpha = (if (isPlaying) 1f else 0.42f) * opacity.coerceIn(0f, 1f) }) {
        val barCount = 64
        val displayLevels = List(barCount) { index -> levels.getOrNull(index) ?: 0.04f }
        when (SettingsManager.normalizeAudioVisualizerStyle(style)) {
            SettingsManager.AUDIO_VISUALIZER_STYLE_RAWS_SPECTRUM ->
                drawRawSSpectrum(displayLevels, accent)
            else -> drawBetterLyricsSpectrumCurve(displayLevels, accent)
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

private fun DrawScope.drawBetterLyricsSpectrumCurve(
    levels: List<Float>,
    accent: Color
) {
    if (levels.size < 2 || size.width <= 0f || size.height <= 0f) return

    fun spectrumPath(heightScale: Float): Path {
        val path = Path()
        val bottom = size.height
        val visualHeight = size.height * heightScale
        val points = levels.mapIndexed { index, raw ->
            val x = size.width * index.toFloat() / (levels.lastIndex).toFloat()
            val shaped = raw.coerceIn(0f, 1f)
            val y = bottom - visualHeight * shaped
            Offset(x, y)
        }

        path.moveTo(0f, bottom)
        path.lineTo(points.first().x, points.first().y)
        for (index in 0 until points.lastIndex) {
            val p0 = points[(index - 1).coerceAtLeast(0)]
            val p1 = points[index]
            val p2 = points[index + 1]
            val p3 = points[(index + 2).coerceAtMost(points.lastIndex)]
            val cp1 = p1 + (p2 - p0) * 0.1666f
            val cp2 = p2 - (p3 - p1) * 0.1666f
            path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
        }
        path.lineTo(size.width, bottom)
        path.close()
        return path
    }

    val glowPath = spectrumPath(0.82f)
    val mainPath = spectrumPath(0.68f)
    val glowBrush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.52f to accent.copy(alpha = 0.10f),
        1f to accent.copy(alpha = 0.28f)
    )
    val mainBrush = Brush.verticalGradient(
        0f to Color.Transparent,
        0.58f to accent.copy(alpha = 0.22f),
        1f to accent.copy(alpha = 0.58f)
    )

    drawPath(glowPath, glowBrush)
    drawPath(mainPath, mainBrush)
    drawPath(
        path = spectrumPath(0.44f),
        brush = Brush.verticalGradient(
            0f to Color.Transparent,
            1f to Color.White.copy(alpha = 0.08f)
        )
    )
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
