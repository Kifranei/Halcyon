package com.ella.music.ui.settings

import androidx.compose.foundation.background
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
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import sh.calvin.reorderable.ReorderableColumn
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
    val summary: String
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
    tileItems: List<HomePreferenceItem>,
    tileOrder: String,
    hiddenTiles: String,
    onlineItems: List<HomePreferenceItem>,
    onlineOrder: String,
    hiddenOnlineTiles: String,
    tilePinButtonsVisible: Boolean,
    homeCardColor: String,
    homeCardOpacity: Int,
    homeTileColors: String,
    homeTileGradientEnabled: Boolean,
    homeTileGradientStartColor: String,
    highlightKey: String? = null,
    onHiddenSectionsChange: (String) -> Unit,
    onHiddenTilesChange: (String) -> Unit,
    onHiddenOnlineTilesChange: (String) -> Unit,
    onSectionOrderChange: (String) -> Unit,
    onRecentSectionModeChange: (Int) -> Unit,
    onTileOrderChange: (String) -> Unit,
    onOnlineOrderChange: (String) -> Unit,
    onTilePinButtonsVisibleChange: (Boolean) -> Unit,
    onHomeCardColorChange: (String) -> Unit,
    onHomeCardOpacityChange: (Int) -> Unit,
    onHomeTileColorChange: (String, String) -> Unit,
    onHomeTileGradientEnabledChange: (Boolean) -> Unit,
    onHomeTileGradientStartColorChange: (String) -> Unit
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
    val hiddenTileIds = remember(hiddenTiles) { hiddenTiles.csvIdSet() }
    val hiddenOnlineTileIds = remember(hiddenOnlineTiles) { hiddenOnlineTiles.csvIdSet() }
    val tileColorMap = remember(homeTileColors) { homeTileColors.parseHomeTileColorStrings() }
    val highlightTileColors = highlightKey == "home_tile_colors"

    if (highlightTileColors) {
        HomeTileColorSettings(
            tileItems = orderedTiles + orderedOnlineTiles,
            homeCardColor = homeCardColor,
            homeCardOpacity = homeCardOpacity,
            tileColorMap = tileColorMap,
            homeTileGradientEnabled = homeTileGradientEnabled,
            homeTileGradientStartColor = homeTileGradientStartColor,
            highlight = true,
            onHomeCardColorChange = onHomeCardColorChange,
            onHomeCardOpacityChange = onHomeCardOpacityChange,
            onHomeTileColorChange = onHomeTileColorChange,
            onHomeTileGradientEnabledChange = onHomeTileGradientEnabledChange,
            onHomeTileGradientStartColorChange = onHomeTileGradientStartColorChange
        )
    }

    HomeDisplayGroup(
        title = stringResource(R.string.settings_home_sections_title),
        items = orderedSections,
        hiddenIds = hiddenSectionIds,
        highlight = highlightKey == "home_sections",
        onHiddenIdsChange = onHiddenSectionsChange,
        onOrderChange = onSectionOrderChange
    )
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
    HomeDisplayGroup(
        title = null,
        items = orderedTiles,
        hiddenIds = hiddenTileIds,
        highlight = highlightKey == "home_library_tiles",
        onHiddenIdsChange = onHiddenTilesChange,
        onOrderChange = onTileOrderChange
    )
    SmallTitle(text = stringResource(R.string.settings_home_online_grid_title))
    HomeDisplayGroup(
        title = null,
        items = orderedOnlineTiles,
        hiddenIds = hiddenOnlineTileIds,
        highlight = highlightKey == "home_online_tiles",
        onHiddenIdsChange = onHiddenOnlineTilesChange,
        onOrderChange = onOnlineOrderChange
    )
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
            tileItems = orderedTiles + orderedOnlineTiles,
            homeCardColor = homeCardColor,
            homeCardOpacity = homeCardOpacity,
            tileColorMap = tileColorMap,
            homeTileGradientEnabled = homeTileGradientEnabled,
            homeTileGradientStartColor = homeTileGradientStartColor,
            highlight = false,
            onHomeCardColorChange = onHomeCardColorChange,
            onHomeCardOpacityChange = onHomeCardOpacityChange,
            onHomeTileColorChange = onHomeTileColorChange,
            onHomeTileGradientEnabledChange = onHomeTileGradientEnabledChange,
            onHomeTileGradientStartColorChange = onHomeTileGradientStartColorChange
        )
    }
}

@Composable
private fun HomeTileColorSettings(
    tileItems: List<HomePreferenceItem>,
    homeCardColor: String,
    homeCardOpacity: Int,
    tileColorMap: Map<String, String>,
    homeTileGradientEnabled: Boolean,
    homeTileGradientStartColor: String,
    highlight: Boolean,
    onHomeCardColorChange: (String) -> Unit,
    onHomeCardOpacityChange: (Int) -> Unit,
    onHomeTileColorChange: (String, String) -> Unit,
    onHomeTileGradientEnabledChange: (Boolean) -> Unit,
    onHomeTileGradientStartColorChange: (String) -> Unit
) {
    var colorTarget by remember { mutableStateOf<HomeColorTarget?>(null) }
    SmallTitle(text = stringResource(R.string.settings_home_tile_colors_title))
    SettingsCardGroup(highlight = highlight) {
        Column {
            BasicComponent(
                title = stringResource(R.string.settings_home_card_color),
                summary = homeCardColor.ifBlank { stringResource(R.string.settings_home_card_color_default) },
                modifier = Modifier.clickable { colorTarget = HomeColorTarget.Global }
            )
            SettingsIntSliderPreference(
                title = stringResource(R.string.settings_home_card_opacity),
                summary = stringResource(R.string.settings_home_card_opacity_summary),
                value = homeCardOpacity,
                valueRange = 20..100,
                valueText = "$homeCardOpacity%",
                onValueChange = onHomeCardOpacityChange
            )
            SwitchPreference(
                title = stringResource(R.string.settings_home_tile_gradient_enabled),
                summary = stringResource(R.string.settings_home_tile_gradient_enabled_summary),
                checked = homeTileGradientEnabled,
                onCheckedChange = onHomeTileGradientEnabledChange
            )
            BasicComponent(
                title = stringResource(R.string.settings_home_tile_gradient_start_color),
                summary = homeTileGradientStartColor.ifBlank { stringResource(R.string.settings_home_card_color_default) },
                modifier = Modifier.clickable { colorTarget = HomeColorTarget.GradientStart }
            )
            tileItems.forEach { item ->
                HomeTileColorRow(
                    item = item,
                    color = tileColorMap[item.id],
                    onClick = { colorTarget = HomeColorTarget.Tile(item) }
                )
            }
        }
    }

    val target = colorTarget
    EllaMiuixBottomSheet(
        show = target != null,
        title = when (target) {
            HomeColorTarget.Global -> stringResource(R.string.settings_home_card_color)
            HomeColorTarget.GradientStart -> stringResource(R.string.settings_home_tile_gradient_start_color)
            is HomeColorTarget.Tile -> target.item.title
            null -> ""
        },
        onDismissRequest = { colorTarget = null }
    ) {
        val saved = when (target) {
            HomeColorTarget.Global -> homeCardColor
            HomeColorTarget.GradientStart -> homeTileGradientStartColor
            is HomeColorTarget.Tile -> tileColorMap[target.item.id].orEmpty()
            null -> ""
        }
        val currentColor = remember(target, saved) {
            saved.parseHomeDisplayColorOrNull() ?: Color(0xFF2B2B31)
        }
        var pickerColor by remember(target, currentColor) { mutableStateOf(currentColor) }
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
                    val value = "#%08X".format(pickerColor.toArgb())
                    when (target) {
                        HomeColorTarget.Global -> onHomeCardColorChange(value)
                        HomeColorTarget.GradientStart -> onHomeTileGradientStartColorChange(value)
                        is HomeColorTarget.Tile -> onHomeTileColorChange(target.item.id, value)
                        null -> Unit
                    }
                    colorTarget = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.common_confirm))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = {
                    when (target) {
                        HomeColorTarget.Global -> onHomeCardColorChange("")
                        HomeColorTarget.GradientStart -> onHomeTileGradientStartColorChange("")
                        is HomeColorTarget.Tile -> onHomeTileColorChange(target.item.id, "")
                        null -> Unit
                    }
                    colorTarget = null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = stringResource(R.string.common_reset))
            }
        }
    }
}

@Composable
private fun HomeTileColorRow(
    item: HomePreferenceItem,
    color: String?,
    onClick: () -> Unit
) {
    BasicComponent(
        title = item.title,
        summary = color ?: stringResource(R.string.settings_home_tile_color_default),
        modifier = Modifier.clickable(onClick = onClick),
        endActions = {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(color?.parseHomeDisplayColorOrNull() ?: MiuixTheme.colorScheme.primary.copy(alpha = 0.32f))
            )
        }
    )
}

private sealed class HomeColorTarget {
    data object Global : HomeColorTarget()
    data object GradientStart : HomeColorTarget()
    data class Tile(val item: HomePreferenceItem) : HomeColorTarget()
}

@Composable
private fun HomeDisplayGroup(
    title: String?,
    items: List<HomePreferenceItem>,
    hiddenIds: Set<String>,
    highlight: Boolean = false,
    onHiddenIdsChange: (String) -> Unit,
    onOrderChange: (String) -> Unit
) {
    var manualItems by remember(items.map { it.id }.joinToString(",")) { mutableStateOf(items) }

    if (title != null) {
        SmallTitle(text = title)
    }
    SettingsCardGroup(highlight = highlight) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HomeDisplayCommand(
                    text = stringResource(R.string.common_select_all),
                    modifier = Modifier.weight(1f),
                    onClick = { onHiddenIdsChange("") }
                )
                HomeDisplayCommand(
                    text = stringResource(R.string.common_invert_selection),
                    modifier = Modifier.weight(1f),
                    onClick = {
                        val allIds = items.map { it.id }.toSet()
                        val nextHidden = allIds - hiddenIds
                        onHiddenIdsChange(nextHidden.toCsv())
                    }
                )
            }
            ReorderableColumn(
                list = manualItems,
                onSettle = { fromIndex, toIndex ->
                    if (fromIndex !in manualItems.indices || toIndex !in manualItems.indices || fromIndex == toIndex) return@ReorderableColumn
                    manualItems = manualItems.moveItem(fromIndex, toIndex)
                    onOrderChange(manualItems.joinToString(",") { it.id })
                },
                modifier = Modifier.fillMaxWidth()
            ) { _, item, isDragging ->
                val checked = item.id !in hiddenIds
                ReorderableItem {
                    HomeDisplayCheckRow(
                        item = item,
                        checked = checked,
                        dragging = isDragging,
                        modifier = Modifier.longPressDraggableHandle(),
                        onClick = {
                            val nextHidden = if (checked) {
                                hiddenIds + item.id
                            } else {
                                hiddenIds - item.id
                            }
                            onHiddenIdsChange(nextHidden.toCsv())
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HomeDisplayCommand(
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
        onClick = onClick
    ) {
        BasicComponent(
            title = text
        )
    }
}

@Composable
private fun HomeDisplayCheckRow(
    item: HomePreferenceItem,
    checked: Boolean,
    dragging: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    BasicComponent(
        title = item.title,
        summary = item.summary,
        modifier = modifier
            .background(
                if (dragging) MiuixTheme.colorScheme.primary.copy(alpha = 0.08f) else Color.Transparent,
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick),
        endActions = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "☰",
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.width(12.dp))
                if (checked) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Check,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    )
}

@Composable
internal fun LyricSourcePriorityBlock(
    items: List<LyricSourcePreferenceItem>,
    onOrderChange: (String) -> Unit
) {
    var sheetVisible by remember { mutableStateOf(false) }
    var manualItems by remember(items.map { it.id }.joinToString(",")) { mutableStateOf(items) }

    BasicComponent(
        title = stringResource(R.string.settings_lyric_source_priority),
        summary = manualItems.joinToString(" / ") { it.title },
        modifier = Modifier.clickable { sheetVisible = true }
    )

    EllaMiuixBottomSheet(
        show = sheetVisible,
        title = stringResource(R.string.settings_lyric_source_priority),
        onDismissRequest = { sheetVisible = false }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_lyric_source_priority_summary),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            ReorderableColumn(
                list = manualItems,
                onSettle = { fromIndex, toIndex ->
                    if (fromIndex !in manualItems.indices || toIndex !in manualItems.indices || fromIndex == toIndex) return@ReorderableColumn
                    manualItems = manualItems.moveItem(fromIndex, toIndex)
                    onOrderChange(manualItems.joinToString(",") { it.id })
                },
                modifier = Modifier.fillMaxWidth()
            ) { _, item, isDragging ->
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
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
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
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
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

internal fun List<LyricSourcePreferenceItem>.orderedByLyricPriority(priority: String): List<LyricSourcePreferenceItem> {
    val byId = associateBy { it.id }
    val ids = SettingsManager.normalizeLyricSourcePriority(priority).split(',')
    return (ids.mapNotNull { byId[it] } + filterNot { it.id in ids }).distinctBy { it.id }
}

private fun String.csvIdSet(): Set<String> =
    split(',', '，', ';', '；')
        .map { it.trim().lowercase(Locale.ROOT) }
        .filter { it.isNotBlank() }
        .toSet()

private fun String.parseHomeTileColorStrings(): Map<String, String> =
    runCatching {
        val json = JSONObject(this)
        buildMap {
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.optString(key).trim()
                if (value.matches(Regex("""#[0-9A-Fa-f]{8}"""))) put(key, value.uppercase(Locale.ROOT))
            }
        }
    }.getOrDefault(emptyMap())

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
