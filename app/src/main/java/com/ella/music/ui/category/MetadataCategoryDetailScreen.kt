package com.ella.music.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ella.music.R
import com.ella.music.data.LibraryAlbumAggregator
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.ShuffleAllSummaryButton
import com.ella.music.ui.components.SideIndexListEndPadding
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.rememberSongDeleteResultHandler
import com.ella.music.ui.folder.folderDisplayName
import com.ella.music.ui.settings.findComponentActivity
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun MetadataCategoryDetailScreen(
    type: String,
    name: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit = {},
    onArtistClick: (String) -> Unit = {},
    onMetadataCategoryClick: (String, String) -> Unit = { _, _ -> },
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val librarySongs by mainViewModel.songs.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val libraryAlbums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val songs by produceState(emptyList<Song>(), type, name, librarySongs) {
        value = withContext(Dispatchers.Default) {
            mainViewModel.getSongsForMetadataCategory(type, name)
        }
    }
    var sortExpanded by remember { mutableStateOf(false) }
    val detailSongSortIndexFlow = remember(type) { mainViewModel.settingsManager.metadataCategoryDetailSongSortIndex(type) }
    val detailAlbumSortIndexFlow = remember(type) { mainViewModel.settingsManager.metadataCategoryDetailAlbumSortIndex(type) }
    val sortIndex by detailSongSortIndexFlow.collectAsState(initial = LibrarySortUiState.metadataCategoryDetailSongSortIndex(type))
    val albumSortIndex by detailAlbumSortIndexFlow.collectAsState(initial = LibrarySortUiState.metadataCategoryDetailAlbumSortIndex(type))
    val sortMode = MetadataDetailSongSortMode.entries.getOrElse(sortIndex) { MetadataDetailSongSortMode.AlbumTrack }
        .let { mode ->
            if (type == "folder" && mode == MetadataDetailSongSortMode.AlbumTrack) MetadataDetailSongSortMode.Title else mode
        }
    val albumSortMode = MetadataDetailAlbumSortMode.entries.getOrElse(albumSortIndex) { MetadataDetailAlbumSortMode.YearAsc }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by rememberSaveable(type, name) { mutableStateOf(MetadataDetailTab.Songs) }
    var actionSong by remember { mutableStateOf<com.ella.music.data.model.Song?>(null) }
    val selection = rememberLibrarySelectionState<Long>()
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val detailQuery = searchQuery.trim()
    val filteredSongs by produceState(emptyList<Song>(), songs, detailQuery) {
        value = withContext(Dispatchers.Default) {
            if (detailQuery.isBlank()) {
                songs
            } else {
                songs.filter { song ->
                    song.title.contains(detailQuery, ignoreCase = true) ||
                        song.artist.contains(detailQuery, ignoreCase = true) ||
                        song.album.contains(detailQuery, ignoreCase = true) ||
                        song.fileName.contains(detailQuery, ignoreCase = true)
                }
            }
        }
    }
    val sortedSongs by produceState(emptyList<Song>(), filteredSongs, sortMode) {
        value = withContext(Dispatchers.Default) {
            filteredSongs.sortedForMetadataDetail(sortMode)
        }
    }
    val showAlbumTab = type == "genre" || type == "year" || type in PERSON_METADATA_CATEGORY_TYPES
    val shouldBuildAlbumTabContent = showAlbumTab && selectedTab == MetadataDetailTab.Albums
    val detailAlbums = remember(songs, libraryAlbums, shouldBuildAlbumTabContent) {
        if (shouldBuildAlbumTabContent) {
            LibraryAlbumAggregator.toAlbumsForSongs(
                songs = songs,
                libraryAlbums = libraryAlbums,
                unknownAlbumName = context.getString(R.string.player_unknown_album)
            )
        } else {
            emptyList()
        }
    }
    val albumDurations = remember(songs, shouldBuildAlbumTabContent) {
        if (shouldBuildAlbumTabContent) LibraryAlbumAggregator.durationsByAlbumIdentity(songs) else emptyMap()
    }
    val filteredAlbums = remember(detailAlbums, detailQuery, shouldBuildAlbumTabContent) {
        if (!shouldBuildAlbumTabContent || detailQuery.isBlank()) {
            detailAlbums
        } else {
            detailAlbums.filter { album ->
                album.name.contains(detailQuery, ignoreCase = true) ||
                    album.artist.contains(detailQuery, ignoreCase = true) ||
                    album.albumArtist.contains(detailQuery, ignoreCase = true) ||
                    album.year.contains(detailQuery, ignoreCase = true)
            }
        }
    }
    val sortedAlbums = remember(filteredAlbums, albumSortMode, albumDurations, shouldBuildAlbumTabContent) {
        if (shouldBuildAlbumTabContent) {
            filteredAlbums.sortedForMetadataAlbumDetail(albumSortMode, albumDurations)
        } else {
            emptyList()
        }
    }
    val albumArtistContextName = remember(type, name) {
        name.takeIf { type in PERSON_METADATA_CATEGORY_TYPES }
    }
    val hasSameNameArtist = remember(type, name, librarySongs, libraryAlbums) {
        type in PERSON_METADATA_CATEGORY_TYPES && (
            mainViewModel.getSongsForArtist(name).isNotEmpty() ||
                mainViewModel.getReleaseAlbumsForArtist(name).isNotEmpty()
        )
    }
    val hasSameNameComposer = remember(type, name, librarySongs) {
        type in PERSON_METADATA_CATEGORY_TYPES &&
            type != "composer" &&
            mainViewModel.getSongsForMetadataCategory("composer", name).isNotEmpty()
    }
    val hasSameNameArranger = remember(type, name, librarySongs) {
        type in PERSON_METADATA_CATEGORY_TYPES &&
            type != "arranger" &&
            mainViewModel.getSongsForMetadataCategory("arranger", name).isNotEmpty()
    }
    val hasSameNameLyricist = remember(type, name, librarySongs) {
        type in PERSON_METADATA_CATEGORY_TYPES &&
            type != "lyricist" &&
            mainViewModel.getSongsForMetadataCategory("lyricist", name).isNotEmpty()
    }
    val pageBackground = ellaPageBackground()
    val folderRootName = stringResource(R.string.folder_root)
    val defaultCategoryTitle = type.categoryTitle()
    val pageTitle = remember(type, name, folderRootName, defaultCategoryTitle) {
        if (type == "folder") name.folderDisplayName(folderRootName) else name.ifBlank { defaultCategoryTitle }
    }
    val listState = rememberLazyListState()
    RestoreListScrollAfterSearch(
        searchExpanded = searchExpanded,
        query = searchQuery,
        listState = listState
    )
    val scope = rememberCoroutineScope()
    val saveScope = context.findComponentActivity()?.lifecycleScope ?: scope
    var fastScrollJob by remember { mutableStateOf<Job?>(null) }
    val deleteSelectedSongs = rememberSongDeleteResultHandler(mainViewModel) { selection.finishSelectionMode() }
    val sortedSongIndexById = remember(sortedSongs) {
        buildMap {
            sortedSongs.forEachIndexed { index, song -> put(song.id, index) }
        }
    }
    val detailSongsByAlbumId = remember(songs, shouldBuildAlbumTabContent) {
        if (shouldBuildAlbumTabContent) songs.groupBy { it.albumIdentityId() } else emptyMap()
    }
    val randomDetailSongs = remember(selectedTab, sortedSongs, sortedAlbums, detailSongsByAlbumId) {
        when (selectedTab) {
            MetadataDetailTab.Songs -> sortedSongs
            MetadataDetailTab.Albums -> sortedAlbums
                .flatMap { album -> detailSongsByAlbumId[album.id].orEmpty() }
                .distinctBy { it.id }
        }
    }
    val currentSelectionIds = remember(selectedTab, sortedSongs, sortedAlbums) {
        when (selectedTab) {
            MetadataDetailTab.Songs -> sortedSongs.map { it.id }
            MetadataDetailTab.Albums -> sortedAlbums.map { it.id }
        }
    }
    val currentSelectionIndexById = remember(currentSelectionIds) {
        buildMap {
            currentSelectionIds.forEachIndexed { index, id -> put(id, index) }
        }
    }
    fun selectedActionSongs(): List<Song> {
        return when (selectedTab) {
            MetadataDetailTab.Songs -> sortedSongs.filter { it.id in selection.selectedIds }
            MetadataDetailTab.Albums -> sortedAlbums
                .filter { it.id in selection.selectedIds }
                .flatMap { detailSongsByAlbumId[it.id].orEmpty() }
                .distinctBy { it.playlistIdentityKey() }
        }
    }
    val selectedSongsForDrag = remember(selectedTab, sortedSongs, selection.selectedIds) {
        if (selectedTab == MetadataDetailTab.Songs) {
            sortedSongs.filter { it.id in selection.selectedIds }
        } else {
            emptyList()
        }
    }
    val selectedVisibleCount = remember(selection.selectedIds, currentSelectionIds) {
        currentSelectionIds.count { it in selection.selectedIds }
    }
    val rangeSelectionAvailable = remember(currentSelectionIndexById, selection.selectedIds, selection.rangeAnchorId, selection.rangeTargetId) {
        selection.isRangeSelectionAvailable(currentSelectionIndexById)
    }
    val currentSongItemIndex = remember(sortedSongIndexById, currentSong?.id, selectedTab, selection.selectionMode) {
        if (selectedTab != MetadataDetailTab.Songs || selection.selectionMode) return@remember -1
        (currentSong?.id?.let { sortedSongIndexById[it] } ?: -1)
            .takeIf { it >= 0 }
            ?.plus(1)
            ?: -1
    }
    val fastIndexLetters = remember(sortedSongs, sortMode) {
        sortedSongs.map { it.metadataDetailIndexLetter(sortMode) }
    }
    val fastIndexTargets = remember(fastIndexLetters) {
        buildMap {
            fastIndexLetters.forEachIndexed { index, letter -> putIfAbsent(letter, index + 1) }
        }
    }
    BackHandler(enabled = selection.selectionMode || sortExpanded || searchExpanded) {
        when {
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
    LaunchedEffect(selectedTab) {
        if (selection.selectionMode) {
            selection.finishSelectionMode()
        }
    }
    LaunchedEffect(selection.selectionMode, currentSelectionIds) {
        if (!selection.selectionMode) return@LaunchedEffect
        val visibleIds = currentSelectionIds.toMutableSet()
        selection.selectedIds = selection.selectedIds.filterTo(mutableSetOf()) { it in visibleIds }
        if (selection.rangeAnchorId !in visibleIds) selection.rangeAnchorId = selection.selectedIds.firstOrNull()
        if (selection.rangeTargetId !in visibleIds) selection.rangeTargetId = null
    }
    LaunchedEffect(type, sortIndex) {
        LibrarySortUiState.updateMetadataCategoryDetailSongSortIndex(type, sortIndex)
    }
    LaunchedEffect(type, albumSortIndex) {
        LibrarySortUiState.updateMetadataCategoryDetailAlbumSortIndex(type, albumSortIndex)
    }
    LaunchedEffect(type, sortIndex) {
        if (type == "folder" && sortIndex == MetadataDetailSongSortMode.AlbumTrack.ordinal) {
            LibrarySortUiState.updateMetadataCategoryDetailSongSortIndex(
                type,
                MetadataDetailSongSortMode.Title.ordinal
            )
            saveScope.launch {
                mainViewModel.settingsManager.setMetadataCategoryDetailSongSortIndex(
                    type,
                    MetadataDetailSongSortMode.Title.ordinal
                )
            }
        }
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(pageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            EllaSmallTopAppBar(
                title = pageTitle,
                color = pageBackground,
                onDoubleTapTitle = { scope.launch { listState.animateScrollToItem(0) } },
                navigationIcon = {
                    IconButton(onClick = { if (selection.selectionMode) selection.finishSelectionMode() else onBack() }) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.common_back),
                            tint = MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                actions = {
                    if (selection.selectionMode) {
                        IconButton(onClick = {
                            val selectedSongs = selectedActionSongs()
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                playerViewModel.playNext(selectedSongs)
                                Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                                selection.finishSelectionMode()
                            }
                        }) {
                            com.ella.music.ui.components.PlayNextActionIcon(
                                contentDescription = stringResource(R.string.song_more_play_next),
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            val selectedSongs = selectedActionSongs()
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                playlistPickerSongs = selectedSongs
                            }
                        }) {
                            com.ella.music.ui.components.AddToPlaylistActionIcon(
                                contentDescription = stringResource(R.string.song_more_add_to_playlist),
                                tint = MiuixTheme.colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            val selectedSongs = selectedActionSongs()
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                pendingDeleteSongs = selectedSongs
                            }
                        }) {
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
                        val sortItems = if (selectedTab == MetadataDetailTab.Albums) {
                            directionalSortModeDropdownItems(
                                fields = listOf(
                                    DirectionalSortModeField(
                                        text = stringResource(R.string.playlist_song_sort_year),
                                        ascendingMode = MetadataDetailAlbumSortMode.YearAsc,
                                        descendingMode = MetadataDetailAlbumSortMode.YearDesc
                                    ),
                                    DirectionalSortModeField(
                                        text = stringResource(R.string.playlist_sort_song_count),
                                        ascendingMode = MetadataDetailAlbumSortMode.SongCountAsc,
                                        descendingMode = MetadataDetailAlbumSortMode.SongCount
                                    ),
                                    DirectionalSortModeField(
                                        text = stringResource(R.string.playlist_sort_duration),
                                        ascendingMode = MetadataDetailAlbumSortMode.DurationAsc,
                                        descendingMode = MetadataDetailAlbumSortMode.Duration
                                    ),
                                    DirectionalSortModeField(
                                        text = stringResource(R.string.category_sort_album_name),
                                        ascendingMode = MetadataDetailAlbumSortMode.Name,
                                        descendingMode = MetadataDetailAlbumSortMode.NameDesc
                                    )
                                ),
                                selectedMode = albumSortMode,
                                onSelect = { mode ->
                                    LibrarySortUiState.updateMetadataCategoryDetailAlbumSortIndex(type, mode.ordinal)
                                    saveScope.launch { mainViewModel.settingsManager.setMetadataCategoryDetailAlbumSortIndex(type, mode.ordinal) }
                                }
                            )
                        } else {
                            directionalSortModeDropdownItems(
                                fields = buildList {
                                    if (type != "folder") {
                                        add(
                                            DirectionalSortModeField(
                                                text = stringResource(R.string.category_sort_album_track),
                                                ascendingMode = MetadataDetailSongSortMode.AlbumTrack,
                                                descendingMode = MetadataDetailSongSortMode.AlbumTrackDesc
                                            )
                                        )
                                    }
                                    addAll(
                                        listOf(
                                            DirectionalSortModeField(
                                                text = stringResource(R.string.playlist_song_sort_title),
                                                ascendingMode = MetadataDetailSongSortMode.Title,
                                                descendingMode = MetadataDetailSongSortMode.TitleDesc
                                            ),
                                            DirectionalSortModeField(
                                                text = stringResource(R.string.playlist_song_sort_file_name),
                                                ascendingMode = MetadataDetailSongSortMode.FileName,
                                                descendingMode = MetadataDetailSongSortMode.FileNameDesc
                                            ),
                                            DirectionalSortModeField(
                                                text = stringResource(R.string.playlist_sort_duration),
                                                ascendingMode = MetadataDetailSongSortMode.DurationAsc,
                                                descendingMode = MetadataDetailSongSortMode.Duration
                                            ),
                                            DirectionalSortModeField(
                                                text = stringResource(R.string.playlist_song_sort_year),
                                                ascendingMode = MetadataDetailSongSortMode.YearAsc,
                                                descendingMode = MetadataDetailSongSortMode.YearDesc
                                            ),
                                            DirectionalSortModeField(
                                                text = stringResource(R.string.playlist_song_sort_date_added),
                                                ascendingMode = MetadataDetailSongSortMode.DateAddedAsc,
                                                descendingMode = MetadataDetailSongSortMode.DateAdded
                                            ),
                                            DirectionalSortModeField(
                                                text = stringResource(R.string.playlist_song_sort_date_modified),
                                                ascendingMode = MetadataDetailSongSortMode.DateModifiedAsc,
                                                descendingMode = MetadataDetailSongSortMode.DateModified
                                            )
                                        )
                                    )
                                },
                                selectedMode = sortMode,
                                onSelect = { mode ->
                                    LibrarySortUiState.updateMetadataCategoryDetailSongSortIndex(type, mode.ordinal)
                                    saveScope.launch { mainViewModel.settingsManager.setMetadataCategoryDetailSongSortIndex(type, mode.ordinal) }
                                }
                            )
                        }
                        SortDropdownMenu(items = sortItems)
                    }
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
                placeholder = stringResource(R.string.library_search_placeholder),
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
                if (selectedTab == MetadataDetailTab.Albums) {
                    MetadataDetailAlbumSortMode.entries.forEach { mode ->
                        Text(
                            text = mode.label(),
                            fontSize = 14.sp,
                            fontWeight = if (albumSortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (albumSortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    sortExpanded = false
                                    LibrarySortUiState.updateMetadataCategoryDetailAlbumSortIndex(type, mode.ordinal)
                                    saveScope.launch { mainViewModel.settingsManager.setMetadataCategoryDetailAlbumSortIndex(type, mode.ordinal) }
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                } else {
                    MetadataDetailSongSortMode.entries
                        .filterNot { type == "folder" && it == MetadataDetailSongSortMode.AlbumTrack }
                        .forEach { mode ->
                    Text(
                        text = mode.label(),
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortExpanded = false
                                LibrarySortUiState.updateMetadataCategoryDetailSongSortIndex(type, mode.ordinal)
                                saveScope.launch { mainViewModel.settingsManager.setMetadataCategoryDetailSongSortIndex(type, mode.ordinal) }
                            }
                            .padding(vertical = 10.dp)
                    )
                    }
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            val showSongSideIndex = selectedTab == MetadataDetailTab.Songs && sortedSongs.size > 30
            if (librarySongs.isEmpty() && !libraryCacheLoaded) {
                EllaCenteredLoadingIndicator()
            } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    end = if (showSongSideIndex) SideIndexListEndPadding else 0.dp,
                    bottom = 120.dp
                )
            ) {
                item {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        val summaryText = if (selection.selectionMode) {
                            stringResource(R.string.library_selected_fraction, selection.selectedIds.size, currentSelectionIds.size)
                        } else if (selectedTab == MetadataDetailTab.Albums) {
                            stringResource(
                                R.string.category_album_summary,
                                sortedAlbums.size,
                                type.categoryTitle(),
                                albumSortMode.displayLabel()
                            )
                        } else {
                            stringResource(
                                R.string.category_song_summary,
                                sortedSongs.size,
                                type.categoryTitle(),
                                sortMode.displayLabel()
                            )
                        }
                        if (type in PERSON_METADATA_CATEGORY_TYPES) {
                            Row(
                                modifier = Modifier.padding(bottom = 10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (hasSameNameArtist) {
                                    MetadataDetailLinkChip(
                                        text = stringResource(R.string.category_artist_page),
                                        onClick = { onArtistClick(name) }
                                    )
                                }
                                if (hasSameNameComposer) {
                                    MetadataDetailLinkChip(
                                        text = stringResource(R.string.category_composer_page),
                                        onClick = { onMetadataCategoryClick("composer", name) }
                                    )
                                }
                                if (hasSameNameArranger) {
                                    MetadataDetailLinkChip(
                                        text = stringResource(R.string.category_arranger_page),
                                        onClick = { onMetadataCategoryClick("arranger", name) }
                                    )
                                }
                                if (hasSameNameLyricist) {
                                    MetadataDetailLinkChip(
                                        text = stringResource(R.string.category_lyricist_page),
                                        onClick = { onMetadataCategoryClick("lyricist", name) }
                                    )
                                }
                            }
                        }
                        if (showAlbumTab) {
                            Row(
                                modifier = Modifier.padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                MetadataDetailTab.entries.forEach { tab ->
                                    Text(
                                        text = tab.label(),
                                        fontSize = 13.sp,
                                        fontWeight = if (selectedTab == tab) FontWeight.Bold else FontWeight.Normal,
                                        color = if (selectedTab == tab) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(999.dp))
                                            .background(
                                                if (selectedTab == tab) MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                                else Color.Transparent
                                            )
                                            .clickable { selectedTab = tab }
                                            .padding(horizontal = 12.dp, vertical = 7.dp)
                                    )
                                }
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            ShuffleAllSummaryButton(
                                visible = !selection.selectionMode && randomDetailSongs.isNotEmpty(),
                                onClick = {
                                    playerViewModel.setPlaylist(randomDetailSongs.shuffled(), 0)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            )
                            Text(
                                text = summaryText,
                                fontSize = 13.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
                if (selectedTab == MetadataDetailTab.Songs) {
                    item {
                        com.ella.music.ui.components.ContinuePlaybackRow(
                            songs = sortedSongs,
                            playbackStats = playbackStats,
                            currentSong = currentSong,
                            onContinue = { index ->
                                playerViewModel.setPlaylist(sortedSongs, index)
                                if (openPlayerOnPlay) onNavigateToPlayer()
                            }
                        )
                    }
                }
                if (selectedTab == MetadataDetailTab.Albums) {
                    items(sortedAlbums, key = { it.id }) { album ->
                        val albumArtUri = remember(shouldBuildAlbumTabContent, album.artAlbumId) {
                            album.artAlbumId
                                .takeIf { shouldBuildAlbumTabContent && it > 0L }
                                ?.let(mainViewModel::getAlbumArtUri)
                        }
                        MetadataAlbumRow(
                            album = album,
                            duration = albumDurations[album.id] ?: 0L,
                            albumArtUri = albumArtUri,
                            contextPersonName = albumArtistContextName,
                            selectionMode = selection.selectionMode,
                            selected = album.id in selection.selectedIds,
                            onClick = {
                                if (selection.selectionMode) {
                                    selection.toggleSelection(album.id)
                                } else {
                                    onAlbumClick(album.id)
                                }
                            },
                            onLongClick = {
                                selection.selectionMode = true
                                selection.selectedIds = selection.selectedIds + album.id
                                selection.updateRangeAnchorsForManualSelection(album.id, selectedNow = true)
                            }
                        )
                    }
                } else {
                    itemsIndexed(sortedSongs, key = { _, song -> song.id }) { index, song ->
                        val selected = song.id in selection.selectedIds
                        val albumArtUri = remember(song.albumId) {
                            song.albumId
                                .takeIf { it > 0L }
                                ?.let(mainViewModel::getAlbumArtUri)
                        }
                        SongItem(
                            song = song,
                            titleOverride = if (
                                sortMode == MetadataDetailSongSortMode.FileName ||
                                    sortMode == MetadataDetailSongSortMode.FileNameDesc
                            ) {
                                song.fileName.ifBlank { song.path.substringAfterLast('/') }
                            } else {
                                null
                            },
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
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
            }

            if (showSongSideIndex) {
                if (
                    sortMode == MetadataDetailSongSortMode.Title ||
                        sortMode == MetadataDetailSongSortMode.TitleDesc ||
                        sortMode == MetadataDetailSongSortMode.FileName ||
                        sortMode == MetadataDetailSongSortMode.FileNameDesc
                ) {
                    FastIndexBar(
                        letters = fastIndexLetters,
                        reverse = sortMode == MetadataDetailSongSortMode.TitleDesc ||
                            sortMode == MetadataDetailSongSortMode.FileNameDesc,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 2.dp),
                        onLetterClick = { letter ->
                            val index = fastIndexTargets[letter]
                            if (index != null) {
                                fastScrollJob?.cancel()
                                fastScrollJob = scope.launch { listState.scrollToItem(index) }
                            }
                        }
                    )
                } else {
                    LazyListScrollIndicator(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
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
                visible = selection.selectionMode && currentSelectionIds.isNotEmpty(),
                rangeEnabled = rangeSelectionAvailable,
                allSelected = currentSelectionIds.isNotEmpty() && selectedVisibleCount == currentSelectionIds.size,
                onRangeSelect = { selection.applyRangeSelection(currentSelectionIds, currentSelectionIndexById) },
                onSelectAll = { selection.toggleSelectAll(currentSelectionIds) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
            )

            MetadataCategoryDetailScreenSurfaces(
                context = context,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                actionSong = actionSong,
                onActionSongChange = { actionSong = it },
                onNavigateToAlbum = onAlbumClick,
                onNavigateToArtist = onArtistClick,
                playlists = playlists,
                playlistPickerSongs = playlistPickerSongs,
                onPlaylistPickerSongsChange = { playlistPickerSongs = it },
                createPlaylistSongs = createPlaylistSongs,
                onCreatePlaylistSongsChange = { createPlaylistSongs = it },
                pendingDeleteSongs = pendingDeleteSongs,
                onPendingDeleteSongsChange = { pendingDeleteSongs = it },
                onDeleteSelectedSongs = deleteSelectedSongs,
                onClearSelection = selection::finishSelectionMode
            )
        }
    }
}
