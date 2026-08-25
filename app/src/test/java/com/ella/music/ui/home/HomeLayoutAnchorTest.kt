package com.ella.music.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutAnchorTest {
    @Test
    fun listPositionMapsToTheContainingGridRow() {
        val songIndex = libraryLayoutAnchorSongIndex(firstVisibleItemIndex = 37, grid = false)

        assertEquals(37, songIndex)
        assertEquals(18, libraryLayoutItemIndexForSong(songIndex, grid = true))
    }

    @Test
    fun gridRowMapsBackToItsFirstVisibleSong() {
        val songIndex = libraryLayoutAnchorSongIndex(firstVisibleItemIndex = 18, grid = true)

        assertEquals(36, songIndex)
        assertEquals(36, libraryLayoutItemIndexForSong(songIndex, grid = false))
    }
}
