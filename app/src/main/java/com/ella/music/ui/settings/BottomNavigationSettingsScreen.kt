package com.ella.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.BottomDockTab
import com.ella.music.R
import com.ella.music.bottomDockTabCatalog
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaSmallTopAppBar
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BottomNavigationSettingsScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    val storedItems by settingsManager.bottomDockItems.collectAsState(
        initial = SettingsManager.DEFAULT_BOTTOM_DOCK_ITEMS.split(',')
    )
    val catalog = bottomDockTabCatalog()
    val selectedIds = storedItems
        .filter { it in catalog }
        .distinct()
        .take(SettingsManager.MAX_BOTTOM_DOCK_ITEMS)
        .ifEmpty { SettingsManager.DEFAULT_BOTTOM_DOCK_ITEMS.split(',') }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val pageBackground = if (isDark) Color(0xFF101014) else Color(0xFFF4F4F7)

    fun save(items: List<String>) {
        scope.launch { settingsManager.setBottomDockItems(items) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_bottom_dock_items),
            color = pageBackground,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            SmallTitle(text = stringResource(R.string.settings_bottom_dock_preview))
            SettingsCardGroup {
                BottomDockPreview(
                    tabs = selectedIds.mapNotNull(catalog::get)
                )
            }

            SmallTitle(text = stringResource(R.string.settings_bottom_dock_selected))
            SettingsCardGroup {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Text(
                        text = stringResource(
                            R.string.settings_bottom_dock_selection_count,
                            selectedIds.size,
                            SettingsManager.MAX_BOTTOM_DOCK_ITEMS
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                    selectedIds.forEachIndexed { index, id ->
                        catalog[id]?.let { tab ->
                            SelectedDockRow(
                                tab = tab,
                                position = index + 1,
                                canMoveUp = index > 0,
                                canMoveDown = index < selectedIds.lastIndex,
                                canRemove = selectedIds.size > 1,
                                onMoveUp = {
                                    val updated = selectedIds.toMutableList()
                                    java.util.Collections.swap(updated, index, index - 1)
                                    save(updated)
                                },
                                onMoveDown = {
                                    val updated = selectedIds.toMutableList()
                                    java.util.Collections.swap(updated, index, index + 1)
                                    save(updated)
                                },
                                onRemove = { save(selectedIds - id) }
                            )
                        }
                    }
                }
            }

            SmallTitle(text = stringResource(R.string.settings_bottom_dock_available))
            SettingsCardGroup {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = stringResource(R.string.settings_bottom_dock_available_summary),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                    catalog.entries.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { (id, tab) ->
                                val selected = id in selectedIds
                                val enabled = selected || selectedIds.size < SettingsManager.MAX_BOTTOM_DOCK_ITEMS
                                AvailableDockTile(
                                    tab = tab,
                                    selected = selected,
                                    enabled = enabled,
                                    onClick = {
                                        when {
                                            selected && selectedIds.size > 1 -> save(selectedIds - id)
                                            !selected && enabled -> save(selectedIds + id)
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowItems.size == 1) Spacer(modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                    }
                }
            }

            SettingsCardGroup {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            save(SettingsManager.DEFAULT_BOTTOM_DOCK_ITEMS.split(','))
                        }
                        .padding(vertical = 17.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.settings_bottom_dock_reset),
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun BottomDockPreview(tabs: List<BottomDockTab>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .height(58.dp)
                .background(
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(29.dp)
                )
                .padding(horizontal = 8.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            tabs.forEachIndexed { index, tab ->
                PreviewItem(
                    icon = tab.icon,
                    label = tab.label,
                    selected = index == 0,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Box(
            modifier = Modifier
                .size(58.dp)
                .background(
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                    shape = RoundedCornerShape(29.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Basic.Search,
                contentDescription = stringResource(R.string.common_search),
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun PreviewItem(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier
) {
    val color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .background(
                    color = if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f) else Color.Transparent,
                    shape = RoundedCornerShape(17.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(21.dp)
            )
        }
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun SelectedDockRow(
    tab: BottomDockTab,
    position: Int,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(25.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = tab.label,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_bottom_dock_position, position),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
        DockActionButton(
            text = "↑",
            contentDescription = stringResource(R.string.common_move_up),
            enabled = canMoveUp,
            onClick = onMoveUp
        )
        DockActionButton(
            text = "↓",
            contentDescription = stringResource(R.string.common_move_down),
            enabled = canMoveDown,
            onClick = onMoveDown
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .alpha(if (canRemove) 1f else 0.28f)
                .clickable(enabled = canRemove, onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Delete,
                contentDescription = stringResource(R.string.common_remove),
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun DockActionButton(
    text: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .alpha(if (enabled) 1f else 0.28f)
            .semantics { this.contentDescription = contentDescription }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = MiuixTheme.colorScheme.primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
        )
    }
}

@Composable
private fun AvailableDockTile(
    tab: BottomDockTab,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val foreground = when {
        selected -> MiuixTheme.colorScheme.primary
        enabled -> MiuixTheme.colorScheme.onSurface
        else -> MiuixTheme.colorScheme.onSurfaceVariantSummary
    }
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.42f)
            .background(
                color = if (selected) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                } else {
                    MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                },
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = null,
            tint = foreground,
            modifier = Modifier.size(23.dp)
        )
        Text(
            text = tab.label,
            color = foreground,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 9.dp)
        )
    }
}
