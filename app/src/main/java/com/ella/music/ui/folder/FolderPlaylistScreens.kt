package com.ella.music.ui.folder

import android.net.Uri
import android.widget.Toast
import androidx.annotation.StringRes
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.EllaMiuixTextField
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
import com.ella.music.ui.components.LibrarySecondaryFloatingControlsBottomPadding
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ScanRefreshIconButton
import com.ella.music.ui.components.SelectionCheck
import com.ella.music.ui.components.ShuffleAllFloatingButton
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.basic.Search
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val persistedSortIndex by mainViewModel.settingsManager.folderPlaylistListSortIndex.collectAsState(
        initial = LibrarySortUiState.folderPlaylistListSortIndex
    )
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
    val pinnedPlaylistIds by mainViewModel.settingsManager.pinnedKeysFlow("folder_playlist").collectAsState(initial = emptyList())
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
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPlaylistIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var rangeAnchorId by remember { mutableStateOf<String?>(null) }
    var rangeTargetId by remember { mutableStateOf<String?>(null) }
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
    val sortedPlaylists = remember(playlists, sortMode, pinnedPlaylistIds, songCountMap, durationMap) {
        playlists.sortedForFolderPlaylists(
            mode = sortMode,
            songCountProvider = { songCountMap[it] ?: 0 },
            durationProvider = { durationMap[it] ?: 0L },
            pinnedIds = pinnedPlaylistIds
        )
    }
    val filteredPlaylists = remember(sortedPlaylists, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            sortedPlaylists
        } else {
            sortedPlaylists.filter { playlist ->
                playlist.name.contains(query, ignoreCase = true) ||
                    playlist.folders.any { folder ->
                        folder.contains(query, ignoreCase = true) ||
                            folder.substringAfterLast('/').contains(query, ignoreCase = true)
                    }
            }
        }
    }

    val editorCoverModel = remember(editorDraftFolders, songs) {
        songs.songsForFolderPlaylist(editorDraftFolders.toList()).firstOrNull().folderPlaylistCoverModel()
    }

    // ---- Multi-select helpers for the folder-playlist list ----
    val displayedPlaylistIds = filteredPlaylists.map { it.id }
    val displayedIndexById = remember(displayedPlaylistIds) {
        buildMap { displayedPlaylistIds.forEachIndexed { index, id -> put(id, index) } }
    }
    val selectedVisibleCount = displayedPlaylistIds.count { it in selectedPlaylistIds }
    val rangeSelectionAvailable = run {
        val anchor = rangeAnchorId
        val target = rangeTargetId
        anchor != null && target != null && anchor != target &&
            anchor in selectedPlaylistIds && target in selectedPlaylistIds &&
            anchor in displayedIndexById && target in displayedIndexById
    }

    fun exitSelection() {
        selectionMode = false
        selectedPlaylistIds = emptySet()
        rangeAnchorId = null
        rangeTargetId = null
    }

    fun updateAnchors(id: String, selectedNow: Boolean) {
        if (selectedNow) {
            when {
                rangeAnchorId == null -> rangeAnchorId = id
                rangeAnchorId == id -> Unit
                else -> rangeTargetId = id
            }
        } else {
            if (rangeTargetId == id) rangeTargetId = null
            if (rangeAnchorId == id) {
                rangeAnchorId = rangeTargetId ?: selectedPlaylistIds.firstOrNull { it != id }
                rangeTargetId = null
            }
        }
    }

    fun togglePlaylist(id: String) {
        val selecting = id !in selectedPlaylistIds
        selectedPlaylistIds = if (selecting) selectedPlaylistIds + id else selectedPlaylistIds - id
        updateAnchors(id, selecting)
    }

    fun selectedSongsFor(playlist: FolderPlaylist): List<Song> =
        songs.songsForFolderPlaylist(playlist.folders)

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

    fun selectAllPlaylists() {
        val ids = displayedPlaylistIds.toSet()
        if (ids.isNotEmpty() && ids.all { it in selectedPlaylistIds }) {
            selectedPlaylistIds = emptySet()
            rangeAnchorId = null
            rangeTargetId = null
            selectionMode = false
        } else {
            selectedPlaylistIds = ids
            rangeAnchorId = displayedPlaylistIds.firstOrNull()
            rangeTargetId = displayedPlaylistIds.lastOrNull()
            selectionMode = true
        }
    }

    fun applyRangeSelection() {
        val anchor = rangeAnchorId ?: return
        val target = rangeTargetId ?: return
        val anchorIndex = displayedIndexById[anchor] ?: return
        val targetIndex = displayedIndexById[target] ?: return
        if (anchorIndex == targetIndex) return
        val bounds = if (anchorIndex < targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
        selectedPlaylistIds = selectedPlaylistIds + bounds.map { displayedPlaylistIds[it] }
        rangeAnchorId = target
        rangeTargetId = null
    }

    // Every song contained in the selected folder-playlists, de-duplicated.
    fun selectedActionSongs(): List<Song> =
        filteredPlaylists
            .filter { it.id in selectedPlaylistIds }
            .flatMap { songs.songsForFolderPlaylist(it.folders) }
            .distinctBy { it.playlistIdentityKey() }

    BackHandler(enabled = selectionMode || searchExpanded || moreMenuTarget != null || pendingDelete != null || pendingBulkDelete != null || showEditor) {
        when {
            showEditor -> showEditor = false
            pendingDelete != null -> pendingDelete = null
            pendingBulkDelete != null -> pendingBulkDelete = null
            moreMenuTarget != null -> moreMenuTarget = null
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
                stringResource(R.string.library_selected_fraction, selectedPlaylistIds.size, filteredPlaylists.size)
            } else {
                stringResource(R.string.folder_playlist_title)
            },
            color = ellaPageBackground(),
            navigationIcon = {
                if (showBackButton || selectionMode) {
                    IconButton(onClick = { if (selectionMode) exitSelection() else onBack() }) {
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
                if (selectionMode) {
                    IconButton(onClick = {
                        val selected = selectedActionSongs()
                        if (selected.isNotEmpty()) playerViewModel.setPlaylist(selected, 0)
                        exitSelection()
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
                            exitSelection()
                        }
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Forward,
                            contentDescription = stringResource(R.string.song_more_play_next),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        val selected = selectedActionSongs()
                        if (selected.isNotEmpty()) {
                            playerViewModel.addToPlaylist(selected)
                            Toast.makeText(context, R.string.song_more_added_to_queue, Toast.LENGTH_SHORT).show()
                            exitSelection()
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
                        Icon(
                            imageVector = MiuixIcons.Regular.Add,
                            contentDescription = stringResource(R.string.player_add_to_playlist),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                } else {
                    IconButton(onClick = {
                        selectionMode = !selectionMode
                        selectedPlaylistIds = emptySet()
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.SelectAll,
                            contentDescription = stringResource(R.string.common_multi_select),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
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
                Text(
                    text = if (selectionMode) {
                        stringResource(R.string.library_selected_fraction, selectedPlaylistIds.size, filteredPlaylists.size)
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
                Text(
                    text = stringResource(R.string.common_create),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = ::openNewFolderPlaylistEditor)
                )
            }
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 130.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredPlaylists, key = { it.id }) { playlist ->
                    val songCount = songCountMap[playlist] ?: 0
                    val duration = durationMap[playlist] ?: 0L
                    FolderPlaylistCard(
                        playlist = playlist,
                        songCount = songCount,
                        duration = duration,
                        coverModel = coverModelMap[playlist],
                        isPinned = playlist.id in pinnedPlaylistIds,
                        selectionMode = selectionMode,
                        selected = playlist.id in selectedPlaylistIds,
                        onClick = {
                            if (selectionMode) togglePlaylist(playlist.id)
                            else onOpenPlaylist(playlist.id)
                        },
                        onLongClick = {
                            selectionMode = true
                            if (playlist.id !in selectedPlaylistIds) togglePlaylist(playlist.id)
                        },
                        onSync = {
                            scope.launch {
                                mainViewModel.refreshFolderPlaylistFolders(playlist.folders)
                                Toast.makeText(context, R.string.folder_playlist_more_refresh, Toast.LENGTH_SHORT).show()
                            }
                        },
                        onMore = { moreMenuTarget = playlist }
                    )
                }
            }
            FloatingSelectionControls(
                visible = selectionMode && displayedPlaylistIds.isNotEmpty(),
                rangeEnabled = rangeSelectionAvailable,
                allSelected = displayedPlaylistIds.isNotEmpty() && selectedVisibleCount == displayedPlaylistIds.size,
                onRangeSelect = ::applyRangeSelection,
                onSelectAll = ::selectAllPlaylists,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
            )
            }
        }
    }

    moreMenuTarget?.let { playlist ->
        val isPinned = playlist.id in pinnedPlaylistIds
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = playlist.name,
            onDismissRequest = { moreMenuTarget = null }
        ) {
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                EllaMiuixMenuItem(
                    text = stringResource(if (isPinned) R.string.common_unpin else R.string.common_pin_to_top),
                    onClick = {
                        scope.launch {
                            mainViewModel.settingsManager.setPinned(
                                "folder_playlist",
                                playlist.id,
                                !isPinned
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
                exitSelection()
            }
        )
    }
}
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
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val playlist = remember(playlists, playlistId) {
        playlists.firstOrNull { it.id == playlistId || it.name == playlistId }
    }
    val playlistSongs = remember(playlist, songs) {
        playlist?.let { songs.songsForFolderPlaylist(it.folders) }.orEmpty()
    }
    var selectedTab by rememberSaveable(playlistId) { mutableStateOf(FolderPlaylistTab.Songs) }
    var searchExpanded by rememberSaveable(playlistId) { mutableStateOf(false) }
    var searchQuery by rememberSaveable(playlistId) { mutableStateOf("") }
    var selectionMode by rememberSaveable(playlistId) { mutableStateOf(false) }
    var selectedSongKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var selectedFolderPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var rangeAnchorKey by remember { mutableStateOf<String?>(null) }
    var rangeTargetKey by remember { mutableStateOf<String?>(null) }
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
            val folderSongs = songs.filter { it.folderPath().normalizeFolderPath().startsWith(normalized) }
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
    val sortedPlaylistSongs = remember(playlistSongs, songSortMode) {
        playlistSongs.sortedForFolderPlaylistDetail(songSortMode)
    }
    val sortedFolderEntries = remember(folderEntries, folderSortMode) {
        folderEntries.sortedForFolderPlaylistDetail(folderSortMode)
    }
    val displayedSongs = remember(sortedPlaylistSongs, detailQuery) {
        if (detailQuery.isBlank()) {
            sortedPlaylistSongs
        } else {
            sortedPlaylistSongs.filter { song ->
                song.title.contains(detailQuery, ignoreCase = true) ||
                    song.artist.contains(detailQuery, ignoreCase = true) ||
                    song.album.contains(detailQuery, ignoreCase = true) ||
                    song.fileName.contains(detailQuery, ignoreCase = true)
            }
        }
    }
    val displayedFolderEntries = remember(sortedFolderEntries, detailQuery) {
        if (detailQuery.isBlank()) {
            sortedFolderEntries
        } else {
            sortedFolderEntries.filter { entry ->
                entry.displayName.contains(detailQuery, ignoreCase = true) ||
                    entry.path.contains(detailQuery, ignoreCase = true)
            }
        }
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
    val displayedIndexByKey = remember(displayedKeysForTab) {
        buildMap { displayedKeysForTab.forEachIndexed { index, key -> put(key, index) } }
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
        rangeAnchorKey = target
        rangeTargetKey = null
    }

    // Songs that the selection-mode actions operate on: the picked songs (Songs tab) or every song
    // inside the picked folders (Folders tab).
    fun selectedActionSongs(): List<Song> = when (selectedTab) {
        FolderPlaylistTab.Songs -> displayedSongs.filter { it.playlistIdentityKey() in selectedSongKeys }
        FolderPlaylistTab.Folders -> {
            val normalizedSelected = selectedFolderPaths.map { it.normalizeFolderPath() }
            playlistSongs.filter { song ->
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
                        Icon(
                            imageVector = MiuixIcons.Regular.Play,
                            contentDescription = stringResource(R.string.song_more_play_next),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    IconButton(onClick = {
                        val selected = selectedActionSongs()
                        if (selected.isNotEmpty()) playlistPickerSongs = selected
                    }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Add,
                            contentDescription = stringResource(R.string.player_add_to_playlist),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
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
                        Text(
                            text = stringResource(R.string.folder_playlist_detail_summary_sorted, displayedSongs.size, playlist.folders.size, currentSortLabel),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
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
                    itemsIndexed(displayedSongs, key = { _, song -> song.playlistIdentityKey() }) { index, song ->
                        val songKey = song.playlistIdentityKey()
                        val albumArtUri = remember(song.albumId) {
                            song.albumId.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri)
                        }
                        SongItem(
                            song = song,
                            isCurrent = currentSong?.playlistIdentityKey() == song.playlistIdentityKey(),
                            albumArtUri = albumArtUri,
                            loadCoverArt = mainViewModel::getCoverArtBitmap,
                            loadAudioInfo = mainViewModel::getAudioInfo,
                            selectionMode = selectionMode,
                            selected = songKey in selectedSongKeys,
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
                                selectionMode = true
                                if (songKey !in selectedSongKeys) toggleKey(songKey)
                            },
                            onPlayNext = { playerViewModel.playNext(song) },
                            onMore = { actionSong = song }
                        )
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
                        Text(
                            text = stringResource(R.string.folder_playlist_detail_summary_sorted, displayedFolderEntries.sumOf { it.songCount }, playlist.folders.size, currentSortLabel),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                        )
                    }
                    items(displayedFolderEntries, key = { it.path }) { entry ->
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
                                        selectionMode = true
                                        if (entry.path !in selectedFolderPaths) toggleKey(entry.path)
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
                                Column(modifier = Modifier.padding(start = 14.dp)) {
                                    Text(
                                        text = entry.displayName,
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 15.sp,
                                        color = MiuixTheme.colorScheme.onSurface,
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
                            }
                        }
                    }
                }
            }
        }
            ShuffleAllFloatingButton(
                visible = !selectionMode && selectedTab == FolderPlaylistTab.Songs && displayedSongs.isNotEmpty(),
                onClick = {
                    playerViewModel.setPlaylist(displayedSongs.shuffled(), 0)
                    if (openPlayerOnPlay) onNavigateToPlayer()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibrarySecondaryFloatingControlsBottomPadding)
            )
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
@Composable
fun LinkToFolderPlaylistSheet(
    show: Boolean,
    songs: List<Song>,
    folderPlaylists: List<FolderPlaylist>,
    onDismiss: () -> Unit,
    onLink: (FolderPlaylist) -> Unit
) {
    if (!show) return
    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = stringResource(R.string.folder_playlist_associate),
        onDismissRequest = onDismiss
    ) {
        if (folderPlaylists.isEmpty()) {
            Text(
                text = stringResource(R.string.folder_playlist_empty),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                items(folderPlaylists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLink(playlist) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FolderOutlineIcon(
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(
                                text = playlist.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.folder_playlist_card_summary, playlist.folders.size, 0),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun FolderPlaylistEditorSheet(
    show: Boolean,
    target: FolderPlaylist?,
    availableFolders: List<String>,
    songs: List<Song>,
    coverModel: Any?,
    draftName: String,
    onDraftNameChange: (String) -> Unit,
    selectedFolders: Set<String>,
    onSelectedFoldersChange: (Set<String>) -> Unit,
    pinnedFolders: Set<String>,
    onPinnedFoldersChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onSave: (FolderPlaylist?, String, List<String>) -> Unit
) {
    if (!show) return
    var searchQuery by remember { mutableStateOf("") }
    var editorSort by remember { mutableStateOf(EditorFolderSort.Name) }

    val filteredFolders = remember(availableFolders, searchQuery) {
        if (searchQuery.isBlank()) availableFolders
        else availableFolders.filter { folder ->
            folder.contains(searchQuery, ignoreCase = true) ||
                folder.substringAfterLast('/').contains(searchQuery, ignoreCase = true)
        }
    }
    // Calculating every nested folder's stats can be expensive in a large library. Keep the
    // editor sheet responsive, then fill the optional sort data after it has appeared.
    val editorFolderStats by produceState<Map<String, EditorFolderStats>>(
        initialValue = emptyMap(),
        availableFolders,
        songs
    ) {
        value = withContext(Dispatchers.Default) {
            val songsByFolder = songs.groupBy { it.folderPath().normalizeFolderPath() }
            availableFolders.associateWith { folder ->
                val folderSongs = songsByFolder[folder.normalizeFolderPath()].orEmpty()
                EditorFolderStats(
                    songCount = folderSongs.size,
                    dateModified = folderSongs.maxOfOrNull(Song::dateModified) ?: 0L
                )
            }
        }
    }

    // Pin folders to the top using the session-persistent pinnedFolders set, which only grows as
    // the user selects new folders and never shrinks on uncheck. This keeps a previously-selected
    // folder pinned even after an accidental mis-tap, until the editor target changes.
    val sortedFilteredFolders = remember(filteredFolders, editorSort, pinnedFolders, editorFolderStats) {
        val base = when (editorSort) {
            EditorFolderSort.Name -> filteredFolders.sortedBy { it.substringAfterLast('/').lowercase() }
            EditorFolderSort.NameDesc -> filteredFolders.sortedByDescending { it.substringAfterLast('/').lowercase() }
            EditorFolderSort.ModifiedTime -> filteredFolders.sortedWith(
                compareByDescending<String> { editorFolderStats[it]?.dateModified ?: 0L }
                    .thenBy { it.substringAfterLast('/').lowercase() }
            )
            EditorFolderSort.ModifiedTimeAsc -> filteredFolders.sortedWith(
                compareBy<String> { editorFolderStats[it]?.dateModified ?: 0L }
                    .thenBy { it.substringAfterLast('/').lowercase() }
            )
            EditorFolderSort.SongCount -> filteredFolders.sortedWith(
                compareByDescending<String> { editorFolderStats[it]?.songCount ?: 0 }
                    .thenBy { it.substringAfterLast('/').lowercase() }
            )
            EditorFolderSort.SongCountAsc -> filteredFolders.sortedWith(
                compareBy<String> { editorFolderStats[it]?.songCount ?: 0 }
                    .thenBy { it.substringAfterLast('/').lowercase() }
            )
        }
        base.sortedWith(
            compareByDescending<String> { it in pinnedFolders }
                .thenBy { base.indexOf(it) }
        )
    }

    // Each folder row shows that folder's own cover (first song in it), not the playlist cover.
    val folderCovers = remember(sortedFilteredFolders, songs) {
        val firstByFolder = HashMap<String, Song>()
        songs.forEach { song ->
            val normalized = song.folderPath().normalizeFolderPath()
            if (normalized !in firstByFolder) firstByFolder[normalized] = song
        }
        sortedFilteredFolders.associateWith { folder ->
            val normalized = folder.normalizeFolderPath()
            firstByFolder[normalized].folderPlaylistCoverModel()
        }
    }

    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = if (target == null) {
            stringResource(R.string.folder_playlist_create)
        } else {
            stringResource(R.string.folder_playlist_edit)
        },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                if (coverModel != null) {
                    SafeCoverImage(
                        model = coverModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        sizePx = 384
                    )
                } else {
                    FolderOutlineIcon(
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            EllaMiuixTextField(
                value = draftName,
                onValueChange = onDraftNameChange,
                label = stringResource(R.string.playlist_name_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (availableFolders.size > 6) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EllaMiuixTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = stringResource(R.string.common_search),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    SortDropdownMenu(
                        items = directionalSortModeDropdownItems(
                            fields = listOf(
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_date_modified),
                                    ascendingMode = EditorFolderSort.ModifiedTimeAsc,
                                    descendingMode = EditorFolderSort.ModifiedTime
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_name),
                                    ascendingMode = EditorFolderSort.Name,
                                    descendingMode = EditorFolderSort.NameDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_song_count),
                                    ascendingMode = EditorFolderSort.SongCountAsc,
                                    descendingMode = EditorFolderSort.SongCount
                                )
                            ),
                            selectedMode = editorSort,
                            onSelect = { editorSort = it }
                        )
                    )
                }
            }
            Text(
                text = stringResource(R.string.folder_playlist_selected_count, selectedFolders.size),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                sortedFilteredFolders.forEach { folder ->
                    val checked = folder in selectedFolders
                    fun toggle(next: Boolean) {
                        if (next) {
                            onSelectedFoldersChange(selectedFolders + folder)
                            onPinnedFoldersChange(pinnedFolders + folder)
                        } else {
                            onSelectedFoldersChange(selectedFolders - folder)
                            // Intentionally do NOT remove from pinnedFolders so the folder
                            // stays pinned for the rest of this editor session.
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { toggle(!checked) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val cover = folderCovers[folder]
                            if (cover != null) {
                                SafeCoverImage(
                                    model = cover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    sizePx = 192
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
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = folder.folderDisplayName(stringResource(R.string.folder_root)),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = folder,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = checked,
                            onCheckedChange = { toggle(it) }
                        )
                    }
                }
            }
            Button(
                onClick = { onSave(target, draftName, selectedFolders.toList()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Text(text = stringResource(R.string.common_save))
            }
        }
    }
}

private data class EditorFolderStats(
    val songCount: Int,
    val dateModified: Long
)

