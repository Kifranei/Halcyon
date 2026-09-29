package com.ella.music.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.LibraryNormalizer
import com.ella.music.viewmodel.MainViewModel
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.layout.ContentScale
import com.ella.music.ui.artist.rememberArtistCoverModel
import com.ella.music.ui.components.SafeCoverImage
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.foundation.lazy.itemsIndexed
import top.yukonga.miuix.kmp.icon.extended.Music

internal data class ReplayPalette(
    val isDark: Boolean,
    val surface: Color,
    val content: Color,
    val mutedContent: Color,
    val accent: Color
)

@Composable
internal fun replayPalette(): ReplayPalette {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val content = if (isDark) Color.White else MiuixTheme.colorScheme.onBackground
    return ReplayPalette(
        isDark = isDark,
        surface = if (isDark) Color.Black else Color.Transparent,
        content = content,
        mutedContent = content.copy(alpha = if (isDark) 0.70f else 0.62f),
        accent = MiuixTheme.colorScheme.primary
    )
}

@Composable
internal fun MonthlyListeningReportCard(
    report: MonthlyListeningReport,
    monthTabs: List<ReplayMonthTab> = emptyList(),
    selectedMonthOffset: Int = 0,
    onMonthSelected: (Int) -> Unit = {}
) {
    val context = LocalContext.current
    val palette = replayPalette()
    val monthListState = rememberLazyListState()
    LaunchedEffect(monthTabs, selectedMonthOffset) {
        val selectedIndex = monthTabs.indexOfFirst {
            it.offsetFromCurrent == selectedMonthOffset
        }
        if (selectedIndex >= 0) monthListState.animateScrollToItem(selectedIndex)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(palette.surface)
    ) {
        // Replay keeps an artwork-like continuous wash, but the light theme fades back into the
        // page instead of forcing a black canvas behind the rest of the statistics.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.linearGradient(
                        if (palette.isDark) {
                            listOf(
                                palette.accent.copy(alpha = 0.28f),
                                Color(0xFF6D3A90).copy(alpha = 0.18f),
                                Color.Black.copy(alpha = 0.94f),
                                Color.Black
                            )
                        } else {
                            listOf(
                                palette.accent.copy(alpha = 0.20f),
                                Color(0xFF8F6DE4).copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        }
                    )
                )
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "REPLAY",
                    fontSize = 13.sp,
                    letterSpacing = 2.2.sp,
                    color = palette.mutedContent,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(palette.content.copy(alpha = if (palette.isDark) 0.14f else 0.08f))
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (report.year > 0) report.year.toString() else report.monthTitle,
                        fontSize = 16.sp,
                        color = palette.content,
                        fontWeight = FontWeight.SemiBold
                    )
                    if (report.year > 0) {
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            tint = palette.content,
                            modifier = Modifier
                                .size(18.dp)
                                .rotate(90f)
                        )
                    }
                }
            }
            if (monthTabs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                LazyRow(
                    state = monthListState,
                    horizontalArrangement = Arrangement.spacedBy(17.dp),
                    contentPadding = PaddingValues(horizontal = 1.dp)
                ) {
                    items(monthTabs) { tab ->
                        val selected = tab.offsetFromCurrent == selectedMonthOffset
                        Column(
                            modifier = Modifier
                                .width(40.dp)
                                .clickable { onMonthSelected(tab.offsetFromCurrent) },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tab.label,
                                fontSize = 15.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) palette.content else palette.mutedContent.copy(alpha = 0.68f)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(
                                        if (selected) palette.content else Color.Transparent
                                    )
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(22.dp))
            Text(
                text = stringResource(
                    R.string.analytics_replay_listened_sentence,
                    report.monthLabel,
                    formatListenDuration(context, report.listenedMs)
                ),
                fontSize = 34.sp,
                lineHeight = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = palette.content
            )
            Text(
                text = stringResource(R.string.analytics_month_report_title),
                fontSize = 19.sp,
                color = palette.content,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 2.dp)
            )
            Text(
                text = stringResource(
                    R.string.analytics_month_report_summary,
                    report.monthTitle,
                    report.uniqueSongCount
                ),
                fontSize = 13.sp,
                color = palette.mutedContent,
                modifier = Modifier.padding(top = 6.dp)
            )
            Spacer(modifier = Modifier.height(30.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                ReplayMetric(
                    value = stringResource(R.string.analytics_times_count, report.playCount),
                    label = stringResource(R.string.analytics_month_total_plays),
                    contentColor = palette.content,
                    mutedColor = palette.mutedContent,
                    modifier = Modifier.weight(1f)
                )
                ReplayMetric(
                    value = stringResource(R.string.analytics_day_count, report.activeDays),
                    label = stringResource(R.string.analytics_month_active_days),
                    contentColor = palette.content,
                    mutedColor = palette.mutedContent,
                    modifier = Modifier.weight(1f)
                )
                ReplayMetric(
                    value = stringResource(R.string.analytics_song_count_value, report.uniqueSongCount),
                    label = stringResource(R.string.analytics_month_unique_songs),
                    contentColor = palette.content,
                    mutedColor = palette.mutedContent,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ReplayMetric(
    value: String,
    label: String,
    contentColor: Color,
    mutedColor: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = value,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = label,
            fontSize = 11.sp,
            color = mutedColor,
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
    val palette = replayPalette()
    val insights = report.favoriteArtists.ifEmpty {
        listOfNotNull(report.favoriteArtist)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 28.dp,
        colors = analyticsWallpaperCardColors()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 20.dp)
        ) {
        Text(
            text = stringResource(R.string.analytics_month_favorites_title),
            fontSize = 24.sp,
            lineHeight = 29.sp,
            fontWeight = FontWeight.ExtraBold,
            color = palette.content,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Text(
            text = stringResource(R.string.analytics_month_favorites_summary, report.monthTitle),
            fontSize = 13.sp,
            color = palette.mutedContent.copy(alpha = 0.86f),
            modifier = Modifier.padding(start = 20.dp, top = 4.dp, end = 20.dp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        if (insights.isEmpty()) {
            Text(
                text = stringResource(R.string.analytics_month_favorites_empty),
                color = palette.mutedContent,
                modifier = Modifier.padding(horizontal = 20.dp)
            )
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                itemsIndexed(insights) { index, insight ->
                    FavoriteInsightCard(
                        insight = insight,
                        rank = index + 1,
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
    rank: Int,
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
    val isArtist = insight.labelRes == R.string.analytics_month_favorite_artist
    val artistCoverFolderUri by mainViewModel.settingsManager.artistCoverFolderUri.collectAsState(initial = "")
    val artistCoverModel = if (isArtist) {
        rememberArtistCoverModel(
            artistName = insight.title,
            representativeSong = insight.song,
            folderLocation = artistCoverFolderUri,
            mainViewModel = mainViewModel,
            coversEnabled = true,
            includeLibraryArtwork = true
        )
    } else null

    Card(
        modifier = Modifier
            .width(168.dp)
            .height(244.dp),
        cornerRadius = 20.dp,
        colors = analyticsWallpaperCardColors(alpha = 0.55f)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (isArtist) {
                if (artistCoverModel != null) {
                    SafeCoverImage(
                        model = artistCoverModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        sizePx = 512,
                        showDefaultPlaceholder = false
                    )
                }
                if (artistCoverModel == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xFF3C315A), Color(0xFF0C0A14))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Music,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.75f),
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
            } else {
                AnalyticsSongCover(
                    song = insight.song,
                    mainViewModel = mainViewModel,
                    modifier = Modifier.fillMaxSize(),
                    coverSize = 512,
                    loadOriginal = insight.labelRes == R.string.analytics_month_favorite_album
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
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Black.copy(alpha = 0.04f),
                                Color.Black.copy(alpha = 0.12f),
                                Color.Black.copy(alpha = 0.86f)
                            )
                        )
                    )
            )
            Text(
                text = rank.toString(),
                fontSize = 46.sp,
                lineHeight = 48.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 14.dp, top = 10.dp)
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(14.dp)
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
                    text = if (insight.listenedMs > 0L) {
                        formatListenDuration(context, insight.listenedMs)
                    } else {
                        stringResource(R.string.analytics_month_favorite_play_count, insight.playCount)
                    },
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
