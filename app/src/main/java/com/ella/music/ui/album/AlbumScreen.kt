package com.ella.music.ui.album

import com.ella.music.ui.components.EllaMiuixBottomSheet

import androidx.activity.compose.BackHandler
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.AlbumCard
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LazyGridScrollIndicator
import com.ella.music.ui.components.RestoreGridScrollAfterSearch
import com.ella.music.ui.components.ShuffleAllSummaryButton
import com.ella.music.ui.components.SideIndexListEndPadding
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.ella.music.data.LibraryAlbumAggregator
import com.ella.music.data.model.albumIdentityId

@Composable
fun AlbumScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onAlbumClick: (albumId: Long, anchorAlbumId: Long?, anchorOffset: Int) -> Unit,
    restoreScrollRequest: Int = 0,
    restoreAnchorAlbumId: Long? = null,
    restoreAnchorOffset: Int = 0
) {
    val context = LocalContext.current
    val albums by mainViewModel.albums.collectAsState()
    val songs by mainViewModel.songs.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    val selection = rememberLibrarySelectionState<Long>()
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var albumMenuTarget by remember { mutableStateOf<Album?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val pinnedAlbumKeys by mainViewModel.settingsManager.pinnedKeysFlow("album").collectAsState(initial = emptyList())
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)
    val sortIndex by mainViewModel.settingsManager.albumListSortIndex.collectAsState(initial = LibrarySortUiState.albumListSortIndex)
    val sortMode = AlbumSortMode.entries.getOrElse(sortIndex) { AlbumSortMode.Name }
    val detailSongSortIndex by mainViewModel.settingsManager.albumDetailSongSortIndex.collectAsState(initial = LibrarySortUiState.albumDetailSongSortIndex)
    val detailSongSortMode = AlbumDetailSongSortMode.entries.getOrElse(detailSongSortIndex) { AlbumDetailSongSortMode.Track }
    val gridColumns by mainViewModel.settingsManager.categoryGridColumns.collectAsState(initial = 2)
    val configuration = LocalConfiguration.current
    val safeGridColumns = if (configuration.smallestScreenWidthDp >= 600) {
        gridColumns.coerceIn(5, 8)
    } else {
        gridColumns.coerceIn(1, 4)
    }
    val scope = rememberCoroutineScope()
    var scrollToTopRequest by remember { mutableStateOf(0) }
    val gridCoversEnabled = true
    val albumDurations = remember(songs) {
        LibraryAlbumAggregator.durationsByAlbumIdentity(songs)
    }
    val representativeSongsByAlbumId = remember(songs) {
        LibraryAlbumAggregator.representativeSongsByAlbumIdentity(songs)
    }

    val filteredAlbums = remember(albums, searchQuery) {
        if (searchQuery.isBlank()) {
            albums
        } else {
            albums.filter {
                it.name.contains(searchQuery, ignoreCase = true) ||
                    it.artist.contains(searchQuery, ignoreCase = true)
            }
        }
    }
    val sortedAlbums = remember(filteredAlbums, sortMode, albumDurations, pinnedAlbumKeys) {
        val sorted = when (sortMode) {
            AlbumSortMode.Name -> filteredAlbums.sortedBy { it.name.musicSortKey() }
            AlbumSortMode.NameDesc -> filteredAlbums.sortedByDescending { it.name.musicSortKey() }
            AlbumSortMode.Artist -> filteredAlbums.sortedWith(
                compareBy<Album> { it.albumArtist.isBlank() && it.artist.isBlank() }
                    .thenBy { it.albumArtist.ifBlank { it.artist }.musicSortKey() }
                    .thenBy { it.name.musicSortKey() }
            )
            AlbumSortMode.ArtistDesc -> filteredAlbums.sortedWith(
                compareBy<Album> { it.albumArtist.isBlank() && it.artist.isBlank() }
                    .thenByDescending { it.albumArtist.ifBlank { it.artist }.musicSortKey() }
                    .thenBy { it.name.musicSortKey() }
            )
            AlbumSortMode.SongCount -> filteredAlbums.sortedByDescending { it.songCount }
            AlbumSortMode.SongCountAsc -> filteredAlbums.sortedBy { it.songCount }
            AlbumSortMode.Duration -> filteredAlbums.sortedByDescending { albumDurations[it.id] ?: 0L }
            AlbumSortMode.DurationAsc -> filteredAlbums.sortedBy { albumDurations[it.id] ?: 0L }
            AlbumSortMode.YearAsc -> filteredAlbums.sortedWith(compareBy<Album> { it.releaseDateSortKey <= 0 }.thenBy { it.releaseDateSortKey }.thenBy { it.name.musicSortKey() })
            AlbumSortMode.YearDesc -> filteredAlbums.sortedWith(compareBy<Album> { it.releaseDateSortKey <= 0 }.thenByDescending { it.releaseDateSortKey }.thenByDescending { it.name.musicSortKey() })
        }
        if (pinnedAlbumKeys.isEmpty()) {
            sorted
        } else {
            val pinnedRank = pinnedAlbumKeys.withIndex().associate { it.value to it.index }
            val pinnedSet = pinnedRank.keys
            val pinned = sorted
                .filter { it.id.toString() in pinnedSet }
                .sortedBy { pinnedRank[it.id.toString()] ?: Int.MAX_VALUE }
            pinned + sorted.filterNot { it.id.toString() in pinnedSet }
        }
    }

    fun selectedAlbumSongs(): List<Song> {
        if (selection.selectedIds.isEmpty()) return emptyList()
        return songs.filter { song -> song.albumIdentityId() in selection.selectedIds }.distinctBy { it.id }
    }
    val sortedAlbumIds = remember(sortedAlbums) {
        sortedAlbums.map { it.id }
    }
    val albumIndexById = remember(sortedAlbums) {
        buildMap {
            sortedAlbums.forEachIndexed { index, album -> put(album.id, index) }
        }
    }
    val selectedVisibleAlbumCount = remember(selection.selectedIds, sortedAlbums) {
        sortedAlbums.count { it.id in selection.selectedIds }
    }
    val rangeSelectionAvailable = remember(selection.selectedIds, selection.rangeAnchorId, selection.rangeTargetId, albumIndexById) {
        selection.isRangeSelectionAvailable(albumIndexById)
    }
    val randomAlbumSongs = remember(sortedAlbums, songs) {
        val visibleAlbumIds = sortedAlbums.mapTo(mutableSetOf()) { it.id }
        songs.filter { it.albumIdentityId() in visibleAlbumIds }.distinctBy { it.id }
    }

    BackHandler(enabled = selection.selectionMode || searchExpanded || sortExpanded) {
        when {
            selection.selectionMode -> selection.finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
        }
    }
    LaunchedEffect(selection.selectionMode, sortedAlbums) {
        if (!selection.selectionMode) return@LaunchedEffect
        val visibleIds = sortedAlbums.mapTo(mutableSetOf()) { it.id }
        selection.selectedIds = selection.selectedIds.filterTo(mutableSetOf()) { it in visibleIds }
        if (selection.rangeAnchorId !in visibleIds) selection.rangeAnchorId = selection.selectedIds.firstOrNull()
        if (selection.rangeTargetId !in visibleIds) selection.rangeTargetId = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            EllaSmallTopAppBar(
                title = if (selection.selectionMode) {
                    stringResource(R.string.library_selected_fraction, selection.selectedIds.size, sortedAlbums.size)
                } else {
                    stringResource(R.string.tab_album)
                },
                color = ellaPageBackground(),
                navigationIcon = {
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
                },
                titleStartPadding = if (showBackButton || selection.selectionMode) 64.dp else 20.dp,
                titleEndPadding = if (selection.selectionMode) 280.dp else 128.dp,
                onDoubleTapTitle = { scrollToTopRequest++ },
                actions = {
                    if (selection.selectionMode) {
                        IconButton(onClick = {
                            val keys = selection.selectedIdsInSelectionOrder().map(Long::toString)
                            if (keys.isNotEmpty()) {
                                scope.launch { mainViewModel.settingsManager.pinKeysInOrder("album", keys) }
                                selection.finishSelectionMode()
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Pin,
                                contentDescription = stringResource(R.string.common_pin_to_top),
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = {
                            val selectedSongs = selectedAlbumSongs()
                            if (selectedSongs.isNotEmpty()) {
                                playerViewModel.playNext(selectedSongs)
                                selection.finishSelectionMode()
                            }
                        }) {
                            com.ella.music.ui.components.PlayNextActionIcon(
                                contentDescription = stringResource(R.string.song_more_play_next),
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            val selectedSongs = selectedAlbumSongs()
                            if (selectedSongs.isNotEmpty()) playlistPickerSongs = selectedSongs
                        }) {
                            com.ella.music.ui.components.AddToPlaylistActionIcon(
                                contentDescription = stringResource(R.string.player_add_to_playlist),
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            val selectedSongs = selectedAlbumSongs()
                            if (selectedSongs.isNotEmpty()) pendingDeleteSongs = selectedSongs
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = androidx.compose.ui.graphics.Color(0xFFE5484D),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                    IconButton(onClick = {
                        selection.selectionMode = true
                        selection.selectedIds = emptySet()
                        selection.rangeAnchorId = null
                        selection.rangeTargetId = null
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.SelectAll,
                            contentDescription = stringResource(R.string.common_multi_select),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = { searchExpanded = !searchExpanded }) {
                        Icon(
                            imageVector = MiuixIcons.Basic.Search,
                            contentDescription = stringResource(R.string.common_search),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    SortDropdownMenu(
                        items = directionalSortModeDropdownItems(
                            fields = listOf(
                                DirectionalSortModeField(
                                    text = stringResource(R.string.album_sort_name),
                                    ascendingMode = AlbumSortMode.Name,
                                    descendingMode = AlbumSortMode.NameDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.album_sort_artist),
                                    ascendingMode = AlbumSortMode.Artist,
                                    descendingMode = AlbumSortMode.ArtistDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_song_count),
                                    ascendingMode = AlbumSortMode.SongCountAsc,
                                    descendingMode = AlbumSortMode.SongCount
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_duration),
                                    ascendingMode = AlbumSortMode.DurationAsc,
                                    descendingMode = AlbumSortMode.Duration
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_year),
                                    ascendingMode = AlbumSortMode.YearAsc,
                                    descendingMode = AlbumSortMode.YearDesc
                                )
                            ),
                            selectedMode = sortMode,
                            onSelect = { mode ->
                                LibrarySortUiState.albumListSortIndex = mode.ordinal
                                scope.launch { mainViewModel.settingsManager.setAlbumListSortIndex(mode.ordinal) }
                            }
                        )
                    )
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = sortExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
            ) {
                AlbumSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.albumListSortIndex = mode.ordinal
                                scope.launch { mainViewModel.settingsManager.setAlbumListSortIndex(mode.ordinal) }
                                sortExpanded = false
                            }
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(mode.labelRes),
                            fontSize = 14.sp,
                            fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (searchExpanded) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = stringResource(R.string.album_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (albums.isEmpty() && !libraryCacheLoaded) {
            EllaCenteredLoadingIndicator()
        } else if (albums.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.album_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            // Use rememberSaveable so the scroll position survives dock-tab switches (the
            // bottom-dock navigation saves/restores state). A plain rememberLazyGridState would
            // reset to the top every time the user leaves and returns to the album grid, which
            // reads as "the page refreshed".
            val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
            RestoreGridScrollAfterSearch(
                searchExpanded = searchExpanded,
                query = searchQuery,
                gridState = gridState
            )
            // The pin list is restored asynchronously from DataStore. Lazy grids preserve the
            // old first-item key while the list is reordered, leaving newly restored pins above
            // the viewport until the user scrolls. On a fresh album-page entry, explicitly
            // anchor the first resolved pinned ordering at index zero.
            var needsInitialPinnedPosition by remember { mutableStateOf(true) }
            // Navigation restores lazy-grid state by index. Once pinned albums are prepended,
            // that index can point below every pinned row. Track the actual visible album id
            // before opening a child and resolve it again after returning instead.
            // A request can arrive before cache/DataStore-backed pins have finished producing
            // the final ordering. Remember the restored anchor instead of consuming the request
            // immediately, then resolve it again whenever the list changes.
            var restoredRequest by rememberSaveable { mutableStateOf(0) }
            var restoredPinnedSignature by rememberSaveable { mutableStateOf("") }
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            LaunchedEffect(scrollToTopRequest) {
                if (scrollToTopRequest > 0) gridState.animateScrollToItem(0)
            }
            LaunchedEffect(
                restoreScrollRequest,
                restoreAnchorAlbumId,
                restoreAnchorOffset,
                pinnedAlbumKeys,
                sortedAlbums
            ) {
                if (
                    (restoreScrollRequest > restoredRequest ||
                        (restoreScrollRequest == restoredRequest &&
                            restoredPinnedSignature != pinnedAlbumKeys.sorted().joinToString("|"))) &&
                    restoreAnchorAlbumId != null &&
                    sortedAlbums.isNotEmpty()
                ) {
                    val restoredIndex = sortedAlbums.indexOfFirst { it.id == restoreAnchorAlbumId }
                    if (restoredIndex >= 0) {
                        gridState.scrollToItem(restoredIndex, restoreAnchorOffset.coerceAtLeast(0))
                        restoredRequest = restoreScrollRequest
                        restoredPinnedSignature = pinnedAlbumKeys.sorted().joinToString("|")
                        needsInitialPinnedPosition = false
                    }
                }
            }
            LaunchedEffect(pinnedAlbumKeys, sortedAlbums.size, restoreScrollRequest) {
                if (restoreScrollRequest > restoredRequest) {
                    return@LaunchedEffect
                }
                if (
                    needsInitialPinnedPosition &&
                    pinnedAlbumKeys.isNotEmpty() &&
                    sortedAlbums.isNotEmpty()
                ) {
                    gridState.scrollToItem(0)
                    needsInitialPinnedPosition = false
                }
            }
            val fastIndexLetters = remember(sortedAlbums, sortMode) {
                sortedAlbums.map { it.indexLetter(sortMode) }
            }
            val fastIndexTargets = remember(fastIndexLetters) {
                buildMap {
                    fastIndexLetters.forEachIndexed { index, letter -> putIfAbsent(letter, index) }
                }
            }
            val showAlbumSideIndex = sortedAlbums.size > 30

            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    com.ella.music.ui.components.SortSummaryHeader(
                        text = stringResource(
                            R.string.album_list_summary,
                            sortedAlbums.size,
                            com.ella.music.ui.components.sortLabel(sortMode.labelRes, sortMode.isDescending())
                        ),
                        leadingContent = {
                            ShuffleAllSummaryButton(
                                visible = !selection.selectionMode && randomAlbumSongs.isNotEmpty(),
                                onClick = { playerViewModel.setPlaylist(randomAlbumSongs.shuffled(), 0) }
                            )
                        }
                    )

                    LazyVerticalGrid(
                        columns = GridCells.Fixed(safeGridColumns),
                        state = gridState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            end = if (showAlbumSideIndex) SideIndexListEndPadding else 0.dp,
                            bottom = 160.dp
                        )
                    ) {
                        items(
                            items = sortedAlbums,
                            key = { it.id }
                        ) { album ->
                            val representativeSong = representativeSongsByAlbumId[album.id]
                            val albumArtUri = remember(gridCoversEnabled, album.artAlbumId) {
                                album.artAlbumId
                                    .takeIf { gridCoversEnabled && it > 0L }
                                    ?.let(mainViewModel::getAlbumArtUri)
                            }
                            val selected = album.id in selection.selectedIds
                            AlbumCard(
                                album = album,
                                albumArtUri = albumArtUri,
                                representativeSong = representativeSong,
                                loadCoverArt = mainViewModel::getAlbumCoverArtBitmap,
                                summary = album.summaryForSort(context, sortMode, albumDurations[album.id] ?: 0L),
                                selectionMode = selection.selectionMode,
                                selected = selected,
                                isPinned = album.id.toString() in pinnedAlbumKeys,
                                onClick = {
                                    if (selection.selectionMode) {
                                        selection.toggleSelection(album.id)
                                    } else {
                                        onAlbumClick(
                                            album.id,
                                            sortedAlbums.getOrNull(gridState.firstVisibleItemIndex)?.id,
                                            gridState.firstVisibleItemScrollOffset
                                        )
                                    }
                                },
                                onLongClick = {
                                    if (selection.selectionMode) {
                                        selection.toggleSelection(album.id)
                                        return@AlbumCard
                                    }
                                    albumMenuTarget = album
                                }
                            )
                        }
                    }
                }

                if (
                    sortMode in setOf(
                        AlbumSortMode.Name,
                        AlbumSortMode.NameDesc,
                        AlbumSortMode.Artist,
                        AlbumSortMode.ArtistDesc
                    ) && showAlbumSideIndex
                ) {
                    FastIndexBar(
                        letters = fastIndexLetters,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 2.dp),
                        onLetterClick = { letter ->
                            val index = fastIndexTargets[letter]
                            if (index != null) {
                                fastScrollJob?.cancel()
                                fastScrollJob = scope.launch { gridState.scrollToItem(index) }
                            }
                        }
                    )
                } else if (showAlbumSideIndex) {
                    LazyGridScrollIndicator(
                        state = gridState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
                FloatingSelectionControls(
                    visible = selection.selectionMode && sortedAlbums.isNotEmpty(),
                    rangeEnabled = rangeSelectionAvailable,
                    allSelected = sortedAlbums.isNotEmpty() && selectedVisibleAlbumCount == sortedAlbums.size,
                    onRangeSelect = { selection.applyRangeSelection(sortedAlbumIds, albumIndexById) },
                    onSelectAll = { selection.toggleSelectAll(sortedAlbumIds) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
                )
            }
        }
    }

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_add_to_playlist),
            onDismissRequest = { playlistPickerSongs = null }
        ) {
            AddToPlaylistSheet(
                playlists = playlists.sortedWith(
                    compareByDescending<UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }
                        .thenByDescending { it.createdAt }
                ),
                songsToAdd = songsToAdd,
                songCount = songsToAdd.size,
                onDismiss = { playlistPickerSongs = null },
                onCreatePlaylist = {
                    createPlaylistSongs = songsToAdd
                    playlistPickerSongs = null
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd, appendToEnd)
                    }
                    playlistPickerSongs = null
                    selection.finishSelectionMode()
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        CreatePlaylistAndAddSheet(
            onDismiss = { createPlaylistSongs = null },
            onCreate = { playlistName ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, playlistName) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                    createPlaylistSongs = null
                    selection.finishSelectionMode()
                }
            }
        )
    }

    albumMenuTarget?.let { album ->
        val albumKey = album.id.toString()
        val isPinned = albumKey in pinnedAlbumKeys
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = album.name,
            onDismissRequest = { albumMenuTarget = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                EllaMiuixMenuItem(
                    text = stringResource(if (isPinned) R.string.common_unpin else R.string.common_pin_to_top),
                    onClick = {
                        scope.launch { mainViewModel.settingsManager.setPinned("album", albumKey, !isPinned) }
                        albumMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_share),
                    onClick = {
                        shareLocalSongs(context, mainViewModel.getSongsForAlbum(album.id))
                        albumMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_add_to_playlist),
                    onClick = {
                        playlistPickerSongs = mainViewModel.getSongsForAlbum(album.id).sortedForAlbumDetail(detailSongSortMode)
                        albumMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_to_queue),
                    onClick = {
                        playerViewModel.addToPlaylist(mainViewModel.getSongsForAlbum(album.id).sortedForAlbumDetail(detailSongSortMode))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                        albumMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_play_next),
                    onClick = {
                        playerViewModel.playNext(mainViewModel.getSongsForAlbum(album.id).sortedForAlbumDetail(detailSongSortMode))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                        albumMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_desktop_shortcut),
                    onClick = {
                        val ok = requestPinnedEllaShortcut(
                            context = context,
                            id = "album_${album.id}",
                            label = album.name,
                            route = Screen.AlbumDetail.createRoute(album.id)
                        )
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.playlist_shortcut_requested, album.name) else context.getString(R.string.playlist_shortcut_unsupported),
                            Toast.LENGTH_SHORT
                        ).show()
                        albumMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_delete_permanently),
                    danger = true,
                    onClick = {
                        pendingDeleteSongs = mainViewModel.getSongsForAlbum(album.id)
                        albumMenuTarget = null
                    }
                )
            }
        }
    }

    if (pendingDeleteSongs.isNotEmpty()) {
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.song_more_delete_song_title),
            message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
            confirmText = stringResource(R.string.song_more_delete_permanently),
            onDismiss = { pendingDeleteSongs = emptyList() },
            onConfirm = {
                requestDeleteSongs(pendingDeleteSongs)
                pendingDeleteSongs = emptyList()
            }
        )
    }
}
