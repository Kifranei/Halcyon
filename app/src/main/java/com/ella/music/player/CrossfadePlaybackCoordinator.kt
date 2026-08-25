package com.ella.music.player

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
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
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.max

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
    private val primaryWaveformProbe: WaveformLevelAudioProcessor,
    private val secondaryWaveformProbe: WaveformLevelAudioProcessor,
    private val primaryGainProcessor: CrossfadeGainAudioProcessor,
    private val secondaryGainProcessor: CrossfadeGainAudioProcessor,
    private val secondaryRenderersFactory: () -> EllaRenderersFactory,
    private val onIncomingAudible: (targetIndex: Int, positionMs: Long, playbackSpeed: Float) -> Unit,
    private val onIncomingFinished: () -> Unit,
    private val scope: CoroutineScope
) {
    private var audioAttributes: AudioAttributes = audioAttributes
    private data class ActiveTransition(
        val sourceMediaId: String,
        val targetMediaId: String,
        val targetIndex: Int,
        val baseVolume: Float,
        var incomingAudibleStartMs: Long? = null,
        var effectiveFadeDurationMs: Long = 0L,
        var presentationStarted: Boolean = false,
        var handoffResyncAttempts: Int = 0,
        var handoffBlendStartedAtElapsedRealtime: Long = 0L,
        var handoffSilentDrainStartedAtElapsedRealtime: Long = 0L
    )

    private var crossfadeDurationMs = 0L
    private var crossfadeCurve = CrossfadeTransitionMath.CURVE_EQUAL_POWER
    private var secondary: ExoPlayer? = null
    private var preparedSourceMediaId: String? = null
    private var preparedTargetMediaId: String? = null
    private var transition: ActiveTransition? = null
    private var handingOff = false
    private var handoffStarted = false
    private var handoffStartedAtElapsedRealtime = 0L
    private var handoffSeekStartedAtElapsedRealtime = 0L

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
            runCatching {
                val active = transition ?: run {
                    restorePrimaryGain()
                    clearSecondary()
                    return
                }
                if (mediaItem?.mediaId == active.targetMediaId) {
                    beginHandoff(active)
                } else {
                    cancelTransition()
                }
            }.onFailure { error ->
                abortTransition("media item transition failed", error)
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

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            abortTransition("primary player error", error)
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
                    runCatching { update() }
                        .onFailure { error -> abortTransition("crossfade update failed", error) }
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
            if (transition != null) cancelTransition() else restorePrimaryGain()
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
            if (auxiliary.playerError != null) {
                abortTransition("secondary player error", auxiliary.playerError)
                return
            }
            auxiliary.playWhenReady = primary.playWhenReady
            if (current.mediaId == active.targetMediaId || handoffStarted) {
                beginHandoff(active)
                updateHandoff(active, auxiliary)
                return
            }
            if (!primary.playWhenReady) return
            val incomingLevel = secondaryWaveformProbe.level
            if (active.incomingAudibleStartMs == null && CrossfadeTransitionMath.isAudible(incomingLevel)) {
                active.incomingAudibleStartMs = auxiliary.currentPosition
                val remainingMs = (primary.duration - primary.currentPosition).coerceAtLeast(MIN_SMART_FADE_MS)
                active.effectiveFadeDurationMs = minOf(fadeMs, remainingMs).coerceAtLeast(MIN_SMART_FADE_MS)
                presentIncoming(active, auxiliary)
            }
            val audibleStartMs = active.incomingAudibleStartMs
            val timelineProgress = if (audibleStartMs == null) {
                0f
            } else {
                CrossfadeTransitionMath.fadeProgress(
                    targetPositionMs = (auxiliary.currentPosition - audibleStartMs).coerceAtLeast(0L),
                    fadeDurationMs = active.effectiveFadeDurationMs.coerceAtLeast(MIN_SMART_FADE_MS)
                )
            }
            val smartProgress = CrossfadeTransitionMath.adaptiveProgress(
                timelineProgress = timelineProgress,
                outgoingLevel = primaryWaveformProbe.level,
                incomingLevel = incomingLevel
            )
            val gains = CrossfadeTransitionMath.gains(
                progress = smartProgress,
                curve = crossfadeCurve
            )
            primaryGainProcessor.gain = gains.outgoing
            secondaryGainProcessor.gain = gains.incoming
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
            candidate.volume = baseVolume
            secondaryGainProcessor.gain = 0f
            candidate.playbackParameters = primary.playbackParameters
            candidate.playWhenReady = true
            transition = ActiveTransition(
                sourceMediaId = current.mediaId,
                targetMediaId = target.mediaId,
                targetIndex = nextIndex,
                baseVolume = baseVolume,
                effectiveFadeDurationMs = fadeMs
            )
            handoffStarted = false
            handoffStartedAtElapsedRealtime = 0L
            handoffSeekStartedAtElapsedRealtime = 0L
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
                secondaryGainProcessor.gain = 0f
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
        handoffStartedAtElapsedRealtime = SystemClock.elapsedRealtime()
        handoffSeekStartedAtElapsedRealtime = handoffStartedAtElapsedRealtime
        presentIncoming(active, auxiliary)
        primaryGainProcessor.gain = 0f
        secondaryGainProcessor.gain = 1f
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
        val now = SystemClock.elapsedRealtime()
        if (
            handoffStartedAtElapsedRealtime > 0L &&
            now - handoffStartedAtElapsedRealtime >= HANDOFF_TIMEOUT_MS
        ) {
            abortTransition("handoff timed out")
            return
        }
        if (
            primary.currentMediaItem?.mediaId != active.targetMediaId ||
            primary.playbackState != Player.STATE_READY
        ) {
            return
        }
        if (active.handoffSilentDrainStartedAtElapsedRealtime > 0L) {
            if (now - active.handoffSilentDrainStartedAtElapsedRealtime >= HANDOFF_SILENT_DRAIN_MS) {
                finishTransition(active)
            }
            return
        }
        if (active.handoffBlendStartedAtElapsedRealtime > 0L) {
            val progress = CrossfadeTransitionMath.handoffBlendProgress(
                elapsedMs = now - active.handoffBlendStartedAtElapsedRealtime,
                durationMs = HANDOFF_BLEND_MS
            )
            // Both players contain the incoming song here. A linear handoff avoids the correlated
            // signal boost of an equal-power curve while eliminating the old hard 0-to-1 switch.
            primaryGainProcessor.gain = progress
            secondaryGainProcessor.gain = 1f - progress
            if (progress >= 1f) {
                active.handoffSilentDrainStartedAtElapsedRealtime = now
            }
            return
        }
        val driftMs = primary.currentPosition - auxiliary.currentPosition
        if (
            CrossfadeTransitionMath.shouldResyncHandoff(driftMs) &&
            active.handoffResyncAttempts < MAX_HANDOFF_RESYNC_ATTEMPTS
        ) {
            active.handoffResyncAttempts++
            val measuredSeekLatencyMs = (now - handoffSeekStartedAtElapsedRealtime)
                .coerceIn(0L, MAX_HANDOFF_SEEK_LEAD_MS)
            val compensatedTargetMs = CrossfadeTransitionMath.compensatedHandoffPosition(
                auxiliaryPositionMs = auxiliary.currentPosition,
                positionDriftMs = driftMs,
                measuredSeekLatencyMs = measuredSeekLatencyMs
            )
            handingOff = true
            try {
                handoffSeekStartedAtElapsedRealtime = now
                primary.seekTo(active.targetIndex, compensatedTargetMs)
            } finally {
                handingOff = false
            }
            return
        }
        active.handoffBlendStartedAtElapsedRealtime = now
    }

    private fun finishTransition(active: ActiveTransition) {
        if (transition !== active) return
        transition = null
        handoffStarted = false
        handoffStartedAtElapsedRealtime = 0L
        handoffSeekStartedAtElapsedRealtime = 0L
        secondaryGainProcessor.gain = 0f
        restorePrimaryGain()
        clearSecondary()
        finishIncomingPresentation(active)
    }

    private fun cancelTransition() {
        val active = transition
        transition = null
        handoffStarted = false
        handoffStartedAtElapsedRealtime = 0L
        handoffSeekStartedAtElapsedRealtime = 0L
        // Restore the PCM envelope first. Releasing a vendor decoder can throw, and must never
        // strand the session at the handoff's temporary zero gain.
        restorePrimaryGain()
        clearSecondary()
        if (active != null) finishIncomingPresentation(active)
    }

    private fun presentIncoming(active: ActiveTransition, auxiliary: ExoPlayer) {
        if (active.presentationStarted) return
        active.presentationStarted = true
        onIncomingAudible(
            active.targetIndex,
            auxiliary.currentPosition.coerceAtLeast(0L),
            auxiliary.playbackParameters.speed
        )
    }

    private fun finishIncomingPresentation(active: ActiveTransition) {
        if (!active.presentationStarted) return
        active.presentationStarted = false
        onIncomingFinished()
    }

    private fun restorePrimaryGain() {
        primaryGainProcessor.gain = 1f
    }

    private fun abortTransition(reason: String, error: Throwable? = null) {
        if (error == null) Log.w(TAG, reason) else Log.e(TAG, reason, error)
        cancelTransition()
    }

    private fun clearSecondary() {
        val playerToRelease = secondary
        secondaryGainProcessor.gain = 0f
        secondary = null
        preparedSourceMediaId = null
        preparedTargetMediaId = null
        runCatching { playerToRelease?.release() }
            .onFailure { Log.w(TAG, "Failed to release secondary player", it) }
    }

    private companion object {
        const val MAX_CROSSFADE_MS = 12_000L
        const val PREPARE_LEAD_MS = 1_500L
        const val IDLE_TICK_MS = 250L
        const val ACTIVE_TICK_MS = 16L
        const val MIN_SMART_FADE_MS = 450L
        const val HANDOFF_TIMEOUT_MS = 5_000L
        const val HANDOFF_BLEND_MS = 112L
        const val HANDOFF_SILENT_DRAIN_MS = 160L
        const val MAX_HANDOFF_SEEK_LEAD_MS = 600L
        const val MAX_HANDOFF_RESYNC_ATTEMPTS = 1
        const val TAG = "CrossfadeCoordinator"
    }
}

internal object CrossfadeTransitionMath {
    const val CURVE_EQUAL_POWER = 0
    const val CURVE_LINEAR = 1
    const val CURVE_SMOOTH = 2
    const val CURVE_FLAT = 3

    private const val HANDOFF_RESYNC_THRESHOLD_MS = 120L
    private const val AUDIBLE_LEVEL = 0.0035f
    private const val QUIET_LEVEL = 0.0015f

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

    fun isAudible(level: Float): Boolean = level >= AUDIBLE_LEVEL

    fun adaptiveProgress(timelineProgress: Float, outgoingLevel: Float, incomingLevel: Float): Float {
        if (!isAudible(incomingLevel)) return 0f
        val progress = timelineProgress.coerceIn(0f, 1f)
        if (outgoingLevel > QUIET_LEVEL) return progress
        // Once the outgoing waveform has reached its tail silence, move decisively to the audible
        // incoming track instead of spending the remaining fade window mixing silence.
        return max(progress, 0.72f)
    }

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

    fun compensatedHandoffPosition(
        auxiliaryPositionMs: Long,
        positionDriftMs: Long,
        measuredSeekLatencyMs: Long
    ): Long {
        val latencyLeadMs = if (positionDriftMs < 0L) measuredSeekLatencyMs.coerceAtLeast(0L) else 0L
        return (auxiliaryPositionMs + latencyLeadMs).coerceAtLeast(0L)
    }

    fun handoffBlendProgress(elapsedMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 1f
        return (elapsedMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}

/**
 * Applies only the temporary transition envelope to decoded PCM.
 *
 * ReplayGain and user volume remain owned by [Player.volume], so an interrupted transition cannot
 * persist a zero player volume into later tracks. This mirrors RawS-Music's separation between its
 * durable media volume and short-lived PCM fade envelope.
 */
@UnstableApi
internal class CrossfadeGainAudioProcessor : BaseAudioProcessor() {
    @Volatile
    private var currentGain = 1f

    var gain: Float
        get() = currentGain
        set(value) {
            currentGain = value.coerceIn(0f, 1f)
        }

    private var encoding: Int = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        return if (encoding in SUPPORTED_ENCODINGS) inputAudioFormat else AudioProcessor.AudioFormat.NOT_SET
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val output = replaceOutputBuffer(inputBuffer.remaining()).order(ByteOrder.LITTLE_ENDIAN)
        val input = inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val appliedGain = currentGain
        when (encoding) {
            C.ENCODING_PCM_FLOAT -> while (input.remaining() >= 4) {
                output.putFloat((input.float * appliedGain).coerceIn(-1f, 1f))
            }
            C.ENCODING_PCM_16BIT -> while (input.remaining() >= 2) {
                output.putShort((input.short.toInt() * appliedGain).toInt().coerceIn(-32_768, 32_767).toShort())
            }
            C.ENCODING_PCM_24BIT -> while (input.remaining() >= 3) {
                val raw = (input.get().toInt() and 0xff) or
                    ((input.get().toInt() and 0xff) shl 8) or
                    (input.get().toInt() shl 16)
                val scaled = (raw * appliedGain).toInt().coerceIn(-8_388_608, 8_388_607)
                output.put((scaled and 0xff).toByte())
                output.put(((scaled ushr 8) and 0xff).toByte())
                output.put(((scaled ushr 16) and 0xff).toByte())
            }
            C.ENCODING_PCM_32BIT -> while (input.remaining() >= 4) {
                val scaled = (input.int.toDouble() * appliedGain.toDouble())
                    .coerceIn(Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble())
                output.putInt(scaled.toInt())
            }
            C.ENCODING_PCM_8BIT -> while (input.hasRemaining()) {
                val centered = (input.get().toInt() and 0xff) - 128
                output.put((centered * appliedGain + 128f).toInt().coerceIn(0, 255).toByte())
            }
            else -> output.put(input)
        }
        while (input.hasRemaining()) output.put(input.get())
        output.flip()
    }

    override fun onReset() {
        // AudioSink.reset() is also called while preparing/replacing media. The coordinator owns
        // the envelope, so resetting it here could unexpectedly unmute the preloaded player.
        encoding = C.ENCODING_INVALID
    }

    private companion object {
        val SUPPORTED_ENCODINGS = setOf(
            C.ENCODING_PCM_8BIT,
            C.ENCODING_PCM_16BIT,
            C.ENCODING_PCM_24BIT,
            C.ENCODING_PCM_32BIT,
            C.ENCODING_PCM_FLOAT
        )
    }
}

@UnstableApi
internal class WaveformLevelAudioProcessor : BaseAudioProcessor() {
    @Volatile
    var level: Float = 0f
        private set

    private var encoding: Int = C.ENCODING_INVALID

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        encoding = inputAudioFormat.encoding
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        level = level * 0.68f + measurePeak(inputBuffer, encoding) * 0.32f
        val output = replaceOutputBuffer(inputBuffer.remaining())
        output.put(inputBuffer).flip()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onFlush() {
        level = 0f
    }

    override fun onReset() {
        level = 0f
        encoding = C.ENCODING_INVALID
    }

    private fun measurePeak(buffer: ByteBuffer, encoding: Int): Float {
        val sample = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        var peak = 0f
        when (encoding) {
            C.ENCODING_PCM_FLOAT -> while (sample.remaining() >= 4) {
                peak = max(peak, abs(sample.float).coerceAtMost(1f))
            }
            C.ENCODING_PCM_16BIT -> while (sample.remaining() >= 2) {
                peak = max(peak, abs(sample.short.toInt()) / 32768f)
            }
            C.ENCODING_PCM_24BIT -> while (sample.remaining() >= 3) {
                val raw = (sample.get().toInt() and 0xff) or
                    ((sample.get().toInt() and 0xff) shl 8) or
                    (sample.get().toInt() shl 16)
                peak = max(peak, abs(raw) / 8_388_608f)
            }
            C.ENCODING_PCM_32BIT -> while (sample.remaining() >= 4) {
                peak = max(peak, abs(sample.int.toLong()).toFloat() / 2_147_483_648f)
            }
        }
        return peak.coerceIn(0f, 1f)
    }
}
