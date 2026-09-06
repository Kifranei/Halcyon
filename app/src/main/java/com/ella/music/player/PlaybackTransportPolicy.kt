package com.ella.music.player

/**
 * Projects the transport state while a MediaController command is still in flight.
 *
 * Media3 exposes [Player.isPlaying] and [Player.playWhenReady] independently.  On some OEM
 * builds the AudioTrack changes first and the corresponding controller callback is delayed or
 * lost, so a short-lived user command must remain visible until the controller catches up.
 */
internal data class TransportStateProjection(
    val isPlaying: Boolean,
    val playWhenReady: Boolean,
    val acknowledged: Boolean
)

internal fun projectTransportState(
    actualIsPlaying: Boolean,
    actualPlayWhenReady: Boolean,
    pendingTarget: Boolean?
): TransportStateProjection {
    val pending = pendingTarget ?: return TransportStateProjection(
        isPlaying = actualPlayWhenReady && actualIsPlaying,
        playWhenReady = actualPlayWhenReady,
        acknowledged = true
    )

    if (pending != actualPlayWhenReady) {
        return TransportStateProjection(
            isPlaying = pending,
            playWhenReady = pending,
            acknowledged = false
        )
    }

    if (!pending) {
        return TransportStateProjection(
            isPlaying = false,
            playWhenReady = false,
            acknowledged = true
        )
    }

    // Keep a play request projected while the decoder is preparing. The guard is cleared as soon
    // as an actual playing callback arrives (or by the manager's timeout if playback failed).
    return TransportStateProjection(
        isPlaying = true,
        playWhenReady = true,
        acknowledged = actualIsPlaying
    )
}

