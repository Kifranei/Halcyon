package com.ella.music.ui.components

import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import org.junit.Assert.assertEquals
import org.junit.Test

class ContinuePlaybackRowTest {
    @Test
    fun currentSourceUsesTheCurrentSongInsteadOfAStaleStoredSong() {
        val first = song(1L, "First")
        val current = song(2L, "Current")
        assertEquals(
            1,
            resolveContinuePlaybackIndex(
                songs = listOf(first, current),
                playbackSourceKey = "playlist:favorites",
                categoryKey = "playlist:favorites",
                currentSong = current,
                storedResumeKey = first.playlistIdentityKey()
            )
        )
    }

    @Test
    fun otherSourceFallsBackToStoredResumeSong() {
        val stored = song(1L, "Stored")
        val current = song(2L, "Current")
        assertEquals(
            0,
            resolveContinuePlaybackIndex(
                songs = listOf(stored, current),
                playbackSourceKey = "album:11",
                categoryKey = "playlist:favorites",
                currentSong = current,
                storedResumeKey = stored.playlistIdentityKey()
            )
        )
    }

    private fun song(id: Long, title: String) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 1_000L,
        path = "/$id.mp3",
        fileName = "$id.mp3"
    )
}
