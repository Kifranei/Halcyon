package com.ella.music.ui.folder

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.settings.findComponentActivity
import androidx.lifecycle.lifecycleScope
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.components.DirectionalSortField
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.directionalSortDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.listmodel.SortDirection
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import com.ella.music.R
import com.ella.music.data.model.albumIdentityId
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Folder
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun FolderScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit,
    onNavigateToLibraryAnalysis: () -> Unit,
    onNavigateToScanSettings: () -> Unit,
    onFolderClick: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val saveScope = context.findComponentActivity()?.lifecycleScope ?: scope
    val songs by mainViewModel.songs.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val folderPlaylists by mainViewModel.settingsManager.folderPlaylists.collectAsState(initial = emptyList())
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val isScanning by mainViewModel.isScanning.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val scanExcludeFolders by mainViewModel.settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    val pinnedFolderPaths by mainViewModel.settingsManager.pinnedKeysFlow("folder").collectAsState(initial = emptyList())
    val folderSortIndex by mainViewModel.settingsManager.folderListSortIndex.collectAsState(initial = LibrarySortUiState.folderListSortIndex)
    val folderSortMode = FolderListSortMode.entries.getOrElse(folderSortIndex) { FolderListSortMode.Name }
    val folderDetailSongSortIndex by mainViewModel.settingsManager.folderDetailSongSortIndex.collectAsState(
        initial = LibrarySortUiState.folderDetailSongSortIndex
    )
    LaunchedEffect(folderSortIndex) {
        LibrarySortUiState.folderListSortIndex = folderSortIndex
    }
    LaunchedEffect(folderDetailSongSortIndex) {
        LibrarySortUiState.folderDetailSongSortIndex = folderDetailSongSortIndex
    }
    var folderToBlock by remember { mutableStateOf<String?>(null) }
    var folderMenuTarget by remember { mutableStateOf<FolderTreeEntry?>(null) }
    var associateFolderPaths by remember { mutableStateOf<List<String>?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<com.ella.music.data.model.Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<com.ella.music.data.model.Song>?>(null) }
    var sortExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var scrollToTopRequest by remember { mutableStateOf(0) }

    val rootFolderPath = remember(songs) { songs.commonFolderRoot() }
    val rootSongs = songs
    val rootChildFolders = remember(songs, rootFolderPath) { songs.childFoldersOf(context, rootFolderPath) }
    fun songsForFolder(folder: FolderTreeEntry): List<com.ella.music.data.model.Song> =
        songs.recursiveSongsInFolder(folder.path).sortedForFolderDetail(
            FolderSongSortMode.entries.getOrElse(
                folderDetailSongSortIndex
            ) { FolderSongSortMode.Title }
        )

    BackHandler(enabled = sortExpanded || searchExpanded || folderToBlock != null || folderMenuTarget != null || associateFolderPaths != null) {
        when {
            associateFolderPaths != null -> associateFolderPaths = null
            folderMenuTarget != null -> folderMenuTarget = null
            folderToBlock != null -> folderToBlock = null
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
            EllaSmallTopAppBar(
                title = stringResource(R.string.tab_folder),
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
                titleStartPadding = if (showBackButton) 64.dp else 20.dp,
                onDoubleTapTitle = { scrollToTopRequest++ },
                actions = {
                    IconButton(onClick = onNavigateToScanSettings) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Settings,
                            contentDescription = stringResource(R.string.folder_scan_settings),
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
                        items = directionalSortDropdownItems(
                            fields = FolderListSortField.entries.map { field ->
                                DirectionalSortField(
                                    field = field,
                                    text = stringResource(field.labelRes),
                                    defaultDirection = when (field) {
                                        FolderListSortField.Name -> SortDirection.Ascending
                                        FolderListSortField.DateModified,
                                        FolderListSortField.SongCount,
                                        FolderListSortField.AlbumCount,
                                        FolderListSortField.Duration -> SortDirection.Descending
                                    },
                                    supportsAscending = true,
                                    supportsDescending = true
                                )
                            },
                            selectedField = folderSortMode.sortField(),
                            selectedDirection = if (folderSortMode.isDescending()) SortDirection.Descending else SortDirection.Ascending,
                            ascendingSummary = stringResource(R.string.common_sort_ascending),
                            descendingSummary = stringResource(R.string.common_sort_descending)
                        ) { field, direction ->
                            val mode = field.toMode(direction == SortDirection.Descending)
                            LibrarySortUiState.folderListSortIndex = mode.ordinal
                            saveScope.launch { mainViewModel.settingsManager.setFolderListSortIndex(mode.ordinal) }
                        }
                    )
                }
            )
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
                placeholder = stringResource(R.string.folder_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
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
                FolderListSortMode.entries.forEach { mode ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    LibrarySortUiState.folderListSortIndex = mode.ordinal
                                    saveScope.launch { mainViewModel.settingsManager.setFolderListSortIndex(mode.ordinal) }
                                    sortExpanded = false
                                }
                            )
                            .padding(vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(mode.labelRes),
                            fontSize = 14.sp,
                            fontWeight = if (folderSortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (folderSortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        if (isScanning) {
            ScanStatusCard(scanProgress = scanProgress)
        }

        LibraryAnalysisEntryCard(onClick = onNavigateToLibraryAnalysis)

        folderMenuTarget?.let { folder ->
            FolderActionSheet(
                title = folder.name,
                isPinned = pinnedFolderPaths.any { it.equals(folder.path, ignoreCase = true) },
                onDismiss = { folderMenuTarget = null },
                onTogglePin = {
                    val isPinned = pinnedFolderPaths.any { it.equals(folder.path, ignoreCase = true) }
                    folderMenuTarget = null
                    scope.launch { mainViewModel.settingsManager.setPinned("folder", folder.path, !isPinned) }
                },
                onShare = {
                    shareLocalSongs(context, songsForFolder(folder))
                    folderMenuTarget = null
                },
                onAssociate = {
                    associateFolderPaths = listOf(folder.path)
                    folderMenuTarget = null
                },
                onAddToPlaylist = {
                    playlistPickerSongs = songsForFolder(folder)
                    folderMenuTarget = null
                },
                onAddToQueue = {
                    playerViewModel.addToPlaylist(songsForFolder(folder))
                    Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                    folderMenuTarget = null
                },
                onPlayNext = {
                    playerViewModel.playNext(songsForFolder(folder))
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
                        if (ok) context.getString(R.string.playlist_shortcut_requested, folder.name) else context.getString(R.string.playlist_shortcut_unsupported),
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
                            (blockedFolders + folderPath).distinct().joinToString("；")
                        )
                        mainViewModel.scanMusic()
                    }
                    folderToBlock = null
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
                playlists = playlists,
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
                    }
                )
            }
        }
        createPlaylistSongs?.let { songsToAdd ->
            CreatePlaylistAndAddSheet(
                onDismiss = { createPlaylistSongs = null },
                onCreate = { name ->
                    mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                        createPlaylistSongs = null
                    }
                }
            )
        }

        if (songs.isEmpty() && !libraryCacheLoaded) {
            EllaCenteredLoadingIndicator()
        } else if (songs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Folder,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (blockedFolders.isNotEmpty()) {
                            stringResource(R.string.folder_empty_blocked_hint)
                        } else {
                            stringResource(R.string.folder_empty)
                        },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            }
        } else {
            val folders = remember(rootChildFolders, rootSongs, rootFolderPath, folderSortMode, searchQuery, pinnedFolderPaths) {
                val entries = buildList {
                    if (rootSongs.isNotEmpty()) {
                        add(
                            FolderTreeEntry(
                                path = rootFolderPath,
                                name = rootFolderPath.substringAfterLast('/').ifBlank { context.getString(R.string.folder_root) },
                                songCount = rootSongs.size,
                                albumCount = rootSongs.map { it.albumIdentityId() }.distinct().size,
                                duration = rootSongs.sumOf { it.duration },
                                dateModified = rootSongs.maxOfOrNull { it.dateModified } ?: 0L
                            )
                        )
                    }
                    addAll(rootChildFolders)
                }
                val query = searchQuery.trim()
                val pinnedRoot = rootFolderPath.takeIf { rootSongs.isNotEmpty() }
                val effectivePinnedPaths = listOfNotNull(pinnedRoot) + pinnedFolderPaths
                entries
                    .sortedForFolderList(folderSortMode, pinnedPaths = effectivePinnedPaths)
                    .let { sorted ->
                        if (query.isBlank()) sorted else sorted.filter { folder ->
                            folder.name.contains(query, ignoreCase = true) ||
                                folder.path.contains(query, ignoreCase = true)
                        }
                    }
            }
            val listState = rememberLazyListState()
            RestoreListScrollAfterSearch(
                searchExpanded = searchExpanded,
                query = searchQuery,
                listState = listState
            )
            LaunchedEffect(scrollToTopRequest) {
                if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
            }
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 160.dp)
                ) {
                    items(
                        items = folders,
                        key = { it.path }
                    ) { folder ->
                        FolderListRow(
                            folder = folder,
                            sortMode = folderSortMode,
                            isPinned = pinnedFolderPaths.any { it.equals(folder.path, ignoreCase = true) },
                            onClick = { onFolderClick(folder.path) },
                            onLongClick = { folderMenuTarget = folder }
                        )
                    }
                }
                if (folders.size > 30) {
                    LazyListScrollIndicator(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
            }
        }
        }
    }
}
