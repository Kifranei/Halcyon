package com.ella.music.data.scanner

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicScannerShallowSongTest {
    @Test
    fun shallowSongFallsBackToFilenameAndUnknownPlaceholders() {
        val song = item(
            title = "",
            artist = "",
            album = "",
            duration = 215_000L
        ).toShallowSong()

        requireNotNull(song)
        assertEquals("Track 01", song.title)
        assertEquals("Unknown Artist", song.artist)
        assertEquals("Unknown Album", song.album)
        assertEquals(215_000L, song.duration)
    }

    @Test
    fun shallowSongKeepsUnknownMediaStoreDuration() {
        val song = item(title = "", duration = 0L).toShallowSong()
        requireNotNull(song)
        assertEquals(0L, song.duration)
        assertEquals("Track 01", song.title)
    }

    @Test
    fun shallowSongSkipsKnownTooShortDuration() {
        assertNull(item(duration = 999L).toShallowSong(minDurationMs = 1_000L))
    }

    @Test
    fun shallowSongSkipsUnknownDurationWithoutMediaStoreId() {
        assertNull(item(duration = 0L).copy(id = -12L).toShallowSong())
    }

    private fun item(
        title: String = "Song",
        artist: String = "Artist",
        album: String = "Album",
        duration: Long = 180_000L
    ): MediaStoreAudioItem = MediaStoreAudioItem(
        id = 1L,
        title = title,
        artist = artist,
        album = album,
        albumId = 2L,
        duration = duration,
        path = "/music/Track 01.flac",
        fileName = "Track 01.flac",
        fileSize = 1024L,
        mimeType = "audio/flac",
        dateAdded = 1_000L,
        dateModified = 2_000L,
        trackNumber = 3,
        discNumber = 1
    )
}
