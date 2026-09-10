package com.ella.music.ui.analytics

import android.view.HapticFeedbackConstants
import androidx.compose.ui.platform.LocalView

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.EllaCenteredLoadingIndicator
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.LibraryEntityActionSheet
import com.ella.music.ui.components.LibraryEntityActions
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.rememberSongDeleteResultHandler
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.navigation.Screen
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.search.searchIdentityKey
import com.ella.music.ui.home.cachedSortedForHomeMode
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.DropdownEntry
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Filter
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup

@Composable
fun LibraryAnalysisScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    onNavigateToPlayer: () -> Unit = {},
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {},
    initialQualityBucket: Boolean? = null,
    initialBucketLabel: String? = null
) {
    val context = LocalContext.current
    val view = LocalView.current
    val songs by mainViewModel.songs.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    var currentDimension by remember { mutableStateOf(AnalysisDimension.FORMAT) }
    var currentMetric by remember { mutableStateOf(AnalysisMetric.SIZE) }
    var filterMenuVisible by remember { mutableStateOf(false) }
    var selectedBucket by remember {
        mutableStateOf<Pair<AnalysisDimension, String>?>(
            if (!initialBucketLabel.isNullOrBlank() && initialQualityBucket != null) {
                (if (initialQualityBucket) AnalysisDimension.QUALITY else AnalysisDimension.FORMAT) to initialBucketLabel
            } else {
                null
            }
        )
    }
    val analysisListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var matchingSongs by remember { mutableStateOf<List<Song>?>(null) }
    var actionBucket by remember { mutableStateOf<Pair<AnalysisDimension, String>?>(null) }
    var actionSongs by remember { mutableStateOf<List<Song>?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val deleteSongs = rememberSongDeleteResultHandler(mainViewModel)
    val analysisCacheKey = remember(songs) { songs.libraryAnalysisCacheKey() }
    val analysis by produceState<LibraryAnalysis?>(
        initialValue = if (songs.isEmpty()) {
            LibraryAnalysis(emptyList(), emptyList(), emptyList(), emptyList(), 0, 0L)
        } else {
            LibraryAnalysisSessionCache.get(analysisCacheKey)
        },
        songs
    ) {
        if (songs.isEmpty()) {
            value = LibraryAnalysis(emptyList(), emptyList(), emptyList(), emptyList(), 0, 0L)
            return@produceState
        }
        val cachedAnalysis = withContext(Dispatchers.IO) { readCachedLibraryAnalysis(context, songs) }
        if (cachedAnalysis != null) {
            value = cachedAnalysis
            return@produceState
        }
        val fresh = withContext(Dispatchers.IO) { buildLibraryAnalysis(songs, mainViewModel) }
        withContext(Dispatchers.IO) {
            writeCachedLibraryAnalysis(context, songs, fresh)
        }
        value = fresh
    }

    LaunchedEffect(selectedBucket, songs, analysis) {
        val bucket = selectedBucket
        if (bucket == null) {
            matchingSongs = null
            return@LaunchedEffect
        }
        val currentAnalysis = analysis
        val (dimension, label) = bucket
        val cachedKeys = currentAnalysis
            ?.getBuckets(dimension)
            ?.firstOrNull { it.label == label }?.songKeys.orEmpty()
        if (cachedKeys.isNotEmpty()) {
            val keySet = cachedKeys.toSet()
            matchingSongs = songs.filter { it.searchIdentityKey() in keySet }
            return@LaunchedEffect
        }
        matchingSongs = withContext(Dispatchers.IO) {
            songs.filter { song ->
                val info = mainViewModel.getAudioInfo(song)
                when (dimension) {
                    AnalysisDimension.FORMAT -> formatLabel(song, info) == label
                    AnalysisDimension.QUALITY -> qualityLabel(song, info) == label
                    AnalysisDimension.SAMPLE_RATE -> sampleRateLabel(info) == label
                    AnalysisDimension.BIT_DEPTH -> bitDepthLabel(info) == label
                }
            }
        }
    }

    LaunchedEffect(actionBucket, songs, analysis) {
        val bucket = actionBucket ?: run {
            actionSongs = null
            return@LaunchedEffect
        }
        actionSongs = null
        val (dimension, label) = bucket
        val cachedKeys = analysis
            ?.getBuckets(dimension)
            ?.firstOrNull { it.label == label }?.songKeys.orEmpty()
        val matchedSongs = if (cachedKeys.isNotEmpty()) {
            val keySet = cachedKeys.toSet()
            songs.filter { it.searchIdentityKey() in keySet }
        } else withContext(Dispatchers.IO) {
            songs.filter { song ->
                val info = mainViewModel.getAudioInfo(song)
                when (dimension) {
                    AnalysisDimension.FORMAT -> formatLabel(song, info) == label
                    AnalysisDimension.QUALITY -> qualityLabel(song, info) == label
                    AnalysisDimension.SAMPLE_RATE -> sampleRateLabel(info) == label
                    AnalysisDimension.BIT_DEPTH -> bitDepthLabel(info) == label
                }
            }
        }
        val sourceKey = com.ella.music.data.CategoryResumeKeys.analysis(dimension == AnalysisDimension.QUALITY, label)
        actionSongs = matchedSongs.cachedSortedForHomeMode(
            LibraryAnalysisBucketSortState.get(sourceKey)
        ).songs
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .then(
                if (selectedBucket != null) {
                    Modifier.height(0.dp).clipToBounds()
                } else {
                    Modifier
                }
            )
            .background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Back,
                        contentDescription = stringResource(R.string.common_back),
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Text(
                text = stringResource(R.string.analytics_library_analysis),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )

            Box(modifier = Modifier.padding(end = 4.dp)) {
                IconButton(onClick = {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    filterMenuVisible = true
                }) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Filter,
                        contentDescription = stringResource(R.string.analytics_filter_title),
                        tint = MiuixTheme.colorScheme.onBackground,
                        modifier = Modifier.size(24.dp)
                    )
                }

                val filterEntries = listOf(
                    DropdownEntry(
                        items = listOf(
                            DropdownItem(
                                text = stringResource(R.string.analytics_dimension_format),
                                selected = currentDimension == AnalysisDimension.FORMAT,
                                onClick = {
                                    currentDimension = AnalysisDimension.FORMAT
                                    filterMenuVisible = false
                                }
                            ),
                            DropdownItem(
                                text = stringResource(R.string.analytics_dimension_quality),
                                selected = currentDimension == AnalysisDimension.QUALITY,
                                onClick = {
                                    currentDimension = AnalysisDimension.QUALITY
                                    filterMenuVisible = false
                                }
                            ),
                            DropdownItem(
                                text = stringResource(R.string.analytics_dimension_sample_rate),
                                selected = currentDimension == AnalysisDimension.SAMPLE_RATE,
                                onClick = {
                                    currentDimension = AnalysisDimension.SAMPLE_RATE
                                    filterMenuVisible = false
                                }
                            ),
                            DropdownItem(
                                text = stringResource(R.string.analytics_dimension_bit_depth),
                                selected = currentDimension == AnalysisDimension.BIT_DEPTH,
                                onClick = {
                                    currentDimension = AnalysisDimension.BIT_DEPTH
                                    filterMenuVisible = false
                                }
                            )
                        )
                    ),
                    DropdownEntry(
                        items = listOf(
                            DropdownItem(
                                text = stringResource(R.string.analytics_metric_size),
                                selected = currentMetric == AnalysisMetric.SIZE,
                                onClick = {
                                    currentMetric = AnalysisMetric.SIZE
                                    filterMenuVisible = false
                                }
                            ),
                            DropdownItem(
                                text = stringResource(R.string.analytics_metric_count),
                                selected = currentMetric == AnalysisMetric.COUNT,
                                onClick = {
                                    currentMetric = AnalysisMetric.COUNT
                                    filterMenuVisible = false
                                }
                            )
                        )
                    )
                )

                WindowListPopup(
                    show = filterMenuVisible,
                    alignment = PopupPositionProvider.Align.End,
                    onDismissRequest = { filterMenuVisible = false }
                ) {
                    ListPopupColumn {
                        val lastEntryIdx = filterEntries.lastIndex
                        filterEntries.forEachIndexed { entryIdx, entry ->
                            val lastItemIdx = entry.items.lastIndex
                            val isFirstEntry = entryIdx == 0
                            val isLastEntry = entryIdx == lastEntryIdx
                            entry.items.forEachIndexed { itemIdx, option ->
                                DropdownImpl(
                                    item = option,
                                    optionSize = entry.items.size,
                                    isSelected = option.selected,
                                    index = itemIdx,
                                    enabled = true,
                                    isFirst = isFirstEntry && itemIdx == 0,
                                    isLast = isLastEntry && itemIdx == lastItemIdx,
                                    onSelectedIndexChange = {
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        option.onClick?.invoke()
                                    }
                                )
                            }
                            if (entryIdx < lastEntryIdx) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                                    thickness = 1.dp
                                )
                            }
                        }
                    }
                }
            }
        }

        val totalSongsCount = analysis?.totalCount ?: songs.size
        val totalSongsSize = analysis?.totalSizeBytes ?: songs.sumOf { it.fileSize }
        if (totalSongsCount > 0) {
            Text(
                text = stringResource(R.string.analytics_header_summary, totalSongsCount, formatFileSize(totalSongsSize)),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 4.dp)
            )
        }

        LazyColumn(
            state = analysisListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 8.dp,
                bottom = 160.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                val currentBuckets = analysis?.getBuckets(currentDimension)
                val palette = when (currentDimension) {
                    AnalysisDimension.FORMAT -> xiaomiStoragePalette
                    AnalysisDimension.QUALITY -> currentBuckets?.map { qualityBucketColor(it.label) } ?: qualityPalette
                    AnalysisDimension.SAMPLE_RATE -> sampleRatePalette
                    AnalysisDimension.BIT_DEPTH -> bitDepthPalette
                }
                Xiaomi3DCylinderStorageCard(
                    title = stringResource(currentDimension.labelRes),
                    loadingText = stringResource(R.string.analytics_loading_data),
                    buckets = currentBuckets,
                    total = totalSongsCount,
                    totalSizeBytes = totalSongsSize,
                    palette = palette,
                    metric = currentMetric,
                    onBucketClick = {
                        selectedBucket = currentDimension to it.label
                    },
                    onBucketLongClick = { actionBucket = currentDimension to it.label }
                )
            }
        }
    }

    selectedBucket?.let { (dimension, label) ->
        LibraryAnalysisBucketDetailScreen(
            bucketLabel = label,
            qualityBucket = (dimension == AnalysisDimension.QUALITY),
            songs = matchingSongs.orEmpty(),
            songsLoading = matchingSongs == null,
            totalLibraryCount = songs.size,
            mainViewModel = mainViewModel,
            playerViewModel = playerViewModel,
            onBack = {
                if (!initialBucketLabel.isNullOrBlank()) onBack() else selectedBucket = null
            },
            onNavigateToPlayer = onNavigateToPlayer,
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist
        )
    }


    actionBucket?.let { (dimension, label) ->
        val bucketSongs = actionSongs
        if (bucketSongs == null) {
            EllaMiuixBottomSheet(
                show = true,
                enableNestedScroll = false,
                title = label,
                onDismissRequest = { actionBucket = null }
            ) {
                EllaCenteredLoadingIndicator(modifier = Modifier.padding(24.dp))
            }
        } else {
            LibraryEntityActionSheet(
                show = true,
                title = label,
                onDismissRequest = { actionBucket = null },
                actions = listOf(
                    LibraryEntityActions.share {
                        shareLocalSongs(context, bucketSongs)
                        actionBucket = null
                    },
                    LibraryEntityActions.addToPlaylist {
                        playlistPickerSongs = bucketSongs
                        actionBucket = null
                    },
                    LibraryEntityActions.addToQueue {
                        playerViewModel.addToPlaylist(bucketSongs)
                        actionBucket = null
                    },
                    LibraryEntityActions.playNext {
                        playerViewModel.playNext(bucketSongs)
                        actionBucket = null
                    },
                    LibraryEntityActions.desktopShortcut {
                        requestPinnedEllaShortcut(
                            context,
                            "analysis_${if (dimension == AnalysisDimension.QUALITY) "quality" else "format"}_$label",
                            label,
                            Screen.LibraryAnalysis.createBucketRoute(dimension == AnalysisDimension.QUALITY, label)
                        )
                        actionBucket = null
                    },
                    LibraryEntityActions.deletePermanently {
                        pendingDeleteSongs = bucketSongs
                        actionBucket = null
                    }
                )
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
                onPlaylistsConfirm = { selected, appendToEnd ->
                    selected.forEach { mainViewModel.addSongsToPlaylist(it.id, songsToAdd, appendToEnd) }
                    playlistPickerSongs = null
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        CreatePlaylistAndAddSheet(
            onDismiss = { createPlaylistSongs = null },
            onCreate = { name ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                    createPlaylistSongs = null
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
            val target = pendingDeleteSongs
            pendingDeleteSongs = emptyList()
            deleteSongs(target)
        }
    )
    }
}
