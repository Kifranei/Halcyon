package com.ella.music.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlayerWaveformSeekBarTest {
    @Test fun verticalDragDoesNotSeek() {
        assertEquals(PlayerTimelineGestureAxis.VerticalScene, resolvePlayerTimelineGestureAxis(3f, 24f, 8f))
    }
    @Test fun horizontalDragOwnsTheTimeline() {
        assertEquals(PlayerTimelineGestureAxis.HorizontalSeek, resolvePlayerTimelineGestureAxis(24f, 3f, 8f))
    }
    @Test fun secondScaleSeeksRelativeToCentreAndClamps() {
        assertEquals(45f, resolveSecondTimelineTapSecond(50f, 50f, 200f, 10f, 100f), .001f)
        assertEquals(0f, resolveSecondTimelineTapSecond(2f, 0f, 200f, 10f, 100f), .001f)
        assertEquals(100f, resolveSecondTimelineTapSecond(99f, 200f, 200f, 10f, 100f), .001f)
    }
    @Test fun silenceDoesNotInventSpectrumEnergy() {
        assertTrue(coneSpectrumTargets(ByteArray(512)).all { it == 0f })
    }
    @Test fun spectrumIsBoundedForFullScaleInput() {
        val targets = coneSpectrumTargets(ByteArray(512) { 127 })
        assertEquals(64, targets.size)
        assertTrue(targets.all { it.isFinite() && it in 0f..1f })
        assertTrue(targets.any { it > 0f })
    }
}
