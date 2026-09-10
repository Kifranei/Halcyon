package com.ella.music.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsSearchFallbackTest {
    @Test
    fun miniPlayerSwipeOpensLyricsNotPlayerAppearance() {
        assertEquals(
            SettingsSearchFallbackDestination.Lyrics("mini_player_swipe_to_open_player"),
            settingsSearchFallbackDestination("settings_mini_player_swipe_to_open_player")
        )
    }

    @Test
    fun otherMiniPlayerRowsStayOnTheLyricsPage() {
        assertEquals(
            SettingsSearchFallbackDestination.Lyrics("mini_lyrics"),
            settingsSearchFallbackDestination("settings_mini_player_lyrics")
        )
        assertEquals(
            SettingsSearchFallbackDestination.Lyrics("mini_player_cover_rotation"),
            settingsSearchFallbackDestination("settings_mini_player_cover_rotation")
        )
        assertEquals(
            SettingsSearchFallbackDestination.Lyrics("mini_player_right_button"),
            settingsSearchFallbackDestination("settings_mini_player_right_button")
        )
    }

    @Test
    fun miniPlayerLongPressStaysOnListAppearance() {
        assertEquals(
            SettingsSearchFallbackDestination.Appearance("mini_player_long_press"),
            settingsSearchFallbackDestination("settings_mini_player_long_press")
        )
    }

    @Test
    fun playerPageStyleStillOpensPlayerAppearance() {
        assertEquals(
            SettingsSearchFallbackDestination.Appearance("player_page_style"),
            settingsSearchFallbackDestination("settings_player_page_style")
        )
    }
}
