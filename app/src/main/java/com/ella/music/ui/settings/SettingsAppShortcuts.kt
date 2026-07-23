package com.ella.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaMiuixAction
import com.ella.music.ui.components.EllaMiuixActionRow
import com.ella.music.ui.components.EllaMiuixBottomSheet
import sh.calvin.reorderable.ReorderableColumn
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
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
            SettingsManager.APP_SHORTCUT_FOLDERS,
            stringResource(R.string.category_folder),
            stringResource(R.string.settings_library_tile_folder_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_FOLDER_TREE,
            stringResource(R.string.category_folder_tree),
            stringResource(R.string.settings_library_tile_folder_tree_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_FOLDER_PLAYLISTS,
            stringResource(R.string.folder_playlist_title),
            stringResource(R.string.settings_library_tile_folder_playlist_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_ALBUMS,
            stringResource(R.string.settings_library_tile_album),
            stringResource(R.string.settings_library_tile_album_summary)
        ),
        AppShortcutPreferenceItem(
            SettingsManager.APP_SHORTCUT_ARTISTS,
            stringResource(R.string.settings_library_tile_artist),
            stringResource(R.string.settings_library_tile_artist_summary)
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

    EllaMiuixBottomSheet(
        show = sheetVisible,
        title = stringResource(R.string.settings_desktop_shortcuts),
        onDismissRequest = { sheetVisible = false }
    ) {
        var manualIds by remember(selectedIds.joinToString(",")) { mutableStateOf(selectedIds) }
        val manualItems = manualIds.mapNotNull(itemById::get)
        val availableItems = items.filterNot { it.id in manualIds }

        fun updateSelection(nextIds: List<String>) {
            manualIds = nextIds
                .filter { it in itemById }
                .distinct()
                .take(SettingsManager.MAX_APP_SHORTCUTS)
            onShortcutIdsChange(manualIds)
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 560.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 18.dp)
        ) {
            Text(
                text = stringResource(
                    R.string.settings_shortcuts_manage_summary,
                    SettingsManager.MAX_APP_SHORTCUTS
                ),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            SmallTitle(
                text = stringResource(
                    R.string.settings_shortcuts_selected,
                    manualItems.size,
                    SettingsManager.MAX_APP_SHORTCUTS
                )
            )
            if (manualItems.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_shortcuts_empty),
                    fontSize = 14.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp)
                )
            } else {
                ReorderableColumn(
                    list = manualItems,
                    onSettle = { fromIndex, toIndex ->
                        if (fromIndex !in manualIds.indices || toIndex !in manualIds.indices || fromIndex == toIndex) {
                            return@ReorderableColumn
                        }
                        updateSelection(manualIds.moveShortcutItem(fromIndex, toIndex))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { _, item, isDragging ->
                    val itemIndex = manualIds.indexOf(item.id)
                    ReorderableItem {
                        BasicComponent(
                            title = item.title,
                            summary = item.summary,
                            modifier = Modifier
                                .background(
                                    if (isDragging) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                                    RoundedCornerShape(12.dp)
                                )
                                .longPressDraggableHandle(),
                            endActions = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    ShortcutSheetAction(
                                        text = "↑",
                                        enabled = itemIndex > 0,
                                        onClick = {
                                            updateSelection(manualIds.moveShortcutItem(itemIndex, itemIndex - 1))
                                        }
                                    )
                                    ShortcutSheetAction(
                                        text = "↓",
                                        enabled = itemIndex in 0 until manualIds.lastIndex,
                                        onClick = {
                                            updateSelection(manualIds.moveShortcutItem(itemIndex, itemIndex + 1))
                                        }
                                    )
                                    ShortcutSheetAction(
                                        text = stringResource(R.string.common_remove),
                                        onClick = { updateSelection(manualIds - item.id) }
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Text(
                                        text = "☰",
                                        fontSize = 16.sp,
                                        color = if (isDragging) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }

            if (availableItems.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                SmallTitle(text = stringResource(R.string.settings_shortcuts_available))
                availableItems.forEach { item ->
                    val canAdd = manualIds.size < SettingsManager.MAX_APP_SHORTCUTS
                    BasicComponent(
                        title = item.title,
                        summary = item.summary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(enabled = canAdd) {
                                updateSelection(manualIds + item.id)
                            },
                        endActions = {
                            Text(
                                text = stringResource(R.string.settings_shortcuts_add),
                                fontSize = 14.sp,
                                color = if (canAdd) {
                                    MiuixTheme.colorScheme.primary
                                } else {
                                    MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.45f)
                                }
                            )
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            EllaMiuixActionRow(
                actions = listOf(
                    EllaMiuixAction(
                        text = stringResource(R.string.common_done),
                        onClick = { sheetVisible = false },
                        primary = true
                    )
                ),
                modifier = Modifier.padding(horizontal = 12.dp)
            )
        }
    }
}

@Composable
private fun ShortcutSheetAction(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 13.sp,
        color = if (enabled) {
            MiuixTheme.colorScheme.primary
        } else {
            MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.35f)
        },
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 5.dp, vertical = 5.dp)
    )
}

private fun <T> List<T>.moveShortcutItem(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}
