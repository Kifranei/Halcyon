package com.ella.music.ui.category

import android.app.Activity
import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.ella.music.R
import com.ella.music.data.LibraryAlbumAggregator
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.model.Album
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.MetadataCategoryItem
import com.ella.music.viewmodel.PlayerViewModel
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LibrarySecondaryFloatingControlsBottomPadding
import com.ella.music.ui.components.LazyGridScrollIndicator
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.ShuffleAllFloatingButton
import com.ella.music.ui.components.SideIndexListEndPadding
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.folder.folderDisplayName
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.settings.findComponentActivity
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Music
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

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
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val songs = remember(type, name, librarySongs) { mainViewModel.getSongsForMetadataCategory(type, name) }
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
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }
    var rangeAnchorId by remember { mutableStateOf<Long?>(null) }
    var rangeTargetId by remember { mutableStateOf<Long?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var pendingSystemDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val detailQuery = searchQuery.trim()
    val filteredSongs = remember(songs, detailQuery) {
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
    val sortedSongs = remember(filteredSongs, sortMode) { filteredSongs.sortedForMetadataDetail(sortMode) }
    fun clearSelection() {
        selectedIds = emptySet()
        rangeAnchorId = null
        rangeTargetId = null
        selectionMode = false
    }
    fun updateRangeAnchorsForManualSelection(songId: Long, selectedNow: Boolean) {
        if (selectedNow) {
            when {
                rangeAnchorId == null -> rangeAnchorId = songId
                rangeAnchorId == songId -> Unit
                else -> rangeTargetId = songId
            }
        } else {
            if (rangeTargetId == songId) rangeTargetId = null
            if (rangeAnchorId == songId) {
                rangeAnchorId = rangeTargetId ?: selectedIds.firstOrNull { it != songId }
                rangeTargetId = null
            }
        }
    }
    fun toggleSongSelection(songId: Long) {
        val selecting = songId !in selectedIds
        selectedIds = if (selecting) selectedIds + songId else selectedIds - songId
        updateRangeAnchorsForManualSelection(songId, selecting)
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
        type != "composer" && mainViewModel.getSongsForMetadataCategory("composer", name).isNotEmpty()
    }
    val hasSameNameArranger = remember(type, name, librarySongs) {
        type != "arranger" && mainViewModel.getSongsForMetadataCategory("arranger", name).isNotEmpty()
    }
    val hasSameNameLyricist = remember(type, name, librarySongs) {
        type != "lyricist" && mainViewModel.getSongsForMetadataCategory("lyricist", name).isNotEmpty()
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
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val songsToDelete = pendingSystemDeleteSongs
        pendingSystemDeleteSongs = emptyList()
        if (result.resultCode == Activity.RESULT_OK && songsToDelete.isNotEmpty()) {
            mainViewModel.removeSongsFromLibrary(songsToDelete)
            Toast.makeText(context, context.getString(R.string.library_deleted_songs, songsToDelete.size), Toast.LENGTH_SHORT).show()
            clearSelection()
        } else if (songsToDelete.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.library_delete_cancelled), Toast.LENGTH_SHORT).show()
        }
    }
    fun deleteSelectedSongs(songsToDelete: List<Song>) {
        if (songsToDelete.isEmpty()) return
        scope.launch {
            val result = mainViewModel.deleteSongsResult(songsToDelete)
            if (result.isSuccess) {
                Toast.makeText(context, context.getString(R.string.library_deleted_songs, songsToDelete.size), Toast.LENGTH_SHORT).show()
                clearSelection()
                return@launch
            }
            val error = result.exceptionOrNull()
            if (error is WritePermissionRequiredException) {
                pendingSystemDeleteSongs = songsToDelete
                deleteRequestLauncher.launch(IntentSenderRequest.Builder(error.intentSender).build())
            } else {
                Toast.makeText(context, error?.localizedMessage ?: context.getString(R.string.song_more_metadata_save_failed), Toast.LENGTH_SHORT).show()
            }
        }
    }
    val sortedSongIndexById = remember(sortedSongs) {
        buildMap {
            sortedSongs.forEachIndexed { index, song -> put(song.id, index) }
        }
    }
    val detailSongsByAlbumId = remember(songs) {
        songs.groupBy { it.albumIdentityId() }
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
            MetadataDetailTab.Songs -> sortedSongs.filter { it.id in selectedIds }
            MetadataDetailTab.Albums -> sortedAlbums
                .filter { it.id in selectedIds }
                .flatMap { detailSongsByAlbumId[it.id].orEmpty() }
                .distinctBy { it.playlistIdentityKey() }
        }
    }
    val selectedVisibleCount = remember(selectedIds, currentSelectionIds) {
        currentSelectionIds.count { it in selectedIds }
    }
    val rangeSelectionAvailable = remember(currentSelectionIndexById, selectedIds, rangeAnchorId, rangeTargetId) {
        val anchor = rangeAnchorId
        val target = rangeTargetId
        anchor != null &&
            target != null &&
            anchor != target &&
            anchor in selectedIds &&
            target in selectedIds &&
            anchor in currentSelectionIndexById &&
            target in currentSelectionIndexById
    }
    fun applyRangeSelection() {
        val anchor = rangeAnchorId ?: return
        val target = rangeTargetId ?: return
        val anchorIndex = currentSelectionIndexById[anchor] ?: return
        val targetIndex = currentSelectionIndexById[target] ?: return
        if (anchorIndex == targetIndex) return
        val bounds = if (anchorIndex < targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
        selectedIds = selectedIds + bounds.map { currentSelectionIds[it] }
        rangeAnchorId = target
        rangeTargetId = null
    }
    fun toggleSelectAllVisibleItems() {
        if (currentSelectionIds.isEmpty()) return
        val ids = currentSelectionIds.toMutableSet()
        if (ids.all { it in selectedIds }) {
            selectedIds = selectedIds - ids
            rangeAnchorId = null
            rangeTargetId = null
        } else {
            selectedIds = selectedIds + ids
            rangeAnchorId = currentSelectionIds.firstOrNull()
            rangeTargetId = currentSelectionIds.lastOrNull()
        }
        selectionMode = true
    }
    val currentSongItemIndex = remember(sortedSongIndexById, currentSong?.id, selectedTab, selectionMode) {
        if (selectedTab != MetadataDetailTab.Songs || selectionMode) return@remember -1
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
    BackHandler(enabled = selectionMode || sortExpanded || searchExpanded) {
        when {
            selectionMode -> {
                clearSelection()
            }
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
        }
    }
    LaunchedEffect(selectedTab) {
        if (selectionMode) {
            clearSelection()
        }
    }
    LaunchedEffect(selectionMode, currentSelectionIds) {
        if (!selectionMode) return@LaunchedEffect
        val visibleIds = currentSelectionIds.toMutableSet()
        selectedIds = selectedIds.filterTo(mutableSetOf()) { it in visibleIds }
        if (rangeAnchorId !in visibleIds) rangeAnchorId = selectedIds.firstOrNull()
        if (rangeTargetId !in visibleIds) rangeTargetId = null
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
                navigationIcon = {
                    IconButton(onClick = { if (selectionMode) clearSelection() else onBack() }) {
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
                            val selectedSongs = selectedActionSongs()
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                playerViewModel.playNext(selectedSongs)
                                Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                                clearSelection()
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
                            val selectedSongs = selectedActionSongs()
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                playlistPickerSongs = selectedSongs
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Add,
                                contentDescription = stringResource(R.string.song_more_add_to_playlist),
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
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
                            selectionMode = true
                            selectedIds = emptySet()
                            rangeAnchorId = null
                            rangeTargetId = null
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
            DoubleTapScrollOverlay(
                onDoubleTap = { scope.launch { listState.animateScrollToItem(0) } },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                endPadding = 208.dp
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
                        val summaryText = if (selectionMode) {
                            stringResource(R.string.library_selected_fraction, selectedIds.size, currentSelectionIds.size)
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
                        Text(
                            text = summaryText,
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
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
                            selectionMode = selectionMode,
                            selected = album.id in selectedIds,
                            onClick = {
                                if (selectionMode) {
                                    toggleSongSelection(album.id)
                                } else {
                                    onAlbumClick(album.id)
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selectedIds = selectedIds + album.id
                                updateRangeAnchorsForManualSelection(album.id, selectedNow = true)
                            }
                        )
                    }
                } else {
                    itemsIndexed(sortedSongs, key = { _, song -> song.id }) { index, song ->
                        val selected = song.id in selectedIds
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
                            showPlayNextInLists = showPlayNextInLists,
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            loadSongRating = mainViewModel::getSongRating,
                            selectionMode = selectionMode,
                            selected = selected,
                            onLongClick = {
                                selectionMode = true
                                selectedIds = selectedIds + song.id
                                updateRangeAnchorsForManualSelection(song.id, selectedNow = true)
                            },
                            onClick = {
                                if (selectionMode) {
                                    toggleSongSelection(song.id)
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
                if (sortMode == MetadataDetailSongSortMode.Title || sortMode == MetadataDetailSongSortMode.FileName) {
                    FastIndexBar(
                        letters = fastIndexLetters,
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

            ShuffleAllFloatingButton(
                visible = !selectionMode && sortedSongs.isNotEmpty(),
                onClick = {
                    playerViewModel.setPlaylist(sortedSongs.shuffled(), 0)
                    if (openPlayerOnPlay) onNavigateToPlayer()
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibrarySecondaryFloatingControlsBottomPadding)
            )
            LocateCurrentSongFloatingButton(
                listState = listState,
                currentItemIndex = currentSongItemIndex,
                locateRequest = locateCurrentSongRequest,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
            )
            FloatingSelectionControls(
                visible = selectionMode && currentSelectionIds.isNotEmpty(),
                rangeEnabled = rangeSelectionAvailable,
                allSelected = currentSelectionIds.isNotEmpty() && selectedVisibleCount == currentSelectionIds.size,
                onRangeSelect = ::applyRangeSelection,
                onSelectAll = ::toggleSelectAllVisibleItems,
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
                onDeleteSelectedSongs = ::deleteSelectedSongs,
                onClearSelection = ::clearSelection
            )
        }
    }
}
