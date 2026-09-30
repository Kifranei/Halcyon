package com.ella.music.ui.folder

import android.content.Context
import com.ella.music.R
import com.ella.music.data.model.formatPlaybackDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class FolderListSortMode(val labelRes: Int) {
    Name(R.string.playlist_sort_name),
    NameDesc(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count),
    AlbumCount(R.string.folder_sort_album_count),
    Duration(R.string.playlist_song_sort_duration),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc),
    SongCountAsc(R.string.playlist_sort_song_count),
    AlbumCountAsc(R.string.folder_sort_album_count),
    DurationAsc(R.string.playlist_song_sort_duration)
}

internal enum class FolderListSortField(val labelRes: Int) {
    Name(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count),
    AlbumCount(R.string.folder_sort_album_count),
    Duration(R.string.playlist_song_sort_duration),
    DateModified(R.string.playlist_song_sort_date_modified)
}

internal fun List<FolderTreeEntry>.sortedForFolderList(
    mode: FolderListSortMode,
    pinnedPaths: List<String> = emptyList()
): List<FolderTreeEntry> {
    val sorted = when (mode) {
        FolderListSortMode.Name -> sortedBy { it.name.musicSortKey() }
        FolderListSortMode.NameDesc -> sortedByDescending { it.name.musicSortKey() }
        FolderListSortMode.SongCount -> sortedWith(compareByDescending<FolderTreeEntry> { it.songCount }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.SongCountAsc -> sortedWith(compareBy<FolderTreeEntry> { it.songCount }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.Duration -> sortedWith(compareByDescending<FolderTreeEntry> { it.duration }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.DurationAsc -> sortedWith(compareBy<FolderTreeEntry> { it.duration }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.AlbumCount -> sortedWith(compareByDescending<FolderTreeEntry> { it.albumCount }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.AlbumCountAsc -> sortedWith(compareBy<FolderTreeEntry> { it.albumCount }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.DateModified -> sortedWith(compareByDescending<FolderTreeEntry> { it.dateModified }.thenBy { it.name.musicSortKey() })
        FolderListSortMode.DateModifiedAsc -> sortedWith(compareBy<FolderTreeEntry> { it.dateModified }.thenBy { it.name.musicSortKey() })
    }
    return sorted.withPinnedFolders(pinnedPaths)
}

internal fun List<FolderTreeEntry>.withPinnedFolders(pinnedPaths: List<String>): List<FolderTreeEntry> {
    val sorted = this
    if (pinnedPaths.isEmpty()) return sorted
    val pinnedRank = pinnedPaths
        .mapIndexed { index, path -> path.lowercase(Locale.ROOT) to index }
        .toMap()
    val pinned = sorted
        .filter { it.path.lowercase(Locale.ROOT) in pinnedRank }
        .sortedBy { pinnedRank[it.path.lowercase(Locale.ROOT)] ?: Int.MAX_VALUE }
    if (pinned.isEmpty()) return sorted
    val pinnedKeys = pinned.mapTo(hashSetOf()) { it.path.lowercase(Locale.ROOT) }
    return pinned + sorted.filterNot { it.path.lowercase(Locale.ROOT) in pinnedKeys }
}

internal fun FolderTreeEntry.summaryFor(context: android.content.Context, mode: FolderListSortMode): String {
    return when (mode) {
        FolderListSortMode.Duration,
        FolderListSortMode.DurationAsc -> duration.formatFolderDuration(context)
        FolderListSortMode.AlbumCount,
        FolderListSortMode.AlbumCountAsc -> context.getString(R.string.album_count, albumCount)
        FolderListSortMode.DateModified,
        FolderListSortMode.DateModifiedAsc -> dateModified.formatFolderDateTime(context)
        else -> context.getString(R.string.song_count, songCount)
    }
}

internal fun FolderListSortMode.sortField(): FolderListSortField = when (this) {
    FolderListSortMode.Name,
    FolderListSortMode.NameDesc -> FolderListSortField.Name
    FolderListSortMode.SongCount,
    FolderListSortMode.SongCountAsc -> FolderListSortField.SongCount
    FolderListSortMode.AlbumCount,
    FolderListSortMode.AlbumCountAsc -> FolderListSortField.AlbumCount
    FolderListSortMode.Duration,
    FolderListSortMode.DurationAsc -> FolderListSortField.Duration
    FolderListSortMode.DateModified,
    FolderListSortMode.DateModifiedAsc -> FolderListSortField.DateModified
}

internal fun FolderListSortMode.isDescending(): Boolean = when (this) {
    FolderListSortMode.NameDesc,
    FolderListSortMode.SongCount,
    FolderListSortMode.AlbumCount,
    FolderListSortMode.Duration,
    FolderListSortMode.DateModified -> true
    else -> false
}

internal fun FolderListSortField.toMode(directionDescending: Boolean): FolderListSortMode = when (this) {
    FolderListSortField.Name -> if (directionDescending) FolderListSortMode.NameDesc else FolderListSortMode.Name
    FolderListSortField.SongCount -> if (directionDescending) FolderListSortMode.SongCount else FolderListSortMode.SongCountAsc
    FolderListSortField.AlbumCount -> if (directionDescending) FolderListSortMode.AlbumCount else FolderListSortMode.AlbumCountAsc
    FolderListSortField.Duration -> if (directionDescending) FolderListSortMode.Duration else FolderListSortMode.DurationAsc
    FolderListSortField.DateModified -> if (directionDescending) FolderListSortMode.DateModified else FolderListSortMode.DateModifiedAsc
}

internal fun Long.formatFolderDuration(context: android.content.Context): String {
    return formatPlaybackDuration()
}

internal fun Long.formatFolderDateTime(context: android.content.Context): String {
    if (this <= 0L) return context.getString(R.string.folder_unknown_modified_time)
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
