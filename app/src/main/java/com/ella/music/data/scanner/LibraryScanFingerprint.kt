package com.ella.music.data.scanner

import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.model.Song
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.util.zip.CRC32

internal data class LibraryScanFingerprint(
    val key: String,
    val path: String,
    val fileSize: Long,
    val dateModified: Long,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long
)

/**
 * A small, persistent content stamp for local audio files. Android's MediaStore and several tag
 * editors can preserve both SIZE and DATE_MODIFIED, so those two fields alone cannot invalidate a
 * library row. Sampling the beginning and end catches the metadata blocks used by ID3, Vorbis,
 * FLAC, MP4 and APE without reading an entire (often multi-hundred-megabyte) audio file.
 */
internal fun quickLocalFileFingerprint(path: String): String? {
    if (path.isBlank() || path.startsWith("content:", ignoreCase = true) ||
        path.startsWith("http://", ignoreCase = true) || path.startsWith("https://", ignoreCase = true)
    ) return null
    val file = File(path)
    if (!file.isFile) return null
    val length = runCatching { file.length() }.getOrDefault(0L)
    if (length <= 0L) return null
    val checksum = CRC32()
    val sampleSize = minOf(QUICK_FINGERPRINT_SAMPLE_BYTES.toLong(), length).toInt()
    return runCatching {
        FileInputStream(file).use { input ->
            updateChecksum(input, checksum, sampleSize)
        }
        if (length > sampleSize) {
            RandomAccessFile(file, "r").use { input ->
                input.seek(length - sampleSize)
                val buffer = ByteArray(sampleSize)
                var remaining = sampleSize
                while (remaining > 0) {
                    val read = input.read(buffer, 0, remaining)
                    if (read <= 0) break
                    checksum.update(buffer, 0, read)
                    remaining -= read
                }
            }
        }
        "$length-${checksum.value.toString(16)}"
    }.getOrNull()
}

private fun updateChecksum(input: FileInputStream, checksum: CRC32, count: Int) {
    val buffer = ByteArray(minOf(16 * 1024, count.coerceAtLeast(1)))
    var remaining = count
    while (remaining > 0) {
        val read = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (read <= 0) break
        checksum.update(buffer, 0, read)
        remaining -= read
    }
}

private const val QUICK_FINGERPRINT_SAMPLE_BYTES = 16 * 1024

internal fun LibraryScanFingerprint.needsUpdateAgainst(
    cached: LibraryScanFingerprint?,
    forcePlaceholderRefresh: Boolean = false
): Boolean {
    if (cached == null) return true
    if (key != cached.key) return true
    if (path != cached.path) return true
    if (fileSize > 0L && cached.fileSize > 0L && fileSize != cached.fileSize) return true
    if (dateModified > 0L && cached.dateModified > 0L && dateModified != cached.dateModified) return true
    if (duration > 0L && cached.duration > 0L && duration != cached.duration) return true
    val currentTitle = LibraryNormalizer.cleanedTagText(title)
    val cachedTitle = LibraryNormalizer.cleanedTagText(cached.title)
    if (LibraryNormalizer.isUsableTagText(currentTitle) &&
        currentTitle != cachedTitle &&
        !LibraryNormalizer.isMissingTag(currentTitle, File(path).name)
    ) {
        return true
    }
    val currentArtist = LibraryNormalizer.cleanedArtistText(artist)
    val cachedArtist = LibraryNormalizer.cleanedArtistText(cached.artist)
    if (LibraryNormalizer.isUsableArtistText(currentArtist) && currentArtist != cachedArtist) {
        return true
    }
    val currentAlbum = LibraryNormalizer.cleanedAlbumText(album)
    val cachedAlbum = LibraryNormalizer.cleanedAlbumText(cached.album)
    if (LibraryNormalizer.isUsableAlbumText(currentAlbum) && currentAlbum != cachedAlbum) {
        return true
    }
    return forcePlaceholderRefresh
}

internal fun LibraryScanFingerprint.hasSameFileSnapshot(other: LibraryScanFingerprint): Boolean =
    key == other.key &&
        path == other.path &&
        (fileSize <= 0L || other.fileSize <= 0L || fileSize == other.fileSize) &&
        (dateModified <= 0L || other.dateModified <= 0L || dateModified == other.dateModified) &&
        (duration <= 0L || other.duration <= 0L || duration == other.duration)

internal fun MediaStoreAudioItem.toLibraryScanFingerprint(): LibraryScanFingerprint =
    LibraryScanFingerprint(
        key = MediaStoreLibraryIndexer.mediaStoreLibrarySyncKey(id, path),
        path = path,
        fileSize = fileSize,
        dateModified = dateModified,
        title = title,
        artist = artist,
        album = album,
        duration = duration
    )

internal fun Song.toLibraryScanFingerprint(): LibraryScanFingerprint =
    LibraryScanFingerprint(
        key = MediaStoreLibraryIndexer.mediaStoreLibrarySyncKey(id, path),
        path = path,
        fileSize = fileSize,
        dateModified = dateModified,
        title = title,
        artist = artist,
        album = album,
        duration = duration
    )

internal fun MediaStoreAudioItem.withLocalFileSnapshot(): MediaStoreAudioItem {
    if (path.isBlank() || path.startsWith("content:", ignoreCase = true) ||
        path.startsWith("http://", ignoreCase = true) ||
        path.startsWith("https://", ignoreCase = true)
    ) {
        return this
    }
    val file = File(path)
    val size = runCatching { file.length() }.getOrDefault(0L)
    val modified = runCatching { file.lastModified() }.getOrDefault(0L)
    if (size <= 0L && modified <= 0L) return this
    if ((size <= 0L || size == fileSize) && (modified <= 0L || modified == dateModified)) return this
    return copy(
        fileSize = size.takeIf { it > 0L } ?: fileSize,
        dateModified = modified.takeIf { it > 0L } ?: dateModified
    )
}
