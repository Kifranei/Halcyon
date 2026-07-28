package com.ella.music.ui.artist

import com.ella.music.data.model.Album
import org.junit.Assert.assertEquals
import org.junit.Test

class ArtistAlbumSortTest {
    @Test
    fun yearDescendingUsesDescendingAlbumNameAndKeepsUnknownDatesLast() {
        val albums = listOf(
            album(id = 1, name = "Alpha", year = "2024"),
            album(id = 2, name = "Zulu", year = "2024"),
            album(id = 3, name = "Unknown", year = "")
        )

        val sorted = albums.sortedForArtistAlbumDetail(
            ArtistDetailAlbumSortMode.YearDesc,
            emptyMap()
        )

        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun yearAscendingStillUsesAscendingAlbumName() {
        val albums = listOf(
            album(id = 1, name = "Zulu", year = "2024"),
            album(id = 2, name = "Alpha", year = "2024")
        )

        val sorted = albums.sortedForArtistAlbumDetail(
            ArtistDetailAlbumSortMode.YearAsc,
            emptyMap()
        )

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
    }

    private fun album(id: Long, name: String, year: String) = Album(
        id = id,
        name = name,
        artist = "Artist",
        songCount = 1,
        year = year
    )
}
