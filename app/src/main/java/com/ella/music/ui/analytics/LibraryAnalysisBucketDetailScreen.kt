package com.ella.music.ui.analytics

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.CategoryResumeKeys
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.DirectionalSortField
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.RememberPlaybackSourceScreen
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.ShuffleAllSummaryButton
import com.ella.music.ui.components.SideIndexListEndPadding
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.directionalSortDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.rememberSongDeleteResultHandler
import com.ella.music.ui.components.sortLabel
import com.ella.music.ui.home.HomeSortField
import com.ella.music.ui.home.HomeSortMode
import com.ella.music.ui.home.cachedSortedForHomeMode
import com.ella.music.ui.home.isDescending
import com.ella.music.ui.home.sortField
import com.ella.music.ui.home.toMode
import com.ella.music.ui.listmodel.SortDirection
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch

@Composable
internal fun LibraryAnalysisBucketDetailScreen(
    bucketLabel: String,
    qualityBucket: Boolean,
    songs: List<Song>,
    songsLoading: Boolean = false,
    totalLibraryCount: Int,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sourceKey = CategoryResumeKeys.analysis(qualityBucket, bucketLabel)
    RememberPlaybackSourceScreen(sourceKey)
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortMode by remember(sourceKey) {
        mutableStateOf(LibraryAnalysisBucketSortState.get(sourceKey))
    }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val selection = rememberLibrarySelectionState<Long>()
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val deleteSelectedSongs = rememberSongDeleteResultHandler(mainViewModel) { selection.finishSelectionMode() }
    val listState = remember(bucketLabel) { LazyListState() }

    val filteredSongs = remember(songs, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true) ||
                it.fileName.contains(query, ignoreCase = true)
        }
    }
    val sortedResult = remember(filteredSongs, sortMode) {
        filteredSongs.cachedSortedForHomeMode(sortMode)
    }
    val sortedSongs = sortedResult.songs
    val sortedSongIdsForSelection = remember(sortedSongs) { sortedSongs.map { it.id } }
    val sortedSongIndexByIdForSelection = remember(sortedSongs) {
        buildMap {
            sortedSongs.forEachIndexed { index, song -> put(song.id, index) }
        }
    }
    val selectedVisibleCount = remember(selection.selectedIds, sortedSongs) {
        sortedSongs.count { it.id in selection.selectedIds }
    }
    val rangeSelectionAvailable = remember(
        sortedSongIndexByIdForSelection,
        selection.selectedIds,
        selection.rangeAnchorId,
        selection.rangeTargetId
    ) {
        selection.isRangeSelectionAvailable(sortedSongIndexByIdForSelection)
    }
    val selectedSongsForDrag = remember(selection.selectedIds, sortedSongs) {
        sortedSongs.filter { it.id in selection.selectedIds }
    }
    val currentSongItemIndex = remember(sortedSongs, currentSong, selection.selectionMode) {
        val playing = currentSong
        if (selection.selectionMode || playing == null) {
            -1
        } else {
            sortedSongs.indexOfFirst { song ->
                song.playlistIdentityKey() == playing.playlistIdentityKey() ||
                    (playing.id > 0L && song.id == playing.id)
            }
        }
    }
    val percent = if (totalLibraryCount > 0) songs.size * 100f / totalLibraryCount else 0f
    val totalSizeBytes = remember(songs) { songs.sumOf(Song::fileSize) }

    BackHandler(enabled = true) {
        when {
            pendingDeleteSongs.isNotEmpty() -> pendingDeleteSongs = emptyList()
            playlistPickerSongs != null -> playlistPickerSongs = null
            createPlaylistSongs != null -> createPlaylistSongs = null
            actionSong != null -> actionSong = null
            selection.selectionMode -> selection.finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            else -> onBack()
        }
    }

    val pageBackground = ellaPageBackground()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = {
                        if (selection.selectionMode) {
                            selection.finishSelectionMode()
                        } else {
                            onBack()
                        }
                    }
                ) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = if (selection.selectionMode) {
                            stringResource(R.string.common_exit_selection)
                        } else {
                            stringResource(R.string.common_back)
                        },
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(listState) {
                            detectTapGestures(onDoubleTap = {
                                scope.launch { listState.animateScrollToItem(0) }
                            })
                        }
                ) {
                    Text(
                        text = if (selection.selectionMode) {
                            stringResource(
                                R.string.library_selected_fraction,
                                selection.selectedIds.size,
                                sortedSongs.size
                            )
                        } else {
                            bucketLabel
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (selection.selectionMode) {
                    IconButton(
                        onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selection.selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, R.string.library_select_songs_first, Toast.LENGTH_SHORT).show()
                            } else {
                                playerViewModel.playNext(selectedSongs)
                                Toast.makeText(context, R.string.song_more_added_to_play_next, Toast.LENGTH_SHORT).show()
                                selection.finishSelectionMode()
                            }
                        }
                    ) {
                        com.ella.music.ui.components.PlayNextActionIcon(
                            contentDescription = stringResource(R.string.song_more_play_next),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selection.selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, R.string.library_select_songs_first, Toast.LENGTH_SHORT).show()
                            } else {
                                playlistPickerSongs = selectedSongs
                            }
                        }
                    ) {
                        com.ella.music.ui.components.AddToPlaylistActionIcon(
                            contentDescription = stringResource(R.string.player_add_to_playlist),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                    IconButton(
                        onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selection.selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, R.string.library_select_songs_first, Toast.LENGTH_SHORT).show()
                            } else {
                                pendingDeleteSongs = selectedSongs
                            }
                        }
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = Color(0xFFE5484D),
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
                        items = directionalSortDropdownItems(
                            fields = HomeSortField.entries.map { field ->
                                DirectionalSortField(
                                    field = field,
                                    text = stringResource(field.labelRes),
                                    defaultDirection = when (field) {
                                        HomeSortField.DateAdded,
                                        HomeSortField.DateModified,
                                        HomeSortField.Duration,
                                        HomeSortField.FileSize -> SortDirection.Descending
                                        else -> SortDirection.Ascending
                                    }
                                )
                            },
                            selectedField = sortMode.sortField(),
                            selectedDirection = if (sortMode.isDescending()) {
                                SortDirection.Descending
                            } else {
                                SortDirection.Ascending
                            },
                            ascendingSummary = stringResource(R.string.common_sort_ascending),
                            descendingSummary = stringResource(R.string.common_sort_descending)
                        ) { field, direction ->
                            sortMode = field.toMode(direction == SortDirection.Descending).also {
                                LibraryAnalysisBucketSortState.put(sourceKey, it)
                            }
                        }
                    )
                }
            }

            AnimatedVisibility(
                visible = searchExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
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

            if (songsLoading) {
                EllaCenteredLoadingIndicator()
            } else if (sortedSongs.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.no_songs_found),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } else {
                RestoreListScrollAfterSearch(
                    searchExpanded = searchExpanded,
                    query = searchQuery,
                    listState = listState
                )
                val showScrollIndicator = sortedSongs.size > 30
                val listEndInset = if (showScrollIndicator) SideIndexListEndPadding else 0.dp
                Box(modifier = Modifier.fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        com.ella.music.ui.components.SortSummaryHeader(
                            text = stringResource(
                                R.string.analytics_detail_summary,
                                songs.size,
                                formatPercent(percent),
                                formatFileSize(totalSizeBytes),
                                sortLabel(sortMode.labelRes, sortMode.isDescending())
                            ),
                            leadingContent = {
                                ShuffleAllSummaryButton(
                                    visible = !selection.selectionMode && sortedSongs.isNotEmpty(),
                                    onClick = {
                                        playerViewModel.setShuffledPlaylist(
                                            sortedSongs,
                                            0,
                                            resumeCategoryKey = sourceKey
                                        )
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                )
                            }
                        )
                        LazyColumn(
                            state = listState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(end = listEndInset, bottom = 120.dp)
                        ) {
                            itemsIndexed(
                                items = sortedSongs,
                                key = { _, song -> song.playlistIdentityKey() }
                            ) { index, song ->
                                val selected = song.id in selection.selectedIds
                                val albumArtUri = remember(song.albumId) {
                                    song.albumId
                                        .takeIf { it > 0L }
                                        ?.let(mainViewModel::getAlbumArtUri)
                                }
                                SongItem(
                                    song = song,
                                    isCurrent = currentSong?.id == song.id,
                                    albumArtUri = albumArtUri,
                                    loadCoverArt = mainViewModel::getCoverArtBitmap,
                                    loadAudioInfo = mainViewModel::getAudioInfo,
                                    loadSongTagInfo = mainViewModel::getSongTagInfo,
                                    showPlayNextInLists = showPlayNextInLists,
                                    isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                                    loadSongRating = mainViewModel::getSongRating,
                                    selectionMode = selection.selectionMode,
                                    selected = selected,
                                    dragSelectedSongs = selectedSongsForDrag,
                                    onLongClick = {
                                        selection.selectionMode = true
                                        selection.selectedIds = selection.selectedIds + song.id
                                        selection.updateRangeAnchorsForManualSelection(song.id, selectedNow = true)
                                    },
                                    onClick = {
                                        if (selection.selectionMode) {
                                            selection.toggleSelection(song.id)
                                        } else {
                                            playerViewModel.setPlaylist(
                                                sortedSongs,
                                                index,
                                                resumeCategoryKey = sourceKey
                                            )
                                            if (openPlayerOnPlay) onNavigateToPlayer()
                                        }
                                    },
                                    onPlayNext = { playerViewModel.playNext(song) },
                                    onMore = { actionSong = song }
                                )
                            }
                        }
                    }
                    if (showScrollIndicator) {
                        LazyListScrollIndicator(
                            state = listState,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxHeight()
                        )
                    }
                    LocateCurrentSongFloatingButton(
                        listState = listState,
                        currentItemIndex = currentSongItemIndex,
                        locateRequest = locateCurrentSongRequest,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = LibraryFloatingControlsEndPadding,
                                bottom = LibraryFloatingControlsBottomPadding
                            )
                    )
                    FloatingSelectionControls(
                        visible = selection.selectionMode && sortedSongs.isNotEmpty(),
                        rangeEnabled = rangeSelectionAvailable,
                        allSelected = sortedSongs.isNotEmpty() && selectedVisibleCount == sortedSongs.size,
                        onRangeSelect = {
                            selection.applyRangeSelection(sortedSongIdsForSelection, sortedSongIndexByIdForSelection)
                        },
                        onSelectAll = { selection.toggleSelectAll(sortedSongIdsForSelection) },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(
                                end = LibraryFloatingControlsEndPadding,
                                bottom = LibraryFloatingControlsBottomPadding
                            )
                    )
                }
            }
        }

        SongMoreActionHost(
            actionSong = actionSong,
            mainViewModel = mainViewModel,
            playerViewModel = playerViewModel,
            onDismissAction = { actionSong = null },
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist
        )

        playlistPickerSongs?.let { songsToAdd ->
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.song_more_add_to_playlist_title),
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
                onCreate = { playlistName ->
                    mainViewModel.createPlaylistOrShowDuplicateToast(context, playlistName) { created ->
                        mainViewModel.addSongsToPlaylist(created.id, songsToAdd)
                        createPlaylistSongs = null
                        selection.finishSelectionMode()
                    }
                }
            )
        }

        ConfirmDangerDialog(
            show = pendingDeleteSongs.isNotEmpty(),
            title = stringResource(R.string.song_more_delete_song_title),
            message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
            confirmText = stringResource(R.string.song_more_delete_permanently),
            onDismiss = { pendingDeleteSongs = emptyList() },
            onConfirm = {
                val songsToDelete = pendingDeleteSongs
                pendingDeleteSongs = emptyList()
                deleteSelectedSongs(songsToDelete)
            }
        )
    }
}

internal object LibraryAnalysisBucketSortState {
    private val modes = java.util.concurrent.ConcurrentHashMap<String, HomeSortMode>()

    fun get(sourceKey: String): HomeSortMode = modes[sourceKey] ?: HomeSortMode.FileSize

    fun put(sourceKey: String, mode: HomeSortMode) {
        modes[sourceKey] = mode
    }
}
