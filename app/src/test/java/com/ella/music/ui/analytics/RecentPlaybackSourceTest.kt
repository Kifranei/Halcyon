package com.ella.music.ui.analytics

import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.toPlaylistSong
import org.junit.Assert.*
import org.junit.Test

class RecentPlaybackSourceTest {
    private val songs = (1L..150L).map {
        Song(it, "Song $it", "Artist", "Album", 1L, 1000L, "/Music/$it.flac", "$it.flac")
    }
    private val playlist = UserPlaylist("p", "华语", songs.map { it.toPlaylistSong(0) }, 0, 0)
    private fun entry(id: String, time: Long, source: String = "playlist:p", video: String = "") =
        PlaybackHistoryEntry(id, 1L, "Song 1", "Artist", "Album", time, categorySourceKey = source, mediaUri = video)

    @Test fun playlistPlaybackOnlyAddsItsActualCategoryAndUsesFullCount() {
        val rows = buildRecentPlaybackRows(listOf(entry("a", 1)), songs, listOf(playlist), emptyList(), RecentPlaybackTab.Collection)
        assertEquals(1, rows.size)
        assertEquals(RecentPlaybackTab.Playlist, rows.single().kind)
        assertTrue(rows.single().subtitle.startsWith("150首"))
        assertTrue(buildRecentPlaybackRows(listOf(entry("a", 1)), songs, listOf(playlist), emptyList(), RecentPlaybackTab.Artist).isEmpty())
    }

    @Test fun repeatedSongsKeepLatestAndRemoveAllMatchingHistoryTogether() {
        val rows = buildRecentPlaybackRows(listOf(entry("a", 1), entry("b", 2)), songs, emptyList(), emptyList(), RecentPlaybackTab.Song)
        assertEquals(1, rows.size)
        assertEquals(2L, rows.single().playedAt)
        assertEquals(setOf("a", "b"), rows.single().entryIds.toSet())
        assertEquals("playlist:p", rows.single().song?.playbackSourceKey)
    }

    @Test fun videosDeduplicateByMediaAndNeverPointDeletionAtOriginalAudio() {
        val history = listOf(entry("a", 1, video = "file:///Music/mv.mp4"), entry("b", 2, video = "file:///Music/mv.mp4"))
        val rows = buildRecentPlaybackRows(history, songs, emptyList(), emptyList(), RecentPlaybackTab.Mv)
        assertEquals(1, rows.size)
        assertEquals("file:///Music/mv.mp4", rows.single().song?.path)
        assertNotEquals(1L, rows.single().song?.id)
        assertTrue(buildRecentPlaybackRows(history, songs, emptyList(), emptyList(), RecentPlaybackTab.Song).isEmpty())
    }

    @Test fun legacyHistoryDoesNotInventCategorySources() {
        assertTrue(buildRecentPlaybackRows(listOf(entry("a", 1, "")), songs, listOf(playlist), emptyList(), RecentPlaybackTab.Collection).isEmpty())
    }
}
