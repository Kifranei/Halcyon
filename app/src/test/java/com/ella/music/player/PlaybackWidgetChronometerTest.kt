package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackWidgetChronometerTest {
    @Test
    fun pausedSnapshotDoesNotRunTheChronometer() {
        val state = widgetChronometerState(
            isPlaying = false,
            livePlayerSession = true,
            positionMs = 12_000L,
            nowElapsedMs = 100_000L
        )
        assertFalse(state.started)
        assertEquals("0:12", state.frozenText)
        assertEquals(88_000L, state.baseElapsedMs)
    }

    @Test
    fun stalePlayingSnapshotWithoutALiveSessionStaysFrozen() {
        val state = widgetChronometerState(
            isPlaying = true,
            livePlayerSession = false,
            positionMs = 5_000L,
            nowElapsedMs = 50_000L
        )
        assertFalse(state.started)
        assertEquals("0:05", state.frozenText)
    }

    @Test
    fun livePlayingSnapshotRunsTheChronometer() {
        val state = widgetChronometerState(
            isPlaying = true,
            livePlayerSession = true,
            positionMs = 90_000L,
            nowElapsedMs = 200_000L
        )
        assertTrue(state.started)
        assertEquals(110_000L, state.baseElapsedMs)
    }
}
