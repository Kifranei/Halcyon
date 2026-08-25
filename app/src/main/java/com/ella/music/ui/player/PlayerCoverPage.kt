package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
import com.ella.music.ui.components.PlayerQueueListIcon
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.repository.MusicRepository
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.RemoteMusicSourceConfig
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.AbRepeatState
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
    musicVideoCustomFolders: List<String>,
    musicVideoSyncEnabled: Boolean,
    musicVideoVisible: Boolean,
    videoPlaybackActive: Boolean,
    immersiveAlbumCover: Boolean,
    coverContentColor: Boolean,
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
    abRepeatState: AbRepeatState,
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
    playerPageStyle: Int,
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
    onAbRepeat: () -> Unit,
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
    val playWhenReady by playerViewModel.playWhenReady.collectAsState()
    val isActuallyPaused = !isPlaying && !playWhenReady
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
    val coverLongPressPreviewEnabled by playerViewModel.settingsManager.playerCoverLongPressPreviewEnabled
        .collectAsState(initial = true)
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
        musicVideoCustomFolders,
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
                    customRootPaths = dynamicCoverCustomFolders,
                    musicVideoCustomFolders = musicVideoCustomFolders
                )?.takeUnless { it.failureKey == dynamicCoverFailedPath }
            }
        }
    }
    val displayedDynamicCover = resolvedDynamicCover?.takeIf { it.playbackOwnerKey == dynamicCoverSongKey }
    val portraitDynamicCover = (if (musicVideoVisible) resolvedMusicVideo else displayedDynamicCover)
        ?.aspectRatio?.let { it < 0.92f } == true
    val skipCoverSwipeModifier = rememberCoverSwipeModifier(
        swipeEnabled = coverSwipeEnabled,
        onSwipePrevious = onSwipePrevious,
        onSwipeNext = onNext
    )
    val coverSwipeModifier = skipCoverSwipeModifier
    var appleMusicShowLyrics by remember { mutableStateOf(false) }
    var appleMusicChromeVisible by remember { mutableStateOf(true) }
    var appleMusicChromeGeneration by remember { mutableIntStateOf(0) }
    fun revealAppleMusicChrome() {
        appleMusicChromeVisible = true
        appleMusicChromeGeneration++
    }
    LaunchedEffect(appleMusicShowLyrics, appleMusicChromeVisible, appleMusicChromeGeneration) {
        if (!appleMusicShowLyrics || !appleMusicChromeVisible) return@LaunchedEffect
        delay(2_000)
        appleMusicChromeVisible = false
    }

    BoxWithConstraints(modifier = modifier) {
        val rootPlayerWidth = maxWidth
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
        val constrainedPortraitContent = !immersiveAlbumCover && maxHeight < 620.dp
        // Full-width artwork like the 1.2.2 layout. The height cap is only a guard for short
        // or wide windows, so the fixed transport area near the gesture bar is never squeezed;
        // on regular portrait phones the width term wins and the cover fills the page.
        val nonImmersiveCoverSize = minOf(
            (maxWidth - 56.dp).coerceAtLeast(0.dp),
            maxHeight * if (constrainedPortraitContent) 0.42f else 0.46f
        )
        // Credits reserve artwork space, but must not turn the lyric preview into an unusable
        // single strip. Only genuinely compact windows use the compact lyric presentation.
        val compactNonImmersiveLyrics = compactWindow
        // The shared player background may be a bright wallpaper or cover.  Its extracted
        // foreground color is not a reliable contrast signal, so use the root safety color
        // consistently for every page component that receives a palette directly.
        val pagePalette = palette.copy(onBackground = LocalPlayerContentColor.current)
        val selectedPlayerPageStyle =
            com.ella.music.data.SettingsManager.normalizePlayerPageStyle(playerPageStyle)
        val usesAlternatePortraitPage =
            selectedPlayerPageStyle != com.ella.music.data.SettingsManager.DEFAULT_PLAYER_PAGE_STYLE
        val showCustomPlayerBackground =
            playerBackgroundEnabled && playerBackgroundUri.isNotBlank() &&
                (useWidePlayer || !immersiveAlbumCover || usesAlternatePortraitPage)
        if (drawBackground && !useWidePlayer && (!immersiveAlbumCover || usesAlternatePortraitPage)) {
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

        @Composable
        fun StyledPlayerArtwork(
            cornerRadius: androidx.compose.ui.unit.Dp,
            modifier: Modifier = Modifier,
            showOverlayBadges: Boolean = true,
            swipeModifier: Modifier = coverSwipeModifier
        ) {
            val coverShape = RoundedCornerShape(cornerRadius)
            Box(
                modifier = modifier
                    .graphicsLayer {
                        shape = coverShape
                        clip = true
                    }
                    .clip(coverShape)
                    .then(
                        if (coverLongPressPreviewEnabled && resolvedStaticCoverPreviewModel != null) {
                            Modifier.combinedClickable(
                                onClick = {},
                                onLongClick = {
                                    previewCover = PlayerCoverPreview(
                                        model = resolvedStaticCoverPreviewModel,
                                        title = song?.coverPreviewDisplayTitle().orEmpty(),
                                        saveName = song?.coverPreviewSaveName().orEmpty()
                                    )
                                }
                            )
                        } else {
                            Modifier
                        }
                    )
                    .then(swipeModifier),
                contentAlignment = Alignment.Center
            ) {
                val musicVideoSource = resolvedMusicVideo
                val dynamicCoverSource = displayedDynamicCover
                val hideArtworkBehindMusicVideo =
                    selectedPlayerPageStyle ==
                        com.ella.music.data.SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC &&
                        musicVideoVisible && musicVideoSource != null
                if (!hideArtworkBehindMusicVideo) {
                    AlbumArtView(
                        song = song,
                        embeddedCover = embeddedCover,
                        coverModel = resolvedStaticCoverPreviewModel,
                        cornerRadius = cornerRadius,
                        showHiResLogo = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                when {
                    videoPlaybackActive && musicVideoVisible && musicVideoSource != null -> {
                        DynamicCoverVideo(
                            source = musicVideoSource,
                            isPlaying = isPlaying,
                            syncPositionMs = currentPosition,
                            syncDurationMs = duration,
                            onPlaybackError = { onDynamicCoverFailed(musicVideoSource.failureKey) },
                            modifier = Modifier.fillMaxSize(),
                            cornerRadiusDp = cornerRadius.value
                        )
                    }
                    videoPlaybackActive && !musicVideoVisible && dynamicCoverSource != null -> {
                        DynamicCoverVideo(
                            source = dynamicCoverSource,
                            isPlaying = isPlaying,
                            onPlaybackError = { onDynamicCoverFailed(dynamicCoverSource.failureKey) },
                            modifier = Modifier.fillMaxSize(),
                            cornerRadiusDp = cornerRadius.value
                        )
                    }
                }
                if (showOverlayBadges && showHiResLogo) {
                    HiResLogoBadge(
                        logoUri = hiResLogoUri,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(10.dp)
                    )
                }
                if (showOverlayBadges && musicVideoSource != null) {
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
                                painter = painterResource(R.drawable.ic_fullscreen),
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
                            text = stringResource(R.string.player_detail_music_video),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = pagePalette.onBackground.copy(alpha = 0.94f)
                        )
                    }
                }
            }
        }

        @Composable
        fun AppleMusicFooterActions(height: androidx.compose.ui.unit.Dp = 56.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                PlayerTransportIconButton(onClick = {
                    if (selectedPlayerPageStyle == com.ella.music.data.SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC) {
                        appleMusicShowLyrics = !appleMusicShowLyrics
                        if (appleMusicShowLyrics) revealAppleMusicChrome()
                    } else {
                        onShowLyrics()
                    }
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_lyrics),
                        contentDescription = stringResource(R.string.player_lyrics_display),
                        tint = pagePalette.onBackground.copy(alpha = 0.90f),
                        modifier = Modifier.size(34.dp)
                    )
                }
                PlayerTransportIconButton(onClick = {
                    revealAppleMusicChrome()
                    onCyclePlaybackMode()
                }) {
                    PlaybackModeIcon(
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        color = pagePalette.onBackground.copy(alpha = 0.90f),
                        modifier = Modifier.size(34.dp)
                    )
                }
                Box(contentAlignment = Alignment.Center) {
                    PlayerTransportIconButton(onClick = onToggleQueue) {
                        PlayerQueueListIcon(
                            color = pagePalette.onBackground.copy(alpha = 0.90f),
                            modifier = Modifier.size(34.dp)
                        )
                    }
                    PlayerQueueSheet(
                        show = queueExpanded,
                        playlist = playlist,
                        currentSongKey = song?.playlistIdentityKey(),
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        queueLocked = queueLocked,
                        favoriteSongKeys = favoriteSongKeys,
                        loadSongRating = loadSongRating,
                        ratingRevision = ratingRevision,
                        onCyclePlaybackMode = onCyclePlaybackMode,
                        onToggleQueueLock = playerViewModel::toggleQueueLock,
                        onDismiss = onDismissQueue,
                        onSongClick = onQueueSongClick,
                        onRemoveSong = onRemoveQueueSong,
                        onMoveSong = onMoveQueueSong,
                        onRandomizeQueue = playerViewModel::randomizePlaylistOrder,
                        onAddQueueToPlaylist = onAddQueueToPlaylist,
                        onClearQueue = onClearQueue
                    )
                }
            }
        }

        @Composable
        fun AppleMusicCoverPage(coverModifier: Modifier = Modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(if (compactWindow) 8.dp else 16.dp))
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    val coverSize = minOf(maxWidth, maxHeight)
                    StyledPlayerArtwork(
                        cornerRadius = 24.dp,
                        modifier = Modifier
                            .size(coverSize)
                            .then(coverModifier)
                    )
                }
                Spacer(modifier = Modifier.height(18.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerSongMetaText(
                        song = song,
                        annotation = annotation,
                        titleFontSize = 25.sp,
                        artistFontSize = 18.sp,
                        artistAlpha = 0.64f,
                        showArtistWithAnnotation = true,
                        contentColor = pagePalette.onBackground,
                        fontFamily = fontFamily,
                        onArtistClick = onArtist,
                        titleMarqueeEnabled = true,
                        artistMarqueeEnabled = true,
                        modifier = Modifier
                            .weight(1f)
                            .widthIn(min = 0.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    PlayerHeaderAction(
                        kind = PlayerHeaderActionKind.Favorite,
                        selected = isFavorite,
                        onClick = onToggleFavorite
                    )
                    PlayerHeaderAction(
                        kind = PlayerHeaderActionKind.More,
                        onClick = onToggleMenu
                    )
                }
                Spacer(modifier = Modifier.height(if (compactWindow) 12.dp else 18.dp))
                PlayerProgressBlock(
                    currentPosition = currentPosition,
                    duration = duration,
                    song = song,
                    audioInfo = audioInfo,
                    bluetoothDeviceName = bluetoothDeviceName,
                    playbackModeLabel = if (musicVideoVisible) "MV" else null,
                    palette = pagePalette,
                    allowTapSeek = playerTapSeekEnabled,
                    showTotalDuration = playerShowTotalDuration,
                    onSeek = onSeek,
                    fontFamily = fontFamily
                )
                Spacer(modifier = Modifier.height(14.dp))
                LandscapeTransportControls(
                    isPlaying = isPlaying,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    palette = pagePalette,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    controlHeight = if (compactWindow) 70.dp else 96.dp,
                    sideIconSize = if (compactWindow) 34.dp else 40.dp,
                    playButtonSize = if (compactWindow) 66.dp else 78.dp,
                    playIconSize = if (compactWindow) 40.dp else 48.dp
                )
                Spacer(modifier = Modifier.height(if (compactWindow) 4.dp else 8.dp))
                AppleMusicFooterActions(height = if (compactWindow) 68.dp else 88.dp)
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        @Composable
        fun AppleMusicLyricsSessionPage(coverModifier: Modifier = Modifier) {
            var lyricMenuExpanded by remember { mutableStateOf(false) }
            Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(if (compactWindow) 8.dp else 12.dp))
                LyricsPlayerHeader(
                    song = song,
                    embeddedCover = embeddedCover,
                    annotation = annotation,
                    activeSinger = null,
                    isFavorite = isFavorite,
                    onDismissLyrics = { appleMusicShowLyrics = false },
                    onArtist = onArtist,
                    onToggleFavorite = onToggleFavorite,
                    onShowMenu = { lyricMenuExpanded = true },
                    fontFamily = fontFamily,
                    coverModifier = coverModifier
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AppleMusicLyricsView(
                        lyrics = lyrics,
                        currentIndex = currentLyricIndex,
                        currentPositionMs = currentPosition,
                        isPlaying = isPlaying,
                        isPaused = isActuallyPaused,
                        pageVisible = true,
                        showTranslation = showTranslation,
                        showPronunciation = showPronunciation,
                        fontFamily = fontFamily,
                        translationFontFamily = translationFontFamily,
                        fontWeight = fontWeight,
                        fontScale = fontScale,
                        secondaryFontScale = secondaryFontScale,
                        primaryTextSizeSp = primaryTextSizeSp,
                        secondaryTextSizeSp = secondaryTextSizeSp,
                        lyricTextAlign = lyricTextAlign,
                        contentColor = pagePalette.onBackground,
                        wordLiftEnabled = appleMusicWordLiftEnabled,
                        onLineClick = { line ->
                            revealAppleMusicChrome()
                            onLyricLineClick(line)
                        },
                        onLineDoubleClick = { revealAppleMusicChrome() },
                        onLineLongClick = onLyricLineLongClick,
                        topContentPadding = 16.dp,
                        bottomContentPadding = if (compactWindow) 56.dp else 72.dp,
                        lineSpacing = if (compactWindow) 14.dp else 20.dp,
                        focusOffsetRatio = 0.22f,
                        modifier = Modifier.fillMaxSize()
                    )
                    androidx.compose.animation.AnimatedVisibility(
                        visible = PlayerMotion.lyricsCornerActionsVisible(appleMusicChromeVisible),
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 8.dp)
                    ) {
                        LyricsCornerActions(
                            showTranslation = showTranslation,
                            onToggleTranslation = onToggleTranslation,
                            contentColor = pagePalette.onBackground
                        )
                    }
                }
                AnimatedVisibility(
                    visible = appleMusicChromeVisible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PlayerProgressBlock(
                            currentPosition = currentPosition,
                            duration = duration,
                            song = song,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            playbackModeLabel = if (musicVideoVisible) "MV" else null,
                            palette = pagePalette,
                            allowTapSeek = playerTapSeekEnabled,
                            showTotalDuration = playerShowTotalDuration,
                            onSeek = {
                                revealAppleMusicChrome()
                                onSeek(it)
                            },
                            fontFamily = fontFamily
                        )
                        Spacer(modifier = Modifier.height(if (compactWindow) 8.dp else 12.dp))
                        LandscapeTransportControls(
                            isPlaying = isPlaying,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            palette = pagePalette,
                            onCyclePlaybackMode = {
                                revealAppleMusicChrome()
                                onCyclePlaybackMode()
                            },
                            onPrevious = {
                                revealAppleMusicChrome()
                                onPrevious()
                            },
                            onPlayPause = {
                                revealAppleMusicChrome()
                                onPlayPause()
                            },
                            onNext = {
                                revealAppleMusicChrome()
                                onNext()
                            },
                            controlHeight = if (compactWindow) 64.dp else 84.dp,
                            sideIconSize = if (compactWindow) 32.dp else 38.dp,
                            playButtonSize = if (compactWindow) 60.dp else 72.dp,
                            playIconSize = if (compactWindow) 36.dp else 44.dp
                        )
                        AppleMusicFooterActions(height = if (compactWindow) 56.dp else 64.dp)
                    }
                }
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
            LyricsPlayerMenuSheet(
                show = lyricMenuExpanded,
                showPronunciation = showPronunciation,
                showTranslation = showTranslation,
                keepScreenOn = lyricPageKeepScreenOn,
                perspectiveEffect = lyricPerspectiveEffect,
                perspectiveYAngle = lyricPerspectiveYAngle,
                lyricFormatAvailability = lyricFormatAvailability,
                preferTtmlLyrics = preferTtmlLyrics,
                lyricSourceMode = lyricSourceMode,
                lyricParserEngine = lyricParserEngine,
                layoutProfile = lyricLayoutProfile,
                fontScale = fontScale,
                secondaryFontScale = secondaryFontScale,
                primaryTextSizeSp = primaryTextSizeSp,
                secondaryTextSizeSp = secondaryTextSizeSp,
                onDismiss = { lyricMenuExpanded = false },
                onTogglePronunciation = {
                    lyricMenuExpanded = false
                    onTogglePronunciation()
                },
                onToggleTranslation = {
                    lyricMenuExpanded = false
                    onToggleTranslation()
                },
                onToggleKeepScreenOn = {
                    lyricMenuExpanded = false
                    onToggleLyricKeepScreenOn()
                },
                onTogglePerspectiveEffect = onToggleLyricPerspectiveEffect,
                onPerspectiveYAngle = onLyricPerspectiveYAngle,
                onLyricSourceMode = { mode ->
                    lyricMenuExpanded = false
                    onLyricSourceMode(mode)
                },
                onLyricFormatPreference = { preferTtml ->
                    lyricMenuExpanded = false
                    onLyricFormatPreference(preferTtml)
                },
                onLyricParserEngine = { engine ->
                    lyricMenuExpanded = false
                    onLyricParserEngine(engine)
                },
                onFontScale = onLyricFontScale,
                onSecondaryFontScale = onLyricSecondaryFontScale,
                onPrimaryTextSize = onLyricPrimaryTextSize,
                onSecondaryTextSize = onLyricSecondaryTextSize,
                modifier = Modifier.fillMaxWidth()
            )
            }
        }

        @OptIn(ExperimentalSharedTransitionApi::class)
        @Composable
        fun AppleMusicPlayerPage() {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                val sharedCoverState = rememberSharedContentState(key = "appleMusicCover")
                AnimatedContent(
                    targetState = appleMusicShowLyrics,
                    transitionSpec = {
                        fadeIn(
                            animationSpec = tween(
                                durationMillis = PlayerMotion.CoverMorphDurationMs,
                                easing = PlayerMotion.CoverMorphEasing
                            )
                        ) togetherWith fadeOut(
                            animationSpec = tween(
                                durationMillis = PlayerMotion.CoverMorphDurationMs / 2,
                                easing = PlayerMotion.CoverMorphEasing
                            )
                        )
                    },
                    label = "AppleMusicLyricsSession"
                ) { showLyrics ->
                    val coverModifier = Modifier.sharedElement(
                        sharedContentState = sharedCoverState,
                        animatedVisibilityScope = this,
                        boundsTransform = { _, _ ->
                            tween(
                                durationMillis = PlayerMotion.CoverMorphDurationMs,
                                easing = PlayerMotion.CoverMorphEasing
                            )
                        }
                    )
                    if (showLyrics) {
                        AppleMusicLyricsSessionPage(coverModifier = coverModifier)
                    } else {
                        AppleMusicCoverPage(coverModifier = coverModifier)
                    }
                }
            }
        }

        @Composable
        fun ImmersiveLyricsPlayerPage() {
            val artworkHeight = minOf(
                maxWidth,
                maxHeight * if (compactWindow) 0.42f else 0.50f
            )
            val lyricsSurface = pagePalette.bottom
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(lyricsSurface)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artworkHeight)
                ) {
                    StyledPlayerArtwork(
                        cornerRadius = 0.dp,
                        showOverlayBadges = false,
                        swipeModifier = skipCoverSwipeModifier,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.00f to Color.Transparent,
                                    0.48f to Color.Transparent,
                                    0.72f to lyricsSurface.copy(alpha = 0.45f),
                                    1.00f to lyricsSurface
                                )
                            )
                    )
                    CompositionLocalProvider(LocalPlayerContentColor provides Color.White) {
                        Row(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(start = 24.dp, end = 16.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerSongMetaText(
                                song = song,
                                annotation = annotation,
                                titleFontSize = 22.sp,
                                artistFontSize = 15.sp,
                                artistAlpha = 0.72f,
                                showArtistWithAnnotation = true,
                                contentColor = Color.White,
                                fontFamily = fontFamily,
                                onArtistClick = onArtist,
                                titleMarqueeEnabled = true,
                                artistMarqueeEnabled = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 0.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color.White.copy(alpha = 0.20f))
                                    .playerNoIndicationClick(onPlayPause),
                                contentAlignment = Alignment.Center
                            ) {
                                CenteredPlayPauseGlyph(
                                    isPlaying = isPlaying,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.Favorite,
                                selected = isFavorite,
                                onClick = onToggleFavorite
                            )
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.More,
                                onClick = onToggleMenu
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(lyricsSurface)
                        .padding(horizontal = 24.dp)
                ) {
                    AppleMusicLyricsView(
                        lyrics = lyrics,
                        currentIndex = currentLyricIndex,
                        currentPositionMs = currentPosition,
                        isPlaying = isPlaying,
                        isPaused = isActuallyPaused,
                        pageVisible = true,
                        showTranslation = showTranslation,
                        showPronunciation = showPronunciation,
                        fontFamily = fontFamily,
                        translationFontFamily = translationFontFamily,
                        fontWeight = fontWeight,
                        fontScale = fontScale,
                        secondaryFontScale = secondaryFontScale,
                        primaryTextSizeSp = primaryTextSizeSp,
                        secondaryTextSizeSp = secondaryTextSizeSp,
                        lyricTextAlign = lyricTextAlign,
                        contentColor = pagePalette.onBackground,
                        wordLiftEnabled = appleMusicWordLiftEnabled,
                        onLineClick = onLyricLineClick,
                        onLineDoubleClick = onShowLyrics,
                        onLineLongClick = onLyricLineLongClick,
                        topContentPadding = 8.dp,
                        bottomContentPadding = if (compactWindow) 56.dp else 72.dp,
                        lineSpacing = if (compactWindow) 12.dp else 18.dp,
                        focusOffsetRatio = 0.12f,
                        modifier = Modifier.fillMaxSize()
                    )
                    if (!compactWindow) {
                        AudioVisualizer(
                            enabled = visualizerEnabled,
                            audioSessionId = audioSessionId,
                            isPlaying = isPlaying,
                            positionMs = currentPosition,
                            opacity = visualizerOpacity,
                            accent = pagePalette.accent.copy(alpha = 0.72f),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .requiredWidth(rootPlayerWidth)
                                .height(30.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            }
        }

        if (useWidePlayer && selectedPlayerPageStyle ==
            com.ella.music.data.SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                if (drawBackground) {
                    LandscapeCoverModeBackground(
                        palette = pagePalette,
                        dynamicCoverSource = displayedDynamicCover,
                        embeddedCover = embeddedCover,
                        paletteBitmap = paletteBitmap,
                        currentPosition = currentPosition,
                        duration = duration,
                        isPlaying = isPlaying,
                        flowEffectMode = flowEffectMode,
                        dynamicFlowEnabled = dynamicFlowEnabled,
                        visualizerEnabled = visualizerEnabled,
                        visualizerOpacity = visualizerOpacity,
                        customBackgroundUri = playerBackgroundUri.takeIf { showCustomPlayerBackground }.orEmpty(),
                        customBackgroundOpacity = playerBackgroundOpacity,
                        customBackgroundDim = playerBackgroundDim,
                        beautifulLyricsBackground = beautifulLyricsBackground,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                @Composable
                fun AppleMusicWideNowPlayingColumn(
                    modifier: Modifier,
                    extraTopPadding: androidx.compose.ui.unit.Dp = 12.dp
                ) {
                    Column(
                        modifier = modifier
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = 20.dp, end = 20.dp, top = extraTopPadding, bottom = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            val coverSize = minOf(maxWidth * 0.86f, maxHeight)
                            StyledPlayerArtwork(
                                cornerRadius = 18.dp,
                                modifier = Modifier.size(coverSize)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlayerSongMetaText(
                                song = song,
                                annotation = annotation,
                                titleFontSize = 20.sp,
                                artistFontSize = 14.sp,
                                artistAlpha = 0.64f,
                                showArtistWithAnnotation = true,
                                contentColor = pagePalette.onBackground,
                                fontFamily = fontFamily,
                                onArtistClick = onArtist,
                                titleMarqueeEnabled = true,
                                artistMarqueeEnabled = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 0.dp)
                            )
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.Favorite,
                                selected = isFavorite,
                                onClick = onToggleFavorite
                            )
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.More,
                                onClick = onToggleMenu
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerProgressBlock(
                            currentPosition = currentPosition,
                            duration = duration,
                            song = song,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            playbackModeLabel = if (musicVideoVisible) "MV" else null,
                            palette = pagePalette,
                            allowTapSeek = playerTapSeekEnabled,
                            showTotalDuration = playerShowTotalDuration,
                            onSeek = onSeek,
                            fontFamily = fontFamily
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LandscapeTransportControls(
                            isPlaying = isPlaying,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            palette = pagePalette,
                            onCyclePlaybackMode = onCyclePlaybackMode,
                            onPrevious = onPrevious,
                            onPlayPause = onPlayPause,
                            onNext = onNext,
                            controlHeight = 78.dp,
                            sideIconSize = 36.dp,
                            playButtonSize = 68.dp,
                            playIconSize = 42.dp
                        )
                        AppleMusicFooterActions(height = 72.dp)
                    }
                }
                if (appleMusicShowLyrics) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppleMusicWideNowPlayingColumn(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.40f),
                            extraTopPadding = 20.dp
                        )
                        Box(
                            modifier = Modifier
                                .weight(0.60f)
                                .fillMaxHeight()
                                .padding(start = 20.dp)
                                .windowInsetsPadding(WindowInsets.statusBars)
                                .windowInsetsPadding(WindowInsets.navigationBars)
                        ) {
                            AppleMusicLyricsView(
                                lyrics = lyrics,
                                currentIndex = currentLyricIndex,
                                currentPositionMs = currentPosition,
                                isPlaying = isPlaying,
                                isPaused = isActuallyPaused,
                                pageVisible = true,
                                showTranslation = showTranslation,
                                showPronunciation = showPronunciation,
                                fontFamily = fontFamily,
                                translationFontFamily = translationFontFamily,
                                fontWeight = fontWeight,
                                fontScale = fontScale,
                                secondaryFontScale = secondaryFontScale,
                                primaryTextSizeSp = primaryTextSizeSp,
                                secondaryTextSizeSp = secondaryTextSizeSp,
                                lyricTextAlign = lyricTextAlign,
                                contentColor = pagePalette.onBackground,
                                wordLiftEnabled = appleMusicWordLiftEnabled,
                                onLineClick = { line ->
                                    revealAppleMusicChrome()
                                    onLyricLineClick(line)
                                },
                                onLineDoubleClick = { revealAppleMusicChrome() },
                                onLineLongClick = onLyricLineLongClick,
                                topContentPadding = 8.dp,
                                bottomContentPadding = 72.dp,
                                lineSpacing = 18.dp,
                                focusOffsetRatio = 0.22f,
                                modifier = Modifier.fillMaxSize()
                            )
                            androidx.compose.animation.AnimatedVisibility(
                                visible = PlayerMotion.lyricsCornerActionsVisible(appleMusicChromeVisible),
                                enter = fadeIn(),
                                exit = fadeOut(),
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(end = 4.dp, bottom = 12.dp)
                            ) {
                                LyricsCornerActions(
                                    showTranslation = showTranslation,
                                    onToggleTranslation = {
                                        revealAppleMusicChrome()
                                        onToggleTranslation()
                                    },
                                    contentColor = pagePalette.onBackground,
                                    packedEnd = true
                                )
                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        AppleMusicWideNowPlayingColumn(
                            modifier = Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(0.44f)
                                .widthIn(max = 520.dp),
                            extraTopPadding = 48.dp
                        )
                    }
                }
            }
        } else if (useWidePlayer) {
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
                coverLongPressPreviewEnabled = coverLongPressPreviewEnabled &&
                    resolvedStaticCoverPreviewModel != null,
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
                onPreviewCover = {
                    resolvedStaticCoverPreviewModel?.let { model ->
                        previewCover = PlayerCoverPreview(
                            model = model,
                            title = song?.coverPreviewDisplayTitle().orEmpty(),
                            saveName = song?.coverPreviewSaveName().orEmpty()
                        )
                    }
                },
                onPlayPause = onPlayPause,
                onNext = onNext,
                onQueueSongClick = onQueueSongClick,
                onRemoveQueueSong = onRemoveQueueSong,
                onMoveQueueSong = onMoveQueueSong,
                onRandomizeQueue = playerViewModel::randomizePlaylistOrder,
                onAddQueueToPlaylist = onAddQueueToPlaylist,
                onClearQueue = onClearQueue,
                onLineClick = onShowLyrics,
                onArtist = onArtist,
                drawBackground = drawBackground,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            when (selectedPlayerPageStyle) {
                com.ella.music.data.SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC -> {
                    AppleMusicPlayerPage()
                }
                com.ella.music.data.SettingsManager.PLAYER_PAGE_STYLE_IMMERSIVE_LYRICS -> {
                    ImmersiveLyricsPlayerPage()
                }
                else -> {
            val immersiveCoverHeight = minOf(
                maxWidth,
                maxHeight * if (annotation.isNotBlank()) 0.42f else 0.47f
            )
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
                                    Modifier.height(immersiveCoverHeight)
                                }
                            )
                            .graphicsLayer {
                                shape = immersiveCoverShape
                                clip = true
                            }
                            .clip(immersiveCoverShape)
                            .then(
                                if (coverLongPressPreviewEnabled && resolvedStaticCoverPreviewModel != null) {
                                    Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = {
                                            previewCover = PlayerCoverPreview(
                                                model = resolvedStaticCoverPreviewModel,
                                                title = song?.coverPreviewDisplayTitle().orEmpty(),
                                                saveName = song?.coverPreviewSaveName().orEmpty()
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
                        // Keep MV silent and on the audio clock while its surface is hidden.
                        if (videoPlaybackActive && musicVideoVisible) resolvedMusicVideo?.let { source ->
                            DynamicCoverVideo(
                                source = source,
                                isPlaying = isPlaying && videoPlaybackActive,
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
                        if (videoPlaybackActive && !musicVideoVisible && displayedDynamicCover != null) {
                            DynamicCoverVideo(
                                source = displayedDynamicCover,
                                isPlaying = isPlaying && videoPlaybackActive,
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
                                    colors = if (pagePalette.isLight) {
                                        listOf(
                                            Color.White.copy(alpha = 0.20f),
                                            Color.White.copy(alpha = 0.12f),
                                            Color.White.copy(alpha = 0.34f)
                                        )
                                    } else {
                                        listOf(
                                            Color.Black.copy(alpha = 0.22f),
                                            Color.Black.copy(alpha = 0.12f),
                                            Color.Black.copy(alpha = 0.38f)
                                        )
                                    }
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
                                isPaused = isActuallyPaused,
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
                            song = song,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            playbackModeLabel = if (musicVideoVisible) "MV" else null,
                            palette = pagePalette,
                            allowTapSeek = playerTapSeekEnabled,
                            showTotalDuration = playerShowTotalDuration,
                            onSeek = onSeek,
                            fontFamily = fontFamily
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
                            onRandomizeQueue = playerViewModel::randomizePlaylistOrder,
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
                                accent = pagePalette.accent.copy(alpha = 0.88f),
                                modifier = Modifier
                                    .requiredWidth(rootPlayerWidth)
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
                                modifier = Modifier
                                    .width(nonImmersiveCoverSize)
                                    .align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        val coverShape = RoundedCornerShape(14.dp)
                        Box(
                            modifier = Modifier
                                .size(nonImmersiveCoverSize)
                                .graphicsLayer {
                                    shape = coverShape
                                    clip = true
                                }
                                .clip(coverShape)
                                .then(
                                    if (coverLongPressPreviewEnabled && resolvedStaticCoverPreviewModel != null) {
                                        Modifier.combinedClickable(
                                            onClick = {},
                                            onLongClick = {
                                                previewCover = PlayerCoverPreview(
                                                    model = resolvedStaticCoverPreviewModel,
                                                    title = song?.coverPreviewDisplayTitle().orEmpty(),
                                                    saveName = song?.coverPreviewSaveName().orEmpty()
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
                            // Keep MV silent and synchronized behind the current cover.
                            if (videoPlaybackActive && musicVideoVisible) resolvedMusicVideo?.let { source ->
                                DynamicCoverVideo(
                                    source = source,
                                    isPlaying = isPlaying && videoPlaybackActive,
                                    syncPositionMs = currentPosition,
                                    syncDurationMs = duration,
                                    onPlaybackError = { onDynamicCoverFailed(source.failureKey) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (musicVideoVisible) 1f else 0.001f },
                                    cornerRadiusDp = 14f
                                )
                            }
                            if (videoPlaybackActive && !musicVideoVisible && displayedDynamicCover != null) {
                                DynamicCoverVideo(
                                    source = displayedDynamicCover,
                                    isPlaying = isPlaying && videoPlaybackActive,
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
                                            painter = painterResource(R.drawable.ic_fullscreen),
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
                                        text = stringResource(R.string.player_detail_music_video),
                                        fontSize = 12.sp,
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
                                modifier = Modifier
                                    .width(nonImmersiveCoverSize)
                                    .align(Alignment.CenterHorizontally)
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
                                isPaused = isActuallyPaused,
                                fontFamily = fontFamily,
                                translationFontFamily = translationFontFamily,
                                fontWeight = fontWeight,
                                fontScale = fontScale,
                                secondaryFontScale = secondaryFontScale,
                                lyricTextAlign = lyricTextAlign,
                                compact = compactNonImmersiveLyrics,
                                contentColor = pagePalette.onBackground,
                                wordLiftEnabled = appleMusicWordLiftEnabled,
                                onLineClick = { onShowLyrics() },
                                modifier = Modifier
                                    .width(nonImmersiveCoverSize)
                                    .align(Alignment.CenterHorizontally)
                                    .height(
                                        if (compactNonImmersiveLyrics) {
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
                                    .width(nonImmersiveCoverSize)
                                    .align(Alignment.CenterHorizontally)
                                    .height(if (compactNonImmersiveLyrics) 40.dp else 150.dp)
                            )
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        // 1.2.2 composition: the lyric preview hugs the title and the flexible
                        // room sits between it and the fixed action/transport area below, which
                        // therefore can never be compressed by the content above.
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
                            song = song,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            playbackModeLabel = if (musicVideoVisible) "MV" else null,
                            palette = pagePalette,
                            allowTapSeek = playerTapSeekEnabled,
                            showTotalDuration = playerShowTotalDuration,
                            onSeek = onSeek,
                            fontFamily = fontFamily
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
                            onRandomizeQueue = playerViewModel::randomizePlaylistOrder,
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
            }
        }

        PlayerCoverActionSheet(
            show = menuExpanded,
            song = song,
            showLyricsDisplayEntry = true,
            playbackSpeed = playbackSpeed,
            playbackPitch = playbackPitch,
            visualizerEnabled = visualizerEnabled,
            visualizerAvailable = true,
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
            onCyclePlaybackMode = onCyclePlaybackMode,
            abRepeatState = abRepeatState,
            onAbRepeat = onAbRepeat,
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
                    previewCover = PlayerCoverPreview(
                        model = model,
                        title = song?.coverPreviewDisplayTitle().orEmpty(),
                        saveName = song?.coverPreviewSaveName().orEmpty()
                    )
                }
            },
            initialPage = actionMenuInitialPage
        )
        previewCover?.let { cover ->
            CoverPreviewDialog(
                model = cover.model,
                title = cover.title,
                saveName = cover.saveName,
                onDismiss = { previewCover = null }
            )
        }
    }
}

private data class PlayerCoverPreview(
    val model: Any,
    val title: String,
    val saveName: String
)

private fun Song.coverPreviewDisplayTitle(): String =
    listOf(title.ifBlank { fileName }, artist.takeIf(String::isNotBlank))
        .filterNotNull()
        .joinToString(" - ")

private fun Song.coverPreviewSaveName(): String =
    listOf(artist.takeIf(String::isNotBlank), title.ifBlank { fileName })
        .filterNotNull()
        .joinToString(" - ")

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
    swipeEnabled: Boolean,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit
): Modifier {
    val dismissHandle = LocalPlayerCoverDismiss.current
    return Modifier.playerCoverGestures(
        swipeEnabled = swipeEnabled,
        onSwipePrevious = onSwipePrevious,
        onSwipeNext = onSwipeNext,
        dismissHandle = dismissHandle
    )
}
