package com.ella.music.ui.player

import androidx.media3.common.Player
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the active MV surface synchronized to the audio player's clock.
 */
internal data class MusicVideoPlaybackSnapshot(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playWhenReady: Boolean = false
)

internal object MusicVideoPlaybackBridge {
    private data class Entry(
        val snapshot: MutableStateFlow<MusicVideoPlaybackSnapshot> =
            MutableStateFlow(MusicVideoPlaybackSnapshot()),
        @Volatile var playWhenReady: Boolean = false,
        // A tap reaches the audio controller asynchronously. Keep that direct user intent until
        // the controller publishes the matching state, so an old audio snapshot cannot restart MV.
        @Volatile var pendingPlayWhenReady: Boolean? = null,
        @Volatile var syncPositionMs: Long? = null,
        @Volatile var syncDurationMs: Long? = null,
        @Volatile var player: Player? = null
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    private fun keyFor(source: DynamicCoverSource): String =
        source.playbackOwnerKey.ifBlank { source.failureKey }

    fun snapshot(source: DynamicCoverSource?): StateFlow<MusicVideoPlaybackSnapshot> {
        val key = source?.let(::keyFor).orEmpty()
        return entries.getOrPut(key) { Entry() }.snapshot
    }

    fun attach(source: DynamicCoverSource, player: Player) {
        if (source.role != PlayerVideoRole.MusicVideo) return
        val entry = entries.getOrPut(keyFor(source)) { Entry() }
        entry.player = player
        player.playWhenReady = entry.playWhenReady
        applySync(entry, player)
        publish(source, player)
    }

    fun publish(source: DynamicCoverSource, player: Player) {
        if (source.role != PlayerVideoRole.MusicVideo) return
        val duration = player.duration.takeIf { it > 0L } ?: 0L
        val entry = entries.getOrPut(keyFor(source)) { Entry() }
        entry.playWhenReady = player.playWhenReady
        entry.snapshot.value = MusicVideoPlaybackSnapshot(
            positionMs = player.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
            playWhenReady = entry.playWhenReady
        )
    }

    fun detach(source: DynamicCoverSource, player: Player) {
        if (source.role != PlayerVideoRole.MusicVideo) return
        entries[keyFor(source)]?.takeIf { it.player === player }?.player = null
    }

    fun seekToProgress(source: DynamicCoverSource?, progress: Float) {
        val resolvedSource = source ?: return
        val entry = entries[keyFor(resolvedSource)] ?: return
        val player = entry.player ?: return
        val duration = player.duration.takeIf { it > 0L } ?: entry.snapshot.value.durationMs
        if (duration <= 0L) return
        player.seekTo((duration * progress.coerceIn(0f, 1f)).toLong())
        publish(resolvedSource, player)
    }

    fun setPlaying(source: DynamicCoverSource?, playing: Boolean) {
        val resolvedSource = source ?: return
        setPlaying(keyFor(resolvedSource), playing)
    }

    /**
     * Records playback intent before the MV source has finished resolving.  This lets a lyric
     * double-tap pause the future landscape decoder as well as an already attached one. Pausing
     * is immediate; resuming waits for the audio controller so the video cannot start on a stale
     * clock and immediately seek back.
     */
    fun setPlaying(playbackOwnerKey: String, playing: Boolean) {
        if (playbackOwnerKey.isBlank()) return
        val entry = entries.getOrPut(playbackOwnerKey) { Entry() }
        entry.pendingPlayWhenReady = playing
        if (!playing) {
            entry.playWhenReady = false
            entry.player?.playWhenReady = false
            entry.snapshot.value = entry.snapshot.value.copy(playWhenReady = playing)
        }
    }

    fun togglePlayback(source: DynamicCoverSource?) {
        val resolvedSource = source ?: return
        val entry = entries.getOrPut(keyFor(resolvedSource)) { Entry() }
        setPlaying(resolvedSource, !entry.playWhenReady)
    }

    fun syncToAudio(
        source: DynamicCoverSource,
        positionMs: Long,
        audioDurationMs: Long?,
        playing: Boolean
    ) {
        if (source.role != PlayerVideoRole.MusicVideo) return
        val entry = entries.getOrPut(keyFor(source)) { Entry() }
        entry.syncPositionMs = positionMs.coerceAtLeast(0L)
        entry.syncDurationMs = audioDurationMs?.coerceAtLeast(0L)
        entry.playWhenReady = when (val pendingPlaying = entry.pendingPlayWhenReady) {
            false -> {
                if (!playing) entry.pendingPlayWhenReady = null
                false
            }
            true -> {
                if (playing) {
                    entry.pendingPlayWhenReady = null
                    true
                } else {
                    // Audio has not resumed yet. Keep the silent decoder on the shared clock.
                    false
                }
            }
            null -> playing
        }
        entry.player?.let { player ->
            applySync(entry, player)
            publish(source, player)
        }
    }

    private fun applySync(entry: Entry, player: Player) {
        val requestedPosition = entry.syncPositionMs ?: return
        val audioDuration = entry.syncDurationMs ?: Long.MAX_VALUE
        val videoDuration = player.duration.takeIf { it > 0L } ?: Long.MAX_VALUE
        val target = if (requestedPosition >= videoDuration) {
            (videoDuration - 1L).coerceAtLeast(0L)
        } else {
            requestedPosition.coerceAtMost(audioDuration)
        }
        // Normal playback advances on the video player's own clock. Seeking every UI position
        // update caused decoder flushes and made MV playback visibly stutter.
        if (kotlin.math.abs(player.currentPosition - target) > MUSIC_VIDEO_RESYNC_TOLERANCE_MS ||
            (!entry.playWhenReady && player.currentPosition != target)
        ) {
            player.seekTo(target)
        }
        player.playWhenReady = entry.playWhenReady && requestedPosition < videoDuration && requestedPosition < audioDuration
    }

    private const val MUSIC_VIDEO_RESYNC_TOLERANCE_MS = 750L
}
