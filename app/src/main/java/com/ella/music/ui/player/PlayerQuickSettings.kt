package com.ella.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.ella.music.R
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaCheckOptionGroup
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetColumn
import com.ella.music.ui.components.SpectrumViewerLauncher
import com.ella.music.ui.components.TagEditorOptionIds
import com.ella.music.ui.settings.ActionMenuReorderableSheet
import com.ella.music.ui.settings.NameSplitSettingsSheet
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch

/**
 * Player-page gestures (#747) shared through composition so every page style's transport row,
 * header actions and quick-action row pick them up without threading new parameters.
 */
internal data class PlayerQuickGestures(
    val playbackMode: (() -> Unit)? = null,
    val favorite: (() -> Unit)? = null,
    val more: (() -> Unit)? = null,
    val previous: (() -> Unit)? = null,
    val playPause: (() -> Unit)? = null,
    val quickAction: ((String) -> Unit)? = null,
    val titleClick: (() -> Unit)? = null,
    val artistLongPress: (() -> Unit)? = null,
    val titleMarquee: Boolean = true,
    val subtitleMarquee: Boolean = true,
    val showFavorite: Boolean = true
) {
    fun quickActionLongPress(id: String): (() -> Unit)? =
        quickAction?.takeIf { id in QUICK_ACTION_SETTINGS }?.let { handler -> { handler(id) } }
}

internal val LocalPlayerQuickGestures = staticCompositionLocalOf { PlayerQuickGestures() }

private val QUICK_ACTION_SETTINGS = setOf(ActionMenuIds.INFO, ActionMenuIds.SPECTRUM, ActionMenuIds.EDIT_TAGS, ActionMenuIds.LYRIC_TIMING)

private enum class PlayerQuickSetting { PlaybackMode, Previous, Pause, VerticalMenu, SongInfoLayout, Spectrum, MetadataEditor, LyricTimingEditor, ArtistSeparators }

@Composable
internal fun PlayerQuickGestureHost(
    playerViewModel: PlayerViewModel,
    onAddToPlaylist: () -> Unit,
    onOpenAlbum: () -> Unit,
    content: @Composable () -> Unit
) {
    val settings = playerViewModel.settingsManager
    val scope = rememberCoroutineScope()
    val titleOpensAlbum by settings.playerTitleOpensAlbum.collectAsState(initial = false)
    val titleMarquee by settings.playerTitleMarquee.collectAsState(initial = true)
    val subtitleMarquee by settings.playerSubtitleMarquee.collectAsState(initial = true)
    val showFavorite by settings.playerShowFavoriteButton.collectAsState(initial = true)
    var sheet by remember { mutableStateOf<PlayerQuickSetting?>(null) }
    val latestAddToPlaylist by rememberUpdatedState(onAddToPlaylist)
    val latestOpenAlbum by rememberUpdatedState(onOpenAlbum)
    // Keyed on settings only: callers pass fresh lambdas each recomposition, and a changing
    // static local would recompose the entire player page.
    val gestures = remember(titleOpensAlbum, titleMarquee, subtitleMarquee, showFavorite) {
        PlayerQuickGestures(
            playbackMode = { sheet = PlayerQuickSetting.PlaybackMode },
            favorite = { latestAddToPlaylist() },
            more = { sheet = PlayerQuickSetting.VerticalMenu },
            previous = { sheet = PlayerQuickSetting.Previous },
            playPause = { sheet = PlayerQuickSetting.Pause },
            quickAction = { id ->
                sheet = when (id) {
                    ActionMenuIds.INFO -> PlayerQuickSetting.SongInfoLayout
                    ActionMenuIds.SPECTRUM -> PlayerQuickSetting.Spectrum
                    ActionMenuIds.EDIT_TAGS -> PlayerQuickSetting.MetadataEditor
                    ActionMenuIds.LYRIC_TIMING -> PlayerQuickSetting.LyricTimingEditor
                    else -> null
                }
            },
            titleClick = if (titleOpensAlbum) { { latestOpenAlbum() } } else null,
            artistLongPress = { sheet = PlayerQuickSetting.ArtistSeparators },
            titleMarquee = titleMarquee,
            subtitleMarquee = subtitleMarquee,
            showFavorite = showFavorite
        )
    }
    CompositionLocalProvider(LocalPlayerQuickGestures provides gestures, content = content)

    val dismiss = { sheet = null }
    when (sheet) {
        PlayerQuickSetting.PlaybackMode -> {
            val shuffle by playerViewModel.shuffleEnabled.collectAsState()
            val repeat by playerViewModel.repeatMode.collectAsState()
            val disableSequential by settings.disableSequentialPlayback.collectAsState(initial = false)
            val stopAfterCurrentAllowed by settings.playbackModeStopAfterCurrent.collectAsState(initial = false)
            val stopAfterCurrentArmed by playerViewModel.stopAfterCurrentEnabled.collectAsState()
            val options = buildList {
                add(PlaybackModeChoice.ListLoop to stringResource(R.string.player_playback_mode_repeat_all))
                add(PlaybackModeChoice.SingleLoop to stringResource(R.string.player_playback_mode_repeat_one))
                add(PlaybackModeChoice.Shuffle to stringResource(R.string.player_playback_mode_shuffle))
                if (!disableSequential) add(PlaybackModeChoice.Sequential to stringResource(R.string.player_playback_mode_in_order))
                if (stopAfterCurrentAllowed) add(PlaybackModeChoice.StopAfterCurrent to stringResource(R.string.player_playback_mode_stop_after_current))
            }
            val current = when {
                stopAfterCurrentArmed && stopAfterCurrentAllowed -> PlaybackModeChoice.StopAfterCurrent
                shuffle -> PlaybackModeChoice.Shuffle
                repeat == Player.REPEAT_MODE_ONE -> PlaybackModeChoice.SingleLoop
                repeat == Player.REPEAT_MODE_ALL -> PlaybackModeChoice.ListLoop
                else -> PlaybackModeChoice.Sequential
            }
            OptionSheet(stringResource(R.string.player_quick_playback_mode), options, current, dismiss) { choice ->
                // Picking a real mode replaces the armed one-shot stop (#播完停止 as a mode option).
                if (choice != PlaybackModeChoice.StopAfterCurrent && stopAfterCurrentArmed) {
                    playerViewModel.setStopAfterCurrentEnabled(false)
                }
                when (choice) {
                    PlaybackModeChoice.ListLoop -> playerViewModel.setPlaybackMode(false, Player.REPEAT_MODE_ALL)
                    PlaybackModeChoice.SingleLoop -> playerViewModel.setPlaybackMode(false, Player.REPEAT_MODE_ONE)
                    PlaybackModeChoice.Shuffle -> playerViewModel.setPlaybackMode(true, Player.REPEAT_MODE_ALL)
                    PlaybackModeChoice.Sequential -> playerViewModel.setPlaybackMode(false, Player.REPEAT_MODE_OFF)
                    PlaybackModeChoice.StopAfterCurrent -> playerViewModel.setStopAfterCurrentEnabled(!stopAfterCurrentArmed)
                }
            }
        }
        PlayerQuickSetting.Previous -> {
            val action by settings.previousButtonAction.collectAsState(initial = SettingsManager.PREVIOUS_BUTTON_PREVIOUS)
            OptionSheet(stringResource(R.string.settings_previous_button), listOf(
                SettingsManager.PREVIOUS_BUTTON_PREVIOUS to stringResource(R.string.settings_previous_button_previous),
                SettingsManager.PREVIOUS_BUTTON_REPLAY_CURRENT to stringResource(R.string.settings_previous_button_replay_current)
            ), action, dismiss, playerViewModel::setPreviousButtonAction)
        }
        PlayerQuickSetting.Pause -> {
            val mode by settings.pausedSwitchMode.collectAsState(initial = SettingsManager.PAUSED_SWITCH_MODE_KEEP_PAUSED)
            OptionSheet(stringResource(R.string.settings_paused_switch_mode), listOf(
                SettingsManager.PAUSED_SWITCH_MODE_KEEP_PAUSED to stringResource(R.string.settings_paused_switch_keep_paused),
                SettingsManager.PAUSED_SWITCH_MODE_PLAY to stringResource(R.string.settings_paused_switch_play)
            ), mode, dismiss, playerViewModel::setPausedSwitchMode)
        }
        PlayerQuickSetting.VerticalMenu -> {
            val layout by settings.playerActionMenuLayout.collectAsState(initial = "")
            ActionMenuReorderableSheet(
                show = true,
                title = stringResource(R.string.settings_player_vertical_actions),
                subtitle = stringResource(R.string.settings_action_menu_summary),
                savedLayout = layout,
                defaultOrder = ActionMenuIds.playerActionMenuDefaults,
                onDismissRequest = dismiss,
                onSave = { value -> scope.launch { settings.setPlayerActionMenuLayout(value); sheet = null } }
            )
        }
        PlayerQuickSetting.SongInfoLayout -> {
            val layout by settings.songInfoLayout.collectAsState(initial = "")
            ActionMenuReorderableSheet(
                show = true,
                title = stringResource(R.string.settings_song_info_layout),
                subtitle = stringResource(R.string.settings_action_menu_summary),
                savedLayout = layout,
                defaultOrder = ActionMenuIds.songInfoDefaults,
                onDismissRequest = dismiss,
                onSave = { value -> scope.launch { settings.setSongInfoLayout(value); sheet = null } }
            )
        }
        PlayerQuickSetting.Spectrum -> {
            val selected by settings.spectrumViewerId.collectAsState(initial = SpectrumViewerLauncher.BUILTIN)
            OptionSheet(stringResource(R.string.settings_spectrum_viewer), spectrumViewerOptions(), selected, dismiss) {
                scope.launch { settings.setSpectrumViewerId(it) }
            }
        }
        PlayerQuickSetting.MetadataEditor -> {
            val selected by settings.metadataEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
            OptionSheet(stringResource(R.string.settings_metadata_editor), metadataEditorOptions(), selected, dismiss) {
                scope.launch { settings.setMetadataEditorId(it) }
            }
        }
        PlayerQuickSetting.LyricTimingEditor -> {
            val selected by settings.lyricTimingEditorId.collectAsState(initial = TagEditorOptionIds.ASK_EACH_TIME)
            OptionSheet(stringResource(R.string.settings_lyric_timing_editor), lyricTimingEditorOptions(), selected, dismiss) {
                scope.launch { settings.setLyricTimingEditorId(it) }
            }
        }
        PlayerQuickSetting.ArtistSeparators -> {
            val artistSeparators by settings.artistSeparators.collectAsState(
                initial = SettingsManager.DEFAULT_ARTIST_SEPARATORS
            )
            val artistProtectedNames by settings.artistProtectedNames.collectAsState(initial = "")
            val genreSeparators by settings.genreSeparators.collectAsState(
                initial = SettingsManager.DEFAULT_GENRE_SEPARATORS
            )
            val genreProtectedNames by settings.genreProtectedNames.collectAsState(initial = "")
            NameSplitSettingsSheet(
                show = true,
                values = listOf(artistSeparators, artistProtectedNames, genreSeparators, genreProtectedNames),
                onDismissRequest = dismiss,
                onSave = { values -> scope.launch {
                    settings.setArtistSeparators(values[0])
                    settings.setArtistProtectedNames(values[1])
                    settings.setGenreSeparators(values[2])
                    settings.setGenreProtectedNames(values[3])
                } }
            )
        }
        null -> Unit
    }
}

private enum class PlaybackModeChoice { ListLoop, SingleLoop, Shuffle, Sequential, StopAfterCurrent }

@Composable
private fun <T> OptionSheet(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onSelect: (T) -> Unit) {
    EllaMiuixBottomSheet(show = true, title = title, onDismissRequest = onDismiss) {
        EllaMiuixSheetColumn(spacing = 8.dp, showHandle = false) {
            EllaCheckOptionGroup(options = options, selected = selected, onSelect = { onSelect(it); onDismiss() })
        }
    }
}

@Composable
internal fun metadataEditorOptions(): List<Pair<String, String>> = listOf(
    TagEditorOptionIds.ASK_EACH_TIME to stringResource(R.string.settings_editor_ask_every_time),
    TagEditorOptionIds.BUILTIN_CUSTOM_TAG to stringResource(R.string.settings_editor_builtin_custom_tag),
    TagEditorOptionIds.LYRICO to "Lyrico",
    TagEditorOptionIds.LUNABEAT_METADATA to stringResource(R.string.settings_editor_lunabeat_metadata),
    TagEditorOptionIds.MUSIC_TAG to stringResource(R.string.settings_editor_music_tag),
    TagEditorOptionIds.SPOTIFLAC to stringResource(R.string.settings_editor_spotiflac)
)

@Composable
internal fun lyricTimingEditorOptions(): List<Pair<String, String>> = listOf(
    TagEditorOptionIds.ASK_EACH_TIME to stringResource(R.string.settings_editor_ask_every_time),
    TagEditorOptionIds.BUILTIN_LYRIC_TIMING to stringResource(R.string.settings_editor_builtin_lyric_timing),
    TagEditorOptionIds.LUNABEAT_LYRIC_TIMING to stringResource(R.string.settings_editor_lunabeat_lyric_timing)
)

@Composable
internal fun spectrumViewerOptions(): List<Pair<String, String>> = listOf(
    SpectrumViewerLauncher.ASK_EACH_TIME to stringResource(R.string.settings_editor_ask_every_time),
    SpectrumViewerLauncher.BUILTIN to stringResource(R.string.settings_spectrum_builtin),
    SpectrumViewerLauncher.ASPECT_PRO to stringResource(R.string.settings_spectrum_aspect_pro),
    SpectrumViewerLauncher.KASPEK to stringResource(R.string.settings_spectrum_kaspek),
    SpectrumViewerLauncher.HEARUSY to stringResource(R.string.settings_spectrum_hearusy)
)
