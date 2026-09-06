package com.ella.music.data.scanner

/**
 * Audio extensions that can enter the library through filesystem/SAF discovery.
 *
 * Keep this list shared with WebDAV discovery. MediaStore does not index every format on every
 * Android build, so the filesystem fallback must use the same allow-list as remote folders.
 */
internal val supportedAudioFileExtensions: Set<String> = setOf(
    "mp3", "mp2", "flac", "ogg", "oga", "opus", "spx", "aac",
    "m4a", "m4b", "m4r", "m4p", "mp4", "wav", "wave", "wma", "asf",
    "aiff", "aif", "aifc", "afc", "ape", "alac",
    "dsf", "dff", "dsdiff", "dts", "dtshd", "wv", "tta", "mpc", "shn", "mka"
)

/** Salt Player keeps MediaStore rows at least 1 KB so empty/placeholder audio is skipped. */
internal const val MEDIA_STORE_MIN_AUDIO_BYTES = 1_000L

internal fun String.audioExtension(): String =
    substringAfterLast('.', "").lowercase()

/**
 * Salt Player's Android MediaStore scan keeps a row when `_data` is present, the file is at
 * least 1 KB, and the extension is a known audio type. No `IS_MUSIC`, duration, or
 * [java.io.File.exists] gate — scoped storage often hides files that MediaStore still indexes.
 */
internal fun isMediaStoreAudioCandidate(path: String, fileSize: Long): Boolean {
    if (path.isBlank() || fileSize < MEDIA_STORE_MIN_AUDIO_BYTES) return false
    return path.audioExtension() in supportedAudioFileExtensions
}
