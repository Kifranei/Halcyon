package com.ella.music.player

import android.content.ContentUris
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import com.ella.music.data.isMediaStoreAlbumArtworkUri
import com.ella.music.data.isUriAudioSource
import com.ella.music.data.model.Song
import com.ella.music.data.repository.mediaStoreAlbumArtUriString
import java.io.File

/**
 * Stateless helpers extracted from [ExoPlayerManager]. All logic is moved verbatim; these
 * functions depend only on their receivers/arguments, never on manager state.
 */

internal data class ExternalSnapshotGuard(
    val mediaId: String?,
    val song: Song
)

internal fun queueEntryMediaId(songId: Long, sequence: Long): String =
    "queue:$songId:$sequence"

internal fun MediaController.matchesExternalSnapshot(guard: ExternalSnapshotGuard?): Boolean {
    guard ?: return true
    val item = currentMediaItem ?: return false
    if (guard.mediaId != null && item.mediaId == guard.mediaId) return true
    return item.matchesSong(guard.song)
}

/**
 * Keep normal multi-thousand-song queues intact. Only very large libraries are reduced to a
 * small controller window to stay below the MediaSession Binder transaction limit.
 */
internal fun List<Song>.windowedForController(index: Int): Pair<List<Song>, Int> {
    if (size <= LARGE_LIBRARY_SAFE_MODE_THRESHOLD) return this to index
    val from = (index - LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE / 2)
        .coerceIn(0, size - LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE)
    return subList(from, from + LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE).toList() to (index - from)
}

/**
 * Keeps the currently playing occurrence at the same queue entry when an item is moved while
 * the MediaController is disconnected.  Identity lookup is not enough here because duplicate
 * songs intentionally share the same path and metadata.
 */
internal fun adjustedQueueIndexAfterMove(
    currentIndex: Int,
    fromIndex: Int,
    toIndex: Int,
    queueSize: Int
): Int {
    if (
        currentIndex !in 0 until queueSize ||
        fromIndex !in 0 until queueSize ||
        toIndex !in 0 until queueSize ||
        fromIndex == toIndex
    ) return currentIndex
    return when {
        currentIndex == fromIndex -> toIndex
        fromIndex < toIndex && currentIndex in (fromIndex + 1)..toIndex -> currentIndex - 1
        fromIndex > toIndex && currentIndex in toIndex until fromIndex -> currentIndex + 1
        else -> currentIndex
    }
}

internal fun buildPseudoShuffleSeed(sourceOrder: List<Song>, current: Song): Long {
    var seed = 0x9E3779B97F4A7C15uL.toLong()
    sourceOrder.forEachIndexed { index, song ->
        val part = "${song.id}|${song.path}|${song.dateModified}|${song.fileSize}|$index".hashCode().toLong()
        seed = seed xor (part + 0x9E3779B97F4A7C15uL.toLong() + (seed shl 6) + (seed ushr 2))
    }
    seed = seed xor "${current.id}|${current.path}".hashCode().toLong()
    return seed
}

internal fun Song.playbackUri(): Uri {
    if (path.isUriAudioSource()) {
        return path.toUri()
    }
    if (onlineSource.isBlank() && path.startsWith("/") && id > 0L) {
        return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    }
    return if (path.startsWith("/")) Uri.fromFile(File(path)) else path.toUri()
}

internal fun Song.artworkUriForMediaCenter(includeAlbumFallback: Boolean = true): Uri? {
    return artworkUriStringForMediaCenter(includeAlbumFallback)?.toUri()
}

internal fun Song.artworkUriStringForMediaCenter(includeAlbumFallback: Boolean = true): String? {
    coverUrl.takeIf {
        it.isNotBlank() && !it.isMediaStoreAlbumArtworkUri()
    }?.let { return it }
    return mediaStoreAlbumArtUriString(albumId)
        ?.takeIf { includeAlbumFallback }
}

internal fun MediaItem.matchesSong(song: Song): Boolean {
    val itemSong = toSongFromMediaItemExtras()
    if (itemSong != null) {
        return itemSong.isSamePlaybackIdentity(song)
    }
    if (song.path.isNotBlank() && localConfiguration?.uri?.toString().orEmpty() == song.path) return true
    if (song.id > 0L && mediaId == song.id.toString()) return true
    return localConfiguration?.uri?.toString().orEmpty() == song.path
}

internal fun MediaMetadata.matchesNotificationDisplay(other: MediaMetadata): Boolean {
    return title?.toString() == other.title?.toString() &&
        artist?.toString() == other.artist?.toString() &&
        albumTitle?.toString() == other.albumTitle?.toString() &&
        artworkUri == other.artworkUri &&
        artworkData.contentEqualsOrBothNull(other.artworkData)
}

internal fun ByteArray?.contentEqualsOrBothNull(other: ByteArray?): Boolean {
    return when {
        this == null && other == null -> true
        this == null || other == null -> false
        else -> contentEquals(other)
    }
}

internal fun MediaMetadata.withPatchedExtrasFrom(item: MediaItem, reason: String): MediaMetadata {
    val mergedExtras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY)
    extras?.let(mergedExtras::putAll)
    mergedExtras.markMetadataOnlyPatch(reason)
    return buildUpon()
        .setExtras(mergedExtras)
        .build()
}

internal fun Song.isM4aOrAppleLosslessOrAACOrApe(): Boolean {
    val ext = path.substringAfterLast('.', "").lowercase()
    val mime = mimeType.lowercase()
    return when {
        ext == "m4a" || ext == "mp4" || ext == "aac" || ext == "ape" -> true
        ext == "alac" -> true
        mime in setOf(
            "audio/mp4",
            "audio/x-m4a",
            "audio/aac",
            "audio/mp4a-latm",
            "audio/alac",
            "audio/x-ape",
            "audio/ape",
            "application/ape"
        ) -> true
        else -> false
    }
}
