package com.ella.music.ui.album

import com.ella.music.ui.components.EllaMiuixBottomSheet

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.matchesArtistName
import com.ella.music.data.splitArtistNames
import com.ella.music.data.splitGenreNames
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.artist.selectArtistCoverSong
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ArtistPickerSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.CoverPreviewDialog
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.EllaSmallTopAppBar
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.LocateCurrentSongFloatingButton
import com.ella.music.ui.components.ShuffleAllSummaryButton
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.selectMetadataCategoryCoverSong
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    onNavigateToMetadataCategory: (String, String) -> Unit = { _, _ -> },
    onNavigateToPlayer: () -> Unit
) {
    val albums by mainViewModel.albums.collectAsState()
    val librarySongs by mainViewModel.songs.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val context = LocalContext.current
    val currentSong by playerViewModel.currentSong.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val locateCurrentSongRequest by playerViewModel.locateCurrentSongRequest.collectAsState()
    val ratingRevision by mainViewModel.ratingRevision.collectAsState()
    val openPlayerOnPlay by mainViewModel.settingsManager.openPlayerOnPlay.collectAsState(initial = false)
    val sortIndex by mainViewModel.settingsManager.albumDetailSongSortIndex.collectAsState(initial = LibrarySortUiState.albumDetailSongSortIndex)
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val showAlbumArtists by mainViewModel.settingsManager.showAlbumArtists.collectAsState(initial = true)
    val artistCoverFolderUri by mainViewModel.settingsManager.artistCoverFolderUri.collectAsState(initial = "")
    val sortMode = AlbumDetailSongSortMode.entries.getOrElse(sortIndex) { AlbumDetailSongSortMode.Track }
    val scope = rememberCoroutineScope()
    var sortExpanded by remember { mutableStateOf(false) }
    var searchExpanded by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val selection = rememberLibrarySelectionState<Long>()
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var albumArtistChoices by remember { mutableStateOf<List<String>>(emptyList()) }
    var showIntroduction by rememberSaveable(albumId) { mutableStateOf(false) }
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)
    val album = albums.find { it.id == albumId }
    val albumSongs = mainViewModel.getSongsForAlbum(albumId)
    val filteredAlbumSongs = remember(albumSongs, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            albumSongs
        } else {
            albumSongs.filter { song ->
                song.title.contains(query, ignoreCase = true) ||
                    song.artist.contains(query, ignoreCase = true) ||
                    song.album.contains(query, ignoreCase = true) ||
                    song.fileName.contains(query, ignoreCase = true)
            }
        }
    }
    val sortedAlbumSongs = remember(filteredAlbumSongs, sortMode) { filteredAlbumSongs.sortedForAlbumDetail(sortMode) }
    val sortedAlbumSongIndexById = remember(sortedAlbumSongs) {
        buildMap {
            sortedAlbumSongs.forEachIndexed { index, song -> put(song.id, index) }
        }
    }
    val albumDuration = remember(albumSongs) { albumSongs.sumOf { it.duration } }
    val useDiscSections = sortMode == AlbumDetailSongSortMode.Track && sortedAlbumSongs.any { it.discNumber > 0 }
    val discGroups = remember(sortedAlbumSongs, sortMode) {
        if (sortMode == AlbumDetailSongSortMode.Track) {
            sortedAlbumSongs.groupForDiscSections()
        } else {
            emptyList()
        }
    }
    val albumArtUri = mainViewModel.getAlbumArtUri(album?.artAlbumId ?: albumSongs.firstOrNull()?.albumId ?: 0L)
    val albumPreviewModel by produceState<Any?>(
        initialValue = albumArtUri,
        albumSongs.firstOrNull()?.let { listOf(it.playlistIdentityKey(), it.dateModified, it.fileSize).joinToString("|") }
    ) {
        value = withContext(Dispatchers.IO) {
            albumSongs.firstOrNull()?.let(mainViewModel::getOriginalCoverModel) ?: albumArtUri
        }
    }
    var coverPreviewVisible by remember(albumPreviewModel) { mutableStateOf(false) }
    val neteaseAlbumUrl by produceState<String?>(initialValue = null, albumId, albumSongs) {
        value = mainViewModel.getNeteaseAlbumUrlForAlbum(albumId)
    }
    val albumCopyright by produceState<String>(initialValue = "", albumId, albumSongs) {
        value = withContext(Dispatchers.IO) {
            albumSongs
                .asSequence()
                .map { mainViewModel.getSongTagInfo(it).copyright }
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(3)
                .joinToString("\n")
        }
    }
    val albumPublisher by produceState<String>(initialValue = "", albumId, albumSongs) {
        value = withContext(Dispatchers.IO) {
            albumSongs
                .asSequence()
                .flatMap { song ->
                    mainViewModel.getFullAudioTagInfo(song)
                        ?.customTags
                        ?.albumPublisherValues()
                        .orEmpty()
                        .asSequence()
                }
                .distinctBy { it.lowercase(Locale.ROOT) }
                .take(3)
                .joinToString("\n")
        }
    }
    val albumReleaseDate by produceState<String?>(initialValue = null, albumId, albumSongs, album?.year) {
        value = withContext(Dispatchers.IO) {
            albumSongs
                .asSequence()
                .mapNotNull { song -> mainViewModel.getFullAudioTagInfo(song)?.recordedDate() }
                .firstOrNull()
                ?: album?.year?.takeIf { it.isNotBlank() }
        }
    }
    val albumRecordedYear = remember(albumReleaseDate) { albumReleaseDate?.extractReleaseYear() }
    val albumGenres = remember(albumSongs) {
        albumSongs
            .flatMap { splitGenreNames(it.genre) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val participatingArtists = remember(albumSongs) {
        albumSongs
            .flatMap { splitArtistNames(it.artist) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val participatingComposers = remember(albumSongs) {
        albumSongs
            .flatMap { splitArtistNames(it.composer) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val participatingArrangers = remember(albumSongs) {
        albumSongs
            .flatMap { splitArtistNames(it.arranger) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val participatingLyricists = remember(albumSongs) {
        albumSongs
            .flatMap { splitArtistNames(it.lyricist) }
            .distinctBy { it.lowercase(Locale.ROOT) }
    }
    val yearDisplayItem = remember(albumRecordedYear, albumSongs) {
        albumRecordedYear?.let { year ->
            buildAlbumMetadataDisplayItem(
                name = year,
                songs = mainViewModel.getSongsForMetadataCategory("year", year),
                mainViewModel = mainViewModel,
                fallbackSong = albumSongs.firstOrNull(),
                categoryType = "year"
            )
        }
    }
    val genreDisplayItems = remember(albumGenres, albumSongs) {
        albumGenres.map { genre ->
            buildAlbumMetadataDisplayItem(
                name = genre,
                songs = mainViewModel.getSongsForMetadataCategory("genre", genre),
                mainViewModel = mainViewModel,
                fallbackSong = albumSongs.firstOrNull(),
                categoryType = "genre"
            )
        }
    }
    val artistDisplayItems = remember(participatingArtists, albumSongs, librarySongs, showAlbumArtists) {
        participatingArtists.map { artist ->
            // The artist section on an album page describes the tracks actually
            // performed by this artist. Album-artist participation must not
            // inflate the song count shown for the current album.
            val artistSongs = albumSongs.filter { it.artist.matchesArtistName(artist) }
            val artistAlbums = mainViewModel.getParticipatedAlbumsForArtist(artist) +
                if (showAlbumArtists) mainViewModel.getReleaseAlbumsForArtist(artist) else emptyList()
            buildAlbumMetadataDisplayItem(
                name = artist,
                songs = artistSongs,
                mainViewModel = mainViewModel,
                fallbackSong = albumSongs.firstOrNull(),
                albumCountOverride = artistAlbums.distinctBy { it.id }.size,
                artistCoverName = artist,
                // Counts stay scoped to this album's participating tracks, but the artist image
                // must use the library-wide #266 priority chain so album pages do not pick a
                // collaboration cover merely because it appears first on this album.
                artistCoverSong = selectArtistCoverSong(librarySongs, artist)
            )
        }
    }
    val composerDisplayItems = remember(participatingComposers, albumSongs, librarySongs) {
        participatingComposers.map { composer ->
            buildAlbumMetadataDisplayItem(
                name = composer,
                songs = mainViewModel.getSongsForMetadataCategory("composer", composer),
                mainViewModel = mainViewModel,
                fallbackSong = albumSongs.firstOrNull(),
                categoryType = "composer",
                coverCandidates = librarySongs
            )
        }
    }
    val arrangerDisplayItems = remember(participatingArrangers, albumSongs, librarySongs) {
        participatingArrangers.map { arranger ->
            buildAlbumMetadataDisplayItem(
                name = arranger,
                songs = mainViewModel.getSongsForMetadataCategory("arranger", arranger),
                mainViewModel = mainViewModel,
                fallbackSong = albumSongs.firstOrNull(),
                categoryType = "arranger",
                coverCandidates = librarySongs
            )
        }
    }
    val lyricistDisplayItems = remember(participatingLyricists, albumSongs, librarySongs) {
        participatingLyricists.map { lyricist ->
            buildAlbumMetadataDisplayItem(
                name = lyricist,
                songs = mainViewModel.getSongsForMetadataCategory("lyricist", lyricist),
                mainViewModel = mainViewModel,
                fallbackSong = albumSongs.firstOrNull(),
                categoryType = "lyricist",
                coverCandidates = librarySongs
            )
        }
    }
    val listState = rememberLazyListState()
    RestoreListScrollAfterSearch(
        searchExpanded = searchExpanded,
        query = searchQuery,
        listState = listState
    )
    val albumSongHeaderCount = 2
    val showSongSideIndex = !selection.selectionMode &&
        sortMode in setOf(
            AlbumDetailSongSortMode.Title,
            AlbumDetailSongSortMode.TitleDesc,
            AlbumDetailSongSortMode.FileName,
            AlbumDetailSongSortMode.FileNameDesc
        ) &&
        sortedAlbumSongs.size > 30
    val songFastIndexData = remember(showSongSideIndex, sortedAlbumSongs) {
        if (!showSongSideIndex) {
            emptyList()
        } else {
            sortedAlbumSongs
                .mapIndexed { index, song ->
                    val indexText = if (
                        sortMode == AlbumDetailSongSortMode.FileName ||
                            sortMode == AlbumDetailSongSortMode.FileNameDesc
                    ) {
                        song.fileName.ifBlank { song.path.substringAfterLast('/') }
                    } else {
                        song.title
                    }
                    indexText.toFastIndexSection() to (index + albumSongHeaderCount)
                }
                .distinctBy { it.first }
        }
    }
    val showScrollIndicator = sortedAlbumSongs.size > 30 && !showSongSideIndex
    var scrollToTopRequest by remember { mutableStateOf(0) }
    fun selectedSongs(): List<Song> = sortedAlbumSongs.filter { it.id in selection.selectedIds }
    val selectedSongsForDrag = remember(selection.selectedIds, sortedAlbumSongs) {
        selectedSongs()
    }
    val selectedVisibleCount = remember(selection.selectedIds, sortedAlbumSongs) {
        sortedAlbumSongs.count { it.id in selection.selectedIds }
    }
    val rangeSelectionAvailable = remember(sortedAlbumSongIndexById, selection.selectedIds, selection.rangeAnchorId, selection.rangeTargetId) {
        selection.isRangeSelectionAvailable(sortedAlbumSongIndexById)
    }

    val currentSongItemIndex = remember(sortedAlbumSongs, discGroups, useDiscSections, currentSong?.id, selection.selectionMode) {
        if (selection.selectionMode) return@remember -1
        val songIndex = currentSong?.id?.let { sortedAlbumSongIndexById[it] } ?: -1
        if (songIndex < 0) {
            -1
        } else {
            val discHeaderCount = if (useDiscSections) {
                val song = sortedAlbumSongs[songIndex]
                discGroups.count { group ->
                    group.discNumber <= song.safeDiscNumber()
                }
            } else {
                0
            }
            2 + discHeaderCount + songIndex
        }
    }

    if (showIntroduction) {
        AlbumIntroductionScreen(
            album = album,
            songs = albumSongs,
            coverModel = albumPreviewModel,
            releaseDate = albumReleaseDate,
            onBack = { showIntroduction = false }
        )
        return
    }

    // Keep the explicit album-list restoration path for every system-back route.  Previously
    // this handler was disabled for the normal detail state, so gesture/system back bypassed
    // `onBack` and Navigation popped directly without restoring the album-list anchor.
    BackHandler {
        when {
            selection.selectionMode -> selection.finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
            else -> onBack()
        }
    }

    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    LaunchedEffect(selection.selectionMode, sortedAlbumSongs) {
        if (!selection.selectionMode) return@LaunchedEffect
        val visibleIds = sortedAlbumSongs.mapTo(mutableSetOf()) { it.id }
        selection.selectedIds = selection.selectedIds.filterTo(mutableSetOf()) { it in visibleIds }
        if (selection.rangeAnchorId !in visibleIds) selection.rangeAnchorId = selection.selectedIds.firstOrNull()
        if (selection.rangeTargetId !in visibleIds) selection.rangeTargetId = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
    ) {
        if (album == null && albumSongs.isEmpty() && !libraryCacheLoaded) {
            // Do not render AlbumHeader's "unknown album" fallback while a cold-start cache is
            // still restoring. Besides being misleading, it made a recoverable service crash
            // look like it had erased the album the user navigated to.
            EllaCenteredLoadingIndicator()
        } else LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp)
        ) {
            item {
                AlbumHeader(
                    album = album,
                    releaseDate = albumReleaseDate,
                    albumCoverModel = albumPreviewModel,
                    songCount = sortedAlbumSongs.size,
                    duration = albumDuration,
                    hasNeteaseAlbum = !neteaseAlbumUrl.isNullOrBlank(),
                    onNeteaseAlbumClick = { openUrl(context, neteaseAlbumUrl.orEmpty()) },
                    onAlbumArtistClick = {
                        val albumArtist = album?.albumArtist?.takeIf { it.isNotBlank() }
                            ?: return@AlbumHeader
                        val artists = splitArtistNames(albumArtist).ifEmpty { listOf(albumArtist.trim()) }
                        if (artists.size == 1) {
                            onNavigateToArtist(artists.first())
                        } else {
                            albumArtistChoices = artists
                        }
                    },
                    onReleaseYearClick = {
                        album?.yearInt?.takeIf { it > 0 }?.let { year ->
                            onNavigateToMetadataCategory("year", year.toString())
                        }
                    },
                    onIntroductionClick = { showIntroduction = true },
                    onCoverClick = { coverPreviewVisible = true },
                    onPlayAll = {
                        if (sortedAlbumSongs.isNotEmpty()) {
                            playerViewModel.setPlaylist(sortedAlbumSongs, 0)
                            if (openPlayerOnPlay) onNavigateToPlayer()
                        }
                    }
                )
            }

            item {
                com.ella.music.ui.components.SortSummaryHeader(
                    text = stringResource(
                        R.string.album_sort_summary,
                        sortedAlbumSongs.size,
                        albumDuration.formatPlaybackDuration(),
                        com.ella.music.ui.components.sortLabel(sortMode.labelRes, sortMode.isDescending())
                    ),
                    leadingContent = {
                        ShuffleAllSummaryButton(
                            visible = !selection.selectionMode && sortedAlbumSongs.isNotEmpty(),
                            onClick = {
                                playerViewModel.setPlaylist(sortedAlbumSongs.shuffled(), 0)
                                if (openPlayerOnPlay) onNavigateToPlayer()
                            }
                        )
                    }
                )
            }

            item {
                com.ella.music.ui.components.ContinuePlaybackRow(
                    songs = sortedAlbumSongs,
                    playbackStats = playbackStats,
                    currentSong = currentSong,
                    onContinue = { index ->
                        playerViewModel.setPlaylist(sortedAlbumSongs, index)
                        if (openPlayerOnPlay) onNavigateToPlayer()
                    }
                )
            }

            if (useDiscSections) {
                discGroups.forEach { group ->
                    item(key = "disc-${group.discNumber}") {
                        DiscHeader(group)
                    }
                    items(
                        items = group.songs,
                        key = { song -> song.id }
                    ) { song ->
                        val index = sortedAlbumSongIndexById[song.id] ?: -1
                        AlbumSongRow(
                            song = song,
                            index = index,
                            sortedAlbumSongs = sortedAlbumSongs,
                            currentSongId = currentSong?.id,
                            isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                            showTrackNumber = true,
                            mainViewModel = mainViewModel,
                            ratingRevision = ratingRevision,
                            playerViewModel = playerViewModel,
                            openPlayerOnPlay = openPlayerOnPlay,
                            onNavigateToPlayer = onNavigateToPlayer,
                            selectionMode = selection.selectionMode,
                            selected = song.id in selection.selectedIds,
                            dragSelectedSongs = selectedSongsForDrag,
                            onLongClick = {
                                selection.selectionMode = true
                                selection.selectedIds = selection.selectedIds + song.id
                                selection.updateRangeAnchorsForManualSelection(song.id, selectedNow = true)
                            },
                            onSelectionClick = { selection.toggleSelection(song.id) },
                            onMore = { actionSong = song },
                            showPlayNextInLists = showPlayNextInLists,
                            titleOverride = if (
                                sortMode == AlbumDetailSongSortMode.FileName ||
                                    sortMode == AlbumDetailSongSortMode.FileNameDesc
                            ) {
                                song.fileName.ifBlank { song.path.substringAfterLast('/') }
                            } else null
                        )
                    }
                }
            } else {
                itemsIndexed(sortedAlbumSongs) { index, song ->
                    AlbumSongRow(
                        song = song,
                        index = index,
                        sortedAlbumSongs = sortedAlbumSongs,
                        currentSongId = currentSong?.id,
                        isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                        showTrackNumber = sortMode == AlbumDetailSongSortMode.Track,
                        mainViewModel = mainViewModel,
                        ratingRevision = ratingRevision,
                        playerViewModel = playerViewModel,
                        openPlayerOnPlay = openPlayerOnPlay,
                        onNavigateToPlayer = onNavigateToPlayer,
                        selectionMode = selection.selectionMode,
                        selected = song.id in selection.selectedIds,
                        dragSelectedSongs = selectedSongsForDrag,
                        onLongClick = {
                            selection.selectionMode = true
                            selection.selectedIds = selection.selectedIds + song.id
                            selection.updateRangeAnchorsForManualSelection(song.id, selectedNow = true)
                        },
                        onSelectionClick = { selection.toggleSelection(song.id) },
                        onMore = { actionSong = song },
                        showPlayNextInLists = showPlayNextInLists,
                        titleOverride = if (
                            sortMode == AlbumDetailSongSortMode.FileName ||
                                sortMode == AlbumDetailSongSortMode.FileNameDesc
                        ) {
                            song.fileName.ifBlank { song.path.substringAfterLast('/') }
                        } else null
                    )
                }
            }
            if (
                albumCopyright.isNotBlank() ||
                albumPublisher.isNotBlank() ||
                albumGenres.isNotEmpty() ||
                participatingArtists.isNotEmpty() ||
                participatingComposers.isNotEmpty() ||
                participatingArrangers.isNotEmpty() ||
                participatingLyricists.isNotEmpty() ||
                albumRecordedYear != null
            ) {
                item(key = "album-extra-info") {
                    AlbumCopyrightFooter(
                        copyright = albumCopyright,
                        publisher = albumPublisher,
                        genres = genreDisplayItems,
                        artists = artistDisplayItems,
                        composers = composerDisplayItems,
                        arrangers = arrangerDisplayItems,
                        lyricists = lyricistDisplayItems,
                        year = yearDisplayItem,
                        mainViewModel = mainViewModel,
                        artistCoverFolderUri = artistCoverFolderUri,
                        onGenreClick = { genre -> onNavigateToMetadataCategory("genre", genre) },
                        onArtistClick = onNavigateToArtist,
                        onComposerClick = { composer -> onNavigateToMetadataCategory("composer", composer) },
                        onArrangerClick = { arranger -> onNavigateToMetadataCategory("arranger", arranger) },
                        onLyricistClick = { lyricist -> onNavigateToMetadataCategory("lyricist", lyricist) },
                        onYearClick = { year -> onNavigateToMetadataCategory("year", year) }
                    )
                }
            }
        }

        if (showSongSideIndex && songFastIndexData.isNotEmpty()) {
            FastIndexBar(
                letters = songFastIndexData.map { it.first },
                reverse = sortMode == AlbumDetailSongSortMode.TitleDesc ||
                    sortMode == AlbumDetailSongSortMode.FileNameDesc,
                onLetterClick = { letter ->
                    songFastIndexData.firstOrNull { it.first == letter }?.second?.let { itemIndex ->
                        scope.launch { listState.scrollToItem(itemIndex) }
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 80.dp, bottom = 118.dp)
            )
        } else if (showScrollIndicator) {
            LazyListScrollIndicator(
                state = listState,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(top = 80.dp, bottom = 118.dp)
            )
        }

        EllaSmallTopAppBar(
            title = if (selection.selectionMode) {
                stringResource(R.string.library_selected_fraction, selection.selectedIds.size, sortedAlbumSongs.size)
            } else {
                ""
            },
            color = Color.Transparent,
            titleStartPadding = 64.dp,
            titleEndPadding = 160.dp,
            modifier = Modifier.align(Alignment.TopCenter),
            onDoubleTapTitle = { scrollToTopRequest++ },
            navigationIcon = {
                IconButton(onClick = { if (selection.selectionMode) selection.finishSelectionMode() else onBack() }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(26.dp)
                    )
                }
            },
            actions = {
                if (selection.selectionMode) {
                    IconButton(onClick = {
                        val selected = selectedSongs()
                        if (selected.isNotEmpty()) playlistPickerSongs = selected
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_playlist_add),
                            contentDescription = stringResource(R.string.player_add_to_playlist),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                    IconButton(onClick = {
                        val selected = selectedSongs()
                        if (selected.isNotEmpty()) {
                            playerViewModel.playNext(selected)
                            selection.finishSelectionMode()
                        }
                    }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_play_next_add),
                            contentDescription = stringResource(R.string.song_more_play_next),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(27.dp)
                        )
                    }
                    IconButton(onClick = {
                        val selected = selectedSongs()
                        if (selected.isNotEmpty()) pendingDeleteSongs = selected
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
                    SortDropdownMenu(
                    items = directionalSortModeDropdownItems(
                        fields = listOf(
                            DirectionalSortModeField(
                                text = stringResource(R.string.album_sort_track),
                                ascendingMode = AlbumDetailSongSortMode.Track,
                                descendingMode = AlbumDetailSongSortMode.TrackDesc
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_song_sort_title),
                                ascendingMode = AlbumDetailSongSortMode.Title,
                                descendingMode = AlbumDetailSongSortMode.TitleDesc
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_song_sort_file_name),
                                ascendingMode = AlbumDetailSongSortMode.FileName,
                                descendingMode = AlbumDetailSongSortMode.FileNameDesc
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_song_sort_duration),
                                ascendingMode = AlbumDetailSongSortMode.DurationAsc,
                                descendingMode = AlbumDetailSongSortMode.Duration
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_song_sort_date_added),
                                ascendingMode = AlbumDetailSongSortMode.DateAddedAsc,
                                descendingMode = AlbumDetailSongSortMode.DateAdded
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_song_sort_date_modified),
                                ascendingMode = AlbumDetailSongSortMode.DateModifiedAsc,
                                descendingMode = AlbumDetailSongSortMode.DateModified
                            )
                        ),
                        selectedMode = sortMode,
                        onSelect = { mode ->
                            LibrarySortUiState.albumDetailSongSortIndex = mode.ordinal
                            scope.launch { mainViewModel.settingsManager.setAlbumDetailSongSortIndex(mode.ordinal) }
                            scrollToTopRequest++
                        }
                    )
                    )
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
                placeholder = stringResource(R.string.folder_detail_search_placeholder),
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
            endPadding = 160.dp
        )

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
                    .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f), androidx.compose.foundation.shape.RoundedCornerShape(18.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                AlbumDetailSongSortMode.entries.forEach { mode ->
                    Text(
                        text = stringResource(mode.labelRes),
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.albumDetailSongSortIndex = mode.ordinal
                                scope.launch { mainViewModel.settingsManager.setAlbumDetailSongSortIndex(mode.ordinal) }
                                scrollToTopRequest++
                                sortExpanded = false
                            }
                            .padding(vertical = 10.dp)
                    )
                }
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
            visible = selection.selectionMode && sortedAlbumSongs.isNotEmpty(),
            rangeEnabled = rangeSelectionAvailable,
            allSelected = sortedAlbumSongs.isNotEmpty() && selectedVisibleCount == sortedAlbumSongs.size,
            onRangeSelect = { selection.applyRangeSelection(sortedAlbumSongs.map { it.id }, sortedAlbumSongIndexById) },
            onSelectAll = { selection.toggleSelectAll(sortedAlbumSongs.map { it.id }) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
        )

        SongMoreActionHost(
            actionSong = actionSong,
            mainViewModel = mainViewModel,
            playerViewModel = playerViewModel,
            onDismissAction = { actionSong = null },
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist
        )

        if (albumArtistChoices.isNotEmpty()) {
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.common_select_artist),
                onDismissRequest = { albumArtistChoices = emptyList() }
            ) {
                ArtistPickerSheet(
                    artists = albumArtistChoices,
                    mainViewModel = mainViewModel,
                    onArtistSelected = { artist ->
                        albumArtistChoices = emptyList()
                        onNavigateToArtist(artist)
                    },
                    onDismiss = { albumArtistChoices = emptyList() }
                )
            }
        }

        playlistPickerSongs?.let { songsToAdd ->
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = stringResource(R.string.player_add_to_playlist),
                onDismissRequest = { playlistPickerSongs = null }
            ) {
                AddToPlaylistSheet(
                    playlists = playlists.sortedWith(
                        compareByDescending<UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }
                            .thenByDescending { it.createdAt }
                    ),
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
                        playlistPickerSongs = null
                        selection.finishSelectionMode()
                    }
                )
            }
        }

        createPlaylistSongs?.let { songsToAdd ->
            CreatePlaylistAndAddSheet(
                onDismiss = { createPlaylistSongs = null },
                onCreate = { playlistName ->
                    mainViewModel.createPlaylistOrShowDuplicateToast(context, playlistName) { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                        createPlaylistSongs = null
                        selection.finishSelectionMode()
                    }
                }
            )
        }
        ConfirmDangerDialog(
            show = pendingDeleteSongs.isNotEmpty(),
            title = stringResource(R.string.song_more_delete_song_title),
            message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
            confirmText = stringResource(R.string.song_more_delete_permanently),
            onDismiss = { pendingDeleteSongs = emptyList() },
            onConfirm = {
                val songsToDelete = pendingDeleteSongs
                pendingDeleteSongs = emptyList()
                requestDeleteSongs(songsToDelete)
                selection.finishSelectionMode()
            }
        )
        if (coverPreviewVisible && albumPreviewModel != null) {
            CoverPreviewDialog(
                model = albumPreviewModel!!,
                title = album?.name.orEmpty(),
                onDismiss = { coverPreviewVisible = false }
            )
        }
    }
}

private fun com.ella.music.data.metadata.AudioTagInfo.recordedDate(): String? =
    (customTags.firstRecordedDateValue() ?: year?.trim())
        ?.normalizeAlbumReleaseDate()

private fun String.extractReleaseYear(): String? = Regex("""\d{4}""").find(this)?.value

private fun String.normalizeAlbumReleaseDate(): String {
    val value = trim()
    val match = Regex("""(\d{4})(?:[-./](\d{1,2})(?:[-./](\d{1,2}))?)?""").find(value)
        ?: return value
    val year = match.groupValues[1]
    val month = match.groupValues.getOrNull(2).orEmpty()
    val day = match.groupValues.getOrNull(3).orEmpty()
    return buildString {
        append(year)
        if (month.isNotBlank()) append("-").append(month.padStart(2, '0'))
        if (day.isNotBlank()) append("-").append(day.padStart(2, '0'))
    }
}

private fun Map<String, List<String>>.firstRecordedDateValue(): String? {
    val targets = setOf(
        "RECORDED DATE",
        "RECORDEDDATE",
        "RECORDING DATE",
        "RECORDINGDATE",
        "DATE RECORDED",
        "RECORDEDTIME",
        "DATE",
        "TDRC"
    ).mapTo(mutableSetOf()) { it.normalizedTagName() }
    return entries
        .firstOrNull { (key, values) ->
            key.normalizedTagName() in targets && values.any { it.isNotBlank() }
        }
        ?.value
        ?.firstOrNull { it.isNotBlank() }
        ?.trim()
}

private fun String.normalizedTagName(): String =
    uppercase(Locale.ROOT).filter { it.isLetterOrDigit() }

private fun buildAlbumMetadataDisplayItem(
    name: String,
    songs: List<Song>,
    mainViewModel: MainViewModel,
    fallbackSong: Song?,
    categoryType: String? = null,
    coverCandidates: List<Song> = songs,
    albumCountOverride: Int? = null,
    artistCoverName: String? = null,
    artistCoverSong: Song? = null
): AlbumMetadataDisplayItem {
    val representativeSong = categoryType?.let { selectMetadataCategoryCoverSong(coverCandidates, it, name) }
        ?: songs.firstOrNull()
        ?: fallbackSong
    return AlbumMetadataDisplayItem(
        name = name,
        songCount = songs.size,
        duration = songs.sumOf { it.duration },
        albumCount = albumCountOverride ?: songs.map { it.albumIdentityId() }.distinct().size,
        coverModel = representativeSong?.coverUrl?.takeIf(String::isNotBlank)
            ?: representativeSong?.albumId?.let(mainViewModel::getAlbumArtUri),
        artistCoverName = artistCoverName,
        artistCoverSong = artistCoverSong
    )
}
