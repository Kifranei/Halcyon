package com.ella.music.viewmodel

import com.ella.music.data.model.Song
import com.ella.music.data.customCategoryValues
import com.ella.music.data.splitArtistNames
import com.ella.music.data.splitGenreNames

data class AiPlaylistRecommendationResult(
    val title: String,
    val reason: String,
    val songs: List<Song>
)

data class AiLibraryChatResult(
    val answer: String,
    val songs: List<Song>,
    val playlistName: String
)

data class MetadataCategoryItem(
    val name: String,
    val songCount: Int,
    val albumCount: Int,
    val duration: Long,
    val dateModified: Long = 0L,
    val coverAlbumIds: List<Long> = emptyList(),
    val representativeSong: Song? = null
)

internal data class ArtistAccumulator(
    val name: String,
    var songCount: Int = 0
)

internal fun Song.metadataCategoryNames(type: String): List<String> {
    return when (type) {
        "genre" -> splitGenreNames(genre)
        "year" -> listOfNotNull(year.extractYear())
        "composer" -> splitArtistNames(composer)
        "arranger" -> splitArtistNames(arranger)
        "lyricist" -> splitArtistNames(lyricist)
        "producer" -> splitArtistNames(producer)
        "folder" -> listOfNotNull(parentFolderPath())
        else -> customCategoryValues(type)
    }
}

private val YEAR_REGEX = Regex("""\d{4}""")

internal fun String.extractYear(): String? {
    return YEAR_REGEX.find(this)?.value
}

internal fun Song.parentFolderPath(): String? {
    val normalized = path.replace('\\', '/')
    return normalized.substringBeforeLast('/', missingDelimiterValue = "")
        .trim()
        .ifBlank { null }
}
