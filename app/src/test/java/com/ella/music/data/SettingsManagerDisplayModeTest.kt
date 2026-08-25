package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsManagerDisplayModeTest {

    @Test
    fun `new installations show the startup poster for one second`() {
        assertEquals(1_000, SettingsManager.DEFAULT_STARTUP_POSTER_DURATION_MS)
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
}
