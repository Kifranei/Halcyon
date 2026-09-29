package com.ella.music.ui.player

import com.ella.music.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RawSArtworkSpectrumTest {
    @Test fun heightPercentScalesTheDefaultFlowCurveHeight() {
        assertEquals(0.25f, audioVisualizerHeightFraction(SettingsManager.DEFAULT_AUDIO_VISUALIZER_HEIGHT), 1e-6f)
        assertEquals(0.25f, audioVisualizerHeightFraction(null), 1e-6f)
        assertEquals(0.125f, audioVisualizerHeightFraction(50), 1e-6f)
        assertEquals(0.5f, audioVisualizerHeightFraction(200), 1e-6f)
        assertEquals(0.125f, audioVisualizerHeightFraction(10), 1e-6f)
        assertEquals(0.5f, audioVisualizerHeightFraction(900), 1e-6f)
    }

    @Test fun heightSliderSnapsToTenPercentInsideRange() {
        assertEquals(14, AUDIO_VISUALIZER_HEIGHT_SLIDER_STEPS)
        assertEquals(70, snapAudioVisualizerHeight(69))
        assertEquals(120, snapAudioVisualizerHeight(124))
        assertEquals(50, snapAudioVisualizerHeight(0))
        assertEquals(200, snapAudioVisualizerHeight(260))
        assertEquals(100, SettingsManager.normalizeAudioVisualizerHeight(null))
    }

    @Test fun coverOverlayStyleIsAValidStyle() {
        assertEquals(
            SettingsManager.AUDIO_VISUALIZER_STYLE_COVER_OVERLAY,
            SettingsManager.normalizeAudioVisualizerStyle(SettingsManager.AUDIO_VISUALIZER_STYLE_COVER_OVERLAY)
        )
    }

    @Test fun silenceProducesNoBands() {
        val bands = rawsArtworkSpectrumTargets(ByteArray(1024), 44_100)
        assertEquals(RAWS_ARTWORK_BAND_COUNT, bands.size)
        assertTrue(bands.all { it == 0f })
    }

    @Test fun lowAndHighTonesLandOnLogarithmicBands() {
        // Bin k spans k * 44100 / 1024 Hz: bin 2 is ~86 Hz, bin 400 is ~17.2 kHz.
        val low = rawsArtworkSpectrumTargets(ByteArray(1024).apply { this[4] = 100 }, 44_100)
        val lowPeak = low.indices.maxByOrNull { low[it] }!!
        assertTrue("low tone peak at band $lowPeak", lowPeak in 5..30)
        assertEquals(0f, low[80], 0f)

        val high = rawsArtworkSpectrumTargets(ByteArray(1024).apply { this[800] = 100 }, 44_100)
        val highPeak = high.indices.maxByOrNull { high[it] }!!
        assertTrue("high tone peak at band $highPeak", highPeak > 95)
        assertEquals(0f, high[40], 0f)
        assertTrue((low + high).all { it in 0f..1f })
    }

    @Test fun invalidInputIsSafe() {
        assertEquals(RAWS_ARTWORK_BAND_COUNT, rawsArtworkSpectrumTargets(ByteArray(2), 44_100).size)
        val unknownRate = rawsArtworkSpectrumTargets(ByteArray(1024).apply { this[4] = 100 }, 0)
        assertTrue(unknownRate.any { it > 0f })
    }

    @Test fun analyzerRiseIsRateLimitedAndDisplaySettles() {
        val motion = RawSArtworkSpectrumMotion(bandCount = 8)
        motion.submit(FloatArray(8) { 1f }, 0.016f)
        repeat(120) { motion.advance(1f / 60f, playing = true) }
        // RawS analyzer rise: 20 full-scale units per second -> 0.32 after 16 ms.
        assertEquals(0.32f, motion.levels[4], 0.002f)
        repeat(120) { motion.advance(1f / 60f, playing = false) }
        assertFalse(motion.hasVisibleEnergy())
    }

    @Test fun displayMotionIsRefreshRateIndependent() {
        val sixty = RawSArtworkSpectrumMotion(bandCount = 4)
        val oneTwenty = RawSArtworkSpectrumMotion(bandCount = 4)
        sixty.submit(FloatArray(4) { 1f }, 0.05f)
        oneTwenty.submit(FloatArray(4) { 1f }, 0.05f)
        repeat(6) { sixty.advance(1f / 60f, playing = true) }
        repeat(12) { oneTwenty.advance(1f / 120f, playing = true) }
        assertEquals(sixty.levels[1], oneTwenty.levels[1], 1e-4f)
    }

    @Test fun columnCountAndSamplingFollowRawS() {
        assertEquals(RAWS_ARTWORK_BAND_COUNT, rawsArtworkBarCount(400f))
        assertEquals(72, rawsArtworkBarCount(120f))
        assertEquals(94, rawsArtworkBarCount(300f))

        val flat = FloatArray(RAWS_ARTWORK_BAND_COUNT) { .5f }
        assertEquals(.5f, rawsArtworkSampledValue(flat, 10, 72), 1e-5f)
        val pair = floatArrayOf(1f, 0f)
        // max * 0.76 + rms * 0.24 over the two source bands of one column.
        assertEquals(.76f + .24f * kotlin.math.sqrt(.5f), rawsArtworkSampledValue(pair, 0, 1), 1e-5f)
        assertEquals(0f, rawsArtworkSampledValue(FloatArray(0), 0, 72), 0f)
    }

    @Test fun visibleFractionUsesClippedWindowBounds() {
        assertEquals(1f, coverVisualizerVisibleFraction(100f, 100f, 100, 100), 0f)
        assertEquals(.25f, coverVisualizerVisibleFraction(50f, 50f, 100, 100), 1e-6f)
        assertEquals(0f, coverVisualizerVisibleFraction(0f, 100f, 100, 100), 0f)
        assertEquals(0f, coverVisualizerVisibleFraction(10f, 10f, 0, 0), 0f)
    }

    @Test fun resonanceStringsStayInsideTheVisualizerHeight() {
        assertEquals(98f, coneStringY(100f, 80f, 2f), 0f)
        assertEquals(2f, coneStringY(100f, -80f, 2f), 0f)
        assertEquals(60f, coneStringY(100f, 10f, 2f), 0f)
        assertEquals(1.5f, coneStringY(3f, 50f, 2f), 0f)
    }
}
