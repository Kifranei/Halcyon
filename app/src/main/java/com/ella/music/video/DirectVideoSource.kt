package com.ella.music.video

/** Ordinary files do not need a network/prefix probe before entering the player. */
internal fun isDirectVideoSource(source: String, mime: String?): Boolean {
    if (!isVideoSource(source)) return false
    if (isPlaylistContentType(mime)) return false
    val path = source.substringBefore('?').substringBefore('#').lowercase()
    if (path.endsWith(".m3u") || path.endsWith(".m3u8")) return false
    return mime?.startsWith("video/", ignoreCase = true) == true ||
        listOf(".mp4", ".m4v", ".mkv", ".webm", ".mov", ".avi", ".ts").any(path::endsWith)
}
