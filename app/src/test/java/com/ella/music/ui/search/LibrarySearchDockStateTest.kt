package com.ella.music.ui.search

import com.ella.music.data.SettingsManager
import com.ella.music.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchDockStateTest {
    @Test
    fun searchRouteUsesBottomSearchDock() {
        assertTrue(usesSearchBottomDock(Screen.LibrarySearch.createRoute()))
        assertTrue(usesSearchBottomDock(Screen.LibrarySearch.baseRoute))
        assertFalse(usesSearchBottomDock(Screen.Home.route))
        assertFalse(usesSearchBottomDock(Screen.Library.route))
        assertFalse(usesSearchBottomDock(null))
    }

    @Test
    fun searchDockKeepsTheTabFromBeforeSearch() {
        assertEquals(
            Screen.Library.route,
            searchDockReturnTabRoute(
                currentRoute = Screen.LibrarySearch.createRoute(),
                currentTabRoute = Screen.LibrarySearch.createRoute(),
                lastTabRoute = Screen.Library.route
            )
        )
        assertEquals(
            Screen.Home.route,
            searchDockReturnTabRoute(
                currentRoute = Screen.Home.route,
                currentTabRoute = Screen.Home.route,
                lastTabRoute = Screen.Library.route
            )
        )
    }

    @Test
    fun searchReopenClearEmptiesTheQueryUnlessIncoming() {
        assertEquals(
            "",
            applySearchReopenQuery(SettingsManager.SEARCH_REOPEN_CLEAR, "Let me", null)
        )
        assertEquals(
            "album",
            applySearchReopenQuery(SettingsManager.SEARCH_REOPEN_CLEAR, "Let me", "album")
        )
        assertEquals(
            "Let me",
            applySearchReopenQuery(SettingsManager.SEARCH_REOPEN_KEEP, "Let me", null)
        )
        assertEquals(
            "Let me",
            applySearchReopenQuery(SettingsManager.SEARCH_REOPEN_SELECT, "Let me", null)
        )
        assertTrue(searchReopenSelectsQuery(SettingsManager.SEARCH_REOPEN_SELECT, "Let me"))
        assertFalse(searchReopenSelectsQuery(SettingsManager.SEARCH_REOPEN_CLEAR, "Let me"))
        assertFalse(searchReopenSelectsQuery(SettingsManager.SEARCH_REOPEN_SELECT, ""))
    }
}
