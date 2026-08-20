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
}
