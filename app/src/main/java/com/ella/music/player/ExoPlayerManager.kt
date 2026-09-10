package com.ella.music.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.util.LruCache
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionToken
import com.ella.music.data.AppLogStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.isMediaStoreAlbumArtworkUri
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume
import kotlin.random.Random

class ExoPlayerManager(private val context: Context) {
    internal data class MetadataWritePlaybackToken(
        val songKey: String,
        val positionMs: Long,
        val shouldResume: Boolean
    )

    private data class MetadataWritePauseRequest(
        val token: MetadataWritePlaybackToken,
        val controller: MediaController?
    )

    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playWhenReady = MutableStateFlow(false)
    val playWhenReady: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

    // A Song is not a unique queue occurrence: the same path can intentionally be queued more
    // than once. Keep the actual queue slot beside the current Song so the UI and transport
    // controls do not have to rediscover it by identity (which always picks the first duplicate).
    private val _currentQueueIndex = MutableStateFlow(-1)
    val currentQueueIndex: StateFlow<Int> = _currentQueueIndex.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _shuffleEnabled = MutableStateFlow(false)
    val shuffleEnabled: StateFlow<Boolean> = _shuffleEnabled.asStateFlow()

    private val _queueLocked = MutableStateFlow(false)
    val queueLocked: StateFlow<Boolean> = _queueLocked.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_ALL)
    val repeatMode: StateFlow<Int> = _repeatMode.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1f)
    val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _playbackPitch = MutableStateFlow(1f)
    val playbackPitch: StateFlow<Float> = _playbackPitch.asStateFlow()

    private var playlist = mutableListOf<Song>()
    private val queueEntrySequence = AtomicLong(0L)

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlistFlow: StateFlow<List<Song>> = _playlist.asStateFlow()
    private var playerListener: Player.Listener? = null
    private var lastQueueSaveMs = 0L
    private var lastStateSaveMs = 0L
    private var shuffleMode = SettingsManager.SHUFFLE_MODE_PSEUDO
    @Volatile
    private var reshuffleOnStartup = false
    @Volatile
    private var disableSequentialPlayback = false
    private var playNextMode = SettingsManager.PLAY_NEXT_MODE_REVERSE_STACK
    private var virtualPlaylistCurrentIndex: Int? = null
    private var pendingOptimisticSongKey: String? = null
    private var pendingOptimisticPreviousSongKey: String? = null
    // MediaSession forwarding (especially the crossfade presentation player) can report the
    // outgoing item once after the incoming item has already become authoritative. Keep that
    // outgoing identity as a short-lived guard so the resident player page does not flash back to
    // the previous cover/mini lyric before settling on the target.
    private var staleTransitionSongKey: String? = null
    private var confirmedTransitionSongKey: String? = null
    private var staleTransitionGuardUntilElapsedRealtime = 0L
    private var playWhenConnected = false
    private var pendingPlaylist: PendingPlaylist? = null
    private var reorderingPlaylistForShuffle = false
    private var suppressSongIdentityUntilElapsedRealtime = 0L
    private var playlistBeforeShuffle: List<Song>? = null
    // Keep the last materialized pseudo-random order independently from the temporary source
    // order used while shuffle is enabled. This is what lets random -> ordered -> random restore
    // the same queue instead of silently generating a new one (#633).
    private var lastShuffleQueue: List<Song>? = null
    private var lastShuffleSourceOrder: List<Song>? = null
    private var pendingShuffleReorder = false
    private var isRestoringSavedQueue = false
    private var playNextAnchorKey: String? = null
    private var playNextForwardCount = 0
    private var replayGainVolume = 1f
    private var resumePlaybackPositionEnabled = false
    private val perSongResumePositions = LinkedHashMap<String, Long>()
    private var externalSnapshotGuard: ExternalSnapshotGuard? = null

    private val persistenceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val artworkRepository = MusicRepository.getInstance(context)
    private val settingsManager = SettingsManager.getInstance(context)
    private val notificationArtworkCache = object : LruCache<String, ByteArray>(4 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size / 1024
    }
    private val missingNotificationArtworkKeys = mutableSetOf<String>()
    private var notificationArtworkJob: Job? = null
    private var deferredCurrentItemMetadataSong: Song? = null
    private var deferredCurrentItemMetadataMediaId: String? = null
    private var currentSongRefreshJob: Job? = null
    private var deferredSeekStateSaveJob: Job? = null
    private var deferredSeekCommandJob: Job? = null
    private var pendingSeekTargetMs: Long? = null
    // MediaController commands are asynchronous.  Keep the latest user transport intent for a
    // short acknowledgement window so an old isPlaying/playWhenReady callback (or a snapshot
    // emitted while a controller is being recreated) cannot put the UI back into the opposite
    // state.  The intent is deliberately short-lived: if a command really failed, the next
    // controller state is eventually allowed to become authoritative.
    private var pendingTransportTarget: Boolean? = null
    private var pendingTransportIssuedAtMs = 0L
    @Volatile
    private var playbackStateSaveGeneration = 0L
    private var decoderRecoveryJob: Job? = null
    private var autoDecoderRetrySongKey: String? = null
    private var artworkAppliedSongKey: String? = null
    private var sessionMetadataSongKey: String? = null
    private var bluetoothMetadataPatchState = MediaNotificationLyricPatchPolicy.onCleared()
    private var suppressExternalSnapshotsUntilMs = 0L
    private var presentationMetadataPatchSongKey: String? = null
    private var presentationMetadataPatchUntilMs = 0L
    private var startupMediaTransitionGuardUntilMs = 0L

    init {
        // Resolve queue-policy switches before the asynchronous MediaController can restore the
        // persisted queue. This avoids applying them one frame after a cold-start restore.
        runBlocking(Dispatchers.IO) {
            reshuffleOnStartup = settingsManager.shuffleReshuffleOnStartup.first()
            disableSequentialPlayback = settingsManager.disableSequentialPlayback.first()
        }
        _shuffleEnabled.value = loadAppShuffleEnabled()
        _repeatMode.value = loadAppRepeatMode()
        // Keep the player surface populated while MediaController reconnects on a cold process
        // start. The actual service state still wins once connected; this is only a persisted
        // visual snapshot, matching the no-flash restoration used by native players.
        seedSavedPlaybackPreview()
    }

    fun connect() {
        val sessionToken = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java)
        )
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture = future
        Futures.addCallback(
            future,
            object : FutureCallback<MediaController> {
                override fun onSuccess(result: MediaController?) {
                    if (controllerFuture !== future || result == null) {
                        result?.let { runCatching { it.release() } }
                        return
                    }
                    mediaController = result
                    setupListener()
                }

                override fun onFailure(t: Throwable) {
                    if (controllerFuture !== future) return
                    controllerFuture = null
                    AppLogStore.error(context, "PlayerController", "Failed to connect media controller", t)
                }
            },
            context.mainExecutor
        )
    }

    fun disconnect() {
        cancelPendingSeekCommand()
        playerListener?.let { mediaController?.removeListener(it) }
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        playerListener = null
        mediaController = null
        clearPresentationMetadataPatchGuard()
    }

    /**
     * Ensures the media controller is connected to the (possibly killed/recreated) playback
     * service. When the app is backgrounded for a while — especially over Bluetooth — the
     * system can tear down the session, leaving a stale, disconnected controller whose
     * commands are silently dropped. Call this on app foreground and before issuing commands.
     */
    fun ensureConnected(refreshStateIfConnected: Boolean = true) {
        val controller = mediaController
        if (controller != null && controller.isConnected) {
            if (refreshStateIfConnected) refreshStateFromController()
            return
        }
        // External playback snapshots can arrive in bursts while a new controller is still being
        // built. Do not release and recreate that in-flight future for every snapshot; doing so
        // produced repeated MediaController Init/Release cycles and let old queue states flash
        // through the player page during a track transition.
        if (controllerFuture?.isDone == false) return
        if (controller != null) disconnect()
        if (controllerFuture == null) connect()
    }

    private fun activeController(): MediaController? = mediaController?.takeIf { it.isConnected }

    private fun clearPendingTransportCommand() {
        pendingTransportTarget = null
        pendingTransportIssuedAtMs = 0L
    }

    private fun pendingTransportTargetOrNull(): Boolean? {
        val target = pendingTransportTarget ?: return null
        if (SystemClock.elapsedRealtime() - pendingTransportIssuedAtMs > TRANSPORT_COMMAND_GUARD_MS) {
            clearPendingTransportCommand()
            return null
        }
        return target
    }

    /**
     * Applies a transport snapshot without allowing it to contradict a just-issued user command.
     * A play request is projected for the short acknowledgement window because some vendor
     * builds start AudioTrack without dispatching the matching MediaController callback. Once the
     * command is acknowledged, the controller's actual `isPlaying` bit becomes authoritative.
     */
    private fun publishTransportState(isPlaying: Boolean, playWhenReady: Boolean) {
        val pending = pendingTransportTargetOrNull()
        val projection = projectTransportState(
            actualIsPlaying = isPlaying,
            actualPlayWhenReady = playWhenReady,
            pendingTarget = pending
        )
        _playWhenReady.value = projection.playWhenReady
        _isPlaying.value = projection.isPlaying
        if (pending != null && projection.acknowledged &&
            SystemClock.elapsedRealtime() - pendingTransportIssuedAtMs >= MIN_TRANSPORT_HOLD_MS
        ) {
            clearPendingTransportCommand()
        }
    }

    /** Issue one transport command and immediately publish its projected state to the UI. */
    private fun requestTransportState(target: Boolean, controller: MediaController?): Boolean {
        pendingTransportTarget = target
        pendingTransportIssuedAtMs = SystemClock.elapsedRealtime()
        _playWhenReady.value = target
        // Project both directions immediately.  In particular, a play command must not leave the
        // pause glyph stuck on "play" when the vendor AudioTrack starts before MediaController
        // delivers onIsPlayingChanged.
        _isPlaying.value = target
        Log.d(
            TIMING_TAG,
            "transport command target=$target connected=${controller?.isConnected == true} " +
                "controllerPlayWhenReady=${controller?.playWhenReady} controllerIsPlaying=${controller?.isPlaying}"
        )

        if (controller == null || !controller.isConnected) {
            // Reconnect on demand when a command arrives while the foreground controller is stale.
            // This is intentionally outside the external-snapshot collector; snapshots are a hot
            // stream and must never release/recreate the controller for every state change.
            ensureConnected(refreshStateIfConnected = false)
            // Keep the intent for the controller that will be created on the next foreground
            // pass.  A pending false is also meaningful: it must cancel a service that kept
            // playing while the UI controller was disconnected.
            playWhenConnected = target
            savePlaybackState(force = true)
            return target
        }

        playWhenConnected = false
        runCatching {
            if (target) controller.play() else controller.pause()
        }.onFailure { error ->
            AppLogStore.warn(
                context,
                "PlayerController",
                "Failed to request ${if (target) "play" else "pause"}",
                error
            )
        }
        savePlaybackState(force = true)
        return target
    }

    /** Retry a projected transport intent once a replacement controller is ready. */
    private fun reconcilePendingTransport(controller: MediaController) {
        val target = pendingTransportTargetOrNull() ?: return
        if (!controller.isConnected) return
        if (controller.playWhenReady == target) {
            playWhenConnected = false
            if (!target || controller.isPlaying) clearPendingTransportCommand()
            return
        }
        runCatching {
            if (target) controller.play() else controller.pause()
        }.onFailure { error ->
            AppLogStore.warn(
                context,
                "PlayerController",
                "Failed to reconcile ${if (target) "play" else "pause"} after reconnect",
                error
            )
        }
    }

    private fun clearPresentationMetadataPatchGuard() {
        presentationMetadataPatchSongKey = null
        presentationMetadataPatchUntilMs = 0L
    }

    private fun clearStaleTransitionGuard() {
        staleTransitionSongKey = null
        confirmedTransitionSongKey = null
        staleTransitionGuardUntilElapsedRealtime = 0L
    }

    private fun rememberAcceptedSongTransition(previousSongKey: String?, acceptedSong: Song?) {
        val acceptedKey = acceptedSong?.playbackStackKey()
        if (previousSongKey.isNullOrBlank() || acceptedKey.isNullOrBlank() || previousSongKey == acceptedKey) {
            clearStaleTransitionGuard()
            return
        }
        staleTransitionSongKey = previousSongKey
        confirmedTransitionSongKey = acceptedKey
        staleTransitionGuardUntilElapsedRealtime =
            SystemClock.elapsedRealtime() + STALE_TRANSITION_GUARD_MS
    }

    private fun rememberAcceptedSongTransition(previousSong: Song?, acceptedSong: Song?) {
        rememberAcceptedSongTransition(previousSong?.playbackStackKey(), acceptedSong)
    }

    private fun shouldIgnoreStaleTransition(restoredSong: Song?): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now >= staleTransitionGuardUntilElapsedRealtime) {
            if (staleTransitionGuardUntilElapsedRealtime != 0L) clearStaleTransitionGuard()
            return false
        }
        val currentKey = _currentSong.value?.playbackStackKey()
        val ignored = currentKey != null &&
            currentKey == confirmedTransitionSongKey &&
            restoredSong?.playbackStackKey() == staleTransitionSongKey
        if (ignored) {
            AppLogStore.debug(
                context,
                "PlayerTransition",
                "Ignored stale controller song=${restoredSong?.title.orEmpty()} " +
                    "while keeping=${_currentSong.value?.title.orEmpty()}"
            )
        }
        return ignored
    }

    fun isConnected(): Boolean = mediaController?.isConnected == true

    suspend fun recreatePlaybackService(
        // `isPlaying` is false during buffering and while a decoder is being rebuilt.  Using it
        // as the restart decision turned a settings-only change (audio focus/output) into an
        // unexpected pause. `playWhenReady` is the stable user intent and remains true across
        // those transient states.
        resumePlayback: Boolean = _playWhenReady.value || _isPlaying.value
    ) {
        withContext(Dispatchers.Main.immediate) {
            savePlaybackQueue(force = true)
            savePlaybackState(force = true)
            playWhenConnected = resumePlayback
            pendingTransportTarget = resumePlayback
            pendingTransportIssuedAtMs = SystemClock.elapsedRealtime()
            AppLogStore.info(context, "PlayerDecoder", "Recreate playback service for decoder change")

            disconnect()
            context.stopService(Intent(context, PlaybackService::class.java))
            playlist.clear()
            _playlist.value = emptyList()
            _currentQueueIndex.value = -1
            notificationArtworkJob?.cancel()
            notificationArtworkJob = null
            sessionMetadataSongKey = null
            artworkAppliedSongKey = null
            clearBluetoothMetadataPatchState()
            delay(650)
            connect()
        }
    }

    private fun setupListener() {
        val controller = mediaController ?: return
        val listener = object : Player.Listener {
            // A released MediaController can still have callbacks queued on the main looper.
            // Never let those callbacks inspect the replacement controller and overwrite the
            // state projected by a newer user command.
            private fun isCurrentController(): Boolean = mediaController === controller

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (!isCurrentController()) return
                publishTransportState(isPlaying, controller.playWhenReady)
                applyDeferredCurrentItemMetadataIfSafe(controller)
                savePlaybackState(force = true)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (!isCurrentController()) return
                publishTransportState(controller.isPlaying, playWhenReady)
                applyDeferredCurrentItemMetadataIfSafe(controller)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (!isCurrentController()) return
                _playbackState.value = playbackState
                _duration.value = controller.duration.coerceAtLeast(0)
                when (playbackState) {
                    Player.STATE_BUFFERING -> Log.d(TIMING_TAG, "controller state BUFFERING mediaId=${controller.currentMediaItem?.mediaId}")
                    Player.STATE_READY -> Log.d(TIMING_TAG, "controller state READY mediaId=${controller.currentMediaItem?.mediaId}")
                    Player.STATE_ENDED -> Log.d(TIMING_TAG, "controller state ENDED mediaId=${controller.currentMediaItem?.mediaId}")
                }
                applyDeferredCurrentItemMetadataIfSafe(controller)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                if (!isCurrentController()) return
                Log.d(TIMING_TAG, "controller media transition reason=$reason mediaId=${mediaItem?.mediaId}")
                externalSnapshotGuard = null
                clearPresentationMetadataPatchGuard()
                resetBluetoothMetadataPatchStateForSong(mediaItem?.toSongFromMediaItemExtras())
                val previousQueueLast = playlist.lastOrNull()
                val inStartupGuard = SystemClock.elapsedRealtime() < startupMediaTransitionGuardUntilMs
                if (
                    pendingShuffleReorder &&
                    !reorderingPlaylistForShuffle &&
                    reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED &&
                    !isRestoringSavedQueue &&
                    !inStartupGuard
                ) {
                    performPendingShuffleReorder(trigger = "transition", seekToNextAfterReorder = false)
                }
                if (
                    reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
                    _shuffleEnabled.value &&
                    controller.repeatMode == Player.REPEAT_MODE_ALL &&
                    !reorderingPlaylistForShuffle &&
                    !isRestoringSavedQueue &&
                    !inStartupGuard
                ) {
                    // Some Media3/OEM players report a loop as a media-item transition with
                    // reason REPEAT instead of the position-discontinuity callback handled
                    // below. Cover both paths so a shuffled round never starts with its previous
                    // final song (#634).
                    reshuffleAtCycleBoundary(controller, previousQueueLast)
                }
                applyDeferredCurrentItemMetadataAfterTransition(controller, mediaItem)
                updateCurrentSong()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                if (!isCurrentController()) return
                if (mediaMetadata.metadataPatchReason() == null) {
                    clearPresentationMetadataPatchGuard()
                    return
                }
                val song = controller.currentMediaItem?.toSongFromMediaItemExtras()
                    ?: _currentSong.value
                presentationMetadataPatchSongKey = song?.playbackStackKey()
                presentationMetadataPatchUntilMs =
                    SystemClock.elapsedRealtime() + PRESENTATION_METADATA_DISCONTINUITY_GUARD_MS
            }

            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int
            ) {
                if (!isCurrentController()) return
                val currentItem = controller.currentMediaItem
                val currentSong = _currentSong.value
                val nowMs = SystemClock.elapsedRealtime()
                // Replacing MediaMetadata for notification lyrics can surface as an internal
                // discontinuity with a transient position. It is not a seek and must not reset
                // the player page's progress or lyric timeline.
                val itemSong = currentItem?.toSongFromMediaItemExtras()
                val ignorePresentationDiscontinuity = shouldIgnorePresentationMetadataDiscontinuity(
                    reason = reason,
                    presentationSongKey = presentationMetadataPatchSongKey,
                    presentationGuardUntilMs = presentationMetadataPatchUntilMs,
                    itemSong = itemSong,
                    currentSong = currentSong,
                    nowMs = nowMs
                )
                val ignoreMarkedDiscontinuity = shouldIgnoreMetadataPatchDiscontinuity(
                    reason = reason,
                    isMetadataOnlyPatch = currentItem?.isMetadataOnlyPatch() == true,
                    itemSong = itemSong,
                    currentSong = currentSong
                )
                clearPresentationMetadataPatchGuard()
                if (ignorePresentationDiscontinuity || ignoreMarkedDiscontinuity) {
                    return
                }
                if (
                    reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION &&
                    _shuffleEnabled.value &&
                    controller.repeatMode == Player.REPEAT_MODE_ALL &&
                    oldPosition.mediaItemIndex == playlist.lastIndex &&
                    newPosition.mediaItemIndex == 0 &&
                    !isRestoringSavedQueue
                ) {
                    // A shuffled round should not simply loop back to its first item. Start a
                    // fresh round at the boundary, keeping the previous round's final song away
                    // from the new round's first slot (#634).
                    reshuffleAtCycleBoundary(controller, playlist.getOrNull(oldPosition.mediaItemIndex))
                    return
                }
                _currentPosition.value = newPosition.positionMs.coerceAtLeast(0L)
                _duration.value = controller.duration.coerceAtLeast(0)
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    updateCurrentSong()
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (!isCurrentController()) return
                if (reorderingPlaylistForShuffle) return
                // Artwork and base-session patches replace the current item's MediaMetadata via
                // replaceMediaItem without changing the actual playback queue. These trigger
                // onTimelineChanged with SOURCE_UPDATE, but the real playback state (isPlaying,
                // position, queue, current song) is unchanged. Skip the full refresh to avoid
                // spurious StateFlow emissions that flicker the lyrics page.
                if (shouldIgnoreDisplayOnlyTimelineUpdate(
                        reason = reason,
                        currentItem = controller.currentMediaItem,
                        currentSong = _currentSong.value
                    )
                ) return
                refreshStateFromController()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (!isCurrentController()) return
                if (isRestoringSavedQueue || SystemClock.elapsedRealtime() < startupMediaTransitionGuardUntilMs) {
                    if (shuffleModeEnabled) {
                        controller.shuffleModeEnabled = false
                    }
                    return
                }
                if (_queueLocked.value) {
                    if (shuffleModeEnabled) {
                        _shuffleEnabled.value = true
                        persistAppShuffleEnabled(true)
                        controller.shuffleModeEnabled = false
                    }
                    return
                }
                if (shuffleModeEnabled) {
                    _shuffleEnabled.value = true
                    persistAppShuffleEnabled(true)
                    if (!pendingShuffleReorder) {
                        markPendingShuffleReorder()
                    }
                    if (!pendingShuffleReorder) {
                        controller.shuffleModeEnabled = false
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                if (!isCurrentController()) return
                val normalizedRepeatMode = normalizeRepeatMode(repeatMode)
                if (repeatMode != normalizedRepeatMode) {
                    controller.repeatMode = normalizedRepeatMode
                    _repeatMode.value = normalizedRepeatMode
                    persistAppRepeatMode(normalizedRepeatMode)
                    return
                }
                _repeatMode.value = normalizedRepeatMode
                // The combined playback-mode button in the media notification changes the app-level
                // shuffle flag (persisted, not part of Media3 state) together with the repeat mode.
                // Re-read it here so the player page stays in sync with notification-driven changes.
                val persistedShuffle = loadAppShuffleEnabled()
                if (_shuffleEnabled.value != persistedShuffle) {
                    _shuffleEnabled.value = persistedShuffle
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                if (!isCurrentController()) return
                _playbackSpeed.value = playbackParameters.speed
                _playbackPitch.value = playbackParameters.pitch
            }

            override fun onPlayerError(error: PlaybackException) {
                if (!isCurrentController()) return
                val song = _currentSong.value
                AppLogStore.error(
                    context,
                    "PlayerError",
                    "Playback failed code=${error.errorCodeName} song=${song?.title.orEmpty()} uri=${controller.currentMediaItem?.localConfiguration?.uri}",
                    error
                )
                decoderRecoveryJob?.cancel()
                decoderRecoveryJob = persistenceScope.launch {
                    val recovered = song?.let { tryRecoverAutoDecoderPlayback(it) } == true
                    if (!recovered) {
                        withContext(Dispatchers.Main.immediate) {
                            skipToNext()
                        }
                    }
                }
            }
        }
        playerListener = listener
        controller.addListener(listener)
        // A recreated MediaController does not retain app-owned ReplayGain state.
        controller.volume = replayGainVolume

        val pending = pendingPlaylist
        if (pending != null) {
            pendingPlaylist = null
            setPlaylist(
                pending.songs,
                pending.startIndex,
                honorShuffle = pending.honorShuffle,
                resetQueueLock = pending.resetQueueLock
            )
        } else {
            isRestoringSavedQueue = true
            try {
                restoreSavedQueueIfNeeded()
                refreshStateFromController(controller)
            } finally {
                isRestoringSavedQueue = false
            }
        }
        val pendingTransport = pendingTransportTargetOrNull()
        if (pendingTransport != null) {
            reconcilePendingTransport(controller)
        } else if (playWhenConnected) {
            playWhenConnected = false
            requestTransportState(target = true, controller = controller)
        }
        applyDeferredCurrentItemMetadataIfSafe(controller)
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        setPlaylist(songs, startIndex, honorShuffle = true, resetQueueLock = true)
    }

    fun setPlaylistForShuffleAll(
        songs: List<Song>,
        startIndex: Int = 0,
        preserveOrder: Boolean = false
    ) {
        if (songs.isEmpty()) return
        _shuffleEnabled.value = true
        _repeatMode.value = Player.REPEAT_MODE_ALL
        persistAppShuffleEnabled(true)
        persistAppRepeatMode(Player.REPEAT_MODE_ALL)
        setPlaylist(
            songs,
            startIndex,
            honorShuffle = !preserveOrder,
            resetQueueLock = true
        )
        mediaController?.let { controller ->
            controller.shuffleModeEnabled = false
            controller.repeatMode = Player.REPEAT_MODE_ALL
        }
        savePlaybackQueue(force = true)
    }

    /** Replaces media URLs while preserving the user's current queue lock. */
    fun replacePlaylistPreservingQueueLock(songs: List<Song>, startIndex: Int = 0) {
        setPlaylist(songs, startIndex, honorShuffle = false, resetQueueLock = false)
    }

    private fun setPlaylist(
        songs: List<Song>,
        startIndex: Int,
        honorShuffle: Boolean,
        resetQueueLock: Boolean
    ) {
        if (songs.isEmpty()) return
        cancelPendingSeekCommand()
        if (resetQueueLock) _queueLocked.value = false
        val requestedIndex = startIndex.coerceIn(songs.indices)
        externalSnapshotGuard = null
        suppressExternalSnapshotsUntilMs = 0L
        AppLogStore.debug(context, "PlayerQueue", "setPlaylist size=${songs.size} start=$startIndex")
        virtualPlaylistCurrentIndex = null
        pendingOptimisticSongKey = null
        pendingOptimisticPreviousSongKey = null
        clearStaleTransitionGuard()
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
        clearShuffleReuseCache()
        resetPlayNextForwardStack()
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongKey = null
        artworkAppliedSongKey = null
        clearBluetoothMetadataPatchState()
        rememberCurrentSongResumePosition()
        // The whole queue is shipped to the playback service over Binder (~1MB transaction limit).
        // Libraries above the safe-mode threshold are reduced to a window around the chosen song.
        val prepared = preparePlaybackQueue(songs, requestedIndex, honorShuffle)
        val queueSongs = prepared.songs
        val safeIndex = prepared.startIndex
        val startPositionMs = queueSongs.getOrNull(safeIndex)?.let(::resumePositionFor) ?: 0L
        // Repeat-OFF is the app's sequential mode.  The old implementation replaced it with
        // repeat-all whenever a new queue was attached, so selecting a song or rebuilding the
        // queue silently cancelled sequential playback (#634).
        val requestedRepeatMode = normalizeRepeatMode(_repeatMode.value)
        playlistBeforeShuffle = prepared.sourceOrderBeforeShuffle
        if (prepared.sourceOrderBeforeShuffle != null) {
            lastShuffleQueue = queueSongs.toList()
            lastShuffleSourceOrder = prepared.sourceOrderBeforeShuffle.toList()
        }
        playlist.clear()
        playlist.addAll(queueSongs)
        _playlist.value = playlist.toList()

        val startSong = queueSongs.getOrNull(safeIndex)
        if (startSong != null) {
            applyOptimisticSong(startSong, startPositionMs, safeIndex)
        }
        suppressSongIdentityUntilElapsedRealtime = 0L

        val mediaItems = queueSongs.map(::songToMediaItem)
        val controller = activeController()
        if (controller == null) {
            // No live controller (first launch, or the session was torn down while backgrounded).
            // Reconnect and queue the request so it is applied once the controller is back, and
            // optimistically reflect the requested song in the UI right away.
            ensureConnected()
            pendingPlaylist = PendingPlaylist(
                songs = queueSongs,
                startIndex = safeIndex,
                honorShuffle = false,
                resetQueueLock = resetQueueLock
            )
            _repeatMode.value = requestedRepeatMode
            persistAppRepeatMode(requestedRepeatMode)
            savePlaybackQueue(force = true)
            return
        }

        controller.apply {
            repeatMode = requestedRepeatMode
            setMediaItems(mediaItems, safeIndex, startPositionMs)
            prepare()
        }
        _repeatMode.value = requestedRepeatMode
        persistAppRepeatMode(requestedRepeatMode)
        requestTransportState(target = true, controller = controller)
        updateCurrentSong()
        savePlaybackQueue(force = true)
    }

    private data class PreparedPlaybackQueue(
        val songs: List<Song>,
        val startIndex: Int,
        val sourceOrderBeforeShuffle: List<Song>?
    )

    private fun preparePlaybackQueue(
        songs: List<Song>,
        requestedIndex: Int,
        honorShuffle: Boolean
    ): PreparedPlaybackQueue {
        if (!honorShuffle || !_shuffleEnabled.value || songs.size <= 1) {
            val (queueSongs, safeIndex) = songs.windowedForController(requestedIndex)
            return PreparedPlaybackQueue(queueSongs, safeIndex, sourceOrderBeforeShuffle = null)
        }

        val currentSong = songs[requestedIndex]
        val shuffleSeed = if (shuffleMode == SettingsManager.SHUFFLE_MODE_TRUE_RANDOM) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            buildPseudoShuffleSeed(songs, currentSong)
        }
        val shuffledSongs = songs
            .filterIndexed { index, _ -> index != requestedIndex }
            .shuffled(Random(shuffleSeed))
        val shuffledQueue = listOf(currentSong) + shuffledSongs

        // Keep the original-order controller queue so turning shuffle off never tries to send a
        // pathologically large source library through the media-session Binder transaction.
        val (sourceWindow, _) = songs.windowedForController(requestedIndex)
        val (queueSongs, safeIndex) = shuffledQueue.windowedForController(0)
        return PreparedPlaybackQueue(queueSongs, safeIndex, sourceWindow)
    }

    fun playResolvedFromVirtualQueue(
        songs: List<Song>,
        currentIndex: Int,
        resolvedSong: Song,
        shouldPlay: Boolean = true
    ) {
        if (songs.isEmpty()) return
        cancelPendingSeekCommand()
        val safeIndex = currentIndex.coerceIn(songs.indices)
        externalSnapshotGuard = null
        suppressExternalSnapshotsUntilMs = 0L
        val queueSong = songs[safeIndex]
        val sourceAwareResolvedSong = if (resolvedSong.playbackSourceKey == null) {
            resolvedSong.copy(playbackSourceKey = queueSong.playbackSourceKey)
        } else {
            resolvedSong
        }
        AppLogStore.debug(context, "PlayerQueue", "playResolvedVirtual size=${songs.size} index=$currentIndex title=${sourceAwareResolvedSong.title}")
        virtualPlaylistCurrentIndex = safeIndex
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
        clearShuffleReuseCache()
        resetPlayNextForwardStack()
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongKey = null
        artworkAppliedSongKey = null
        clearBluetoothMetadataPatchState()
        rememberCurrentSongResumePosition()
        playlist.clear()
        playlist.addAll(songs.mapIndexed { index, song -> if (index == safeIndex) sourceAwareResolvedSong else song })
        _playlist.value = playlist.toList()
        _currentQueueIndex.value = safeIndex

        activeController()?.let { controller ->
            controller.setMediaItems(
                listOf(songToMediaItem(sourceAwareResolvedSong)),
                0,
                resumePositionFor(sourceAwareResolvedSong)
            )
            controller.prepare()
            requestTransportState(target = shouldPlay, controller = controller)
        }
        _currentSong.value = sourceAwareResolvedSong
        _duration.value = sourceAwareResolvedSong.duration
        savePlaybackQueue(force = true)
    }

    fun addToPlaylist(song: Song) {
        addToPlaylist(listOf(song))
    }

    fun addToPlaylist(songs: List<Song>) {
        if (_queueLocked.value || songs.isEmpty()) return
        val combined = playlist + songs
        if (combined.size > LARGE_LIBRARY_SAFE_MODE_THRESHOLD) {
            val currentIndex = currentQueueIndex(mediaController).coerceAtLeast(0).coerceIn(combined.indices)
            setPlaylist(combined, currentIndex, honorShuffle = false, resetQueueLock = false)
            return
        }
        virtualPlaylistCurrentIndex = null
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
        clearShuffleReuseCache()
        resetPlayNextForwardStack()
        AppLogStore.debug(context, "PlayerQueue", "addMany size=${songs.size}")
        playlist.addAll(songs)
        _playlist.value = playlist.toList()
        mediaController?.addMediaItems(songs.map(::songToMediaItem))
        if ((mediaController?.mediaItemCount ?: 0) == songs.size) {
            mediaController?.prepare()
        }
        savePlaybackQueue(force = true)
    }

    fun playNext(song: Song) {
        playNext(listOf(song))
    }

    fun playNext(songs: List<Song>) {
        if (_queueLocked.value || songs.isEmpty()) return
        val controller = mediaController
        val insertIndex = playNextInsertIndex(controller, songs.size)
        if (playlist.size + songs.size > LARGE_LIBRARY_SAFE_MODE_THRESHOLD) {
            val combined = playlist.toMutableList().apply { addAll(insertIndex, songs) }
            val currentIndex = currentQueueIndex(controller).coerceAtLeast(0).coerceIn(combined.indices)
            setPlaylist(combined, currentIndex, honorShuffle = false, resetQueueLock = false)
            return
        }
        virtualPlaylistCurrentIndex = null
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
        clearShuffleReuseCache()
        AppLogStore.debug(context, "PlayerQueue", "playNextMany size=${songs.size} index=$insertIndex mode=$playNextMode")
        playlist.addAll(insertIndex, songs)
        _playlist.value = playlist.toList()
        controller?.addMediaItems(insertIndex, songs.map(::songToMediaItem))
        if ((controller?.mediaItemCount ?: 0) == songs.size) {
            controller?.prepare()
        }
        savePlaybackQueue(force = true)
    }

    private fun playNextInsertIndex(controller: MediaController?, insertCount: Int): Int {
        val currentIndex = currentQueueIndex(controller)
        val anchorKey = currentSongQueueKey(controller, currentIndex)
        val baseIndex = (currentIndex + 1).coerceIn(0, playlist.size)
        if (playNextMode == SettingsManager.PLAY_NEXT_MODE_FORWARD_STACK && anchorKey != null) {
            if (playNextAnchorKey != anchorKey) {
                playNextAnchorKey = anchorKey
                playNextForwardCount = 0
            }
            val insertIndex = (baseIndex + playNextForwardCount).coerceIn(0, playlist.size)
            playNextForwardCount += insertCount
            return insertIndex
        }
        if (playNextMode != SettingsManager.PLAY_NEXT_MODE_FORWARD_STACK) {
            resetPlayNextForwardStack()
        }
        return baseIndex
    }

    private fun currentQueueIndex(controller: MediaController?): Int {
        val virtualIndex = virtualPlaylistCurrentIndex
        virtualIndex?.takeIf { it in playlist.indices }?.let { return it }
        val controllerIndex = controller?.currentMediaItemIndex ?: C.INDEX_UNSET
        if (controllerIndex in playlist.indices) return controllerIndex
        val publishedIndex = _currentQueueIndex.value
        if (publishedIndex in playlist.indices) return publishedIndex
        val currentSong = _currentSong.value
        val currentSongIndex = playlist.indexOfFirst { it.isSamePlaybackIdentity(currentSong) }
        return if (currentSongIndex >= 0) currentSongIndex else -1
    }

    private fun currentSongQueueKey(controller: MediaController?, currentIndex: Int): String? {
        val song = when {
            currentIndex in playlist.indices -> playlist[currentIndex]
            else -> controller?.currentMediaItem?.toSongFromMediaItemExtras()
                ?: controller?.currentMediaItem?.toSong()
                ?: _currentSong.value
        }
        return song?.playbackStackKey()
    }

    private fun resetPlayNextForwardStack() {
        playNextAnchorKey = null
        playNextForwardCount = 0
    }

    private fun clearPendingShuffleReorder(
        disableNativeShuffle: Boolean = true,
        clearOriginalOrder: Boolean = false
    ) {
        val plan = clearPendingShufflePlan(
            hasOriginalOrder = playlistBeforeShuffle != null,
            disableNativeShuffle = disableNativeShuffle,
            clearOriginalOrder = clearOriginalOrder
        )
        pendingShuffleReorder = plan.pending
        if (!plan.keepOriginalOrder) {
            playlistBeforeShuffle = null
        }
        if (disableNativeShuffle) {
            mediaController?.takeIf { it.shuffleModeEnabled }?.shuffleModeEnabled = false
        }
    }

    private fun reconcileNativeShuffleState(controller: MediaController) {
        if (isRestoringSavedQueue || SystemClock.elapsedRealtime() < startupMediaTransitionGuardUntilMs) {
            if (controller.shuffleModeEnabled) {
                controller.shuffleModeEnabled = false
            }
            return
        }
        val persistedShuffle = loadAppShuffleEnabled()
        if (_shuffleEnabled.value != persistedShuffle) {
            _shuffleEnabled.value = persistedShuffle
        }
        if (_queueLocked.value) {
            controller.takeIf { it.shuffleModeEnabled }?.shuffleModeEnabled = false
            return
        }
        if (!controller.shuffleModeEnabled) return

        if (shouldAdoptNativeShuffleAsPending(
                appShuffleEnabled = persistedShuffle,
                pending = pendingShuffleReorder,
                nativeShuffleEnabled = true,
                queueSize = playlist.size,
                hasVirtualQueue = virtualPlaylistCurrentIndex != null
            )
        ) {
            if (!markPendingShuffleReorder()) {
                controller.shuffleModeEnabled = false
            }
            return
        }

        if (!pendingShuffleReorder) {
            // Notification/media-button shuffle may be toggled while the manager is disconnected.
            // If there is no Halcyon pending reorder to own native shuffle, turn it off so the
            // app queue order and Media3 playback order cannot diverge indefinitely.
            controller.shuffleModeEnabled = false
        }
    }

    fun playQueueIndex(index: Int) {
        if (index !in playlist.indices) return
        cancelPendingSeekCommand()
        externalSnapshotGuard = null
        suppressExternalSnapshotsUntilMs = 0L
        pendingOptimisticSongKey = null
        pendingOptimisticPreviousSongKey = null
        clearStaleTransitionGuard()
        resetPlayNextForwardStack()
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = false)
        rememberCurrentSongResumePosition()
        val resumePosition = resumePositionFor(playlist[index])
        _currentQueueIndex.value = index
        mediaController?.seekTo(index, resumePosition)
        mediaController?.play()
        updateCurrentSong()
        savePlaybackQueue(force = true)
    }

    fun removeFromPlaylist(index: Int) {
        if (_queueLocked.value || index !in playlist.indices) return
        virtualPlaylistCurrentIndex = null
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
        clearShuffleReuseCache()
        resetPlayNextForwardStack()
        AppLogStore.debug(context, "PlayerQueue", "remove index=$index title=${playlist[index].title}")
        if (playlist.size == 1) {
            clearPlaylist()
            return
        }

        playlist.removeAt(index)
        _playlist.value = playlist.toList()
        mediaController?.let { controller ->
            if (index < controller.mediaItemCount) {
                controller.removeMediaItem(index)
            }
            if (controller.mediaItemCount > 0 && controller.currentMediaItemIndex == C.INDEX_UNSET) {
                controller.seekToDefaultPosition(index.coerceAtMost(controller.mediaItemCount - 1))
            }
            updateCurrentSong()
        } ?: run {
            _currentSong.value = playlist.firstOrNull()
            _currentQueueIndex.value = playlist.firstOrNull()?.let { 0 } ?: -1
            _duration.value = _currentSong.value?.duration ?: 0L
        }
        savePlaybackQueue(force = true)
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        if (_queueLocked.value || fromIndex !in playlist.indices || toIndex !in playlist.indices || fromIndex == toIndex) return
        virtualPlaylistCurrentIndex = null
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
        clearShuffleReuseCache()
        resetPlayNextForwardStack()
        val currentIndexBeforeMove = currentQueueIndex(null)
        val movedSong = playlist.removeAt(fromIndex)
        playlist.add(toIndex, movedSong)
        _playlist.value = playlist.toList()
        mediaController?.let { controller ->
            if (fromIndex < controller.mediaItemCount && toIndex < controller.mediaItemCount) {
                controller.moveMediaItem(fromIndex, toIndex)
            }
            updateCurrentSong()
        } ?: run {
            val currentIndexAfterMove = adjustedQueueIndexAfterMove(
                currentIndex = currentIndexBeforeMove,
                fromIndex = fromIndex,
                toIndex = toIndex,
                queueSize = playlist.size
            ).takeIf { it in playlist.indices }
            val fallbackIndex = currentIndexAfterMove
                ?: playlist.indexOfFirst { it.isSamePlaybackIdentity(_currentSong.value) }
                    .takeIf { it >= 0 }
                ?: playlist.indices.firstOrNull()
            _currentQueueIndex.value = fallbackIndex ?: -1
            _currentSong.value = fallbackIndex?.let(playlist::get)
            _duration.value = _currentSong.value?.duration ?: 0L
        }
        savePlaybackQueue(force = true)
    }

    /**
     * Materializes a new visible queue order. Unlike playback shuffle this deliberately changes
     * the queue itself, while retaining the currently playing media item and its position.
     */
    fun randomizePlaylistOrder(): Boolean {
        if (_queueLocked.value || playlist.size < 2 || virtualPlaylistCurrentIndex != null) return false
        val controller = activeController() ?: return false
        val previousOrder = playlist.toList()
        val currentIndexBeforeShuffle = currentQueueIndex(controller)
            .takeIf { it in playlist.indices }
            ?: return false
        // Keep the original occurrence index alongside each Song. Two queue entries can have
        // identical identities; carrying the index is what prevents the first duplicate from
        // becoming the new current item after a materialized shuffle.
        val indexedPlaylist = playlist.withIndex().toMutableList()
        val shuffled = mutableListOf<Song>()
        // A new seed on each invocation avoids pseudo-shuffle returning the same list twice.
        val random = Random(SystemClock.elapsedRealtimeNanos())
        var attempts = 0
        do {
            indexedPlaylist.shuffle(random)
            shuffled.clear()
            shuffled.addAll(indexedPlaylist.map { it.value })
            attempts++
        } while (shuffled == playlist && attempts < 8)
        // A two-item queue has a 50% chance of returning unchanged. Guarantee a visible reorder
        // whenever there is more than one item, including duplicate Song identities.
        if (shuffled == playlist) {
            Collections.rotate(indexedPlaylist, 1)
            shuffled.clear()
            shuffled.addAll(indexedPlaylist.map { it.value })
        }
        val currentIndex = indexedPlaylist.indexOfFirst { it.index == currentIndexBeforeShuffle }
        if (currentIndex < 0) return false
        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying
        reorderingPlaylistForShuffle = true
        try {
            clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
            clearShuffleReuseCache()
            // Random order is a concrete queue operation, not a playback-mode toggle. Preserve
            // the user's shuffle preference while Media3 itself remains in ordered traversal.
            controller.shuffleModeEnabled = false
            resetPlayNextForwardStack()
            applyControllerPlaylistOrder(controller, shuffled, currentIndex, positionMs, wasPlaying)
            playlist.clear()
            playlist.addAll(shuffled)
            _playlist.value = shuffled
            lastShuffleQueue = shuffled.toList()
            lastShuffleSourceOrder = previousOrder
            updateCurrentSong()
            savePlaybackQueue(force = true)
            return true
        } finally {
            reorderingPlaylistForShuffle = false
        }
    }

    fun clearPlaylist() {
        if (_queueLocked.value) return
        cancelPendingSeekCommand()
        externalSnapshotGuard = null
        suppressExternalSnapshotsUntilMs = SystemClock.elapsedRealtime() + CLEAR_EXTERNAL_SNAPSHOT_SUPPRESSION_MS
        currentSongRefreshJob?.cancel()
        currentSongRefreshJob = null
        virtualPlaylistCurrentIndex = null
        pendingOptimisticSongKey = null
        pendingOptimisticPreviousSongKey = null
        clearStaleTransitionGuard()
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
        clearShuffleReuseCache()
        resetPlayNextForwardStack()
        playlist.clear()
        _playlist.value = emptyList()
        _currentSong.value = null
        _currentQueueIndex.value = -1
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongKey = null
        artworkAppliedSongKey = null
        clearBluetoothMetadataPatchState()
        _currentPosition.value = 0L
        _duration.value = 0L
        requestTransportState(target = false, controller = activeController())
        _playbackState.value = Player.STATE_IDLE
        autoDecoderRetrySongKey = null
        _queueLocked.value = false
        mediaController?.run {
            stop()
            clearMediaItems()
        }
        clearSavedQueue()
    }

    fun toggleQueueLock() {
        setQueueLocked(!_queueLocked.value)
    }

    /**
     * Freezes the current playback queue. A pending pseudo-shuffle is materialized before the
     * lock is applied, so the queue shown to the user is also the order that will keep playing.
     */
    fun setQueueLocked(locked: Boolean) {
        if (_queueLocked.value == locked) return
        if (locked) {
            performPendingShuffleReorder(trigger = "queueLock", seekToNextAfterReorder = false)
            clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = false)
        }
        _queueLocked.value = locked
        savePlaybackQueue(force = true)
    }

    fun playSong(song: Song) {
        // A direct play request for the already-playing identity must keep its actual queue
        // occurrence.  Searching by identity alone would jump from the second duplicate back to
        // the first one.
        val index = if (song.isSamePlaybackIdentity(_currentSong.value)) {
            currentQueueIndex(mediaController)
        } else {
            playlist.indexOfFirst { it.isSamePlaybackIdentity(song) }
        }
        if (index >= 0) {
            playQueueIndex(index)
        } else {
            setPlaylist(listOf(song), 0)
        }
    }

    fun togglePlayPause() {
        flushPendingSeekCommand()
        val controller = activeController()
        // `isPlaying` is false while buffering and during a crossfade handoff even though the
        // user still intends playback to continue. Toggling that transient bit is what turned a
        // pause tap into a play command. `playWhenReady` is the stable transport intent instead.
        val currentIntent = pendingTransportTargetOrNull()
            ?: controller?.playWhenReady
            ?: _playWhenReady.value
        requestTransportState(target = !currentIntent, controller = controller)
    }

    /**
     * Live playback position straight from the controller. Must be called on the main
     * thread (the controller's looper). The [currentPosition] StateFlow is only sampled
     * periodically, so remote readers (MCP) get a stale value mid-track without this.
     */
    fun livePositionMs(): Long =
        mediaController?.currentPosition?.coerceAtLeast(0) ?: _currentPosition.value

    fun play() {
        val controller = activeController()
        if (controller == null) {
            requestTransportState(target = true, controller = null)
            return
        }
        if (controller.mediaItemCount > 0) {
            requestTransportState(target = true, controller = controller)
            refreshStateFromController(controller)
        }
    }

    fun pause() {
        flushPendingSeekCommand()
        requestTransportState(target = false, controller = activeController())
    }

    internal suspend fun pauseForMetadataWrite(song: Song): MetadataWritePlaybackToken? {
        val request = withContext(Dispatchers.Main.immediate) {
            val current = _currentSong.value ?: return@withContext null
            if (!current.isSamePlaybackIdentity(song)) return@withContext null

            val controller = mediaController
            val controllerIntendsPlayback = controller?.playWhenReady == true || controller?.isPlaying == true
            val shouldResume = controllerIntendsPlayback ||
                (pendingTransportTargetOrNull() ?: (_playWhenReady.value || _isPlaying.value))
            val token = MetadataWritePlaybackToken(
                songKey = current.playbackStackKey(),
                positionMs = controller?.currentPosition?.coerceAtLeast(0L)
                    ?: _currentPosition.value.coerceAtLeast(0L),
                shouldResume = shouldResume
            )
            if (shouldResume) {
                if (controller?.isConnected != true) {
                    throw IllegalStateException("播放器尚未连接，无法安全写入当前歌曲")
                }
                requestTransportState(target = false, controller = controller)
            }
            MetadataWritePauseRequest(token, controller)
        } ?: return null

        if (!request.token.shouldResume) return request.token
        val controller = request.controller
            ?: throw IllegalStateException("播放器尚未连接，无法安全写入当前歌曲")
        val paused = try {
            withContext(Dispatchers.Main.immediate) {
                awaitPlayWhenReady(controller, target = false)
            }
        } catch (error: Throwable) {
            restorePlaybackAfterMetadataPauseFailure(controller)
            throw error
        }
        if (!paused) {
            restorePlaybackAfterMetadataPauseFailure(controller)
            throw IllegalStateException("暂停当前歌曲超时，已取消歌词写入")
        }
        return request.token
    }

    internal suspend fun resumeAfterMetadataWrite(token: MetadataWritePlaybackToken?) {
        if (token?.shouldResume != true) return
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            val controller = mediaController ?: return@withContext
            val current = _currentSong.value ?: return@withContext
            if (!controller.isConnected || current.playbackStackKey() != token.songKey) return@withContext
            if (controller.playWhenReady) return@withContext
            controller.seekTo(token.positionMs)
            requestTransportState(target = true, controller = controller)
        }
    }

    private suspend fun awaitPlayWhenReady(controller: MediaController, target: Boolean): Boolean {
        if (!controller.isConnected) return false
        if (controller.playWhenReady == target) return true
        return withTimeoutOrNull(2_000L) {
            suspendCancellableCoroutine { continuation ->
                val listener = object : Player.Listener {
                    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                        if (playWhenReady != target || !continuation.isActive) return
                        controller.removeListener(this)
                        continuation.resume(true)
                    }
                }
                continuation.invokeOnCancellation { controller.removeListener(listener) }
                controller.addListener(listener)
                if (controller.playWhenReady == target && continuation.isActive) {
                    controller.removeListener(listener)
                    continuation.resume(true)
                }
            }
        } ?: false
    }

    private suspend fun restorePlaybackAfterMetadataPauseFailure(controller: MediaController) {
        withContext(NonCancellable + Dispatchers.Main.immediate) {
            if (mediaController === controller && controller.isConnected && !controller.playWhenReady) {
                requestTransportState(target = true, controller = controller)
            }
        }
    }

    fun skipToNext(autoPlayIfPaused: Boolean = false) {
        val controller = mediaController ?: return
        val startPlayback = autoPlayIfPaused && !controller.playWhenReady
        cancelPendingSeekCommand()
        rememberCurrentSongResumePosition()
        // Enabling shuffle is initially bridged through Media3 so notification/headset commands
        // can still be received. If this UI command materializes that pending reorder, the
        // reorder itself must also perform the single next transition. Running the normal
        // adjacent-item path afterwards used the stale pre-reorder index and could skip twice or
        // leave the page on the previous album while audio had already advanced.
        if (performPendingShuffleReorder(trigger = "skipNext", seekToNextAfterReorder = true)) {
            if (startPlayback) requestTransportState(target = true, controller = controller)
            savePlaybackQueue(force = true)
            return
        }
        if (!seekAdjacentPlaylistItem(controller, offset = 1, startPositionMs = { 0L })) {
            controller.seekToNextMediaItem()
        }
        if (startPlayback) requestTransportState(target = true, controller = controller)
        savePlaybackQueue(force = true)
    }

    fun skipToPrevious(autoPlayIfPaused: Boolean = false) {
        val controller = mediaController ?: return
        val startPlayback = autoPlayIfPaused && !controller.playWhenReady
        cancelPendingSeekCommand()
        rememberCurrentSongResumePosition()
        if (!seekAdjacentPlaylistItem(controller, offset = -1, startPositionMs = { song -> resumePositionFor(song) })) {
            controller.seekToPreviousMediaItem()
            scheduleCurrentSongRefresh()
        }
        if (startPlayback) requestTransportState(target = true, controller = controller)
        savePlaybackQueue(force = true)
    }

    private fun seekAdjacentPlaylistItem(
        controller: MediaController,
        offset: Int,
        startPositionMs: (Song) -> Long
    ): Boolean {
        if (virtualPlaylistCurrentIndex != null) return false
        val wrap = controller.repeatMode != Player.REPEAT_MODE_OFF
        val fromIndex = currentQueueIndex(controller).takeIf { it in playlist.indices }
            ?: playlist.indexOfFirst { it.isSamePlaybackIdentity(_currentSong.value) }
                .takeIf { it >= 0 }
            ?: controller.currentMediaItemIndex
        val targetIndex = adjacentPlaylistIndex(
            currentIndex = fromIndex,
            offset = offset,
            queueSize = playlist.size,
            wrap = wrap
        ) ?: return false
        val target = playlist[targetIndex]
        val positionMs = startPositionMs(target)
        applyOptimisticSong(target, positionMs, targetIndex)
        controller.seekTo(targetIndex, positionMs)
        return true
    }

    private fun applyOptimisticSong(song: Song, positionMs: Long, queueIndex: Int? = null) {
        currentSongRefreshJob?.cancel()
        pendingOptimisticPreviousSongKey = _currentSong.value?.playbackStackKey()
        clearStaleTransitionGuard()
        pendingOptimisticSongKey = song.playbackStackKey()
        _currentSong.value = song
        queueIndex?.takeIf { it in playlist.indices }?.let { _currentQueueIndex.value = it }
        _currentPosition.value = positionMs.coerceAtLeast(0L)
        _duration.value = song.duration.coerceAtLeast(0L)
    }

    fun restartCurrent(autoPlayIfPaused: Boolean = false) {
        cancelPendingSeekCommand()
        pendingOptimisticSongKey = null
        pendingOptimisticPreviousSongKey = null
        clearStaleTransitionGuard()
        activeController()?.run {
            seekToDefaultPosition(currentMediaItemIndex.coerceAtLeast(0))
            if (playWhenReady || autoPlayIfPaused) {
                requestTransportState(target = true, controller = this)
            }
        }
        _currentPosition.value = 0L
        updateCurrentSong()
        savePlaybackState(force = true)
    }

    fun restartSong(song: Song?) {
        val controller = activeController() ?: return
        cancelPendingSeekCommand()
        val target = song ?: _currentSong.value
        val targetIndex = if (target != null && target.isSamePlaybackIdentity(_currentSong.value)) {
            currentQueueIndex(controller)
        } else {
            target?.let { current -> playlist.indexOfFirst { it.isSamePlaybackIdentity(current) } } ?: -1
        }
        val safeIndex = targetIndex.takeIf { it >= 0 } ?: controller.currentMediaItemIndex
        if (safeIndex < 0) return
        _currentQueueIndex.value = safeIndex
        controller.seekToDefaultPosition(safeIndex)
        requestTransportState(target = true, controller = controller)
        _currentPosition.value = 0L
        updateCurrentSong()
        savePlaybackQueue(force = true)
        savePlaybackState(force = true)
    }

    private fun scheduleCurrentSongRefresh() {
        currentSongRefreshJob?.cancel()
        currentSongRefreshJob = persistenceScope.launch {
            delay(150L)
            withContext(Dispatchers.Main.immediate) {
                refreshStateFromController()
            }
        }
    }

    fun seekTo(positionMs: Long): Long? {
        val controller = mediaController ?: return null
        val target = playbackSeekTarget(positionMs, controller.duration)
        _currentPosition.value = target
        enqueueSeekCommand(target)
        scheduleSeekStateSave(target)
        return target
    }

    fun seekToProgress(progress: Float, fallbackDurationMs: Long): Long? {
        val controller = mediaController ?: return null
        val target = playbackSeekTargetForProgress(
            progress = progress,
            playerDurationMs = controller.duration,
            fallbackDurationMs = fallbackDurationMs
        ) ?: return null
        _currentPosition.value = target
        if (controller.duration > 0L) _duration.value = controller.duration
        enqueueSeekCommand(target)
        scheduleSeekStateSave(target)
        return target
    }

    fun toggleShuffle() {
        val nextShuffle = !_shuffleEnabled.value
        val currentRepeat = mediaController?.repeatMode ?: _repeatMode.value
        val nextRepeat = if (disableSequentialPlayback && currentRepeat == Player.REPEAT_MODE_OFF) {
            Player.REPEAT_MODE_ALL
        } else {
            currentRepeat
        }
        applyPlaybackMode(
            shuffle = nextShuffle,
            repeatMode = nextRepeat,
            reorderForShuffleChange = true
        )
    }

    fun setShuffleMode(mode: Int) {
        shuffleMode = mode.coerceIn(
            SettingsManager.SHUFFLE_MODE_PSEUDO,
            SettingsManager.SHUFFLE_MODE_TRUE_RANDOM
        )
    }

    fun setShuffleReshuffleOnStartup(enabled: Boolean) {
        reshuffleOnStartup = enabled
    }

    fun setDisableSequentialPlayback(enabled: Boolean) {
        disableSequentialPlayback = enabled
        if (enabled && _repeatMode.value == Player.REPEAT_MODE_OFF) {
            applyPlaybackMode(
                shuffle = _shuffleEnabled.value,
                repeatMode = Player.REPEAT_MODE_ALL,
                reorderForShuffleChange = false
            )
        }
    }

    fun setPlayNextMode(mode: Int) {
        playNextMode = mode.coerceIn(
            SettingsManager.PLAY_NEXT_MODE_REVERSE_STACK,
            SettingsManager.PLAY_NEXT_MODE_FORWARD_STACK
        )
        resetPlayNextForwardStack()
    }

    fun setResumePlaybackPositionEnabled(enabled: Boolean) {
        resumePlaybackPositionEnabled = enabled
        if (!enabled) perSongResumePositions.clear()
    }

    private fun rememberCurrentSongResumePosition() {
        if (!resumePlaybackPositionEnabled) return
        val controller = mediaController ?: return
        val song = _currentSong.value ?: resolveCurrentPlaybackSong(controller) ?: return
        val position = controller.currentPosition.coerceAtLeast(0L)
        val duration = controller.duration.takeIf { it > 0L } ?: song.duration
        val key = song.playbackStackKey()
        if (position < RESUME_POSITION_MIN_MS ||
            (duration > 0L && duration - position < RESUME_POSITION_END_GUARD_MS)
        ) {
            perSongResumePositions.remove(key)
            return
        }
        perSongResumePositions[key] = position
        trimResumePositions()
    }

    private fun resumePositionFor(song: Song): Long {
        if (!resumePlaybackPositionEnabled) return 0L
        val position = perSongResumePositions[song.playbackStackKey()] ?: return 0L
        val duration = song.duration
        return if (duration > 0L) {
            position.coerceIn(0L, (duration - SEEK_END_GUARD_MS).coerceAtLeast(0L))
        } else {
            position.coerceAtLeast(0L)
        }
    }

    private fun trimResumePositions() {
        while (perSongResumePositions.size > MAX_RESUME_POSITION_ENTRIES) {
            val firstKey = perSongResumePositions.keys.firstOrNull() ?: return
            perSongResumePositions.remove(firstKey)
        }
    }

    fun toggleRepeat() {
        val current = mediaController?.repeatMode ?: _repeatMode.value
        val next = when (current) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> if (disableSequentialPlayback) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
        }
        applyPlaybackMode(
            shuffle = _shuffleEnabled.value,
            repeatMode = next,
            reorderForShuffleChange = false
        )
    }

    fun cyclePlaybackMode() {
        val controller = mediaController ?: return
        val currentShuffle = _shuffleEnabled.value
        val currentRepeat = controller.repeatMode
        val (nextShuffle, nextRepeat) = when {
            disableSequentialPlayback && currentShuffle -> false to Player.REPEAT_MODE_ALL
            disableSequentialPlayback && currentRepeat == Player.REPEAT_MODE_ALL -> false to Player.REPEAT_MODE_ONE
            disableSequentialPlayback -> true to Player.REPEAT_MODE_ALL
            currentShuffle -> false to Player.REPEAT_MODE_OFF
            currentRepeat == Player.REPEAT_MODE_OFF -> false to Player.REPEAT_MODE_ALL
            currentRepeat == Player.REPEAT_MODE_ALL -> false to Player.REPEAT_MODE_ONE
            else -> true to Player.REPEAT_MODE_ALL
        }
        applyPlaybackMode(
            shuffle = nextShuffle,
            repeatMode = nextRepeat,
            reorderForShuffleChange = nextShuffle != currentShuffle
        )
    }

    fun applyExternalPlaybackMode(shuffle: Boolean, repeatMode: Int) {
        val needsQueueReorder = shuffle != _shuffleEnabled.value ||
            (shuffle && playlistBeforeShuffle == null) ||
            (!shuffle && playlistBeforeShuffle != null)
        applyPlaybackMode(
            shuffle = shuffle,
            repeatMode = if (disableSequentialPlayback && repeatMode == Player.REPEAT_MODE_OFF) {
                Player.REPEAT_MODE_ALL
            } else {
                repeatMode
            },
            reorderForShuffleChange = needsQueueReorder
        )
    }

    private fun applyPlaybackMode(
        shuffle: Boolean,
        repeatMode: Int,
        reorderForShuffleChange: Boolean
    ) {
        val controller = mediaController ?: return
        val effectiveRepeatMode = normalizeRepeatMode(repeatMode)
        val previousShuffle = _shuffleEnabled.value
        val queueOrderCanChange = reorderForShuffleChange && !_queueLocked.value
        var keepNativeShuffleUntilReorder = pendingShuffleReorder && shuffle && !_queueLocked.value
        if (queueOrderCanChange) {
            if (shuffle) {
                if (!previousShuffle || playlistBeforeShuffle == null) {
                    // Re-entering random mode after a temporary ordered mode should reuse the
                    // previous concrete queue when it still describes this exact set of queue
                    // occurrences. A newly selected library/queue has no reusable cache and keeps
                    // the existing deferred materialization behavior.
                    val reused = !previousShuffle && reuseLastShuffleQueueKeepingCurrent(controller)
                    keepNativeShuffleUntilReorder = !reused && markPendingShuffleReorder()
                }
            } else {
                if (pendingShuffleReorder) {
                    clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
                } else {
                    // The native shuffle cursor must be disabled before moving the timeline back
                    // to source order. Leaving it on while issuing moveMediaItem calls lets
                    // Media3 select a different current occurrence and makes the next command
                    // appear to repeat the previous album.
                    controller.shuffleModeEnabled = false
                    restorePlaylistOrderAfterShuffle()
                }
                keepNativeShuffleUntilReorder = false
            }
        }
        _shuffleEnabled.value = shuffle
        persistAppShuffleEnabled(shuffle)
        persistAppRepeatMode(effectiveRepeatMode)
        controller.shuffleModeEnabled = keepNativeShuffleUntilReorder
        // Media3 reports repeat changes asynchronously. Publish the requested mode immediately
        // so the player page cannot render the previous icon for one frame after a tap.
        _repeatMode.value = effectiveRepeatMode
        if (controller.repeatMode != effectiveRepeatMode) {
            controller.repeatMode = effectiveRepeatMode
        }
        val syncCommandArgs = Bundle().apply {
            putBoolean(PlaybackService.EXTRA_PLAYBACK_MODE_SHUFFLE, shuffle)
            putInt(PlaybackService.EXTRA_PLAYBACK_MODE_REPEAT, effectiveRepeatMode)
        }
        runCatching {
            controller.sendCustomCommand(
                SessionCommand(PlaybackService.ACTION_SYNC_PLAYBACK_MODE, Bundle.EMPTY),
                syncCommandArgs
            )
        }
        savePlaybackQueue(force = true)
    }

    fun setPlaybackParameters(speed: Float, pitch: Float) {
        val safeSpeed = speed.coerceIn(0.5f, 2f)
        val safePitch = pitch.coerceIn(0.5f, 2f)
        mediaController?.playbackParameters = PlaybackParameters(safeSpeed, safePitch)
        _playbackSpeed.value = safeSpeed
        _playbackPitch.value = safePitch
        savePlaybackState()
    }

    fun setReplayGainVolume(volume: Float) {
        replayGainVolume = volume.coerceIn(0f, 1f)
        mediaController?.volume = replayGainVolume
    }

    fun updatePosition() {
        if (_currentSong.value == null && (mediaController?.mediaItemCount ?: 0) > 0) {
            refreshStateFromController()
        }
        mediaController?.let { controller ->
            if (mediaController === controller && controller.isConnected) {
                // A few vendor builds update AudioTrack without dispatching the matching
                // MediaController listener callback. The existing 10 Hz position ticker is a
                // reliable, low-cost reconciliation point for the play/pause state.
                publishTransportState(controller.isPlaying, controller.playWhenReady)
                _currentPosition.value = controller.currentPosition.coerceAtLeast(0)
                _duration.value = controller.duration.coerceAtLeast(0)
            }
        }
        if (_currentSong.value != null) savePlaybackState()
    }

    fun updateBluetoothLyric(text: String?, secondaryText: String? = null, force: Boolean = false): Boolean {
        val controller = mediaController ?: return true
        val song = _currentSong.value ?: return true
        val index = controller.currentMediaItemIndex

        if (index < 0 || index >= controller.mediaItemCount) return true

        val currentItem = controller.currentMediaItem ?: return true
        if (!currentItem.matchesSong(song)) {
            clearBluetoothMetadataPatchState()
            return true
        }
        val lyricText = text?.takeIf { it.isNotBlank() }
        val lyricSecondaryText = secondaryText?.takeIf { it.isNotBlank() }
        val payload = MediaNotificationLyricPayload(lyricText, lyricSecondaryText)
        val songKey = song.playbackStackKey()

        val decision = MediaNotificationLyricPatchPolicy.actionFor(
            state = bluetoothMetadataPatchState,
            songKey = songKey,
            payload = payload,
            nowMs = SystemClock.elapsedRealtime(),
            force = force
        )
        when (decision.action) {
            MediaNotificationLyricPatchAction.Defer -> return false
            MediaNotificationLyricPatchAction.Skip -> return true
            MediaNotificationLyricPatchAction.Patch,
            MediaNotificationLyricPatchAction.RestoreSongMetadata -> Unit
        }

        val commandArgs = Bundle().apply {
            putString(PlaybackService.EXTRA_NOTIFICATION_LYRIC_SONG_KEY, songKey)
            lyricText?.let { putString(PlaybackService.EXTRA_NOTIFICATION_LYRIC_TEXT, it) }
            lyricSecondaryText?.let {
                putString(PlaybackService.EXTRA_NOTIFICATION_LYRIC_SECONDARY_TEXT, it)
            }
        }
        runCatching {
            controller.sendCustomCommand(
                SessionCommand(PlaybackService.ACTION_UPDATE_NOTIFICATION_LYRIC, Bundle.EMPTY),
                commandArgs
            )
        }.onFailure { error ->
            Log.w(TIMING_TAG, "media notification lyric presentation update failed", error)
            return false
        }
        bluetoothMetadataPatchState = if (lyricText == null) {
            MediaNotificationLyricPatchPolicy.onCleared()
        } else {
            MediaNotificationLyricPatchPolicy.onPatched(songKey, payload, SystemClock.elapsedRealtime())
        }
        Log.d(
            TIMING_TAG,
            "media notification lyric presentation ${if (lyricText == null) "cleared" else "updated"} mediaId=${song.id}"
        )
        return true
    }

    fun clearBluetoothLyric() {
        updateBluetoothLyric(null)
    }
    fun refreshStateFromController() {
        mediaController?.let(::refreshStateFromController)
    }

    private fun refreshStateFromController(controller: MediaController) {
        if (mediaController !== controller) return
        hydratePlaylistFromController(controller)
        if (shouldIgnoreStaleControllerSong(controller)) return

        publishTransportState(controller.isPlaying, controller.playWhenReady)
        _playbackState.value = controller.playbackState
        val normalizedRepeatMode = normalizeRepeatMode(controller.repeatMode)
        if (controller.repeatMode != normalizedRepeatMode) {
            controller.repeatMode = normalizedRepeatMode
        }
        _repeatMode.value = normalizedRepeatMode
        _playbackSpeed.value = controller.playbackParameters.speed
        _playbackPitch.value = controller.playbackParameters.pitch
        _currentPosition.value = controller.currentPosition.coerceAtLeast(0)
        _duration.value = controller.duration.coerceAtLeast(0)

        reconcileNativeShuffleState(controller)
        updateCurrentSong()
    }

    private fun hydratePlaylistFromController(controller: MediaController) {
        val mediaItemCount = controller.mediaItemCount
        if (mediaItemCount <= 0 || playlist.isNotEmpty()) {
            if (_playlist.value != playlist) _playlist.value = playlist.toList()
            return
        }
        val saved = loadSavedQueue()
        val currentItemSong = controller.currentMediaItem?.toSongFromMediaItemExtras()
            ?: controller.currentMediaItem?.toSong()
        val savedCurrentIndex = saved?.indexForCurrentSong(currentItemSong) ?: -1
        if (saved != null && shouldHydrateSavedQueue(
                savedSongCount = saved.songs.size,
                controllerMediaItemCount = mediaItemCount,
                savedCurrentIndex = savedCurrentIndex
            )
        ) {
            playlist.addAll(saved.songs)
            virtualPlaylistCurrentIndex = savedCurrentIndex.takeIf { mediaItemCount == 1 && it >= 0 }
        } else {
            for (index in 0 until mediaItemCount) {
                playlist += controller.getMediaItemAt(index).toSong()
            }
        }
        _playlist.value = playlist.toList()
    }

    fun applyExternalPlaybackSnapshot(snapshot: PlaybackExternalSnapshot) {
        val snapshotSong = snapshot.mediaItem?.toSongFromMediaItemExtras()
            ?: snapshot.mediaItem?.toSong()
        val snapshotSongKey = snapshotSong?.playbackStackKey()
        // A manual skip is reflected in the UI before the Binder command reaches the service.
        // Ignore an older external snapshot until the controller reports the requested target;
        // otherwise the resident player flashes back to the outgoing cover and mini lyric.
        val pendingKey = pendingOptimisticSongKey
        var acceptedPendingSnapshot = false
        if (pendingKey != null && snapshotSongKey != pendingKey) {
            val indexedTarget = playlist.getOrNull(snapshot.mediaItemIndex)
            if (indexedTarget?.playbackStackKey() != pendingKey ||
                (snapshotSong != null && !snapshotSong.isSamePlaybackIdentity(indexedTarget))
            ) {
                return
            }
            acceptedPendingSnapshot = true
        } else if (pendingKey != null && snapshotSongKey == pendingKey) {
            acceptedPendingSnapshot = true
        }
        if (acceptedPendingSnapshot && snapshotSong != null) {
            // Usually onMediaItemTransition clears this marker. External playback snapshots can
            // be the only callback delivered after a Binder skip, though; confirm the optimistic
            // target here as well so a later automatic transition is not blocked forever. Keep the
            // short stale-identity guard to reject an outgoing snapshot that follows this one.
            val previousKey = pendingOptimisticPreviousSongKey
            pendingOptimisticSongKey = null
            pendingOptimisticPreviousSongKey = null
            rememberAcceptedSongTransition(previousKey, snapshotSong)
        }
        // The crossfade/session forwarding chain can publish the outgoing item once after the
        // incoming item has already been accepted. Reuse the same short-lived identity guard used
        // by controller callbacks so external snapshots cannot undo that transition.
        if (snapshotSong != null && shouldIgnoreStaleTransition(snapshotSong)) {
            return
        }
        if (snapshotSong != null && SystemClock.elapsedRealtime() < suppressExternalSnapshotsUntilMs) {
            return
        }

        // Notification lyric metadata patches can arrive as external playback snapshots with a
        // fresh position/timeline. Treat them as display-only before writing position; otherwise
        // the lyric page sees an artificial timeline jump and rebuilds when the current line changes.
        if (isDisplayOnlyMetadataPatchSnapshot(
                isMetadataOnlyPatch = snapshot.mediaItem?.isMetadataOnlyPatch() == true,
                snapshotSong = snapshotSong,
                currentSong = _currentSong.value
            )
        ) {
            externalSnapshotGuard = null
            _duration.value = snapshot.durationMs.takeIf { it > 0L }
                ?: snapshotSong?.duration?.coerceAtLeast(0L)
                ?: _duration.value
            return
        }

        val pending = pendingTransportTargetOrNull()
        if (pending == null || snapshot.playWhenReady == pending) {
            publishTransportState(snapshot.isPlaying, snapshot.playWhenReady)
        }
        _playbackState.value = snapshot.playbackState
        _repeatMode.value = normalizeRepeatMode(snapshot.repeatMode)
        _currentPosition.value = snapshot.positionMs.coerceAtLeast(0L)
        _duration.value = snapshot.durationMs.coerceAtLeast(0L)

        if (snapshotSong == null) {
            if (snapshot.mediaItemCount <= 0) {
                externalSnapshotGuard = null
                playlist.clear()
                _playlist.value = emptyList()
                _currentSong.value = null
                _currentQueueIndex.value = -1
                _duration.value = 0L
                return
            }
            refreshStateFromController()
            return
        }

        externalSnapshotGuard = ExternalSnapshotGuard(
            mediaId = snapshot.mediaItem?.mediaId,
            song = snapshotSong
        )

        if (playlist.isEmpty() && snapshot.mediaItemCount > 0) {
            val saved = loadSavedQueue()
            val savedCurrentIndex = saved?.indexForCurrentSong(snapshotSong) ?: -1
            if (saved != null && shouldHydrateSavedQueue(
                    savedSongCount = saved.songs.size,
                    controllerMediaItemCount = snapshot.mediaItemCount,
                    savedCurrentIndex = savedCurrentIndex
                )
            ) {
                playlist.addAll(saved.songs)
                virtualPlaylistCurrentIndex = savedCurrentIndex.takeIf {
                    snapshot.mediaItemCount == 1 && it >= 0
                }
            } else {
                playlist.add(snapshotSong)
            }
            _playlist.value = playlist.toList()
        }

        val index = virtualPlaylistCurrentIndex?.takeIf { it in playlist.indices }
            ?: snapshot.mediaItemIndex
        _currentQueueIndex.value = index.takeIf { it in playlist.indices } ?: -1
        // External session snapshots do not always carry our private queue extras. When the
        // controller reports the same queue occurrence, keep the source attached to that slot;
        // otherwise a metadata refresh can silently turn a category-origin song into an
        // unclassified one and the queue's source button routes nowhere.
        val sourceAwareSnapshotSong = if (snapshotSong.playbackSourceKey == null) {
            playlist.getOrNull(index)
                ?.takeIf { it.isSamePlaybackIdentity(snapshotSong) }
                ?.let { snapshotSong.copy(playbackSourceKey = it.playbackSourceKey) }
                ?: _currentSong.value
                    ?.takeIf { it.isSamePlaybackIdentity(snapshotSong) }
                    ?.let { snapshotSong.copy(playbackSourceKey = it.playbackSourceKey) }
                ?: snapshotSong
        } else {
            snapshotSong
        }
        if (index in playlist.indices && !playlist[index].isSamePlaybackIdentity(sourceAwareSnapshotSong)) {
            playlist[index] = sourceAwareSnapshotSong
            _playlist.value = playlist.toList()
        }

        val previousSong = _currentSong.value
        _currentSong.value = sourceAwareSnapshotSong
        _duration.value = snapshot.durationMs.takeIf { it > 0L }
            ?: sourceAwareSnapshotSong.duration.coerceAtLeast(0L)

        if (!previousSong.isSamePlaybackIdentity(sourceAwareSnapshotSong)) {
            rememberAcceptedSongTransition(previousSong, sourceAwareSnapshotSong)
            resetPlayNextForwardStack()
            notificationArtworkJob?.cancel()
            notificationArtworkJob = null
            artworkAppliedSongKey = null
            sessionMetadataSongKey = null
            resetBluetoothMetadataPatchStateForSong(sourceAwareSnapshotSong)
        }

        refreshCurrentNotificationArtwork(sourceAwareSnapshotSong)
    }

    fun updateCurrentSongMetadata(updatedSong: Song) {
        val controller = mediaController
        val current = _currentSong.value ?: return
        if (!current.isSamePlaybackIdentity(updatedSong)) return
        val sourceAwareUpdatedSong = if (updatedSong.playbackSourceKey == null) {
            updatedSong.copy(playbackSourceKey = current.playbackSourceKey)
        } else {
            updatedSong
        }

        val playlistIndex = listOfNotNull(
            _currentQueueIndex.value.takeIf { it in playlist.indices },
            virtualPlaylistCurrentIndex?.takeIf { it in playlist.indices },
            controller?.currentMediaItemIndex?.takeIf { it in playlist.indices }
        ).firstOrNull { index -> playlist[index].isSamePlaybackIdentity(current) }
            ?: playlist.indexOfFirst {
                it.isSamePlaybackIdentity(current) &&
                    (current.playbackSourceKey == null || it.playbackSourceKey == current.playbackSourceKey)
            }
            .takeIf { it >= 0 }
            ?: playlist.indexOfFirst { it.isSamePlaybackIdentity(current) }
        if (playlistIndex >= 0) {
            playlist[playlistIndex] = sourceAwareUpdatedSong
            _playlist.value = playlist.toList()
            _currentQueueIndex.value = playlistIndex
        }

        _currentSong.value = sourceAwareUpdatedSong
        notificationArtworkCache.remove(current.notificationArtworkKey())
        notificationArtworkCache.remove(sourceAwareUpdatedSong.notificationArtworkKey())
        missingNotificationArtworkKeys.remove(current.notificationArtworkKey())
        missingNotificationArtworkKeys.remove(sourceAwareUpdatedSong.notificationArtworkKey())
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        artworkAppliedSongKey = null
        sessionMetadataSongKey = null

        if (controller != null && controller.currentMediaItemIndex >= 0) {
            // Keep the UI and saved queue fresh immediately, but avoid replacing the currently
            // decoded MediaItem while it is playing; some sources briefly stall on that refresh.
            if (shouldDeferCurrentItemMetadata(controller)) {
                deferredCurrentItemMetadataSong = sourceAwareUpdatedSong
                deferredCurrentItemMetadataMediaId = controller.currentMediaItem?.mediaId
                Log.d(TIMING_TAG, "defer current metadata update while playing mediaId=${sourceAwareUpdatedSong.id}")
            } else {
                deferredCurrentItemMetadataSong = null
                deferredCurrentItemMetadataMediaId = null
                refreshCurrentMetadataAndArtwork(controller, sourceAwareUpdatedSong)
            }
        }
        savePlaybackQueue(force = true)
    }

    internal suspend fun updateCurrentSongMetadataAndAwaitArtwork(updatedSong: Song) {
        updateCurrentSongMetadata(updatedSong)
        notificationArtworkJob?.join()
    }

    private fun songToMediaItem(song: Song): MediaItem {
        val cachedArtwork = notificationArtworkCache.get(song.notificationArtworkKey())
        val builder = MediaItem.Builder()
            .setUri(song.playbackUri())
            // Media3 uses mediaId as an item identity in several transitions. The same song is
            // allowed to appear more than once in a queue, so each occurrence needs its own id;
            // the stable library id remains in MediaMetadata extras.
            .setMediaId(queueEntryMediaId(song.id, queueEntrySequence.incrementAndGet()))
            .setMediaMetadata(
                song.mediaMetadata(
                    artworkData = cachedArtwork,
                    // A local album URI is shared by every track in that album and can therefore
                    // display the wrong picture. Wait for this song's embedded bytes; the album
                    // URI is published later only if extraction confirms they are unavailable.
                    includeArtworkUri = cachedArtwork == null,
                    includeAlbumArtworkUri = false
                )
            )

        if (song.mimeType.isNotBlank()) {
            builder.setMimeType(song.mimeType)
        }

        return builder.build()
    }

    private fun Song.mediaMetadata(
        titleOverride: CharSequence? = null,
        artistOverride: CharSequence? = null,
        artworkData: ByteArray? = null,
        includeArtworkUri: Boolean = true,
        includeAlbumArtworkUri: Boolean = true
    ): MediaMetadata {
        val extras = toMediaItemExtras().apply {
            putString(EXTRA_ONLINE_SOURCE, onlineSource)
            putString(EXTRA_ONLINE_ID, onlineId)
            putString(EXTRA_SONG_JSON, this@mediaMetadata.toPlaybackQueueJson().toString())
        }
        return MediaMetadata.Builder()
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setTitle(titleOverride ?: title)
            .setArtist(artistOverride ?: artist)
            .setAlbumTitle(album)
            // Preserve the tag exactly. Album artist and track artist are different metadata
            // fields; synthesizing one here leaks incorrect data into system media surfaces.
            .setAlbumArtist(albumArtist.takeIf { it.isNotBlank() })
            .setDisplayTitle(titleOverride ?: title)
            .setSubtitle(artistOverride ?: artist)
            .setDescription(album)
            .setTrackNumber(trackNumber.takeIf { it > 0 })
            .setDiscNumber(discNumber.takeIf { it > 0 })
            .setExtras(extras)
            .apply {
                duration.takeIf { it > 0L }?.let(::setDurationMs)
                if (artworkData != null) {
                    setArtworkData(artworkData, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                }
                if (includeArtworkUri) {
                    artworkUriForMediaCenter(includeAlbumArtworkUri)?.let(::setArtworkUri)
                }
            }
            .build()
    }

    private fun updateCurrentSong() {
        val controller = mediaController ?: return
        if (shouldIgnoreStaleControllerSong(controller)) return

        val currentIndex = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem
        val itemSong = currentItem?.toSongFromMediaItemExtras() ?: currentItem?.toSong()
        val playlistIndex = virtualPlaylistCurrentIndex?.takeIf { it in playlist.indices } ?: currentIndex
        val playlistSong = playlist.getOrNull(playlistIndex)
        val restoredSong = resolveControllerPlaylistSong(
            currentIndex = playlistIndex,
            playlistSize = playlist.size,
            itemSong = itemSong,
            playlistSong = playlistSong
        )
        val previousSong = _currentSong.value
        val pendingKey = pendingOptimisticSongKey
        var acceptedOptimisticTransition = false
        if (pendingKey != null) {
            if (restoredSong?.playbackStackKey() == pendingKey) {
                pendingOptimisticSongKey = null
                acceptedOptimisticTransition = true
                rememberAcceptedSongTransition(
                    previousSongKey = pendingOptimisticPreviousSongKey,
                    acceptedSong = restoredSong
                )
                pendingOptimisticPreviousSongKey = null
            } else {
                // seekToNext is Binder-async; keep the already-switched cover/title until the
                // controller actually lands on the song the skip button targeted.
                return
            }
        }
        if (!acceptedOptimisticTransition && shouldIgnoreStaleTransition(restoredSong)) {
            _duration.value = controller.duration.coerceAtLeast(0)
            return
        }
        if (currentItem?.isMetadataOnlyPatch() == true &&
            previousSong.isSamePlaybackIdentity(restoredSong)
        ) {
            _duration.value = controller.duration.coerceAtLeast(0)
            savePlaybackState(force = true)
            return
        }
        if (
            previousSong != null &&
            !previousSong.isSamePlaybackIdentity(restoredSong) &&
            SystemClock.elapsedRealtime() < suppressSongIdentityUntilElapsedRealtime
        ) {
            _duration.value = controller.duration.coerceAtLeast(0)
            return
        }
        _currentQueueIndex.value = playlistIndex.takeIf { it in playlist.indices } ?: -1
        _currentSong.value = restoredSong
        _duration.value = controller.duration.coerceAtLeast(0)
        if (!previousSong.isSamePlaybackIdentity(restoredSong)) {
            deferredCurrentItemMetadataSong = null
            deferredCurrentItemMetadataMediaId = null
            if (!acceptedOptimisticTransition) {
                rememberAcceptedSongTransition(previousSong, restoredSong)
            }
            autoDecoderRetrySongKey = null
            resetPlayNextForwardStack()
            notificationArtworkJob?.cancel()
            notificationArtworkJob = null
            artworkAppliedSongKey = null
            sessionMetadataSongKey = null
            clearBluetoothMetadataPatchState()
        }
        savePlaybackState(force = true)
    }

    private fun shouldIgnoreStaleControllerSong(controller: MediaController): Boolean {
        val guard = externalSnapshotGuard ?: return false
        if (controller.matchesExternalSnapshot(guard)) {
            return false
        }
        val currentSong = _currentSong.value
        val currentItem = controller.currentMediaItem
        if (currentSong != null &&
            currentItem?.isMetadataOnlyPatch() == true &&
            currentItem.matchesSong(currentSong)
        ) {
            externalSnapshotGuard = null
            return false
        }
        return true
    }

    private suspend fun tryRecoverAutoDecoderPlayback(song: Song): Boolean {
        // Keep Auto genuinely automatic: normal queue changes stay inside the existing Media3
        // player (which already prepares its next media period). Rebuilding the service before
        // every AAC/ALAC track made rapid skips visibly stall and contradicted the setting's
        // "fallback only when unsupported" contract. We only pay that cost after a real decode
        // failure, then retain the FFmpeg override for the remaining connected session.
        if (!song.isM4aOrAppleLosslessOrAACOrApe()) return false
        if (settingsManager.decoderMode.first() != DECODER_MODE_AUTO) return false
        if (PlaybackService.decoderModeOverride.value == DECODER_MODE_FFMPEG_PREFER) return false

        val songKey = song.playbackStackKey()
        if (autoDecoderRetrySongKey == songKey) return false

        autoDecoderRetrySongKey = songKey
        PlaybackService.decoderModeOverride.value = DECODER_MODE_FFMPEG_PREFER
        AppLogStore.warn(
            context,
            "PlayerDecoder",
            "Retry ${song.title} with FFmpeg after playback failure"
        )
        val shouldResume = _playWhenReady.value || _isPlaying.value
        withContext(Dispatchers.Main.immediate) {
            recreatePlaybackService(resumePlayback = shouldResume)
        }
        return true
    }

    private fun shufflePlaylistKeepingCurrent(): Boolean {
        val controller = mediaController ?: return false
        if (_queueLocked.value) return false
        if (reorderingPlaylistForShuffle) return false
        if (virtualPlaylistCurrentIndex != null || playlist.size <= 1) return false
        if (playlistBeforeShuffle == null) {
            playlistBeforeShuffle = playlist.toList()
        }
        val sourceOrder = playlistBeforeShuffle ?: playlist.toList()
        val current = resolveCurrentPlaybackSong(controller) ?: return false
        if (reuseLastShuffleQueueKeepingCurrent(controller, sourceOrder)) {
            return true
        }
        val shuffleSeed = if (shuffleMode == SettingsManager.SHUFFLE_MODE_TRUE_RANDOM) {
            SystemClock.elapsedRealtimeNanos()
        } else {
            buildPseudoShuffleSeed(sourceOrder, current)
        }
        val plan = buildShuffleQueueKeepingCurrent(
            sourceOrder = sourceOrder,
            current = current,
            currentIndexHint = controller.currentMediaItemIndex,
            seed = shuffleSeed
        ) ?: return false
        val newPlaylist = plan.queue
        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying

        reorderingPlaylistForShuffle = true
        try {
            applyControllerPlaylistOrder(
                controller = controller,
                targetOrder = newPlaylist,
                targetIndex = plan.currentIndex,
                positionMs = positionMs,
                wasPlaying = wasPlaying
            )
            playlist.clear()
            playlist.addAll(newPlaylist)
            _playlist.value = newPlaylist
            lastShuffleQueue = newPlaylist.toList()
            lastShuffleSourceOrder = sourceOrder.toList()
            updateCurrentSong()
            return true
        } finally {
            reorderingPlaylistForShuffle = false
        }
    }

    private fun clearShuffleReuseCache() {
        lastShuffleQueue = null
        lastShuffleSourceOrder = null
    }

    /**
     * Restores the previously materialized shuffle order without changing which queue
     * occurrence is currently playing. The occurrence token (identity + ordinal) matters here:
     * the same file can deliberately appear more than once in a queue.
     */
    private fun reuseLastShuffleQueueKeepingCurrent(
        controller: MediaController,
        sourceOrder: List<Song>? = null
    ): Boolean {
        val cachedOrder = lastShuffleQueue ?: return false
        val source = sourceOrder ?: lastShuffleSourceOrder ?: return false
        if (source.queueOccurrenceTokens().toSet() != playlist.queueOccurrenceTokens().toSet()) {
            return false
        }
        if (cachedOrder.queueOccurrenceTokens().toSet() != source.queueOccurrenceTokens().toSet()) {
            return false
        }
        val currentIndex = currentQueueIndex(controller)
            .takeIf { it in playlist.indices }
            ?: playlist.indexOfFirst { it.isSamePlaybackIdentity(_currentSong.value) }
                .takeIf { it >= 0 }
            ?: return false
        val currentToken = playlist.queueOccurrenceTokens().getOrNull(currentIndex) ?: return false
        val targetTokens = cachedOrder.queueOccurrenceTokens()
        val targetIndex = targetTokens.indexOf(currentToken)
        if (targetIndex < 0) return false

        playlistBeforeShuffle = source.toList()
        if (playlist == cachedOrder) {
            _currentQueueIndex.value = targetIndex
            return true
        }

        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying || controller.playWhenReady
        reorderingPlaylistForShuffle = true
        try {
            controller.shuffleModeEnabled = false
            applyControllerPlaylistOrder(
                controller = controller,
                targetOrder = cachedOrder,
                targetIndex = targetIndex,
                positionMs = positionMs,
                wasPlaying = wasPlaying
            )
            playlist.clear()
            playlist.addAll(cachedOrder)
            _playlist.value = cachedOrder
            updateCurrentSong()
            return true
        } finally {
            reorderingPlaylistForShuffle = false
        }
    }

    private fun markPendingShuffleReorder(): Boolean {
        if (_queueLocked.value) return false
        if (!shouldDeferShuffleReorder(
                enableShuffle = true,
                previousShuffle = false,
                queueSize = playlist.size,
                hasVirtualQueue = virtualPlaylistCurrentIndex != null
            )
        ) return false
        if (playlistBeforeShuffle == null) {
            playlistBeforeShuffle = playlist.toList()
        }
        pendingShuffleReorder = true
        return true
    }

    private fun performPendingShuffleReorder(trigger: String, seekToNextAfterReorder: Boolean): Boolean {
        if (_queueLocked.value) {
            clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = false)
            return false
        }
        val controller = mediaController ?: return false
        when (pendingShuffleReorderAction(
            pending = pendingShuffleReorder,
            shuffleEnabled = _shuffleEnabled.value,
            repeatOne = controller.repeatMode == Player.REPEAT_MODE_ONE,
            queueSize = playlist.size,
            hasVirtualQueue = virtualPlaylistCurrentIndex != null
        )) {
            PendingShuffleReorderAction.None -> return false
            PendingShuffleReorderAction.Clear -> {
                clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = false)
                AppLogStore.debug(context, "PlayerQueue", "clear pending shuffle without reorder trigger=$trigger")
                return false
            }
            PendingShuffleReorderAction.Materialize -> Unit
        }
        Log.d(TIMING_TAG, "perform pending shuffle reorder trigger=$trigger")
        controller.shuffleModeEnabled = false
        val materialized = shufflePlaylistKeepingCurrent()
        clearPendingShuffleReorder(disableNativeShuffle = false, clearOriginalOrder = false)
        if (!materialized) {
            return false
        }
        if (seekToNextAfterReorder) {
            val currentIndex = currentQueueIndex(controller)
            val nextIndex = adjacentPlaylistIndex(
                currentIndex = currentIndex,
                offset = 1,
                queueSize = playlist.size,
                wrap = controller.repeatMode != Player.REPEAT_MODE_OFF
            )
            nextIndex?.let { index ->
                applyOptimisticSong(playlist[index], 0L, index)
            }
            controller.seekToNextMediaItem()
        }
        return true
    }

    private fun restorePlaylistOrderAfterShuffle() {
        val original = playlistBeforeShuffle ?: return
        if (original.isEmpty()) {
            playlistBeforeShuffle = null
            return
        }
        val controller = mediaController ?: run {
            playlist.clear()
            playlist.addAll(original)
            _playlist.value = original
            playlistBeforeShuffle = null
            return
        }
        if (reorderingPlaylistForShuffle) return

        val currentIndex = currentQueueIndex(controller)
        val currentOccurrence = playlist.getOrNull(currentIndex)
        val targetIndex = if (currentOccurrence != null) {
            val occurrenceOrdinal = playlist
                .take(currentIndex + 1)
                .count { it.isSamePlaybackIdentity(currentOccurrence) } - 1
            original.withIndex()
                .filter { it.value.isSamePlaybackIdentity(currentOccurrence) }
                .getOrNull(occurrenceOrdinal)
                ?.index
        } else {
            null
        } ?: original.indexOfFirst {
            it.isSamePlaybackIdentity(resolveCurrentPlaybackSong(controller))
        }.takeIf { it >= 0 }
            ?: controller.currentMediaItemIndex.coerceIn(0, original.lastIndex)
        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying

        reorderingPlaylistForShuffle = true
        try {
            // Disabling shuffle is a mode change, not a request to reload the current media. Move
            // the existing queue entries in place so the audio renderer keeps its decoder and
            // playWhenReady state. setMediaItems here was the short pause reported when users
            // switched away from random playback.
            val reorderedInPlace = moveControllerPlaylistOrderWithoutReset(
                controller = controller,
                targetOrder = original
            )
            if (!reorderedInPlace) {
                // A controller window can differ from the local queue after a reconnect. Keep a
                // correct fallback for that case; ordinary mode toggles use the in-place path.
                applyControllerPlaylistOrder(
                    controller = controller,
                    targetOrder = original,
                    targetIndex = targetIndex,
                    positionMs = positionMs,
                    wasPlaying = wasPlaying
                )
            }
            playlist.clear()
            playlist.addAll(original)
            _playlist.value = original
            _currentQueueIndex.value = targetIndex
            _currentSong.value = original.getOrNull(targetIndex)
            _currentPosition.value = positionMs
            _duration.value = controller.duration.coerceAtLeast(0L).takeIf { it > 0L }
                ?: original.getOrNull(targetIndex)?.duration
                ?: 0L
            playlistBeforeShuffle = null
        } finally {
            reorderingPlaylistForShuffle = false
        }
    }

    private fun reshuffleAtCycleBoundary(controller: MediaController, previousLast: Song?): Boolean {
        if (previousLast == null || reorderingPlaylistForShuffle || playlist.size <= 1) return false
        val sourceOrder = playlistBeforeShuffle ?: playlist.toList()
        val plan = buildShuffleQueueForCycle(
            sourceOrder = sourceOrder,
            previousLast = previousLast,
            seed = SystemClock.elapsedRealtimeNanos()
        ) ?: return false
        val wasPlaying = controller.isPlaying || controller.playWhenReady
        reorderingPlaylistForShuffle = true
        try {
            controller.shuffleModeEnabled = false
            applyControllerPlaylistOrder(
                controller = controller,
                targetOrder = plan.queue,
                targetIndex = plan.currentIndex,
                positionMs = 0L,
                wasPlaying = wasPlaying
            )
            playlist.clear()
            playlist.addAll(plan.queue)
            _playlist.value = plan.queue
            _currentQueueIndex.value = plan.currentIndex
            _currentSong.value = plan.queue.firstOrNull()
            _currentPosition.value = 0L
            _duration.value = plan.queue.firstOrNull()?.duration ?: 0L
            playlistBeforeShuffle = sourceOrder
            lastShuffleQueue = plan.queue.toList()
            lastShuffleSourceOrder = sourceOrder.toList()
            savePlaybackQueue(force = true)
            return true
        } finally {
            reorderingPlaylistForShuffle = false
        }
    }

    /**
     * Reorders the current Media3 timeline with moveMediaItem calls. Unlike setMediaItems this
     * does not tear down/re-prepare the current renderer, so turning shuffle off remains audible
     * and visually continuous. Occurrence tokens keep duplicate paths distinguishable.
     */
    private fun moveControllerPlaylistOrderWithoutReset(
        controller: MediaController,
        targetOrder: List<Song>
    ): Boolean {
        if (targetOrder.size != playlist.size || controller.mediaItemCount != playlist.size) return false
        val workingSongs = playlist.toMutableList()
        val workingTokens = playlist.queueOccurrenceTokens().toMutableList()
        val targetTokens = targetOrder.queueOccurrenceTokens()
        if (workingTokens.toSet() != targetTokens.toSet()) return false

        return runCatching {
            targetTokens.forEachIndexed { targetIndex, token ->
                val fromIndex = workingTokens.indexOf(token)
                check(fromIndex >= 0) { "queue occurrence missing while restoring shuffle" }
                if (fromIndex == targetIndex) return@forEachIndexed
                controller.moveMediaItem(fromIndex, targetIndex)
                val song = workingSongs.removeAt(fromIndex)
                workingSongs.add(targetIndex, song)
                val movedToken = workingTokens.removeAt(fromIndex)
                workingTokens.add(targetIndex, movedToken)
            }
            check(workingTokens == targetTokens) { "queue occurrence order was not restored" }
        }.isSuccess
    }

    private data class QueueOccurrenceToken(
        val identity: String,
        val ordinal: Int
    )

    private fun List<Song>.queueOccurrenceTokens(): List<QueueOccurrenceToken> {
        val seen = mutableMapOf<String, Int>()
        return map { song ->
            val identity = "${song.playbackStackKey()}|source=${song.playbackSourceKey.orEmpty()}"
            val ordinal = seen.getOrDefault(identity, 0)
            seen[identity] = ordinal + 1
            QueueOccurrenceToken(identity, ordinal)
        }
    }


    private fun resolveCurrentPlaybackSong(controller: MediaController): Song? {
        val controllerIndex = currentQueueIndex(controller)
        val itemSong = controller.currentMediaItem?.toSongFromMediaItemExtras()
            ?: controller.currentMediaItem?.toSong()
        if (controllerIndex in playlist.indices) {
            return resolveControllerPlaylistSong(
                currentIndex = controllerIndex,
                playlistSize = playlist.size,
                itemSong = itemSong,
                playlistSong = playlist[controllerIndex]
            )
        }
        return itemSong
            ?: _currentSong.value
    }

    private fun applyControllerPlaylistOrder(
        controller: MediaController,
        targetOrder: List<Song>,
        targetIndex: Int,
        positionMs: Long,
        wasPlaying: Boolean
    ) {
        if (targetOrder.isEmpty()) return
        val safeIndex = targetIndex.coerceIn(targetOrder.indices)
        // setMediaItems may publish a transient index 0 before its timeline callback reaches the
        // controller. Project the selected occurrence first so a following next/previous command
        // and the player page both use the same queue cursor during that Binder window.
        applyOptimisticSong(targetOrder[safeIndex], positionMs, safeIndex)
        // Always use setMediaItems (single IPC call) instead of N moveMediaItem calls
        // to avoid main-thread freezes with large playlists
        controller.setMediaItems(targetOrder.map(::songToMediaItem), safeIndex, positionMs)
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        if (wasPlaying) controller.play()
        // setMediaItems often reports index 0 for a frame. Hold the previous song identity
        // so lyrics/cover do not jump to the first queue item (#461).
        suppressSongIdentityUntilElapsedRealtime =
            SystemClock.elapsedRealtime() + SHUFFLE_REORDER_IDENTITY_GUARD_MS
    }

    private fun refreshCurrentSessionMetadata(controller: MediaController, song: Song) {
        val index = controller.currentMediaItemIndex
        val currentItem = controller.currentMediaItem ?: return
        val songKey = song.playbackStackKey()
        if (index < 0 || sessionMetadataSongKey == songKey) return
        if (!currentItem.matchesSong(song)) return

        runCatching {
            val cachedArtwork = notificationArtworkCache.get(song.notificationArtworkKey())
            val targetMetadata = song.mediaMetadata(
                artworkData = cachedArtwork,
                includeArtworkUri = cachedArtwork == null,
                includeAlbumArtworkUri = false
            ).withPatchedExtrasFrom(currentItem, PATCH_REASON_BASE_SESSION_METADATA)
            sessionMetadataSongKey = songKey
            if (currentItem.mediaMetadata.matchesNotificationDisplay(targetMetadata)) {
                Log.d(TIMING_TAG, "base metadata already current mediaId=${song.id}")
                return@runCatching
            }
            controller.replaceMediaItem(
                index,
                currentItem.buildUpon()
                    .setMediaMetadata(targetMetadata)
                    .build()
            )
            Log.d(TIMING_TAG, "base metadata updated mediaId=${song.id}")
        }
    }

    private fun refreshCurrentMetadataAndArtwork(controller: MediaController, song: Song) {
        if (song.coverUrl.isNotBlank() && !song.coverUrl.isMediaStoreAlbumArtworkUri()) {
            refreshCurrentSessionMetadata(controller, song)
            refreshCurrentNotificationArtwork(song)
        } else {
            // For embedded/local artwork, the artwork refresh also publishes all base media
            // fields; doing both passes used to replace the active MediaItem twice in succession.
            refreshCurrentNotificationArtwork(song)
        }
    }

    private fun refreshCurrentNotificationArtwork(song: Song?) {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem ?: return
        val index = controller.currentMediaItemIndex
        val songKey = song?.notificationArtworkKey()
        if (song == null || index < 0 || artworkAppliedSongKey == songKey) return
        if (song.coverUrl.isNotBlank() && !song.coverUrl.isMediaStoreAlbumArtworkUri()) {
            artworkAppliedSongKey = songKey
            return
        }
        if (!currentItem.matchesSong(song)) return

        val artworkKey = song.notificationArtworkKey()
        val cached = notificationArtworkCache.get(artworkKey)
        if (cached != null) {
            Log.d(TIMING_TAG, "artwork cache hit mediaId=${song.id}")
            replaceCurrentItemArtwork(controller, index, song, cached)
            return
        }
        if (missingNotificationArtworkKeys.contains(artworkKey)) return

        notificationArtworkJob?.cancel()
        notificationArtworkJob = persistenceScope.launch {
            val startedAt = SystemClock.elapsedRealtime()
            Log.d(TIMING_TAG, "artwork load start mediaId=${song.id}")
            val data = runCatching {
                artworkRepository.getCoverArt(song)?.let(::notificationArtworkBytes)
            }.getOrElse { error ->
                AppLogStore.warn(context, "PlayerArtwork", "Failed to load notification artwork for ${song.title}", error)
                null
            }
            if (data == null) {
                Log.d(TIMING_TAG, "artwork load finish mediaId=${song.id} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms missing")
                withContext(Dispatchers.Main.immediate) {
                    val latestController = mediaController
                    val latestIndex = latestController?.currentMediaItemIndex ?: -1
                    if (latestController?.currentMediaItem?.matchesSong(song) == true) {
                        missingNotificationArtworkKeys += artworkKey
                        replaceCurrentItemArtwork(latestController, latestIndex, song, null)
                    }
                }
                return@launch
            }
            withContext(Dispatchers.Main.immediate) {
                val latestController = mediaController ?: return@withContext
                val latestIndex = latestController.currentMediaItemIndex
                val latestItem = latestController.currentMediaItem ?: return@withContext
                Log.d(TIMING_TAG, "artwork load finish mediaId=${song.id} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms")
                if (_currentSong.value.isSamePlaybackIdentity(song) &&
                    latestItem.matchesSong(song) &&
                    latestIndex >= 0
                ) {
                    notificationArtworkCache.put(artworkKey, data)
                    replaceCurrentItemArtwork(latestController, latestIndex, song, data)
                }
            }
        }
    }

    private fun shouldDeferCurrentItemMetadata(controller: MediaController): Boolean =
        controller.playWhenReady &&
            controller.playbackState != Player.STATE_IDLE &&
            controller.playbackState != Player.STATE_ENDED

    private fun applyDeferredCurrentItemMetadataIfSafe(controller: MediaController) {
        if (mediaController !== controller || shouldDeferCurrentItemMetadata(controller)) return
        val pendingSong = deferredCurrentItemMetadataSong ?: return
        if (!_currentSong.value.isSamePlaybackIdentity(pendingSong)) {
            deferredCurrentItemMetadataSong = null
            deferredCurrentItemMetadataMediaId = null
            return
        }
        if (controller.currentMediaItem?.matchesSong(pendingSong) != true) return

        deferredCurrentItemMetadataSong = null
        deferredCurrentItemMetadataMediaId = null
        refreshCurrentMetadataAndArtwork(controller, pendingSong)
    }

    private fun applyDeferredCurrentItemMetadataAfterTransition(
        controller: MediaController,
        transitionedItem: MediaItem?
    ) {
        val pendingSong = deferredCurrentItemMetadataSong ?: return
        val transitionedSong = transitionedItem?.toSongFromMediaItemExtras() ?: return
        if (pendingSong.isSamePlaybackIdentity(transitionedSong)) return

        val pendingMediaId = deferredCurrentItemMetadataMediaId
        val pendingIndex = pendingMediaId?.let { mediaId ->
            (0 until controller.mediaItemCount).firstOrNull { index ->
                controller.getMediaItemAt(index).mediaId == mediaId
            }
        }
        if (pendingIndex != null && pendingIndex != controller.currentMediaItemIndex) {
            val oldItem = controller.getMediaItemAt(pendingIndex)
            if (oldItem.matchesSong(pendingSong)) {
                val cachedArtwork = notificationArtworkCache.get(pendingSong.notificationArtworkKey())
                val metadata = pendingSong.mediaMetadata(
                    artworkData = cachedArtwork,
                    includeArtworkUri = cachedArtwork == null,
                    includeAlbumArtworkUri = false
                ).withPatchedExtrasFrom(oldItem, PATCH_REASON_BASE_SESSION_METADATA)
                runCatching {
                    controller.replaceMediaItem(
                        pendingIndex,
                        oldItem.buildUpon().setMediaMetadata(metadata).build()
                    )
                }
            }
        }
        deferredCurrentItemMetadataSong = null
        deferredCurrentItemMetadataMediaId = null
    }

    private fun notificationArtworkBytes(data: ByteArray): ByteArray? {
        if (data.size <= MAX_NOTIFICATION_ARTWORK_BYTES) return data
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            var sample = 1
            while (
                bounds.outWidth / sample > NOTIFICATION_ARTWORK_MAX_SIDE ||
                bounds.outHeight / sample > NOTIFICATION_ARTWORK_MAX_SIDE
            ) {
                sample *= 2
            }
            val bitmap = BitmapFactory.decodeByteArray(
                data,
                0,
                data.size,
                BitmapFactory.Options().apply { inSampleSize = sample }
            ) ?: return@runCatching null
            val out = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
            if (!bitmap.isRecycled) bitmap.recycle()
            out.toByteArray().takeIf { it.isNotEmpty() }
        }.getOrNull()
    }

    private fun replaceCurrentItemArtwork(
        controller: MediaController,
        index: Int,
        song: Song,
        artworkData: ByteArray?
    ) {
        if (index != controller.currentMediaItemIndex) return
        val latestItem = controller.currentMediaItem ?: return
        if (!latestItem.matchesSong(song)) return
        runCatching {
            artworkAppliedSongKey = song.notificationArtworkKey()
            val targetMetadata = song.mediaMetadata(
                artworkData = artworkData,
                includeArtworkUri = artworkData == null,
                includeAlbumArtworkUri = artworkData == null
            )
                .withPatchedExtrasFrom(latestItem, PATCH_REASON_NOTIFICATION_ARTWORK)
            if (latestItem.mediaMetadata.matchesNotificationDisplay(targetMetadata)) {
                sessionMetadataSongKey = song.playbackStackKey()
                Log.d(TIMING_TAG, "artwork metadata already current mediaId=${song.id}")
                return@runCatching
            }
            controller.replaceMediaItem(
                index,
                latestItem.buildUpon()
                    .setMediaMetadata(targetMetadata)
                    .build()
            )
            sessionMetadataSongKey = song.playbackStackKey()
            Log.d(TIMING_TAG, "artwork metadata updated mediaId=${song.id}")
        }
    }

    private fun clearBluetoothMetadataPatchState() {
        bluetoothMetadataPatchState = MediaNotificationLyricPatchPolicy.onCleared()
    }

    private fun resetBluetoothMetadataPatchStateForSong(song: Song?) {
        bluetoothMetadataPatchState = MediaNotificationLyricPatchPolicy.onSongChanged(
            songKey = song?.playbackStackKey(),
            nowMs = SystemClock.elapsedRealtime()
        )
    }

    private fun restoreSavedQueueIfNeeded() {
        val controller = mediaController ?: return
        if (controller.mediaItemCount > 0) return

        val saved = loadSavedQueue() ?: return
        if (saved.songs.isEmpty()) return

        isRestoringSavedQueue = true
        try {
            clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = false)
            pendingShuffleReorder = false
            startupMediaTransitionGuardUntilMs = SystemClock.elapsedRealtime() + 3000L
            val requestedIndex = saved.index.coerceIn(saved.songs.indices)
            val reshuffledSongs = if (saved.shuffle && reshuffleOnStartup && saved.songs.size > 1) {
                // Shuffle occurrences rather than identities so duplicate queue entries remain
                // distinct, while keeping the previously playing occurrence selected for resume.
                val shuffledOccurrences = saved.songs
                    .mapIndexed { index, song -> index to song }
                    .shuffled(Random(SystemClock.elapsedRealtimeNanos()))
                shuffledOccurrences.map { it.second } to
                    shuffledOccurrences.indexOfFirst { it.first == requestedIndex }
            } else {
                saved.songs to requestedIndex
            }
            val (restoredOrder, restoredIndex) = reshuffledSongs
            val (queueSongs, safeIndex) = restoredOrder.windowedForController(restoredIndex)
            val restoredRepeatMode = if (disableSequentialPlayback && saved.repeatMode == Player.REPEAT_MODE_OFF) {
                Player.REPEAT_MODE_ALL
            } else {
                saved.repeatMode
            }
            val wasReshuffled = restoredOrder !== saved.songs
            playlist.clear()
            playlist.addAll(queueSongs)
            _playlist.value = playlist.toList()
            if (saved.shuffle) {
                lastShuffleQueue = queueSongs.toList()
                lastShuffleSourceOrder = queueSongs.toList()
            }

            // Restoring a queue is a data operation, not an implicit play request. Pause before
            // attaching the first media item: some Media3/OEM service recreations retain
            // playWhenReady across setMediaItems(), and prepare() may start decoding immediately.
            // If an explicit startup-resume/Bluetooth/decoder request is pending, setupListener()
            // reapplies it after this restore returns.
            controller.pause()
            controller.setMediaItems(queueSongs.map(::songToMediaItem), safeIndex, saved.positionMs.coerceAtLeast(0L))
            controller.repeatMode = restoredRepeatMode
            controller.shuffleModeEnabled = false
            controller.playbackParameters = PlaybackParameters(saved.speed.coerceIn(0.5f, 2f), saved.pitch.coerceIn(0.5f, 2f))
            controller.prepare()

            _currentSong.value = playlist.getOrNull(safeIndex)
            _currentQueueIndex.value = safeIndex
            _currentPosition.value = saved.positionMs.coerceAtLeast(0L)
            _repeatMode.value = restoredRepeatMode
            _shuffleEnabled.value = saved.shuffle
            _queueLocked.value = saved.queueLocked
            persistAppShuffleEnabled(saved.shuffle)
            persistAppRepeatMode(restoredRepeatMode)
            _playbackSpeed.value = saved.speed
            _playbackPitch.value = saved.pitch
            if (wasReshuffled || saved.songs.size > LARGE_LIBRARY_SAFE_MODE_THRESHOLD) {
                savePlaybackQueue(force = true)
            }
        } finally {
            isRestoringSavedQueue = false
        }
    }

    private fun seedSavedPlaybackPreview() {
        val saved = loadSavedQueue() ?: return
        val index = saved.index.takeIf { it in saved.songs.indices } ?: return
        val current = saved.songs[index]
        playlist.clear()
        playlist.addAll(saved.songs)
        _playlist.value = playlist.toList()
        _currentSong.value = current
        _currentQueueIndex.value = index
        _currentPosition.value = saved.positionMs.coerceAtLeast(0L)
        _duration.value = current.duration.coerceAtLeast(0L)
        val previewRepeatMode = normalizeRepeatMode(saved.repeatMode)
        _repeatMode.value = previewRepeatMode
        _shuffleEnabled.value = saved.shuffle
        if (saved.shuffle) {
            lastShuffleQueue = saved.songs.toList()
            lastShuffleSourceOrder = saved.songs.toList()
        }
        _queueLocked.value = saved.queueLocked
        _playbackSpeed.value = saved.speed.coerceIn(0.5f, 2f)
        _playbackPitch.value = saved.pitch.coerceIn(0.5f, 2f)
    }

    private fun savePlaybackQueue(force: Boolean = false) {
        if (playlist.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastQueueSaveMs < 2_500L) return
        lastQueueSaveMs = now

        val songs = playlist.toList()
        val snapshot = capturePlaybackState()

        persistenceScope.launch {
            val payload = playbackQueueJson(snapshot, songs)

            context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_QUEUE, payload.toString())
                .putString(KEY_STATE, snapshot.toJson().toString())
                .apply()
        }
    }

    private fun savePlaybackState(force: Boolean = false) {
        if (playlist.isEmpty()) return
        val now = System.currentTimeMillis()
        if (!force && now - lastStateSaveMs < 2_500L) return
        lastStateSaveMs = now

        val snapshot = capturePlaybackState()
        // An immediate state transition (pause, next, reconnect, etc.) must supersede a
        // deferred seek snapshot so an old position cannot be written after it.
        deferredSeekStateSaveJob?.cancel()
        deferredSeekStateSaveJob = null
        playbackStateSaveGeneration++
        persistenceScope.launch {
            persistPlaybackState(snapshot)
        }
    }

    /**
     * Seeking can produce a burst of controller discontinuities.  Persisting every intermediate
     * position used to enqueue a SharedPreferences write for each tap; on slower storage those
     * writes accumulated and made the next pause visibly late.  Keep the latest target only.
     */
    private fun enqueueSeekCommand(target: Long) {
        pendingSeekTargetMs = target
        deferredSeekCommandJob?.cancel()
        deferredSeekCommandJob = commandScope.launch {
            delay(SEEK_COMMAND_COALESCE_MS)
            flushPendingSeekCommand()
        }
    }

    /** Sends only the newest target from a rapid seek burst before a pause/track switch. */
    private fun flushPendingSeekCommand() {
        val target = pendingSeekTargetMs ?: return
        pendingSeekTargetMs = null
        deferredSeekCommandJob = null
        mediaController?.takeIf { it.isConnected }?.seekTo(target)
    }

    private fun cancelPendingSeekCommand() {
        pendingSeekTargetMs = null
        deferredSeekCommandJob?.cancel()
        deferredSeekCommandJob = null
    }

    private fun scheduleSeekStateSave(targetPositionMs: Long) {
        if (playlist.isEmpty()) return
        lastStateSaveMs = System.currentTimeMillis()
        val snapshot = capturePlaybackState(positionOverrideMs = targetPositionMs)
        val generation = ++playbackStateSaveGeneration
        deferredSeekStateSaveJob?.cancel()
        deferredSeekStateSaveJob = persistenceScope.launch {
            delay(SEEK_STATE_SAVE_DEBOUNCE_MS)
            if (generation != playbackStateSaveGeneration) return@launch
            persistPlaybackState(snapshot)
        }
    }

    private fun persistPlaybackState(snapshot: PlaybackStateSnapshot) {
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, snapshot.toJson().toString())
            .apply()
    }

    private fun capturePlaybackState(positionOverrideMs: Long? = null): PlaybackStateSnapshot {
        val controller = mediaController
        val index = currentQueueIndex(controller).takeIf { it >= 0 } ?: -1
        return PlaybackStateSnapshot(
            index = index.coerceAtLeast(0),
            positionMs = positionOverrideMs ?: controller?.currentPosition?.coerceAtLeast(0) ?: _currentPosition.value,
            repeatMode = normalizeRepeatMode(controller?.repeatMode ?: _repeatMode.value),
            shuffle = _shuffleEnabled.value,
            speed = controller?.playbackParameters?.speed ?: _playbackSpeed.value,
            pitch = controller?.playbackParameters?.pitch ?: _playbackPitch.value,
            queueLocked = _queueLocked.value
        )
    }

    private fun clearSavedQueue() {
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .remove(KEY_QUEUE)
            .remove(KEY_STATE)
            .remove(KEY_APP_SHUFFLE)
            .remove(KEY_APP_REPEAT)
            .apply()
    }

    private fun persistAppShuffleEnabled(enabled: Boolean) {
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_SHUFFLE, enabled)
            .apply()
    }

    private fun persistAppRepeatMode(repeatMode: Int) {
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_APP_REPEAT, repeatMode)
            .apply()
    }

    private fun loadAppShuffleEnabled(): Boolean =
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_SHUFFLE, _shuffleEnabled.value)

    private fun loadAppRepeatMode(): Int {
        val stored = context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .getInt(KEY_APP_REPEAT, Player.REPEAT_MODE_ALL)
        return when (stored) {
            Player.REPEAT_MODE_OFF,
            Player.REPEAT_MODE_ONE,
            Player.REPEAT_MODE_ALL -> stored
            else -> Player.REPEAT_MODE_ALL
        }
    }

    private fun normalizeRepeatMode(repeatMode: Int): Int {
        val valid = when (repeatMode) {
            Player.REPEAT_MODE_OFF,
            Player.REPEAT_MODE_ONE,
            Player.REPEAT_MODE_ALL -> repeatMode
            else -> Player.REPEAT_MODE_ALL
        }
        return if (disableSequentialPlayback && valid == Player.REPEAT_MODE_OFF) {
            Player.REPEAT_MODE_ALL
        } else {
            valid
        }
    }

    private fun loadSavedQueue(): SavedQueue? {
        val prefs = context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
        val raw = prefs
            .getString(KEY_QUEUE, null)
            ?: return null

        return parseSavedQueue(raw, prefs.getString(KEY_STATE, null))
    }

    fun hasSavedQueue(): Boolean = loadSavedQueue()?.songs?.isNotEmpty() == true

    private fun MediaItem.toSong(): Song {
        val metadata = mediaMetadata
        metadata.extras
            ?.getString(EXTRA_SONG_JSON)
            ?.let { raw -> runCatching { JSONObject(raw).toPlaybackQueueSongOrNull() }.getOrNull() }
            ?.let { return it }

        val path = localConfiguration?.uri?.toString().orEmpty()
        val mediaIdValue = mediaId.toLongOrNull() ?: path.hashCode().toLong()
        val fileName = path.substringAfterLast('/').ifBlank { metadata.title?.toString().orEmpty() }
        return Song(
            id = mediaIdValue,
            title = metadata.title?.toString()?.ifBlank { fileName } ?: fileName,
            artist = metadata.artist?.toString()?.ifBlank { "Unknown" } ?: "Unknown",
            album = metadata.albumTitle?.toString()?.ifBlank { "Music" } ?: "Music",
            albumId = 0L,
            duration = mediaController?.duration?.coerceAtLeast(0) ?: 0L,
            path = path,
            fileName = fileName,
            mimeType = localConfiguration?.mimeType.orEmpty(),
            coverUrl = metadata.artworkUri?.toString()
                ?.takeUnless { it.isMediaStoreAlbumArtworkUri() }
                .orEmpty(),
            onlineSource = metadata.extras?.getString(EXTRA_ONLINE_SOURCE).orEmpty(),
            onlineId = metadata.extras?.getString(EXTRA_ONLINE_ID).orEmpty()
        )
    }

    private companion object {
        const val TIMING_TAG = "EllaPlaybackTiming"
        // Guard so a seek never lands on the last frame and trips end-of-stream auto-advance.
        const val SEEK_END_GUARD_MS = 600L
        const val SEEK_STATE_SAVE_DEBOUNCE_MS = 220L
        // Collapse rapid lyric-page taps before they reach the MediaController command queue.
        const val SEEK_COMMAND_COALESCE_MS = 48L
        const val RESUME_POSITION_MIN_MS = 5_000L
        const val RESUME_POSITION_END_GUARD_MS = 8_000L
        const val MAX_RESUME_POSITION_ENTRIES = 256
        const val CLEAR_EXTERNAL_SNAPSHOT_SUPPRESSION_MS = 3_000L
        const val TRANSPORT_COMMAND_GUARD_MS = 3_000L
        const val MIN_TRANSPORT_HOLD_MS = 200L
        const val EXTRA_ONLINE_SOURCE = "com.ella.music.extra.ONLINE_SOURCE"
        const val EXTRA_ONLINE_ID = "com.ella.music.extra.ONLINE_ID"
        const val EXTRA_SONG_JSON = "com.ella.music.extra.SONG_JSON"
        const val MAX_NOTIFICATION_ARTWORK_BYTES = 2 * 1024 * 1024
        const val NOTIFICATION_ARTWORK_MAX_SIDE = 512
        const val PLAYBACK_PREFS = "ella_playback_state"
        const val KEY_QUEUE = "queue"
        const val KEY_STATE = "state"
        const val KEY_APP_SHUFFLE = "app_shuffle_enabled"
        const val KEY_APP_REPEAT = "app_repeat_mode"
        const val SHUFFLE_REORDER_IDENTITY_GUARD_MS = 450L
        const val STALE_TRANSITION_GUARD_MS = 2_000L
        const val DECODER_MODE_FFMPEG_PREFER = 1
        const val DECODER_MODE_AUTO = 2
    }
}
