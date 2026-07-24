package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.CoverPreviewDialog
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.repository.MusicRepository
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.RemoteMusicSourceConfig
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun CoverPlayerPage(
    context: Context,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    annotation: String,
    dynamicCoverFailedPath: String?,
    dynamicCoverEnabled: Boolean,
    dynamicCoverCustomFolders: List<String>,
    musicVideoSyncEnabled: Boolean,
    musicVideoVisible: Boolean,
    immersiveAlbumCover: Boolean,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    hiResLogoEnabled: Boolean,
    hiResLogoUri: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    audioInfo: AudioInfo?,
    palette: PlayerPalette,
    flowEffectMode: Int,
    dynamicFlowEnabled: Boolean,
    lyrics: List<LyricLine>,
    lyricsLoading: Boolean,
    currentLyricIndex: Int,
    miniLyricLine: LyricLine?,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    lyricPageKeepScreenOn: Boolean,
    appleMusicWordLiftEnabled: Boolean,
    lyricFormatAvailability: MusicRepository.LyricFormatAvailability,
    preferTtmlLyrics: Boolean?,
    lyricSourceMode: Int,
    lyricParserEngine: Int,
    lyricLayoutProfile: PlayerLyricLayoutProfile,
    fontFamily: FontFamily?,
    translationFontFamily: FontFamily? = fontFamily,
    fontPath: String,
    fontWeight: FontWeight,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    lyricPerspectiveEffect: Boolean,
    lyricPerspectiveYAngle: Int,
    lyricTextAlign: Int,
    playerTapSeekEnabled: Boolean,
    playerShowTotalDuration: Boolean,
    coverSwipeEnabled: Boolean,
    playerTitlePosition: Int,
    showPlayerKeepScreenOnAction: Boolean,
    playerKeepScreenOn: Boolean,
    menuExpanded: Boolean,
    queueExpanded: Boolean,
    playlist: List<Song>,
    favoriteSongKeys: Set<String> = emptySet(),
    loadSongRating: (Song) -> Int = { 0 },
    ratingRevision: Int = 0,
    sleepTimerEndRealtimeMs: Long?,
    stopAfterCurrentEnabled: Boolean,
    sleepTimerCustomMinutes: Int,
    sleepTimerStopAfterCurrent: Boolean,
    playbackSpeed: Float,
    playbackPitch: Float,
    isFavorite: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float,
    visualizerOpacityPercent: Int,
    lyricOffsetMs: Long,
    metadataEditorId: String,
    lyricTimingEditorId: String,
    onVisualizerEnabled: (Boolean) -> Unit,
    onVisualizerOpacityChange: (Int) -> Unit,
    onPlayerKeepScreenOnChange: (Boolean) -> Unit,
    onDynamicCoverFailed: (String) -> Unit,
    onToggleMusicVideo: () -> Unit,
    onOpenMusicVideoLandscape: () -> Unit,
    onMatchDynamicCover: () -> Unit,
    onToggleMenu: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDismissMenu: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onShowLyrics: () -> Unit,
    onLyricLineClick: (LyricLine) -> Unit,
    onLyricLineLongClick: (LyricLine) -> Unit,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleLyricKeepScreenOn: () -> Unit,
    onToggleLyricPerspectiveEffect: () -> Unit,
    onLyricPerspectiveYAngle: (Int) -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    onLyricFormatPreference: (Boolean) -> Unit,
    onLyricParserEngine: (Int) -> Unit,
    onLyricFontScale: (Float) -> Unit,
    onLyricSecondaryFontScale: (Float) -> Unit,
    onLyricPrimaryTextSize: (Float) -> Unit,
    onLyricSecondaryTextSize: (Float) -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onSwipePrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onRemoveQueueSong: (Int) -> Unit,
    onMoveQueueSong: (Int, Int) -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onNavigateToAlbumId: (Long) -> Unit,
    onNavigateToArtistName: (String) -> Unit,
    onDownload: () -> Unit,
    onLandscape: () -> Unit,
    onSongInfo: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onShareSong: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onSetRating: () -> Unit,
    onAiInterpret: () -> Unit,
    onSpectrum: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onDeleteSong: () -> Unit,
    onEditMetadata: () -> Unit,
    onLyricTiming: () -> Unit,
    onMatchOnlineLyrics: () -> Unit,
    onOpenTimer: () -> Unit,
    onOpenMetadataEditor: () -> Unit,
    onStopAfterCurrent: (Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCustomTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onLyricOffset: (Long) -> Unit,
    actionMenuInitialPage: PlayerActionSheetPage,
    drawBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    val staticCoverPreviewModel by produceState<Any?>(
        initialValue = resolveCoverPreviewModel(song, embeddedCover),
        song?.let { listOf(it.playlistIdentityKey(), it.dateModified, it.fileSize).joinToString("|") }
    ) {
        value = withContext(Dispatchers.IO) {
            song?.let(playerViewModel::getOriginalCoverModel)
                ?: resolveCoverPreviewModel(song, embeddedCover)
        }
    }
    val resolvedStaticCoverPreviewModel = staticCoverPreviewModel
    // Keep an opened preview as a snapshot.  Changing tracks must update the player behind the
    // dialog, not dismiss or replace the artwork the user is currently inspecting.
    var previewCover by remember { mutableStateOf<PlayerCoverPreview?>(null) }
    val bluetoothDeviceName = rememberBluetoothOutputName()
    val queueLocked by playerViewModel.queueLocked.collectAsState()
    val navidromeConfig by playerViewModel.settingsManager.navidromeConfig.collectAsState(
        initial = RemoteMusicSourceConfig(RemoteMusicProvider.Navidrome, "")
    )
    val openSubsonicConfig by playerViewModel.settingsManager.openSubsonicConfig.collectAsState(
        initial = RemoteMusicSourceConfig(RemoteMusicProvider.OpenSubsonic, "")
    )
    val remoteStreamMaxBitRate = when (song?.onlineSource) {
        RemoteMusicProvider.Navidrome.id -> navidromeConfig.streamMaxBitRate
        RemoteMusicProvider.OpenSubsonic.id -> openSubsonicConfig.streamMaxBitRate
        else -> null
    }
    val dynamicCoverSongKey = song?.dynamicCoverResolutionKey().orEmpty()
    // Resolving a dynamic cover scans many candidate files and probes media tracks; doing that in
    // composition janked every song change (even when no cover exists). Resolve it off the main
    // thread, only while the player page is shown. Clear the previous source first so a song
    // switch never keeps rendering the old video's PlayerView while the next source is resolving.
    val resolvedDynamicCover by produceState<DynamicCoverSource?>(
        initialValue = null,
        dynamicCoverEnabled,
        dynamicCoverCustomFolders,
        dynamicCoverSongKey,
        dynamicCoverFailedPath
    ) {
        val current = song
        if (current == null) {
            value = null
        } else {
            value = null
            value = withContext(Dispatchers.IO) {
                current.dynamicCoverSource(
                    context,
                    includeExternalFiles = dynamicCoverEnabled,
                    customRootPaths = dynamicCoverCustomFolders
                )?.takeUnless { it.failureKey == dynamicCoverFailedPath }
            }
        }
    }
    // Resolve MV separately.  Its lookup can be relatively expensive, and must never delay the
    // regular dynamic-cover lookup or prevent it from reaching the screen.
    val resolvedMusicVideo by produceState<DynamicCoverSource?>(
        initialValue = null,
        musicVideoSyncEnabled,
        dynamicCoverCustomFolders,
        dynamicCoverSongKey,
        dynamicCoverFailedPath
    ) {
        val current = song
        value = if (current == null || !musicVideoSyncEnabled) {
            null
        } else {
            withContext(Dispatchers.IO) {
                current.musicVideoSource(
                    context,
                    customRootPaths = dynamicCoverCustomFolders
                )?.takeUnless { it.failureKey == dynamicCoverFailedPath }
            }
        }
    }
    val displayedDynamicCover = resolvedDynamicCover
    val portraitDynamicCover = (if (musicVideoVisible) resolvedMusicVideo else displayedDynamicCover)
        ?.aspectRatio?.let { it < 0.92f } == true
    val coverSwipeModifier = if (coverSwipeEnabled) {
        rememberCoverSwipeModifier(
            onSwipePrevious = onSwipePrevious,
            onSwipeNext = onNext
        )
    } else {
        Modifier
    }

    BoxWithConstraints(modifier = modifier) {
        val useWidePlayer = maxWidth > maxHeight && maxWidth >= 700.dp
        val isSmallWindow = maxWidth < 300.dp || (maxWidth < 420.dp && maxHeight < 560.dp)
        // Tall-but-narrow or short floating windows: the lyric preview overflows and the bottom
        // transport controls get clipped. Compact the lyrics (smaller, single line) and drop the
        // visualizer to reclaim vertical space, keeping the 1:1 cover untouched.
        val compactWindow = !useWidePlayer && (maxHeight < 720.dp || maxWidth < 340.dp)
        val effectiveMiniLyricLine = miniLyricLine.takeUnless { isSmallWindow }
        val showHiResLogo = hiResLogoEnabled && audioInfo?.isHiResLogoTrack() == true
        val titleAboveCover = !immersiveAlbumCover &&
            playerTitlePosition == com.ella.music.data.SettingsManager.PLAYER_TITLE_POSITION_ABOVE_COVER
        val pagePalette = palette
        val showCustomPlayerBackground =
            playerBackgroundEnabled && playerBackgroundUri.isNotBlank() && (useWidePlayer || !immersiveAlbumCover)
        if (drawBackground && !useWidePlayer && !immersiveAlbumCover) {
            SharedPlayerPageBackground(
                song = song,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                palette = pagePalette,
                currentPositionMs = currentPosition,
                isPlaying = isPlaying,
                playerBackgroundEnabled = playerBackgroundEnabled,
                playerBackgroundUri = playerBackgroundUri,
                playerBackgroundOpacity = playerBackgroundOpacity,
                playerBackgroundDim = playerBackgroundDim,
                beautifulLyricsBackground = beautifulLyricsBackground,
                dynamicFlowEnabled = dynamicFlowEnabled,
                useBlurBackground = false,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (useWidePlayer) {
            LandscapeCoverPlayerPage(
                song = song,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                annotation = annotation,
                dynamicCoverSource = displayedDynamicCover,
                isPlaying = isPlaying,
                currentPosition = currentPosition,
                duration = duration,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                audioInfo = audioInfo,
                hiResLogoEnabled = hiResLogoEnabled,
                hiResLogoUri = hiResLogoUri,
                palette = pagePalette,
                flowEffectMode = flowEffectMode,
                dynamicFlowEnabled = dynamicFlowEnabled,
                customBackgroundUri = playerBackgroundUri.takeIf { showCustomPlayerBackground }.orEmpty(),
                customBackgroundOpacity = playerBackgroundOpacity,
                customBackgroundDim = playerBackgroundDim,
                beautifulLyricsBackground = beautifulLyricsBackground,
                lyrics = lyrics,
                lyricsLoading = lyricsLoading,
                currentLyricIndex = currentLyricIndex,
                showTranslation = showTranslation,
                showPronunciation = showPronunciation,
                appleMusicWordLiftEnabled = appleMusicWordLiftEnabled,
                fontFamily = fontFamily,
                translationFontFamily = translationFontFamily,
                fontPath = fontPath,
                fontWeight = fontWeight,
                fontScale = fontScale,
                secondaryFontScale = secondaryFontScale,
                primaryTextSizeSp = primaryTextSizeSp,
                secondaryTextSizeSp = secondaryTextSizeSp,
                lyricPerspectiveEffect = lyricPerspectiveEffect,
                lyricPerspectiveYAngle = lyricPerspectiveYAngle,
                lyricTextAlign = lyricTextAlign,
                showTotalDuration = playerShowTotalDuration,
                playerTapSeekEnabled = playerTapSeekEnabled,
                coverSwipeEnabled = coverSwipeEnabled,
                queueExpanded = queueExpanded,
                playlist = playlist,
                queueLocked = queueLocked,
                favoriteSongKeys = favoriteSongKeys,
                loadSongRating = loadSongRating,
                ratingRevision = ratingRevision,
                audioSessionId = audioSessionId,
                visualizerEnabled = visualizerEnabled,
                visualizerOpacity = visualizerOpacity,
                onDynamicCoverFailed = onDynamicCoverFailed,
                isFavorite = isFavorite,
                onToggleMenu = onToggleMenu,
                onToggleFavorite = onToggleFavorite,
                onToggleQueue = onToggleQueue,
                onDismissQueue = onDismissQueue,
                onToggleQueueLock = playerViewModel::toggleQueueLock,
                onShowLyrics = onShowLyrics,
                onLyricLineClick = onLyricLineClick,
                onLyricLineLongClick = onLyricLineLongClick,
                onSeek = onSeek,
                onCyclePlaybackMode = onCyclePlaybackMode,
                onPrevious = onPrevious,
                onSwipePrevious = onSwipePrevious,
                onPlayPause = onPlayPause,
                onNext = onNext,
                onQueueSongClick = onQueueSongClick,
                onRemoveQueueSong = onRemoveQueueSong,
                onMoveQueueSong = onMoveQueueSong,
                onAddQueueToPlaylist = onAddQueueToPlaylist,
                onClearQueue = onClearQueue,
                onLineClick = onShowLyrics,
                onArtist = onArtist,
                drawBackground = drawBackground,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (immersiveAlbumCover) {
                    val immersiveCoverCornerRadius = 0.dp
                    val immersiveCoverShape = RoundedCornerShape(immersiveCoverCornerRadius)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (portraitDynamicCover) {
                                    Modifier.weight(1f)
                                } else {
                                    Modifier.aspectRatio(1f)
                                }
                            )
                            .graphicsLayer {
                                shape = immersiveCoverShape
                                clip = true
                            }
                            .clip(immersiveCoverShape)
                            .then(
                                if (resolvedStaticCoverPreviewModel != null) {
                                    Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            previewCover = PlayerCoverPreview(
                                                model = resolvedStaticCoverPreviewModel,
                                                title = song?.title.orEmpty()
                                            )
                                        }
                                    )
                                } else {
                                    Modifier
                                }
                            )
                            .then(coverSwipeModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        androidx.compose.runtime.LaunchedEffect(
                            resolvedMusicVideo?.failureKey,
                            currentPosition,
                            duration,
                            isPlaying
                        ) {
                            resolvedMusicVideo?.let { source ->
                                MusicVideoPlaybackBridge.syncToAudio(source, currentPosition, duration, isPlaying)
                            }
                        }
                        // Keep MV silent and on the audio clock while its surface is hidden.
                        resolvedMusicVideo?.let { source ->
                            DynamicCoverVideo(
                                source = source,
                                isPlaying = isPlaying,
                                syncPositionMs = currentPosition,
                                syncDurationMs = duration,
                                onPlaybackError = { onDynamicCoverFailed(source.failureKey) },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { alpha = if (musicVideoVisible) 1f else 0.001f },
                                cornerRadiusDp = 0f,
                                resizeMode = if (portraitDynamicCover) {
                                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                } else {
                                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            )
                        }
                        if (!musicVideoVisible && displayedDynamicCover != null) {
                            DynamicCoverVideo(
                                source = displayedDynamicCover,
                                isPlaying = isPlaying,
                                onPlaybackError = { onDynamicCoverFailed(displayedDynamicCover.failureKey) },
                                modifier = Modifier.fillMaxSize(),
                                cornerRadiusDp = 0f,
                                resizeMode = if (portraitDynamicCover) {
                                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                } else {
                                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                }
                            )
                        } else if (!musicVideoVisible || resolvedMusicVideo == null) {
                            FullBleedCover(
                                song = song,
                                embeddedCover = embeddedCover,
                                coverModel = resolvedStaticCoverPreviewModel,
                                cornerRadius = immersiveCoverCornerRadius,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.18f),
                                            Color.White.copy(alpha = 0.06f),
                                            Color.White.copy(alpha = 0.16f)
                                        )
                                    )
                                )
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                // Fade the full-bleed artwork into the content surface before the
                                // layout switches from the cover box to the detail column.
                                .height(280.dp)
                                .background(
                                    Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.0f to Color.Transparent,
                                            0.40f to pagePalette.middle.copy(alpha = 0.30f),
                                            0.72f to pagePalette.middle.copy(alpha = 0.82f),
                                            1.0f to pagePalette.middle
                                        )
                                    )
                                )
                        )
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                if (portraitDynamicCover) {
                                    Modifier
                                } else {
                                    Modifier.weight(1f)
                                }
                            )
                            .background(playerContentSurfaceBrush(pagePalette, flowEffectMode))
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerSongMetaText(
                                song = song,
                                annotation = annotation,
                                titleFontSize = 22.sp,
                                artistFontSize = 14.sp,
                                artistAlpha = 0.54f,
                                showArtistWithAnnotation = true,
                                contentColor = pagePalette.onBackground,
                                fontFamily = fontFamily,
                                onArtistClick = onArtist,
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(max = 230.dp)
                            )
                            Spacer(modifier = Modifier.width(20.dp))
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.Favorite,
                                selected = isFavorite,
                                onClick = onToggleFavorite
                            )
                            PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                        }

                        if (effectiveMiniLyricLine != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            MiniLyricsPreview(
                                lyrics = lyrics,
                                currentIndex = currentLyricIndex,
                                showTranslation = showTranslation,
                                showPronunciation = showPronunciation,
                                currentPositionMs = currentPosition,
                                isPlaying = isPlaying,
                                fontFamily = fontFamily,
                                translationFontFamily = translationFontFamily,
                                fontWeight = fontWeight,
                                fontScale = fontScale,
                                secondaryFontScale = secondaryFontScale,
                                lyricTextAlign = lyricTextAlign,
                                compact = compactWindow,
                                contentColor = pagePalette.onBackground,
                                wordLiftEnabled = appleMusicWordLiftEnabled,
                                onLineClick = { onShowLyrics() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(
                                        if (compactWindow) {
                                            miniLyricsCompactHeight()
                                        } else {
                                            miniLyricsPreviewHeight()
                                        }
                                    )
                            )
                        } else if (lyrics.isEmpty() && !lyricsLoading) {
                            Spacer(modifier = Modifier.height(6.dp))
                            MiniNoLyricsPreview(
                                contentColor = pagePalette.onBackground,
                                fontWeight = fontWeight,
                                onClick = onShowLyrics,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (compactWindow) 40.dp else 150.dp)
                            )
                        }

                        if (portraitDynamicCover) {
                            Spacer(modifier = Modifier.height(10.dp))
                        } else {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        PlayerProgressBlock(
                            currentPosition = currentPosition,
                            duration = duration,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            playbackModeLabel = if (musicVideoVisible) "MV" else null,
                            palette = pagePalette,
                            allowTapSeek = playerTapSeekEnabled,
                            showTotalDuration = playerShowTotalDuration,
                            onSeek = onSeek
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PlayerTransportControls(
                            isPlaying = isPlaying,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            palette = pagePalette,
                            queueExpanded = queueExpanded,
                            playlist = playlist,
                            favoriteSongKeys = favoriteSongKeys,
                            loadSongRating = loadSongRating,
                            ratingRevision = ratingRevision,
                            currentSongKey = song?.playlistIdentityKey(),
                            queueLocked = queueLocked,
                            onCyclePlaybackMode = onCyclePlaybackMode,
                            onToggleQueueLock = playerViewModel::toggleQueueLock,
                            onPrevious = onPrevious,
                            onPlayPause = onPlayPause,
                            onNext = onNext,
                            onToggleQueue = onToggleQueue,
                            onDismissQueue = onDismissQueue,
                            onQueueSongClick = onQueueSongClick,
                            onRemoveQueueSong = onRemoveQueueSong,
                            onMoveQueueSong = onMoveQueueSong,
                            onAddQueueToPlaylist = onAddQueueToPlaylist,
                            onClearQueue = onClearQueue,
                            modifier = Modifier.requiredHeight(76.dp)
                        )
                        if (!compactWindow) {
                            AudioVisualizer(
                                enabled = visualizerEnabled,
                                audioSessionId = audioSessionId,
                                isPlaying = isPlaying,
                                positionMs = currentPosition,
                                opacity = visualizerOpacity,
                                accent = Color.White.copy(alpha = 0.86f),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                            )
                        }
                        Spacer(
                            modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(22.dp))
                        if (titleAboveCover) {
                            PlayerCoverTitleRow(
                                song = song,
                                annotation = annotation,
                                palette = pagePalette,
                                fontFamily = fontFamily,
                                isFavorite = isFavorite,
                                onArtist = onArtist,
                                onToggleFavorite = onToggleFavorite,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        val coverShape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(1f)
                                .graphicsLayer {
                                    shape = coverShape
                                    clip = true
                                }
                                .clip(coverShape)
                                .then(
                                    if (resolvedStaticCoverPreviewModel != null) {
                                        Modifier.combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                previewCover = PlayerCoverPreview(
                                                    model = resolvedStaticCoverPreviewModel,
                                                    title = song?.title.orEmpty()
                                                )
                                            }
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .then(coverSwipeModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            androidx.compose.runtime.LaunchedEffect(
                                resolvedMusicVideo?.failureKey,
                                currentPosition,
                                duration,
                                isPlaying
                            ) {
                                resolvedMusicVideo?.let { source ->
                                    MusicVideoPlaybackBridge.syncToAudio(source, currentPosition, duration, isPlaying)
                                }
                            }
                            // Keep MV silent and synchronized behind the current cover.
                            resolvedMusicVideo?.let { source ->
                                DynamicCoverVideo(
                                    source = source,
                                    isPlaying = isPlaying,
                                    syncPositionMs = currentPosition,
                                    syncDurationMs = duration,
                                    onPlaybackError = { onDynamicCoverFailed(source.failureKey) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (musicVideoVisible) 1f else 0.001f },
                                    cornerRadiusDp = 14f
                                )
                            }
                            if (!musicVideoVisible && displayedDynamicCover != null) {
                                DynamicCoverVideo(
                                    source = displayedDynamicCover,
                                    isPlaying = isPlaying,
                                    onPlaybackError = { onDynamicCoverFailed(displayedDynamicCover.failureKey) },
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadiusDp = 14f
                                )
                                if (showHiResLogo) {
                                    HiResLogoBadge(
                                        logoUri = hiResLogoUri,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(10.dp)
                                    )
                                }
                            } else if (!musicVideoVisible || resolvedMusicVideo == null) {
                                AlbumArtView(
                                    song = song,
                                    embeddedCover = embeddedCover,
                                    coverModel = resolvedStaticCoverPreviewModel,
                                    cornerRadius = 14.dp,
                                    showHiResLogo = showHiResLogo,
                                    hiResLogoUri = hiResLogoUri,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else if (showHiResLogo) {
                                HiResLogoBadge(
                                    logoUri = hiResLogoUri,
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(10.dp)
                                )
                            }
                            if (resolvedMusicVideo != null) {
                                if (musicVideoVisible) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(top = 10.dp, end = 60.dp)
                                            .size(42.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(pagePalette.middle.copy(alpha = 0.62f))
                                            .clickable(onClick = onOpenMusicVideoLandscape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.ic_music_video_landscape),
                                            contentDescription = stringResource(R.string.player_music_video_landscape),
                                            tint = pagePalette.onBackground.copy(alpha = 0.94f),
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(10.dp)
                                        .size(42.dp)
                                .clip(RoundedCornerShape(14.dp))
                                        .background(pagePalette.middle.copy(alpha = 0.62f))
                                        .clickable(onClick = onToggleMusicVideo),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (musicVideoVisible) "♫" else "MV",
                                        fontSize = if (musicVideoVisible) 18.sp else 12.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = pagePalette.onBackground.copy(alpha = 0.94f)
                                    )
                                }
                            }
                        }
                        if (!titleAboveCover) {
                            Spacer(modifier = Modifier.height(8.dp))
                            PlayerCoverTitleRow(
                                song = song,
                                annotation = annotation,
                                palette = pagePalette,
                                fontFamily = fontFamily,
                                isFavorite = isFavorite,
                                onArtist = onArtist,
                                onToggleFavorite = onToggleFavorite,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (effectiveMiniLyricLine != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MiniLyricsPreview(
                                lyrics = lyrics,
                                currentIndex = currentLyricIndex,
                                showTranslation = showTranslation,
                                showPronunciation = showPronunciation,
                                currentPositionMs = currentPosition,
                                isPlaying = isPlaying,
                                fontFamily = fontFamily,
                                translationFontFamily = translationFontFamily,
                                fontWeight = fontWeight,
                                fontScale = fontScale,
                                secondaryFontScale = secondaryFontScale,
                                lyricTextAlign = lyricTextAlign,
                                compact = compactWindow,
                                contentColor = pagePalette.onBackground,
                                wordLiftEnabled = appleMusicWordLiftEnabled,
                                onLineClick = { onShowLyrics() },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(
                                        if (compactWindow) {
                                            miniLyricsCompactHeight()
                                        } else {
                                            miniLyricsPreviewHeight(compact = true)
                                        }
                                    )
                            )
                        } else if (lyrics.isEmpty() && !lyricsLoading) {
                            Spacer(modifier = Modifier.height(8.dp))
                            MiniNoLyricsPreview(
                                contentColor = pagePalette.onBackground,
                                fontWeight = fontWeight,
                                onClick = onShowLyrics,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(if (compactWindow) 40.dp else 150.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        Spacer(modifier = Modifier.weight(1f))
                        PlayerQuickActionRow(
                            onSongInfo = onSongInfo,
                            onShareSong = onShareSong,
                            onTimer = onOpenTimer,
                            onEditMetadata = onOpenMetadataEditor,
                            onMore = onToggleMenu,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        PlayerProgressBlock(
                            currentPosition = currentPosition,
                            duration = duration,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            playbackModeLabel = if (musicVideoVisible) "MV" else null,
                            palette = pagePalette,
                            allowTapSeek = playerTapSeekEnabled,
                            showTotalDuration = playerShowTotalDuration,
                            onSeek = onSeek
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PlayerTransportControls(
                            isPlaying = isPlaying,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            palette = pagePalette,
                            queueExpanded = queueExpanded,
                            playlist = playlist,
                            favoriteSongKeys = favoriteSongKeys,
                            loadSongRating = loadSongRating,
                            ratingRevision = ratingRevision,
                            currentSongKey = song?.playlistIdentityKey(),
                            queueLocked = queueLocked,
                            onCyclePlaybackMode = onCyclePlaybackMode,
                            onToggleQueueLock = playerViewModel::toggleQueueLock,
                            onPrevious = onPrevious,
                            onPlayPause = onPlayPause,
                            onNext = onNext,
                            onToggleQueue = onToggleQueue,
                            onDismissQueue = onDismissQueue,
                            onQueueSongClick = onQueueSongClick,
                            onRemoveQueueSong = onRemoveQueueSong,
                            onMoveQueueSong = onMoveQueueSong,
                            onAddQueueToPlaylist = onAddQueueToPlaylist,
                            onClearQueue = onClearQueue,
                            modifier = Modifier.requiredHeight(92.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Spacer(
                            modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars)
                        )
                    }
                }
            }
        }

        PlayerCoverActionSheet(
            show = menuExpanded,
            song = song,
            showLyricsDisplayEntry = useWidePlayer,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            visualizerEnabled = visualizerEnabled,
            visualizerAvailable = immersiveAlbumCover,
            visualizerOpacity = visualizerOpacityPercent,
            lyricOffsetMs = lyricOffsetMs,
            showPronunciation = showPronunciation,
            showTranslation = showTranslation,
            lyricPageKeepScreenOn = lyricPageKeepScreenOn,
            lyricFormatAvailability = lyricFormatAvailability,
            preferTtmlLyrics = preferTtmlLyrics,
            lyricSourceMode = lyricSourceMode,
            lyricParserEngine = lyricParserEngine,
            lyricLayoutProfile = lyricLayoutProfile,
            lyricFontScale = fontScale,
            lyricSecondaryFontScale = secondaryFontScale,
            lyricPrimaryTextSizeSp = primaryTextSizeSp,
            lyricSecondaryTextSizeSp = secondaryTextSizeSp,
            lyricPerspectiveEffect = lyricPerspectiveEffect,
            lyricPerspectiveYAngle = lyricPerspectiveYAngle,
            metadataEditorId = metadataEditorId,
            lyricTimingEditorId = lyricTimingEditorId,
            showPlayerKeepScreenOnAction = showPlayerKeepScreenOnAction,
            playerKeepScreenOn = playerKeepScreenOn,
            sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
            stopAfterCurrentEnabled = stopAfterCurrentEnabled,
            sleepTimerCustomMinutes = sleepTimerCustomMinutes,
            sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
            remoteStreamMaxBitRate = remoteStreamMaxBitRate,
            onDismiss = onDismissMenu,
            onAlbum = onAlbum,
            onArtist = onArtist,
            onDownload = onDownload,
            onLandscape = onLandscape,
            onSongInfo = onSongInfo,
            onAddToPlaylist = onAddToPlaylist,
            onAddToQueue = onAddToQueue,
            onPlayNext = onPlayNext,
            onShareSong = onShareSong,
            onSetRating = onSetRating,
            onAiInterpret = onAiInterpret,
            onSpectrum = onSpectrum,
            onOpenEqualizer = onOpenEqualizer,
            onDeleteSong = onDeleteSong,
            onEditMetadata = onEditMetadata,
            onLyricTiming = onLyricTiming,
            onMatchOnlineLyrics = onMatchOnlineLyrics,
            onMatchDynamicCover = onMatchDynamicCover,
            onStopAfterCurrent = onStopAfterCurrent,
            onTimer = onTimer,
            onCustomTimerMinutes = onCustomTimerMinutes,
            onCancelTimer = onCancelTimer,
            onSpeed = onSpeed,
            onPitch = onPitch,
            onLyricOffset = onLyricOffset,
            onTogglePronunciation = onTogglePronunciation,
            onToggleTranslation = onToggleTranslation,
            onToggleLyricKeepScreenOn = onToggleLyricKeepScreenOn,
            onToggleLyricPerspectiveEffect = onToggleLyricPerspectiveEffect,
            onLyricPerspectiveYAngle = onLyricPerspectiveYAngle,
            onLyricSourceMode = onLyricSourceMode,
            onLyricFormatPreference = onLyricFormatPreference,
            onLyricParserEngine = onLyricParserEngine,
            onLyricFontScale = onLyricFontScale,
            onLyricSecondaryFontScale = onLyricSecondaryFontScale,
            onLyricPrimaryTextSize = onLyricPrimaryTextSize,
            onLyricSecondaryTextSize = onLyricSecondaryTextSize,
            onVisualizerEnabled = onVisualizerEnabled,
            onVisualizerOpacityChange = onVisualizerOpacityChange,
            onPlayerKeepScreenOnChange = onPlayerKeepScreenOnChange,
            onCycleRemoteStreamQuality = playerViewModel::cycleRemoteStreamQuality,
            onPreviewCover = {
                resolvedStaticCoverPreviewModel?.let { model ->
                    previewCover = PlayerCoverPreview(model = model, title = song?.title.orEmpty())
                }
            },
            initialPage = actionMenuInitialPage
        )
        previewCover?.let { cover ->
            CoverPreviewDialog(
                model = cover.model,
                title = cover.title,
                onDismiss = { previewCover = null }
            )
        }
    }
}

private data class PlayerCoverPreview(
    val model: Any,
    val title: String
)

@Composable
private fun PlayerCoverTitleRow(
    song: Song?,
    annotation: String,
    palette: PlayerPalette,
    fontFamily: FontFamily?,
    isFavorite: Boolean,
    onArtist: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerSongMetaText(
            song = song,
            annotation = annotation,
            titleFontSize = 23.sp,
            artistFontSize = 14.sp,
            artistAlpha = 0.62f,
            showArtistWithAnnotation = true,
            contentColor = palette.onBackground,
            fontFamily = fontFamily,
            onArtistClick = onArtist,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(18.dp))
        PlayerHeaderAction(
            kind = PlayerHeaderActionKind.Favorite,
            selected = isFavorite,
            onClick = onToggleFavorite
        )
    }
}

@Composable
private fun rememberCoverSwipeModifier(
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit
): Modifier {
    val thresholdPx = with(LocalDensity.current) { 84.dp.toPx() }
    return Modifier.pointerInput(onSwipePrevious, onSwipeNext, thresholdPx) {
        var dragTravel = 0f
        detectHorizontalDragGestures(
            onDragStart = { dragTravel = 0f },
            onHorizontalDrag = { change, amount ->
                dragTravel += amount
                change.consume()
            },
            onDragEnd = {
                when {
                    dragTravel <= -thresholdPx -> onSwipeNext()
                    dragTravel >= thresholdPx -> onSwipePrevious()
                }
                dragTravel = 0f
            },
            onDragCancel = { dragTravel = 0f }
        )
    }
}
