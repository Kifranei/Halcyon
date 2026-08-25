package com.ella.music.ui.search

import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySearchModelsTest {
    @Test
    fun musicVideoFilterIsASongResultFilter() {
        assertEquals(SearchFilter.MusicVideos, SearchFilter.fromRouteType("mv"))
        assertEquals(SearchFilter.MusicVideos, SearchFilter.fromRouteType("musicvideo"))
        assertTrue(SearchFilter.MusicVideos.acceptsSongResults)
    }

    @Test
    fun excludedSearchResultsCreateASingleSongQueue() {
        val songs = listOf(song(1), song(2), song(3))

        val selection = searchPlaybackSelection(songs, 1, excludeResultsFromPlaylist = true)

        assertEquals(listOf(2L), selection.songs.map(Song::id))
        assertEquals(0, selection.startIndex)
    }

    @Test
    fun normalSearchKeepsTheResultQueueAndSelectedIndex() {
        val songs = listOf(song(1), song(2), song(3))

        val selection = searchPlaybackSelection(songs, 1, excludeResultsFromPlaylist = false)

        assertEquals(listOf(1L, 2L, 3L), selection.songs.map(Song::id))
        assertEquals(1, selection.startIndex)
    }

    @Test
    fun searchCanInsertResultSetAfterCurrentSongAndPlaySelection() {
        val queue = listOf(song(1), song(2), song(3), song(4), song(5))
        val results = listOf(song(7), song(8), song(9))

        val selection = searchPlaybackSelection(
            resultSongs = results,
            selectedIndex = 1,
            excludeResultsFromPlaylist = false,
            playbackMode = SettingsManager.SEARCH_CLICK_INSERT_NEXT,
            currentQueue = queue,
            currentSong = queue[2]
        )

        assertEquals(listOf(1L, 2L, 3L, 7L, 8L, 9L, 4L, 5L), selection.songs.map(Song::id))
        assertEquals(4, selection.startIndex)
    }

    @Test
    fun searchCanAppendOnlySelectedSongAndPlayIt() {
        val queue = listOf(song(1), song(2), song(3))
        val results = listOf(song(7), song(8), song(9))

        val selection = searchPlaybackSelection(
            resultSongs = results,
            selectedIndex = 1,
            excludeResultsFromPlaylist = true,
            playbackMode = SettingsManager.SEARCH_CLICK_APPEND,
            currentQueue = queue,
            currentSong = queue[1]
        )

        assertEquals(listOf(1L, 2L, 3L, 8L), selection.songs.map(Song::id))
        assertEquals(3, selection.startIndex)
    }

    @Test
    fun appendTargetsTheNewOccurrenceWhenSongAlreadyExistsInQueue() {
        val duplicate = song(8)
        val queue = listOf(song(1), duplicate, song(3))

        val selection = searchPlaybackSelection(
            resultSongs = listOf(duplicate),
            selectedIndex = 0,
            excludeResultsFromPlaylist = true,
            playbackMode = SettingsManager.SEARCH_CLICK_APPEND,
            currentQueue = queue,
            currentSong = queue.first()
        )

        assertEquals(listOf(1L, 8L, 3L, 8L), selection.songs.map(Song::id))
        assertEquals(3, selection.startIndex)
    }

    @Test
    fun insertNextTargetsTheNewOccurrenceWhenSongAlreadyExistsInQueue() {
        val duplicate = song(8)
        val queue = listOf(song(1), duplicate, song(3))

        val selection = searchPlaybackSelection(
            resultSongs = listOf(duplicate),
            selectedIndex = 0,
            excludeResultsFromPlaylist = true,
            playbackMode = SettingsManager.SEARCH_CLICK_INSERT_NEXT,
            currentQueue = queue,
            currentSong = queue.first()
        )

        assertEquals(listOf(1L, 8L, 8L, 3L), selection.songs.map(Song::id))
        assertEquals(1, selection.startIndex)
    }

    private fun song(id: Long) = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        albumId = id,
        duration = 180_000L,
        path = "/music/$id.mp3",
        fileName = "$id.mp3"
    )
}
