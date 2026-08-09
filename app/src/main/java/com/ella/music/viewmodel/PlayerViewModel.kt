package com.ella.music.viewmodel

import android.app.Application
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ella.music.data.AppLogStore
import com.ella.music.data.lastfm.LastFmHistoryStore
import com.ella.music.data.lastfm.ListeningHistorySource
import com.ella.music.data.PlaylistStore
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.model.shiftedBy
import com.ella.music.data.remote.OpenSubsonicCollectionsStore
import com.ella.music.data.remote.NavidromeService
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.SavedRemoteServer
import com.ella.music.data.remote.isSubsonicLike
import com.ella.music.data.repository.CoverUsage
import com.ella.music.data.repository.MusicRepository
import com.ella.music.player.DesktopLyricBridge
import com.ella.music.player.ExoPlayerManager
import com.ella.music.player.LyricGetterBridge
import com.ella.music.player.LiveLyricNotificationBridge
import com.ella.music.player.buildLiveLyricSecondaryText
import com.ella.music.player.buildLiveLyricNotificationText
import com.ella.music.player.LyriconBridge
import com.ella.music.player.MediaNotificationLyricPatchPolicy
import com.ella.music.player.PlaybackService
import com.ella.music.player.PlaybackWidgetUpdater
import com.ella.music.player.SuperLyricBridge
import com.ella.music.player.TickerBridge
import com.ella.music.player.XiaomiSuperIslandLyricBridge
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val LYRIC_POSITION_BACKWARD_DRIFT_TOLERANCE_MS = 600L
// Compose lyrics interpolate between position samples on the display clock. 10 Hz is therefore
// visually smooth while avoiding a 20 Hz controller query / bridge dispatch loop all day.
private const val PLAYBACK_POSITION_UPDATE_INTERVAL_MS = 100L
private const val SEEK_EXTERNAL_LYRIC_SYNC_DEBOUNCE_MS = 80L
private const val LIVE_UPDATE_ARTWORK_SIZE = 256

private const val DECODER_MODE_SYSTEM = 0
private const val DECODER_MODE_AUTO = 2

class PlayerViewModel(application: Application) : AndroidViewModel(application) {
    private data class LiveLyricNotificationState(
        val songKey: String,
        val lineIndex: Int,
        val wordIndex: Int,
        val mode: Int,
        val displayMode: Int,
        val secondaryMode: Int,
        val lyric: String,
        val compactLyric: String,
        val allowLongCompactLyric: Boolean,
        val preserveCompactLyric: Boolean,
        val secondaryLyric: String?
    )

    companion object {
        private val cleanupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    private val repository = MusicRepository.getInstance(application)
    val playerManager = ExoPlayerManager(application)
    val settingsManager = SettingsManager.getInstance(application)
    val lyriconBridge = LyriconBridge(application)
    val tickerBridge = TickerBridge(application)
    private val liveLyricNotificationBridge = LiveLyricNotificationBridge(application)
    private val xiaomiSuperIslandLyricBridge = XiaomiSuperIslandLyricBridge(application, viewModelScope)
    val desktopLyricBridge = DesktopLyricBridge(application)
    val superLyricBridge = SuperLyricBridge()
    val lyricGetterBridge = LyricGetterBridge(application)
    private val playlistStore = PlaylistStore.getInstance(application)
    private val openSubsonicCollectionsStore = OpenSubsonicCollectionsStore.getInstance(application)
    private val playbackStatsStore = PlaybackStatsStore.getInstance(application)
    private val lastFmHistoryStore = LastFmHistoryStore.getInstance(application)
    private val navidromeService = NavidromeService(application)
    private val playbackStatsTracker = PlayerPlaybackStatsTracker(
        playbackStatsStore = playbackStatsStore,
        onPlayCounted = { song ->
            // Never hold the 10 Hz position/lyrics loop on a network request.
            viewModelScope.launch(Dispatchers.IO) {
                val config = when (song.onlineSource) {
                    RemoteMusicProvider.Navidrome.id -> settingsManager.navidromeConfig.first()
                    RemoteMusicProvider.OpenSubsonic.id -> settingsManager.openSubsonicConfig.first()
                    else -> null
                }
                if (config?.isConfigured == true && song.onlineId.isNotBlank()) {
                    runCatching { navidromeService.scrobble(config, song.onlineId) }
                        .onFailure { AppLogStore.warn(application, "SubsonicScrobble", "Failed to scrobble ${song.title}", it) }
                }
            }
        },
        onLastFmScrobbleEligible = { song, startedAt ->
            viewModelScope.launch(Dispatchers.IO) {
                val source = ListeningHistorySource.fromPreference(settingsManager.listeningHistorySource.first())
                if (source.usesLastFm) {
                    lastFmHistoryStore.enqueueScrobble(song, startedAt)
                }
            }
        }
    )
    private val lazyOnlineQueueController = PlayerLazyOnlineQueueController(viewModelScope, playerManager)
    private val playbackSettingsBridge = PlayerPlaybackSettingsBridge(
        application = application,
        scope = viewModelScope,
        settingsManager = settingsManager,
        playerManager = playerManager,
        repository = repository
    )

    val currentSong: StateFlow<Song?> = playerManager.currentSong
    val isPlaying: StateFlow<Boolean> = playerManager.isPlaying
    val playWhenReady: StateFlow<Boolean> = playerManager.playWhenReady
    val currentPosition: StateFlow<Long> = playerManager.currentPosition
    val duration: StateFlow<Long> = playerManager.duration
    val shuffleEnabled: StateFlow<Boolean> = playerManager.shuffleEnabled
    val queueLocked: StateFlow<Boolean> = playerManager.queueLocked
    val repeatMode: StateFlow<Int> = playerManager.repeatMode
    val playbackSpeed: StateFlow<Float> = playerManager.playbackSpeed
    val playbackPitch: StateFlow<Float> = playerManager.playbackPitch
    val playlist: StateFlow<List<Song>> = playerManager.playlistFlow
    val userPlaylists: StateFlow<List<UserPlaylist>> = playlistStore.playlists
    val favoriteSongKeys: StateFlow<Set<String>> = combine(
        playlistStore.playlists.map { playlists ->
            playlists
                .firstOrNull { it.isFavorites }
                ?.songs
                ?.mapTo(mutableSetOf()) { it.key }
                ?: emptySet()
        },
        openSubsonicCollectionsStore.favoriteSongKeys
    ) { localFavorites, remoteFavorites ->
        localFavorites + remoteFavorites
    }
        .stateIn(viewModelScope, SharingStarted.Eagerly, playlistStore.favoriteSongKeys())

    private val _rawLyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    private val _lyrics = MutableStateFlow<List<LyricLine>>(emptyList())
    val lyrics: StateFlow<List<LyricLine>> = _lyrics.asStateFlow()
    private val _lyricsLoading = MutableStateFlow(false)
    val lyricsLoading: StateFlow<Boolean> = _lyricsLoading.asStateFlow()
    private val _currentLyricOffsetMs = MutableStateFlow(0L)
    val currentLyricOffsetMs: StateFlow<Long> = _currentLyricOffsetMs.asStateFlow()

    fun cycleRemoteStreamQuality() {
        val song = currentSong.value ?: return
        val provider = RemoteMusicProvider.fromId(song.onlineSource)
        if (!provider.isSubsonicLike) return
        viewModelScope.launch {
            val activeId: String
            val servers: List<SavedRemoteServer>
            when (provider) {
                RemoteMusicProvider.Navidrome -> {
                    activeId = settingsManager.navidromeActiveServerId.first()
                    servers = settingsManager.navidromeServers.first()
                }
                RemoteMusicProvider.OpenSubsonic -> {
                    activeId = settingsManager.openSubsonicActiveServerId.first()
                    servers = settingsManager.openSubsonicServers.first()
                }
                else -> return@launch
            }
            val server = servers.firstOrNull { it.id == activeId } ?: return@launch
            val nextBitRate = when (server.config.streamMaxBitRate) {
                0 -> 320
                320 -> 192
                192 -> 128
                else -> 0
            }
            val updatedServer = server.copy(config = server.config.copy(streamMaxBitRate = nextBitRate))
            when (provider) {
                RemoteMusicProvider.Navidrome -> settingsManager.upsertNavidromeServer(updatedServer)
                RemoteMusicProvider.OpenSubsonic -> settingsManager.upsertOpenSubsonicServer(updatedServer)
            }

            val currentIndex = playlist.value.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
            val position = currentPosition.value
            val wasPlaying = isPlaying.value
            val updatedQueue = playlist.value.map { queued ->
                if (queued.onlineSource == provider.id && queued.onlineId.isNotBlank()) {
                    queued.copy(path = navidromeService.streamUrl(updatedServer.config, queued.onlineId, nextBitRate))
                } else {
                    queued
                }
            }
            playerManager.replacePlaylistPreservingQueueLock(updatedQueue, currentIndex)
            playerManager.seekTo(position)
            if (!wasPlaying) playerManager.pause()
        }
    }

    private val _lyricFormatAvailability = MutableStateFlow(MusicRepository.LyricFormatAvailability())
    val lyricFormatAvailability: StateFlow<MusicRepository.LyricFormatAvailability> =
        _lyricFormatAvailability.asStateFlow()

    private val _preferTtmlLyrics = MutableStateFlow<Boolean?>(null)
    val preferTtmlLyrics: StateFlow<Boolean?> = _preferTtmlLyrics.asStateFlow()

    private val _currentLyricIndex = MutableStateFlow(-1)
    val currentLyricIndex: StateFlow<Int> = _currentLyricIndex.asStateFlow()

    private val _showLyrics = MutableStateFlow(false)
    val showLyrics: StateFlow<Boolean> = _showLyrics.asStateFlow()

    private val _showLyricTranslation = MutableStateFlow(true)
    val showLyricTranslation: StateFlow<Boolean> = _showLyricTranslation.asStateFlow()

    private val _showLyricPronunciation = MutableStateFlow(true)
    val showLyricPronunciation: StateFlow<Boolean> = _showLyricPronunciation.asStateFlow()

    private val _locateCurrentSongRequest = MutableStateFlow(0)
    val locateCurrentSongRequest: StateFlow<Int> = _locateCurrentSongRequest.asStateFlow()

    private val sleepTimerController = PlayerSleepTimerController(
        scope = viewModelScope,
        currentSong = { currentSong.value },
        duration = { duration.value },
        currentPosition = { currentPosition.value },
        onPause = { playerManager.pause() }
    )
    val sleepTimerEndRealtimeMs: StateFlow<Long?> = sleepTimerController.sleepTimerEndRealtimeMs
    val stopAfterCurrentEnabled: StateFlow<Boolean> = sleepTimerController.stopAfterCurrentEnabled

    private var positionUpdateJob: Job? = null
    private var seekExternalLyricSyncJob: Job? = null
    private var lastSentPlayingState: Boolean? = null
    private var lastTickerPayload: Pair<String, String?>? = null
    private var lastLiveUpdateLyricPayload: LiveLyricNotificationState? = null
    private var liveUpdateArtwork: Bitmap? = null
    private var bluetoothLyricEnabled = false
    private var bluetoothLyricTranslationEnabled = true
    private var bluetoothLyricPronunciationEnabled = false
    private var lyriconTranslationEnabled = true
    private var lyriconPronunciationEnabled = false
    private var samsungFloatingLyricTranslationEnabled = false
    private var statusBarAllowPhoneticEnabled = false
    private var tickerHideNotificationEnabled = false
    private var liveUpdateLyricEnabled = false
    private var liveUpdateLyricMode = SettingsManager.LIVE_UPDATE_LYRIC_MODE_ORIGINAL
    private var liveUpdateLyricDisplayMode = SettingsManager.LIVE_UPDATE_LYRIC_DISPLAY_MODE_COMPACT
    private var liveUpdateLyricSecondaryMode = SettingsManager.LIVE_UPDATE_LYRIC_SECONDARY_MODE_SONG
    private var xiaomiSuperIslandLyricEnabled = false
    private var desktopLyricHideWhenPausedEnabled = false
    private var desktopLyricStatusBarModeEnabled = false
    private var desktopLyricStatusBarHideWhenPausedEnabled = false
    private var superLyricTranslationEnabled = true
    private var superLyricPronunciationEnabled = false
    private var lyricSourceMode = SettingsManager.LYRIC_SOURCE_AUTO
    private var lyricOffsetOverrides = emptyMap<String, Long>()
    private var lyricBlacklistRules = emptyList<LyricBlacklistRule>()
    private var hideLyricExtraInfo = true
    private var appliedDecoderMode: Int? = null
    private var appliedLyricSourceMode: Int? = null
    private var previousButtonAction = SettingsManager.PREVIOUS_BUTTON_PREVIOUS
    private var manualSeekAfterPreviousButton = false
    private var lastBluetoothLyricPayload: Pair<String, String?>? = null
    private var bluetoothLyricRetryJob: Job? = null
    private var externalLyricResendJob: Job? = null
    private var loadedLyricSongKey: String? = null
    private var lastLyricPositionSongKey: String? = null
    private var lastLyricPositionMs: Long = 0L
    private var suppressLeadingZeroLyric = false

    init {
        playerManager.connect()
        startPositionUpdates()
        observeCurrentSong()
        observePlayState()
        initLyricon()
        initTicker()
        initLiveUpdateLyric()
        initXiaomiSuperIslandLyric()
        initDesktopLyric()
        initSuperLyric()
        initLyricGetter()
        initLyricPageTranslation()
        initBluetoothLyric()
        playbackSettingsBridge.initShuffleMode()
        playbackSettingsBridge.initPlayNextMode()
        initPreviousButtonAction()
        playbackSettingsBridge.initResumePlaybackPosition()
        initDecoderMode()
        playbackSettingsBridge.initAudioFocusMode()
        playbackSettingsBridge.initPlaybackOutputSettings()
        playbackSettingsBridge.initReplayGain()
        initLyricSourceMode()
        initLyricLineBlacklist()
        initLyricExtraInfoFilter()
        initLyricHeaderTagFilter()
        initLyricOffsetOverrides()
        playbackSettingsBridge.initBluetoothAutoPlay()
        playbackSettingsBridge.initExternalPlaybackSync()
        lazyOnlineQueueController.observePlaybackEnd()
    }

    private fun initLyricon() {
        viewModelScope.launch {
            val enabled = settingsManager.lyriconEnabled.first()
            lyriconTranslationEnabled = settingsManager.lyriconTranslation.first()
            lyriconPronunciationEnabled = settingsManager.lyriconPronunciation.first()
            if (lyriconTranslationEnabled && lyriconPronunciationEnabled) {
                lyriconTranslationEnabled = false
                settingsManager.setLyriconTranslation(false)
            }
            lyriconBridge.setSecondaryMode(currentLyriconSecondaryMode())
            lyriconBridge.setEnabled(enabled)
            if (enabled) resendExternalLyrics()
        }
        viewModelScope.launch {
            settingsManager.lyriconTranslation.distinctUntilChanged().collect { enabled ->
                lyriconTranslationEnabled = enabled
                if (enabled && lyriconPronunciationEnabled) {
                    lyriconPronunciationEnabled = false
                    settingsManager.setLyriconPronunciation(false)
                }
                lyriconBridge.setSecondaryMode(currentLyriconSecondaryMode())
                if (lyriconBridge.isEnabled()) resendExternalLyrics(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.lyriconPronunciation.distinctUntilChanged().collect { enabled ->
                lyriconPronunciationEnabled = enabled
                if (enabled && lyriconTranslationEnabled) {
                    lyriconTranslationEnabled = false
                    settingsManager.setLyriconTranslation(false)
                }
                lyriconBridge.setSecondaryMode(currentLyriconSecondaryMode())
                if (lyriconBridge.isEnabled()) resendExternalLyrics(force = true)
            }
        }
    }

    private fun initTicker() {
        viewModelScope.launch {
            val enabled = settingsManager.tickerEnabled.first()
            val hideNotification = true
            if (settingsManager.tickerHideNotification.first() != hideNotification) {
                settingsManager.setTickerHideNotification(hideNotification)
            }
            tickerHideNotificationEnabled = hideNotification
            samsungFloatingLyricTranslationEnabled = settingsManager.samsungFloatingLyricTranslation.first()
            statusBarAllowPhoneticEnabled = settingsManager.statusBarAllowPhonetic.first()
            tickerBridge.setHideNotification(hideNotification)
            tickerBridge.setHeadsUpLyricsEnabled(settingsManager.tickerHeadsUpLyrics.first())
            tickerBridge.setEnabled(enabled)
            if (enabled) resendTickerLyric()
        }
        viewModelScope.launch {
            settingsManager.tickerHideNotification.distinctUntilChanged().collect { enabled ->
                if (!enabled) {
                    settingsManager.setTickerHideNotification(true)
                    return@collect
                }
                tickerHideNotificationEnabled = true
                tickerBridge.setHideNotification(true)
                lastTickerPayload = null
                if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.tickerHeadsUpLyrics.distinctUntilChanged().collect { enabled ->
                tickerBridge.setHeadsUpLyricsEnabled(enabled)
                lastTickerPayload = null
                if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.samsungFloatingLyricTranslation.distinctUntilChanged().collect { enabled ->
                samsungFloatingLyricTranslationEnabled = enabled
                if (samsungFloatingLyricTranslationEnabled && statusBarAllowPhoneticEnabled) {
                    statusBarAllowPhoneticEnabled = false
                    settingsManager.setStatusBarAllowPhonetic(false)
                }
                lastTickerPayload = null
                if (tickerBridge.isEnabled()) resendTickerLyric()
            }
        }
        viewModelScope.launch {
            settingsManager.statusBarAllowPhonetic.distinctUntilChanged().collect { enabled ->
                statusBarAllowPhoneticEnabled = enabled
                if (enabled && samsungFloatingLyricTranslationEnabled) {
                    samsungFloatingLyricTranslationEnabled = false
                    settingsManager.setSamsungFloatingLyricTranslation(false)
                }
                lastTickerPayload = null
                if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
            }
        }
    }

    private fun initLiveUpdateLyric() {
        viewModelScope.launch {
            liveUpdateLyricEnabled = settingsManager.liveUpdateLyricEnabled.first()
            liveUpdateLyricMode = settingsManager.liveUpdateLyricMode.first()
            liveUpdateLyricDisplayMode = settingsManager.liveUpdateLyricDisplayMode.first()
            liveUpdateLyricSecondaryMode = settingsManager.liveUpdateLyricSecondaryMode.first()
            liveLyricNotificationBridge.setEnabled(liveUpdateLyricEnabled)
            if (liveUpdateLyricEnabled) resendLiveUpdateLyric(force = true)
        }
        viewModelScope.launch {
            settingsManager.liveUpdateLyricEnabled.distinctUntilChanged().collect { enabled ->
                liveUpdateLyricEnabled = enabled
                lastLiveUpdateLyricPayload = null
                liveLyricNotificationBridge.setEnabled(enabled)
                if (enabled) resendLiveUpdateLyric(force = true) else liveLyricNotificationBridge.clear()
            }
        }
        viewModelScope.launch {
            settingsManager.liveUpdateLyricMode.distinctUntilChanged().collect { mode ->
                liveUpdateLyricMode = mode
                lastLiveUpdateLyricPayload = null
                if (liveUpdateLyricEnabled) resendLiveUpdateLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.liveUpdateLyricDisplayMode.distinctUntilChanged().collect { mode ->
                liveUpdateLyricDisplayMode = mode
                lastLiveUpdateLyricPayload = null
                if (liveUpdateLyricEnabled) resendLiveUpdateLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.liveUpdateLyricSecondaryMode.distinctUntilChanged().collect { mode ->
                liveUpdateLyricSecondaryMode = mode
                lastLiveUpdateLyricPayload = null
                if (liveUpdateLyricEnabled) resendLiveUpdateLyric(force = true)
            }
        }
    }

    private fun initXiaomiSuperIslandLyric() {
        viewModelScope.launch {
            settingsManager.xiaomiSuperIslandSettings.distinctUntilChanged().collect { settings ->
                xiaomiSuperIslandLyricBridge.setSettings(settings)
                if (xiaomiSuperIslandLyricEnabled) resendXiaomiSuperIslandLyric()
            }
        }
        viewModelScope.launch {
            xiaomiSuperIslandLyricEnabled = settingsManager.xiaomiSuperIslandLyricEnabled.first()
            xiaomiSuperIslandLyricBridge.setEnabled(xiaomiSuperIslandLyricEnabled)
            if (xiaomiSuperIslandLyricEnabled) resendXiaomiSuperIslandLyric()
        }
        viewModelScope.launch {
            settingsManager.xiaomiSuperIslandLyricEnabled.distinctUntilChanged().collect { enabled ->
                xiaomiSuperIslandLyricEnabled = enabled
                xiaomiSuperIslandLyricBridge.setEnabled(enabled)
                if (enabled) resendXiaomiSuperIslandLyric()
            }
        }
    }

    private fun initDesktopLyric() {
        viewModelScope.launch {
            desktopLyricStatusBarModeEnabled = settingsManager.desktopLyricStatusBarMode.first()
            desktopLyricHideWhenPausedEnabled = settingsManager.desktopLyricHideWhenPaused.first()
            desktopLyricStatusBarHideWhenPausedEnabled = settingsManager.desktopLyricStatusBarHideWhenPaused.first()
        }
        viewModelScope.launch {
            settingsManager.desktopLyricEnabled.distinctUntilChanged().collect { enabled ->
                desktopLyricBridge.setEnabled(enabled)
                if (enabled) {
                    resendDesktopLyric()
                }
            }
        }
        viewModelScope.launch {
            settingsManager.desktopLyricHideWhenPaused.distinctUntilChanged().collect { enabled ->
                desktopLyricHideWhenPausedEnabled = enabled
                if (!desktopLyricStatusBarModeEnabled && enabled && !isPlaying.value) {
                    desktopLyricBridge.clearLyric()
                } else {
                    resendDesktopLyric()
                }
            }
        }
        viewModelScope.launch {
            settingsManager.desktopLyricStatusBarMode.distinctUntilChanged().collect { statusBarMode ->
                desktopLyricStatusBarModeEnabled = statusBarMode
                if (activeDesktopLyricHideWhenPaused() && !isPlaying.value) desktopLyricBridge.clearLyric()
                else resendDesktopLyric()
            }
        }
        viewModelScope.launch {
            settingsManager.desktopLyricStatusBarHideWhenPaused.distinctUntilChanged().collect { enabled ->
                desktopLyricStatusBarHideWhenPausedEnabled = enabled
                if (desktopLyricStatusBarModeEnabled && enabled && !isPlaying.value) desktopLyricBridge.clearLyric()
                else resendDesktopLyric()
            }
        }
    }

    private fun activeDesktopLyricHideWhenPaused(): Boolean =
        if (desktopLyricStatusBarModeEnabled) desktopLyricStatusBarHideWhenPausedEnabled
        else desktopLyricHideWhenPausedEnabled

    private fun initSuperLyric() {
        viewModelScope.launch {
            val enabled = settingsManager.superLyricEnabled.first()
            superLyricTranslationEnabled = settingsManager.superLyricTranslation.first()
            superLyricPronunciationEnabled = settingsManager.superLyricPronunciation.first()
            if (superLyricTranslationEnabled && superLyricPronunciationEnabled) {
                superLyricTranslationEnabled = false
                settingsManager.setSuperLyricTranslation(false)
            }
            superLyricBridge.setSecondaryMode(currentSuperLyricSecondaryMode())
            superLyricBridge.setEnabled(enabled)
            if (enabled) resendSuperLyric()
        }
        viewModelScope.launch {
            settingsManager.superLyricTranslation.distinctUntilChanged().collect { enabled ->
                superLyricTranslationEnabled = enabled
                if (enabled && superLyricPronunciationEnabled) {
                    superLyricPronunciationEnabled = false
                    settingsManager.setSuperLyricPronunciation(false)
                }
                superLyricBridge.setSecondaryMode(currentSuperLyricSecondaryMode())
                if (superLyricBridge.isEnabled()) resendSuperLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.superLyricPronunciation.distinctUntilChanged().collect { enabled ->
                superLyricPronunciationEnabled = enabled
                if (enabled && superLyricTranslationEnabled) {
                    superLyricTranslationEnabled = false
                    settingsManager.setSuperLyricTranslation(false)
                }
                superLyricBridge.setSecondaryMode(currentSuperLyricSecondaryMode())
                if (superLyricBridge.isEnabled()) resendSuperLyric(force = true)
            }
        }
    }

    private fun initLyricGetter() {
        viewModelScope.launch {
            settingsManager.lyricGetterEnabled.distinctUntilChanged().collect { enabled ->
                lyricGetterBridge.setEnabled(enabled)
                if (enabled) resendLyricGetter(force = true)
            }
        }
    }

    private fun currentLyriconSecondaryMode(): LyriconBridge.SecondaryMode =
        lyriconSecondaryMode(
            translationEnabled = lyriconTranslationEnabled,
            pronunciationEnabled = lyriconPronunciationEnabled
        )

    private fun currentSuperLyricSecondaryMode(): SuperLyricBridge.SecondaryMode =
        superLyricSecondaryMode(
            translationEnabled = superLyricTranslationEnabled,
            pronunciationEnabled = superLyricPronunciationEnabled
        )

    private fun initBluetoothLyric() {
        viewModelScope.launch {
            bluetoothLyricTranslationEnabled = settingsManager.bluetoothLyricTranslation.first()
            bluetoothLyricPronunciationEnabled = settingsManager.bluetoothLyricPronunciation.first()
            if (bluetoothLyricTranslationEnabled && bluetoothLyricPronunciationEnabled) {
                bluetoothLyricTranslationEnabled = false
                settingsManager.setBluetoothLyricTranslation(false)
            }
        }
        viewModelScope.launch {
            settingsManager.bluetoothLyricEnabled.distinctUntilChanged().collect { enabled ->
                bluetoothLyricEnabled = enabled
                lastBluetoothLyricPayload = null

                if (enabled) {
                    resendBluetoothLyric()
                } else {
                    bluetoothLyricRetryJob?.cancel()
                    playerManager.clearBluetoothLyric()
                }
            }
        }
        viewModelScope.launch {
            settingsManager.bluetoothLyricTranslation.distinctUntilChanged().collect { enabled ->
                bluetoothLyricTranslationEnabled = enabled
                if (enabled && bluetoothLyricPronunciationEnabled) {
                    bluetoothLyricPronunciationEnabled = false
                    settingsManager.setBluetoothLyricPronunciation(false)
                }
                lastBluetoothLyricPayload = null
                if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
            }
        }
        viewModelScope.launch {
            settingsManager.bluetoothLyricPronunciation.distinctUntilChanged().collect { enabled ->
                bluetoothLyricPronunciationEnabled = enabled
                if (enabled && bluetoothLyricTranslationEnabled) {
                    bluetoothLyricTranslationEnabled = false
                    settingsManager.setBluetoothLyricTranslation(false)
                }
                lastBluetoothLyricPayload = null
                if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
            }
        }
    }

    private fun initPreviousButtonAction() {
        viewModelScope.launch {
            settingsManager.previousButtonAction.distinctUntilChanged().collect { action ->
                previousButtonAction = action.coerceIn(
                    SettingsManager.PREVIOUS_BUTTON_PREVIOUS,
                    SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT
                )
            }
        }
    }

    private fun initDecoderMode() {
        viewModelScope.launch {
            settingsManager.decoderMode.collect { mode ->
                if (appliedDecoderMode == null) {
                    appliedDecoderMode = mode
                    return@collect
                }
                if (appliedDecoderMode == mode) return@collect
                appliedDecoderMode = mode
                if (mode != DECODER_MODE_AUTO) {
                    PlaybackService.decoderModeOverride.value = null
                }
                playerManager.recreatePlaybackService()
                AppLogStore.info(getApplication(), "PlayerDecoder", "Decoder mode changed to $mode")
            }
        }
    }

    private fun initLyricSourceMode() {
        viewModelScope.launch {
            settingsManager.lyricSourceMode.distinctUntilChanged().collect { mode ->
                val safeMode = mode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
                if (appliedLyricSourceMode == null) {
                    appliedLyricSourceMode = safeMode
                    lyricSourceMode = safeMode
                    return@collect
                }
                if (appliedLyricSourceMode == safeMode) return@collect
                appliedLyricSourceMode = safeMode
                lyricSourceMode = safeMode
                currentSong.value?.let { reloadLyrics(it, force = true) }
            }
        }
    }

    private fun initLyricOffsetOverrides() {
        viewModelScope.launch {
            settingsManager.lyricOffsetOverrides.distinctUntilChanged().collect { overrides ->
                lyricOffsetOverrides = overrides
                applyCurrentLyricOffset(notifyExternal = true)
            }
        }
    }

    private fun initLyricLineBlacklist() {
        viewModelScope.launch {
            var initialized = false
            settingsManager.lyricLineBlacklist.distinctUntilChanged().collect { rules ->
                lyricBlacklistRules = rules.map(::LyricBlacklistRule)
                if (!initialized) {
                    initialized = true
                    applyCurrentLyricOffset(notifyExternal = false)
                    return@collect
                }
                applyCurrentLyricOffset(notifyExternal = true)
            }
        }
    }

    private fun initLyricExtraInfoFilter() {
        viewModelScope.launch {
            var initialized = false
            settingsManager.hideLyricExtraInfo.distinctUntilChanged().collect { enabled ->
                hideLyricExtraInfo = enabled
                if (!initialized) {
                    initialized = true
                    applyCurrentLyricOffset(notifyExternal = false)
                    return@collect
                }
                applyCurrentLyricOffset(notifyExternal = true)
            }
        }
    }

    private fun initLyricHeaderTagFilter() {
        viewModelScope.launch {
            var initialized = false
            settingsManager.ignoreLyricHeaderTags.distinctUntilChanged().collect {
                if (!initialized) {
                    initialized = true
                    return@collect
                }
                currentSong.value?.let { song -> reloadLyrics(song, force = true) }
            }
        }
    }

    private fun sendBluetoothLyric(index: Int, lyrics: List<LyricLine>) {
        if (!bluetoothLyricEnabled) return
        if (!playerManager.isPlaying.value) return

        val payload = lyrics.bluetoothPayloadAt(
            index = index,
            includeTranslation = bluetoothLyricTranslationEnabled,
            includePronunciation = bluetoothLyricPronunciationEnabled
        ) ?: return
        if (payload == lastBluetoothLyricPayload) return

        if (playerManager.updateBluetoothLyric(payload.first, payload.second)) {
            lastBluetoothLyricPayload = payload
            bluetoothLyricRetryJob?.cancel()
        } else {
            scheduleBluetoothLyricRetry()
        }
    }

    private fun resendBluetoothLyric(force: Boolean = false) {
        if (!bluetoothLyricEnabled || !isPlaying.value) return

        val index = _currentLyricIndex.value
        val currentLyrics = _lyrics.value
        val payload = currentLyrics.bluetoothPayloadAt(
            index = index,
            includeTranslation = bluetoothLyricTranslationEnabled,
            includePronunciation = bluetoothLyricPronunciationEnabled
        ) ?: return
        if (!force && payload == lastBluetoothLyricPayload) return

        if (playerManager.updateBluetoothLyric(payload.first, payload.second, force = force)) {
            lastBluetoothLyricPayload = payload
            bluetoothLyricRetryJob?.cancel()
        } else {
            scheduleBluetoothLyricRetry()
        }
    }

    private fun scheduleBluetoothLyricRetry() {
        bluetoothLyricRetryJob?.cancel()
        val scheduledSongKey = currentSong.value?.lyricIdentityKey()
        bluetoothLyricRetryJob = viewModelScope.launch {
            delay(MediaNotificationLyricPatchPolicy.MIN_PATCH_INTERVAL_MS)
            if (currentSong.value?.lyricIdentityKey() != scheduledSongKey) return@launch
            if (!bluetoothLyricEnabled || !isPlaying.value) return@launch
            resendBluetoothLyric(force = true)
        }
    }

    private fun startPositionUpdates() {
        if (positionUpdateJob?.isActive == true) return
        positionUpdateJob = viewModelScope.launch {
            while (isActive) {
                runCatching {
                    playerManager.updatePosition()
                    updateCurrentLyricIndex()
                    PlaybackWidgetUpdater.updateLyrics(
                        context = getApplication<Application>(),
                        song = currentSong.value,
                        line = _lyrics.value.getOrNull(_currentLyricIndex.value),
                        positionMs = playerManager.currentPosition.value,
                        isPlaying = isPlaying.value
                    )
                    updatePlaybackStats()
                    updateSleepTimer()

                    if (lyriconBridge.isEnabled()) {
                        lyriconBridge.sendPosition(playerManager.currentPosition.value)
                    }
                    updateDesktopLyricFrame()
                }.onFailure { error ->
                    AppLogStore.warn(
                        getApplication(),
                        "PlayerPosition",
                        "Position update loop iteration failed; keeping ticker alive",
                        error
                    )
                }

                delay(PLAYBACK_POSITION_UPDATE_INTERVAL_MS)
            }
        }
    }

    private fun observeCurrentSong() {
        viewModelScope.launch {
            playerManager.currentSong.collectLatest { song ->
                if (song != null) {
                    viewModelScope.launch(Dispatchers.IO) {
                        val source = ListeningHistorySource.fromPreference(settingsManager.listeningHistorySource.first())
                        if (source.usesLastFm) {
                            lastFmHistoryStore.updateNowPlaying(song)
                        }
                    }
                    val songKey = song.lyricIdentityKey()
                    if (loadedLyricSongKey == songKey) {
                        updateCurrentLyricIndex()
                        return@collectLatest
                    }
                    suppressLeadingZeroLyric = true
                    _lyricsLoading.value = true
                    _rawLyrics.value = emptyList()
                    _lyrics.value = emptyList()
                    _currentLyricIndex.value = -1
                    PlaybackWidgetUpdater.clearLyrics(getApplication<Application>())
                    // Clear external bridge state before async fetch to prevent stale lyrics
                    lastTickerPayload = null
                    lastLiveUpdateLyricPayload = null
                    liveUpdateArtwork = null
                    lastBluetoothLyricPayload = null
                    bluetoothLyricRetryJob?.cancel()
                    lyricGetterBridge.clearLyric()
                    tickerBridge.clearLyric()
                    xiaomiSuperIslandLyricBridge.clear()
                    if (activeDesktopLyricHideWhenPaused()) {
                        desktopLyricBridge.clearLyric()
                    }
                    superLyricBridge.sendStop()
                    // Send song metadata to bridges BEFORE setting lyrics,
                    // so the 50ms update loop can't send new lyrics with old metadata
                    if (lyriconBridge.isEnabled()) {
                        lyriconBridge.sendSong(song, emptyList())
                    }
                    superLyricBridge.sendSong(song)
                    val songLyrics = repository.getLyrics(song, lyricSourceMode)
                    val notificationArtwork = withContext(Dispatchers.IO) {
                        repository.getCoverArtBitmap(
                            song = song,
                            maxSize = LIVE_UPDATE_ARTWORK_SIZE,
                            usage = CoverUsage.Notification
                        )
                    }
                    // Verify song hasn't changed during async fetch
                    if (playerManager.currentSong.value?.lyricIdentityKey() != songKey) {
                        return@collectLatest
                    }
                    liveUpdateArtwork = notificationArtwork
                    loadedLyricSongKey = songKey
                    setLoadedLyrics(song, songLyrics, notifyExternal = false)
                    _lyricsLoading.value = false
                    val displayedLyrics = _lyrics.value

                    if (lyriconBridge.isEnabled()) {
                        lyriconBridge.sendSong(song, displayedLyrics)
                    }
                    if (displayedLyrics.isEmpty()) {
                        clearExternalLyrics(clearLyricon = false, clearSuperLyricSong = false)
                    } else {
                        scheduleExternalLyricResend()
                    }
                } else {
                    loadedLyricSongKey = null
                    liveUpdateArtwork = null
                    suppressLeadingZeroLyric = false
                    _lyricsLoading.value = false
                    _rawLyrics.value = emptyList()
                    _lyrics.value = emptyList()
                    _currentLyricOffsetMs.value = 0L
                    _currentLyricIndex.value = -1
                    PlaybackWidgetUpdater.clearLyrics(getApplication<Application>())
                    clearExternalLyrics(clearLyricon = true, clearSuperLyricSong = true)
                }
            }
        }
    }

    private fun observePlayState() {
        viewModelScope.launch {
            playerManager.isPlaying.collect { playing ->
                if (lastSentPlayingState != playing) {
                    lastSentPlayingState = playing
                    lyriconBridge.sendPlaybackState(playing)
                    if (!playing) {
                        seekExternalLyricSyncJob?.cancel()
                        seekExternalLyricSyncJob = null
                        tickerBridge.clearLyric()
                        liveLyricNotificationBridge.clear()
                        lastLiveUpdateLyricPayload = null
                        xiaomiSuperIslandLyricBridge.onPlaybackPaused()
                        if (activeDesktopLyricHideWhenPaused()) {
                            desktopLyricBridge.clearLyric()
                        } else {
                            resendDesktopLyric()
                        }
                        superLyricBridge.sendStop()
                        lyricGetterBridge.clearLyric()
                        playerManager.clearBluetoothLyric()
                        lastBluetoothLyricPayload = null
                        bluetoothLyricRetryJob?.cancel()
                    } else {
                        viewModelScope.launch { resendExternalLyrics(force = true) }
                        resendBluetoothLyric(force = true)
                    }
                }
            }
        }
    }

    private fun sendSuperLyricAt(index: Int, lyrics: List<LyricLine>) {
        if (!superLyricBridge.isEnabled() || !isPlaying.value) return

        val line = lyrics.getOrNull(index) ?: return
        superLyricBridge.sendLyric(
            line = line,
            positionMs = currentPosition.value,
            showTranslation = _showLyricTranslation.value && superLyricTranslationEnabled
        )
    }

    private fun updateCurrentLyricIndex() {
        val currentLyrics = _lyrics.value
        if (currentLyrics.isEmpty()) {
            liveLyricNotificationBridge.clear()
            lastLiveUpdateLyricPayload = null
            lastLyricPositionSongKey = currentSong.value?.lyricIdentityKey()
            lastLyricPositionMs = playerManager.currentPosition.value
            return
        }

        val position = playerManager.currentPosition.value
        val songKey = currentSong.value?.lyricIdentityKey()
        val previousPosition = if (lastLyricPositionSongKey == songKey) lastLyricPositionMs else position
        val effectivePosition = if (
            isPlaying.value &&
            previousPosition > position &&
            previousPosition - position <= LYRIC_POSITION_BACKWARD_DRIFT_TOLERANCE_MS
        ) {
            previousPosition
        } else {
            position
        }
        val loopedToStart = playerManager.repeatMode.value == Player.REPEAT_MODE_ONE &&
            previousPosition > 1_500L &&
            effectivePosition <= 750L &&
            previousPosition - effectivePosition > 1_500L

        val indexResult = currentLyricIndexAt(
            positionMs = effectivePosition,
            lyrics = currentLyrics,
            suppressLeadingZero = suppressLeadingZeroLyric
        )
        val index = indexResult.index
        if (!indexResult.suppressedLeadingZero) {
            suppressLeadingZeroLyric = false
        }
        if (loopedToStart && index < 0 && _currentLyricIndex.value >= 0) {
            _currentLyricIndex.value = -1
        }
        if (index != _currentLyricIndex.value) {
            _currentLyricIndex.value = index

            if (index >= 0 && index < currentLyrics.size) {
                sendTickerLyric(index, currentLyrics)
                sendBluetoothLyric(index, currentLyrics)
                sendSuperLyricAt(index, currentLyrics)
                sendLyricGetterAt(index, currentLyrics)
            }
        }
        if (index >= 0 && index < currentLyrics.size) {
            // Word-timed lyrics can change while the line index remains stable. The Live Update
            // helper deduplicates unchanged results, so this stays a 10 Hz calculation rather
            // than a 10 Hz notification stream.
            sendLiveUpdateLyric(index, currentLyrics, effectivePosition)
            sendXiaomiSuperIslandLyric(index, currentLyrics, effectivePosition)
        }
        lastLyricPositionSongKey = songKey
        lastLyricPositionMs = effectivePosition
    }

    private suspend fun updatePlaybackStats() {
        playbackStatsTracker.update(
            nowMs = SystemClock.elapsedRealtime(),
            song = currentSong.value,
            isPlaying = isPlaying.value
        )
    }

    private suspend fun resendExternalLyrics(force: Boolean = false) {
        val song = currentSong.value ?: return
        val songKey = song.lyricIdentityKey()
        // Guard: if lyrics are loaded for a different song, skip this resend
        if (loadedLyricSongKey != null && loadedLyricSongKey != songKey) return
        if (_lyrics.value.isEmpty()) {
            val loaded = repository.getLyrics(song, lyricSourceMode)
            setLoadedLyrics(song, loaded, notifyExternal = false)
        }
        val songLyrics = _lyrics.value
        // Re-verify after potential async fetch
        if (playerManager.currentSong.value?.lyricIdentityKey() != songKey) return
        lyriconBridge.sendSong(song, songLyrics)
        lyriconBridge.sendPlaybackState(isPlaying.value)
        lyriconBridge.sendPosition(currentPosition.value)
        if (songLyrics.isEmpty()) {
            clearExternalLyrics(clearLyricon = false, clearSuperLyricSong = false)
            return
        }
        resendTickerLyric(force)
        resendLiveUpdateLyric(force)
        resendXiaomiSuperIslandLyric()
        resendDesktopLyric()
        resendSuperLyric(force)
        resendLyricGetter(force)
    }

    private fun resendTickerLyric(force: Boolean = false) {
        if (!tickerBridge.isEnabled() || !isPlaying.value) return
        if (force) lastTickerPayload = null
        val index = _currentLyricIndex.value
        val currentLyrics = _lyrics.value
        sendTickerLyric(index, currentLyrics)
    }

    private fun resendLiveUpdateLyric(force: Boolean = false) {
        if (!liveUpdateLyricEnabled || !isPlaying.value) return
        if (force) lastLiveUpdateLyricPayload = null
        sendLiveUpdateLyric(
            index = _currentLyricIndex.value,
            lyrics = _lyrics.value,
            positionMs = effectiveLyricPositionMs()
        )
    }

    private fun resendXiaomiSuperIslandLyric() {
        if (!xiaomiSuperIslandLyricEnabled || !isPlaying.value) return
        sendXiaomiSuperIslandLyric(
            index = _currentLyricIndex.value,
            lyrics = _lyrics.value,
            positionMs = effectiveLyricPositionMs()
        )
    }

    private fun sendXiaomiSuperIslandLyric(
        index: Int,
        lyrics: List<LyricLine>,
        positionMs: Long
    ) {
        if (!xiaomiSuperIslandLyricEnabled || !isPlaying.value) return
        val song = currentSong.value ?: return
        val line = lyrics.getOrNull(index) ?: return
        xiaomiSuperIslandLyricBridge.sendLyric(
            song = song,
            line = line,
            positionMs = positionMs,
            durationMs = duration.value,
            artwork = liveUpdateArtwork
        )
    }

    private fun sendLiveUpdateLyric(index: Int, lyrics: List<LyricLine>, positionMs: Long) {
        if (!liveUpdateLyricEnabled || !playerManager.isPlaying.value) return
        val song = currentSong.value ?: return
        val line = lyrics.getOrNull(index) ?: return
        val display = buildLiveLyricNotificationText(
            line = line,
            mode = liveUpdateLyricMode,
            positionMs = positionMs
        ) ?: return
        val notificationLyric = if (
            liveUpdateLyricDisplayMode == SettingsManager.LIVE_UPDATE_LYRIC_DISPLAY_MODE_FULL
        ) {
            display.fullLyric
        } else {
            display.lyric
        }
        val preserveFullLyric =
            liveUpdateLyricDisplayMode == SettingsManager.LIVE_UPDATE_LYRIC_DISPLAY_MODE_FULL
        // Full-line mode is intended for lock-screen/AOD surfaces. Do not keep feeding the
        // word-timed window and current word into the compact chip in this mode: Xiaomi AOD can
        // show the complete sentence, while word-level updates would make it jump on every word.
        val notificationCompactLyric = if (preserveFullLyric) {
            notificationLyric
        } else {
            display.compactLyric
        }
        val secondaryLyric = buildLiveLyricSecondaryText(line, liveUpdateLyricSecondaryMode)
        val payload = LiveLyricNotificationState(
            songKey = song.lyricIdentityKey(),
            lineIndex = index,
            wordIndex = if (preserveFullLyric) -1 else display.wordIndex,
            mode = liveUpdateLyricMode,
            displayMode = liveUpdateLyricDisplayMode,
            secondaryMode = liveUpdateLyricSecondaryMode,
            lyric = notificationLyric,
            compactLyric = notificationCompactLyric,
            allowLongCompactLyric = if (preserveFullLyric) false else display.allowLongCompactLyric,
            preserveCompactLyric = preserveFullLyric,
            secondaryLyric = secondaryLyric
        )
        if (payload == lastLiveUpdateLyricPayload) return
        lastLiveUpdateLyricPayload = payload
        liveLyricNotificationBridge.sendLyric(
            songTitle = song.title.ifBlank { song.fileName },
            lyric = notificationLyric,
            compactLyric = notificationCompactLyric,
            allowLongCompactLyric = if (preserveFullLyric) false else display.allowLongCompactLyric,
            preserveCompactLyric = preserveFullLyric,
            secondaryLyric = secondaryLyric,
            artwork = liveUpdateArtwork
        )
    }

    private fun resendDesktopLyric() {
        if (!desktopLyricBridge.isEnabled()) return
        if (activeDesktopLyricHideWhenPaused() && !isPlaying.value) return
        val index = _currentLyricIndex.value
        val currentLyrics = _lyrics.value
        desktopLyricBridge.sendLyric(
            line = currentLyrics.getOrNull(index),
            positionMs = effectiveLyricPositionMs(),
            showTranslation = _showLyricTranslation.value,
            showPronunciation = _showLyricPronunciation.value
        )
    }

    private fun updateDesktopLyricFrame() {
        if (!desktopLyricBridge.isEnabled()) return
        if (activeDesktopLyricHideWhenPaused() && !isPlaying.value) return
        val index = _currentLyricIndex.value
        val line = _lyrics.value.getOrNull(index) ?: return
        desktopLyricBridge.sendLyric(
            line,
            effectiveLyricPositionMs(),
            _showLyricTranslation.value,
            _showLyricPronunciation.value
        )
    }

    /**
     * The lyric page and Live Update already use [lastLyricPositionMs], which filters the small
     * backwards samples emitted by the player position ticker.  Desktop lyrics animate from each
     * received position anchor, so feeding them the raw value makes a single backwards sample
     * visibly replay the current words. Keep all lyric surfaces on the same effective position;
     * a real seek updates this value immediately in [applySeekSideEffects].
     */
    private fun effectiveLyricPositionMs(): Long {
        val songKey = currentSong.value?.lyricIdentityKey()
        return if (songKey != null && songKey == lastLyricPositionSongKey) {
            lastLyricPositionMs
        } else {
            playerManager.currentPosition.value
        }
    }

    private fun resendSuperLyric(force: Boolean = false) {
        if (!superLyricBridge.isEnabled() || !isPlaying.value) return
        val index = _currentLyricIndex.value
        val line = _lyrics.value.getOrNull(index) ?: return
        superLyricBridge.sendLyric(line, currentPosition.value, _showLyricTranslation.value && superLyricTranslationEnabled, force)
    }

    private fun sendLyricGetterAt(index: Int, lyrics: List<LyricLine>) {
        if (!lyricGetterBridge.isEnabled() || !isPlaying.value) return
        lyricGetterBridge.sendLyric(lyrics.getOrNull(index))
    }

    private fun resendLyricGetter(force: Boolean = false) {
        if (!lyricGetterBridge.isEnabled() || !isPlaying.value) return
        lyricGetterBridge.sendLyric(_lyrics.value.getOrNull(_currentLyricIndex.value), force)
    }

    private fun setLoadedLyrics(
        song: Song,
        rawLyrics: List<LyricLine>,
        notifyExternal: Boolean
    ) {
        _rawLyrics.value = rawLyrics
        applyCurrentLyricOffset(song = song, notifyExternal = notifyExternal)
    }

    private fun applyCurrentLyricOffset(
        song: Song? = currentSong.value,
        notifyExternal: Boolean = false
    ) {
        if (song == null) {
            _currentLyricOffsetMs.value = 0L
            val nextLyrics = _rawLyrics.value.preparedForDisplay()
            if (_lyrics.value != nextLyrics) {
                _lyrics.value = nextLyrics
                _currentLyricIndex.value = -1
            }
            return
        }
        val offsetMs = lyricOffsetOverrides[song.lyricIdentityKey()] ?: 0L
        _currentLyricOffsetMs.value = offsetMs
        val nextLyrics = _rawLyrics.value
            .filterBlacklistedLyricLines()
            .shiftedBy(offsetMs)
            .withImplicitLineEndTimes()
        val lyricsChanged = _lyrics.value != nextLyrics
        if (lyricsChanged) {
            _lyrics.value = nextLyrics
            _currentLyricIndex.value = -1
            suppressLeadingZeroLyric = true
            updateCurrentLyricIndex()
            lastTickerPayload = null
            lastLiveUpdateLyricPayload = null
            lastBluetoothLyricPayload = null
        }
        if (!notifyExternal) return
        if (lyriconBridge.isEnabled()) lyriconBridge.sendSong(song, _lyrics.value)
        superLyricBridge.sendSong(song)
        if (_lyrics.value.isEmpty()) {
            clearExternalLyrics(clearLyricon = false, clearSuperLyricSong = false)
        } else {
            resendTickerLyric(force = true)
            resendLiveUpdateLyric(force = true)
            resendDesktopLyric()
            resendSuperLyric(force = true)
            resendLyricGetter(force = true)
            resendBluetoothLyric(force = true)
            scheduleExternalLyricResend()
        }
    }

    private fun scheduleExternalLyricResend() {
        externalLyricResendJob?.cancel()
        val scheduledSongKey = currentSong.value?.lyricIdentityKey()
        externalLyricResendJob = viewModelScope.launch {
            repeat(3) { attempt ->
                delay(350L + attempt * 550L)
                // Skip if song changed since scheduling
                if (currentSong.value?.lyricIdentityKey() != scheduledSongKey) return@launch
                resendExternalLyrics(force = true)
                resendBluetoothLyric(force = true)
                resendLyricGetter(force = true)
            }
        }
    }

    private fun List<LyricLine>.filterBlacklistedLyricLines(): List<LyricLine> =
        filterBlacklistedLyricLines(lyricBlacklistRules, hideLyricExtraInfo)

    private fun List<LyricLine>.preparedForDisplay(): List<LyricLine> =
        preparedForDisplay(lyricBlacklistRules, hideLyricExtraInfo)

    private fun clearExternalLyrics(clearLyricon: Boolean, clearSuperLyricSong: Boolean) {
        externalLyricResendJob?.cancel()
        bluetoothLyricRetryJob?.cancel()
        lastTickerPayload = null
        lastLiveUpdateLyricPayload = null
        lastBluetoothLyricPayload = null
        tickerBridge.clearLyric()
        liveLyricNotificationBridge.clear()
        xiaomiSuperIslandLyricBridge.clear()
        desktopLyricBridge.clearLyric()
        lyricGetterBridge.clearLyric()
        playerManager.clearBluetoothLyric()
        if (clearLyricon) lyriconBridge.clearSong()
        if (clearSuperLyricSong) {
            superLyricBridge.destroy()
        } else {
            superLyricBridge.sendStop()
        }
    }

    private fun initLyricPageTranslation() {
        viewModelScope.launch {
            settingsManager.lyricPageTranslation.distinctUntilChanged().collect { enabled ->
                _showLyricTranslation.value = enabled
            }
        }
    }

    fun setPlaylist(songs: List<Song>, startIndex: Int = 0) {
        lazyOnlineQueueController.clear()
        playerManager.setPlaylist(songs, startIndex)
    }

    fun setLazyOnlinePlaylist(
        songs: List<Song>,
        startIndex: Int,
        resolvedStartSong: Song,
        resolver: suspend (Song) -> Song
    ) {
        playerManager.setQueueLocked(false)
        lazyOnlineQueueController.setQueue(
            songs = songs,
            startIndex = startIndex,
            resolvedStartSong = resolvedStartSong,
            resolver = resolver
        )
    }

    fun playSong(song: Song) {
        playerManager.playSong(song)
    }

    fun playRestoredQueue() {
        playerManager.play()
    }

    fun hasSavedPlaybackQueue(): Boolean = playerManager.hasSavedQueue()

    fun togglePlayPause() = playerManager.togglePlayPause()
    fun pauseForMusicVideo() = playerManager.pause()
    fun resumeAfterMusicVideo() = playerManager.play()
    fun skipToNext() {
        if (!lazyOnlineQueueController.playOffset(1)) playerManager.skipToNext()
    }

    fun skipToPrevious() {
        if (shouldReplayCurrentFromPreviousButton()) {
            playerManager.restartCurrent()
            return
        }
        manualSeekAfterPreviousButton = false
        if (!lazyOnlineQueueController.playOffset(-1)) playerManager.skipToPrevious()
    }

    /**
     * Always move to the previous track, ignoring the "previous button replays current song"
     * preference. Used by the landscape cover-wall swipe, where the gesture unambiguously means
     * "go to that cover" rather than "restart this one".
     */
    fun skipToPreviousTrack() {
        manualSeekAfterPreviousButton = false
        if (!lazyOnlineQueueController.playOffset(-1)) playerManager.skipToPrevious()
    }

    private fun shouldReplayCurrentFromPreviousButton(): Boolean {
        if (manualSeekAfterPreviousButton) {
            manualSeekAfterPreviousButton = false
            return false
        }
        return shouldReplayFromPreviousButton(
            manualSeekAfterPreviousButton = false,
            previousButtonAction = previousButtonAction,
            currentPositionMs = currentPosition.value
        )
    }

    fun seekTo(positionMs: Long) {
        val target = playerManager.seekTo(positionMs) ?: return
        applySeekSideEffects(target)
    }

    fun seekToProgress(progress: Float, fallbackDurationMs: Long) {
        val target = playerManager.seekToProgress(progress, fallbackDurationMs) ?: return
        applySeekSideEffects(target)
    }

    private fun applySeekSideEffects(positionMs: Long) {
        manualSeekAfterPreviousButton = true
        lastLyricPositionSongKey = currentSong.value?.lyricIdentityKey()
        lastLyricPositionMs = positionMs

        val lyrics = _lyrics.value
        val index = currentLyricIndexAt(
            positionMs = positionMs,
            lyrics = lyrics,
            suppressLeadingZero = positionMs in 0L until LEADING_ZERO_LYRIC_SUPPRESSION_MS
        ).index
        _currentLyricIndex.value = index
        scheduleSeekExternalLyricSync(positionMs)
    }

    /**
     * A seek is a UI-critical controller command.  The Lyricon/SuperLyric publishers can make
     * synchronous IPC calls, so issuing them for every tap on the lyric page used to leave those
     * calls queued ahead of the next pause.  Coalesce a burst and publish only its final target.
     */
    private fun scheduleSeekExternalLyricSync(positionMs: Long) {
        if (!lyriconBridge.isEnabled() && !superLyricBridge.isEnabled()) return
        seekExternalLyricSyncJob?.cancel()
        seekExternalLyricSyncJob = viewModelScope.launch(Dispatchers.IO) {
            delay(SEEK_EXTERNAL_LYRIC_SYNC_DEBOUNCE_MS)
            lyriconBridge.seekTo(positionMs)
            if (!isPlaying.value || !superLyricBridge.isEnabled()) return@launch
            val line = _lyrics.value.getOrNull(_currentLyricIndex.value) ?: return@launch
            superLyricBridge.sendLyric(
                line = line,
                positionMs = positionMs,
                showTranslation = _showLyricTranslation.value && superLyricTranslationEnabled
            )
        }
    }

    fun toggleShuffle() = playerManager.toggleShuffle()
    fun toggleRepeat() = playerManager.toggleRepeat()
    fun toggleQueueLock() = playerManager.toggleQueueLock()
    fun setShuffleMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setShuffleMode(mode)
            playerManager.setShuffleMode(mode)
        }
    }

    fun setPlayNextMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setPlayNextMode(mode)
            playerManager.setPlayNextMode(mode)
        }
    }

    fun setPreviousButtonAction(action: Int) {
        previousButtonAction = action.coerceIn(
            SettingsManager.PREVIOUS_BUTTON_PREVIOUS,
            SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT
        )
        viewModelScope.launch {
            settingsManager.setPreviousButtonAction(previousButtonAction)
        }
    }

    fun setResumePlaybackPositionEnabled(enabled: Boolean) {
        playerManager.setResumePlaybackPositionEnabled(enabled)
    }

    fun setDecoderMode(mode: Int) {
        viewModelScope.launch {
            val safeMode = mode.coerceIn(0, 2)
            settingsManager.setDecoderMode(safeMode)
            if (appliedDecoderMode != safeMode) {
                appliedDecoderMode = safeMode
                PlaybackService.decoderModeOverride.value = null
                playerManager.recreatePlaybackService()
                AppLogStore.info(getApplication(), "PlayerDecoder", "Decoder mode changed to $safeMode")
            }
        }
    }
    fun addToPlaylist(song: Song) {
        if (queueLocked.value) return
        lazyOnlineQueueController.clear()
        playerManager.addToPlaylist(song)
    }
    fun addToPlaylist(songs: List<Song>) {
        if (queueLocked.value) return
        lazyOnlineQueueController.clear()
        playerManager.addToPlaylist(songs)
    }

    fun playNext(song: Song) {
        if (queueLocked.value) return
        lazyOnlineQueueController.clear()
        playerManager.playNext(song)
    }

    fun playNext(songs: List<Song>) {
        if (queueLocked.value) return
        lazyOnlineQueueController.clear()
        playerManager.playNext(songs)
    }

    /** Re-establish the media controller if the playback session was torn down in the background. */
    fun ensurePlayerConnected() {
        // The manager already receives live callbacks while this process stays alive. Asking a
        // healthy controller to refresh on every resume momentarily republishes the queue/song
        // state, which made the whole app appear to reload after returning from background.
        playerManager.ensureConnected(refreshStateIfConnected = false)
        startPositionUpdates()
    }

    fun livePositionMs(): Long = playerManager.livePositionMs()

    fun playQueueIndex(index: Int) {
        if (!lazyOnlineQueueController.playIndex(index)) playerManager.playQueueIndex(index)
    }

    fun removeFromPlaylist(index: Int) {
        if (queueLocked.value) return
        lazyOnlineQueueController.clear()
        playerManager.removeFromPlaylist(index)
    }

    fun movePlaylistItem(fromIndex: Int, toIndex: Int) {
        if (queueLocked.value) return
        lazyOnlineQueueController.clear()
        playerManager.movePlaylistItem(fromIndex, toIndex)
    }

    fun randomizePlaylistOrder(): Boolean {
        if (queueLocked.value) return false
        lazyOnlineQueueController.clear()
        return playerManager.randomizePlaylistOrder()
    }

    fun clearPlaylist() {
        if (queueLocked.value) return
        lazyOnlineQueueController.clear()
        playerManager.clearPlaylist()
    }

    fun requestLocateCurrentSong() {
        _locateCurrentSongRequest.value += 1
    }

    fun cyclePlaybackMode() {
        playerManager.cyclePlaybackMode()
    }

    fun getCoverArtBitmap(song: Song) = repository.getCoverArtBitmap(song, 1200, CoverUsage.Player)

    fun getOriginalCoverModel(song: Song): Any? = repository.getOriginalCoverModel(song)

    fun getAudioInfo(song: Song) = repository.getAudioInfo(song)

    fun getSongTagInfo(song: Song) = repository.getSongTagInfo(song)

    fun toggleLyrics() {
        _showLyrics.value = !_showLyrics.value
    }

    fun setShowLyrics(show: Boolean) {
        _showLyrics.value = show
    }

    fun setPlaybackSpeed(speed: Float) {
        playerManager.setPlaybackParameters(speed, playbackPitch.value)
    }

    fun setPlaybackPitch(pitch: Float) {
        playerManager.setPlaybackParameters(playbackSpeed.value, pitch)
    }

    fun setLyricSourceMode(mode: Int) {
        viewModelScope.launch {
            _preferTtmlLyrics.value = null
            settingsManager.setLyricSourceMode(mode)
            lyricSourceMode = mode.coerceIn(SettingsManager.LYRIC_SOURCE_AUTO, SettingsManager.LYRIC_SOURCE_EMBEDDED)
            appliedLyricSourceMode = lyricSourceMode
            currentSong.value?.let { reloadLyrics(it, force = true) }
        }
    }

    fun setLyricFormatPreference(preferTtml: Boolean) {
        viewModelScope.launch {
            _preferTtmlLyrics.value = preferTtml
            currentSong.value?.let { reloadLyrics(it, force = true) }
        }
    }

    fun setCurrentLyricOffsetMs(offsetMs: Long) {
        val song = currentSong.value ?: return
        val safeOffset = offsetMs.coerceIn(-5000L, 5000L)
        viewModelScope.launch {
            settingsManager.setLyricOffsetOverride(song.lyricIdentityKey(), safeOffset)
        }
    }

    fun clearOnlineMetadataCache() {
        repository.clearRemoteMetadataCache()
    }

    fun refreshCurrentSongAfterExternalEdit(updatedFromLibrary: Song?) {
        val current = currentSong.value ?: return
        viewModelScope.launch {
            val updated = updatedFromLibrary
                ?.takeIf { it.lyricIdentityKey() == current.lyricIdentityKey() }
                ?: repository.refreshSongAfterExternalEdit(current)
                ?: current
            repository.clearMetadataCache(current)
            repository.clearMetadataCache(updated)
            playerManager.updateCurrentSongMetadata(updated)
            reloadLyrics(updated, force = true)
        }
    }

    fun reloadCurrentLyrics() {
        val song = currentSong.value ?: return
        viewModelScope.launch { reloadLyrics(song, force = true) }
    }

    fun toggleCurrentSongFavorite() {
        val song = currentSong.value ?: return
        viewModelScope.launch {
            if (openSubsonicCollectionsStore.isManagedFavorite(song)) {
                runCatching { openSubsonicCollectionsStore.toggleFavorite(song) }
            } else {
                playlistStore.toggleFavorite(song)
            }
        }
    }

    fun isFavorite(song: Song?): Boolean =
        song?.playlistIdentityKey()?.let { it in favoriteSongKeys.value } == true

    private suspend fun reloadLyrics(song: Song, force: Boolean = false) {
        lastTickerPayload = null
        lastLiveUpdateLyricPayload = null
        lastBluetoothLyricPayload = null
        bluetoothLyricRetryJob?.cancel()
        val availability = repository.getLyricFormatAvailability(song)
        _lyricFormatAvailability.value = availability
        val formatOverride = _preferTtmlLyrics.value.takeIf { availability.hasBoth }
        if (!availability.hasBoth) _preferTtmlLyrics.value = null
        val songLyrics = if (formatOverride != null) {
            repository.reloadLyricsByFormat(song, formatOverride)
        } else if (force) {
            repository.reloadLyrics(song, lyricSourceMode)
        } else {
            repository.getLyrics(song, lyricSourceMode)
        }
        loadedLyricSongKey = song.lyricIdentityKey()
        setLoadedLyrics(song, songLyrics, notifyExternal = false)
        val displayedLyrics = _lyrics.value
        if (lyriconBridge.isEnabled()) lyriconBridge.sendSong(song, displayedLyrics)
        superLyricBridge.sendSong(song)
        if (displayedLyrics.isEmpty()) {
            clearExternalLyrics(clearLyricon = false, clearSuperLyricSong = false)
        } else {
            if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
            if (liveUpdateLyricEnabled) resendLiveUpdateLyric(force = true)
            if (desktopLyricBridge.isEnabled()) resendDesktopLyric()
            if (superLyricBridge.isEnabled()) resendSuperLyric(force = true)
            if (lyricGetterBridge.isEnabled()) resendLyricGetter(force = true)
            if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
            scheduleExternalLyricResend()
        }
    }

    fun startSleepTimer(
        minutes: Int,
        stopAfterCurrentWhenExpired: Boolean = false
    ) {
        sleepTimerController.start(minutes, stopAfterCurrentWhenExpired)
    }

    fun setStopAfterCurrentEnabled(enabled: Boolean) {
        sleepTimerController.setStopAfterCurrentEnabled(enabled)
    }

    fun cancelSleepTimer() {
        sleepTimerController.cancel()
    }

    private fun updateSleepTimer() {
        sleepTimerController.update()
    }

    fun setLyricPageTranslation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyricPageTranslation(enabled)
            _showLyricTranslation.value = enabled
        }
    }

    fun setLyricPagePronunciation(enabled: Boolean) {
        _showLyricPronunciation.value = enabled
        resendDesktopLyric()
    }

    fun setLyriconEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyriconEnabled(enabled)
            lyriconBridge.setEnabled(enabled)
            if (enabled) {
                currentSong.value?.let { song ->
                    lyriconBridge.sendSong(song, _lyrics.value)
                    lyriconBridge.sendPlaybackState(isPlaying.value)
                }
            }
        }
    }

    fun setLyriconTranslation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyriconTranslation(enabled)
            lyriconTranslationEnabled = enabled
            if (enabled && lyriconPronunciationEnabled) {
                lyriconPronunciationEnabled = false
                settingsManager.setLyriconPronunciation(false)
            }
            lyriconBridge.setSecondaryMode(currentLyriconSecondaryMode())
            if (lyriconBridge.isEnabled()) resendExternalLyrics(force = true)
        }
    }

    fun setTickerEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setTickerEnabled(enabled)
            if (enabled) {
                settingsManager.setTickerHideNotification(true)
                tickerHideNotificationEnabled = true
            }
            tickerBridge.setHideNotification(true)
            tickerBridge.setHeadsUpLyricsEnabled(settingsManager.tickerHeadsUpLyrics.first())
            tickerBridge.setEnabled(enabled)
            lastTickerPayload = null
            if (enabled) resendTickerLyric()
        }
    }

    fun setLiveUpdateLyricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLiveUpdateLyricEnabled(enabled)
            liveUpdateLyricEnabled = enabled
            lastLiveUpdateLyricPayload = null
            liveLyricNotificationBridge.setEnabled(enabled)
            if (enabled) resendLiveUpdateLyric(force = true) else liveLyricNotificationBridge.clear()
        }
    }

    fun setXiaomiSuperIslandLyricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setXiaomiSuperIslandLyricEnabled(enabled)
            xiaomiSuperIslandLyricEnabled = enabled
            xiaomiSuperIslandLyricBridge.setEnabled(enabled)
            if (enabled) resendXiaomiSuperIslandLyric()
        }
    }

    fun setLiveUpdateLyricMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setLiveUpdateLyricMode(mode)
            liveUpdateLyricMode = mode.coerceIn(
                SettingsManager.LIVE_UPDATE_LYRIC_MODE_ORIGINAL,
                SettingsManager.LIVE_UPDATE_LYRIC_MODE_PRONUNCIATION
            )
            lastLiveUpdateLyricPayload = null
            if (liveUpdateLyricEnabled) resendLiveUpdateLyric(force = true)
        }
    }

    fun setLiveUpdateLyricDisplayMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setLiveUpdateLyricDisplayMode(mode)
            liveUpdateLyricDisplayMode = mode.coerceIn(
                SettingsManager.LIVE_UPDATE_LYRIC_DISPLAY_MODE_COMPACT,
                SettingsManager.LIVE_UPDATE_LYRIC_DISPLAY_MODE_FULL
            )
            lastLiveUpdateLyricPayload = null
            if (liveUpdateLyricEnabled) resendLiveUpdateLyric(force = true)
        }
    }

    fun setLiveUpdateLyricSecondaryMode(mode: Int) {
        viewModelScope.launch {
            settingsManager.setLiveUpdateLyricSecondaryMode(mode)
            liveUpdateLyricSecondaryMode = mode.coerceIn(
                SettingsManager.LIVE_UPDATE_LYRIC_SECONDARY_MODE_SONG,
                SettingsManager.LIVE_UPDATE_LYRIC_SECONDARY_MODE_PRONUNCIATION
            )
            lastLiveUpdateLyricPayload = null
            if (liveUpdateLyricEnabled) resendLiveUpdateLyric(force = true)
        }
    }

    fun setTickerHideNotification(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setTickerHideNotification(true)
            tickerHideNotificationEnabled = true
            tickerBridge.setHideNotification(true)
            lastTickerPayload = null
            if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
        }
    }

    fun setTickerHeadsUpLyrics(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setTickerHeadsUpLyrics(enabled)
            tickerBridge.setHeadsUpLyricsEnabled(enabled)
            lastTickerPayload = null
            if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
        }
    }

    fun setSamsungFloatingLyricTranslation(enabled: Boolean) {
        viewModelScope.launch {
            val safeEnabled = enabled
            settingsManager.setSamsungFloatingLyricTranslation(safeEnabled)
            samsungFloatingLyricTranslationEnabled = safeEnabled
            if (safeEnabled && statusBarAllowPhoneticEnabled) {
                statusBarAllowPhoneticEnabled = false
                settingsManager.setStatusBarAllowPhonetic(false)
            }
            lastTickerPayload = null
            if (tickerBridge.isEnabled()) resendTickerLyric()
        }
    }

    fun setStatusBarAllowPhonetic(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setStatusBarAllowPhonetic(enabled)
            statusBarAllowPhoneticEnabled = enabled
            if (enabled && samsungFloatingLyricTranslationEnabled) {
                samsungFloatingLyricTranslationEnabled = false
                settingsManager.setSamsungFloatingLyricTranslation(false)
            }
            lastTickerPayload = null
            if (tickerBridge.isEnabled()) resendTickerLyric(force = true)
        }
    }

    fun setLyriconPronunciation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyriconPronunciation(enabled)
            lyriconPronunciationEnabled = enabled
            if (enabled && lyriconTranslationEnabled) {
                lyriconTranslationEnabled = false
                settingsManager.setLyriconTranslation(false)
            }
            lyriconBridge.setSecondaryMode(currentLyriconSecondaryMode())
            if (lyriconBridge.isEnabled()) resendExternalLyrics(force = true)
        }
    }

    fun setDesktopLyricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setDesktopLyricEnabled(enabled)
            desktopLyricBridge.setEnabled(enabled)
            if (enabled) resendDesktopLyric()
        }
    }

    fun setDesktopLyricHideWhenPaused(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setDesktopLyricHideWhenPaused(enabled)
            desktopLyricHideWhenPausedEnabled = enabled
            desktopLyricBridge.applySettings()
            if (!desktopLyricStatusBarModeEnabled && enabled && !isPlaying.value) {
                desktopLyricBridge.clearLyric()
            } else {
                resendDesktopLyric()
            }
        }
    }

    fun applyDesktopLyricSettings() {
        desktopLyricBridge.applySettings()
        resendDesktopLyric()
    }

    fun setSuperLyricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSuperLyricEnabled(enabled)
            superLyricBridge.setEnabled(enabled)
            if (enabled) {
                currentSong.value?.let { superLyricBridge.sendSong(it) }
                resendSuperLyric()
            }
        }
    }

    fun setLyricGetterEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setLyricGetterEnabled(enabled)
            lyricGetterBridge.setEnabled(enabled)
            if (enabled) resendLyricGetter(force = true)
        }
    }

    fun setSuperLyricTranslation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSuperLyricTranslation(enabled)
            superLyricTranslationEnabled = enabled
            if (enabled && superLyricPronunciationEnabled) {
                superLyricPronunciationEnabled = false
                settingsManager.setSuperLyricPronunciation(false)
            }
            superLyricBridge.setSecondaryMode(currentSuperLyricSecondaryMode())
            if (superLyricBridge.isEnabled()) resendSuperLyric(force = true)
        }
    }

    fun setSuperLyricPronunciation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setSuperLyricPronunciation(enabled)
            superLyricPronunciationEnabled = enabled
            if (enabled && superLyricTranslationEnabled) {
                superLyricTranslationEnabled = false
                settingsManager.setSuperLyricTranslation(false)
            }
            superLyricBridge.setSecondaryMode(currentSuperLyricSecondaryMode())
            if (superLyricBridge.isEnabled()) resendSuperLyric(force = true)
        }
    }

    fun setBluetoothLyricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setBluetoothLyricEnabled(enabled)
            bluetoothLyricEnabled = enabled
            lastBluetoothLyricPayload = null

            if (enabled) {
                resendBluetoothLyric()
            } else {
                bluetoothLyricRetryJob?.cancel()
                playerManager.clearBluetoothLyric()
            }
        }
    }

    fun setBluetoothLyricTranslation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setBluetoothLyricTranslation(enabled)
            bluetoothLyricTranslationEnabled = enabled
            if (enabled && bluetoothLyricPronunciationEnabled) {
                bluetoothLyricPronunciationEnabled = false
                settingsManager.setBluetoothLyricPronunciation(false)
            }
            lastBluetoothLyricPayload = null
            if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
        }
    }

    fun setBluetoothLyricPronunciation(enabled: Boolean) {
        viewModelScope.launch {
            settingsManager.setBluetoothLyricPronunciation(enabled)
            bluetoothLyricPronunciationEnabled = enabled
            if (enabled && bluetoothLyricTranslationEnabled) {
                bluetoothLyricTranslationEnabled = false
                settingsManager.setBluetoothLyricTranslation(false)
            }
            lastBluetoothLyricPayload = null
            if (bluetoothLyricEnabled) resendBluetoothLyric(force = true)
        }
    }

    private fun sendTickerLyric(index: Int, lyrics: List<LyricLine>) {
        if (!tickerBridge.isEnabled() || !playerManager.isPlaying.value) return

        val payload = lyrics.lyricPayloadAt(index, samsungFloatingLyricTranslationEnabled) ?: return
        if (payload == lastTickerPayload) return

        lastTickerPayload = payload
        val pronunciation = if (statusBarAllowPhoneticEnabled) {
            lyrics.getOrNull(index)?.pronunciation?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        tickerBridge.sendLyric(payload.first, payload.second, pronunciation)
    }

    override fun onCleared() {
        val pendingStatsFlush = playbackStatsTracker.takePendingFlush()
        if (pendingStatsFlush != null) {
            cleanupScope.launch {
                playbackStatsStore.addListenTime(
                    song = pendingStatsFlush.song,
                    listenedMs = pendingStatsFlush.listenedMs,
                    historyEntryId = pendingStatsFlush.historyEntryId
                )
            }
        }
        super.onCleared()
        externalLyricResendJob?.cancel()
        positionUpdateJob?.cancel()
        seekExternalLyricSyncJob?.cancel()
        sleepTimerController.dispose()
        tickerBridge.clearLyric()
        liveLyricNotificationBridge.clear()
        xiaomiSuperIslandLyricBridge.destroy()
        lyricGetterBridge.clearLyric()
        superLyricBridge.destroy()
        lyriconBridge.destroy()
        playerManager.disconnect()
    }
}
