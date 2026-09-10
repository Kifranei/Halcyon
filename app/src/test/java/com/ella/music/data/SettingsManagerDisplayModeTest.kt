package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test
import com.ella.music.data.lastfm.shortLabel

class SettingsManagerDisplayModeTest {

    @Test
    fun `new installations show the startup poster for one second`() {
        assertEquals(1_000, SettingsManager.DEFAULT_STARTUP_POSTER_DURATION_MS)
    }

    @Test
    fun `new installations use 20 percent default home card opacity`() {
        assertEquals(20, SettingsManager.DEFAULT_HOME_CARD_OPACITY)
    }

    @Test
    fun `new installations use the adaptive large-screen landscape player`() {
        assertEquals(
            SettingsManager.PLAYER_LANDSCAPE_STYLE_WIDE,
            SettingsManager.DEFAULT_PLAYER_LANDSCAPE_STYLE
        )
    }

    @Test
    fun `removed classic landscape style migrates to adaptive player`() {
        assertEquals(
            SettingsManager.PLAYER_LANDSCAPE_STYLE_WIDE,
            SettingsManager.normalizePlayerLandscapeStyle(1)
        )
    }

    @Test
    fun `player progress and visualizer styles normalize unknown values`() {
        assertEquals(
            SettingsManager.DEFAULT_PLAYER_PROGRESS_STYLE,
            SettingsManager.normalizePlayerProgressStyle(Int.MAX_VALUE)
        )
        assertEquals(
            SettingsManager.PLAYER_PROGRESS_STYLE_WAVEFORM,
            SettingsManager.normalizePlayerProgressStyle(SettingsManager.PLAYER_PROGRESS_STYLE_WAVEFORM)
        )
        assertEquals(
            SettingsManager.DEFAULT_AUDIO_VISUALIZER_STYLE,
            SettingsManager.normalizeAudioVisualizerStyle(null)
        )
        assertEquals(
            SettingsManager.AUDIO_VISUALIZER_STYLE_RAWS_SPECTRUM,
            SettingsManager.normalizeAudioVisualizerStyle(SettingsManager.AUDIO_VISUALIZER_STYLE_RAWS_SPECTRUM)
        )
        assertEquals(
            SettingsManager.AUDIO_VISUALIZER_STYLE_PARTICLES,
            SettingsManager.normalizeAudioVisualizerStyle(SettingsManager.AUDIO_VISUALIZER_STYLE_PARTICLES)
        )
        assertEquals(
            SettingsManager.AUDIO_VISUALIZER_STYLE_STRINGS,
            SettingsManager.normalizeAudioVisualizerStyle(SettingsManager.AUDIO_VISUALIZER_STYLE_STRINGS)
        )
        assertEquals(
            SettingsManager.AUDIO_VISUALIZER_STYLE_CLASSIC_BARS,
            SettingsManager.normalizeAudioVisualizerStyle(SettingsManager.AUDIO_VISUALIZER_STYLE_CLASSIC_BARS)
        )
        assertEquals(
            SettingsManager.AUDIO_VISUALIZER_STYLE_WATER_RIPPLE,
            SettingsManager.normalizeAudioVisualizerStyle(SettingsManager.AUDIO_VISUALIZER_STYLE_WATER_RIPPLE)
        )
    }

    @Test
    fun `legacy hidden system bars migrate to hide both`() {
        assertEquals(
            SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH,
            SettingsManager.resolveSystemBarsMode(
                storedMode = null,
                legacyHideSystemBars = true
            )
        )
    }

    @Test
    fun `stored system bars mode takes priority and is normalized`() {
        assertEquals(
            SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS,
            SettingsManager.resolveSystemBarsMode(
                storedMode = SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS,
                legacyHideSystemBars = true
            )
        )
        assertEquals(
            SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH,
            SettingsManager.resolveSystemBarsMode(
                storedMode = Int.MAX_VALUE,
                legacyHideSystemBars = false
            )
        )
    }

    @Test
    fun `hidden system bar area selection uses the inverse of the reserve flag`() {
        assertEquals(
            false,
            SettingsManager.systemBarsReserveSpaceForSelection(
                SettingsManager.SYSTEM_BARS_HIDDEN_SPACE_USE
            )
        )
        assertEquals(
            true,
            SettingsManager.systemBarsReserveSpaceForSelection(
                SettingsManager.SYSTEM_BARS_HIDDEN_SPACE_NOT_USE
            )
        )
        assertEquals(
            SettingsManager.SYSTEM_BARS_HIDDEN_SPACE_USE,
            SettingsManager.systemBarsSelectionForReserveSpace(false)
        )
        assertEquals(
            SettingsManager.SYSTEM_BARS_HIDDEN_SPACE_NOT_USE,
            SettingsManager.systemBarsSelectionForReserveSpace(true)
        )
    }

    @Test
    fun `visible dock items hide search unless merge is enabled`() {
        val items = listOf(
            SettingsManager.BOTTOM_DOCK_ITEM_HOME,
            SettingsManager.BOTTOM_DOCK_ITEM_SEARCH,
            SettingsManager.BOTTOM_DOCK_ITEM_LIBRARY
        )
        assertEquals(
            listOf(SettingsManager.BOTTOM_DOCK_ITEM_HOME, SettingsManager.BOTTOM_DOCK_ITEM_LIBRARY),
            SettingsManager.visibleBottomDockItems(items, mergeSearch = false)
        )
        assertEquals(items, SettingsManager.visibleBottomDockItems(items, mergeSearch = true))
    }

    @Test
    fun `search merge allows a fifth visible dock entry`() {
        val items = listOf("home", "library", "settings", "playlists", "search")
        assertEquals(4, SettingsManager.maxBottomDockItems(mergeSearch = false))
        assertEquals(5, SettingsManager.maxBottomDockItems(mergeSearch = true))
        assertEquals(items, SettingsManager.visibleBottomDockItems(items, mergeSearch = true))
    }

    @Test
    fun `dock normalization preserves five entries for search merge`() {
        assertEquals(
            "home,library,settings,playlists,search",
            SettingsManager.normalizeBottomDockItems("home,library,settings,playlists,search")
        )
    }

    @Test
    fun `normalize bottom dock items keeps a search slot`() {
        assertEquals(
            "home,search,library",
            SettingsManager.normalizeBottomDockItems("home,search,library")
        )
    }

    @Test
    fun `startup dock destination follows configured entries and defaults to home`() {
        assertEquals(
            SettingsManager.BOTTOM_DOCK_ITEM_HOME,
            SettingsManager.normalizeBottomDockStartupItem(
                value = null,
                configuredItems = listOf(
                    SettingsManager.BOTTOM_DOCK_ITEM_HOME,
                    SettingsManager.BOTTOM_DOCK_ITEM_LIBRARY
                )
            )
        )
        assertEquals(
            SettingsManager.BOTTOM_DOCK_ITEM_LIBRARY,
            SettingsManager.normalizeBottomDockStartupItem(
                value = SettingsManager.BOTTOM_DOCK_ITEM_LIBRARY,
                configuredItems = listOf(
                    SettingsManager.BOTTOM_DOCK_ITEM_HOME,
                    SettingsManager.BOTTOM_DOCK_ITEM_LIBRARY
                )
            )
        )
    }

    @Test
    fun `startup dock destination falls back when selected entry is removed`() {
        assertEquals(
            SettingsManager.BOTTOM_DOCK_ITEM_LIBRARY,
            SettingsManager.normalizeBottomDockStartupItem(
                value = SettingsManager.BOTTOM_DOCK_ITEM_SETTINGS,
                configuredItems = listOf(SettingsManager.BOTTOM_DOCK_ITEM_LIBRARY)
            )
        )
    }

    @Test
    fun `list quality display follows tablet phone and always modes`() {
        assertEquals(
            true,
            SettingsManager.shouldShowListQuality(
                SettingsManager.LIST_QUALITY_DISPLAY_TABLET,
                600
            )
        )
        assertEquals(
            false,
            SettingsManager.shouldShowListQuality(
                SettingsManager.LIST_QUALITY_DISPLAY_TABLET,
                411
            )
        )
        assertEquals(
            true,
            SettingsManager.shouldShowListQuality(
                SettingsManager.LIST_QUALITY_DISPLAY_PHONE,
                411
            )
        )
        assertEquals(
            false,
            SettingsManager.shouldShowListQuality(
                SettingsManager.LIST_QUALITY_DISPLAY_PHONE,
                600
            )
        )
        assertEquals(
            true,
            SettingsManager.shouldShowListQuality(
                SettingsManager.LIST_QUALITY_DISPLAY_ALWAYS,
                411
            )
        )
    }

    @Test
    fun `new installations default to DeepSeek AI provider`() {
        assertEquals("https://api.deepseek.com/v1", SettingsManager.DEFAULT_OPENAI_BASE_URL)
        assertEquals("deepseek-flash", SettingsManager.DEFAULT_OPENAI_MODEL)
    }

    @Test
    fun `biography providers expose compact source labels`() {
        assertEquals("N", com.ella.music.data.lastfm.ArtistBioMenuSource.Netease.shortLabel)
        assertEquals("L", com.ella.music.data.lastfm.ArtistBioMenuSource.LastFm.shortLabel)
        assertEquals("W", com.ella.music.data.lastfm.ArtistBioMenuSource.Wikipedia.shortLabel)
    }
}
