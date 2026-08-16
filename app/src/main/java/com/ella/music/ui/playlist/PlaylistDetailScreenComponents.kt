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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.AddToPlaylistActionIcon
import com.ella.music.ui.components.PlayNextActionIcon
import com.ella.music.ui.components.ellaPageBackground
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PlaylistDetailTopBar(
    title: String,
    selectionMode: Boolean,
    showRemoveSelected: Boolean,
    showExport: Boolean,
    onNavigationClick: () -> Unit,
    onPlayNextSelectedClick: () -> Unit,
    onAddSelectedClick: () -> Unit,
    onRemoveSelectedClick: () -> Unit,
    onSearchClick: () -> Unit,
    onExportClick: () -> Unit,
    onSelectionModeClick: () -> Unit,
    onDoubleTapTitle: (() -> Unit)? = null
) {
    EllaSmallTopAppBar(
        title = title,
        color = ellaPageBackground(),
        onDoubleTapTitle = onDoubleTapTitle,
        navigationIcon = {
            IconButton(onClick = onNavigationClick) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onSurface
                )
            }
        },
        actions = {
            if (selectionMode) {
                IconButton(onClick = onPlayNextSelectedClick) {
                    PlayNextActionIcon(
                        contentDescription = stringResource(R.string.song_more_play_next),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = onAddSelectedClick) {
                    AddToPlaylistActionIcon(
                        contentDescription = stringResource(R.string.player_add_to_playlist),
                        tint = MiuixTheme.colorScheme.primary
                    )
                }
                if (showRemoveSelected) {
                    IconButton(onClick = onRemoveSelectedClick) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = Color(0xFFE5484D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            } else {
                if (showExport) {
                    IconButton(onClick = onExportClick) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Share,
                            contentDescription = stringResource(R.string.playlist_export_title),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                IconButton(onClick = onSearchClick) {
                    Icon(
                        imageVector = MiuixIcons.Basic.Search,
                        contentDescription = stringResource(R.string.common_search),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                IconButton(onClick = onSelectionModeClick) {
                    Icon(
                        imageVector = MiuixIcons.Regular.SelectAll,
                        contentDescription = stringResource(R.string.common_multi_select),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    )
}

@Composable
internal fun PlaylistDetailSortSection(
    visible: Boolean,
    sortMode: PlaylistSongSortMode,
    onModeSelected: (PlaylistSongSortMode) -> Unit
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
            PlaylistSongSortMode.entries.forEach { mode ->
                Text(
                    text = stringResource(mode.labelRes),
                    fontSize = 14.sp,
                    fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                    color = if (sortMode == mode) {
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
internal fun PlaylistDetailSearchSection(
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
            placeholder = stringResource(R.string.playlist_search_songs_placeholder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}

@Composable
internal fun PlaylistDetailNotFoundState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = stringResource(R.string.playlist_not_found),
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
internal fun PlaylistDetailEmptyState(
    searchQuery: String,
    playlist: UserPlaylist
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 60.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = when {
                searchQuery.isNotBlank() -> stringResource(R.string.playlist_empty_song_search)
                playlist.isFavorites -> stringResource(R.string.playlist_favorites_hint)
                playlist.isFiveStarRating -> stringResource(R.string.playlist_five_star_hint)
                else -> stringResource(R.string.playlist_empty_songs)
            },
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            fontSize = 14.sp
        )
    }
}

@Composable
internal fun PlaylistDetailReorderHandle(
    isDragging: Boolean,
    draggedSelectionCount: Int? = null,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        draggedSelectionCount?.let { count ->
            Text(
                text = stringResource(R.string.playlist_drag_selection_count, count),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MiuixTheme.colorScheme.primary)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
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
}

@Composable
internal fun PlaylistDetailSelectAllFloatingButton(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier
    ) {
        FloatingActionButton(
            onClick = onClick,
            minWidth = 46.dp,
            minHeight = 46.dp,
            containerColor = MiuixTheme.colorScheme.primary
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.SelectAll,
                contentDescription = stringResource(R.string.common_select_all),
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(21.dp)
            )
        }
    }
}
