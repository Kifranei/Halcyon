/*
 * Additional software DSP effects for Halcyon's Media3 audio pipeline, in the spirit of the
 * RawS-Music DSP engine (https://github.com/QFDY-GZC/RawS-Music, Apache-2.0): a bass/treble shelf,
 * a feed-forward dynamics compressor, and a stereo widener. See THIRD_PARTY_LICENSES.md.
 * Further RawS-Music-derived effects live in DspToneEffects.kt, DspOutputEffects.kt, and
 * DspSurroundEffects.kt.
 */
package com.ella.music.dsp

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow

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
