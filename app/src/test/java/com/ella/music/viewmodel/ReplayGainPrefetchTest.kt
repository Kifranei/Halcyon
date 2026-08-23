package com.ella.music.viewmodel

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayGainPrefetchTest {
    @Test
    fun prefetchesFollowingSongsAndWrapsQueue() {
        val songs = (1L..4L).map(::song)

        assertEquals(
            listOf(songs[3], songs[0], songs[1]),
            replayGainPrefetchSongs(songs, songs[2], count = 3)
        )
    }

    @Test
    fun excludesCurrentSongFromSingleItemQueue() {
        val onlySong = song(1L)

        assertTrue(replayGainPrefetchSongs(listOf(onlySong), onlySong, count = 3).isEmpty())
    }

    private fun song(id: Long): Song = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 1_000L,
        path = "/music/$id.flac",
        fileName = "$id.flac"
    )
}
