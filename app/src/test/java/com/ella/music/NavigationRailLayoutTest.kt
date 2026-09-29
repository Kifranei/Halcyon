package com.ella.music

import com.ella.music.data.BottomBarStyle
import org.junit.Assert.*
import org.junit.Test

class NavigationRailLayoutTest {
    @Test fun phonesUseTheRailOnlyWhenTheWindowIsWideEnough() {
        assertFalse(useNavigationRail(BottomBarStyle.Normal, 412f, 915f, false))
        assertTrue(useNavigationRail(BottomBarStyle.Normal, 915f, 412f, false))
        assertTrue(useNavigationRail(BottomBarStyle.Normal, 560f, 350f, false))
    }

    @Test fun floatingPortraitTabletAndUnmeasuredWindowsKeepBottomNavigation() {
        assertFalse(useNavigationRail(BottomBarStyle.LiquidGlass, 800f, 1280f, true))
        assertFalse(useNavigationRail(BottomBarStyle.Normal, 0f, 0f, false))
    }

    @Test fun tabletsSupportPortraitButNarrowSplitWindowsKeepTheBottomBar() {
        assertTrue(useNavigationRail(BottomBarStyle.Normal, 800f, 1280f, true))
        assertTrue(useNavigationRail(BottomBarStyle.Normal, 1280f, 800f, true))
        assertFalse(useNavigationRail(BottomBarStyle.Normal, 480f, 800f, true))
    }

    @Test fun everyLandscapeStyleUsesTheRailAndPortraitKeepsItsPreference() {
        listOf(BottomBarStyle.Normal, BottomBarStyle.Floating, BottomBarStyle.LiquidGlass).forEach {
            assertTrue(useNavigationRail(it, 1280f, 800f, true))
            assertTrue(useNavigationRail(it, 915f, 412f, false))
            assertTrue(useNavigationRail(it, 560f, 350f, false))
            assertFalse(useNavigationRail(it, 412f, 915f, false))
        }
    }
}
