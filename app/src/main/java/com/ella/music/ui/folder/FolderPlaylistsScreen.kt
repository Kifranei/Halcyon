package com.ella.music.ui.folder

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.ShuffleAllSummaryButton
import com.ella.music.ui.components.ScanRefreshIconButton
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.playlist.ImmediateOrLongPressDragGestureDetector
import com.ella.music.ui.playlist.PlaylistDragHandle
import com.ella.music.ui.playlist.PlaylistToolbarChip
import com.ella.music.ui.playlist.moveSelectedItemsAsBlock
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel

import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.theme.MiuixTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

private data class FolderPlaylistSortAnchor(
    val playlistId: String,
    val offset: Int,
    val targetSortMode: FolderPlaylistSortMode
)

@Composable
fun FolderPlaylistsScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onOpenPlaylist: (String) -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val songs by mainViewModel.songs.collectAsState()
    val playlists by mainViewModel.settingsManager.folderPlaylists.collectAsState(initial = emptyList())
    val customOrderIds by mainViewModel.settingsManager.folderPlaylistCustomOrder.collectAsState(initial = emptyList())
    val persistedSortIndex by mainViewModel.settingsManager.folderPlaylistListSortIndex.collectAsState(
        initial = LibrarySortUiState.folderPlaylistListSortIndex
    )
    val folderDetailSongSortIndex by mainViewModel.settingsManager.folderPlaylistDetailSongSortIndex.collectAsState(initial = 0)
    val folderDetailSongSortMode = FolderPlaylistSongSortMode.entries.getOrElse(folderDetailSongSortIndex) {
        FolderPlaylistSongSortMode.Custom
    }
    val sortIndex = LibrarySortUiState.pendingFolderPlaylistListSortIndex ?: persistedSortIndex
    val sortMode = FolderPlaylistSortMode.entries.getOrElse(sortIndex) { FolderPlaylistSortMode.DateCreatedDesc }
    androidx.compose.runtime.LaunchedEffect(sortIndex) {
        LibrarySortUiState.folderPlaylistListSortIndex = sortIndex
    }
    androidx.compose.runtime.LaunchedEffect(persistedSortIndex) {
        if (LibrarySortUiState.pendingFolderPlaylistListSortIndex == persistedSortIndex) {
            LibrarySortUiState.pendingFolderPlaylistListSortIndex = null
        }
    }
    val availableFolders = remember(songs) { songs.availableFolderPlaylistFolders() }
    var editorTarget by remember { mutableStateOf<FolderPlaylist?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    // Keep an edit draft while reopening the same existing playlist, but explicitly clear every
    // field when creating a new folder playlist. `null` is not a distinct remember key for two
    // consecutive "new" actions, so relying on the target key alone retains the old draft.
    var editorDraftName by remember(editorTarget?.id) { mutableStateOf(editorTarget?.name.orEmpty()) }
    var editorDraftFolders by remember(editorTarget?.id) { mutableStateOf(editorTarget?.folders.orEmpty().toSet()) }
    // Folders that should stay pinned to the top for the duration of this editor session. Unlike
    // editorDraftFolders, this set only grows (new selections are added) and never shrinks when a
    // folder is unchecked — so a folder that was selected when the sheet opened remains pinned even
    // after the user accidentally unchecks it, until the editor target changes.
    var editorPinnedFolders by remember(editorTarget?.id) {
        mutableStateOf(editorTarget?.folders.orEmpty().toSet())
    }
    fun openNewFolderPlaylistEditor() {
        editorTarget = null
        editorDraftName = ""
        editorDraftFolders = emptySet()
        editorPinnedFolders = emptySet()
        showEditor = true
    }
    var pendingDelete by remember { mutableStateOf<FolderPlaylist?>(null) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    RestoreListScrollAfterSearch(
        searchExpanded = searchExpanded,
        query = searchQuery,
        listState = listState
    )
    var moreMenuTarget by remember { mutableStateOf<FolderPlaylist?>(null) }
    var associateFolderPaths by remember { mutableStateOf<List<String>?>(null) }
    var draggedPlaylistId by remember { mutableStateOf<String?>(null) }
    var pressedDragHandlePlaylistId by remember { mutableStateOf<String?>(null) }
    val selection = rememberLibrarySelectionState<String>()
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingBulkDelete by remember { mutableStateOf<List<FolderPlaylist>?>(null) }
    var pendingSortAnchor by remember { mutableStateOf<FolderPlaylistSortAnchor?>(null) }
    val userPlaylists by mainViewModel.playlists.collectAsState()

    val songCountMap = remember(playlists, songs) {
        playlists.associateWith { playlist -> songs.songsForFolderPlaylist(playlist.folders).size }
    }
    val durationMap = remember(playlists, songs) {
        playlists.associateWith { playlist -> songs.songsForFolderPlaylist(playlist.folders).sumOf { it.duration } }
    }
    val coverModelMap = remember(playlists, songs) {
        playlists.associateWith { playlist ->
            songs.songsForFolderPlaylist(playlist.folders).firstOrNull().folderPlaylistCoverModel()
        }
    }
    val sortedPlaylists = remember(playlists, sortMode, songCountMap, durationMap, customOrderIds) {
        playlists.sortedForFolderPlaylists(
            mode = sortMode,
            songCountProvider = { songCountMap[it] ?: 0 },
            durationProvider = { durationMap[it] ?: 0L },
            customOrderIds = customOrderIds
        )
    }
    val customSortedPlaylists = remember(playlists, songCountMap, durationMap, customOrderIds) {
        playlists.sortedForFolderPlaylists(
            mode = FolderPlaylistSortMode.Custom,
            songCountProvider = { songCountMap[it] ?: 0 },
            durationProvider = { durationMap[it] ?: 0L },
            customOrderIds = customOrderIds
        )
    }
    var manualCustomPlaylists by remember { mutableStateOf(customSortedPlaylists) }
    var manualCustomOrderDirty by remember { mutableStateOf(false) }
    LaunchedEffect(customSortedPlaylists) {
        val persistedIds = customSortedPlaylists.map(FolderPlaylist::id)
        if (!manualCustomOrderDirty || persistedIds == manualCustomPlaylists.map(FolderPlaylist::id)) {
            manualCustomPlaylists = customSortedPlaylists
            manualCustomOrderDirty = false
        }
    }
    val reorderEnabled = selection.selectionMode &&
        sortMode == FolderPlaylistSortMode.Custom &&
        searchQuery.isBlank()
    val customPlaylistsSource = if (reorderEnabled) manualCustomPlaylists else sortedPlaylists
    val filteredPlaylists = remember(customPlaylistsSource, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            customPlaylistsSource
        } else {
            customPlaylistsSource.filter { playlist ->
                playlist.name.contains(query, ignoreCase = true) ||
                    playlist.folders.any { folder ->
                        folder.contains(query, ignoreCase = true) ||
                            folder.substringAfterLast('/').contains(query, ignoreCase = true)
                    }
            }
        }
    }
    val draggedSelectionIds = remember(draggedPlaylistId, selection.selectedIds, filteredPlaylists) {
        val draggedId = draggedPlaylistId
        if (draggedId == null || draggedId !in selection.selectedIds) {
            emptySet()
        } else {
            filteredPlaylists
                .filter { it.id in selection.selectedIds }
                .mapTo(mutableSetOf(), FolderPlaylist::id)
        }
    }
    val reorderablePlaylists = remember(filteredPlaylists, draggedPlaylistId, draggedSelectionIds) {
        if (draggedSelectionIds.size <= 1) {
            filteredPlaylists
        } else {
            filteredPlaylists.filter { it.id == draggedPlaylistId || it.id !in draggedSelectionIds }
        }
    }
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (!reorderEnabled) return@rememberReorderableLazyListState
            val fromIndex = from.index
            val toIndex = to.index
            val fromPlaylist = reorderablePlaylists.getOrNull(fromIndex)
                ?: return@rememberReorderableLazyListState
            val toPlaylist = reorderablePlaylists.getOrNull(toIndex)
                ?: return@rememberReorderableLazyListState
            val sourceIndex = manualCustomPlaylists.indexOfFirst { it.id == fromPlaylist.id }
            val targetIndex = manualCustomPlaylists.indexOfFirst { it.id == toPlaylist.id }
            if (sourceIndex !in manualCustomPlaylists.indices || targetIndex !in manualCustomPlaylists.indices) {
                return@rememberReorderableLazyListState
            }
            manualCustomPlaylists = manualCustomPlaylists.moveSelectedItemsAsBlock(
                from = sourceIndex,
                to = targetIndex,
                selectedKeys = selection.selectedIds,
                keyOf = FolderPlaylist::id
            )
            manualCustomOrderDirty = true
        }
    )
    fun persistManualFolderPlaylistOrder() {
        scope.launch {
            mainViewModel.settingsManager.setFolderPlaylistCustomOrder(
                manualCustomPlaylists.map(FolderPlaylist::id)
            )
        }
    }
    val randomFolderPlaylistSongs = remember(filteredPlaylists, songs) {
        filteredPlaylists
            .flatMap { playlist -> songs.songsForFolderPlaylist(playlist.folders) }
            .distinctBy { it.id }
    }

    val editorCoverModel = remember(editorDraftFolders, songs) {
        songs.songsForFolderPlaylist(editorDraftFolders.toList()).firstOrNull().folderPlaylistCoverModel()
    }

    // ---- Multi-select helpers for the folder-playlist list ----
    val displayedPlaylistIds = filteredPlaylists.map { it.id }
    val displayedIndexById = remember(displayedPlaylistIds) {
        buildMap { displayedPlaylistIds.forEachIndexed { index, id -> put(id, index) } }
    }
    val selectedVisibleCount = displayedPlaylistIds.count { it in selection.selectedIds }
    val rangeSelectionAvailable = selection.isRangeSelectionAvailable(displayedIndexById)

    fun selectedSongsFor(playlist: FolderPlaylist): List<Song> =
        songs.songsForFolderPlaylist(playlist.folders).sortedForFolderPlaylistDetail(folderDetailSongSortMode)

    fun preserveListAnchorForSortChange(mode: FolderPlaylistSortMode) {
        // LazyColumn may already be reconciling an earlier sort when the menu is tapped. Its
        // visible key is stable across every ordering, unlike firstVisibleItemIndex.
        val anchorId = listState.layoutInfo.visibleItemsInfo
            .firstOrNull()
            ?.key as? String
            ?: filteredPlaylists.getOrNull(listState.firstVisibleItemIndex)?.id
        val anchorOffset = listState.firstVisibleItemScrollOffset
        // Keep the same card under the reader after sorting rather than retaining a raw index.
        pendingSortAnchor = anchorId?.let { FolderPlaylistSortAnchor(it, anchorOffset, mode) }
        LibrarySortUiState.pendingFolderPlaylistListSortIndex = mode.ordinal
        LibrarySortUiState.folderPlaylistListSortIndex = mode.ordinal
        scope.launch {
            mainViewModel.settingsManager.setFolderPlaylistListSortIndex(mode.ordinal)
        }
    }

    androidx.compose.runtime.LaunchedEffect(filteredPlaylists, pendingSortAnchor, sortMode) {
        val anchor = pendingSortAnchor ?: return@LaunchedEffect
        if (sortMode != anchor.targetSortMode) return@LaunchedEffect
        // Sorting changes the item order during LazyColumn's next layout. Restore after that
        // layout has committed; otherwise its own key reconciliation can overwrite the seek and
        // place the list at its end (#374).
        androidx.compose.runtime.withFrameNanos { }
        androidx.compose.runtime.withFrameNanos { }
        filteredPlaylists.indexOfFirst { it.id == anchor.playlistId }
            .takeIf { it >= 0 }
            ?.let { index -> listState.scrollToItem(index, anchor.offset) }
        pendingSortAnchor = null
    }

    // Unlike the shared toggleSelectAll, deselecting-all here also exits selection mode and the
    // select branch replaces (not adds to) the current selection.
    fun selectAllPlaylists() {
        val ids = displayedPlaylistIds.toSet()
        if (ids.isNotEmpty() && ids.all { it in selection.selectedIds }) {
            selection.finishSelectionMode()
        } else {
            selection.selectedIds = ids
            selection.rangeAnchorId = displayedPlaylistIds.firstOrNull()
            selection.rangeTargetId = displayedPlaylistIds.lastOrNull()
            selection.selectionMode = true
        }
    }

    // Every song contained in the selected folder-playlists, de-duplicated.
    fun selectedActionSongs(): List<Song> =
        filteredPlaylists
            .filter { it.id in selection.selectedIds }
            .flatMap { songs.songsForFolderPlaylist(it.folders).sortedForFolderPlaylistDetail(folderDetailSongSortMode) }
            .distinctBy { it.playlistIdentityKey() }

    BackHandler(enabled = selection.selectionMode || searchExpanded || moreMenuTarget != null || pendingDelete != null || pendingBulkDelete != null || showEditor) {
        when {
            showEditor -> showEditor = false
            pendingDelete != null -> pendingDelete = null
            pendingBulkDelete != null -> pendingBulkDelete = null
            moreMenuTarget != null -> moreMenuTarget = null
            selection.selectionMode -> selection.finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        EllaSmallTopAppBar(
            title = if (selection.selectionMode) {
                stringResource(R.string.library_selected_fraction, selection.selectedIds.size, filteredPlaylists.size)
            } else {
                stringResource(R.string.folder_playlist_title)
            },
            color = ellaPageBackground(),
            titleEndPadding = 192.dp,
            onDoubleTapTitle = { scope.launch { listState.animateScrollToItem(0) } },
            navigationIcon = {
                if (showBackButton || selection.selectionMode) {
                    IconButton(onClick = { if (selection.selectionMode) selection.finishSelectionMode() else onBack() }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            },
            actions = {
                if (selection.selectionMode) {
                    IconButton(onClick = {
                        val keys = selection.selectedIdsInSelectionOrder()
                        if (keys.isNotEmpty()) {
                            scope.launch {
                                val orderedIds = keys + customSortedPlaylists
                                    .map(FolderPlaylist::id)
                                    .filterNot { it in keys }
                                mainViewModel.settingsManager.setFolderPlaylistCustomOrder(orderedIds)
                            }
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
                        val selected = selectedActionSongs()
                        if (selected.isNotEmpty()) playerViewModel.setPlaylist(selected, 0)
                        selection.finishSelectionMode()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Play,
                            contentDescription = stringResource(R.string.common_play),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        val selected = selectedActionSongs()
                        if (selected.isNotEmpty()) {
                            playerViewModel.playNext(selected)
                            Toast.makeText(context, R.string.song_more_added_to_play_next, Toast.LENGTH_SHORT).show()
                            selection.finishSelectionMode()
                        }
                    }) {
                        com.ella.music.ui.components.PlayNextActionIcon(
                            contentDescription = stringResource(R.string.song_more_play_next),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        val selected = selectedActionSongs()
                        if (selected.isNotEmpty()) {
                            playerViewModel.addToPlaylist(selected)
                            Toast.makeText(context, R.string.song_more_added_to_queue, Toast.LENGTH_SHORT).show()
                            selection.finishSelectionMode()
                        }
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Playlist,
                            contentDescription = stringResource(R.string.common_add_to_queue),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        selectedActionSongs().takeIf { it.isNotEmpty() }?.let { playlistPickerSongs = it }
                    }) {
                        com.ella.music.ui.components.AddToPlaylistActionIcon(
                            contentDescription = stringResource(R.string.player_add_to_playlist),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                } else {
                    ScanRefreshIconButton(
                        enabled = true,
                        onScan = { scope.launch { mainViewModel.scanMusic() } },
                        onDeepRescan = { scope.launch { mainViewModel.fullRescanMusic() } }
                    )
                    IconButton(onClick = {
                        searchExpanded = !searchExpanded
                        if (!searchExpanded) searchQuery = ""
                    }) {
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
                                    text = stringResource(R.string.playlist_sort_custom),
                                    ascendingMode = FolderPlaylistSortMode.Custom,
                                    descendingMode = FolderPlaylistSortMode.CustomDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_updated_at),
                                    ascendingMode = FolderPlaylistSortMode.DateUpdatedAsc,
                                    descendingMode = FolderPlaylistSortMode.DateUpdated
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_created_at),
                                    ascendingMode = FolderPlaylistSortMode.DateCreated,
                                    descendingMode = FolderPlaylistSortMode.DateCreatedDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_name),
                                    ascendingMode = FolderPlaylistSortMode.Name,
                                    descendingMode = FolderPlaylistSortMode.NameDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.folder_playlist_sort_folder_count),
                                    ascendingMode = FolderPlaylistSortMode.FolderCountAsc,
                                    descendingMode = FolderPlaylistSortMode.FolderCount
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_song_count),
                                    ascendingMode = FolderPlaylistSortMode.SongCountAsc,
                                    descendingMode = FolderPlaylistSortMode.SongCount
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_duration),
                                    ascendingMode = FolderPlaylistSortMode.DurationAsc,
                                    descendingMode = FolderPlaylistSortMode.Duration
                                )
                            ),
                            selectedMode = sortMode,
                            onSelect = ::preserveListAnchorForSortChange
                        )
                    )
                }
            }
        )

        if (searchExpanded) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = stringResource(R.string.common_search),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (playlists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = stringResource(R.string.folder_playlist_empty),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                FloatingActionButton(
                    onClick = ::openNewFolderPlaylistEditor,
                    minWidth = 46.dp,
                    minHeight = 46.dp,
                    containerColor = MiuixTheme.colorScheme.primary,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = stringResource(R.string.folder_playlist_create),
                        tint = MiuixTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShuffleAllSummaryButton(
                    visible = !selection.selectionMode && randomFolderPlaylistSongs.isNotEmpty(),
                    onClick = { playerViewModel.setPlaylist(randomFolderPlaylistSongs.shuffled(), 0) }
                )
                Text(
                    text = if (selection.selectionMode) {
                        stringResource(R.string.library_selected_fraction, selection.selectedIds.size, filteredPlaylists.size)
                    } else {
                        stringResource(
                            R.string.folder_playlist_list_summary_sorted,
                            filteredPlaylists.size,
                            com.ella.music.ui.components.sortLabel(sortMode.labelRes, sortMode.isDescending())
                        )
                    },
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f)
                )
                if (!selection.selectionMode) {
                    PlaylistToolbarChip(
                        icon = MiuixIcons.Regular.Add,
                        label = stringResource(R.string.common_create),
                        onClick = ::openNewFolderPlaylistEditor
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PlaylistToolbarChip(
                        icon = MiuixIcons.Regular.SelectAll,
                        label = stringResource(R.string.common_multi_select),
                        onClick = {
                            selection.selectionMode = true
                            selection.selectedIds = emptySet()
                        }
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(reorderablePlaylists, key = { _, playlist -> playlist.id }) { _, playlist ->
                    val songCount = songCountMap[playlist] ?: 0
                    val duration = durationMap[playlist] ?: 0L
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = playlist.id
                    ) { isDragging ->
                        val dragHandleModifier = Modifier.draggableHandle(
                            dragGestureDetector = ImmediateOrLongPressDragGestureDetector,
                            onDragStarted = {
                                pressedDragHandlePlaylistId = playlist.id
                                draggedPlaylistId = playlist.id
                            },
                            onDragStopped = {
                                draggedPlaylistId = null
                                pressedDragHandlePlaylistId = null
                                persistManualFolderPlaylistOrder()
                            }
                        )
                        FolderPlaylistCard(
                            playlist = playlist,
                            songCount = songCount,
                            duration = duration,
                            coverModel = coverModelMap[playlist],
                            isPinned = false,
                            selectionMode = selection.selectionMode,
                            selected = playlist.id in selection.selectedIds,
                            draggedSelectionCount = draggedSelectionIds.size.takeIf {
                                isDragging && playlist.id == draggedPlaylistId && it > 1
                            },
                            onClick = {
                                if (selection.selectionMode) selection.toggleSelection(playlist.id)
                                else onOpenPlaylist(playlist.id)
                            },
                            onLongClick = {
                                if (pressedDragHandlePlaylistId == playlist.id) return@FolderPlaylistCard
                                selection.selectionMode = true
                                if (playlist.id !in selection.selectedIds) selection.toggleSelection(playlist.id)
                            },
                            onSync = {
                                scope.launch {
                                    mainViewModel.refreshFolderPlaylistFolders(playlist.folders)
                                    Toast.makeText(context, R.string.folder_playlist_more_refresh, Toast.LENGTH_SHORT).show()
                                }
                            },
                            onMore = { moreMenuTarget = playlist },
                            trailingContent = if (reorderEnabled) {
                                {
                                    PlaylistDragHandle(
                                        isDragging = isDragging,
                                        modifier = dragHandleModifier
                                    )
                                }
                            } else null
                        )
                    }
                }
            }
            FloatingSelectionControls(
                visible = selection.selectionMode && displayedPlaylistIds.isNotEmpty(),
                rangeEnabled = rangeSelectionAvailable,
                allSelected = displayedPlaylistIds.isNotEmpty() && selectedVisibleCount == displayedPlaylistIds.size,
                onRangeSelect = { selection.applyRangeSelection(displayedPlaylistIds, displayedIndexById) },
                onSelectAll = ::selectAllPlaylists,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
            )
            }
        }
    }

    moreMenuTarget?.let { playlist ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = playlist.name,
            onDismissRequest = { moreMenuTarget = null }
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_pin_to_top),
                    onClick = {
                        scope.launch {
                            mainViewModel.settingsManager.setFolderPlaylistCustomOrder(
                                (listOf(playlist.id) + customSortedPlaylists.map(FolderPlaylist::id)).distinct()
                            )
                        }
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.folder_playlist_more_refresh),
                    onClick = {
                        scope.launch { mainViewModel.refreshFolderPlaylistFolders(playlist.folders) }
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.folder_playlist_more_share),
                    onClick = {
                        shareLocalSongs(context, selectedSongsFor(playlist))
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.folder_playlist_associate),
                    onClick = {
                        associateFolderPaths = playlist.folders
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_add_to_playlist),
                    onClick = {
                        playlistPickerSongs = selectedSongsFor(playlist)
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_to_queue),
                    onClick = {
                        playerViewModel.addToPlaylist(selectedSongsFor(playlist))
                        Toast.makeText(context, R.string.song_more_added_to_queue, Toast.LENGTH_SHORT).show()
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_play_next),
                    onClick = {
                        playerViewModel.playNext(selectedSongsFor(playlist))
                        Toast.makeText(context, R.string.song_more_added_to_play_next, Toast.LENGTH_SHORT).show()
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.folder_playlist_edit),
                    onClick = {
                        editorTarget = playlist
                        showEditor = true
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_desktop_shortcut),
                    onClick = {
                        val ok = requestPinnedEllaShortcut(
                            context = context,
                            id = "folder_playlist_${playlist.id}",
                            label = playlist.name,
                            route = Screen.FolderPlaylistDetail.createRoute(playlist.id)
                        )
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.playlist_shortcut_requested, playlist.name)
                            else context.getString(R.string.playlist_shortcut_unsupported),
                            Toast.LENGTH_SHORT
                        ).show()
                        moreMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_delete),
                    danger = true,
                    onClick = {
                        pendingDelete = playlist
                        moreMenuTarget = null
                    }
                )
            }
        }
    }

    FolderPlaylistEditorSheet(
        show = showEditor,
        target = editorTarget,
        availableFolders = availableFolders,
        songs = songs,
        coverModel = editorCoverModel,
        draftName = editorDraftName,
        onDraftNameChange = { editorDraftName = it },
        selectedFolders = editorDraftFolders,
        onSelectedFoldersChange = { editorDraftFolders = it },
        pinnedFolders = editorPinnedFolders,
        onPinnedFoldersChange = { editorPinnedFolders = it },
        onDismiss = { showEditor = false },
        onSave = { target, name, folders ->
            scope.launch {
                val safeName = name.trim()
                val nameExists = playlists.any { playlist ->
                    playlist.id != target?.id && playlist.name.trim().equals(safeName, ignoreCase = true)
                }
                if (nameExists) {
                    Toast.makeText(context, R.string.playlist_name_exists, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val saved = mainViewModel.settingsManager.upsertFolderPlaylist(target?.id, name, folders)
                if (saved == null) {
                    Toast.makeText(context, R.string.folder_playlist_save_failed, Toast.LENGTH_SHORT).show()
                } else {
                    showEditor = false
                }
            }
        }
    )

    associateFolderPaths?.let { sourceFolders ->
        LinkToFolderPlaylistSheet(
            show = true,
            songs = songs,
            selectedFolderCount = sourceFolders.size,
            folderPlaylists = playlists,
            onDismiss = { associateFolderPaths = null },
            onLink = { targets ->
                scope.launch {
                    targets.forEach { target ->
                        val mergedFolders = (target.folders + sourceFolders).distinctBy { it.lowercase() }
                        mainViewModel.settingsManager.upsertFolderPlaylist(
                            playlistId = target.id,
                            name = target.name,
                            folders = mergedFolders
                        )
                    }
                    Toast.makeText(
                        context,
                        if (targets.size == 1) context.getString(R.string.folder_playlist_associate_done, targets.first().name)
                        else context.getString(R.string.folder_playlist_associate_multi_done, targets.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                associateFolderPaths = null
            },
            onCreatePlaylist = { name ->
                scope.launch { mainViewModel.settingsManager.upsertFolderPlaylist(null, name, sourceFolders) }
                associateFolderPaths = null
            }
        )
    }

    pendingDelete?.let { playlist ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.folder_playlist_delete_title),
            message = stringResource(R.string.folder_playlist_delete_message, playlist.name),
            confirmText = stringResource(R.string.common_delete),
            onDismiss = { pendingDelete = null },
            onConfirm = {
                scope.launch { mainViewModel.settingsManager.deleteFolderPlaylist(playlist.id) }
                pendingDelete = null
            }
        )
    }

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_add_to_playlist_title),
            onDismissRequest = { playlistPickerSongs = null }
        ) {
            AddToPlaylistSheet(
                playlists = userPlaylists
                    .sortedWith(compareByDescending<UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }.thenByDescending { it.createdAt }),
                songsToAdd = songsToAdd,
                songCount = songsToAdd.size,
                onDismiss = { playlistPickerSongs = null },
                onCreatePlaylist = {
                    createPlaylistSongs = songsToAdd
                    playlistPickerSongs = null
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { targetPlaylist ->
                        mainViewModel.addSongsToPlaylist(targetPlaylist.id, songsToAdd, appendToEnd)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    playlistPickerSongs = null
                    selection.finishSelectionMode()
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        CreatePlaylistAndAddSheet(
            onDismiss = { createPlaylistSongs = null },
            onCreate = { name ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { targetPlaylist ->
                    mainViewModel.addSongsToPlaylist(targetPlaylist.id, songsToAdd)
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlist_named, targetPlaylist.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    createPlaylistSongs = null
                    selection.finishSelectionMode()
                }
            }
        )
    }

    pendingBulkDelete?.let { targets ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.folder_playlist_delete_title),
            message = stringResource(R.string.folder_playlist_delete_message, targets.joinToString("、") { it.name }),
            confirmText = stringResource(R.string.common_delete),
            onDismiss = { pendingBulkDelete = null },
            onConfirm = {
                scope.launch { targets.forEach { mainViewModel.settingsManager.deleteFolderPlaylist(it.id) } }
                pendingBulkDelete = null
                selection.finishSelectionMode()
            }
        )
    }
}
