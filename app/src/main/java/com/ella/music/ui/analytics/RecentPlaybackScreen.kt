package com.ella.music.ui.analytics

import top.yukonga.miuix.kmp.icon.extended.Add
import top.yukonga.miuix.kmp.icon.extended.Share
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.shape.CircleShape
import com.ella.music.ui.components.SongItem
import com.ella.music.data.model.playlistIdentityKey
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.icon.extended.SelectAll
import top.yukonga.miuix.kmp.icon.extended.Search
import com.ella.music.ui.components.rememberLibrarySelectionState
import com.ella.music.ui.components.SortSummaryHeader
import com.ella.music.ui.components.ShuffleAllSummaryButton
import com.ella.music.ui.components.SongSelectionActionRow
import com.ella.music.ui.components.EllaSearchBar
import com.ella.music.ui.components.isAppWallpaperVisible
import com.ella.music.ui.components.LibrarySelectionState
import com.ella.music.ui.components.ContinuePlaybackRow
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.data.CategoryResumeKeys
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.LaunchedEffect
import top.yukonga.miuix.kmp.basic.Checkbox
import androidx.compose.ui.state.ToggleableState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.ActionMenuLayout
import com.ella.music.data.artistNamesForSong
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.splitGenreNames
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.ella.music.data.PlaylistExportFormat
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.LibraryEntityAction
import com.ella.music.ui.components.LibraryEntityActionSheet
import com.ella.music.ui.components.LibraryEntityActions
import com.ella.music.ui.components.SongInfoSheet
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.actionMenuIcon
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.rememberSongDeleteRequester
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.folder.FolderPlaylistEditorSheet
import com.ella.music.ui.folder.FolderPlaylistFolderSortMode
import com.ella.music.ui.folder.LinkToFolderPlaylistSheet
import com.ella.music.ui.folder.songsForFolderPlaylist
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.navigation.Screen
import com.ella.music.ui.playlist.CreatePlaylistDialog
import com.ella.music.ui.playlist.ExportPlaylistFormatSheet
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.TextField
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.preference.SwitchPreference
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class RecentPlaybackTab(val routeValue: String, val labelRes: Int) {
    Collection("collection", R.string.recent_playback_tab_collection),
    Song("song", R.string.recent_playback_tab_song),
    Mv("mv", R.string.recent_playback_tab_mv),
    Playlist("playlist", R.string.category_playlist),
    Artist("artist", R.string.category_artist),
    Album("album", R.string.category_album),
    Folder("folder", R.string.category_folder),
    FolderPlaylists("folder_playlists", R.string.folder_playlist_title),
    Year("year", R.string.category_year),
    Genre("genre", R.string.category_genre),
    Composer("composer", R.string.category_composer),
    Arranger("arranger", R.string.category_arranger),
    Lyricist("lyricist", R.string.category_lyricist);

    companion object {
        fun fromRoute(value: String?): RecentPlaybackTab =
            entries.firstOrNull { it.routeValue == value } ?: Collection
    }
}

private data class ResolvedRecentEntry(
    val entry: PlaybackHistoryEntry,
    val song: Song?
)

private data class RecentPlaybackRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val playedAt: Long,
    val song: Song?,
    val entryIds: List<String>,
    /** Circular art for artist / composer / arranger / lyricist; square rounded otherwise. */
    val circularArt: Boolean = false,
    /** Entity kind for per-type overflow menus (collection rows keep the underlying type). */
    val kind: RecentPlaybackTab = RecentPlaybackTab.Song,
    /** Playlist / folder-playlist id, or raw category/folder/artist/album name. */
    val entityId: String = "",
    /** Album media id when known (for shortcut / album song resolve). */
    val albumId: Long = 0L,
    /** Songs belonging to this row (recent matches); actions prefer ViewModel resolve when possible. */
    val rowSongs: List<Song> = emptyList()
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RecentPlaybackScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    initialType: String? = null,
    onBack: () -> Unit,
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    val songs by mainViewModel.songs.collectAsState()
    val history by mainViewModel.recentPlaybackHistory.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    val folderPlaylists by mainViewModel.settingsManager.folderPlaylists.collectAsState(initial = emptyList())
    var selectedTab by rememberSaveable(initialType) {
        mutableStateOf(RecentPlaybackTab.fromRoute(initialType).routeValue)
    }
    var showSettings by remember { mutableStateOf(false) }
    var clearRows by remember { mutableStateOf<List<RecentPlaybackRow>?>(null) }
    var deleteSingleEntryId by remember { mutableStateOf<String?>(null) }
    var deleteIdenticalEntryIds by remember { mutableStateOf<Set<String>?>(null) }
    var deleteIdenticalTitle by remember { mutableStateOf<String?>(null) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var actionRecentRow by remember { mutableStateOf<RecentPlaybackRow?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val currentTab = remember(selectedTab) { RecentPlaybackTab.fromRoute(selectedTab) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    val selection = rememberLibrarySelectionState<String>()
    var searchActive by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val tabs = remember { RecentPlaybackTab.entries.toList() }
    val initialPagerPage = remember(initialType) {
        tabs.indexOf(RecentPlaybackTab.fromRoute(initialType)).coerceAtLeast(0)
    }
    val pagerState = rememberPagerState(initialPage = initialPagerPage, pageCount = { tabs.size })
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collectLatest { page ->
            val tab = tabs.getOrNull(page) ?: return@collectLatest
            if (selectedTab != tab.routeValue) selectedTab = tab.routeValue
        }
    }
    LaunchedEffect(selectedTab) {
        val index = tabs.indexOf(currentTab)
        if (index >= 0 && pagerState.currentPage != index) {
            pagerState.animateScrollToPage(index)
        }
    }
    val rows by produceState(
        initialValue = emptyList<RecentPlaybackRow>(),
        history, songs, playlists, folderPlaylists, currentTab
    ) {
        value = withContext(Dispatchers.Default) {
            buildRecentPlaybackRows(history, songs, playlists, folderPlaylists, currentTab)
        }
    }
    // Stable per-tab flows — recreating the Flow every recomposition made collectAsState
    // restart with the default and look like limit changes never stuck.
    val recentLimitFlow = remember(currentTab.routeValue) {
        mainViewModel.settingsManager.recentPlaybackLimit(currentTab.routeValue)
    }
    val recentLimit by recentLimitFlow.collectAsState(
        initial = com.ella.music.data.SettingsManager.DEFAULT_RECENT_PLAYBACK_LIMIT
    )
    val showDateFlow = remember(currentTab.routeValue) {
        mainViewModel.settingsManager.recentPlaybackShowDate(currentTab.routeValue)
    }
    val showDate by showDateFlow.collectAsState(
        initial = com.ella.music.data.SettingsManager.DEFAULT_RECENT_PLAYBACK_SHOW_DATE
    )
    val collectionTypes by mainViewModel.settingsManager.recentPlaybackCollectionTypes.collectAsState(
        initial = com.ella.music.data.SettingsManager.DEFAULT_RECENT_PLAYBACK_COLLECTION_TYPES.split(',').toSet()
    )
    val listActionMenuLayout by mainViewModel.settingsManager.listActionMenuLayout.collectAsState(initial = "")
    val visibleListActionIds = remember(listActionMenuLayout) {
        ActionMenuLayout.parse(listActionMenuLayout, ActionMenuIds.listDefaults)
            .visibleIds(ActionMenuIds.listDefaults)
    }
    val visibleRows = remember(rows, recentLimit, collectionTypes, currentTab, searchQuery) {
        rows
            .filter { row ->
                currentTab != RecentPlaybackTab.Collection ||
                    row.key.substringBefore(':') in collectionTypes
            }
            .filter { row ->
                val q = searchQuery.trim()
                q.isEmpty() ||
                    row.title.contains(q, ignoreCase = true) ||
                    row.subtitle.contains(q, ignoreCase = true) ||
                    row.song?.artist.orEmpty().contains(q, ignoreCase = true)
            }
            .let { filtered ->
                if (recentLimit == com.ella.music.data.SettingsManager.RECENT_PLAYBACK_UNLIMITED) {
                    filtered
                } else {
                    filtered.take(recentLimit.coerceAtLeast(0))
                }
            }
    }
    val tabSongs = remember(visibleRows) { visibleRows.mapNotNull { it.song } }
    val orderedRowKeys = remember(visibleRows) { visibleRows.map { it.key } }
    val rowIndexByKey = remember(orderedRowKeys) { orderedRowKeys.withIndex().associate { it.value to it.index } }
    val countLabel = stringResource(currentTab.countDescriptionRes(), visibleRows.size)
    val resumeCategoryKey = CategoryResumeKeys.RECENT_PLAYBACK
    val currentPlayingSong by playerViewModel.currentSong.collectAsState()
    val favoriteSongKeys by playerViewModel.favoriteSongKeys.collectAsState()
    val showPlayNextInLists by mainViewModel.settingsManager.showPlayNextInLists.collectAsState(initial = false)
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var entityMenuRow by remember { mutableStateOf<RecentPlaybackRow?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var playlistToRename by remember { mutableStateOf<UserPlaylist?>(null) }
    var playlistPendingDelete by remember { mutableStateOf<UserPlaylist?>(null) }
    var folderPlaylistPendingDelete by remember { mutableStateOf<FolderPlaylist?>(null) }
    var associateFolderPaths by remember { mutableStateOf<List<String>?>(null) }
    var mvInfoSong by remember { mutableStateOf<Song?>(null) }
    var exportPlaylistTarget by remember { mutableStateOf<UserPlaylist?>(null) }
    var showExportFormatSheet by remember { mutableStateOf(false) }
    var pendingM3uExportFormat by remember { mutableStateOf<PlaylistExportFormat?>(null) }
    var folderPlaylistEditorTarget by remember { mutableStateOf<FolderPlaylist?>(null) }
    var showFolderPlaylistEditor by remember { mutableStateOf(false) }
    var editorDraftName by remember { mutableStateOf("") }
    var editorDraftFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    var editorPinnedFolders by remember { mutableStateOf<Set<String>>(emptySet()) }
    val requestDeleteSongs = rememberSongDeleteRequester(mainViewModel)
    val availableFolders = remember(songs) {
        songs.map { it.path.substringBeforeLast('/', missingDelimiterValue = "") }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
    }
    val folderEditorSortMode = FolderPlaylistFolderSortMode.Name
    val txtExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val target = exportPlaylistTarget
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        mainViewModel.exportLocalPlaylist(target, uri, PlaylistExportFormat.PlainText) { result ->
            result
                .onSuccess { exportResult ->
                    val skippedText = if (exportResult.skippedCount > 0) {
                        context.getString(R.string.playlist_export_skipped, exportResult.skippedCount)
                    } else ""
                    Toast.makeText(
                        context,
                        context.getString(R.string.playlist_export_done, exportResult.exportedCount, skippedText),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        context.getString(R.string.playlist_export_failed, it.message.orEmpty()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
        exportPlaylistTarget = null
    }
    val m3uExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("audio/x-mpegurl")) { uri ->
        val target = exportPlaylistTarget
        val targetFormat = pendingM3uExportFormat ?: PlaylistExportFormat.M3u8
        pendingM3uExportFormat = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        mainViewModel.exportLocalPlaylist(target, uri, targetFormat) { result ->
            result
                .onSuccess { exportResult ->
                    val skippedText = if (exportResult.skippedCount > 0) {
                        context.getString(R.string.playlist_export_skipped, exportResult.skippedCount)
                    } else ""
                    Toast.makeText(
                        context,
                        context.getString(R.string.playlist_export_done, exportResult.exportedCount, skippedText),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                .onFailure {
                    Toast.makeText(
                        context,
                        context.getString(R.string.playlist_export_failed, it.message.orEmpty()),
                        Toast.LENGTH_SHORT
                    ).show()
                }
        }
        exportPlaylistTarget = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 8.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = MiuixTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }
            Text(
                text = stringResource(R.string.recent_playback_title),
                color = MiuixTheme.colorScheme.onBackground,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            if (selectionMode) {
                IconButton(onClick = {
                    selectionMode = false
                    selection.finishSelectionMode()
                }) {
                    Text(
                        text = stringResource(R.string.common_cancel),
                        color = MiuixTheme.colorScheme.primary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            } else {
                IconButton(onClick = { showSettings = true }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Settings,
                        contentDescription = stringResource(R.string.recent_playback_settings),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = {
                    selectionMode = true
                    selection.selectionMode = true
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.SelectAll,
                        contentDescription = stringResource(R.string.recent_playback_multi_select),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = {
                    searchActive = !searchActive
                    if (!searchActive) searchQuery = ""
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Search,
                        contentDescription = stringResource(R.string.recent_playback_search),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = { clearRows = rows }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Delete,
                        contentDescription = stringResource(R.string.recent_playback_clear),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
        }
        if (searchActive) {
            val searchBarColor = if (isAppWallpaperVisible()) {
                MiuixTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.74f)
            } else {
                MiuixTheme.colorScheme.surfaceContainerHigh
            }
            EllaSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                onSearch = { searchActive = false },
                placeholder = stringResource(R.string.recent_playback_search_hint),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                containerColor = searchBarColor
            )
        }

        val tabScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tabScrollState)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RecentPlaybackTab.entries.forEach { tab ->
                val selected = tab == currentTab
                Text(
                    text = stringResource(tab.labelRes),
                    fontSize = 14.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (selected) MiuixTheme.colorScheme.onPrimary else MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    modifier = Modifier
                        .wrapContentWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            if (selected) MiuixTheme.colorScheme.primary
                            else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
                        )
                        .clickable { selectedTab = tab.routeValue }
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
            }
        }

        SortSummaryHeader(
            text = countLabel,
            leadingContent = {
                ShuffleAllSummaryButton(
                    visible = !selectionMode && tabSongs.isNotEmpty(),
                    onClick = {
                        playerViewModel.setShuffledPlaylist(
                            tabSongs,
                            resumeCategoryKey = resumeCategoryKey
                        )
                        onNavigateToPlayer()
                    }
                )
            }
        )

        if (currentTab == RecentPlaybackTab.Song && tabSongs.isNotEmpty() && !selectionMode) {
            ContinuePlaybackRow(
                songs = tabSongs,
                categoryKey = resumeCategoryKey,
                currentSong = currentPlayingSong,
                onContinue = { index ->
                    playerViewModel.setPlaylist(
                        tabSongs,
                        index,
                        resumeCategoryKey = resumeCategoryKey
                    )
                    onNavigateToPlayer()
                }
            )
        }

        if (selectionMode) {
            SongSelectionActionRow(
                selectedCount = selection.selectedIds.size,
                totalCount = visibleRows.size,
                rangeEnabled = selection.isRangeSelectionAvailable(rowIndexByKey),
                allSelected = visibleRows.isNotEmpty() && visibleRows.all { it.key in selection.selectedIds },
                onRangeSelect = { selection.applyRangeSelection(orderedRowKeys, rowIndexByKey) },
                onSelectAll = { selection.toggleSelectAll(orderedRowKeys) },
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            val selectedSongs = remember(selection.selectedIds, visibleRows) {
                visibleRows.filter { it.key in selection.selectedIds }.mapNotNull { it.song }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    if (selectedSongs.isNotEmpty()) {
                        shareLocalSongs(context, selectedSongs)
                        selectionMode = false
                        selection.finishSelectionMode()
                    }
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Share,
                        contentDescription = stringResource(R.string.common_share),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = {
                    if (selectedSongs.isNotEmpty()) {
                        playerViewModel.playNext(selectedSongs)
                        selectionMode = false
                        selection.finishSelectionMode()
                    }
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_play_next_add),
                        contentDescription = stringResource(R.string.song_more_play_next),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = {
                    if (selectedSongs.isNotEmpty()) {
                        playerViewModel.addToPlaylist(selectedSongs)
                        selectionMode = false
                        selection.finishSelectionMode()
                    }
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Add,
                        contentDescription = stringResource(R.string.common_add_to_queue),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
                IconButton(onClick = {
                    if (selectedSongs.isNotEmpty()) {
                        playlistPickerSongs = selectedSongs
                        selectionMode = false
                        selection.finishSelectionMode()
                    }
                }) {
                    Icon(
                        painter = painterResource(R.drawable.ic_playlist_add),
                        contentDescription = stringResource(R.string.player_add_to_playlist),
                        tint = MiuixTheme.colorScheme.onBackground
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            userScrollEnabled = !selectionMode,
            beyondViewportPageCount = 0
        ) { page ->
            // Content always reflects currentTab/visibleRows (synced with pager via LaunchedEffect).
            if (visibleRows.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.recent_playback_empty),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
            } else {
                val daySections = remember(visibleRows) { groupRowsByPlayDay(visibleRows) }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 160.dp)
                ) {
                    daySections.forEach { section ->
                        item(key = "day-${section.dayKey}") {
                            RecentPlaybackDayHeader(playedAt = section.rows.first().playedAt)
                        }
                        itemsIndexed(
                            section.rows,
                            key = { _, row -> row.key }
                        ) { _, row ->
                            val selected = row.key in selection.selectedIds
                            if (currentTab == RecentPlaybackTab.Song) {
                                val song = row.song
                                if (song != null) {
                                    val albumArtUri = remember(song.albumId) {
                                        song.albumId.takeIf { it > 0L }?.let(mainViewModel::getAlbumArtUri)
                                    }
                                    SongItem(
                                        song = song,
                                        isCurrent = currentPlayingSong?.id == song.id,
                                        albumArtUri = albumArtUri,
                                        loadCoverArt = mainViewModel::getCoverArtBitmap,
                                        loadAudioInfo = mainViewModel::getAudioInfo,
                                        loadSongTagInfo = mainViewModel::getSongTagInfo,
                                        showPlayNextInLists = showPlayNextInLists,
                                        isFavorite = song.playlistIdentityKey() in favoriteSongKeys,
                                        loadSongRating = mainViewModel::getSongRating,
                                        selectionMode = selectionMode,
                                        selected = selected,
                                        onLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selection.selectionMode = true
                                            }
                                            selection.toggleSelection(row.key)
                                        },
                                        onClick = {
                                            if (selectionMode) {
                                                selection.toggleSelection(row.key)
                                            } else {
                                                val songsInTab = tabSongs
                                                val start = songsInTab.indexOfFirst { it.id == song.id }.coerceAtLeast(0)
                                                playerViewModel.setPlaylist(
                                                    songsInTab,
                                                    start,
                                                    resumeCategoryKey = resumeCategoryKey
                                                )
                                                onNavigateToPlayer()
                                            }
                                        },
                                        onPlayNext = { playerViewModel.playNext(song) },
                                        onMore = {
                                            actionSong = song
                                            actionRecentRow = row
                                        }
                                    )
                                } else {
                                    RecentPlaybackRowCard(
                                        row = row,
                                        mainViewModel = mainViewModel,
                                        selected = selectionMode && selected,
                                        selectionMode = selectionMode,
                                        onPlay = {
                                            if (selectionMode) selection.toggleSelection(row.key)
                                        },
                                        onLongClick = {
                                            if (!selectionMode) {
                                                selectionMode = true
                                                selection.selectionMode = true
                                            }
                                            selection.toggleSelection(row.key)
                                        },
                                        onMore = {
                                            if (row.kind == RecentPlaybackTab.Song) {
                                                row.song?.let {
                                                    actionSong = it
                                                    actionRecentRow = row
                                                }
                                            } else {
                                                entityMenuRow = row
                                            }
                                        }
                                    )
                                }
                            } else {
                                RecentPlaybackRowCard(
                                    row = row,
                                    mainViewModel = mainViewModel,
                                    selected = selectionMode && selected,
                                    selectionMode = selectionMode,
                                    onPlay = {
                                        if (selectionMode) {
                                            selection.toggleSelection(row.key)
                                        } else {
                                            row.song?.let { song ->
                                                playerViewModel.playSongUncategorized(song)
                                                onNavigateToPlayer()
                                            }
                                        }
                                    },
                                    onLongClick = {
                                        if (!selectionMode) {
                                            selectionMode = true
                                            selection.selectionMode = true
                                        }
                                        selection.toggleSelection(row.key)
                                    },
                                    onMore = {
                                        when (row.kind) {
                                            RecentPlaybackTab.Song -> {
                                                row.song?.let {
                                                    actionSong = it
                                                    actionRecentRow = row
                                                }
                                            }
                                            else -> entityMenuRow = row
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    }



        // Multi-select share / playlist pickers are wired through existing song action hosts below.
    SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = {
            actionSong = null
            actionRecentRow = null
        },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist,
        onDeleteSingleRecentPlayback = actionRecentRow?.let { row ->
            val singleId = row.entryIds.firstOrNull()
            if (singleId != null) {
                {
                    actionSong = null
                    actionRecentRow = null
                    deleteSingleEntryId = singleId
                }
            } else null
        },
        onClearRecentPlayback = actionRecentRow?.let { row ->
            {
                val song = row.song
                val targetIds = if (song != null) {
                    history.filter { it.songId == song.id || (it.title == song.title && it.artist == song.artist) }
                        .map { it.entryId }
                        .toSet()
                        .ifEmpty { row.entryIds.toSet() }
                } else {
                    row.entryIds.toSet()
                }
                actionSong = null
                actionRecentRow = null
                deleteIdenticalEntryIds = targetIds
                deleteIdenticalTitle = row.title
            }
        }
    )

    entityMenuRow?.let { menuRow ->
        val resolvedSongs = remember(menuRow, playlists, folderPlaylists, songs) {
            resolveRecentRowSongs(menuRow, mainViewModel, playlists, folderPlaylists, songs)
        }
        val displayName = recentRowDisplayName(menuRow)
        LibraryEntityActionSheet(
            show = true,
            title = stringResource(R.string.player_more_actions),
            onDismissRequest = { entityMenuRow = null },
            actions = buildRecentEntityActions(
                row = menuRow,
                displayName = displayName,
                resolvedSongs = resolvedSongs,
                playlists = playlists,
                folderPlaylists = folderPlaylists,
                context = context,
                mainViewModel = mainViewModel,
                playerViewModel = playerViewModel,
                onDismiss = { entityMenuRow = null },
                onShare = {
                    if (resolvedSongs.isNotEmpty()) shareLocalSongs(context, resolvedSongs)
                    entityMenuRow = null
                },
                onAddToPlaylist = {
                    if (resolvedSongs.isNotEmpty()) playlistPickerSongs = resolvedSongs
                    entityMenuRow = null
                },
                onAddToQueue = {
                    if (resolvedSongs.isNotEmpty()) {
                        playerViewModel.addToPlaylist(resolvedSongs)
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                    }
                    entityMenuRow = null
                },
                onPlayNext = {
                    if (resolvedSongs.isNotEmpty()) {
                        playerViewModel.playNext(resolvedSongs)
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                    }
                    entityMenuRow = null
                },
                onRemoveFromRecent = {
                    deleteIdenticalEntryIds = menuRow.entryIds.toSet()
                    deleteIdenticalTitle = displayName
                    entityMenuRow = null
                },
                onDeletePermanently = {
                    if (resolvedSongs.isNotEmpty()) pendingDeleteSongs = resolvedSongs
                    entityMenuRow = null
                },
                onExportPlaylist = { playlist ->
                    exportPlaylistTarget = playlist
                    showExportFormatSheet = true
                    entityMenuRow = null
                },
                onRenamePlaylist = { playlist ->
                    playlistToRename = playlist
                    entityMenuRow = null
                },
                onDeletePlaylist = { playlist ->
                    playlistPendingDelete = playlist
                    entityMenuRow = null
                },
                onRefreshFolderPlaylist = { playlist ->
                    scope.launch { mainViewModel.refreshFolderPlaylistFolders(playlist.folders) }
                    entityMenuRow = null
                },
                onAssociateFolders = { paths ->
                    associateFolderPaths = paths
                    entityMenuRow = null
                },
                onEditFolderPlaylist = { playlist ->
                    folderPlaylistEditorTarget = playlist
                    editorDraftName = playlist.name
                    editorDraftFolders = playlist.folders.toSet()
                    editorPinnedFolders = emptySet()
                    showFolderPlaylistEditor = true
                    entityMenuRow = null
                },
                onDeleteFolderPlaylist = { playlist ->
                    folderPlaylistPendingDelete = playlist
                    entityMenuRow = null
                },
                onMvInfo = { song ->
                    mvInfoSong = song
                    entityMenuRow = null
                },
                onDesktopShortcut = { id, label, route ->
                    val ok = requestPinnedEllaShortcut(
                        context = context,
                        id = id,
                        label = label,
                        route = route
                    )
                    Toast.makeText(
                        context,
                        if (ok) context.getString(R.string.playlist_shortcut_requested, label)
                        else context.getString(R.string.playlist_shortcut_unsupported),
                        Toast.LENGTH_SHORT
                    ).show()
                    entityMenuRow = null
                }
            )
        )
    }

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_add_to_playlist),
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
                    selectedPlaylists.forEach { target ->
                        mainViewModel.addSongsToPlaylist(target.id, songsToAdd, appendToEnd)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    playlistPickerSongs = null
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        CreatePlaylistAndAddSheet(
            onDismiss = { createPlaylistSongs = null },
            onCreate = { playlistName ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, playlistName) { created ->
                    mainViewModel.addSongsToPlaylist(created.id, songsToAdd)
                    createPlaylistSongs = null
                }
            }
        )
    }

    playlistToRename?.let { playlist ->
        CreatePlaylistDialog(
            onDismiss = { playlistToRename = null },
            onCreate = { newName ->
                if (newName.isBlank()) return@CreatePlaylistDialog
                mainViewModel.renamePlaylist(playlist.id, newName) { renamed ->
                    if (renamed) {
                        playlistToRename = null
                    } else {
                        Toast.makeText(context, R.string.playlist_name_exists, Toast.LENGTH_SHORT).show()
                    }
                }
            },
            initialName = playlist.name,
            title = stringResource(R.string.common_rename),
            confirmText = stringResource(R.string.common_confirm)
        )
    }

    playlistPendingDelete?.let { playlist ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.playlist_delete_title),
            message = stringResource(R.string.playlist_delete_message, playlist.name),
            onDismiss = { playlistPendingDelete = null },
            onConfirm = {
                mainViewModel.deletePlaylist(playlist.id)
                playlistPendingDelete = null
            }
        )
    }

    folderPlaylistPendingDelete?.let { playlist ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.folder_playlist_delete_title),
            message = stringResource(R.string.folder_playlist_delete_message, playlist.name),
            onDismiss = { folderPlaylistPendingDelete = null },
            onConfirm = {
                scope.launch {
                    mainViewModel.settingsManager.deleteFolderPlaylist(playlist.id)
                }
                folderPlaylistPendingDelete = null
            }
        )
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

    associateFolderPaths?.let { sourceFolders ->
        LinkToFolderPlaylistSheet(
            show = true,
            songs = songs,
            selectedFolderCount = sourceFolders.size,
            folderPlaylists = folderPlaylists,
            onDismiss = { associateFolderPaths = null },
            onLink = { targets ->
                scope.launch {
                    targets.forEach { target ->
                        val mergedFolders = (target.folders + sourceFolders).distinctBy { it.lowercase() }
                        mainViewModel.settingsManager.upsertFolderPlaylist(
                            playlistId = target.id,
                            name = target.name,
                            folders = mergedFolders
                        )
                    }
                    Toast.makeText(
                        context,
                        if (targets.size == 1) {
                            context.getString(R.string.folder_playlist_associate_done, targets.first().name)
                        } else {
                            context.getString(R.string.folder_playlist_associate_multi_done, targets.size)
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
                associateFolderPaths = null
            },
            onCreatePlaylist = { name ->
                scope.launch { mainViewModel.settingsManager.upsertFolderPlaylist(null, name, sourceFolders) }
                associateFolderPaths = null
            }
        )
    }

    FolderPlaylistEditorSheet(
        show = showFolderPlaylistEditor,
        target = folderPlaylistEditorTarget,
        availableFolders = availableFolders,
        songs = songs,
        coverModel = folderPlaylistEditorTarget?.let { fp ->
            songs.songsForFolderPlaylist(fp.folders).firstOrNull()?.let { song ->
                song.coverUrl.takeIf { it.isNotBlank() } ?: mainViewModel.getAlbumArtUri(song.albumId)
            }
        },
        draftName = editorDraftName,
        onDraftNameChange = { editorDraftName = it },
        selectedFolders = editorDraftFolders,
        onSelectedFoldersChange = { editorDraftFolders = it },
        pinnedFolders = editorPinnedFolders,
        onPinnedFoldersChange = { editorPinnedFolders = it },
        editorSort = folderEditorSortMode,
        onEditorSortChange = { },
        onDismiss = { showFolderPlaylistEditor = false },
        onSave = { target, name, folders ->
            scope.launch {
                val safeName = name.trim()
                val nameExists = folderPlaylists.any { playlist ->
                    playlist.id != target?.id && playlist.name.trim().equals(safeName, ignoreCase = true)
                }
                if (nameExists) {
                    Toast.makeText(context, R.string.playlist_name_exists, Toast.LENGTH_SHORT).show()
                    return@launch
                }
                val saved = mainViewModel.settingsManager.upsertFolderPlaylist(target?.id, name, folders)
                if (saved == null) {
                    Toast.makeText(context, R.string.folder_playlist_save_failed, Toast.LENGTH_SHORT).show()
                } else {
                    showFolderPlaylistEditor = false
                }
            }
        }
    )

    mvInfoSong?.let { infoSong ->
        EllaMiuixBottomSheet(
            show = true,
            title = stringResource(R.string.song_more_view_song_info),
            onDismissRequest = { mvInfoSong = null }
        ) {
            SongInfoSheet(
                song = infoSong,
                audioInfoLoader = mainViewModel::getAudioInfo,
                tagInfoLoader = mainViewModel::getSongTagInfo,
                onDismiss = { mvInfoSong = null }
            )
        }
    }

    if (showExportFormatSheet && exportPlaylistTarget != null) {
        ExportPlaylistFormatSheet(
            onDismiss = {
                showExportFormatSheet = false
                exportPlaylistTarget = null
            },
            onFormatSelected = { format ->
                showExportFormatSheet = false
                val playlist = exportPlaylistTarget ?: return@ExportPlaylistFormatSheet
                val fileName = when (format) {
                    PlaylistExportFormat.PlainText -> "${playlist.name}.txt"
                    PlaylistExportFormat.M3u8 -> "${playlist.name}.m3u8"
                    PlaylistExportFormat.M3u -> "${playlist.name}.m3u"
                }
                when (format) {
                    PlaylistExportFormat.PlainText -> txtExportLauncher.launch(fileName)
                    PlaylistExportFormat.M3u8,
                    PlaylistExportFormat.M3u -> {
                        pendingM3uExportFormat = format
                        m3uExportLauncher.launch(fileName)
                    }
                }
            }
        )
    }

    var showCustomLimitDialog by remember { mutableStateOf(false) }
    var customLimitInput by remember { mutableStateOf("") }

    if (showSettings) {
        EllaMiuixBottomSheet(
            show = true,
            title = stringResource(R.string.recent_playback_settings),
            onDismissRequest = { showSettings = false }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(R.string.recent_playback_settings_count),
                    color = MiuixTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        com.ella.music.data.SettingsManager.DEFAULT_RECENT_PLAYBACK_LIMIT to stringResource(R.string.recent_playback_limit_100),
                        300 to stringResource(R.string.recent_playback_limit_300),
                        com.ella.music.data.SettingsManager.RECENT_PLAYBACK_UNLIMITED to stringResource(R.string.recent_playback_limit_unlimited)
                    ).forEach { (value, label) ->
                        val selected = recentLimit == value
                        Button(
                            onClick = {
                                scope.launch {
                                    mainViewModel.settingsManager.setRecentPlaybackLimit(currentTab.routeValue, value)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            minWidth = 0.dp,
                            cornerRadius = 14.dp,
                            colors = if (selected) {
                                ButtonDefaults.buttonColorsPrimary()
                            } else {
                                ButtonDefaults.buttonColors()
                            },
                            insideMargin = PaddingValues(horizontal = 8.dp, vertical = 10.dp)
                        ) {
                            Text(text = label)
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.recent_playback_limit_custom),
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                val currentVal = if (recentLimit <= 0) 300 else recentLimit
                                val nextVal = (currentVal - 20).coerceAtLeast(10)
                                scope.launch {
                                    mainViewModel.settingsManager.setRecentPlaybackLimit(currentTab.routeValue, nextVal)
                                }
                            },
                            minWidth = 36.dp,
                            cornerRadius = 10.dp,
                            colors = ButtonDefaults.buttonColors(),
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "−", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                        Button(
                            onClick = {
                                customLimitInput = if (recentLimit <= 0) "100" else recentLimit.toString()
                                showCustomLimitDialog = true
                            },
                            cornerRadius = 10.dp,
                            colors = ButtonDefaults.buttonColors(),
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = if (recentLimit <= 0) stringResource(R.string.recent_playback_limit_unlimited) else recentLimit.toString(),
                                color = MiuixTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Button(
                            onClick = {
                                val currentVal = if (recentLimit <= 0) 100 else recentLimit
                                val nextVal = currentVal + 20
                                scope.launch {
                                    mainViewModel.settingsManager.setRecentPlaybackLimit(currentTab.routeValue, nextVal)
                                }
                            },
                            minWidth = 36.dp,
                            cornerRadius = 10.dp,
                            colors = ButtonDefaults.buttonColors(),
                            insideMargin = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(text = "+", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    }
                }
                SwitchPreference(
                    checked = showDate,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            mainViewModel.settingsManager.setRecentPlaybackShowDate(currentTab.routeValue, enabled)
                        }
                    },
                    title = stringResource(R.string.recent_playback_show_date),
                    modifier = Modifier.fillMaxWidth()
                )
                if (currentTab == RecentPlaybackTab.Collection) {
                    Text(
                        text = stringResource(R.string.recent_playback_collection_types),
                        color = MiuixTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)
                    )
                    RecentPlaybackTab.entries
                        .filter { it !in setOf(RecentPlaybackTab.Collection, RecentPlaybackTab.Song, RecentPlaybackTab.Mv) }
                        .forEach { tab ->
                            val enabled = tab.routeValue in collectionTypes
                            SwitchPreference(
                                checked = enabled,
                                onCheckedChange = { checked ->
                                    val updated = if (checked) collectionTypes + tab.routeValue
                                    else collectionTypes - tab.routeValue
                                    scope.launch {
                                        mainViewModel.settingsManager.setRecentPlaybackCollectionTypes(updated)
                                    }
                                },
                                title = stringResource(tab.labelRes),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                }
            }
        }
    }

    if (showCustomLimitDialog) {
        EllaMiuixDialog(
            show = true,
            title = stringResource(R.string.recent_playback_limit_custom),
            onDismissRequest = { showCustomLimitDialog = false }
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                TextField(
                    value = customLimitInput,
                    onValueChange = { customLimitInput = it.filter(Char::isDigit).take(5) },
                    label = "100",
                    useLabelAsPlaceholder = true,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { showCustomLimitDialog = false },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(text = stringResource(R.string.common_cancel))
                    }
                    Button(
                        onClick = {
                            val parsed = customLimitInput.toIntOrNull()
                            if (parsed != null && parsed > 0) {
                                scope.launch {
                                    mainViewModel.settingsManager.setRecentPlaybackLimit(currentTab.routeValue, parsed)
                                }
                            }
                            showCustomLimitDialog = false
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColorsPrimary()
                    ) {
                        Text(text = stringResource(R.string.common_confirm))
                    }
                }
            }
        }
    }

    clearRows?.let { targetRows ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.recent_playback_clear),
            message = stringResource(R.string.recent_playback_clear_message, stringResource(currentTab.labelRes)),
            onDismiss = { clearRows = null },
            onConfirm = {
                val ids = targetRows.flatMap(RecentPlaybackRow::entryIds).toSet()
                clearRows = null
                scope.launch {
                    val targetEntries = history.filter { it.entryId in ids }
                    mainViewModel.removeRecentPlaybackHistoryEntries(targetEntries)
                }
            }
        )
    }

    deleteSingleEntryId?.let { entryId ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.recent_playback_remove_from_recent),
            message = stringResource(R.string.recent_playback_remove_from_recent_message),
            onDismiss = { deleteSingleEntryId = null },
            onConfirm = {
                val targetId = entryId
                deleteSingleEntryId = null
                scope.launch {
                    history.find { it.entryId == targetId }?.let { entry ->
                        mainViewModel.removeRecentPlaybackHistoryEntry(entry)
                    }
                }
            }
        )
    }

    deleteIdenticalEntryIds?.let { targetIds ->
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.recent_playback_remove_from_recent),
            message = stringResource(
                R.string.recent_playback_remove_from_recent_message,
                deleteIdenticalTitle ?: stringResource(currentTab.labelRes)
            ),
            onDismiss = {
                deleteIdenticalEntryIds = null
                deleteIdenticalTitle = null
            },
            onConfirm = {
                val ids = targetIds
                deleteIdenticalEntryIds = null
                deleteIdenticalTitle = null
                scope.launch {
                    val targetEntries = history.filter { it.entryId in ids }
                    mainViewModel.removeRecentPlaybackHistoryEntries(targetEntries)
                }
            }
        )
    }
}

@Composable
private fun RecentPlaybackDayHeader(playedAt: Long) {
    val label = if (isPlayDayToday(playedAt)) {
        stringResource(R.string.recent_playback_today)
    } else {
        recentPlaybackSectionDate(playedAt)
    }
    Text(
        text = label,
        color = MiuixTheme.colorScheme.onBackground,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .background(ellaPageBackground())
            .padding(horizontal = 16.dp, vertical = 10.dp)
    )
}

@Composable
private fun RecentPlaybackRowCard(
    row: RecentPlaybackRow,
    mainViewModel: MainViewModel,
    onPlay: () -> Unit,
    onMore: () -> Unit,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {}
) {
    val coverShape = if (row.circularArt) CircleShape else RoundedCornerShape(10.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onPlay, onLongClick = onLongClick)
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
                else androidx.compose.ui.graphics.Color.Transparent
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectionMode) {
            Checkbox(
                state = ToggleableState(selected),
                onClick = { onPlay() }
            )
            Spacer(modifier = Modifier.width(8.dp))
        }
        AnalyticsSongCover(
            song = row.song,
            mainViewModel = mainViewModel,
            modifier = Modifier.size(52.dp),
            clipShape = coverShape
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp)
        ) {
            Text(
                text = row.title,
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = row.subtitle,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
        if (!selectionMode) {
            IconButton(onClick = onMore) {
                Icon(
                    imageVector = MiuixIcons.Regular.More,
                    contentDescription = stringResource(R.string.song_more_actions_title),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

private class RecentSongLookup(songs: List<Song>) {
    private val songsById = HashMap<Long, Song>(songs.size)
    private val songsByMeta = HashMap<String, Song>(songs.size)

    init {
        for (song in songs) {
            songsById[song.id] = song
            val key = metadataKey(song.title, song.artist, song.album)
            if (key.isNotEmpty()) {
                songsByMeta.putIfAbsent(key, song)
            }
        }
    }

    fun resolve(entry: PlaybackHistoryEntry): Song? {
        val metaKey = metadataKey(entry.title, entry.artist, entry.album)
        return (if (metaKey.isNotEmpty()) songsByMeta[metaKey] else null)
            ?: songsById[entry.songId]
    }

    companion object {
        fun metadataKey(title: String, artist: String, album: String): String {
            val t = title.trim().lowercase(Locale.ROOT)
            val a = artist.trim().lowercase(Locale.ROOT)
            val b = album.trim().lowercase(Locale.ROOT)
            if (t.isEmpty() && a.isEmpty() && b.isEmpty()) return ""
            return "$t\u0000$a\u0000$b"
        }
    }
}

private fun buildRecentPlaybackRows(
    history: List<PlaybackHistoryEntry>,
    songs: List<Song>,
    playlists: List<UserPlaylist>,
    folderPlaylists: List<FolderPlaylist>,
    tab: RecentPlaybackTab
): List<RecentPlaybackRow> {
    val lookup = RecentSongLookup(songs)
    val resolved = history
        .sortedByDescending(PlaybackHistoryEntry::playedAt)
        .map { entry -> ResolvedRecentEntry(entry, lookup.resolve(entry)) }
    return buildRecentPlaybackRowsForResolved(resolved, playlists, folderPlaylists, tab)
}

private fun buildRecentPlaybackRowsForResolved(
    resolved: List<ResolvedRecentEntry>,
    playlists: List<UserPlaylist>,
    folderPlaylists: List<FolderPlaylist>,
    tab: RecentPlaybackTab
): List<RecentPlaybackRow> {
    return when (tab) {
        RecentPlaybackTab.Song -> resolved
            .distinctBy { it.song?.id?.takeIf { id -> id > 0L } ?: "meta:${it.entry.title}|${it.entry.artist}" }
            .map { it.asSongRow() }
        RecentPlaybackTab.Mv -> resolved
            .filter { it.song?.isAudioMv() == true }
            .map(ResolvedRecentEntry::asMvRow)
        RecentPlaybackTab.Playlist -> buildPlaylistRows(resolved, playlists)
        RecentPlaybackTab.FolderPlaylists -> buildFolderPlaylistRows(resolved, folderPlaylists)
        RecentPlaybackTab.Collection -> listOf(
            RecentPlaybackTab.Playlist,
            RecentPlaybackTab.Artist,
            RecentPlaybackTab.Album,
            RecentPlaybackTab.Folder,
            RecentPlaybackTab.FolderPlaylists,
            RecentPlaybackTab.Year,
            RecentPlaybackTab.Genre,
            RecentPlaybackTab.Composer,
            RecentPlaybackTab.Arranger,
            RecentPlaybackTab.Lyricist
        ).flatMap { collectionTab ->
            buildRecentPlaybackRowsForResolved(resolved, playlists, folderPlaylists, collectionTab)
                .map { row ->
                    row.copy(
                        key = "${collectionTab.routeValue}:${row.key}",
                        title = formatCollectionTitle(collectionTab, row.title)
                    )
                }
        }.sortedByDescending(RecentPlaybackRow::playedAt)
        RecentPlaybackTab.Artist -> groupRecentRows(resolved, tab) { song ->
            artistNamesForSong(song).ifEmpty { listOf(song.artist) }
        }
        RecentPlaybackTab.Album -> groupRecentRows(resolved, tab) { song ->
            listOf(song.album.ifBlank { "Unknown Album" })
        }
        RecentPlaybackTab.Folder -> groupRecentRows(resolved, tab) { song ->
            listOf(song.folderPathValue())
        }
        RecentPlaybackTab.Year -> groupRecentRows(resolved, tab) { song ->
            listOf(song.year.ifBlank { "Unknown" })
        }
        RecentPlaybackTab.Genre -> groupRecentRows(resolved, tab) { song ->
            splitGenreNames(song.genre).ifEmpty { listOf(song.genre) }
        }
        RecentPlaybackTab.Composer -> groupRecentRows(resolved, tab) { song ->
            listOf(song.composer.ifBlank { "Unknown" })
        }
        RecentPlaybackTab.Arranger -> groupRecentRows(resolved, tab) { song ->
            listOf(song.arranger.ifBlank { "Unknown" })
        }
        RecentPlaybackTab.Lyricist -> groupRecentRows(resolved, tab) { song ->
            listOf(song.lyricist.ifBlank { "Unknown" })
        }
    }
}

private fun ResolvedRecentEntry.asSongRow(): RecentPlaybackRow = RecentPlaybackRow(
    key = "song:${entry.entryId}",
    title = song?.title ?: entry.title,
    subtitle = listOf(song?.artist ?: entry.artist, song?.album ?: entry.album)
        .filter(String::isNotBlank)
        .joinToString(" · "),
    playedAt = entry.playedAt,
    song = song,
    entryIds = listOf(entry.entryId),
    kind = RecentPlaybackTab.Song,
    entityId = song?.id?.toString().orEmpty(),
    albumId = song?.albumId ?: 0L,
    rowSongs = listOfNotNull(song)
)

private fun ResolvedRecentEntry.asMvRow(): RecentPlaybackRow = RecentPlaybackRow(
    key = "mv:${entry.entryId}",
    title = song?.title ?: entry.title,
    subtitle = listOf(
        song?.artist ?: entry.artist,
        (song?.duration ?: entry.durationMs).takeIf { it > 0L }?.formatPlaybackDuration()
    ).filterNotNull().filter(String::isNotBlank).joinToString(" · "),
    playedAt = entry.playedAt,
    song = song,
    entryIds = listOf(entry.entryId),
    kind = RecentPlaybackTab.Mv,
    entityId = song?.id?.toString().orEmpty(),
    albumId = song?.albumId ?: 0L,
    rowSongs = listOfNotNull(song)
)

private fun groupRecentRows(
    resolved: List<ResolvedRecentEntry>,
    tab: RecentPlaybackTab,
    keys: (Song) -> List<String>
): List<RecentPlaybackRow> {
    val groups = linkedMapOf<String, MutableList<ResolvedRecentEntry>>()
    resolved.forEach { item ->
        item.song?.let { song ->
            keys(song)
                .map(String::trim)
                .filter(String::isNotBlank)
                .distinct()
                .forEach { key -> groups.getOrPut(key) { mutableListOf() } += item }
        }
    }
    return groups.map { (name, entries) ->
        val latest = entries.maxBy(ResolvedRecentEntry::entryPlayedAt)
        RecentPlaybackRow(
            key = "${tab.routeValue}:${name.lowercase(Locale.getDefault())}",
            title = name,
            subtitle = formatPlayedToSubtitle(entries.size, latest.song?.title ?: latest.entry.title, latest.song?.artist ?: latest.entry.artist),
            playedAt = latest.entry.playedAt,
            song = latest.song,
            entryIds = entries.map { it.entry.entryId }.distinct(),
            circularArt = tab in setOf(
                RecentPlaybackTab.Artist,
                RecentPlaybackTab.Composer,
                RecentPlaybackTab.Arranger,
                RecentPlaybackTab.Lyricist
            ),
            kind = tab,
            entityId = name,
            albumId = if (tab == RecentPlaybackTab.Album) latest.song?.albumId ?: 0L else 0L,
            rowSongs = entries.mapNotNull { it.song }.distinctBy { it.id }
        )
    }.sortedByDescending(RecentPlaybackRow::playedAt)
}

private fun buildPlaylistRows(
    resolved: List<ResolvedRecentEntry>,
    playlists: List<UserPlaylist>
): List<RecentPlaybackRow> {
    return playlists.mapNotNull { playlist ->
        val playlistKeys = playlist.songs.map { it.key }.toSet()
        val playlistIds = playlist.songs.map { it.id }.toSet()
        val matching = resolved.filter { item ->
            val song = item.song ?: return@filter false
            song.playlistIdentityKey() in playlistKeys || song.id in playlistIds
        }
        val latest = matching.maxByOrNull(ResolvedRecentEntry::entryPlayedAt) ?: return@mapNotNull null
        RecentPlaybackRow(
            key = "playlist:${playlist.id}",
            title = playlist.name,
            subtitle = formatPlayedToSubtitle(matching.size, latest.song?.title ?: latest.entry.title, latest.song?.artist ?: latest.entry.artist),
            playedAt = latest.entry.playedAt,
            song = latest.song,
            entryIds = matching.map { it.entry.entryId },
            kind = RecentPlaybackTab.Playlist,
            entityId = playlist.id,
            rowSongs = matching.mapNotNull { it.song }.distinctBy { it.id }
        )
    }.sortedByDescending(RecentPlaybackRow::playedAt)
}

private fun buildFolderPlaylistRows(
    resolved: List<ResolvedRecentEntry>,
    folderPlaylists: List<FolderPlaylist>
): List<RecentPlaybackRow> = folderPlaylists.mapNotNull { playlist ->
    val matching = resolved.filter { item ->
        val song = item.song ?: return@filter false
        val folderPath = song.folderPathValue()
        playlist.folders.any { configured ->
            val normalized = configured.trim().trimEnd('/')
            folderPath.equals(normalized, ignoreCase = true) ||
                folderPath.startsWith("$normalized/", ignoreCase = true) ||
                folderPath.substringAfterLast('/').equals(normalized.substringAfterLast('/'), ignoreCase = true)
        }
    }
    val latest = matching.maxByOrNull(ResolvedRecentEntry::entryPlayedAt) ?: return@mapNotNull null
    RecentPlaybackRow(
        key = "folder_playlist:${playlist.id}",
        title = playlist.name,
        subtitle = formatPlayedToSubtitle(matching.size, latest.song?.title ?: latest.entry.title, latest.song?.artist ?: latest.entry.artist),
        playedAt = latest.entry.playedAt,
        song = latest.song,
        entryIds = matching.map { it.entry.entryId },
        kind = RecentPlaybackTab.FolderPlaylists,
        entityId = playlist.id,
        rowSongs = matching.mapNotNull { it.song }.distinctBy { it.id }
    )
}.sortedByDescending(RecentPlaybackRow::playedAt)

private fun ResolvedRecentEntry.entryPlayedAt(): Long = entry.playedAt

private fun Song.isAudioMv(): Boolean =
    mimeType.startsWith("video/", ignoreCase = true) ||
        fileName.substringAfterLast('.', "").lowercase() in setOf("mp4", "m4v", "mkv", "webm", "mov")

private fun Song.folderPathValue(): String =
    path.substringBeforeLast('/', missingDelimiterValue = "").ifBlank { "Unknown" }

private data class RecentPlaybackDaySection(
    val dayKey: String,
    val rows: List<RecentPlaybackRow>
)

private fun groupRowsByPlayDay(rows: List<RecentPlaybackRow>): List<RecentPlaybackDaySection> {
    if (rows.isEmpty()) return emptyList()
    val sections = linkedMapOf<String, MutableList<RecentPlaybackRow>>()
    for (row in rows) {
        val key = playDayKey(row.playedAt)
        sections.getOrPut(key) { mutableListOf() }.add(row)
    }
    return sections.map { (key, sectionRows) ->
        RecentPlaybackDaySection(dayKey = key, rows = sectionRows)
    }
}

private fun playDayKey(timestampMs: Long): String =
    SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(timestampMs))

/** Section date text under sticky headers uses 今日 / yyyy.MM.dd. */
private fun isPlayDayToday(timestampMs: Long): Boolean {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = timestampMs }
    return now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)
}

private fun recentPlaybackSectionDate(timestampMs: Long): String =
    SimpleDateFormat("yyyy.MM.dd", Locale.getDefault()).format(Date(timestampMs))


private fun RecentPlaybackTab.countDescriptionRes(): Int = when (this) {
    RecentPlaybackTab.Collection -> R.string.recent_playback_count_collection
    RecentPlaybackTab.Song -> R.string.recent_playback_count_song
    RecentPlaybackTab.Mv -> R.string.recent_playback_count_mv
    RecentPlaybackTab.Playlist -> R.string.recent_playback_count_playlist
    RecentPlaybackTab.Artist -> R.string.recent_playback_count_artist
    RecentPlaybackTab.Album -> R.string.recent_playback_count_album
    RecentPlaybackTab.Folder -> R.string.recent_playback_count_folder
    RecentPlaybackTab.FolderPlaylists -> R.string.recent_playback_count_folder_playlist
    RecentPlaybackTab.Year -> R.string.recent_playback_count_year
    RecentPlaybackTab.Genre -> R.string.recent_playback_count_genre
    RecentPlaybackTab.Composer -> R.string.recent_playback_count_composer
    RecentPlaybackTab.Arranger -> R.string.recent_playback_count_arranger
    RecentPlaybackTab.Lyricist -> R.string.recent_playback_count_lyricist
}

private fun formatPlayedToSubtitle(songCount: Int, title: String, artist: String): String {
    val safeTitle = title.ifBlank { "Unknown" }
    val safeArtist = artist.ifBlank { "Unknown" }
    return "${songCount}首 · 播放到 $safeTitle - $safeArtist"
}

private fun formatCollectionTitle(tab: RecentPlaybackTab, name: String): String {
    val prefix = when (tab) {
        RecentPlaybackTab.Playlist -> "歌单"
        RecentPlaybackTab.Artist -> "艺术家"
        RecentPlaybackTab.Album -> "专辑"
        RecentPlaybackTab.Folder -> "文件夹"
        RecentPlaybackTab.FolderPlaylists -> "文件夹歌单"
        RecentPlaybackTab.Year -> "年份"
        RecentPlaybackTab.Genre -> "流派"
        RecentPlaybackTab.Composer -> "作曲家"
        RecentPlaybackTab.Arranger -> "编曲家"
        RecentPlaybackTab.Lyricist -> "作词家"
        else -> return name
    }
    return "$prefix：$name"
}


private fun recentRowDisplayName(row: RecentPlaybackRow): String {
    val title = row.title
    val idx = title.indexOf('：')
    return if (idx >= 0 && idx + 1 < title.length) title.substring(idx + 1) else title
}

private fun resolveRecentRowSongs(
    row: RecentPlaybackRow,
    mainViewModel: MainViewModel,
    playlists: List<UserPlaylist>,
    folderPlaylists: List<FolderPlaylist>,
    allSongs: List<Song>
): List<Song> {
    val fallback = row.rowSongs.ifEmpty { listOfNotNull(row.song) }
    return when (row.kind) {
        RecentPlaybackTab.Song, RecentPlaybackTab.Mv -> fallback
        RecentPlaybackTab.Playlist -> {
            val playlist = playlists.find { it.id == row.entityId }
            if (playlist != null) mainViewModel.playlistSongs(playlist).ifEmpty { fallback } else fallback
        }
        RecentPlaybackTab.FolderPlaylists -> {
            val fp = folderPlaylists.find { it.id == row.entityId }
            if (fp != null) allSongs.songsForFolderPlaylist(fp.folders).ifEmpty { fallback } else fallback
        }
        RecentPlaybackTab.Artist ->
            mainViewModel.getSongsForArtist(row.entityId).ifEmpty { fallback }
        RecentPlaybackTab.Album -> when {
            row.albumId > 0L -> mainViewModel.getSongsForAlbum(row.albumId).ifEmpty { fallback }
            row.entityId.isNotBlank() ->
                allSongs.filter { it.album.equals(row.entityId, ignoreCase = true) }.ifEmpty { fallback }
            else -> fallback
        }
        RecentPlaybackTab.Folder -> {
            val path = row.entityId
            allSongs.filter {
                val folder = it.path.substringBeforeLast('/', missingDelimiterValue = "")
                folder.equals(path, ignoreCase = true) ||
                    folder.startsWith("$path/", ignoreCase = true)
            }.ifEmpty { fallback }
        }
        RecentPlaybackTab.Year,
        RecentPlaybackTab.Genre,
        RecentPlaybackTab.Composer,
        RecentPlaybackTab.Arranger,
        RecentPlaybackTab.Lyricist ->
            mainViewModel.getSongsForMetadataCategory(row.kind.routeValue, row.entityId).ifEmpty { fallback }
        RecentPlaybackTab.Collection -> fallback
    }
}

@Composable
private fun buildRecentEntityActions(
    row: RecentPlaybackRow,
    displayName: String,
    resolvedSongs: List<Song>,
    playlists: List<UserPlaylist>,
    folderPlaylists: List<FolderPlaylist>,
    @Suppress("UNUSED_PARAMETER") context: android.content.Context,
    mainViewModel: MainViewModel,
    @Suppress("UNUSED_PARAMETER") playerViewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    onShare: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onRemoveFromRecent: () -> Unit,
    onDeletePermanently: () -> Unit,
    onExportPlaylist: (UserPlaylist) -> Unit,
    onRenamePlaylist: (UserPlaylist) -> Unit,
    onDeletePlaylist: (UserPlaylist) -> Unit,
    onRefreshFolderPlaylist: (FolderPlaylist) -> Unit,
    onAssociateFolders: (List<String>) -> Unit,
    onEditFolderPlaylist: (FolderPlaylist) -> Unit,
    onDeleteFolderPlaylist: (FolderPlaylist) -> Unit,
    onMvInfo: (Song) -> Unit,
    onDesktopShortcut: (id: String, label: String, route: String) -> Unit
): List<LibraryEntityAction> {
    val removeRecent = LibraryEntityAction(
        title = stringResource(R.string.recent_playback_remove_from_recent),
        icon = actionMenuIcon(ActionMenuIds.REMOVE_FROM_RECENT_PLAYBACK),
        danger = true,
        onClick = onRemoveFromRecent
    )
    val share = LibraryEntityActions.share(onShare)
    val addPlaylist = LibraryEntityActions.addToPlaylist(onAddToPlaylist)
    val addQueue = LibraryEntityActions.addToQueue(onAddToQueue)
    val playNext = LibraryEntityActions.playNext(onPlayNext)
    val deleteSongs = LibraryEntityActions.deletePermanently(onDeletePermanently)
    val deleteEntity = LibraryEntityActions.delete(onDeletePermanently)
    val mvInfoTitle = stringResource(R.string.song_more_view_song_info)
    val mvInfoIcon = actionMenuIcon(ActionMenuIds.INFO)
    val exportAction = LibraryEntityActions.export {
        playlists.find { it.id == row.entityId }?.let(onExportPlaylist)
    }
    val renameAction = LibraryEntityActions.rename {
        playlists.find { it.id == row.entityId }?.let(onRenamePlaylist)
    }
    val deletePlaylistAction = LibraryEntityActions.delete {
        playlists.find { it.id == row.entityId }?.let(onDeletePlaylist)
    }
    val refreshFolderPlaylistAction = LibraryEntityActions.refresh {
        folderPlaylists.find { it.id == row.entityId }?.let(onRefreshFolderPlaylist)
    }
    val associateFolderPlaylistAction = LibraryEntityActions.associate {
        folderPlaylists.find { it.id == row.entityId }?.folders?.let(onAssociateFolders)
    }
    val editFolderPlaylistAction = LibraryEntityActions.edit {
        folderPlaylists.find { it.id == row.entityId }?.let(onEditFolderPlaylist)
    }
    val deleteFolderPlaylistAction = LibraryEntityActions.delete {
        folderPlaylists.find { it.id == row.entityId }?.let(onDeleteFolderPlaylist)
    }
    val refreshFolderAction = LibraryEntityActions.refresh {
        mainViewModel.refreshFolderPlaylistFolders(listOf(row.entityId))
        onDismiss()
    }
    val associateFolderAction = LibraryEntityActions.associate {
        onAssociateFolders(listOf(row.entityId))
    }

    val playlist = playlists.find { it.id == row.entityId }
    val folderPlaylist = folderPlaylists.find { it.id == row.entityId }

    val playlistShortcut = if (playlist != null) {
        LibraryEntityActions.desktopShortcut {
            onDesktopShortcut(
                "playlist_${playlist.id}",
                playlist.name,
                Screen.PlaylistDetail.createRoute(playlist.id)
            )
        }
    } else null
    val folderPlaylistShortcut = if (folderPlaylist != null) {
        LibraryEntityActions.desktopShortcut {
            onDesktopShortcut(
                "folder_playlist_${folderPlaylist.id}",
                folderPlaylist.name,
                Screen.FolderPlaylistDetail.createRoute(folderPlaylist.id)
            )
        }
    } else null
    val artistShortcut = LibraryEntityActions.desktopShortcut {
        onDesktopShortcut(
            "artist_${row.entityId.tagIdentityKey()}",
            displayName,
            Screen.ArtistDetail.createRoute(row.entityId)
        )
    }
    val albumShortcut = if (row.albumId > 0L) {
        LibraryEntityActions.desktopShortcut {
            onDesktopShortcut(
                "album_${row.albumId}",
                displayName,
                Screen.AlbumDetail.createRoute(row.albumId)
            )
        }
    } else null
    val folderShortcut = LibraryEntityActions.desktopShortcut {
        onDesktopShortcut(
            "folder_${row.entityId.tagIdentityKey()}",
            displayName.ifBlank { row.entityId.substringAfterLast('/') },
            Screen.FolderDetail.createRoute(row.entityId)
        )
    }
    val categoryShortcut = LibraryEntityActions.desktopShortcut {
        onDesktopShortcut(
            "category_${row.kind.routeValue}_${row.entityId.tagIdentityKey()}",
            displayName,
            Screen.MetadataCategoryDetail.createRoute(row.kind.routeValue, row.entityId)
        )
    }
    val mvInfoAction = row.song?.let { song ->
        LibraryEntityAction(
            title = mvInfoTitle,
            icon = mvInfoIcon,
            onClick = { onMvInfo(song) }
        )
    }

    return when (row.kind) {
        RecentPlaybackTab.Mv -> listOfNotNull(
            share,
            mvInfoAction,
            removeRecent,
            deleteSongs.takeIf { resolvedSongs.isNotEmpty() }
        )
        RecentPlaybackTab.Playlist -> listOfNotNull(
            exportAction.takeIf { playlist != null },
            share,
            addPlaylist,
            addQueue,
            playNext,
            renameAction.takeIf { playlist != null },
            playlistShortcut,
            removeRecent,
            deletePlaylistAction.takeIf { playlist != null }
        )
        RecentPlaybackTab.FolderPlaylists -> listOfNotNull(
            refreshFolderPlaylistAction.takeIf { folderPlaylist != null },
            share,
            associateFolderPlaylistAction.takeIf { folderPlaylist != null },
            addPlaylist,
            addQueue,
            playNext,
            editFolderPlaylistAction.takeIf { folderPlaylist != null },
            folderPlaylistShortcut,
            removeRecent,
            deleteFolderPlaylistAction.takeIf { folderPlaylist != null }
        )
        RecentPlaybackTab.Folder -> listOfNotNull(
            refreshFolderAction,
            share,
            associateFolderAction,
            addPlaylist,
            addQueue,
            playNext,
            folderShortcut,
            removeRecent,
            deleteEntity.takeIf { resolvedSongs.isNotEmpty() }
        )
        RecentPlaybackTab.Artist -> listOfNotNull(
            share, addPlaylist, addQueue, playNext, artistShortcut, removeRecent,
            deleteSongs.takeIf { resolvedSongs.isNotEmpty() }
        )
        RecentPlaybackTab.Album -> listOfNotNull(
            share, addPlaylist, addQueue, playNext, albumShortcut, removeRecent,
            deleteSongs.takeIf { resolvedSongs.isNotEmpty() }
        )
        RecentPlaybackTab.Year,
        RecentPlaybackTab.Genre,
        RecentPlaybackTab.Composer,
        RecentPlaybackTab.Arranger,
        RecentPlaybackTab.Lyricist -> listOfNotNull(
            share, addPlaylist, addQueue, playNext, categoryShortcut, removeRecent,
            deleteSongs.takeIf { resolvedSongs.isNotEmpty() }
        )
        else -> listOfNotNull(
            share, addPlaylist, addQueue, playNext, removeRecent,
            deleteSongs.takeIf { resolvedSongs.isNotEmpty() }
        )
    }
}

