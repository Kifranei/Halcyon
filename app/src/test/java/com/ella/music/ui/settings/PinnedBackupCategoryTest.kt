package com.ella.music.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinnedBackupCategoryTest {
    @Test
    fun artistAndAlbumPinsFollowLibraryBackupSelection() {
        assertEquals(BackupType.LibraryAndScan, "pinned_artist".backupType())
        assertEquals(BackupType.LibraryAndScan, "pinned_album".backupType())
    }

    @Test
    fun otherPinNamespacesUseTheirExistingBackupCategories() {
        assertEquals(BackupType.LibraryAndScan, "pinned_category:genre".backupType())
        assertEquals(BackupType.FolderPlaylists, "pinned_folder_playlist".backupType())
    }

    @Test
    fun homeFeatureWallpaperIsExcludedFromPortableSettings() {
        assertTrue("home_feature_wallpaper_uri".isBackupExcludedSettingKey())
        assertFalse("home_card_color".isBackupExcludedSettingKey())
    }

    @Test
    fun aiProtocolFollowsAiBackupSelection() {
        assertEquals(BackupType.AiConfigAndChat, "ai_api_protocol".backupType())
        assertEquals(BackupType.AiConfigAndChat, "openai_model".backupType())
    }

    @Test
    fun lyricoPluginSettingsFollowOnlineSourcesBackupSelection() {
        assertEquals(BackupType.OnlineSources, "lyrico_plugin_enabled_ids".backupType())
        assertEquals(BackupType.OnlineSources, "lyrico_plugin_cache".backupType())
    }

    @Test
    fun lyricSourcePriorityCanDisableEntries() {
        assertEquals(
            "embedded_ttml,external_ttml",
            com.ella.music.data.SettingsManager.normalizeLyricSourcePriority(
                "embedded_ttml,external_ttml"
            )
        )
        assertEquals(
            "",
            com.ella.music.data.SettingsManager.normalizeLyricSourcePriority("")
        )
    }

    @Test
    fun playerProgressInfoPriorityMigratesFromBooleans() {
        assertEquals(
            "audio_info,output_device",
            com.ella.music.data.SettingsManager.migratePlayerProgressInfoPriority(
                stored = null,
                showQuality = false,
                showAudioInfo = true,
                showOutputDevice = true
            )
        )
        assertEquals(
            "",
            com.ella.music.data.SettingsManager.normalizePlayerProgressInfoPriority("")
        )
        assertEquals(
            "output_device,quality",
            com.ella.music.data.SettingsManager.normalizePlayerProgressInfoPriority(
                "output_device,quality,unknown"
            )
        )
    }

    @Test
    fun secondsInputParsesToMilliseconds() {
        assertEquals(1_000, parseSecondsInputToMs("1", 100, 3_000))
        assertEquals(1_250, parseSecondsInputToMs("1.25", 100, 3_000))
        assertEquals(100, parseSecondsInputToMs("0,10", 100, 3_000))
        assertEquals(null, parseSecondsInputToMs("abc", 100, 3_000))
        assertEquals(null, parseSecondsInputToMs("12", 100, 3_000))
        assertEquals("1.00", formatMsAsSecondsInput(1_000))
    }

    @Test
    fun systemFontPathsStayInBackupWithoutPacking() {
        assertTrue(isKeepableUnpackedFontPath("__system_default__"))
        assertTrue(isKeepableUnpackedFontPath("/system/fonts/Roboto.ttf"))
        assertFalse(isKeepableUnpackedFontPath("/data/user/0/com.ella.music/files/lyric_fonts/Inter.ttf"))
    }

    @Test
    fun wallpapersAndImagesFollowDedicatedBackupSelection() {
        assertEquals(BackupType.WallpapersAndImages, "app_wallpaper_uri".backupType())
        assertEquals(BackupType.WallpapersAndImages, "startup_poster_uri".backupType())
        assertEquals(BackupType.WallpapersAndImages, "player_background_uri".backupType())
        assertEquals(BackupType.WallpapersAndImages, "hi_res_logo_uri".backupType())
    }

    @Test
    fun fontSettingsFollowFontsBackupSelection() {
        assertEquals(BackupType.Fonts, "lyric_font_path".backupType())
        assertEquals(BackupType.Fonts, "global_western_font_path".backupType())
        assertEquals(BackupType.Fonts, "global_cjk_font_path".backupType())
        assertEquals(BackupType.Fonts, "lyric_original_western_font_path".backupType())
        assertEquals(BackupType.Fonts, "imported_font_custom.ttf".backupType())
        assertEquals(BackupType.Fonts, "lyric_share_use_lyric_font".backupType())
        assertEquals(BackupType.Fonts, "desktop_lyric_font_scale".backupType())
    }

    @Test
    fun backupFileNameRecognizesDateTimeFormat() {
        val fileName = generateBackupFileName("zip")
        assertTrue(fileName.matches(Regex("""^\d{4}-\d{2}-\d{2}--\d{2}-\d{2}-\d{2}\.zip""")))
        assertTrue(fileName.isHalcyonBackupFileName())
        assertTrue("2026-09-12--11-30-00.zip".isHalcyonBackupFileName())
        assertTrue("halcyon_backup_12345.zip".isHalcyonBackupFileName())
        assertTrue("halcyon_settings_12345.zip".isHalcyonBackupFileName())
    }
}
