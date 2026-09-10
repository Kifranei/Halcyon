package com.ella.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaMiuixAction
import com.ella.music.ui.components.EllaMiuixActionRow
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.ReorderableSelectionItem
import com.ella.music.ui.components.ReorderableSelectionSheet
import kotlinx.coroutines.flow.Flow
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.ColorPicker
import top.yukonga.miuix.kmp.basic.ColorSpace
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Check
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Locale

internal data class HomePreferenceItem(
    val id: String,
    val title: String,
    val summary: String
)

internal data class LyricSourcePreferenceItem(
    val id: String,
    val title: String,
    val summary: String,
    val enabled: Boolean = true
)

@Composable
internal fun <T> Flow<T>.collectSettingsState(initialValue: T): State<T> {
    return collectAsState(initial = initialValue)
}

@Composable
internal fun HomeDisplaySettingsPage(
    sectionItems: List<HomePreferenceItem>,
    sectionOrder: String,
    recentSectionMode: Int,
    hiddenSections: String,
    topBarActionItems: List<HomePreferenceItem>,
    topBarActionOrder: String,
    hiddenTopBarActions: String,
    tileItems: List<HomePreferenceItem>,
    tileOrder: String,
    hiddenTiles: String,
    onlineItems: List<HomePreferenceItem>,
    onlineOrder: String,
    hiddenOnlineTiles: String,
    tilePinButtonsVisible: Boolean,
    homeCardColor: String,
    nostalgiaEnabled: Boolean,
    highlightKey: String? = null,
    onHiddenSectionsChange: (String) -> Unit,
    onHiddenTilesChange: (String) -> Unit,
    onHiddenOnlineTilesChange: (String) -> Unit,
    onSectionOrderChange: (String) -> Unit,
    onRecentSectionModeChange: (Int) -> Unit,
    onTopBarActionOrderChange: (String) -> Unit,
    onHiddenTopBarActionsChange: (String) -> Unit,
    onTileOrderChange: (String) -> Unit,
    onOnlineOrderChange: (String) -> Unit,
    onTilePinButtonsVisibleChange: (Boolean) -> Unit,
    onHomeCardColorChange: (String) -> Unit,
    onNostalgiaEnabledChange: (Boolean) -> Unit
) {
    val orderedSections = remember(sectionItems, sectionOrder) {
        sectionItems.orderedByCsv(sectionOrder, SettingsManager.DEFAULT_HOME_SECTION_ORDER)
    }
    val orderedTiles = remember(tileItems, tileOrder) {
        tileItems.orderedByCsv(tileOrder, SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER)
    }
    val orderedOnlineTiles = remember(onlineItems, onlineOrder) {
        onlineItems.orderedByCsv(onlineOrder, SettingsManager.DEFAULT_HOME_ONLINE_TILE_ORDER)
    }
    val hiddenSectionIds = remember(hiddenSections) { hiddenSections.csvIdSet() }
    val orderedTopBarActions = remember(topBarActionItems, topBarActionOrder) {
        topBarActionItems.orderedByCsv(
            topBarActionOrder,
            SettingsManager.DEFAULT_HOME_TOP_BAR_ACTION_ORDER
        )
    }
    val hiddenTopBarActionIds = remember(hiddenTopBarActions) { hiddenTopBarActions.csvIdSet() }
    val hiddenTileIds = remember(hiddenTiles) { hiddenTiles.csvIdSet() }
    val hiddenOnlineTileIds = remember(hiddenOnlineTiles) { hiddenOnlineTiles.csvIdSet() }
    val highlightTileColors = highlightKey == "home_tile_colors"

    if (highlightTileColors) {
        HomeTileColorSettings(
            homeCardColor = homeCardColor,
            highlight = true,
            onHomeCardColorChange = onHomeCardColorChange
        )
    }

    var showSectionSheet by remember { mutableStateOf(false) }
    var showTopBarActionSheet by remember { mutableStateOf(false) }
    var showTileSheet by remember { mutableStateOf(false) }
    var showOnlineTileSheet by remember { mutableStateOf(false) }

    SmallTitle(text = stringResource(R.string.settings_home_top_actions_title))
    SettingsCardGroup(highlight = highlightKey == "home_top_actions") {
        val enabledTopBarActionTitles = orderedTopBarActions
            .filter { it.id !in hiddenTopBarActionIds }
            .map { it.title }
        ArrowPreference(
            title = stringResource(R.string.settings_home_top_actions_custom_title),
            summary = if (enabledTopBarActionTitles.isNotEmpty()) {
                enabledTopBarActionTitles.joinToString(" / ")
            } else {
                stringResource(R.string.custom_sort_or_hide_summary)
            },
            onClick = { showTopBarActionSheet = true }
        )
    }
    SettingsCardGroup(highlight = highlightKey == "home_nostalgia") {
        SwitchPreference(
            title = stringResource(R.string.settings_home_nostalgia),
            summary = stringResource(R.string.settings_home_nostalgia_summary),
            checked = nostalgiaEnabled,
            onCheckedChange = onNostalgiaEnabledChange
        )
    }
    SmallTitle(text = stringResource(R.string.settings_home_sections_title))
    SettingsCardGroup(highlight = highlightKey == "home_sections") {
        val enabledSectionTitles = orderedSections.filter { it.id !in hiddenSectionIds }.map { it.title }
        ArrowPreference(
            title = stringResource(R.string.settings_home_sections_custom_title),
            summary = if (enabledSectionTitles.isNotEmpty()) {
                enabledSectionTitles.joinToString(" / ")
            } else {
                stringResource(R.string.custom_sort_or_hide_summary)
            },
            onClick = { showSectionSheet = true }
        )
    }
    SettingsCardGroup(highlight = highlightKey == "home_recent_section_mode") {
        WindowSpinnerPreference(
            title = stringResource(R.string.settings_home_recent_content),
            summary = stringResource(R.string.settings_home_recent_content_summary),
            items = listOf(
                DropdownItem(stringResource(R.string.home_recent_played)),
                DropdownItem(stringResource(R.string.home_recent_added))
            ),
            selectedIndex = recentSectionMode.coerceIn(
                SettingsManager.HOME_RECENT_SECTION_MODE_PLAYED,
                SettingsManager.HOME_RECENT_SECTION_MODE_ADDED
            ),
            onSelectedIndexChange = onRecentSectionModeChange
        )
    }
    SmallTitle(text = stringResource(R.string.settings_home_library_grid_title))
    SettingsCardGroup(highlight = highlightKey == "home_library_tiles") {
        val enabledTileTitles = orderedTiles.filter { it.id !in hiddenTileIds }.map { it.title }
        ArrowPreference(
            title = stringResource(R.string.settings_home_library_tiles_custom_title),
            summary = if (enabledTileTitles.isNotEmpty()) {
                enabledTileTitles.joinToString(" / ")
            } else {
                stringResource(R.string.custom_sort_or_hide_summary)
            },
            onClick = { showTileSheet = true }
        )
    }
    SmallTitle(text = stringResource(R.string.settings_home_online_grid_title))
    SettingsCardGroup(highlight = highlightKey == "home_online_tiles") {
        val enabledOnlineTitles = orderedOnlineTiles.filter { it.id !in hiddenOnlineTileIds }.map { it.title }
        ArrowPreference(
            title = stringResource(R.string.settings_home_online_tiles_custom_title),
            summary = if (enabledOnlineTitles.isNotEmpty()) {
                enabledOnlineTitles.joinToString(" / ")
            } else {
                stringResource(R.string.custom_sort_or_hide_summary)
            },
            onClick = { showOnlineTileSheet = true }
        )
    }
    SettingsCardGroup(highlight = highlightKey == "home_tile_pin_buttons") {
        SwitchPreference(
            title = stringResource(R.string.settings_home_tile_pin_buttons),
            summary = stringResource(R.string.settings_home_tile_pin_buttons_summary),
            checked = tilePinButtonsVisible,
            onCheckedChange = onTilePinButtonsVisibleChange
        )
    }
    if (!highlightTileColors) {
        HomeTileColorSettings(
            homeCardColor = homeCardColor,
            highlight = false,
            onHomeCardColorChange = onHomeCardColorChange
        )
    }

    val sectionSelectionItems = remember(orderedSections, hiddenSectionIds) {
        orderedSections.map {
            ReorderableSelectionItem(
                id = it.id,
                title = it.title,
                summary = it.summary,
                enabled = it.id !in hiddenSectionIds
            )
        }
    }
    val topBarActionSelectionItems = remember(orderedTopBarActions, hiddenTopBarActionIds) {
        orderedTopBarActions.map {
            ReorderableSelectionItem(
                id = it.id,
                title = it.title,
                summary = it.summary,
                enabled = it.id !in hiddenTopBarActionIds
            )
        }
    }
    val defaultTopBarActionItems = remember(topBarActionItems) {
        topBarActionItems
            .orderedByCsv(
                SettingsManager.DEFAULT_HOME_TOP_BAR_ACTION_ORDER,
                SettingsManager.DEFAULT_HOME_TOP_BAR_ACTION_ORDER
            )
            .map {
                ReorderableSelectionItem(
                    id = it.id,
                    title = it.title,
                    summary = it.summary,
                    enabled = true
                )
            }
    }
    ReorderableSelectionSheet(
        show = showTopBarActionSheet,
        title = stringResource(R.string.settings_home_top_actions_custom_title),
        items = topBarActionSelectionItems,
        defaultItems = defaultTopBarActionItems,
        onDismissRequest = { showTopBarActionSheet = false },
        onSave = { updated ->
            val newOrder = updated.joinToString(",") { it.id }
            val newHidden = updated.filterNot { it.enabled }.map { it.id }.toSet().toCsv()
            onTopBarActionOrderChange(newOrder)
            onHiddenTopBarActionsChange(newHidden)
        }
    )
    val defaultSectionItems = remember(sectionItems) {
        sectionItems.orderedByCsv(SettingsManager.DEFAULT_HOME_SECTION_ORDER, SettingsManager.DEFAULT_HOME_SECTION_ORDER).map {
            ReorderableSelectionItem(
                id = it.id,
                title = it.title,
                summary = it.summary,
                enabled = true
            )
        }
    }
    ReorderableSelectionSheet(
        show = showSectionSheet,
        title = stringResource(R.string.settings_home_sections_custom_title),
        items = sectionSelectionItems,
        defaultItems = defaultSectionItems,
        onDismissRequest = { showSectionSheet = false },
        onSave = { updated ->
            val newOrder = updated.joinToString(",") { it.id }
            val newHidden = updated.filterNot { it.enabled }.map { it.id }.toSet().toCsv()
            onSectionOrderChange(newOrder)
            onHiddenSectionsChange(newHidden)
        }
    )

    val tileSelectionItems = remember(orderedTiles, hiddenTileIds) {
        orderedTiles.map {
            ReorderableSelectionItem(
                id = it.id,
                title = it.title,
                summary = it.summary,
                enabled = it.id !in hiddenTileIds
            )
        }
    }
    val defaultTileItems = remember(tileItems) {
        tileItems.orderedByCsv(SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER, SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER).map {
            ReorderableSelectionItem(
                id = it.id,
                title = it.title,
                summary = it.summary,
                enabled = true
            )
        }
    }
    ReorderableSelectionSheet(
        show = showTileSheet,
        title = stringResource(R.string.settings_home_library_tiles_custom_title),
        items = tileSelectionItems,
        defaultItems = defaultTileItems,
        onDismissRequest = { showTileSheet = false },
        onSave = { updated ->
            val newOrder = updated.joinToString(",") { it.id }
            val newHidden = updated.filterNot { it.enabled }.map { it.id }.toSet().toCsv()
            onTileOrderChange(newOrder)
            onHiddenTilesChange(newHidden)
        }
    )

    val onlineTileSelectionItems = remember(orderedOnlineTiles, hiddenOnlineTileIds) {
        orderedOnlineTiles.map {
            ReorderableSelectionItem(
                id = it.id,
                title = it.title,
                summary = it.summary,
                enabled = it.id !in hiddenOnlineTileIds
            )
        }
    }
    val defaultOnlineTileItems = remember(onlineItems) {
        onlineItems.orderedByCsv(SettingsManager.DEFAULT_HOME_ONLINE_TILE_ORDER, SettingsManager.DEFAULT_HOME_ONLINE_TILE_ORDER).map {
            ReorderableSelectionItem(
                id = it.id,
                title = it.title,
                summary = it.summary,
                enabled = true
            )
        }
    }
    ReorderableSelectionSheet(
        show = showOnlineTileSheet,
        title = stringResource(R.string.settings_home_online_tiles_custom_title),
        items = onlineTileSelectionItems,
        defaultItems = defaultOnlineTileItems,
        onDismissRequest = { showOnlineTileSheet = false },
        onSave = { updated ->
            val newOrder = updated.joinToString(",") { it.id }
            val newHidden = updated.filterNot { it.enabled }.map { it.id }.toSet().toCsv()
            onOnlineOrderChange(newOrder)
            onHiddenOnlineTilesChange(newHidden)
        }
    )
}

@Composable
private fun HomeTileColorSettings(
    homeCardColor: String,
    highlight: Boolean,
    onHomeCardColorChange: (String) -> Unit
) {
    var showColorPicker by remember { mutableStateOf(false) }
    SmallTitle(text = stringResource(R.string.settings_home_tile_colors_title))
    SettingsCardGroup(highlight = highlight) {
        BasicComponent(
            title = stringResource(R.string.settings_home_card_color),
            summary = homeCardColor.ifBlank { stringResource(R.string.settings_home_card_color_default) },
            modifier = Modifier.clickable { showColorPicker = true }
        )
    }

    EllaMiuixBottomSheet(
        show = showColorPicker,
        title = stringResource(R.string.settings_home_card_color),
        onDismissRequest = { showColorPicker = false }
    ) {
        val currentColor = remember(homeCardColor) {
            homeCardColor.parseHomeDisplayColorOrNull() ?: Color(0xFF2B2B31)
        }
        var pickerColor by remember(currentColor) { mutableStateOf(currentColor) }
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
            ColorPicker(
                color = pickerColor,
                onColorChanged = { pickerColor = it },
                colorSpace = ColorSpace.HSV,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = {
                    onHomeCardColorChange("#%08X".format(pickerColor.toArgb()))
                    showColorPicker = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.common_confirm))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    onHomeCardColorChange("")
                    showColorPicker = false
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.common_reset))
            }
        }
    }
}

@Composable
internal fun LyricSourcePriorityBlock(
    items: List<LyricSourcePreferenceItem>,
    onOrderChange: (String) -> Unit,
    title: String = stringResource(R.string.settings_lyric_source_priority),
    subtitle: String = stringResource(R.string.settings_lyric_source_priority_summary),
    defaultOrder: String = SettingsManager.DEFAULT_LYRIC_SOURCE_PRIORITY
) {
    var sheetVisible by remember { mutableStateOf(false) }

    val enabledItems = items.filter { it.enabled }
    val summaryText = if (enabledItems.isNotEmpty()) {
        enabledItems.joinToString(" / ") { it.title }
    } else {
        stringResource(R.string.custom_sort_or_hide_summary)
    }

    ArrowPreference(
        title = title,
        summary = summaryText,
        onClick = { sheetVisible = true }
    )

    val selectionItems = remember(items) {
        items.map {
            ReorderableSelectionItem(
                id = it.id,
                title = it.title,
                summary = it.summary,
                enabled = it.enabled
            )
        }
    }

    val defaultItems = remember(items, defaultOrder) {
        val byId = items.associateBy { it.id }
        val defaultIds = defaultOrder.split(',')
        (defaultIds.mapNotNull { byId[it] } + items.filterNot { it.id in defaultIds }).map {
            ReorderableSelectionItem(
                id = it.id,
                title = it.title,
                summary = it.summary,
                enabled = true
            )
        }
    }

    ReorderableSelectionSheet(
        show = sheetVisible,
        title = title,
        subtitle = subtitle,
        items = selectionItems,
        defaultItems = defaultItems,
        onDismissRequest = { sheetVisible = false },
        onSave = { updatedItems ->
            val priority = updatedItems.filter { it.enabled }.joinToString(",") { it.id }
            onOrderChange(priority)
        }
    )
}

private fun <T> List<T>.moveItem(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices || from == to) return this
    return toMutableList().apply {
        add(to, removeAt(from))
    }
}

private fun List<HomePreferenceItem>.orderedByCsv(order: String, defaultOrder: String): List<HomePreferenceItem> {
    val byId = associateBy { it.id }
    val orderIds = order.csvIds(defaultOrder)
    return (orderIds.mapNotNull { byId[it] } + filterNot { it.id in orderIds }).distinctBy { it.id }
}

internal fun List<LyricSourcePreferenceItem>.orderedByLyricPriority(priority: String): List<LyricSourcePreferenceItem> =
    orderedByEnabledIds(SettingsManager.normalizeLyricSourcePriority(priority))

internal fun List<LyricSourcePreferenceItem>.orderedByEnabledIds(enabledCsv: String): List<LyricSourcePreferenceItem> {
    val byId = associateBy { it.id }
    val enabledIds = enabledCsv
        .split(',')
        .map { it.trim() }
        .filter { it.isNotBlank() }
    val enabled = enabledIds.mapNotNull { id -> byId[id]?.copy(enabled = true) }
    val disabled = filterNot { it.id in enabledIds }.map { it.copy(enabled = false) }
    return enabled + disabled
}

private fun String.csvIdSet(): Set<String> =
    split(',', '，', ';', '；')
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet()

private fun String.parseHomeDisplayColorOrNull(): Color? {
    val normalized = trim().takeIf { it.isNotBlank() } ?: return null
    return runCatching { Color(android.graphics.Color.parseColor(normalized)) }.getOrNull()
}

private fun String.csvIds(defaultValue: String): List<String> {
    val ids = csvIdSet().toList()
    val defaults = defaultValue.csvIdSet().toList()
    return (ids + defaults).distinct()
}

private fun Set<String>.toCsv(): String = sorted().joinToString(",")
