package com.ella.music.ui.home

import com.ella.music.data.SettingsManager
import org.junit.Assert.*
import org.junit.Test

class LibraryPresentationLanesTest {
    private val list = SettingsManager.LIBRARY_LAYOUT_LIST
    private val grid = SettingsManager.LIBRARY_LAYOUT_GRID
    private val details = SettingsManager.LIBRARY_LAYOUT_DETAILS

    @Test fun releasingFallbackKeepsDestinationIdentityAndNextGestureCanPrepareAgain() {
        val lanes = LibraryPresentationLanes(list)
        lanes.bind(true, true, list, grid, list)
        lanes.bind(false, false, grid, grid, grid)
        lanes.releaseInactive()
        assertTrue(lanes.retained(false, false))
        assertFalse(lanes.retained(true, false))
        assertEquals(grid, lanes.layout(false))
        lanes.bind(false, true, grid, details, grid)
        assertEquals(grid, lanes.layout(false))
        assertEquals(details, lanes.layout(true))
        assertTrue(lanes.retained(true, false))
    }

    @Test fun commitKeepsThePreparedDestinationAndItsFallbackMounted() {
        val lanes = LibraryPresentationLanes(list)
        assertFalse(lanes.retained(false, true))
        lanes.bind(true, true, list, grid, list)
        assertEquals(grid, lanes.layout(false))
        lanes.bind(false, false, grid, grid, grid)
        assertEquals(grid, lanes.layout(false))
        assertTrue(lanes.retained(false, false))
        assertTrue(lanes.retained(true, false))
        assertEquals(list, lanes.layout(true))
    }

    @Test fun cancelKeepsSourceAndNextPinchOnlyRebindsTheInactiveOwner() {
        val lanes = LibraryPresentationLanes(list)
        lanes.bind(true, true, list, grid, list)
        lanes.bind(true, false, list, list, list)
        assertEquals(list, lanes.layout(true))
        assertEquals(grid, lanes.layout(false))
        lanes.bind(true, true, list, details, list)
        assertEquals(list, lanes.layout(true))
        assertEquals(details, lanes.layout(false))
    }

    @Test fun consecutiveCommitsAlternateOwnersWithoutRebindingDestination() {
        val lanes = LibraryPresentationLanes(list)
        lanes.bind(true, true, list, details, list)
        lanes.bind(false, false, details, details, details)
        lanes.bind(false, true, details, grid, details)
        assertEquals(details, lanes.layout(false))
        assertEquals(grid, lanes.layout(true))
        lanes.bind(true, false, grid, grid, grid)
        assertEquals(grid, lanes.layout(true))
        assertEquals(details, lanes.layout(false))
    }
}
