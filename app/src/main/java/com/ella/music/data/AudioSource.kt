package com.ella.music.data

fun String.isHttpAudioSource(): Boolean =
    startsWith("http://", ignoreCase = true) || startsWith("https://", ignoreCase = true)

fun String.isContentAudioSource(): Boolean =
    startsWith("content://", ignoreCase = true)

fun String.isMediaStoreContentAudioSource(): Boolean =
    startsWith("content://media/", ignoreCase = true)

/**
 * MediaStore album-art URIs are provider fallbacks, not song-specific artwork. Some vendor
 * providers expose the URI in Media3 metadata but cannot decode it from an app process; local
 * playback surfaces must read the file's embedded/sidecar artwork before trying this fallback.
 */
fun String.isMediaStoreAlbumArtworkUri(): Boolean {
    val normalized = trim()
    if (!normalized.startsWith("content://media/", ignoreCase = true)) return false
    return normalized.contains("/audio/albumart/", ignoreCase = true) ||
        normalized.contains("/audio/albums/", ignoreCase = true)
}

fun String.isFileUriAudioSource(): Boolean =
    startsWith("file://", ignoreCase = true)

fun String.isUriAudioSource(): Boolean =
    isContentAudioSource() || isHttpAudioSource() || isFileUriAudioSource()
