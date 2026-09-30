package com.ella.music.ui.player

import com.ella.music.data.SettingsManager
import org.junit.Assert.*
import org.junit.Test

class PlayerMorphSurfaceOwnershipTest {
    @Test fun occludedPageCannotCaptureArtworkForMvOrClassicSplit() {
        for (style in listOf(SettingsManager.PLAYER_LANDSCAPE_STYLE_MUSIC_VIDEO,
            SettingsManager.PLAYER_LANDSCAPE_STYLE_CLASSIC_SPLIT, SettingsManager.PLAYER_LANDSCAPE_STYLE_COVER_FLOW,
            SettingsManager.PLAYER_LANDSCAPE_STYLE_COVER_CLOCK)) {
            assertFalse(playerMorphPageArtworkEnabled(true, true, style))
            assertTrue(playerMorphPageArtworkEnabled(true, false, style))
        }
    }
    @Test fun wideLayoutKeepsTheNormalPagesArtworkAndInactiveSurfaceNeverCaptures() {
        assertTrue(playerMorphPageArtworkEnabled(true, true, SettingsManager.PLAYER_LANDSCAPE_STYLE_WIDE))
        assertFalse(playerMorphPageArtworkEnabled(false, false, SettingsManager.PLAYER_LANDSCAPE_STYLE_WIDE))
    }
}
