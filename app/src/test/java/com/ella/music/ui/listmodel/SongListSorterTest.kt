package com.ella.music.ui.listmodel

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongListSorterTest {
    @Test
    fun releaseYearParsesFirstFourDigitYear() {
        assertEquals(2024, song(year = "2024").releaseYearOrNull())
        assertEquals(2024, song(year = "2024-06-01").releaseYearOrNull())
        assertEquals(null, song(year = "").releaseYearOrNull())
        assertEquals(null, song(year = "unknown").releaseYearOrNull())
    }

    @Test
    fun fileNameSortUsesFileNameThenPathLeaf() {
        val songs = listOf(
            song(id = 1, title = "B", fileName = "", path = "/music/b.flac"),
            song(id = 2, title = "A", fileName = "a.flac", path = "/music/z.flac")
        )

        val sorted = LibraryListSorter.sortSongs(
            songs,
            SortSpec(SongSortField.FileName)
        ).items

        assertEquals(listOf(2L, 1L), sorted.map { it.id })
        assertEquals("a.flac", sorted[0].resolvedFileName())
        assertEquals("b.flac", sorted[1].resolvedFileName())
    }

    @Test
    fun titleSortKeepsStableOrderWhenKeysMatch() {
        val songs = listOf(
            song(id = 1, title = "Same"),
            song(id = 2, title = "Same"),
            song(id = 3, title = "中文")
        )

        val sorted = LibraryListSorter.sortSongs(
            songs,
            SortSpec(SongSortField.Title)
        ).items

        assertEquals(listOf(1L, 2L), sorted.take(2).map { it.id })
        assertTrue(sorted.any { it.title == "中文" })
    }

    @Test
    fun customSortPreservesPlaylistOrderAndDescReversesIt() {
        val songs = listOf(
            song(id = 1, title = "One"),
            song(id = 2, title = "Two"),
            song(id = 3, title = "Three")
        )

        val custom = LibraryListSorter.sortSongs(
            songs,
            SortSpec(SongSortField.Custom)
        ).items
        val customDesc = LibraryListSorter.sortSongs(
            songs,
            SortSpec(SongSortField.Custom, SortDirection.Descending)
        ).items

        assertEquals(listOf(1L, 2L, 3L), custom.map { it.id })
        assertEquals(listOf(3L, 2L, 1L), customDesc.map { it.id })
    }

    @Test
    fun releaseDateDescendingUsesDescendingAlbumThenAscendingDiscAndTrack() {
        val songs = listOf(
            song(id = 1, album = "Alpha", year = "2024", discNumber = 1, trackNumber = 1),
            song(id = 2, album = "Zulu", year = "2024", discNumber = 1, trackNumber = 2),
            song(id = 3, album = "Zulu", year = "2024", discNumber = 1, trackNumber = 1),
            song(id = 4, album = "Unknown", year = "")
        )

        val sorted = songs.sortedByReleaseDate(SortDirection.Descending)

        assertEquals(listOf(3L, 2L, 1L, 4L), sorted.map { it.id })
    }

    @Test
    fun releaseDateAscendingKeepsAscendingAlbumOrder() {
        val songs = listOf(
            song(id = 1, album = "Zulu", year = "2024"),
            song(id = 2, album = "Alpha", year = "2024")
        )

        assertEquals(
            listOf(2L, 1L),
            songs.sortedByReleaseDate(SortDirection.Ascending).map { it.id }
        )
    }

    @Test
    fun fastIndexSectionsResolveAsciiAndNonAsciiTitles() {
        assertEquals("A", FastIndexSectionResolver.sectionForText("Alice"))

        val section = FastIndexSectionResolver.sectionForText("中文")

        assertTrue(section.isNotBlank())
    }

    private fun song(
        id: Long = 1L,
        title: String = "Title",
        album: String = "Album",
        path: String = "/music/title.flac",
        fileName: String = "title.flac",
        year: String = "",
        discNumber: Int = 0,
        trackNumber: Int = 0
    ): Song =
        Song(
            id = id,
            title = title,
            artist = "Artist",
            album = album,
            albumId = 1L,
            duration = 120_000L,
            path = path,
            fileName = fileName,
            year = year,
            discNumber = discNumber,
            trackNumber = trackNumber
        )

    private fun Song.releaseYearOrNull(): Int? =
        year.trim().takeIf { it.length >= 4 }?.substring(0, 4)?.toIntOrNull()
}
