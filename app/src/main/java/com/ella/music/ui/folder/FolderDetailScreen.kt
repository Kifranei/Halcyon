package com.ella.music.ui.folder

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ella.music.R
import com.ella.music.data.tagIdentityKey
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.ShuffleAllSummaryButton
import com.ella.music.ui.components.SideIndexListEndPadding
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.rememberSongDeleteResultHandler
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.settings.findComponentActivity
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FolderDetailScreen(
    folderPath: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onFolderClick: (String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val folderPlaylists by mainViewModel.settingsManager.folderPlaylists.collectAsState(initial = emptyList())
    val currentSong by playerViewModel.currentSong.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val scanExcludeFolders by mainViewModel.settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    val pinnedFolderPaths by mainViewModel.settingsManager.pinnedKeysFlow("folder").collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val saveScope = context.findComponentActivity()?.lifecycleScope ?: scope
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val selection = rememberLibrarySelectionState<Long>()
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var folderToBlock by remember { mutableStateOf<String?>(null) }
    var folderMenuTarget by remember { mutableStateOf<FolderTreeEntry?>(null) }
    var associateFolderPaths by remember { mutableStateOf<List<String>?>(null) }
    val persistedSortIndex by mainViewModel.settingsManager.folderDetailSongSortIndex.collectAsState(
        initial = LibrarySortUiState.folderDetailSongSortIndex
    )
    val sortIndex = LibrarySortUiState.pendingFolderDetailSongSortIndex ?: persistedSortIndex
    val sortMode = FolderSongSortMode.entries.getOrElse(sortIndex) { FolderSongSortMode.Title }
    LaunchedEffect(persistedSortIndex) {
        val pendingSortIndex = LibrarySortUiState.pendingFolderDetailSongSortIndex
        if (pendingSortIndex != null && persistedSortIndex != pendingSortIndex) return@LaunchedEffect
        LibrarySortUiState.pendingFolderDetailSongSortIndex = null
        LibrarySortUiState.folderDetailSongSortIndex = persistedSortIndex
    }
    fun updateSortMode(mode: FolderSongSortMode) {
        val nextSortIndex = mode.ordinal
        if (sortIndex == nextSortIndex) return
        LibrarySortUiState.pendingFolderDetailSongSortIndex = nextSortIndex
        LibrarySortUiState.folderDetailSongSortIndex = nextSortIndex
        saveScope.launch { mainViewModel.settingsManager.setFolderDetailSongSortIndex(nextSortIndex) }
    }
    val normalizedFolderPath = remember(folderPath) { folderPath.normalizeFolderPath() }
    var scrollToTopRequest by remember { mutableStateOf(0) }

    val childFolders = remember(songs, normalizedFolderPath, pinnedFolderPaths) {
        songs.childFoldersOf(context, normalizedFolderPath)
            .sortedForFolderList(FolderListSortMode.Name, pinnedFolderPaths)
    }
    val directSongs = remember(songs, normalizedFolderPath) {
        songs.directSongsInFolder(normalizedFolderPath)
    }
    val recursiveSongs = remember(songs, normalizedFolderPath, searchQuery) {
        if (searchQuery.isBlank()) emptyList() else songs.recursiveSongsInFolder(normalizedFolderPath)
    }
    val filteredSongs = remember(directSongs, recursiveSongs, searchQuery) {
        val sourceSongs = if (searchQuery.isBlank()) directSongs else recursiveSongs
        if (searchQuery.isBlank()) sourceSongs
        else sourceSongs.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.artist.contains(searchQuery, ignoreCase = true) ||
                it.album.contains(searchQuery, ignoreCase = true) ||
                it.fileName.contains(searchQuery, ignoreCase = true)
        }
    }
    val sortedSongs = remember(filteredSongs, sortMode) {
        filteredSongs.sortedForFolderDetail(sortMode)
    }
    val sortedSongIdsForSelection = remember(sortedSongs) { sortedSongs.map { it.id } }
    val sortedSongIndexByIdForSelection = remember(sortedSongs) {
        buildMap {
            sortedSongs.forEachIndexed { index, song -> put(song.id, index) }
        }
    }
    val selectedVisibleCount = remember(selection.selectedIds, sortedSongs) {
        sortedSongs.count { it.id in selection.selectedIds }
    }
    val rangeSelectionAvailable = remember(sortedSongIndexByIdForSelection, selection.selectedIds, selection.rangeAnchorId, selection.rangeTargetId) {
        selection.isRangeSelectionAvailable(sortedSongIndexByIdForSelection)
    }
    val selectedSongsForDrag = remember(selection.selectedIds, sortedSongs) {
        sortedSongs.filter { it.id in selection.selectedIds }
    }

    val folderRootName = stringResource(R.string.folder_root)
    val folderName = remember(normalizedFolderPath, folderRootName) {
        normalizedFolderPath.folderDisplayName(folderRootName)
    }
    val deleteSelectedSongs = rememberSongDeleteResultHandler(mainViewModel) { selection.finishSelectionMode() }

    BackHandler(enabled = selection.selectionMode || searchExpanded || sortExpanded || folderToBlock != null || folderMenuTarget != null || associateFolderPaths != null) {
        when {
            associateFolderPaths != null -> associateFolderPaths = null
            folderMenuTarget != null -> folderMenuTarget = null
            folderToBlock != null -> folderToBlock = null
            selection.selectionMode -> {
                selection.finishSelectionMode()
            }
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
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
        Box {
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
                        contentDescription = if (selection.selectionMode) stringResource(R.string.common_exit_selection) else stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                if (!selection.selectionMode) {
                    FolderOutlineIcon(
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selection.selectionMode) {
                            stringResource(R.string.library_selected_fraction, selection.selectedIds.size, sortedSongs.size)
                        } else {
                            folderName.ifEmpty { stringResource(R.string.folder_root) }
                        },
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { scrollToTopRequest++ })
                        }
                    )
                    if (!selection.selectionMode) {
                        Text(
                            text = stringResource(R.string.folder_detail_header_summary, childFolders.size, recursiveSongs.size),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
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
                                if (selectedSongs.isNotEmpty()) {
                                    pendingDeleteSongs = selectedSongs
                                } else {
                                    Toast.makeText(context, R.string.library_select_songs_first, Toast.LENGTH_SHORT).show()
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
                        items = directionalSortModeDropdownItems(
                            fields = listOf(
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_title),
                                    ascendingMode = FolderSongSortMode.Title,
                                    descendingMode = FolderSongSortMode.TitleDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_file_name),
                                    ascendingMode = FolderSongSortMode.FileName,
                                    descendingMode = FolderSongSortMode.FileNameDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_duration),
                                    ascendingMode = FolderSongSortMode.DurationAsc,
                                    descendingMode = FolderSongSortMode.Duration
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_date_added),
                                    ascendingMode = FolderSongSortMode.DateAddedAsc,
                                    descendingMode = FolderSongSortMode.DateAdded
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_date_modified),
                                    ascendingMode = FolderSongSortMode.DateModifiedAsc,
                                    descendingMode = FolderSongSortMode.DateModified
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_year),
                                    ascendingMode = FolderSongSortMode.YearAsc,
                                    descendingMode = FolderSongSortMode.YearDesc
                                )
                            ),
                            selectedMode = sortMode,
                            onSelect = ::updateSortMode
                        )
                    )
                }
            }
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
                FolderSongSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                updateSortMode(mode)
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
                placeholder = stringResource(R.string.folder_detail_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (songs.isEmpty() && !libraryCacheLoaded) {
            EllaCenteredLoadingIndicator()
        } else if (childFolders.isEmpty() && sortedSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.folder_detail_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            val listState = rememberSaveable(normalizedFolderPath, saver = LazyListState.Saver) {
                LazyListState()
            }
            RestoreListScrollAfterSearch(
                searchExpanded = searchExpanded,
                query = searchQuery,
                listState = listState
            )
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            LaunchedEffect(scrollToTopRequest) {
                if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
            }
            val sortedSongIndexById = remember(sortedSongs) {
                buildMap {
                    sortedSongs.forEachIndexed { index, song -> put(song.id, index) }
                }
            }
            LaunchedEffect(selection.selectionMode, sortedSongs) {
                if (!selection.selectionMode) return@LaunchedEffect
                val visibleIds = sortedSongs.mapTo(mutableSetOf()) { it.id }
                selection.selectedIds = selection.selectedIds.filterTo(mutableSetOf()) { it in visibleIds }
                if (selection.rangeAnchorId !in visibleIds) selection.rangeAnchorId = selection.selectedIds.firstOrNull()
                if (selection.rangeTargetId !in visibleIds) selection.rangeTargetId = null
            }
            val currentSongItemIndex = remember(sortedSongIndexById, childFolders, searchQuery, currentSong?.id, selection.selectionMode) {
                if (selection.selectionMode) return@remember -1
                (currentSong?.id?.let { sortedSongIndexById[it] } ?: -1)
                    .takeIf { it >= 0 }
                    ?.plus(if (searchQuery.isBlank()) childFolders.size else 0)
                    ?: -1
            }
            val fastIndexLetters = remember(childFolders, sortedSongs, sortMode, searchQuery) {
                val folderLetters = if (searchQuery.isBlank()) {
                    childFolders.map { it.name.musicSortKey().toFastIndexSection() }
                } else {
                    emptyList()
                }
                val songLetters = if (
                    sortMode == FolderSongSortMode.Title ||
                        sortMode == FolderSongSortMode.TitleDesc ||
                        sortMode == FolderSongSortMode.FileName ||
                        sortMode == FolderSongSortMode.FileNameDesc
                ) {
                    sortedSongs.map { sortMode.songDisplaySpec().displayTitleFor(it).toFastIndexSection() }
                } else {
                    emptyList()
                }
                folderLetters + songLetters
            }
            val fastIndexTargets = remember(childFolders, sortedSongs, sortMode, searchQuery) {
                val folderLetters = if (searchQuery.isBlank()) {
                    childFolders.map { it.name.musicSortKey().toFastIndexSection() }
                } else {
                    emptyList()
                }
                val offset = folderLetters.size
                buildMap {
                    folderLetters.forEachIndexed { index, letter -> putIfAbsent(letter, index) }
                    if (
                        sortMode == FolderSongSortMode.Title ||
                            sortMode == FolderSongSortMode.TitleDesc ||
                            sortMode == FolderSongSortMode.FileName ||
                            sortMode == FolderSongSortMode.FileNameDesc
                    ) {
                        sortedSongs.forEachIndexed { index, song ->
                            putIfAbsent(sortMode.songDisplaySpec().displayTitleFor(song).toFastIndexSection(), index + offset)
                        }
                    }
                }
            }
            val showFastIndex = fastIndexLetters.size > 30 && (
                childFolders.isNotEmpty() ||
                    sortMode == FolderSongSortMode.Title ||
                    sortMode == FolderSongSortMode.TitleDesc ||
                    sortMode == FolderSongSortMode.FileName ||
                    sortMode == FolderSongSortMode.FileNameDesc
                )
            val showScrollIndicator = !showFastIndex && sortedSongs.size > 30
            val listEndInset = if (showFastIndex || showScrollIndicator) SideIndexListEndPadding else 0.dp
            Box(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.fillMaxSize()) {
                    com.ella.music.ui.components.SortSummaryHeader(
                        text = if (searchQuery.isBlank()) {
                            stringResource(
                                R.string.folder_detail_current_summary,
                                childFolders.size,
                                sortedSongs.size,
                                com.ella.music.ui.components.sortLabel(sortMode.labelRes, sortMode.isDescending())
                            )
                        } else {
                            stringResource(
                                R.string.folder_detail_search_summary,
                                sortedSongs.size,
                                com.ella.music.ui.components.sortLabel(sortMode.labelRes, sortMode.isDescending())
                            )
                        },
                        leadingContent = {
                            ShuffleAllSummaryButton(
                                visible = !selection.selectionMode && sortedSongs.isNotEmpty(),
                                onClick = {
                                    playerViewModel.setPlaylist(sortedSongs.shuffled(), 0)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            )
                        }
                    )
                    com.ella.music.ui.components.ContinuePlaybackRow(
                        songs = sortedSongs,
                        playbackStats = playbackStats,
                        currentSong = currentSong,
                        onContinue = { index ->
                            playerViewModel.setPlaylist(sortedSongs, index)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    )
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(end = listEndInset, bottom = 120.dp)
                    ) {
                        if (searchQuery.isBlank()) {
                            items(childFolders, key = { it.path }) { folder ->
                                ChildFolderRow(
                                    folder = folder,
                                    onClick = { onFolderClick(folder.path) },
                                    onLongClick = { folderMenuTarget = folder }
                                )
                            }
                        }
                        itemsIndexed(
                            items = sortedSongs,
                            key = { _, song -> song.id }
                        ) { index, song ->
                            val selected = song.id in selection.selectedIds
                            val albumArtUri = remember(song.albumId) {
                                song.albumId
                                    .takeIf { it > 0L }
                                    ?.let(mainViewModel::getAlbumArtUri)
                            }
                            SongItem(
                                song = song,
                                titleOverride = sortMode.songDisplaySpec().displayTitleFor(song),
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
                                        playerViewModel.setPlaylist(sortedSongs, index)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                },
                                onPlayNext = { playerViewModel.playNext(song) },
                                onMore = { actionSong = song }
                            )
                        }
                    }
                }
                if (showFastIndex) {
                    FastIndexBar(
                        letters = fastIndexLetters,
                        reverse = sortMode == FolderSongSortMode.TitleDesc ||
                            sortMode == FolderSongSortMode.FileNameDesc,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 0.dp),
                        onLetterClick = { letter ->
                            val index = fastIndexTargets[letter]
                            if (index != null) {
                                fastScrollJob?.cancel()
                                fastScrollJob = scope.launch { listState.scrollToItem(index) }
                            }
                        }
                    )
                } else if (showScrollIndicator) {
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
                        .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
                )
                FloatingSelectionControls(
                    visible = selection.selectionMode && sortedSongs.isNotEmpty(),
                    rangeEnabled = rangeSelectionAvailable,
                    allSelected = sortedSongs.isNotEmpty() && selectedVisibleCount == sortedSongs.size,
                    onRangeSelect = { selection.applyRangeSelection(sortedSongIdsForSelection, sortedSongIndexByIdForSelection) },
                    onSelectAll = { selection.toggleSelectAll(sortedSongIdsForSelection) },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
                )
            }
        }

        folderMenuTarget?.let { folder ->
            // Folder actions follow the same order the user sees in this folder, not MediaStore order.
            val folderSongs = remember(songs, folder.path, sortMode) {
                songs.recursiveSongsInFolder(folder.path).sortedForFolderDetail(sortMode)
            }
            val isPinned = pinnedFolderPaths.any { it.equals(folder.path, ignoreCase = true) }
            FolderActionSheet(
                title = folder.name,
                isPinned = isPinned,
                onDismiss = { folderMenuTarget = null },
                onTogglePin = {
                    folderMenuTarget = null
                    scope.launch { mainViewModel.settingsManager.setPinned("folder", folder.path, !isPinned) }
                },
                onShare = {
                    shareLocalSongs(context, folderSongs)
                    folderMenuTarget = null
                },
                onAssociate = {
                    associateFolderPaths = listOf(folder.path)
                    folderMenuTarget = null
                },
                onAddToPlaylist = {
                    playlistPickerSongs = folderSongs
                    folderMenuTarget = null
                },
                onAddToQueue = {
                    playerViewModel.addToPlaylist(folderSongs)
                    Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                    folderMenuTarget = null
                },
                onPlayNext = {
                    playerViewModel.playNext(folderSongs)
                    Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                    folderMenuTarget = null
                },
                onAddShortcut = {
                    val ok = requestPinnedEllaShortcut(
                        context = context,
                        id = "folder_${folder.path.tagIdentityKey()}",
                        label = folder.name,
                        route = Screen.FolderDetail.createRoute(folder.path)
                    )
                    Toast.makeText(
                        context,
                        if (ok) context.getString(R.string.playlist_shortcut_requested, folder.name)
                        else context.getString(R.string.playlist_shortcut_unsupported),
                        Toast.LENGTH_SHORT
                    ).show()
                    folderMenuTarget = null
                },
                onBlock = {
                    folderToBlock = folder.path
                    folderMenuTarget = null
                }
            )
        }

        associateFolderPaths?.let { sourceFolders ->
            LinkToFolderPlaylistSheet(
                show = true,
                songs = songs,
                selectedFolderCount = sourceFolders.size,
                folderPlaylists = folderPlaylists,
                onDismiss = { associateFolderPaths = null },
                onLink = { targets ->
                    scope.launch {
                        targets.forEach { target ->
                            mainViewModel.settingsManager.upsertFolderPlaylist(
                                target.id,
                                target.name,
                                (target.folders + sourceFolders).distinctBy { it.lowercase() }
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

        folderToBlock?.let { folderPath ->
            FolderBlockDialog(
                folderPath = folderPath,
                onDismiss = { folderToBlock = null },
                onBlock = {
                    scope.launch {
                        mainViewModel.settingsManager.setScanExcludeFolders(
                            (blockedFolders + folderPath.normalizeFolderPath()).distinct().joinToString("；")
                        )
                        mainViewModel.scanMusic()
                    }
                    folderToBlock = null
                }
            )
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
                playlists = playlists
                    .sortedWith(compareByDescending<com.ella.music.data.model.UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }.thenByDescending { it.createdAt }),
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
                        Toast.makeText(context, context.getString(R.string.player_added_to_playlists, selectedPlaylists.size), Toast.LENGTH_SHORT).show()
                        playlistPickerSongs = null
                        selection.finishSelectionMode()
                    }
                )
            }
        }

        createPlaylistSongs?.let { songsToAdd ->
            CreatePlaylistAndAddSelectedSheet(
                songCount = songsToAdd.size,
                onDismiss = { createPlaylistSongs = null },
                onCreate = { name ->
                    mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                        Toast.makeText(context, context.getString(R.string.player_added_to_playlist_named, playlist.name), Toast.LENGTH_SHORT).show()
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
}
