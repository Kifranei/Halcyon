package com.ella.music.data.scanner

import com.ella.music.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class PersistentMetadataCacheTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun cacheKeyNormalizesPathCaseAndTrims() {
        val key1 = PersistentMetadataCache.cacheKey("  /storage/Music/Song.FLAC  ", 1024L, 5000L)
        val key2 = PersistentMetadataCache.cacheKey("/storage/music/song.flac", 1024L, 5000L)
        assertEquals("/storage/music/song.flac|1024|5000", key1)
        assertEquals(key1, key2)
    }

    @Test
    fun putAndGetEnrichedMetadata() {
        val file = File(tempFolder.root, "test_cache.json")
        val cache = PersistentMetadataCache(file, ConcurrentHashMap())

        val enriched = Song(
            id = 1L,
            title = "Test Title",
            artist = "Test Artist",
            album = "Test Album",
            albumId = 2L,
            duration = 180_000L,
            path = "/storage/music/test.flac",
            fileName = "test.flac",
            fileSize = 10_000L,
            mimeType = "audio/flac",
            dateAdded = 1000L,
            dateModified = 2000L,
            trackNumber = 3,
            discNumber = 1,
            genre = "Rock",
            year = "2024",
            composer = "Test Composer"
        )

        cache.put(enriched)
        assertTrue(cache.contains(enriched))

        val shallow = Song(
            id = 1L,
            title = "test",
            artist = "Unknown Artist",
            album = "Unknown Album",
            albumId = 2L,
            duration = 180_000L,
            path = "/storage/music/test.flac",
            fileName = "test.flac",
            fileSize = 10_000L,
            mimeType = "audio/flac",
            dateAdded = 1000L,
            dateModified = 2000L
        )

        val applied = cache.get(shallow)
        assertNotNull(applied)
        assertEquals("Test Title", applied!!.title)
        assertEquals("Test Artist", applied.artist)
        assertEquals("Test Album", applied.album)
        assertEquals("Rock", applied.genre)
        assertEquals("2024", applied.year)
        assertEquals("Test Composer", applied.composer)
        assertEquals(3, applied.trackNumber)
        assertEquals(1, applied.discNumber)
    }

    @Test
    fun getReturnsNullWhenFileSizeOrDateModifiedMismatch() {
        val file = File(tempFolder.root, "test_cache.json")
        val cache = PersistentMetadataCache(file, ConcurrentHashMap())

        val song = Song(
            id = 1L,
            title = "Song",
            artist = "Artist",
            album = "Album",
            albumId = 1L,
            duration = 100_000L,
            path = "/music/song.mp3",
            fileName = "song.mp3",
            fileSize = 5000L,
            dateModified = 1000L
        )
        cache.put(song)

        val modifiedSong = song.copy(dateModified = 2000L)
        assertFalse(cache.contains(modifiedSong))
        assertNull(cache.get(modifiedSong))

        val resizedSong = song.copy(fileSize = 6000L)
        assertFalse(cache.contains(resizedSong))
        assertNull(cache.get(resizedSong))
    }

    @Test
    fun cachedMetadataApplyToUpdatesMetadata() {
        val cached = PersistentMetadataCache.CachedMetadata(
            path = "/storage/music/song.flac",
            fileSize = 1000L,
            dateModified = 2000L,
            title = "Cached Title",
            artist = "Cached Artist",
            album = "Cached Album",
            albumArtist = "Cached Album Artist",
            genre = "Rock",
            year = "2024",
            composer = "Composer",
            arranger = "Arranger",
            lyricist = "Lyricist",
            trackNumber = 5,
            discNumber = 2
        )

        val shallow = Song(
            id = 1L,
            title = "song",
            artist = "Unknown Artist",
            album = "Unknown Album",
            albumId = 2L,
            duration = 180_000L,
            path = "/storage/music/song.flac",
            fileName = "song.flac",
            fileSize = 1000L,
            dateModified = 2000L
        )

        val enriched = cached.applyTo(shallow)
        assertEquals("Cached Title", enriched.title)
        assertEquals("Cached Artist", enriched.artist)
        assertEquals("Cached Album", enriched.album)
        assertEquals("Cached Album Artist", enriched.albumArtist)
        assertEquals("Rock", enriched.genre)
        assertEquals("2024", enriched.year)
        assertEquals("Composer", enriched.composer)
        assertEquals("Arranger", enriched.arranger)
        assertEquals("Lyricist", enriched.lyricist)
        assertEquals(5, enriched.trackNumber)
        assertEquals(2, enriched.discNumber)
    }

    @Test
    fun clearEmptiesCache() {
        val file = File(tempFolder.root, "test_cache.json")
        val cache = PersistentMetadataCache(file, ConcurrentHashMap())
        cache.put(
            Song(
                id = 1L, title = "A", artist = "B", album = "C",
                albumId = 1L, duration = 1L, path = "/a.mp3",
                fileName = "a.mp3", fileSize = 100L, dateModified = 100L
            )
        )
        assertEquals(1, cache.size())
        cache.clear()
        assertEquals(0, cache.size())
    }
}
