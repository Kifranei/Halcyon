package com.ella.music.ui.player

import com.ella.music.data.model.playlistIdentityKey
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverFlowPositionTest {
    @Test fun repeatedTrackKeepsItsActualQueueSlot() {
        val song = com.ella.music.data.model.Song(1, "Song", "Artist", "Album", 1, 1000, "/music/a.mp3", "a.mp3")
        val other = song.copy(id = 2, path = "/music/b.mp3")
        val songs = listOf(song, other, song)
        assertEquals(2, resolveCoverFlowQueueIndex(songs, song.playlistIdentityKey(), 2))
        assertEquals(0, resolveCoverFlowQueueIndex(songs, song.playlistIdentityKey(), -1))
        assertEquals(1, resolveCoverFlowQueueIndex(songs, other.playlistIdentityKey(), 100))
    }

}
