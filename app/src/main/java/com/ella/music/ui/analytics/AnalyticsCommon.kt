package com.ella.music.ui.analytics

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.model.Song
import com.ella.music.ui.components.CoverLoadLimiter
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.EllaLoadingIndicator
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SummaryCard(
    songs: List<Song>,
    playbackStats: List<SongPlaybackStats>
) {
    val context = LocalContext.current
    val listenedMs = playbackStats.sumOf { it.listenedMs }
    val playCount = playbackStats.sumOf { it.playCount }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = analyticsWallpaperCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.analytics_overview),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            StatLine(stringResource(R.string.analytics_library_song_count), stringResource(R.string.analytics_song_count_value, songs.size))
            StatLine(stringResource(R.string.analytics_library_size), formatFileSize(songs.sumOf { it.fileSize }))
            StatLine(stringResource(R.string.analytics_total_plays), stringResource(R.string.analytics_times_count, playCount))
            StatLine(stringResource(R.string.analytics_total_listen), formatListenDuration(context, listenedMs))
        }
    }
}

@Composable
internal fun DonutChartCard(
    title: String,
    loadingText: String,
    buckets: List<AnalysisBucket>?,
    total: Int,
    totalSizeBytes: Long,
    palette: List<Color>,
    onBucketClick: ((AnalysisBucket) -> Unit)? = null,
    onBucketLongClick: ((AnalysisBucket) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = analyticsWallpaperCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                buckets == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    EllaLoadingIndicator()
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = loadingText,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                total == 0 -> Text(
                    text = stringResource(R.string.analytics_no_songs),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                else -> {
                    DonutChart(
                        buckets = buckets,
                        total = total,
                        colors = palette,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(220.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    buckets.forEachIndexed { index, bucket ->
                        BucketLegendRow(
                            bucket = bucket,
                            total = total,
                            color = palette[index % palette.size],
                            onClick = onBucketClick?.let { callback -> { callback(bucket) } },
                            onLongClick = onBucketLongClick?.let { callback -> { callback(bucket) } }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    StatLine(
                        stringResource(R.string.analytics_all_label),
                        stringResource(R.string.analytics_all_summary, total, formatFileSize(totalSizeBytes))
                    )
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    buckets: List<AnalysisBucket>,
    total: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 34.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = androidx.compose.ui.geometry.Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val chartSize = androidx.compose.ui.geometry.Size(diameter, diameter)

        drawArc(
            color = Color.White.copy(alpha = 0.10f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = chartSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )

        var startAngle = -90f
        buckets.forEachIndexed { index, bucket ->
            val sweep = (bucket.count.toFloat() / total.toFloat()) * 360f
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = chartSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun BucketLegendRow(
    bucket: AnalysisBucket,
    total: Int,
    color: Color,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val percent = if (total > 0) bucket.count * 100f / total.toFloat() else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick
                    )
                } else Modifier
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = bucket.label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(
                R.string.analytics_bucket_summary,
                bucket.count,
                formatPercent(percent),
                formatFileSize(bucket.sizeBytes)
            ),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
internal fun RankingCard(
    title: String,
    emptyText: String,
    stats: List<SongPlaybackStats>,
    libraryById: Map<Long, Song>,
    libraryByStatsKey: Map<String, Song>,
    mainViewModel: MainViewModel,
    valueText: (SongPlaybackStats) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = analyticsWallpaperCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (stats.isEmpty()) {
                Text(
                    text = emptyText,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                stats.forEachIndexed { index, stat ->
                    RankingRow(
                        index = index + 1,
                        stat = stat,
                        value = valueText(stat),
                        song = libraryById[stat.songId] ?: libraryByStatsKey[stat.analyticsStatsKey()],
                        mainViewModel = mainViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingRow(
    index: Int,
    stat: SongPlaybackStats,
    value: String,
    song: Song?,
    mainViewModel: MainViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        AnalyticsSongCover(
            song = song,
            mainViewModel = mainViewModel,
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: stat.title,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song?.artist ?: stat.artist,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
internal fun AnalyticsSongCover(
    song: Song?,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
    coverSize: Int = 128,
    loadOriginal: Boolean = false
) {
    val originalModel by produceState<Any?>(
        initialValue = null,
        song?.id,
        song?.dateModified,
        song?.fileSize,
        loadOriginal
    ) {
        value = if (!loadOriginal) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { song?.let(mainViewModel::getOriginalCoverModel) }.getOrNull()
            }
        }
    }
    if (loadOriginal && originalModel != null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            SafeCoverImage(
                model = originalModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sizePx = 3000,
                loadOriginal = true
            )
        }
        return
    }
    val coverBitmap by produceState<Bitmap?>(initialValue = null, song?.id, song?.dateModified, song?.fileSize) {
        value = withContext(Dispatchers.IO) {
            // Analytics 页同时渲染 40-50 个封面，不限流会并发解码大量 bitmap 触发 OOM，
            // 进而被系统杀进程重启（#133）。经 CoverLoadLimiter 排队后最多 2 个并发，其余
            // 排队等待，内存峰值大幅降低。
            runCatching {
                CoverLoadLimiter.run {
                    song?.let { s ->
                        if (coverSize > 128) mainViewModel.getAlbumCoverArtBitmap(s)
                        else mainViewModel.getCoverArtBitmap(s)
                    }
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (coverBitmap != null && !coverBitmap!!.isRecycled) {
            Image(
                bitmap = coverBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun analyticsWallpaperCardColors(alpha: Float = 0.42f) =
    CardDefaults.defaultColors(color = analyticsWallpaperCardColor(alpha))

@Composable
private fun analyticsWallpaperCardColor(alpha: Float): Color {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val wallpaperEnabled by settingsManager.appWallpaperEnabled.collectAsState(initial = false)
    val wallpaperUri by settingsManager.appWallpaperUri.collectAsState(initial = "")
    return if (wallpaperEnabled && wallpaperUri.isNotBlank()) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = alpha)
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
}
