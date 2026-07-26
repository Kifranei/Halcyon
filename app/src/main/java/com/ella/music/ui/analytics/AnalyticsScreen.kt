package com.ella.music.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AnalyticsScreen(
    mainViewModel: MainViewModel,
    onBack: () -> Unit,
    showBackButton: Boolean = true,
    onNavigateToHistory: () -> Unit = {}
) {
    val context = LocalContext.current
    val songs by mainViewModel.songs.collectAsState()
    val playbackStats by mainViewModel.playbackStats.collectAsState()
    val playbackHistory by mainViewModel.playbackHistory.collectAsState()
    val dailyListenMs by mainViewModel.dailyListenMs.collectAsState()
    val libraryById = remember(songs) { songs.associateBy { it.id } }
    val libraryByStatsKey = remember(songs) { songs.associateBy { it.analyticsStatsKey() } }
    val monthlyReport = remember(playbackHistory, dailyListenMs, songs) {
        buildMonthlyListeningReport(
            history = playbackHistory,
            dailyListenMs = dailyListenMs,
            librarySongs = songs
        )
    }
    val tasteProfile = remember(playbackStats, libraryById, libraryByStatsKey) {
        buildTasteProfile(
            stats = playbackStats,
            libraryById = libraryById,
            libraryByStatsKey = libraryByStatsKey
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
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
            } else {
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = stringResource(R.string.analytics_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onBackground,
                modifier = Modifier.padding(start = 8.dp)
            )
        }

        LazyColumn(
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
                MonthlyListeningReportCard(report = monthlyReport)
            }

            item {
                MonthlyFavoritesCard(
                    report = monthlyReport,
                    mainViewModel = mainViewModel
                )
            }

            item {
                ListeningHabitCard(report = monthlyReport)
            }

            item {
                TasteProfileCard(profile = tasteProfile)
            }

            item {
                ListenHeatmapCard(dailyListenMs = dailyListenMs)
            }

            item {
                HistoryCard(
                    history = playbackHistory.take(20),
                    totalCount = playbackHistory.size,
                    libraryById = libraryById,
                    libraryByStatsKey = libraryByStatsKey,
                    mainViewModel = mainViewModel,
                    onClick = onNavigateToHistory
                )
            }

            item {
                RankingCard(
                    title = stringResource(R.string.analytics_listen_duration_ranking),
                    emptyText = stringResource(R.string.analytics_listen_duration_empty),
                    stats = playbackStats
                        .filter { it.listenedMs > 0L }
                        .sortedByDescending { it.listenedMs }
                        .take(10),
                    libraryById = libraryById,
                    libraryByStatsKey = libraryByStatsKey,
                    mainViewModel = mainViewModel,
                    valueText = { formatListenDuration(context, it.listenedMs) }
                )
            }

            item {
                RankingCard(
                    title = stringResource(R.string.analytics_play_count_ranking),
                    emptyText = stringResource(R.string.analytics_play_count_empty),
                    stats = playbackStats
                        .filter { it.playCount > 0 }
                        .sortedByDescending { it.playCount }
                        .take(10),
                    libraryById = libraryById,
                    libraryByStatsKey = libraryByStatsKey,
                    mainViewModel = mainViewModel,
                    valueText = { context.getString(R.string.analytics_times_count, it.playCount) }
                )
            }
        }
    }
}
