package com.ella.music.ui.album

import androidx.compose.ui.graphics.Color
import com.ella.music.R
import com.ella.music.data.DOLBY_MARK
import com.ella.music.data.model.Song
import java.util.Locale

internal enum class AlbumDetailSongSortMode(val labelRes: Int) {
    Track(R.string.album_sort_track),
    Title(R.string.playlist_song_sort_title),
    FileName(R.string.playlist_song_sort_file_name),
    Duration(R.string.playlist_song_sort_duration),
    DateAdded(R.string.playlist_song_sort_date_added),
    DateAddedAsc(R.string.playlist_song_sort_date_added_asc),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc),
    TrackDesc(R.string.album_sort_track),
    TitleDesc(R.string.playlist_song_sort_title),
    FileNameDesc(R.string.playlist_song_sort_file_name),
    DurationAsc(R.string.playlist_song_sort_duration)
}

internal fun AlbumDetailSongSortMode.isDescending(): Boolean = when (this) {
    AlbumDetailSongSortMode.TrackDesc,
    AlbumDetailSongSortMode.TitleDesc,
    AlbumDetailSongSortMode.FileNameDesc,
    AlbumDetailSongSortMode.Duration,
    AlbumDetailSongSortMode.DateAdded,
    AlbumDetailSongSortMode.DateModified -> true
    else -> false
}

internal data class AlbumDiscGroup(
    val discNumber: Int,
    val songs: List<Song>
)

internal fun List<Song>.groupForDiscSections(): List<AlbumDiscGroup> =
    groupBy { it.safeDiscNumber() }
        .toSortedMap()
        .map { (discNumber, songs) -> AlbumDiscGroup(discNumber, songs) }

internal fun Song.safeDiscNumber(): Int =
    if (discNumber > 0) discNumber else 1

internal fun Song.displayTrackNumber(): String =
    trackNumber.takeIf { it > 0 }?.toString().orEmpty()

internal fun List<Song>.sortedForAlbumDetail(mode: AlbumDetailSongSortMode): List<Song> {
    return when (mode) {
        AlbumDetailSongSortMode.Track -> sortedWith(
            compareBy<Song> { it.discNumber <= 0 && it.trackNumber <= 0 }
                .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id }
        )
        AlbumDetailSongSortMode.TrackDesc -> sortedWith(
            compareBy<Song> { it.discNumber <= 0 && it.trackNumber <= 0 }
                .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.id }
                .reversed()
        )
        AlbumDetailSongSortMode.Title -> sortedBy { it.title.lowercase(Locale.ROOT) }
        AlbumDetailSongSortMode.TitleDesc -> sortedByDescending { it.title.lowercase(Locale.ROOT) }
        AlbumDetailSongSortMode.FileName -> sortedBy { it.fileName.ifBlank { it.path.substringAfterLast('/') }.lowercase(Locale.ROOT) }
        AlbumDetailSongSortMode.FileNameDesc -> sortedByDescending { it.fileName.ifBlank { it.path.substringAfterLast('/') }.lowercase(Locale.ROOT) }
        AlbumDetailSongSortMode.Duration -> sortedByDescending { it.duration }
        AlbumDetailSongSortMode.DurationAsc -> sortedBy { it.duration }
        AlbumDetailSongSortMode.DateAdded -> sortedByDescending { it.dateAdded }
        AlbumDetailSongSortMode.DateAddedAsc -> sortedBy { it.dateAdded }
        AlbumDetailSongSortMode.DateModified -> sortedByDescending { it.dateModified }
        AlbumDetailSongSortMode.DateModifiedAsc -> sortedBy { it.dateModified }
    }
}

internal fun albumDetailQualityColor(tag: String): Color {
    return when (tag) {
        "AC3", "EC3", "EAC3", "SUR", DOLBY_MARK -> Color(0xFF6EE7FF)
        "MQ" -> Color(0xFFFF8F3D)
        "HR" -> Color(0xFFFFC23A)
        "SQ" -> Color(0xFF9B59FF)
        "HQ" -> Color(0xFF3D83FF)
        "LQ" -> Color(0xFF34C56E)
        else -> Color(0xFF9E9E9E)
    }
}
