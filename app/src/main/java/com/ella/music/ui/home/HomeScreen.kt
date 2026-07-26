package com.ella.music.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import android.app.Activity
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarRate
import com.ella.music.data.SettingsManager
import com.ella.music.data.NeteaseKeyInfo
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.ArtistPickerSheet
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.DirectionalSortField
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.SideIndexListEndPadding
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.SongSelectionActionRow
import com.ella.music.ui.components.ShuffleAllFloatingButton
import com.ella.music.ui.components.ScanRefreshIconButton
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.TagEditorOption
import com.ella.music.ui.components.TagEditorOptionKind
import com.ella.music.ui.components.buildTagEditorOptions
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.launchTagEditorOption
import com.ella.music.ui.components.openSongSpectrumWithAspectPro
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.shareLocalSong
import com.ella.music.ui.components.wallpaperContentOverlayColor
import com.ella.music.ui.components.directionalSortDropdownItems
import com.ella.music.ui.listmodel.SortDirection
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.FloatingActionButton
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.Job

@Composable
fun LibraryScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onNavigateToPlayer: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToSearch: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val songs by mainViewModel.songs.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val isScanning by mainViewModel.isScanning.collectAsState()
    val scanProgress by mainViewModel.scanProgress.collectAsState()
    val ratingRevision by mainViewModel.ratingRevision.collectAsState()
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val openPlayerOnPlay by settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val pageBackground = ellaPageBackground()
    val contentOverlayColor = wallpaperContentOverlayColor()
    val wallpaperVisible = contentOverlayColor.alpha > 0f
    val libraryPageBackground = if (wallpaperVisible) contentOverlayColor else pageBackground
    val searchBarColor = if (wallpaperVisible) {
        MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f)
    } else {
        MiuixTheme.colorScheme.surfaceContainerHigh
    }

    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var ratingFilter by remember { mutableStateOf(HomeRatingFilterUiState.selectedRatings) }
    var favoriteFilter by remember { mutableStateOf(false) }
    val sortIndex by settingsManager.librarySongSortIndex.collectAsState(initial = LibrarySortUiState.librarySongSortIndex)
    val sortMode = HomeSortMode.entries.getOrElse(sortIndex) { HomeSortMode.Title }
    LaunchedEffect(sortIndex) {
        LibrarySortUiState.librarySongSortIndex = sortIndex
    }
    val selection = rememberLibrarySelectionState<Long>()
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var artistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var songInfoSheetSong by remember { mutableStateOf<Song?>(null) }
    var aiInterpretationSong by remember { mutableStateOf<Song?>(null) }
    var listCoversEnabled by remember { mutableStateOf(false) }
    var pendingSystemDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var pendingConfirmDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var ratingFilterExpanded by remember { mutableStateOf(false) }
    var scrollToTopRequest by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    fun applyHomeSortMode(mode: HomeSortMode) {
        LibrarySortUiState.librarySongSortIndex = mode.ordinal
        scope.launch { settingsManager.setLibrarySongSortIndex(mode.ordinal) }
        scrollToTopRequest++
    }

    fun navigateToArtistOrChoose(artistText: String) {
        val artists = splitArtistNames(artistText)
            .distinctBy { it.tagIdentityKey() }
        when (artists.size) {
            0 -> Toast.makeText(context, context.getString(R.string.player_no_artist_jump), Toast.LENGTH_SHORT).show()
            1 -> onNavigateToArtist(artists.first())
            else -> artistChoices = artists
        }
    }
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val songsToDelete = pendingSystemDeleteSongs
        pendingSystemDeleteSongs = emptyList()
        if (result.resultCode == Activity.RESULT_OK && songsToDelete.isNotEmpty()) {
            mainViewModel.removeSongsFromLibrary(songsToDelete)
            Toast.makeText(
                context,
                context.getString(R.string.library_deleted_songs, songsToDelete.size),
                Toast.LENGTH_SHORT
            ).show()
        } else if (songsToDelete.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.library_delete_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    fun requestDeleteSongs(songsToDelete: List<Song>) {
        if (songsToDelete.isEmpty()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = songsToDelete
                .filter { it.id > 0L }
                .map { song ->
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                }
            if (uris.isNotEmpty()) {
                runCatching {
                    pendingSystemDeleteSongs = songsToDelete
                    val request = MediaStore.createDeleteRequest(context.contentResolver, uris)
                    deleteRequestLauncher.launch(
                        IntentSenderRequest.Builder(request.intentSender).build()
                    )
                }.onFailure {
                    pendingSystemDeleteSongs = emptyList()
                    mainViewModel.deleteSongs(songsToDelete)
                    Toast.makeText(
                        context,
                        context.getString(R.string.library_deleting_songs, songsToDelete.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return
            }
        }
        mainViewModel.deleteSongs(songsToDelete)
        Toast.makeText(
            context,
            context.getString(R.string.library_deleting_songs, songsToDelete.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    LaunchedEffect(Unit) {
        delay(260L)
        listCoversEnabled = true
    }

    val activeFavoriteSongKeys = if (favoriteFilter) favoriteSongKeys else emptySet()
    val activeRatingRevision = if (ratingFilter.isEmpty()) 0 else ratingRevision
    val filteredSongs by produceState(
        initialValue = songs,
        songs,
        searchQuery,
        ratingFilter,
        favoriteFilter,
        activeFavoriteSongKeys,
        activeRatingRevision
    ) {
        val query = searchQuery.trim()
        val favoriteKeys = activeFavoriteSongKeys
        if (query.isBlank() && ratingFilter.isEmpty() && !favoriteFilter) {
            value = songs
            return@produceState
        }
        val base = withContext(Dispatchers.IO) {
            songs.filter { song ->
                val ratingMatched = when {
                    ratingFilter.isEmpty() -> true
                    ratingFilter == setOf(0) -> mainViewModel.getSongRating(song) !in 1..5
                    else -> mainViewModel.getSongRating(song) in ratingFilter
                }
                if (!ratingMatched) return@filter false
                if (favoriteFilter && song.playlistIdentityKey() !in favoriteKeys) return@filter false
                true
            }
        }
        value = if (query.isBlank()) base else mainViewModel.filterSongsBySearchSnapshot(base, query)
    }
    // Keep initialValue O(1) and avoid rendering the full unsorted list before the background
    // sort completes. Large libraries can otherwise allocate several 60k-entry helper
    // collections twice while switching into this screen.
    val sortedResult by produceState<HomeSortedSongs?>(
        initialValue = null,
        filteredSongs,
        sortMode
    ) {
        value = withContext(Dispatchers.Default) { filteredSongs.cachedSortedForHomeMode(sortMode) }
    }
    val sortedSongs = sortedResult?.songs.orEmpty()
    val sortKeysBySongId = sortedResult?.sortKeysBySongId.orEmpty()
    val visibleSongIds = remember(selection.selectionMode, sortedSongs) {
        if (selection.selectionMode) sortedSongs.mapTo(mutableSetOf()) { it.id } else emptySet()
    }
    val sortedSongIndexById = remember(selection.selectionMode, sortedSongs) {
        if (!selection.selectionMode) {
            emptyMap()
        } else {
            buildMap {
                sortedSongs.forEachIndexed { index, song -> put(song.id, index) }
            }
        }
    }
    val selectedVisibleCount = remember(selection.selectionMode, selection.selectedIds, visibleSongIds) {
        if (selection.selectionMode) selection.selectedIds.count { it in visibleSongIds } else 0
    }
    val rangeSelectionAvailable = remember(sortedSongIndexById, selection.selectedIds, selection.rangeAnchorId, selection.rangeTargetId) {
        selection.isRangeSelectionAvailable(sortedSongIndexById)
    }

    LaunchedEffect(selection.selectionMode, visibleSongIds) {
        if (!selection.selectionMode) return@LaunchedEffect
        selection.selectedIds = selection.selectedIds.filterTo(mutableSetOf()) { it in visibleSongIds }
        if (selection.rangeAnchorId !in visibleSongIds) selection.rangeAnchorId = selection.selectedIds.firstOrNull()
        if (selection.rangeTargetId !in visibleSongIds) selection.rangeTargetId = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(libraryPageBackground)
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            EllaSmallTopAppBar(
                title = "",
                color = libraryPageBackground,
                titleStartPadding = if (!selection.selectionMode && songs.isNotEmpty()) 156.dp else 20.dp,
                titleEndPadding = if (selection.selectionMode) 170.dp else 152.dp,
                navigationIcon = {
                    if (!selection.selectionMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            ScanRefreshIconButton(
                                enabled = !isScanning,
                                onScan = { mainViewModel.scanMusic() },
                                onDeepRescan = { mainViewModel.fullRescanMusic() }
                            )
                            if (songs.isNotEmpty()) {
                                IconButton(onClick = { ratingFilterExpanded = !ratingFilterExpanded }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_rating_star_half),
                                        contentDescription = stringResource(R.string.song_more_set_rating),
                                        tint = if (ratingFilter.isNotEmpty() || ratingFilterExpanded) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                IconButton(onClick = { favoriteFilter = !favoriteFilter }) {
                                    Icon(
                                        painter = painterResource(
                                            id = if (favoriteFilter) {
                                                R.drawable.ic_notification_favorite_filled
                                            } else {
                                                R.drawable.ic_notification_favorite
                                            }
                                        ),
                                        contentDescription = stringResource(R.string.favorite_filter),
                                        tint = if (favoriteFilter) {
                                            Color(0xFFFF4D6D)
                                        } else {
                                            MiuixTheme.colorScheme.onSurface
                                        },
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                },
                actions = {
                    if (selection.selectionMode) {
                        IconButton(onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selection.selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                playlistPickerSongs = selectedSongs
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Add,
                                contentDescription = stringResource(R.string.category_playlist),
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = {
                            val selectedSongs = sortedSongs.filter { it.id in selection.selectedIds }
                            if (selectedSongs.isEmpty()) {
                                Toast.makeText(context, context.getString(R.string.library_select_songs_first), Toast.LENGTH_SHORT).show()
                            } else {
                                pendingConfirmDeleteSongs = selectedSongs
                            }
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = Color(0xFFE5484D),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = {
                            selection.finishSelectionMode()
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Close,
                                contentDescription = stringResource(R.string.common_exit_selection),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            selection.selectionMode = true
                            selection.selectedIds = emptySet()
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.SelectAll,
                                contentDescription = stringResource(R.string.common_select_all),
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
                                applyHomeSortMode(
                                    field.toMode(direction == SortDirection.Descending)
                                )
                            }
                        )
                    }
                }
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scrollToTopRequest++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                startPadding = if (!selection.selectionMode && songs.isNotEmpty()) 160.dp else 56.dp
            )
        }

        BackHandler(enabled = selection.selectionMode || searchExpanded || ratingFilterExpanded) {
            when {
                selection.selectionMode -> {
                    selection.finishSelectionMode()
                }
                searchExpanded -> {
                    searchExpanded = false
                    searchQuery = ""
                }
                ratingFilterExpanded -> ratingFilterExpanded = false
            }
        }

        if (searchExpanded) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = stringResource(R.string.library_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                containerColor = searchBarColor
            )
        }

        AnimatedVisibility(
            visible = songs.isNotEmpty() && !selection.selectionMode && ratingFilterExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            StarRatingFilterRow(
                selectedRatings = ratingFilter,
                onRatingsChange = {
                    ratingFilter = it
                    HomeRatingFilterUiState.selectedRatings = it
                }
            )
        }

        AnimatedVisibility(
            visible = isScanning && scanProgress > 0,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Text(
                    text = stringResource(R.string.library_scanning_count, scanProgress),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        if (songs.isEmpty() && !libraryCacheLoaded && !isScanning) {
            EllaCenteredLoadingIndicator()
        } else if (songs.isEmpty() && !isScanning) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.library_empty_hint),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else if (sortedResult == null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.library_organizing),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        } else {
            val listState = rememberLazyListState()
            RestoreListScrollAfterSearch(
                searchExpanded = searchExpanded,
                query = searchQuery,
                listState = listState
            )
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            var handledLocateRequest by remember { mutableStateOf(locateCurrentSongRequest) }
            val currentSongKey = remember(currentSong) { currentSong?.playlistIdentityKey() }
            val currentSongIndex = remember(sortedSongs, currentSongKey) {
                currentSongKey ?: return@remember -1
                sortedSongs.indexOfFirst { it.playlistIdentityKey() == currentSongKey }
            }
            val showLocateCurrentSongButton by remember(currentSongIndex, selection.selectionMode) {
                derivedStateOf {
                    if (selection.selectionMode || currentSongIndex < 0) return@derivedStateOf false
                    val visibleIndexes = listState.layoutInfo.visibleItemsInfo.map { it.index }
                    if (visibleIndexes.isEmpty()) return@derivedStateOf false
                    visibleIndexes.none { kotlin.math.abs(it - currentSongIndex) <= 2 }
                }
            }

            LaunchedEffect(locateCurrentSongRequest) {
                if (locateCurrentSongRequest <= 0 || locateCurrentSongRequest == handledLocateRequest) return@LaunchedEffect
                handledLocateRequest = locateCurrentSongRequest
                if (currentSongIndex >= 0) listState.animateScrollToItem(currentSongIndex)
            }

            LaunchedEffect(scrollToTopRequest) {
                if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
            }

            // Compute the per-song index letter once and reuse it for both the bar labels and the
            // scroll targets. Building this inline on every recomposition was O(n) main-thread work
            // that scaled badly for large libraries (1k–10k+ songs).
            val showFastIndexBar = sortMode.sortField() == HomeSortField.Title && sortedSongs.size > 30
            val fastIndexData = remember(showFastIndexBar, sortedSongs, sortKeysBySongId) {
                if (!showFastIndexBar) {
                    FastIndexData.Empty
                } else {
                    val targets = LinkedHashMap<String, Int>()
                    sortedSongs.forEachIndexed { index, song ->
                        targets.putIfAbsent(song.indexLetter(sortKeysBySongId[song.id]), index)
                    }
                    FastIndexData(
                        letters = targets.keys.toList(),
                        targets = targets
                    )
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                if (wallpaperVisible) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(contentOverlayColor)
                    )
                }
                val showScrollIndicator = sortedSongs.size > 30 && !showFastIndexBar
                // Keep a small inset so the more button sits near, but not under, the side index bar.
                val listEndInset = when {
                    showFastIndexBar || showScrollIndicator -> SideIndexListEndPadding
                    else -> 0.dp
                }
                Column(modifier = Modifier.fillMaxSize()) {
                    if (selection.selectionMode) {
                        SongSelectionActionRow(
                            selectedCount = selectedVisibleCount,
                            totalCount = sortedSongs.size,
                            rangeEnabled = rangeSelectionAvailable,
                            allSelected = sortedSongs.isNotEmpty() && selectedVisibleCount == sortedSongs.size,
                            onRangeSelect = { selection.applyRangeSelection(sortedSongs.map { it.id }, sortedSongIndexById) },
                            onSelectAll = { selection.toggleSelectAll(sortedSongs.map { it.id }) },
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    } else {
                        Text(
                            text =
                                stringResource(
                                    R.string.library_song_count_sorted,
                                    sortedSongs.size,
                                    listOfNotNull(
                                        com.ella.music.ui.components.sortLabel(
                                            sortMode.sortField().labelRes,
                                            sortMode.isDescending()
                                        ),
                                        ratingFilter.summaryLabel(context),
                                    stringResource(R.string.favorite_filter).takeIf { favoriteFilter }
                                ).joinToString(" · ")
                                ),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(end = listEndInset, bottom = 160.dp)
                    ) {
                        itemsIndexed(
                            items = sortedSongs,
                            key = { _, song -> song.playlistIdentityKey() }
                        ) { index, song ->
                            val selected = song.id in selection.selectedIds
                            val albumArtUri = remember(listCoversEnabled, song.albumId) {
                                song.albumId
                                    .takeIf { listCoversEnabled && it > 0L }
                                    ?.let(mainViewModel::getAlbumArtUri)
                            }

                            SongItem(
                                song = song,
                                isCurrent = song.playlistIdentityKey() == currentSongKey,
                                albumArtUri = albumArtUri,
                                loadCoverArt = mainViewModel::getCoverArtBitmap,
                                loadAudioInfo = mainViewModel::getAudioInfo,
                                isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                                loadSongRating = mainViewModel::getSongRating,
                                showPlayNextInLists = showPlayNextInLists,
                                selectionMode = selection.selectionMode,
                                selected = selected,
                                onLongClick = {
                                    selection.selectionMode = true
                                    if (song.id !in selection.selectedIds) {
                                        selection.selectedIds = selection.selectedIds + song.id
                                        selection.updateRangeAnchorsForManualSelection(song.id, selectedNow = true)
                                    }
                                },
                                onClick = {
                                    if (selection.selectionMode) {
                                        selection.toggleSelection(song.id)
                                    } else {
                                        playerViewModel.setPlaylist(sortedSongs, index)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                },
                                onPlayNext = {
                                    playerViewModel.playNext(song)
                                    Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                                },
                                onMore = { actionSong = song }
                            )
                        }
                    }
                }

                if (showFastIndexBar) {
                    FastIndexBar(
                        letters = fastIndexData.letters,
                        reverse = sortMode == HomeSortMode.TitleDesc,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(end = 0.dp),
                        onLetterClick = { letter ->
                            val index = fastIndexData.targets[letter]
                            if (index != null) {
                                fastScrollJob?.cancel()
                                fastScrollJob = scope.launch {
                                    listState.scrollToItem(index)
                                }
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

                ShuffleAllFloatingButton(
                    visible = !selection.selectionMode && sortedSongs.isNotEmpty(),
                    onClick = {
                        playerViewModel.setPlaylist(sortedSongs.shuffled(), 0)
                        if (openPlayerOnPlay) onNavigateToPlayer()
                    },
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = 232.dp)
                )

                androidx.compose.animation.AnimatedVisibility(
                    visible = showLocateCurrentSongButton,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 22.dp, bottom = 176.dp)
                ) {
                    FloatingActionButton(
                        onClick = { playerViewModel.requestLocateCurrentSong() },
                        minWidth = 46.dp,
                        minHeight = 46.dp,
                        containerColor = MiuixTheme.colorScheme.primary
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_my_location),
                            contentDescription = stringResource(R.string.player_locate_current_song),
                            tint = MiuixTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }
            }
        }

        SongMoreActionHost(
            actionSong = actionSong,
            mainViewModel = mainViewModel,
            playerViewModel = playerViewModel,
            onDismissAction = { actionSong = null },
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist,
            showSongTitleInSheetHeader = false,
            onDeleteSong = { song -> requestDeleteSongs(listOf(song)) }
        )

        if (artistChoices.isNotEmpty()) {
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.song_more_select_artist),
                onDismissRequest = { artistChoices = emptyList() }
            ) {
                ArtistPickerSheet(
                    artists = artistChoices,
                    mainViewModel = mainViewModel,
                    onArtistSelected = { artist ->
                        artistChoices = emptyList()
                        onNavigateToArtist(artist)
                    },
                    onDismiss = { artistChoices = emptyList() }
                )
            }
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
                songCount = songsToAdd.size,
                onDismiss = { createPlaylistSongs = null },
                onCreate = { name ->
                    mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                        Toast.makeText(
                            context,
                            context.getString(R.string.player_added_to_playlist_named, playlist.name),
                            Toast.LENGTH_SHORT
                        ).show()
                        createPlaylistSongs = null
                        selection.finishSelectionMode()
                    }
                }
            )
        }

        ConfirmDangerDialog(
            show = pendingConfirmDeleteSongs.isNotEmpty(),
            title = stringResource(R.string.song_more_delete_song_title),
            message = stringResource(R.string.library_delete_selected_message, pendingConfirmDeleteSongs.size),
            confirmText = stringResource(R.string.song_more_delete_permanently),
            onDismiss = { pendingConfirmDeleteSongs = emptyList() },
            onConfirm = {
                requestDeleteSongs(pendingConfirmDeleteSongs)
                pendingConfirmDeleteSongs = emptyList()
                selection.finishSelectionMode()
            }
        )

        tagEditorSong?.let { song ->
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.song_more_edit_tags_title),
                onDismissRequest = { tagEditorSong = null }
            ) {
                SongTagEditorMenu(
                    song = song,
                    options = buildTagEditorOptions(context, song).filter { it.kind == TagEditorOptionKind.Metadata },
                    onDismiss = { tagEditorSong = null },
                    onOptionClick = { option ->
                        launchTagEditorOption(context, option)
                        tagEditorSong = null
                    }
                )
            }
        }

        songInfoSheetSong?.let { song ->
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.player_song_details),
                onDismissRequest = { songInfoSheetSong = null }
            ) {
                SongInfoMenu(
                    song = song,
                    audioInfoLoader = mainViewModel::getAudioInfo,
                    tagInfoLoader = mainViewModel::getSongTagInfo,
                    onAiInterpret = {
                        songInfoSheetSong = null
                        aiInterpretationSong = song
                    },
                    onDismiss = { songInfoSheetSong = null }
                )
            }
        }

        aiInterpretationSong?.let { song ->
            SongAiInterpretationMenu(
                song = song,
                mainViewModel = mainViewModel,
                onDismiss = { aiInterpretationSong = null }
            )
        }
    }
}

private data class FastIndexData(
    val letters: List<String>,
    val targets: Map<String, Int>
) {
    companion object {
        val Empty = FastIndexData(emptyList(), emptyMap())
    }
}
