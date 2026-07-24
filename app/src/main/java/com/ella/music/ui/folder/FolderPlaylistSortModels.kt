package com.ella.music.ui.folder

import com.ella.music.R
import com.ella.music.data.model.FolderPlaylist

internal enum class FolderPlaylistSortMode(val labelRes: Int) {
    Custom(R.string.playlist_sort_custom),
    DateUpdated(R.string.playlist_sort_updated_at),
    DateCreatedDesc(R.string.playlist_sort_created_at_desc),
    DateCreated(R.string.playlist_sort_created_at),
    Name(R.string.playlist_sort_name),
    FolderCount(R.string.folder_playlist_sort_folder_count),
    SongCount(R.string.playlist_sort_song_count),
    Duration(R.string.playlist_sort_duration),
    CustomDesc(R.string.playlist_sort_custom_desc),
    DateUpdatedAsc(R.string.playlist_sort_updated_at),
    NameDesc(R.string.playlist_sort_name),
    FolderCountAsc(R.string.folder_playlist_sort_folder_count),
    SongCountAsc(R.string.playlist_sort_song_count),
    DurationAsc(R.string.playlist_sort_duration)
}

internal fun List<FolderPlaylist>.sortedForFolderPlaylists(
    mode: FolderPlaylistSortMode,
    songCountProvider: (FolderPlaylist) -> Int,
    durationProvider: (FolderPlaylist) -> Long,
    pinnedIds: List<String> = emptyList()
): List<FolderPlaylist> {
    val sorted = when (mode) {
        // "Custom" defaults to creation-time descending (newest first) per issue #237③. Until the
        // user manually reorders, this is the natural default. CustomDesc is the reverse of that.
        FolderPlaylistSortMode.Custom -> sortedWith(compareByDescending<FolderPlaylist> { it.createdAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.DateUpdated -> sortedWith(compareByDescending<FolderPlaylist> { it.updatedAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.DateUpdatedAsc -> sortedWith(compareBy<FolderPlaylist> { it.updatedAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.DateCreatedDesc -> sortedWith(compareByDescending<FolderPlaylist> { it.createdAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.DateCreated -> sortedWith(compareBy<FolderPlaylist> { it.createdAt }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.Name -> sortedBy { it.name.musicSortKey() }
        FolderPlaylistSortMode.NameDesc -> sortedByDescending { it.name.musicSortKey() }
        FolderPlaylistSortMode.FolderCount -> sortedWith(compareByDescending<FolderPlaylist> { it.folders.size }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.FolderCountAsc -> sortedWith(compareBy<FolderPlaylist> { it.folders.size }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.SongCount -> sortedWith(compareByDescending<FolderPlaylist> { songCountProvider(it) }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.SongCountAsc -> sortedWith(compareBy<FolderPlaylist> { songCountProvider(it) }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.Duration -> sortedWith(compareByDescending<FolderPlaylist> { durationProvider(it) }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.DurationAsc -> sortedWith(compareBy<FolderPlaylist> { durationProvider(it) }.thenBy { it.name.musicSortKey() })
        FolderPlaylistSortMode.CustomDesc -> sortedWith(compareBy<FolderPlaylist> { it.createdAt }.thenBy { it.name.musicSortKey() })
    }
    if (pinnedIds.isEmpty()) return sorted
    val pinnedRank = pinnedIds.withIndex().associate { it.value to it.index }
    val pinnedSet = pinnedRank.keys
    val pinned = sorted
        .filter { it.id in pinnedSet }
        .sortedBy { pinnedRank[it.id] ?: Int.MAX_VALUE }
    return pinned + sorted.filterNot { it.id in pinnedSet }
}

internal fun FolderPlaylistSortMode.isDescending(): Boolean = when (this) {
    FolderPlaylistSortMode.Custom,
    FolderPlaylistSortMode.DateUpdated,
    FolderPlaylistSortMode.DateCreatedDesc,
    FolderPlaylistSortMode.FolderCount,
    FolderPlaylistSortMode.SongCount,
    FolderPlaylistSortMode.Duration,
    FolderPlaylistSortMode.NameDesc -> true
    else -> false
}
