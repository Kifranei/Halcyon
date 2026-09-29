package com.ella.music.player

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSongIdentityTest {
    @Test
    fun prefersPlaylistSongWhenControllerStillReportsPreviousTrack() {
        val previous = testSong(id = 1L, title = "Asphodelus", path = "/music/asphodelus.flac")
        val next = testSong(id = 2L, title = "NieR", path = "/music/nier.flac")
        val resolved = resolveControllerPlaylistSong(
            currentIndex = 0,
            playlistSize = 1,
            itemSong = previous,
            playlistSong = next
        )
        assertEquals(next, resolved)
    }

    @Test
    fun keepsMediaItemExtrasWhenIdentityMatchesPlaylistSlot() {
        val queued = testSong(id = 2L, title = "NieR", path = "/music/nier.flac")
        val extras = queued.copy(coverUrl = "https://example.com/cover.jpg")
        val resolved = resolveControllerPlaylistSong(
            currentIndex = 0,
            playlistSize = 1,
            itemSong = extras,
            playlistSong = queued
        )
        assertEquals(extras, resolved)
    }

    @Test
    fun fallsBackToItemWhenControllerIndexIsOutsideNewPlaylist() {
        val previous = testSong(id = 1L, title = "Asphodelus", path = "/music/asphodelus.flac")
        val resolved = resolveControllerPlaylistSong(
            currentIndex = 15,
            playlistSize = 1,
            itemSong = previous,
            playlistSong = testSong(id = 2L, title = "NieR", path = "/music/nier.flac")
        )
        assertEquals(previous, resolved)
    }

    private fun testSong(id: Long, title: String, path: String) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = id,
        duration = 1_000L,
        path = path,
        fileName = path.substringAfterLast('/')
    )
}
