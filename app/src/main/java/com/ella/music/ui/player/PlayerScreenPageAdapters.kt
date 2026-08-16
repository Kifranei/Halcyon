package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ella.music.R
import com.ella.music.data.NeteaseKeyInfo
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseMvUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.components.TagEditorOptionKind
import com.ella.music.ui.components.SpectrumViewerLauncher
import com.ella.music.ui.components.shareLocalSong
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
internal fun CoverPageContent(
    context: Context,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    songAnnotation: String,
    dynamicCoverFailedPath: String?,
    dynamicCoverEnabled: Boolean,
    dynamicCoverCustomFolders: List<String>,
    musicVideoCustomFolders: List<String>,
    musicVideoSyncEnabled: Boolean,
    musicVideoVisible: Boolean,
    videoPlaybackActive: Boolean,
    onMusicVideoVisibleChange: (Boolean) -> Unit,
    onOpenMusicVideoLandscape: () -> Unit,
    immersiveAlbumCover: Boolean,
    coverContentColor: Boolean,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    playerDynamicFlowEnabled: Boolean,
    hiResLogoEnabled: Boolean,
    hiResLogoUri: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    audioInfo: AudioInfo?,
    palette: PlayerPalette,
    lyricPalette: PlayerPalette,
    lyrics: List<LyricLine>,
    lyricsLoading: Boolean,
    currentLyricIndex: Int,
    miniLyricLine: LyricLine?,
    showLyricTranslation: Boolean,
    showLyricPronunciation: Boolean,
    lyricPageKeepScreenOn: Boolean,
    appleMusicLyricsWordLift: Boolean,
    lyricFormatAvailability: MusicRepository.LyricFormatAvailability,
    preferTtmlLyrics: Boolean?,
    lyricSourceMode: Int,
    lyricParserEngine: Int,
    lyricLayoutProfile: PlayerLyricLayoutProfile,
    lyricFontFamily: FontFamily?,
    lyricTranslationFontFamily: FontFamily? = lyricFontFamily,
    effectiveLyricFontPath: String,
    lyricFontWeight: FontWeight,
    lyricFontScale: Float,
    lyricSecondaryFontScale: Float,
    lyricPrimaryTextSizeSp: Float,
    lyricSecondaryTextSizeSp: Float,
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
    onMenuExpandedChange: (Boolean) -> Unit,
    queueExpanded: Boolean,
    onQueueExpandedChange: (Boolean) -> Unit,
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
    isCurrentSongFavorite: Boolean,
    audioSessionId: Int,
    audioVisualizerEnabled: Boolean,
    audioVisualizerOpacity: Float,
    audioVisualizerOpacityPercent: Int,
    lyricOffsetMs: Long,
    metadataEditorId: String,
    lyricTimingEditorId: String,
    onVisualizerEnabled: (Boolean) -> Unit,
    onVisualizerOpacityChange: (Int) -> Unit,
    onPlayerKeepScreenOnChange: (Boolean) -> Unit,
    onDynamicCoverFailedPathChange: (String) -> Unit,
    onDynamicCoverSheetSongChange: (Song?) -> Unit,
    onPlaylistPickerSongChange: (Song?) -> Unit,
    onPlaylistPickerSongsChange: (List<Song>?) -> Unit,
    onLandscapeExpandedChange: (Boolean) -> Unit,
    onSongInfoExpandedChange: (Boolean) -> Unit,
    onRatingSheetSongChange: (Song?) -> Unit,
    onAiSheetSongChange: (Song?) -> Unit,
    onTagEditorSongChange: (Song?) -> Unit,
    onTagEditorKindChange: (TagEditorOptionKind) -> Unit,
    onLyricMatchSongChange: (Song?) -> Unit,
    onOpenEqualizer: () -> Unit,
    onRequestDeleteSong: (Song) -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    openLyricSharePicker: (LyricLine) -> Unit,
    navigateToArtistOrChoose: (String) -> Unit,
    onShowLyrics: () -> Unit,
    onSwipePrevious: () -> Unit,
    drawBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    var actionMenuInitialPage by remember { mutableStateOf(PlayerActionSheetPage.Main) }
    fun openTagEditor(kind: TagEditorOptionKind) {
        val current = song
        when {
            current == null -> {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
            current.path.startsWith("http://", ignoreCase = true) ||
                current.path.startsWith("https://", ignoreCase = true) -> {
                Toast.makeText(
                    context,
                    context.getString(R.string.player_external_editor_not_supported_for_remote),
                    Toast.LENGTH_SHORT
                ).show()
            }
            else -> {
                onTagEditorKindChange(kind)
                onTagEditorSongChange(current)
                onMenuExpandedChange(false)
            }
        }
    }
    CoverPlayerPage(
        context = context,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        song = song,
        embeddedCover = embeddedCover,
        paletteBitmap = paletteBitmap,
        annotation = songAnnotation,
        dynamicCoverFailedPath = dynamicCoverFailedPath,
        dynamicCoverEnabled = dynamicCoverEnabled,
        dynamicCoverCustomFolders = dynamicCoverCustomFolders,
        musicVideoCustomFolders = musicVideoCustomFolders,
        musicVideoSyncEnabled = musicVideoSyncEnabled,
        musicVideoVisible = musicVideoVisible,
        videoPlaybackActive = videoPlaybackActive,
        onToggleMusicVideo = {
            onMusicVideoVisibleChange(!musicVideoVisible)
        },
        onOpenMusicVideoLandscape = onOpenMusicVideoLandscape,
        immersiveAlbumCover = immersiveAlbumCover,
        coverContentColor = coverContentColor,
        playerBackgroundEnabled = playerBackgroundEnabled,
        playerBackgroundUri = playerBackgroundUri,
        playerBackgroundOpacity = playerBackgroundOpacity,
        playerBackgroundDim = playerBackgroundDim,
        beautifulLyricsBackground = beautifulLyricsBackground,
        hiResLogoEnabled = hiResLogoEnabled,
        hiResLogoUri = hiResLogoUri,
        isPlaying = isPlaying,
        currentPosition = currentPosition,
        duration = duration,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        audioInfo = audioInfo,
        palette = lyricPalette,
        flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
        dynamicFlowEnabled = playerDynamicFlowEnabled,
        lyrics = lyrics,
        lyricsLoading = lyricsLoading,
        currentLyricIndex = currentLyricIndex,
        miniLyricLine = miniLyricLine,
        showTranslation = showLyricTranslation,
        showPronunciation = showLyricPronunciation,
        lyricPageKeepScreenOn = lyricPageKeepScreenOn,
        appleMusicWordLiftEnabled = appleMusicLyricsWordLift,
        lyricFormatAvailability = lyricFormatAvailability,
        preferTtmlLyrics = preferTtmlLyrics,
        lyricSourceMode = lyricSourceMode,
        lyricParserEngine = lyricParserEngine,
        lyricLayoutProfile = lyricLayoutProfile,
        fontFamily = lyricFontFamily,
        translationFontFamily = lyricTranslationFontFamily,
        fontPath = effectiveLyricFontPath,
        fontWeight = lyricFontWeight,
        fontScale = lyricFontScale,
        secondaryFontScale = lyricSecondaryFontScale,
        primaryTextSizeSp = lyricPrimaryTextSizeSp,
        secondaryTextSizeSp = lyricSecondaryTextSizeSp,
        lyricPerspectiveEffect = lyricPerspectiveEffect,
        lyricPerspectiveYAngle = lyricPerspectiveYAngle,
        lyricTextAlign = lyricTextAlign,
        playerTapSeekEnabled = playerTapSeekEnabled,
        playerShowTotalDuration = playerShowTotalDuration,
        coverSwipeEnabled = coverSwipeEnabled,
        playerTitlePosition = playerTitlePosition,
        playerPageStyle = playerPageStyle,
        showPlayerKeepScreenOnAction = showPlayerKeepScreenOnAction,
        playerKeepScreenOn = playerKeepScreenOn,
        menuExpanded = menuExpanded,
        queueExpanded = queueExpanded,
        playlist = playlist,
        favoriteSongKeys = favoriteSongKeys,
        loadSongRating = loadSongRating,
        ratingRevision = ratingRevision,
        sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
        stopAfterCurrentEnabled = stopAfterCurrentEnabled,
        sleepTimerCustomMinutes = sleepTimerCustomMinutes,
        sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
        onDynamicCoverFailed = { onDynamicCoverFailedPathChange(it) },
        onMatchDynamicCover = {
            onMenuExpandedChange(false)
            onDynamicCoverSheetSongChange(song)
        },
        onToggleMenu = {
            actionMenuInitialPage = PlayerActionSheetPage.Main
            onMenuExpandedChange(!menuExpanded)
        },
        onToggleFavorite = { playerViewModel.toggleCurrentSongFavorite() },
        onDismissMenu = { onMenuExpandedChange(false) },
        onToggleQueue = { onQueueExpandedChange(!queueExpanded) },
        onDismissQueue = { onQueueExpandedChange(false) },
        onShowLyrics = onShowLyrics,
        onLyricLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
        onLyricLineLongClick = openLyricSharePicker,
        onTogglePronunciation = {
            playerViewModel.setLyricPagePronunciation(!showLyricPronunciation)
        },
        onToggleTranslation = {
            playerViewModel.setLyricPageTranslation(!showLyricTranslation)
        },
        onToggleLyricKeepScreenOn = {
            scope.launch { settingsManager.setLyricPageKeepScreenOn(!lyricPageKeepScreenOn) }
        },
        onToggleLyricPerspectiveEffect = {
            scope.launch { settingsManager.setLyricPerspectiveEffect(!lyricPerspectiveEffect) }
        },
        onLyricPerspectiveYAngle = { angle ->
            scope.launch { settingsManager.setLyricPerspectiveYAngle(angle) }
        },
        onLyricSourceMode = { mode ->
            playerViewModel.setLyricSourceMode(mode)
        },
        onLyricFormatPreference = { preferTtml ->
            playerViewModel.setLyricFormatPreference(preferTtml)
        },
        onLyricParserEngine = { engine ->
            scope.launch { settingsManager.setLyricParserEngine(engine) }
        },
        onLyricFontScale = { scale ->
            scope.launch { settingsManager.setLyricFontScale((scale * 100f).roundToInt()) }
        },
        onLyricSecondaryFontScale = { scale ->
            scope.launch { settingsManager.setLyricSecondaryFontScale((scale * 100f).roundToInt()) }
        },
        onLyricPrimaryTextSize = { sizeSp ->
            scope.launch {
                when (lyricLayoutProfile) {
                    PlayerLyricLayoutProfile.Compact ->
                        settingsManager.setLyricCompactPrimaryTextSize(sizeSp.roundToInt())
                    PlayerLyricLayoutProfile.Wide ->
                        settingsManager.setLyricWidePrimaryTextSize(sizeSp.roundToInt())
                }
            }
        },
        onLyricSecondaryTextSize = { sizeSp ->
            scope.launch {
                when (lyricLayoutProfile) {
                    PlayerLyricLayoutProfile.Compact ->
                        settingsManager.setLyricCompactSecondaryTextSize(sizeSp.roundToInt())
                    PlayerLyricLayoutProfile.Wide ->
                        settingsManager.setLyricWideSecondaryTextSize(sizeSp.roundToInt())
                }
            }
        },
        onSeek = { fraction -> playerViewModel.seekToProgress(fraction, duration) },
        onCyclePlaybackMode = { playerViewModel.cyclePlaybackMode() },
        onPrevious = { playerViewModel.skipToPrevious() },
        onSwipePrevious = onSwipePrevious,
        onPlayPause = { playerViewModel.togglePlayPause() },
        onNext = { playerViewModel.skipToNext() },
        onQueueSongClick = { index ->
            onQueueExpandedChange(false)
            playerViewModel.playQueueIndex(index)
        },
        onRemoveQueueSong = { index ->
            playerViewModel.removeFromPlaylist(index)
        },
        onMoveQueueSong = { fromIndex, toIndex ->
            playerViewModel.movePlaylistItem(fromIndex, toIndex)
        },
        onAddQueueToPlaylist = {
            onQueueExpandedChange(false)
            onPlaylistPickerSongsChange(playlist)
        },
        onClearQueue = {
            onQueueExpandedChange(false)
            playerViewModel.clearPlaylist()
        },
        onAlbum = {
            onMenuExpandedChange(false)
            val albumId = song?.albumIdentityId() ?: 0L
            if (albumId > 0L) onNavigateToAlbum(albumId)
            else Toast.makeText(context, context.getString(R.string.player_no_album_jump), Toast.LENGTH_SHORT).show()
        },
        onArtist = {
            onMenuExpandedChange(false)
            navigateToArtistOrChoose(song?.artist.orEmpty())
        },
        onNavigateToAlbumId = onNavigateToAlbum,
        onNavigateToArtistName = onNavigateToArtist,
        onDownload = {
            onMenuExpandedChange(false)
            val current = song
            if (current != null) {
                enqueuePlayerDownload(context, current)
                Toast.makeText(context, context.getString(R.string.player_download_started), Toast.LENGTH_SHORT).show()
            }
        },
        onLandscape = {
            onMenuExpandedChange(false)
            onLandscapeExpandedChange(true)
        },
        onSongInfo = {
            onMenuExpandedChange(false)
            onSongInfoExpandedChange(true)
        },
        onAddToPlaylist = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                onPlaylistPickerSongChange(current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onShareSong = {
            val current = song
            if (current != null) shareLocalSong(context, current)
            else Toast.makeText(context, context.getString(R.string.player_no_share_song), Toast.LENGTH_SHORT).show()
        },
        onAddToQueue = {
            val current = song
            if (current != null) {
                playerViewModel.addToPlaylist(current)
                onMenuExpandedChange(false)
                Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onPlayNext = {
            val current = song
            if (current != null) {
                playerViewModel.playNext(current)
                onMenuExpandedChange(false)
                Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onSetRating = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                onRatingSheetSongChange(current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onAiInterpret = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                onAiSheetSongChange(current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onSpectrum = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                scope.launch { SpectrumViewerLauncher.openSelected(context, current) }
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onOpenEqualizer = {
            onMenuExpandedChange(false)
            onOpenEqualizer()
        },
        onDeleteSong = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                onRequestDeleteSong(current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onOpenTimer = {
            actionMenuInitialPage = PlayerActionSheetPage.Timer
            onMenuExpandedChange(true)
        },
        onOpenMetadataEditor = {
            openTagEditor(TagEditorOptionKind.Metadata)
        },
        onEditMetadata = {
            openTagEditor(TagEditorOptionKind.Metadata)
        },
        onLyricTiming = {
            openTagEditor(TagEditorOptionKind.LyricTiming)
        },
        onMatchOnlineLyrics = {
            val current = song
            if (current != null) {
                onMenuExpandedChange(false)
                onLyricMatchSongChange(current)
            } else {
                Toast.makeText(context, context.getString(R.string.player_no_song_playing), Toast.LENGTH_SHORT).show()
            }
        },
        onStopAfterCurrent = {
            scope.launch { settingsManager.setSleepTimerStopAfterCurrent(it) }
            if (sleepTimerEndRealtimeMs == null) {
                playerViewModel.setStopAfterCurrentEnabled(it)
            } else if (!it) {
                playerViewModel.setStopAfterCurrentEnabled(false)
            }
            Toast.makeText(
                context,
                if (it) context.getString(R.string.player_pause_after_current_on) else context.getString(R.string.player_pause_after_current_off),
                Toast.LENGTH_SHORT
            ).show()
        },
        onTimer = { minutes ->
            scope.launch { settingsManager.setSleepTimerCustomMinutes(minutes) }
            playerViewModel.setStopAfterCurrentEnabled(false)
            playerViewModel.startSleepTimer(
                minutes = minutes,
                stopAfterCurrentWhenExpired = sleepTimerStopAfterCurrent
            )
            Toast.makeText(context, context.getString(R.string.player_sleep_timer_minutes, minutes), Toast.LENGTH_SHORT).show()
        },
        onCustomTimerMinutes = { minutes ->
            scope.launch { settingsManager.setSleepTimerCustomMinutes(minutes) }
        },
        onCancelTimer = {
            playerViewModel.cancelSleepTimer()
            Toast.makeText(context, context.getString(R.string.player_sleep_timer_cancelled), Toast.LENGTH_SHORT).show()
        },
        onSpeed = { playerViewModel.setPlaybackSpeed(it) },
        onPitch = { playerViewModel.setPlaybackPitch(it) },
        onLyricOffset = { playerViewModel.setCurrentLyricOffsetMs(it) },
        playbackSpeed = playbackSpeed,
        playbackPitch = playbackPitch,
        isFavorite = isCurrentSongFavorite,
        audioSessionId = audioSessionId,
        visualizerEnabled = audioVisualizerEnabled,
        visualizerOpacity = audioVisualizerOpacity,
        visualizerOpacityPercent = audioVisualizerOpacityPercent,
        lyricOffsetMs = lyricOffsetMs,
        metadataEditorId = metadataEditorId,
        lyricTimingEditorId = lyricTimingEditorId,
        onVisualizerEnabled = onVisualizerEnabled,
        onVisualizerOpacityChange = onVisualizerOpacityChange,
        onPlayerKeepScreenOnChange = onPlayerKeepScreenOnChange,
        actionMenuInitialPage = actionMenuInitialPage,
        drawBackground = drawBackground,
        modifier = modifier
    )
}

@Composable
internal fun LyricsPageContent(
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    songAnnotation: String,
    lyrics: List<LyricLine>,
    lyricsLoading: Boolean,
    currentLyricIndex: Int,
    currentPosition: Long,
    showLyricTranslation: Boolean,
    showLyricPronunciation: Boolean,
    lyricPageKeepScreenOn: Boolean,
    appleMusicLyricsWordLift: Boolean,
    lyricFormatAvailability: MusicRepository.LyricFormatAvailability,
    preferTtmlLyrics: Boolean?,
    lyricSourceMode: Int,
    lyricParserEngine: Int,
    lyricLayoutProfile: PlayerLyricLayoutProfile,
    lyricFontFamily: FontFamily?,
    lyricTranslationFontFamily: FontFamily? = lyricFontFamily,
    effectiveLyricFontPath: String,
    lyricFontWeight: FontWeight,
    lyricFontScale: Float,
    lyricSecondaryFontScale: Float,
    lyricPrimaryTextSizeSp: Float,
    lyricSecondaryTextSizeSp: Float,
    lyricPerspectiveEffect: Boolean,
    lyricPerspectiveYAngle: Int,
    lyricTextAlign: Int,
    lyricPalette: PlayerPalette,
    isPlaying: Boolean,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    playerDynamicFlowEnabled: Boolean,
    isCurrentSongFavorite: Boolean,
    audioSessionId: Int,
    effectiveAudioVisualizerEnabled: Boolean,
    audioVisualizerOpacity: Float,
    playerViewModel: PlayerViewModel,
    settingsManager: SettingsManager,
    scope: CoroutineScope,
    openLyricSharePicker: (LyricLine) -> Unit,
    navigateToArtistOrChoose: (String) -> Unit,
    onDismissLyrics: () -> Unit,
    enableSwipeDismiss: Boolean,
    backEnabled: Boolean = true,
    pageVisible: Boolean = true,
    immersiveAlbumCover: Boolean,
    drawBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    val playWhenReady by playerViewModel.playWhenReady.collectAsState()
    val isActuallyPaused = !isPlaying && !playWhenReady
    LyricsPlayerPage(
        song = song,
        embeddedCover = embeddedCover,
        paletteBitmap = paletteBitmap,
        annotation = songAnnotation,
        lyrics = lyrics,
        lyricsLoading = lyricsLoading,
        currentLyricIndex = currentLyricIndex,
        currentPosition = currentPosition,
        showTranslation = showLyricTranslation,
        showPronunciation = showLyricPronunciation,
        keepScreenOn = lyricPageKeepScreenOn,
        appleMusicWordLiftEnabled = appleMusicLyricsWordLift,
        lyricFormatAvailability = lyricFormatAvailability,
        preferTtmlLyrics = preferTtmlLyrics,
        lyricSourceMode = lyricSourceMode,
        lyricParserEngine = lyricParserEngine,
        layoutProfile = lyricLayoutProfile,
        fontFamily = lyricFontFamily,
        translationFontFamily = lyricTranslationFontFamily,
        fontPath = effectiveLyricFontPath,
        fontWeight = lyricFontWeight,
        italic = false,
        fontScale = lyricFontScale,
        secondaryFontScale = lyricSecondaryFontScale,
        primaryTextSizeSp = lyricPrimaryTextSizeSp,
        secondaryTextSizeSp = lyricSecondaryTextSizeSp,
        perspectiveEffect = lyricPerspectiveEffect,
        perspectiveYAngle = lyricPerspectiveYAngle,
        lyricTextAlign = lyricTextAlign,
        palette = lyricPalette,
        flowEffectMode = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
        currentPositionMs = currentPosition,
        isPlaying = isPlaying,
        isPaused = isActuallyPaused,
        playerBackgroundEnabled = playerBackgroundEnabled,
        playerBackgroundUri = playerBackgroundUri,
        playerBackgroundOpacity = playerBackgroundOpacity,
        playerBackgroundDim = playerBackgroundDim,
        beautifulLyricsBackground = beautifulLyricsBackground,
        dynamicFlowEnabled = playerDynamicFlowEnabled,
        isFavorite = isCurrentSongFavorite,
        audioSessionId = audioSessionId,
        visualizerEnabled = effectiveAudioVisualizerEnabled,
        visualizerOpacity = audioVisualizerOpacity,
        onLineClick = { line -> playerViewModel.seekTo(line.timeMs) },
        onLineDoubleClick = { playerViewModel.togglePlayPause() },
        onLineLongClick = openLyricSharePicker,
        onDismissLyrics = onDismissLyrics,
        onTogglePronunciation = {
            playerViewModel.setLyricPagePronunciation(!showLyricPronunciation)
        },
        onToggleTranslation = {
            playerViewModel.setLyricPageTranslation(!showLyricTranslation)
        },
        onToggleKeepScreenOn = {
            scope.launch { settingsManager.setLyricPageKeepScreenOn(!lyricPageKeepScreenOn) }
        },
        onTogglePerspectiveEffect = {
            scope.launch { settingsManager.setLyricPerspectiveEffect(!lyricPerspectiveEffect) }
        },
        onPerspectiveYAngle = { angle ->
            scope.launch { settingsManager.setLyricPerspectiveYAngle(angle) }
        },
        onToggleFavorite = { playerViewModel.toggleCurrentSongFavorite() },
        onFontScale = { scale ->
            scope.launch { settingsManager.setLyricFontScale((scale * 100f).roundToInt()) }
        },
        onSecondaryFontScale = { scale ->
            scope.launch { settingsManager.setLyricSecondaryFontScale((scale * 100f).roundToInt()) }
        },
        onPrimaryTextSize = { sizeSp ->
            scope.launch {
                when (lyricLayoutProfile) {
                    PlayerLyricLayoutProfile.Compact ->
                        settingsManager.setLyricCompactPrimaryTextSize(sizeSp.roundToInt())
                    PlayerLyricLayoutProfile.Wide ->
                        settingsManager.setLyricWidePrimaryTextSize(sizeSp.roundToInt())
                }
            }
        },
        onSecondaryTextSize = { sizeSp ->
            scope.launch {
                when (lyricLayoutProfile) {
                    PlayerLyricLayoutProfile.Compact ->
                        settingsManager.setLyricCompactSecondaryTextSize(sizeSp.roundToInt())
                    PlayerLyricLayoutProfile.Wide ->
                        settingsManager.setLyricWideSecondaryTextSize(sizeSp.roundToInt())
                }
            }
        },
        onLyricSourceMode = { mode ->
            playerViewModel.setLyricSourceMode(mode)
        },
        onLyricFormatPreference = { preferTtml ->
            playerViewModel.setLyricFormatPreference(preferTtml)
        },
        onLyricParserEngine = { engine ->
            scope.launch { settingsManager.setLyricParserEngine(engine) }
        },
        onArtist = {
            navigateToArtistOrChoose(song?.artist.orEmpty())
        },
        enableSwipeDismiss = enableSwipeDismiss,
        backEnabled = backEnabled,
        pageVisible = pageVisible,
        useBlurBackground = false,
        drawBackground = drawBackground,
        modifier = modifier
    )
}

@Composable
internal fun DetailPageContent(
    context: Context,
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    tagInfo: SongTagInfo?,
    neteaseInfo: NeteaseKeyInfo?,
    librarySongs: List<Song>,
    albumArtForAlbum: (Long) -> Any?,
    artistCoverFolderUri: String,
    mainViewModel: MainViewModel,
    lyricPalette: PlayerPalette,
    currentPosition: Long,
    isPlaying: Boolean,
    beautifulLyricsBackground: Boolean,
    playerDynamicFlowEnabled: Boolean,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    immersiveAlbumCover: Boolean,
    playerBackgroundEnabled: Boolean,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToMetadataCategory: (String, String) -> Unit,
    openNetease: (String?) -> Unit,
    musicVideoEnabled: Boolean,
    musicVideoCustomFolders: List<String>,
    dynamicCoverCustomFolders: List<String>,
    onOpenMusicVideo: (DynamicCoverSource) -> Unit,
    drawBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    PlayerDetailPage(
        song = song,
        embeddedCover = embeddedCover,
        paletteBitmap = paletteBitmap,
        tagInfo = tagInfo,
        neteaseInfo = neteaseInfo,
        librarySongs = librarySongs,
        albumArtForAlbum = albumArtForAlbum,
        artistCoverFolderUri = artistCoverFolderUri,
        mainViewModel = mainViewModel,
        palette = lyricPalette,
        currentPositionMs = currentPosition,
        isPlaying = isPlaying,
        beautifulLyricsBackground = beautifulLyricsBackground,
        dynamicFlowEnabled = playerDynamicFlowEnabled,
        useBlurBackground = immersiveAlbumCover,
        playerBackgroundEnabled = playerBackgroundEnabled,
        customBackgroundUri = playerBackgroundUri.takeIf {
            !immersiveAlbumCover && playerBackgroundEnabled && playerBackgroundUri.isNotBlank()
        }.orEmpty(),
        customBackgroundOpacity = playerBackgroundOpacity,
        customBackgroundDim = playerBackgroundDim,
        drawBackground = drawBackground,
        onAlbum = {
            val albumId = song?.albumIdentityId() ?: 0L
            if (albumId > 0L) onNavigateToAlbum(albumId)
            else Toast.makeText(context, context.getString(R.string.player_no_album_jump), Toast.LENGTH_SHORT).show()
        },
        onArtist = { name -> onNavigateToArtist(name) },
        onComposer = { name -> onNavigateToMetadataCategory("composer", name) },
        onArranger = { name -> onNavigateToMetadataCategory("arranger", name) },
        onLyricist = { name -> onNavigateToMetadataCategory("lyricist", name) },
        onYear = { year -> onNavigateToMetadataCategory("year", year) },
        onGenre = { genre -> onNavigateToMetadataCategory("genre", genre) },
        onNeteaseSong = { openNetease(neteaseInfo?.musicId?.takeIf { it.isNotBlank() }?.let(::neteaseSongUrl)) },
        onNeteaseMusicVideo = {
            openNetease(neteaseInfo?.mvId?.takeIf { it.isNotBlank() }?.let(::neteaseMvUrl))
        },
        onNeteaseArtist = { id -> openNetease(neteaseArtistUrl(id)) },
        onNeteaseAlbum = { openNetease(neteaseInfo?.albumId?.takeIf { it.isNotBlank() }?.let(::neteaseAlbumUrl)) },
        musicVideoEnabled = musicVideoEnabled,
        musicVideoCustomFolders = musicVideoCustomFolders,
        dynamicCoverCustomFolders = dynamicCoverCustomFolders,
        onMusicVideo = onOpenMusicVideo,
        modifier = modifier
    )
}
