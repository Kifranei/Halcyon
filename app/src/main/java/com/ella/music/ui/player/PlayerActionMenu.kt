package com.ella.music.ui.player

import android.graphics.Bitmap
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.ActionMenuLayout
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.components.actionMenuIcon
import com.ella.music.viewmodel.AbRepeatPhase
import com.ella.music.viewmodel.AbRepeatState

@Composable
internal fun PlayerActionMenu(
    song: Song?,
    embeddedCover: Bitmap?,
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
    page: PlayerActionSheetPage? = null,
    onPageChange: ((PlayerActionSheetPage) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val savedLayout by settingsManager.playerActionMenuLayout.collectAsState(initial = "")
    val rawShortcuts by settingsManager.playerShortcutItems.collectAsState(initial = SettingsManager.DEFAULT_PLAYER_SHORTCUT_ITEMS)
    val shortcutIds = remember(rawShortcuts) {
        if (rawShortcuts.isBlank()) {
            emptyList()
        } else {
            rawShortcuts.split(',')
                .filter { it.isNotBlank() && it in ActionMenuIds.playerShortcutCatalog }
                .distinct()
                .take(SettingsManager.MAX_PLAYER_SHORTCUT_ITEMS)
        }
    }
    // Shortcut-only ids (speed/eq/timer/playlist/play-next) are not in playerDefaults.
    // Put them first so when horizontal shortcuts are cleared they land at the top of the
    // vertical list (especially important on tablet sheets with a shorter scroll viewport).
    val playerActionMenuDefaults = remember {
        (ActionMenuIds.playerShortcutDefaults +
            ActionMenuIds.playerDefaults +
            ActionMenuIds.playerShortcutCatalog +
            listOf(ActionMenuIds.REMOTE_QUALITY, PlayerExtraActionIds.LYRIC_SHARE))
            .distinct()
    }
    val visibleActions = remember(savedLayout, playerActionMenuDefaults, shortcutIds) {
        ActionMenuLayout.parse(savedLayout, playerActionMenuDefaults)
            .visibleIds(playerActionMenuDefaults)
            .filterNot { it in shortcutIds }
    }
    val lyricNonCurrentBlurPercent by settingsManager.lyricNonCurrentBlurPercent.collectAsState(initial = 70)
    var internalPage by remember(initialPage) { mutableStateOf(initialPage) }
    val currentPage = page ?: internalPage
    val setPage: (PlayerActionSheetPage) -> Unit = { target ->
        internalPage = target
        onPageChange?.invoke(target)
    }
    val pageScrollState = rememberScrollState()
    LaunchedEffect(currentPage) {
        pageScrollState.scrollTo(0)
    }
    val abRepeatLabel = when (abRepeatState.phase) {
        AbRepeatPhase.IDLE -> stringResource(R.string.player_repeat_mode)
        AbRepeatPhase.A_SET -> stringResource(
            R.string.player_ab_repeat_record_b,
            DateUtils.formatElapsedTime((abRepeatState.startMs ?: 0L) / 1000L)
        )
        AbRepeatPhase.ACTIVE -> stringResource(
            R.string.player_ab_repeat_active,
            DateUtils.formatElapsedTime((abRepeatState.startMs ?: 0L) / 1000L),
            DateUtils.formatElapsedTime((abRepeatState.endMs ?: 0L) / 1000L)
        )
    }

    Column(
        modifier = modifier
            .verticalScroll(pageScrollState)
            .navigationBarsPadding()
            .padding(vertical = 8.dp)
    ) {
        when (currentPage) {
            PlayerActionSheetPage.Main -> {
                PlayerActionMenuHeader(
                    song = song,
                    embeddedCover = embeddedCover,
                    onArtist = onArtist,
                    onAlbum = onAlbum,
                    onPreviewCover = onPreviewCover
                )
                if (shortcutIds.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(14.dp))
                    PlayerActionMenuGroup {
                        PlayerActionShortcutRow(
                            shortcutIds = shortcutIds,
                            sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
                            onActionClick = { actionId ->
                                when (actionId) {
                                    ActionMenuIds.SPEED -> setPage(PlayerActionSheetPage.Speed)
                                    ActionMenuIds.EQUALIZER -> onOpenEqualizer()
                                    ActionMenuIds.TIMER -> setPage(PlayerActionSheetPage.Timer)
                                    ActionMenuIds.ADD_TO_PLAYLIST -> onAddToPlaylist()
                                    ActionMenuIds.PLAY_NEXT -> onPlayNext()
                                    ActionMenuIds.ADD_TO_QUEUE -> onAddToQueue()
                                    ActionMenuIds.SHARE -> onShare()
                                    ActionMenuIds.AI -> onAiInterpret()
                                    ActionMenuIds.INFO -> onSongInfo()
                                    ActionMenuIds.AUDIO_OUTPUT -> setPage(PlayerActionSheetPage.AudioOutput)
                                    ActionMenuIds.CASTING -> openSystemOutputSwitcher(context)
                                    ActionMenuIds.AB_REPEAT -> onAbRepeat()
                                    ActionMenuIds.LANDSCAPE -> onLandscape()
                                    ActionMenuIds.LYRICS_DISPLAY -> if (showLyricsDisplayEntry) setPage(PlayerActionSheetPage.LyricDisplay)
                                    ActionMenuIds.SPECTRUM -> onSpectrum()
                                    ActionMenuIds.RATING -> onSetRating()
                                    ActionMenuIds.DYNAMIC_COVER -> onMatchDynamicCover()
                                    ActionMenuIds.VISUALIZER -> if (visualizerAvailable) setPage(PlayerActionSheetPage.Visualizer)
                                    ActionMenuIds.EDIT_TAGS -> onEditMetadata()
                                    ActionMenuIds.LYRIC_TIMING -> onLyricTiming()
                                    ActionMenuIds.ONLINE_LYRICS -> onMatchOnlineLyrics()
                                    ActionMenuIds.LYRIC_OFFSET -> setPage(PlayerActionSheetPage.LyricOffset)
                                    ActionMenuIds.KEEP_SCREEN_ON -> if (showPlayerKeepScreenOnAction) onPlayerKeepScreenOnChange(!playerKeepScreenOn)
                                    ActionMenuIds.DOWNLOAD -> onDownload()
                                    ActionMenuIds.DELETE -> onDeleteSong()
                                }
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                PlayerActionMenuGroup {
                    visibleActions.forEach { actionId ->
                        val icon = actionMenuIcon(actionId)
                        when (actionId) {
                            // Fallback rows for horizontal shortcuts that are turned off.
                            // Without these branches the ids stay in visibleActions but render nothing.
                            ActionMenuIds.SPEED -> PlayerActionMenuItem(
                                stringResource(R.string.player_speed_pitch),
                                { setPage(PlayerActionSheetPage.Speed) },
                                icon = icon
                            )
                            ActionMenuIds.EQUALIZER -> PlayerActionMenuItem(
                                stringResource(R.string.player_equalizer),
                                onOpenEqualizer,
                                icon = icon
                            )
                            ActionMenuIds.ADD_TO_PLAYLIST -> PlayerActionMenuItem(
                                stringResource(R.string.song_more_add_to_playlist),
                                onAddToPlaylist,
                                icon = icon
                            )
                            ActionMenuIds.PLAY_NEXT -> PlayerActionMenuItem(
                                stringResource(R.string.song_more_play_next),
                                onPlayNext,
                                icon = icon
                            )
                            ActionMenuIds.ADD_TO_QUEUE -> PlayerActionMenuItem(
                                stringResource(R.string.common_add_to_queue),
                                onAddToQueue,
                                icon = icon
                            )
                            ActionMenuIds.SHARE -> PlayerActionMenuItem(
                                stringResource(R.string.common_share),
                                onShare,
                                icon = icon
                            )
                            ActionMenuIds.AI -> PlayerActionMenuItem(
                                stringResource(R.string.song_more_ai_title),
                                onAiInterpret,
                                icon = icon
                            )
                            ActionMenuIds.INFO -> PlayerActionMenuItem(
                                stringResource(R.string.player_song_info),
                                onSongInfo,
                                icon = icon
                            )
                            ActionMenuIds.AUDIO_OUTPUT -> PlayerActionMenuItem(
                                text = stringResource(R.string.player_audio_output_info),
                                onClick = { setPage(PlayerActionSheetPage.AudioOutput) },
                                icon = icon
                            )
                            ActionMenuIds.CASTING -> PlayerActionMenuItem(
                                text = stringResource(R.string.casting_devices_title),
                                onClick = { openSystemOutputSwitcher(context) },
                                icon = icon
                            )
                            ActionMenuIds.AB_REPEAT -> PlayerActionMenuItem(
                                abRepeatLabel,
                                onAbRepeat,
                                icon = icon
                            )
                            ActionMenuIds.REMOTE_QUALITY -> remoteStreamMaxBitRate?.let { bitRate ->
                                PlayerActionMenuItem(
                                    stringResource(
                                        R.string.player_remote_stream_quality,
                                        if (bitRate == 0) stringResource(R.string.player_remote_stream_original) else "$bitRate kbps"
                                    ),
                                    onCycleRemoteStreamQuality,
                                    icon = icon
                                )
                            }
                            ActionMenuIds.LANDSCAPE -> PlayerActionMenuItem(
                                stringResource(R.string.player_landscape_lyrics),
                                onLandscape,
                                icon = icon
                            )
                            ActionMenuIds.LYRICS_DISPLAY -> if (showLyricsDisplayEntry) {
                                PlayerActionMenuItem(
                                    text = stringResource(R.string.player_lyrics_display),
                                    onClick = { setPage(PlayerActionSheetPage.LyricDisplay) },
                                    icon = icon
                                )
                            }
                            ActionMenuIds.SPECTRUM -> PlayerActionMenuItem(
                                stringResource(R.string.song_more_view_spectrum),
                                onSpectrum,
                                icon = icon
                            )
                            ActionMenuIds.RATING -> PlayerActionMenuItem(
                                stringResource(R.string.song_more_set_rating),
                                onSetRating,
                                icon = icon
                            )
                            ActionMenuIds.DYNAMIC_COVER -> PlayerActionMenuItem(
                                stringResource(R.string.player_match_dynamic_cover),
                                onMatchDynamicCover,
                                icon = icon
                            )
                            ActionMenuIds.VISUALIZER -> if (visualizerAvailable) {
                                PlayerActionMenuItem(
                                    text = stringResource(R.string.player_visualizer_settings),
                                    onClick = { setPage(PlayerActionSheetPage.Visualizer) },
                                    icon = icon
                                )
                            }
                            ActionMenuIds.EDIT_TAGS -> PlayerActionMenuItem(
                                stringResource(R.string.player_edit_metadata),
                                onEditMetadata,
                                icon = icon
                            )
                            ActionMenuIds.LYRIC_TIMING -> PlayerActionMenuItem(
                                stringResource(R.string.player_lyric_timing),
                                onLyricTiming,
                                icon = icon
                            )
                            ActionMenuIds.ONLINE_LYRICS -> PlayerActionMenuItem(
                                stringResource(R.string.player_match_online_lyrics),
                                onMatchOnlineLyrics,
                                icon = icon
                            )
                            ActionMenuIds.LYRIC_OFFSET -> PlayerActionMenuItem(
                                text = stringResource(R.string.player_lyric_offset),
                                onClick = { setPage(PlayerActionSheetPage.LyricOffset) },
                                icon = icon
                            )
                            ActionMenuIds.KEEP_SCREEN_ON -> if (showPlayerKeepScreenOnAction) {
                                PlayerActionMenuItem(
                                    stringResource(
                                        if (playerKeepScreenOn) R.string.player_disable_playback_keep_screen_on
                                        else R.string.player_enable_playback_keep_screen_on
                                    ),
                                    { onPlayerKeepScreenOnChange(!playerKeepScreenOn) },
                                    icon = icon
                                )
                            }
                            ActionMenuIds.DOWNLOAD -> if (song?.onlineSource == "kw" && song.path.startsWith("http")) {
                                PlayerActionMenuItem(
                                    stringResource(R.string.player_download_lx_song),
                                    onDownload,
                                    icon = icon
                                )
                            }
                            ActionMenuIds.TIMER -> {
                                val remaining = rememberSleepTimerRemaining(sleepTimerEndRealtimeMs)
                                PlayerActionMenuItem(
                                    text = stringResource(R.string.player_sleep_timer_title),
                                    onClick = { setPage(PlayerActionSheetPage.Timer) },
                                    icon = icon,
                                    subtitle = remaining?.let {
                                        stringResource(R.string.player_sleep_timer_remaining, it)
                                    }
                                )
                            }
                            ActionMenuIds.DELETE -> if (
                                song != null && !song.path.startsWith("http://", ignoreCase = true) &&
                                !song.path.startsWith("https://", ignoreCase = true)
                            ) {
                                PlayerActionMenuItem(
                                    stringResource(R.string.song_more_delete_permanently),
                                    onDeleteSong,
                                    danger = true,
                                    icon = icon
                                )
                            }
                        }
                    }
                }
            }
            PlayerActionSheetPage.Timer -> {
                TimerSheetContent(
                    onBack = { setPage(PlayerActionSheetPage.Main) },
                    sleepTimerEndRealtimeMs = sleepTimerEndRealtimeMs,
                    stopAfterCurrentEnabled = stopAfterCurrentEnabled,
                    sleepTimerCustomMinutes = sleepTimerCustomMinutes,
                    sleepTimerStopAfterCurrent = sleepTimerStopAfterCurrent,
                    onStopAfterCurrent = onStopAfterCurrent,
                    onTimer = onTimer,
                    onCustomTimerMinutes = onCustomTimerMinutes,
                    onCancelTimer = onCancelTimer,
                    showHeader = false
                )
            }
            PlayerActionSheetPage.Speed -> {
                SpeedPitchSheetContent(
                    speed = speed,
                    pitch = pitch,
                    onBack = { setPage(PlayerActionSheetPage.Main) },
                    onSpeed = onSpeed,
                    onPitch = onPitch,
                    showHeader = false
                )
            }
            PlayerActionSheetPage.LyricOffset -> {
                LyricOffsetSheetContent(
                    offsetMs = lyricOffsetMs,
                    onBack = { setPage(PlayerActionSheetPage.Main) },
                    onOffsetChange = onLyricOffset,
                    showHeader = false
                )
            }
            PlayerActionSheetPage.Visualizer -> {
                VisualizerSheetContent(
                    enabled = visualizerEnabled,
                    opacity = visualizerOpacity,
                    onBack = { setPage(PlayerActionSheetPage.Main) },
                    onEnabledChange = onVisualizerEnabled,
                    onOpacityChange = onVisualizerOpacityChange,
                    showHeader = false
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
                    onFontScale = onLyricFontScale,
                    onSecondaryFontScale = onLyricSecondaryFontScale,
                    onPrimaryTextSize = onLyricPrimaryTextSize,
                    onSecondaryTextSize = onLyricSecondaryTextSize,
                    onStyleSettings = { setPage(PlayerActionSheetPage.LyricStyle) },
                    showSheetHeader = false,
                    onBack = { setPage(PlayerActionSheetPage.Main) },
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
                    onBack = { setPage(PlayerActionSheetPage.LyricDisplay) },
                    initialBlurPercent = lyricNonCurrentBlurPercent,
                    showSheetHeader = false,
                    applyScrollableContainer = false,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            PlayerActionSheetPage.AudioOutput -> {
                AudioOutputInfoSheetContent(
                    song = song,
                    onBack = { setPage(PlayerActionSheetPage.Main) },
                    showHeader = false
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
    AudioOutput,
    LyricDisplay,
    LyricStyle
}
