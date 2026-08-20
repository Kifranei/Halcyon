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
    onNavigateToArtist: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    var selectedBucket by remember { mutableStateOf<Pair<Boolean, String>?>(null) }
    val analysisListState = rememberSaveable(saver = LazyListState.Saver) { LazyListState() }
    var matchingSongs by remember { mutableStateOf<List<Song>?>(null) }
    val analysis by produceState<LibraryAnalysis?>(
        initialValue = if (songs.isEmpty()) LibraryAnalysis(emptyList(), emptyList(), 0, 0L) else null,
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

    LaunchedEffect(selectedBucket, songs) {
        val bucket = selectedBucket
        if (bucket == null) {
            matchingSongs = null
            return@LaunchedEffect
        }
        matchingSongs = null
        val (quality, label) = bucket
        matchingSongs = withContext(Dispatchers.IO) {
            songs.filter { song ->
                val info = mainViewModel.getAudioInfo(song)
                if (quality) qualityLabel(song, info) == label else formatLabel(song, info) == label
            }
        }
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
                        matchingSongs = null
                        selectedBucket = false to it.label
                    }
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
                        matchingSongs = null
                        selectedBucket = true to it.label
                    }
                )
            }
        }
    }

    selectedBucket?.let { (_, label) ->
        LibraryAnalysisBucketDetailScreen(
            bucketLabel = label,
            songs = matchingSongs.orEmpty(),
            songsLoading = matchingSongs == null,
            totalLibraryCount = songs.size,
            mainViewModel = mainViewModel,
            playerViewModel = playerViewModel,
            onBack = { selectedBucket = null },
            onNavigateToPlayer = onNavigateToPlayer,
            onNavigateToAlbum = onNavigateToAlbum,
            onNavigateToArtist = onNavigateToArtist
        )
    }
    }
}
