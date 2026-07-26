/*
 * Tone-shaping DSP effects for Halcyon's Media3 audio pipeline, moved verbatim from DspEffects.kt.
 * Ported from / inspired by the RawS-Music DSP engine
 * (https://github.com/QFDY-GZC/RawS-Music, Apache-2.0). See THIRD_PARTY_LICENSES.md.
 */
package com.ella.music.dsp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Equal-loudness contour and left/right balance ported from RawS-Music's Apache-2.0 processor.
 * The contour is deliberately applied before spatial effects, while balance only attenuates the
 * opposite channel so changing it never clips a PCM stream.
 */
class LoudnessBalance(sampleRate: Int, private val channels: Int) {
    private val rate = sampleRate.coerceAtLeast(8_000)
    private val lowAlpha = onePoleCoefficient(120f)
    private val highAlpha = onePoleCoefficient(6_000f)
    private val smoothing = (1.0 - exp(-1.0 / (0.020 * rate))).toFloat()
    private val lowState = FloatArray(2)
    private val highLowState = FloatArray(2)
    private var enabled = false
    private var targetLoudness = 0f
    private var targetBalance = 0f
    private var smoothedLoudness = 0f
    private var smoothedBalance = 0f

    fun setParams(enabled: Boolean, loudnessPercent: Float, balancePercent: Float) {
        val enabling = !this.enabled && enabled
        this.enabled = enabled
        targetLoudness = (loudnessPercent / 100f).coerceIn(0f, 1f)
        targetBalance = (balancePercent / 100f).coerceIn(-1f, 1f)
        if (enabling) reset()
    }

    fun reset() {
        lowState.fill(0f)
        highLowState.fill(0f)
        smoothedLoudness = if (enabled) targetLoudness else 0f
        smoothedBalance = targetBalance
    }

    fun process(samples: FloatArray, frames: Int) {
        if (!enabled) return
        for (frame in 0 until frames) {
            smoothedLoudness += (targetLoudness - smoothedLoudness) * smoothing
            smoothedBalance += (targetBalance - smoothedBalance) * smoothing
            val lowGain = 10f.pow(11f * smoothedLoudness / 20f)
            val highGain = 10f.pow(4.5f * smoothedLoudness / 20f)
            val headroom = 10f.pow(-2.2f * smoothedLoudness / 20f)
            val leftGain = if (smoothedBalance > 0f) cos(smoothedBalance * Math.PI.toFloat() * 0.5f) else 1f
            val rightGain = if (smoothedBalance < 0f) cos(-smoothedBalance * Math.PI.toFloat() * 0.5f) else 1f
            val processedChannels = minOf(channels, 2)
            for (channel in 0 until processedChannels) {
                val index = frame * channels + channel
                val input = samples[index].takeIf { it.isFinite() } ?: 0f
                lowState[channel] += lowAlpha * (input - lowState[channel])
                highLowState[channel] += highAlpha * (input - highLowState[channel])
                val high = input - highLowState[channel]
                val balanceGain = if (channel == 0) leftGain else rightGain
                samples[index] = (input + lowState[channel] * (lowGain - 1f) + high * (highGain - 1f)) * headroom * balanceGain
            }
        }
    }

    private fun onePoleCoefficient(frequency: Float): Float =
        (1.0 - exp(-2.0 * Math.PI * frequency / rate)).toFloat()
}

/**
 * Program-dependent tone correction with a linked de-esser, adapted from RawS-Music's
 * DynamicEqProcessor. The detector is shared by both channels so vocals stay centered.
 */
class DynamicEq(sampleRate: Int) {
    private val rate = sampleRate.coerceAtLeast(8_000).toFloat()
    private val bodyLowState = FloatArray(2)
    private val presenceLowState = FloatArray(2)
    private val presenceHighState = FloatArray(2)
    private val deEsserLowState = FloatArray(2)
    private var enabled = false
    private var intensity = 0.5f
    private var deEsser = 0.45f
    private var deEsserFrequency = 6_500f
    private var programEnvelope = 0f
    private var sibilanceEnvelope = 0f
    private var reductionGain = 1f

    fun setParams(enabled: Boolean, intensityPercent: Float, deEsserPercent: Float, deEsserFrequencyHz: Float) {
        val enabling = !this.enabled && enabled
        this.enabled = enabled
        intensity = (intensityPercent / 100f).coerceIn(0f, 1f)
        deEsser = (deEsserPercent / 100f).coerceIn(0f, 1f)
        deEsserFrequency = deEsserFrequencyHz.coerceIn(4_000f, 10_000f)
        if (enabling) reset()
    }

    fun reset() {
        bodyLowState.fill(0f)
        presenceLowState.fill(0f)
        presenceHighState.fill(0f)
        deEsserLowState.fill(0f)
        programEnvelope = 0f
        sibilanceEnvelope = 0f
        reductionGain = 1f
    }

    fun process(samples: FloatArray, frames: Int, channels: Int) {
        if (!enabled || channels !in 1..2) return
        val bodyAlpha = onePoleCoefficient(180f)
        val presenceLowAlpha = onePoleCoefficient(1_800f)
        val presenceHighAlpha = onePoleCoefficient(5_200f)
        val deEsserAlpha = onePoleCoefficient(deEsserFrequency)
        val programAttack = envelopeCoefficient(8f)
        val programRelease = envelopeCoefficient(180f)
        val sibilanceAttack = envelopeCoefficient(1.5f)
        val sibilanceRelease = envelopeCoefficient(90f)
        val gainAttack = envelopeCoefficient(2f)
        val gainRelease = envelopeCoefficient(110f)
        val threshold = 10f.pow(-30f / 20f)

        for (frame in 0 until frames) {
            val offset = frame * channels
            val inputLeft = samples[offset].takeIf { it.isFinite() } ?: 0f
            val inputRight = if (channels == 2) samples[offset + 1].takeIf { it.isFinite() } ?: 0f else 0f
            deEsserLowState[0] += deEsserAlpha * (inputLeft - deEsserLowState[0])
            val highLeft = inputLeft - deEsserLowState[0]
            var peak = abs(inputLeft)
            var highPeak = abs(highLeft)
            val highRight = if (channels == 2) {
                deEsserLowState[1] += deEsserAlpha * (inputRight - deEsserLowState[1])
                val result = inputRight - deEsserLowState[1]
                peak = max(peak, abs(inputRight))
                highPeak = max(highPeak, abs(result))
                result
            } else 0f
            programEnvelope += (peak - programEnvelope) * if (peak > programEnvelope) programAttack else programRelease
            sibilanceEnvelope += (highPeak - sibilanceEnvelope) * if (highPeak > sibilanceEnvelope) sibilanceAttack else sibilanceRelease

            val targetReduction = if (sibilanceEnvelope > threshold) {
                val over = ((sibilanceEnvelope - threshold) / (1f - threshold)).coerceIn(0f, 1f)
                10f.pow((-12f * deEsser * sqrt(over)) / 20f)
            } else {
                1f
            }
            reductionGain += (targetReduction - reductionGain) * if (targetReduction < reductionGain) gainAttack else gainRelease
            val quiet = ((0.65f - programEnvelope) / 0.55f).coerceIn(0f, 1f)
            val dense = ((programEnvelope - 0.55f) / 0.35f).coerceIn(0f, 1f)
            val bodyGain = 10f.pow((intensity * (5f * quiet - 2f * dense)) / 20f)
            val presenceGain = 10f.pow((2f * intensity * quiet) / 20f)
            val headroom = 10f.pow((-1.2f * intensity * quiet) / 20f)

            bodyLowState[0] += bodyAlpha * (inputLeft - bodyLowState[0])
            presenceLowState[0] += presenceLowAlpha * (inputLeft - presenceLowState[0])
            presenceHighState[0] += presenceHighAlpha * (inputLeft - presenceHighState[0])
            val presenceLeft = presenceHighState[0] - presenceLowState[0]
            val deEssedLeft = inputLeft + highLeft * (reductionGain - 1f)
            samples[offset] = ((deEssedLeft + bodyLowState[0] * (bodyGain - 1f) + presenceLeft * (presenceGain - 1f)) * headroom)
                .takeIf { it.isFinite() }
                ?: 0f
            if (channels == 2) {
                bodyLowState[1] += bodyAlpha * (inputRight - bodyLowState[1])
                presenceLowState[1] += presenceLowAlpha * (inputRight - presenceLowState[1])
                presenceHighState[1] += presenceHighAlpha * (inputRight - presenceHighState[1])
                val presenceRight = presenceHighState[1] - presenceLowState[1]
                val deEssedRight = inputRight + highRight * (reductionGain - 1f)
                samples[offset + 1] = ((deEssedRight + bodyLowState[1] * (bodyGain - 1f) + presenceRight * (presenceGain - 1f)) * headroom)
                    .takeIf { it.isFinite() }
                    ?: 0f
            }
        }
    }

    private fun onePoleCoefficient(frequency: Float): Float =
        (1.0 - exp(-2.0 * Math.PI * frequency.coerceAtMost(rate * 0.45f) / rate)).toFloat()

    private fun envelopeCoefficient(milliseconds: Float): Float =
        (1.0 - exp(-1.0 / (milliseconds * 0.001 * rate))).toFloat()
}

/**
 * Four-stage zero-delay-feedback ladder filter adapted from RawS-Music's Apache-2.0
 * implementation. Two-times oversampling and parameter smoothing keep resonance sweeps stable
 * in Halcyon's managed PCM pipeline.
 */
class MoogLadderFilter(sampleRate: Int) {
    enum class Mode { LowPass24, LowPass12, HighPass24, BandPass12, Notch }

    private val rate = sampleRate.coerceAtLeast(8_000).toFloat()
    private val state = Array(2) { FloatArray(4) }
    private val previousInput = FloatArray(2)
    private var enabled = false
    private var mode = Mode.LowPass24
    private var cutoffHz = 12_000f
    private var resonance = 0.2f
    private var driveDb = 0f
    private var targetMix = 1f
    private var smoothedMix = 0f
    private var smoothedG = coefficientFor(cutoffHz)
    private var smoothedResonance = resonance
    private var smoothedDriveDb = driveDb
    private val smoothing = (1.0 - exp(-1.0 / (0.015 * rate))).toFloat()

    fun setParams(
        enabled: Boolean,
        mode: Int,
        cutoffHz: Float,
        resonancePercent: Float,
        driveDb: Float,
        mixPercent: Float
    ) {
        val enabling = !this.enabled && enabled
        this.enabled = enabled
        this.mode = Mode.entries.getOrElse(mode.coerceIn(0, Mode.entries.lastIndex)) { Mode.LowPass24 }
        this.cutoffHz = cutoffHz.coerceIn(20f, 20_000f)
        resonance = (resonancePercent / 100f).coerceIn(0f, 1f)
        this.driveDb = driveDb.coerceIn(0f, 18f)
        targetMix = (mixPercent / 100f).coerceIn(0f, 1f)
        if (enabling) reset()
    }

    fun reset() {
        state.forEach { it.fill(0f) }
        previousInput.fill(0f)
        smoothedMix = if (enabled) targetMix else 0f
        smoothedG = coefficientFor(cutoffHz)
        smoothedResonance = resonance
        smoothedDriveDb = driveDb
    }

    fun process(samples: FloatArray, frames: Int, channels: Int) {
        if (!enabled || channels !in 1..2) return
        val targetG = coefficientFor(cutoffHz)
        for (frame in 0 until frames) {
            smoothedG += (targetG - smoothedG) * smoothing
            smoothedResonance += (resonance - smoothedResonance) * smoothing
            smoothedDriveDb += (driveDb - smoothedDriveDb) * smoothing
            smoothedMix += (targetMix - smoothedMix) * smoothing
            val feedback = 3.95f * smoothedResonance
            for (channel in 0 until channels) {
                val index = frame * channels + channel
                val dry = samples[index].takeIf { it.isFinite() } ?: 0f
                val midpoint = (previousInput[channel] + dry) * 0.5f
                val first = processOversampled(saturate(midpoint, smoothedDriveDb), channel, smoothedG, feedback)
                val second = processOversampled(saturate(dry, smoothedDriveDb), channel, smoothedG, feedback)
                previousInput[channel] = dry
                val wet = (first + second) * 0.5f
                samples[index] = (dry + (wet - dry) * smoothedMix).takeIf { it.isFinite() } ?: 0f
            }
        }
    }

    private fun coefficientFor(cutoff: Float): Float {
        val oversampledRate = rate * 2f
        val g = kotlin.math.tan(Math.PI.toFloat() * cutoff.coerceAtMost(oversampledRate * 0.45f) / oversampledRate)
        return g / (1f + g)
    }

    private fun saturate(sample: Float, drive: Float): Float {
        if (drive <= 0.01f) return sample
        val gain = 10f.pow(drive / 20f)
        return kotlin.math.tanh(sample * gain) / kotlin.math.tanh(gain)
    }

    private fun processOversampled(input: Float, channel: Int, g: Float, feedback: Float): Float {
        val z = state[channel]
        val oneMinusG = 1f - g
        val g2 = g * g
        val g3 = g2 * g
        val g4 = g2 * g2
        val sigma = g3 * oneMinusG * z[0] + g2 * oneMinusG * z[1] + g * oneMinusG * z[2] + oneMinusG * z[3]
        val filteredInput = input - feedback * ((g4 * input + sigma) / (1f + feedback * g4))
        var stageInput = filteredInput
        var y1 = 0f
        var y2 = 0f
        var y3 = 0f
        var y4 = 0f
        for (stage in 0..3) {
            val v = (stageInput - z[stage]) * g
            var output = v + z[stage]
            z[stage] = output + v
            if (!output.isFinite() || abs(output) > 16f) {
                output = kotlin.math.tanh(if (output.isFinite()) output else 0f)
                z[stage] = output
            }
            when (stage) {
                0 -> y1 = output
                1 -> y2 = output
                2 -> y3 = output
                else -> y4 = output
            }
            stageInput = output
        }
        return when (mode) {
            Mode.LowPass24 -> y4
            Mode.LowPass12 -> y2
            Mode.HighPass24 -> filteredInput - 4f * y1 + 6f * y2 - 4f * y3 + y4
            Mode.BandPass12 -> 4f * (y2 - 2f * y3 + y4)
            Mode.Notch -> y4 + filteredInput - 4f * y1 + 6f * y2 - 4f * y3 + y4
        }
    }
}
