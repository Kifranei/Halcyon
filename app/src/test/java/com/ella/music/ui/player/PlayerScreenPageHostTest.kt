package com.ella.music.ui.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerScreenPageHostTest {
    @Test
    fun lyricsRendererIsPausedWhilePagerIsMoving() {
        assertTrue(isPlayerLyricsPageVisible(PLAYER_PAGE_LYRICS, PLAYER_PAGE_LYRICS, false))
        assertFalse(isPlayerLyricsPageVisible(PLAYER_PAGE_LYRICS, PLAYER_PAGE_LYRICS, true))
        assertFalse(isPlayerLyricsPageVisible(PLAYER_PAGE_COVER, PLAYER_PAGE_LYRICS, false))
    }

    @Test
    fun backIsInterceptedOnlyOnLyricsPage() {
        assertTrue(shouldInterceptPlayerPagerBack(true, PLAYER_PAGE_LYRICS))
        assertFalse(shouldInterceptPlayerPagerBack(true, PLAYER_PAGE_DETAILS))
        assertFalse(shouldInterceptPlayerPagerBack(true, PLAYER_PAGE_COVER))
        assertFalse(shouldInterceptPlayerPagerBack(false, PLAYER_PAGE_LYRICS))
    }
}
