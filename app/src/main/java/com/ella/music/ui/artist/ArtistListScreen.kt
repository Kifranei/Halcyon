package com.ella.music.ui.artist

import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.folder.musicSortKey

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.LibrarySortUiState
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.DoubleTapScrollOverlay
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.FastIndexBar
import com.ella.music.ui.components.FloatingSelectionControls
import com.ella.music.ui.components.LibraryFloatingControlsBottomPadding
import com.ella.music.ui.components.LibraryFloatingControlsEndPadding
import com.ella.music.ui.components.LazyListScrollIndicator
import com.ella.music.ui.components.RestoreListScrollAfterSearch
import com.ella.music.ui.components.SideIndexListEndPadding
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import com.ella.music.ui.components.EllaSmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Search
import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Sort
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class ArtistListAggregate(
    val artists: List<Artist> = emptyList(),
    val representativeSongsByArtist: Map<String, Song> = emptyMap(),
    val artistDurations: Map<String, Long> = emptyMap(),
    val releaseAlbumCounts: Map<String, Int> = emptyMap()
)

private class ArtistListAccumulator(
    val name: String
) {
    var songCount: Int = 0
    var duration: Long = 0L
    val albumIds: MutableSet<Long> = linkedSetOf()
    var representativeSong: Song? = null
    private var representativeSongPriority: Int = Int.MAX_VALUE

    fun considerCoverCandidate(song: Song) {
        val priority = artistCoverPriority(song, name) ?: return
        if (priority < representativeSongPriority) {
            representativeSong = song
            representativeSongPriority = priority
        }
    }
}

private fun buildArtistListAggregate(
    songs: List<Song>,
    albums: List<Album>,
    includeAlbumArtists: Boolean
): ArtistListAggregate {
    val artistsByKey = linkedMapOf<String, ArtistListAccumulator>()
    val releaseAlbumCounts = mutableMapOf<String, Int>()

    fun accumulatorFor(rawName: String): ArtistListAccumulator {
        val key = rawName.tagIdentityKey()
        return artistsByKey.getOrPut(key) { ArtistListAccumulator(rawName) }
    }

    songs.forEach { song ->
        val albumIdentityId = song.albumIdentityId()
        splitArtistNames(song.artist).forEach { artistName ->
            val accumulator = accumulatorFor(artistName)
            accumulator.songCount += 1
            accumulator.duration += song.duration
            accumulator.albumIds += albumIdentityId
            accumulator.considerCoverCandidate(song)
        }
        if (includeAlbumArtists) {
            splitArtistNames(song.albumArtist).forEach { artistName ->
                val accumulator = accumulatorFor(artistName)
                accumulator.albumIds += albumIdentityId
                accumulator.considerCoverCandidate(song)
            }
        }
    }

    if (includeAlbumArtists) {
        albums.forEach { album ->
            splitArtistNames(album.albumArtist).forEach { artistName ->
                val key = artistName.tagIdentityKey()
                val accumulator = accumulatorFor(artistName)
                if (album.id > 0L) {
                    accumulator.albumIds += album.id
                }
                releaseAlbumCounts[key] = (releaseAlbumCounts[key] ?: 0) + 1
            }
        }
    }

    val artists = artistsByKey.values
        .map { accumulator ->
            Artist(
                name = accumulator.name,
                songCount = accumulator.songCount,
                albumCount = accumulator.albumIds.size
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
    val representativeSongsByArtist = artistsByKey.mapNotNull { (key, accumulator) ->
        accumulator.representativeSong?.let { key to it }
    }.toMap()
    val artistDurations = artistsByKey.mapNotNull { (key, accumulator) ->
        accumulator.duration.takeIf { it > 0L }?.let { key to it }
    }.toMap()

    return ArtistListAggregate(
        artists = artists,
        representativeSongsByArtist = representativeSongsByArtist,
        artistDurations = artistDurations,
        releaseAlbumCounts = releaseAlbumCounts
    )
}

@Composable
fun ArtistListScreen(
    mainViewModel: MainViewModel,
    playerViewModel: com.ella.music.viewmodel.PlayerViewModel,
    showBackButton: Boolean = true,
    onBack: () -> Unit,
    onArtistClick: (String) -> Unit
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val albums by mainViewModel.albums.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val libraryCacheLoaded by mainViewModel.libraryCacheLoaded.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var searchExpanded by remember { mutableStateOf(false) }
    var sortExpanded by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedArtistKeys by remember { mutableStateOf(setOf<String>()) }
    var rangeAnchorArtistKey by remember { mutableStateOf<String?>(null) }
    var rangeTargetArtistKey by remember { mutableStateOf<String?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var artistMenuTarget by remember { mutableStateOf<Artist?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val sortIndex by mainViewModel.settingsManager.artistListSortIndex.collectAsState(initial = LibrarySortUiState.artistListSortIndex)
    val detailSongSortIndex by mainViewModel.settingsManager.artistDetailSongSortIndex.collectAsState(initial = LibrarySortUiState.artistDetailSongSortIndex)
    val detailSongSortMode = ArtistDetailSongSortMode.entries.getOrElse(detailSongSortIndex) { ArtistDetailSongSortMode.Title }
    val showAlbumArtists by mainViewModel.settingsManager.showAlbumArtists.collectAsState(initial = true)
    val artistCoverFolderUri by mainViewModel.settingsManager.artistCoverFolderUri.collectAsState(initial = "")
    val tagIgnoreCase by mainViewModel.settingsManager.tagIgnoreCase.collectAsState(initial = false)
    val pinnedArtistKeys by mainViewModel.settingsManager.pinnedKeysFlow("artist").collectAsState(initial = emptyList())
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)
    val sortMode = ArtistSortMode.entries.getOrElse(sortIndex) { ArtistSortMode.Name }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var scrollToTopRequest by remember { mutableStateOf(0) }
    var listCoversEnabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(220L)
        listCoversEnabled = true
    }

    val aggregate by produceState(
        ArtistListAggregate(),
        songs,
        albums,
        showAlbumArtists,
        tagIgnoreCase
    ) {
        value = withContext(Dispatchers.Default) {
            buildArtistListAggregate(
                songs = songs,
                albums = albums,
                includeAlbumArtists = showAlbumArtists
            )
        }
    }
    val artists = aggregate.artists
    val representativeSongsByArtist = aggregate.representativeSongsByArtist
    val artistDurations = aggregate.artistDurations
    val releaseAlbumCounts = aggregate.releaseAlbumCounts
    val filteredArtists = remember(artists, searchQuery, sortMode, artistDurations, releaseAlbumCounts, pinnedArtistKeys) {
        val filtered = if (searchQuery.isBlank()) {
            artists
        } else {
            artists.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        val sorted = when (sortMode) {
            ArtistSortMode.Name -> filtered.sortedBy { it.name.musicSortKey() }
            ArtistSortMode.NameDesc -> filtered.sortedByDescending { it.name.musicSortKey() }
            ArtistSortMode.SongCount -> filtered.sortedByDescending { it.songCount }
            ArtistSortMode.SongCountAsc -> filtered.sortedBy { it.songCount }
            ArtistSortMode.AlbumCount -> filtered.sortedByDescending { it.albumCount }
            ArtistSortMode.AlbumCountAsc -> filtered.sortedBy { it.albumCount }
            ArtistSortMode.ReleaseAlbumCount -> filtered.sortedByDescending { releaseAlbumCounts[it.name.tagIdentityKey()] ?: 0 }
            ArtistSortMode.ReleaseAlbumCountAsc -> filtered.sortedBy { releaseAlbumCounts[it.name.tagIdentityKey()] ?: 0 }
            ArtistSortMode.Duration -> filtered.sortedByDescending { artistDurations[it.name.tagIdentityKey()] ?: 0L }
            ArtistSortMode.DurationAsc -> filtered.sortedBy { artistDurations[it.name.tagIdentityKey()] ?: 0L }
        }
        if (pinnedArtistKeys.isEmpty()) {
            sorted
        } else {
            val pinnedRank = pinnedArtistKeys.withIndex().associate { it.value to it.index }
            val pinnedSet = pinnedRank.keys
            val pinned = sorted
                .filter { it.name.tagIdentityKey() in pinnedSet }
                .sortedBy { pinnedRank[it.name.tagIdentityKey()] ?: Int.MAX_VALUE }
            pinned + sorted.filterNot { it.name.tagIdentityKey() in pinnedSet }
        }
    }

    fun finishSelectionMode() {
        selectionMode = false
        selectedArtistKeys = emptySet()
        rangeAnchorArtistKey = null
        rangeTargetArtistKey = null
    }
    fun updateRangeAnchorsForManualSelection(artistKey: String, selectedNow: Boolean) {
        if (selectedNow) {
            when {
                rangeAnchorArtistKey == null -> rangeAnchorArtistKey = artistKey
                rangeAnchorArtistKey == artistKey -> Unit
                else -> rangeTargetArtistKey = artistKey
            }
        } else {
            if (rangeTargetArtistKey == artistKey) rangeTargetArtistKey = null
            if (rangeAnchorArtistKey == artistKey) {
                rangeAnchorArtistKey = rangeTargetArtistKey ?: selectedArtistKeys.firstOrNull { it != artistKey }
                rangeTargetArtistKey = null
            }
        }
    }
    fun toggleArtistSelection(artist: Artist) {
        val key = artist.name.tagIdentityKey()
        val selecting = key !in selectedArtistKeys
        val next = if (selecting) selectedArtistKeys + key else selectedArtistKeys - key
        selectedArtistKeys = next
        updateRangeAnchorsForManualSelection(key, selecting)
        if (next.isEmpty()) selectionMode = false
    }
    fun selectedArtistSongs(): List<Song> {
        if (selectedArtistKeys.isEmpty()) return emptyList()
        return songs.filter { song ->
            val names = if (showAlbumArtists) splitArtistNames(song.artist) + splitArtistNames(song.albumArtist)
            else splitArtistNames(song.artist)
            names.any { it.tagIdentityKey() in selectedArtistKeys }
        }.distinctBy { it.id }
    }
    val artistIndexByKey = remember(filteredArtists) {
        buildMap {
            filteredArtists.forEachIndexed { index, artist -> put(artist.name.tagIdentityKey(), index) }
        }
    }
    val selectedVisibleArtistCount = remember(selectedArtistKeys, filteredArtists) {
        filteredArtists.count { it.name.tagIdentityKey() in selectedArtistKeys }
    }
    val rangeSelectionAvailable = remember(artistIndexByKey, selectedArtistKeys, rangeAnchorArtistKey, rangeTargetArtistKey) {
        val anchor = rangeAnchorArtistKey
        val target = rangeTargetArtistKey
        anchor != null &&
            target != null &&
            anchor != target &&
            anchor in selectedArtistKeys &&
            target in selectedArtistKeys &&
            anchor in artistIndexByKey &&
            target in artistIndexByKey
    }
    fun applyRangeSelection() {
        val anchor = rangeAnchorArtistKey ?: return
        val target = rangeTargetArtistKey ?: return
        val anchorIndex = artistIndexByKey[anchor] ?: return
        val targetIndex = artistIndexByKey[target] ?: return
        if (anchorIndex == targetIndex) return
        val bounds = if (anchorIndex < targetIndex) anchorIndex..targetIndex else targetIndex..anchorIndex
        selectedArtistKeys = selectedArtistKeys + bounds.map { filteredArtists[it].name.tagIdentityKey() }
        rangeAnchorArtistKey = target
        rangeTargetArtistKey = null
    }
    fun toggleSelectAllVisibleArtists() {
        if (filteredArtists.isEmpty()) return
        val keys = filteredArtists.mapTo(mutableSetOf()) { it.name.tagIdentityKey() }
        if (keys.all { it in selectedArtistKeys }) {
            selectedArtistKeys = selectedArtistKeys - keys
            rangeAnchorArtistKey = null
            rangeTargetArtistKey = null
        } else {
            selectedArtistKeys = selectedArtistKeys + keys
            rangeAnchorArtistKey = filteredArtists.firstOrNull()?.name?.tagIdentityKey()
            rangeTargetArtistKey = filteredArtists.lastOrNull()?.name?.tagIdentityKey()
        }
        selectionMode = true
    }

    BackHandler(enabled = selectionMode || searchExpanded || sortExpanded) {
        when {
            selectionMode -> finishSelectionMode()
            searchExpanded -> {
                searchExpanded = false
                searchQuery = ""
            }
            sortExpanded -> sortExpanded = false
        }
    }
    LaunchedEffect(selectionMode, filteredArtists) {
        if (!selectionMode) return@LaunchedEffect
        val visibleKeys = filteredArtists.mapTo(mutableSetOf()) { it.name.tagIdentityKey() }
        selectedArtistKeys = selectedArtistKeys.filterTo(mutableSetOf()) { it in visibleKeys }
        if (rangeAnchorArtistKey !in visibleKeys) rangeAnchorArtistKey = selectedArtistKeys.firstOrNull()
        if (rangeTargetArtistKey !in visibleKeys) rangeTargetArtistKey = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Box {
            EllaSmallTopAppBar(
                title = if (selectionMode) {
                    stringResource(R.string.library_selected_fraction, selectedArtistKeys.size, filteredArtists.size)
                } else {
                    stringResource(R.string.category_artist)
                },
                color = ellaPageBackground(),
                navigationIcon = {
                    if (showBackButton || selectionMode) {
                        IconButton(onClick = { if (selectionMode) finishSelectionMode() else onBack() }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Back,
                                contentDescription = stringResource(R.string.common_back),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                },
                titleStartPadding = if (showBackButton || selectionMode) 64.dp else 20.dp,
                actions = {
                    if (selectionMode) {
                        IconButton(onClick = {
                            val selectedSongs = selectedArtistSongs()
                            if (selectedSongs.isNotEmpty()) {
                                playerViewModel.playNext(selectedSongs)
                                finishSelectionMode()
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
                            val selectedSongs = selectedArtistSongs()
                            if (selectedSongs.isNotEmpty()) playlistPickerSongs = selectedSongs
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Add,
                                contentDescription = stringResource(R.string.player_add_to_playlist),
                                tint = MiuixTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = {
                            val selectedSongs = selectedArtistSongs()
                            if (selectedSongs.isNotEmpty()) pendingDeleteSongs = selectedSongs
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.Delete,
                                contentDescription = stringResource(R.string.common_delete),
                                tint = androidx.compose.ui.graphics.Color(0xFFE5484D),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        IconButton(onClick = {
                            selectionMode = true
                            selectedArtistKeys = emptySet()
                            rangeAnchorArtistKey = null
                            rangeTargetArtistKey = null
                        }) {
                            Icon(
                                imageVector = MiuixIcons.Regular.SelectAll,
                                contentDescription = stringResource(R.string.common_multi_select),
                                tint = MiuixTheme.colorScheme.onSurface,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { searchExpanded = !searchExpanded }) {
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
                                        text = stringResource(R.string.artist_list_sort_name),
                                        ascendingMode = ArtistSortMode.Name,
                                        descendingMode = ArtistSortMode.NameDesc
                                    ),
                                    DirectionalSortModeField(
                                        text = stringResource(R.string.artist_list_sort_song_count),
                                        ascendingMode = ArtistSortMode.SongCountAsc,
                                        descendingMode = ArtistSortMode.SongCount
                                    ),
                                    DirectionalSortModeField(
                                        text = stringResource(R.string.artist_list_sort_album_count),
                                        ascendingMode = ArtistSortMode.AlbumCountAsc,
                                        descendingMode = ArtistSortMode.AlbumCount
                                    ),
                                    DirectionalSortModeField(
                                        text = stringResource(R.string.artist_list_sort_release_album_count),
                                        ascendingMode = ArtistSortMode.ReleaseAlbumCountAsc,
                                        descendingMode = ArtistSortMode.ReleaseAlbumCount
                                    ),
                                    DirectionalSortModeField(
                                        text = stringResource(R.string.artist_list_sort_duration),
                                        ascendingMode = ArtistSortMode.DurationAsc,
                                        descendingMode = ArtistSortMode.Duration
                                    )
                                ),
                                selectedMode = sortMode,
                                onSelect = { mode ->
                                    LibrarySortUiState.artistListSortIndex = mode.ordinal
                                    scope.launch { mainViewModel.settingsManager.setArtistListSortIndex(mode.ordinal) }
                                }
                            )
                        )
                    }
                }
            )
            DoubleTapScrollOverlay(
                onDoubleTap = { scrollToTopRequest++ },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            )
        }

        AnimatedVisibility(
            visible = sortExpanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                ArtistSortMode.entries.forEach { mode ->
                    Text(
                        text = stringResource(mode.labelRes),
                        fontSize = 14.sp,
                        fontWeight = if (sortMode == mode) FontWeight.Bold else FontWeight.Normal,
                        color = if (sortMode == mode) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                LibrarySortUiState.artistListSortIndex = mode.ordinal
                                scope.launch { mainViewModel.settingsManager.setArtistListSortIndex(mode.ordinal) }
                                sortExpanded = false
                            }
                            .padding(vertical = 10.dp)
                    )
                }
            }
        }

        if (searchExpanded) {
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchExpanded = false },
                placeholder = stringResource(R.string.artist_list_search_placeholder),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        if (songs.isEmpty() && !libraryCacheLoaded) {
            EllaCenteredLoadingIndicator()
        } else if (filteredArtists.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = stringResource(R.string.artist_list_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        } else {
            val listState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
            RestoreListScrollAfterSearch(
                searchExpanded = searchExpanded,
                query = searchQuery,
                listState = listState
            )
            var fastScrollJob by remember { mutableStateOf<Job?>(null) }
            LaunchedEffect(scrollToTopRequest) {
                if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
            }
            val fastIndexLetters = remember(filteredArtists) {
                filteredArtists.map { it.indexLetter() }
            }
            val fastIndexTargets = remember(fastIndexLetters) {
                buildMap {
                    fastIndexLetters.forEachIndexed { index, letter -> putIfAbsent(letter, index + 1) }
                }
            }
            val showArtistSideIndex = filteredArtists.size > 30
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        end = if (showArtistSideIndex) SideIndexListEndPadding else 0.dp,
                        bottom = 160.dp
                    )
                ) {
                    item {
                        Text(
                            text = stringResource(
                                R.string.artist_list_summary,
                                filteredArtists.size,
                                com.ella.music.ui.components.sortLabel(sortMode.labelRes, sortMode.isDescending())
                            ),
                            fontSize = 13.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                    items(filteredArtists, key = { it.name }) { artist ->
                        val artistKey = artist.name.tagIdentityKey()
                        val selected = artistKey in selectedArtistKeys
                        val isPinned = artistKey in pinnedArtistKeys
                        ArtistRow(
                            artist = artist,
                            representativeSong = representativeSongsByArtist[artistKey],
                            mainViewModel = mainViewModel,
                            artistCoverFolderUri = artistCoverFolderUri,
                            coversEnabled = listCoversEnabled,
                            selectionMode = selectionMode,
                            selected = selected,
                            summary = artist.summaryForSort(
                                sortMode = sortMode,
                                duration = artistDurations[artistKey] ?: 0L,
                                releaseAlbumCount = releaseAlbumCounts[artistKey] ?: 0,
                                stringResolver = { resId, args -> context.getString(resId, *args) }
                            ),
                            isPinned = isPinned,
                            onClick = {
                                if (selectionMode) toggleArtistSelection(artist) else onArtistClick(artist.name)
                            },
                            onLongClick = {
                                if (selectionMode) {
                                    toggleArtistSelection(artist)
                                    return@ArtistRow
                                }
                                artistMenuTarget = artist
                            }
                        )
                    }
                }

                if (sortMode in setOf(ArtistSortMode.Name, ArtistSortMode.NameDesc) && showArtistSideIndex) {
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
                } else if (showArtistSideIndex) {
                    LazyListScrollIndicator(
                        state = listState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                    )
                }
                FloatingSelectionControls(
                    visible = selectionMode && filteredArtists.isNotEmpty(),
                    rangeEnabled = rangeSelectionAvailable,
                    allSelected = filteredArtists.isNotEmpty() && selectedVisibleArtistCount == filteredArtists.size,
                    onRangeSelect = ::applyRangeSelection,
                    onSelectAll = ::toggleSelectAllVisibleArtists,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = LibraryFloatingControlsEndPadding, bottom = LibraryFloatingControlsBottomPadding)
                )
            }
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
                    finishSelectionMode()
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
                    finishSelectionMode()
                }
            }
        )
    }

    artistMenuTarget?.let { artist ->
        val artistKey = artist.name.tagIdentityKey()
        val isPinned = artistKey in pinnedArtistKeys
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = artist.name,
            onDismissRequest = { artistMenuTarget = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                EllaMiuixMenuItem(
                    text = stringResource(if (isPinned) R.string.common_unpin else R.string.common_pin_to_top),
                    onClick = {
                        scope.launch { mainViewModel.settingsManager.setPinned("artist", artistKey, !isPinned) }
                        artistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_share),
                    onClick = {
                        shareLocalSongs(context, mainViewModel.getSongsForArtist(artist.name))
                        artistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_add_to_playlist),
                    onClick = {
                        playlistPickerSongs = mainViewModel.getSongsForArtist(artist.name).sortedForArtistDetail(detailSongSortMode)
                        artistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_to_queue),
                    onClick = {
                        playerViewModel.addToPlaylist(mainViewModel.getSongsForArtist(artist.name).sortedForArtistDetail(detailSongSortMode))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                        artistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_play_next),
                    onClick = {
                        playerViewModel.playNext(mainViewModel.getSongsForArtist(artist.name).sortedForArtistDetail(detailSongSortMode))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                        artistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_desktop_shortcut),
                    onClick = {
                        val ok = requestPinnedEllaShortcut(
                            context = context,
                            id = "artist_${artistKey}",
                            label = artist.name,
                            route = Screen.ArtistDetail.createRoute(artist.name)
                        )
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.playlist_shortcut_requested, artist.name) else context.getString(R.string.playlist_shortcut_unsupported),
                            Toast.LENGTH_SHORT
                        ).show()
                        artistMenuTarget = null
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_delete_permanently),
                    danger = true,
                    onClick = {
                        pendingDeleteSongs = mainViewModel.getSongsForArtist(artist.name)
                        artistMenuTarget = null
                    }
                )
            }
        }
    }

    if (pendingDeleteSongs.isNotEmpty()) {
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.song_more_delete_song_title),
            message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
            confirmText = stringResource(R.string.song_more_delete_permanently),
            onDismiss = { pendingDeleteSongs = emptyList() },
            onConfirm = {
                requestDeleteSongs(pendingDeleteSongs)
                pendingDeleteSongs = emptyList()
            }
        )
    }
}
