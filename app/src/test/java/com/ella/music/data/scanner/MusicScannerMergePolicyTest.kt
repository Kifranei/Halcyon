package com.ella.music.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
    fun prefersMediaStoreDataPathOverRelativePath() {
        assertEquals(
            "/storage/emulated/0/Music/a.flac",
            MediaStoreLibraryIndexer.reconstructStoragePath(
                data = "/storage/emulated/0/Music/a.flac",
                relativePath = "Download/",
                displayName = "ignored.flac",
                volumeName = "external_primary"
            )
        )
    }

    @Test
    fun primaryDocumentIdMapsToEmulatedStoragePath() {
        assertEquals("/storage/emulated/0", primaryDocumentIdToStoragePath("primary:"))
        assertEquals("/storage/emulated/0/Music/Custom", primaryDocumentIdToStoragePath("primary:Music/Custom"))
        assertEquals(null, primaryDocumentIdToStoragePath("12F0-21F1:Music"))
        assertEquals("primary:Music/Custom", storagePathToPrimaryDocumentId("/storage/emulated/0/Music/Custom"))
    }

    @Test
    fun folderFilterExcludesDownloadWithCanonicalAndRelativePaths() {
        val path1 = "/storage/emulated/0/Download/track.flac"
        val path2 = "/sdcard/Download/track.flac"
        val pathMusic = "/storage/emulated/0/Music/track.flac"

        // Exclude by full canonical path
        assertFalse(path1.isAllowedByFolderFilters(emptyList(), listOf("/storage/emulated/0/Download")))
        assertFalse(path2.isAllowedByFolderFilters(emptyList(), listOf("/storage/emulated/0/Download")))
        assertTrue(pathMusic.isAllowedByFolderFilters(emptyList(), listOf("/storage/emulated/0/Download")))

        // Exclude by /sdcard path
        assertFalse(path1.isAllowedByFolderFilters(emptyList(), listOf("/sdcard/Download")))
        assertFalse(path2.isAllowedByFolderFilters(emptyList(), listOf("/sdcard/Download")))

        // Exclude by relative folder name
        assertFalse(path1.isAllowedByFolderFilters(emptyList(), listOf("Download")))
        assertFalse(path2.isAllowedByFolderFilters(emptyList(), listOf("Download")))
    }

    @Test
    fun folderFilterIncludesOnlySpecifiedFolders() {
        val pathMusic = "/storage/emulated/0/Music/track.flac"
        val pathMusicSd = "/sdcard/Music/track.flac"
        val pathDownload = "/storage/emulated/0/Download/track.flac"

        val include = listOf("/storage/emulated/0/Music")
        assertTrue(pathMusic.isAllowedByFolderFilters(include, emptyList()))
        assertTrue(pathMusicSd.isAllowedByFolderFilters(include, emptyList()))
        assertFalse(pathDownload.isAllowedByFolderFilters(include, emptyList()))
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
