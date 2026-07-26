/*
 * Output/routing-stage DSP effects for Halcyon's Media3 audio pipeline, moved verbatim from
 * DspEffects.kt. Ported from / inspired by the RawS-Music DSP engine
 * (https://github.com/QFDY-GZC/RawS-Music, Apache-2.0). See THIRD_PARTY_LICENSES.md.
 */
package com.ella.music.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.pow

/**
 * Headphone crossfeed: filters the opposite channel to approximate natural speaker crosstalk.
 * Parameters follow RawS-Music's safe range and remain independent from stereo-width controls.
 */
class Crossfeed(sampleRate: Int) {
    private val rate = sampleRate.coerceAtLeast(8_000).toFloat()
    private val highPass = BiQuadFilter()
    private val lowPass = BiQuadFilter()
    private var enabled = false
    private var lowCutHz = 300f
    private var highCutHz = 2_000f
    private var gain = 10f.pow(-6f / 20f)

    fun setParams(enabled: Boolean, lowCutHz: Float, highCutHz: Float, attenuationDb: Float) {
        val enabling = !this.enabled && enabled
        this.enabled = enabled
        val nextLow = lowCutHz.coerceIn(50f, 1_000f)
        val nextHigh = highCutHz.coerceIn(500f, 8_000f).coerceAtLeast(nextLow + 100f)
        if (nextLow != this.lowCutHz || nextHigh != this.highCutHz) {
            this.lowCutHz = nextLow
            this.highCutHz = nextHigh
            highPass.setHighPass(rate, nextLow)
            lowPass.setLowPass(rate, nextHigh)
        }
        gain = 10f.pow(-attenuationDb.coerceIn(0f, 15f) / 20f)
        if (enabling) reset()
    }

    fun reset() {
        highPass.reset()
        lowPass.reset()
        highPass.setHighPass(rate, lowCutHz)
        lowPass.setLowPass(rate, highCutHz)
    }

    fun process(samples: FloatArray, frames: Int, channels: Int) {
        if (!enabled || channels != 2) return
        for (frame in 0 until frames) {
            val offset = frame * 2
            val left = samples[offset]
            val right = samples[offset + 1]
            val leftCross = lowPass.processSample(highPass.processSample(left, 0), 0)
            val rightCross = lowPass.processSample(highPass.processSample(right, 1), 1)
            samples[offset] = left + rightCross * gain
            samples[offset + 1] = right + leftCross * gain
        }
    }
}

/** Keeps bass centered below a crossover without collapsing the rest of the stereo image. */
class MonoBass(sampleRate: Int) {
    private val rate = sampleRate.coerceAtLeast(8_000).toFloat()
    private val lowPass = BiQuadFilter()
    private var enabled = false
    private var amount = 0f
    private var crossoverHz = 120f

    fun setParams(enabled: Boolean, crossoverHz: Float, amountPercent: Float) {
        val enabling = !this.enabled && enabled
        this.enabled = enabled
        val nextCrossover = crossoverHz.coerceIn(60f, 300f)
        if (nextCrossover != this.crossoverHz) {
            this.crossoverHz = nextCrossover
            lowPass.setLowPass(rate, nextCrossover)
        }
        amount = (amountPercent / 100f).coerceIn(0f, 1f)
        if (enabling) reset()
    }

    fun reset() {
        lowPass.reset()
        lowPass.setLowPass(rate, crossoverHz)
    }

    fun process(samples: FloatArray, frames: Int, channels: Int) {
        if (!enabled || amount <= 0f || channels != 2) return
        for (frame in 0 until frames) {
            val offset = frame * 2
            val left = samples[offset]
            val right = samples[offset + 1]
            val lowLeft = lowPass.processSample(left, 0)
            val lowRight = lowPass.processSample(right, 1)
            val sharedLow = (lowLeft + lowRight) * 0.5f
            samples[offset] = left + (sharedLow - lowLeft) * amount
            samples[offset + 1] = right + (sharedLow - lowRight) * amount
        }
    }
}

/**
 * A linked safety limiter placed last in the software DSP chain. It recovers gently after a
 * transient and prevents gain-bearing effects from reaching the PCM converter clipped.
 */
class PeakLimiter(sampleRate: Int) {
    private val rate = sampleRate.coerceAtLeast(8_000).toFloat()
    private var enabled = true
    private var currentGain = 1f
    private var targetGain = 1f
    private val attack = (1.0 - exp(-1.0 / (0.0015 * rate))).toFloat()
    private val release = (1.0 - exp(-1.0 / (0.150 * rate))).toFloat()

    fun setEnabled(enabled: Boolean) {
        if (this.enabled != enabled) reset()
        this.enabled = enabled
    }

    fun reset() {
        currentGain = 1f
        targetGain = 1f
    }

    fun process(samples: FloatArray, frames: Int, channels: Int) {
        if (!enabled || channels <= 0) return
        for (frame in 0 until frames) {
            var peak = 0f
            for (channel in 0 until channels) {
                peak = max(peak, abs(samples[frame * channels + channel].takeIf { it.isFinite() } ?: 0f))
            }
            targetGain = if (peak > CEILING) {
                minOf(targetGain, CEILING / peak.coerceAtLeast(1e-6f))
            } else {
                (targetGain + release).coerceAtMost(1f)
            }
            currentGain += (targetGain - currentGain) * if (targetGain < currentGain) attack else release
            for (channel in 0 until channels) {
                val index = frame * channels + channel
                samples[index] = (samples[index].takeIf { it.isFinite() } ?: 0f) * currentGain
            }
        }
    }

    private companion object {
        const val CEILING = 0.98f
    }
}

/**
 * Compact three-mode speaker enhancement inspired by RawS-Music's speaker-output effect.
 * It is deliberately conservative because Halcyon receives already-mixed PCM rather than RawS's
 * native output stream: each mode keeps a little headroom and avoids permanent full-band gain.
 */
class SpeakerOutput(sampleRate: Int) {
    enum class Mode { Elasticity, Powerful, Wide }

    private val rate = sampleRate.coerceAtLeast(8_000).toFloat()
    private val bodyLow = BiQuadFilter()
    private val bodyHigh = BiQuadFilter()
    private val presence = BiQuadFilter()
    private val lowPass = BiQuadFilter()
    private var enabled = false
    private var mode = Mode.Elasticity
    private var strength = 0.8f
    private var envelope = 0f
    private var bodyEnvelope = 0f
    private val attack = (1.0 - exp(-1.0 / (0.008 * rate))).toFloat()
    private val release = (1.0 - exp(-1.0 / (0.090 * rate))).toFloat()
    private val bodyAttack = (1.0 - exp(-1.0 / (0.025 * rate))).toFloat()
    private val bodyRelease = (1.0 - exp(-1.0 / (0.180 * rate))).toFloat()

    init { configureFilters() }

    fun setParams(enabled: Boolean, mode: Int, strengthPercent: Float) {
        val enabling = !this.enabled && enabled
        this.enabled = enabled
        this.mode = Mode.entries.getOrElse(mode.coerceIn(0, Mode.entries.lastIndex)) { Mode.Elasticity }
        strength = (strengthPercent / 100f).coerceIn(0f, 1f)
        if (enabling) reset()
    }

    fun reset() {
        envelope = 0f
        bodyEnvelope = 0f
        bodyLow.reset()
        bodyHigh.reset()
        presence.reset()
        lowPass.reset()
        configureFilters()
    }

    fun process(samples: FloatArray, frames: Int, channels: Int) {
        if (!enabled || strength <= 0f || channels !in 1..2) return
        when (mode) {
            Mode.Elasticity -> processElasticity(samples, frames, channels)
            Mode.Powerful -> processPowerful(samples, frames, channels)
            Mode.Wide -> processWide(samples, frames, channels)
        }
    }

    private fun processElasticity(samples: FloatArray, frames: Int, channels: Int) {
        for (frame in 0 until frames) {
            var peak = 0f
            val offset = frame * channels
            val bandLeft = bodyHigh.processSample(bodyLow.processSample(samples[offset], 0), 0)
            peak = abs(bandLeft)
            val bandRight = if (channels == 2) {
                bodyHigh.processSample(bodyLow.processSample(samples[offset + 1], 1), 1).also { peak = max(peak, abs(it)) }
            } else {
                0f
            }
            val coefficient = if (peak > envelope) attack else release
            envelope += (peak - envelope) * coefficient
            // Boost only when a transient exceeds the slower envelope.
            val transient = ((peak - envelope).coerceAtLeast(0f) / (peak + 1e-4f)).coerceIn(0f, 1f)
            val gain = 10f.pow((4.2f * strength * transient) / 20f)
            samples[offset] = (samples[offset] + bandLeft * (gain - 1f)).coerceIn(-0.98f, 0.98f)
            if (channels == 2) {
                samples[offset + 1] = (samples[offset + 1] + bandRight * (gain - 1f)).coerceIn(-0.98f, 0.98f)
            }
        }
    }

    private fun processPowerful(samples: FloatArray, frames: Int, channels: Int) {
        for (frame in 0 until frames) {
            var body = 0f
            for (channel in 0 until channels) {
                val input = samples[frame * channels + channel]
                body += bodyHigh.processSample(bodyLow.processSample(input, channel), channel)
            }
            body /= channels
            val magnitude = abs(body)
            val coefficient = if (magnitude > bodyEnvelope) bodyAttack else bodyRelease
            bodyEnvelope += (magnitude - bodyEnvelope) * coefficient
            val bodyGain = 10f.pow((4f * strength * (1f - bodyEnvelope).coerceIn(0.25f, 1f)) / 20f)
            for (channel in 0 until channels) {
                val index = frame * channels + channel
                val dry = samples[index]
                val harmonic = (body * body * body) * (0.18f * strength)
                val upper = presence.processSample(dry, channel) * (0.10f * strength)
                samples[index] = (dry + body * (bodyGain - 1f) + harmonic + upper).coerceIn(-0.98f, 0.98f)
            }
        }
    }

    private fun processWide(samples: FloatArray, frames: Int, channels: Int) {
        if (channels != 2) return
        for (frame in 0 until frames) {
            val offset = frame * 2
            val left = samples[offset]
            val right = samples[offset + 1]
            val lowLeft = lowPass.processSample(left, 0)
            val lowRight = lowPass.processSample(right, 1)
            val mid = (left + right) * 0.5f
            val side = (left - right) * 0.5f
            // Keep the bass near the center while expanding only the remaining side signal.
            val lowSide = (lowLeft - lowRight) * 0.5f
            val highSide = side - lowSide
            val expandedSide = highSide * (1f + 0.55f * strength) + lowSide * (1f - 0.55f * strength)
            samples[offset] = (mid + expandedSide).coerceIn(-0.98f, 0.98f)
            samples[offset + 1] = (mid - expandedSide).coerceIn(-0.98f, 0.98f)
        }
    }

    private fun configureFilters() {
        bodyLow.setHighPass(rate, 85f)
        bodyHigh.setLowPass(rate, 1_350f)
        presence.setHighPass(rate, 2_800f)
        lowPass.setLowPass(rate, 760f)
    }
}
