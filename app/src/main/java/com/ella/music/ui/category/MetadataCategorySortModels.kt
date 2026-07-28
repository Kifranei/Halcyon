package com.ella.music.ui.category

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.ui.folder.musicSortKey
import com.ella.music.ui.listmodel.SortDirection
import com.ella.music.ui.listmodel.sortedByReleaseDate
import com.ella.music.viewmodel.MetadataCategoryItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class MetadataCategorySortMode {
    Name,
    NameDesc,
    SongCount,
    AlbumCount,
    Duration,
    DateModified,
    DateModifiedAsc,
    SongCountAsc,
    AlbumCountAsc,
    DurationAsc
}

internal enum class MetadataCategorySortField {
    Name,
    SongCount,
    AlbumCount,
    Duration,
    DateModified
}

@Composable
internal fun MetadataCategorySortField.displayLabel(type: String): String {
    val context = LocalContext.current
    return when (this) {
        MetadataCategorySortField.Name -> if (type == "year") {
            context.getString(R.string.category_year)
        } else {
            context.getString(R.string.category_sort_name)
        }
        MetadataCategorySortField.SongCount -> context.getString(R.string.playlist_sort_song_count)
        MetadataCategorySortField.AlbumCount -> if (type in PERSON_METADATA_CATEGORY_TYPES) {
            context.getString(R.string.category_sort_participating_albums)
        } else {
            context.getString(R.string.category_sort_album_count)
        }
        MetadataCategorySortField.Duration -> context.getString(R.string.playlist_sort_duration)
        MetadataCategorySortField.DateModified -> context.getString(R.string.playlist_song_sort_date_modified)
    }
}

internal fun MetadataCategorySortMode.availableFor(type: String): Boolean {
    return when (this) {
        MetadataCategorySortMode.DateModified,
        MetadataCategorySortMode.DateModifiedAsc -> type == "folder"
        else -> true
    }
}

@Composable
internal fun MetadataCategorySortMode.displayLabel(type: String): String =
    com.ella.music.ui.components.sortLabel(
        field = sortField().displayLabel(type),
        descending = isDescending()
    )

internal fun List<MetadataCategoryItem>.sortedForCategory(
    type: String,
    mode: MetadataCategorySortMode
): List<MetadataCategoryItem> {
    fun MetadataCategoryItem.nameSortKey(): String =
        (if (type == "folder") name.substringAfterLast('/').ifBlank { name } else name).musicSortKey()
    return when (mode) {
        MetadataCategorySortMode.Name -> sortedBy { it.nameSortKey() }
        MetadataCategorySortMode.NameDesc -> if (type == "year") {
            sortedByDescending { it.name.toIntOrNull() ?: Int.MIN_VALUE }
        } else {
            sortedByDescending { it.nameSortKey() }
        }
        MetadataCategorySortMode.SongCount -> sortedByDescending { it.songCount }
        MetadataCategorySortMode.SongCountAsc -> sortedBy { it.songCount }
        MetadataCategorySortMode.AlbumCount -> sortedByDescending { it.albumCount }
        MetadataCategorySortMode.AlbumCountAsc -> sortedBy { it.albumCount }
        MetadataCategorySortMode.Duration -> sortedByDescending { it.duration }
        MetadataCategorySortMode.DurationAsc -> sortedBy { it.duration }
        MetadataCategorySortMode.DateModified -> sortedByDescending { it.dateModified }
        MetadataCategorySortMode.DateModifiedAsc -> sortedBy { it.dateModified }
    }
}

internal fun MetadataCategorySortMode.sortField(): MetadataCategorySortField = when (this) {
    MetadataCategorySortMode.Name,
    MetadataCategorySortMode.NameDesc -> MetadataCategorySortField.Name
    MetadataCategorySortMode.SongCount,
    MetadataCategorySortMode.SongCountAsc -> MetadataCategorySortField.SongCount
    MetadataCategorySortMode.AlbumCount,
    MetadataCategorySortMode.AlbumCountAsc -> MetadataCategorySortField.AlbumCount
    MetadataCategorySortMode.Duration,
    MetadataCategorySortMode.DurationAsc -> MetadataCategorySortField.Duration
    MetadataCategorySortMode.DateModified,
    MetadataCategorySortMode.DateModifiedAsc -> MetadataCategorySortField.DateModified
}

internal fun MetadataCategorySortMode.isDescending(): Boolean = when (this) {
    MetadataCategorySortMode.NameDesc,
    MetadataCategorySortMode.SongCount,
    MetadataCategorySortMode.AlbumCount,
    MetadataCategorySortMode.Duration,
    MetadataCategorySortMode.DateModified -> true
    else -> false
}

internal fun MetadataCategorySortField.toMode(descending: Boolean): MetadataCategorySortMode = when (this) {
    MetadataCategorySortField.Name -> if (descending) MetadataCategorySortMode.NameDesc else MetadataCategorySortMode.Name
    MetadataCategorySortField.SongCount -> if (descending) MetadataCategorySortMode.SongCount else MetadataCategorySortMode.SongCountAsc
    MetadataCategorySortField.AlbumCount -> if (descending) MetadataCategorySortMode.AlbumCount else MetadataCategorySortMode.AlbumCountAsc
    MetadataCategorySortField.Duration -> if (descending) MetadataCategorySortMode.Duration else MetadataCategorySortMode.DurationAsc
    MetadataCategorySortField.DateModified -> if (descending) MetadataCategorySortMode.DateModified else MetadataCategorySortMode.DateModifiedAsc
}

internal fun MetadataCategoryItem.matchesCategorySearch(query: String, type: String): Boolean {
    return name.contains(query, ignoreCase = true) ||
        (type == "folder" && name.substringAfterLast('/').contains(query, ignoreCase = true))
}

@Composable
internal fun MetadataCategoryItem.categorySortSummary(sortMode: MetadataCategorySortMode): String {
    val context = LocalContext.current
    return when (sortMode) {
        MetadataCategorySortMode.AlbumCount,
        MetadataCategorySortMode.AlbumCountAsc -> context.getString(R.string.category_album_count_detail, albumCount)
        MetadataCategorySortMode.Duration,
        MetadataCategorySortMode.DurationAsc -> duration.formatDuration()
        else -> context.getString(R.string.category_song_count_card, songCount)
    }
}

@Composable
internal fun MetadataCategoryItem.folderSortSummary(sortMode: MetadataCategorySortMode): String {
    val context = LocalContext.current
    return when (sortMode) {
        MetadataCategorySortMode.AlbumCount,
        MetadataCategorySortMode.AlbumCountAsc -> context.getString(R.string.category_album_count_detail, albumCount)
        MetadataCategorySortMode.Duration,
        MetadataCategorySortMode.DurationAsc -> duration.formatDuration()
        MetadataCategorySortMode.DateModified,
        MetadataCategorySortMode.DateModifiedAsc -> dateModified.formatDateTimeText(context)
        else -> context.getString(R.string.analytics_song_count_value, songCount)
    }
}

@Composable
internal fun MetadataCategoryItem.personSortSummary(sortMode: MetadataCategorySortMode): String {
    val context = LocalContext.current
    return when (sortMode) {
        MetadataCategorySortMode.Duration,
        MetadataCategorySortMode.DurationAsc -> context.getString(R.string.category_person_sort_duration, duration.formatDuration(), albumCount)
        else -> context.getString(R.string.category_person_sort, songCount, albumCount)
    }
}

internal enum class MetadataDetailSongSortMode {
    AlbumTrack,
    Title,
    FileName,
    Duration,
    YearAsc,
    YearDesc,
    DateAdded,
    DateAddedAsc,
    DateModified,
    DateModifiedAsc,
    AlbumTrackDesc,
    TitleDesc,
    FileNameDesc,
    DurationAsc
}

internal fun MetadataDetailSongSortMode.isDescending(): Boolean = when (this) {
    MetadataDetailSongSortMode.AlbumTrackDesc,
    MetadataDetailSongSortMode.TitleDesc,
    MetadataDetailSongSortMode.FileNameDesc,
    MetadataDetailSongSortMode.Duration,
    MetadataDetailSongSortMode.YearDesc,
    MetadataDetailSongSortMode.DateAdded,
    MetadataDetailSongSortMode.DateModified -> true
    else -> false
}

@Composable
internal fun MetadataDetailSongSortMode.label(): String {
    val context = LocalContext.current
    return context.getString(when (this) {
        MetadataDetailSongSortMode.AlbumTrack,
        MetadataDetailSongSortMode.AlbumTrackDesc -> R.string.category_sort_album_track
        MetadataDetailSongSortMode.Title,
        MetadataDetailSongSortMode.TitleDesc -> R.string.playlist_song_sort_title
        MetadataDetailSongSortMode.FileName,
        MetadataDetailSongSortMode.FileNameDesc -> R.string.playlist_song_sort_file_name
        MetadataDetailSongSortMode.Duration,
        MetadataDetailSongSortMode.DurationAsc -> R.string.playlist_sort_duration
        MetadataDetailSongSortMode.YearAsc -> R.string.playlist_song_sort_year_asc
        MetadataDetailSongSortMode.YearDesc -> R.string.playlist_song_sort_year_desc
        MetadataDetailSongSortMode.DateAdded -> R.string.playlist_song_sort_date_added
        MetadataDetailSongSortMode.DateAddedAsc -> R.string.category_sort_date_added_asc
        MetadataDetailSongSortMode.DateModified -> R.string.playlist_song_sort_date_modified
        MetadataDetailSongSortMode.DateModifiedAsc -> R.string.category_sort_date_modified_asc
    })
}

internal fun List<com.ella.music.data.model.Song>.sortedForMetadataDetail(
    mode: MetadataDetailSongSortMode
): List<com.ella.music.data.model.Song> {
    return when (mode) {
        MetadataDetailSongSortMode.AlbumTrack -> sortedWith(
            compareBy<com.ella.music.data.model.Song> { it.album.lowercase(Locale.ROOT) }
                .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
        MetadataDetailSongSortMode.AlbumTrackDesc -> sortedWith(
            compareByDescending<com.ella.music.data.model.Song> { it.album.lowercase(Locale.ROOT) }
                .thenByDescending { if (it.discNumber > 0) it.discNumber else Int.MIN_VALUE }
                .thenByDescending { if (it.trackNumber > 0) it.trackNumber else Int.MIN_VALUE }
                .thenByDescending { it.title.lowercase(Locale.ROOT) }
        )
        MetadataDetailSongSortMode.Title -> sortedBy { it.title.musicSortKey() }
        MetadataDetailSongSortMode.TitleDesc -> sortedByDescending { it.title.musicSortKey() }
        MetadataDetailSongSortMode.FileName -> sortedBy { song ->
            song.fileName.ifBlank { song.path.substringAfterLast('/') }.musicSortKey()
        }
        MetadataDetailSongSortMode.FileNameDesc -> sortedByDescending { song ->
            song.fileName.ifBlank { song.path.substringAfterLast('/') }.musicSortKey()
        }
        MetadataDetailSongSortMode.Duration -> sortedByDescending { it.duration }
        MetadataDetailSongSortMode.DurationAsc -> sortedBy { it.duration }
        MetadataDetailSongSortMode.YearAsc -> sortedByReleaseDate(SortDirection.Ascending)
        MetadataDetailSongSortMode.YearDesc -> sortedByReleaseDate(SortDirection.Descending)
        MetadataDetailSongSortMode.DateAdded -> sortedByDescending { it.dateAdded }
        MetadataDetailSongSortMode.DateAddedAsc -> sortedBy { it.dateAdded }
        MetadataDetailSongSortMode.DateModified -> sortedByDescending { it.dateModified }
        MetadataDetailSongSortMode.DateModifiedAsc -> sortedBy { it.dateModified }
    }
}

internal fun MetadataCategoryItem.categoryIndexLetter(type: String): String {
    val displayName = if (type == "folder") name.substringAfterLast('/').ifBlank { name } else name
    return displayName.musicSortKey().toFastIndexSection()
}

internal fun Song.metadataDetailIndexLetter(mode: MetadataDetailSongSortMode): String {
    val sortText = when (mode) {
        MetadataDetailSongSortMode.FileName,
        MetadataDetailSongSortMode.FileNameDesc -> fileName.ifBlank { path.substringAfterLast('/') }
        else -> title
    }
    return sortText.musicSortKey().toFastIndexSection()
}

internal enum class MetadataDetailTab {
    Songs,
    Albums
}

@Composable
internal fun MetadataDetailTab.label(): String {
    val context = LocalContext.current
    return context.getString(when (this) {
        MetadataDetailTab.Songs -> R.string.album_stat_songs
        MetadataDetailTab.Albums -> R.string.category_album
    })
}

internal enum class MetadataDetailAlbumSortMode {
    YearAsc,
    YearDesc,
    SongCount,
    Duration,
    Name,
    SongCountAsc,
    DurationAsc,
    NameDesc
}

@Composable
internal fun MetadataDetailSongSortMode.displayLabel(): String =
    com.ella.music.ui.components.sortLabel(label(), isDescending())

internal fun MetadataDetailAlbumSortMode.isDescending(): Boolean = when (this) {
    MetadataDetailAlbumSortMode.YearDesc,
    MetadataDetailAlbumSortMode.SongCount,
    MetadataDetailAlbumSortMode.Duration,
    MetadataDetailAlbumSortMode.NameDesc -> true
    else -> false
}

@Composable
internal fun MetadataDetailAlbumSortMode.label(): String {
    val context = LocalContext.current
    return context.getString(when (this) {
        MetadataDetailAlbumSortMode.YearAsc -> R.string.playlist_song_sort_year_asc
        MetadataDetailAlbumSortMode.YearDesc -> R.string.playlist_song_sort_year_desc
        MetadataDetailAlbumSortMode.SongCount,
        MetadataDetailAlbumSortMode.SongCountAsc -> R.string.playlist_sort_song_count
        MetadataDetailAlbumSortMode.Duration,
        MetadataDetailAlbumSortMode.DurationAsc -> R.string.playlist_sort_duration
        MetadataDetailAlbumSortMode.Name,
        MetadataDetailAlbumSortMode.NameDesc -> R.string.category_sort_album_name
    })
}

@Composable
internal fun MetadataDetailAlbumSortMode.displayLabel(): String =
    com.ella.music.ui.components.sortLabel(label(), isDescending())

internal fun List<Album>.sortedForMetadataAlbumDetail(
    mode: MetadataDetailAlbumSortMode,
    durations: Map<Long, Long>
): List<Album> {
    return when (mode) {
        MetadataDetailAlbumSortMode.YearAsc -> sortedWith(compareBy<Album> { it.releaseDateSortKey <= 0 }.thenBy { it.releaseDateSortKey }.thenBy { it.name.lowercase(Locale.ROOT) })
        MetadataDetailAlbumSortMode.YearDesc -> sortedWith(compareBy<Album> { it.releaseDateSortKey <= 0 }.thenByDescending { it.releaseDateSortKey }.thenByDescending { it.name.lowercase(Locale.ROOT) })
        MetadataDetailAlbumSortMode.SongCount -> sortedByDescending { it.songCount }
        MetadataDetailAlbumSortMode.SongCountAsc -> sortedBy { it.songCount }
        MetadataDetailAlbumSortMode.Duration -> sortedByDescending { durations[it.id] ?: 0L }
        MetadataDetailAlbumSortMode.DurationAsc -> sortedBy { durations[it.id] ?: 0L }
        MetadataDetailAlbumSortMode.Name -> sortedBy { it.name.lowercase(Locale.ROOT) }
        MetadataDetailAlbumSortMode.NameDesc -> sortedByDescending { it.name.lowercase(Locale.ROOT) }
    }
}

@Composable
internal fun String.categoryTitle(): String {
    val context = LocalContext.current
    return when (this) {
        "genre" -> context.getString(R.string.category_genre)
        "year" -> context.getString(R.string.category_year)
        "composer" -> context.getString(R.string.category_composer)
        "arranger" -> context.getString(R.string.category_arranger)
        "lyricist" -> context.getString(R.string.category_lyricist)
        "folder" -> context.getString(R.string.category_folder)
        else -> context.getString(R.string.category_general)
    }
}

@Composable
internal fun String.categoryCountSummary(count: Int): String {
    val context = LocalContext.current
    return when (this) {
        "genre" -> context.getString(R.string.category_count_genres, count)
        "composer" -> context.getString(R.string.category_count_composers, count)
        "arranger" -> context.getString(R.string.category_count_arrangers, count)
        "lyricist" -> context.getString(R.string.category_count_lyricists, count)
        "folder" -> context.getString(R.string.category_count_folders, count)
        "year" -> context.getString(R.string.category_count_years, count)
        else -> context.getString(R.string.category_count_general, count)
    }
}

internal fun String.usesSingleColumnCategory(): Boolean {
    return this in PERSON_METADATA_CATEGORY_TYPES || this == "folder"
}

internal val PERSON_METADATA_CATEGORY_TYPES = setOf("composer", "arranger", "lyricist")

internal fun Long.formatDuration(): String {
    return formatPlaybackDuration()
}

internal fun Long.formatDateText(context: android.content.Context): String {
    if (this <= 0L) return context.getString(R.string.folder_unknown_modified_time)
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(millis))
}

internal fun Long.formatDateTimeText(context: android.content.Context): String {
    if (this <= 0L) return context.getString(R.string.folder_unknown_modified_time)
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
