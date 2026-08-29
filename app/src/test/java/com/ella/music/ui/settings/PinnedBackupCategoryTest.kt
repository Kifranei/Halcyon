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
}
