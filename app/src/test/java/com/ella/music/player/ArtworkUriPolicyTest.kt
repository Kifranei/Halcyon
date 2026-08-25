package com.ella.music.player

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkUriPolicyTest {
    @Test
    fun localAlbumArtworkIsOnlyPublishedAsAnExplicitFallback() {
        val song = testSong(albumId = 47L)

        assertNull(song.artworkUriStringForMediaCenter(includeAlbumFallback = false))
        assertEquals(
            "content://media/external/audio/albumart/47",
            song.artworkUriStringForMediaCenter(includeAlbumFallback = true)
        )
    }

    @Test
    fun explicitOnlineArtworkDoesNotDependOnAlbumFallbackPolicy() {
        val song = testSong(albumId = 47L, coverUrl = "https://example.com/song-cover.jpg")

        assertEquals(
            "https://example.com/song-cover.jpg",
            song.artworkUriStringForMediaCenter(includeAlbumFallback = false)
        )
    }

    private fun testSong(albumId: Long, coverUrl: String = "") = Song(
        id = 1L,
        title = "Track",
        artist = "Artist",
        album = "Album",
        albumId = albumId,
        duration = 1_000L,
        path = "/music/track.flac",
        fileName = "track.flac",
        coverUrl = coverUrl
    )
}
