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
    customOrderIds: List<String> = emptyList(),
    pinnedIds: List<String> = emptyList()
): List<FolderPlaylist> {
    val customOrdered = applyFolderPlaylistCustomOrder(customOrderIds)
    val sorted = when (mode) {
        // Before manual ordering was persisted, the custom list was newest-first. Keep that
        // fallback for existing installations that have no saved order yet.
        FolderPlaylistSortMode.Custom -> customOrdered
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
        FolderPlaylistSortMode.CustomDesc -> customOrdered.asReversed()
    }
    if (pinnedIds.isEmpty()) return sorted
    val pinnedRank = pinnedIds.withIndex().associate { it.value to it.index }
    val pinnedSet = pinnedRank.keys
    val pinned = sorted
        .filter { it.id in pinnedSet }
        .sortedBy { pinnedRank[it.id] ?: Int.MAX_VALUE }
    return pinned + sorted.filterNot { it.id in pinnedSet }
}

internal fun List<FolderPlaylist>.applyFolderPlaylistCustomOrder(orderIds: List<String>): List<FolderPlaylist> {
    if (isEmpty()) return emptyList()
    val byId = associateBy(FolderPlaylist::id)
    val ordered = orderIds.mapNotNull(byId::get)
    val orderedIds = ordered.mapTo(mutableSetOf(), FolderPlaylist::id)
    val newItems = filterNot { it.id in orderedIds }
        .sortedWith(compareByDescending<FolderPlaylist> { it.createdAt }.thenBy { it.name.musicSortKey() })
    return ordered + newItems
}

internal fun FolderPlaylistSortMode.isDescending(): Boolean = when (this) {
    FolderPlaylistSortMode.CustomDesc,
    FolderPlaylistSortMode.DateUpdated,
    FolderPlaylistSortMode.DateCreatedDesc,
    FolderPlaylistSortMode.FolderCount,
    FolderPlaylistSortMode.SongCount,
    FolderPlaylistSortMode.Duration,
    FolderPlaylistSortMode.NameDesc -> true
    else -> false
}
