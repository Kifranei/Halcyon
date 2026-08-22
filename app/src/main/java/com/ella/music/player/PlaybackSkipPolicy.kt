package com.ella.music.player

/**
 * Local queue arithmetic for skip buttons. MediaController.seekToNextMediaItem is async over Binder
 * and may leave currentMediaItem stale for a frame; the UI therefore jumps from this index first.
 */
internal fun adjacentPlaylistIndex(
    currentIndex: Int,
    offset: Int,
    queueSize: Int,
    wrap: Boolean
): Int? {
    if (queueSize <= 0 || offset == 0) return null
    if (currentIndex !in 0 until queueSize) return null
    val raw = currentIndex + offset
    return when {
        raw in 0 until queueSize -> raw
        wrap -> Math.floorMod(raw, queueSize)
        else -> null
    }
}
