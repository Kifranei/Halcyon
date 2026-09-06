package com.ella.music.data.scanner

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LibraryScanFingerprintTest {
    @Test
    fun missingCacheAlwaysNeedsUpdate() {
        assertTrue(fingerprint().needsUpdateAgainst(cached = null))
    }

    @Test
    fun unchangedSnapshotIsReused() {
        val current = fingerprint()
        assertFalse(current.needsUpdateAgainst(current))
    }

    @Test
    fun preservedTimestampStillUpdatesWhenMediaStoreTitleChanged() {
        val cached = fingerprint(title = "Old Title", dateModified = 1_000L, fileSize = 200L)
        val current = fingerprint(title = "New Title", dateModified = 1_000L, fileSize = 200L)
        assertTrue(current.needsUpdateAgainst(cached))
    }

    @Test
    fun emptyMediaStoreArtistDoesNotForceUpdate() {
        val cached = fingerprint(artist = "Unknown Artist")
        val current = fingerprint(artist = "")
        assertFalse(current.needsUpdateAgainst(cached))
    }

    @Test
    fun localFileSizeChangeNeedsUpdate() {
        val cached = fingerprint(fileSize = 200L, dateModified = 1_000L)
        val current = fingerprint(fileSize = 400L, dateModified = 1_000L)
        assertTrue(current.needsUpdateAgainst(cached))
    }

    @Test
    fun durationChangeNeedsUpdate() {
        val cached = fingerprint(duration = 180_000L)
        val current = fingerprint(duration = 181_000L)
        assertTrue(current.needsUpdateAgainst(cached))
    }

    @Test
    fun quickFingerprintCatchesSameSizeEditWithPreservedTimestamp() {
        val file = File.createTempFile("halcyon-fingerprint", ".mp3")
        try {
            file.writeBytes(ByteArray(32 * 1024) { index -> (index and 0x7f).toByte() })
            val originalTimestamp = file.lastModified()
            val first = quickLocalFileFingerprint(file.absolutePath)
            file.outputStream().use { output ->
                output.write(ByteArray(16 * 1024) { 0x5a.toByte() })
                output.write(ByteArray(16 * 1024) { index -> (index and 0x7f).toByte() })
            }
            file.setLastModified(originalTimestamp)
            val second = quickLocalFileFingerprint(file.absolutePath)
            assertNotEquals(first, second)
        } finally {
            file.delete()
        }
    }

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

    private fun fingerprint(
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        fileSize: Long = 200L,
        dateModified: Long = 1_000L,
        duration: Long = 180_000L
    ) = LibraryScanFingerprint(
        key = "content://media/external/audio/media/1",
        path = "/music/Song.flac",
        fileSize = fileSize,
        dateModified = dateModified,
        title = title,
        artist = artist,
        album = album,
        duration = duration
    )
}
