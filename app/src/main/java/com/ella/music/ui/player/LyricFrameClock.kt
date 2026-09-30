package com.ella.music.ui.player

import androidx.compose.runtime.*

/** Live controller time stays continuous across low-frequency UI/overlay payload updates.
 * Consumers read this state during drawing, rather than remeasuring text every frame.
 */
@Composable
internal fun rememberLyricFramePosition(
    sampledMs: Long,
    playing: Boolean,
    active: Boolean = true,
    provider: (() -> Long?)? = LocalPlayerLyricPositionProvider.current,
    offsetMs: Long = 0L
): State<Long> {
    val position = remember { mutableLongStateOf(sampledMs) }
    val latestSample by rememberUpdatedState(sampledMs)
    val latestProvider by rememberUpdatedState(provider)
    val latestOffset by rememberUpdatedState(offsetMs)
    SideEffect { if (!playing || !active) position.longValue = sampledMs.coerceAtLeast(0L) }
    LaunchedEffect(playing, active) {
        if (!playing || !active) return@LaunchedEffect
        var previousFrame = 0L
        while (true) {
            val frame = withFrameNanos { it }
            val live = latestProvider?.invoke()?.let { (it - latestOffset).coerceAtLeast(0L) }
            position.longValue = lyricFramePositionMs(live, position.longValue, latestSample,
                if (previousFrame == 0L) 0L else (frame - previousFrame) / 1_000_000L, playing)
            previousFrame = frame
        }
    }
    return position
}
