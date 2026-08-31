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
        assertEquals(1L, playbackCountThresholdMs(100_000L, 1, 1L))
        assertEquals(360_000L, playbackCountThresholdMs(1_000_000L, 100, 999_999L))
    }

    @Test
    fun `zero in either slider counts as soon as playback begins`() {
        assertEquals(0L, playbackCountThresholdMs(240_000L, 0, 180_000L))
        assertEquals(0L, playbackCountThresholdMs(240_000L, 50, 0L))
    }

    @Test
    fun `one percent and one second are not raised to legacy minimums`() {
        assertEquals(1_000L, playbackCountThresholdMs(240_000L, 1, 1_000L))
        assertEquals(2_400L, playbackCountThresholdMs(240_000L, 1, 30_000L))
    }
}
