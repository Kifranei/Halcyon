package com.ella.music.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DesktopLyricOverlayChromeTest {
    @Test
    fun lockedDesktopLyricsDoNotKeepTheControlPanelChrome() {
        assertFalse(
            desktopLyricControlPanelVisible(
                locked = true,
                statusBarMode = false,
                controlsVisible = true
            )
        )
        assertTrue(
            desktopLyricControlPanelVisible(
                locked = false,
                statusBarMode = false,
                controlsVisible = true
            )
        )
        assertFalse(
            desktopLyricControlPanelVisible(
                locked = false,
                statusBarMode = true,
                controlsVisible = true
            )
        )
    }

    @Test
    fun lockedDesktopLyricsPassTouchesThroughWithoutLeavingADeadZone() {
        assertFalse(desktopLyricPassThroughTouches(locked = false, statusBarMode = false))
        assertTrue(desktopLyricPassThroughTouches(locked = true, statusBarMode = false))
        assertTrue(desktopLyricPassThroughTouches(locked = false, statusBarMode = true))
        assertFalse(desktopLyricUsesCompactWindow(locked = true, statusBarMode = false))
        assertFalse(desktopLyricUsesCompactWindow(locked = false, statusBarMode = false))
        assertFalse(desktopLyricUsesCompactWindow(locked = true, statusBarMode = true))
    }
}
