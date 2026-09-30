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
    @Test
    fun aStalledPlayerCannotRevealTheNextSustainedWord() {
        var display = 1_000L
        val futureWord = AppleMusicRenderWord(com.ella.music.data.model.LyricWord("again", 1_200L, 3_000L), 3_000L)
        repeat(180) {
            display = nextSmoothLyricPositionMs(display, 1_000L, 16L, true)
            assertTrue(display <= 1_150L)
            assertEquals(0f, futureWord.karaokeProgress(display, true), 0f)
            assertTrue(karaokeHaloMaskStops(futureWord.karaokeProgress(display, true), false, .85f)
                .all { it.second.alpha == 0f })
        }
    }

    @Test
    fun interpolationRecoversWhenSamplesResumeAndStillHandlesBackwardSeeks() {
        var display = 1_000L
        repeat(60) { display = nextSmoothLyricPositionMs(display, 1_000L, 16L, true) }
        val resumed = nextSmoothLyricPositionMs(display, 1_200L, 16L, true)
        assertTrue(resumed >= display)
        assertTrue(resumed <= 1_350L)
        assertEquals(500L, nextSmoothLyricPositionMs(5_000L, 500L, 16L, true))
    }

    @Test
    fun liveClockKeepsTheSweepMovingBetweenThrottledUiUpdates() {
        for (refreshHz in listOf(60, 90, 120)) {
            var display = 1_000L
            var oldDisplay = 1_000L
            var oldPausedFrames = 0
            var lastFrameMs = 0L
            val word = AppleMusicRenderWord(
                com.ella.music.data.model.LyricWord("feeling", 1_000L, 2_500L), 2_500L)
            for (frame in 1..refreshHz) {
                val elapsedMs = frame * 1_000L / refreshHz
                val sample = 1_000L + elapsedMs / 250L * 250L
                val delta = elapsedMs - lastFrameMs
                lastFrameMs = elapsedMs
                val next = lyricFramePositionMs(1_000L + elapsedMs, display, sample, delta, true)
                assertTrue("$refreshHz Hz sweep stopped at frame $frame", next > display)
                assertEquals(elapsedMs / 1_500f, word.karaokeProgress(next, true), 0.0001f)
                display = next
                val oldNext = nextSmoothLyricPositionMs(oldDisplay, sample, delta, true)
                if (oldNext == oldDisplay) oldPausedFrames++
                oldDisplay = oldNext
            }
            assertTrue("regression fixture must reproduce the old freezes", oldPausedFrames > 0)
        }
    }

    @Test
    fun liveClockDoesNotPredictPastABufferingPlayerOrIgnoreASeek() {
        val futureWord = AppleMusicRenderWord(
            com.ella.music.data.model.LyricWord("again", 1_200L, 3_000L), 3_000L)
        var display = 1_000L
        repeat(180) {
            display = lyricFramePositionMs(1_000L, display, 1_000L, 16L, true)
            assertEquals(0f, futureWord.karaokeProgress(display, true), 0f)
        }
        assertEquals(500L, lyricFramePositionMs(500L, display, 1_000L, 16L, true))
        assertEquals(2_000L, lyricFramePositionMs(2_000L, 500L, 1_000L, 16L, true))
        assertEquals(1_000L, lyricFramePositionMs(null, 900L, 1_000L, 16L, false))
    }
}
