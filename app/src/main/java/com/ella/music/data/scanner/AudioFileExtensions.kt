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
