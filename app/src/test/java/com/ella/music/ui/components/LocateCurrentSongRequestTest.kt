package com.ella.music.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocateCurrentSongRequestTest {
    @Test
    fun waitsUntilTheCurrentSongIndexIsKnown() {
        assertFalse(shouldHonorLocateCurrentSongRequest(1, 0, -1))
        assertTrue(shouldHonorLocateCurrentSongRequest(1, 0, 4))
        assertFalse(shouldHonorLocateCurrentSongRequest(1, 1, 4))
        assertFalse(shouldHonorLocateCurrentSongRequest(0, 0, 4))
    }

    @Test
    fun ordinaryOpenAfterPreviousLocateDoesNotReuseRequest() {
        // After a previous 来源 locate, request may already be > 0. A newly opened
        // category page must treat the current request as already handled.
        assertFalse(shouldHonorLocateCurrentSongRequest(3, 3, 4))
        assertTrue(shouldHonorLocateCurrentSongRequest(4, 3, 4))
    }
}
