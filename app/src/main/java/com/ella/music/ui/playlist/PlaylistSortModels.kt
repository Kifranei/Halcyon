package com.ella.music.ui.playlist

import android.net.Uri
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.sanitizeExportFileName
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.listmodel.LibraryListSorter
import com.ella.music.ui.listmodel.SongDisplaySpec
import com.ella.music.ui.listmodel.SongSortField
import com.ella.music.ui.listmodel.SortDirection
import com.ella.music.ui.listmodel.SortSpec
import com.ella.music.ui.listmodel.songDisplaySpecFor
import java.util.Locale

internal enum class PlaylistSortMode(val labelRes: Int) {
    Custom(R.string.playlist_sort_custom),
    CustomDesc(R.string.playlist_sort_custom_desc),
    UpdatedAt(R.string.playlist_sort_updated_at),
    CreatedAt(R.string.playlist_sort_created_at_desc),
    CreatedAtAsc(R.string.playlist_sort_created_at),
    Name(R.string.playlist_sort_name),
    SongCount(R.string.playlist_sort_song_count),
    Duration(R.string.playlist_sort_duration),
    UpdatedAtAsc(R.string.playlist_sort_updated_at),
    NameDesc(R.string.playlist_sort_name),
    SongCountAsc(R.string.playlist_sort_song_count),
    DurationAsc(R.string.playlist_sort_duration)
}

internal fun PlaylistSortMode.isDescending(): Boolean = when (this) {
    PlaylistSortMode.CustomDesc,
    PlaylistSortMode.UpdatedAt,
    PlaylistSortMode.CreatedAt,
    PlaylistSortMode.NameDesc,
    PlaylistSortMode.SongCount,
    PlaylistSortMode.Duration -> true
    else -> false
}

internal fun List<UserPlaylist>.sortedForPlaylistList(mode: PlaylistSortMode): List<UserPlaylist> {
    return when (mode) {
        PlaylistSortMode.Custom -> this
        PlaylistSortMode.CustomDesc -> asReversed()
        PlaylistSortMode.UpdatedAt -> sortedWith(
            compareByDescending<UserPlaylist> { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        PlaylistSortMode.UpdatedAtAsc -> sortedWith(
            compareBy<UserPlaylist> { it.updatedAt }
                .thenBy { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        PlaylistSortMode.CreatedAt -> sortedWith(
            compareByDescending<UserPlaylist> { it.createdAt }
                .thenByDescending { it.updatedAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        PlaylistSortMode.CreatedAtAsc -> sortedWith(
            compareBy<UserPlaylist> { it.createdAt }
                .thenByDescending { it.updatedAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        PlaylistSortMode.Name -> sortedWith(
            compareBy<UserPlaylist> { it.name.lowercase(Locale.ROOT) }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )
        PlaylistSortMode.NameDesc -> sortedWith(
            compareByDescending<UserPlaylist> { it.name.lowercase(Locale.ROOT) }
                .thenByDescending { it.createdAt }
                .thenBy { it.id }
        )
        PlaylistSortMode.SongCount -> sortedWith(
            compareByDescending<UserPlaylist> { it.songs.size }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        PlaylistSortMode.SongCountAsc -> sortedWith(
            compareBy<UserPlaylist> { it.songs.size }
                .thenBy { it.updatedAt }
                .thenBy { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        PlaylistSortMode.Duration -> sortedWith(
            compareByDescending<UserPlaylist> { playlist -> playlist.songs.sumOf { it.duration } }
                .thenByDescending { it.updatedAt }
                .thenByDescending { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        PlaylistSortMode.DurationAsc -> sortedWith(
            compareBy<UserPlaylist> { playlist -> playlist.songs.sumOf { it.duration } }
                .thenBy { it.updatedAt }
                .thenBy { it.createdAt }
                .thenBy { it.name.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
    }
}

internal fun List<UserPlaylist>.applyPlaylistCustomOrder(orderedIds: List<String>): List<UserPlaylist> {
    if (isEmpty()) return emptyList()
    val fallbackComparator =
        compareByDescending<UserPlaylist> { it.createdAt }
            .thenByDescending { it.updatedAt }
            .thenBy { it.name.lowercase(Locale.ROOT) }
            .thenBy { it.id }
    if (orderedIds.isEmpty()) return sortedWith(fallbackComparator)

    val playlistsById = associateBy(UserPlaylist::id)
    return buildList {
        val orderedIdSet = orderedIds.toSet()
        addAll(filterNot { it.id in orderedIdSet }.sortedWith(fallbackComparator))
        orderedIds.forEach { id ->
            playlistsById[id]?.let { playlist ->
                add(playlist)
            }
        }
    }
}

internal fun UserPlaylist.matchesPlaylistSearch(query: String): Boolean {
    if (query.isBlank()) return true
    return name.contains(query, ignoreCase = true) ||
        songs.any { song ->
            song.title.contains(query, ignoreCase = true) ||
                song.artist.contains(query, ignoreCase = true) ||
                song.album.contains(query, ignoreCase = true)
        }
}

internal enum class PlaylistSongSortMode(val labelRes: Int) {
    Custom(R.string.playlist_song_sort_custom),
    CustomDesc(R.string.playlist_song_sort_custom_desc),
    AddedAt(R.string.playlist_song_sort_added_at),
    Title(R.string.playlist_song_sort_title),
    FileName(R.string.playlist_song_sort_file_name),
    Duration(R.string.playlist_song_sort_duration),
    YearAsc(R.string.playlist_song_sort_year_asc),
    YearDesc(R.string.playlist_song_sort_year_desc),
    DateAdded(R.string.playlist_song_sort_date_added),
    DateAddedAsc(R.string.playlist_song_sort_date_added_asc),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc),
    AddedAtDesc(R.string.playlist_song_sort_added_at),
    TitleDesc(R.string.playlist_song_sort_title),
    FileNameDesc(R.string.playlist_song_sort_file_name),
    DurationAsc(R.string.playlist_song_sort_duration)
}

internal fun PlaylistSongSortMode.isDescending(): Boolean = when (this) {
    PlaylistSongSortMode.CustomDesc,
    PlaylistSongSortMode.AddedAtDesc,
    PlaylistSongSortMode.TitleDesc,
    PlaylistSongSortMode.FileNameDesc,
    PlaylistSongSortMode.Duration,
    PlaylistSongSortMode.YearDesc,
    PlaylistSongSortMode.DateAdded,
    PlaylistSongSortMode.DateModified -> true
    else -> false
}

internal fun List<Song>.sortedForPlaylistDetail(mode: PlaylistSongSortMode): List<Song> {
    return when (mode) {
        PlaylistSongSortMode.Custom -> this
        PlaylistSongSortMode.CustomDesc -> asReversed()
        PlaylistSongSortMode.AddedAt -> this
        PlaylistSongSortMode.AddedAtDesc -> asReversed()
        else -> LibraryListSorter.sortSongs(this, mode.toSongSortSpec()).items
    }
}

internal fun PlaylistSongSortMode.songDisplaySpec(): SongDisplaySpec =
    songDisplaySpecFor(toSongSortSpec())

internal fun Long.formatPlaylistDuration(): String {
    return formatPlaybackDuration()
}

internal fun Song?.playlistCoverModel(): Any? {
    val song = this ?: return null
    return song.coverUrl.takeIf { it.isNotBlank() }
        ?: song.albumId.takeIf { it > 0L }?.let { Uri.parse("content://media/external/audio/albumart/$it") }
}

internal fun String.safePlaylistFileName(): String =
    sanitizeExportFileName(fallback = "Halcyon Playlist")

private fun PlaylistSongSortMode.toSongSortSpec(): SortSpec<SongSortField> =
    SortSpec(
        field = when (this) {
            PlaylistSongSortMode.Title,
            PlaylistSongSortMode.TitleDesc -> SongSortField.Title
            PlaylistSongSortMode.FileName,
            PlaylistSongSortMode.FileNameDesc -> SongSortField.FileName
            PlaylistSongSortMode.Duration,
            PlaylistSongSortMode.DurationAsc -> SongSortField.Duration
            PlaylistSongSortMode.YearAsc,
            PlaylistSongSortMode.YearDesc -> SongSortField.Year
            PlaylistSongSortMode.DateAdded,
            PlaylistSongSortMode.DateAddedAsc -> SongSortField.DateAdded
            PlaylistSongSortMode.DateModified,
            PlaylistSongSortMode.DateModifiedAsc -> SongSortField.DateModified
            PlaylistSongSortMode.Custom,
            PlaylistSongSortMode.CustomDesc,
            PlaylistSongSortMode.AddedAt,
            PlaylistSongSortMode.AddedAtDesc -> SongSortField.Custom
        },
        direction = when (this) {
            PlaylistSongSortMode.TitleDesc,
            PlaylistSongSortMode.FileNameDesc,
            PlaylistSongSortMode.Duration,
            PlaylistSongSortMode.YearDesc,
            PlaylistSongSortMode.DateAdded,
            PlaylistSongSortMode.DateModified,
            PlaylistSongSortMode.CustomDesc,
            PlaylistSongSortMode.AddedAtDesc -> SortDirection.Descending
            else -> SortDirection.Ascending
        }
    )
