package com.ella.music.player

import android.content.res.Configuration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuperIslandControlIconsTest {
    @Test
    fun darkBackgroundUsesWhiteButtons() {
        assertEquals(0xFFFFFFFF.toInt(), superIslandControlButtonColor(darkBackground = true))
    }

    @Test
    fun lightBackgroundUsesNearBlackButtons() {
        assertEquals(0xFF111111.toInt(), superIslandControlButtonColor(darkBackground = false))
    }

    @Test
    fun compactIconsFollowSystemNightMode() {
        assertTrue(superIslandSystemUiIsDark(Configuration.UI_MODE_NIGHT_YES))
        assertFalse(superIslandSystemUiIsDark(Configuration.UI_MODE_NIGHT_NO))
        assertEquals(
            superIslandControlButtonColor(darkBackground = false),
            superIslandControlButtonColor(
                darkBackground = superIslandSystemUiIsDark(Configuration.UI_MODE_NIGHT_NO)
            )
        )
    }
}
