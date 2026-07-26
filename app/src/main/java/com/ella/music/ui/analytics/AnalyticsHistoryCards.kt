package com.ella.music.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun PlaybackHistoryScreen(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
    onNavigateToAlbum: (Long) -> Unit = {},
    onNavigateToArtist: (String) -> Unit = {}
) {
    ListeningCalendarHistoryScreen(
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onBack = onBack,
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist
    )
}

@Composable
internal fun ListenHeatmapCard(dailyListenMs: Map<String, Long>) {
    val context = LocalContext.current
    val days = remember(dailyListenMs) { recentDateKeys(56) }
    val maxMs = days.maxOfOrNull { dailyListenMs[it] ?: 0L }?.coerceAtLeast(1L) ?: 1L
    val todayListenMs = days.lastOrNull()?.let { dailyListenMs[it] } ?: 0L
    var selectedDate by remember(days) { mutableStateOf(days.lastOrNull().orEmpty()) }
    val selectedListenMs = dailyListenMs[selectedDate] ?: 0L
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = analyticsWallpaperCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.analytics_heatmap_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.analytics_recent_8_weeks),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Spacer(modifier = Modifier.height(12.dp))
            if (selectedDate.isNotBlank()) {
                Text(
                    text = stringResource(
                        R.string.analytics_heatmap_selected,
                        selectedDate,
                        formatListenDuration(context, selectedListenMs)
                    ),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
                Spacer(modifier = Modifier.height(10.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                days.chunked(7).forEach { week ->
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        week.forEach { date ->
                            val listenedMs = dailyListenMs[date] ?: 0L
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(13.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(heatmapColor(listenedMs, maxMs))
                                    .clickable { selectedDate = date }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.analytics_heatmap_low),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.width(6.dp))
                listOf(0.16f, 0.36f, 0.58f, 0.82f).forEach { level ->
                    Box(
                        modifier = Modifier
                            .size(13.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(heatmapColor((maxMs * level).toLong(), maxMs))
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                Text(
                    text = stringResource(R.string.analytics_heatmap_high),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = stringResource(R.string.analytics_heatmap_today, formatListenDuration(context, todayListenMs)),
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
        }
    }
}

@Composable
internal fun HistoryCard(
    history: List<PlaybackHistoryEntry>,
    totalCount: Int,
    libraryById: Map<Long, Song>,
    libraryByStatsKey: Map<String, Song>,
    mainViewModel: MainViewModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = analyticsWallpaperCardColors(),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.analytics_listening_history_title),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    if (totalCount > history.size) {
                        Text(
                            text = stringResource(R.string.analytics_view_all_records, totalCount),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                    }
                }
                Icon(
                    imageVector = MiuixIcons.Basic.ArrowRight,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (history.isEmpty()) {
                Text(
                    text = stringResource(R.string.analytics_history_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                history.forEach { entry ->
                    HistoryRow(
                        entry = entry,
                        song = libraryById[entry.songId] ?: libraryByStatsKey[entry.analyticsStatsKey()],
                        mainViewModel = mainViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryRow(
    entry: PlaybackHistoryEntry,
    song: Song?,
    mainViewModel: MainViewModel,
    timeText: String = formatHistoryTime(entry.playedAt)
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AnalyticsSongCover(
            song = song,
            mainViewModel = mainViewModel,
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: entry.title,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song?.artist ?: entry.artist,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = timeText,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(min = 72.dp)
        )
    }
}
