package com.ella.music.ui.player

import com.ella.music.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformProgressTuningTest {
    private val t = WaveformProgressTuning

    @Test fun defaultDensityKeepsOriginalGeometryExactly() {
        val default = SettingsManager.DEFAULT_PLAYER_WAVEFORM_DENSITY
        assertEquals(160, t.waveformVisibleBarCount(t.BASE_WAVEFORM_BAR_COUNT, default))
        assertEquals(7.45f, t.secondBarStepDp(default), 0f)
        assertEquals(4.9f, t.secondBarWidthDp(default), 0f)
    }

    @Test fun densityScalesBarCountAndIsClamped() {
        assertEquals(80, t.waveformVisibleBarCount(160, 50))
        assertEquals(320, t.waveformVisibleBarCount(160, 200))
        assertEquals(80, t.waveformVisibleBarCount(160, 10))
        assertEquals(320, t.waveformVisibleBarCount(160, 999))
        assertEquals(32, t.waveformVisibleBarCount(40, 50))
    }

    @Test fun denserSecondsTimelineTightensPitchWithoutOverlap() {
        assertEquals(3.725f, t.secondBarStepDp(200), 1e-5f)
        assertEquals(14.9f, t.secondBarStepDp(50), 1e-5f)
        // Sparser keeps bar width; denser shrinks it with the pitch.
        assertEquals(4.9f, t.secondBarWidthDp(50), 0f)
        assertEquals(2.45f, t.secondBarWidthDp(200), 1e-5f)
        for (percent in 50..200 step 10) {
            assertTrue(t.secondBarWidthDp(percent) < t.secondBarStepDp(percent))
        }
    }

    @Test fun defaultPeakHeightKeepsOriginalHalfHeightExactly() {
        val scale = t.peakHeightScale(SettingsManager.DEFAULT_PLAYER_WAVEFORM_PEAK_HEIGHT)
        assertEquals(1f, scale, 0f)
        val h = 62f * 2.75f
        val minHalf = 2.4f * 2.75f
        val maxHalf = h * 0.5f
        for (shaped in listOf(0.05f, 0.3f, 0.77f, 1f)) {
            val original = (minHalf + maxHalf * shaped).coerceAtMost(h * 0.49f)
            assertEquals(original, t.waveformBarHalfHeight(minHalf, maxHalf, shaped, scale, h), 0f)
        }
    }

    @Test fun peakHeightScalesAndStaysWithinNeedle() {
        val h = 100f
        val half = t.waveformBarHalfHeight(0f, 50f, 0.5f, 0.5f, h)
        assertEquals(12.5f, half, 1e-4f)
        val tall = t.waveformBarHalfHeight(0f, 50f, 1f, t.peakHeightScale(150), h)
        assertEquals(h * t.WAVEFORM_MAX_HALF_HEIGHT_CAP, tall, 1e-4f)
        assertEquals(0.88f, t.peakCap(0.88f, 0.5f, 1.3f), 0f)
        assertEquals(1.3f, t.peakCap(0.88f, 1.5f, 1.3f), 1e-6f)
        assertEquals(1.5f, t.peakHeightScale(999), 0f)
        assertEquals(0.5f, t.peakHeightScale(0), 0f)
    }

    @Test fun disabledScaleAnimationKeepsFullSize() {
        assertEquals(0.95f, t.pauseScaleTarget(scaleAnimationEnabled = true, playing = false, dragging = false), 0f)
        assertEquals(1f, t.pauseScaleTarget(scaleAnimationEnabled = true, playing = false, dragging = true), 0f)
        assertEquals(1f, t.pauseScaleTarget(scaleAnimationEnabled = false, playing = false, dragging = false), 0f)
    }

    @Test fun sliderHelpers() {
        assertEquals(14, t.sliderSteps(50..200, 10))
        assertEquals(19, t.sliderSteps(50..150, 5))
        assertEquals(70, t.snapToStep(69, 10))
        assertEquals(70, t.snapToStep(70, 10))
        assertEquals(55, t.snapToStep(54, 5))
    }
}
