package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class AppleCoverFlowSpeedTest {
    @Test
    fun speedUsesTenthsMultiplier() {
        assertEquals(5_000L, scaledAppleFlowTimeMs(elapsedMs = 10_000L, speedTenths = 5))
        assertEquals(10_000L, scaledAppleFlowTimeMs(elapsedMs = 10_000L, speedTenths = 10))
        assertEquals(35_000L, scaledAppleFlowTimeMs(elapsedMs = 10_000L, speedTenths = 35))
        assertEquals(60_000L, scaledAppleFlowTimeMs(elapsedMs = 10_000L, speedTenths = 60))
    }

    @Test
    fun speedIsClampedToSupportedRange() {
        assertEquals(5_000L, scaledAppleFlowTimeMs(elapsedMs = 10_000L, speedTenths = 1))
        assertEquals(60_000L, scaledAppleFlowTimeMs(elapsedMs = 10_000L, speedTenths = 100))
    }
}
