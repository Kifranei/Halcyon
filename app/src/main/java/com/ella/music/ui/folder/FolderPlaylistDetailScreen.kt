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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SelectionCheck
import com.ella.music.ui.components.ShuffleAllSummaryButton
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.playlist.ImmediateOrLongPressDragGestureDetector
import com.ella.music.ui.playlist.PlaylistDragHandle
import com.ella.music.ui.playlist.moveSelectedItemsAsBlock
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel

import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.basic.Search
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun FolderPlaylistDetailScreen(
    playlistId: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToFolder: (String) -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val scope = rememberCoroutineScope()
    val songs by mainViewModel.songs.collectAsState()
    val playlists by mainViewModel.settingsManager.folderPlaylists.collectAsState(initial = emptyList())
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val currentSong by playerViewModel.currentSong.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val playlist = remember(playlists, playlistId) {
        playlists.firstOrNull { it.id == playlistId || it.name == playlistId }
    }
    val allPlaylistSongs = remember(playlist, songs) {
        playlist?.let { songs.songsForFolderPlaylist(it.folders) }.orEmpty()
    }
    val playlistSongs = remember(allPlaylistSongs, playlist?.hiddenFolders) {
        val hiddenFolders = playlist?.hiddenFolders.orEmpty().map { it.normalizeFolderPath() }
        allPlaylistSongs.filterNot { song ->
            val songFolder = song.folderPath().normalizeFolderPath()
            hiddenFolders.any { hidden ->
                songFolder.equals(hidden, ignoreCase = true) ||
                    songFolder.startsWith("${hidden.trimEnd('/')}/", ignoreCase = true)
            }
        }
    }
    var selectedTab by rememberSaveable(playlistId) { mutableStateOf(FolderPlaylistTab.Songs) }
    var searchExpanded by rememberSaveable(playlistId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(playlistId) { mutableStateOf("") }
    var selectionMode by rememberSaveable(playlistId) { mutableStateOf(false) }
    var selectedSongKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolderPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var folderActionTarget by remember { mutableStateOf<FolderPlaylistFolderEntry?>(null) }
    var associateFolderPaths by remember { mutableStateOf<List<String>?>(null) }
    var rangeAnchorKey by remember { mutableStateOf<String?>(null) }
    var rangeTargetKey by remember { mutableStateOf<String?>(null) }
    var draggedKey by remember { mutableStateOf<String?>(null) }
    var pressedDragHandleKey by remember { mutableStateOf<String?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)
    val userPlaylists by mainViewModel.playlists.collectAsState()
    // Sort selections persist across sessions (like the folder/playlist detail screens) instead of
    // resetting to Custom every time the screen is re-entered.
    val songSortIndex by mainViewModel.settingsManager.folderPlaylistDetailSongSortIndex.collectAsState(initial = 0)
    val folderSortIndex by mainViewModel.settingsManager.folderPlaylistDetailFolderSortIndex.collectAsState(initial = 0)
    val songSortMode = FolderPlaylistSongSortMode.entries.getOrElse(songSortIndex) { FolderPlaylistSongSortMode.Custom }
    val folderSortMode = FolderPlaylistFolderSortMode.entries.getOrElse(folderSortIndex) { FolderPlaylistFolderSortMode.Custom }
    val detailQuery = searchQuery.trim()
    val folderEntries = remember(playlist, songs) {
        playlist?.folders.orEmpty().mapNotNull { folderPath ->
            val normalized = folderPath.normalizeFolderPath()
            val folderSongs = songs.filter {
                val songFolder = it.folderPath().normalizeFolderPath()
                songFolder.equals(normalized, ignoreCase = true) ||
                    songFolder.startsWith("${normalized.trimEnd('/')}/", ignoreCase = true)
            }
            if (folderSongs.isEmpty()) return@mapNotNull null
            FolderPlaylistFolderEntry(
                path = folderPath,
                displayName = folderPath.folderDisplayName(""),
                songCount = folderSongs.size,
                albumCount = folderSongs.map { it.album }.distinct().size,
                duration = folderSongs.sumOf { it.duration },
                dateModified = folderSongs.maxOfOrNull { it.dateModified } ?: 0L,
                coverModel = folderSongs.firstOrNull().folderPlaylistCoverModel()
            )
        }
    }
    val sortedPlaylistSongs = remember(playlistSongs, songSortMode, playlist?.songOrder) {
        playlistSongs.sortedForFolderPlaylistDetail(songSortMode, playlist?.songOrder.orEmpty())
    }
    val sortedFolderEntries = remember(folderEntries, folderSortMode, playlist?.folderOrder) {
        folderEntries.sortedForFolderPlaylistDetail(folderSortMode, playlist?.folderOrder.orEmpty())
    }
    val customSongs = remember(playlistSongs, playlist?.songOrder) {
        playlistSongs.sortedForFolderPlaylistDetail(
            FolderPlaylistSongSortMode.Custom,
            playlist?.songOrder.orEmpty()
        )
    }
    val customFolderEntries = remember(folderEntries, playlist?.folderOrder) {
        folderEntries.sortedForFolderPlaylistDetail(
            FolderPlaylistFolderSortMode.Custom,
            playlist?.folderOrder.orEmpty()
        )
    }
    var manualSongs by remember(customSongs) { mutableStateOf(customSongs) }
    var manualFolderEntries by remember(customFolderEntries) { mutableStateOf(customFolderEntries) }
    LaunchedEffect(customSongs) {
        manualSongs = customSongs
    }
    LaunchedEffect(customFolderEntries) {
        manualFolderEntries = customFolderEntries
    }
    val songReorderEnabled = selectionMode &&
        selectedTab == FolderPlaylistTab.Songs &&
        songSortMode == FolderPlaylistSongSortMode.Custom &&
        detailQuery.isBlank()
    val folderReorderEnabled = selectionMode &&
        selectedTab == FolderPlaylistTab.Folders &&
        folderSortMode == FolderPlaylistFolderSortMode.Custom &&
        detailQuery.isBlank()
    val displayedSongSource = if (songReorderEnabled) manualSongs else sortedPlaylistSongs
    val displayedFolderSource = if (folderReorderEnabled) manualFolderEntries else sortedFolderEntries
    val displayedSongs = remember(displayedSongSource, detailQuery) {
        if (detailQuery.isBlank()) {
            displayedSongSource
        } else {
            displayedSongSource.filter { song ->
                song.title.contains(detailQuery, ignoreCase = true) ||
                    song.artist.contains(detailQuery, ignoreCase = true) ||
                    song.album.contains(detailQuery, ignoreCase = true) ||
                    song.fileName.contains(detailQuery, ignoreCase = true)
            }
        }
    }
    val displayedFolderEntries = remember(displayedFolderSource, detailQuery) {
        if (detailQuery.isBlank()) {
            displayedFolderSource
        } else {
            displayedFolderSource.filter { entry ->
                entry.displayName.contains(detailQuery, ignoreCase = true) ||
                    entry.path.contains(detailQuery, ignoreCase = true)
            }
        }
    }
    val randomFolderEntrySongs = remember(displayedFolderEntries, allPlaylistSongs) {
        val normalizedFolders = displayedFolderEntries.map { it.path.normalizeFolderPath() }
        allPlaylistSongs
            .filter { song ->
                val songFolder = song.folderPath().normalizeFolderPath()
                normalizedFolders.any { folder -> songFolder.startsWith(folder) }
            }
            .distinctBy { it.id }
    }
    val songsListState = rememberLazyListState()
    val foldersListState = rememberLazyListState()
    RestoreListScrollAfterSearch(
        searchExpanded = searchExpanded,
        query = searchQuery,
        listState = songsListState
    )
    RestoreListScrollAfterSearch(
        searchExpanded = searchExpanded,
        query = searchQuery,
        listState = foldersListState
    )
    val displayedSongIndexByKey = remember(displayedSongs) {
        buildMap {
            displayedSongs.forEachIndexed { index, song ->
                put(song.playlistIdentityKey(), index + 1)
            }
        }
    }
    val currentSongItemIndex = remember(
        displayedSongIndexByKey,
        currentSong?.playlistIdentityKey(),
        selectionMode,
        selectedTab
    ) {
        if (selectionMode || selectedTab != FolderPlaylistTab.Songs) {
            -1
        } else {
            currentSong?.playlistIdentityKey()?.let(displayedSongIndexByKey::get) ?: -1
        }
    }
    val currentSortLabel = when (selectedTab) {
        FolderPlaylistTab.Songs -> com.ella.music.ui.components.sortLabel(
            songSortMode.labelRes,
            songSortMode.isDescending()
        )
        FolderPlaylistTab.Folders -> com.ella.music.ui.components.sortLabel(
            folderSortMode.labelRes,
            folderSortMode.isDescending()
        )
    }

    // ---- Multi-select helpers (shared by the Songs and Folders tabs) ----
    val displayedKeysForTab: List<String> = when (selectedTab) {
        FolderPlaylistTab.Songs -> displayedSongs.map { it.playlistIdentityKey() }
        FolderPlaylistTab.Folders -> displayedFolderEntries.map { it.path }
    }
    val currentSelectedKeys: Set<String> = when (selectedTab) {
        FolderPlaylistTab.Songs -> selectedSongKeys
        FolderPlaylistTab.Folders -> selectedFolderPaths
    }
    val selectedSongsForDrag = remember(displayedSongs, selectedSongKeys, selectedTab) {
        if (selectedTab == FolderPlaylistTab.Songs) {
            displayedSongs.filter { it.playlistIdentityKey() in selectedSongKeys }
        } else {
            emptyList()
        }
    }
    val displayedIndexByKey = remember(displayedKeysForTab) {
        buildMap { displayedKeysForTab.forEachIndexed { index, key -> put(key, index) } }
    }
    val draggedSelectionKeys = remember(draggedKey, currentSelectedKeys, displayedKeysForTab) {
        val activeKey = draggedKey
        if (activeKey == null || activeKey !in currentSelectedKeys) {
            emptySet()
        } else {
            currentSelectedKeys
        }
    }
    val reorderableSongs = remember(displayedSongs, draggedKey, draggedSelectionKeys) {
        if (selectedTab != FolderPlaylistTab.Songs || draggedSelectionKeys.size <= 1) {
            displayedSongs
        } else {
            displayedSongs.filter { song ->
                val key = song.playlistIdentityKey()
                key == draggedKey || key !in draggedSelectionKeys
            }
        }
    }
    val reorderableFolderEntries = remember(displayedFolderEntries, draggedKey, draggedSelectionKeys) {
        if (selectedTab != FolderPlaylistTab.Folders || draggedSelectionKeys.size <= 1) {
            displayedFolderEntries
        } else {
            displayedFolderEntries.filter { entry ->
                entry.path == draggedKey || entry.path !in draggedSelectionKeys
            }
        }
    }
    val songReorderableState = rememberReorderableLazyListState(
        lazyListState = songsListState,
        onMove = { from, to ->
            if (!songReorderEnabled) return@rememberReorderableLazyListState
            val fromSong = reorderableSongs.getOrNull(from.index - 1)
                ?: return@rememberReorderableLazyListState
            val toSong = reorderableSongs.getOrNull(to.index - 1)
                ?: return@rememberReorderableLazyListState
            val fromIndex = manualSongs.indexOfFirst { it.playlistIdentityKey() == fromSong.playlistIdentityKey() }
            val toIndex = manualSongs.indexOfFirst { it.playlistIdentityKey() == toSong.playlistIdentityKey() }
            if (fromIndex !in manualSongs.indices || toIndex !in manualSongs.indices) {
                return@rememberReorderableLazyListState
            }
            manualSongs = manualSongs.moveSelectedItemsAsBlock(
                from = fromIndex,
                to = toIndex,
                selectedKeys = selectedSongKeys,
                keyOf = { it.playlistIdentityKey() }
            )
        }
    )
    val folderReorderableState = rememberReorderableLazyListState(
        lazyListState = foldersListState,
        onMove = { from, to ->
            if (!folderReorderEnabled) return@rememberReorderableLazyListState
            val fromEntry = reorderableFolderEntries.getOrNull(from.index - 1)
                ?: return@rememberReorderableLazyListState
            val toEntry = reorderableFolderEntries.getOrNull(to.index - 1)
                ?: return@rememberReorderableLazyListState
            val fromIndex = manualFolderEntries.indexOfFirst { it.path == fromEntry.path }
            val toIndex = manualFolderEntries.indexOfFirst { it.path == toEntry.path }
            if (fromIndex !in manualFolderEntries.indices || toIndex !in manualFolderEntries.indices) {
                return@rememberReorderableLazyListState
            }
            manualFolderEntries = manualFolderEntries.moveSelectedItemsAsBlock(
                from = fromIndex,
                to = toIndex,
                selectedKeys = selectedFolderPaths,
                keyOf = FolderPlaylistFolderEntry::path
            )
        }
    )
    fun persistSongOrder() {
        playlist?.let { target ->
            scope.launch {
                mainViewModel.settingsManager.setFolderPlaylistSongOrder(
                    target.id,
                    manualSongs.map { it.playlistIdentityKey() }
                )
            }
        }
    }
    fun persistFolderOrder() {
        playlist?.let { target ->
            scope.launch {
                mainViewModel.settingsManager.setFolderPlaylistFolderOrder(
                    target.id,
                    manualFolderEntries.map { it.path }
                )
            }
        }
    }
    val selectedVisibleCount = displayedKeysForTab.count { it in currentSelectedKeys }
    val rangeSelectionAvailable = run {
        val anchor = rangeAnchorKey
        val target = rangeTargetKey
        anchor != null && target != null && anchor != target &&
            anchor in currentSelectedKeys && target in currentSelectedKeys &&
            anchor in displayedIndexByKey && target in displayedIndexByKey
    }

    fun setSelectedKeys(keys: Set<String>) {
        when (selectedTab) {
            FolderPlaylistTab.Songs -> selectedSongKeys = keys
            FolderPlaylistTab.Folders -> selectedFolderPaths = keys
        }
    }

    fun exitSelection() {
        selectionMode = false
        selectedSongKeys = emptySet()
        selectedFolderPaths = emptySet()
        rangeAnchorKey = null
        rangeTargetKey = null
    }

    fun updateAnchorsForManualSelection(key: String, selectedNow: Boolean) {
        if (selectedNow) {
            when {
                rangeAnchorKey == null -> rangeAnchorKey = key
                rangeAnchorKey == key -> Unit
                else -> rangeTargetKey = key
            }
        } else {
            if (rangeTargetKey == key) rangeTargetKey = null
            if (rangeAnchorKey == key) {
                rangeAnchorKey = rangeTargetKey ?: currentSelectedKeys.firstOrNull { it != key }
                rangeTargetKey = null
            }
        }
    }

    fun toggleKey(key: String) {
        val selecting = key !in currentSelectedKeys
        val next = if (selecting) currentSelectedKeys + key else currentSelectedKeys - key
        setSelectedKeys(next)
        updateAnchorsForManualSelection(key, selecting)
    }

    fun selectAllCurrent() {
        val displayedKeys = displayedKeysForTab.toSet()
        if (displayedKeys.isNotEmpty() && displayedKeys.all { it in currentSelectedKeys }) {
            setSelectedKeys(emptySet())
            rangeAnchorKey = null
            rangeTargetKey = null
            selectionMode = false
        } else {
            setSelectedKeys(displayedKeys)
            rangeAnchorKey = displayedKeysForTab.firstOrNull()
            rangeTargetKey = displayedKeysForTab.lastOrNull()
            selectionMode = true
        }
    }

    fun applyRangeSelection() {
        val anchor = rangeAnchorKey ?: return
        val target = rangeTargetKey ?: return
        val anchorIndex = displayedIndexByKey[anchor] ?: return
        val targetIndex = displayedIndexByKey[target] ?: return
        if (anchorIndex == targetIndex) return
        val bounds = if (anchorIndex < targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
        setSelectedKeys(currentSelectedKeys + bounds.map { displayedKeysForTab[it] })
        rangeAnchorKey = null
        rangeTargetKey = null
    }

    // Songs that the selection-mode actions operate on: the picked songs (Songs tab) or every song
    // inside the picked folders (Folders tab).
    fun selectedActionSongs(): List<Song> = when (selectedTab) {
        FolderPlaylistTab.Songs -> displayedSongs.filter { it.playlistIdentityKey() in selectedSongKeys }
        FolderPlaylistTab.Folders -> {
            val normalizedSelected = selectedFolderPaths.map { it.normalizeFolderPath() }
            allPlaylistSongs.filter { song ->
                val songFolder = song.folderPath().normalizeFolderPath()
                normalizedSelected.any { songFolder.startsWith(it) }
            }
        }
    }

    fun removeSelectedFoldersFromPlaylist() {
        val target = playlist ?: return
        val remaining = target.folders.filter { it !in selectedFolderPaths }
        scope.launch {
            if (remaining.isEmpty()) {
                mainViewModel.settingsManager.deleteFolderPlaylist(target.id)
                exitSelection()
                onBack()
            } else {
                mainViewModel.settingsManager.upsertFolderPlaylist(target.id, target.name, remaining)
                exitSelection()
            }
        }
    }

    BackHandler(enabled = selectionMode || searchExpanded) {
        when {
            selectionMode -> exitSelection()
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
            title = if (selectionMode) {
                stringResource(R.string.library_selected_fraction, selectedVisibleCount, displayedKeysForTab.size)
            } else {
                playlist?.name ?: stringResource(R.string.folder_playlist_title)
            },
            color = ellaPageBackground(),
            onDoubleTapTitle = {
                scope.launch {
                    when (selectedTab) {
                        FolderPlaylistTab.Songs -> songsListState.animateScrollToItem(0)
                        FolderPlaylistTab.Folders -> foldersListState.animateScrollToItem(0)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = { if (selectionMode) exitSelection() else onBack() }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
            },
            actions = {
                if (selectionMode) {
                    IconButton(onClick = {
                        val selected = selectedActionSongs()
                        if (selected.isNotEmpty()) {
                            playerViewModel.playNext(selected)
                            Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                            exitSelection()
                        }
                    }) {
                        com.ella.music.ui.components.PlayNextActionIcon(
                            contentDescription = stringResource(R.string.song_more_play_next),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        val selected = selectedActionSongs()
                        if (selected.isNotEmpty()) playlistPickerSongs = selected
                    }) {
                        com.ella.music.ui.components.AddToPlaylistActionIcon(
                            contentDescription = stringResource(R.string.player_add_to_playlist),
                            tint = MiuixTheme.colorScheme.primary
                        )
                    }
                    IconButton(onClick = {
                        when (selectedTab) {
                            FolderPlaylistTab.Songs -> {
                                val selected = selectedActionSongs()
                                if (selected.isNotEmpty()) {
                                    requestDeleteSongs(selected)
                                    exitSelection()
                                }
                            }
                            FolderPlaylistTab.Folders -> {
                                if (selectedFolderPaths.isNotEmpty()) removeSelectedFoldersFromPlaylist()
                            }
                        }
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Delete,
                            contentDescription = stringResource(
                                if (selectedTab == FolderPlaylistTab.Folders) R.string.common_remove else R.string.common_delete
                            ),
                            tint = Color(0xFFE5484D),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                IconButton(onClick = {
                    selectionMode = !selectionMode
                    selectedSongKeys = emptySet()
                    selectedFolderPaths = emptySet()
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.SelectAll,
                        contentDescription = stringResource(R.string.common_multi_select),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(24.dp)
                    )
                }
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
                    items = when (selectedTab) {
                        FolderPlaylistTab.Songs -> directionalSortModeDropdownItems(
                            fields = listOf(
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_custom),
                                    ascendingMode = FolderPlaylistSongSortMode.Custom,
                                    descendingMode = FolderPlaylistSongSortMode.CustomDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_title),
                                    ascendingMode = FolderPlaylistSongSortMode.Title,
                                    descendingMode = FolderPlaylistSongSortMode.TitleDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_file_name),
                                    ascendingMode = FolderPlaylistSongSortMode.FileName,
                                    descendingMode = FolderPlaylistSongSortMode.FileNameDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_duration),
                                    ascendingMode = FolderPlaylistSongSortMode.DurationAsc,
                                    descendingMode = FolderPlaylistSongSortMode.Duration
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_year),
                                    ascendingMode = FolderPlaylistSongSortMode.YearAsc,
                                    descendingMode = FolderPlaylistSongSortMode.YearDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_date_added),
                                    ascendingMode = FolderPlaylistSongSortMode.DateAddedAsc,
                                    descendingMode = FolderPlaylistSongSortMode.DateAdded
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_date_modified),
                                    ascendingMode = FolderPlaylistSongSortMode.DateModifiedAsc,
                                    descendingMode = FolderPlaylistSongSortMode.DateModified
                                )
                            ),
                            selectedMode = songSortMode,
                            onSelect = { mode ->
                                scope.launch { mainViewModel.settingsManager.setFolderPlaylistDetailSongSortIndex(mode.ordinal) }
                            }
                        )
                        FolderPlaylistTab.Folders -> directionalSortModeDropdownItems(
                            fields = listOf(
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_custom),
                                    ascendingMode = FolderPlaylistFolderSortMode.Custom,
                                    descendingMode = FolderPlaylistFolderSortMode.CustomDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_name),
                                    ascendingMode = FolderPlaylistFolderSortMode.Name,
                                    descendingMode = FolderPlaylistFolderSortMode.NameDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_song_count),
                                    ascendingMode = FolderPlaylistFolderSortMode.SongCountAsc,
                                    descendingMode = FolderPlaylistFolderSortMode.SongCount
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.folder_sort_album_count),
                                    ascendingMode = FolderPlaylistFolderSortMode.AlbumCountAsc,
                                    descendingMode = FolderPlaylistFolderSortMode.AlbumCount
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_duration),
                                    ascendingMode = FolderPlaylistFolderSortMode.DurationAsc,
                                    descendingMode = FolderPlaylistFolderSortMode.Duration
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_date_modified),
                                    ascendingMode = FolderPlaylistFolderSortMode.DateModifiedAsc,
                                    descendingMode = FolderPlaylistFolderSortMode.DateModified
                                )
                            ),
                            selectedMode = folderSortMode,
                            onSelect = { mode ->
                                scope.launch { mainViewModel.settingsManager.setFolderPlaylistDetailFolderSortIndex(mode.ordinal) }
                            }
                        )
                    }
                )
                }
            }
        )

        if (playlist == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.playlist_not_found),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            return@Column
        }

        if (searchExpanded) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = {
                    keyboardController?.hide()
                    focusManager.clearFocus()
                    searchExpanded = false
                },
                placeholder = when (selectedTab) {
                    FolderPlaylistTab.Songs -> stringResource(R.string.folder_detail_search_placeholder)
                    FolderPlaylistTab.Folders -> stringResource(R.string.folder_search_placeholder)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FolderPlaylistTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                Text(
                    text = stringResource(tab.labelRes),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                        )
                        .clickable { selectedTab = tab }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
        when (selectedTab) {
            FolderPlaylistTab.Songs -> {
                LazyColumn(
                    state = songsListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 130.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShuffleAllSummaryButton(
                                visible = !selectionMode && displayedSongs.isNotEmpty(),
                                onClick = {
                                    playerViewModel.setPlaylist(displayedSongs.shuffled(), 0)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            )
                            Text(
                                text = stringResource(R.string.folder_playlist_detail_summary_sorted, displayedSongs.size, playlist.folders.size, currentSortLabel),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                            )
                        }
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
                    if (playlistSongs.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.folder_playlist_empty_songs),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 24.dp)
                            )
                        }
                    }
                    itemsIndexed(reorderableSongs, key = { _, song -> song.playlistIdentityKey() }) { index, song ->
                        ReorderableItem(
                            state = songReorderableState,
                            key = song.playlistIdentityKey()
                        ) { isDragging ->
                            val songKey = song.playlistIdentityKey()
                            val albumArtUri = remember(song.albumId) {
                                song.albumId.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri)
                            }
                            val dragHandleModifier = Modifier.draggableHandle(
                                dragGestureDetector = ImmediateOrLongPressDragGestureDetector,
                                onDragStarted = {
                                    pressedDragHandleKey = songKey
                                    draggedKey = songKey
                                },
                                onDragStopped = {
                                    draggedKey = null
                                    pressedDragHandleKey = null
                                    persistSongOrder()
                                }
                            )
                            SongItem(
                                song = song,
                                titleOverride = if (
                                    songSortMode == FolderPlaylistSongSortMode.FileName ||
                                        songSortMode == FolderPlaylistSongSortMode.FileNameDesc
                                ) {
                                    song.fileName.ifBlank { song.path.substringAfterLast('/') }
                                } else {
                                    null
                                },
                                isCurrent = currentSong?.playlistIdentityKey() == song.playlistIdentityKey(),
                                albumArtUri = albumArtUri,
                                loadCoverArt = mainViewModel::getCoverArtBitmap,
                                loadAudioInfo = mainViewModel::getAudioInfo,
                                loadSongTagInfo = mainViewModel::getSongTagInfo,
                                selectionMode = selectionMode,
                                selected = songKey in selectedSongKeys,
                                dragSelectedSongs = selectedSongsForDrag,
                                showPlayNextInLists = showPlayNextInLists,
                                isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                                loadSongRating = mainViewModel::getSongRating,
                                onClick = {
                                    if (selectionMode) {
                                        toggleKey(songKey)
                                    } else {
                                        playerViewModel.setPlaylist(displayedSongs, index)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                },
                                onLongClick = {
                                    if (pressedDragHandleKey == songKey) return@SongItem
                                    selectionMode = true
                                    if (songKey !in selectedSongKeys) toggleKey(songKey)
                                },
                                onPlayNext = { playerViewModel.playNext(song) },
                                onMore = { actionSong = song },
                                trailingContent = if (songReorderEnabled) {
                                    { PlaylistDragHandle(isDragging = isDragging, modifier = dragHandleModifier) }
                                } else null,
                                showTrailingContentInSelectionMode = songReorderEnabled
                            )
                        }
                    }
                }
            }
            FolderPlaylistTab.Folders -> {
                LazyColumn(
                    state = foldersListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 130.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShuffleAllSummaryButton(
                                visible = !selectionMode && randomFolderEntrySongs.isNotEmpty(),
                                onClick = {
                                    playerViewModel.setPlaylist(randomFolderEntrySongs.shuffled(), 0)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            )
                            Text(
                                text = stringResource(R.string.folder_playlist_detail_summary_sorted, displayedFolderEntries.sumOf { it.songCount }, playlist.folders.size, currentSortLabel),
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                            )
                        }
                    }
                    items(reorderableFolderEntries, key = { it.path }) { entry ->
                        ReorderableItem(
                            state = folderReorderableState,
                            key = entry.path
                        ) { isDragging ->
                            val isHidden = playlist.hiddenFolders.any { it.equals(entry.path, ignoreCase = true) }
                            val dragHandleModifier = Modifier.draggableHandle(
                                dragGestureDetector = ImmediateOrLongPressDragGestureDetector,
                                onDragStarted = {
                                    pressedDragHandleKey = entry.path
                                    draggedKey = entry.path
                                },
                                onDragStopped = {
                                    draggedKey = null
                                    pressedDragHandleKey = null
                                    persistFolderOrder()
                                }
                            )
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .combinedClickable(
                                        onClick = {
                                            if (selectionMode) {
                                                toggleKey(entry.path)
                                            } else {
                                                onNavigateToFolder(entry.path)
                                            }
                                        },
                                        onLongClick = {
                                            if (pressedDragHandleKey != entry.path) {
                                                selectionMode = true
                                                if (entry.path !in selectedFolderPaths) toggleKey(entry.path)
                                            }
                                        }
                                    ),
                                cornerRadius = 12.dp
                            ) {
                                Row(
                                    modifier = Modifier
                                        .background(
                                            if (entry.path in selectedFolderPaths) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
                                            else Color.Transparent
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (selectionMode) {
                                        SelectionCheck(
                                            selected = entry.path in selectedFolderPaths,
                                            checkColor = Color.White
                                        )
                                        Spacer(modifier = Modifier.size(12.dp))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(MiuixTheme.colorScheme.surfaceContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (entry.coverModel != null) {
                                            SafeCoverImage(
                                                model = entry.coverModel,
                                                contentDescription = entry.displayName,
                                                modifier = Modifier.fillMaxSize(),
                                                sizePx = 256
                                            )
                                        } else {
                                            FolderOutlineIcon(
                                                tint = MiuixTheme.colorScheme.primary,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .padding(9.dp)
                                            )
                                        }
                                    }
                                    Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                                        Text(
                                            text = entry.displayName,
                                            fontWeight = FontWeight.Medium,
                                            fontSize = 15.sp,
                                            color = MiuixTheme.colorScheme.onSurface,
                                            textDecoration = if (isHidden) TextDecoration.LineThrough else TextDecoration.None,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = entry.detailSummaryForSort(folderSortMode, context),
                                            fontSize = 12.sp,
                                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (folderReorderEnabled) {
                                        PlaylistDragHandle(isDragging = isDragging, modifier = dragHandleModifier)
                                    } else if (!selectionMode) {
                                        IconButton(onClick = { folderActionTarget = entry }) {
                                            Icon(
                                                imageVector = MiuixIcons.Regular.More,
                                                contentDescription = stringResource(R.string.player_more_actions),
                                                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                                modifier = Modifier.size(22.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
            LocateCurrentSongFloatingButton(
                listState = songsListState,
                currentItemIndex = currentSongItemIndex,
                locateRequest = locateCurrentSongRequest,
                enabled = selectedTab == FolderPlaylistTab.Songs && !selectionMode,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
            )
            FloatingSelectionControls(
                visible = selectionMode && displayedKeysForTab.isNotEmpty(),
                rangeEnabled = rangeSelectionAvailable,
                allSelected = displayedKeysForTab.isNotEmpty() && selectedVisibleCount == displayedKeysForTab.size,
                onRangeSelect = ::applyRangeSelection,
                onSelectAll = ::selectAllCurrent,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
            )
        }
    }

    SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = { actionSong = null },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        showDelete = false
    )

    folderActionTarget?.let { entry ->
        val targetPlaylist = playlist
        if (targetPlaylist != null) {
            val folderSongs = allPlaylistSongs.filter { song ->
                val songFolder = song.folderPath().normalizeFolderPath()
                val targetFolder = entry.path.normalizeFolderPath()
                songFolder.equals(targetFolder, ignoreCase = true) ||
                    songFolder.startsWith("${targetFolder.trimEnd('/')}/", ignoreCase = true)
            }
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = entry.displayName,
                onDismissRequest = { folderActionTarget = null }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.common_pin_to_top),
                        onClick = {
                            scope.launch {
                                mainViewModel.settingsManager.setFolderPlaylistFolderOrder(
                                    targetPlaylist.id,
                                    (listOf(entry.path) + folderEntries.map(FolderPlaylistFolderEntry::path)).distinct()
                                )
                            }
                            folderActionTarget = null
                        }
                    )
                    EllaMiuixMenuItem(
                        text = stringResource(
                            if (targetPlaylist.hiddenFolders.any { it.equals(entry.path, ignoreCase = true) }) {
                                R.string.folder_playlist_show
                            } else {
                                R.string.folder_playlist_hide
                            }
                        ),
                        onClick = {
                            val hidden = targetPlaylist.hiddenFolders.toMutableList()
                            val existing = hidden.indexOfFirst { it.equals(entry.path, ignoreCase = true) }
                            if (existing >= 0) hidden.removeAt(existing) else hidden += entry.path
                            scope.launch {
                                mainViewModel.settingsManager.setFolderPlaylistHiddenFolders(targetPlaylist.id, hidden)
                            }
                            folderActionTarget = null
                        }
                    )
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.common_remove),
                        onClick = {
                            val remaining = targetPlaylist.folders.filterNot { it.equals(entry.path, ignoreCase = true) }
                            scope.launch {
                                if (remaining.isEmpty()) {
                                    mainViewModel.settingsManager.deleteFolderPlaylist(targetPlaylist.id)
                                    onBack()
                                } else {
                                    mainViewModel.settingsManager.upsertFolderPlaylist(
                                        targetPlaylist.id,
                                        targetPlaylist.name,
                                        remaining
                                    )
                                }
                            }
                            folderActionTarget = null
                        }
                    )
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.folder_playlist_associate),
                        onClick = {
                            associateFolderPaths = listOf(entry.path)
                            folderActionTarget = null
                        }
                    )
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.common_share),
                        onClick = {
                            shareLocalSongs(context, folderSongs)
                            folderActionTarget = null
                        }
                    )
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.song_more_add_to_playlist),
                        onClick = {
                            playlistPickerSongs = folderSongs
                            folderActionTarget = null
                        }
                    )
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.common_add_to_queue),
                        onClick = {
                            playerViewModel.addToPlaylist(folderSongs)
                            folderActionTarget = null
                        }
                    )
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.song_more_play_next),
                        onClick = {
                            playerViewModel.playNext(folderSongs)
                            folderActionTarget = null
                        }
                    )
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.common_add_desktop_shortcut),
                        onClick = {
                            requestPinnedEllaShortcut(
                                context = context,
                                id = "folder_${entry.path.tagIdentityKey()}",
                                label = entry.displayName,
                                route = Screen.FolderDetail.createRoute(entry.path)
                            )
                            folderActionTarget = null
                        }
                    )
                }
            }
        }
    }

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
                    exitSelection()
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
                    exitSelection()
                }
            }
        )
    }
}
