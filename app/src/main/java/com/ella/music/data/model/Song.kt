package com.ella.music.data.model

import androidx.compose.runtime.Immutable
import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.NameSplitConfigStore

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val path: String,
    val fileName: String,
    val fileSize: Long = 0L,
    val mimeType: String = "",
    val dateAdded: Long = 0L,
    val dateModified: Long = 0L,
    val trackNumber: Int = 0,
    val discNumber: Int = 0,
    val albumArtist: String = "",
    val genre: String = "",
    val year: String = "",
    val composer: String = "",
    val arranger: String = "",
    val lyricist: String = "",
    val coverUrl: String = "",
    val onlineSource: String = "",
    val onlineId: String = "",
    val onlineMvId: String = "",
    val onlineLyrics: String = "",
    val onlineLyricTranslation: String = "",
    val onlineLyricPronunciation: String = "",
    /**
     * Category that supplied this particular queue occurrence.
     *
     * This is intentionally nullable: null means that an old/unclassified song has no source,
     * while an empty string explicitly means "do not offer source navigation". Keeping it on the
     * queue item, rather than in a map keyed by the song identity, lets the same song be queued
     * from two different categories without one occurrence overwriting the other.
     */
    val playbackSourceKey: String? = null
) {
    val durationText: String
        get() = duration.formatPlaybackDuration()
}

/**
 * True for a NetEase track played straight from the network. A downloaded NetEase song is a
 * regular local file (absolute path, `file://` or `content://`) and is not a stream, so it keeps
 * every file-based action (tag editing, rating, spectrum, lyric timing, ...).
 */
fun Song.isNeteaseStream(): Boolean {
    if (onlineSource != "netease") return false
    val localFile = path.startsWith("/") ||
        path.startsWith("file:", ignoreCase = true) ||
        path.startsWith("content:", ignoreCase = true)
    return !localFile
}

fun Song.albumIdentityId(): Long {
    val albumName = LibraryNormalizer.cleanedAlbumText(album).ifBlank { "Unknown Album" }
    val albumOwner = LibraryNormalizer.cleanedArtistText(albumArtist)
    val key = "${albumName.normalizedAlbumIdentityPart()}|${albumOwner.normalizedAlbumIdentityPart()}"
    var hash = -0x340d631b7bdddcdbL
    key.forEach { char ->
        hash = hash xor char.code.toLong()
        hash *= 0x100000001b3L
    }
    return hash and Long.MAX_VALUE
}

private fun String.normalizedAlbumIdentityPart(): String =
    trim()
        .ifBlank { "unknown" }
        .let { value -> if (NameSplitConfigStore.tagIgnoreCase) value.lowercase() else value }
        .replace(Regex("\\s+"), " ")
