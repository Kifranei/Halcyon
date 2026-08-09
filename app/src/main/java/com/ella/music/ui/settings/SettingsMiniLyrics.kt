package com.ella.music.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference

@Composable
internal fun SettingsMiniLyricsControls(highlightKey: String? = null) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val miniPlayerLyricSecondary by settingsManager.miniPlayerLyricSecondary.collectAsState(initial = SettingsManager.LYRIC_SECONDARY_TRANSLATION)
    val miniPlayerCoverRotation by settingsManager.miniPlayerCoverRotation.collectAsState(initial = true)
    val miniPlayerRightButton by settingsManager.miniPlayerRightButton.collectAsState(initial = 0)
    val miniPlayerLyricsEnabled by settingsManager.miniPlayerLyricsEnabled.collectAsState(initial = true)
    val miniPlayerSwipeToOpenPlayer by settingsManager.miniPlayerSwipeToOpenPlayer.collectAsState(initial = true)
    val lyricSourcePriority by settingsManager.lyricSourcePriority.collectAsState(initial = SettingsManager.DEFAULT_LYRIC_SOURCE_PRIORITY)

    val statusLyricSecondaryLabels = listOf(
        stringResource(R.string.settings_status_secondary_off),
        stringResource(R.string.settings_status_secondary_translation),
        stringResource(R.string.settings_status_secondary_pronunciation)
    )
    val statusLyricSecondaryEntries = remember(statusLyricSecondaryLabels) {
        statusLyricSecondaryLabels.map { DropdownItem(title = it) }
    }

    SettingsFocusAnchor(active = highlightKey == "mini_lyrics") {
        SwitchPreference(
            title = stringResource(R.string.settings_mini_player_lyrics),
            summary = stringResource(R.string.settings_mini_player_lyrics_summary),
            checked = miniPlayerLyricsEnabled,
            onCheckedChange = { enabled ->
                scope.launch { settingsManager.setMiniPlayerLyricsEnabled(enabled) }
            }
        )
    }

    WindowSpinnerPreference(
        title = stringResource(R.string.settings_mini_player_secondary),
        summary = stringResource(
            R.string.settings_current_value,
            statusLyricSecondaryLabels[miniPlayerLyricSecondary.coerceIn(0, 2)]
        ),
        enabled = miniPlayerLyricsEnabled,
        items = statusLyricSecondaryEntries,
        selectedIndex = miniPlayerLyricSecondary.coerceIn(0, 2),
        onSelectedIndexChange = { index ->
            scope.launch { settingsManager.setMiniPlayerLyricSecondary(index) }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_mini_player_cover_rotation),
        summary = stringResource(R.string.settings_mini_player_cover_rotation_summary),
        checked = miniPlayerCoverRotation,
        onCheckedChange = { enabled ->
            scope.launch { settingsManager.setMiniPlayerCoverRotation(enabled) }
        }
    )

    SwitchPreference(
        title = stringResource(R.string.settings_mini_player_swipe_to_open_player),
        summary = stringResource(R.string.settings_mini_player_swipe_to_open_player_summary),
        checked = miniPlayerSwipeToOpenPlayer,
        onCheckedChange = { enabled ->
            scope.launch { settingsManager.setMiniPlayerSwipeToOpenPlayer(enabled) }
        }
    )

    val miniPlayerRightButtonLabels = listOf(
        stringResource(R.string.settings_mini_player_right_next),
        stringResource(R.string.settings_mini_player_right_queue)
    )
    WindowSpinnerPreference(
        title = stringResource(R.string.settings_mini_player_right_button),
        summary = stringResource(
            R.string.settings_current_value,
            miniPlayerRightButtonLabels.getOrElse(miniPlayerRightButton) { miniPlayerRightButtonLabels[0] }
        ),
        items = miniPlayerRightButtonLabels.map { DropdownItem(title = it) },
        selectedIndex = miniPlayerRightButton.coerceIn(0, 1),
        onSelectedIndexChange = { index ->
            scope.launch { settingsManager.setMiniPlayerRightButton(index) }
        }
    )

    LyricSourcePriorityBlock(
        items = listOf(
            LyricSourcePreferenceItem(
                id = SettingsManager.LYRIC_SOURCE_EMBEDDED_TTML,
                title = stringResource(R.string.settings_lyric_source_embedded_ttml),
                summary = stringResource(R.string.settings_lyric_source_embedded_ttml_summary)
            ),
            LyricSourcePreferenceItem(
                id = SettingsManager.LYRIC_SOURCE_EMBEDDED_PLAIN,
                title = stringResource(R.string.settings_lyric_source_embedded_plain),
                summary = stringResource(R.string.settings_lyric_source_embedded_plain_summary)
            ),
            LyricSourcePreferenceItem(
                id = SettingsManager.LYRIC_SOURCE_EXTERNAL_TTML,
                title = stringResource(R.string.settings_lyric_source_external_ttml),
                summary = stringResource(R.string.settings_lyric_source_external_ttml_summary)
            ),
            LyricSourcePreferenceItem(
                id = SettingsManager.LYRIC_SOURCE_EXTERNAL_PLAIN,
                title = stringResource(R.string.settings_lyric_source_external_plain),
                summary = stringResource(R.string.settings_lyric_source_external_plain_summary)
            )
        ).orderedByLyricPriority(lyricSourcePriority),
        onOrderChange = { priority ->
            scope.launch { settingsManager.setLyricSourcePriority(priority) }
        }
    )
}
