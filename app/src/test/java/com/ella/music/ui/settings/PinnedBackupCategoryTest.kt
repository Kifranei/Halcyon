package com.ella.music.ui.settings

import org.junit.Assert.assertEquals
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
}
