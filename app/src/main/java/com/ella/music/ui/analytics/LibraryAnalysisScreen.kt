package com.ella.music.ui.analytics

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
import com.ella.music.ui.components.SongMenuItem
import com.ella.music.ui.components.SongSheetColumn
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
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    val songs by mainViewModel.songs.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val playlists by mainViewModel.playlists.collectAsState()
    var selectedBucket by remember {
        mutableStateOf(
            if (!initialBucketLabel.isNullOrBlank() && initialQualityBucket != null) {
                initialQualityBucket to initialBucketLabel
            } else {
                null
            }
        )
    }
    val analysisListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var matchingSongs by remember { mutableStateOf<List<Song>?>(null) }
    var actionBucket by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    var actionSongs by remember { mutableStateOf<List<Song>?>(null) }
    var playlistPickerSongs by remember { mutableStateOf<List<Song>?>(null) }
    var createPlaylistSongs by remember { mutableStateOf<List<Song>?>(null) }
    var pendingDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val deleteSongs = rememberSongDeleteResultHandler(mainViewModel)
    val analysisCacheKey = remember(songs) { songs.libraryAnalysisCacheKey() }
    val analysis by produceState<LibraryAnalysis?>(
        initialValue = if (songs.isEmpty()) {
            LibraryAnalysis(emptyList(), emptyList(), 0, 0L)
        } else {
            LibraryAnalysisSessionCache.get(analysisCacheKey)
        },
        songs
    ) {
        if (songs.isEmpty()) {
            value = LibraryAnalysis(emptyList(), emptyList(), 0, 0L)
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
        val (quality, label) = bucket
        val cachedKeys = currentAnalysis
            ?.let { if (quality) it.qualityBuckets else it.formatBuckets }
            ?.firstOrNull { it.label == label }?.songKeys.orEmpty()
        if (cachedKeys.isNotEmpty()) {
            val keySet = cachedKeys.toSet()
            matchingSongs = songs.filter { it.searchIdentityKey() in keySet }
            return@LaunchedEffect
        }
        matchingSongs = withContext(Dispatchers.IO) {
            songs.filter { song ->
                val info = mainViewModel.getAudioInfo(song)
                if (quality) qualityLabel(song, info) == label else formatLabel(song, info) == label
            }
        }
    }

    LaunchedEffect(actionBucket, songs, analysis) {
        val bucket = actionBucket ?: run {
            actionSongs = null
            return@LaunchedEffect
        }
        actionSongs = null
        val (quality, label) = bucket
        val cachedKeys = analysis
            ?.let { if (quality) it.qualityBuckets else it.formatBuckets }
            ?.firstOrNull { it.label == label }?.songKeys.orEmpty()
        val matchedSongs = if (cachedKeys.isNotEmpty()) {
            val keySet = cachedKeys.toSet()
            songs.filter { it.searchIdentityKey() in keySet }
        } else withContext(Dispatchers.IO) {
            songs.filter { song ->
                val info = mainViewModel.getAudioInfo(song)
                if (quality) qualityLabel(song, info) == label else formatLabel(song, info) == label
            }
        }
        val sourceKey = com.ella.music.data.CategoryResumeKeys.analysis(quality, label)
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
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyColumn(
            state = analysisListState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 12.dp,
                end = 12.dp,
                top = 12.dp,
                bottom = 160.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SummaryCard(
                    songs = songs,
                    playbackStats = playbackStats
                )
            }

            item {
                DonutChartCard(
                    title = stringResource(R.string.analytics_audio_format_stats),
                    loadingText = stringResource(R.string.analytics_loading_audio_formats),
                    buckets = analysis?.formatBuckets,
                    total = analysis?.totalCount ?: 0,
                    totalSizeBytes = analysis?.totalSizeBytes ?: 0L,
                    palette = formatPalette,
                    onBucketClick = {
                        selectedBucket = false to it.label
                    },
                    onBucketLongClick = { actionBucket = false to it.label }
                )
            }

            item {
                DonutChartCard(
                    title = stringResource(R.string.analytics_audio_quality_stats),
                    loadingText = stringResource(R.string.analytics_loading_audio_quality),
                    buckets = analysis?.qualityBuckets,
                    total = analysis?.totalCount ?: 0,
                    totalSizeBytes = analysis?.totalSizeBytes ?: 0L,
                    palette = analysis?.qualityBuckets?.map { qualityBucketColor(it.label) } ?: qualityPalette,
                    onBucketClick = {
                        selectedBucket = true to it.label
                    },
                    onBucketLongClick = { actionBucket = true to it.label }
                )
            }
        }
    }

    selectedBucket?.let { (quality, label) ->
        LibraryAnalysisBucketDetailScreen(
            bucketLabel = label,
            qualityBucket = quality,
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


    actionBucket?.let { (quality, label) ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = label,
            onDismissRequest = { actionBucket = null }
        ) {
            val bucketSongs = actionSongs
            if (bucketSongs == null) {
                EllaCenteredLoadingIndicator(modifier = Modifier.padding(24.dp))
            } else {
                SongSheetColumn {
                    SongMenuItem(stringResource(R.string.common_share), onClick = {
                        shareLocalSongs(context, bucketSongs)
                        actionBucket = null
                    })
                    SongMenuItem(stringResource(R.string.song_more_add_to_playlist), onClick = {
                        playlistPickerSongs = bucketSongs
                        actionBucket = null
                    })
                    SongMenuItem(stringResource(R.string.common_add_to_queue), onClick = {
                        playerViewModel.addToPlaylist(bucketSongs)
                        actionBucket = null
                    })
                    SongMenuItem(stringResource(R.string.song_more_play_next), onClick = {
                        playerViewModel.playNext(bucketSongs)
                        actionBucket = null
                    })
                    SongMenuItem(stringResource(R.string.common_add_desktop_shortcut), onClick = {
                        requestPinnedEllaShortcut(
                            context,
                            "analysis_${if (quality) "quality" else "format"}_$label",
                            label,
                            Screen.LibraryAnalysis.createBucketRoute(quality, label)
                        )
                        actionBucket = null
                    })
                    SongMenuItem(stringResource(R.string.song_more_delete_permanently), onClick = {
                        pendingDeleteSongs = bucketSongs
                        actionBucket = null
                    }, danger = true)
                }
            }
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
