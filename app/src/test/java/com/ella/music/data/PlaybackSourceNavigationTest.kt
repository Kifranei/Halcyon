package com.ella.music.data

import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSourceNavigationTest {
    @Test
    fun browseSourceKeysCanNavigateBackToTheirParentScreens() {
        assertEquals(true, PlaybackSourceNavigation.isNavigableSourceKey(CategoryResumeKeys.album(7L)))
        assertEquals(true, PlaybackSourceNavigation.isNavigableSourceKey(CategoryResumeKeys.playlist("mix")))
        assertEquals(true, PlaybackSourceNavigation.isNavigableSourceKey(CategoryResumeKeys.HOME))
        assertEquals(true, PlaybackSourceNavigation.isNavigableSourceKey(CategoryResumeKeys.DASHBOARD))
        assertEquals(false, PlaybackSourceNavigation.isNavigableSourceKey("search:night"))
        assertEquals(false, PlaybackSourceNavigation.isNavigableSourceKey(null))
    }

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
    fun recentSongSourceCanBeClearedBeforeIndependentPlayback() {
        val songKey = "recent-song"
        PlaybackSourceNavigation.updateSource(null)
        PlaybackSourceNavigation.recordSongSources(mapOf(songKey to CategoryResumeKeys.DASHBOARD))

        PlaybackSourceNavigation.clearSourceForSong(songKey)

        assertNull(PlaybackSourceNavigation.sourceForSong(songKey))
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
