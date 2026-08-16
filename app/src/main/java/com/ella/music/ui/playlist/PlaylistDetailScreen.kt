package com.ella.music.ui.playlist

import com.ella.music.ui.components.EllaMiuixBottomSheet

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
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
import com.ella.music.R
import com.ella.music.data.model.FIVE_STAR_PLAYLIST_ID
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.PlaylistExportFormat
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistDetailScreen(
    playlistId: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val ratingRevision by mainViewModel.ratingRevision.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val showRemoveFromPlaylistButton by mainViewModel.settingsManager.showRemoveFromPlaylistButton.collectAsState(initial = true)
    val isFiveStarPlaylist = playlistId == FIVE_STAR_PLAYLIST_ID
    val storedPlaylist = playlists.firstOrNull { it.id == playlistId || it.name == playlistId }
    val fiveStarSongs by produceState(initialValue = emptyList(), isFiveStarPlaylist, librarySongs, ratingRevision) {
        value = if (isFiveStarPlaylist) mainViewModel.getFiveStarSongs() else emptyList()
    }
    val playlist = if (isFiveStarPlaylist) {
        UserPlaylist(
            id = FIVE_STAR_PLAYLIST_ID,
            name = stringResource(R.string.playlist_five_star_name),
            createdAt = 0L,
            updatedAt = 0L
        )
    } else {
        storedPlaylist
    }
    val isRemotePlaylist = playlist?.isRemote == true
    val isRemoteReadOnly = isRemotePlaylist && !playlist.remoteWritable
    val songs = remember(playlist, librarySongs, fiveStarSongs, isFiveStarPlaylist) {
        if (isFiveStarPlaylist) fiveStarSongs else playlist?.let(mainViewModel::playlistSongs).orEmpty()
    }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var actionSong by remember { mutableStateOf<com.ella.music.data.model.Song?>(null) }
    val sortIndex by mainViewModel.settingsManager.playlistDetailSongSortIndex.collectAsState(initial = 2)
    val sortMode = PlaylistSongSortMode.entries.getOrElse(sortIndex) { PlaylistSongSortMode.AddedAt }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    RestoreListScrollAfterSearch(
        searchExpanded = searchExpanded,
        query = searchQuery,
        listState = listState
    )
    var removeFromPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var removeSelectedPlaylistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var manualOrder by remember(playlist?.id) { mutableStateOf(songs) }
    val selection = rememberLibrarySelectionState<String>()
    var draggedSongKey by remember { mutableStateOf<String?>(null) }
    var pressedDragHandleSongKey by remember { mutableStateOf<String?>(null) }
    val sortedSongs = remember(songs, sortMode) { songs.sortedForPlaylistDetail(sortMode) }
    LaunchedEffect(playlist?.id, songs) {
        manualOrder = songs
    }
    LaunchedEffect(playlist?.id) {
        selection.selectionMode = false
        selection.selectedIds = emptySet()
        draggedSongKey = null
        pressedDragHandleSongKey = null
    }
    val reorderEnabled = !isRemoteReadOnly &&
        playlist?.isFiveStarRating != true &&
        sortMode == PlaylistSongSortMode.Custom &&
        searchQuery.isBlank()
    val reorderHandlesVisible = selection.selectionMode && reorderEnabled
    val baseSongs = if (reorderEnabled) manualOrder else sortedSongs
    val displayedSongs by produceState(initialValue = baseSongs, baseSongs, searchQuery, ratingRevision) {
        val query = searchQuery.trim()
        value = if (query.isBlank()) {
            baseSongs
        } else {
            mainViewModel.filterSongsBySearchSnapshot(baseSongs, query)
        }
    }
    val draggedSelectionKeys = remember(draggedSongKey, selection.selectedIds, displayedSongs) {
        val draggedKey = draggedSongKey
        if (draggedKey == null || draggedKey !in selection.selectedIds) {
            emptySet()
        } else {
            displayedSongs
                .map { it.playlistIdentityKey() }
                .filterTo(mutableSetOf()) { it in selection.selectedIds }
        }
    }
    // A multi-selection has one physical drag target; its hidden siblings move with it.
    val reorderableSongs = remember(displayedSongs, draggedSongKey, draggedSelectionKeys) {
        if (draggedSelectionKeys.size <= 1) {
            displayedSongs
        } else {
            displayedSongs.filter { song ->
                val key = song.playlistIdentityKey()
                key == draggedSongKey || key !in draggedSelectionKeys
            }
        }
    }
    val songListHeaderCount = 2
    val showSongSideIndex = !selection.selectionMode &&
        searchQuery.isBlank() &&
        sortMode in setOf(
            PlaylistSongSortMode.Title,
            PlaylistSongSortMode.TitleDesc,
            PlaylistSongSortMode.FileName,
            PlaylistSongSortMode.FileNameDesc
        ) &&
        displayedSongs.size > 30
    val songFastIndexData = remember(showSongSideIndex, displayedSongs) {
        if (!showSongSideIndex) {
            emptyList()
        } else {
            displayedSongs
                .mapIndexed { index, song ->
                    sortMode.songDisplaySpec().displayTitleFor(song).toFastIndexSection() to (index + songListHeaderCount)
                }
                .distinctBy { it.first }
        }
    }
    val showScrollIndicator = displayedSongs.size > 30 && !showSongSideIndex
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (!reorderHandlesVisible) return@rememberReorderableLazyListState
            val fromSong = reorderableSongs.getOrNull(from.index - songListHeaderCount)
                ?: return@rememberReorderableLazyListState
            val toSong = reorderableSongs.getOrNull(to.index - songListHeaderCount)
                ?: return@rememberReorderableLazyListState
            val fromSongIndex = manualOrder.indexOfFirst {
                it.playlistIdentityKey() == fromSong.playlistIdentityKey()
            }
            val toSongIndex = manualOrder.indexOfFirst {
                it.playlistIdentityKey() == toSong.playlistIdentityKey()
            }
            if (fromSongIndex !in manualOrder.indices || toSongIndex !in manualOrder.indices) return@rememberReorderableLazyListState
            manualOrder = manualOrder.moveSelectedItemsAsBlock(
                from = fromSongIndex,
                to = toSongIndex,
                selectedKeys = selection.selectedIds,
                keyOf = { it.playlistIdentityKey() }
            )
        }
    )
    fun finishSelectionMode() {
        selection.finishSelectionMode()
        draggedSongKey = null
    }
    fun selectedDisplayedSongs(): List<Song> =
        displayedSongs.filter { it.playlistIdentityKey() in selection.selectedIds }
    val selectedSongsForDrag = remember(displayedSongs, selection.selectedIds) {
        displayedSongs.filter { it.playlistIdentityKey() in selection.selectedIds }
    }
    BackHandler(enabled = selection.selectionMode || searchExpanded) {
        when {
            selection.selectionMode -> finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
        }
    }
    val displayedSongIndexByKey = remember(displayedSongs) {
        buildMap {
            displayedSongs.forEachIndexed { index, song -> put(song.playlistIdentityKey(), index) }
        }
    }
    val selectedVisibleSongCount = remember(displayedSongs, selection.selectedIds) {
        displayedSongs.count { it.playlistIdentityKey() in selection.selectedIds }
    }
    val rangeSelectionAvailable = remember(
        displayedSongIndexByKey,
        selection.selectedIds,
        selection.rangeAnchorId,
        selection.rangeTargetId
    ) {
        selection.isRangeSelectionAvailable(displayedSongIndexByKey)
    }
    val currentSongItemIndex = remember(displayedSongIndexByKey, currentSong?.playlistIdentityKey()) {
        (currentSong?.playlistIdentityKey()?.let { displayedSongIndexByKey[it] } ?: -1)
            .takeIf { it >= 0 }
            ?.plus(2)
            ?: -1
    }
    val playlistCoverModel = remember(sortedSongs) {
        sortedSongs.firstOrNull()?.let { song ->
            song.coverUrl.takeIf { it.isNotBlank() } ?: mainViewModel.getAlbumArtUri(song.albumId)
        }
    }
    LaunchedEffect(selection.selectionMode, displayedSongs) {
        if (!selection.selectionMode) return@LaunchedEffect
        val displayedKeys = displayedSongs.mapTo(mutableSetOf()) { it.playlistIdentityKey() }
        selection.selectedIds = selection.selectedIds.filterTo(mutableSetOf()) { it in displayedKeys }
        if (selection.rangeAnchorId !in displayedKeys) selection.rangeAnchorId = selection.selectedIds.firstOrNull()
        if (selection.rangeTargetId !in displayedKeys) selection.rangeTargetId = null
    }
    var showExportFormatSheet by remember { mutableStateOf(false) }
    var pendingM3uExportFormat by remember { mutableStateOf<PlaylistExportFormat?>(null) }
    val txtExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val targetPlaylist = playlist
        if (uri == null || targetPlaylist == null) return@rememberLauncherForActivityResult
        mainViewModel.exportLocalPlaylist(targetPlaylist, uri, PlaylistExportFormat.PlainText) { result ->
            result
                .onSuccess { exportResult ->
                    val skippedText = if (exportResult.skippedCount > 0) context.getString(R.string.playlist_export_skipped, exportResult.skippedCount) else ""
                    Toast.makeText(context, context.getString(R.string.playlist_export_done, exportResult.exportedCount, skippedText), Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, context.getString(R.string.playlist_export_failed, it.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
        }
    }
    val m3uExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val targetPlaylist = playlist
        val targetFormat = pendingM3uExportFormat ?: PlaylistExportFormat.M3u8
        pendingM3uExportFormat = null
        if (uri == null || targetPlaylist == null) return@rememberLauncherForActivityResult
        mainViewModel.exportLocalPlaylist(targetPlaylist, uri, targetFormat) { result ->
            result
                .onSuccess { exportResult ->
                    val skippedText = if (exportResult.skippedCount > 0) context.getString(R.string.playlist_export_skipped, exportResult.skippedCount) else ""
                    Toast.makeText(context, context.getString(R.string.playlist_export_done, exportResult.exportedCount, skippedText), Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, context.getString(R.string.playlist_export_failed, it.message.orEmpty()), Toast.LENGTH_SHORT).show()
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        PlaylistDetailTopBar(
            title = when {
                selection.selectionMode -> stringResource(R.string.library_selected_fraction, selection.selectedIds.size, displayedSongs.size)
                playlist == null -> stringResource(R.string.playlist_title)
                listState.firstVisibleItemIndex > 0 -> playlist.name
                else -> stringResource(R.string.playlist_title)
            },
            selectionMode = selection.selectionMode,
            showRemoveSelected = !isFiveStarPlaylist && !isRemoteReadOnly,
            showExport = playlist != null && !isFiveStarPlaylist,
            onNavigationClick = {
                if (selection.selectionMode) finishSelectionMode() else onBack()
            },
            onPlayNextSelectedClick = {
                val selected = selectedDisplayedSongs()
                if (selected.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                } else {
                    playerViewModel.playNext(selected)
                    Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                    finishSelectionMode()
                }
            },
            onAddSelectedClick = {
                val selected = selectedDisplayedSongs()
                if (selected.isEmpty()) {
                    Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                } else {
                    playlistPickerSongs = selected
                }
            },
            onRemoveSelectedClick = {
                val selected = selectedDisplayedSongs()
                if (selected.isNotEmpty()) removeSelectedPlaylistSongs = selected
            },
            onSearchClick = {
                searchExpanded = !searchExpanded
                if (!searchExpanded) searchQuery = ""
            },
            onExportClick = { showExportFormatSheet = true },
            onSelectionModeClick = {
                selection.selectionMode = true
                if (selection.selectedIds.isEmpty()) {
                    selection.rangeAnchorId = null
                    selection.rangeTargetId = null
                }
            },
            onDoubleTapTitle = { scope.launch { listState.animateScrollToItem(0) } }
        )

        PlaylistDetailSearchSection(
            visible = searchExpanded && !selection.selectionMode,
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            onSearch = { searchExpanded = false }
        )

        if (playlist == null) {
            PlaylistDetailNotFoundState()
            return@Column
        }

        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 150.dp)
            ) {
                item {
                    val playlistPlayCount = remember(sortedSongs, playbackStats) {
                        val statsMap = playbackStats.associateBy { it.songId }
                        sortedSongs.sumOf { statsMap[it.id]?.playCount ?: 0 }
                    }
                    PlaylistDetailHero(
                        playlist = playlist,
                        coverModel = playlistCoverModel,
                        songCount = sortedSongs.size,
                        playCount = playlistPlayCount,
                        duration = sortedSongs.sumOf { it.duration },
                        onShuffle = if (selection.selectionMode) {
                            null
                        } else {
                            {
                                if (displayedSongs.isNotEmpty()) {
                                    playerViewModel.setPlaylist(displayedSongs.shuffled(), 0)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            }
                        }
                    )
                }

                item {
                    PlaylistPlayAllBar(
                        songCount = displayedSongs.size,
                        sortLabel = com.ella.music.ui.components.sortLabel(sortMode.labelRes, sortMode.isDescending()),
                        onPlayAll = {
                            if (displayedSongs.isNotEmpty()) {
                                playerViewModel.setPlaylist(displayedSongs, 0)
                                if (openPlayerOnPlay) onNavigateToPlayer()
                            }
                        },
                        onShuffle = null,
                        sortItems = directionalSortModeDropdownItems(
                            fields = listOf(
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_custom),
                                    ascendingMode = PlaylistSongSortMode.Custom,
                                    descendingMode = PlaylistSongSortMode.CustomDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_added_at),
                                    ascendingMode = PlaylistSongSortMode.AddedAt,
                                    descendingMode = PlaylistSongSortMode.AddedAtDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_title),
                                    ascendingMode = PlaylistSongSortMode.Title,
                                    descendingMode = PlaylistSongSortMode.TitleDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_file_name),
                                    ascendingMode = PlaylistSongSortMode.FileName,
                                    descendingMode = PlaylistSongSortMode.FileNameDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_duration),
                                    ascendingMode = PlaylistSongSortMode.DurationAsc,
                                    descendingMode = PlaylistSongSortMode.Duration
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_year),
                                    ascendingMode = PlaylistSongSortMode.YearAsc,
                                    descendingMode = PlaylistSongSortMode.YearDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_date_added),
                                    ascendingMode = PlaylistSongSortMode.DateAddedAsc,
                                    descendingMode = PlaylistSongSortMode.DateAdded
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_date_modified),
                                    ascendingMode = PlaylistSongSortMode.DateModifiedAsc,
                                    descendingMode = PlaylistSongSortMode.DateModified
                                )
                            ),
                            selectedMode = sortMode,
                            onSelect = { mode ->
                                scope.launch { mainViewModel.settingsManager.setPlaylistDetailSongSortIndex(mode.ordinal) }
                                scope.launch { listState.animateScrollToItem(0) }
                            }
                        )
                    )
                }

                item {
                    com.ella.music.ui.components.ContinuePlaybackRow(
                        songs = displayedSongs,
                        playbackStats = playbackStats,
                        currentSong = currentSong,
                        onContinue = { index ->
                            playerViewModel.setPlaylist(displayedSongs, index)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    )
                }

            if (displayedSongs.isEmpty() && librarySongs.isEmpty() && !libraryCacheLoaded) {
                item {
                    EllaCenteredLoadingIndicator(modifier = Modifier.fillParentMaxSize())
                }
            } else if (displayedSongs.isEmpty()) {
                item {
                    PlaylistDetailEmptyState(
                        searchQuery = searchQuery,
                        playlist = playlist
                    )
                }
            } else {
                itemsIndexed(reorderableSongs, key = { _, song -> song.playlistIdentityKey() }) { index, song ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = song.playlistIdentityKey()
                    ) { isDragging ->
                        fun settleManualOrder() {
                            mainViewModel.reorderPlaylistSongs(
                                playlist.id,
                                manualOrder.map { it.playlistIdentityKey() }
                            )
                        }
                        val songKey = song.playlistIdentityKey()
                        val dragHandleModifier = Modifier
                            .draggableHandle(
                                dragGestureDetector = ImmediateOrLongPressDragGestureDetector,
                                onDragStarted = {
                                    pressedDragHandleSongKey = songKey
                                    draggedSongKey = songKey
                                },
                                onDragStopped = {
                                    draggedSongKey = null
                                    pressedDragHandleSongKey = null
                                    settleManualOrder()
                                }
                            )
                        val albumArtUri = remember(song.albumId) {
                            song.albumId
                                .takeIf { it > 0L }
                                ?.let(mainViewModel::getAlbumArtUri)
                        }
                        SongItem(
                            song = song,
                            titleOverride = sortMode.songDisplaySpec().displayTitleFor(song),
                            isCurrent = currentSong?.playlistIdentityKey() == song.playlistIdentityKey(),
                            albumArtUri = albumArtUri,
                            loadCoverArt = mainViewModel::getCoverArtBitmap,
                            loadAudioInfo = mainViewModel::getAudioInfo,
                            loadSongTagInfo = mainViewModel::getSongTagInfo,
                            selectionMode = selection.selectionMode,
                            selected = song.playlistIdentityKey() in selection.selectedIds,
                            dragSelectedSongs = selectedSongsForDrag,
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            loadSongRating = mainViewModel::getSongRating,
                            ratingRevision = ratingRevision,
                            showPlayNextInLists = showPlayNextInLists,
                            onClick = {
                                if (selection.selectionMode) {
                                    selection.toggleSelection(songKey)
                                } else {
                                    playerViewModel.setPlaylist(displayedSongs, index)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            },
                            onLongClick = {
                                if (pressedDragHandleSongKey == songKey) return@SongItem
                                selection.selectionMode = true
                                selection.selectedIds = selection.selectedIds + songKey
                                selection.updateRangeAnchorsForManualSelection(songKey, selectedNow = true)
                            },
                            onPlayNext = { playerViewModel.playNext(song) },
                            onRemove = if (
                                playlist.isFiveStarRating ||
                                isRemoteReadOnly ||
                                !showRemoveFromPlaylistButton
                            ) null else {
                                {
                                    removeFromPlaylistSong = song
                                }
                            },
                            onMore = { actionSong = song },
                            leadingLabel = (manualOrder.indexOfFirst {
                                it.playlistIdentityKey() == song.playlistIdentityKey()
                            } + 1).toString(),
                            leadingLabelBeforeCover = true,
                            trailingContent = if (reorderHandlesVisible) {
                                {
                                    PlaylistDetailReorderHandle(
                                        isDragging = isDragging,
                                        draggedSelectionCount = draggedSelectionKeys
                                            .size
                                            .takeIf { isDragging && song.playlistIdentityKey() == draggedSongKey && it > 1 },
                                        modifier = Modifier
                                            .then(dragHandleModifier)
                                    )
                                }
                            } else null,
                            showTrailingContentInSelectionMode = reorderHandlesVisible,
                            modifier = Modifier
                        )
                    }
                }
            }
            }

            if (showSongSideIndex && songFastIndexData.isNotEmpty()) {
                FastIndexBar(
                    letters = songFastIndexData.map { it.first },
                    reverse = sortMode == PlaylistSongSortMode.TitleDesc ||
                        sortMode == PlaylistSongSortMode.FileNameDesc,
                    onLetterClick = { letter ->
                        songFastIndexData.firstOrNull { it.first == letter }?.second?.let { itemIndex ->
                            scope.launch { listState.scrollToItem(itemIndex) }
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 72.dp, bottom = 126.dp)
                )
            } else if (showScrollIndicator) {
                LazyListScrollIndicator(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(top = 72.dp, bottom = 126.dp)
                )
            }

            LocateCurrentSongFloatingButton(
                listState = listState,
                currentItemIndex = if (selection.selectionMode) -1 else currentSongItemIndex,
                locateRequest = locateCurrentSongRequest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
            )

            FloatingSelectionControls(
                visible = selection.selectionMode && displayedSongs.isNotEmpty(),
                rangeEnabled = rangeSelectionAvailable,
                allSelected = displayedSongs.isNotEmpty() && selectedVisibleSongCount == displayedSongs.size,
                onRangeSelect = { selection.applyRangeSelection(displayedSongs.map { it.playlistIdentityKey() }, displayedSongIndexByKey) },
                onSelectAll = { selection.toggleSelectAll(displayedSongs.map { it.playlistIdentityKey() }) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
            )

            SongMoreActionHost(
                actionSong = actionSong,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onDismissAction = { actionSong = null },
                onNavigateToAlbum = onNavigateToAlbum,
                onNavigateToArtist = onNavigateToArtist,
                onSongRemovedFromPlaylist = if (
                    playlist.isFiveStarRating ||
                    isRemoteReadOnly
                ) null else {
                    { song -> removeFromPlaylistSong = song }
                }
            )

            removeFromPlaylistSong?.let { song ->
                ConfirmDangerDialog(
                    show = true,
                    title = stringResource(R.string.playlist_remove_song_title),
                    message = stringResource(R.string.playlist_remove_song_message, playlist.name, song.title.ifBlank { song.fileName.ifBlank { stringResource(R.string.common_this_song) } }),
                    confirmText = stringResource(R.string.common_remove),
                    onDismiss = { removeFromPlaylistSong = null },
                    onConfirm = {
                        mainViewModel.removeSongFromPlaylist(playlist.id, song.playlistIdentityKey())
                        removeFromPlaylistSong = null
                    }
                )
            }

            if (removeSelectedPlaylistSongs.isNotEmpty()) {
                ConfirmDangerDialog(
                    show = true,
                    title = stringResource(R.string.playlist_remove_selected_title),
                    message = stringResource(R.string.playlist_remove_selected_message, removeSelectedPlaylistSongs.size),
                    confirmText = stringResource(R.string.common_remove),
                    onDismiss = { removeSelectedPlaylistSongs = emptyList() },
                    onConfirm = {
                        mainViewModel.removeSongsFromPlaylist(
                            playlist.id,
                            removeSelectedPlaylistSongs.mapTo(mutableSetOf()) { it.playlistIdentityKey() }
                        )
                        removeSelectedPlaylistSongs = emptyList()
                        finishSelectionMode()
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
                        playlists = playlists
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
                            finishSelectionMode()
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
                            finishSelectionMode()
                        }
                    }
                )
            }
        }
    }

    if (showExportFormatSheet && playlist != null) {
        ExportPlaylistFormatSheet(
            onDismiss = { showExportFormatSheet = false },
            onFormatSelected = { format ->
                val extension = when (format) {
                    PlaylistExportFormat.PlainText -> "txt"
                    PlaylistExportFormat.M3u8 -> "m3u8"
                    PlaylistExportFormat.M3u -> "m3u"
                }
                showExportFormatSheet = false
                val fileName = "${playlist.name.safePlaylistFileName()}.$extension"
                when (format) {
                    PlaylistExportFormat.PlainText -> txtExportLauncher.launch(fileName)
                    PlaylistExportFormat.M3u8,
                    PlaylistExportFormat.M3u -> {
                        pendingM3uExportFormat = format
                        m3uExportLauncher.launch(fileName)
                    }
                }
            }
        )
    }
}
