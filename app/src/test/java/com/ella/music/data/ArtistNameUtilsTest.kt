package com.ella.music.data

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import com.ella.music.data.model.Song

class ArtistNameUtilsTest {
    @After
    fun tearDown() {
        NameSplitConfigStore.artistCustomSeparators = emptyList()
        NameSplitConfigStore.artistProtectedNames = emptyList()
        NameSplitConfigStore.genreCustomSeparators = emptyList()
        NameSplitConfigStore.genreProtectedNames = emptyList()
        NameSplitConfigStore.tagIgnoreCase = false
        NameSplitConfigStore.parseFeaturedArtists = false
    }

    @Test
    fun splitArtistNames_doesNotUseImplicitSeparators() {
        assertEquals(listOf("R!N/Gemie/澤野弘之"), splitArtistNames("R!N/Gemie/澤野弘之"))
    }

    @Test
    fun splitArtistNames_appliesProtectedNamesBeforeCustomSeparator() {
        NameSplitConfigStore.artistCustomSeparators = listOf("/")
        NameSplitConfigStore.artistProtectedNames = listOf("R!N/Gemie")

        assertEquals(listOf("R!N/Gemie", "澤野弘之"), splitArtistNames("R!N/Gemie/澤野弘之"))
    }

    @Test
    fun splitGenreNames_doesNotUseImplicitSeparators() {
        assertEquals(listOf("Rock/Pop"), splitGenreNames("Rock/Pop"))
    }

    @Test
    fun artistNamesForSong_addsFeaturedArtistsAndDeduplicatesTheTrackArtist() {
        NameSplitConfigStore.artistCustomSeparators = listOf("/")

        val song = Song(
            id = 1L,
            title = "Main song (feat. Guest / Main)",
            artist = "Main",
            album = "Album",
            albumId = 1L,
            duration = 1L,
            path = "/music/main.mp3",
            fileName = "main.mp3"
        )

        assertEquals(
            listOf("Main", "Guest"),
            artistNamesForSong(song, includeFeaturedArtists = true)
        )
    }
}
