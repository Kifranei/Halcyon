package com.ella.music.ui.folder

import org.junit.Assert.*
import org.junit.Test

class FolderDisplayColumnsTest {
    @Test fun unchangedLayoutIsSingleColumn() { assertEquals(1, folderDisplayColumns(100,100)) }
    @Test fun previouslySmallCardsNowShareRows() {
        assertEquals(2, folderDisplayColumns(65,65))
        assertEquals(2, folderDisplayColumns(100,50))
        assertEquals(3, folderDisplayColumns(100,33))
        assertEquals(4, folderDisplayColumns(100,25))
    }
    @Test fun groupingPreservesAllEntriesAndPartialLastRow() {
        val rows = (1..7).toList().chunked(folderDisplayColumns(100,33))
        assertEquals(listOf(listOf(1,2,3), listOf(4,5,6), listOf(7)), rows)
    }
    @Test fun indexIncludesLettersInEveryGridCellAndTargetsTheirActualRow() {
        val targets = folderFastIndexTargets(listOf("A","B","B","C"),2)
        assertEquals(mapOf("A" to 0,"B" to 0,"C" to 1),targets)
    }

}
