package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimerEndClockTest {
    @Test
    fun expiredTimerHasNoRemainingLabel() {
        assertNull(sleepTimerRemainingLabel(endRealtimeMs = 1_000L, nowRealtimeMs = 1_000L))
        assertNull(sleepTimerRemainingLabel(endRealtimeMs = 500L, nowRealtimeMs = 1_000L))
    }

    @Test
    fun activeTimerFormatsRemainingCountdown() {
        val label = sleepTimerRemainingLabel(
            endRealtimeMs = 60_000L,
            nowRealtimeMs = 0L
        )
        assertEquals("01:00", label)
        assertEquals(
            "00:05",
            sleepTimerRemainingLabel(endRealtimeMs = 5_000L, nowRealtimeMs = 0L)
        )
    }
}
