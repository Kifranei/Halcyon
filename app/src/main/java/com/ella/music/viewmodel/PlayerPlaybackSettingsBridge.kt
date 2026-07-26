package com.ella.music.viewmodel

import android.app.Application
import com.ella.music.data.AppLogStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.repository.MusicRepository
import com.ella.music.player.ExoPlayerManager
import com.ella.music.player.PlaybackOutputSettings
import com.ella.music.player.PlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings-to-playback bridge listeners moved verbatim out of [PlayerViewModel].
 *
 * Each `initXxx()` here was a same-named private function in PlayerViewModel and must keep being
 * invoked from its init block at the exact same position, so launch ordering on the scope's
 * dispatcher is unchanged. None of these touch PlayerViewModel's private lyric/UI state: they only
 * observe [SettingsManager] / [PlaybackService] flows and forward to [ExoPlayerManager].
 */
internal class PlayerPlaybackSettingsBridge(
    private val application: Application,
    private val scope: CoroutineScope,
    private val settingsManager: SettingsManager,
    private val playerManager: ExoPlayerManager,
    private val repository: MusicRepository
) {
    private var appliedAudioFocusDisabled: Boolean? = null
    private var appliedPlaybackOutputSettings: PlaybackOutputSettings? = null

    fun initShuffleMode() {
        scope.launch {
            settingsManager.shuffleMode.distinctUntilChanged().collect { mode ->
                playerManager.setShuffleMode(mode)
            }
        }
    }

    fun initPlayNextMode() {
        scope.launch {
            settingsManager.playNextMode.distinctUntilChanged().collect { mode ->
                playerManager.setPlayNextMode(mode)
            }
        }
    }

    fun initResumePlaybackPosition() {
        scope.launch {
            settingsManager.resumePlaybackPosition.distinctUntilChanged().collect { enabled ->
                playerManager.setResumePlaybackPositionEnabled(enabled)
            }
        }
    }

    fun initAudioFocusMode() {
        scope.launch {
            settingsManager.audioFocusDisabled.distinctUntilChanged().collect { disabled ->
                if (appliedAudioFocusDisabled == null) {
                    appliedAudioFocusDisabled = disabled
                    return@collect
                }
                if (appliedAudioFocusDisabled == disabled) return@collect
                appliedAudioFocusDisabled = disabled
                playerManager.recreatePlaybackService()
                AppLogStore.info(application, "PlayerDecoder", "Audio focus disabled changed to $disabled")
            }
        }
    }

    fun initPlaybackOutputSettings() {
        scope.launch {
            settingsManager.playbackOutputSettings.distinctUntilChanged().collect { settings ->
                if (appliedPlaybackOutputSettings == null) {
                    appliedPlaybackOutputSettings = settings
                    return@collect
                }
                if (appliedPlaybackOutputSettings == settings) return@collect
                appliedPlaybackOutputSettings = settings
                playerManager.recreatePlaybackService()
                AppLogStore.info(
                    application,
                    "PlayerDecoder",
                    "Playback output changed: backend=${settings.backend}, bitDepth=${settings.bitDepth}, sampleRate=${settings.sampleRate}"
                )
            }
        }
    }

    fun initReplayGain() {
        scope.launch {
            combine(
                settingsManager.replayGainMode.distinctUntilChanged(),
                playerManager.currentSong
            ) { mode, song -> mode to song }
                .collectLatest { (mode, song) ->
                    val volume = if (mode != SettingsManager.REPLAY_GAIN_OFF && song != null) {
                        withContext(Dispatchers.IO) {
                            repository.getReplayGain(song, mode)
                        }.toReplayGainVolume()
                    } else {
                        1f
                    }
                    playerManager.setReplayGainVolume(volume)
                }
        }
    }

    fun initBluetoothAutoPlay() {
        scope.launch {
            PlaybackService.bluetoothConnectEvent.collect {
                if (playerManager.currentSong.value != null && !playerManager.isPlaying.value) {
                    playerManager.play()
                    AppLogStore.info(application, "BtAutoPlay", "Resumed existing queue on Bluetooth connect")
                } else if (playerManager.currentSong.value == null && playerManager.hasSavedQueue()) {
                    playerManager.play()
                    AppLogStore.info(application, "BtAutoPlay", "Restored saved queue on Bluetooth connect")
                }
            }
        }
    }

    fun initExternalPlaybackSync() {
        scope.launch {
            PlaybackService.externalPlaybackSnapshot.collectLatest { snapshot ->
                snapshot ?: return@collectLatest
                playerManager.ensureConnected(refreshStateIfConnected = false)
                playerManager.applyExternalPlaybackSnapshot(snapshot)
            }
        }
        scope.launch {
            PlaybackService.externalPlaybackModeEvent.collect { snapshot ->
                playerManager.ensureConnected(refreshStateIfConnected = false)
                playerManager.applyExternalPlaybackMode(
                    shuffle = snapshot.shuffle,
                    repeatMode = snapshot.repeatMode
                )
            }
        }
    }
}
