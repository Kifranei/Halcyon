package com.ella.music.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicScannerMergePolicyTest {
    @Test
    fun filesystemFallbackItemIsAddedWhenMediaStoreMissesPath() {
        val mediaStore = listOf(item(path = "/music/a.flac"))
        val fallback = listOf(item(path = "/music/b.flac"))

        val (merged, stats) = mergeMediaStoreAndFilesystemItems(mediaStore, fallback)

        assertEquals(listOf("/music/a.flac", "/music/b.flac"), merged.map { it.path })
        assertEquals(1, stats.mediaStoreItemCount)
        assertEquals(1, stats.filesystemFallbackItemCount)
        assertEquals(2, stats.mergedItemCount)
    }

    @Test
    fun duplicateFilesystemPathDoesNotCreateGhostItem() {
        val mediaStore = listOf(item(path = "/Music/A.FLAC"))
        val fallback = listOf(item(path = "/music/a.flac"))

        val (merged, stats) = mergeMediaStoreAndFilesystemItems(mediaStore, fallback)

        assertEquals(1, merged.size)
        assertEquals(0, stats.filesystemFallbackItemCount)
    }

    @Test
    fun primaryDocumentIdMapsToEmulatedStoragePath() {
        assertEquals("/storage/emulated/0", primaryDocumentIdToStoragePath("primary:"))
        assertEquals("/storage/emulated/0/Music/Custom", primaryDocumentIdToStoragePath("primary:Music/Custom"))
        assertEquals(null, primaryDocumentIdToStoragePath("12F0-21F1:Music"))
        assertEquals("primary:Music/Custom", storagePathToPrimaryDocumentId("/storage/emulated/0/Music/Custom"))
    }

    @Test
    fun renamePolicyTreatsOldAndNewPathsAsDifferentItems() {
        val mediaStore = emptyList<MediaStoreAudioItem>()
        val fallback = listOf(item(path = "/music/new-name.flac"))

        val (merged, stats) = mergeMediaStoreAndFilesystemItems(mediaStore, fallback)

        assertEquals("/music/new-name.flac", merged.single().path)
        assertEquals(1, stats.filesystemFallbackItemCount)
    }

    private fun item(path: String): MediaStoreAudioItem = MediaStoreAudioItem(
        id = path.hashCode().toLong(),
        title = path.substringAfterLast('/').substringBeforeLast('.'),
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 180_000L,
        path = path,
        fileName = path.substringAfterLast('/'),
        fileSize = 200L,
        mimeType = "audio/flac",
        dateAdded = 1_000L,
        dateModified = 1_000L,
        trackNumber = 0,
        discNumber = 0
    )
}
