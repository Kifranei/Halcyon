package com.ella.music.ui.folder

import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.listmodel.LibraryListSorter
import com.ella.music.ui.listmodel.MusicSortKeyNormalizer
import com.ella.music.ui.listmodel.SongDisplaySpec
import com.ella.music.ui.listmodel.SongSortField
import com.ella.music.ui.listmodel.SortDirection
import com.ella.music.ui.listmodel.SortSpec
import com.ella.music.ui.listmodel.fastIndexSection
import com.ella.music.ui.listmodel.songDisplaySpecFor

internal enum class FolderSongSortMode(val labelRes: Int) {
    Title(R.string.playlist_song_sort_title),
    FileName(R.string.playlist_song_sort_file_name),
    Duration(R.string.playlist_song_sort_duration),
    DateAdded(R.string.playlist_song_sort_date_added),
    DateAddedAsc(R.string.playlist_song_sort_date_added_asc),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc),
    YearAsc(R.string.playlist_song_sort_year_asc),
    YearDesc(R.string.playlist_song_sort_year_desc),
    TitleDesc(R.string.playlist_song_sort_title),
    FileNameDesc(R.string.playlist_song_sort_file_name),
    DurationAsc(R.string.playlist_song_sort_duration)
}

internal fun FolderSongSortMode.isDescending(): Boolean = when (this) {
    FolderSongSortMode.TitleDesc,
    FolderSongSortMode.FileNameDesc,
    FolderSongSortMode.Duration,
    FolderSongSortMode.DateAdded,
    FolderSongSortMode.DateModified,
    FolderSongSortMode.YearDesc -> true
    else -> false
}

internal fun List<Song>.sortedForFolderDetail(mode: FolderSongSortMode): List<Song> =
    LibraryListSorter.sortSongs(this, mode.toSongSortSpec()).items

internal fun FolderSongSortMode.songDisplaySpec(): SongDisplaySpec =
    songDisplaySpecFor(toSongSortSpec())

internal fun Song.indexLetter(): String {
    return fastIndexSection()
}

internal fun String.musicSortKey(): String {
    return MusicSortKeyNormalizer.normalize(this)
}

private fun FolderSongSortMode.toSongSortSpec(): SortSpec<SongSortField> =
    SortSpec(
        field = when (this) {
            FolderSongSortMode.Title,
            FolderSongSortMode.TitleDesc -> SongSortField.Title
            FolderSongSortMode.FileName,
            FolderSongSortMode.FileNameDesc -> SongSortField.FileName
            FolderSongSortMode.Duration,
            FolderSongSortMode.DurationAsc -> SongSortField.Duration
            FolderSongSortMode.DateAdded,
            FolderSongSortMode.DateAddedAsc -> SongSortField.DateAdded
            FolderSongSortMode.DateModified,
            FolderSongSortMode.DateModifiedAsc -> SongSortField.DateModified
            FolderSongSortMode.YearAsc,
            FolderSongSortMode.YearDesc -> SongSortField.Year
        },
        direction = when (this) {
            FolderSongSortMode.TitleDesc,
            FolderSongSortMode.FileNameDesc,
            FolderSongSortMode.Duration,
            FolderSongSortMode.DateAdded,
            FolderSongSortMode.DateModified,
            FolderSongSortMode.YearDesc -> SortDirection.Descending
            else -> SortDirection.Ascending
        }
    )
