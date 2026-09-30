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

}
