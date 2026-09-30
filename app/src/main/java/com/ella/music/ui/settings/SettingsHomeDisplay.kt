package com.ella.music.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.ReorderableSelectionItem
import com.ella.music.ui.components.ReorderableSelectionSheet
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.preference.SwitchPreference
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
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
    onlineItems: List<HomePreferenceItem>,
    onlineOrder: String,
    hiddenOnlineTiles: String,
    tilePinButtonsVisible: Boolean,
    highlightKey: String? = null,
    onHiddenSectionsChange: (String) -> Unit,
    onHiddenOnlineTilesChange: (String) -> Unit,
    onSectionOrderChange: (String) -> Unit,
    onRecentSectionModeChange: (Int) -> Unit,
    onTopBarActionOrderChange: (String) -> Unit,
    onHiddenTopBarActionsChange: (String) -> Unit,
    onOnlineOrderChange: (String) -> Unit,
    onTilePinButtonsVisibleChange: (Boolean) -> Unit,
) {
    val styleContext = androidx.compose.ui.platform.LocalContext.current
    val styleSettings = remember(styleContext) { SettingsManager.getInstance(styleContext) }
    val styleScope = androidx.compose.runtime.rememberCoroutineScope()
    val featureStyle by styleSettings.homeFeatureStyle.collectAsState(initial = 0)
    val featureStyleLabels = listOf(
        stringResource(R.string.home_feature_style_default),
        stringResource(R.string.home_feature_style_transparent),
        stringResource(R.string.home_feature_style_outline)
    )
    SettingsCardGroup {
        // search-anchor:start
        SettingsSearchAnchor(R.string.settings_home_feature_style) {
        WindowSpinnerPreference(
            title = stringResource(R.string.settings_home_feature_style),
            items = featureStyleLabels.map { DropdownItem(title = it) },
            selectedIndex = featureStyle,
            onSelectedIndexChange = { index -> styleScope.launch { styleSettings.setHomeFeatureStyle(index) } }
        )
        } // search-anchor:end
    }
    val orderedSections = remember(sectionItems, sectionOrder) {
        sectionItems.orderedByCsv(sectionOrder, SettingsManager.DEFAULT_HOME_SECTION_ORDER)
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
    val hiddenOnlineTileIds = remember(hiddenOnlineTiles) { hiddenOnlineTiles.csvIdSet() }

    var showSectionSheet by remember { mutableStateOf(false) }
    var showTopBarActionSheet by remember { mutableStateOf(false) }
    var showOnlineTileSheet by remember { mutableStateOf(false) }

    SmallTitle(text = stringResource(R.string.settings_home_top_actions_title))
    SettingsCardGroup(highlight = highlightKey == "home_top_actions") {
        val enabledTopBarActionTitles = orderedTopBarActions
            .filter { it.id !in hiddenTopBarActionIds }
            .map { it.title }
        // search-anchor:start
        SettingsSearchAnchor(R.string.settings_home_top_actions_custom_title) {
        ArrowPreference(
            title = stringResource(R.string.settings_home_top_actions_custom_title),
            summary = if (enabledTopBarActionTitles.isNotEmpty()) {
                enabledTopBarActionTitles.joinToString(" / ")
            } else {
                stringResource(R.string.custom_sort_or_hide_summary)
            },
            onClick = { showTopBarActionSheet = true }
        )
        } // search-anchor:end

    }
    SmallTitle(text = stringResource(R.string.settings_home_sections_title))
    SettingsCardGroup(highlight = highlightKey == "home_sections") {
        val enabledSectionTitles = orderedSections.filter { it.id !in hiddenSectionIds }.map { it.title }
        // search-anchor:start
        SettingsSearchAnchor(R.string.settings_home_sections_custom_title) {
        ArrowPreference(
            title = stringResource(R.string.settings_home_sections_custom_title),
            summary = if (enabledSectionTitles.isNotEmpty()) {
                enabledSectionTitles.joinToString(" / ")
            } else {
                stringResource(R.string.custom_sort_or_hide_summary)
            },
            onClick = { showSectionSheet = true }
        )
        } // search-anchor:end

    }
    SettingsCardGroup(highlight = highlightKey == "home_recent_section_mode") {
        // search-anchor:start
        SettingsSearchAnchor(R.string.settings_home_recent_content) {
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
        } // search-anchor:end

    }
    HomeTileEditors(tileItems, highlightKey)
    SmallTitle(text = stringResource(R.string.settings_home_online_grid_title))
    SettingsCardGroup(highlight = highlightKey == "home_online_tiles") {
        val enabledOnlineTitles = orderedOnlineTiles.filter { it.id !in hiddenOnlineTileIds }.map { it.title }
        // search-anchor:start
        SettingsSearchAnchor(R.string.settings_home_online_tiles_custom_title) {
        ArrowPreference(
            title = stringResource(R.string.settings_home_online_tiles_custom_title),
            summary = if (enabledOnlineTitles.isNotEmpty()) {
                enabledOnlineTitles.joinToString(" / ")
            } else {
                stringResource(R.string.custom_sort_or_hide_summary)
            },
            onClick = { showOnlineTileSheet = true }
        )
        } // search-anchor:end

    }
    SettingsCardGroup(highlight = highlightKey == "home_tile_pin_buttons") {
        // search-anchor:start
        SettingsSearchAnchor(R.string.settings_home_tile_pin_buttons) {
        SwitchPreference(
            title = stringResource(R.string.settings_home_tile_pin_buttons),
            summary = stringResource(R.string.settings_home_tile_pin_buttons_summary),
            checked = tilePinButtonsVisible,
            onCheckedChange = onTilePinButtonsVisibleChange
        )
        } // search-anchor:end

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

private fun String.csvIds(defaultValue: String): List<String> {
    val ids = csvIdSet().toList()
    val defaults = defaultValue.csvIdSet().toList()
    return (ids + defaults).distinct()
}

private fun Set<String>.toCsv(): String = sorted().joinToString(",")

@Composable
private fun HomeTileEditors(items: List<HomePreferenceItem>, highlightKey: String?) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember(context) { SettingsManager.getInstance(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val shortcuts by settings.homeShortcutItems.collectAsState(initial = "artist,album,recent_playback,folder")
    val features by settings.homeFeatureItems.collectAsState(initial = "folder_tree,folder_playlist,playlist,genre,year,composer,arranger,lyricist")
    listOf(true, false).forEach { shortcut ->
        val selected = if (shortcut) shortcuts else features
        val ids = selected.split(',').filter { it.isNotBlank() }
        val defaults = SettingsManager.DEFAULT_HOME_LIBRARY_TILE_ORDER.split(',').let { if (shortcut) it.take(4) else it.drop(4) }
        val ordered = (ids.mapNotNull { id -> items.find { it.id == id } } + items.filter { it.id !in ids })
        var show by remember { mutableStateOf(false) }
        val title = stringResource(if (shortcut) R.string.settings_home_shortcuts else R.string.settings_home_features)
        SettingsCardGroup(highlight = highlightKey == (if (shortcut) "home_shortcuts" else "home_features") || highlightKey == "home_library_tiles") {
            ArrowPreference(title = title,
                summary = ordered.filter { it.id in ids }.joinToString(" / ") { it.title }.ifBlank { stringResource(R.string.custom_sort_or_hide_summary) },
                onClick = { show = true })
        }
        ReorderableSelectionSheet(
            show = show, title = title,
            items = ordered.map { ReorderableSelectionItem(it.id, it.title, it.summary, it.id in ids) },
            defaultItems = (defaults.mapNotNull { id -> items.find { it.id == id } } + items.filter { it.id !in defaults })
                .map { ReorderableSelectionItem(it.id, it.title, it.summary, it.id in defaults) },
            onDismissRequest = { show = false },
            onSave = { updated ->
                scope.launch {
                    val value = updated.filter { it.enabled }.joinToString(",") { it.id }
                    if (shortcut) settings.setHomeShortcutItems(value) else settings.setHomeFeatureItems(value)
                }
            }
        )
    }
}
