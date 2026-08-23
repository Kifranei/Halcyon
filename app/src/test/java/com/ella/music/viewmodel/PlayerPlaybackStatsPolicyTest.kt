package com.ella.music.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerPlaybackStatsPolicyTest {

    @Test
    fun `short song counts at configured percentage before duration limit`() {
        assertEquals(120_000L, playbackCountThresholdMs(240_000L, 50, 180_000L))
    }

    @Test
    fun `long song counts at configured duration before percentage`() {
        assertEquals(180_000L, playbackCountThresholdMs(600_000L, 50, 180_000L))
    }

    @Test
    fun `unknown duration falls back to configured duration`() {
        assertEquals(90_000L, playbackCountThresholdMs(0L, 75, 90_000L))
    }

    @Test
    fun `threshold settings stay within issue ranges`() {
        assertEquals(30_000L, playbackCountThresholdMs(100_000L, 1, 1L))
        assertEquals(360_000L, playbackCountThresholdMs(1_000_000L, 100, 999_999L))
    }
}
