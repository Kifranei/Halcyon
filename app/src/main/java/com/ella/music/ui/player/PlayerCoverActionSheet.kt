package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.ella.music.viewmodel.AbRepeatState
import com.ella.music.ui.components.EllaMiuixBottomSheet
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PlayerCoverActionSheet(
    show: Boolean,
    song: Song?,
    embeddedCover: Bitmap?,
    showLyricsDisplayEntry: Boolean,
    playbackSpeed: Float,
    playbackPitch: Float,
    visualizerEnabled: Boolean,
    visualizerAvailable: Boolean,
    visualizerOpacity: Int,
    lyricOffsetMs: Long,
    showPronunciation: Boolean,
    showTranslation: Boolean,
    lyricPageKeepScreenOn: Boolean,
    lyricFormatAvailability: MusicRepository.LyricFormatAvailability,
    preferTtmlLyrics: Boolean?,
    lyricSourceMode: Int,
    lyricLayoutProfile: PlayerLyricLayoutProfile,
    lyricFontScale: Float,
    lyricSecondaryFontScale: Float,
    lyricPrimaryTextSizeSp: Float,
    lyricSecondaryTextSizeSp: Float,
    lyricPerspectiveEffect: Boolean,
    lyricPerspectiveYAngle: Int,
    metadataEditorId: String,
    lyricTimingEditorId: String,
    showPlayerKeepScreenOnAction: Boolean,
    playerKeepScreenOn: Boolean,
    sleepTimerEndRealtimeMs: Long?,
    stopAfterCurrentEnabled: Boolean,
    sleepTimerCustomMinutes: Int,
    sleepTimerStopAfterCurrent: Boolean,
    remoteStreamMaxBitRate: Int?,
    onCyclePlaybackMode: () -> Unit,
    abRepeatState: AbRepeatState,
    onAbRepeat: () -> Unit,
    onDismiss: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onDownload: () -> Unit,
    onLandscape: () -> Unit,
    onSongInfo: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onShareSong: () -> Unit,
    onLyricShare: () -> Unit,
    onSetRating: () -> Unit,
    onAiInterpret: () -> Unit,
    onSpectrum: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onDeleteSong: () -> Unit,
    onEditMetadata: () -> Unit,
    onLyricTiming: () -> Unit,
    onMatchOnlineLyrics: () -> Unit,
    onMatchDynamicCover: () -> Unit,
    onStopAfterCurrent: (Boolean) -> Unit,
    onTimer: (Int) -> Unit,
    onCustomTimerMinutes: (Int) -> Unit,
    onCancelTimer: () -> Unit,
    onSpeed: (Float) -> Unit,
    onPitch: (Float) -> Unit,
    onLyricOffset: (Long) -> Unit,
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
    onVisualizerEnabled: (Boolean) -> Unit,
    onVisualizerOpacityChange: (Int) -> Unit,
    onPlayerKeepScreenOnChange: (Boolean) -> Unit,
    onCycleRemoteStreamQuality: () -> Unit,
    onPreviewCover: () -> Unit,
    initialPage: PlayerActionSheetPage
) {
    var page by remember { mutableStateOf(initialPage) }
    LaunchedEffect(show, initialPage) {
        if (show) page = initialPage
    }

    val currentTitle = when (page) {
        PlayerActionSheetPage.Main -> stringResource(R.string.player_more_actions)
        PlayerActionSheetPage.Timer -> stringResource(R.string.player_sleep_timer_title)
        PlayerActionSheetPage.Speed -> stringResource(R.string.player_speed_pitch)
        PlayerActionSheetPage.LyricOffset -> stringResource(R.string.player_lyric_offset)
        PlayerActionSheetPage.Visualizer -> stringResource(R.string.player_visualizer_settings)
        PlayerActionSheetPage.AudioOutput -> stringResource(R.string.player_audio_output_info)
        PlayerActionSheetPage.LyricDisplay -> stringResource(R.string.player_lyrics_display)
        PlayerActionSheetPage.LyricStyle -> stringResource(R.string.player_lyric_style_settings)
    }

    val startAction: @Composable (() -> Unit)? = if (page != PlayerActionSheetPage.Main) {
        {
            IconButton(
                onClick = {
                    page = if (page == PlayerActionSheetPage.LyricStyle) {
                        PlayerActionSheetPage.LyricDisplay
                    } else {
                        PlayerActionSheetPage.Main
                    }
                }
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    } else null

    val configuration = LocalConfiguration.current
    val maxSheetHeight = (configuration.screenHeightDp * 0.88f).dp

    EllaMiuixBottomSheet(
        show = show,
        enableNestedScroll = false,
        title = currentTitle,
        startAction = startAction,
        onDismissRequest = onDismiss
    ) {
        PlayerActionMenu(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight),
            page = page,
            onPageChange = { page = it },
            song = song,
            embeddedCover = embeddedCover,
            showLyricsDisplayEntry = showLyricsDisplayEntry,
            speed = playbackSpeed,
            pitch = playbackPitch,
            visualizerEnabled = visualizerEnabled,
            visualizerAvailable = visualizerAvailable,
            visualizerOpacity = visualizerOpacity,
            lyricOffsetMs = lyricOffsetMs,
            showPronunciation = showPronunciation,
            showTranslation = showTranslation,
            lyricPageKeepScreenOn = lyricPageKeepScreenOn,
            lyricFormatAvailability = lyricFormatAvailability,
            preferTtmlLyrics = preferTtmlLyrics,
            lyricSourceMode = lyricSourceMode,
            lyricLayoutProfile = lyricLayoutProfile,
            lyricFontScale = lyricFontScale,
            lyricSecondaryFontScale = lyricSecondaryFontScale,
            lyricPrimaryTextSizeSp = lyricPrimaryTextSizeSp,
            lyricSecondaryTextSizeSp = lyricSecondaryTextSizeSp,
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
            onClose = onDismiss,
            onAlbum = onAlbum,
            onArtist = onArtist,
            onDownload = onDownload,
            onLandscape = onLandscape,
            onSongInfo = onSongInfo,
            onAddToPlaylist = onAddToPlaylist,
            onAddToQueue = onAddToQueue,
            onPlayNext = onPlayNext,
            onShare = onShareSong,
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
            onCycleRemoteStreamQuality = onCycleRemoteStreamQuality,
            onPreviewCover = onPreviewCover,
            initialPage = initialPage
        )
    }
}
