package com.ella.music.ui.search

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.matchesArtistName
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.folder.toFolderSettingList
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun LibrarySearchScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    initialFilterType: String? = null,
    initialQuery: String? = null,
    autoFocusSearch: Boolean = false,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit,
    onNavigateToPlaylist: (String) -> Unit,
    onNavigateToMetadataCategory: (String, String) -> Unit,
    onNavigateToPlayer: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val settingsManager = mainViewModel.settingsManager
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    val currentSong by playerViewModel.currentSong.collectAsState()
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)
    val lyricSourceMode by settingsManager.lyricSourceMode.collectAsState(initial = SettingsManager.LYRIC_SOURCE_AUTO)
    val showPlayNextInLists by settingsManager.showPlayNextInLists.collectAsState(initial = false)
    val excludeSearchResultsFromPlaylist by settingsManager.excludeSearchResultsFromPlaylist.collectAsState(initial = false)
    val showAlbumArtists by settingsManager.showAlbumArtists.collectAsState(initial = true)
    val artistCoverFolderUri by settingsManager.artistCoverFolderUri.collectAsState(initial = "")
    val fullTagSearchEnabled by settingsManager.fullTagSearchEnabled.collectAsState(initial = true)
    val songRatingDisplayMode by settingsManager.songRatingDisplayMode.collectAsState(
        initial = SettingsManager.SONG_RATING_DISPLAY_STAR_NUMBER
    )
    val searchAllCategoryTypes by settingsManager.searchAllCategoryTypes.collectAsState(initial = SettingsManager.SEARCH_ALL_CATEGORY_TYPES)
    val searchAllSongMatchTypes by settingsManager.searchAllSongMatchTypes.collectAsState(
        initial = SettingsManager.SEARCH_ALL_SONG_MATCH_TYPES
    )
    val scanExcludeFolders by settingsManager.scanExcludeFolders.collectAsState(initial = "")
    val blockedFolders = remember(scanExcludeFolders) { scanExcludeFolders.toFolderSettingList() }
    var query by rememberSaveable(initialQuery) { mutableStateOf(initialQuery.orEmpty()) }
    var filter by rememberSaveable(initialFilterType, stateSaver = SearchFilterSaver) {
        mutableStateOf(SearchFilter.fromRouteType(initialFilterType))
    }
    var duplicatesOnly by remember { mutableStateOf(false) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var actionTarget by remember { mutableStateOf<SearchActionTarget?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var history by remember { mutableStateOf(loadSearchHistory(context)) }
    var showClearHistoryConfirm by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedSongKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var rangeAnchorSongKey by remember { mutableStateOf<String?>(null) }
    var rangeTargetSongKey by remember { mutableStateOf<String?>(null) }

    val trimmedQuery = query.trim()
    val songSelectionAvailable = filter in listOf(SearchFilter.Songs, SearchFilter.Lyrics)
    val duplicateSongs by produceState(initialValue = emptyList<Song>(), songs) {
        value = withContext(Dispatchers.Default) { songs.duplicateTitleAlbumSongs() }
    }
    val duplicatesOnlyActive = duplicatesOnly && filter.supportsDuplicateFilter
    val songSearchSource = remember(songs, duplicateSongs, duplicatesOnlyActive) {
        if (duplicatesOnlyActive) duplicateSongs else songs
    }
    val cachedSongResults = remember(context, songs, trimmedQuery, filter, duplicatesOnlyActive, fullTagSearchEnabled) {
        if (duplicatesOnlyActive || !fullTagSearchEnabled) {
            emptyList()
        } else {
            loadCachedSongSearchResults(context, songs, trimmedQuery, filter)
        }
    }
    val songResults by produceState(
        initialValue = cachedSongResults,
        songSearchSource,
        trimmedQuery,
        filter,
        duplicateSongs,
        duplicatesOnlyActive,
        cachedSongResults,
        lyricSourceMode,
        fullTagSearchEnabled
    ) {
        val initialResults = cachedSongResults
        value = initialResults
        if (!filter.acceptsSongResults) {
            value = emptyList()
            return@produceState
        }
        if (trimmedQuery.isBlank()) {
            value = if (duplicatesOnlyActive) {
                withContext(Dispatchers.Default) {
                    buildDirectSongSearchResults(songSearchSource, trimmedQuery, filter)
                }
            } else {
                emptyList()
            }
            return@produceState
        }
        if (!fullTagSearchEnabled && filter == SearchFilter.Lyrics) {
            return@produceState
        }
        if (filter == SearchFilter.Lyrics) {
            val current = mutableListOf<SongSearchResult>()
            for (song in songSearchSource) {
                val snippet = mainViewModel.repository
                    .getLyrics(song, lyricSourceMode)
                    .firstMatchingLyricSnippet(trimmedQuery)
                    ?: continue
                current += SongSearchResult(song = song, lyricSnippet = snippet)
                value = current.toList()
            }
            return@produceState
        }
        if (duplicatesOnlyActive) {
            value = withContext(Dispatchers.Default) {
                buildDirectSongSearchResults(songSearchSource, trimmedQuery, filter)
            }
            return@produceState
        }
        val current = if (initialResults.isNotEmpty()) {
            initialResults.map { result ->
                if (result.lyricSnippet == null && result.matches.isEmpty()) {
                    val tagInfo = if (fullTagSearchEnabled) mainViewModel.getSongTagInfo(result.song) else null
                    result.copy(
                        matches = result.song.directSearchMatches(
                            trimmedQuery,
                            tagInfo = tagInfo,
                            includeSnapshotTag = fullTagSearchEnabled
                        )
                    )
                } else {
                    result
                }
            }.toMutableList()
        } else {
            withContext(Dispatchers.Default) {
                buildDirectSongSearchResults(songSearchSource, trimmedQuery, filter)
            }.toMutableList()
        }
        if (current != initialResults) value = current.toList()
        val seenKeys = current.map { it.song.searchIdentityKey() }.toMutableSet()
        val remainingSongs = songSearchSource.filter { it.searchIdentityKey() !in seenKeys }
        val snapshotMatches = mainViewModel
            .filterSongsBySearchSnapshot(remainingSongs, trimmedQuery)
            .asSequence()
            .filter { it.searchIdentityKey() !in seenKeys }
            .toList()
        snapshotMatches.forEach { song ->
            val tagInfo = if (fullTagSearchEnabled) mainViewModel.getSongTagInfo(song) else null
            current += SongSearchResult(
                song = song,
                matches = song.directSearchMatches(
                    trimmedQuery,
                    tagInfo = tagInfo,
                    includeSnapshotTag = fullTagSearchEnabled
                )
            )
            seenKeys += song.searchIdentityKey()
        }
        if (snapshotMatches.isNotEmpty()) value = current.toList()
        if (!fullTagSearchEnabled) {
            return@produceState
        }
        for (song in remainingSongs) {
            if (song.searchIdentityKey() in seenKeys) continue
            val snippet = mainViewModel.repository
                .getLyrics(song, lyricSourceMode)
                .firstMatchingLyricSnippet(trimmedQuery)
                ?: continue
            current += SongSearchResult(song = song, lyricSnippet = snippet)
            seenKeys += song.searchIdentityKey()
            value = current.toList()
        }
        saveCachedSongSearchResults(context, trimmedQuery, filter, current)
    }

    val requestedCategoryTypes = remember(filter, searchAllCategoryTypes) {
        when (filter) {
            SearchFilter.All -> searchAllCategoryTypes.sorted()
            SearchFilter.Folders -> listOf("folder")
            SearchFilter.Composers -> listOf("composer")
            SearchFilter.Arrangers -> listOf("arranger")
            SearchFilter.Lyricists -> listOf("lyricist")
            SearchFilter.Genres -> listOf("genre")
            SearchFilter.Years -> listOf("year")
            else -> emptyList()
        }
    }
    val facetCacheKey = remember(
        trimmedQuery,
        filter,
        duplicatesOnlyActive,
        showAlbumArtists,
        requestedCategoryTypes,
        songs,
        albums,
        playlists
    ) {
        librarySearchFacetCacheKey(
            query = trimmedQuery,
            filter = filter,
            duplicatesOnlyActive = duplicatesOnlyActive,
            showAlbumArtists = showAlbumArtists,
            categoryTypes = requestedCategoryTypes,
            songs = songs,
            albums = albums,
            playlists = playlists
        )
    }
    val facetResults by produceState(
        initialValue = loadCachedLibrarySearchFacetResults(facetCacheKey) ?: LibrarySearchFacetResults(),
        facetCacheKey,
        trimmedQuery,
        filter,
        duplicatesOnlyActive,
        showAlbumArtists,
        requestedCategoryTypes,
        songs,
        albums,
        playlists
    ) {
        if (duplicatesOnlyActive || trimmedQuery.isBlank()) {
            value = LibrarySearchFacetResults()
            return@produceState
        }
        loadCachedLibrarySearchFacetResults(facetCacheKey)?.let { cached ->
            value = cached
            return@produceState
        }
        val needsAlbums = filter in listOf(SearchFilter.All, SearchFilter.Albums)
        val needsArtists = filter in listOf(SearchFilter.All, SearchFilter.Artists)
        val needsPlaylists = filter in listOf(SearchFilter.All, SearchFilter.Playlists)
        val result = withContext(Dispatchers.Default) {
            val albumResults = if (needsAlbums) {
                albums.filter { it.matchesLibrarySearch(trimmedQuery) }
            } else {
                emptyList()
            }
            val artistResults = if (needsArtists) {
                songs.asSequence()
                    .flatMap { song ->
                        val names = if (showAlbumArtists) {
                            (com.ella.music.data.splitArtistNames(song.artist) +
                                com.ella.music.data.splitArtistNames(song.albumArtist))
                                .distinctBy { it.tagIdentityKey() }
                        } else {
                            com.ella.music.data.splitArtistNames(song.artist)
                        }
                        names.asSequence()
                            .filter { it.isNotBlank() && it.contains(trimmedQuery, ignoreCase = true) }
                            .map { it to song }
                    }
                    .groupBy { it.first.tagIdentityKey() }
                    .values
                    .map { pairs ->
                        val name = pairs.first().first
                        val participatingSongs = pairs.map { it.second }.distinctBy { it.searchIdentityKey() }
                        // Album artists may appear on a release even when they do not perform a
                        // given track. Match the artist page: the song count only includes tracks
                        // whose *song artist* actually contains this artist.
                        val artistSongs = participatingSongs.filter { song ->
                            song.artist.matchesArtistName(name)
                        }
                        ArtistSearchResult(
                            artist = com.ella.music.data.model.Artist(
                                name = name,
                                songCount = artistSongs.size,
                                albumCount = participatingSongs.map { it.albumIdentityId() }.distinct().size
                            ),
                            representativeSong = artistSongs.firstOrNull() ?: participatingSongs.firstOrNull(),
                            participatedAlbumCount = participatingSongs.map { it.albumIdentityId() }.distinct().size
                        )
                    }
                    .sortedBy { it.artist.name.lowercase() }
            } else {
                emptyList()
            }
            val playlistResults = if (needsPlaylists) {
                playlists.filter { playlist ->
                    playlist.name.contains(trimmedQuery, ignoreCase = true) ||
                        playlist.songs.any { song ->
                            song.title.contains(trimmedQuery, ignoreCase = true) ||
                                song.artist.contains(trimmedQuery, ignoreCase = true) ||
                                song.album.contains(trimmedQuery, ignoreCase = true)
                        }
                }
            } else {
                emptyList()
            }
            val categoryResults = requestedCategoryTypes.associateWith { type ->
                mainViewModel.getMetadataCategoryItems(songs, type)
                    .filter { it.name.contains(trimmedQuery, ignoreCase = true) }
            }
            LibrarySearchFacetResults(
                albums = albumResults,
                artists = artistResults,
                playlists = playlistResults,
                categoriesByType = categoryResults
            )
        }
        saveCachedLibrarySearchFacetResults(facetCacheKey, result)
        value = result
    }
    val albumResults = facetResults.albums
    val artistResults = facetResults.artists
    val playlistResults = facetResults.playlists
    val categoryResultsByType = facetResults.categoriesByType
    val categoryResultsCount = remember(categoryResultsByType) { categoryResultsByType.values.sumOf { it.size } }
    val visibleAlbumCount = if (filter in listOf(SearchFilter.All, SearchFilter.Albums)) albumResults.size else 0
    val visibleArtistCount = if (filter in listOf(SearchFilter.All, SearchFilter.Artists)) artistResults.size else 0
    val visiblePlaylistCount = if (filter in listOf(SearchFilter.All, SearchFilter.Playlists)) playlistResults.size else 0
    val songResultGroups = remember(songResults, filter, searchAllSongMatchTypes) {
        songResults
            .flatMap { it.toSearchGroupEntries(filter, searchAllSongMatchTypes) }
            .groupBy({ it.first }, { it.second })
            .map { it.key to it.value }
    }
    val displayedSongResults = remember(songResultGroups) {
        songResultGroups
            .flatMap { (_, entries) -> entries.map { it.result } }
            .distinctBy { it.song.searchIdentityKey() }
    }
    val visibleResultCount = displayedSongResults.size + visibleAlbumCount + visibleArtistCount + visiblePlaylistCount + categoryResultsCount

    LaunchedEffect(filter, trimmedQuery) {
        selectionMode = false
        selectedSongKeys = emptySet()
        rangeAnchorSongKey = null
        rangeTargetSongKey = null
    }

    val displayedSongIndexByKey = remember(displayedSongResults) {
        buildMap {
            displayedSongResults.forEachIndexed { index, result -> put(result.song.searchIdentityKey(), index) }
        }
    }
    val rangeSelectionAvailable = remember(
        displayedSongIndexByKey,
        selectedSongKeys,
        rangeAnchorSongKey,
        rangeTargetSongKey
    ) {
        val anchor = rangeAnchorSongKey
        val target = rangeTargetSongKey
        anchor != null &&
            target != null &&
            anchor != target &&
            anchor in selectedSongKeys &&
            target in selectedSongKeys &&
            anchor in displayedSongIndexByKey &&
            target in displayedSongIndexByKey
    }

    fun updateRangeAnchorsForManualSelection(songKey: String, selectedNow: Boolean) {
        if (selectedNow) {
            when {
                rangeAnchorSongKey == null -> rangeAnchorSongKey = songKey
                rangeAnchorSongKey == songKey -> Unit
                else -> rangeTargetSongKey = songKey
            }
        } else {
            if (rangeTargetSongKey == songKey) rangeTargetSongKey = null
            if (rangeAnchorSongKey == songKey) {
                rangeAnchorSongKey = rangeTargetSongKey ?: selectedSongKeys.firstOrNull { it != songKey }
                rangeTargetSongKey = null
            }
        }
    }

    fun toggleSongSelection(song: Song) {
        val key = song.searchIdentityKey()
        val selecting = key !in selectedSongKeys
        selectedSongKeys = if (selecting) selectedSongKeys + key else selectedSongKeys - key
        updateRangeAnchorsForManualSelection(key, selecting)
    }

    fun toggleSelectAllSongResults() {
        val allKeys = displayedSongResults.mapTo(mutableSetOf()) { it.song.searchIdentityKey() }
        selectedSongKeys = if (allKeys.isNotEmpty() && allKeys.all { it in selectedSongKeys }) {
            rangeAnchorSongKey = null
            rangeTargetSongKey = null
            emptySet()
        } else {
            rangeAnchorSongKey = displayedSongResults.firstOrNull()?.song?.searchIdentityKey()
            rangeTargetSongKey = displayedSongResults.lastOrNull()?.song?.searchIdentityKey()
            allKeys
        }
    }

    fun applyRangeSelection() {
        val anchor = rangeAnchorSongKey ?: return
        val target = rangeTargetSongKey ?: return
        val anchorIndex = displayedSongIndexByKey[anchor] ?: return
        val targetIndex = displayedSongIndexByKey[target] ?: return
        if (anchorIndex == targetIndex) return
        val bounds = if (anchorIndex < targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
        selectedSongKeys = selectedSongKeys + bounds.map { displayedSongResults[it].song.searchIdentityKey() }
        rangeAnchorSongKey = target
        rangeTargetSongKey = null
    }

    fun selectedSearchSongs(): List<Song> =
        displayedSongResults
            .map { it.song }
            .distinctBy { it.searchIdentityKey() }
            .filter { it.searchIdentityKey() in selectedSongKeys }

    fun selectedOrToast(): List<Song> {
        val selected = selectedSearchSongs()
        if (selected.isEmpty()) {
            Toast.makeText(context, R.string.library_select_songs_first, Toast.LENGTH_SHORT).show()
        }
        return selected
    }

    fun finishSelectionMode() {
        selectionMode = false
        selectedSongKeys = emptySet()
        rangeAnchorSongKey = null
        rangeTargetSongKey = null
    }

    fun commitSearch(text: String = query) {
        val value = text.trim()
        if (value.isBlank()) return
        history = saveSearchHistory(context, value)
    }

    fun songsForActionTarget(target: SearchActionTarget): List<Song> = when (target) {
        is SearchActionTarget.AlbumTarget -> mainViewModel.getSongsForAlbum(target.album.id)
        is SearchActionTarget.ArtistTarget -> mainViewModel.getSongsForArtist(
            artistName = target.artist.name,
            includeAlbumArtist = showAlbumArtists
        )
        is SearchActionTarget.PlaylistTarget -> mainViewModel.playlistSongs(target.playlist)
        is SearchActionTarget.CategoryTarget -> mainViewModel.getSongsForMetadataCategory(target.type, target.item.name)
    }

    fun shortcutRouteForActionTarget(target: SearchActionTarget): String = when (target) {
        is SearchActionTarget.AlbumTarget -> Screen.AlbumDetail.createRoute(target.album.id)
        is SearchActionTarget.ArtistTarget -> Screen.ArtistDetail.createRoute(target.artist.name)
        is SearchActionTarget.PlaylistTarget -> Screen.PlaylistDetail.createRoute(target.playlist.id)
        is SearchActionTarget.CategoryTarget -> Screen.MetadataCategoryDetail.createRoute(target.type, target.item.name)
    }

    fun shortcutIdForActionTarget(target: SearchActionTarget): String = when (target) {
        is SearchActionTarget.AlbumTarget -> "album_${target.album.id}"
        is SearchActionTarget.ArtistTarget -> "artist_${target.artist.name.tagIdentityKey()}"
        is SearchActionTarget.PlaylistTarget -> "playlist_${target.playlist.id}"
        is SearchActionTarget.CategoryTarget -> "category_${target.type}_${target.item.name.tagIdentityKey()}"
    }

    LaunchedEffect(initialQuery) {
        initialQuery?.trim()?.takeIf { it.isNotBlank() }?.let { value ->
            history = saveSearchHistory(context, value)
        }
    }

    BackHandler {
        if (selectionMode) {
            finishSelectionMode()
        } else {
            onBack()
        }
    }

    val resolvedSearchAutoFocus = when {
        autoFocusSearch -> true
        !initialQuery.isNullOrBlank() -> false
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        LibrarySearchTopBar(
            query = query,
            autoFocus = resolvedSearchAutoFocus,
            showBackButton = showBackButton,
            onBack = onBack,
            onQueryChange = { query = it },
            onSearch = { commitSearch() }
        )
        LibrarySearchFilterBar(
            filter = filter,
            trimmedQuery = trimmedQuery,
            duplicatesOnlyActive = duplicatesOnlyActive,
            songResultsCount = displayedSongResults.size,
            albumResultsCount = albumResults.size,
            artistResultsCount = artistResults.size,
            playlistResultsCount = playlistResults.size,
            categoryResultsByType = categoryResultsByType,
            onFilterChange = { item ->
                filter = item
                if (!item.supportsDuplicateFilter) duplicatesOnly = false
            }
        )
        LibrarySearchDuplicateToggle(
            visible = filter.supportsDuplicateFilter,
            duplicatesOnly = duplicatesOnly,
            onToggle = { duplicatesOnly = !duplicatesOnly }
        )
        if (selectionMode) {
            LibrarySearchSelectionToolbar(
                selectedCount = selectedSongKeys.size,
                totalCount = displayedSongResults.size,
                rangeEnabled = rangeSelectionAvailable,
                allSelected = displayedSongResults.isNotEmpty() && displayedSongResults.all { it.song.searchIdentityKey() in selectedSongKeys },
                onRangeSelect = ::applyRangeSelection,
                onSelectAll = ::toggleSelectAllSongResults,
                onPlayNext = {
                    val selected = selectedOrToast()
                    if (selected.isNotEmpty()) {
                        playerViewModel.playNext(selected)
                        Toast.makeText(context, R.string.song_more_added_to_play_next, Toast.LENGTH_SHORT).show()
                        finishSelectionMode()
                    }
                },
                onAddToPlaylist = {
                    val selected = selectedOrToast()
                    if (selected.isNotEmpty()) {
                        playlistPickerSongs = selected
                    }
                },
                onAddToQueue = {
                    val selected = selectedOrToast()
                    if (selected.isNotEmpty()) {
                        playerViewModel.addToPlaylist(selected)
                        Toast.makeText(context, R.string.song_more_added_to_queue, Toast.LENGTH_SHORT).show()
                        finishSelectionMode()
                    }
                },
                onShare = {
                    val selected = selectedOrToast()
                    if (selected.isNotEmpty()) {
                        com.ella.music.ui.components.shareLocalSongs(context, selected)
                    }
                },
                onDelete = {
                    val selected = selectedOrToast()
                    if (selected.isNotEmpty()) pendingDeleteSongs = selected
                },
                onFinishSelection = ::finishSelectionMode
            )
        }
        LibrarySearchResultsPane(
            mainViewModel = mainViewModel,
            playerViewModel = playerViewModel,
            songs = songs,
            libraryCacheLoaded = libraryCacheLoaded,
            currentSong = currentSong,
            showPlayNextInLists = showPlayNextInLists,
            songRatingDisplayMode = songRatingDisplayMode,
            excludeSearchResultsFromPlaylist = excludeSearchResultsFromPlaylist,
            filter = filter,
            trimmedQuery = trimmedQuery,
            duplicatesOnlyActive = duplicatesOnlyActive,
            history = history,
            selectionMode = selectionMode,
            selectedSongKeys = selectedSongKeys,
            songSelectionAvailable = songSelectionAvailable,
            songResults = displayedSongResults,
            songResultGroups = songResultGroups,
            albumResults = albumResults,
            artistResults = artistResults,
            playlistResults = playlistResults,
            categoryResultsByType = categoryResultsByType,
            artistCoverFolderUri = artistCoverFolderUri,
            visibleResultCount = visibleResultCount,
            onSelectHistory = { item ->
                query = item
                filter = SearchFilter.All
                duplicatesOnly = false
                commitSearch(item)
                keyboardController?.hide()
                focusManager.clearFocus()
            },
            onDeleteHistory = { item ->
                history = history - item
                saveSearchHistory(context, history)
            },
            onClearHistoryRequest = { showClearHistoryConfirm = true },
            onToggleSongSelection = ::toggleSongSelection,
            onEnterSongSelection = { song ->
                selectionMode = true
                val songKey = song.searchIdentityKey()
                selectedSongKeys = selectedSongKeys + songKey
                updateRangeAnchorsForManualSelection(songKey, selectedNow = true)
            },
            onSongAction = { actionSong = it },
            onActionTarget = { actionTarget = it },
            onCommitSearch = { commitSearch() },
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist,
            onNavigateToPlaylist = onNavigateToPlaylist,
            onNavigateToMetadataCategory = onNavigateToMetadataCategory,
            onNavigateToPlayer = onNavigateToPlayer
        )
    }

    LibrarySearchAuxiliarySurfaces(
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        settingsManager = settingsManager,
        playlists = playlists,
        blockedFolders = blockedFolders,
        actionSong = actionSong,
        onActionSongChange = { actionSong = it },
        actionTarget = actionTarget,
        onActionTargetChange = { actionTarget = it },
        playlistPickerSongs = playlistPickerSongs,
        onPlaylistPickerSongsChange = { playlistPickerSongs = it },
        createPlaylistSongs = createPlaylistSongs,
        onCreatePlaylistSongsChange = { createPlaylistSongs = it },
        showClearHistoryConfirm = showClearHistoryConfirm,
        onShowClearHistoryConfirmChange = { showClearHistoryConfirm = it },
        onClearHistoryConfirmed = {
            history = emptyList()
            saveSearchHistory(context, emptyList())
        },
        pendingDeleteSongs = pendingDeleteSongs,
        onPendingDeleteSongsChange = { pendingDeleteSongs = it },
        onRequestDeleteSongs = requestDeleteSongs,
        onFinishSelectionMode = ::finishSelectionMode,
        songsForActionTarget = ::songsForActionTarget,
        shortcutIdForActionTarget = ::shortcutIdForActionTarget,
        shortcutRouteForActionTarget = ::shortcutRouteForActionTarget,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist
    )
}
