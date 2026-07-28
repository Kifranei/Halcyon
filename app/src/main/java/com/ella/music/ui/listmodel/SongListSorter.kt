package com.ella.music.ui.listmodel

import com.ella.music.data.model.Song
import java.util.Locale

internal enum class SongSortField {
    Title,
    FileName,
    Duration,
    FileSize,
    DateAdded,
    DateModified,
    Year,
    Custom
}

internal enum class SongListDisplayMode {
    Title,
    FileName
}

internal data class SongDisplaySpec(
    val mode: SongListDisplayMode = SongListDisplayMode.Title
) : DisplaySpec<Song> {
    override fun displayTitleFor(item: Song): String =
        when (mode) {
            SongListDisplayMode.Title -> item.title
            SongListDisplayMode.FileName -> item.resolvedFileName()
        }
}

internal fun displayTitleFor(song: Song, sortSpec: SortSpec<SongSortField>): String =
    songDisplaySpecFor(sortSpec).displayTitleFor(song)

internal fun songDisplaySpecFor(sortSpec: SortSpec<SongSortField>): SongDisplaySpec =
    SongDisplaySpec(
        mode = if (sortSpec.field == SongSortField.FileName) {
            SongListDisplayMode.FileName
        } else {
            SongListDisplayMode.Title
        }
    )

internal object LibraryListSorter {
    fun sortSongs(
        songs: List<Song>,
        sortSpec: SortSpec<SongSortField>
    ): SortedListResult<Song> =
        when (sortSpec.field) {
            SongSortField.Title -> songs.sortedByMusicKey(
                selector = Song::title,
                direction = sortSpec.direction
            )
            SongSortField.FileName -> songs.sortedByMusicKey(
                selector = Song::resolvedFileName,
                direction = sortSpec.direction
            )
            SongSortField.Duration -> SortedListResult(
                songs.sortedWithDirection(sortSpec.direction, compareBy<Song> { it.duration })
            )
            SongSortField.FileSize -> SortedListResult(
                songs.sortedWithDirection(sortSpec.direction, compareBy<Song> { it.fileSize })
            )
            SongSortField.DateAdded -> SortedListResult(
                songs.sortedWithDirection(sortSpec.direction, compareBy<Song> { it.dateAdded })
            )
            SongSortField.DateModified -> SortedListResult(
                songs.sortedWithDirection(sortSpec.direction, compareBy<Song> { it.dateModified })
            )
            SongSortField.Year -> SortedListResult(songs.sortedByReleaseDate(sortSpec.direction))
            SongSortField.Custom -> SortedListResult(
                if (sortSpec.direction == SortDirection.Descending) songs.asReversed() else songs
            )
        }
}

internal fun Song.releaseDateSortKeyOrNull(): Int? =
    parseReleaseDateSortKey(year)

internal fun Song.resolvedFileName(): String =
    fileName.ifBlank { path.substringAfterLast('/') }

internal fun Song.fastIndexSection(sortKey: String? = null): String =
    FastIndexSectionResolver.sectionFor(sortKey ?: MusicSortKeyNormalizer.normalize(title))

internal fun String.musicSortKey(): String =
    MusicSortKeyNormalizer.normalize(this)

internal fun List<Song>.sortedByReleaseDate(direction: SortDirection): List<Song> {
    val releaseDateComparator = if (direction == SortDirection.Ascending) {
        compareBy<Song> { it.releaseDateSortKeyOrNull() == null }
            .thenBy { it.releaseDateSortKeyOrNull() ?: Int.MAX_VALUE }
    } else {
        compareBy<Song> { it.releaseDateSortKeyOrNull() == null }
            .thenByDescending { it.releaseDateSortKeyOrNull() ?: Int.MIN_VALUE }
    }
    val albumComparator = if (direction == SortDirection.Ascending) {
        compareBy<Song> { it.album.lowercase(Locale.ROOT) }
    } else {
        compareByDescending<Song> { it.album.lowercase(Locale.ROOT) }
    }
    return sortedWith(
        releaseDateComparator
            .then(albumComparator)
            .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
            .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
            .thenBy { it.title.lowercase(Locale.ROOT) }
    )
}

private inline fun List<Song>.sortedByMusicKey(
    direction: SortDirection,
    crossinline selector: (Song) -> String
): SortedListResult<Song> {
    val entries = mapIndexed { index, song ->
        val raw = selector(song)
        SongSortEntry(
            song = song,
            sortKey = MusicSortKeyNormalizer.normalize(raw),
            fallback = raw,
            originalIndex = index
        )
    }.sortedWith(
        compareBy<SongSortEntry> { it.sortKey }
            .thenBy { it.fallback }
            .thenBy { it.originalIndex }
    ).let { entries ->
        if (direction == SortDirection.Descending) entries.asReversed() else entries
    }
    return SortedListResult(
        items = entries.map { it.song },
        fastIndexKeysById = entries.associate { it.song.id to it.sortKey }
    )
}

private fun List<Song>.sortedWithDirection(
    direction: SortDirection,
    comparator: Comparator<Song>
): List<Song> =
    if (direction == SortDirection.Descending) {
        sortedWith(comparator.reversed())
    } else {
        sortedWith(comparator)
    }

private data class SongSortEntry(
    val song: Song,
    val sortKey: String,
    val fallback: String,
    val originalIndex: Int
)

private val ReleaseDateRegex = Regex("""(\d{4})(?:[-./](\d{1,2})(?:[-./](\d{1,2}))?)?""")

private fun parseReleaseDateSortKey(value: String): Int? {
    val match = ReleaseDateRegex.find(value) ?: return null
    val year = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return null
    val month = match.groupValues.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 12) ?: 0
    val day = match.groupValues.getOrNull(3)?.toIntOrNull()?.coerceIn(0, 31) ?: 0
    return year * 10_000 + month * 100 + day
}
