package com.ella.music.ui.playlist

import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.ella.music.R
import com.ella.music.data.model.FIVE_STAR_PLAYLIST_ID
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.PlaylistExportFormat
import com.ella.music.data.PlaylistImportMode
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.SideIndexListEndPadding
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.ui.folder.musicSortKey
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.settings.findComponentActivity
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistScreen(
    mainViewModel: MainViewModel,
    playerViewModel: com.ella.music.viewmodel.PlayerViewModel,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onPlaylistClick: (String) -> Unit
) {
    val context = LocalContext.current
    val playlists by mainViewModel.playlists.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val ratingRevision by mainViewModel.ratingRevision.collectAsState()
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    var showCreateDialog by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val persistedPlaylistSortIndex by mainViewModel.settingsManager.playlistListSortIndex.collectAsState(initial = LibrarySortUiState.playlistListSortIndex)
    val playlistCustomOrderIds by mainViewModel.settingsManager.playlistCustomOrder.collectAsState(
        initial = LibrarySortUiState.playlistCustomOrderIds
    )
    val specialPlaylistEntriesVisible by mainViewModel.settingsManager.playlistSpecialEntriesVisible.collectAsState(initial = false)
    val playlistSortIndex = LibrarySortUiState.pendingPlaylistListSortIndex ?: persistedPlaylistSortIndex
    val playlistSortMode = PlaylistSortMode.entries.getOrElse(playlistSortIndex) { PlaylistSortMode.UpdatedAt }
    LaunchedEffect(playlistSortIndex) {
        LibrarySortUiState.playlistListSortIndex = playlistSortIndex
    }
    LaunchedEffect(persistedPlaylistSortIndex) {
        if (LibrarySortUiState.pendingPlaylistListSortIndex == persistedPlaylistSortIndex) {
            LibrarySortUiState.pendingPlaylistListSortIndex = null
        }
    }
    LaunchedEffect(playlistCustomOrderIds) {
        LibrarySortUiState.playlistCustomOrderIds = playlistCustomOrderIds
    }
    var pendingImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var showImportModeSheet by remember { mutableStateOf(false) }
    var playlistPendingDelete by remember { mutableStateOf<UserPlaylist?>(null) }
    var playlistsPendingDelete by remember { mutableStateOf<List<UserPlaylist>>(emptyList()) }
    val selection = rememberLibrarySelectionState<String>()
    var draggedPlaylistId by remember { mutableStateOf<String?>(null) }
    var pressedDragHandlePlaylistId by remember { mutableStateOf<String?>(null) }
    var showExportAllFormatSheet by remember { mutableStateOf(false) }
    var pendingExportAllFormat by remember { mutableStateOf<PlaylistExportFormat?>(null) }
    var playlistMenuTarget by remember { mutableStateOf<UserPlaylist?>(null) }
    var playlistToRename by remember { mutableStateOf<UserPlaylist?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<com.ella.music.data.model.Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<com.ella.music.data.model.Song>?>(null) }
    var playlistsToExport by remember { mutableStateOf<List<UserPlaylist>>(emptyList()) }
    var showExportFormatSheet by remember { mutableStateOf(false) }
    var pendingExportFormat by remember { mutableStateOf<PlaylistExportFormat?>(null) }
    val listState = rememberLazyListState()
    RestoreListScrollAfterSearch(
        searchExpanded = searchExpanded,
        query = searchQuery,
        listState = listState
    )
    val scope = rememberCoroutineScope()
    val saveScope = context.findComponentActivity()?.lifecycleScope ?: scope
    val favorites = playlists.firstOrNull { it.id == FAVORITES_PLAYLIST_ID }
    val storedCustomPlaylists = remember(playlists) {
        playlists.filterNot { it.id == FAVORITES_PLAYLIST_ID || it.id == FIVE_STAR_PLAYLIST_ID }
    }
    val orderedCustomPlaylists = remember(storedCustomPlaylists, playlistCustomOrderIds) {
        storedCustomPlaylists.applyPlaylistCustomOrder(playlistCustomOrderIds)
    }
    // Keep the drag result locally until its exact order is observed from persistence. Recreating
    // this state for every store/DataStore emission can otherwise restore an earlier drag while a
    // second drag of the same playlist is in progress.
    var manualCustomPlaylists by remember { mutableStateOf(orderedCustomPlaylists) }
    var manualCustomOrderDirty by remember { mutableStateOf(false) }
    LaunchedEffect(orderedCustomPlaylists) {
        val persistedIds = orderedCustomPlaylists.map(UserPlaylist::id)
        if (
            shouldApplyPersistedPlaylistOrder(
                localOrderDirty = manualCustomOrderDirty,
                persistedIds = persistedIds,
                localIds = manualCustomPlaylists.map(UserPlaylist::id)
            )
        ) {
            manualCustomPlaylists = orderedCustomPlaylists
            manualCustomOrderDirty = false
        }
    }
    val customPlaylists = remember(storedCustomPlaylists, orderedCustomPlaylists, playlistSortMode) {
        when (playlistSortMode) {
            PlaylistSortMode.Custom -> orderedCustomPlaylists
            PlaylistSortMode.CustomDesc -> orderedCustomPlaylists.asReversed()
            else -> storedCustomPlaylists.sortedForPlaylistList(playlistSortMode)
        }
    }
    val reorderEnabled = selection.selectionMode &&
        playlistSortMode == PlaylistSortMode.Custom &&
        searchQuery.isBlank() &&
        customPlaylists.none { it.isRemote }
    val customPlaylistsSource = if (reorderEnabled) manualCustomPlaylists else customPlaylists
    val displayedCustomPlaylists = remember(customPlaylistsSource, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) customPlaylistsSource else customPlaylistsSource.filter { it.matchesPlaylistSearch(query) }
    }
    val draggedSelectionIds = remember(draggedPlaylistId, selection.selectedIds, displayedCustomPlaylists) {
        val draggedId = draggedPlaylistId
        if (draggedId == null || draggedId !in selection.selectedIds) {
            emptySet()
        } else {
            displayedCustomPlaylists
                .filter { it.id in selection.selectedIds && !it.isRemote }
                .mapTo(mutableSetOf()) { it.id }
        }
    }
    // During a multi-selection drag, keep only the actively dragged playlist in the lazy list.
    // This gives the selection one physical drag target while the full list remains the source of
    // truth for the block-move policy below.
    val reorderablePlaylists = remember(displayedCustomPlaylists, draggedPlaylistId, draggedSelectionIds) {
        if (draggedSelectionIds.size <= 1) {
            displayedCustomPlaylists
        } else {
            displayedCustomPlaylists.filter { playlist ->
                playlist.id == draggedPlaylistId || playlist.id !in draggedSelectionIds
            }
        }
    }
    val randomPlaylistSongs = remember(displayedCustomPlaylists, librarySongs) {
        displayedCustomPlaylists
            .flatMap { mainViewModel.playlistSongs(it) }
            .distinctBy { it.id }
    }
    val playlistCoverModels = remember(playlists, librarySongs) {
        playlists.associate { playlist ->
            playlist.id to mainViewModel.playlistSongs(playlist).firstOrNull().playlistCoverModel()
        }
    }
    val showFavorites = remember(favorites, searchQuery, specialPlaylistEntriesVisible) {
        specialPlaylistEntriesVisible &&
            favorites != null &&
            (searchQuery.isBlank() || favorites.matchesPlaylistSearch(searchQuery.trim()))
    }
    val fiveStarName = stringResource(R.string.playlist_five_star_name)
    val showFiveStar = remember(searchQuery, fiveStarName, specialPlaylistEntriesVisible) {
        specialPlaylistEntriesVisible &&
            (searchQuery.isBlank() || fiveStarName.contains(searchQuery.trim(), ignoreCase = true))
    }
    val fiveStarSongs by produceState(initialValue = emptyList(), librarySongs, ratingRevision) {
        value = mainViewModel.getFiveStarSongs()
    }
    val fiveStarCoverModel = remember(fiveStarSongs) {
        fiveStarSongs.firstOrNull().playlistCoverModel()
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        pendingImportUris = uris
        showImportModeSheet = true
    }
    val exportAllFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val format = pendingExportAllFormat
        pendingExportAllFormat = null
        if (uri == null || format == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        mainViewModel.exportLocalPlaylists(storedCustomPlaylists, uri, format) { result ->
            result
                .onSuccess { exportResult ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.playlist_export_all_done,
                            exportResult.exportedPlaylists,
                            exportResult.exportedSongs
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        context.getString(R.string.playlist_export_failed, it.message.orEmpty()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    val exportPlaylistFolderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val format = pendingExportFormat
        val exportTargets = playlistsToExport
        pendingExportFormat = null
        playlistsToExport = emptyList()
        if (uri == null || format == null || exportTargets.isEmpty()) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        mainViewModel.exportLocalPlaylists(exportTargets, uri, format) { result ->
            result
                .onSuccess { exportResult ->
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.playlist_export_all_done,
                            exportResult.exportedPlaylists,
                            exportResult.exportedSongs
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        context.getString(R.string.playlist_export_failed, it.message.orEmpty()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    fun importPendingPlaylists(mode: PlaylistImportMode) {
        val uris = pendingImportUris
        if (uris.isEmpty()) return
        showImportModeSheet = false
        pendingImportUris = emptyList()
        mainViewModel.importLocalPlaylists(uris, mode) { result ->
            result
                .onSuccess { importResult ->
                    val message = if (importResult.importedCount == 0) {
                        context.getString(R.string.playlist_import_none)
                    } else {
                        val missingText = if (importResult.missingCount > 0) {
                            context.getString(
                                R.string.playlist_import_missing_paths,
                                importResult.missingCount
                            )
                        } else ""
                        val duplicateText = if (importResult.duplicateCount > 0) {
                            context.getString(
                                R.string.playlist_import_duplicates,
                                importResult.duplicateCount
                            )
                        } else ""
                        val playlistText = if (importResult.importedPlaylists > 1) {
                            context.getString(
                                R.string.playlist_import_playlist_prefix,
                                importResult.importedPlaylists
                            )
                        } else ""
                        context.getString(
                            R.string.playlist_import_result,
                            playlistText,
                            importResult.importedCount,
                            importResult.matchedCount,
                            missingText,
                            duplicateText
                        )
                    }
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        context.getString(
                            R.string.playlist_import_failed,
                            it.message.orEmpty()
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
    }
    fun finishSelectionMode() {
        selection.finishSelectionMode()
        draggedPlaylistId = null
    }
    fun togglePlaylistSelection(playlist: UserPlaylist) {
        if (playlist.isRemote) return
        selection.toggleSelection(playlist.id)
    }
    fun selectedPlaylists(): List<UserPlaylist> =
        displayedCustomPlaylists.filter { !it.isRemote && it.id in selection.selectedIds }
    fun selectedPlaylistSongs(): List<com.ella.music.data.model.Song> =
        selectedPlaylists()
            .flatMap { mainViewModel.playlistSongs(it) }
            .distinctBy { it.id }
    val playlistIndexById = remember(displayedCustomPlaylists) {
        buildMap {
            displayedCustomPlaylists.forEachIndexed { index, playlist -> put(playlist.id, index) }
        }
    }
    val selectedVisiblePlaylistCount = remember(selection.selectedIds, displayedCustomPlaylists) {
        displayedCustomPlaylists.count { it.id in selection.selectedIds }
    }
    val playlistRangeSelectionAvailable = remember(
        playlistIndexById,
        selection.selectedIds,
        selection.rangeAnchorId,
        selection.rangeTargetId
    ) {
        selection.isRangeSelectionAvailable(playlistIndexById)
    }
    fun applyPlaylistRangeSelection() {
        val anchor = selection.rangeAnchorId ?: return
        val target = selection.rangeTargetId ?: return
        val anchorIndex = playlistIndexById[anchor] ?: return
        val targetIndex = playlistIndexById[target] ?: return
        if (anchorIndex == targetIndex) return
        val bounds = if (anchorIndex < targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
        selection.selectedIds = selection.selectedIds + bounds
            .map { displayedCustomPlaylists[it] }
            .filterNot { it.isRemote }
            .map { it.id }
        // A range action completes the current anchor/target gesture. The next two taps must
        // start a fresh range instead of extending the previous one (#246).
        selection.rangeAnchorId = null
        selection.rangeTargetId = null
    }
    fun persistManualPlaylistOrder() {
        mainViewModel.reorderPlaylists(manualCustomPlaylists.map(UserPlaylist::id))
    }
    val playlistListHeaderCount = (if (showFavorites) 1 else 0) + (if (showFiveStar) 1 else 0) + 1
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (!reorderEnabled) return@rememberReorderableLazyListState
            val fromIndex = from.index - playlistListHeaderCount
            val toIndex = to.index - playlistListHeaderCount
            val fromPlaylist = reorderablePlaylists.getOrNull(fromIndex) ?: return@rememberReorderableLazyListState
            val toPlaylist = reorderablePlaylists.getOrNull(toIndex) ?: return@rememberReorderableLazyListState
            val sourceIndex = manualCustomPlaylists.indexOfFirst { it.id == fromPlaylist.id }
            val targetIndex = manualCustomPlaylists.indexOfFirst { it.id == toPlaylist.id }
            if (sourceIndex !in manualCustomPlaylists.indices || targetIndex !in manualCustomPlaylists.indices) {
                return@rememberReorderableLazyListState
            }
            manualCustomPlaylists = manualCustomPlaylists.moveSelectedItemsAsBlock(
                from = sourceIndex,
                to = targetIndex,
                selectedKeys = selection.selectedIds,
                keyOf = UserPlaylist::id
            )
            manualCustomOrderDirty = true
        }
    )

    BackHandler(enabled = selection.selectionMode || sortExpanded || searchExpanded) {
        when {
            selection.selectionMode -> finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
        }
    }
    LaunchedEffect(selection.selectionMode, displayedCustomPlaylists) {
        if (!selection.selectionMode) return@LaunchedEffect
        val visibleIds = displayedCustomPlaylists.mapTo(mutableSetOf()) { it.id }
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
            PlaylistScreenTopBar(
                selectionMode = selection.selectionMode,
                selectedCount = selection.selectedIds.size,
                totalCount = displayedCustomPlaylists.size,
                showBackButton = showBackButton,
                sortItems = directionalSortModeDropdownItems(
                    fields = listOf(
                        DirectionalSortModeField(
                            text = stringResource(R.string.playlist_sort_custom),
                            ascendingMode = PlaylistSortMode.Custom,
                            descendingMode = PlaylistSortMode.CustomDesc
                        ),
                        DirectionalSortModeField(
                            text = stringResource(R.string.playlist_sort_updated_at),
                            ascendingMode = PlaylistSortMode.UpdatedAtAsc,
                            descendingMode = PlaylistSortMode.UpdatedAt
                        ),
                        DirectionalSortModeField(
                            text = stringResource(R.string.playlist_sort_created_at),
                            ascendingMode = PlaylistSortMode.CreatedAtAsc,
                            descendingMode = PlaylistSortMode.CreatedAt
                        ),
                        DirectionalSortModeField(
                            text = stringResource(R.string.playlist_sort_name),
                            ascendingMode = PlaylistSortMode.Name,
                            descendingMode = PlaylistSortMode.NameDesc
                        ),
                        DirectionalSortModeField(
                            text = stringResource(R.string.playlist_sort_song_count),
                            ascendingMode = PlaylistSortMode.SongCountAsc,
                            descendingMode = PlaylistSortMode.SongCount
                        ),
                        DirectionalSortModeField(
                            text = stringResource(R.string.playlist_sort_duration),
                            ascendingMode = PlaylistSortMode.DurationAsc,
                            descendingMode = PlaylistSortMode.Duration
                        )
                    ),
                    selectedMode = playlistSortMode,
                    onSelect = { mode ->
                        LibrarySortUiState.pendingPlaylistListSortIndex = mode.ordinal
                        LibrarySortUiState.playlistListSortIndex = mode.ordinal
                        saveScope.launch { mainViewModel.settingsManager.setPlaylistListSortIndex(mode.ordinal) }
                    }
                ),
            onBackClick = { if (selection.selectionMode) finishSelectionMode() else onBack() },
            onPinSelectedClick = {
                val keys = selection.selectedIdsInSelectionOrder()
                if (keys.isNotEmpty()) {
                    val orderedIds = keys + storedCustomPlaylists
                        .map(UserPlaylist::id)
                        .filterNot { it in keys }
                    mainViewModel.reorderPlaylists(orderedIds)
                    finishSelectionMode()
                }
            },
            onExportSelectedClick = {
                val targets = selectedPlaylists()
                if (targets.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                } else {
                    playlistsToExport = targets
                    showExportFormatSheet = true
                }
            },
            onPlayNextSelectedClick = {
                val selectedSongs = selectedPlaylistSongs()
                if (selectedSongs.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                } else {
                    playerViewModel.playNext(selectedSongs)
                    Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                    finishSelectionMode()
                }
            },
            onAddSelectedToQueueClick = {
                val selectedSongs = selectedPlaylistSongs()
                if (selectedSongs.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                } else {
                    playerViewModel.addToPlaylist(selectedSongs)
                    Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                    finishSelectionMode()
                }
            },
            onAddSelectedToPlaylistClick = {
                val selectedSongs = selectedPlaylistSongs()
                if (selectedSongs.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                } else {
                    playlistPickerSongs = selectedSongs
                }
            },
            onDeleteSelectedClick = {
                val targets = storedCustomPlaylists.filter { it.id in selection.selectedIds }
                if (targets.isNotEmpty()) playlistsPendingDelete = targets
            },
            onSearchClick = {
                searchExpanded = !searchExpanded
                if (!searchExpanded) searchQuery = ""
            },
            onImportClick = {
                importLauncher.launch(
                    arrayOf(
                        "audio/x-mpegurl",
                        "audio/mpegurl",
                        "application/vnd.apple.mpegurl",
                        "text/plain",
                        "application/octet-stream",
                        "*/*"
                    )
                )
            },
            onExportAllClick = { showExportAllFormatSheet = true },
            onDoubleTapTitle = { scope.launch { listState.animateScrollToItem(0) } }
        )

        PlaylistSearchSection(
            visible = searchExpanded,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { searchExpanded = false }
        )

        PlaylistSortSection(
            visible = sortExpanded,
            selectedMode = playlistSortMode,
        onModeSelected = { mode ->
            sortExpanded = false
            LibrarySortUiState.pendingPlaylistListSortIndex = mode.ordinal
            LibrarySortUiState.playlistListSortIndex = mode.ordinal
            saveScope.launch { mainViewModel.settingsManager.setPlaylistListSortIndex(mode.ordinal) }
        }
        )

        Box(modifier = Modifier.fillMaxSize()) {
        val playlistFastIndexLetters = remember(reorderablePlaylists) {
            reorderablePlaylists.map { it.name.musicSortKey().toFastIndexSection() }
        }
        val playlistFastIndexTargets = remember(playlistFastIndexLetters, playlistListHeaderCount) {
            buildMap {
                playlistFastIndexLetters.forEachIndexed { index, letter ->
                    putIfAbsent(letter, index + playlistListHeaderCount)
                }
            }
        }
        val showPlaylistSideIndex = reorderablePlaylists.size > 30
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = if (showPlaylistSideIndex) SideIndexListEndPadding else 12.dp,
                top = 8.dp,
                bottom = 8.dp
            )
        ) {
            if (favorites != null && showFavorites) {
                item(key = favorites.id) {
                    PlaylistRow(
                        playlist = favorites,
                        coverModel = playlistCoverModels[favorites.id],
                        accent = true,
                        onClick = { onPlaylistClick(favorites.id) }
                    )
                }
            }

            if (showFiveStar) item(key = FIVE_STAR_PLAYLIST_ID) {
                PlaylistRow(
                    playlist = UserPlaylist(
                        id = FIVE_STAR_PLAYLIST_ID,
                        name = stringResource(R.string.playlist_five_star_name),
                        createdAt = 0L,
                        updatedAt = 0L
                    ),
                    coverModel = fiveStarCoverModel,
                    countOverride = fiveStarSongs.size,
                    durationOverride = fiveStarSongs.sumOf { it.duration },
                    accent = true,
                    onClick = { onPlaylistClick(FIVE_STAR_PLAYLIST_ID) }
                )
            }

            item {
                PlaylistListSummaryRow(
                    playlistCount = displayedCustomPlaylists.size,
                    sortMode = playlistSortMode,
                    selectionMode = selection.selectionMode,
                    randomSongsAvailable = randomPlaylistSongs.isNotEmpty(),
                    onShuffleClick = { playerViewModel.setPlaylist(randomPlaylistSongs.shuffled(), 0) },
                    onCreateClick = { showCreateDialog = true },
                    onSelectAllClick = {
                        selection.selectionMode = true
                        selection.selectedIds = emptySet()
                        selection.rangeAnchorId = null
                        selection.rangeTargetId = null
                    }
                )
            }

            if (reorderablePlaylists.isEmpty() && librarySongs.isEmpty() && !libraryCacheLoaded) {
                item {
                    EllaCenteredLoadingIndicator(modifier = Modifier.fillParentMaxSize())
                }
            } else if (reorderablePlaylists.isEmpty()) {
                item {
                    PlaylistEmptyMessage(searchQuery = searchQuery)
                }
            } else {
                itemsIndexed(reorderablePlaylists, key = { _, playlist -> playlist.id }) { _, playlist ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = playlist.id
                    ) { isDragging ->
                        // The reorder handle must not turn an unselected row into a selection.
                        val dragHandleModifier = Modifier
                            .draggableHandle(
                                dragGestureDetector = ImmediateOrLongPressDragGestureDetector,
                                onDragStarted = {
                                    pressedDragHandlePlaylistId = playlist.id
                                    draggedPlaylistId = playlist.id
                                },
                                onDragStopped = {
                                    draggedPlaylistId = null
                                    pressedDragHandlePlaylistId = null
                                    persistManualPlaylistOrder()
                                }
                            )
                        PlaylistRow(
                            playlist = playlist,
                            coverModel = playlistCoverModels[playlist.id],
                            selectionMode = selection.selectionMode,
                            selected = playlist.id in selection.selectedIds,
                            draggedSelectionCount = draggedSelectionIds
                                .size
                                .takeIf { isDragging && playlist.id == draggedPlaylistId && it > 1 },
                            onClick = {
                                if (selection.selectionMode) {
                                    togglePlaylistSelection(playlist)
                                } else {
                                    onPlaylistClick(playlist.id)
                                }
                            },
                            onLongClick = if (playlist.isRemote) {
                                null
                            } else {
                                {
                                    if (pressedDragHandlePlaylistId == playlist.id) return@PlaylistRow
                                    if (selection.selectionMode) {
                                        if (shouldSelectPlaylistOnLongPress(true, playlist.id in selection.selectedIds)) {
                                            togglePlaylistSelection(playlist)
                                        }
                                    } else {
                                        selection.selectionMode = true
                                        selection.selectedIds = selection.selectedIds + playlist.id
                                        selection.updateRangeAnchorsForManualSelection(playlist.id, selectedNow = true)
                                    }
                                }
                            },
                            onMore = if (selection.selectionMode || playlist.isRemote) null else { { playlistMenuTarget = playlist } },
                            trailingContent = if (reorderEnabled) {
                                {
                                    PlaylistDragHandle(
                                        isDragging = isDragging,
                                        modifier = Modifier
                                            .then(dragHandleModifier)
                                    )
                                }
                            } else null
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(150.dp)) }
        }
            if (playlistSortMode == PlaylistSortMode.Name && showPlaylistSideIndex) {
                FastIndexBar(
                    letters = playlistFastIndexLetters,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight(),
                    onLetterClick = { letter ->
                        val index = playlistFastIndexTargets[letter]
                        if (index != null) scope.launch { listState.scrollToItem(index) }
                    }
                )
            } else if (showPlaylistSideIndex) {
                LazyListScrollIndicator(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                )
            }
            FloatingSelectionControls(
                visible = selection.selectionMode && displayedCustomPlaylists.isNotEmpty(),
                rangeEnabled = playlistRangeSelectionAvailable,
                allSelected = displayedCustomPlaylists.any { !it.isRemote } &&
                    selectedVisiblePlaylistCount == displayedCustomPlaylists.count { !it.isRemote },
                onRangeSelect = ::applyPlaylistRangeSelection,
                onSelectAll = { selection.toggleSelectAll(displayedCustomPlaylists.filterNot { it.isRemote }.map { it.id }) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 176.dp)
            )
        }
    }

    if (showCreateDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                if (name.isBlank()) return@CreatePlaylistDialog
                mainViewModel.createPlaylist(name) { playlist ->
                    if (playlist == null) {
                        Toast.makeText(context, R.string.playlist_name_exists, Toast.LENGTH_SHORT).show()
                    } else {
                        showCreateDialog = false
                    }
                }
            }
        )
    }
    if (showImportModeSheet) {
        ImportPlaylistModeSheet(
            count = pendingImportUris.size,
            onDismiss = {
                showImportModeSheet = false
                pendingImportUris = emptyList()
            },
            onModeSelected = ::importPendingPlaylists
        )
    }
    playlistPendingDelete?.let { playlist ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.playlist_delete_title),
            message = stringResource(R.string.playlist_delete_message, playlist.name),
            confirmText = stringResource(R.string.common_delete),
            onDismiss = { playlistPendingDelete = null },
            onConfirm = {
                mainViewModel.deletePlaylist(playlist.id)
                playlistPendingDelete = null
            }
        )
    }
    if (playlistsPendingDelete.isNotEmpty()) {
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.playlist_delete_title),
            message = stringResource(R.string.playlist_delete_multiple_message, playlistsPendingDelete.size),
            confirmText = stringResource(R.string.common_delete),
            onDismiss = { playlistsPendingDelete = emptyList() },
            onConfirm = {
                mainViewModel.deletePlaylists(playlistsPendingDelete.mapTo(mutableSetOf()) { it.id })
                playlistsPendingDelete = emptyList()
                finishSelectionMode()
            }
        )
    }
    if (showExportAllFormatSheet) {
        ExportPlaylistFormatSheet(
            onDismiss = { showExportAllFormatSheet = false },
            onFormatSelected = { format ->
                showExportAllFormatSheet = false
                pendingExportAllFormat = format
                exportAllFolderLauncher.launch(null)
            }
        )
    }

    playlistMenuTarget?.let { playlist ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = playlist.name,
            onDismissRequest = { playlistMenuTarget = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_pin_to_top),
                    onClick = {
                        val orderedIds = (listOf(playlist.id) + storedCustomPlaylists.map { it.id }).distinct()
                        mainViewModel.reorderPlaylists(orderedIds)
                        playlistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.playlist_export_title),
                    onClick = {
                        playlistsToExport = listOf(playlist)
                        showExportFormatSheet = true
                        playlistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_share),
                    onClick = {
                        shareLocalSongs(context, mainViewModel.playlistSongs(playlist))
                        playlistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_add_to_playlist),
                    onClick = {
                        playlistPickerSongs = mainViewModel.playlistSongs(playlist)
                        playlistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_to_queue),
                    onClick = {
                        playerViewModel.addToPlaylist(mainViewModel.playlistSongs(playlist))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                        playlistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_play_next),
                    onClick = {
                        playerViewModel.playNext(mainViewModel.playlistSongs(playlist))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                        playlistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_rename),
                    onClick = {
                        playlistToRename = playlist
                        playlistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_desktop_shortcut),
                    onClick = {
                        val ok = requestPinnedEllaShortcut(
                            context = context,
                            id = "playlist_${playlist.id}",
                            label = playlist.name,
                            route = Screen.PlaylistDetail.createRoute(playlist.id)
                        )
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.playlist_shortcut_requested, playlist.name) else context.getString(R.string.playlist_shortcut_unsupported),
                            Toast.LENGTH_SHORT
                        ).show()
                        playlistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_delete),
                    danger = true,
                    onClick = {
                        playlistPendingDelete = playlist
                        playlistMenuTarget = null
                    }
                )
            }
        }
    }

    playlistToRename?.let { playlist ->
        CreatePlaylistDialog(
            onDismiss = { playlistToRename = null },
            onCreate = { newName ->
                if (newName.isBlank()) return@CreatePlaylistDialog
                mainViewModel.renamePlaylist(playlist.id, newName) { renamed ->
                    if (renamed) {
                        playlistToRename = null
                    } else {
                        Toast.makeText(context, R.string.playlist_name_exists, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            initialName = playlist.name,
            title = stringResource(R.string.common_rename),
            confirmText = stringResource(R.string.common_confirm)
        )
    }

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_add_to_playlist),
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
                    selectedPlaylists.forEach { target ->
                        mainViewModel.addSongsToPlaylist(target.id, songsToAdd, appendToEnd)
                    }
                    Toast.makeText(context, context.getString(R.string.player_added_to_playlists, selectedPlaylists.size), Toast.LENGTH_SHORT).show()
                    playlistPickerSongs = null
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        CreatePlaylistAndAddSheet(
            onDismiss = { createPlaylistSongs = null },
            onCreate = { playlistName ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, playlistName) { created ->
                    mainViewModel.addSongsToPlaylist(created.id, songsToAdd)
                    createPlaylistSongs = null
                }
            }
        )
    }

    if (showExportFormatSheet) {
        ExportPlaylistFormatSheet(
            onDismiss = {
                showExportFormatSheet = false
                playlistsToExport = emptyList()
            },
            onFormatSelected = { format ->
                showExportFormatSheet = false
                pendingExportFormat = format
                exportPlaylistFolderLauncher.launch(null)
            }
        )
    }
}
