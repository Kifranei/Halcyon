/*
 * Binaural surround DSP effects for Halcyon's Media3 audio pipeline, moved verbatim from
 * DspEffects.kt. Ported from the RawS-Music DSP engine
 * (https://github.com/QFDY-GZC/RawS-Music, Apache-2.0). See THIRD_PARTY_LICENSES.md.
 */
package com.ella.music.dsp

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

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
