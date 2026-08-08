package com.ella.music.player

import androidx.media3.common.MediaItem

data class PlaybackExternalSnapshot(
    val mediaItem: MediaItem?,
    val mediaItemIndex: Int,
    val mediaItemCount: Int,
    val positionMs: Long,
    val durationMs: Long,
    val repeatMode: Int,
    val isPlaying: Boolean,
    val playbackState: Int,
    val playWhenReady: Boolean = isPlaying
)

data class PlaybackModeExternalSnapshot(
    val shuffle: Boolean,
    val repeatMode: Int
)
