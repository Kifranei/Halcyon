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
