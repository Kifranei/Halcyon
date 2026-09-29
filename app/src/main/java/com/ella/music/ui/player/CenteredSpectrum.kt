package com.ella.music.ui.player

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/** Android adaptation of the centered spectrum behavior observed in BetterLyrics.
 * Independently implemented for Android's mixed, signed-byte FFT (not stereo loopback PCM).
 * Low frequencies meet in the middle; both outer edges contain high frequencies.
 */
internal fun centeredSpectrumTargets(fft: ByteArray, samplingRateHz: Int, count: Int = 64): FloatArray {
    val bins = fft.size / 2
    if (bins < 2 || count < 2) return FloatArray(count.coerceAtLeast(0))
    return FloatArray(count) { index ->
        val distance = ((abs(2f * index - (count - 1)) - if (count % 2 == 0) 1f else 0f) /
            (count - if (count % 2 == 0) 2 else 1).coerceAtLeast(1)).coerceIn(0f, 1f)
        val bin = (distance * (bins - 2)).toInt().coerceIn(1, bins - 1)
        val real = fft[bin * 2].toFloat() / 128f
        val imaginary = fft[bin * 2 + 1].toFloat() / 128f
        val frequency = bin.toFloat() * samplingRateHz / fft.size
        // Broad high-frequency compensation, without log remapping or a fabricated floor.
        val compensation = 1f + 11f * (frequency / 20_000f).coerceIn(0f, 1f).pow(0.8f)
        (sqrt(real * real + imaginary * imaginary) * compensation * 5f).coerceIn(0f, 1f)
    }
}

internal class CenteredSpectrumMotion(count: Int = 64) {
    val levels = FloatArray(count)

    fun advance(target: FloatArray, seconds: Float) {
        val frames = seconds.coerceIn(0f, .1f) * 60f
        val rise = 1f - .92f.pow(frames)
        val decay = .98f.pow(frames)
        for (i in levels.indices) {
            val next = target.getOrElse(i) { 0f }
            levels[i] = if (next > levels[i]) levels[i] + (next - levels[i]) * rise else levels[i] * decay
            if (levels[i] < .0001f) levels[i] = 0f
        }
    }

    fun reset() = levels.fill(0f)
}
