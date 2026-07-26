package com.ella.music.data.scanner

import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.model.Song
import java.io.File

data class MediaStoreAudioItem(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val path: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String,
    val dateAdded: Long,
    val dateModified: Long,
    val trackNumber: Int,
    val discNumber: Int
)

internal data class ScannerMergeStats(
    val mediaStoreItemCount: Int,
    val filesystemFallbackItemCount: Int,
    val mergedItemCount: Int
)

internal fun mergeMediaStoreAndFilesystemItems(
    mediaStoreItems: List<MediaStoreAudioItem>,
    filesystemItems: List<MediaStoreAudioItem>
): Pair<List<MediaStoreAudioItem>, ScannerMergeStats> {
    val merged = ArrayList<MediaStoreAudioItem>(mediaStoreItems.size + filesystemItems.size)
    val seenPaths = HashSet<String>()
    mediaStoreItems.forEach { item ->
        val key = item.path.normalizedAudioPathKey()
        if (key.isNotBlank() && seenPaths.add(key)) merged += item
    }
    var fallbackCount = 0
    filesystemItems.forEach { item ->
        val key = item.path.normalizedAudioPathKey()
        if (key.isNotBlank() && seenPaths.add(key)) {
            merged += item
            fallbackCount++
        }
    }
    return merged to ScannerMergeStats(
        mediaStoreItemCount = mediaStoreItems.size,
        filesystemFallbackItemCount = fallbackCount,
        mergedItemCount = merged.size
    )
}

internal fun String.normalizedAudioPathKey(): String =
    trim().replace('\\', '/').lowercase()

internal fun MediaStoreAudioItem.toShallowSong(minDurationMs: Long = 0): Song? {
    val safeDuration = duration
    if (safeDuration <= 0L || safeDuration < minDurationMs) return null
    return Song(
        id = id,
        title = LibraryNormalizer.cleanedTagText(title)
            .ifBlank { fileName.substringBeforeLast('.').ifBlank { path.substringAfterLast('/') } },
        artist = LibraryNormalizer.cleanedArtistText(artist).ifBlank { "Unknown Artist" },
        album = LibraryNormalizer.cleanedAlbumText(album).ifBlank { "Unknown Album" },
        albumId = albumId,
        duration = safeDuration,
        path = path,
        fileName = fileName,
        fileSize = fileSize.coerceAtLeast(0L),
        mimeType = mimeType,
        dateAdded = dateAdded,
        dateModified = dateModified,
        trackNumber = trackNumber,
        discNumber = discNumber
    )
}

internal fun File.toFallbackAudioItem(): MediaStoreAudioItem {
    val path = absolutePath
    val extension = extension.lowercase()
    val mime = when (extension) {
        "mp3" -> "audio/mpeg"
        "flac" -> "audio/flac"
        "ogg", "oga" -> "audio/ogg"
        "opus" -> "audio/opus"
        "aac" -> "audio/aac"
        "m4a", "mp4" -> "audio/mp4"
        "wav", "wave" -> "audio/wav"
        "wma" -> "audio/x-ms-wma"
        "aiff", "aif" -> "audio/aiff"
        "ape" -> "audio/ape"
        "alac" -> "audio/alac"
        else -> "audio/$extension"
    }
    val stableId = -kotlin.math.abs(path.normalizedAudioPathKey().hashCode().toLong()).coerceAtLeast(1L)
    val modified = lastModified().takeIf { it > 0L } ?: System.currentTimeMillis()
    return MediaStoreAudioItem(
        id = stableId,
        title = nameWithoutExtension,
        artist = "",
        album = "",
        albumId = 0L,
        duration = 0L,
        path = path,
        fileName = name,
        fileSize = length().coerceAtLeast(0L),
        mimeType = mime,
        dateAdded = modified,
        dateModified = modified,
        trackNumber = 0,
        discNumber = 0
    )
}
