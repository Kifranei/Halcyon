package com.ella.music.data.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaStoreLibraryIndexerTest {

    @Test
    fun reconstructsPathFromRelativePathWhenDataIsBlank() {
        assertTrue(
            MediaStoreLibraryIndexer.reconstructStoragePath(
                data = "",
                relativePath = "Music/Album/",
                displayName = "track.flac",
                volumeName = "external_primary"
            ).endsWith("/Music/Album/track.flac")
        )
    }

    @Test
    fun scanRootsSkipPlaceholderFolder() {
        val roots = MediaStoreLibraryIndexer.scanRoots(listOf("__ella_no_custom_folder__", "/storage/emulated/0/Music"))
        assertTrue(roots.any { it.endsWith("/Music") || it == "/storage/emulated/0/Music" })
        assertFalse(roots.contains("__ella_no_custom_folder__"))
    }
}
