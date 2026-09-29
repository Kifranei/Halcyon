package com.ella.music.ui.player

import org.junit.Assert.*
import org.junit.Test

class MiniPlayerOpeningGestureTest {
    @Test fun shortSlowPullReturnsToMiniPlayer() {
        assertFalse(shouldExpandMiniPlayer(.12f, -200f, 1000f))
    }
    @Test fun committedDistanceOpens() {
        assertTrue(shouldExpandMiniPlayer(.4f, 0f, 1000f))
    }
    @Test fun quickUpwardFlingOpensBeforeDistanceThreshold() {
        assertTrue(shouldExpandMiniPlayer(.1f, -1400f, 1000f))
    }
    @Test fun downwardReversalCancelsEvenAfterLongPull() {
        assertFalse(shouldExpandMiniPlayer(.7f, 1300f, 1000f))
    }
}
