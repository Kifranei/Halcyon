package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.SettingsManager

@Composable
internal fun PlayerLandscapeOverlayHost(
    context: Context,
    expanded: Boolean,
    layoutStyle: Int,
    dynamicCoverEnabled: Boolean,
    dynamicCoverCustomFolders: List<String>,
    musicVideoCustomFolders: List<String>,
    musicVideoEnabled: Boolean,
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    annotation: String,
    dynamicCoverFailedPath: String?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    audioInfo: AudioInfo?,
    palette: PlayerPalette,
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    translationFontFamily: FontFamily? = fontFamily,
    fontPath: String,
    fontWeight: FontWeight,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    showTotalDuration: Boolean,
    queueExpanded: Boolean,
    playlist: List<Song>,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float,
    coverSwipeEnabled: Boolean,
    beautifulLyricsBackground: Boolean,
    flowEffectMode: Int,
    isFavorite: Boolean,
    onDynamicCoverFailed: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onLyricLineClick: (LyricLine) -> Unit,
    onLyricLineLongClick: (LyricLine) -> Unit,
    onSeekProgress: (Float) -> Unit,
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
    onArtist: () -> Unit,
    onDismiss: () -> Unit
) {
    if (!expanded) return

    ForceLandscapePlayerBars(onDismiss = onDismiss)
    if (layoutStyle == SettingsManager.PLAYER_LANDSCAPE_STYLE_WIDE) return

    val dynamicCoverSongKey = song?.dynamicCoverResolutionKey().orEmpty()
    val useMusicVideoBackground =
        layoutStyle == SettingsManager.PLAYER_LANDSCAPE_STYLE_MUSIC_VIDEO &&
            musicVideoEnabled
    // Resolve off the main thread (file scan + media probe) so opening the landscape player
    // doesn't jank, even for songs without a dynamic cover. Clear the previous source first so
    // switching songs cannot keep the old video attached while the next source is resolving.
    val landscapeDynamicCoverSource by produceState<DynamicCoverSource?>(
        initialValue = null,
        dynamicCoverEnabled,
        musicVideoEnabled,
        useMusicVideoBackground,
        dynamicCoverCustomFolders,
        musicVideoCustomFolders,
        dynamicCoverSongKey,
        dynamicCoverFailedPath
    ) {
        val current = song
        if (current == null) {
            value = null
        } else {
            value = null
            value = withContext(Dispatchers.IO) {
                if (useMusicVideoBackground) {
                    current.musicVideoSource(
                        context,
                        customRootPaths = dynamicCoverCustomFolders,
                        musicVideoCustomFolders = musicVideoCustomFolders
                    )?.takeUnless { it.failureKey == dynamicCoverFailedPath }
                } else {
                    current.dynamicCoverSource(
                        context,
                        includeExternalFiles = dynamicCoverEnabled,
                        customRootPaths = dynamicCoverCustomFolders
                    )?.takeUnless { it.failureKey == dynamicCoverFailedPath }
                }
            }
        }
    }
    androidx.compose.runtime.LaunchedEffect(
        landscapeDynamicCoverSource?.failureKey,
        currentPosition,
        duration,
        isPlaying,
        useMusicVideoBackground
    ) {
        if (useMusicVideoBackground) {
            landscapeDynamicCoverSource?.let { source ->
                MusicVideoPlaybackBridge.syncToAudio(source, currentPosition, duration, isPlaying)
            }
        }
    }
    LandscapeCoverPlaybackOverlay(
        song = song,
        embeddedCover = embeddedCover,
        paletteBitmap = paletteBitmap,
        annotation = annotation,
        dynamicCoverSource = landscapeDynamicCoverSource,
        isPlaying = isPlaying,
        currentPosition = currentPosition,
        duration = duration,
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        audioInfo = audioInfo,
        palette = palette,
        lyrics = lyrics,
        currentLyricIndex = currentLyricIndex,
        showTranslation = showTranslation,
        showPronunciation = showPronunciation,
        fontFamily = fontFamily,
        translationFontFamily = translationFontFamily,
        fontPath = fontPath,
        fontWeight = fontWeight,
        fontScale = fontScale,
        secondaryFontScale = secondaryFontScale,
        primaryTextSizeSp = primaryTextSizeSp,
        secondaryTextSizeSp = secondaryTextSizeSp,
        showTotalDuration = showTotalDuration,
        queueExpanded = queueExpanded,
        playlist = playlist,
        audioSessionId = audioSessionId,
        visualizerEnabled = visualizerEnabled,
        visualizerOpacity = visualizerOpacity,
        coverSwipeEnabled = coverSwipeEnabled,
        flowEffectMode = flowEffectMode,
        beautifulLyricsBackground = beautifulLyricsBackground,
        hideNeighborCoversInitially =
            layoutStyle == SettingsManager.PLAYER_LANDSCAPE_STYLE_MUSIC_VIDEO,
        onDynamicCoverFailed = onDynamicCoverFailed,
        isFavorite = isFavorite,
        onToggleFavorite = onToggleFavorite,
        onToggleQueue = onToggleQueue,
        onDismissQueue = onDismissQueue,
        onLyricLineClick = onLyricLineClick,
        onLyricLineLongClick = onLyricLineLongClick,
        onSeek = onSeekProgress,
        onCyclePlaybackMode = onCyclePlaybackMode,
        onPrevious = onPrevious,
        onSwipePrevious = onSwipePrevious,
        onPlayPause = {
            // The MV uses its own silent decoder. Pause it immediately rather than waiting
            // for the audio state to propagate through the player view model. The source can
            // still be resolving when lyrics are double-tapped, so use the stable song owner.
            if (useMusicVideoBackground) {
                MusicVideoPlaybackBridge.setPlaying(dynamicCoverSongKey, !isPlaying)
            }
            onPlayPause()
        },
        onNext = onNext,
        onQueueSongClick = onQueueSongClick,
        onRemoveQueueSong = onRemoveQueueSong,
        onMoveQueueSong = onMoveQueueSong,
        onAddQueueToPlaylist = onAddQueueToPlaylist,
        onClearQueue = onClearQueue,
        onArtist = onArtist,
        onDismiss = onDismiss,
        modifier = Modifier.fillMaxSize()
    )
}
