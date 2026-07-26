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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaMiuixBottomSheet
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
import com.ella.music.ui.components.SelectionCheck
import com.ella.music.ui.components.ShuffleAllFloatingButton
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel

import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.basic.Search
import androidx.compose.ui.graphics.Color
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
