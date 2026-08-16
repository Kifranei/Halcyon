package com.ella.music.ui.search

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.SongSelectionActionRow
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LibrarySearchTopBar(
    query: String,
    autoFocus: Boolean?,
    showBackButton: Boolean,
    onBack: () -> Unit,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (showBackButton) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        EllaSearchBar(
            query = query,
            onQueryChange = onQueryChange,
            onSearch = onSearch,
            placeholder = stringResource(R.string.library_search_page_placeholder),
            modifier = Modifier.weight(1f),
            autoFocus = autoFocus
        )
    }
}

@Composable
internal fun LibrarySearchFilterBar(
    filter: SearchFilter,
    trimmedQuery: String,
    duplicatesOnlyActive: Boolean,
    songResultsCount: Int,
    albumResultsCount: Int,
    artistResultsCount: Int,
    playlistResultsCount: Int,
    categoryResultsByType: Map<String, List<*>>,
    onFilterChange: (SearchFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        SearchFilter.entries.forEach { item ->
            val baseLabel = stringResource(item.labelRes())
            val itemCount = when (item) {
                SearchFilter.All -> songResultsCount + albumResultsCount + artistResultsCount + playlistResultsCount + categoryResultsByType.values.sumOf { it.size }
                SearchFilter.Songs -> if (filter in listOf(SearchFilter.All, SearchFilter.Songs) || duplicatesOnlyActive) songResultsCount else 0
                SearchFilter.MusicVideos -> if (filter == SearchFilter.MusicVideos) songResultsCount else 0
                SearchFilter.Lyrics -> if (filter == SearchFilter.Lyrics) songResultsCount else 0
                SearchFilter.Albums -> albumResultsCount
                SearchFilter.Artists -> artistResultsCount
                SearchFilter.Playlists -> playlistResultsCount
                SearchFilter.Folders -> categoryResultsByType["folder"].orEmpty().size
                SearchFilter.Composers -> categoryResultsByType["composer"].orEmpty().size
                SearchFilter.Arrangers -> categoryResultsByType["arranger"].orEmpty().size
                SearchFilter.Lyricists -> categoryResultsByType["lyricist"].orEmpty().size
                SearchFilter.Genres -> categoryResultsByType["genre"].orEmpty().size
                SearchFilter.Years -> categoryResultsByType["year"].orEmpty().size
            }
            SearchPill(
                text = if ((trimmedQuery.isNotBlank() || duplicatesOnlyActive) && itemCount > 0) "$baseLabel ($itemCount)" else baseLabel,
                selected = filter == item,
                onClick = { onFilterChange(item) }
            )
        }
    }
}

@Composable
internal fun LibrarySearchContentFilterBar(
    visible: Boolean,
    musicVideoTab: Boolean,
    duplicatesOnly: Boolean,
    onDuplicatesToggle: () -> Unit,
    filters: LibrarySearchContentFilters,
    mvExpanded: Boolean,
    onNoLyricsChange: (Boolean) -> Unit,
    onTtmlLyricsChange: (Boolean) -> Unit,
    onMvExpandedChange: (Boolean) -> Unit,
    onMusicVideoChange: (Boolean) -> Unit,
    onLocalMusicVideoChange: (Boolean) -> Unit,
    onOnlineMusicVideoChange: (Boolean) -> Unit,
    onDynamicCoverChange: (Boolean) -> Unit
) {
    if (!visible) return
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!musicVideoTab) {
                SearchPill(
                    text = stringResource(R.string.library_search_duplicates),
                    selected = duplicatesOnly,
                    onClick = onDuplicatesToggle
                )
                SearchPill(
                    text = stringResource(R.string.library_search_filter_no_lyrics),
                    selected = filters.noLyrics,
                    onClick = { onNoLyricsChange(!filters.noLyrics) }
                )
                SearchPill(
                    text = stringResource(R.string.library_search_filter_ttml_lyrics),
                    selected = filters.ttmlLyrics,
                    onClick = { onTtmlLyricsChange(!filters.ttmlLyrics) }
                )
            }
            SearchPill(
                text = stringResource(R.string.library_search_filter_mv),
                selected = musicVideoTab || filters.musicVideo,
                onClick = {
                    if (musicVideoTab) {
                        onMvExpandedChange(!mvExpanded)
                    } else if (mvExpanded) {
                        onMusicVideoChange(false)
                    } else {
                        onMusicVideoChange(true)
                    }
                }
            )
            if (!musicVideoTab) {
                SearchPill(
                    text = stringResource(R.string.library_search_filter_dynamic_cover),
                    selected = filters.dynamicCover,
                    onClick = { onDynamicCoverChange(!filters.dynamicCover) }
                )
            }
        }
        if (mvExpanded || musicVideoTab) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 28.dp, end = 16.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SearchPill(
                    text = stringResource(R.string.library_search_filter_local_mv),
                    selected = filters.localMusicVideo,
                    onClick = { onLocalMusicVideoChange(!filters.localMusicVideo) }
                )
                SearchPill(
                    text = stringResource(R.string.library_search_filter_online_mv),
                    selected = filters.onlineMusicVideo,
                    onClick = { onOnlineMusicVideoChange(!filters.onlineMusicVideo) }
                )
            }
        }
    }
}

@Composable
internal fun LibrarySearchSelectionToolbar(
    selectedCount: Int,
    totalCount: Int,
    rangeEnabled: Boolean,
    allSelected: Boolean,
    onRangeSelect: () -> Unit,
    onSelectAll: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onFinishSelection: () -> Unit
) {
    SongSelectionActionRow(
        selectedCount = selectedCount,
        totalCount = totalCount,
        rangeEnabled = rangeEnabled,
        allSelected = allSelected,
        onRangeSelect = onRangeSelect,
        onSelectAll = onSelectAll,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPlayNext) {
            com.ella.music.ui.components.PlayNextActionIcon(
                contentDescription = stringResource(R.string.song_more_play_next),
                tint = MiuixTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onAddToPlaylist) {
            com.ella.music.ui.components.AddToPlaylistActionIcon(
                contentDescription = stringResource(R.string.player_add_to_playlist),
                tint = MiuixTheme.colorScheme.primary
            )
        }
        IconButton(onClick = onAddToQueue) {
            Icon(
                imageVector = MiuixIcons.Regular.Playlist,
                contentDescription = stringResource(R.string.common_add_to_queue),
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        }
        IconButton(onClick = onShare) {
            Icon(
                imageVector = MiuixIcons.Regular.Share,
                contentDescription = stringResource(R.string.common_share),
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = MiuixIcons.Regular.Delete,
                contentDescription = stringResource(R.string.common_delete),
                tint = androidx.compose.ui.graphics.Color(0xFFE5484D),
                modifier = Modifier.size(24.dp)
            )
        }
        IconButton(onClick = onFinishSelection) {
            Icon(
                imageVector = MiuixIcons.Regular.Close,
                contentDescription = stringResource(R.string.common_exit_selection),
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
