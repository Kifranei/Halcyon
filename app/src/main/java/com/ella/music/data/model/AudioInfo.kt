package com.ella.music.data.model

import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseMvUrl
import com.ella.music.data.neteaseSongUrl

data class AudioInfo(
    val format: String,
    val bitRate: Int = 0,
    val sampleRate: Int = 0,
    val bitDepth: Int = 0,
    val channels: Int = 0,
    val replayGainDb: Float? = null
)

data class SongTagInfo(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val composer: String = "",
    val arranger: String = "",
    val lyricist: String = "",
    val track: String = "",
    val comment: String = "",
    val copyright: String = "",
    val neteaseKey: String = "",
    val lyrics: String = "",
    val rating: Int = 0,
    val customTagText: String = "",
    /** Keeps individual metadata keys available to search UI and content filters. */
    val customTags: Map<String, List<String>> = emptyMap()
) {
    val displayComment: String
        get() = comment
            .cleanDisplayComment()
            .takeIf { it.isNotBlank() }
            ?.takeUnless { it.looksLikeNeteaseKey() || it == neteaseKey.cleanDisplayComment() || it.looksLikeSourceGarbageComment() }
            ?: decodeNeteaseKey(neteaseKey)?.comment?.cleanDisplayComment()?.takeIf { it.isNotBlank() }
            .orEmpty()
}

fun Song.matchesFullTagSearch(
    query: String,
    tagInfo: SongTagInfo = SongTagInfo()
): Boolean {
    val target = query.trim()
    if (target.isBlank()) return false
    return searchableTagValues(tagInfo).any { it.contains(target, ignoreCase = true) }
}

fun Song.searchableTagValues(tagInfo: SongTagInfo = SongTagInfo()): Sequence<String> = sequence {
    yieldNonBlank(title)
    yieldNonBlank(artist)
    yieldNonBlank(album)
    yieldNonBlank(albumArtist)
    yieldNonBlank(genre)
    yieldNonBlank(year)
    yieldNonBlank(composer)
    yieldNonBlank(arranger)
    yieldNonBlank(lyricist)
    yieldNonBlank(fileName)
    yieldNonBlank(onlineSource)
    yieldNonBlank(onlineId)
    if (trackNumber > 0) yield(trackNumber.toString())
    if (discNumber > 0) yield(discNumber.toString())

    yieldNonBlank(tagInfo.title)
    yieldNonBlank(tagInfo.artist)
    yieldNonBlank(tagInfo.album)
    yieldNonBlank(tagInfo.albumArtist)
    yieldNonBlank(tagInfo.genre)
    yieldNonBlank(tagInfo.year)
    yieldNonBlank(tagInfo.composer)
    yieldNonBlank(tagInfo.arranger)
    yieldNonBlank(tagInfo.lyricist)
    yieldNonBlank(tagInfo.track)
    yieldNonBlank(tagInfo.displayComment)
    yieldNonBlank(tagInfo.copyright)
    yieldNonBlank(tagInfo.customTagText)
    yieldRatingValues(tagInfo.rating)

    decodeNeteaseKey(tagInfo.neteaseKey)?.let { key ->
        yieldNonBlank(key.musicId)
        yieldNonBlank(key.musicName)
        yieldNonBlank(key.mvId)
        key.aliases.forEach { yieldNonBlank(it) }
        key.translatedNames.forEach { yieldNonBlank(it) }
        yieldNonBlank(key.albumId)
        yieldNonBlank(key.albumName)
        yieldNonBlank(key.comment)
        if (key.musicId.isNotBlank()) yield(neteaseSongUrl(key.musicId))
        if (key.mvId.isNotBlank()) yield(neteaseMvUrl(key.mvId))
        if (key.albumId.isNotBlank()) yield(neteaseAlbumUrl(key.albumId))
        key.artists.forEach { artist ->
            yieldNonBlank(artist.id)
            yieldNonBlank(artist.name)
            if (artist.id.isNotBlank()) yield(neteaseArtistUrl(artist.id))
        }
    }
}

private val LYRIC_METADATA_TAGS = setOf(
    "LYRIC", "LYRICS", "SYNCEDLYRIC", "SYNCEDLYRICS", "UNSYNCEDLYRIC", "UNSYNCEDLYRICS",
    "TTML", "TTMLLYRIC", "TTMLLYRICS"
)

fun SongTagInfo.hasLyricMetadata(): Boolean =
    lyrics.isNotBlank() || customTags.any { (rawKey, values) ->
        rawKey.normalizeMetadataTagKey() in LYRIC_METADATA_TAGS && values.any { it.isNotBlank() }
    }

fun SongTagInfo.hasTtmlLyricMetadata(): Boolean = customTags.any { (rawKey, values) ->
    rawKey.normalizeMetadataTagKey() in setOf("TTML", "TTMLLYRIC", "TTMLLYRICS") &&
        values.any { it.isNotBlank() }
}

private fun String.normalizeMetadataTagKey(): String =
    uppercase().filter(Char::isLetterOrDigit)

private suspend fun SequenceScope<String>.yieldNonBlank(value: String?) {
    value?.trim()?.takeIf { it.isNotBlank() }?.let { yield(it) }
}

private suspend fun SequenceScope<String>.yieldRatingValues(rating: Int) {
    val safeRating = rating.coerceIn(0, 5)
    if (safeRating <= 0) return
    yield(safeRating.toString())
    yield("${safeRating}星")
    yield("★".repeat(safeRating))
    when (safeRating) {
        1 -> yield("一星")
        2 -> yield("二星")
        3 -> yield("三星")
        4 -> yield("四星")
        5 -> yield("五星")
    }
}

fun String.looksLikeNeteaseKey(): Boolean {
    val normalized = lowercase()
    return "163" in normalized ||
        "netease" in normalized ||
        "cloudmusic" in normalized ||
        "music.163.com" in normalized
}

private fun String.cleanDisplayComment(): String {
    val text = trim()
        .trim('"', '\'', ' ', '\t', '\r', '\n')
        .replace(Regex("""\s+"""), " ")
    return text.unwrapWholeCommentMarks()
}

private fun String.unwrapWholeCommentMarks(): String {
    val pairs = listOf('《' to '》', '「' to '」', '『' to '』', '<' to '>')
    val first = firstOrNull() ?: return this
    val last = lastOrNull() ?: return this
    val pair = pairs.firstOrNull { it.first == first && it.second == last } ?: return this
    val inner = substring(1, length - 1).trim()
    return inner.takeIf { it.isNotBlank() && pair.first !in it && pair.second !in it } ?: this
}

private fun String.looksLikeSourceGarbageComment(): Boolean {
    val normalized = lowercase()
        .replace(Regex("""[\s_\-:：/\\|,.，。;；()\[\]{}<>《》「」『』]+"""), "")
    if (normalized in setOf("kuwo", "酷我", "kw", "lx")) return true
    return normalized.startsWith("kuwo") && normalized.length <= 12
}
