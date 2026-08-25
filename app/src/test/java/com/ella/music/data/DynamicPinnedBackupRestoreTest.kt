package com.ella.music.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DynamicPinnedBackupRestoreTest {
    @Test
    fun allPinNamespacesAreRestorableDynamicStringPreferences() {
        assertTrue(isRestorableDynamicStringPreferenceKey("pinned_artist"))
        assertTrue(isRestorableDynamicStringPreferenceKey("pinned_album"))
        assertTrue(isRestorableDynamicStringPreferenceKey("pinned_category:genre"))
        assertTrue(isRestorableDynamicStringPreferenceKey("pinned_folder_playlist"))
    }

    @Test
    fun unrelatedBackupKeysAreNotAcceptedByDynamicRestorePath() {
        assertFalse(isRestorableDynamicStringPreferenceKey("artist"))
        assertFalse(isRestorableDynamicStringPreferenceKey("custom_unknown_setting"))
    }
}
