package com.ella.music.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class AppearanceSubpageHighlightTest {
    @Test
    fun mapsWallpaperTertiaryKeys() {
        listOf(
            "wallpaper",
            "beautiful_lyrics",
            "beautiful_lyrics_speed",
            "beautiful_lyrics_blur",
            "beautiful_lyrics_brightness",
            "apple_flow_speed",
            "player_dynamic_flow",
            "app_now_playing_flow_background",
            "player_background_opacity"
        ).forEach { key ->
            assertEquals(key, APPEARANCE_PAGE_WALLPAPER, appearanceSubpageForHighlight(key))
        }
    }

    @Test
    fun mapsPlayerTertiaryKeys() {
        listOf(
            "player_page",
            "player_landscape",
            "player_title_position",
            "player_show_song_annotation",
            "player_immersive",
            "hi_res_logo",
            "transport_button_outlines"
        ).forEach { key ->
            assertEquals(key, APPEARANCE_PAGE_PLAYER, appearanceSubpageForHighlight(key))
        }
    }

    @Test
    fun mapsListAndSystemBarsAndThemeKeys() {
        assertEquals(APPEARANCE_PAGE_LIST, appearanceSubpageForHighlight("search_click_playback_mode"))
        assertEquals(APPEARANCE_PAGE_LIST, appearanceSubpageForHighlight("search_reopen_behavior"))
        assertEquals(APPEARANCE_PAGE_LIST, appearanceSubpageForHighlight("auto_show_search_keyboard"))
        assertEquals(APPEARANCE_PAGE_SYSTEM_BARS, appearanceSubpageForHighlight("system_bars"))
        assertEquals(APPEARANCE_PAGE_SYSTEM_BARS, appearanceSubpageForHighlight("system_bars_reserve_space"))
        assertEquals(APPEARANCE_PAGE_THEME, appearanceSubpageForHighlight("player_bg_theme"))
        assertEquals(APPEARANCE_PAGE_THEME, appearanceSubpageForHighlight("app_icon"))
        assertEquals(APPEARANCE_PAGE_THEME, appearanceSubpageForHighlight("appearance"))
    }
}
