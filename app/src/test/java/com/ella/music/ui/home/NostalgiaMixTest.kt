package com.ella.music.ui.home

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NostalgiaMixTest {
    @Test
    fun sameDayKeepsTheSameThirtyToFiftySongs() {
        val songs = List(200) { index -> song(index.toLong()) }
        val first = nostalgiaPlaylist(songs, epochDay = 20_000L)
        val second = nostalgiaPlaylist(songs.reversed(), epochDay = 20_000L)
        assertEquals(first, second)
        assertTrue(first.size in NOSTALGIA_MIN_SONGS..NOSTALGIA_MAX_SONGS)
        assertEquals(first.size, first.distinctBy { it.id }.size)
    }

    @Test
    fun nextDayChangesTheList() {
        val songs = List(80) { index -> song(index.toLong()) }
        val today = nostalgiaPlaylist(songs, epochDay = 20_000L)
        val tomorrow = nostalgiaPlaylist(songs, epochDay = 20_001L)
        assertTrue(today != tomorrow)
    }

    @Test
    fun smallLibraryPlaysEverything() {
        val songs = List(12) { index -> song(index.toLong()) }
        assertEquals(12, nostalgiaPlaylist(songs, epochDay = 3L).size)
    }

    private fun song(id: Long) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 1_000L,
        path = "/$id.mp3",
        fileName = "$id.mp3"
    )
}
