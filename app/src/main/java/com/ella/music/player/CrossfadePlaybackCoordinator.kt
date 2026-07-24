package com.ella.music.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Performs an opt-in, two-player crossfade without changing the MediaSession's primary player.
 *
 * The session player remains responsible for the queue, notification, and lyrics.  A silent
 * secondary player starts the next media item near the end of the current one, then hands its
 * current position back to the primary player as the queue advances.  Keeping the feature off by
 * default avoids a second decoder and extra network request for users who prefer gapless playback.
 */
@UnstableApi
internal class CrossfadePlaybackCoordinator(
    private val context: Context,
    private val primary: ExoPlayer,
    private val dataSourceFactory: DataSource.Factory,
    audioAttributes: AudioAttributes,
    private val secondaryRenderersFactory: () -> EllaRenderersFactory,
    private val scope: CoroutineScope
) {
    private var audioAttributes: AudioAttributes = audioAttributes
    private data class ActiveTransition(
        val sourceMediaId: String,
        val targetMediaId: String,
        val targetIndex: Int,
        val startPositionMs: Long,
        val baseVolume: Float
    )

    private var crossfadeDurationMs = 0L
    private var secondary: ExoPlayer? = null
    private var preparedSourceMediaId: String? = null
    private var preparedTargetMediaId: String? = null
    private var transition: ActiveTransition? = null
    private var handoffJob: Job? = null
    private var handingOff = false

    private var monitorJob: Job? = null

    private val primaryListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            transition?.let { active ->
                secondary?.playWhenReady = isPlaying
                if (!isPlaying) {
                    primary.volume = active.baseVolume
                    secondary?.volume = 0f
                }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val active = transition ?: run {
                clearSecondary()
                return
            }
            if (mediaItem?.mediaId == active.targetMediaId) {
                handOffToPrimary(active)
            } else {
                cancelTransition()
            }
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) {
            if (transition != null && !handingOff && reason != Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                // A user seek or an externally requested queue jump must immediately win over an
                // in-flight crossfade; leaving the secondary running would create a duplicate song.
                cancelTransition()
            }
        }
    }

    init {
        primary.addListener(primaryListener)
    }

    fun setDuration(durationMs: Int) {
        val normalized = durationMs.coerceIn(0, MAX_CROSSFADE_MS.toInt()).toLong()
        if (crossfadeDurationMs == normalized) return
        crossfadeDurationMs = normalized
        if (normalized <= 0L) {
            monitorJob?.cancel()
            monitorJob = null
            cancelTransition()
        } else if (monitorJob == null) {
            monitorJob = scope.launch {
                while (isActive) {
                    update()
                    delay(if (transition != null) ACTIVE_TICK_MS else IDLE_TICK_MS)
                }
            }
        }
    }

    fun setAudioAttributes(attributes: AudioAttributes) {
        if (audioAttributes == attributes) return
        audioAttributes = attributes
        secondary?.setAudioAttributes(attributes, false)
    }

    fun release() {
        primary.removeListener(primaryListener)
        monitorJob?.cancel()
        monitorJob = null
        handoffJob?.cancel()
        handoffJob = null
        cancelTransition()
    }

    private fun update() {
        val fadeMs = crossfadeDurationMs
        if (fadeMs <= 0L || !primary.isPlaying || primary.duration <= fadeMs) {
            if (transition != null) cancelTransition()
            return
        }
        val source = primary.currentMediaItem ?: run {
            cancelTransition()
            return
        }
        val nextIndex = primary.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex == primary.currentMediaItemIndex) {
            if (transition != null) cancelTransition()
            return
        }
        val target = primary.getMediaItemAt(nextIndex)
        val remainingMs = (primary.duration - primary.currentPosition).coerceAtLeast(0L)

        val active = transition
        if (active != null) {
            if (active.sourceMediaId != source.mediaId || active.targetMediaId != target.mediaId) {
                cancelTransition()
                return
            }
            val progress = ((primary.currentPosition - active.startPositionMs).toFloat() / fadeMs)
                .coerceIn(0f, 1f)
            primary.volume = active.baseVolume * (1f - progress)
            secondary?.volume = active.baseVolume * progress
            return
        }

        if (remainingMs <= fadeMs + PREPARE_LEAD_MS) {
            prepareSecondary(source, target)
        }
        val candidate = secondary
        if (
            remainingMs <= fadeMs &&
            candidate?.playbackState == Player.STATE_READY &&
            preparedSourceMediaId == source.mediaId &&
            preparedTargetMediaId == target.mediaId
        ) {
            val baseVolume = primary.volume.coerceIn(0f, 1f)
            candidate.volume = 0f
            candidate.playWhenReady = true
            transition = ActiveTransition(
                sourceMediaId = source.mediaId,
                targetMediaId = target.mediaId,
                targetIndex = nextIndex,
                startPositionMs = primary.currentPosition,
                baseVolume = baseVolume
            )
        }
    }

    private fun prepareSecondary(source: MediaItem, target: MediaItem) {
        if (preparedSourceMediaId == source.mediaId && preparedTargetMediaId == target.mediaId) return
        clearSecondary()
        secondary = ExoPlayer.Builder(context, secondaryRenderersFactory())
            .setAudioAttributes(audioAttributes, false)
            .setHandleAudioBecomingNoisy(false)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
            .also { player ->
                player.volume = 0f
                player.setMediaItem(target)
                player.prepare()
            }
        preparedSourceMediaId = source.mediaId
        preparedTargetMediaId = target.mediaId
    }

    private fun handOffToPrimary(active: ActiveTransition) {
        val auxiliary = secondary ?: run {
            cancelTransition()
            return
        }
        handoffJob?.cancel()
        handoffJob = scope.launch {
            handingOff = true
            try {
                primary.volume = 0f
                primary.seekTo(active.targetIndex, auxiliary.currentPosition.coerceAtLeast(0L))
                repeat(HANDOFF_STEPS) { step ->
                    val progress = (step + 1).toFloat() / HANDOFF_STEPS
                    primary.volume = active.baseVolume * progress
                    auxiliary.volume = active.baseVolume * (1f - progress)
                    delay(HANDOFF_STEP_MS)
                }
            } finally {
                handingOff = false
                clearSecondary()
                transition = null
                primary.volume = active.baseVolume
            }
        }
    }

    private fun cancelTransition() {
        handoffJob?.cancel()
        handoffJob = null
        val baseVolume = transition?.baseVolume
        transition = null
        clearSecondary()
        if (baseVolume != null) primary.volume = baseVolume
    }

    private fun clearSecondary() {
        secondary?.release()
        secondary = null
        preparedSourceMediaId = null
        preparedTargetMediaId = null
    }

    private companion object {
        const val MAX_CROSSFADE_MS = 12_000L
        const val PREPARE_LEAD_MS = 1_500L
        const val IDLE_TICK_MS = 250L
        const val ACTIVE_TICK_MS = 16L
        const val HANDOFF_STEPS = 8
        const val HANDOFF_STEP_MS = 16L
    }
}
