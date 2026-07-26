package com.ella.music.ui.artist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.LibraryAlbumAggregator
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LibrarySecondaryFloatingControlsBottomPadding
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.ShuffleAllFloatingButton
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.ui.components.wallpaperContentOverlayColor
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ArtistScreen(
    artistName: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onAlbumClick: (Long) -> Unit,
    onArtistClick: (String) -> Unit = {},
    onMetadataCategoryClick: (String, String) -> Unit = { _, _ -> },
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val showAlbumArtists by mainViewModel.settingsManager.showAlbumArtists.collectAsState(initial = true)
    val artistCoverFolderUri by mainViewModel.settingsManager.artistCoverFolderUri.collectAsState(initial = "")
    val dynamicCoverEnabled by mainViewModel.settingsManager.dynamicCoverEnabled.collectAsState(initial = false)
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    var sortExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val sortIndex by mainViewModel.settingsManager.artistDetailSongSortIndex.collectAsState(initial = LibrarySortUiState.artistDetailSongSortIndex)
    val sortMode = ArtistDetailSongSortMode.entries.getOrElse(sortIndex) { ArtistDetailSongSortMode.Title }
    val albumSortIndex by mainViewModel.settingsManager.artistDetailAlbumSortIndex.collectAsState(initial = LibrarySortUiState.artistDetailAlbumSortIndex)
    val albumSortMode = ArtistDetailAlbumSortMode.entries.getOrElse(albumSortIndex) { ArtistDetailAlbumSortMode.YearAsc }
    val scope = rememberCoroutineScope()
    var selectedTabTarget by rememberSaveable(artistName) { mutableStateOf(ArtistTab.Songs) }
    var scrollToTopRequest by remember { mutableStateOf(0) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    val selection = rememberLibrarySelectionState<Long>()
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlistPickerSong by remember { mutableStateOf<Song?>(null) }
    var createPlaylistSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var tagEditorSong by remember { mutableStateOf<Song?>(null) }
    var songInfoSheetSong by remember { mutableStateOf<Song?>(null) }
    var aiInterpretationSong by remember { mutableStateOf<Song?>(null) }
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)

    val artistSongs = remember(songs, artistName) {
        mainViewModel.getSongsForArtist(artistName)
    }
    val artistQuery = searchQuery.trim()
    val filteredArtistSongs = remember(artistSongs, artistQuery) {
        if (artistQuery.isBlank()) {
            artistSongs
        } else {
            artistSongs.filter { song ->
                song.title.contains(artistQuery, ignoreCase = true) ||
                    song.artist.contains(artistQuery, ignoreCase = true) ||
                    song.album.contains(artistQuery, ignoreCase = true) ||
                    song.fileName.contains(artistQuery, ignoreCase = true)
            }
        }
    }
    val sortedArtistSongs = remember(filteredArtistSongs, sortMode) {
        filteredArtistSongs.sortedForArtistDetail(sortMode)
    }
    val participatedAlbums = remember(albums, songs, artistName) {
        mainViewModel.getParticipatedAlbumsForArtist(artistName)
    }
    val releaseAlbums = remember(albums, songs, artistName) {
        mainViewModel.getReleaseAlbumsForArtist(artistName)
    }
    val showReleaseAlbums = remember(albums, songs, artistName, showAlbumArtists, artistSongs) {
        mainViewModel.hasAlbumArtistTags() &&
            releaseAlbums.isNotEmpty() &&
            (showAlbumArtists || artistSongs.isEmpty())
    }
    val albumDurations = remember(songs) {
        LibraryAlbumAggregator.durationsByAlbumIdentity(songs)
    }
    val representativeSongsByAlbumId = remember(songs) {
        LibraryAlbumAggregator.representativeSongsByAlbumIdentity(songs)
    }
    val filteredParticipatedAlbums = remember(participatedAlbums, artistQuery) {
        if (artistQuery.isBlank()) {
            participatedAlbums
        } else {
            participatedAlbums.filter { album ->
                album.name.contains(artistQuery, ignoreCase = true) ||
                    album.artist.contains(artistQuery, ignoreCase = true) ||
                    album.albumArtist.contains(artistQuery, ignoreCase = true) ||
                    album.year.contains(artistQuery, ignoreCase = true)
            }
        }
    }
    val sortedParticipatedAlbums = remember(filteredParticipatedAlbums, albumSortMode, albumDurations) {
        filteredParticipatedAlbums.sortedForArtistAlbumDetail(albumSortMode, albumDurations)
    }
    val filteredReleaseAlbums = remember(releaseAlbums, artistQuery) {
        if (artistQuery.isBlank()) {
            releaseAlbums
        } else {
            releaseAlbums.filter { album ->
                album.name.contains(artistQuery, ignoreCase = true) ||
                    album.artist.contains(artistQuery, ignoreCase = true) ||
                    album.albumArtist.contains(artistQuery, ignoreCase = true) ||
                    album.year.contains(artistQuery, ignoreCase = true)
            }
        }
    }
    val sortedReleaseAlbums = remember(filteredReleaseAlbums, albumSortMode, albumDurations) {
        filteredReleaseAlbums.sortedForArtistAlbumDetail(albumSortMode, albumDurations)
    }
    val hasComposerCategory = remember(songs, artistName) {
        mainViewModel.hasMetadataCategory("composer", artistName)
    }
    val hasArrangerCategory = remember(songs, artistName) {
        mainViewModel.hasMetadataCategory("arranger", artistName)
    }
    val hasLyricistCategory = remember(songs, artistName) {
        mainViewModel.hasMetadataCategory("lyricist", artistName)
    }
    val neteaseArtistUrl by produceState<String?>(initialValue = null, artistName, songs) {
        value = mainViewModel.getNeteaseArtistUrlForArtist(artistName)
    }
    val tabs = remember(showReleaseAlbums) {
        buildList {
            add(ArtistTab.Songs)
            add(ArtistTab.ParticipatedAlbums)
            if (showReleaseAlbums) add(ArtistTab.ReleaseAlbums)
        }
    }
    val selectedArtistTab = selectedTabTarget.takeIf { it in tabs } ?: ArtistTab.Songs
    val listState = rememberLazyListState()
    RestoreListScrollAfterSearch(
        searchExpanded = searchExpanded,
        query = searchQuery,
        listState = listState
    )
    val hasArtistJumpActions = hasComposerCategory || hasArrangerCategory || hasLyricistCategory || !neteaseArtistUrl.isNullOrBlank()
    val artistDetailListBodyStartIndex = 3 + if (hasArtistJumpActions) 1 else 0
    val activeArtistListSize = when (selectedArtistTab) {
        ArtistTab.Songs -> sortedArtistSongs.size
        ArtistTab.ParticipatedAlbums -> sortedParticipatedAlbums.size
        ArtistTab.ReleaseAlbums -> sortedReleaseAlbums.size
    }
    val showSongSideIndex = !selection.selectionMode &&
        selectedArtistTab == ArtistTab.Songs &&
        sortMode == ArtistDetailSongSortMode.Title &&
        sortedArtistSongs.size > 30
    val songFastIndexData = remember(showSongSideIndex, sortedArtistSongs, artistDetailListBodyStartIndex) {
        if (!showSongSideIndex) {
            emptyList()
        } else {
            sortedArtistSongs
                .mapIndexed { index, song -> song.title.toFastIndexSection() to (index + artistDetailListBodyStartIndex) }
                .distinctBy { it.first }
        }
    }
    val showScrollIndicator = activeArtistListSize > 30 && !showSongSideIndex
    val sortedArtistSongIndexById = remember(sortedArtistSongs) {
        buildMap {
            sortedArtistSongs.forEachIndexed { index, song -> put(song.id, index) }
        }
    }
    val currentSongItemIndex = remember(sortedArtistSongIndexById, currentSong?.id, selectedArtistTab, artistDetailListBodyStartIndex) {
        if (selectedArtistTab != ArtistTab.Songs || selection.selectionMode) {
            -1
        } else {
            (currentSong?.id?.let { sortedArtistSongIndexById[it] } ?: -1)
                .takeIf { it >= 0 }
                ?.plus(artistDetailListBodyStartIndex)
                ?: -1
        }
    }

    val representativeCoverSong = remember(songs, artistName) {
        selectArtistCoverSong(songs, artistName)
    }
    val artistCoverUri = representativeCoverSong?.albumId
        ?.takeIf { it > 0L }
        ?.let { mainViewModel.getAlbumArtUri(it) }
    val artistCoverState = rememberSongArtworkState(
        song = representativeCoverSong,
        albumArtUri = artistCoverUri,
        loadCoverArt = mainViewModel::getAlbumCoverArtBitmap,
        usage = ArtworkUsage.ArtistImage,
        showDefaultWhenMissing = false
    )
    // The representative song is still chosen by the #266 policy above; only the decoded
    // source changes here so that the header is no longer capped at the list thumbnail size.
    val artistOriginalCoverModel by produceState<Any?>(
        initialValue = artistCoverState.model,
        representativeCoverSong?.let { listOf(it.playlistIdentityKey(), it.dateModified, it.fileSize).joinToString("|") }
    ) {
        value = withContext(Dispatchers.IO) {
            representativeCoverSong?.let(mainViewModel::getOriginalCoverModel) ?: artistCoverState.model
        }
    }
    val customArtistCoverAssets = rememberArtistCoverAssets(
        artistName = artistName,
        folderLocation = artistCoverFolderUri,
        mainViewModel = mainViewModel
    )
    val artistCoverCarousel by mainViewModel.settingsManager.artistCoverCarousel.collectAsState(initial = true)
    val librarySongsByAlbumId = remember(songs) {
        songs.groupBy { it.albumIdentityId() }
    }
    val currentSelectionIds = remember(
        selectedArtistTab,
        sortedArtistSongs,
        sortedParticipatedAlbums,
        sortedReleaseAlbums
    ) {
        when (selectedArtistTab) {
            ArtistTab.Songs -> sortedArtistSongs.map { it.id }
            ArtistTab.ParticipatedAlbums -> sortedParticipatedAlbums.map { it.id }
            ArtistTab.ReleaseAlbums -> sortedReleaseAlbums.map { it.id }
        }
    }
    val currentSelectionIndexById = remember(currentSelectionIds) {
        buildMap {
            currentSelectionIds.forEachIndexed { index, id -> put(id, index) }
        }
    }
    fun selectedActionSongs(): List<Song> {
        val selectedAlbums = when (selectedArtistTab) {
            ArtistTab.ParticipatedAlbums -> sortedParticipatedAlbums.filter { it.id in selection.selectedIds }
            ArtistTab.ReleaseAlbums -> sortedReleaseAlbums.filter { it.id in selection.selectedIds }
            ArtistTab.Songs -> emptyList()
        }
        return when (selectedArtistTab) {
            ArtistTab.Songs -> sortedArtistSongs.filter { it.id in selection.selectedIds }
            ArtistTab.ParticipatedAlbums,
            ArtistTab.ReleaseAlbums -> selectedAlbums
                .flatMap { librarySongsByAlbumId[it.id].orEmpty() }
                .distinctBy { it.playlistIdentityKey() }
        }
    }

    val selectedVisibleCount = remember(selection.selectedIds, currentSelectionIds) {
        currentSelectionIds.count { it in selection.selectedIds }
    }
    val rangeSelectionAvailable = remember(selection.selectedIds, selection.rangeAnchorId, selection.rangeTargetId, currentSelectionIndexById) {
        selection.isRangeSelectionAvailable(currentSelectionIndexById)
    }

    BackHandler(enabled = selection.selectionMode || sortExpanded || searchExpanded) {
        when {
            selection.selectionMode -> selection.finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            else -> sortExpanded = false
        }
    }

    LaunchedEffect(selectedArtistTab) {
        if (selection.selectionMode) selection.finishSelectionMode()
    }
    LaunchedEffect(selection.selectionMode, currentSelectionIds) {
        if (!selection.selectionMode) return@LaunchedEffect
        val visibleIds = currentSelectionIds.toMutableSet()
        selection.selectedIds = selection.selectedIds.filterTo(mutableSetOf()) { it in visibleIds }
        if (selection.rangeAnchorId !in visibleIds) selection.rangeAnchorId = selection.selectedIds.firstOrNull()
        if (selection.rangeTargetId !in visibleIds) selection.rangeTargetId = null
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
    ) {
        val overlayColor = wallpaperContentOverlayColor()
        if (overlayColor.alpha > 0f) {
            Box(modifier = Modifier.fillMaxSize().background(overlayColor))
        }
        // While the library is still loading (remote source / cold start) the songs list can be
        // momentarily empty; show a spinner instead of flashing the empty/"not found" content.
        val showLibraryLoading = artistSongs.isEmpty() && !libraryCacheLoaded
        if (showLibraryLoading) {
            EllaCenteredLoadingIndicator()
        } else {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                ArtistHeader(
                    artistName = artistName,
                    fallbackCoverModel = artistOriginalCoverModel,
                    customCoverAssets = customArtistCoverAssets,
                    dynamicCoverEnabled = dynamicCoverEnabled,
                    carousel = artistCoverCarousel,
                    songCount = sortedArtistSongs.size,
                    albumCount = (participatedAlbums + releaseAlbums).distinctBy { it.id }.size,
                    onPlayAll = {
                        if (sortedArtistSongs.isNotEmpty()) {
                            playerViewModel.setPlaylist(sortedArtistSongs, 0)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    }
                )
            }

            if (hasArtistJumpActions) {
                item {
                    ArtistJumpActions(
                        hasComposerCategory = hasComposerCategory,
                        hasArrangerCategory = hasArrangerCategory,
                        hasLyricistCategory = hasLyricistCategory,
                        hasNeteaseArtist = !neteaseArtistUrl.isNullOrBlank(),
                        onComposerClick = { onMetadataCategoryClick("composer", artistName) },
                        onArrangerClick = { onMetadataCategoryClick("arranger", artistName) },
                        onLyricistClick = { onMetadataCategoryClick("lyricist", artistName) },
                        onNeteaseClick = { openUrl(context, neteaseArtistUrl.orEmpty()) }
                    )
                }
            }

            item {
                ArtistTabRow(
                    tabs = tabs,
                    selectedTab = selectedArtistTab,
                    onTabSelected = { tab -> selectedTabTarget = tab }
                )
            }

            when (selectedArtistTab) {
                ArtistTab.Songs -> {
                    item {
                        com.ella.music.ui.components.SortSummaryHeader(
                            text = stringResource(
                                R.string.artist_song_count_sorted,
                                sortedArtistSongs.size,
                                com.ella.music.ui.components.sortLabel(sortMode.labelRes, sortMode.isDescending())
                            )
                        )
                    }

                    itemsIndexed(sortedArtistSongs) { index, song ->
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
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            loadSongRating = mainViewModel::getSongRating,
                            showPlayNextInLists = showPlayNextInLists,
                            selectionMode = selection.selectionMode,
                            selected = selected,
                            onClick = {
                                if (selection.selectionMode) {
                                    selection.toggleSelection(song.id)
                                } else {
                                    playerViewModel.setPlaylist(sortedArtistSongs, index)
                                    if (openPlayerOnPlay) onNavigateToPlayer()
                                }
                            },
                            onLongClick = {
                                selection.selectionMode = true
                                selection.selectedIds = selection.selectedIds + song.id
                                selection.updateRangeAnchorsForManualSelection(song.id, selectedNow = true)
                            },
                            onPlayNext = { playerViewModel.playNext(song) },
                            onMore = { actionSong = song }
                        )
                    }
                }

                ArtistTab.ParticipatedAlbums -> {
                    item {
                        com.ella.music.ui.components.SortSummaryHeader(
                            text = stringResource(
                                R.string.artist_participated_album_count_sorted,
                                sortedParticipatedAlbums.size,
                                com.ella.music.ui.components.sortLabel(albumSortMode.labelRes, albumSortMode.isDescending())
                            )
                        )
                    }
                    items(
                        items = sortedParticipatedAlbums,
                        key = { it.id }
                    ) { album ->
                        val albumArtUri = remember(album.artAlbumId) {
                            album.artAlbumId
                                .takeIf { it > 0L }
                                ?.let(mainViewModel::getAlbumArtUri)
                        }
                        ArtistAlbumRow(
                            album = album,
                            duration = albumDurations[album.id] ?: 0L,
                            albumArtUri = albumArtUri,
                            representativeSong = representativeSongsByAlbumId[album.id],
                            loadCoverArt = mainViewModel::getLargeCoverArtBitmap,
                            contextArtistName = artistName,
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
                }

                ArtistTab.ReleaseAlbums -> {
                    item {
                        com.ella.music.ui.components.SortSummaryHeader(
                            text = stringResource(
                                R.string.artist_release_album_count_sorted,
                                sortedReleaseAlbums.size,
                                com.ella.music.ui.components.sortLabel(albumSortMode.labelRes, albumSortMode.isDescending())
                            )
                        )
                    }
                    items(
                        items = sortedReleaseAlbums,
                        key = { it.id }
                    ) { album ->
                        val albumArtUri = remember(album.artAlbumId) {
                            album.artAlbumId
                                .takeIf { it > 0L }
                                ?.let(mainViewModel::getAlbumArtUri)
                        }
                        ArtistAlbumRow(
                            album = album,
                            duration = albumDurations[album.id] ?: 0L,
                            albumArtUri = albumArtUri,
                            representativeSong = representativeSongsByAlbumId[album.id],
                            loadCoverArt = mainViewModel::getLargeCoverArtBitmap,
                            contextArtistName = artistName,
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
                }
            }

            if (selectedArtistTab != ArtistTab.Songs && (selectedArtistTab == ArtistTab.ParticipatedAlbums && participatedAlbums.isEmpty() || selectedArtistTab == ArtistTab.ReleaseAlbums && releaseAlbums.isEmpty())) {
                item {
                    Text(
                        text = stringResource(R.string.artist_no_albums),
                        fontSize = 14.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp)
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        }

        if (showSongSideIndex && songFastIndexData.isNotEmpty()) {
            FastIndexBar(
                letters = songFastIndexData.map { it.first },
                onLetterClick = { letter ->
                    songFastIndexData.firstOrNull { it.first == letter }?.second?.let { itemIndex ->
                        scope.launch { listState.scrollToItem(itemIndex) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 88.dp, bottom = 118.dp)
            )
        } else if (showScrollIndicator) {
            LazyListScrollIndicator(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 88.dp, bottom = 118.dp)
            )
        }

        IconButton(
            onClick = { if (selection.selectionMode) selection.finishSelectionMode() else onBack() },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 8.dp, top = 8.dp)
                .size(48.dp)
                .align(Alignment.TopStart)
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Back,
                contentDescription = stringResource(R.string.common_back),
                tint = Color.White,
                modifier = Modifier.size(26.dp)
            )
        }

        IconButton(
            onClick = {
                if (selection.selectionMode) {
                    val selected = selectedActionSongs()
                    if (selected.isNotEmpty()) playlistPickerSongs = selected
                } else {
                    selection.selectionMode = true
                    selection.selectedIds = emptySet()
                    selection.rangeAnchorId = null
                    selection.rangeTargetId = null
                }
            },
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(end = 104.dp, top = 8.dp)
                .size(48.dp)
                .align(Alignment.TopEnd)
        ) {
            Icon(
                imageVector = if (selection.selectionMode) MiuixIcons.Regular.Add else MiuixIcons.Regular.SelectAll,
                contentDescription = stringResource(if (selection.selectionMode) R.string.player_add_to_playlist else R.string.common_multi_select),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        if (selection.selectionMode) {
            IconButton(
                onClick = {
                    val selected = selectedActionSongs()
                    if (selected.isNotEmpty()) {
                        playerViewModel.playNext(selected)
                        selection.finishSelectionMode()
                    }
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = 56.dp, top = 8.dp)
                    .size(48.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Play,
                    contentDescription = stringResource(R.string.song_more_play_next),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            IconButton(
                onClick = {
                    val selected = selectedActionSongs()
                    if (selected.isNotEmpty()) pendingDeleteSongs = selected
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = 8.dp, top = 8.dp)
                    .size(48.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = Color(0xFFE5484D),
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        if (!selection.selectionMode) {
            IconButton(
                onClick = {
                    searchExpanded = !searchExpanded
                    if (!searchExpanded) searchQuery = ""
                },
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = 56.dp, top = 8.dp)
                    .size(48.dp)
                    .align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = MiuixIcons.Basic.Search,
                    contentDescription = stringResource(R.string.common_search),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(end = 8.dp, top = 8.dp)
                    .size(48.dp)
                    .align(Alignment.TopEnd)
            ) {
                val sortItems = if (selectedArtistTab == ArtistTab.Songs) {
                    directionalSortModeDropdownItems(
                        fields = listOf(
                            DirectionalSortModeField(
                                text = stringResource(R.string.artist_sort_title),
                                ascendingMode = ArtistDetailSongSortMode.Title,
                                descendingMode = ArtistDetailSongSortMode.TitleDesc
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.artist_sort_album_track),
                                ascendingMode = ArtistDetailSongSortMode.AlbumTrack,
                                descendingMode = ArtistDetailSongSortMode.AlbumTrackDesc
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.artist_sort_file_name),
                                ascendingMode = ArtistDetailSongSortMode.FileName,
                                descendingMode = ArtistDetailSongSortMode.FileNameDesc
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.artist_sort_duration),
                                ascendingMode = ArtistDetailSongSortMode.DurationAsc,
                                descendingMode = ArtistDetailSongSortMode.Duration
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_song_sort_date_added),
                                ascendingMode = ArtistDetailSongSortMode.DateAddedAsc,
                                descendingMode = ArtistDetailSongSortMode.DateAdded
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_song_sort_date_modified),
                                ascendingMode = ArtistDetailSongSortMode.DateModifiedAsc,
                                descendingMode = ArtistDetailSongSortMode.DateModified
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_song_sort_year),
                                ascendingMode = ArtistDetailSongSortMode.YearAsc,
                                descendingMode = ArtistDetailSongSortMode.YearDesc
                            )
                        ),
                        selectedMode = sortMode,
                        onSelect = { mode ->
                            LibrarySortUiState.artistDetailSongSortIndex = mode.ordinal
                            scope.launch { mainViewModel.settingsManager.setArtistDetailSongSortIndex(mode.ordinal) }
                            scrollToTopRequest++
                        }
                    )
                } else {
                    directionalSortModeDropdownItems(
                        fields = listOf(
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_song_sort_year),
                                ascendingMode = ArtistDetailAlbumSortMode.YearAsc,
                                descendingMode = ArtistDetailAlbumSortMode.YearDesc
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.artist_sort_song_count),
                                ascendingMode = ArtistDetailAlbumSortMode.SongCountAsc,
                                descendingMode = ArtistDetailAlbumSortMode.SongCount
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.artist_sort_duration),
                                ascendingMode = ArtistDetailAlbumSortMode.DurationAsc,
                                descendingMode = ArtistDetailAlbumSortMode.Duration
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.artist_sort_album_name),
                                ascendingMode = ArtistDetailAlbumSortMode.Name,
                                descendingMode = ArtistDetailAlbumSortMode.NameDesc
                            )
                        ),
                        selectedMode = albumSortMode,
                        onSelect = { mode ->
                            LibrarySortUiState.artistDetailAlbumSortIndex = mode.ordinal
                            scope.launch { mainViewModel.settingsManager.setArtistDetailAlbumSortIndex(mode.ordinal) }
                            scrollToTopRequest++
                        }
                    )
                }
                SortDropdownMenu(
                    items = sortItems,
                    tint = Color.White
                )
            }
        }

        AnimatedVisibility(
            visible = searchExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp)
        ) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = stringResource(R.string.library_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
            )
        }

        DoubleTapScrollOverlay(
            onDoubleTap = { scrollToTopRequest++ },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .fillMaxWidth()
                .height(56.dp),
            startPadding = 64.dp,
            endPadding = 208.dp
        )

        if (selection.selectionMode) {
            Text(
                text = stringResource(R.string.library_selected_fraction, selection.selectedIds.size, currentSelectionIds.size),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 22.dp)
            )
        }

        AnimatedVisibility(
            visible = sortExpanded,
            enter = expandVertically(),
            exit = shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 60.dp, end = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                if (selectedArtistTab == ArtistTab.Songs) {
                    ArtistDetailSongSortMode.entries.forEach { mode ->
                        Text(
                            text = stringResource(mode.labelRes),
                            fontSize = 14.sp,
                            fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LibrarySortUiState.artistDetailSongSortIndex = mode.ordinal
                                    scope.launch { mainViewModel.settingsManager.setArtistDetailSongSortIndex(mode.ordinal) }
                                    scrollToTopRequest++
                                    sortExpanded = false
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                } else {
                    ArtistDetailAlbumSortMode.entries.forEach { mode ->
                        Text(
                            text = stringResource(mode.labelRes),
                            fontSize = 14.sp,
                            fontWeight = if (albumSortMode == mode) FontWeight.Bold else FontWeight.Normal,
                            color = if (albumSortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    LibrarySortUiState.artistDetailAlbumSortIndex = mode.ordinal
                                    scope.launch { mainViewModel.settingsManager.setArtistDetailAlbumSortIndex(mode.ordinal) }
                                    scrollToTopRequest++
                                    sortExpanded = false
                                }
                                .padding(vertical = 10.dp)
                        )
                    }
                }
            }
        }

        ShuffleAllFloatingButton(
            visible = !selection.selectionMode && sortedArtistSongs.isNotEmpty(),
            onClick = {
                playerViewModel.setPlaylist(sortedArtistSongs.shuffled(), 0)
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
            visible = selection.selectionMode && currentSelectionIds.isNotEmpty(),
            rangeEnabled = rangeSelectionAvailable,
            allSelected = currentSelectionIds.isNotEmpty() && selectedVisibleCount == currentSelectionIds.size,
            onRangeSelect = { selection.applyRangeSelection(currentSelectionIds, currentSelectionIndexById) },
            onSelectAll = { selection.toggleSelectAll(currentSelectionIds) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
        )

        ArtistScreenSurfaces(
            context = context,
            mainViewModel = mainViewModel,
            playlists = playlists,
            actionSong = actionSong,
            onActionSongChange = { actionSong = it },
            playerViewModel = playerViewModel,
            onNavigateToAlbum = onAlbumClick,
            onNavigateToArtist = onArtistClick,
            playlistPickerSong = playlistPickerSong,
            onPlaylistPickerSongChange = { playlistPickerSong = it },
            createPlaylistSong = createPlaylistSong,
            onCreatePlaylistSongChange = { createPlaylistSong = it },
            playlistPickerSongs = playlistPickerSongs,
            onPlaylistPickerSongsChange = { playlistPickerSongs = it },
            createPlaylistSongs = createPlaylistSongs,
            onCreatePlaylistSongsChange = { createPlaylistSongs = it },
            pendingDeleteSongs = pendingDeleteSongs,
            onPendingDeleteSongsChange = { pendingDeleteSongs = it },
            onRequestDeleteSongs = requestDeleteSongs,
            onFinishSelectionMode = selection::finishSelectionMode,
            tagEditorSong = tagEditorSong,
            onTagEditorSongChange = { tagEditorSong = it },
            songInfoSheetSong = songInfoSheetSong,
            onSongInfoSheetSongChange = { songInfoSheetSong = it },
            aiInterpretationSong = aiInterpretationSong,
            onAiInterpretationSongChange = { aiInterpretationSong = it }
        )
    }
}
