package com.ella.music.plugin.source

import com.ella.music.data.metadata.AudioTagInfo

/**
 * Converts the information exposed by an online lyric source into fields understood by
 * Halcyon's embedded-tag editor.  Search results are preferred because they identify the
 * selected release; lyric payload tags fill any fields the search response omitted.
 */
internal fun PluginSearchHit.toAudioTagInfo(
    lyricTags: Map<String, String> = emptyMap()
): AudioTagInfo {
    val search = song
    val metadataMaps = listOf(search.fields, lyricTags)

    fun value(vararg keys: String): String? = metadataMaps.asSequence()
        .flatMap { map -> map.asSequence() }
        .firstOrNull { (key, candidate) ->
            keys.any { wanted -> key.equals(wanted, ignoreCase = true) } && candidate.isNotBlank()
        }
        ?.value
        ?.trim()
        ?.takeIf { it.isNotBlank() }

    fun firstNotBlank(primary: String, vararg keys: String): String? =
        primary.trim().takeIf { it.isNotBlank() } ?: value(*keys)

    fun number(primary: String, vararg keys: String): Int? =
        firstNotBlank(primary, *keys)
            ?.substringBefore('/')
            ?.substringBefore('／')
            ?.trim()
            ?.toIntOrNull()

    return AudioTagInfo(
        title = firstNotBlank(search.title, "title", "ti", "name", "songName"),
        artist = firstNotBlank(search.artist, "artist", "ar", "artists", "singer"),
        album = firstNotBlank(search.album, "album", "al", "albumName"),
        albumArtist = value("albumArtist", "album_artist", "album artist", "albumartist"),
        composer = value("composer", "composers"),
        arranger = value("arranger", "arrangers", "arrangedBy", "arranged_by", "arrangement"),
        lyricist = value("lyricist", "lyricsBy", "lyrics_by", "writer"),
        songwriters = value("songwriters", "songwriter", "author"),
        genre = value("genre", "style"),
        year = firstNotBlank(search.date, "year", "date", "releaseDate", "release_date"),
        trackNumber = number(search.trackNumber, "track", "trackNumber", "track_number", "trackNo"),
        discNumber = number("", "disc", "discNumber", "disc_number", "discNo"),
        copyright = value("copyright", "copyrightInfo"),
        comment = value("comment", "description")
    )
}
