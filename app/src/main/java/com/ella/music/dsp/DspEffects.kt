/*
 * Additional software DSP effects for Halcyon's Media3 audio pipeline, in the spirit of the
 * RawS-Music DSP engine (https://github.com/QFDY-GZC/RawS-Music, Apache-2.0): a bass/treble shelf,
 * a feed-forward dynamics compressor, and a stereo widener. See THIRD_PARTY_LICENSES.md.
 */
package com.ella.music.dsp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Low-shelf (bass) + high-shelf (treble) tone control over interleaved float PCM.
 */
class ShelfEqualizer(sampleRate: Int, private val channels: Int) {
    private val sampleRateFloat = sampleRate.toFloat()
    private val bass = BiQuadFilter()
    private val treble = BiQuadFilter()
    private var bassGainDb = 0f
    private var trebleGainDb = 0f

    fun setGains(bassDb: Float, trebleDb: Float) {
        bassGainDb = bassDb
        trebleGainDb = trebleDb
        bass.setLowShelf(sampleRateFloat, BASS_FREQ, bassDb)
        treble.setHighShelf(sampleRateFloat, TREBLE_FREQ, trebleDb)
    }

    fun isActive(): Boolean = bassGainDb != 0f || trebleGainDb != 0f

    fun reset() {
        bass.reset()
        treble.reset()
        setGains(bassGainDb, trebleGainDb)
    }

    fun process(samples: FloatArray, frames: Int) {
        if (!isActive()) return
        val doBass = bassGainDb != 0f
        val doTreble = trebleGainDb != 0f
        for (i in 0 until frames) {
            for (ch in 0 until channels) {
                val index = i * channels + ch
                var s = samples[index]
                if (doBass) s = bass.processSample(s, ch)
                if (doTreble) s = treble.processSample(s, ch)
                samples[index] = s
            }
        }
    }

    companion object {
        const val BASS_FREQ = 100f
        const val TREBLE_FREQ = 10_000f
    }
}

/**
 * Feed-forward peak compressor with a stereo-linked envelope follower.
 * Fixed 5 ms attack / 120 ms release; threshold / ratio / makeup are user-controlled.
 */
class Compressor(sampleRate: Int, private val channels: Int) {
    private val attackCoeff = exp(-1.0 / (0.005 * sampleRate)).toFloat()
    private val releaseCoeff = exp(-1.0 / (0.120 * sampleRate)).toFloat()

    private var enabled = false
    private var thresholdDb = 0f
    private var ratio = 1f
    private var makeupLin = 1f
    private var envelope = 0f

    fun setParams(enabled: Boolean, thresholdDb: Float, ratio: Float, makeupDb: Float) {
        this.enabled = enabled
        this.thresholdDb = thresholdDb
        this.ratio = ratio.coerceAtLeast(1f)
        this.makeupLin = 10f.pow(makeupDb / 20f)
    }

    fun isActive(): Boolean = enabled

    fun reset() {
        envelope = 0f
    }

    fun process(samples: FloatArray, frames: Int) {
        if (!enabled) return
        val log2010 = ln(10f)
        for (i in 0 until frames) {
            var peak = 0f
            for (ch in 0 until channels) {
                peak = max(peak, abs(samples[i * channels + ch]))
            }
            val coeff = if (peak > envelope) attackCoeff else releaseCoeff
            envelope = coeff * envelope + (1f - coeff) * peak
            var gain = 1f
            if (envelope > 1e-6f) {
                val envDb = 20f * ln(envelope) / log2010
                if (envDb > thresholdDb) {
                    val overDb = envDb - thresholdDb
                    val reduceDb = overDb - overDb / ratio
                    gain = 10f.pow(-reduceDb / 20f)
                }
            }
            val total = gain * makeupLin
            for (ch in 0 until channels) {
                samples[i * channels + ch] *= total
            }
        }
    }
}

/**
 * Mid/side stereo widener. width = 1.0 is unchanged, <1 narrows toward mono, >1 widens.
 */
class StereoWidener {
    private var width = 1f

    fun setWidth(width: Float) {
        this.width = width.coerceIn(0f, 2f)
    }

    fun isActive(): Boolean = width != 1f

    fun process(samples: FloatArray, frames: Int, channels: Int) {
        if (channels != 2 || width == 1f) return
        for (i in 0 until frames) {
            val l = samples[i * 2]
            val r = samples[i * 2 + 1]
            val mid = (l + r) * 0.5f
            val side = (l - r) * 0.5f * width
            samples[i * 2] = (mid + side).coerceIn(-1f, 1f)
            samples[i * 2 + 1] = (mid - side).coerceIn(-1f, 1f)
        }
    }
}

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

/**
 * 2D binaural surround port of RawS-Music's Apache-2.0 Surround360 effect. It uses a Woodworth
 * spherical-head approximation (ILD, ITD, head shadow, and rear decorrelation) on stereo PCM.
 */
class Surround360(sampleRate: Int) {
    private var sampleRate = sampleRate.coerceAtLeast(8_000)
    private var enabled = false
    private var intensity = 0.5f
    private var azimuthRadians = 0f
    private var rotationRadiansPerSecond = 0f
    private var gainLeft = 1f
    private var gainRight = 1f
    private var delaySamples = 0f
    private var rearMix = 0f
    private var smoothGainLeft = 1f
    private var smoothGainRight = 1f
    private var smoothDelay = 0f
    private var smoothAzimuth = 0f
    private var writeIndex = 0
    private val delayLeft = FloatArray(DELAY_BUFFER_SIZE)
    private val delayRight = FloatArray(DELAY_BUFFER_SIZE)
    private var shadowLeft = OnePoleLowPass(this.sampleRate)
    private var shadowRight = OnePoleLowPass(this.sampleRate)
    private var allPassLeft = FirstOrderAllPass(this.sampleRate, 700f)
    private var allPassRight = FirstOrderAllPass(this.sampleRate, 1_100f)
    private var smoothing = smoothingFor(this.sampleRate)

    fun setParams(
        enabled: Boolean,
        intensityPercent: Float,
        azimuthDegrees: Float,
        rotationDegreesPerSecond: Float = 0f
    ) {
        val wasEnabled = this.enabled
        this.enabled = enabled
        intensity = (intensityPercent / 100f).coerceIn(0f, 1f)
        val degrees = ((azimuthDegrees % 360f) + 540f) % 360f - 180f
        azimuthRadians = Math.toRadians(degrees.toDouble()).toFloat()
        rotationRadiansPerSecond = Math.toRadians(rotationDegreesPerSecond.coerceIn(0f, 360f).toDouble()).toFloat()
        updateParams()
        if (!wasEnabled && enabled) reset()
    }

    fun reset() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        writeIndex = 0
        shadowLeft.reset()
        shadowRight.reset()
        allPassLeft.reset()
        allPassRight.reset()
        smoothGainLeft = gainLeft
        smoothGainRight = gainRight
        smoothDelay = delaySamples
        smoothAzimuth = azimuthRadians
    }

    fun process(samples: FloatArray, frames: Int, channels: Int) {
        if (!enabled || channels != 2 || intensity <= 0f) return
        if (rotationRadiansPerSecond != 0f) {
            azimuthRadians = wrapRadians(azimuthRadians + rotationRadiansPerSecond * frames / sampleRate)
            updateParams()
        }
        val a = smoothing
        val b = 1f - a
        var leftGain = smoothGainLeft
        var rightGain = smoothGainRight
        var delay = smoothDelay
        var angle = smoothAzimuth
        var index = writeIndex
        repeat(frames) { frame ->
            leftGain = leftGain * b + gainLeft * a
            rightGain = rightGain * b + gainRight * a
            delay = delay * b + delaySamples * a
            angle = angle * b + azimuthRadians * a
            val offset = frame * 2
            val inLeft = samples[offset]
            val inRight = samples[offset + 1]
            delayLeft[index] = inLeft
            delayRight[index] = inRight
            val readPosition = index - delay
            val floorPosition = kotlin.math.floor(readPosition).toInt()
            val fraction = readPosition - floorPosition
            val read0 = floorPosition and (DELAY_BUFFER_SIZE - 1)
            val read1 = (read0 + 1) and (DELAY_BUFFER_SIZE - 1)
            val delayedLeft = delayLeft[read0] * (1f - fraction) + delayLeft[read1] * fraction
            val delayedRight = delayRight[read0] * (1f - fraction) + delayRight[read1] * fraction
            var outLeft: Float
            var outRight: Float
            if (angle > 0f) {
                outLeft = delayedLeft
                outRight = inRight
            } else {
                outLeft = inLeft
                outRight = delayedRight
            }
            outLeft = shadowLeft.process(outLeft * leftGain)
            outRight = shadowRight.process(outRight * rightGain)
            if (rearMix > 0.001f) {
                outLeft = outLeft * (1f - rearMix) + allPassLeft.process(inLeft) * rearMix
                outRight = outRight * (1f - rearMix) + allPassRight.process(inRight) * rearMix
            }
            samples[offset] = outLeft.coerceIn(-1f, 1f)
            samples[offset + 1] = outRight.coerceIn(-1f, 1f)
            index = (index + 1) and (DELAY_BUFFER_SIZE - 1)
        }
        smoothGainLeft = leftGain
        smoothGainRight = rightGain
        smoothDelay = delay
        smoothAzimuth = angle
        writeIndex = index
    }

    private fun updateParams() {
        val sine = sin(azimuthRadians)
        val cosine = cos(azimuthRadians)
        val left = 1f - sine * 0.5f * intensity
        val right = 1f + sine * 0.5f * intensity
        val normalization = 1.4142f / sqrt(left * left + right * right)
        gainLeft = left * normalization
        gainRight = right * normalization
        delaySamples = (HEAD_RADIUS / SPEED_OF_SOUND * (azimuthRadians + sine) * intensity).let { abs(it) * sampleRate }
            .coerceAtMost((DELAY_BUFFER_SIZE - 2).toFloat())
        val shadowFrequency = (15_000f - 13_000f * abs(sine) * intensity).coerceAtLeast(1_000f)
        if (sine > 0f) {
            shadowLeft.setCutoff(shadowFrequency)
            shadowRight.setCutoff(20_000f)
        } else {
            shadowLeft.setCutoff(20_000f)
            shadowRight.setCutoff(shadowFrequency)
        }
        rearMix = max(0f, -cosine) * 0.4f * intensity
    }

    private class OnePoleLowPass(private val sampleRate: Int) {
        private var coefficient = 0f
        private var state = 0f

        fun setCutoff(cutoff: Float) {
            coefficient = (1f - exp(-2.0 * Math.PI * cutoff.coerceAtMost(sampleRate * 0.45f) / sampleRate)).toFloat()
        }

        fun process(sample: Float): Float {
            state += coefficient * (sample - state)
            return state
        }

        fun reset() { state = 0f }
    }

    private class FirstOrderAllPass(sampleRate: Int, frequency: Float) {
        private val coefficient = ((1.0 - kotlin.math.tan(Math.PI * frequency / sampleRate)) /
            (1.0 + kotlin.math.tan(Math.PI * frequency / sampleRate))).toFloat()
        private var previousInput = 0f
        private var previousOutput = 0f

        fun process(sample: Float): Float {
            val output = -coefficient * sample + previousInput + coefficient * previousOutput
            previousInput = sample
            previousOutput = output
            return output
        }

        fun reset() {
            previousInput = 0f
            previousOutput = 0f
        }
    }

    private fun smoothingFor(rate: Int): Float = (1.0 - exp(-1.0 / (0.010 * rate))).toFloat()

    private fun wrapRadians(value: Float): Float {
        val full = (Math.PI * 2.0).toFloat()
        return ((value + Math.PI.toFloat()) % full + full) % full - Math.PI.toFloat()
    }

    companion object {
        private const val HEAD_RADIUS = 0.0875f
        private const val SPEED_OF_SOUND = 343f
        private const val DELAY_BUFFER_SIZE = 256
    }
}

/**
 * 3D extension of [Surround360] ported from RawS-Music's Panoramic360 stage. It keeps the
 * binaural ILD/ITD cues, then adds elevation-dependent pinna EQ, early reflections, and a small
 * feedback-delay room. The effect is intentionally self-contained so it can run in Media3's
 * Kotlin PCM chain without RawS-Music's native output engine or external BRIR files.
 */
class Panoramic360(sampleRate: Int) {
    private val sampleRate = sampleRate.coerceAtLeast(8_000)
    private val surround = Surround360(this.sampleRate)
    private val pinnaLeft = BiQuadFilter()
    private val pinnaRight = BiQuadFilter()
    private val reflectionLeft = FloatArray(REFLECTION_BUFFER_SIZE)
    private val reflectionRight = FloatArray(REFLECTION_BUFFER_SIZE)
    private val fdnBuffers = Array(FDN_ORDER) { FloatArray(FDN_BUFFER_SIZE) }
    private val fdnDamping = Array(FDN_ORDER) { BiQuadFilter() }
    private val reflectionDelays = IntArray(REFLECTION_COUNT)
    private val reflectionGainLeft = FloatArray(REFLECTION_COUNT)
    private val reflectionGainRight = FloatArray(REFLECTION_COUNT)
    private val fdnDelays = IntArray(FDN_ORDER)

    private var enabled = false
    private var intensity = 0.5f
    private var azimuthDegrees = 0f
    private var elevationDegrees = 0f
    private var reflectionWriteIndex = 0
    private var fdnWriteIndex = 0
    private var reflectionHpPreviousInput = 0f
    private var reflectionHpPreviousOutput = 0f
    private var fdnHpPreviousInput = 0f
    private var fdnHpPreviousOutput = 0f
    private var reflectionMix = 0f
    private var roomMix = 0f
    private var fdnFeedback = 0.4f
    private var pinnaActive = false

    fun setParams(
        enabled: Boolean,
        intensityPercent: Float,
        azimuthDegrees: Float,
        elevationDegrees: Float
    ) {
        val wasEnabled = this.enabled
        this.enabled = enabled
        intensity = (intensityPercent / 100f).coerceIn(0f, 1f)
        this.azimuthDegrees = azimuthDegrees.coerceIn(-180f, 180f)
        this.elevationDegrees = elevationDegrees.coerceIn(-90f, 90f)
        updateParameters()
        if (!wasEnabled && enabled) reset()
    }

    fun reset() {
        surround.reset()
        pinnaLeft.reset()
        pinnaRight.reset()
        reflectionLeft.fill(0f)
        reflectionRight.fill(0f)
        fdnBuffers.forEach { it.fill(0f) }
        fdnDamping.forEach { it.reset() }
        reflectionWriteIndex = 0
        fdnWriteIndex = 0
        reflectionHpPreviousInput = 0f
        reflectionHpPreviousOutput = 0f
        fdnHpPreviousInput = 0f
        fdnHpPreviousOutput = 0f
        updateParameters()
    }

    fun process(samples: FloatArray, frames: Int, channels: Int) {
        if (!enabled || channels != 2 || intensity <= 0f) return

        surround.process(samples, frames, channels)
        if (pinnaActive) {
            repeat(frames) { frame ->
                val offset = frame * 2
                samples[offset] = pinnaLeft.processSample(samples[offset], 0)
                samples[offset + 1] = pinnaRight.processSample(samples[offset + 1], 0)
            }
        }

        var reflectionIndex = reflectionWriteIndex
        var reflectionPreviousInput = reflectionHpPreviousInput
        var reflectionPreviousOutput = reflectionHpPreviousOutput
        repeat(frames) { frame ->
            val offset = frame * 2
            val dryLeft = samples[offset]
            val dryRight = samples[offset + 1]
            val mono = (dryLeft + dryRight) * 0.5f
            val highPassed = REFLECTION_HP_COEFFICIENT * (
                reflectionPreviousOutput + mono - reflectionPreviousInput
            )
            reflectionPreviousInput = mono
            reflectionPreviousOutput = highPassed
            reflectionLeft[reflectionIndex] = highPassed
            reflectionRight[reflectionIndex] = highPassed

            var reflectedLeft = 0f
            var reflectedRight = 0f
            for (reflection in 0 until REFLECTION_COUNT) {
                val readIndex = (reflectionIndex - reflectionDelays[reflection]) and REFLECTION_MASK
                reflectedLeft += reflectionLeft[readIndex] * reflectionGainLeft[reflection]
                reflectedRight += reflectionRight[readIndex] * reflectionGainRight[reflection]
            }
            samples[offset] = (dryLeft + reflectedLeft * reflectionMix).coerceIn(-1f, 1f)
            samples[offset + 1] = (dryRight + reflectedRight * reflectionMix).coerceIn(-1f, 1f)
            reflectionIndex = (reflectionIndex + 1) and REFLECTION_MASK
        }
        reflectionWriteIndex = reflectionIndex
        reflectionHpPreviousInput = reflectionPreviousInput
        reflectionHpPreviousOutput = reflectionPreviousOutput

        if (roomMix <= 0.001f) return
        var fdnIndex = fdnWriteIndex
        var fdnPreviousInput = fdnHpPreviousInput
        var fdnPreviousOutput = fdnHpPreviousOutput
        repeat(frames) { frame ->
            val offset = frame * 2
            val input = (samples[offset] + samples[offset + 1]) * 0.5f
            val highPassed = FDN_HP_COEFFICIENT * (fdnPreviousOutput + input - fdnPreviousInput)
            fdnPreviousInput = input
            fdnPreviousOutput = highPassed

            val d0 = fdnDamping[0].processSample(fdnBuffers[0][(fdnIndex - fdnDelays[0]) and FDN_MASK], 0)
            val d1 = fdnDamping[1].processSample(fdnBuffers[1][(fdnIndex - fdnDelays[1]) and FDN_MASK], 0)
            val d2 = fdnDamping[2].processSample(fdnBuffers[2][(fdnIndex - fdnDelays[2]) and FDN_MASK], 0)
            val d3 = fdnDamping[3].processSample(fdnBuffers[3][(fdnIndex - fdnDelays[3]) and FDN_MASK], 0)
            val feedback = fdnFeedback * 0.5f
            fdnBuffers[0][fdnIndex] = highPassed + (d0 + d1 + d2 + d3) * feedback
            fdnBuffers[1][fdnIndex] = highPassed + (d0 - d1 + d2 - d3) * feedback
            fdnBuffers[2][fdnIndex] = highPassed + (d0 + d1 - d2 - d3) * feedback
            fdnBuffers[3][fdnIndex] = highPassed + (d0 - d1 - d2 + d3) * feedback

            val wetLeft = (d0 + d1 - d2 - d3) * 0.25f
            val wetRight = (d0 - d1 + d2 - d3) * 0.25f
            samples[offset] = (samples[offset] * (1f - roomMix) + wetLeft * roomMix).coerceIn(-1f, 1f)
            samples[offset + 1] = (samples[offset + 1] * (1f - roomMix) + wetRight * roomMix).coerceIn(-1f, 1f)
            fdnIndex = (fdnIndex + 1) and FDN_MASK
        }
        fdnWriteIndex = fdnIndex
        fdnHpPreviousInput = fdnPreviousInput
        fdnHpPreviousOutput = fdnPreviousOutput
    }

    private fun updateParameters() {
        surround.setParams(enabled, intensity * 100f, azimuthDegrees, 0f)
        val azimuthRadians = Math.toRadians(azimuthDegrees.toDouble()).toFloat()
        val elevationRadians = Math.toRadians(elevationDegrees.toDouble()).toFloat()
        val pinnaEffect = sin(elevationRadians)
        val azimuthOffset = sin(azimuthRadians) * 0.3f
        val pinnaGainLeft = pinnaEffect * (1f + azimuthOffset) * 6f * intensity
        val pinnaGainRight = pinnaEffect * (1f - azimuthOffset) * 6f * intensity
        pinnaLeft.setHighShelf(sampleRate.toFloat(), PINNA_FREQUENCY_HZ, pinnaGainLeft)
        pinnaRight.setHighShelf(sampleRate.toFloat(), PINNA_FREQUENCY_HZ, pinnaGainRight)
        pinnaActive = abs(pinnaGainLeft) > 0.1f || abs(pinnaGainRight) > 0.1f

        val basePanLeft = floatArrayOf(0.3f, 0.7f, 0.3f, 0.7f, 0.3f, 0.7f)
        val reflectionGain = floatArrayOf(0.35f, 0.35f, 0.30f, 0.30f, 0.20f, 0.20f)
        val reflectionDelayMs = floatArrayOf(5.8f, 6.2f, 8.3f, 8.7f, 12.1f, 12.5f)
        val cosine = cos(azimuthRadians)
        val sine = sin(azimuthRadians)
        for (reflection in 0 until REFLECTION_COUNT) {
            reflectionDelays[reflection] = (reflectionDelayMs[reflection] * 0.001f * sampleRate)
                .toInt().coerceIn(1, REFLECTION_BUFFER_SIZE - 1)
            val baseLeft = basePanLeft[reflection]
            val baseRight = 1f - baseLeft
            val left = (baseLeft * (1f + cosine * 0.3f) - baseRight * sine * 0.2f).coerceIn(0f, 1f)
            val right = (baseRight * (1f - cosine * 0.3f) + baseLeft * sine * 0.2f).coerceIn(0f, 1f)
            reflectionGainLeft[reflection] = reflectionGain[reflection] * left
            reflectionGainRight[reflection] = reflectionGain[reflection] * right
        }
        reflectionMix = intensity * 0.42f

        val primes = intArrayOf(113, 163, 223, 311)
        val baseTimeMs = 30f + intensity * 50f
        val dampingFrequency = 4_000f + (1f - 0.4f) * 12_000f
        for (line in 0 until FDN_ORDER) {
            fdnDelays[line] = (baseTimeMs * 0.001f * sampleRate * primes[line] / primes[0])
                .toInt().coerceIn(1, FDN_BUFFER_SIZE - 1)
            fdnDamping[line].setLowPass(sampleRate.toFloat(), dampingFrequency)
        }
        fdnFeedback = 0.4f + intensity * 0.22f
        roomMix = intensity * 0.12f
    }

    private companion object {
        const val REFLECTION_COUNT = 6
        const val REFLECTION_BUFFER_SIZE = 4_096
        const val REFLECTION_MASK = REFLECTION_BUFFER_SIZE - 1
        const val FDN_ORDER = 4
        const val FDN_BUFFER_SIZE = 8_192
        const val FDN_MASK = FDN_BUFFER_SIZE - 1
        const val PINNA_FREQUENCY_HZ = 8_000f
        const val REFLECTION_HP_COEFFICIENT = 0.995f
        const val FDN_HP_COEFFICIENT = 0.995f
    }
}
