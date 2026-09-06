package com.ella.music.data.repository

import com.ella.music.data.model.Song
import java.io.File

internal val embeddedArtworkFileExtensions: Set<String> = setOf(
    "mp3", "mp2", "flac", "ogg", "oga", "opus", "spx", "aac",
    "m4a", "m4b", "m4r", "m4p", "mp4", "wav", "wave", "wma", "asf",
    "aiff", "aif", "aifc", "afc", "ape", "alac",
    "dsf", "dff", "dsdiff", "dts", "dtshd", "wv", "tta", "mpc", "shn", "mka"
)

internal val coverImageFileExtensions: List<String> = listOf("jpg", "jpeg", "png", "webp")

internal val folderAlbumCoverNames: List<String> = listOf(
    "cover", "folder", "albumart", "albumartsmall", "front", "album", "artwork"
)

internal fun Song.audioExtension(): String =
    fileName.substringAfterLast('.', path.substringAfterLast('.', ""))
        .lowercase()
        .substringBefore('?')
        .substringBefore('#')

internal fun Song.prefersEmbeddedArtwork(): Boolean =
    audioExtension() in embeddedArtworkFileExtensions

/**
 * Images that belong to this song only: same-stem files next to the audio, plus per-song
 * thumbnail-cache names. Folder-level `cover.jpg` is intentionally excluded so mixed albums
 * in one directory do not all render the same picture.
 */
internal fun songNamedCoverFileCandidates(
    songDirectory: File?,
    fileName: String,
    path: String,
    songId: Long,
    musicThumbnailsDir: File
): List<File> {
    val fileNameBase = fileName.ifBlank { File(path).name }
    val stem = fileNameBase.substringBeforeLast('.').ifBlank { File(path).nameWithoutExtension }
    val directories = buildList {
        songDirectory?.let { add(it) }
        songDirectory?.let { add(File(it, ".thumbnails")) }
        add(musicThumbnailsDir)
    }.distinctBy { it.absolutePath }
    val keys = listOf(
        stem,
        fileNameBase,
        songId.takeIf { it > 0L }?.toString().orEmpty(),
        path.sha256()
    ).filter { it.isNotBlank() }.distinct()
    return directories.flatMap { dir ->
        keys.flatMap { key ->
            coverImageFileExtensions.map { ext -> File(dir, "$key.$ext") }
        }
    }
}

/**
 * Album-folder artwork such as `cover.jpg`. Never falls back to "any large image in the folder",
 * because a random photo would then become every song's thumbnail.
 */
internal fun folderAlbumCoverFileCandidates(directory: File): List<File> {
    return folderAlbumCoverNames.flatMap { name ->
        coverImageFileExtensions.map { ext -> File(directory, "$name.$ext") }
    }
}
