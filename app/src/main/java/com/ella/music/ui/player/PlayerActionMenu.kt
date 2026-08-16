package com.ella.music.ui.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository

@Composable
internal fun PlayerActionMenu(
    song: Song?,
    showLyricsDisplayEntry: Boolean,
    speed: Float,
    pitch: Float,
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
    lyricParserEngine: Int,
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
    onClose: () -> Unit,
    onAlbum: () -> Unit,
    onArtist: () -> Unit,
    onDownload: () -> Unit,
    onLandscape: () -> Unit,
    onSongInfo: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
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
    onLyricParserEngine: (Int) -> Unit,
    onLyricFontScale: (Float) -> Unit,
    onLyricSecondaryFontScale: (Float) -> Unit,
    onLyricPrimaryTextSize: (Float) -> Unit,
    onLyricSecondaryTextSize: (Float) -> Unit,
    onVisualizerEnabled: (Boolean) -> Unit,
    onVisualizerOpacityChange: (Int) -> Unit,
    onPlayerKeepScreenOnChange: (Boolean) -> Unit,
    onCycleRemoteStreamQuality: () -> Unit,
    onPreviewCover: () -> Unit,
    initialPage: PlayerActionSheetPage = PlayerActionSheetPage.Main,
    modifier: Modifier = Modifier
) {
    var page by remember(initialPage) { mutableStateOf(initialPage) }

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        when (page) {
            PlayerActionSheetPage.Main -> {
                PlayerActionMenuHeader(
                    song = song,
                    onArtist = onArtist,
                    onAlbum = onAlbum,
                    onPreviewCover = onPreviewCover
                )
                Spacer(modifier = Modifier.height(14.dp))
                PlayerActionShortcutRow(
                    onAddToPlaylist = onAddToPlaylist,
                    onPlayNext = onPlayNext,
                    onTimer = { page = PlayerActionSheetPage.Timer },
                    onSpeed = { page = PlayerActionSheetPage.Speed },
                    onOpenEqualizer = onOpenEqualizer,
                )
                Spacer(modifier = Modifier.height(14.dp))
                PlayerActionMenuGroup {
                    PlayerActionMenuItem(stringResource(R.string.common_add_to_queue), onAddToQueue)
                    PlayerActionMenuItem(stringResource(R.string.common_share), onShare)
                    PlayerActionMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
                    PlayerActionMenuItem(stringResource(R.string.player_song_info), onSongInfo)
                    PlayerActionMenuItem(stringResource(R.string.player_repeat_mode), onCyclePlaybackMode)
                    remoteStreamMaxBitRate?.let { bitRate ->
                        PlayerActionMenuItem(
                            stringResource(
                                R.string.player_remote_stream_quality,
                                if (bitRate == 0) stringResource(R.string.player_remote_stream_original) else "$bitRate kbps"
                            ),
                            onCycleRemoteStreamQuality
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                PlayerActionMenuGroup {
                    PlayerActionMenuItem(stringResource(R.string.player_landscape_lyrics), onLandscape)
                    if (showLyricsDisplayEntry) {
                        PlayerActionMenuItem(
                            stringResource(R.string.player_lyrics_display),
                            { page = PlayerActionSheetPage.LyricDisplay }
                        )
                    }
                    PlayerActionMenuItem(stringResource(R.string.song_more_view_spectrum), onSpectrum)
                    PlayerActionMenuItem(stringResource(R.string.song_more_set_rating), onSetRating)
                    PlayerActionMenuItem(stringResource(R.string.player_match_dynamic_cover), onMatchDynamicCover)
                    if (visualizerAvailable) {
                        PlayerActionMenuItem(stringResource(R.string.player_visualizer_settings), { page = PlayerActionSheetPage.Visualizer })
                    }
                    PlayerActionMenuItem(stringResource(R.string.player_edit_metadata), onEditMetadata)
                    PlayerActionMenuItem(stringResource(R.string.player_lyric_timing), onLyricTiming)
                    PlayerActionMenuItem(stringResource(R.string.player_match_online_lyrics), onMatchOnlineLyrics)
                    PlayerActionMenuItem(stringResource(R.string.player_lyric_offset), { page = PlayerActionSheetPage.LyricOffset })
                    if (showPlayerKeepScreenOnAction) {
                        PlayerActionMenuItem(
                            stringResource(
                                if (playerKeepScreenOn) {
                                    R.string.player_disable_playback_keep_screen_on
                                } else {
                                    R.string.player_enable_playback_keep_screen_on
                                }
                            ),
                            { onPlayerKeepScreenOnChange(!playerKeepScreenOn) }
                        )
                    }
                    if (song?.onlineSource == "kw" && song.path.startsWith("http")) {
                        PlayerActionMenuItem(stringResource(R.string.player_download_lx_song), onDownload)
                    }
                    if (song != null && !song.path.startsWith("http://", ignoreCase = true) && !song.path.startsWith("https://", ignoreCase = true)) {
                        PlayerActionMenuItem(stringResource(R.string.song_more_delete_permanently), onDeleteSong, danger = true)
                    }
                }
            }
            PlayerActionSheetPage.Timer -> {
                TimerSheetContent(
                    onBack = { page = PlayerActionSheetPage.Main },
                    sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
                    stopAfterCurrentEnabled = stopAfterCurrentEnabled,
                    sleepTimerCustomMinutes = sleepTimerCustomMinutes,
                    sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
                    onStopAfterCurrent = onStopAfterCurrent,
                    onTimer = onTimer,
                    onCustomTimerMinutes = onCustomTimerMinutes,
                    onCancelTimer = onCancelTimer
                )
            }
            PlayerActionSheetPage.Speed -> {
                SpeedPitchSheetContent(
                    speed = speed,
                    pitch = pitch,
                    onBack = { page = PlayerActionSheetPage.Main },
                    onSpeed = onSpeed,
                    onPitch = onPitch
                )
            }
            PlayerActionSheetPage.LyricOffset -> {
                LyricOffsetSheetContent(
                    offsetMs = lyricOffsetMs,
                    onBack = { page = PlayerActionSheetPage.Main },
                    onOffsetChange = onLyricOffset
                )
            }
            PlayerActionSheetPage.Visualizer -> {
                VisualizerSheetContent(
                    enabled = visualizerEnabled,
                    opacity = visualizerOpacity,
                    onBack = { page = PlayerActionSheetPage.Main },
                    onEnabledChange = onVisualizerEnabled,
                    onOpacityChange = onVisualizerOpacityChange
                )
            }
            PlayerActionSheetPage.LyricDisplay -> {
                LyricActionMenu(
                    showPronunciation = showPronunciation,
                    showTranslation = showTranslation,
                    keepScreenOn = lyricPageKeepScreenOn,
                    lyricFormatAvailability = lyricFormatAvailability,
                    preferTtmlLyrics = preferTtmlLyrics,
                    lyricSourceMode = lyricSourceMode,
                    lyricParserEngine = lyricParserEngine,
                    layoutProfile = lyricLayoutProfile,
                    fontScale = lyricFontScale,
                    secondaryFontScale = lyricSecondaryFontScale,
                    primaryTextSizeSp = lyricPrimaryTextSizeSp,
                    secondaryTextSizeSp = lyricSecondaryTextSizeSp,
                    perspectiveEffect = lyricPerspectiveEffect,
                    perspectiveYAngle = lyricPerspectiveYAngle,
                    onTogglePronunciation = onTogglePronunciation,
                    onToggleTranslation = onToggleTranslation,
                    onToggleKeepScreenOn = onToggleLyricKeepScreenOn,
                    onTogglePerspectiveEffect = onToggleLyricPerspectiveEffect,
                    onPerspectiveYAngle = onLyricPerspectiveYAngle,
                    onLyricSourceMode = onLyricSourceMode,
                    onLyricFormatPreference = onLyricFormatPreference,
                    onLyricParserEngine = onLyricParserEngine,
                    onFontScale = onLyricFontScale,
                    onSecondaryFontScale = onLyricSecondaryFontScale,
                    onPrimaryTextSize = onLyricPrimaryTextSize,
                    onSecondaryTextSize = onLyricSecondaryTextSize,
                    onStyleSettings = { page = PlayerActionSheetPage.LyricStyle },
                    showSheetHeader = true,
                    onBack = { page = PlayerActionSheetPage.Main },
                    applyScrollableContainer = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            PlayerActionSheetPage.LyricStyle -> {
                LyricStyleSettingsContent(
                    layoutProfile = lyricLayoutProfile,
                    fontScale = lyricFontScale,
                    secondaryFontScale = lyricSecondaryFontScale,
                    primaryTextSizeSp = lyricPrimaryTextSizeSp,
                    secondaryTextSizeSp = lyricSecondaryTextSizeSp,
                    perspectiveEffect = lyricPerspectiveEffect,
                    perspectiveYAngle = lyricPerspectiveYAngle,
                    onPerspectiveYAngle = onLyricPerspectiveYAngle,
                    onFontScale = onLyricFontScale,
                    onSecondaryFontScale = onLyricSecondaryFontScale,
                    onPrimaryTextSize = onLyricPrimaryTextSize,
                    onSecondaryTextSize = onLyricSecondaryTextSize,
                    onBack = { page = PlayerActionSheetPage.LyricDisplay },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

internal enum class PlayerActionSheetPage {
    Main,
    Timer,
    Speed,
    LyricOffset,
    Visualizer,
    LyricDisplay,
    LyricStyle
}
