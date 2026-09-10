@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ella.music.ui.player

import androidx.compose.foundation.gestures.detectTapGestures

import android.content.Context
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.CoverPreviewDialog
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixDialogActions
import com.ella.music.ui.components.PlayerQueueListIcon
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.SettingsManager
import com.ella.music.data.repository.MusicRepository
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.RemoteMusicSourceConfig
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.AbRepeatState
import com.ella.music.viewmodel.PlayerViewModel
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

/**
 * Keeps player controls visually clear of the bottom edge even when an OEM reports no gesture
 * inset while its gesture handle is still visible. The system inset is retained when available;
 * the fixed 8dp clearance is the stable fallback for hidden/zero-inset navigation bars.
 */
internal val PlayerBottomClearanceFallback = 8.dp

@Composable
private fun PlayerBottomClearance(
    reserveNavigation: Boolean = true
) {
    if (reserveNavigation) {
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
    Spacer(modifier = Modifier.height(PlayerBottomClearanceFallback))
}

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
    defaultAppleMusicShowLyrics: Boolean = false,
    showPlayerKeepScreenOnAction: Boolean,
    playerKeepScreenOn: Boolean,
    menuExpanded: Boolean,
    queueExpanded: Boolean,
    playlist: List<Song>,
    librarySongs: List<Song> = emptyList(),
    currentQueueIndexHint: Int = -1,
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
    onLyricShare: () -> Unit,
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
    val previousSong by playerViewModel.previousSong.collectAsState()
    val nextSong by playerViewModel.nextSong.collectAsState()
    val isActuallyPaused = !isPlaying && !playWhenReady
    // The transport glyph must reflect the player's actual transport state. playWhenReady only
    // describes the request/intent to play and remains true while buffering or after a stale
    // controller snapshot, which can make the icon disagree with the sound.
    val visualIsPlaying = isPlaying
    val staticCoverPreviewModel by produceState<Any?>(
        initialValue = resolveCoverPreviewModel(song, null),
        song?.let {
            listOf(it.playlistIdentityKey(), it.dateModified, it.fileSize, it.coverUrl).joinToString("|")
        }
    ) {
        value = withContext(Dispatchers.IO) {
            preferredCoverPreviewModel(
                originalModel = song?.let(playerViewModel::getOriginalCoverModel),
                decodedFallback = resolveCoverPreviewModel(song, null)
            )
        }
    }
    // A decoded player bitmap is only a display fallback. Preview and the cover surface must
    // keep the original file/embedded bytes, otherwise Coil's ORIGINAL request still shows the
    // downsampled thumbnail (#609).
    val resolvedStaticCoverPreviewModel = preferredCoverPreviewModel(
        originalModel = staticCoverPreviewModel,
        decodedFallback = embeddedCover
    )
    // Keep one painter alive above the Apple Music cover/lyrics switch. The two layouts use the
    // same artwork at different bounds; recreating AsyncImage inside AnimatedContent briefly
    // cleared the painter and produced a visible flash on tablets.
    val stableArtworkRequest = remember(context, resolvedStaticCoverPreviewModel) {
        ImageRequest.Builder(context)
            .data(resolvedStaticCoverPreviewModel)
            .size(2048)
            .build()
    }
    val stableArtworkPainter = rememberAsyncImagePainter(model = stableArtworkRequest)
    // Keep an opened preview as a snapshot.  Changing tracks must update the player behind the
    // dialog, not dismiss or replace the artwork the user is currently inspecting.
    var previewCover by remember { mutableStateOf<PlayerCoverPreview?>(null) }
    val coverLongPressPreviewEnabled by playerViewModel.settingsManager.playerCoverLongPressPreviewEnabled
        .collectAsState(initial = true)
    val musicVideoFullscreenButtonEnabled by playerViewModel.settingsManager.musicVideoFullscreenButtonEnabled
        .collectAsState(initial = SettingsManager.DEFAULT_MUSIC_VIDEO_FULLSCREEN_BUTTON_ENABLED)
    val musicVideoLongPressInfoEnabled by playerViewModel.settingsManager.musicVideoLongPressInfoEnabled
        .collectAsState(initial = SettingsManager.DEFAULT_MUSIC_VIDEO_LONG_PRESS_INFO_ENABLED)
    val musicVideoLongPressImmersiveLyricsEnabled by playerViewModel.settingsManager.musicVideoLongPressImmersiveLyricsEnabled
        .collectAsState(initial = SettingsManager.DEFAULT_MUSIC_VIDEO_LONG_PRESS_IMMERSIVE_LYRICS_ENABLED)
    val playerAlbumCoverCornerRadius by playerViewModel.settingsManager.playerAlbumCoverCornerRadius
        .collectAsState(initial = SettingsManager.DEFAULT_PLAYER_ALBUM_COVER_CORNER_RADIUS_DP)
    val playerMusicVideoCornerRadius by playerViewModel.settingsManager.playerMusicVideoCornerRadius
        .collectAsState(initial = SettingsManager.DEFAULT_PLAYER_MUSIC_VIDEO_CORNER_RADIUS_DP)
    var showMusicVideoInfo by remember { mutableStateOf(false) }
    val immersiveLyricSwipeEnabled by playerViewModel.settingsManager.playerImmersiveLyricSwipe
        .collectAsState(initial = false)
    val appleMusicImmersiveSquareCover by playerViewModel.settingsManager.appleMusicPlayerImmersiveCover
        .collectAsState(initial = false)
    val appleMusicUseAppleFavorite by playerViewModel.settingsManager.appleMusicUseAppleFavorite
        .collectAsState(initial = false)
    val systemBarsReserveSpace by playerViewModel.settingsManager.systemBarsReserveSpace
        .collectAsState(initial = SettingsManager.DEFAULT_SYSTEM_BARS_RESERVE_SPACE)
    val playerSystemBarsMode by playerViewModel.settingsManager.playerSystemBarsMode
        .collectAsState(initial = SettingsManager.DEFAULT_PLAYER_SYSTEM_BARS_MODE)
    val globalSystemBarsMode by playerViewModel.settingsManager.systemBarsMode
        .collectAsState(initial = SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH)
    val effectivePlayerSystemBarsMode = SettingsManager.playerSystemBarsEffectiveMode(
        playerMode = playerSystemBarsMode,
        globalMode = globalSystemBarsMode
    )
    val shouldReserveStatusBar = systemBarsReserveSpace || effectivePlayerSystemBarsMode !in setOf(
        SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS,
        SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH
    )
    val shouldReserveNavigationBar = systemBarsReserveSpace || effectivePlayerSystemBarsMode !in setOf(
        SettingsManager.SYSTEM_BARS_MODE_HIDE_NAVIGATION,
        SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH
    )
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
    val rawNonImmersiveShortcuts by playerViewModel.settingsManager.nonImmersivePlayerShortcutItems
        .collectAsState(initial = SettingsManager.DEFAULT_NON_IMMERSIVE_PLAYER_SHORTCUT_ITEMS)
    val nonImmersiveShortcutIds = remember(rawNonImmersiveShortcuts) {
        if (rawNonImmersiveShortcuts.isBlank()) {
            emptyList()
        } else {
            rawNonImmersiveShortcuts.split(',')
                .filter { it.isNotBlank() && (it in ActionMenuIds.playerShortcutCatalog || it == ActionMenuIds.REMOTE_QUALITY) }
                .distinct()
                .take(SettingsManager.MAX_NON_IMMERSIVE_PLAYER_SHORTCUT_ITEMS)
        }
    }
    var localActionMenuPage by remember { mutableStateOf<PlayerActionSheetPage?>(null) }
    val executePlayerAction: (String) -> Unit = { actionId ->
        when (actionId) {
            ActionMenuIds.SPEED -> localActionMenuPage = PlayerActionSheetPage.Speed
            ActionMenuIds.EQUALIZER -> onOpenEqualizer()
            ActionMenuIds.TIMER -> localActionMenuPage = PlayerActionSheetPage.Timer
            ActionMenuIds.ADD_TO_PLAYLIST -> onAddToPlaylist()
            ActionMenuIds.PLAY_NEXT -> onPlayNext()
            ActionMenuIds.ADD_TO_QUEUE -> onAddToQueue()
            ActionMenuIds.SHARE -> onShareSong()
            ActionMenuIds.AI -> onAiInterpret()
            ActionMenuIds.INFO -> onSongInfo()
            ActionMenuIds.AUDIO_OUTPUT -> localActionMenuPage = PlayerActionSheetPage.AudioOutput
            ActionMenuIds.CASTING -> openSystemOutputSwitcher(context)
            ActionMenuIds.AB_REPEAT -> onAbRepeat()
            ActionMenuIds.LANDSCAPE -> onLandscape()
            ActionMenuIds.LYRICS_DISPLAY -> localActionMenuPage = PlayerActionSheetPage.LyricDisplay
            ActionMenuIds.SPECTRUM -> onSpectrum()
            ActionMenuIds.RATING -> onSetRating()
            ActionMenuIds.DYNAMIC_COVER -> onMatchDynamicCover()
            ActionMenuIds.VISUALIZER -> localActionMenuPage = PlayerActionSheetPage.Visualizer
            ActionMenuIds.EDIT_TAGS -> onOpenMetadataEditor()
            ActionMenuIds.LYRIC_TIMING -> onLyricTiming()
            ActionMenuIds.ONLINE_LYRICS -> onMatchOnlineLyrics()
            ActionMenuIds.LYRIC_OFFSET -> localActionMenuPage = PlayerActionSheetPage.LyricOffset
            ActionMenuIds.KEEP_SCREEN_ON -> if (showPlayerKeepScreenOnAction) onPlayerKeepScreenOnChange(!playerKeepScreenOn)
            ActionMenuIds.DOWNLOAD -> onDownload()
            ActionMenuIds.DELETE -> onDeleteSong()
        }
    }
    val dynamicCoverSongKey = song?.dynamicCoverResolutionKey().orEmpty()
    val syncMusicVideoPlayPause = {
        // Pause the silent MV decoder immediately. Waiting for isPlaying to propagate lets a
        // stale audio callback restart the video after a brief pause (#605).
        if (musicVideoVisible) {
            MusicVideoPlaybackBridge.setPlaying(dynamicCoverSongKey, !playWhenReady)
        }
        onPlayPause()
    }
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
    // Do not let an async lookup from the previous song feed the current page. This is
    // especially important for the resident player, where the composable stays alive across
    // queue changes.
    val displayedMusicVideo = resolvedMusicVideo?.takeIf { it.playbackOwnerKey == dynamicCoverSongKey }
    val onMusicVideoInfoLongPress: (() -> Unit)? = if (
        musicVideoVisible && displayedMusicVideo != null && musicVideoLongPressInfoEnabled
    ) {
        { showMusicVideoInfo = true }
    } else {
        null
    }
    val portraitDynamicCover = (if (musicVideoVisible) displayedMusicVideo else displayedDynamicCover)
        ?.aspectRatio?.let { it < 0.92f } == true
    val skipCoverSwipeModifier = rememberCoverSwipeModifier(
        swipeEnabled = coverSwipeEnabled,
        onSwipePrevious = onSwipePrevious,
        onSwipeNext = onNext,
        hintColor = palette.onBackground,
        previousSongTitle = previousSong?.title,
        nextSongTitle = nextSong?.title
    )
    val coverSwipeModifier = skipCoverSwipeModifier
    val immersiveLyricSwipeModifier = rememberCoverSwipeModifier(
        swipeEnabled = immersiveLyricSwipeEnabled,
        onSwipePrevious = onSwipePrevious,
        onSwipeNext = onNext,
        hintColor = palette.onBackground,
        previousSongTitle = previousSong?.title,
        nextSongTitle = nextSong?.title,
        dismissEnabled = false
    )
    val defaultAppleMusicLyrics = defaultAppleMusicShowLyrics &&
        com.ella.music.data.SettingsManager.normalizePlayerPageStyle(playerPageStyle) ==
        com.ella.music.data.SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC
    var appleMusicShowLyrics by remember(defaultAppleMusicLyrics) { mutableStateOf(defaultAppleMusicLyrics) }
    var appleMusicShowQueue by remember { mutableStateOf(false) }
    // Start hidden while lyrics are on 鈥?show chrome on enter, auto-hide after 3s; bottom third reveals it.
    var appleMusicChromeVisible by remember(defaultAppleMusicLyrics) {
        mutableStateOf(true)
    }
    var appleMusicChromeGeneration by remember { mutableIntStateOf(0) }
    fun revealAppleMusicChrome() {
        appleMusicChromeVisible = true
        appleMusicChromeGeneration++
    }
    fun hideAppleMusicChrome() {
        appleMusicChromeVisible = false
    }
    LaunchedEffect(appleMusicShowLyrics) {
        if (appleMusicShowLyrics) {
            // Entering lyrics: keep transport visible, then auto-hide after delay.
            appleMusicChromeVisible = true
            appleMusicChromeGeneration++
        } else {
            appleMusicChromeVisible = true
            appleMusicChromeGeneration++
        }
    }
    LaunchedEffect(appleMusicShowLyrics, appleMusicChromeVisible, appleMusicChromeGeneration) {
        if (!appleMusicShowLyrics || !appleMusicChromeVisible) return@LaunchedEffect
        delay(3_000)
        appleMusicChromeVisible = false
    }
    BackHandler(
        enabled = appleMusicShowQueue || shouldInterceptAppleMusicLyricsBack(
            showLyrics = appleMusicShowLyrics,
            playerPageStyle = playerPageStyle,
            preserveLyricsOnBack = defaultAppleMusicLyrics
        )
    ) {
        if (appleMusicShowQueue) {
            appleMusicShowQueue = false
            revealAppleMusicChrome()
        } else {
            appleMusicShowLyrics = false
            revealAppleMusicChrome()
        }
    }

    BoxWithConstraints(modifier = modifier) {
        val rootPlayerWidth = maxWidth
        val rootPlayerHeight = maxHeight
        val useWidePlayer = maxWidth > maxHeight && maxWidth >= 700.dp
        // A phone in landscape can also exceed the wide-player dp threshold. Keep its previous
        // landscape composition independent from the tablet-specific centered column below.
        val isLargeScreenDevice = LocalConfiguration.current.smallestScreenWidthDp >= 600
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
        if (drawBackground && !useWidePlayer) {
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
            contentScale: ContentScale = ContentScale.Fit,
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
                        when {
                            // In silent MV mode the video owns the long press. Keeping the
                            // album-cover preview gesture here used to make the suggested MV
                            // immersive-lyrics shortcut impossible to reach.
                            musicVideoVisible && displayedMusicVideo != null &&
                                musicVideoLongPressImmersiveLyricsEnabled -> {
                                Modifier.combinedClickable(
                                    onClick = {},
                                    onLongClick = onOpenMusicVideoLandscape
                                )
                            }
                            coverLongPressPreviewEnabled && resolvedStaticCoverPreviewModel != null -> {
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
                            }
                            else -> Modifier
                        }
                    )
                    .then(swipeModifier),
                contentAlignment = Alignment.Center
            ) {
                val musicVideoSource = displayedMusicVideo
                val dynamicCoverSource = displayedDynamicCover
                val hasDynamicSource = (musicVideoVisible && musicVideoSource != null) || (!musicVideoVisible && dynamicCoverSource != null)
                val hideArtworkBehindDynamic = hasDynamicSource
                if (!hideArtworkBehindDynamic) {
                    AlbumArtView(
                        song = song,
                        embeddedCover = embeddedCover,
                        coverModel = resolvedStaticCoverPreviewModel,
                        artworkPainter = stableArtworkPainter,
                        cornerRadius = cornerRadius,
                        contentScale = contentScale,
                        showHiResLogo = false,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                val dynamicResizeMode = if (contentScale == ContentScale.Crop) {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                } else {
                    androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
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
                            cornerRadiusDp = cornerRadius.value,
                            resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                        )
                    }
                    videoPlaybackActive && !musicVideoVisible && dynamicCoverSource != null -> {
                        DynamicCoverVideo(
                            source = dynamicCoverSource,
                            isPlaying = isPlaying,
                            onPlaybackError = { onDynamicCoverFailed(dynamicCoverSource.failureKey) },
                            modifier = Modifier.fillMaxSize(),
                            cornerRadiusDp = cornerRadius.value,
                            resizeMode = dynamicResizeMode
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
                    if (musicVideoVisible && musicVideoFullscreenButtonEnabled) {
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
                            fontFamily = fontFamily,
                            color = pagePalette.onBackground.copy(alpha = 0.94f)
                        )
                    }
                }
            }
        }

        @Composable
        fun AppleMusicFooterActions(height: androidx.compose.ui.unit.Dp = 56.dp) {
            val audioOutputState = rememberAudioOutputDeviceState()
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
                        if (appleMusicShowLyrics) {
                            appleMusicShowQueue = false
                            revealAppleMusicChrome()
                        }
                    } else {
                        onShowLyrics()
                    }
                }) {
                    AppleLyricsIcon(
                        color = pagePalette.onBackground.copy(alpha = if (appleMusicShowLyrics) 1f else 0.72f),
                        active = appleMusicShowLyrics,
                        modifier = Modifier.size(30.dp)
                    )
                }
                val isConnectedAudio = audioOutputState.isBluetooth || audioOutputState.isHeadphones
                val connectedDeviceName = audioOutputState.deviceName
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.playerNoIndicationClick {
                        revealAppleMusicChrome()
                        openSystemOutputSwitcher(context)
                    }
                ) {
                    if (isConnectedAudio) {
                        val iconRes = if (audioOutputState.isBluetoothSpeaker) {
                            R.drawable.ic_speaker
                        } else {
                            R.drawable.ic_earphone
                        }
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = connectedDeviceName,
                            tint = pagePalette.onBackground.copy(alpha = 0.90f),
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        AppleChromecastIcon(
                            color = pagePalette.onBackground.copy(alpha = 0.90f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    if (isConnectedAudio && !connectedDeviceName.isNullOrBlank()) {
                        Text(
                            text = connectedDeviceName,
                            color = pagePalette.onBackground.copy(alpha = 0.72f),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                }
                Box(contentAlignment = Alignment.Center) {
                    PlayerTransportIconButton(onClick = {
                        if (selectedPlayerPageStyle == com.ella.music.data.SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC) {
                            appleMusicShowQueue = !appleMusicShowQueue
                            if (appleMusicShowQueue) {
                                appleMusicShowLyrics = false
                                revealAppleMusicChrome()
                            }
                        } else {
                            onToggleQueue()
                        }
                    }) {
                        AppleQueueIcon(
                            color = pagePalette.onBackground.copy(alpha = if (appleMusicShowQueue) 1f else 0.90f),
                            active = appleMusicShowQueue,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                    PlayerQueueSheet(
                        show = queueExpanded,
                        playlist = playlist,
                        currentSongKey = song?.playlistIdentityKey(),
                        currentQueueIndexHint = currentQueueIndexHint,
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
        fun AppleMusicCoverPage(
            modifier: Modifier = Modifier.fillMaxSize(),
            coverModifier: Modifier = Modifier,
            coverScale: Float = 1f,
            forceNonImmersive: Boolean = false
        ) {
            val hasDynamicCover = (musicVideoVisible && displayedMusicVideo != null) ||
                (!musicVideoVisible && displayedDynamicCover != null)
            // Keep immersive chrome for dynamic covers too 鈥?previously !hasDynamicCover forced
            // the card layout and produced a hard horizontal edge on 1:1 video covers.
            val isAppleMusicImmersive = immersiveAlbumCover && !forceNonImmersive
            val activeDynamicAspect = when {
                musicVideoVisible -> displayedMusicVideo?.aspectRatio
                else -> displayedDynamicCover?.aspectRatio
            }
            // Tall/portrait matched covers (aspect < ~0.95) use a taller soft-melt column like
            // coneplayer / Apple Music dynamic covers; square 1:1 stays aspectRatio(1f).
            val tallDynamicImmersive = hasDynamicCover &&
                (activeDynamicAspect?.let { it > 0f && it < 0.95f } == true)
            val useSquareImmersiveSlot = appleMusicImmersiveSquareCover && !tallDynamicImmersive
            Column(
                modifier = modifier,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isAppleMusicImmersive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(
                                when {
                                    tallDynamicImmersive -> Modifier.weight(0.92f)
                                    useSquareImmersiveSlot -> Modifier.aspectRatio(1f)
                                    else -> Modifier.weight(0.92f)
                                }
                            )
                    ) {
                        StyledPlayerArtwork(
                            cornerRadius = 0.dp,
                            contentScale = ContentScale.Crop,
                            showOverlayBadges = false,
                            modifier = coverModifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    compositingStrategy = CompositingStrategy.Offscreen
                                }
                                .drawWithContent {
                                    drawContent()
                                    // Soft bottom melt into the shared flow canvas 鈥?same curve
                                    // for static and 1:1 / tall dynamic video covers.
                                    val fadeStart = if (tallDynamicImmersive) 0.62f else 0.70f
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.00f to Color.White,
                                                fadeStart to Color.White,
                                                0.88f to Color.White.copy(alpha = 0.55f),
                                                1.00f to Color.Transparent
                                            )
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                        )
                    }
                    Spacer(
                        modifier = when {
                            tallDynamicImmersive -> Modifier.weight(0.08f)
                            useSquareImmersiveSlot -> Modifier.weight(1f)
                            else -> Modifier.weight(0.08f)
                        }
                    )
                } else {
                    if (shouldReserveStatusBar) {
                        Spacer(modifier = Modifier.windowInsetsTopHeight(WindowInsets.statusBarsIgnoringVisibility))
                    }
                    Spacer(modifier = Modifier.height(if (compactWindow) 8.dp else 16.dp))
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val baseCoverSize = minOf(maxWidth, maxHeight)
                        val currentCoverSize = baseCoverSize * coverScale
                        StyledPlayerArtwork(
                            cornerRadius = playerAlbumCoverCornerRadius.dp,
                            modifier = Modifier
                                .size(currentCoverSize)
                                .then(coverModifier)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Top
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
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
                            onTitleLongClick = onSongInfo,
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
                            useAppleIcons = appleMusicUseAppleFavorite,
                            onClick = onToggleFavorite
                        )
                        PlayerHeaderAction(
                            kind = PlayerHeaderActionKind.More,
                            useAppleIcons = appleMusicUseAppleFavorite,
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
                        isAppleMusic = true,
                        palette = pagePalette,
                        allowTapSeek = playerTapSeekEnabled,
                        showTotalDuration = playerShowTotalDuration,
                        onSeek = onSeek,
                        fontFamily = fontFamily,
                        onInfoLongPress = onMusicVideoInfoLongPress
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LandscapeTransportControls(
                        isPlaying = visualIsPlaying,
                        shuffleEnabled = shuffleEnabled,
                        repeatMode = repeatMode,
                        palette = pagePalette,
                        onCyclePlaybackMode = onCyclePlaybackMode,
                        onPrevious = onPrevious,
                        onPlayPause = syncMusicVideoPlayPause,
                        onNext = onNext,
                        controlHeight = if (compactWindow) 68.dp else 88.dp,
                        sideIconSize = if (compactWindow) 30.dp else 36.dp,
                        playButtonSize = if (compactWindow) 58.dp else 68.dp,
                        playIconSize = if (compactWindow) 34.dp else 40.dp,
                        useAppleMusicIcons = true
                    )
                    Spacer(modifier = Modifier.height(if (compactWindow) 4.dp else 8.dp))
                    AppleMusicFooterActions(height = if (compactWindow) 68.dp else 88.dp)
                    PlayerBottomClearance(reserveNavigation = shouldReserveNavigationBar)
                }
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
                    coverModifier = coverModifier,
                    artworkPainter = stableArtworkPainter,
                    useAppleIcons = appleMusicUseAppleFavorite
                )
                BoxWithConstraints(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    var anchoredFocusOffset by remember { mutableStateOf<androidx.compose.ui.unit.Dp?>(null) }
                    LaunchedEffect(maxHeight, appleMusicChromeVisible) {
                        if (appleMusicChromeVisible || anchoredFocusOffset == null) {
                            anchoredFocusOffset = maxHeight * 0.22f
                        }
                    }
                    val effectiveFocusOffset = if (appleMusicChromeVisible) {
                        maxHeight * 0.22f
                    } else {
                        anchoredFocusOffset ?: (maxHeight * 0.22f)
                    }

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
                            onLyricLineClick(line)
                        },
                        onLineDoubleClick = { /* lyrics surface: do not reveal chrome */ },
                        onLineLongClick = onLyricLineLongClick,
                        topContentPadding = 16.dp,
                        bottomContentPadding = if (compactWindow) 56.dp else 72.dp,
                        lineSpacing = if (compactWindow) 14.dp else 20.dp,
                        focusOffsetRatio = 0.22f,
                        focusOffsetDp = effectiveFocusOffset,
                        modifier = Modifier.fillMaxSize()
                    )
                    // Bottom ~1/4: tap reveals chrome while hidden (upper area stays lyrics-only).
                    if (appleMusicShowLyrics && !appleMusicChromeVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .fillMaxHeight(1f / 4f)
                                .pointerInput(Unit) {
                                    detectTapGestures {
                                        revealAppleMusicChrome()
                                    }
                                }
                        )
                    }
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
                            isAppleMusic = true,
                            palette = pagePalette,
                            allowTapSeek = playerTapSeekEnabled,
                            showTotalDuration = playerShowTotalDuration,
                            onSeek = {
                                revealAppleMusicChrome()
                                onSeek(it)
                            },
                            fontFamily = fontFamily,
                            onInfoLongPress = onMusicVideoInfoLongPress
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        LandscapeTransportControls(
                            isPlaying = visualIsPlaying,
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
                                syncMusicVideoPlayPause()
                            },
                            onNext = {
                                revealAppleMusicChrome()
                                onNext()
                            },
                            controlHeight = if (compactWindow) 68.dp else 88.dp,
                            sideIconSize = if (compactWindow) 30.dp else 36.dp,
                            playButtonSize = if (compactWindow) 58.dp else 68.dp,
                            playIconSize = if (compactWindow) 34.dp else 40.dp,
                            useAppleMusicIcons = true
                        )
                        Spacer(modifier = Modifier.height(if (compactWindow) 4.dp else 8.dp))
                        AppleMusicFooterActions(height = if (compactWindow) 68.dp else 88.dp)
                    }
                }
                PlayerBottomClearance(reserveNavigation = shouldReserveNavigationBar)
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
                onFontScale = onLyricFontScale,
                onSecondaryFontScale = onLyricSecondaryFontScale,
                onPrimaryTextSize = onLyricPrimaryTextSize,
                onSecondaryTextSize = onLyricSecondaryTextSize,
                modifier = Modifier.fillMaxWidth()
            )
            }
        }

        @Composable
        fun AppleMusicQueueSessionPageWrapper(coverModifier: Modifier = Modifier) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AppleMusicQueueSessionPage(
                    song = song,
                    embeddedCover = embeddedCover,
                    playlist = playlist,
                    currentQueueIndexHint = currentQueueIndexHint,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    isFavorite = isFavorite,
                    pagePalette = pagePalette,
                    fontFamily = fontFamily,
                    compactWindow = compactWindow,
                    useAppleIcons = appleMusicUseAppleFavorite,
                    coverModifier = coverModifier,
                    artworkPainter = stableArtworkPainter,
                    annotation = annotation,
                    onArtist = onArtist,
                    onToggleFavorite = onToggleFavorite,
                    onSongInfo = onToggleMenu,
                    onClearQueue = onClearQueue,
                    onToggleRepeat = { playerViewModel.toggleRepeat() },
                    onToggleShuffle = { playerViewModel.toggleShuffle() },
                    onSongClick = onQueueSongClick,
                    onMoveSong = onMoveQueueSong,
                    onRemoveSong = onRemoveQueueSong,
                    onDismissQueue = {
                        appleMusicShowQueue = false
                        revealAppleMusicChrome()
                    },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerProgressBlock(
                        currentPosition = currentPosition,
                        duration = duration,
                        song = song,
                        audioInfo = audioInfo,
                        bluetoothDeviceName = bluetoothDeviceName,
                        playbackModeLabel = if (musicVideoVisible) "MV" else null,
                        isAppleMusic = true,
                        palette = pagePalette,
                        allowTapSeek = playerTapSeekEnabled,
                        showTotalDuration = playerShowTotalDuration,
                        onSeek = {
                            revealAppleMusicChrome()
                            onSeek(it)
                        },
                        fontFamily = fontFamily,
                        onInfoLongPress = onMusicVideoInfoLongPress
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LandscapeTransportControls(
                        isPlaying = visualIsPlaying,
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
                            syncMusicVideoPlayPause()
                        },
                        onNext = {
                            revealAppleMusicChrome()
                            onNext()
                        },
                        controlHeight = if (compactWindow) 68.dp else 88.dp,
                        sideIconSize = if (compactWindow) 30.dp else 36.dp,
                        playButtonSize = if (compactWindow) 58.dp else 68.dp,
                        playIconSize = if (compactWindow) 34.dp else 40.dp,
                        useAppleMusicIcons = true
                    )
                    Spacer(modifier = Modifier.height(if (compactWindow) 4.dp else 8.dp))
                    AppleMusicFooterActions(height = if (compactWindow) 68.dp else 88.dp)
                }
                PlayerBottomClearance(reserveNavigation = shouldReserveNavigationBar)
            }
        }

        @OptIn(ExperimentalSharedTransitionApi::class)
        @Composable
        fun AppleMusicPlayerPage() {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize()) {
                val sharedCoverState = rememberSharedContentState(key = "appleMusicCover")
                val appleMusicCoverScale by animateFloatAsState(
                    targetValue = if (visualIsPlaying) 1.0f else 0.82f,
                    animationSpec = spring(
                        dampingRatio = 0.8f,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "AppleMusicCoverScale"
                )
                val targetSession = when {
                    appleMusicShowQueue -> AppleMusicSessionPage.Queue
                    appleMusicShowLyrics -> AppleMusicSessionPage.Lyrics
                    else -> AppleMusicSessionPage.Cover
                }
                AnimatedContent(
                    targetState = targetSession,
                    transitionSpec = {
                        if (isLargeScreenDevice) {
                            // Keep the tablet artwork slot opaque while its shared bounds morph;
                            // otherwise the cover flashes between the left column and lyrics.
                            fadeIn(
                                animationSpec = tween(
                                    durationMillis = PlayerMotion.CoverMorphDurationMs,
                                    easing = PlayerMotion.CoverMorphEasing
                                ),
                                initialAlpha = 1f
                            ) togetherWith fadeOut(
                                animationSpec = tween(
                                    durationMillis = PlayerMotion.CoverMorphDurationMs / 2,
                                    easing = PlayerMotion.CoverMorphEasing
                                ),
                                targetAlpha = 1f
                            )
                        } else {
                            // Restore the phone transition. The fully opaque outgoing cover page
                            // briefly exposed its title/actions over the lyric page (#635).
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
                        }
                    },
                    label = "AppleMusicSession"
                ) { session ->
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
                    when (session) {
                        AppleMusicSessionPage.Lyrics -> {
                            AppleMusicLyricsSessionPage(coverModifier = coverModifier)
                        }
                        AppleMusicSessionPage.Queue -> {
                            AppleMusicQueueSessionPageWrapper(coverModifier = coverModifier)
                        }
                        AppleMusicSessionPage.Cover -> {
                            AppleMusicCoverPage(
                                coverModifier = coverModifier,
                                coverScale = appleMusicCoverScale
                            )
                        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(artworkHeight)
                ) {
                    StyledPlayerArtwork(
                        cornerRadius = 0.dp,
                        contentScale = ContentScale.Crop,
                        showOverlayBadges = false,
                        swipeModifier = skipCoverSwipeModifier,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .drawWithContent {
                                drawContent()
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colorStops = arrayOf(
                                            0.00f to Color.White,
                                            0.78f to Color.White,
                                            1.00f to Color.Transparent
                                        )
                                    ),
                                    blendMode = BlendMode.DstIn
                                )
                            }
                    )
                }
                CompositionLocalProvider(LocalPlayerContentColor provides pagePalette.onBackground) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 24.dp, end = 16.dp, top = 10.dp, bottom = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerSongMetaText(
                            song = song,
                            annotation = annotation,
                            titleFontSize = 22.sp,
                            artistFontSize = 15.sp,
                            artistAlpha = 0.72f,
                            showArtistWithAnnotation = true,
                            contentColor = pagePalette.onBackground,
                            fontFamily = fontFamily,
                             onArtistClick = onArtist,
                             onTitleLongClick = onSongInfo,
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
                                .background(pagePalette.onBackground.copy(alpha = 0.20f))
                                .playerNoIndicationClick(syncMusicVideoPlayPause),
                            contentAlignment = Alignment.Center
                        ) {
                            CenteredPlayPauseGlyph(
                                isPlaying = visualIsPlaying,
                                tint = pagePalette.onBackground,
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
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .then(immersiveLyricSwipeModifier)
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
                        onLineDoubleClick = syncMusicVideoPlayPause,
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
                PlayerBottomClearance(reserveNavigation = shouldReserveNavigationBar)
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
                    extraTopPadding: androidx.compose.ui.unit.Dp = 12.dp,
                    showCover: Boolean = true,
                    applySystemInsets: Boolean = true
                ) {
                    Column(
                        modifier = modifier
                            .then(
                                if (applySystemInsets) {
                                    Modifier
                                        .windowInsetsPadding(WindowInsets.statusBars)
                                        .windowInsetsPadding(WindowInsets.navigationBars)
                                } else {
                                    Modifier
                                }
                            )
                            .padding(
                                start = if (showCover) 20.dp else 8.dp,
                                end = 20.dp,
                                top = extraTopPadding,
                                bottom = 12.dp
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (showCover) {
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
                        }
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
                                 onTitleLongClick = onSongInfo,
                                titleMarqueeEnabled = true,
                                artistMarqueeEnabled = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .widthIn(min = 0.dp)
                            )
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.Favorite,
                                selected = isFavorite,
                                useAppleIcons = appleMusicUseAppleFavorite,
                                onClick = onToggleFavorite
                            )
                            PlayerHeaderAction(
                                kind = PlayerHeaderActionKind.More,
                                useAppleIcons = appleMusicUseAppleFavorite,
                                onClick = onToggleMenu
                            )
                        }
                        if (!showCover) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        PlayerProgressBlock(
                            currentPosition = currentPosition,
                            duration = duration,
                            song = song,
                            audioInfo = audioInfo,
                            bluetoothDeviceName = bluetoothDeviceName,
                            playbackModeLabel = if (musicVideoVisible) "MV" else null,
                            isAppleMusic = true,
                            palette = pagePalette,
                            allowTapSeek = playerTapSeekEnabled,
                            showTotalDuration = playerShowTotalDuration,
                            onSeek = onSeek,
                            fontFamily = fontFamily,
                            onInfoLongPress = onMusicVideoInfoLongPress
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        LandscapeTransportControls(
                            isPlaying = visualIsPlaying,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            palette = pagePalette,
                            onCyclePlaybackMode = onCyclePlaybackMode,
                            onPrevious = onPrevious,
                            onPlayPause = syncMusicVideoPlayPause,
                            onNext = onNext,
                            controlHeight = 78.dp,
                            sideIconSize = 36.dp,
                            playButtonSize = 68.dp,
                            playIconSize = 42.dp,
                            useAppleMusicIcons = true
                        )
                        if (!showCover) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        AppleMusicFooterActions(height = 72.dp)
                    }
                }
                if (appleMusicShowQueue) {
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
                            AppleMusicQueueSessionPage(
                                song = song,
                                embeddedCover = embeddedCover,
                                playlist = playlist,
                                currentQueueIndexHint = currentQueueIndexHint,
                                shuffleEnabled = shuffleEnabled,
                                repeatMode = repeatMode,
                                isFavorite = isFavorite,
                                pagePalette = pagePalette,
                                fontFamily = fontFamily,
                                compactWindow = false,
                                useAppleIcons = appleMusicUseAppleFavorite,
                                artworkPainter = stableArtworkPainter,
                                annotation = annotation,
                                onArtist = onArtist,
                                onToggleFavorite = onToggleFavorite,
                                onSongInfo = onToggleMenu,
                                onClearQueue = onClearQueue,
                                onToggleRepeat = { playerViewModel.toggleRepeat() },
                                onToggleShuffle = { playerViewModel.toggleShuffle() },
                                onSongClick = onQueueSongClick,
                                onMoveSong = onMoveQueueSong,
                                onRemoveSong = onRemoveQueueSong,
                                onDismissQueue = {
                                    appleMusicShowQueue = false
                                    revealAppleMusicChrome()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                } else if (appleMusicShowLyrics) {
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
                                    onLyricLineClick(line)
                                },
                                onLineDoubleClick = { /* lyrics surface: do not reveal chrome */ },
                                onLineLongClick = onLyricLineLongClick,
                                topContentPadding = 8.dp,
                                bottomContentPadding = 72.dp,
                                lineSpacing = 18.dp,
                                focusOffsetRatio = 0.22f,
                                modifier = Modifier.fillMaxSize()
                            )
                            // Bottom ~1/4: tap reveals chrome while hidden (upper area stays lyrics-only).
                            if (appleMusicShowLyrics && !appleMusicChromeVisible) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .fillMaxHeight(1f / 4f)
                                        .pointerInput(Unit) {
                                            detectTapGestures {
                                                revealAppleMusicChrome()
                                            }
                                        }
                                )
                            }
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
                } else if (isLargeScreenDevice) {
                    // The no-lyrics tablet state follows the portrait Apple Music composition:
                    // artwork, metadata and transport controls stay in one centered vertical
                    // rhythm. The split landscape treatment is reserved for the lyrics session.
                    BoxWithConstraints(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        // Match the left column of the lyrics layout (40% of the usable tablet
                        // width). The landscape wallpaper remains behind it, while both sides
                        // stay intentionally empty instead of stretching the player controls.
                        val lyricsLeftColumnWidth =
                            ((maxWidth - 40.dp).coerceAtLeast(0.dp) * 0.40f)
                        val centeredContentWidth = minOf(
                            lyricsLeftColumnWidth,
                            maxHeight * 0.78f
                        )
                        AppleMusicCoverPage(
                            modifier = Modifier
                                .width(centeredContentWidth)
                                .fillMaxHeight(),
                            forceNonImmersive = true
                        )
                    }
                } else {
                    // This is the pre-tablet phone-landscape layout. Keep the artwork on the
                    // left and the compact metadata/transport column on the right; the centered
                    // narrow column is intentionally tablet-only.
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(start = 24.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .weight(0.5f)
                                .fillMaxHeight()
                                .padding(end = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val coverSize = minOf(maxWidth, maxHeight) * 0.90f
                            StyledPlayerArtwork(
                                cornerRadius = 18.dp,
                                modifier = Modifier.size(coverSize)
                            )
                        }
                        AppleMusicWideNowPlayingColumn(
                            modifier = Modifier
                                .fillMaxHeight()
                                .weight(0.5f),
                            extraTopPadding = 8.dp,
                            showCover = false,
                            applySystemInsets = false
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
                isPlaying = visualIsPlaying,
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
                playerTitlePosition = playerTitlePosition,
                coverSwipeEnabled = coverSwipeEnabled,
                previousSongTitle = previousSong?.title,
                nextSongTitle = nextSong?.title,
                coverLongPressPreviewEnabled = coverLongPressPreviewEnabled &&
                    resolvedStaticCoverPreviewModel != null,
                queueExpanded = queueExpanded,
                playlist = playlist,
                currentQueueIndexHint = currentQueueIndexHint,
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
                onPlayPause = syncMusicVideoPlayPause,
                onNext = onNext,
                onQueueSongClick = onQueueSongClick,
                onRemoveQueueSong = onRemoveQueueSong,
                onMoveQueueSong = onMoveQueueSong,
                onRandomizeQueue = playerViewModel::randomizePlaylistOrder,
                onAddQueueToPlaylist = onAddQueueToPlaylist,
                onClearQueue = onClearQueue,
                 onLineClick = onShowLyrics,
                 onArtist = onArtist,
                 onSongInfo = onSongInfo,
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
            // A static immersive cover is always a screen-width square. Metadata annotations
            // belong to the section below and must never squeeze the artwork into a shorter Fit
            // container, which exposes the black backing at both sides of a square cover.
            val immersiveCoverHeight = maxWidth
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
                            .height(immersiveCoverHeight)
                            .graphicsLayer {
                                shape = immersiveCoverShape
                                clip = true
                                // The artwork fades into the one full-screen flow canvas behind
                                // both the cover and lyric pages. Offscreen compositing is required
                                // for the destination-in mask to affect only this artwork layer.
                                compositingStrategy = CompositingStrategy.Offscreen
                            }
                            .clip(immersiveCoverShape)
                            .then(
                                // Soft bottom fade into the shared flow canvas 閳?same for static and
                                // dynamic covers so immersive mode does not hard-cut video artwork.
                                Modifier.drawWithContent {
                                    drawContent()
                                    drawRect(
                                        brush = Brush.verticalGradient(
                                            colorStops = arrayOf(
                                                0.00f to Color.White,
                                                0.70f to Color.White,
                                                0.88f to Color.White.copy(alpha = 0.62f),
                                                1.00f to Color.Transparent
                                            )
                                        ),
                                        blendMode = BlendMode.DstIn
                                    )
                                }
                            )
                            .then(
                                when {
                                    musicVideoVisible && displayedMusicVideo != null &&
                                        musicVideoLongPressImmersiveLyricsEnabled -> {
                                        Modifier.combinedClickable(
                                            onClick = {},
                                            onLongClick = onOpenMusicVideoLandscape
                                        )
                                    }
                                    coverLongPressPreviewEnabled && resolvedStaticCoverPreviewModel != null -> {
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
                                    }
                                    else -> Modifier
                                }
                            )
                            .then(coverSwipeModifier),
                        contentAlignment = Alignment.Center
                    ) {
                        // Keep MV silent and on the audio clock while its surface is hidden.
                        if (videoPlaybackActive && musicVideoVisible) displayedMusicVideo?.let { source ->
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
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            )
                        }
                        if (videoPlaybackActive && !musicVideoVisible && displayedDynamicCover != null) {
                            DynamicCoverVideo(
                                source = displayedDynamicCover,
                                isPlaying = isPlaying && videoPlaybackActive,
                                onPlaybackError = { onDynamicCoverFailed(displayedDynamicCover.failureKey) },
                                modifier = Modifier.fillMaxSize(),
                                cornerRadiusDp = 0f,
                                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                            )
                        }
                        val hideStaticArtworkBehindDynamic = (musicVideoVisible && displayedMusicVideo != null) ||
                            (!musicVideoVisible && displayedDynamicCover != null)
                        if (!hideStaticArtworkBehindDynamic) {
                            FullBleedCover(
                                song = song,
                                embeddedCover = embeddedCover,
                                coverModel = resolvedStaticCoverPreviewModel,
                                cornerRadius = immersiveCoverCornerRadius,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            // The root PlayerScreen owns one full-screen flow renderer. Keeping
                            // this surface transparent makes the mini-lyric area the exact lower
                            // crop of the canvas that remains visible on the immersive lyric page.
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val useFlexibleMiniLyricsViewport = !compactWindow
                        val hasMiniLyricsViewport = effectiveMiniLyricLine != null ||
                            (lyrics.isEmpty() && !lyricsLoading)
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
                                 onTitleLongClick = onSongInfo,
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
                                compact = compactWindow,
                                legacyWindow = true,
                                contentColor = pagePalette.onBackground,
                                wordLiftEnabled = appleMusicWordLiftEnabled,
                                onLineClick = { onShowLyrics() },
                                onLineDoubleClick = syncMusicVideoPlayPause,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(immersiveLyricSwipeModifier)
                                    .then(
                                        if (useFlexibleMiniLyricsViewport) {
                                            // Consume the former spacer above the progress block.
                                            // The fixed progress/transport/footer area below is
                                            // therefore unchanged with or without visualization.
                                            Modifier.weight(1f)
                                        } else {
                                            Modifier.height(
                                                if (compactWindow) {
                                                    miniLyricsCompactHeight(
                                                        effectiveMiniLyricLine,
                                                        showTranslation,
                                                        showPronunciation
                                                    )
                                                } else {
                                                    miniLyricsPreviewHeight(
                                                        effectiveMiniLyricLine,
                                                        showTranslation,
                                                        showPronunciation
                                                    )
                                                }
                                            )
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
                                    .then(immersiveLyricSwipeModifier)
                                    .then(
                                        if (useFlexibleMiniLyricsViewport) {
                                            Modifier.weight(1f)
                                        } else {
                                            Modifier.height(if (compactWindow) 40.dp else 150.dp)
                                        }
                                    )
                            )
                        }

                        if (!hasMiniLyricsViewport || !useFlexibleMiniLyricsViewport) {
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
                            fontFamily = fontFamily,
                            onInfoLongPress = onMusicVideoInfoLongPress
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PlayerTransportControls(
                            isPlaying = visualIsPlaying,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            palette = pagePalette,
                            queueExpanded = queueExpanded,
                            playlist = playlist,
                            currentQueueIndexHint = currentQueueIndexHint,
                            favoriteSongKeys = favoriteSongKeys,
                            loadSongRating = loadSongRating,
                            ratingRevision = ratingRevision,
                            currentSongKey = song?.playlistIdentityKey(),
                            queueLocked = queueLocked,
                            onCyclePlaybackMode = onCyclePlaybackMode,
                            onToggleQueueLock = playerViewModel::toggleQueueLock,
                            onPrevious = onPrevious,
                            onPlayPause = syncMusicVideoPlayPause,
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
                        PlayerBottomClearance(reserveNavigation = shouldReserveNavigationBar)
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (shouldReserveStatusBar) {
                                    Modifier.windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility)
                                } else {
                                    Modifier
                                }
                            )
                            .padding(horizontal = 28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Spacer(modifier = Modifier.height(22.dp))
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                        if (titleAboveCover) {
                            PlayerCoverTitleRow(
                                song = song,
                                annotation = annotation,
                                palette = pagePalette,
                                fontFamily = fontFamily,
                                isFavorite = isFavorite,
                                onArtist = onArtist,
                                onToggleFavorite = onToggleFavorite,
                                onSongInfo = onSongInfo,
                                modifier = Modifier
                                    .width(nonImmersiveCoverSize)
                                    .align(Alignment.CenterHorizontally)
                            )
                            Spacer(modifier = Modifier.height(14.dp))
                        }
                        // The configurable radii apply only to the default Halcyon, non-
                        // immersive cover page. Alternate layouts keep their authored shapes.
                        val albumCoverCornerRadius = playerAlbumCoverCornerRadius.dp
                        val musicVideoCornerRadius = playerMusicVideoCornerRadius.dp
                        // The parent clip must follow the visible surface as well; otherwise an
                        // album radius of 0 would silently cap a separately configured MV radius
                        // (and vice versa) before DynamicCoverVideo gets a chance to clip itself.
                        val coverShape = RoundedCornerShape(
                            if (musicVideoVisible && displayedMusicVideo != null) {
                                musicVideoCornerRadius
                            } else {
                                albumCoverCornerRadius
                            }
                        )
                        Box(
                            modifier = Modifier
                                .size(nonImmersiveCoverSize)
                                .graphicsLayer {
                                    shape = coverShape
                                    clip = true
                                }
                                .clip(coverShape)
                                .then(
                                    when {
                                        musicVideoVisible && displayedMusicVideo != null &&
                                            musicVideoLongPressImmersiveLyricsEnabled -> {
                                            Modifier.combinedClickable(
                                                onClick = {},
                                                onLongClick = onOpenMusicVideoLandscape
                                            )
                                        }
                                        coverLongPressPreviewEnabled && resolvedStaticCoverPreviewModel != null -> {
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
                                        }
                                        else -> Modifier
                                    }
                                )
                                .then(coverSwipeModifier),
                            contentAlignment = Alignment.Center
                        ) {
                            // Keep MV silent and synchronized behind the current cover.
                            if (videoPlaybackActive && musicVideoVisible) displayedMusicVideo?.let { source ->
                                DynamicCoverVideo(
                                    source = source,
                                    isPlaying = isPlaying && videoPlaybackActive,
                                    syncPositionMs = currentPosition,
                                    syncDurationMs = duration,
                                    onPlaybackError = { onDynamicCoverFailed(source.failureKey) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .graphicsLayer { alpha = if (musicVideoVisible) 1f else 0.001f },
                                    cornerRadiusDp = musicVideoCornerRadius.value,
                                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_FIT
                                )
                            }
                            if (videoPlaybackActive && !musicVideoVisible && displayedDynamicCover != null) {
                                DynamicCoverVideo(
                                    source = displayedDynamicCover,
                                    isPlaying = isPlaying && videoPlaybackActive,
                                    onPlaybackError = { onDynamicCoverFailed(displayedDynamicCover.failureKey) },
                                    modifier = Modifier.fillMaxSize(),
                                    cornerRadiusDp = albumCoverCornerRadius.value,
                                    resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                )
                                if (showHiResLogo) {
                                    HiResLogoBadge(
                                        logoUri = hiResLogoUri,
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(10.dp)
                                    )
                                }
                            } else if (!musicVideoVisible || displayedMusicVideo == null) {
                                AlbumArtView(
                                    song = song,
                                    embeddedCover = embeddedCover,
                                    coverModel = resolvedStaticCoverPreviewModel,
                                    cornerRadius = albumCoverCornerRadius,
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
                            if (displayedMusicVideo != null) {
                                if (musicVideoVisible && musicVideoFullscreenButtonEnabled) {
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
                                        fontFamily = fontFamily,
                                        color = pagePalette.onBackground.copy(alpha = 0.94f)
                                    )
                                }
                            }
                        }
                        val hasMiniLyricBlock = effectiveMiniLyricLine != null ||
                            (lyrics.isEmpty() && !lyricsLoading)
                        // Extra room stays above the title/preview pair so the 8.dp lyric
                        // margins stay equal. The footer below this column is measured first,
                        // so the play button keeps the same nav-inset + 8.dp clearance as
                        // immersive instead of floating up or getting clipped.
                        if (hasMiniLyricBlock) {
                            Spacer(modifier = Modifier.weight(1f))
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
                                onSongInfo = onSongInfo,
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
                                compact = compactNonImmersiveLyrics,
                                // Keep non-immersive previews on the same retained LazyColumn
                                // window as immersive mode. The bounded neighbor list changes its
                                // keys on every line and therefore loses the elastic scroll.
                                legacyWindow = true,
                                contentColor = pagePalette.onBackground,
                                wordLiftEnabled = appleMusicWordLiftEnabled,
                                onLineClick = { onShowLyrics() },
                                onLineDoubleClick = syncMusicVideoPlayPause,
                                modifier = Modifier
                                    .width(nonImmersiveCoverSize)
                                    .align(Alignment.CenterHorizontally)
                                    .height(
                                        if (compactNonImmersiveLyrics) {
                                            miniLyricsCompactHeight(
                                                effectiveMiniLyricLine,
                                                showTranslation,
                                                showPronunciation
                                            )
                                        } else {
                                            miniLyricsPreviewHeight(
                                                effectiveMiniLyricLine,
                                                showTranslation,
                                                showPronunciation,
                                                // Keep the non-immersive preview height from 1.2.7.
                                                compact = true
                                            )
                                        }
                                    )
                            )
                            // Keep the mini-lyric viewport's outer margins symmetrical. The
                            // lyric renderer owns its internal line spacing; this spacer is only
                            // the gap from the last lyric line to the action bar.
                            Spacer(modifier = Modifier.height(8.dp))
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
                            Spacer(modifier = Modifier.height(8.dp))
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (!hasMiniLyricBlock) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                        }
                        PlayerQuickActionRow(
                            shortcutIds = nonImmersiveShortcutIds,
                            onAction = executePlayerAction,
                            onMore = onToggleMenu,
                            sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
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
                            fontFamily = fontFamily,
                            onInfoLongPress = onMusicVideoInfoLongPress
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        PlayerTransportControls(
                            isPlaying = visualIsPlaying,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            palette = pagePalette,
                            queueExpanded = queueExpanded,
                            playlist = playlist,
                            currentQueueIndexHint = currentQueueIndexHint,
                            favoriteSongKeys = favoriteSongKeys,
                            loadSongRating = loadSongRating,
                            ratingRevision = ratingRevision,
                            currentSongKey = song?.playlistIdentityKey(),
                            queueLocked = queueLocked,
                            onCyclePlaybackMode = onCyclePlaybackMode,
                            onToggleQueueLock = playerViewModel::toggleQueueLock,
                            onPrevious = onPrevious,
                            onPlayPause = syncMusicVideoPlayPause,
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
                        PlayerBottomClearance(reserveNavigation = shouldReserveNavigationBar)
                    }
                }
            }
                }
            }
        }

        PlayerCoverActionSheet(
            show = menuExpanded || localActionMenuPage != null,
            song = song,
            embeddedCover = embeddedCover,
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
            onDismiss = {
                localActionMenuPage = null
                onDismissMenu()
            },
            onAlbum = onAlbum,
            onArtist = onArtist,
            onDownload = onDownload,
            onLandscape = onLandscape,
            onSongInfo = onSongInfo,
            onAddToPlaylist = onAddToPlaylist,
            onAddToQueue = onAddToQueue,
            onPlayNext = onPlayNext,
            onShareSong = onShareSong,
            onLyricShare = onLyricShare,
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
            onLyricFontScale = onLyricFontScale,
            onLyricSecondaryFontScale = onLyricSecondaryFontScale,
            onLyricPrimaryTextSize = onLyricPrimaryTextSize,
            onLyricSecondaryTextSize = onLyricSecondaryTextSize,
            onVisualizerEnabled = onVisualizerEnabled,
            onVisualizerOpacityChange = onVisualizerOpacityChange,
            onPlayerKeepScreenOnChange = onPlayerKeepScreenOnChange,
            onCycleRemoteStreamQuality = playerViewModel::cycleRemoteStreamQuality,
            onPreviewCover = {
                (resolvedStaticCoverPreviewModel ?: resolveCoverPreviewModel(song, embeddedCover))?.let { model ->
                    previewCover = PlayerCoverPreview(
                        model = model,
                        title = song?.coverPreviewDisplayTitle().orEmpty(),
                        saveName = song?.coverPreviewSaveName().orEmpty()
                    )
                }
            },
            initialPage = localActionMenuPage ?: actionMenuInitialPage
        )
        if (showMusicVideoInfo && displayedMusicVideo != null) {
            MusicVideoInfoDialog(
                source = displayedMusicVideo,
                title = song?.title.orEmpty(),
                onDismiss = { showMusicVideoInfo = false }
            )
        }
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

internal fun shouldInterceptAppleMusicLyricsBack(
    showLyrics: Boolean,
    playerPageStyle: Int,
    preserveLyricsOnBack: Boolean
): Boolean = showLyrics &&
    !preserveLyricsOnBack &&
    com.ella.music.data.SettingsManager.normalizePlayerPageStyle(playerPageStyle) ==
    com.ella.music.data.SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC

private enum class AppleMusicSessionPage {
    Cover,
    Lyrics,
    Queue
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
internal fun PlayerCoverTitleRow(
    song: Song?,
    annotation: String,
    palette: PlayerPalette,
    fontFamily: FontFamily?,
    isFavorite: Boolean,
    onArtist: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleMenu: (() -> Unit)? = null,
    onSongInfo: (() -> Unit)? = null,
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
            onTitleLongClick = onSongInfo,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(18.dp))
        PlayerHeaderAction(
            kind = PlayerHeaderActionKind.Favorite,
            selected = isFavorite,
            onClick = onToggleFavorite
        )
        onToggleMenu?.let { onClick ->
            PlayerHeaderAction(
                kind = PlayerHeaderActionKind.More,
                onClick = onClick
            )
        }
    }
}

@Composable
private fun rememberCoverSwipeModifier(
    swipeEnabled: Boolean,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    hintColor: Color,
    previousSongTitle: String? = null,
    nextSongTitle: String? = null,
    dismissEnabled: Boolean = true
): Modifier {
    val dismissHandle = LocalPlayerCoverDismiss.current.takeIf { dismissEnabled }
    return Modifier.playerCoverGestures(
        swipeEnabled = swipeEnabled,
        onSwipePrevious = onSwipePrevious,
        onSwipeNext = onSwipeNext,
        dismissHandle = dismissHandle,
        hintColor = hintColor,
        previousSongTitle = previousSongTitle,
        nextSongTitle = nextSongTitle
    )
}