package com.ella.music.ui.home

import com.ella.music.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeLayoutAnchorTest {
    @Test
    fun listPositionMapsToTheContainingGridRow() {
        val songIndex = libraryLayoutAnchorSongIndex(firstVisibleItemIndex = 37, columns = 1)

        assertEquals(37, songIndex)
        assertEquals(18, libraryLayoutItemIndexForSong(songIndex, columns = 2))
    }

    @Test
    fun gridRowMapsBackToItsFirstVisibleSong() {
        val songIndex = libraryLayoutAnchorSongIndex(firstVisibleItemIndex = 18, columns = 2)

        assertEquals(36, songIndex)
        assertEquals(36, libraryLayoutItemIndexForSong(songIndex, columns = 1))
    }

    @Test
    fun multiRowLandscapeUsesThreeColumns() {
        assertEquals(3, libraryLayoutColumnCount(multiRow = true, grid = false, landscape = true))
        assertEquals(2, libraryLayoutColumnCount(multiRow = true, grid = false, landscape = false))
    }

    @Test
    fun gridUsesTheConfiguredDeviceColumnCount() {
        assertEquals(2, libraryLayoutColumnCount(multiRow = false, grid = true, landscape = false, gridColumns = 2))
        assertEquals(5, libraryLayoutColumnCount(multiRow = false, grid = true, landscape = true, gridColumns = 5))
    }

    @Test
    fun libraryPinchFromTwoColumnsTargetsDetailsBeforeCoverGrid() {
        val state = LibraryPinchState(SettingsManager.LIBRARY_LAYOUT_MULTI_ROW)

        state.beginPinch()
        state.updatePinch(rawDelta = 0.4f, velocityDp = 0f)

        assertEquals(SettingsManager.LIBRARY_LAYOUT_DETAILS, state.targetLayout)
        state.cancelPinch()
    }
}
