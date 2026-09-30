package com.ella.music.ui.analytics

import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.toPlaylistSong
import org.junit.Assert.*
import org.junit.Test

class RecentPlaybackSourceTest {
    @Test fun flatAndNestedFolderHistoryRemainSeparateAndOpenTheCorrectScreen() {
        val library = listOf(
            Song(1, "direct", "a", "al", 1, 1000, "/Music/华语/1.flac", "1.flac"),
            Song(2, "child", "a", "al", 1, 1000, "/Music/华语/子目录/2.flac", "2.flac")
        )
        val history = listOf(entry("flat", 1, "category:folder:/Music/华语"), entry("nested", 2, "folder:/Music/华语"))
        val rows = buildRecentPlaybackRowsForResolved(resolveRecentPlaybackEntries(history, library), library, emptyList(), emptyList(), RecentPlaybackTab.Folder)
        assertEquals(2, rows.size)
        assertEquals(2, rows.map { it.key }.distinct().size)
        val flat = rows.single { !it.nestedFolder }
        val nested = rows.single { it.nestedFolder }
        assertEquals(1, flat.rowSongs.size)
        assertEquals(2, nested.rowSongs.size)
        assertEquals("文件夹：华语", flat.title)
        assertEquals("嵌套文件夹：华语", nested.title)
        assertEquals(com.ella.music.ui.navigation.Screen.MetadataCategoryDetail.createRoute("folder", "/Music/华语"), recentPlaybackRowRoute(flat))
        assertEquals(com.ella.music.ui.navigation.Screen.FolderDetail.createRoute("/Music/华语"), recentPlaybackRowRoute(nested))
        assertFalse(recentPlaybackTypeVisible(flat, RecentPlaybackTab.Folder, setOf("nested_folder"), emptySet()))
        assertTrue(recentPlaybackTypeVisible(nested, RecentPlaybackTab.Folder, setOf("nested_folder"), emptySet()))
    }

    @Test fun localAndOnlineVideosHaveDistinctLabelsAndCanBeFilteredSeparately() {
        val history = listOf(entry("local", 1, video = "file:///Music/mv.mp4"), entry("online", 2, video = "halcyon-netease-mv://mv/123"))
        val rows = buildRecentPlaybackRowsForResolved(resolveRecentPlaybackEntries(history, songs), songs, emptyList(), emptyList(), RecentPlaybackTab.Mv)
        assertEquals(setOf("本地：Song 1", "在线：Song 1"), rows.map { it.title }.toSet())
        assertEquals(1, rows.count { recentPlaybackTypeVisible(it, RecentPlaybackTab.Mv, emptySet(), setOf("local")) })
        assertEquals(1, rows.count { recentPlaybackTypeVisible(it, RecentPlaybackTab.Mv, emptySet(), setOf("online")) })
        assertTrue(rows.none { recentPlaybackTypeVisible(it, RecentPlaybackTab.Mv, emptySet(), emptySet()) })
        assertEquals("online", recentVideoType("https://cdn.example.com/mv.mp4"))
        assertEquals("local", recentVideoType("content://media/external/video/media/1"))
    }

    @Test fun emptyTypeSelectionIsPersistedWithoutRestoringBothTypes() {
        val defaults = setOf("local", "online")
        assertEquals(defaults, com.ella.music.data.parseRecentPlaybackTypes(null, defaults))
        assertEquals(emptySet<String>(), com.ella.music.data.parseRecentPlaybackTypes("", defaults))
        assertEquals(setOf("online"), com.ella.music.data.parseRecentPlaybackTypes("online,unknown", defaults))
    }

    private val songs = (1L..150L).map {
        Song(it, "Song $it", "Artist", "Album", 1L, 1000L, "/Music/$it.flac", "$it.flac")
    }
    private val playlist = UserPlaylist("p", "华语", songs.map { it.toPlaylistSong(0) }, 0, 0)
    private fun entry(id: String, time: Long, source: String = "playlist:p", video: String = "") =
        PlaybackHistoryEntry(id, 1L, "Song 1", "Artist", "Album", time, categorySourceKey = source, mediaUri = video)

    @Test fun playlistPlaybackOnlyAddsItsActualCategoryAndUsesFullCount() {
        val rows = buildRecentPlaybackRowsForResolved(resolveRecentPlaybackEntries(listOf(entry("a", 1)), songs), songs, listOf(playlist), emptyList(), RecentPlaybackTab.Collection)
        assertEquals(1, rows.size)
        assertEquals(RecentPlaybackTab.Playlist, rows.single().kind)
        assertTrue(rows.single().subtitle.startsWith("150首"))
        assertTrue(buildRecentPlaybackRowsForResolved(resolveRecentPlaybackEntries(listOf(entry("a", 1)), songs), songs, listOf(playlist), emptyList(), RecentPlaybackTab.Artist).isEmpty())
    }

    @Test fun repeatedSongsKeepLatestAndRemoveAllMatchingHistoryTogether() {
        val rows = buildRecentPlaybackRowsForResolved(resolveRecentPlaybackEntries(listOf(entry("a", 1), entry("b", 2)), songs), songs, emptyList(), emptyList(), RecentPlaybackTab.Song)
        assertEquals(1, rows.size)
        assertEquals(2L, rows.single().playedAt)
        assertEquals(setOf("a", "b"), rows.single().entryIds.toSet())
        assertEquals("playlist:p", rows.single().song?.playbackSourceKey)
    }

    @Test fun videosDeduplicateByMediaAndNeverPointDeletionAtOriginalAudio() {
        val history = listOf(entry("a", 1, video = "file:///Music/mv.mp4"), entry("b", 2, video = "file:///Music/mv.mp4"))
        val rows = buildRecentPlaybackRowsForResolved(resolveRecentPlaybackEntries(history, songs), songs, emptyList(), emptyList(), RecentPlaybackTab.Mv)
        assertEquals(1, rows.size)
        assertEquals("file:///Music/mv.mp4", rows.single().song?.path)
        assertNotEquals(1L, rows.single().song?.id)
        assertTrue(buildRecentPlaybackRowsForResolved(resolveRecentPlaybackEntries(history, songs), songs, emptyList(), emptyList(), RecentPlaybackTab.Song).isEmpty())
    }

    @Test fun legacyHistoryDoesNotInventCategorySources() {
        assertTrue(buildRecentPlaybackRowsForResolved(resolveRecentPlaybackEntries(listOf(entry("a", 1, "")), songs), songs, listOf(playlist), emptyList(), RecentPlaybackTab.Collection).isEmpty())
    }

}
