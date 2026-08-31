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
    fun systemFontPathsStayInBackupWithoutPacking() {
        assertTrue(isKeepableUnpackedFontPath("__system_default__"))
        assertTrue(isKeepableUnpackedFontPath("/system/fonts/Roboto.ttf"))
        assertFalse(isKeepableUnpackedFontPath("/data/user/0/com.ella.music/files/lyric_fonts/Inter.ttf"))
    }
}
