package com.ella.music.ui.components

import org.junit.Assert.*
import org.junit.Test

class LibraryRangeSelectionTest {
    @Test fun rangePinsInDisplayOrderIncludingPreviouslySelectedEndpoints() {
        val ids = ('A'..'G').toList()
        val indexes = ids.withIndex().associate { it.value to it.index }
        val state = LibrarySelectionState<Char>()
        state.toggleSelection('B')
        state.toggleSelection('E')
        state.applyRangeSelection(ids, indexes)
        assertEquals("BCDE".toList(), state.selectedIdsInSelectionOrder())
    }

    @Test fun ordinaryMultiSelectionStillPinsInTapOrder() {
        val state = LibrarySelectionState<Char>()
        "BECD".forEach(state::toggleSelection)
        assertEquals("BECD".toList(), state.selectedIdsInSelectionOrder())
    }

    @Test fun reverseRangeKeepsOutsideSelectionsInTheirOriginalOrder() {
        val ids = ('A'..'G').toList()
        val indexes = ids.withIndex().associate { it.value to it.index }
        val state = LibrarySelectionState<Char>()
        "GEB".forEach(state::toggleSelection)
        state.applyRangeSelection(ids, indexes)
        assertEquals("GBCDE".toList(), state.selectedIdsInSelectionOrder())
    }

    @Test fun rangesUseLastTwoManualTapsAndResetAfterEachRange() {
        val ids = ('A'..'N').toList()
        val indexes = ids.withIndex().associate { it.value to it.index }
        val state = LibrarySelectionState<Char>()
        state.toggleSelection('B')
        state.toggleSelection('E')
        state.applyRangeSelection(ids, indexes)
        state.toggleSelection('H')
        assertFalse(state.isRangeSelectionAvailable(indexes))
        state.toggleSelection('I')
        assertTrue(state.isRangeSelectionAvailable(indexes))
        state.toggleSelection('L')
        state.toggleSelection('N')
        state.applyRangeSelection(ids, indexes)
        assertEquals("BCDEHILMN".toSet(), state.selectedIds)
        assertFalse(state.isRangeSelectionAvailable(indexes))
    }

    @Test fun deselectingAnchorDoesNotReuseAnOlderCompletedRange() {
        val state = LibrarySelectionState<Char>()
        state.selectedIds = setOf('B', 'C', 'D')
        state.toggleSelection('H')
        state.toggleSelection('H')
        state.toggleSelection('L')
        assertNull(state.rangeTargetId)
    }
}
