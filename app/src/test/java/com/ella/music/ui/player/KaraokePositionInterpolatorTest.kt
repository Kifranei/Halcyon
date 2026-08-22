package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KaraokePositionInterpolatorTest {
    @Test
    fun playingKeepsFrameClockInsteadOfSnappingToASlightlyLateSample() {
        val display = 10_000L
        val lateSample = 9_950L
        val next = nextSmoothLyricPositionMs(
            displayMs = display,
            sampledMs = lateSample,
            frameDeltaMs = 16L,
            playing = true
        )
        assertEquals(10_016L, next)
    }

    @Test
    fun pausedFollowsTheSampledPosition() {
        assertEquals(
            4_200L,
            nextSmoothLyricPositionMs(
                displayMs = 4_000L,
                sampledMs = 4_200L,
                frameDeltaMs = 16L,
                playing = false
            )
        )
    }

    @Test
    fun largeSeekSnapsToTheSample() {
        assertEquals(
            80_000L,
            nextSmoothLyricPositionMs(
                displayMs = 1_000L,
                sampledMs = 80_000L,
                frameDeltaMs = 16L,
                playing = true
            )
        )
    }

    @Test
    fun aStaleSampleDoesNotRewindTheKaraokeFill() {
        var display = 1_000L
        repeat(8) {
            display = nextSmoothLyricPositionMs(
                displayMs = display,
                sampledMs = 1_000L,
                frameDeltaMs = 16L,
                playing = true
            )
        }
        assertTrue(display >= 1_100L)
    }
}
