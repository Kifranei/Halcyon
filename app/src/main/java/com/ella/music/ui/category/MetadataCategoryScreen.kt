package com.ella.music.ui.category

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.MetadataCategoryItem
import com.ella.music.viewmodel.PlayerViewModel
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.DirectionalSortField
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LazyGridScrollIndicator
import com.ella.music.ui.components.RestoreGridScrollAfterSearch
import com.ella.music.ui.components.ShuffleAllSummaryButton
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.SideIndexListEndPadding
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.folder.toFolderSettingList
import com.ella.music.ui.listmodel.SortDirection
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
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MetadataCategoryScreen(
    type: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    onCategoryClick: (String) -> Unit
) {
    val context = LocalContext.current
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)
    val songs by mainViewModel.songs.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val items by produceState(emptyList<MetadataCategoryItem>(), type, songs) {
        value = withContext(Dispatchers.Default) { mainViewModel.getMetadataCategoryItems(type) }
    }
    var sortExpanded by remember { mutableStateOf(false) }
    val sortIndexFlow = remember(type) { mainViewModel.settingsManager.metadataCategorySortIndex(type) }
    val sortIndex by sortIndexFlow.collectAsState(initial = LibrarySortUiState.metadataCategorySortIndex(type))
    val availableSortModes = remember(type) { MetadataCategorySortMode.entries.filter { it.availableFor(type) } }
    val sortMode = availableSortModes.getOrElse(sortIndex) { MetadataCategorySortMode.Name }
    val sortedItems = remember(items, type, sortMode) { items.sortedForCategory(type, sortMode) }
    val playlists by mainViewModel.playlists.collectAsState()
    val scanExcludeFolders by mainViewModel.settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    val pinnedCategoryKeys by mainViewModel.settingsManager
        .pinnedKeysFlow("category:$type")
        .collectAsState(initial = emptyList())
    val pinnedOrderedItems = remember(sortedItems, pinnedCategoryKeys) {
        if (pinnedCategoryKeys.isEmpty()) {
            sortedItems
        } else {
            val pinnedRank = pinnedCategoryKeys.withIndex().associate { it.value to it.index }
            val pinnedSet = pinnedRank.keys
            val pinned = sortedItems
                .filter { it.name in pinnedSet }
                .sortedBy { pinnedRank[it.name] ?: Int.MAX_VALUE }
            pinned + sortedItems.filterNot { it.name in pinnedSet }
        }
    }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val selection = rememberLibrarySelectionState<String>()
    var categoryMenuItem by remember { mutableStateOf<MetadataCategoryItem?>(null) }
    var folderToBlock by remember { mutableStateOf<String?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val displayedItems = remember(pinnedOrderedItems, searchQuery, type) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            pinnedOrderedItems
        } else {
            pinnedOrderedItems.filter { it.matchesCategorySearch(query, type) }
        }
    }
    val gridColumns by mainViewModel.settingsManager.categoryGridColumns.collectAsState(initial = 2)
    val configuration = LocalConfiguration.current
    val safeGridColumns = if (type.usesSingleColumnCategory()) {
        1
    } else if (configuration.smallestScreenWidthDp >= 600) {
        gridColumns.coerceIn(5, 8)
    } else {
        gridColumns.coerceIn(1, 4)
    }
    val pageBackground = ellaPageBackground()
    val gridState = rememberSaveable(saver = LazyGridState.Saver) { LazyGridState() }
    RestoreGridScrollAfterSearch(
        searchExpanded = searchExpanded,
        query = searchQuery,
        gridState = gridState
    )
    val scope = rememberCoroutineScope()
    val saveScope = context.findComponentActivity()?.lifecycleScope ?: scope
    val currentSelectionKeys = remember(displayedItems) { displayedItems.map { it.name } }
    val currentSelectionIndexByName = remember(currentSelectionKeys) {
        buildMap {
            currentSelectionKeys.forEachIndexed { index, name -> put(name, index) }
        }
    }
    fun selectedActionSongs(): List<Song> =
        selection.selectedIds
            .asSequence()
            .flatMap { categoryName -> mainViewModel.getSongsForMetadataCategory(type, categoryName).asSequence() }
            .distinctBy { it.playlistIdentityKey() }
            .toList()
    fun toggleSelectAllVisibleItems() {
        if (currentSelectionKeys.isEmpty()) return
        val visible = currentSelectionKeys.toSet()
        if (visible.all { it in selection.selectedIds }) {
            selection.selectedIds = selection.selectedIds - visible
            selection.rangeAnchorId = null
            selection.rangeTargetId = null
        } else {
            selection.selectedIds = selection.selectedIds + visible
        }
        selection.selectionMode = selection.selectedIds.isNotEmpty()
    }
    val selectedVisibleCount = remember(selection.selectedIds, currentSelectionKeys) {
        currentSelectionKeys.count { it in selection.selectedIds }
    }
    val rangeSelectionAvailable = remember(currentSelectionIndexByName, selection.selectedIds, selection.rangeAnchorId, selection.rangeTargetId) {
        selection.isRangeSelectionAvailable(currentSelectionIndexByName)
    }
    val displayedCategoryNames = remember(displayedItems) { displayedItems.map { it.name } }
    val randomCategorySongs by produceState(emptyList<Song>(), type, songs, displayedCategoryNames) {
        value = withContext(Dispatchers.Default) {
            mainViewModel.getSongsForMetadataCategories(type, displayedCategoryNames)
        }
    }
    BackHandler(enabled = selection.selectionMode || sortExpanded || searchExpanded || folderToBlock != null) {
        when {
            folderToBlock != null -> folderToBlock = null
            selection.selectionMode -> selection.finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
        }
    }
    LaunchedEffect(selection.selectionMode, currentSelectionKeys) {
        if (!selection.selectionMode) return@LaunchedEffect
        val visibleKeys = currentSelectionKeys.toSet()
        selection.selectedIds = selection.selectedIds.filterTo(linkedSetOf()) { it in visibleKeys }
    }
    LaunchedEffect(type, sortIndex) {
        LibrarySortUiState.updateMetadataCategorySortIndex(type, sortIndex)
    }

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
                title = if (selection.selectionMode) {
                    context.getString(R.string.library_selected_fraction, selection.selectedIds.size, currentSelectionKeys.size)
                } else {
                    type.categoryTitle()
                },
                color = pageBackground,
                navigationIcon = {
                    if (showBackButton) {
                        IconButton(onClick = { if (selection.selectionMode) selection.finishSelectionMode() else onBack() }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Back,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                titleStartPadding = if (showBackButton || selection.selectionMode) 64.dp else 20.dp,
                titleEndPadding = if (selection.selectionMode) 280.dp else 128.dp,
                onDoubleTapTitle = { scope.launch { gridState.animateScrollToItem(0) } },
                actions = {
                    if (selection.selectionMode) {
                        IconButton(onClick = {
                            val keys = selection.selectedIdsInSelectionOrder()
                            if (keys.isNotEmpty()) {
                                scope.launch {
                                    mainViewModel.settingsManager.pinKeysInOrder("category:$type", keys)
                                }
                                selection.finishSelectionMode()
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Pin,
                                contentDescription = stringResource(R.string.common_pin_to_top),
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
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
                        if (type != "folder") {
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
                        }
                    } else {
                        IconButton(onClick = {
                            selection.selectionMode = true
                            selection.selectedIds = emptySet()
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
                            items = directionalSortDropdownItems(
                                fields = MetadataCategorySortField.entries
                                    .filter { field ->
                                        field != MetadataCategorySortField.DateModified || type == "folder"
                                    }
                                    .map { field ->
                                        DirectionalSortField(
                                            field = field,
                                            text = field.displayLabel(type),
                                            defaultDirection = when (field) {
                                                MetadataCategorySortField.Name -> SortDirection.Ascending
                                                MetadataCategorySortField.DateModified,
                                                MetadataCategorySortField.SongCount,
                                                MetadataCategorySortField.AlbumCount,
                                                MetadataCategorySortField.Duration -> SortDirection.Descending
                                            },
                                            supportsAscending = true,
                                            supportsDescending = true
                                        )
                                    },
                                selectedField = sortMode.sortField(),
                                selectedDirection = if (sortMode.isDescending()) SortDirection.Descending else SortDirection.Ascending,
                                ascendingSummary = stringResource(R.string.common_sort_ascending),
                                descendingSummary = stringResource(R.string.common_sort_descending)
                            ) { field, direction ->
                                val mode = field.toMode(direction == SortDirection.Descending)
                                val nextSortIndex = availableSortModes.indexOf(mode)
                                LibrarySortUiState.updateMetadataCategorySortIndex(type, nextSortIndex)
                                saveScope.launch { mainViewModel.settingsManager.setMetadataCategorySortIndex(type, nextSortIndex) }
                            }
                        )
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
                placeholder = stringResource(R.string.category_search_placeholder, type.categoryTitle()),
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
                availableSortModes.forEach { mode ->
                    Text(
                        text = mode.displayLabel(type),
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                sortExpanded = false
                                val nextSortIndex = availableSortModes.indexOf(mode)
                                LibrarySortUiState.updateMetadataCategorySortIndex(type, nextSortIndex)
                                saveScope.launch { mainViewModel.settingsManager.setMetadataCategorySortIndex(type, nextSortIndex) }
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }

        if (songs.isEmpty() && !libraryCacheLoaded) {
            EllaCenteredLoadingIndicator()
        } else if (displayedItems.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (searchQuery.isBlank()) stringResource(R.string.category_empty_hint, type.categoryTitle()) else stringResource(R.string.category_no_match, type.categoryTitle()),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    fontSize = 14.sp
                )
            }
        } else {
            // A-Z index bar for the single-column person categories when
            // sorted by name, mirroring the artists list.
            val showCategoryIndexBar = (type in PERSON_METADATA_CATEGORY_TYPES || type == "folder") &&
                sortMode == MetadataCategorySortMode.Name &&
                displayedItems.size > 30
            val categoryIndexLetters = remember(displayedItems, showCategoryIndexBar) {
                if (showCategoryIndexBar) displayedItems.map { it.categoryIndexLetter(type) } else emptyList()
            }
            val categoryIndexTargets = remember(categoryIndexLetters) {
                buildMap {
                    categoryIndexLetters.forEachIndexed { index, letter -> putIfAbsent(letter, index) }
                }
            }
            val showCategorySideIndex = displayedItems.size > 30
            Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(safeGridColumns),
                    state = gridState,
                    contentPadding = PaddingValues(
                        end = if (showCategorySideIndex) SideIndexListEndPadding else 0.dp,
                        bottom = 120.dp
                    )
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        com.ella.music.ui.components.SortSummaryHeader(
                            text = "${type.categoryCountSummary(displayedItems.size)} · ${sortMode.displayLabel(type)}",
                            leadingContent = {
                                ShuffleAllSummaryButton(
                                    visible = !selection.selectionMode && randomCategorySongs.isNotEmpty(),
                                    onClick = { playerViewModel.setPlaylist(randomCategorySongs.shuffled(), 0) }
                                )
                            }
                        )
                    }
                    items(displayedItems, key = { it.name }) { item ->
                        val coverSong = item.representativeSong
                        val albumArtUri = remember(coverSong?.albumId, item.coverAlbumIds) {
                            coverSong?.albumId?.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri)
                                ?: item.coverAlbumIds.firstOrNull()?.let(mainViewModel::getAlbumArtUri)
                        }
                        MetadataCategoryCard(
                            type = type,
                            item = item,
                            sortMode = sortMode,
                            albumArtUri = albumArtUri,
                            representativeSong = coverSong,
                            loadCoverArt = if (type.prefersEmbeddedCategoryCardCover()) mainViewModel::getAlbumCoverArtBitmap else null,
                            selectionMode = selection.selectionMode,
                            selected = item.name in selection.selectedIds,
                            isPinned = item.name in pinnedCategoryKeys,
                            onClick = {
                                if (selection.selectionMode) selection.toggleSelection(item.name) else onCategoryClick(item.name)
                            },
                            onLongClick = {
                                if (selection.selectionMode) {
                                    selection.toggleSelection(item.name)
                                } else {
                                    categoryMenuItem = item
                                }
                            }
                        )
                    }
                }
                if (showCategoryIndexBar) {
                    FastIndexBar(
                        letters = categoryIndexLetters,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 0.dp),
                        onLetterClick = { letter ->
                            val index = categoryIndexTargets[letter]
                            if (index != null) {
                                // +1 to skip the count-summary header item.
                                scope.launch { gridState.scrollToItem(index + 1) }
                            }
                        }
                    )
                } else if (showCategorySideIndex) {
                    LazyGridScrollIndicator(
                        state = gridState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
                FloatingSelectionControls(
                    visible = selection.selectionMode && currentSelectionKeys.isNotEmpty(),
                    rangeEnabled = rangeSelectionAvailable,
                    allSelected = currentSelectionKeys.isNotEmpty() && selectedVisibleCount == currentSelectionKeys.size,
                    onRangeSelect = { selection.applyRangeSelection(currentSelectionKeys, currentSelectionIndexByName) },
                    onSelectAll = ::toggleSelectAllVisibleItems,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
                )
            }
        }
    }

    MetadataCategoryScreenSurfaces(
        context = context,
        type = type,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        playlists = playlists,
        blockedFolders = blockedFolders,
        categoryMenuItem = categoryMenuItem,
        onCategoryMenuItemChange = { categoryMenuItem = it },
        pinnedCategoryKeys = pinnedCategoryKeys,
        folderToBlock = folderToBlock,
        onFolderToBlockChange = { folderToBlock = it },
        playlistPickerSongs = playlistPickerSongs,
        onPlaylistPickerSongsChange = { playlistPickerSongs = it },
        createPlaylistSongs = createPlaylistSongs,
        onCreatePlaylistSongsChange = { createPlaylistSongs = it },
        pendingDeleteSongs = pendingDeleteSongs,
        onPendingDeleteSongsChange = { pendingDeleteSongs = it },
        onRequestDeleteSongs = requestDeleteSongs,
        loadDetailSongs = { categoryType, categoryName ->
            mainViewModel.detailSortedSongsForMetadataCategory(categoryType, categoryName)
        }
    )
}
}

/**
 * Gathers the songs for a metadata category and orders them using that category type's
 * persisted DETAIL-page song-sort setting (matching MetadataCategoryDetailScreen).
 */
private suspend fun MainViewModel.detailSortedSongsForMetadataCategory(
    type: String,
    name: String
): List<Song> {
    val index = settingsManager.metadataCategoryDetailSongSortIndex(type).first()
    val mode = MetadataDetailSongSortMode.entries.getOrElse(index) { MetadataDetailSongSortMode.AlbumTrack }
        .let { resolved ->
            if (type == "folder" && resolved == MetadataDetailSongSortMode.AlbumTrack) {
                MetadataDetailSongSortMode.Title
            } else {
                resolved
            }
        }
    return getSongsForMetadataCategory(type, name).sortedForMetadataDetail(mode)
}
