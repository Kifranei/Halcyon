package com.ella.music.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.LibraryNormalizer
import com.ella.music.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MonthlyListeningReportCard(report: MonthlyListeningReport) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = analyticsWallpaperCardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF2B4CFF).copy(alpha = 0.78f),
                            Color(0xFF8A4DFF).copy(alpha = 0.62f),
                            Color(0xFFFF6F91).copy(alpha = 0.46f)
                        )
                    )
                )
                .padding(18.dp)
        ) {
            Text(
                text = stringResource(R.string.analytics_month_report_title),
                fontSize = 14.sp,
                color = Color.White.copy(alpha = 0.82f),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.analytics_month_report_summary, report.monthTitle, report.uniqueSongCount),
                fontSize = 25.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = stringResource(R.string.analytics_month_report_subtitle, formatListenDuration(context, report.listenedMs), report.activeDays),
                fontSize = 13.sp,
                color = Color.White.copy(alpha = 0.78f),
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MonthlyMetricPill(
                    label = stringResource(R.string.analytics_month_total_listen),
                    value = formatListenDuration(context, report.listenedMs),
                    modifier = Modifier.weight(1f)
                )
                MonthlyMetricPill(
                    label = stringResource(R.string.analytics_month_total_plays),
                    value = stringResource(R.string.analytics_times_count, report.playCount),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MonthlyMetricPill(
                    label = stringResource(R.string.analytics_month_active_days),
                    value = stringResource(R.string.analytics_day_count, report.activeDays),
                    modifier = Modifier.weight(1f)
                )
                MonthlyMetricPill(
                    label = stringResource(R.string.analytics_month_unique_songs),
                    value = stringResource(R.string.analytics_song_count_value, report.uniqueSongCount),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MonthlyMetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = Color.White.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ListeningHabitCard(report: MonthlyListeningReport) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth(), colors = analyticsWallpaperCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.analytics_habit_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HabitMetric(
                    label = stringResource(R.string.analytics_habit_peak_time),
                    value = report.peakTimeLabelRes?.let { stringResource(it) } ?: stringResource(R.string.common_unknown),
                    modifier = Modifier.weight(1f)
                )
                HabitMetric(
                    label = stringResource(R.string.analytics_habit_streak),
                    value = stringResource(R.string.analytics_day_count, report.longestStreakDays),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HabitMetric(
                    label = stringResource(R.string.analytics_habit_avg_active_day),
                    value = formatListenDuration(context, report.averagePerActiveDayMs),
                    modifier = Modifier.weight(1f)
                )
                HabitMetric(
                    label = stringResource(R.string.analytics_habit_active_ratio),
                    value = stringResource(R.string.analytics_active_days_ratio, report.activeDays, report.elapsedDaysInMonth),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun HabitMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 11.dp)
    ) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun TasteProfileCard(profile: TasteProfile) {
    val insights = listOfNotNull(profile.topArtist, profile.topAlbum, profile.topGenre)
    Card(modifier = Modifier.fillMaxWidth(), colors = analyticsWallpaperCardColors()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.analytics_taste_profile_title),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.analytics_taste_profile_summary),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 3.dp)
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (insights.isEmpty()) {
                Text(
                    text = stringResource(R.string.analytics_taste_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                insights.forEach { TasteInsightRow(insight = it) }
            }
        }
    }
}

@Composable
private fun TasteInsightRow(insight: TasteInsight) {
    val context = LocalContext.current
    val displayTitle = if (insight.labelRes == R.string.analytics_taste_top_album &&
        LibraryNormalizer.isGeneratedUnknownAlbumPlaceholder(insight.title)
    ) {
        context.getString(R.string.player_unknown_album)
    } else {
        insight.title
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(insight.labelRes),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            Text(
                text = displayTitle,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (insight.subtitle.isNotBlank()) {
                Text(
                    text = insight.subtitle,
                    fontSize = 11.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Text(
            text = formatListenDuration(context, insight.listenedMs),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
internal fun MonthlyFavoritesCard(
    report: MonthlyListeningReport,
    mainViewModel: MainViewModel
) {
    val insights = listOfNotNull(
        report.favoriteArtist,
        report.favoriteSong,
        report.favoriteAlbum
    )
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = analyticsWallpaperCardColors()
    ) {
        Column(modifier = Modifier.padding(vertical = 16.dp)) {
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Text(
                    text = stringResource(R.string.analytics_month_favorites_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.analytics_month_favorites_summary, report.monthTitle),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(top = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            if (insights.isEmpty()) {
                Text(
                    text = stringResource(R.string.analytics_month_favorites_empty),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(insights) { insight ->
                        FavoriteInsightCard(
                            insight = insight,
                            mainViewModel = mainViewModel
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteInsightCard(
    insight: ListeningInsight,
    mainViewModel: MainViewModel
) {
    val context = LocalContext.current
    val displayTitle = if (insight.labelRes == R.string.analytics_month_favorite_album &&
        LibraryNormalizer.isGeneratedUnknownAlbumPlaceholder(insight.title)
    ) {
        context.getString(R.string.player_unknown_album)
    } else {
        insight.title
    }
    Card(
        modifier = Modifier
            .width(168.dp)
            .height(226.dp),
        colors = analyticsWallpaperCardColors(alpha = 0.55f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AnalyticsSongCover(
                song = insight.song,
                mainViewModel = mainViewModel,
                modifier = Modifier.fillMaxSize(),
                coverSize = 512
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.08f),
                                Color.Black.copy(alpha = 0.28f),
                                Color.Black.copy(alpha = 0.78f)
                            )
                        )
                    )
            )
            Text(
                text = stringResource(insight.labelRes),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White.copy(alpha = 0.18f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = displayTitle,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = stringResource(R.string.analytics_month_favorite_play_count, insight.playCount),
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.padding(top = 4.dp)
                )
                if (insight.subtitle.isNotBlank()) {
                    Text(
                        text = insight.subtitle,
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }
    }
}
