package com.ella.music.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerMotionTest {
    @Test
    fun lyricsCornerActionsFollowChrome() {
        assertTrue(PlayerMotion.lyricsCornerActionsVisible(true))
        assertFalse(PlayerMotion.lyricsCornerActionsVisible(false))
    }
}
