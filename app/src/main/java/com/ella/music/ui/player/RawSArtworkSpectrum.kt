// Adapted from RawS Music, Copyright 2024–2026 RawSMusic Contributors, Apache-2.0.
// Sources: core/ui/.../player/ComposeAudioVisualizer.kt (AlbumArtworkSpectrumOverlay with
// AudioVisualizerLayer.Foreground, DisplaySpectrumMotion) and app/src/main/cpp/
// stereo_spectrum_analyzer.cpp (MonoSpectrumAnalyzer::transform / updateSmoothedBands).
package com.ella.music.ui.player

import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.ella.music.data.SettingsManager
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** RawS' native analyzer emits 112 logarithmic mono bands from 25 Hz to 20 kHz. */
internal const val RAWS_ARTWORK_BAND_COUNT = 112
private const val RAWS_MIN_FREQUENCY_HZ = 25f
private const val RAWS_MAX_FREQUENCY_HZ = 20_000f

// Android's Visualizer FFT is computed from 8-bit PCM, so its usable range ends near one LSB
// (-42 dBFS) instead of RawS' float-PCM -66 dB noise floor. The ceiling is scaled accordingly.
private const val ANDROID_FFT_FLOOR_DB = -42f
private const val ANDROID_FFT_CEILING_DB = -6f
// 8-bit capture loses most treble detail; a mild log-axis lift keeps the upper third visible
// without the flat "wall" RawS' tilt was written to avoid.
private const val ANDROID_TREBLE_LIFT_DB = 9f

/** The default visualizer height: the flow curve's 25% of the player surface. */
internal const val AUDIO_VISUALIZER_BASE_HEIGHT_FRACTION = 0.25f

/**
 * Fraction of the player surface height used by the bottom visualizer overlay for a
 * user percentage (50–200%) of the default flow-curve height.
 */
internal fun audioVisualizerHeightFraction(percent: Int?): Float =
    AUDIO_VISUALIZER_BASE_HEIGHT_FRACTION *
        SettingsManager.normalizeAudioVisualizerHeight(percent) / 100f

/** Visualizer-height slider increment, in percent. */
internal const val AUDIO_VISUALIZER_HEIGHT_STEP = 10

/** Intermediate Miuix slider stops for 50..200% in 10% increments. */
internal val AUDIO_VISUALIZER_HEIGHT_SLIDER_STEPS: Int =
    ((SettingsManager.MAX_AUDIO_VISUALIZER_HEIGHT - SettingsManager.MIN_AUDIO_VISUALIZER_HEIGHT) /
        AUDIO_VISUALIZER_HEIGHT_STEP - 1).coerceAtLeast(0)

/** Snap a (possibly float-truncated) slider value to the 10% grid inside the allowed range. */
internal fun snapAudioVisualizerHeight(value: Int): Int =
    SettingsManager.normalizeAudioVisualizerHeight(
        ((value + AUDIO_VISUALIZER_HEIGHT_STEP / 2) / AUDIO_VISUALIZER_HEIGHT_STEP) * AUDIO_VISUALIZER_HEIGHT_STEP
    )

/**
 * Port of RawS `MonoSpectrumAnalyzer::transform` for Android's signed-byte FFT: logarithmic
 * 25 Hz–20 kHz bands, peak × 0.68 + RMS × 0.32 magnitude, dB normalization, pow 1.45 and the
 * gentle `1.06 - 0.16 * t` tilt.
 *
 * @param fft `Visualizer.getFft` output; bin k occupies `fft[2k]` / `fft[2k + 1]`.
 * @param samplingRateHz the Visualizer sampling rate in Hz (not mHz).
 */
internal fun rawsArtworkSpectrumTargets(
    fft: ByteArray,
    samplingRateHz: Int,
    bandCount: Int = RAWS_ARTWORK_BAND_COUNT
): FloatArray {
    val out = FloatArray(bandCount.coerceAtLeast(0))
    val fftSize = fft.size
    val lastBin = fftSize / 2 - 1
    if (lastBin < 1 || bandCount <= 0) return out
    val sampleRate = (if (samplingRateHz > 0) samplingRateHz else 44_100).toFloat()
    val maxFrequency = min(RAWS_MAX_FREQUENCY_HZ, sampleRate * 0.49f)
        .coerceAtLeast(RAWS_MIN_FREQUENCY_HZ * 2f)
    val ratio = maxFrequency / RAWS_MIN_FREQUENCY_HZ
    for (band in 0 until bandCount) {
        val lowT = band.toFloat() / bandCount
        val highT = (band + 1f) / bandCount
        val lowFrequency = RAWS_MIN_FREQUENCY_HZ * ratio.pow(lowT)
        val highFrequency = RAWS_MIN_FREQUENCY_HZ * ratio.pow(highT)
        val lowBin = floor(lowFrequency * fftSize / sampleRate).toInt().coerceIn(1, lastBin)
        val highBin = ceil(highFrequency * fftSize / sampleRate).toInt().coerceIn(lowBin, lastBin)
        var peak = 0f
        var energy = 0f
        var count = 0
        for (bin in lowBin..highBin) {
            // Normalize the signed-byte magnitude to full scale (1.0 == 128).
            val magnitude = hypot(fft[bin * 2].toFloat(), fft[bin * 2 + 1].toFloat()) / 128f
            peak = max(peak, magnitude)
            energy += magnitude * magnitude
            count++
        }
        val rms = if (count > 0) sqrt(energy / count) else 0f
        val magnitude = peak * 0.68f + rms * 0.32f
        if (magnitude <= 0f) continue
        val db = 20f * log10(magnitude) + ANDROID_TREBLE_LIFT_DB * highT
        val normalized = ((db - ANDROID_FFT_FLOOR_DB) / (ANDROID_FFT_CEILING_DB - ANDROID_FFT_FLOOR_DB))
            .coerceIn(0f, 1f)
        val tilt = 1.06f - 0.16f * highT
        out[band] = (normalized.pow(1.45f) * tilt).coerceIn(0f, 1f)
    }
    return out
}

/**
 * The two smoothing stages RawS applies to this presentation: the analyzer's linear
 * rise/fall (20 / 4.5 full-scale per second, `updateSmoothedBands`) on every FFT capture, then
 * `DisplaySpectrumMotion` on the display clock (3-tap spatial blend, exponential attack 62/s and
 * release 28/s while playing, 8/s when paused). Plain render data; mutate on one thread only.
 */
internal class RawSArtworkSpectrumMotion(bandCount: Int = RAWS_ARTWORK_BAND_COUNT) {
    private val analyzer = FloatArray(bandCount)
    private val targets = FloatArray(bandCount)
    val levels = FloatArray(bandCount)

    /** Feed one analyzer frame. [seconds] is the time since the previous capture. */
    fun submit(bands: FloatArray, seconds: Float) {
        val dt = seconds.coerceIn(0.008f, 0.050f)
        val rise = 20f * dt
        val fall = 4.5f * dt
        for (i in analyzer.indices) {
            val target = bands.getOrElse(i) { 0f }.coerceIn(0f, 1f)
            val current = analyzer[i]
            analyzer[i] = if (target > current) min(target, current + rise) else max(target, current - fall)
        }
        val last = analyzer.lastIndex
        for (i in analyzer.indices) {
            val raw = analyzer[i]
            val previous = if (i > 0) analyzer[i - 1] else raw
            val next = if (i < last) analyzer[i + 1] else raw
            targets[i] = (raw * 0.78f + previous * 0.11f + next * 0.11f).coerceIn(0f, 1f)
        }
    }

    /** Advance the display motion by one display frame. */
    fun advance(seconds: Float, playing: Boolean) {
        val dt = seconds.coerceIn(1f / 240f, 1f / 20f)
        val attack = 1f - exp(-dt * 62f)
        val release = 1f - exp(-dt * if (playing) 28f else 8f)
        for (i in levels.indices) {
            val target = if (playing) targets[i] else 0f
            val level = levels[i]
            levels[i] = (level + (target - level) * if (target > level) attack else release).coerceIn(0f, 1f)
        }
    }

    fun hasVisibleEnergy(): Boolean = levels.any { it > 0.002f }

    fun reset() {
        analyzer.fill(0f); targets.fill(0f); levels.fill(0f)
    }
}

/** RawS' foreground column count: one slot per ~3.2 dp, clamped to 72..112 columns. */
internal fun rawsArtworkBarCount(widthDp: Float, bandCount: Int = RAWS_ARTWORK_BAND_COUNT): Int =
    (widthDp / 3.2f).roundToInt().coerceIn(min(72, bandCount), bandCount)

/** RawS `sampledValue`: blend of peak (76%) and RMS (24%) over the source bands in one column. */
internal fun rawsArtworkSampledValue(levels: FloatArray, displayBand: Int, barCount: Int): Float {
    val bands = levels.size
    if (bands == 0 || barCount <= 0) return 0f
    val start = floor(displayBand * bands.toFloat() / barCount).toInt().coerceIn(0, bands - 1)
    val end = ceil((displayBand + 1) * bands.toFloat() / barCount).toInt().coerceIn(start + 1, bands)
    var maximum = 0f
    var squareSum = 0f
    for (band in start until end) {
        val value = levels[band].coerceIn(0f, 1f)
        maximum = max(maximum, value)
        squareSum += value * value
    }
    val rms = sqrt(squareSum / (end - start))
    return (maximum * 0.76f + rms * 0.24f).coerceIn(0f, 1f)
}

/**
 * RawS' foreground artwork spectrum: thin white columns expanding above and below an axis at
 * 53.5% of the height (max half height 46.5%), a faint halo per column and a hairline axis.
 * Everything stays inside [DrawScope.size], so callers only need to clip to the cover shape.
 */
internal fun DrawScope.drawRawSArtworkSpectrum(levels: FloatArray, isPlaying: Boolean, color: Color = Color.White) {
    if (size.width <= 0f || size.height <= 0f || levels.isEmpty()) return
    val barCount = rawsArtworkBarCount(size.width / density, levels.size)
    val slotWidth = size.width / barCount
    val barWidth = (slotWidth * 0.34f).coerceIn(0.55f * density, 1.15f * density)
    val haloWidth = (barWidth * 1.32f).coerceAtMost(slotWidth * 0.58f)
    val coreRadius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
    val haloRadius = CornerRadius(haloWidth * 0.5f, haloWidth * 0.5f)
    val axisY = size.height * 0.535f
    // Leave room for the halo's 0.5 dp overhang so the tallest column is never cut off.
    val maximumHalfHeight = min(size.height * 0.465f, size.height - axisY - density)
    val minimumHalfHeight = 1.0f * density
    val totalAlpha = 0.96f * if (isPlaying) 1f else 0.86f
    val coreBrush = Brush.verticalGradient(
        0.00f to color.copy(alpha = 0.70f * totalAlpha),
        0.37f to color.copy(alpha = 0.88f * totalAlpha),
        0.50f to color.copy(alpha = 0.98f * totalAlpha),
        0.63f to color.copy(alpha = 0.88f * totalAlpha),
        1.00f to color.copy(alpha = 0.70f * totalAlpha),
        startY = axisY - maximumHalfHeight,
        endY = axisY + maximumHalfHeight
    )
    val haloColor = color.copy(alpha = 0.07f * totalAlpha)
    for (band in 0 until barCount) {
        val gated = ((rawsArtworkSampledValue(levels, band, barCount) - 0.006f) / 0.994f).coerceIn(0f, 1f)
        val shaped = gated.pow(0.96f)
        val halfHeight = minimumHalfHeight + (maximumHalfHeight - minimumHalfHeight).coerceAtLeast(0f) * shaped
        val x = (band + 0.5f) * slotWidth
        val top = axisY - halfHeight
        if (shaped > 0.01f) {
            drawRoundRect(
                color = haloColor,
                topLeft = Offset(x - haloWidth * 0.5f, top - 0.5f * density),
                size = Size(haloWidth, halfHeight * 2f + density),
                cornerRadius = haloRadius
            )
        }
        drawRoundRect(
            brush = coreBrush,
            topLeft = Offset(x - barWidth * 0.5f, top),
            size = Size(barWidth, halfHeight * 2f),
            cornerRadius = coreRadius
        )
    }
    drawLine(
        color = color.copy(alpha = 0.16f),
        start = Offset(0f, axisY),
        end = Offset(size.width, axisY),
        strokeWidth = 0.7f * density,
        cap = StrokeCap.Round
    )
}
