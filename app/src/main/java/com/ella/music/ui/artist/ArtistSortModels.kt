package com.ella.music.ui.artist

import androidx.annotation.StringRes
import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.ui.listmodel.SortDirection
import com.ella.music.ui.listmodel.sortedByReleaseDate
import java.util.Locale

internal enum class ArtistDetailSongSortMode(@param:StringRes val labelRes: Int) {
    Title(R.string.artist_sort_title),
    AlbumTrack(R.string.artist_sort_album_track),
    FileName(R.string.artist_sort_file_name),
    Duration(R.string.artist_sort_duration),
    DateAdded(R.string.artist_sort_date_added),
    DateAddedAsc(R.string.artist_sort_date_added_asc),
    DateModified(R.string.artist_sort_date_modified),
    DateModifiedAsc(R.string.artist_sort_date_modified_asc),
    YearAsc(R.string.artist_sort_year_asc),
    YearDesc(R.string.artist_sort_year_desc),
    TitleDesc(R.string.artist_sort_title),
    AlbumTrackDesc(R.string.artist_sort_album_track),
    FileNameDesc(R.string.artist_sort_file_name),
    DurationAsc(R.string.artist_sort_duration)
}

internal fun ArtistDetailSongSortMode.isDescending(): Boolean = when (this) {
    ArtistDetailSongSortMode.TitleDesc,
    ArtistDetailSongSortMode.AlbumTrackDesc,
    ArtistDetailSongSortMode.FileNameDesc,
    ArtistDetailSongSortMode.Duration,
    ArtistDetailSongSortMode.DateAdded,
    ArtistDetailSongSortMode.DateModified,
    ArtistDetailSongSortMode.YearDesc -> true
    else -> false
}

internal fun List<Song>.sortedForArtistDetail(mode: ArtistDetailSongSortMode): List<Song> {
    return when (mode) {
        ArtistDetailSongSortMode.Title -> sortedBy { it.title.lowercase(Locale.ROOT) }
        ArtistDetailSongSortMode.TitleDesc -> sortedByDescending { it.title.lowercase(Locale.ROOT) }
        ArtistDetailSongSortMode.AlbumTrack -> sortedWith(
            compareBy<Song> { it.album.lowercase(Locale.ROOT) }
                .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
        ArtistDetailSongSortMode.AlbumTrackDesc -> sortedWith(
            compareBy<Song> { it.album.lowercase(Locale.ROOT) }
                .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.ROOT) }
                .reversed()
        )
        ArtistDetailSongSortMode.FileName -> sortedBy { it.fileName.ifBlank { it.path.substringAfterLast('/') }.lowercase(Locale.ROOT) }
        ArtistDetailSongSortMode.FileNameDesc -> sortedByDescending { it.fileName.ifBlank { it.path.substringAfterLast('/') }.lowercase(Locale.ROOT) }
        ArtistDetailSongSortMode.Duration -> sortedByDescending { it.duration }
        ArtistDetailSongSortMode.DurationAsc -> sortedBy { it.duration }
        ArtistDetailSongSortMode.YearAsc -> sortedByReleaseDate(SortDirection.Ascending)
        ArtistDetailSongSortMode.YearDesc -> sortedByReleaseDate(SortDirection.Descending)
        ArtistDetailSongSortMode.DateAdded -> sortedByDescending { it.dateAdded }
        ArtistDetailSongSortMode.DateAddedAsc -> sortedBy { it.dateAdded }
        ArtistDetailSongSortMode.DateModified -> sortedByDescending { it.dateModified }
        ArtistDetailSongSortMode.DateModifiedAsc -> sortedBy { it.dateModified }
    }
}

internal enum class ArtistDetailAlbumSortMode(@param:StringRes val labelRes: Int) {
    YearAsc(R.string.artist_sort_year_asc),
    YearDesc(R.string.artist_sort_year_desc),
    SongCount(R.string.artist_sort_song_count),
    Duration(R.string.artist_sort_duration),
    Name(R.string.artist_sort_album_name),
    SongCountAsc(R.string.artist_sort_song_count),
    DurationAsc(R.string.artist_sort_duration),
    NameDesc(R.string.artist_sort_album_name)
}

internal fun ArtistDetailAlbumSortMode.isDescending(): Boolean = when (this) {
    ArtistDetailAlbumSortMode.YearDesc,
    ArtistDetailAlbumSortMode.SongCount,
    ArtistDetailAlbumSortMode.Duration,
    ArtistDetailAlbumSortMode.NameDesc -> true
    else -> false
}

internal fun List<Album>.sortedForArtistAlbumDetail(
    mode: ArtistDetailAlbumSortMode,
    durations: Map<Long, Long>
): List<Album> {
    return when (mode) {
        ArtistDetailAlbumSortMode.YearAsc -> sortedWith(compareBy<Album> { it.releaseDateSortKey <= 0 }.thenBy { it.releaseDateSortKey }.thenBy { it.name.lowercase(Locale.ROOT) })
        ArtistDetailAlbumSortMode.YearDesc -> sortedWith(compareBy<Album> { it.releaseDateSortKey <= 0 }.thenByDescending { it.releaseDateSortKey }.thenByDescending { it.name.lowercase(Locale.ROOT) })
        ArtistDetailAlbumSortMode.SongCount -> sortedByDescending { it.songCount }
        ArtistDetailAlbumSortMode.SongCountAsc -> sortedBy { it.songCount }
        ArtistDetailAlbumSortMode.Duration -> sortedByDescending { durations[it.id] ?: 0L }
        ArtistDetailAlbumSortMode.DurationAsc -> sortedBy { durations[it.id] ?: 0L }
        ArtistDetailAlbumSortMode.Name -> sortedBy { it.name.lowercase(Locale.ROOT) }
        ArtistDetailAlbumSortMode.NameDesc -> sortedByDescending { it.name.lowercase(Locale.ROOT) }
    }
}

