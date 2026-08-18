package com.ella.music.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsDetailNavigationTest {
    @Test
    fun embeddedHomeDisplay_handlesBackInsideAppearancePage() {
        assertTrue(
            shouldHandleHomeDisplayBackLocally(
                showHomeDisplayPage = true,
                initialHomeDisplay = false
            )
        )
    }

    @Test
    fun directHomeDisplayRoute_leavesBackToNavigationController() {
        assertFalse(
            shouldHandleHomeDisplayBackLocally(
                showHomeDisplayPage = true,
                initialHomeDisplay = true
            )
        )
    }

    @Test
    fun appearancePage_leavesBackToNavigationController() {
        assertFalse(
            shouldHandleHomeDisplayBackLocally(
                showHomeDisplayPage = false,
                initialHomeDisplay = false
            )
        )
    }
}
