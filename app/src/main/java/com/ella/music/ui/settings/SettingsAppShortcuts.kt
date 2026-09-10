package com.ella.music.ui.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.ReorderableSelectionItem
import com.ella.music.ui.components.ReorderableSelectionSheet
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Link

private data class AppShortcutPreferenceItem(
    val id: String,
    val title: String,
    val summary: String
)

/** Settings UI for Android 7.1+ dynamic launcher shortcuts. */
@Composable
internal fun SettingsAppShortcutsPreference(
    shortcutIds: List<String>,
    onShortcutIdsChange: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val items = listOf(
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_LIBRARY,
            stringResource(R.string.shortcut_library_short),
            stringResource(R.string.shortcut_library_long)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_SEARCH,
            stringResource(R.string.shortcut_search_short),
            stringResource(R.string.common_search)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_PLAY,
            stringResource(R.string.shortcut_play_short),
            stringResource(R.string.shortcut_play_long)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_SHUFFLE_ALL,
            stringResource(R.string.shortcut_shuffle_all_short),
            stringResource(R.string.shortcut_shuffle_all_long)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_PLAYLISTS,
            stringResource(R.string.settings_library_tile_playlist),
            stringResource(R.string.settings_library_tile_playlist_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_ARTISTS,
            stringResource(R.string.category_artist),
            stringResource(R.string.settings_library_tile_artist_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_ALBUMS,
            stringResource(R.string.category_album),
            stringResource(R.string.settings_library_tile_album_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_FOLDERS,
            stringResource(R.string.category_folder),
            stringResource(R.string.settings_library_tile_folder_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_GENRES,
            stringResource(R.string.category_genre),
            stringResource(R.string.settings_library_tile_genre_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_YEARS,
            stringResource(R.string.category_year),
            stringResource(R.string.settings_library_tile_year_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_COMPOSERS,
            stringResource(R.string.category_composer),
            stringResource(R.string.settings_library_tile_composer_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_ARRANGERS,
            stringResource(R.string.category_arranger),
            stringResource(R.string.settings_library_tile_arranger_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_LYRICISTS,
            stringResource(R.string.category_lyricist),
            stringResource(R.string.settings_library_tile_lyricist_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_ANALYTICS,
            stringResource(R.string.settings_library_tile_analytics),
            stringResource(R.string.settings_library_tile_analytics_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_LIBRARY_ANALYSIS,
            stringResource(R.string.analytics_library_analysis),
            stringResource(R.string.folder_library_analysis_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_SCAN_SETTINGS,
            stringResource(R.string.folder_scan_settings),
            stringResource(R.string.settings_scan)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_SETTINGS,
            stringResource(R.string.settings),
            stringResource(R.string.settings)
        )
    )
    val itemById = remember(items) { items.associateBy { it.id } }
    val selectedIds = remember(shortcutIds, itemById) {
        shortcutIds
            .filter { it in itemById }
            .distinct()
            .take(SettingsManager.MAX_APP_SHORTCUTS)
    }
    val selectedItems = selectedIds.mapNotNull(itemById::get)
    var sheetVisible by remember { mutableStateOf(false) }

    val shortcutSelectionItems = remember(selectedIds, items) {
        selectedIds.mapNotNull { itemById[it] }.map {
            ReorderableSelectionItem(id = it.id, title = it.title, summary = it.summary, enabled = true)
        } + items.filterNot { it.id in selectedIds }.map {
            ReorderableSelectionItem(id = it.id, title = it.title, summary = it.summary, enabled = false)
        }
    }
    val defaultShortcutItems = remember(items) {
        SettingsManager.DEFAULT_APP_SHORTCUT_ORDER.mapNotNull { itemById[it] }.map {
            ReorderableSelectionItem(id = it.id, title = it.title, summary = it.summary, enabled = true)
        }
    }

    BasicComponent(
        title = stringResource(
            R.string.settings_shortcuts_selected,
            selectedItems.size,
            SettingsManager.MAX_APP_SHORTCUTS
        ),
        summary = if (selectedItems.isEmpty()) {
            stringResource(R.string.settings_shortcuts_empty)
        } else {
            selectedItems.joinToString(" · ") { it.title }
        },
        modifier = Modifier.clickable { sheetVisible = true },
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = MiuixIcons.Regular.Link,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = "${selectedItems.size}/${SettingsManager.MAX_APP_SHORTCUTS}",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    )

    ReorderableSelectionSheet(
        show = sheetVisible,
        title = stringResource(R.string.settings_desktop_shortcuts),
        subtitle = stringResource(
            R.string.settings_shortcuts_manage_summary,
            SettingsManager.MAX_APP_SHORTCUTS
        ),
        items = shortcutSelectionItems,
        defaultItems = defaultShortcutItems,
        maxSelectCount = SettingsManager.MAX_APP_SHORTCUTS,
        onExceedMaxSelect = {
            Toast.makeText(
                context,
                context.getString(R.string.settings_shortcuts_manage_summary, SettingsManager.MAX_APP_SHORTCUTS),
                Toast.LENGTH_SHORT
            ).show()
        },
        onDismissRequest = { sheetVisible = false },
        onSave = { updated ->
            val newIds = updated.filter { it.enabled }.map { it.id }.take(SettingsManager.MAX_APP_SHORTCUTS)
            onShortcutIdsChange(newIds)
            sheetVisible = false
        },
        onReset = {
            onShortcutIdsChange(SettingsManager.DEFAULT_APP_SHORTCUT_ORDER)
            sheetVisible = false
        }
    )
}
