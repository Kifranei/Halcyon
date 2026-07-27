package com.ella.music.ui.components

import com.ella.music.data.model.Song
import com.ella.music.data.splitArtistNames
import com.ella.music.data.splitGenreNames
import com.ella.music.data.tagIdentityKey

/** Selects the representative cover for every metadata-category surface using one priority order. */
internal fun selectMetadataCategoryCoverSong(
    songs: List<Song>,
    type: String,
    name: String
): Song? {
    val target = name.tagIdentityKey()
    val matched = songs.filter { song -> song.matchesMetadataCategory(type, target) }
    val albumArtistMatched = if (type in setOf("composer", "arranger", "lyricist")) {
        songs.filter { song -> splitArtistNames(song.albumArtist).any { it.tagIdentityKey() == target } }
    } else {
        emptyList()
    }
    if (matched.isEmpty() && albumArtistMatched.isEmpty()) return null

    fun hasUsableCover(song: Song): Boolean = song.coverUrl.isNotBlank() || song.albumId > 0L
    val candidates = when (type) {
        "composer", "arranger", "lyricist" -> {
            val roleMatched = matched
            val uniqueAlbumArtist = albumArtistMatched.filter { song ->
                splitArtistNames(song.albumArtist).singleOrNull()?.tagIdentityKey() == target
            }
            val uniqueRole = roleMatched.filter { song ->
                song.metadataCategoryNamesForCover(type).singleOrNull()?.tagIdentityKey() == target
            }
            val anyAlbumArtist = albumArtistMatched
            val anyRole = roleMatched
            // "Unique" describes the tag field itself, not an album containing one song.
            // Keep a multi-track album eligible when its album artist/composer is unambiguous.
            uniqueAlbumArtist + uniqueRole + anyAlbumArtist + anyRole + matched
        }
        "genre" -> {
            val genreOnly = matched.filter { song ->
                splitGenreNames(song.genre).singleOrNull()?.tagIdentityKey() == target
            }
            genreOnly + matched
        }
        "year" -> {
            // Prefer a matching year's own album cover and always retain a matching-song fallback.
            matched.sortedByDescending { it.albumId > 0L } + matched
        }
        else -> matched
    }
    return candidates.distinctBy { it.id to it.path }.firstOrNull(::hasUsableCover)
        ?: matched.firstOrNull()
}

private fun Song.matchesMetadataCategory(type: String, target: String): Boolean =
    metadataCategoryNamesForCover(type).any { it.tagIdentityKey() == target }

private fun Song.metadataCategoryNamesForCover(type: String): List<String> = when (type) {
    "composer" -> splitArtistNames(composer)
    "arranger" -> splitArtistNames(arranger)
    "lyricist" -> splitArtistNames(lyricist)
    "genre" -> splitGenreNames(genre)
    "year" -> Regex("""\d{4}""").find(year)?.value?.let(::listOf).orEmpty()
    else -> emptyList()
}
