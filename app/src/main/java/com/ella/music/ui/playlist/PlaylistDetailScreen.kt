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
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LibrarySecondaryFloatingControlsBottomPadding
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.ShuffleAllFloatingButton
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.ellaPageBackground
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
    var selectionMode by remember { mutableStateOf(false) }
    var selectedSongKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var draggedSongKey by remember { mutableStateOf<String?>(null) }
    var rangeAnchorSongKey by remember { mutableStateOf<String?>(null) }
    var rangeTargetSongKey by remember { mutableStateOf<String?>(null) }
    val sortedSongs = remember(songs, sortMode) { songs.sortedForPlaylistDetail(sortMode) }
    LaunchedEffect(playlist?.id, songs) {
        manualOrder = songs
    }
    LaunchedEffect(playlist?.id) {
        selectionMode = false
        selectedSongKeys = emptySet()
        draggedSongKey = null
    }
    val reorderEnabled = !isRemoteReadOnly &&
        playlist?.isFiveStarRating != true &&
        sortMode == PlaylistSongSortMode.Custom &&
        searchQuery.isBlank()
    val reorderHandlesVisible = selectionMode && reorderEnabled
    val baseSongs = if (reorderEnabled) manualOrder else sortedSongs
    val displayedSongs by produceState(initialValue = baseSongs, baseSongs, searchQuery, ratingRevision) {
        val query = searchQuery.trim()
        value = if (query.isBlank()) {
            baseSongs
        } else {
            mainViewModel.filterSongsBySearchSnapshot(baseSongs, query)
        }
    }
    val draggedSelectionKeys = remember(draggedSongKey, selectedSongKeys, displayedSongs) {
        val draggedKey = draggedSongKey
        if (draggedKey == null || draggedKey !in selectedSongKeys) {
            emptySet()
        } else {
            displayedSongs
                .map { it.playlistIdentityKey() }
                .filterTo(mutableSetOf()) { it in selectedSongKeys }
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
    val showSongSideIndex = !selectionMode &&
        searchQuery.isBlank() &&
        sortMode == PlaylistSongSortMode.Title &&
        displayedSongs.size > 30
    val songFastIndexData = remember(showSongSideIndex, displayedSongs) {
        if (!showSongSideIndex) {
            emptyList()
        } else {
            displayedSongs
                .mapIndexed { index, song -> song.title.toFastIndexSection() to (index + songListHeaderCount) }
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
                selectedKeys = selectedSongKeys,
                keyOf = { it.playlistIdentityKey() }
            )
        }
    )
    fun finishSelectionMode() {
        selectionMode = false
        selectedSongKeys = emptySet()
        draggedSongKey = null
        rangeAnchorSongKey = null
        rangeTargetSongKey = null
    }
    fun updateRangeAnchorsForManualSelection(songKey: String, selectedNow: Boolean) {
        if (selectedNow) {
            when {
                rangeAnchorSongKey == null -> rangeAnchorSongKey = songKey
                rangeAnchorSongKey == songKey -> Unit
                else -> rangeTargetSongKey = songKey
            }
        } else {
            if (rangeTargetSongKey == songKey) rangeTargetSongKey = null
            if (rangeAnchorSongKey == songKey) {
                rangeAnchorSongKey = rangeTargetSongKey ?: selectedSongKeys.firstOrNull { it != songKey }
                rangeTargetSongKey = null
            }
        }
    }
    fun toggleSelection(song: Song) {
        val key = song.playlistIdentityKey()
        val selecting = key !in selectedSongKeys
        val next = if (selecting) selectedSongKeys + key else selectedSongKeys - key
        selectedSongKeys = next
        updateRangeAnchorsForManualSelection(key, selecting)
        if (next.isEmpty()) selectionMode = false
    }
    fun selectAllDisplayedSongs() {
        val displayedKeys = displayedSongs.mapTo(mutableSetOf()) { it.playlistIdentityKey() }
        selectedSongKeys = if (displayedKeys.isNotEmpty() && displayedKeys.all { it in selectedSongKeys }) {
            rangeAnchorSongKey = null
            rangeTargetSongKey = null
            emptySet()
        } else {
            rangeAnchorSongKey = displayedSongs.firstOrNull()?.playlistIdentityKey()
            rangeTargetSongKey = displayedSongs.lastOrNull()?.playlistIdentityKey()
            displayedKeys
        }
        selectionMode = true
    }
    fun selectedDisplayedSongs(): List<Song> =
        displayedSongs.filter { it.playlistIdentityKey() in selectedSongKeys }
    BackHandler(enabled = selectionMode || searchExpanded) {
        when {
            selectionMode -> finishSelectionMode()
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
    val selectedVisibleSongCount = remember(displayedSongs, selectedSongKeys) {
        displayedSongs.count { it.playlistIdentityKey() in selectedSongKeys }
    }
    val rangeSelectionAvailable = remember(
        displayedSongIndexByKey,
        selectedSongKeys,
        rangeAnchorSongKey,
        rangeTargetSongKey
    ) {
        val anchor = rangeAnchorSongKey
        val target = rangeTargetSongKey
        anchor != null &&
            target != null &&
            anchor != target &&
            anchor in selectedSongKeys &&
            target in selectedSongKeys &&
            anchor in displayedSongIndexByKey &&
            target in displayedSongIndexByKey
    }
    fun applyRangeSelection() {
        val anchor = rangeAnchorSongKey ?: return
        val target = rangeTargetSongKey ?: return
        val anchorIndex = displayedSongIndexByKey[anchor] ?: return
        val targetIndex = displayedSongIndexByKey[target] ?: return
        if (anchorIndex == targetIndex) return
        val bounds = if (anchorIndex < targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
        selectedSongKeys = selectedSongKeys + bounds.map { displayedSongs[it].playlistIdentityKey() }
        rangeAnchorSongKey = target
        rangeTargetSongKey = null
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
    LaunchedEffect(selectionMode, displayedSongs) {
        if (!selectionMode) return@LaunchedEffect
        val displayedKeys = displayedSongs.mapTo(mutableSetOf()) { it.playlistIdentityKey() }
        selectedSongKeys = selectedSongKeys.filterTo(mutableSetOf()) { it in displayedKeys }
        if (rangeAnchorSongKey !in displayedKeys) rangeAnchorSongKey = selectedSongKeys.firstOrNull()
        if (rangeTargetSongKey !in displayedKeys) rangeTargetSongKey = null
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
        Box {
            PlaylistDetailTopBar(
                title = when {
                    selectionMode -> stringResource(R.string.library_selected_fraction, selectedSongKeys.size, displayedSongs.size)
                    playlist == null -> stringResource(R.string.playlist_title)
                    listState.firstVisibleItemIndex > 0 -> playlist.name
                    else -> stringResource(R.string.playlist_title)
                },
                selectionMode = selectionMode,
                showRemoveSelected = !isFiveStarPlaylist && !isRemoteReadOnly,
                showExport = playlist != null && !isFiveStarPlaylist,
                onNavigationClick = {
                    if (selectionMode) finishSelectionMode() else onBack()
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
                    selectionMode = true
                    if (selectedSongKeys.isEmpty()) {
                        rangeAnchorSongKey = null
                        rangeTargetSongKey = null
                    }
                }
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scope.launch { listState.animateScrollToItem(0) } },
                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                endPadding = 160.dp
            )
        }

        PlaylistDetailSearchSection(
            visible = searchExpanded && !selectionMode,
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
                        duration = sortedSongs.sumOf { it.duration }
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
                        val dragHandleModifier = Modifier
                            .draggableHandle(
                                onDragStarted = { draggedSongKey = song.playlistIdentityKey() },
                                onDragStopped = {
                                    draggedSongKey = null
                                    settleManualOrder()
                                }
                            )
                            .longPressDraggableHandle(
                                onDragStarted = { draggedSongKey = song.playlistIdentityKey() },
                                onDragStopped = {
                                    draggedSongKey = null
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
                            isCurrent = currentSong?.playlistIdentityKey() == song.playlistIdentityKey(),
                            albumArtUri = albumArtUri,
                            loadCoverArt = mainViewModel::getCoverArtBitmap,
                            loadAudioInfo = mainViewModel::getAudioInfo,
                            selectionMode = selectionMode,
                            selected = song.playlistIdentityKey() in selectedSongKeys,
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            loadSongRating = mainViewModel::getSongRating,
                            ratingRevision = ratingRevision,
                            showPlayNextInLists = showPlayNextInLists,
                            onClick = {
                                if (selectionMode) {
                                    toggleSelection(song)
                                } else {
                                    playerViewModel.setPlaylist(displayedSongs, index)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                val songKey = song.playlistIdentityKey()
                                selectedSongKeys = selectedSongKeys + songKey
                                updateRangeAnchorsForManualSelection(songKey, selectedNow = true)
                            },
                            onPlayNext = { playerViewModel.playNext(song) },
                            onRemove = if (playlist.isFiveStarRating || isRemoteReadOnly) null else {
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

            ShuffleAllFloatingButton(
                visible = !selectionMode && displayedSongs.isNotEmpty(),
                onClick = {
                    playerViewModel.setPlaylist(displayedSongs.shuffled(), 0)
                    if (openPlayerOnPlay) onNavigateToPlayer()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibrarySecondaryFloatingControlsBottomPadding)
            )
            LocateCurrentSongFloatingButton(
                listState = listState,
                currentItemIndex = if (selectionMode) -1 else currentSongItemIndex,
                locateRequest = locateCurrentSongRequest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
            )

            FloatingSelectionControls(
                visible = selectionMode && displayedSongs.isNotEmpty(),
                rangeEnabled = rangeSelectionAvailable,
                allSelected = displayedSongs.isNotEmpty() && selectedVisibleSongCount == displayedSongs.size,
                onRangeSelect = ::applyRangeSelection,
                onSelectAll = ::selectAllDisplayedSongs,
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
                onSongRemovedFromPlaylist = if (playlist.isFiveStarRating || isRemoteReadOnly) null else {
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
