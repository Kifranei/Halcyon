package com.ella.music.ui.player

import android.app.Activity
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalView
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.normalizedAudioFormat
import com.ella.music.data.normalizedBitDepth
import com.ella.music.data.parser.LrcParser
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.player.PlaybackAudioSession
import com.ella.music.ui.components.LyricVideoProgress
import com.ella.music.ui.components.LyricVideoShareProgressOverlay
import com.ella.music.ui.components.generateLyricVideo
import com.ella.music.ui.components.shareLyricCard
import com.ella.music.ui.components.shareLyricVideoFile
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import com.ella.music.ui.settings.rememberMusicVideoSyncPermissionLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PlayerScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToMetadataCategory: (String, String) -> Unit = { _, _ -> },
    onNavigateToEqualizer: () -> Unit = {},
    onDismissProgressChange: (Float) -> Unit = {},
    openToken: Int = 0,
    playerVisible: Boolean = true
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val uriHandler = LocalUriHandler.current
    val view = LocalView.current
    val isLargeScreenDevice = configuration.smallestScreenWidthDp >= 600
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val playerSettings = rememberPlayerScreenSettings(settingsManager)
    val playerTapSeekEnabled = playerSettings.playerTapSeekEnabled
    val playerShowTotalDuration = playerSettings.playerShowTotalDuration
    val coverSwipeEnabled = playerSettings.coverSwipeEnabled
    val lyricParserEngine = playerSettings.lyricParserEngine
    val playerTitlePosition = playerSettings.playerTitlePosition
    val playerPageStyle = playerSettings.playerPageStyle
    val playerLandscapeStyle = playerSettings.playerLandscapeStyle
    val playerKeepScreenOn = playerSettings.playerKeepScreenOn
    val lyricSourceMode = playerSettings.lyricSourceMode
    val lyricFontState = rememberPlayerLyricFontState(context, settingsManager)
    val lyricFontFamily = lyricFontState.originalFontFamily
    val lyricTranslationFontFamily = lyricFontState.translationFontFamily
    val effectiveLyricFontPath = lyricFontState.originalFontPath
    val lyricFontWeight = lyricFontState.fontWeight
    val lyricLayoutProfile = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        configuration.smallestScreenWidthDp
    ) {
        resolvePlayerLyricLayoutProfile(
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp,
            smallestScreenWidthDp = configuration.smallestScreenWidthDp
        )
    }
    val lyricUltraWideScaleEnabled = remember(configuration.screenWidthDp, configuration.screenHeightDp) {
        isUltraWideLandscapePlayerLayout(
            screenWidthDp = configuration.screenWidthDp,
            screenHeightDp = configuration.screenHeightDp
        )
    }
    val lyricFontScaleRange = remember(lyricLayoutProfile, lyricUltraWideScaleEnabled) {
        lyricLayoutProfile.primaryScaleRangePercent(lyricUltraWideScaleEnabled)
    }
    val lyricSecondaryFontScaleRange = remember(lyricLayoutProfile, lyricUltraWideScaleEnabled) {
        lyricLayoutProfile.secondaryScaleRangePercent(lyricUltraWideScaleEnabled)
    }
    val lyricFontScale = lyricFontState.fontScale.coerceIn(
        lyricFontScaleRange.first / 100f,
        lyricFontScaleRange.last / 100f
    )
    val lyricSecondaryFontScale = lyricFontState.secondaryFontScale.coerceIn(
        lyricSecondaryFontScaleRange.first / 100f,
        lyricSecondaryFontScaleRange.last / 100f
    )
    val lyricPrimaryTextSizeSp = lyricFontState.primaryTextSizeSp(lyricLayoutProfile)
    val lyricSecondaryTextSizeSp = lyricFontState.secondaryTextSizeSp(lyricLayoutProfile)
    val lyricShareTypeface = lyricFontState.shareTypeface
    val currentSong by playerViewModel.currentSong.collectAsState()
    val currentSongKey = remember(currentSong) { currentSong?.playlistIdentityKey() }
    val isPlaying by playerViewModel.isPlaying.collectAsState()
    val currentPosition = rememberThrottledPlayerPosition(
        positionFlow = playerViewModel.currentPosition,
        isPlaying = isPlaying,
        anchorKey = currentSongKey,
        livePositionProvider = playerViewModel::livePositionMs
    )
    val duration by playerViewModel.duration.collectAsState()
    val shuffleEnabled by playerViewModel.shuffleEnabled.collectAsState()
    val repeatMode by playerViewModel.repeatMode.collectAsState()
    val playbackSpeed by playerViewModel.playbackSpeed.collectAsState()
    val playbackPitch by playerViewModel.playbackPitch.collectAsState()
    val audioSessionId by PlaybackAudioSession.audioSessionId.collectAsState()
    val audioVisualizerEnabled = playerSettings.audioVisualizerEnabled
    val audioVisualizerOpacity = playerSettings.audioVisualizerOpacity / 100f
    val dynamicCoverEnabled = playerSettings.dynamicCoverEnabled
    val musicVideoSyncEnabled = playerSettings.musicVideoSyncEnabled
    val dynamicCoverCustomFolders = playerSettings.dynamicCoverCustomFolders
    val musicVideoCustomFolders = playerSettings.musicVideoCustomFolders
    val immersiveAlbumCover = playerSettings.immersiveAlbumCover
    val coverContentColor = playerSettings.coverContentColor
    val playerBackgroundEnabled = playerSettings.playerBackgroundEnabled
    val playerBackgroundUri = playerSettings.playerBackgroundUri
    val playerBackgroundOpacity = playerSettings.playerBackgroundOpacity / 100f
    val playerBackgroundDim = playerSettings.playerBackgroundDim / 100f
    val beautifulLyricsBackground = playerSettings.beautifulLyricsBackground
    val playerDynamicFlowEnabled = playerSettings.playerDynamicFlowEnabled
    val hiResLogoEnabled = playerSettings.hiResLogoEnabled
    val hiResLogoUri = playerSettings.hiResLogoUri
    val lyricShareCustomInfo = playerSettings.lyricShareCustomInfo
    val metadataEditorId = playerSettings.metadataEditorId
    val lyricTimingEditorId = playerSettings.lyricTimingEditorId
    val sleepTimerCustomMinutes = playerSettings.sleepTimerCustomMinutes
    val sleepTimerStopAfterCurrent = playerSettings.sleepTimerStopAfterCurrent
    val playlists by mainViewModel.playlists.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()
    val artistCoverFolderUri by settingsManager.artistCoverFolderUri.collectAsState(initial = "")
    val playlist by playerViewModel.playlist.collectAsState()
    val lyrics by playerViewModel.lyrics.collectAsState()
    val lyricsLoading by playerViewModel.lyricsLoading.collectAsState()
    val lyricFormatAvailability by playerViewModel.lyricFormatAvailability.collectAsState()
    val preferTtmlLyrics by playerViewModel.preferTtmlLyrics.collectAsState()
    val currentLyricOffsetMs by playerViewModel.currentLyricOffsetMs.collectAsState()
    val currentLyricIndex by playerViewModel.currentLyricIndex.collectAsState()
    val showLyrics by playerViewModel.showLyrics.collectAsState()
    val showLyricTranslation by playerViewModel.showLyricTranslation.collectAsState()
    val showLyricPronunciation by playerViewModel.showLyricPronunciation.collectAsState()
    val lyricPageKeepScreenOn = playerSettings.lyricPageKeepScreenOn
    val appleMusicLyricsWordLift = playerSettings.appleMusicLyricsWordLift
    val lyricPerspectiveEffect = playerSettings.lyricPerspectiveEffect
    val lyricPerspectiveYAngle = playerSettings.lyricPerspectiveYAngle
    val playerLyricTextAlign = playerSettings.playerLyricTextAlign
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val ratingRevision by mainViewModel.ratingRevision.collectAsState()
    val sleepTimerEndRealtimeMs by playerViewModel.sleepTimerEndRealtimeMs.collectAsState()
    val stopAfterCurrentEnabled by playerViewModel.stopAfterCurrentEnabled.collectAsState()
    val currentLyricLine = lyrics.getOrNull(currentLyricIndex)
    val miniLyricLine = currentLyricLine
        ?.takeIf { it.hasMiniLyric() }
        ?: lyrics.firstOrNull { it.hasMiniLyric() }
    val uiState = rememberPlayerScreenUiState()
    val musicVideoPermissionLauncher = rememberMusicVideoSyncPermissionLauncher(settingsManager)
    val landscapeState = rememberPlayerLandscapeUiState()
    val musicVideoLandscapePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            uiState.musicVideoVisible = true
            landscapeState.expanded = true
        }
    }
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    LaunchedEffect(openToken, playerVisible, isLandscape, playerLandscapeStyle) {
        if (!playerVisible) {
            landscapeState.expanded = false
        } else if (isLandscape) {
            // A permanently-landscape device (for example an in-car display) never performs the
            // portrait-to-landscape rotation that used to open this host. Apply the user's chosen
            // landscape presentation as soon as the player page itself opens instead.
            landscapeState.expanded =
                playerLandscapeStyle != SettingsManager.PLAYER_LANDSCAPE_STYLE_WIDE
        }
    }
    val visualizerPermissionState = rememberPlayerVisualizerPermissionState(
        context = context,
        scope = scope,
        settingsManager = settingsManager,
        immersiveAlbumCover = immersiveAlbumCover,
        audioVisualizerEnabled = audioVisualizerEnabled,
        isPlaying = isPlaying,
        showLyrics = showLyrics,
        landscapeExpanded = landscapeState.expanded,
        largeScreenDevice = isLargeScreenDevice
    )
    val effectiveAudioVisualizerEnabled = visualizerPermissionState.effectiveEnabled
    val setAudioVisualizerEnabled = visualizerPermissionState.setEnabled
    val deletePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            uiState.pendingWriteRetry?.let { retry ->
                scope.launch { retry() }
            }
            uiState.pendingWriteRetry = null
        } else {
            uiState.pendingWriteRetry = null
        }
    }
    if (playerVisible) {
        PlayerSystemBarsEffect(
            context = context,
            view = view,
            trigger = landscapeState.expanded
        )
        PlayerSurfaceKeepScreenOnEffect(
            view = view,
            // Player always-on is a page-level choice, not a large-screen-only feature. The
            // same Activity hosts portrait and landscape player layouts, so this also covers
            // the landscape player while it is visible.
            keepScreenOn = (showLyrics && lyricPageKeepScreenOn) || playerKeepScreenOn
        )
    }

    val song = currentSong
    val isCurrentSongFavorite = song?.playlistIdentityKey()?.let { it in favoriteSongKeys } == true
    fun requestDeleteSong(targetSong: Song) {
        uiState.deleteConfirmSong = targetSong
    }
    val playerBackgroundTheme by settingsManager.playerBackgroundTheme
        .collectAsState(initial = SettingsManager.PLAYER_BG_THEME_DARK)
    val playerLight = when (playerBackgroundTheme) {
        SettingsManager.PLAYER_BG_THEME_LIGHT -> true
        SettingsManager.PLAYER_BG_THEME_DARK -> false
        else -> MiuixTheme.colorScheme.background.luminance() >= 0.5f
    }
    val songPresentation = rememberPlayerSongPresentationState(
        context = context,
        song = song,
        playerViewModel = playerViewModel,
        playerLight = playerLight
    )
    val embeddedCover = songPresentation.embeddedCover
    val paletteBitmap = songPresentation.paletteBitmap
    val palette = if (coverContentColor && !(landscapeState.expanded && uiState.musicVideoVisible)) {
        songPresentation.palette.withCoverContentColor()
    } else {
        songPresentation.palette
    }
    val lyricPalette = if (coverContentColor && !(landscapeState.expanded && uiState.musicVideoVisible)) {
        songPresentation.lyricPalette.withCoverContentColor()
    } else {
        songPresentation.lyricPalette
    }
    val audioInfo = songPresentation.audioInfo
    val tagInfo = songPresentation.tagInfo
    val songAnnotation = songPresentation.annotation
    val displayAnnotation = if (playerSettings.showSongAnnotation) songAnnotation else ""
    val neteaseInfo = songPresentation.neteaseInfo
    val lyricVideoShareEnabled = remember(song?.path, song?.mimeType, audioInfo?.format, audioInfo?.sampleRate, audioInfo?.bitDepth) {
        !isLyricVideoShareUnsupported(song, audioInfo)
    }
    var lyricShareInitialLine by remember { mutableStateOf<LyricLine?>(null) }
    fun openLyricSharePicker(line: LyricLine) {
        lyricShareInitialLine = line
    }
    fun shareSelectedLyrics(lines: List<LyricLine>, includeTranslation: Boolean) {
        shareLyricCard(
            context = context,
            song = song,
            lines = lines,
            cover = embeddedCover ?: paletteBitmap,
            backgroundColors = listOf(
                palette.top.toArgb(),
                palette.middle.toArgb(),
                palette.bottom.toArgb()
            ),
            annotation = songAnnotation,
            customInfo = lyricShareCustomInfo,
            shareTypeface = lyricShareTypeface,
            includeTranslation = includeTranslation
        )
        lyricShareInitialLine = null
    }
    var videoShareProgress by remember { mutableStateOf<LyricVideoProgress?>(null) }
    var videoShareGenerating by remember { mutableStateOf(false) }
    var videoShareJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    fun shareSelectedLyricsVideo(lines: List<LyricLine>, includeTranslation: Boolean) {
        lyricShareInitialLine = null
        videoShareGenerating = true
        videoShareProgress = LyricVideoProgress(0, 1)
        videoShareJob = scope.launch {
            val uri = generateLyricVideo(
                context = context,
                song = song,
                lines = lines,
                cover = embeddedCover ?: paletteBitmap,
                includeTranslation = includeTranslation,
                typeface = lyricShareTypeface,
                onProgress = { progress -> videoShareProgress = progress }
            )
            videoShareGenerating = false
            videoShareProgress = null
            videoShareJob = null
            if (uri != null) {
                withContext(Dispatchers.Main) {
                    shareLyricVideoFile(context, uri)
                }
            } else {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, context.getString(R.string.lyric_video_share_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    fun navigateToArtistOrChoose(artistText: String) {
        val artists = splitArtistNames(artistText)
            .distinctBy { it.tagIdentityKey() }
        when (artists.size) {
            0 -> Toast.makeText(context, context.getString(R.string.player_no_artist_jump), Toast.LENGTH_SHORT).show()
            1 -> onNavigateToArtist(artists.first())
            else -> uiState.artistChoices = artists
        }
    }
    fun openNetease(url: String?) {
        if (url.isNullOrBlank()) {
            Toast.makeText(context, context.getString(R.string.player_no_netease_jump), Toast.LENGTH_SHORT).show()
        } else {
            uriHandler.openUri(url)
        }
    }
    val playerPagerState = rememberPagerState(
        initialPage = PLAYER_PAGE_COVER,
        pageCount = { PLAYER_PAGE_COUNT }
    )
    LaunchedEffect(openToken) {
        if (playerPagerState.currentPage != PLAYER_PAGE_COVER) {
            playerPagerState.scrollToPage(PLAYER_PAGE_COVER)
        }
    }
    LaunchedEffect(song?.dynamicCoverResolutionKey()) {
        uiState.musicVideoVisible = false
        // Clear all remembered video positions so the next/previous song's MV or
        // dynamic cover starts from the beginning instead of resuming a stale position.
        DynamicCoverPlaybackMemory.clearAll()
    }
    // Sync the lyric parser engine setting to the LrcParser singleton at runtime.
    LaunchedEffect(lyricParserEngine) {
        LrcParser.parserEngine = lyricParserEngine
    }
    PlayerPagerSyncEffects(
        immersiveAlbumCover = immersiveAlbumCover,
        showLyrics = showLyrics,
        pagerState = playerPagerState,
        onShowLyricsChange = playerViewModel::setShowLyrics
    )

    PlayerDismissMotionHost(
        openToken = openToken,
        onDismissProgressChange = onDismissProgressChange,
        // Always retain an in-app back handler while the player overlay is visible. Disabling
        // it made Android fall through to MainActivity's default back action and finish the app.
        backEnabled = playerVisible,
        onDismiss = {
            playerViewModel.setShowLyrics(false)
            onBack()
        },
        overlayContent = {
            PlayerLyricShareHost(
                song = song,
                lyrics = lyrics,
                initialLine = lyricShareInitialLine,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                palette = palette,
                annotation = songAnnotation,
                customInfo = lyricShareCustomInfo,
                shareTypeface = lyricShareTypeface,
                onDismiss = { lyricShareInitialLine = null },
                onShare = ::shareSelectedLyrics,
                onVideoShare = if (lyricVideoShareEnabled) ::shareSelectedLyricsVideo else null
            )
            LyricVideoShareProgressOverlay(
                visible = videoShareGenerating,
                progress = videoShareProgress,
                onCancel = {
                    videoShareJob?.cancel()
                    videoShareJob = null
                    videoShareGenerating = false
                    videoShareProgress = null
                }
            )
        }
    ) { dismissingPlayer ->
        Box(modifier = Modifier.fillMaxSize()) {
          CompositionLocalProvider(
              // With cover colouring off, palette is the neutral variant: dark content on a
              // light player background, white on a dark one (the pre-1.2.3 behaviour). A
              // hardcoded white fallback made light backgrounds unreadable.
              LocalPlayerContentColor provides palette.onBackground,
              LocalPlayerSurfaceActive provides playerVisible
          ) {
            // Keep one background composed for both pages. Recreating Apple/Beautiful Lyrics
            // backgrounds while opening lyrics was the white flash seen on immersive players.
            SharedPlayerPageBackground(
                song = song,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                palette = palette,
                currentPositionMs = currentPosition,
                isPlaying = isPlaying,
                playerBackgroundEnabled = playerBackgroundEnabled,
                playerBackgroundUri = playerBackgroundUri,
                playerBackgroundOpacity = playerBackgroundOpacity,
                playerBackgroundDim = playerBackgroundDim,
                beautifulLyricsBackground = beautifulLyricsBackground,
                dynamicFlowEnabled = playerDynamicFlowEnabled,
                useBlurBackground = false,
                modifier = Modifier.fillMaxSize()
            )
            PlayerScreenPageHost(
                immersiveAlbumCover = immersiveAlbumCover,
                showLyrics = showLyrics,
                pagerState = playerPagerState,
                userScrollEnabled = !dismissingPlayer,
                onShowImmersiveLyrics = { playerViewModel.setShowLyrics(true) },
                onDismissImmersiveLyrics = { playerViewModel.setShowLyrics(false) },
                onShowPagedLyrics = {
                    scope.launch { playerPagerState.animateScrollToPage(PLAYER_PAGE_LYRICS) }
                },
                onDismissPagedLyrics = {
                    scope.launch { playerPagerState.animateScrollToPage(PLAYER_PAGE_COVER) }
                },
                coverPage = { onShowLyrics, pageModifier ->
                    val videoPlaybackActive = if (immersiveAlbumCover) {
                        !showLyrics
                    } else {
                        playerPagerState.currentPage == PLAYER_PAGE_COVER
                    }
                    CoverPageContent(
                        context = context,
                        mainViewModel = mainViewModel,
                        playerViewModel = playerViewModel,
                        settingsManager = settingsManager,
                        scope = scope,
                        song = song,
                        embeddedCover = embeddedCover,
                        paletteBitmap = paletteBitmap,
                        songAnnotation = displayAnnotation,
                        dynamicCoverFailedPath = uiState.dynamicCoverFailedPath,
                        dynamicCoverEnabled = dynamicCoverEnabled,
                        dynamicCoverCustomFolders = dynamicCoverCustomFolders,
                        musicVideoCustomFolders = musicVideoCustomFolders,
                        musicVideoSyncEnabled = musicVideoSyncEnabled,
                        // The landscape host owns its MV decoder while expanded. Keeping the
                        // portrait surface composed underneath created a second video pipeline.
                        musicVideoVisible = uiState.musicVideoVisible && !landscapeState.expanded,
                        videoPlaybackActive = videoPlaybackActive,
                        onMusicVideoVisibleChange = { visible ->
                            if (!musicVideoSyncEnabled) {
                                uiState.musicVideoVisible = false
                            } else if (visible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED) {
                                musicVideoPermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
                            } else {
                                uiState.musicVideoVisible = visible
                            }
                        },
                        onOpenMusicVideoLandscape = {
                            if (musicVideoSyncEnabled) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    musicVideoLandscapePermissionLauncher.launch(Manifest.permission.READ_MEDIA_VIDEO)
                                } else {
                                    uiState.musicVideoVisible = true
                                    landscapeState.expanded = true
                                }
                            }
                        },
                        immersiveAlbumCover = immersiveAlbumCover,
                        coverContentColor = coverContentColor,
                        playerBackgroundEnabled = playerBackgroundEnabled,
                        playerBackgroundUri = playerBackgroundUri,
                        playerBackgroundOpacity = playerBackgroundOpacity,
                        playerBackgroundDim = playerBackgroundDim,
                        beautifulLyricsBackground = beautifulLyricsBackground,
                        playerDynamicFlowEnabled = playerDynamicFlowEnabled,
                        hiResLogoEnabled = hiResLogoEnabled,
                        hiResLogoUri = hiResLogoUri,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        audioInfo = audioInfo,
                        palette = palette,
                        lyricPalette = palette,
                        lyrics = lyrics,
                        lyricsLoading = lyricsLoading,
                        currentLyricIndex = currentLyricIndex,
                        miniLyricLine = miniLyricLine,
                        showLyricTranslation = showLyricTranslation,
                        showLyricPronunciation = showLyricPronunciation,
                        lyricPageKeepScreenOn = lyricPageKeepScreenOn,
                        appleMusicLyricsWordLift = appleMusicLyricsWordLift,
                        lyricFormatAvailability = lyricFormatAvailability,
                        preferTtmlLyrics = preferTtmlLyrics,
                        lyricSourceMode = lyricSourceMode,
                        lyricParserEngine = lyricParserEngine,
                        lyricLayoutProfile = lyricLayoutProfile,
                        lyricFontFamily = lyricFontFamily,
                        lyricTranslationFontFamily = lyricTranslationFontFamily,
                        effectiveLyricFontPath = effectiveLyricFontPath,
                        lyricFontWeight = lyricFontWeight,
                        lyricFontScale = lyricFontScale,
                        lyricSecondaryFontScale = lyricSecondaryFontScale,
                        lyricPrimaryTextSizeSp = lyricPrimaryTextSizeSp,
                        lyricSecondaryTextSizeSp = lyricSecondaryTextSizeSp,
                        lyricPerspectiveEffect = lyricPerspectiveEffect,
                        lyricPerspectiveYAngle = lyricPerspectiveYAngle,
                        lyricTextAlign = playerLyricTextAlign,
                        playerTapSeekEnabled = playerTapSeekEnabled,
                        playerShowTotalDuration = playerShowTotalDuration,
                        coverSwipeEnabled = coverSwipeEnabled,
                        playerTitlePosition = playerTitlePosition,
                        playerPageStyle = playerPageStyle,
                        showPlayerKeepScreenOnAction = true,
                        playerKeepScreenOn = playerKeepScreenOn,
                        menuExpanded = uiState.menuExpanded,
                        onMenuExpandedChange = { uiState.menuExpanded = it },
                        queueExpanded = uiState.queueExpanded,
                        onQueueExpandedChange = { uiState.queueExpanded = it },
                        playlist = playlist,
                        favoriteSongKeys = favoriteSongKeys,
                        loadSongRating = mainViewModel::getSongRating,
                        ratingRevision = ratingRevision,
                        sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
                        stopAfterCurrentEnabled = stopAfterCurrentEnabled,
                        sleepTimerCustomMinutes = sleepTimerCustomMinutes,
                        sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
                        playbackSpeed = playbackSpeed,
                        playbackPitch = playbackPitch,
                        isCurrentSongFavorite = isCurrentSongFavorite,
                        audioSessionId = audioSessionId,
                        audioVisualizerEnabled = audioVisualizerEnabled,
                        audioVisualizerOpacity = audioVisualizerOpacity,
                        audioVisualizerOpacityPercent = playerSettings.audioVisualizerOpacity,
                        lyricOffsetMs = currentLyricOffsetMs,
                        metadataEditorId = metadataEditorId,
                        lyricTimingEditorId = lyricTimingEditorId,
                        onVisualizerEnabled = setAudioVisualizerEnabled,
                        onVisualizerOpacityChange = {
                            scope.launch { settingsManager.setAudioVisualizerOpacity(it) }
                        },
                        onPlayerKeepScreenOnChange = {
                            scope.launch { settingsManager.setPlayerKeepScreenOn(it) }
                        },
                        onDynamicCoverFailedPathChange = {
                            uiState.dynamicCoverFailedPath = it
                            if (uiState.musicVideoVisible) {
                                uiState.musicVideoVisible = false
                            }
                        },
                        onDynamicCoverSheetSongChange = { uiState.dynamicCoverSheetSong = it },
                        onPlaylistPickerSongChange = { uiState.playlistPickerSong = it },
                        onPlaylistPickerSongsChange = { uiState.playlistPickerSongs = it },
                        onLandscapeExpandedChange = {
                            if (it) {
                                val useMusicVideo =
                                    playerLandscapeStyle ==
                                        SettingsManager.PLAYER_LANDSCAPE_STYLE_MUSIC_VIDEO
                                uiState.musicVideoVisible = useMusicVideo && musicVideoSyncEnabled
                            }
                            landscapeState.expanded = it
                        },
                        onSongInfoExpandedChange = { uiState.songInfoExpanded = it },
                        onRatingSheetSongChange = { uiState.ratingSheetSong = it },
                        onAiSheetSongChange = { uiState.aiSheetSong = it },
                        onTagEditorSongChange = { uiState.tagEditorSong = it },
                        onTagEditorKindChange = { uiState.tagEditorKind = it },
                        onLyricMatchSongChange = { uiState.lyricMatchSong = it },
                        onOpenEqualizer = onNavigateToEqualizer,
                        onRequestDeleteSong = ::requestDeleteSong,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToArtist = onNavigateToArtist,
                        openLyricSharePicker = ::openLyricSharePicker,
                        navigateToArtistOrChoose = ::navigateToArtistOrChoose,
                        onShowLyrics = onShowLyrics,
                        onSwipePrevious = { playerViewModel.skipToPreviousTrack() },
                        drawBackground = false,
                        modifier = pageModifier
                    )
                },
                lyricsPage = { onDismissLyrics, enableSwipeDismiss, backEnabled, pageVisible, pageModifier ->
                    LyricsPageContent(
                        song = song,
                        embeddedCover = embeddedCover,
                        paletteBitmap = paletteBitmap,
                        songAnnotation = displayAnnotation,
                        lyrics = lyrics,
                        lyricsLoading = lyricsLoading,
                        currentLyricIndex = currentLyricIndex,
                        currentPosition = currentPosition,
                        showLyricTranslation = showLyricTranslation,
                        showLyricPronunciation = showLyricPronunciation,
                        lyricPageKeepScreenOn = lyricPageKeepScreenOn,
                        appleMusicLyricsWordLift = appleMusicLyricsWordLift,
                        lyricFormatAvailability = lyricFormatAvailability,
                        preferTtmlLyrics = preferTtmlLyrics,
                        lyricSourceMode = lyricSourceMode,
                        lyricParserEngine = lyricParserEngine,
                        lyricLayoutProfile = lyricLayoutProfile,
                        lyricFontFamily = lyricFontFamily,
                        lyricTranslationFontFamily = lyricTranslationFontFamily,
                        effectiveLyricFontPath = effectiveLyricFontPath,
                        lyricFontWeight = lyricFontWeight,
                        lyricFontScale = lyricFontScale,
                        lyricSecondaryFontScale = lyricSecondaryFontScale,
                        lyricPrimaryTextSizeSp = lyricPrimaryTextSizeSp,
                        lyricSecondaryTextSizeSp = lyricSecondaryTextSizeSp,
                        lyricPerspectiveEffect = lyricPerspectiveEffect,
                        lyricPerspectiveYAngle = lyricPerspectiveYAngle,
                        lyricTextAlign = playerLyricTextAlign,
                        lyricPalette = palette,
                        isPlaying = isPlaying,
                        playerBackgroundEnabled = playerBackgroundEnabled,
                        playerBackgroundUri = playerBackgroundUri,
                        playerBackgroundOpacity = playerBackgroundOpacity,
                        playerBackgroundDim = playerBackgroundDim,
                        beautifulLyricsBackground = beautifulLyricsBackground,
                        playerDynamicFlowEnabled = playerDynamicFlowEnabled,
                        isCurrentSongFavorite = isCurrentSongFavorite,
                        audioSessionId = audioSessionId,
                        effectiveAudioVisualizerEnabled = effectiveAudioVisualizerEnabled,
                        audioVisualizerOpacity = audioVisualizerOpacity,
                        playerViewModel = playerViewModel,
                        settingsManager = settingsManager,
                        scope = scope,
                        openLyricSharePicker = ::openLyricSharePicker,
                        navigateToArtistOrChoose = ::navigateToArtistOrChoose,
                        onDismissLyrics = onDismissLyrics,
                        enableSwipeDismiss = enableSwipeDismiss,
                        backEnabled = backEnabled,
                        pageVisible = pageVisible,
                        immersiveAlbumCover = immersiveAlbumCover,
                        drawBackground = false,
                        modifier = pageModifier
                    )
                },
                detailPage = { pageModifier ->
                    DetailPageContent(
                        context = context,
                        song = song,
                        embeddedCover = embeddedCover,
                        paletteBitmap = paletteBitmap,
                        tagInfo = tagInfo,
                        neteaseInfo = neteaseInfo,
                        librarySongs = librarySongs,
                        albumArtForAlbum = mainViewModel::getAlbumArtUri,
                        artistCoverFolderUri = artistCoverFolderUri,
                        mainViewModel = mainViewModel,
                        lyricPalette = palette,
                        currentPosition = currentPosition,
                        isPlaying = isPlaying,
                        beautifulLyricsBackground = beautifulLyricsBackground,
                        playerDynamicFlowEnabled = playerDynamicFlowEnabled,
                        playerBackgroundUri = playerBackgroundUri,
                        playerBackgroundOpacity = playerBackgroundOpacity,
                        playerBackgroundDim = playerBackgroundDim,
                        immersiveAlbumCover = immersiveAlbumCover,
                        playerBackgroundEnabled = playerBackgroundEnabled,
                        onNavigateToAlbum = onNavigateToAlbum,
                        onNavigateToArtist = onNavigateToArtist,
                        onNavigateToMetadataCategory = onNavigateToMetadataCategory,
                        openNetease = ::openNetease,
                        // Detail-page MVs are an independent, audible player. The sync switch
                        // only controls the silent MV surface on the main playback page.
                        musicVideoEnabled = dynamicCoverEnabled,
                        musicVideoCustomFolders = musicVideoCustomFolders,
                        dynamicCoverCustomFolders = dynamicCoverCustomFolders,
                        onOpenMusicVideo = { source ->
                            uiState.musicVideoVisible = false
                            playerViewModel.pauseForMusicVideo()
                            com.ella.music.MusicVideoLauncher.open(context, song, source)
                        },
                        drawBackground = false,
                        modifier = pageModifier
                    )
                },
                playerVisible = playerVisible,
                modifier = Modifier.fillMaxSize()
            )

            PlayerLandscapeOverlayHost(
                context = context,
                expanded = landscapeState.expanded,
                // The explicit MV landscape action is an intent to open the MV-backed player,
                // regardless of the default landscape style selected in Settings.
                layoutStyle = if (landscapeState.expanded && uiState.musicVideoVisible) {
                    SettingsManager.PLAYER_LANDSCAPE_STYLE_MUSIC_VIDEO
                } else {
                    playerLandscapeStyle
                },
                dynamicCoverEnabled = dynamicCoverEnabled,
                dynamicCoverCustomFolders = dynamicCoverCustomFolders,
                musicVideoCustomFolders = musicVideoCustomFolders,
                musicVideoEnabled = musicVideoSyncEnabled,
                song = song,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                annotation = displayAnnotation,
                dynamicCoverFailedPath = uiState.dynamicCoverFailedPath,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                audioInfo = audioInfo,
                palette = palette,
                lyrics = lyrics,
                currentLyricIndex = currentLyricIndex,
                showTranslation = showLyricTranslation,
                showPronunciation = showLyricPronunciation,
                fontFamily = lyricFontFamily,
                translationFontFamily = lyricTranslationFontFamily,
                fontPath = effectiveLyricFontPath,
                fontWeight = lyricFontWeight,
                fontScale = lyricFontScale,
                secondaryFontScale = lyricSecondaryFontScale,
                primaryTextSizeSp = lyricPrimaryTextSizeSp,
                secondaryTextSizeSp = lyricSecondaryTextSizeSp,
                showTotalDuration = playerShowTotalDuration,
                queueExpanded = uiState.queueExpanded,
                playlist = playlist,
                audioSessionId = audioSessionId,
                visualizerEnabled = effectiveAudioVisualizerEnabled,
                visualizerOpacity = audioVisualizerOpacity,
                // Landscape player always allows swiping covers to switch songs,
                // regardless of the "swipe cover to switch song" setting (which only
                // applies to the portrait player cover page).
                coverSwipeEnabled = true,
                beautifulLyricsBackground = beautifulLyricsBackground,
                flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
                isFavorite = isCurrentSongFavorite,
                onDynamicCoverFailed = { uiState.dynamicCoverFailedPath = it },
                onToggleFavorite = { playerViewModel.toggleCurrentSongFavorite() },
                onToggleQueue = { uiState.queueExpanded = !uiState.queueExpanded },
                onDismissQueue = { uiState.queueExpanded = false },
                onLyricLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
                onLyricLineLongClick = ::openLyricSharePicker,
                onSeekProgress = { progress ->
                    playerViewModel.seekToProgress(progress, duration)
                },
                onCyclePlaybackMode = { playerViewModel.cyclePlaybackMode() },
                onPrevious = { playerViewModel.skipToPrevious() },
                onSwipePrevious = { playerViewModel.skipToPreviousTrack() },
                onPlayPause = { playerViewModel.togglePlayPause() },
                onNext = { playerViewModel.skipToNext() },
                onQueueSongClick = { index ->
                    uiState.queueExpanded = false
                    playerViewModel.playQueueIndex(index)
                },
                onRemoveQueueSong = { index -> playerViewModel.removeFromPlaylist(index) },
                onMoveQueueSong = { fromIndex, toIndex ->
                    playerViewModel.movePlaylistItem(fromIndex, toIndex)
                },
                onAddQueueToPlaylist = {
                    uiState.queueExpanded = false
                    uiState.playlistPickerSongs = playlist
                },
                onClearQueue = {
                    uiState.queueExpanded = false
                    playerViewModel.clearPlaylist()
                },
                onArtist = {
                    navigateToArtistOrChoose(song?.artist.orEmpty())
                },
                onDismiss = {
                    landscapeState.expanded = false
                }
            )
          }

            PlayerScreenSheetHost(
                context = context,
                scope = scope,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                song = song,
                playlists = playlists,
                artistChoices = uiState.artistChoices,
                onArtistChoicesChange = { uiState.artistChoices = it },
                onNavigateToArtist = onNavigateToArtist,
                songInfoExpanded = uiState.songInfoExpanded,
                onSongInfoExpandedChange = { uiState.songInfoExpanded = it },
                dynamicCoverSheetSong = uiState.dynamicCoverSheetSong,
                onDynamicCoverSheetSongChange = { uiState.dynamicCoverSheetSong = it },
                ratingSheetSong = uiState.ratingSheetSong,
                onRatingSheetSongChange = { uiState.ratingSheetSong = it },
                aiSheetSong = uiState.aiSheetSong,
                onAiSheetSongChange = { uiState.aiSheetSong = it },
                deleteConfirmSong = uiState.deleteConfirmSong,
                onDeleteConfirmSongChange = { uiState.deleteConfirmSong = it },
                lyricMatchSong = uiState.lyricMatchSong,
                onLyricMatchSongChange = { uiState.lyricMatchSong = it },
                tagEditorSong = uiState.tagEditorSong,
                onTagEditorSongChange = { uiState.tagEditorSong = it },
                tagEditorKind = uiState.tagEditorKind,
                onTagEditorKindChange = { uiState.tagEditorKind = it },
                metadataEditorId = metadataEditorId,
                lyricTimingEditorId = lyricTimingEditorId,
                metadataEditorSong = uiState.metadataEditorSong,
                onMetadataEditorSongChange = { uiState.metadataEditorSong = it },
                lyricTimingEditorSong = uiState.lyricTimingEditorSong,
                onLyricTimingEditorSongChange = { uiState.lyricTimingEditorSong = it },
                onWritePermissionRequired = { error, retry ->
                    uiState.pendingWriteRetry = retry
                    deletePermissionLauncher.launch(
                        IntentSenderRequest.Builder(error.intentSender).build()
                    )
                },
                playlistPickerSong = uiState.playlistPickerSong,
                onPlaylistPickerSongChange = { uiState.playlistPickerSong = it },
                playlistPickerSongs = uiState.playlistPickerSongs,
                onPlaylistPickerSongsChange = { uiState.playlistPickerSongs = it },
                createPlaylistSong = uiState.createPlaylistSong,
                onCreatePlaylistSongChange = { uiState.createPlaylistSong = it },
                createPlaylistSongs = uiState.createPlaylistSongs,
                onCreatePlaylistSongsChange = { uiState.createPlaylistSongs = it }
            )
        }
    }
}

private fun isLyricVideoShareUnsupported(
    song: Song?,
    audioInfo: AudioInfo?
): Boolean {
    if (isLikelyLosslessM4aLyricVideoSource(song, audioInfo)) return true

    // Master / Hi-Res 24-bit 192kHz+ audio — transcode pipeline produces pitch-shifted output
    // due to PCM buffer size miscalculation at very high sample rates.
    // Disable video sharing for these until the encoder is fixed.
    val sampleRate = audioInfo?.sampleRate ?: 0
    val bitDepth = audioInfo?.bitDepth ?: 0
    if (sampleRate >= 192_000 && bitDepth >= 24) return true

    return false
}

private fun isLikelyLosslessM4aLyricVideoSource(
    song: Song?,
    audioInfo: AudioInfo?
): Boolean {
    if (normalizedAudioFormat(audioInfo?.format.orEmpty()) == "ALAC") return true

    val mimeType = song?.mimeType.orEmpty().lowercase()
    if ("alac" in mimeType) return true

    val path = song?.path.orEmpty().lowercase()
    if (path.endsWith(".alac")) return true

    val isM4aContainer = path.endsWith(".m4a") ||
        path.endsWith(".mp4") ||
        "audio/mp4" in mimeType ||
        "audio/x-m4a" in mimeType ||
        "mp4a" in mimeType
    if (!isM4aContainer) return false

    val sampleRate = audioInfo?.sampleRate ?: 0
    val bitRate = audioInfo?.bitRate ?: 0
    val bitDepth = audioInfo?.let(::normalizedBitDepth) ?: 0
    return sampleRate >= 44_100 && (
        bitDepth >= 24 ||
            (bitDepth >= 16 && bitRate >= 450_000)
        )
}
