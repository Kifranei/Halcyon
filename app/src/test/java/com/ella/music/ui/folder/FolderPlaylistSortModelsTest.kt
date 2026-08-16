package com.ella.music.ui.folder

import com.ella.music.data.model.FolderPlaylist
import org.junit.Assert.assertEquals
import org.junit.Test

class FolderPlaylistSortModelsTest {
    @Test
    fun customAscendingDefaultsToNewestFirstAndDescendingReversesIt() {
        val playlists = listOf(
            playlist("old", createdAt = 1L),
            playlist("new", createdAt = 3L),
            playlist("middle", createdAt = 2L)
        )

        assertEquals(
            listOf("new", "middle", "old"),
            playlists.sortedForFolderPlaylists(FolderPlaylistSortMode.Custom, { 0 }, { 0L }).map { it.id }
        )
        assertEquals(
            listOf("old", "middle", "new"),
            playlists.sortedForFolderPlaylists(FolderPlaylistSortMode.CustomDesc, { 0 }, { 0L }).map { it.id }
        )
    }

    @Test
    fun customOrderCanMoveAnItemToTheFrontWithoutCreatingAPinnedSection() {
        val playlists = listOf(
            playlist("one", createdAt = 1L),
            playlist("two", createdAt = 2L),
            playlist("three", createdAt = 3L)
        )

        assertEquals(
            listOf("one", "three", "two"),
            playlists.sortedForFolderPlaylists(
                mode = FolderPlaylistSortMode.Custom,
                songCountProvider = { 0 },
                durationProvider = { 0L },
                customOrderIds = listOf("one")
            ).map { it.id }
        )
        assertEquals(
            listOf("three", "two", "one"),
            playlists.sortedForFolderPlaylists(
                mode = FolderPlaylistSortMode.DateCreatedDesc,
                songCountProvider = { 0 },
                durationProvider = { 0L },
                customOrderIds = listOf("one")
            ).map { it.id }
        )
    }

    private fun playlist(id: String, createdAt: Long) = FolderPlaylist(
        id = id,
        name = id,
        folders = listOf("/music/$id"),
        createdAt = createdAt,
        updatedAt = createdAt
    )
}
