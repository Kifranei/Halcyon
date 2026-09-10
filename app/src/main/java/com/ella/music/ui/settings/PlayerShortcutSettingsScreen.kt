@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ella.music.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.actionMenuIcon
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.ReorderableSelectionItem
import com.ella.music.ui.components.ReorderableSelectionSheet
import com.ella.music.ui.player.PlayerQuickActionKind
import com.ella.music.ui.player.QuickActionIcon
import com.ella.music.ui.player.PlayerExtraActionIds
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.preference.ArrowPreference
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Notes
import top.yukonga.miuix.kmp.icon.extended.Stopwatch
import top.yukonga.miuix.kmp.theme.MiuixTheme

enum class PlayerShortcutEditMode {
    NonImmersive4,
    Horizontal5
}

@Composable
fun PlayerShortcutSettingsScreen(
    initialMode: PlayerShortcutEditMode = PlayerShortcutEditMode.Horizontal5,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    var currentMode by remember { mutableStateOf(initialMode) }

    val rawHorizontal by settingsManager.playerShortcutItems.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_SHORTCUT_ITEMS
    )
    val rawNonImmersive by settingsManager.nonImmersivePlayerShortcutItems.collectAsState(
        initial = SettingsManager.DEFAULT_NON_IMMERSIVE_PLAYER_SHORTCUT_ITEMS
    )

    val isNonImmersive = currentMode == PlayerShortcutEditMode.NonImmersive4
    val rawShortcuts = if (isNonImmersive) rawNonImmersive else rawHorizontal
    val maxItems = if (isNonImmersive) SettingsManager.MAX_NON_IMMERSIVE_PLAYER_SHORTCUT_ITEMS else SettingsManager.MAX_PLAYER_SHORTCUT_ITEMS

    // Keep the vertical player menu and the shortcut editor in sync. Remote quality is a
    // player-only action and is intentionally appended here for installs created before it was
    // added to the shared catalog.
    val catalog = remember {
        (ActionMenuIds.playerShortcutCatalog +
            listOf(ActionMenuIds.REMOTE_QUALITY, PlayerExtraActionIds.LYRIC_SHARE)).distinct()
    }
    val selectedIds = remember(rawShortcuts, maxItems) {
        if (rawShortcuts.isBlank()) {
            emptyList()
        } else {
            rawShortcuts.split(',')
                .filter { it.isNotBlank() && it in catalog }
                .distinct()
                .take(maxItems)
        }
    }

    val pageBackground = ellaPageBackground()

    fun save(items: List<String>) {
        scope.launch {
            if (isNonImmersive) {
                settingsManager.setNonImmersivePlayerShortcutItems(items)
            } else {
                settingsManager.setPlayerShortcutItems(items)
            }
        }
    }

    fun reset() {
        scope.launch {
            if (isNonImmersive) {
                settingsManager.resetNonImmersivePlayerShortcutItems()
            } else {
                settingsManager.resetPlayerShortcutItems()
            }
        }
    }

    val systemBarsMode by settingsManager.systemBarsMode.collectAsState(
        initial = SettingsManager.SYSTEM_BARS_MODE_SHOW_BOTH
    )
    val systemBarsReserveSpace by settingsManager.systemBarsReserveSpace.collectAsState(
        initial = SettingsManager.DEFAULT_SYSTEM_BARS_RESERVE_SPACE
    )
    val shouldReserveStatus = systemBarsReserveSpace || systemBarsMode !in setOf(
        SettingsManager.SYSTEM_BARS_MODE_HIDE_STATUS,
        SettingsManager.SYSTEM_BARS_MODE_HIDE_BOTH
    )
    val statusBarHeight = if (shouldReserveStatus) {
        WindowInsets.statusBarsIgnoringVisibility.asPaddingValues().calculateTopPadding()
    } else {
        0.dp
    }
    val topBarHeight = 56.dp + statusBarHeight

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberSettingsScrollState("settings_player_shortcut"))
                .padding(horizontal = 12.dp)
        ) {
            Spacer(modifier = Modifier.height(topBarHeight + 8.dp))

            // Segmented mode tabs
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    PlayerShortcutEditMode.NonImmersive4 to stringResource(R.string.settings_non_immersive_player_shortcuts),
                    PlayerShortcutEditMode.Horizontal5 to stringResource(R.string.settings_player_horizontal_shortcuts)
                ).forEach { (mode, label) ->
                    val isSelected = currentMode == mode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                color = if (isSelected) MiuixTheme.colorScheme.surface else Color.Transparent,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { currentMode = mode }
                            .padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            SmallTitle(text = stringResource(R.string.settings_player_shortcut_preview))
            SettingsCardGroup {
                if (selectedIds.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp, horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isNonImmersive) {
                                stringResource(R.string.settings_non_immersive_player_shortcuts_preview_empty)
                            } else {
                                stringResource(R.string.settings_player_shortcut_preview_empty)
                            },
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        selectedIds.forEach { id ->
                            PlayerShortcutPreviewTile(
                                id = id,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            var showReorderSheet by remember { mutableStateOf(false) }

            SmallTitle(text = stringResource(R.string.settings_player_shortcut_selected))
            SettingsCardGroup {
                ArrowPreference(
                    title = if (isNonImmersive) {
                        stringResource(R.string.settings_non_immersive_player_shortcuts)
                    } else {
                        stringResource(R.string.settings_player_horizontal_shortcuts)
                    },
                    summary = stringResource(
                        R.string.settings_player_shortcut_selection_count,
                        selectedIds.size,
                        maxItems
                    ),
                    onClick = { showReorderSheet = true }
                )
            }

            val allItems = remember(catalog) {
                catalog.map { id ->
                    ReorderableSelectionItem(
                        id = id,
                        title = id,
                        enabled = false
                    )
                }
            }
            val currentSheetItems = remember(selectedIds, allItems, isNonImmersive) {
                val selected = selectedIds.mapNotNull { id ->
                    allItems.find { it.id == id }?.copy(title = id, enabled = true)
                }
                val unselected = allItems.filter { it.id !in selectedIds }
                selected + unselected
            }
            val defaultIds = remember(isNonImmersive) {
                if (isNonImmersive) {
                    SettingsManager.DEFAULT_NON_IMMERSIVE_PLAYER_SHORTCUT_ITEMS.split(',')
                } else {
                    SettingsManager.DEFAULT_PLAYER_SHORTCUT_ITEMS.split(',')
                }
            }
            val defaultSheetItems = remember(defaultIds, allItems) {
                val defSelected = defaultIds.mapNotNull { id ->
                    allItems.find { it.id == id }?.copy(title = id, enabled = true)
                }
                val defUnselected = allItems.filter { it.id !in defaultIds }
                defSelected + defUnselected
            }

            ReorderableSelectionSheet(
                show = showReorderSheet,
                title = if (isNonImmersive) {
                    stringResource(R.string.settings_non_immersive_player_shortcuts)
                } else {
                    stringResource(R.string.settings_player_horizontal_shortcuts)
                },
                subtitle = stringResource(R.string.settings_player_shortcut_available_summary, maxItems),
                items = currentSheetItems.map { it.copy(title = playerShortcutLabel(it.id)) },
                defaultItems = defaultSheetItems.map { it.copy(title = playerShortcutLabel(it.id)) },
                maxSelectCount = maxItems,
                onExceedMaxSelect = {
                    android.widget.Toast.makeText(
                        context,
                        context.getString(R.string.settings_player_shortcut_selection_count, maxItems, maxItems),
                        android.widget.Toast.LENGTH_SHORT
                    ).show()
                },
                onDismissRequest = { showReorderSheet = false },
                onSave = { updated ->
                    val newSelected = updated.filter { it.enabled }.map { it.id }.take(maxItems)
                    save(newSelected)
                    showReorderSheet = false
                },
                onReset = {
                    reset()
                }
            )

            // The always-visible mini-player is drawn above this route. Leave enough scrollable
            // tail space so the reset action can be brought fully above it on every OEM.
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
            Spacer(modifier = Modifier.height(116.dp))
        }

        EllaSmallTopAppBar(
            title = stringResource(R.string.settings_player_shortcut_items),
            color = pageBackground,
            defaultWindowInsetsPadding = shouldReserveStatus,
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            modifier = Modifier.align(Alignment.TopCenter)
        )
    }
}

@Composable
internal fun PlayerShortcutItemIcon(
    id: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    when (id) {
        ActionMenuIds.INFO -> QuickActionIcon(PlayerQuickActionKind.Info, color = tint, modifier = modifier)
        ActionMenuIds.SHARE -> QuickActionIcon(PlayerQuickActionKind.Share, color = tint, modifier = modifier)
        ActionMenuIds.EDIT_TAGS -> QuickActionIcon(PlayerQuickActionKind.Edit, color = tint, modifier = modifier)
        ActionMenuIds.SPEED -> QuickActionIcon(PlayerQuickActionKind.Speed, color = tint, modifier = modifier)
        ActionMenuIds.EQUALIZER -> QuickActionIcon(PlayerQuickActionKind.Equalizer, color = tint, modifier = modifier)
        ActionMenuIds.TIMER -> Icon(
            imageVector = actionMenuIcon(id) ?: MiuixIcons.Regular.Stopwatch,
            contentDescription = null,
            tint = tint,
            modifier = modifier
        )
        ActionMenuIds.ADD_TO_PLAYLIST -> QuickActionIcon(PlayerQuickActionKind.Add, color = tint, modifier = modifier)
        ActionMenuIds.PLAY_NEXT -> QuickActionIcon(PlayerQuickActionKind.PlayNext, color = tint, modifier = modifier)
        else -> {
            val vector = actionMenuIcon(id) ?: MiuixIcons.Regular.Notes
            Icon(
                imageVector = vector,
                contentDescription = null,
                tint = tint,
                modifier = modifier
            )
        }
    }
}

@Composable
internal fun playerShortcutLabel(id: String): String = when (id) {
    ActionMenuIds.SPEED -> stringResource(R.string.player_speed_pitch)
    ActionMenuIds.EQUALIZER -> stringResource(R.string.player_equalizer)
    ActionMenuIds.TIMER -> stringResource(R.string.player_sleep_timer)
    ActionMenuIds.ADD_TO_PLAYLIST -> stringResource(R.string.player_add_to_playlist)
    ActionMenuIds.PLAY_NEXT -> stringResource(R.string.song_more_play_next)
    ActionMenuIds.ADD_TO_QUEUE -> stringResource(R.string.common_add_to_queue)
    ActionMenuIds.SHARE -> stringResource(R.string.common_share)
    ActionMenuIds.AI -> stringResource(R.string.song_more_ai_title)
    ActionMenuIds.INFO -> stringResource(R.string.player_song_info)
    ActionMenuIds.AUDIO_OUTPUT -> stringResource(R.string.player_audio_output_info)
    ActionMenuIds.CASTING -> stringResource(R.string.casting_devices_title)
    ActionMenuIds.AB_REPEAT -> stringResource(R.string.player_repeat_mode)
    ActionMenuIds.REMOTE_QUALITY -> stringResource(R.string.settings_action_menu_remote_quality)
    PlayerExtraActionIds.LYRIC_SHARE -> stringResource(R.string.player_lyric_share)
    ActionMenuIds.LANDSCAPE -> stringResource(R.string.player_landscape_lyrics)
    ActionMenuIds.LYRICS_DISPLAY -> stringResource(R.string.player_lyrics_display)
    ActionMenuIds.SPECTRUM -> stringResource(R.string.song_more_view_spectrum)
    ActionMenuIds.RATING -> stringResource(R.string.song_more_set_rating)
    ActionMenuIds.DYNAMIC_COVER -> stringResource(R.string.player_match_dynamic_cover)
    ActionMenuIds.VISUALIZER -> stringResource(R.string.player_visualizer_settings)
    ActionMenuIds.EDIT_TAGS -> stringResource(R.string.player_edit_metadata)
    ActionMenuIds.LYRIC_TIMING -> stringResource(R.string.player_lyric_timing)
    ActionMenuIds.ONLINE_LYRICS -> stringResource(R.string.player_match_online_lyrics)
    ActionMenuIds.LYRIC_OFFSET -> stringResource(R.string.player_lyric_offset)
    ActionMenuIds.KEEP_SCREEN_ON -> stringResource(R.string.settings_action_menu_keep_screen_on)
    ActionMenuIds.DOWNLOAD -> stringResource(R.string.player_download_lx_song)
    ActionMenuIds.DELETE -> stringResource(R.string.song_more_delete_permanently)
    else -> id
}

@Composable
private fun PlayerShortcutPreviewTile(
    id: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 72.dp)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        PlayerShortcutItemIcon(
            id = id,
            tint = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.82f),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = playerShortcutLabel(id),
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SelectedShortcutRow(
    id: String,
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
        PlayerShortcutItemIcon(
            id = id,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(25.dp)
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        ) {
            Text(
                text = playerShortcutLabel(id),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = stringResource(R.string.settings_player_shortcut_position, position),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 12.sp
            )
        }
        ShortcutActionButton(
            text = "↑",
            contentDescription = stringResource(R.string.common_move_up),
            enabled = canMoveUp,
            onClick = onMoveUp
        )
        ShortcutActionButton(
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
private fun ShortcutActionButton(
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
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun AvailableShortcutTile(
    id: String,
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
        PlayerShortcutItemIcon(
            id = id,
            tint = foreground,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = playerShortcutLabel(id),
            color = foreground,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(start = 10.dp)
                .weight(1f)
        )
        if (selected) {
            Text(
                text = "✓",
                color = MiuixTheme.colorScheme.primary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
        }
    }
}
