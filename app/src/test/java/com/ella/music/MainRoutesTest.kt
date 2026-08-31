package com.ella.music

import com.ella.music.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainRoutesTest {
    @Test
    fun dockSettingsPatternResolvesToFirstLevelTab() {
        val route = resolvedCurrentRoute(
            destinationRoute = Screen.Settings.route,
            fromDock = true
        )
        assertEquals(Screen.Settings.createRoute(fromDock = true), route)
        assertTrue(route.isBottomDockRoute())
        assertEquals(Screen.Settings.createRoute(fromDock = true), route.toCurrentTabRoute())
    }

    @Test
    fun settingsOpenedWithoutDockStaysSecondary() {
        val route = resolvedCurrentRoute(
            destinationRoute = Screen.Settings.route,
            fromDock = false
        )
        assertEquals(Screen.Settings.createRoute(fromDock = false), route)
        assertFalse(route.isBottomDockRoute())
        assertEquals(null, route.toCurrentTabRoute())
    }

    @Test
    fun backFromArtistRestoresPlayerWhenPreviousPageIsTheSavedRoute() {
        val library = navRouteIdentity(Screen.Library.route) { null }
        assertTrue(shouldRestorePlayerOnBack(library, library))
        assertFalse(shouldRestorePlayerOnBack(library, Screen.ArtistDetail.route))
        assertFalse(shouldRestorePlayerOnBack(null, library))
        assertFalse(playerDismissBackEnabled(playerVisible = true, restorePlayerOnBack = true))
        assertTrue(playerDismissBackEnabled(playerVisible = true, restorePlayerOnBack = false))
    }

    @Test
    fun settingsGraphIncludesMainPageAndSubpages() {
        assertTrue(Screen.Settings.createRoute(fromDock = true).isSettingsGraphRoute())
        assertTrue(Screen.Settings.createRoute(fromDock = false).isSettingsGraphRoute())
        assertTrue(Screen.SettingsDetail.createRoute().isSettingsGraphRoute())
        assertTrue(Screen.LyricSettings.createRoute().isSettingsGraphRoute())
        assertTrue(Screen.AppearanceSubpage.createRoute("player").isSettingsGraphRoute())
        assertTrue(Screen.ScanSettings.createRoute(fromDock = true).isSettingsGraphRoute())
        assertFalse(Screen.Library.route.isSettingsGraphRoute())
        assertFalse(Screen.Home.route.isSettingsGraphRoute())
        assertFalse(Screen.AlbumDetail.createRoute(11L).isSettingsGraphRoute())
    }

    @Test
    fun rawSettingsTemplateIsNotADockTabUntilFromDockIsFilled() {
        assertFalse(Screen.Settings.route.isBottomDockRoute())
        assertEquals(null, Screen.Settings.route.toCurrentTabRoute())
    }
}
