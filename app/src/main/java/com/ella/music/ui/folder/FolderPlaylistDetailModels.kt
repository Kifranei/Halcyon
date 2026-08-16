package com.ella.music.ui.folder

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.listmodel.SortDirection
import com.ella.music.ui.listmodel.sortedByReleaseDate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class FolderPlaylistTab(@param:StringRes val labelRes: Int) {
    Songs(R.string.folder_playlist_tab_songs),
    Folders(R.string.folder_playlist_tab_folders)
}

internal enum class FolderPlaylistSongSortMode(@param:StringRes val labelRes: Int) {
    Custom(R.string.playlist_sort_custom),
    CustomDesc(R.string.playlist_sort_custom_desc),
    Title(R.string.playlist_sort_name),
    FileName(R.string.playlist_song_sort_file_name),
    Duration(R.string.playlist_song_sort_duration),
    YearAsc(R.string.playlist_song_sort_year_asc),
    YearDesc(R.string.playlist_song_sort_year_desc),
    DateAdded(R.string.playlist_song_sort_date_added),
    DateAddedAsc(R.string.playlist_song_sort_date_added_asc),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc),
    TitleDesc(R.string.playlist_sort_name),
    FileNameDesc(R.string.playlist_song_sort_file_name),
    DurationAsc(R.string.playlist_song_sort_duration)
}

internal enum class FolderPlaylistFolderSortMode(@param:StringRes val labelRes: Int) {
    Custom(R.string.playlist_sort_custom),
    CustomDesc(R.string.playlist_sort_custom_desc),
    Name(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count),
    AlbumCount(R.string.folder_sort_album_count),
    Duration(R.string.playlist_sort_duration),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc),
    NameDesc(R.string.playlist_sort_name),
    SongCountAsc(R.string.playlist_sort_song_count),
    AlbumCountAsc(R.string.folder_sort_album_count),
    DurationAsc(R.string.playlist_sort_duration)
}

internal fun FolderPlaylistSongSortMode.isDescending(): Boolean = when (this) {
    FolderPlaylistSongSortMode.CustomDesc,
    FolderPlaylistSongSortMode.TitleDesc,
    FolderPlaylistSongSortMode.FileNameDesc,
    FolderPlaylistSongSortMode.Duration,
    FolderPlaylistSongSortMode.YearDesc,
    FolderPlaylistSongSortMode.DateAdded,
    FolderPlaylistSongSortMode.DateModified -> true
    else -> false
}

internal fun FolderPlaylistFolderSortMode.isDescending(): Boolean = when (this) {
    FolderPlaylistFolderSortMode.CustomDesc,
    FolderPlaylistFolderSortMode.NameDesc,
    FolderPlaylistFolderSortMode.SongCount,
    FolderPlaylistFolderSortMode.AlbumCount,
    FolderPlaylistFolderSortMode.Duration,
    FolderPlaylistFolderSortMode.DateModified -> true
    else -> false
}

internal data class FolderPlaylistFolderEntry(
    val path: String,
    val displayName: String,
    val songCount: Int,
    val albumCount: Int,
    val duration: Long,
    val dateModified: Long,
    val coverModel: Any?
)

internal enum class EditorFolderSort(@param:StringRes val labelRes: Int) {
    ModifiedTime(R.string.playlist_song_sort_date_modified),
    Name(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count),
    ModifiedTimeAsc(R.string.playlist_song_sort_date_modified),
    NameDesc(R.string.playlist_sort_name),
    SongCountAsc(R.string.playlist_sort_song_count)
}

internal fun List<Song>.sortedForFolderPlaylistDetail(
    mode: FolderPlaylistSongSortMode,
    customOrderKeys: List<String> = emptyList()
): List<Song> = when (mode) {
    FolderPlaylistSongSortMode.Custom -> applyFolderPlaylistSongOrder(customOrderKeys)
        .ifEmpty { sortedWith(compareByDescending<Song> { it.dateModified }.thenBy { it.title.musicSortKey() }) }
    FolderPlaylistSongSortMode.CustomDesc -> applyFolderPlaylistSongOrder(customOrderKeys).asReversed()
        .ifEmpty { sortedWith(compareBy<Song> { it.dateModified }.thenBy { it.title.musicSortKey() }) }
    FolderPlaylistSongSortMode.Title -> sortedBy { it.title.musicSortKey() }
    FolderPlaylistSongSortMode.TitleDesc -> sortedByDescending { it.title.musicSortKey() }
    FolderPlaylistSongSortMode.FileName -> sortedBy { it.fileName.musicSortKey() }
    FolderPlaylistSongSortMode.FileNameDesc -> sortedByDescending { it.fileName.musicSortKey() }
    FolderPlaylistSongSortMode.Duration -> sortedByDescending { it.duration }
    FolderPlaylistSongSortMode.DurationAsc -> sortedBy { it.duration }
    FolderPlaylistSongSortMode.YearAsc ->
        sortedWith(compareBy<Song> { it.year.toIntOrNull() ?: Int.MAX_VALUE }.thenBy { it.title.musicSortKey() })
    FolderPlaylistSongSortMode.YearDesc -> sortedByReleaseDate(SortDirection.Descending)
    FolderPlaylistSongSortMode.DateAdded -> sortedByDescending { it.dateAdded }
    FolderPlaylistSongSortMode.DateAddedAsc -> sortedBy { it.dateAdded }
    FolderPlaylistSongSortMode.DateModified -> sortedByDescending { it.dateModified }
    FolderPlaylistSongSortMode.DateModifiedAsc -> sortedBy { it.dateModified }
}

internal fun List<FolderPlaylistFolderEntry>.sortedForFolderPlaylistDetail(
    mode: FolderPlaylistFolderSortMode,
    customOrderPaths: List<String> = emptyList()
): List<FolderPlaylistFolderEntry> = when (mode) {
    FolderPlaylistFolderSortMode.Custom -> applyFolderPlaylistFolderOrder(customOrderPaths)
        .ifEmpty { sortedBy { it.displayName.musicSortKey() } }
    FolderPlaylistFolderSortMode.Name -> sortedBy { it.displayName.musicSortKey() }
    FolderPlaylistFolderSortMode.CustomDesc -> applyFolderPlaylistFolderOrder(customOrderPaths).asReversed()
    FolderPlaylistFolderSortMode.NameDesc -> sortedByDescending { it.displayName.musicSortKey() }
    FolderPlaylistFolderSortMode.SongCount ->
        sortedWith(compareByDescending<FolderPlaylistFolderEntry> { it.songCount }.thenBy { it.displayName.musicSortKey() })
    FolderPlaylistFolderSortMode.SongCountAsc ->
        sortedWith(compareBy<FolderPlaylistFolderEntry> { it.songCount }.thenBy { it.displayName.musicSortKey() })
    FolderPlaylistFolderSortMode.AlbumCount ->
        sortedWith(compareByDescending<FolderPlaylistFolderEntry> { it.albumCount }.thenBy { it.displayName.musicSortKey() })
    FolderPlaylistFolderSortMode.AlbumCountAsc ->
        sortedWith(compareBy<FolderPlaylistFolderEntry> { it.albumCount }.thenBy { it.displayName.musicSortKey() })
    FolderPlaylistFolderSortMode.Duration ->
        sortedWith(compareByDescending<FolderPlaylistFolderEntry> { it.duration }.thenBy { it.displayName.musicSortKey() })
    FolderPlaylistFolderSortMode.DurationAsc ->
        sortedWith(compareBy<FolderPlaylistFolderEntry> { it.duration }.thenBy { it.displayName.musicSortKey() })
    FolderPlaylistFolderSortMode.DateModified ->
        sortedWith(compareByDescending<FolderPlaylistFolderEntry> { it.dateModified }.thenBy { it.displayName.musicSortKey() })
    FolderPlaylistFolderSortMode.DateModifiedAsc ->
        sortedWith(compareBy<FolderPlaylistFolderEntry> { it.dateModified }.thenBy { it.displayName.musicSortKey() })
}

internal fun List<Song>.applyFolderPlaylistSongOrder(orderKeys: List<String>): List<Song> {
    if (isEmpty()) return emptyList()
    val byKey = associateBy { it.playlistIdentityKey() }
    val ordered = orderKeys.mapNotNull(byKey::get)
    val orderedKeys = ordered.mapTo(mutableSetOf()) { it.playlistIdentityKey() }
    return ordered + filterNot { it.playlistIdentityKey() in orderedKeys }
        .sortedWith(compareByDescending<Song> { it.dateModified }.thenBy { it.title.musicSortKey() })
}

internal fun List<FolderPlaylistFolderEntry>.applyFolderPlaylistFolderOrder(
    orderPaths: List<String>
): List<FolderPlaylistFolderEntry> {
    if (isEmpty()) return emptyList()
    val byPath = associateBy { it.path.lowercase() }
    val ordered = orderPaths.mapNotNull { byPath[it.lowercase()] }
    val orderedPaths = ordered.mapTo(mutableSetOf()) { it.path.lowercase() }
    return ordered + filterNot { it.path.lowercase() in orderedPaths }
        .sortedBy { it.displayName.musicSortKey() }
}

internal fun FolderPlaylistFolderEntry.summaryForSort(
    mode: FolderPlaylistFolderSortMode,
    context: Context
): String = when (mode) {
    FolderPlaylistFolderSortMode.SongCount,
    FolderPlaylistFolderSortMode.SongCountAsc -> context.getString(R.string.song_count, songCount)
    FolderPlaylistFolderSortMode.AlbumCount,
    FolderPlaylistFolderSortMode.AlbumCountAsc -> context.getString(R.string.album_count, albumCount)
    FolderPlaylistFolderSortMode.Duration,
    FolderPlaylistFolderSortMode.DurationAsc -> duration.formatPlaybackDuration()
    FolderPlaylistFolderSortMode.DateModified,
    FolderPlaylistFolderSortMode.DateModifiedAsc -> dateModified.formatFolderPlaylistDateTime(context)
    FolderPlaylistFolderSortMode.Custom,
    FolderPlaylistFolderSortMode.CustomDesc,
    FolderPlaylistFolderSortMode.Name,
    FolderPlaylistFolderSortMode.NameDesc -> listOf(
        context.getString(R.string.song_count, songCount),
        context.getString(R.string.album_count, albumCount),
        duration.formatPlaybackDuration()
    ).joinToString(" · ")
}

internal fun FolderPlaylistFolderEntry.detailSummaryForSort(
    mode: FolderPlaylistFolderSortMode,
    context: Context
): String = when (mode) {
    FolderPlaylistFolderSortMode.Custom,
    FolderPlaylistFolderSortMode.CustomDesc,
    FolderPlaylistFolderSortMode.Name,
    FolderPlaylistFolderSortMode.NameDesc -> "${context.getString(R.string.song_count, songCount)} · $path"
    else -> "${summaryForSort(mode, context)} · $path"
}

internal fun List<Song>.availableFolderPlaylistFolders(): List<String> =
    map { it.folderPath() }
        .distinctBy { it.lowercase() }
        .sortedWith(compareBy<String> { it.substringAfterLast('/').musicSortKey() }.thenBy { it.musicSortKey() })

internal fun List<Song>.songsForFolderPlaylist(folders: List<String>): List<Song> {
    val normalizedFolders = folders.map { it.normalizeFolderPath() }.filter(String::isNotBlank)
    if (normalizedFolders.isEmpty()) return emptyList()
    return filter { song ->
        val songFolder = song.folderPath()
        normalizedFolders.any { folder ->
            songFolder.equals(folder, ignoreCase = true) ||
                songFolder.startsWith("${folder.trimEnd('/')}/", ignoreCase = true)
        }
    }.distinctBy { it.playlistIdentityKey() }
        .sortedWith(compareBy<Song> { it.folderPath().musicSortKey() }.thenBy { it.title.musicSortKey() })
}

internal fun Song?.folderPlaylistCoverModel(): Any? =
    this?.coverUrl?.takeIf(String::isNotBlank)
        ?: this?.albumId?.takeIf { it > 0L }?.let { Uri.parse("content://media/external/audio/albumart/$it") }

internal fun Long.formatFolderPlaylistDateTime(context: Context): String {
    if (this <= 0L) return context.getString(R.string.folder_unknown_modified_time)
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
