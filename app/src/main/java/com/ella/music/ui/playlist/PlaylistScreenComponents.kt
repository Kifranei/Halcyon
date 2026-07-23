package com.ella.music.ui.playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.SortDropdownItem
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.ellaPageBackground
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PlaylistScreenTopBar(
    selectionMode: Boolean,
    selectedCount: Int,
    totalCount: Int,
    showBackButton: Boolean,
    sortItems: List<SortDropdownItem>,
    onBackClick: () -> Unit,
    onExportSelectedClick: () -> Unit,
    onPlayNextSelectedClick: () -> Unit,
    onAddSelectedToQueueClick: () -> Unit,
    onAddSelectedToPlaylistClick: () -> Unit,
    onDeleteSelectedClick: () -> Unit,
    onSearchClick: () -> Unit,
    onImportClick: () -> Unit,
    onExportAllClick: () -> Unit,
    onScrollToTop: () -> Unit
) {
    Box {
        EllaSmallTopAppBar(
            title = if (selectionMode) {
                stringResource(R.string.library_selected_fraction, selectedCount, totalCount)
            } else {
                stringResource(R.string.playlist_title)
            },
            color = ellaPageBackground(),
            navigationIcon = {
                if (showBackButton || selectionMode) {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            actions = {
                if (selectionMode) {
                    IconButton(onClick = onExportSelectedClick) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Share,
                            contentDescription = stringResource(R.string.playlist_export_title),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onPlayNextSelectedClick) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Play,
                            contentDescription = stringResource(R.string.song_more_play_next),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onAddSelectedToQueueClick) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Playlist,
                            contentDescription = stringResource(R.string.player_queue_title),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onAddSelectedToPlaylistClick) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Add,
                            contentDescription = stringResource(R.string.song_more_add_to_playlist),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = onDeleteSelectedClick) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = Color(0xFFE5484D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    PlaylistTopBarIcon(
                        icon = MiuixIcons.Regular.Share,
                        contentDescription = stringResource(R.string.playlist_export_all_title),
                        onClick = onExportAllClick
                    )
                    PlaylistTopBarIcon(
                        icon = MiuixIcons.Regular.Download,
                        contentDescription = stringResource(R.string.playlist_import_title),
                        onClick = onImportClick
                    )
                    PlaylistTopBarIcon(
                        icon = MiuixIcons.Basic.Search,
                        contentDescription = stringResource(R.string.common_search),
                        onClick = onSearchClick
                    )
                    SortDropdownMenu(items = sortItems)
                }
            }
        )
        DoubleTapScrollOverlay(
            onDoubleTap = onScrollToTop,
            onClick = onScrollToTop,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            endPadding = 216.dp
        )
    }
}

@Composable
private fun PlaylistTopBarIcon(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
internal fun PlaylistSearchSection(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        EllaSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            placeholder = stringResource(R.string.playlist_search_placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun PlaylistSortSection(
    visible: Boolean,
    selectedMode: PlaylistSortMode,
    onModeSelected: (PlaylistSortMode) -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(),
        exit = shrinkVertically()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            PlaylistSortMode.entries.forEach { mode ->
                Text(
                    text = stringResource(mode.labelRes),
                    fontSize = 14.sp,
                    fontWeight = if (selectedMode == mode) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedMode == mode) {
                        MiuixTheme.colorScheme.primary
                    } else {
                        MiuixTheme.colorScheme.onSurface
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onModeSelected(mode) }
                        .padding(vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
internal fun PlaylistListSummaryRow(
    playlistCount: Int,
    sortMode: PlaylistSortMode,
    selectionMode: Boolean,
    onCreateClick: () -> Unit,
    onSelectAllClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(
                R.string.playlist_list_summary,
                playlistCount,
                stringResource(sortMode.labelRes)
            ),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f)
        )
        if (!selectionMode) {
            PlaylistToolbarChip(
                icon = MiuixIcons.Regular.Add,
                label = stringResource(R.string.playlist_create_title),
                onClick = onCreateClick
            )
            Spacer(modifier = Modifier.width(8.dp))
            PlaylistToolbarChip(
                icon = MiuixIcons.Regular.SelectAll,
                label = stringResource(R.string.common_multi_select),
                onClick = onSelectAllClick
            )
        }
    }
}

@Composable
internal fun PlaylistEmptyMessage(
    searchQuery: String
) {
    Text(
        text = if (searchQuery.isBlank()) {
            stringResource(R.string.playlist_empty_custom)
        } else {
            stringResource(R.string.playlist_empty_search)
        },
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp)
    )
}

@Composable
internal fun PlaylistDragHandle(
    isDragging: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(32.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (isDragging) {
                    MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "\u2630",
            fontSize = 16.sp,
            color = if (isDragging) {
                MiuixTheme.colorScheme.primary
            } else {
                MiuixTheme.colorScheme.onSurfaceVariantSummary
            }
        )
    }
}
