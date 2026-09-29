package com.ella.music.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeShortcutColumnsTest {
    @Test
    fun portraitUsesFourShortcuts() {
        assertEquals(4, homeShortcutColumnCount(isLandscape = false, smallestScreenWidthDp = 360))
        assertEquals(4, homeShortcutColumnCount(isLandscape = false, smallestScreenWidthDp = 800))
    }

    @Test
    fun landscapeAdaptsToPhoneAndTabletWidths() {
        assertEquals(6, homeShortcutColumnCount(isLandscape = true, smallestScreenWidthDp = 411))
        assertEquals(8, homeShortcutColumnCount(isLandscape = true, smallestScreenWidthDp = 600))
    }

    @Test
    fun remainingLibraryTilesStayInFeatureBlocksWithoutBeingDuplicated() {
        val tiles = (1..7).map { index ->
            HomeTileSpec("tile$index", "Tile $index", "", "route$index", {})
        }

        val (shortcuts, featureBlocks) = splitHomeTileSections(tiles, shortcutCount = 4)

        assertEquals(listOf("tile1", "tile2", "tile3", "tile4"), shortcuts.map { it.id })
        assertEquals(listOf("tile5", "tile6", "tile7"), featureBlocks.map { it.id })
        assertEquals(tiles, shortcuts + featureBlocks)
    }
}
