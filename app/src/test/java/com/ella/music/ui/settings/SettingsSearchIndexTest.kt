package com.ella.music.ui.settings

import com.ella.music.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchIndexTest {
    @Test
    fun everySearchEntryHasAnExplicitDestinationAndUsefulLabel() {
        assertTrue(settingsSearchCatalog.isNotEmpty())
        assertTrue(settingsSearchCatalog.all { it.titleRes != 0 })
        assertTrue(settingsSearchCatalog.all { it.target.toString().isNotBlank() })
        assertEquals(
            settingsSearchCatalog.size,
            settingsSearchCatalog.map { Triple(it.titleRes, it.target, it.sheet) }.distinct().size
        )
    }

    @Test
    fun deepControlsKeepTheirOwningPageAndSheet() {
        assertTrue(settingsSearchCatalog.any {
            it.titleRes == R.string.settings_app_display_scale &&
                it.target == SettingsSearchTarget.AppearancePage("theme")
        })
        assertEquals(setOf("sizing", "mini"), settingsSearchCatalog.filter {
            it.titleRes == R.string.player_lyric_font_scale
        }.map { it.sheet }.toSet())
        assertTrue(settingsSearchCatalog.any {
            it.titleRes == R.string.settings_xiaomi_super_island_lyric_content && it.sheet == "island"
        })
        assertTrue(settingsSearchCatalog.any {
            it.titleRes == R.string.settings_backup_webdav_password_label &&
                it.target == SettingsSearchTarget.Backup("")
        })
    }

    @Test
    fun importantRowsNavigateToTheirOwningSettingsPage() {
        assertEquals(
            SettingsSearchTarget.Lyrics("mini_player_swipe_to_open_player"),
            settingsSearchCatalog.first { it.titleRes == R.string.settings_mini_player_swipe_to_open_player }.target
        )
        assertEquals(
            SettingsSearchTarget.CoverMedia("dynamic_cover"),
            settingsSearchCatalog.first { it.titleRes == R.string.settings_dynamic_cover }.target
        )
        assertEquals(
            SettingsSearchTarget.HomeDisplay("home_top_actions"),
            settingsSearchCatalog.first { it.titleRes == R.string.home_daily_shuffle }.target
        )
    }

    @Test
    fun staleSubstringFallbackIsGone() {
        assertEquals(APPEARANCE_PAGE_LIST, appearanceSubpageForHighlight("mini_player_long_press"))
        assertEquals(APPEARANCE_PAGE_PLAYER, appearanceSubpageForHighlight("player_page"))
        assertEquals(
            APPEARANCE_PAGE_PLAYER_ACTION_MENU,
            appearanceSubpageForHighlight("player_action_menu")
        )
    }
}
