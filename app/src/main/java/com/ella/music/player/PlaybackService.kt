package com.ella.music.player

import android.app.PendingIntent
import android.os.SystemClock
import android.content.Intent
import android.media.audiofx.AudioEffect
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.Timeline
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.common.Player
import androidx.media3.cast.CastPlayer
import androidx.media3.cast.RemoteCastPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaLibraryService.MediaLibrarySession
import androidx.media3.session.MediaSession
import androidx.media3.session.LegacyArtworkCompat
import androidx.media3.session.SessionCommand
import com.ella.music.R
import com.ella.music.MainActivity
import com.ella.music.data.AppLogStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.PlaylistStore
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.model.Song
import com.ella.music.data.neteaseShareSongUrl
import com.ella.music.data.repository.MusicRepository
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import com.ella.music.dsp.TenBandEqualizer
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.google.common.util.concurrent.SettableFuture
import com.ella.music.oem.HonorHdAudioSupport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import android.os.Bundle

@OptIn(UnstableApi::class)
class PlaybackService : MediaLibraryService() {

    companion object {
        private const val TAG = "PlaybackService"
        internal const val LIBRARY_ROOT_ID = "ella_music_root"
        internal const val LIBRARY_QUEUE_ID = "ella_music_current_queue"
        private const val PLAYBACK_PREFS = "ella_playback_state"
        private const val KEY_APP_SHUFFLE = "app_shuffle_enabled"
        private const val KEY_APP_REPEAT = "app_repeat_mode"
        const val ACTION_TOGGLE_TRANSLATION =
            "io.github.andrealtb.lockscreenlyrics.action.TOGGLE_TRANSLATION"
        const val ACTION_TOGGLE_FAVORITE = "com.ella.music.action.TOGGLE_FAVORITE"
        const val ACTION_TOGGLE_DESKTOP_LYRIC = "com.ella.music.action.TOGGLE_DESKTOP_LYRIC"
        const val ACTION_TOGGLE_SHUFFLE = "com.ella.music.action.TOGGLE_SHUFFLE"
        const val ACTION_UPDATE_NOTIFICATION_LYRIC =
            "com.ella.music.action.UPDATE_NOTIFICATION_LYRIC"
        const val EXTRA_NOTIFICATION_LYRIC_SONG_KEY = "notification_lyric_song_key"
        const val EXTRA_NOTIFICATION_LYRIC_TEXT = "notification_lyric_text"
        const val EXTRA_NOTIFICATION_LYRIC_SECONDARY_TEXT = "notification_lyric_secondary_text"
        const val ACTION_SKIP_PREVIOUS = "com.ella.music.action.SKIP_PREVIOUS"
        const val ACTION_PLAY_PAUSE = "com.ella.music.action.PLAY_PAUSE"
        const val ACTION_SKIP_NEXT = "com.ella.music.action.SKIP_NEXT"
        const val ACTION_WIDGET_PREVIOUS = "com.ella.music.action.WIDGET_PREVIOUS"
        const val ACTION_WIDGET_PLAY_PAUSE = "com.ella.music.action.WIDGET_PLAY_PAUSE"
        const val ACTION_WIDGET_NEXT = "com.ella.music.action.WIDGET_NEXT"
        internal const val TIMING_TAG = "EllaPlaybackTiming"
        private const val CROSSFADE_PROMOTION_SETTLE_CHECK_MS = 16L
        private const val CROSSFADE_PROMOTION_STABLE_CHECKS = 3
        private const val CROSSFADE_PROMOTION_SETTLE_MAX_CHECKS = 60

        val bluetoothConnectEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
        // StateFlow retains the latest service state so a ViewModel recreated after a long
        // background stay can rebuild immediately instead of waiting for the next player event.
        val externalPlaybackSnapshot = MutableStateFlow<PlaybackExternalSnapshot?>(null)
        val externalPlaybackModeEvent = MutableSharedFlow<PlaybackModeExternalSnapshot>(extraBufferCapacity = 4)

        /**
         * 自动解码模式下的临时覆盖。仅在 decoder_mode 为 Auto 且当前曲目为
         * m4a/ALAC/AAC 时由 PlayerViewModel 设置为 ffmpeg-prefer（1）。
         * Service 创建时优先使用该值，不持久化到 DataStore。
         */
        val decoderModeOverride = MutableStateFlow<Int?>(null)

        fun isXiaomiFamilyDevice(): Boolean {
            val manufacturer = android.os.Build.MANUFACTURER.orEmpty().lowercase()
            val brand = android.os.Build.BRAND.orEmpty().lowercase()
            return manufacturer in setOf("xiaomi", "redmi", "poco") ||
                brand in setOf("xiaomi", "redmi", "poco")
        }
    }

    private var mediaSession: MediaLibrarySession? = null
    private var legacyArtworkPublishSequence = 0L
    private var sessionPresentationPlayer: SessionPresentationPlayer? = null
    private var crossfadePlaybackCoordinator: CrossfadePlaybackCoordinator? = null
    private var crossfadePresentationSettlementJob: kotlinx.coroutines.Job? = null
    private var castMediaServer: LocalCastMediaServer? = null
    private lateinit var notificationProvider: NoArtworkMediaNotificationProvider
    private lateinit var settingsManager: SettingsManager
    private lateinit var musicRepository: MusicRepository
    private lateinit var desktopLyricBridge: DesktopLyricBridge
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var bluetoothReceiver: BluetoothAutoPlayReceiver? = null
    private var bluetoothReceiverRegistered = false
    private var lastBluetoothAutoPlayAttemptMs = 0L
    private var openedAudioEffectSessionId = -1
    private val audioEffectController = AudioEffectController()
    private lateinit var equalizerAudioProcessor: EqualizerAudioProcessor
    private lateinit var karaokeAccompanimentProcessor: CenterChannelSuppressorAudioProcessor
    private lateinit var usbAudioController: UsbAudioController
    private var honorHdAudioSupport: HonorHdAudioSupport? = null
    private lateinit var oplusLyricHandler: OPlusLyricHandler
    @Volatile
    private var colorOsLockScreenLyricEnabled = false
    @Volatile
    private var previousButtonAction = SettingsManager.PREVIOUS_BUTTON_PREVIOUS
    @Volatile
    internal var appShuffleEnabled = false
    @Volatile
    internal var mediaNotificationButtonIds: List<String> =
        SettingsManager.DEFAULT_MEDIA_NOTIFICATION_BUTTON_IDS
    @Volatile
    internal var desktopLyricEnabled = false
    @Volatile
    internal var desktopLyricLocked = false
    @Volatile
    private var bluetoothAutoPlayEnabled = false
    @Volatile
    private var openPlayerFromNotification = false

    internal fun desktopLyricNotificationIcon(): Int = when {
        desktopLyricEnabled && desktopLyricLocked -> R.drawable.ic_notification_desktop_lyrics_locked
        desktopLyricEnabled -> R.drawable.ic_notification_desktop_lyrics_enabled
        else -> R.drawable.ic_notification_desktop_lyrics
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        honorHdAudioSupport = HonorHdAudioSupport(this).also { it.initialize() }
        notificationProvider = NoArtworkMediaNotificationProvider(this)
        setMediaNotificationProvider(notificationProvider)
        settingsManager = SettingsManager.getInstance(this)
        openPlayerFromNotification = runBlocking(Dispatchers.IO) {
            settingsManager.openPlayerFromNotification.first()
        }
        musicRepository = MusicRepository.getInstance(this)
        desktopLyricBridge = DesktopLyricBridge(this)
        mediaNotificationButtonIds = runBlocking(Dispatchers.IO) {
            settingsManager.mediaNotificationButtonIds.first()
        }
        desktopLyricEnabled = runBlocking(Dispatchers.IO) {
            settingsManager.desktopLyricEnabled.first()
        }
        desktopLyricLocked = runBlocking(Dispatchers.IO) {
            settingsManager.desktopLyricLocked.first()
        }
        usbAudioController = UsbAudioController.getInstance(this)
        oplusLyricHandler = OPlusLyricHandler(
            settingsManager,
            musicRepository,
            serviceScope,
            playerProvider = { mediaSession?.player },
            onLyricInfoChanged = { song, lyricInfoJson, forceRepublish ->
                sessionPresentationPlayer?.setOplusLyric(
                    songKey = song?.playbackStackKey(),
                    lyricInfoJson = lyricInfoJson,
                    forceRepublish = forceRepublish
                )
                sessionPresentationPlayer?.replaceCurrentOplusLyricInfo(song, lyricInfoJson)
                if (!forceRepublish) {
                    updateMediaButtonPreferences()
                }
            }
        )
        var webDavConfig = currentWebDavConfig(settingsManager)
        appShuffleEnabled = loadAppShuffleEnabled()
        previousButtonAction = runBlocking(Dispatchers.IO) {
            settingsManager.previousButtonAction.first()
        }
        colorOsLockScreenLyricEnabled = runBlocking(Dispatchers.IO) {
            settingsManager.colorOsLockScreenLyricEnabled.first()
        }
        oplusLyricHandler.colorOsLockScreenLyricEnabled = colorOsLockScreenLyricEnabled
        oplusLyricHandler.colorOsLockScreenLyricMode = runBlocking(Dispatchers.IO) {
            settingsManager.colorOsLockScreenLyricMode.first()
        }
        bluetoothAutoPlayEnabled = runBlocking(Dispatchers.IO) {
            settingsManager.bluetoothAutoPlay.first()
        }
        val httpDataSourceFactory = OkHttpDataSource.Factory(
            WebDavClient.newAuthenticatedOkHttpClient { webDavConfig }
        )
        serviceScope.launch {
            settingsManager.previousButtonAction.collect { action ->
                previousButtonAction = action.coerceIn(
                    SettingsManager.PREVIOUS_BUTTON_PREVIOUS,
                    SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT
                )
            }
        }
        serviceScope.launch {
            settingsManager.mediaNotificationButtonIds.collect { ids ->
                mediaNotificationButtonIds = ids
                updateMediaButtonPreferences()
                notificationProvider.refresh()
            }
        }
        serviceScope.launch {
            combine(
                settingsManager.desktopLyricEnabled,
                settingsManager.desktopLyricLocked
            ) { enabled, locked -> enabled to locked }.collect { (enabled, locked) ->
                desktopLyricEnabled = enabled
                desktopLyricLocked = locked
                updateMediaButtonPreferences()
                notificationProvider.refresh()
            }
        }
        serviceScope.launch {
            var initialized = false
            settingsManager.bluetoothAutoPlay.collect { enabled ->
                val wasEnabled = bluetoothAutoPlayEnabled
                bluetoothAutoPlayEnabled = enabled
                if (!initialized) {
                    initialized = true
                    return@collect
                }
                if (enabled && !wasEnabled) {
                    ensureBluetoothAutoPlayReceiverRegistered()
                    scheduleBluetoothAutoPlayIfConnected("setting enabled")
                }
            }
        }
        serviceScope.launch {
            settingsManager.colorOsLockScreenLyricEnabled.collect { enabled ->
                colorOsLockScreenLyricEnabled = enabled
                oplusLyricHandler.colorOsLockScreenLyricEnabled = enabled
                sessionPresentationPlayer?.setKeepSongIdentityMetadata(
                    OPlusLyricPublishPolicy.shouldKeepSongIdentityMetadata(enabled)
                )
                if (enabled) {
                    oplusLyricHandler.refreshCurrentOplusLyricInfo()
                } else {
                    oplusLyricHandler.clearCurrentOplusLyricInfo()
                }
                updateMediaButtonPreferences()
            }
        }
        serviceScope.launch {
            settingsManager.colorOsLockScreenLyricMode.collect { mode ->
                if (oplusLyricHandler.colorOsLockScreenLyricMode == mode) return@collect
                oplusLyricHandler.colorOsLockScreenLyricMode = mode
                if (colorOsLockScreenLyricEnabled) {
                    oplusLyricHandler.clearCurrentOplusLyricInfo()
                    oplusLyricHandler.refreshCurrentOplusLyricInfo()
                }
                updateMediaButtonPreferences()
            }
        }
        serviceScope.launch {
            combine(
                settingsManager.webDavUrl,
                settingsManager.webDavUsername,
                settingsManager.webDavPassword
            ) { url, username, password ->
                WebDavConfig(url = url, username = username, password = password)
            }.collect { config ->
                webDavConfig = config
            }
        }
        serviceScope.launch {
            settingsManager.usbDacMode.collect { _ ->
                usbAudioController.applyUsbRoutingIfEnabled()
            }
        }
        serviceScope.launch {
            usbAudioController.preferredUsbDevice.collect { _ ->
                usbAudioController.applyUsbRoutingIfEnabled()
            }
        }
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)

        val decoderMode = runBlocking(Dispatchers.IO) {
            decoderModeOverride.value ?: settingsManager.decoderMode.first()
        }
        val handleAudioFocus = runBlocking(Dispatchers.IO) {
            !settingsManager.audioFocusDisabled.first()
        }
        val playbackOutputSettings = runBlocking(Dispatchers.IO) {
            settingsManager.playbackOutputSettings.first()
        }
        val renderersFactory = EllaRenderersFactory(this).apply {
            setExtensionRendererMode(
                when (decoderMode) {
                    1 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                    2 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                }
            )
            // Auto: system decoder first (including vendor Dolby). FFmpeg is only the
            // fallback when MediaCodec has no usable decoder, so 6-channel Atmos stays
            // on the hardware path instead of the quieter stereo FFmpeg mix.
            setEnableDecoderFallback(decoderMode != 0)
        }
        equalizerAudioProcessor = EqualizerAudioProcessor()
        val secondaryEqualizerAudioProcessor = EqualizerAudioProcessor()
        karaokeAccompanimentProcessor = CenterChannelSuppressorAudioProcessor()
        val secondaryKaraokeAccompanimentProcessor = CenterChannelSuppressorAudioProcessor()
        val primaryWaveformProbe = WaveformLevelAudioProcessor()
        val secondaryWaveformProbe = WaveformLevelAudioProcessor()
        val primaryTransitionGain = CrossfadeGainAudioProcessor()
        val secondaryTransitionGain = CrossfadeGainAudioProcessor().apply { gain = 0f }
        renderersFactory.setEqualizerAudioProcessor(equalizerAudioProcessor)
        renderersFactory.setExtraAudioProcessors(listOf(karaokeAccompanimentProcessor, primaryWaveformProbe))
        renderersFactory.setTransitionGainAudioProcessor(primaryTransitionGain)
        renderersFactory.setPlaybackOutputSettings(playbackOutputSettings)
        AppLogStore.info(this, TAG, "Decoder mode=${decoderMode.decoderModeLabel()}")
        AppLogStore.info(
            this,
            TAG,
            "Audio output backend=${playbackOutputSettings.backend}, bitDepth=${playbackOutputSettings.bitDepth}, sampleRate=${playbackOutputSettings.sampleRate}"
        )

        var customSurroundEnabled = runBlocking(Dispatchers.IO) {
            settingsManager.audioEffectSettings.first().let { it.surround360Enabled || it.panoramic360Enabled }
        }
        var platformSpatialRequested = runBlocking(Dispatchers.IO) {
            settingsManager.platformSpatialAudioEnabled.first()
        }
        fun resolvedAudioAttributes(): AudioAttributes = AndroidSpatialAudio.mediaAttributes(
            context = this,
            platformRequested = platformSpatialRequested,
            customSpatialRenderer = customSurroundEnabled
        )
        var mediaAudioAttributes = resolvedAudioAttributes()
        val player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(mediaAudioAttributes, handleAudioFocus)
            .setHandleAudioBecomingNoisy(true)
            // Local playback only needs a CPU wake lock. Keeping the Wi-Fi lock requested by
            // WAKE_MODE_NETWORK for every local album was a measurable standby battery drain.
            // The listener below enables the network wake lock only for remote media.
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
            .build()
        player.repeatMode = Player.REPEAT_MODE_ALL
        // Pre-buffer the next queue item so skipToNext is decode-ready instead of a cold open.
        player.setPreloadConfiguration(ExoPlayer.PreloadConfiguration(/* targetPreloadDurationUs= */ 5_000_000L))
        val localPlayer = SwitchableLocalPlayer(player)
        val mediaServer = LocalCastMediaServer(this)
        val sessionPlayer: Player = runCatching {
            val remotePlayer = RemoteCastPlayer.Builder(this)
                .setMediaItemConverter(HalcyonCastMediaItemConverter(mediaServer))
                .build()
            CastPlayer.Builder(this)
                .setLocalPlayer(localPlayer)
                .setRemotePlayer(remotePlayer)
                .build()
        }.onSuccess {
            castMediaServer = mediaServer
            AppLogStore.info(this, TAG, "Chromecast player initialized")
        }.getOrElse { error ->
            mediaServer.release()
            AppLogStore.warn(this, TAG, "Chromecast unavailable; using local player: ${error.message}")
            localPlayer
        }
        serviceScope.launch {
            settingsManager.openPlayerFromNotification.collect { enabled ->
                openPlayerFromNotification = enabled
                mediaSession?.setSessionActivity(buildSessionActivityPendingIntent(enabled))
            }
        }
        crossfadePlaybackCoordinator = CrossfadePlaybackCoordinator(
            context = this,
            initialPrimary = player,
            dataSourceFactory = dataSourceFactory,
            audioAttributes = mediaAudioAttributes,
            initialPrimaryWaveformProbe = primaryWaveformProbe,
            initialSecondaryWaveformProbe = secondaryWaveformProbe,
            initialPrimaryGainProcessor = primaryTransitionGain,
            initialSecondaryGainProcessor = secondaryTransitionGain,
            secondaryRenderersFactory = {
                EllaRenderersFactory(this).apply {
                    setExtensionRendererMode(
                        when (decoderMode) {
                            1 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                            2 -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                            else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                        }
                    )
                    setEnableDecoderFallback(decoderMode != 0)
                    // AudioProcessor instances are stateful and belong to exactly one AudioSink.
                    // Sharing the primary instances lets either player flush/reset the other's DSP.
                    setEqualizerAudioProcessor(secondaryEqualizerAudioProcessor)
                    setExtraAudioProcessors(listOf(secondaryKaraokeAccompanimentProcessor, secondaryWaveformProbe))
                    setTransitionGainAudioProcessor(secondaryTransitionGain)
                    setPlaybackOutputSettings(playbackOutputSettings)
                }
            },
            onPrimaryPromoted = { promotedPlayer ->
                val promotedMediaId = promotedPlayer.currentMediaItem?.mediaId
                localPlayer.promote(promotedPlayer)
                PlaybackAudioSession.update(promotedPlayer.audioSessionId)
                audioEffectController.bind(promotedPlayer.audioSessionId)
                crossfadePresentationSettlementJob?.cancel()
                crossfadePresentationSettlementJob = serviceScope.launch {
                    // ForwardingSimpleBasePlayer invalidates its delegate synchronously, while
                    // CastPlayer -> RepeatOneLockingPlayer -> MediaSession clients receive that
                    // invalidation on following main-loop turns. Keep the explicit incoming-song
                    // presentation active until the complete forwarding chain has reported the
                    // promoted media item for several consecutive frames. Clearing it immediately
                    // lets a late callback from the retired player overwrite cover/title/lyrics
                    // with the outgoing song even though audio and system metadata are already on
                    // the target.
                    var stableChecks = 0
                    repeat(CROSSFADE_PROMOTION_SETTLE_MAX_CHECKS) {
                        val activeMatches = promotedMediaId != null &&
                            localPlayer.activePlayer.currentMediaItem?.mediaId == promotedMediaId
                        val sessionMatches = promotedMediaId != null &&
                            sessionPlayer.currentMediaItem?.mediaId == promotedMediaId
                        stableChecks = if (activeMatches && sessionMatches) stableChecks + 1 else 0
                        if (stableChecks >= CROSSFADE_PROMOTION_STABLE_CHECKS) {
                            sessionPresentationPlayer?.clearCrossfadePresentation()
                            val publishedPlayer = sessionPresentationPlayer ?: sessionPlayer
                            publishExternalPlaybackSnapshot(publishedPlayer)
                            PlaybackWidgetUpdater.updateFromPlayer(this@PlaybackService, publishedPlayer)
                            oplusLyricHandler.refreshCurrentOplusLyricInfo(sessionPlayer)
                            Log.d(
                                TIMING_TAG,
                                "crossfade presentation committed mediaId=$promotedMediaId"
                            )
                            return@launch
                        }
                        delay(CROSSFADE_PROMOTION_SETTLE_CHECK_MS)
                    }
                    // The promoted decoder is authoritative. Do not expose the outgoing song on
                    // timeout; retain the target presentation and leave a precise diagnostic for
                    // vendor-specific forwarding chains that fail to settle.
                    Log.w(
                        TIMING_TAG,
                        "crossfade presentation settle timed out mediaId=$promotedMediaId " +
                            "sessionMediaId=${sessionPlayer.currentMediaItem?.mediaId}"
                    )
                }
            },
            onIncomingAudible = { targetIndex, positionMs, playbackSpeed ->
                sessionPresentationPlayer?.presentCrossfadeTarget(
                    targetIndex = targetIndex,
                    positionMs = positionMs,
                    playbackSpeed = playbackSpeed
                )
            },
            onIncomingFinished = {
                crossfadePresentationSettlementJob?.cancel()
                crossfadePresentationSettlementJob = null
                sessionPresentationPlayer?.clearCrossfadePresentation()
            },
            scope = serviceScope
        )
        serviceScope.launch {
            settingsManager.karaokeAccompanimentEnabled.collect { enabled ->
                karaokeAccompanimentProcessor.enabled = enabled
                secondaryKaraokeAccompanimentProcessor.enabled = enabled
            }
        }
        serviceScope.launch {
            settingsManager.crossfadeDurationMs.collect { durationMs ->
                crossfadePlaybackCoordinator?.setDuration(durationMs)
            }
        }
        serviceScope.launch {
            settingsManager.crossfadeCurve.collect { curve ->
                crossfadePlaybackCoordinator?.setCurve(curve)
            }
        }
        PlaybackAudioSession.update(player.audioSessionId)
        audioEffectController.bind(player.audioSessionId)
        serviceScope.launch {
            PlaybackAudioSession.audioSessionId.collect { sessionId ->
                if (sessionId > 0) {
                    audioEffectController.bind(sessionId)
                }
            }
        }
        serviceScope.launch {
            combine(
                settingsManager.audioEffectSettings,
                settingsManager.platformSpatialAudioEnabled
            ) { settings, platformSpatial -> settings to platformSpatial }.collect { (settings, platformSpatial) ->
                val customSpatialEnabled = settings.surround360Enabled || settings.panoramic360Enabled
                val attributesNeedUpdate = customSurroundEnabled != customSpatialEnabled ||
                    platformSpatialRequested != platformSpatial
                customSurroundEnabled = customSpatialEnabled
                platformSpatialRequested = platformSpatial
                if (attributesNeedUpdate) {
                    mediaAudioAttributes = resolvedAudioAttributes()
                    localPlayer.activePlayer.setAudioAttributes(mediaAudioAttributes, handleAudioFocus)
                    crossfadePlaybackCoordinator?.setAudioAttributes(mediaAudioAttributes)
                }
                audioEffectController.apply(settings)
                val equalizerSettings = EqualizerSettings(
                        enabled = settings.eqEnabled,
                        bandGainsDb = FloatArray(TenBandEqualizer.BAND_COUNT) { index ->
                            settings.eqBandLevelsMb.getOrElse(index) { 0 } / 100f
                        },
                        masterGainDb = if (settings.masterGainEnabled) settings.masterGainTenthsDb / 10f else 0f,
                        eqQ = settings.eqQ / 100f,
                        bassGainDb = settings.bassGainDb.toFloat(),
                        trebleGainDb = settings.trebleGainDb.toFloat(),
                        compressorEnabled = settings.compressorEnabled,
                        compressorThresholdDb = settings.compressorThresholdDb.toFloat(),
                        compressorRatio = settings.compressorRatio.toFloat(),
                        compressorMakeupDb = settings.compressorMakeupDb.toFloat(),
                        stereoWidth = settings.stereoWidth / 100f,
                        reverbPreset = settings.reverbPreset,
                        surround360Enabled = settings.surround360Enabled,
                        surround360Intensity = settings.surround360Intensity.toFloat(),
                        surround360RotationSpeed = settings.surround360RotationSpeed.toFloat(),
                        panoramic360Enabled = settings.panoramic360Enabled,
                        panoramic360Intensity = settings.panoramic360Intensity.toFloat(),
                        panoramic360AzimuthDegrees = settings.panoramic360AzimuthDegrees.toFloat(),
                        panoramic360ElevationDegrees = settings.panoramic360ElevationDegrees.toFloat(),
                        loudnessBalanceEnabled = settings.loudnessBalanceEnabled,
                        loudnessPercent = settings.loudnessPercent.toFloat(),
                        channelBalance = settings.channelBalance.toFloat(),
                        crossfeedEnabled = settings.crossfeedEnabled,
                        crossfeedLowCutHz = settings.crossfeedLowCutHz.toFloat(),
                        crossfeedHighCutHz = settings.crossfeedHighCutHz.toFloat(),
                        crossfeedAttenuationDb = settings.crossfeedAttenuationDbTenths / 10f,
                        monoBassEnabled = settings.monoBassEnabled,
                        monoBassCrossoverHz = settings.monoBassCrossoverHz.toFloat(),
                        monoBassAmount = settings.monoBassAmount.toFloat(),
                        speakerOutputEnabled = settings.speakerOutputEnabled,
                        speakerOutputMode = settings.speakerOutputMode,
                        speakerOutputStrength = settings.speakerOutputStrength.toFloat(),
                        dynamicEqEnabled = settings.dynamicEqEnabled,
                        dynamicEqIntensity = settings.dynamicEqIntensity.toFloat(),
                        deEsserAmount = settings.deEsserAmount.toFloat(),
                        deEsserFrequencyHz = settings.deEsserFrequencyHz.toFloat(),
                        moogLadderEnabled = settings.moogLadderEnabled,
                        moogLadderMode = settings.moogLadderMode,
                        moogLadderCutoffHz = settings.moogLadderCutoffHz.toFloat(),
                        moogLadderResonance = settings.moogLadderResonance.toFloat(),
                        moogLadderDriveDb = settings.moogLadderDriveDb.toFloat(),
                        moogLadderMix = settings.moogLadderMix.toFloat(),
                        peakLimiterEnabled = settings.peakLimiterEnabled
                    )
                equalizerAudioProcessor.setSettings(equalizerSettings)
                secondaryEqualizerAudioProcessor.setSettings(equalizerSettings)
            }
        }
        var localOutputBackend = PlaybackAudioOutputState.info.value.outputBackend
        sessionPlayer.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                PlaybackAudioSession.update(audioSessionId)
                audioEffectController.bind(audioSessionId)
                if (localPlayer.activePlayer.isPlaying) openAudioEffectSession(audioSessionId)
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                val isLocalPlayback = sessionPlayer.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE
                if (isPlaying && isLocalPlayback) {
                    // Retry attaching effects now that the audio track is live: some ROMs
                    // (e.g. ColorOS/OxygenOS) reject Equalizer creation before playback starts.
                    val activeAudioSessionId = localPlayer.activePlayer.audioSessionId
                    audioEffectController.bind(activeAudioSessionId)
                    openAudioEffectSession(activeAudioSessionId)
                } else if (crossfadePlaybackCoordinator?.isIncomingAudible() != true) {
                    // The session player briefly stops its AudioTrack while seeking into the
                    // already-audible incoming item. Keep the effects session and public playback
                    // state alive until that PCM handoff has actually completed.
                    closeAudioEffectSession()
                }
                val publishedPlayer = sessionPresentationPlayer ?: sessionPlayer
                publishExternalPlaybackSnapshot(publishedPlayer)
                PlaybackWidgetUpdater.updateFromPlayer(this@PlaybackService, publishedPlayer)
            }

            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                notifyLibraryChanged(sessionPlayer.mediaItemCount)
                oplusLyricHandler.refreshCurrentOplusLyricInfo(sessionPlayer)
                scheduleLegacyArtworkCompat(sessionPlayer.mediaMetadata)
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                updateMediaButtonPreferences()
                PlaybackWidgetUpdater.updateFromPlayer(
                    this@PlaybackService,
                    sessionPresentationPlayer ?: sessionPlayer
                )
                scheduleLegacyArtworkCompat(mediaMetadata)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d(TIMING_TAG, "service media transition reason=$reason mediaId=${mediaItem?.mediaId}")
                localPlayer.activePlayer.setWakeMode(
                    if (mediaItem?.localConfiguration?.uri?.scheme.equals("http", ignoreCase = true) ||
                        mediaItem?.localConfiguration?.uri?.scheme.equals("https", ignoreCase = true)
                    ) {
                        C.WAKE_MODE_NETWORK
                    } else {
                        C.WAKE_MODE_LOCAL
                    }
                )
                sessionPresentationPlayer?.clearNotificationLyric()
                updateMediaButtonPreferences()
                oplusLyricHandler.refreshCurrentOplusLyricInfo(sessionPlayer)
                val publishedPlayer = sessionPresentationPlayer ?: sessionPlayer
                publishExternalPlaybackSnapshot(publishedPlayer)
                PlaybackWidgetUpdater.updateFromPlayer(this@PlaybackService, publishedPlayer)
            }

            override fun onTracksChanged(tracks: Tracks) {
                if (sessionPlayer.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                    PlaybackAudioOutputState.clear()
                    PlaybackAudioOutputState.updateBackend("Chromecast")
                    return
                }
                val selectedAudioFormat = tracks.groups.asSequence()
                    .filter { it.type == C.TRACK_TYPE_AUDIO }
                    .flatMap { group ->
                        (0 until group.length).asSequence()
                            .filter(group::isTrackSelected)
                            .map(group::getTrackFormat)
                    }
                    .firstOrNull()
                PlaybackAudioOutputState.updateSource(selectedAudioFormat)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> Log.d(TIMING_TAG, "player state BUFFERING mediaId=${sessionPlayer.currentMediaItem?.mediaId}")
                    Player.STATE_READY -> {
                        Log.d(TIMING_TAG, "player state READY mediaId=${sessionPlayer.currentMediaItem?.mediaId}")
                        if (sessionPlayer.deviceInfo.playbackType != DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                            audioEffectController.bind(localPlayer.activePlayer.audioSessionId)
                        }
                    }
                    Player.STATE_ENDED -> Log.d(TIMING_TAG, "player state ENDED mediaId=${sessionPlayer.currentMediaItem?.mediaId}")
                }
                val publishedPlayer = sessionPresentationPlayer ?: sessionPlayer
                publishExternalPlaybackSnapshot(publishedPlayer)
                PlaybackWidgetUpdater.updateFromPlayer(this@PlaybackService, publishedPlayer)
            }

            override fun onDeviceInfoChanged(deviceInfo: DeviceInfo) {
                if (deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_REMOTE) {
                    localOutputBackend = PlaybackAudioOutputState.info.value.outputBackend
                    closeAudioEffectSession()
                    PlaybackAudioSession.clear()
                    PlaybackAudioOutputState.clear()
                    PlaybackAudioOutputState.updateBackend("Chromecast")
                    crossfadePlaybackCoordinator?.setDuration(0)
                } else {
                    PlaybackAudioSession.update(localPlayer.activePlayer.audioSessionId)
                    PlaybackAudioOutputState.updateBackend(localOutputBackend)
                    serviceScope.launch {
                        crossfadePlaybackCoordinator?.setDuration(settingsManager.crossfadeDurationMs.first())
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                updateMediaButtonPreferences()
                notificationProvider.refresh()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateMediaButtonPreferences()
                notificationProvider.refresh()
                publishExternalPlaybackSnapshot(sessionPresentationPlayer ?: sessionPlayer)
            }
        })

        val pendingIntent = buildSessionActivityPendingIntent(openPlayerFromNotification)

        val presentationPlayer = SessionPresentationPlayer(
            player = RepeatOneLockingPlayer(
                player = sessionPlayer,
                previousButtonActionProvider = { previousButtonAction },
                onExternalPlaybackChanged = ::scheduleExternalPlaybackRefresh
            ),
            cachedOplusLyricProvider = { songKey -> oplusLyricHandler.peekLyricInfoJson(songKey) }
        )
        presentationPlayer.setKeepSongIdentityMetadata(
            OPlusLyricPublishPolicy.shouldKeepSongIdentityMetadata(colorOsLockScreenLyricEnabled)
        )
        sessionPresentationPlayer = presentationPlayer
        mediaSession = MediaLibrarySession.Builder(
            this,
            presentationPlayer,
            EllaLibrarySessionCallback(this)
        )
            .setSessionActivity(pendingIntent)
            .build()
        scheduleLegacyArtworkCompat(presentationPlayer.mediaMetadata)

        updateMediaButtonPreferences()

        // Register Bluetooth auto-play receiver
        bluetoothReceiver = BluetoothAutoPlayReceiver(
            isAutoPlayEnabled = { bluetoothAutoPlayEnabled }
        ) {
            scheduleBluetoothAutoPlayIfConnected("bluetooth broadcast")
        }
        ensureBluetoothAutoPlayReceiverRegistered()
        scheduleBluetoothAutoPlayIfConnected("service started")

        Log.i(TAG, "PlaybackService created")
        AppLogStore.info(this, TAG, "PlaybackService created")
    }

    private fun buildSessionActivityPendingIntent(openPlayer: Boolean): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
            .setAction(MainActivity.ACTION_MEDIA_NOTIFICATION_CLICK)
            .putExtra(MainActivity.EXTRA_OPEN_PLAYER_FROM_NOTIFICATION, openPlayer)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        return PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return mediaSession
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        mediaSession?.player?.let { player ->
            when (intent?.action) {
                ACTION_WIDGET_PREVIOUS,
                ACTION_SKIP_PREVIOUS -> player.seekToPreviousMediaItem()
                ACTION_WIDGET_PLAY_PAUSE,
                ACTION_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
                ACTION_WIDGET_NEXT,
                ACTION_SKIP_NEXT -> player.seekToNextMediaItem()
            }
            PlaybackWidgetUpdater.updateFromPlayer(this, player)
        }
        // Media playback must remain restartable after the system reclaims a background process.
        // Media3 rebuilds the session from the saved queue when the service is recreated.
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        // During a network rebuffer ExoPlayer can briefly report !isPlaying although the user has
        // not stopped playback. Do not turn that transient state into a service teardown when the
        // task is removed; keeping the media session alive also keeps Lyricon/desktop bridges alive.
        // Never tear down a prepared MediaLibraryService because its task was swiped. Some ROMs
        // deliver this while Bluetooth playback is transitioning or while the app is backgrounded.
        // Media3 owns the foreground notification and shuts down only after the real queue ends.
        if ((player?.mediaItemCount ?: 0) > 0) publishExternalPlaybackSnapshot(player)
    }

    override fun onDestroy() {
        legacyArtworkPublishSequence++
        LegacyArtworkCompat.clear()
        PlaybackWidgetUpdater.stopProgressUpdates()
        bluetoothReceiver?.let {
            runCatching { unregisterReceiver(it) }
            bluetoothReceiver = null
            bluetoothReceiverRegistered = false
        }
        audioEffectController.release()
        AudioEffectState.publish(null)
        crossfadePresentationSettlementJob?.cancel()
        crossfadePresentationSettlementJob = null
        crossfadePlaybackCoordinator?.release()
        crossfadePlaybackCoordinator = null
        mediaSession?.run {
            closeAudioEffectSession()
            player.release()
            release()
        }
        mediaSession = null
        sessionPresentationPlayer = null
        castMediaServer?.release()
        castMediaServer = null
        usbAudioController.clearUsbRouting()
        honorHdAudioSupport?.release()
        honorHdAudioSupport = null
        PlaybackAudioSession.clear()
        PlaybackAudioOutputState.clear()
        serviceScope.cancel()
        super.onDestroy()
    }

    /**
     * Media3 publishes DISPLAY_ICON and ALBUM_ART to its legacy platform session but omits ART.
     * Some OEM lock screens only read ART. Salt Player writes the same 500 px bitmap to all three
     * fields, so mirror that compatibility behaviour after Media3 has finished its own update.
     */
    private fun scheduleLegacyArtworkCompat(metadata: MediaMetadata) {
        val session = mediaSession
        val sequence = ++legacyArtworkPublishSequence
        if (session == null) return
        val bitmapFuture = session.bitmapLoader.loadBitmapFromMetadata(metadata) ?: return
        bitmapFuture.addListener(
            {
                val bitmap = runCatching { Futures.getDone(bitmapFuture) }.getOrNull() ?: return@addListener
                serviceScope.launch {
                    // Media3 also posts an async legacy metadata update. Publish after it so ART is
                    // not immediately replaced by Media3's ALBUM_ART-only metadata.
                    delay(64L)
                    if (sequence != legacyArtworkPublishSequence || mediaSession !== session) return@launch
                    val player = session.player
                    runCatching {
                        LegacyArtworkCompat.publish(
                            session,
                            player.mediaMetadata,
                            player.currentMediaItem?.mediaId.orEmpty(),
                            player.duration,
                            bitmap
                        )
                    }
                        .onFailure { error ->
                            AppLogStore.warn(
                                this@PlaybackService,
                                "LegacyArtwork",
                                "Failed to publish lock-screen ART metadata",
                                error
                            )
                        }
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    fun launchServiceJob(block: suspend () -> Unit) {
        serviceScope.launch { block() }
    }

    private fun ensureBluetoothAutoPlayReceiverRegistered() {
        if (bluetoothReceiverRegistered) return
        val receiver = bluetoothReceiver ?: return
        if (!BluetoothAutoPlayReceiver.hasBluetoothConnectPermission(this)) {
            Log.w(TAG, "Bluetooth auto-play receiver not registered: missing BLUETOOTH_CONNECT")
            AppLogStore.warn(this, "BtAutoPlay", "Missing BLUETOOTH_CONNECT; Bluetooth auto-play cannot listen for connections")
            return
        }
        runCatching {
            registerReceiver(receiver, BluetoothAutoPlayReceiver.createIntentFilter())
            bluetoothReceiverRegistered = true
            AppLogStore.info(this, "BtAutoPlay", "Bluetooth auto-play receiver registered")
        }.onFailure { error ->
            Log.w(TAG, "Bluetooth auto-play receiver registration failed", error)
            AppLogStore.warn(this, "BtAutoPlay", "Bluetooth auto-play receiver registration failed: ${error.message.orEmpty()}")
        }
    }

    private fun scheduleBluetoothAutoPlayIfConnected(reason: String) {
        if (!bluetoothAutoPlayEnabled) return
        serviceScope.launch {
            if (!BluetoothAutoPlayReceiver.isBluetoothAudioConnected(this@PlaybackService)) {
                delay(700)
            }
            triggerBluetoothAutoPlayIfConnected(reason)
        }
    }

    private fun triggerBluetoothAutoPlayIfConnected(reason: String) {
        if (!bluetoothAutoPlayEnabled) return
        if (!BluetoothAutoPlayReceiver.isBluetoothAudioConnected(this)) {
            AppLogStore.info(this, "BtAutoPlay", "Ignored $reason: no active Bluetooth output route")
            return
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastBluetoothAutoPlayAttemptMs < 1_500L) {
            AppLogStore.info(this, "BtAutoPlay", "Ignored duplicate Bluetooth auto-play event from $reason")
            return
        }
        lastBluetoothAutoPlayAttemptMs = now

        val player = mediaSession?.player
        if (player != null && player.mediaItemCount > 0 && !player.isPlaying) {
            player.play()
            AppLogStore.info(this, "BtAutoPlay", "Started playback from $reason")
        } else {
            AppLogStore.info(this, "BtAutoPlay", "Emitting queue restore event from $reason")
        }
        bluetoothConnectEvent.tryEmit(Unit)
    }

    private fun openAudioEffectSession(audioSessionId: Int) {
        if (audioSessionId <= 0) return
        if (openedAudioEffectSessionId == audioSessionId) return
        closeAudioEffectSession()
        sendBroadcast(Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
            putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
        })
        openedAudioEffectSessionId = audioSessionId
    }

    private fun closeAudioEffectSession() {
        val audioSessionId = openedAudioEffectSessionId
        if (audioSessionId <= 0) return
        sendBroadcast(Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
            putExtra(AudioEffect.EXTRA_AUDIO_SESSION, audioSessionId)
            putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
        })
        openedAudioEffectSessionId = -1
    }

    fun handleNotificationCustomAction(action: String): Boolean {
        return when (action) {
            ACTION_TOGGLE_SHUFFLE -> {
                AppLogStore.info(this, TAG, "NotificationAction playback mode clicked")
                mediaSession?.player?.let { player ->
                    player.cycleNotificationPlaybackMode()
                    publishExternalPlaybackModeSnapshot(player)
                }
                updateMediaButtonPreferences()
                notificationProvider.refresh()
                true
            }

            ACTION_TOGGLE_FAVORITE -> {
                AppLogStore.info(this, TAG, "NotificationAction favorite clicked")
                val song = mediaSession?.player?.currentMediaItem?.toSongFromMediaItemExtras()
                if (song == null) {
                    AppLogStore.warn(this, TAG, "NotificationAction currentMediaItem cannot restore Song")
                    return true
                }
                serviceScope.launch {
                    val added = PlaylistStore.getInstance(this@PlaybackService).toggleFavorite(song)
                    AppLogStore.info(
                        this@PlaybackService,
                        TAG,
                        "NotificationAction favorite toggled added=$added"
                    )
                    updateMediaButtonPreferences()
                    notificationProvider.refresh()
                }
                true
            }

            ACTION_TOGGLE_DESKTOP_LYRIC -> {
                AppLogStore.info(this, TAG, "NotificationAction desktop lyric clicked")
                serviceScope.launch {
                    val nextEnabled = !settingsManager.desktopLyricEnabled.first()
                    val locked = settingsManager.desktopLyricLocked.first()
                    if (!nextEnabled && locked) {
                        settingsManager.setDesktopLyricLocked(false)
                        desktopLyricBridge.unlock()
                    } else {
                        settingsManager.setDesktopLyricEnabled(nextEnabled)
                        desktopLyricBridge.setEnabled(nextEnabled)
                        if (nextEnabled) {
                            PlaybackTickerState.current()?.text?.let(desktopLyricBridge::sendLyric)
                        }
                    }
                    updateMediaButtonPreferences()
                    notificationProvider.refresh()
                }
                true
            }

            ACTION_SKIP_PREVIOUS -> {
                AppLogStore.info(this, TAG, "NotificationAction previous clicked")
                mediaSession?.player?.seekToPreviousMediaItem()
                scheduleExternalPlaybackRefresh()
                true
            }

            ACTION_PLAY_PAUSE -> {
                AppLogStore.info(this, TAG, "NotificationAction play/pause clicked")
                mediaSession?.player?.let { player ->
                    if (player.isPlaying) {
                        player.pause()
                    } else {
                        player.play()
                    }
                }
                scheduleExternalPlaybackRefresh()
                true
            }

            ACTION_SKIP_NEXT -> {
                AppLogStore.info(this, TAG, "NotificationAction next clicked")
                mediaSession?.player?.seekToNextMediaItem()
                scheduleExternalPlaybackRefresh()
                true
            }

            ACTION_TOGGLE_TRANSLATION -> {
                AppLogStore.info(this, TAG, "Lockscreen translation action delegated to bridge module")
                true
            }

            else -> false
        }
    }

    internal fun prepareOplusLyricForSetMediaItems(
        mediaItems: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long
    ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
        val result = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
        val job = serviceScope.launch {
            var preparedMediaItems = mediaItems
            try {
                withTimeoutOrNull(OPlusLyricPublishPolicy.INITIAL_PREPARE_TIMEOUT_MS) {
                    preparedMediaItems = oplusLyricHandler.prepareInitialOplusLyricInfo(mediaItems, startIndex)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(TAG, "Failed to attach OPlus lyricInfo before initial publish", error)
            }
            if (!result.isDone) {
                result.set(
                    MediaSession.MediaItemsWithStartPosition(
                        preparedMediaItems,
                        startIndex,
                        startPositionMs
                    )
                )
            }
        }
        job.invokeOnCompletion { cause ->
            if (cause == null || result.isDone) return@invokeOnCompletion
            if (cause is CancellationException) {
                result.cancel(false)
            } else {
                result.setException(cause)
            }
        }
        return result
    }

    internal fun updateNotificationLyricPresentation(args: Bundle): Boolean {
        val presentationPlayer = sessionPresentationPlayer ?: return false
        val currentSong = presentationPlayer.currentMediaItem?.toSongFromMediaItemExtras() ?: return false
        val songKey = args.getString(EXTRA_NOTIFICATION_LYRIC_SONG_KEY).orEmpty()
        if (songKey != currentSong.playbackStackKey()) return false

        presentationPlayer.setNotificationLyric(
            songKey = songKey,
            title = args.getString(EXTRA_NOTIFICATION_LYRIC_TEXT),
            secondaryText = args.getString(EXTRA_NOTIFICATION_LYRIC_SECONDARY_TEXT)
        )
        // Flyme's hidden ticker path already refreshes the app notification from the same lyric
        // snapshot. Other devices still need one explicit refresh for the app-owned media card.
        if (PlaybackTickerState.current() == null) notificationProvider.refresh()
        return true
    }

    @OptIn(UnstableApi::class)
    private fun updateMediaButtonPreferences() {
        val session = mediaSession ?: return
        val player = session.player

        val currentSong = player.currentMediaItem?.toSongFromMediaItemExtras()
        val isFavorite = currentSong?.let {
            PlaylistStore.getInstance(this).isFavorite(it)
        } == true

        appShuffleEnabled = loadAppShuffleEnabled()
        val playbackModeAction = player.notificationPlaybackModeAction()
        val selectedButtons = mediaNotificationButtonIds.toSet()
        val buttons = mutableListOf<CommandButton>()

        if (player.shouldPublishOplusTranslationAction()) {
            buttons += CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(getString(R.string.settings_status_secondary_translation))
                .setCustomIconResId(R.drawable.ic_translation)
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_TRANSLATION, Bundle.EMPTY))
                .build()
        }

        if (SettingsManager.MEDIA_NOTIFICATION_BUTTON_DESKTOP_LYRIC in selectedButtons) {
            buttons += CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(getString(R.string.notification_action_desktop_lyric))
                .setCustomIconResId(desktopLyricNotificationIcon())
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_DESKTOP_LYRIC, Bundle.EMPTY))
                .build()
        }

        if (SettingsManager.MEDIA_NOTIFICATION_BUTTON_FAVORITE in selectedButtons) {
            buttons += CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(if (isFavorite) getString(R.string.common_unfavorite) else getString(R.string.common_favorite))
                .setCustomIconResId(
                    if (isFavorite) {
                        R.drawable.ic_notification_favorite_filled
                    } else {
                        R.drawable.ic_notification_favorite
                    }
                )
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_FAVORITE, Bundle.EMPTY))
                .build()
        }

        buttons += CommandButton.Builder(CommandButton.ICON_PREVIOUS)
            .setDisplayName(getString(R.string.common_previous))
            .setCustomIconResId(R.drawable.ic_skip_previous)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .build()

        buttons += CommandButton.Builder(
            if (player.isPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY
        )
            .setDisplayName(if (player.isPlaying) getString(R.string.common_pause) else getString(R.string.common_play))
            .setCustomIconResId(
                if (player.isPlaying) {
                    R.drawable.ic_player_pause
                } else {
                    R.drawable.ic_player_play
                }
            )
            .setPlayerCommand(Player.COMMAND_PLAY_PAUSE)
            .build()

        buttons += CommandButton.Builder(CommandButton.ICON_NEXT)
            .setDisplayName(getString(R.string.common_next))
            .setCustomIconResId(R.drawable.ic_skip_next)
            .setPlayerCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

        if (SettingsManager.MEDIA_NOTIFICATION_BUTTON_PLAYBACK_MODE in selectedButtons) {
            buttons += CommandButton.Builder(CommandButton.ICON_UNDEFINED)
                .setDisplayName(playbackModeAction.title)
                .setCustomIconResId(playbackModeAction.icon)
                .setSessionCommand(SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY))
                .build()
        }

        session.setMediaButtonPreferences(ImmutableList.copyOf(buttons))
    }

    internal fun xiaomiMediaIslandShareParams(song: Song): String? {
        val tagInfo = musicRepository.getCachedSongTagInfo(song)
            ?: musicRepository.getSongTagInfo(song)
        val shareContent = decodeNeteaseKey(tagInfo.neteaseKey)
            ?.musicId
            ?.takeIf { it.isNotBlank() }
            ?.let(::neteaseShareSongUrl)
            ?: listOf(song.title.trim(), song.artist.trim())
                .filter(String::isNotBlank)
                .joinToString(" - ")
                .ifBlank { song.fileName.trim() }
                .takeIf(String::isNotBlank)
                ?: return null
        return song.toXiaomiMediaIslandShareParams(shareContent)
    }

    private fun Player.shouldPublishOplusTranslationAction(): Boolean {
        val lyricInfoJson = mediaMetadata.extras
            ?.getString(OPlusLyricHandler.OPLUS_LYRIC_INFO_KEY)
        return OPlusTranslationActionPolicy.shouldPublish(
            colorOsLyricEnabled = colorOsLockScreenLyricEnabled,
            deliveryMode = oplusLyricHandler.colorOsLockScreenLyricMode,
            lyricInfoJson = lyricInfoJson
        )
    }

    private data class MediaButtonPlaybackModeAction(
        val icon: Int,
        val title: String
    )

    private fun Player.notificationPlaybackModeAction(): MediaButtonPlaybackModeAction {
        // The player page persists shuffle/repeat out-of-band; Media3's player fields can lag
        // one event behind a tap on the player page.
        appShuffleEnabled = loadAppShuffleEnabled()
        val persistedRepeat = loadAppRepeatMode()
        return when {
            appShuffleEnabled -> MediaButtonPlaybackModeAction(
                icon = R.drawable.ic_notification_shuffle,
                title = getString(R.string.notification_action_shuffle)
            )

            persistedRepeat == Player.REPEAT_MODE_ONE -> MediaButtonPlaybackModeAction(
                icon = R.drawable.ic_repeat_one,
                title = getString(R.string.notification_action_repeat_one)
            )

            persistedRepeat == Player.REPEAT_MODE_ALL -> MediaButtonPlaybackModeAction(
                icon = R.drawable.ic_repeat,
                title = getString(R.string.notification_action_repeat_all)
            )

            else -> MediaButtonPlaybackModeAction(
                icon = R.drawable.ic_playback_order,
                title = getString(R.string.notification_action_order)
            )
        }
    }

    private fun Player.cycleNotificationPlaybackMode() {
        // Start from the latest persisted shuffle flag (the player page may have changed it).
        appShuffleEnabled = loadAppShuffleEnabled()
        when {
            appShuffleEnabled -> {
                appShuffleEnabled = false
                persistAppShuffleEnabled(false)
                persistAppRepeatMode(Player.REPEAT_MODE_OFF)
                shuffleModeEnabled = false
                repeatMode = Player.REPEAT_MODE_OFF
            }

            loadAppRepeatMode() == Player.REPEAT_MODE_OFF -> {
                appShuffleEnabled = false
                persistAppShuffleEnabled(false)
                persistAppRepeatMode(Player.REPEAT_MODE_ALL)
                shuffleModeEnabled = false
                repeatMode = Player.REPEAT_MODE_ALL
            }

            loadAppRepeatMode() == Player.REPEAT_MODE_ALL -> {
                appShuffleEnabled = false
                persistAppShuffleEnabled(false)
                persistAppRepeatMode(Player.REPEAT_MODE_ONE)
                shuffleModeEnabled = false
                repeatMode = Player.REPEAT_MODE_ONE
            }

            else -> {
                appShuffleEnabled = true
                persistAppShuffleEnabled(true)
                persistAppRepeatMode(Player.REPEAT_MODE_ALL)
                repeatMode = Player.REPEAT_MODE_ALL
                // Temporary bridge for notification/headset next actions. ExoPlayerManager owns
                // the deferred Halcyon queue reorder; if it is disconnected, it will adopt this
                // native shuffle as pending (or disable it if no pending reorder is valid) when
                // the controller reconnects and refreshes state.
                shuffleModeEnabled = true
            }
        }
    }

    private fun persistAppShuffleEnabled(enabled: Boolean) {
        getSharedPreferences(PLAYBACK_PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_APP_SHUFFLE, enabled)
            .apply()
    }

    private fun persistAppRepeatMode(repeatMode: Int) {
        getSharedPreferences(PLAYBACK_PREFS, MODE_PRIVATE)
            .edit()
            .putInt(KEY_APP_REPEAT, repeatMode)
            .apply()
    }

    private fun loadAppShuffleEnabled(): Boolean =
        getSharedPreferences(PLAYBACK_PREFS, MODE_PRIVATE)
            .getBoolean(KEY_APP_SHUFFLE, appShuffleEnabled)

    private fun loadAppRepeatMode(): Int =
        getSharedPreferences(PLAYBACK_PREFS, MODE_PRIVATE)
            .getInt(KEY_APP_REPEAT, Player.REPEAT_MODE_OFF)

    private fun currentWebDavConfig(settingsManager: SettingsManager): WebDavConfig {
        return runBlocking(Dispatchers.IO) {
            WebDavConfig(
                url = settingsManager.webDavUrl.first(),
                username = settingsManager.webDavUsername.first(),
                password = settingsManager.webDavPassword.first()
            )
        }
    }

    private fun Int.decoderModeLabel(): String = when (this) {
        0 -> "system"
        1 -> "ffmpeg-prefer"
        2 -> "auto-system-first"
        else -> "unknown"
    }

    internal fun libraryRootItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(LIBRARY_ROOT_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.app_name))
                    .setDisplayTitle(getString(R.string.app_name))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    internal fun currentQueueFolderItem(): MediaItem {
        return MediaItem.Builder()
            .setMediaId(LIBRARY_QUEUE_ID)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(getString(R.string.notification_current_queue))
                    .setDisplayTitle(getString(R.string.notification_current_queue))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()
    }

    internal fun currentQueueItems(): List<MediaItem> {
        val player = mediaSession?.player ?: return emptyList()
        return List(player.mediaItemCount) { index ->
            player.getMediaItemAt(index).buildUpon()
                .setMediaMetadata(
                    player.getMediaItemAt(index).mediaMetadata.buildUpon()
                        .setIsBrowsable(false)
                        .setIsPlayable(true)
                        .build()
                )
                .build()
        }
    }

    private fun notifyLibraryChanged(itemCount: Int) {
        mediaSession?.notifyChildrenChanged(LIBRARY_ROOT_ID, 1, null)
        mediaSession?.notifyChildrenChanged(LIBRARY_QUEUE_ID, itemCount, null)
    }

    private fun scheduleExternalPlaybackRefresh() {
        serviceScope.launch {
            repeat(5) { attempt ->
                if (attempt > 0) delay(90L + attempt * 70L)
                updateMediaButtonPreferences()
                notificationProvider.refresh()
                publishExternalPlaybackSnapshot()
            }
        }
    }

    private fun publishExternalPlaybackSnapshot(player: Player? = mediaSession?.player) {
        val current = player ?: return
        val snapshot = PlaybackExternalSnapshot(
            mediaItem = current.currentMediaItem,
            mediaItemIndex = current.currentMediaItemIndex,
            mediaItemCount = current.mediaItemCount,
            positionMs = current.currentPosition.coerceAtLeast(0L),
            durationMs = current.duration.coerceAtLeast(0L),
            repeatMode = current.repeatMode,
            isPlaying = current.isPlaying,
            playbackState = current.playbackState,
            playWhenReady = current.playWhenReady
        )
        externalPlaybackSnapshot.value = snapshot
    }

    private fun publishExternalPlaybackModeSnapshot(player: Player? = mediaSession?.player) {
        val current = player ?: return
        appShuffleEnabled = loadAppShuffleEnabled()
        externalPlaybackModeEvent.tryEmit(
            PlaybackModeExternalSnapshot(
                shuffle = appShuffleEnabled,
                repeatMode = current.repeatMode
            )
        )
    }
}
