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
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Performs an opt-in, two-player crossfade without changing the MediaSession's primary player.
 *
 * The session player remains responsible for the queue, notification, and lyrics. A silent
 * secondary player pre-buffers the next media item, fades it in while the primary finishes the
 * current item, then stays audible until the primary is ready at the same target position. Keeping
 * the feature off by default avoids a second decoder and extra network request for users who prefer
 * gapless playback.
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
        val baseVolume: Float
    )

    private var crossfadeDurationMs = 0L
    private var crossfadeCurve = CrossfadeTransitionMath.CURVE_EQUAL_POWER
    private var secondary: ExoPlayer? = null
    private var preparedSourceMediaId: String? = null
    private var preparedTargetMediaId: String? = null
    private var transition: ActiveTransition? = null
    private var handingOff = false
    private var handoffStarted = false

    private var monitorJob: kotlinx.coroutines.Job? = null

    private val primaryListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            transition?.let {
                // Buffering the incoming item reports isPlaying=false even though playWhenReady is
                // still true. Keep the outgoing source audible until the target is actually ready.
                secondary?.playWhenReady = primary.playWhenReady
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val active = transition ?: run {
                clearSecondary()
                return
            }
            if (mediaItem?.mediaId == active.targetMediaId) {
                beginHandoff(active)
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

    fun setCurve(curve: Int) {
        crossfadeCurve = CrossfadeTransitionMath.normalizeCurve(curve)
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
        cancelTransition()
    }

    private fun update() {
        val fadeMs = crossfadeDurationMs
        if (fadeMs <= 0L) {
            if (transition != null) cancelTransition()
            return
        }
        val current = primary.currentMediaItem ?: run {
            cancelTransition()
            return
        }
        val active = transition
        if (active != null) {
            if (current.mediaId != active.sourceMediaId && current.mediaId != active.targetMediaId) {
                cancelTransition()
                return
            }
            val auxiliary = secondary ?: run {
                cancelTransition()
                return
            }
            auxiliary.playWhenReady = primary.playWhenReady
            if (current.mediaId == active.targetMediaId || handoffStarted) {
                beginHandoff(active)
                updateHandoff(active, auxiliary)
                return
            }
            if (!primary.playWhenReady) return
            val gains = CrossfadeTransitionMath.gains(
                progress = CrossfadeTransitionMath.fadeProgress(
                    targetPositionMs = auxiliary.currentPosition,
                    fadeDurationMs = fadeMs
                ),
                curve = crossfadeCurve
            )
            primary.volume = active.baseVolume * gains.outgoing
            auxiliary.volume = active.baseVolume * gains.incoming
            if (
                gains.progress >= 1f ||
                primary.playbackState == Player.STATE_ENDED ||
                auxiliary.playbackState == Player.STATE_ENDED
            ) {
                beginHandoff(active)
            }
            return
        }
        if (!primary.isPlaying || primary.duration <= fadeMs) return

        val nextIndex = primary.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET || nextIndex == primary.currentMediaItemIndex) {
            return
        }
        val target = primary.getMediaItemAt(nextIndex)
        val remainingMs = (primary.duration - primary.currentPosition).coerceAtLeast(0L)

        if (remainingMs <= fadeMs + PREPARE_LEAD_MS) {
            prepareSecondary(current, target)
        }
        val candidate = secondary
        if (
            remainingMs <= fadeMs &&
            candidate?.playbackState == Player.STATE_READY &&
            preparedSourceMediaId == current.mediaId &&
            preparedTargetMediaId == target.mediaId
        ) {
            val baseVolume = primary.volume.coerceIn(0f, 1f)
            candidate.volume = 0f
            candidate.playWhenReady = true
            transition = ActiveTransition(
                sourceMediaId = current.mediaId,
                targetMediaId = target.mediaId,
                targetIndex = nextIndex,
                baseVolume = baseVolume
            )
            handoffStarted = false
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
                player.playWhenReady = false
            }
        preparedSourceMediaId = source.mediaId
        preparedTargetMediaId = target.mediaId
    }

    private fun beginHandoff(active: ActiveTransition) {
        if (transition !== active || handoffStarted) return
        val auxiliary = secondary ?: run {
            cancelTransition()
            return
        }
        handoffStarted = true
        primary.volume = 0f
        primary.playWhenReady = auxiliary.playWhenReady
        val targetPositionMs = auxiliary.currentPosition.coerceAtLeast(0L)
        handingOff = true
        try {
            if (
                primary.currentMediaItem?.mediaId != active.targetMediaId ||
                CrossfadeTransitionMath.shouldResyncHandoff(
                    primary.currentPosition - targetPositionMs
                )
            ) {
                primary.seekTo(active.targetIndex, targetPositionMs)
            }
        } finally {
            handingOff = false
        }
    }

    private fun updateHandoff(active: ActiveTransition, auxiliary: ExoPlayer) {
        if (
            primary.currentMediaItem?.mediaId != active.targetMediaId ||
            primary.playbackState != Player.STATE_READY
        ) {
            return
        }
        val driftMs = primary.currentPosition - auxiliary.currentPosition
        if (CrossfadeTransitionMath.shouldResyncHandoff(driftMs)) {
            handingOff = true
            try {
                primary.seekTo(active.targetIndex, auxiliary.currentPosition.coerceAtLeast(0L))
            } finally {
                handingOff = false
            }
            return
        }
        finishTransition(active)
    }

    private fun finishTransition(active: ActiveTransition) {
        if (transition !== active) return
        transition = null
        handoffStarted = false
        secondary?.volume = 0f
        primary.volume = active.baseVolume
        clearSecondary()
    }

    private fun cancelTransition() {
        val baseVolume = transition?.baseVolume
        transition = null
        handoffStarted = false
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
    }
}

internal object CrossfadeTransitionMath {
    const val CURVE_EQUAL_POWER = 0
    const val CURVE_LINEAR = 1
    const val CURVE_SMOOTH = 2
    const val CURVE_FLAT = 3

    private const val HANDOFF_RESYNC_THRESHOLD_MS = 120L

    data class Gains(
        val progress: Float,
        val incoming: Float,
        val outgoing: Float
    )

    fun fadeProgress(targetPositionMs: Long, fadeDurationMs: Long): Float {
        if (fadeDurationMs <= 0L) return 1f
        return (targetPositionMs.toFloat() / fadeDurationMs).coerceIn(0f, 1f)
    }

    fun normalizeCurve(curve: Int): Int = curve.coerceIn(CURVE_EQUAL_POWER, CURVE_FLAT)

    fun gains(progress: Float, curve: Int): Gains {
        val safeProgress = progress.coerceIn(0f, 1f)
        val (incoming, outgoing) = when (normalizeCurve(curve)) {
            CURVE_LINEAR -> safeProgress to (1f - safeProgress)
            CURVE_SMOOTH -> {
                val smooth = safeProgress * safeProgress * (3f - 2f * safeProgress)
                smooth to (1f - smooth)
            }
            CURVE_FLAT -> 1f to 1f
            else -> {
                val angle = safeProgress * (PI.toFloat() / 2f)
                sin(angle) to cos(angle)
            }
        }
        return Gains(
            progress = safeProgress,
            incoming = incoming.coerceIn(0f, 1f),
            outgoing = outgoing.coerceIn(0f, 1f)
        )
    }

    fun shouldResyncHandoff(positionDriftMs: Long): Boolean =
        kotlin.math.abs(positionDriftMs) > HANDOFF_RESYNC_THRESHOLD_MS
}
