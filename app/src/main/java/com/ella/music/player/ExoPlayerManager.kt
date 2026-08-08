package com.ella.music.player

import android.content.ComponentName
import android.content.Context
import android.content.Intent
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
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.Collections
import kotlin.random.Random

class ExoPlayerManager(private val context: Context) {
    private var mediaController: MediaController? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playWhenReady = MutableStateFlow(false)
    val playWhenReady: StateFlow<Boolean> = _playWhenReady.asStateFlow()

    private val _currentSong = MutableStateFlow<Song?>(null)
    val currentSong: StateFlow<Song?> = _currentSong.asStateFlow()

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

    private val _playlist = MutableStateFlow<List<Song>>(emptyList())
    val playlistFlow: StateFlow<List<Song>> = _playlist.asStateFlow()
    private var playerListener: Player.Listener? = null
    private var lastQueueSaveMs = 0L
    private var lastStateSaveMs = 0L
    private var shuffleMode = SettingsManager.SHUFFLE_MODE_PSEUDO
    private var playNextMode = SettingsManager.PLAY_NEXT_MODE_REVERSE_STACK
    private var virtualPlaylistCurrentIndex: Int? = null
    private var playWhenConnected = false
    private var pendingPlaylist: PendingPlaylist? = null
    private var reorderingPlaylistForShuffle = false
    private var playlistBeforeShuffle: List<Song>? = null
    private var pendingShuffleReorder = false
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
    private var currentSongRefreshJob: Job? = null
    private var deferredSeekStateSaveJob: Job? = null
    private var deferredSeekCommandJob: Job? = null
    private var pendingSeekTargetMs: Long? = null
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

    init {
        _shuffleEnabled.value = loadAppShuffleEnabled()
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
                    if (controllerFuture !== future || result == null) return
                    mediaController = result
                    setupListener()
                }

                override fun onFailure(t: Throwable) {
                    if (controllerFuture !== future) return
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
        if (controller != null) disconnect()
        if (controllerFuture == null) connect()
    }

    private fun activeController(): MediaController? = mediaController?.takeIf { it.isConnected }

    private fun clearPresentationMetadataPatchGuard() {
        presentationMetadataPatchSongKey = null
        presentationMetadataPatchUntilMs = 0L
    }

    fun isConnected(): Boolean = mediaController?.isConnected == true

    suspend fun recreatePlaybackService(resumePlayback: Boolean = _isPlaying.value) {
        withContext(Dispatchers.Main.immediate) {
            savePlaybackQueue(force = true)
            savePlaybackState(force = true)
            playWhenConnected = resumePlayback
            AppLogStore.info(context, "PlayerDecoder", "Recreate playback service for decoder change")

            disconnect()
            context.stopService(Intent(context, PlaybackService::class.java))
            playlist.clear()
            _playlist.value = emptyList()
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
        playerListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _isPlaying.value = isPlaying
                _playWhenReady.value = mediaController?.playWhenReady ?: isPlaying
                savePlaybackState(force = true)
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                _playWhenReady.value = playWhenReady
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                _playbackState.value = playbackState
                _duration.value = mediaController?.duration?.coerceAtLeast(0) ?: 0L
                when (playbackState) {
                    Player.STATE_BUFFERING -> Log.d(TIMING_TAG, "controller state BUFFERING mediaId=${mediaController?.currentMediaItem?.mediaId}")
                    Player.STATE_READY -> Log.d(TIMING_TAG, "controller state READY mediaId=${mediaController?.currentMediaItem?.mediaId}")
                    Player.STATE_ENDED -> Log.d(TIMING_TAG, "controller state ENDED mediaId=${mediaController?.currentMediaItem?.mediaId}")
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d(TIMING_TAG, "controller media transition reason=$reason mediaId=${mediaItem?.mediaId}")
                externalSnapshotGuard = null
                clearPresentationMetadataPatchGuard()
                resetBluetoothMetadataPatchStateForSong(mediaItem?.toSongFromMediaItemExtras())
                if (pendingShuffleReorder && reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT) {
                    performPendingShuffleReorder(trigger = "transition", seekToNextAfterReorder = false)
                }
                updateCurrentSong()
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                if (mediaMetadata.metadataPatchReason() == null) {
                    clearPresentationMetadataPatchGuard()
                    return
                }
                val song = mediaController?.currentMediaItem?.toSongFromMediaItemExtras()
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
                val currentItem = mediaController?.currentMediaItem
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
                _currentPosition.value = newPosition.positionMs.coerceAtLeast(0L)
                _duration.value = mediaController?.duration?.coerceAtLeast(0) ?: 0L
                if (reason == Player.DISCONTINUITY_REASON_AUTO_TRANSITION) {
                    updateCurrentSong()
                }
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
                if (reorderingPlaylistForShuffle) return
                // Artwork and base-session patches replace the current item's MediaMetadata via
                // replaceMediaItem without changing the actual playback queue. These trigger
                // onTimelineChanged with SOURCE_UPDATE, but the real playback state (isPlaying,
                // position, queue, current song) is unchanged. Skip the full refresh to avoid
                // spurious StateFlow emissions that flicker the lyrics page.
                if (shouldIgnoreDisplayOnlyTimelineUpdate(
                        reason = reason,
                        currentItem = mediaController?.currentMediaItem,
                        currentSong = _currentSong.value
                    )
                ) return
                refreshStateFromController()
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                if (_queueLocked.value) {
                    if (shuffleModeEnabled) {
                        _shuffleEnabled.value = true
                        persistAppShuffleEnabled(true)
                        mediaController?.shuffleModeEnabled = false
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
                        mediaController?.shuffleModeEnabled = false
                    }
                }
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _repeatMode.value = repeatMode
                // The combined playback-mode button in the media notification changes the app-level
                // shuffle flag (persisted, not part of Media3 state) together with the repeat mode.
                // Re-read it here so the player page stays in sync with notification-driven changes.
                val persistedShuffle = loadAppShuffleEnabled()
                if (_shuffleEnabled.value != persistedShuffle) {
                    _shuffleEnabled.value = persistedShuffle
                }
            }

            override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
                _playbackSpeed.value = playbackParameters.speed
                _playbackPitch.value = playbackParameters.pitch
            }

            override fun onPlayerError(error: PlaybackException) {
                val song = _currentSong.value
                AppLogStore.error(
                    context,
                    "PlayerError",
                    "Playback failed code=${error.errorCodeName} song=${song?.title.orEmpty()} uri=${mediaController?.currentMediaItem?.localConfiguration?.uri}",
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
        mediaController?.addListener(playerListener!!)

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
            restoreSavedQueueIfNeeded()
        }
        refreshStateFromController()
        if (playWhenConnected) {
            playWhenConnected = false
            play()
        }
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        setPlaylist(songs, startIndex, honorShuffle = true, resetQueueLock = true)
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
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
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
        playlistBeforeShuffle = prepared.sourceOrderBeforeShuffle
        playlist.clear()
        playlist.addAll(queueSongs)
        _playlist.value = playlist.toList()

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
            _currentSong.value = queueSongs.getOrNull(safeIndex)
            _duration.value = queueSongs.getOrNull(safeIndex)?.duration ?: 0L
            _repeatMode.value = Player.REPEAT_MODE_ALL
            savePlaybackQueue(force = true)
            return
        }

        controller.apply {
            if (repeatMode == Player.REPEAT_MODE_OFF) {
                repeatMode = Player.REPEAT_MODE_ALL
            }
            setMediaItems(mediaItems, safeIndex, startPositionMs)
            prepare()
            play()
        }
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

    fun playResolvedFromVirtualQueue(songs: List<Song>, currentIndex: Int, resolvedSong: Song) {
        if (songs.isEmpty()) return
        cancelPendingSeekCommand()
        val safeIndex = currentIndex.coerceIn(songs.indices)
        externalSnapshotGuard = null
        suppressExternalSnapshotsUntilMs = 0L
        AppLogStore.debug(context, "PlayerQueue", "playResolvedVirtual size=${songs.size} index=$currentIndex title=${resolvedSong.title}")
        virtualPlaylistCurrentIndex = safeIndex
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
        resetPlayNextForwardStack()
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongKey = null
        artworkAppliedSongKey = null
        clearBluetoothMetadataPatchState()
        rememberCurrentSongResumePosition()
        playlist.clear()
        playlist.addAll(songs.mapIndexed { index, song -> if (index == safeIndex) resolvedSong else song })
        _playlist.value = playlist.toList()

        mediaController?.apply {
            setMediaItems(listOf(songToMediaItem(resolvedSong)), 0, resumePositionFor(resolvedSong))
            prepare()
            play()
        }
        _currentSong.value = resolvedSong
        _duration.value = resolvedSong.duration
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
        val controllerIndex = controller?.currentMediaItemIndex ?: C.INDEX_UNSET
        if (controllerIndex in playlist.indices) return controllerIndex
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
        resetPlayNextForwardStack()
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = false)
        rememberCurrentSongResumePosition()
        val resumePosition = resumePositionFor(playlist[index])
        mediaController?.seekTo(index, resumePosition)
        mediaController?.play()
        updateCurrentSong()
        savePlaybackQueue(force = true)
    }

    fun removeFromPlaylist(index: Int) {
        if (_queueLocked.value || index !in playlist.indices) return
        virtualPlaylistCurrentIndex = null
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
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
            _duration.value = _currentSong.value?.duration ?: 0L
        }
        savePlaybackQueue(force = true)
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        if (_queueLocked.value || fromIndex !in playlist.indices || toIndex !in playlist.indices || fromIndex == toIndex) return
        virtualPlaylistCurrentIndex = null
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
        resetPlayNextForwardStack()
        val movedSong = playlist.removeAt(fromIndex)
        playlist.add(toIndex, movedSong)
        _playlist.value = playlist.toList()
        mediaController?.let { controller ->
            if (fromIndex < controller.mediaItemCount && toIndex < controller.mediaItemCount) {
                controller.moveMediaItem(fromIndex, toIndex)
            }
            updateCurrentSong()
        } ?: run {
            _currentSong.value = playlist.firstOrNull()
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
        val current = resolveCurrentPlaybackSong(controller) ?: return false
        val shuffled = playlist.toMutableList()
        // A new seed on each invocation avoids pseudo-shuffle returning the same list twice.
        val random = Random(SystemClock.elapsedRealtimeNanos())
        var attempts = 0
        do {
            shuffled.shuffle(random)
            attempts++
        } while (shuffled == playlist && attempts < 8)
        // A two-item queue has a 50% chance of returning unchanged. Guarantee a visible reorder
        // whenever there is more than one item, including duplicate Song identities.
        if (shuffled == playlist) {
            Collections.rotate(shuffled, 1)
        }
        val currentIndex = shuffled.indexOfFirst { it.isSamePlaybackIdentity(current) }
        if (currentIndex < 0) return false
        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying
        reorderingPlaylistForShuffle = true
        try {
            clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
            // Random order is a concrete queue operation, not a playback-mode toggle. Preserve
            // the user's shuffle preference while Media3 itself remains in ordered traversal.
            controller.shuffleModeEnabled = false
            resetPlayNextForwardStack()
            applyControllerPlaylistOrder(controller, shuffled, currentIndex, positionMs, wasPlaying)
            playlist.clear()
            playlist.addAll(shuffled)
            _playlist.value = shuffled
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
        clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
        resetPlayNextForwardStack()
        playlist.clear()
        _playlist.value = emptyList()
        _currentSong.value = null
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        sessionMetadataSongKey = null
        artworkAppliedSongKey = null
        clearBluetoothMetadataPatchState()
        _currentPosition.value = 0L
        _duration.value = 0L
        _isPlaying.value = false
        _playWhenReady.value = false
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
        val index = playlist.indexOfFirst { it.isSamePlaybackIdentity(song) }
        if (index >= 0) {
            playQueueIndex(index)
        } else {
            setPlaylist(listOf(song), 0)
        }
    }

    fun togglePlayPause() {
        mediaController?.let {
            flushPendingSeekCommand()
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    /**
     * Live playback position straight from the controller. Must be called on the main
     * thread (the controller's looper). The [currentPosition] StateFlow is only sampled
     * periodically, so remote readers (MCP) get a stale value mid-track without this.
     */
    fun livePositionMs(): Long =
        mediaController?.currentPosition?.coerceAtLeast(0) ?: _currentPosition.value

    fun play() {
        val controller = mediaController
        if (controller == null) {
            playWhenConnected = true
            return
        }
        if (controller.mediaItemCount > 0) {
            controller.play()
            refreshStateFromController()
        }
    }

    fun pause() {
        flushPendingSeekCommand()
        mediaController?.pause()
    }

    fun skipToNext() {
        val controller = mediaController ?: return
        cancelPendingSeekCommand()
        rememberCurrentSongResumePosition()
        if (!performPendingShuffleReorder(trigger = "skipNext", seekToNextAfterReorder = true)) {
            controller.seekToNextMediaItem()
        }
        scheduleCurrentSongRefresh()
        savePlaybackQueue(force = true)
    }

    fun skipToPrevious() {
        val controller = mediaController ?: return
        cancelPendingSeekCommand()
        rememberCurrentSongResumePosition()
        val previousIndex = (controller.currentMediaItemIndex - 1).takeIf { it in playlist.indices }
        if (previousIndex != null) {
            controller.seekTo(previousIndex, resumePositionFor(playlist[previousIndex]))
        } else {
            controller.seekToPreviousMediaItem()
        }
        scheduleCurrentSongRefresh()
        savePlaybackQueue(force = true)
    }

    fun restartCurrent() {
        cancelPendingSeekCommand()
        mediaController?.run {
            seekToDefaultPosition(currentMediaItemIndex.coerceAtLeast(0))
            play()
        }
        _currentPosition.value = 0L
        updateCurrentSong()
        savePlaybackState(force = true)
    }

    fun restartSong(song: Song?) {
        val controller = mediaController ?: return
        cancelPendingSeekCommand()
        val target = song ?: _currentSong.value
        val targetIndex = target?.let { current ->
            playlist.indexOfFirst { it.isSamePlaybackIdentity(current) }
        } ?: -1
        val safeIndex = targetIndex.takeIf { it >= 0 } ?: controller.currentMediaItemIndex
        if (safeIndex < 0) return
        controller.seekToDefaultPosition(safeIndex)
        controller.play()
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
        applyPlaybackMode(
            shuffle = nextShuffle,
            repeatMode = if (nextShuffle) Player.REPEAT_MODE_ALL else mediaController?.repeatMode ?: _repeatMode.value,
            reorderForShuffleChange = true
        )
    }

    fun setShuffleMode(mode: Int) {
        shuffleMode = mode.coerceIn(
            SettingsManager.SHUFFLE_MODE_PSEUDO,
            SettingsManager.SHUFFLE_MODE_TRUE_RANDOM
        )
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
        val current = mediaController?.repeatMode ?: Player.REPEAT_MODE_OFF
        val next = when (current) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
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
            repeatMode = repeatMode,
            reorderForShuffleChange = needsQueueReorder
        )
    }

    private fun applyPlaybackMode(
        shuffle: Boolean,
        repeatMode: Int,
        reorderForShuffleChange: Boolean
    ) {
        val controller = mediaController ?: return
        val previousShuffle = _shuffleEnabled.value
        val queueOrderCanChange = reorderForShuffleChange && !_queueLocked.value
        var keepNativeShuffleUntilReorder = pendingShuffleReorder && shuffle && !_queueLocked.value
        if (queueOrderCanChange) {
            if (shuffle) {
                if (!previousShuffle || playlistBeforeShuffle == null) {
                    keepNativeShuffleUntilReorder = markPendingShuffleReorder()
                }
            } else {
                if (pendingShuffleReorder) {
                    clearPendingShuffleReorder(disableNativeShuffle = true, clearOriginalOrder = true)
                } else {
                    restorePlaylistOrderAfterShuffle()
                }
                keepNativeShuffleUntilReorder = false
            }
        }
        _shuffleEnabled.value = shuffle
        persistAppShuffleEnabled(shuffle)
        controller.shuffleModeEnabled = keepNativeShuffleUntilReorder
        if (controller.repeatMode != repeatMode) {
            controller.repeatMode = repeatMode
        } else {
            _repeatMode.value = repeatMode
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
        _currentPosition.value = mediaController?.currentPosition?.coerceAtLeast(0) ?: 0L
        _duration.value = mediaController?.duration?.coerceAtLeast(0) ?: 0L
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
        val controller = mediaController ?: return
        if (shouldIgnoreStaleControllerSong(controller)) return

        _isPlaying.value = controller.isPlaying
        _playWhenReady.value = controller.playWhenReady
        _playbackState.value = controller.playbackState
        _repeatMode.value = controller.repeatMode
        _playbackSpeed.value = controller.playbackParameters.speed
        _playbackPitch.value = controller.playbackParameters.pitch
        _currentPosition.value = controller.currentPosition.coerceAtLeast(0)
        _duration.value = controller.duration.coerceAtLeast(0)

        val mediaItemCount = controller.mediaItemCount
        if (mediaItemCount > 0 && playlist.isEmpty()) {
            val saved = loadSavedQueue()
            val currentItemSong = controller.currentMediaItem?.toSongFromMediaItemExtras()
                ?: controller.currentMediaItem?.toSong()
            val savedCurrentIndex = currentItemSong?.let { song ->
                saved?.songs?.indexOfFirst { it.isSamePlaybackIdentity(song) }
            } ?: -1
            if (saved != null && saved.songs.isNotEmpty() && (saved.songs.size == mediaItemCount || savedCurrentIndex >= 0)) {
                playlist.addAll(saved.songs)
                virtualPlaylistCurrentIndex = savedCurrentIndex.takeIf { mediaItemCount == 1 && it >= 0 }
            } else {
                for (index in 0 until mediaItemCount) {
                    playlist += controller.getMediaItemAt(index).toSong()
                }
            }
        }
        _playlist.value = playlist.toList()
        reconcileNativeShuffleState(controller)
        updateCurrentSong()
    }

    fun applyExternalPlaybackSnapshot(snapshot: PlaybackExternalSnapshot) {
        val snapshotSong = snapshot.mediaItem?.toSongFromMediaItemExtras()
            ?: snapshot.mediaItem?.toSong()
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

        _isPlaying.value = snapshot.isPlaying
        _playWhenReady.value = snapshot.playWhenReady
        _playbackState.value = snapshot.playbackState
        _repeatMode.value = snapshot.repeatMode
        _currentPosition.value = snapshot.positionMs.coerceAtLeast(0L)
        _duration.value = snapshot.durationMs.coerceAtLeast(0L)

        if (snapshotSong == null) {
            if (snapshot.mediaItemCount <= 0) {
                externalSnapshotGuard = null
                playlist.clear()
                _playlist.value = emptyList()
                _currentSong.value = null
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

        val index = snapshot.mediaItemIndex
        if (index in playlist.indices && !playlist[index].isSamePlaybackIdentity(snapshotSong)) {
            playlist[index] = snapshotSong
            _playlist.value = playlist.toList()
        } else if (playlist.isEmpty() && snapshot.mediaItemCount == 1) {
            playlist.add(snapshotSong)
            _playlist.value = playlist.toList()
        }

        val previousSong = _currentSong.value
        _currentSong.value = snapshotSong
        _duration.value = snapshot.durationMs.takeIf { it > 0L }
            ?: snapshotSong.duration.coerceAtLeast(0L)

        if (!previousSong.isSamePlaybackIdentity(snapshotSong)) {
            resetPlayNextForwardStack()
            notificationArtworkJob?.cancel()
            notificationArtworkJob = null
            artworkAppliedSongKey = null
            sessionMetadataSongKey = null
            resetBluetoothMetadataPatchStateForSong(snapshotSong)
        }

        refreshCurrentNotificationArtwork(snapshotSong)
    }

    fun updateCurrentSongMetadata(updatedSong: Song) {
        val controller = mediaController
        val current = _currentSong.value ?: return
        if (!current.isSamePlaybackIdentity(updatedSong)) return

        val playlistIndex = playlist.indexOfFirst { it.isSamePlaybackIdentity(current) }
        if (playlistIndex >= 0) {
            playlist[playlistIndex] = updatedSong
            _playlist.value = playlist.toList()
        }

        _currentSong.value = updatedSong
        notificationArtworkCache.remove(current.notificationArtworkKey())
        notificationArtworkCache.remove(updatedSong.notificationArtworkKey())
        missingNotificationArtworkKeys.remove(current.notificationArtworkKey())
        missingNotificationArtworkKeys.remove(updatedSong.notificationArtworkKey())
        notificationArtworkJob?.cancel()
        notificationArtworkJob = null
        artworkAppliedSongKey = null
        sessionMetadataSongKey = null

        if (controller != null && controller.currentMediaItemIndex >= 0) {
            refreshCurrentSessionMetadata(controller, updatedSong)
            refreshCurrentNotificationArtwork(updatedSong)
        }
        savePlaybackQueue(force = true)
    }

    private fun songToMediaItem(song: Song): MediaItem {
        val cachedArtwork = notificationArtworkCache.get(song.notificationArtworkKey())
        val builder = MediaItem.Builder()
            .setUri(song.playbackUri())
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                song.mediaMetadata(
                    artworkData = cachedArtwork,
                    includeArtworkUri = cachedArtwork != null
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
        includeArtworkUri: Boolean = true
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
                } else if (includeArtworkUri) {
                    artworkUriForMediaCenter()?.let(::setArtworkUri)
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
        val restoredSong = if (currentIndex in playlist.indices) {
            itemSong?.takeUnless { it.isSamePlaybackIdentity(playlistSong) } ?: playlistSong
        } else {
            itemSong
        }
        val previousSong = _currentSong.value
        if (currentItem?.isMetadataOnlyPatch() == true &&
            previousSong.isSamePlaybackIdentity(restoredSong)
        ) {
            _duration.value = controller.duration.coerceAtLeast(0)
            savePlaybackState(force = true)
            return
        }
        _currentSong.value = restoredSong
        _duration.value = controller.duration.coerceAtLeast(0)
        if (!previousSong.isSamePlaybackIdentity(restoredSong)) {
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
        withContext(Dispatchers.Main.immediate) {
            recreatePlaybackService(resumePlayback = true)
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

        val current = resolveCurrentPlaybackSong(controller)
        val targetIndex = original.indexOfFirst { it.isSamePlaybackIdentity(current) }
            .takeIf { it >= 0 }
            ?: controller.currentMediaItemIndex.coerceIn(0, original.lastIndex)
        val positionMs = controller.currentPosition.coerceAtLeast(0L)
        val wasPlaying = controller.isPlaying

        reorderingPlaylistForShuffle = true
        try {
            applyControllerPlaylistOrder(
                controller = controller,
                targetOrder = original,
                targetIndex = targetIndex,
                positionMs = positionMs,
                wasPlaying = wasPlaying
            )
            playlist.clear()
            playlist.addAll(original)
            _playlist.value = original
            updateCurrentSong()
            playlistBeforeShuffle = null
        } finally {
            reorderingPlaylistForShuffle = false
        }
    }

    private fun resolveCurrentPlaybackSong(controller: MediaController): Song? {
        val controllerIndex = controller.currentMediaItemIndex
        val itemSong = controller.currentMediaItem?.toSongFromMediaItemExtras()
            ?: controller.currentMediaItem?.toSong()
        if (controllerIndex in playlist.indices) {
            val playlistSong = playlist[controllerIndex]
            return itemSong?.takeUnless { it.isSamePlaybackIdentity(playlistSong) } ?: playlistSong
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
        // Always use setMediaItems (single IPC call) instead of N moveMediaItem calls
        // to avoid main-thread freezes with large playlists
        controller.setMediaItems(targetOrder.map(::songToMediaItem), safeIndex, positionMs)
        if (controller.playbackState == Player.STATE_IDLE) controller.prepare()
        if (wasPlaying) controller.play()
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
                includeArtworkUri = cachedArtwork != null
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

    private fun refreshCurrentNotificationArtwork(song: Song?) {
        val controller = mediaController ?: return
        val currentItem = controller.currentMediaItem ?: return
        val index = controller.currentMediaItemIndex
        val songKey = song?.notificationArtworkKey()
        if (song == null || index < 0 || artworkAppliedSongKey == songKey) return
        if (song.coverUrl.isNotBlank() || song.albumId > 0L) {
            if (song.coverUrl.isNotBlank()) {
                artworkAppliedSongKey = songKey
                return
            }
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
                artworkRepository.getCoverArt(song)
            }.getOrElse { error ->
                AppLogStore.warn(context, "PlayerArtwork", "Failed to load notification artwork for ${song.title}", error)
                null
            }
            if (data == null) {
                Log.d(TIMING_TAG, "artwork load finish mediaId=${song.id} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms missing")
                withContext(Dispatchers.Main.immediate) {
                    if (mediaController?.currentMediaItem?.matchesSong(song) == true) {
                        missingNotificationArtworkKeys += artworkKey
                    }
                }
                return@launch
            }
            if (data.size > MAX_NOTIFICATION_ARTWORK_BYTES) {
                Log.d(TIMING_TAG, "artwork load finish mediaId=${song.id} elapsed=${SystemClock.elapsedRealtime() - startedAt}ms oversized=${data.size}")
                AppLogStore.warn(context, "PlayerArtwork", "Skip oversized notification artwork for ${song.title}: ${data.size} bytes")
                withContext(Dispatchers.Main.immediate) {
                    if (mediaController?.currentMediaItem?.matchesSong(song) == true) {
                        missingNotificationArtworkKeys += artworkKey
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

    private fun replaceCurrentItemArtwork(
        controller: MediaController,
        index: Int,
        song: Song,
        artworkData: ByteArray
    ) {
        if (index != controller.currentMediaItemIndex) return
        val latestItem = controller.currentMediaItem ?: return
        if (!latestItem.matchesSong(song)) return
        runCatching {
            artworkAppliedSongKey = song.notificationArtworkKey()
            val targetMetadata = song.mediaMetadata(artworkData = artworkData)
                .withPatchedExtrasFrom(latestItem, PATCH_REASON_NOTIFICATION_ARTWORK)
            if (latestItem.mediaMetadata.matchesNotificationDisplay(targetMetadata)) {
                Log.d(TIMING_TAG, "artwork metadata already current mediaId=${song.id}")
                return@runCatching
            }
            controller.replaceMediaItem(
                index,
                latestItem.buildUpon()
                    .setMediaMetadata(targetMetadata)
                    .build()
            )
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

        val requestedIndex = saved.index.coerceIn(saved.songs.indices)
        val (queueSongs, safeIndex) = saved.songs.windowedForController(requestedIndex)
        playlist.clear()
        playlist.addAll(queueSongs)
        _playlist.value = playlist.toList()

        controller.setMediaItems(queueSongs.map(::songToMediaItem), safeIndex, saved.positionMs.coerceAtLeast(0L))
        controller.repeatMode = saved.repeatMode
        controller.shuffleModeEnabled = false
        controller.playbackParameters = PlaybackParameters(saved.speed.coerceIn(0.5f, 2f), saved.pitch.coerceIn(0.5f, 2f))
        controller.prepare()

        _currentSong.value = playlist.getOrNull(safeIndex)
        _currentPosition.value = saved.positionMs.coerceAtLeast(0L)
        _repeatMode.value = saved.repeatMode
        _shuffleEnabled.value = saved.shuffle
        _queueLocked.value = saved.queueLocked
        persistAppShuffleEnabled(saved.shuffle)
        _playbackSpeed.value = saved.speed
        _playbackPitch.value = saved.pitch
        if (saved.songs.size > LARGE_LIBRARY_SAFE_MODE_THRESHOLD) savePlaybackQueue(force = true)
    }

    private fun seedSavedPlaybackPreview() {
        val saved = loadSavedQueue() ?: return
        val index = saved.index.takeIf { it in saved.songs.indices } ?: return
        val current = saved.songs[index]
        _currentSong.value = current
        _currentPosition.value = saved.positionMs.coerceAtLeast(0L)
        _duration.value = current.duration.coerceAtLeast(0L)
        _repeatMode.value = saved.repeatMode
        _shuffleEnabled.value = saved.shuffle
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
        val index = controller?.currentMediaItemIndex?.takeIf { it >= 0 }
            ?: _currentSong.value?.let { current ->
                playlist.indexOfFirst { it.isSamePlaybackIdentity(current) }
            }
            ?: -1
        return PlaybackStateSnapshot(
            index = index.coerceAtLeast(0),
            positionMs = positionOverrideMs ?: controller?.currentPosition?.coerceAtLeast(0) ?: _currentPosition.value,
            repeatMode = controller?.repeatMode ?: _repeatMode.value,
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
            .apply()
    }

    private fun persistAppShuffleEnabled(enabled: Boolean) {
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_SHUFFLE, enabled)
            .apply()
    }

    private fun loadAppShuffleEnabled(): Boolean =
        context.getSharedPreferences(PLAYBACK_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_APP_SHUFFLE, _shuffleEnabled.value)

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
            coverUrl = metadata.artworkUri?.toString().orEmpty(),
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
        const val EXTRA_ONLINE_SOURCE = "com.ella.music.extra.ONLINE_SOURCE"
        const val EXTRA_ONLINE_ID = "com.ella.music.extra.ONLINE_ID"
        const val EXTRA_SONG_JSON = "com.ella.music.extra.SONG_JSON"
        const val MAX_NOTIFICATION_ARTWORK_BYTES = 2 * 1024 * 1024
        const val PLAYBACK_PREFS = "ella_playback_state"
        const val KEY_QUEUE = "queue"
        const val KEY_STATE = "state"
        const val KEY_APP_SHUFFLE = "app_shuffle_enabled"
        const val DECODER_MODE_FFMPEG_PREFER = 1
        const val DECODER_MODE_AUTO = 2
    }
}
