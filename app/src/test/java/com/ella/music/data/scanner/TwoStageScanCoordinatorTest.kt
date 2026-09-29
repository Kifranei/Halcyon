package com.ella.music.data.scanner

import com.ella.music.data.model.Song
import com.ella.music.data.scanner.TwoStageScanCoordinator.looksLikeLastFolderName
import com.ella.music.data.scanner.TwoStageScanCoordinator.needsMetadataPlaceholderRefresh
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TwoStageScanCoordinatorTest {

    @Test
    fun needsMetadataPlaceholderRefreshIdentifiesPlaceholders() {
        val unknownArtist = createSong(artist = "Unknown Artist", album = "Real Album")
        assertTrue(unknownArtist.needsMetadataPlaceholderRefresh())

        val unknownAlbum = createSong(artist = "Real Artist", album = "Unknown Album")
        assertTrue(unknownAlbum.needsMetadataPlaceholderRefresh())

        val folderAlbum = createSong(
            artist = "Real Artist",
            album = "MyFolder",
            path = "/storage/emulated/0/Music/MyFolder/track01.flac"
        )
        assertTrue(folderAlbum.needsMetadataPlaceholderRefresh())

        val legitimateSong = createSong(
            artist = "Chopin",
            album = "Nocturnes",
            path = "/storage/emulated/0/Music/Classical/track01.flac"
        )
        assertFalse(legitimateSong.needsMetadataPlaceholderRefresh())
    }

    @Test
    fun looksLikeLastFolderNameMatchesCorrectly() {
        assertTrue("AlbumDir".looksLikeLastFolderName("/music/AlbumDir/song.mp3"))
        assertTrue("albumdir".looksLikeLastFolderName("/music/AlbumDir/song.mp3"))
        assertFalse("OtherDir".looksLikeLastFolderName("/music/AlbumDir/song.mp3"))
        assertFalse("".looksLikeLastFolderName("/music/AlbumDir/song.mp3"))
    }

    @Test
    fun eventPropertiesArePreserved() {
        val song = createSong(title = "S", artist = "A", album = "B")
        val quick = TwoStageScanEvent.QuickCompleted(listOf(song), 1234L)
        assertEquals(1, quick.songs.size)
        assertEquals(1234L, quick.timeMs)

        val batch = TwoStageScanEvent.EnrichBatchCompleted(
            enrichedBatch = listOf(song),
            processed = 64,
            total = 128,
            cacheHits = 10,
            enrichedCount = 54
        )
        assertEquals(64, batch.processed)
        assertEquals(128, batch.total)
        assertEquals(10, batch.cacheHits)
        assertEquals(54, batch.enrichedCount)

        val completed = TwoStageScanEvent.FullyCompleted(
            allSongs = listOf(song),
            totalTimeMs = 5000L,
            cacheHits = 800,
            enrichedCount = 76
        )
        assertEquals(1, completed.allSongs.size)
        assertEquals(5000L, completed.totalTimeMs)
        assertEquals(800, completed.cacheHits)
        assertEquals(76, completed.enrichedCount)
    }

    private fun createSong(
        title: String = "Title",
        artist: String = "Artist",
        album: String = "Album",
        path: String = "/music/folder/song.flac"
    ): Song = Song(
        id = 1L,
        title = title,
        artist = artist,
        album = album,
        albumId = 1L,
        duration = 180_000L,
        path = path,
        fileName = "song.flac",
        fileSize = 1024L,
        dateModified = 1000L
    )
}
