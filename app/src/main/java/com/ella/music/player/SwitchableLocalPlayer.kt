package com.ella.music.player

import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer

/**
 * Stable local-player identity whose audible ExoPlayer can be replaced without reconnecting
 * CastPlayer, MediaSession, or existing MediaControllers.
 *
 * Crossfade uses this to promote the already-running incoming player. The promoted decoder and
 * AudioTrack therefore keep running; only command/state forwarding changes ownership.
 */
@OptIn(UnstableApi::class)
internal class SwitchableLocalPlayer(initialPlayer: ExoPlayer) :
    ForwardingSimpleBasePlayer(initialPlayer) {

    val activePlayer: ExoPlayer
        get() = getPlayer() as ExoPlayer

    fun promote(player: ExoPlayer) {
        setPlayer(player)
    }
}
