package com.ella.music.ui.artist

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import android.widget.Toast
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.MusicVideoLauncher
import com.ella.music.data.LibraryAlbumAggregator
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.ShuffleAllSummaryButton
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.rememberSongArtworkState
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.ui.components.openVideoWithMediaInfo
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Forward
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Share
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
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val showAlbumArtists by mainViewModel.settingsManager.showAlbumArtists.collectAsState(initial = true)
    val artistCoverFolderUri by mainViewModel.settingsManager.artistCoverFolderUri.collectAsState(initial = "")
    val dynamicCoverEnabled by mainViewModel.settingsManager.dynamicCoverEnabled.collectAsState(initial = false)
    val dynamicCoverCustomFolders by mainViewModel.settingsManager.dynamicCoverCustomFolders.collectAsState(initial = emptyList())
    val musicVideoCustomFolders by mainViewModel.settingsManager.musicVideoCustomFolders.collectAsState(initial = emptyList())
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
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
    var musicVideoSortMode by rememberSaveable(artistName) { mutableStateOf(ArtistMusicVideoSortMode.ReleaseDesc) }
    var musicVideoRevision by remember { mutableStateOf(0) }
    var pendingDeleteMusicVideos by remember { mutableStateOf<List<ArtistMusicVideo>>(emptyList()) }
    var musicVideoMenuTarget by remember { mutableStateOf<ArtistMusicVideo?>(null) }
    var musicVideoInfoTarget by remember { mutableStateOf<ArtistMusicVideo?>(null) }
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)

    val artistSongs = remember(songs, artistName) {
        mainViewModel.getSongsForArtist(artistName)
    }
    val artistMusicVideoState by produceState(
        initialValue = emptyList<ArtistMusicVideo>() to true,
        artistSongs,
        dynamicCoverCustomFolders,
        musicVideoCustomFolders,
        musicVideoRevision
    ) {
        value = emptyList<ArtistMusicVideo>() to true
        val sources = withContext(Dispatchers.IO) {
            resolveArtistMusicVideoSources(
                context = context.applicationContext,
                songs = artistSongs,
                dynamicCoverFolders = dynamicCoverCustomFolders,
                musicVideoFolders = musicVideoCustomFolders,
                onProgress = { partial -> value = partial to true }
            )
        }
        value = sources to true
        value = withContext(Dispatchers.IO) {
            enrichArtistMusicVideos(context.applicationContext, sources)
        }.let { enriched -> enriched to false }
    }
    val artistMusicVideos = artistMusicVideoState.first
    val artistMusicVideosLoading = artistMusicVideoState.second
    val artistQuery = searchQuery.trim()
    DisposableEffect(artistMusicVideos) {
        onDispose {
            artistMusicVideos.mapNotNull(ArtistMusicVideo::preview).forEach { preview ->
                if (!preview.isRecycled) preview.recycle()
            }
        }
    }
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
    val filteredArtistMusicVideos = remember(artistMusicVideos, artistQuery) {
        if (artistQuery.isBlank()) {
            artistMusicVideos
        } else {
            artistMusicVideos.filter { item ->
                item.song.title.contains(artistQuery, ignoreCase = true) ||
                    item.song.artist.contains(artistQuery, ignoreCase = true) ||
                    item.song.album.contains(artistQuery, ignoreCase = true) ||
                    item.song.fileName.contains(artistQuery, ignoreCase = true)
            }
        }
    }
    val sortedArtistMusicVideos = remember(filteredArtistMusicVideos, musicVideoSortMode) {
        filteredArtistMusicVideos.sortedForArtistMusicVideo(musicVideoSortMode)
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
    val tabs = remember(showReleaseAlbums, artistMusicVideos) {
        buildList {
            add(ArtistTab.Songs)
            add(ArtistTab.ParticipatedAlbums)
            if (showReleaseAlbums) add(ArtistTab.ReleaseAlbums)
            if (artistMusicVideos.isNotEmpty()) add(ArtistTab.MusicVideos)
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
        ArtistTab.MusicVideos -> sortedArtistMusicVideos.size
    }
    val showSongSideIndex = !selection.selectionMode &&
        selectedArtistTab == ArtistTab.Songs &&
        sortMode in setOf(
            ArtistDetailSongSortMode.Title,
            ArtistDetailSongSortMode.TitleDesc,
            ArtistDetailSongSortMode.FileName,
            ArtistDetailSongSortMode.FileNameDesc
        ) &&
        sortedArtistSongs.size > 30
    val songFastIndexData = remember(showSongSideIndex, sortedArtistSongs, artistDetailListBodyStartIndex) {
        if (!showSongSideIndex) {
            emptyList()
        } else {
            sortedArtistSongs
                .mapIndexed { index, song ->
                    val indexText = if (
                        sortMode == ArtistDetailSongSortMode.FileName ||
                            sortMode == ArtistDetailSongSortMode.FileNameDesc
                    ) {
                        song.fileName.ifBlank { song.path.substringAfterLast('/') }
                    } else {
                        song.title
                    }
                    indexText.toFastIndexSection() to (index + artistDetailListBodyStartIndex)
                }
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
    val randomParticipatedAlbumSongs = remember(sortedParticipatedAlbums, librarySongsByAlbumId) {
        sortedParticipatedAlbums
            .flatMap { album -> librarySongsByAlbumId[album.id].orEmpty() }
            .distinctBy { it.id }
    }
    val randomReleaseAlbumSongs = remember(sortedReleaseAlbums, librarySongsByAlbumId) {
        sortedReleaseAlbums
            .flatMap { album -> librarySongsByAlbumId[album.id].orEmpty() }
            .distinctBy { it.id }
    }
    val randomArtistMusicVideoSongs = remember(sortedArtistMusicVideos) {
        sortedArtistMusicVideos.map { it.song }.distinctBy { it.id }
    }
    val playableArtistTabSongs = remember(
        selectedArtistTab,
        sortedArtistSongs,
        randomParticipatedAlbumSongs,
        randomReleaseAlbumSongs,
        randomArtistMusicVideoSongs
    ) {
        when (selectedArtistTab) {
            ArtistTab.Songs -> sortedArtistSongs
            ArtistTab.ParticipatedAlbums -> randomParticipatedAlbumSongs
            ArtistTab.ReleaseAlbums -> randomReleaseAlbumSongs
            ArtistTab.MusicVideos -> randomArtistMusicVideoSongs
        }
    }
    val currentSelectionIds = remember(
        selectedArtistTab,
        sortedArtistSongs,
        sortedParticipatedAlbums,
        sortedReleaseAlbums,
        sortedArtistMusicVideos
    ) {
        when (selectedArtistTab) {
            ArtistTab.Songs -> sortedArtistSongs.map { it.id }
            ArtistTab.ParticipatedAlbums -> sortedParticipatedAlbums.map { it.id }
            ArtistTab.ReleaseAlbums -> sortedReleaseAlbums.map { it.id }
            ArtistTab.MusicVideos -> sortedArtistMusicVideos.map { it.song.id }
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
            ArtistTab.Songs,
            ArtistTab.MusicVideos -> emptyList()
        }
        return when (selectedArtistTab) {
            ArtistTab.Songs -> sortedArtistSongs.filter { it.id in selection.selectedIds }
            ArtistTab.ParticipatedAlbums,
            ArtistTab.ReleaseAlbums -> selectedAlbums
                .flatMap { librarySongsByAlbumId[it.id].orEmpty() }
                .distinctBy { it.playlistIdentityKey() }
            ArtistTab.MusicVideos -> emptyList()
        }
    }
    fun selectedActionMusicVideos(): List<ArtistMusicVideo> =
        sortedArtistMusicVideos.filter { it.song.id in selection.selectedIds }
    val selectedSongsForDrag = remember(selectedArtistTab, sortedArtistSongs, selection.selectedIds) {
        if (selectedArtistTab == ArtistTab.Songs) {
            sortedArtistSongs.filter { it.id in selection.selectedIds }
        } else {
            emptyList()
        }
    }

    val selectedVisibleCount = remember(selection.selectedIds, currentSelectionIds) {
        currentSelectionIds.count { it in selection.selectedIds }
    }
    val rangeSelectionAvailable = remember(selection.selectedIds, selection.rangeAnchorId, selection.rangeTargetId, currentSelectionIndexById) {
        selection.isRangeSelectionAvailable(currentSelectionIndexById)
    }

    BackHandler(enabled = selection.selectionMode || searchExpanded) {
        when {
            selection.selectionMode -> selection.finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
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
                    albumCount = if (showAlbumArtists) {
                        (participatedAlbums + releaseAlbums).distinctBy { it.id }.size
                    } else {
                        participatedAlbums.distinctBy { it.id }.size
                    },
                    onPlayAll = {
                        if (playableArtistTabSongs.isNotEmpty()) {
                            playerViewModel.setPlaylist(playableArtistTabSongs, 0)
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
                            ),
                            leadingContent = {
                                ShuffleAllSummaryButton(
                                    visible = !selection.selectionMode && sortedArtistSongs.isNotEmpty(),
                                    onClick = {
                                        playerViewModel.setPlaylist(sortedArtistSongs.shuffled(), 0)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                )
                            }
                        )
                    }

                    item {
                        com.ella.music.ui.components.ContinuePlaybackRow(
                            songs = sortedArtistSongs,
                            playbackStats = playbackStats,
                            currentSong = currentSong,
                            onContinue = { index ->
                                playerViewModel.setPlaylist(sortedArtistSongs, index)
                                if (openPlayerOnPlay) onNavigateToPlayer()
                            }
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
                            titleOverride = if (
                                sortMode == ArtistDetailSongSortMode.FileName ||
                                    sortMode == ArtistDetailSongSortMode.FileNameDesc
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
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            loadSongRating = mainViewModel::getSongRating,
                            showPlayNextInLists = showPlayNextInLists,
                            selectionMode = selection.selectionMode,
                            selected = selected,
                            dragSelectedSongs = selectedSongsForDrag,
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
                            ),
                            leadingContent = {
                                ShuffleAllSummaryButton(
                                    visible = !selection.selectionMode && randomParticipatedAlbumSongs.isNotEmpty(),
                                    onClick = {
                                        playerViewModel.setPlaylist(randomParticipatedAlbumSongs.shuffled(), 0)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                )
                            }
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
                            ),
                            leadingContent = {
                                ShuffleAllSummaryButton(
                                    visible = !selection.selectionMode && randomReleaseAlbumSongs.isNotEmpty(),
                                    onClick = {
                                        playerViewModel.setPlaylist(randomReleaseAlbumSongs.shuffled(), 0)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                )
                            }
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

                ArtistTab.MusicVideos -> {
                    item {
                        com.ella.music.ui.components.SortSummaryHeader(
                            text = stringResource(
                                R.string.artist_music_video_count_sorted,
                                sortedArtistMusicVideos.size,
                                com.ella.music.ui.components.sortLabel(
                                    musicVideoSortMode.labelRes(),
                                    musicVideoSortMode.isDescending()
                                )
                            ),
                            leadingContent = {
                                ShuffleAllSummaryButton(
                                    visible = !selection.selectionMode && randomArtistMusicVideoSongs.isNotEmpty(),
                                    onClick = {
                                        playerViewModel.setPlaylist(randomArtistMusicVideoSongs.shuffled(), 0)
                                        if (openPlayerOnPlay) onNavigateToPlayer()
                                    }
                                )
                            }
                        )
                    }
                    items(
                        items = sortedArtistMusicVideos,
                        key = { "${it.song.id}:${it.source.failureKey}" }
                    ) { item ->
                        val selected = item.song.id in selection.selectedIds
                        ArtistMusicVideoRow(
                            item = item,
                            selectionMode = selection.selectionMode,
                            selected = selected,
                            onClick = {
                                if (selection.selectionMode) {
                                    selection.toggleSelection(item.song.id)
                                } else {
                                    MusicVideoLauncher.open(context, item.song, item.source)
                                }
                            },
                            onLongClick = {
                                selection.selectionMode = true
                                selection.selectedIds = selection.selectedIds + item.song.id
                                selection.updateRangeAnchorsForManualSelection(item.song.id, selectedNow = true)
                            },
                            onMore = { musicVideoMenuTarget = item }
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
                reverse = sortMode == ArtistDetailSongSortMode.TitleDesc ||
                    sortMode == ArtistDetailSongSortMode.FileNameDesc,
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

        EllaSmallTopAppBar(
            title = "",
            color = Color.Transparent,
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter),
            onDoubleTapTitle = { scrollToTopRequest++ },
            navigationIcon = {
                IconButton(onClick = { if (selection.selectionMode) selection.finishSelectionMode() else onBack() }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            actions = {
                if (selection.selectionMode) {
                    IconButton(
                        onClick = {
                            if (selectedArtistTab == ArtistTab.MusicVideos) {
                                val selected = selectedActionMusicVideos()
                                MusicVideoLauncher.share(context, selected.map { it.source.uri }, artistName)
                            } else {
                                val selected = selectedActionSongs()
                                if (selected.isNotEmpty()) playlistPickerSongs = selected
                            }
                        }
                    ) {
                        if (selectedArtistTab == ArtistTab.MusicVideos) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Share,
                                contentDescription = stringResource(R.string.common_share),
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        } else {
                            com.ella.music.ui.components.AddToPlaylistActionIcon(
                                contentDescription = stringResource(R.string.player_add_to_playlist),
                                tint = Color.White
                            )
                        }
                    }
                    if (selectedArtistTab != ArtistTab.MusicVideos) {
                        IconButton(
                            onClick = {
                                val selected = selectedActionSongs()
                                if (selected.isNotEmpty()) {
                                    playerViewModel.playNext(selected)
                                    selection.finishSelectionMode()
                                }
                            }
                        ) {
                            com.ella.music.ui.components.PlayNextActionIcon(
                                contentDescription = stringResource(R.string.song_more_play_next),
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(
                        onClick = {
                            if (selectedArtistTab == ArtistTab.MusicVideos) {
                                pendingDeleteMusicVideos = selectedActionMusicVideos()
                            } else {
                                val selected = selectedActionSongs()
                                if (selected.isNotEmpty()) pendingDeleteSongs = selected
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
                            tint = Color.White,
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
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    val sortItems = when (selectedArtistTab) {
                    ArtistTab.Songs -> {
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
                    }
                    ArtistTab.MusicVideos -> {
                        directionalSortModeDropdownItems(
                            fields = listOf(
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_year),
                                    ascendingMode = ArtistMusicVideoSortMode.ReleaseAsc,
                                    descendingMode = ArtistMusicVideoSortMode.ReleaseDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.artist_music_video_sort_duration),
                                    ascendingMode = ArtistMusicVideoSortMode.DurationAsc,
                                    descendingMode = ArtistMusicVideoSortMode.DurationDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.artist_music_video_sort_title),
                                    ascendingMode = ArtistMusicVideoSortMode.NameAsc,
                                    descendingMode = ArtistMusicVideoSortMode.NameDesc
                                )
                            ),
                            selectedMode = musicVideoSortMode,
                            onSelect = { mode ->
                                musicVideoSortMode = mode
                                scrollToTopRequest++
                            }
                        )
                    }
                    ArtistTab.ParticipatedAlbums,
                    ArtistTab.ReleaseAlbums -> {
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
                }
                    SortDropdownMenu(items = sortItems, tint = Color.White)
                }
            }
        )

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

        musicVideoMenuTarget?.let { item ->
            EllaMiuixBottomSheet(
                show = true,
                title = item.song.title,
                onDismissRequest = { musicVideoMenuTarget = null }
            ) {
                ArtistMusicVideoActionMenu(
                    onShare = {
                        MusicVideoLauncher.share(context, item.source.uri, item.song.title)
                    },
                    onInfo = { musicVideoInfoTarget = item },
                    onDelete = { pendingDeleteMusicVideos = listOf(item) },
                    onDismiss = { musicVideoMenuTarget = null }
                )
            }
        }

        musicVideoInfoTarget?.let { item ->
            EllaMiuixBottomSheet(
                show = true,
                title = stringResource(R.string.artist_music_video_info),
                onDismissRequest = { musicVideoInfoTarget = null }
            ) {
                ArtistMusicVideoInfoSheet(
                    item = item,
                    onOpenMediaInfo = {
                        openVideoWithMediaInfo(
                            context = context,
                            uri = item.source.uri,
                            title = item.metadata.fileName,
                            mimeType = item.metadata.mimeType
                        )
                    },
                    onDismiss = { musicVideoInfoTarget = null }
                )
            }
        }

        ConfirmDangerDialog(
            show = pendingDeleteMusicVideos.isNotEmpty(),
            title = stringResource(R.string.artist_music_video_delete_title),
            message = stringResource(
                R.string.artist_music_video_delete_message,
                pendingDeleteMusicVideos.size
            ),
            onDismiss = { pendingDeleteMusicVideos = emptyList() },
            onConfirm = {
                val targets = pendingDeleteMusicVideos
                pendingDeleteMusicVideos = emptyList()
                scope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        deleteArtistMusicVideos(context.applicationContext, targets)
                    }
                    clearArtistMusicVideoSourceCache()
                    musicVideoRevision++
                    selection.finishSelectionMode()
                    Toast.makeText(
                        context,
                        context.getString(R.string.artist_music_video_delete_result, deleted, targets.size),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        )
    }
}
