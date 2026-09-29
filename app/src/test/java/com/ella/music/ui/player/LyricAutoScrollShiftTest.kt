package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class LyricAutoScrollShiftTest {
    @Test fun recentJumpIsReportedThenExpires() {
        val shift = LyricAutoScrollShift()
        assertEquals(0f, shift.recent(), 0f)
        shift.record(180f)
        assertEquals(180f, shift.recent(), 0f)
        Thread.sleep(30)
        assertEquals(0f, shift.recent(windowMs = 10L), 0f)
    }
}
