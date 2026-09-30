package com.ella.music.ui.album

import com.ella.music.ui.components.BackgroundBrowseResult
import org.junit.Assert.*
import org.junit.Test

class AlbumGridScrollRestoreTest {
    @Test fun firstEntryWaitsForActualPinnedOrderingBeforeChoosingTheTop() {
        val sorted = BackgroundBrowseResult(listOf(100L, 101L), listOf(listOf<String>()))
        val pins = listOf("2", "3", "4", "5")
        assertNull(albumGridScrollTarget(sorted.isReadyFor(pins), sorted.value, 0, 0, null, 0, true))
        val final = BackgroundBrowseResult(listOf(2L, 3L, 4L, 5L, 100L, 101L), listOf(pins))
        assertEquals(AlbumGridScrollTarget(0, 0, 0),
            albumGridScrollTarget(final.isReadyFor(pins), final.value, 0, 0, null, 0, true))
    }
    @Test fun returningFromAPinnedAlbumRestoresItsVisibleKeyAndPixelOffset() {
        assertNull(albumGridScrollTarget(false, listOf(100L, 101L), 2, 1, 4L, 37, false))
        val target = albumGridScrollTarget(true, listOf(2L, 3L, 4L, 5L, 100L), 2, 1, 4L, 37, false)
        assertEquals(AlbumGridScrollTarget(2, 37, 2), target)
        assertNull(albumGridScrollTarget(true, listOf(2L, 3L, 4L, 5L, 100L), 2, 2, 4L, 37, false))
    }
    @Test fun alreadyRestoredOrResumedGridIsNotResetByAnotherCalculation() {
        assertNull(albumGridScrollTarget(true, listOf(2L, 3L, 100L), 0, 0, null, 0, false))
        assertNull(albumGridScrollTarget(true, listOf(3L, 2L, 100L), 3, 3, 2L, 18, false))
    }
    @Test fun subsequentDetailReturnsEachRestoreTheirOwnAnchor() {
        var consumed = 0
        for ((request, anchor) in listOf(1 to 2L, 2 to 100L, 3 to 3L)) {
            val target = albumGridScrollTarget(true, listOf(2L, 3L, 100L), request, consumed, anchor, 19, false)!!
            assertEquals(anchor, listOf(2L, 3L, 100L)[target.index])
            consumed = target.restoredRequest
        }
    }
}
