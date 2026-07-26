package com.ella.music.data.repository

import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.searchableTagValues

/**
 * Library search-snapshot matching/filtering plus the snapshot-text builder used by
 * [MusicSnapshotManager]. Logic moved verbatim from [MusicRepository]; the repository delegates
 * here and supplies [cachedTagInfo] (its in-memory tag-info cache lookup).
 */
internal class MusicLibrarySearchCoordinator(
    private val snapshotManager: MusicSnapshotManager,
    private val cachedTagInfo: (Song) -> SongTagInfo?
) {
    suspend fun songMatchesSearchSnapshot(
        song: Song,
        query: String,
        includeFullTags: Boolean = true
    ): Boolean =
        if (includeFullTags) {
            snapshotManager.songMatchesSearchSnapshot(song, query)
        } else {
            buildSongSearchSnapshotText(song, includeCachedTagInfo = false).contains(query, ignoreCase = true)
        }

    suspend fun filterSongsBySearchSnapshot(
        songs: List<Song>,
        query: String,
        includeFullTags: Boolean = true
    ): List<Song> =
        if (includeFullTags) {
            snapshotManager.filterSongsBySearchSnapshot(songs, query)
        } else {
            val target = query.trim()
            if (target.isBlank()) songs else songs.filter { songMatchesSearchSnapshot(it, target, includeFullTags = false) }
        }

    fun buildSongSearchSnapshotText(
        song: Song,
        includeCachedTagInfo: Boolean
    ): String {
        val tagInfo = if (includeCachedTagInfo) {
            cachedTagInfo(song) ?: SongTagInfo()
        } else {
            SongTagInfo()
        }
        return song.searchableTagValues(tagInfo)
            .joinToString(separator = "\n")
            .lowercase()
    }
}
