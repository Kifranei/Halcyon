package com.ella.music.ui.home

import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.listmodel.LibraryListSorter
import com.ella.music.ui.listmodel.SongDisplaySpec
import com.ella.music.ui.listmodel.SongSortField
import com.ella.music.ui.listmodel.SortDirection
import com.ella.music.ui.listmodel.SortSpec
import com.ella.music.ui.listmodel.fastIndexSection
import com.ella.music.ui.listmodel.songDisplaySpecFor

internal enum class HomeSortMode(val labelRes: Int) {
    Title(R.string.playlist_song_sort_title),
    FileName(R.string.playlist_song_sort_file_name),
    DateAdded(R.string.playlist_song_sort_date_added),
    DateAddedAsc(R.string.playlist_song_sort_date_added_asc),
    DateModified(R.string.playlist_song_sort_date_modified),
    DateModifiedAsc(R.string.playlist_song_sort_date_modified_asc),
    YearAsc(R.string.playlist_song_sort_year_asc),
    YearDesc(R.string.playlist_song_sort_year_desc),
    TitleDesc(R.string.playlist_song_sort_title),
    FileNameDesc(R.string.playlist_song_sort_file_name),
    Duration(R.string.playlist_song_sort_duration),
    DurationAsc(R.string.playlist_song_sort_duration),
    FileSize(R.string.playlist_song_sort_file_size),
    FileSizeAsc(R.string.playlist_song_sort_file_size)
}

internal enum class HomeSortField(val labelRes: Int) {
    Title(R.string.playlist_song_sort_title),
    FileName(R.string.playlist_song_sort_file_name),
    DateAdded(R.string.playlist_song_sort_date_added),
    DateModified(R.string.playlist_song_sort_date_modified),
    Year(R.string.playlist_song_sort_year),
    Duration(R.string.playlist_song_sort_duration),
    FileSize(R.string.playlist_song_sort_file_size)
}

internal fun HomeSortMode.sortField(): HomeSortField = when (this) {
    HomeSortMode.Title,
    HomeSortMode.TitleDesc -> HomeSortField.Title
    HomeSortMode.FileName,
    HomeSortMode.FileNameDesc -> HomeSortField.FileName
    HomeSortMode.DateAdded,
    HomeSortMode.DateAddedAsc -> HomeSortField.DateAdded
    HomeSortMode.DateModified,
    HomeSortMode.DateModifiedAsc -> HomeSortField.DateModified
    HomeSortMode.YearAsc,
    HomeSortMode.YearDesc -> HomeSortField.Year
    HomeSortMode.Duration,
    HomeSortMode.DurationAsc -> HomeSortField.Duration
    HomeSortMode.FileSize,
    HomeSortMode.FileSizeAsc -> HomeSortField.FileSize
}

internal fun HomeSortMode.isDescending(): Boolean = when (this) {
    HomeSortMode.TitleDesc,
    HomeSortMode.FileNameDesc,
    HomeSortMode.DateAdded,
    HomeSortMode.DateModified,
    HomeSortMode.YearDesc,
    HomeSortMode.Duration,
    HomeSortMode.FileSize -> true
    else -> false
}

internal fun HomeSortField.toMode(descending: Boolean = false): HomeSortMode = when (this) {
    HomeSortField.Title -> if (descending) HomeSortMode.TitleDesc else HomeSortMode.Title
    HomeSortField.FileName -> if (descending) HomeSortMode.FileNameDesc else HomeSortMode.FileName
    HomeSortField.DateAdded -> if (descending) HomeSortMode.DateAdded else HomeSortMode.DateAddedAsc
    HomeSortField.DateModified -> if (descending) HomeSortMode.DateModified else HomeSortMode.DateModifiedAsc
    HomeSortField.Year -> if (descending) HomeSortMode.YearDesc else HomeSortMode.YearAsc
    HomeSortField.Duration -> if (descending) HomeSortMode.Duration else HomeSortMode.DurationAsc
    HomeSortField.FileSize -> if (descending) HomeSortMode.FileSize else HomeSortMode.FileSizeAsc
}

internal fun HomeSortMode.songDisplaySpec(): SongDisplaySpec =
    songDisplaySpecFor(toSongSortSpec())

internal fun List<Song>.sortedForHomeMode(sortMode: HomeSortMode): HomeSortedSongs =
    LibraryListSorter.sortSongs(this, sortMode.toSongSortSpec()).toHomeSortedSongs()

internal fun List<Song>.cachedSortedForHomeMode(sortMode: HomeSortMode): HomeSortedSongs =
    HomeSortResultCache.getOrPut(this, sortMode) { sortedForHomeMode(sortMode) }

internal fun Song.indexLetter(sortKey: String? = null): String {
    return fastIndexSection(sortKey)
}

internal data class HomeSortedSongs(
    val songs: List<Song>,
    val sortKeysBySongId: Map<Long, String>
)

private object HomeSortResultCache {
    private const val MaxSize = 8
    private const val MaxCachedSongCount = 10_000
    private val lock = Any()
    private val values = object : LinkedHashMap<HomeSortCacheKey, HomeSortedSongs>(MaxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<HomeSortCacheKey, HomeSortedSongs>?): Boolean {
            return size > MaxSize
        }
    }

    fun getOrPut(songs: List<Song>, sortMode: HomeSortMode, builder: () -> HomeSortedSongs): HomeSortedSongs {
        if (songs.size > MaxCachedSongCount) return builder()
        val key = songs.cacheKey(sortMode)
        synchronized(lock) {
            values[key]?.let { return it }
        }
        val sorted = builder()
        synchronized(lock) {
            values[key] = sorted
        }
        return sorted
    }

    private fun List<Song>.cacheKey(sortMode: HomeSortMode): HomeSortCacheKey {
        var hash = 1125899906842597L
        forEach { song ->
            hash = hash.mix(song.hashCode().toLong())
                .mix(song.id)
                .mix(song.dateAdded)
                .mix(song.dateModified)
                .mix(song.fileSize)
                .mix(song.duration)
                .mix(song.title.hashCode().toLong())
                .mix(song.fileName.hashCode().toLong())
                .mix(song.album.hashCode().toLong())
                .mix(song.year.hashCode().toLong())
                .mix(song.discNumber.toLong())
                .mix(song.trackNumber.toLong())
        }
        return HomeSortCacheKey(sortMode, size, hash)
    }

    private fun Long.mix(value: Long): Long =
        (this xor value).let { mixed -> mixed * 1099511628211L }
}

private data class HomeSortCacheKey(
    val sortMode: HomeSortMode,
    val size: Int,
    val fingerprint: Long
)

private fun HomeSortMode.toSongSortSpec(): SortSpec<SongSortField> =
    SortSpec(
        field = when (this) {
            HomeSortMode.Title,
            HomeSortMode.TitleDesc -> SongSortField.Title
            HomeSortMode.FileName,
            HomeSortMode.FileNameDesc -> SongSortField.FileName
            HomeSortMode.YearAsc,
            HomeSortMode.YearDesc -> SongSortField.Year
            HomeSortMode.DateAdded,
            HomeSortMode.DateAddedAsc -> SongSortField.DateAdded
            HomeSortMode.DateModified,
            HomeSortMode.DateModifiedAsc -> SongSortField.DateModified
            HomeSortMode.Duration,
            HomeSortMode.DurationAsc -> SongSortField.Duration
            HomeSortMode.FileSize,
            HomeSortMode.FileSizeAsc -> SongSortField.FileSize
        },
        direction = if (isDescending()) SortDirection.Descending else SortDirection.Ascending
    )

private fun com.ella.music.ui.listmodel.SortedListResult<Song>.toHomeSortedSongs(): HomeSortedSongs =
    HomeSortedSongs(
        songs = items,
        sortKeysBySongId = fastIndexKeysById
    )
