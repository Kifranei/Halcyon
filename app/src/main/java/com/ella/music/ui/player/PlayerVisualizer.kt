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
            SettingsManager.AUDIO_VISUALIZER_STYLE_PARTICLES ->
                drawParticlesSpectrum(displayLevels, accent)
            SettingsManager.AUDIO_VISUALIZER_STYLE_STRINGS ->
                drawStringsSpectrum(displayLevels, accent)
            SettingsManager.AUDIO_VISUALIZER_STYLE_CLASSIC_BARS ->
                drawClassicBarsSpectrum(displayLevels, accent)
            SettingsManager.AUDIO_VISUALIZER_STYLE_WATER_RIPPLE ->
                drawWaterRippleSpectrum(displayLevels, accent, positionMs)
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

/** ConePlayer-inspired floating particles / stardust visualizer. */
private fun DrawScope.drawParticlesSpectrum(levels: List<Float>, accent: Color) {
    if (levels.isEmpty() || size.width <= 0f || size.height <= 0f) return
    val columnCount = 32
    val step = (levels.size / columnCount).coerceAtLeast(1)
    val slotWidth = size.width / columnCount
    val maxTravel = size.height * 0.88f

    for (i in 0 until columnCount) {
        val startIdx = i * step
        val endIdx = (startIdx + step).coerceAtMost(levels.size)
        var sum = 0f
        for (k in startIdx until endIdx) sum += levels[k]
        val avg = sum / (endIdx - startIdx).coerceAtLeast(1)
        val shaped = sqrt(avg.coerceIn(0f, 1f))
        val x = (i + 0.5f) * slotWidth

        // Deterministic pseudo-randomness per column to keep particles stable yet organically offset
        val hash = ((i * 2654435761L) and 0xFFFFFFFFL).toFloat() / 0xFFFFFFFFL
        val hash2 = (((i + 17) * 2246822519L) and 0xFFFFFFFFL).toFloat() / 0xFFFFFFFFL
        val hash3 = (((i + 31) * 3266489917L) and 0xFFFFFFFFL).toFloat() / 0xFFFFFFFFL

        val particleY = size.height - (size.height * 0.10f + maxTravel * shaped * (0.80f + 0.20f * hash))

        // Faint vertical trail
        if (shaped > 0.08f) {
            drawLine(
                brush = Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to accent.copy(alpha = 0.16f * shaped),
                    startY = particleY,
                    endY = size.height
                ),
                start = Offset(x, particleY),
                end = Offset(x, size.height),
                strokeWidth = 1.2f * density,
                cap = StrokeCap.Round
            )
        }

        // Main particle glow & core
        val coreRadius = (1.5f + 1.6f * shaped) * density
        val glowRadius = coreRadius * 2.6f
        drawCircle(
            color = accent.copy(alpha = 0.22f * shaped),
            radius = glowRadius,
            center = Offset(x, particleY)
        )
        drawCircle(
            color = Color.White.copy(alpha = (0.50f + 0.45f * shaped).coerceIn(0f, 1f)),
            radius = coreRadius,
            center = Offset(x, particleY)
        )

        // Floating secondary ember
        if (shaped > 0.15f) {
            val emberX = (x + (hash2 - 0.5f) * slotWidth * 0.9f).coerceIn(0f, size.width)
            val emberY = (particleY - (6f + 14f * hash3) * density * shaped).coerceAtLeast(2f * density)
            val emberRadius = (0.9f + 0.8f * shaped) * density
            drawCircle(
                color = accent.copy(alpha = 0.35f * shaped),
                radius = emberRadius * 2.2f,
                center = Offset(emberX, emberY)
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.80f * shaped),
                radius = emberRadius,
                center = Offset(emberX, emberY)
            )
        }
    }
}

/** ConePlayer-inspired vibrating resonant strings / harmonics visualizer. */
private fun DrawScope.drawStringsSpectrum(levels: List<Float>, accent: Color) {
    if (levels.isEmpty() || size.width <= 0f || size.height <= 0f) return
    val stringCount = 6
    val segments = 48
    val maxAmplitude = size.height * 0.18f

    // Band groupings: low bass, mid-bass, lower-mid, mid, upper-mid, treble
    val bandIndices = listOf(
        0..3,
        4..9,
        10..18,
        19..30,
        31..45,
        46..levels.lastIndex
    )

    for (s in 0 until stringCount) {
        val range = bandIndices.getOrNull(s) ?: (0..0)
        var sum = 0f
        var count = 0
        for (idx in range) {
            if (idx in levels.indices) {
                sum += levels[idx]
                count++
            }
        }
        val bandLevel = if (count > 0) sum / count else 0.05f
        val shaped = sqrt(bandLevel.coerceIn(0f, 1f))
        val baseY = size.height * (0.20f + 0.65f * (s.toFloat() / (stringCount - 1).coerceAtLeast(1)))
        val amplitude = maxAmplitude * shaped * (0.75f + 0.25f * (s % 2))

        val path = Path()
        val harmonicMode = s + 1

        for (k in 0..segments) {
            val ratio = k.toFloat() / segments
            val x = size.width * ratio
            // Pin both ends to 0 with sine envelope: sin(pi * ratio)
            val envelope = sin(PI.toFloat() * ratio)
            // Harmonic wave with alternating sign
            val wave = sin(harmonicMode * PI.toFloat() * ratio)
            val sign = if (s % 2 == 1) -1f else 1f
            val y = baseY + amplitude * wave * envelope * sign

            if (k == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }

        // Soft outer glow stroke
        drawPath(
            path = path,
            color = accent.copy(alpha = (0.16f + 0.26f * shaped).coerceIn(0f, 1f)),
            style = Stroke(
                width = 3.6f * density,
                cap = StrokeCap.Round
            )
        )
        // Sharp core string
        drawPath(
            path = path,
            color = Color.White.copy(alpha = (0.60f + 0.35f * shaped).coerceIn(0f, 1f)),
            style = Stroke(
                width = 1.2f * density,
                cap = StrokeCap.Round
            )
        )
    }
}

/** ConePlayer-inspired classic EQ jumping columns with floating peak caps. */
private fun DrawScope.drawClassicBarsSpectrum(levels: List<Float>, accent: Color) {
    if (levels.isEmpty() || size.width <= 0f || size.height <= 0f) return
    val barCount = 32
    val step = (levels.size / barCount).coerceAtLeast(1)
    val slotWidth = size.width / barCount
    val barWidth = (slotWidth * 0.68f).coerceAtLeast(1.5f * density)
    val cornerRadius = CornerRadius(barWidth * 0.35f, barWidth * 0.35f)
    val maxHeight = size.height * 0.88f
    val minHeight = 2f * density
    val capHeight = (2f * density).coerceAtLeast(1.5f)

    for (i in 0 until barCount) {
        val startIdx = i * step
        val endIdx = (startIdx + step).coerceAtMost(levels.size)
        var sum = 0f
        for (k in startIdx until endIdx) sum += levels[k]
        val avg = sum / (endIdx - startIdx).coerceAtLeast(1)
        val shaped = sqrt(avg.coerceIn(0f, 1f))
        val barHeight = minHeight + (maxHeight - minHeight) * shaped
        val x = (i + 0.5f) * slotWidth
        val left = x - barWidth * 0.5f
        val top = size.height - barHeight

        // Glow behind column
        drawRoundRect(
            color = accent.copy(alpha = 0.14f * shaped),
            topLeft = Offset(left - density, top - density),
            size = Size(barWidth + 2f * density, barHeight + density),
            cornerRadius = cornerRadius
        )

        // Main bar gradient fill
        drawRoundRect(
            brush = Brush.verticalGradient(
                0f to Color.White.copy(alpha = (0.80f + 0.18f * shaped).coerceIn(0f, 1f)),
                0.25f to accent.copy(alpha = 0.78f),
                1f to accent.copy(alpha = 0.22f),
                startY = top,
                endY = size.height
            ),
            topLeft = Offset(left, top),
            size = Size(barWidth, barHeight),
            cornerRadius = cornerRadius
        )

        // Floating peak cap
        val capDistance = (2f + 5f * shaped) * density
        val capTop = (top - capDistance - capHeight).coerceAtLeast(1f * density)
        val capRadius = CornerRadius(capHeight * 0.5f, capHeight * 0.5f)

        // Cap halo
        drawRoundRect(
            color = accent.copy(alpha = 0.28f * shaped),
            topLeft = Offset(left - 0.5f * density, capTop - 0.5f * density),
            size = Size(barWidth + density, capHeight + density),
            cornerRadius = capRadius
        )
        // Cap core
        drawRoundRect(
            color = Color.White.copy(alpha = (0.75f + 0.22f * shaped).coerceIn(0f, 1f)),
            topLeft = Offset(left, capTop),
            size = Size(barWidth, capHeight),
            cornerRadius = capRadius
        )
    }
}

/** ConePlayer-inspired fluid water ripple / wave layers visualizer. */
private fun DrawScope.drawWaterRippleSpectrum(
    levels: List<Float>,
    accent: Color,
    positionMs: Long
) {
    if (levels.isEmpty() || size.width <= 0f || size.height <= 0f) return

    val bass = (levels.take(12).average().toFloat()).coerceIn(0f, 1f)
    val mids = (levels.subList(12, 36.coerceAtMost(levels.size)).average().toFloat()).coerceIn(0f, 1f)
    val highs = (levels.takeLast(24.coerceAtMost(levels.size)).average().toFloat()).coerceIn(0f, 1f)

    val bassShaped = sqrt(bass)
    val midsShaped = sqrt(mids)
    val highsShaped = sqrt(highs)

    val bottom = size.height
    val segments = 40
    val timePhase = ((positionMs % 60000L) / 1000f * 1.2f)

    fun drawWaveLayer(
        amplitude: Float,
        baseY: Float,
        cycles: Float,
        phaseOffset: Float,
        fillBrush: Brush,
        crestColor: Color? = null
    ) {
        val path = Path()
        path.moveTo(0f, bottom)

        val points = (0..segments).map { i ->
            val ratio = i.toFloat() / segments
            val x = size.width * ratio
            val wave = sin(ratio * cycles * 2f * PI.toFloat() + phaseOffset + timePhase)
            val y = (baseY - amplitude * wave).coerceIn(0f, bottom)
            Offset(x, y)
        }

        path.lineTo(points.first().x, points.first().y)
        for (i in 0 until points.lastIndex) {
            val p0 = points[(i - 1).coerceAtLeast(0)]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
            val cp1 = p1 + (p2 - p0) * 0.1666f
            val cp2 = p2 - (p3 - p1) * 0.1666f
            path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
        }
        path.lineTo(size.width, bottom)
        path.close()

        drawPath(path, fillBrush)

        crestColor?.let { color ->
            val crestPath = Path()
            crestPath.moveTo(points.first().x, points.first().y)
            for (i in 0 until points.lastIndex) {
                val p0 = points[(i - 1).coerceAtLeast(0)]
                val p1 = points[i]
                val p2 = points[i + 1]
                val p3 = points[(i + 2).coerceAtMost(points.lastIndex)]
                val cp1 = p1 + (p2 - p0) * 0.1666f
                val cp2 = p2 - (p3 - p1) * 0.1666f
                crestPath.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
            }
            drawPath(
                path = crestPath,
                color = color,
                style = Stroke(width = 1.6f * density, cap = StrokeCap.Round)
            )
        }
    }

    // Layer 1: Background deep surge (driven by bass)
    val layer1BaseY = bottom - (size.height * (0.28f + 0.52f * bassShaped))
    val layer1Amp = size.height * (0.06f + 0.14f * bassShaped)
    drawWaveLayer(
        amplitude = layer1Amp,
        baseY = layer1BaseY,
        cycles = 1.5f,
        phaseOffset = 0f,
        fillBrush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.4f to accent.copy(alpha = 0.14f),
            1f to accent.copy(alpha = 0.28f),
            startY = layer1BaseY - layer1Amp,
            endY = bottom
        )
    )

    // Layer 2: Mid wave rolling swell (driven by mids)
    val layer2BaseY = bottom - (size.height * (0.22f + 0.44f * midsShaped))
    val layer2Amp = size.height * (0.05f + 0.12f * midsShaped)
    drawWaveLayer(
        amplitude = layer2Amp,
        baseY = layer2BaseY,
        cycles = 2.5f,
        phaseOffset = 1.8f,
        fillBrush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.45f to accent.copy(alpha = 0.20f),
            1f to accent.copy(alpha = 0.42f),
            startY = layer2BaseY - layer2Amp,
            endY = bottom
        )
    )

    // Layer 3: Foreground crest ripple (driven by highs and energy)
    val layer3BaseY = bottom - (size.height * (0.16f + 0.36f * highsShaped))
    val layer3Amp = size.height * (0.04f + 0.10f * highsShaped)
    drawWaveLayer(
        amplitude = layer3Amp,
        baseY = layer3BaseY,
        cycles = 3.5f,
        phaseOffset = 3.6f,
        fillBrush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.5f to accent.copy(alpha = 0.30f),
            1f to accent.copy(alpha = 0.58f),
            startY = layer3BaseY - layer3Amp,
            endY = bottom
        ),
        crestColor = Color.White.copy(alpha = (0.65f + 0.30f * highsShaped).coerceIn(0f, 1f))
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
