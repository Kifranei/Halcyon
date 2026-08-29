package com.ella.music.ui.playlist

import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.toPlaylistSong
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaylistSortModelsTest {
    @Test
    fun playCountSortAggregatesSongCountsAcrossEachPlaylist() {
        val firstSong = song(1L, "First")
        val secondSong = song(2L, "Second")
        val playlists = listOf(
            playlist("low", firstSong),
            playlist("high", firstSong, secondSong),
            playlist("none")
        )
        val counts = mapOf(1L to 2, 2L to 5)

        assertEquals(
            listOf("high", "low", "none"),
            playlists.sortedForPlaylistList(PlaylistSortMode.PlayCount, counts).map { it.id }
        )
        assertEquals(
            listOf("none", "low", "high"),
            playlists.sortedForPlaylistList(PlaylistSortMode.PlayCountAsc, counts).map { it.id }
        )
    }

    @Test
    fun playlistSongPlayCountSortOrdersSongsInBothDirections() {
        val firstSong = song(1L, "First")
        val secondSong = song(2L, "Second")
        val thirdSong = song(3L, "Third")
        val songs = listOf(firstSong, secondSong, thirdSong)
        val counts = mapOf(1L to 2, 2L to 5)

        assertEquals(
            listOf(2L, 1L, 3L),
            songs.sortedForPlaylistDetail(PlaylistSongSortMode.PlayCount, counts).map { it.id }
        )
        assertEquals(
            listOf(3L, 1L, 2L),
            songs.sortedForPlaylistDetail(PlaylistSongSortMode.PlayCountAsc, counts).map { it.id }
        )
    }

    private fun playlist(id: String, vararg songs: Song) = UserPlaylist(
        id = id,
        name = id,
        songs = songs.map { it.toPlaylistSong(addedAt = 1L) },
        createdAt = 1L,
        updatedAt = 1L
    )

    private fun song(id: Long, title: String) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 1L,
        path = "/music/$id.mp3",
        fileName = "$id.mp3"
    )
}
