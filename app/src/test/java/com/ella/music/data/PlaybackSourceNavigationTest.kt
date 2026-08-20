package com.ella.music.data

import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceNavigationTest {
    @Test
    fun firstGroupKeepsSourceWhenSongsOverlap() {
        val songA = song(1L, "A")
        val songB = song(2L, "B")
        val sources = playbackSourcesForSongs(
            listOf(
                CategoryResumeKeys.playlist("alpha") to listOf(songA, songB),
                CategoryResumeKeys.playlist("beta") to listOf(songB, song(3L, "C"))
            )
        )
        assertEquals(CategoryResumeKeys.playlist("alpha"), sources[songA.playlistIdentityKey()])
        assertEquals(CategoryResumeKeys.playlist("alpha"), sources[songB.playlistIdentityKey()])
        assertEquals(CategoryResumeKeys.playlist("beta"), sources[song(3L, "C").playlistIdentityKey()])
    }

    @Test
    fun homeRecentUsesDashboardOnlyWhenAddingFreshSong() {
        val recent = listOf("a", "b", "c", "d", "e")
        assertTrue(shouldUseHomeSourceForRecentSong(emptyList(), recent, "a"))
        assertTrue(shouldUseHomeSourceForRecentSong(listOf("other"), recent, "a"))
        assertFalse(shouldUseHomeSourceForRecentSong(listOf("a", "x"), recent, "a"))
        assertFalse(shouldUseHomeSourceForRecentSong(listOf("b"), recent, "a"))
        assertFalse(shouldUseHomeSourceForRecentSong(emptyList(), recent, ""))
    }

    private fun song(id: Long, title: String): Song = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 1_000L,
        path = "/music/$title.flac",
        fileName = "$title.flac"
    )
}
