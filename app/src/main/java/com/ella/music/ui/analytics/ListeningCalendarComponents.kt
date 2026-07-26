package com.ella.music.ui.analytics

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.PlaybackHistorySource
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.CoverLoadLimiter
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ListeningMonthCard(
    month: ListeningMonthSection,
    selectedDateKey: String?,
    onDayClick: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                text = month.label,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            month.weeks.forEach { week ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    week.forEach { day ->
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                            if (day == null) {
                                Spacer(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.82f)
                                )
                            } else {
                                ListeningCalendarDayCell(
                                    day = day,
                                    selected = day.dateKey == selectedDateKey,
                                    onClick = { onDayClick(day.dateKey) }
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun ListeningCalendarDayCell(
    day: ListeningDayAggregate,
    selected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = listeningHeatColor(day.heatValue, day.maxHeatValue)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) Color.White.copy(alpha = 0.06f) else Color.Transparent)
            .clickable(enabled = day.entries.isNotEmpty(), onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = day.dayOfMonth.toString(),
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (day.entries.isNotEmpty()) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Spacer(modifier = Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
        )
    }
}

@Composable
internal fun ListeningDayDetailSection(
    day: ListeningDayAggregate?,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onSongMore: (Song) -> Unit,
    onRemoveHistoryEntry: (PlaybackHistoryEntry) -> Unit
) {
    if (day == null || day.entries.isEmpty()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.listening_calendar_empty_day),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(18.dp)
            )
        }
        return
    }

    val representativeSong = day.representativeSong
    val playableSongs = remember(day.entries) { day.entries.mapNotNull { it.song } }
    val representativeSongKey = remember(representativeSong) { representativeSong?.listeningIdentityKey() }
    val coverBitmap by produceState<Bitmap?>(initialValue = null, representativeSongKey, day.dateKey) {
        value = withContext(Dispatchers.IO) {
            CoverLoadLimiter.run { representativeSong?.let(mainViewModel::getAlbumCoverArtBitmap) }
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                DayRepresentativeCover(
                    bitmap = coverBitmap,
                    modifier = Modifier.size(84.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = formatCalendarDetailDate(day.dateKey),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = MiuixTheme.colorScheme.onSurface,
                        lineHeight = 30.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ListeningActionIconButton(
                            iconRes = R.drawable.ic_shuffle,
                            contentDescription = stringResource(R.string.listening_calendar_shuffle),
                            active = false,
                            onClick = {
                                if (playableSongs.isNotEmpty()) {
                                    playerViewModel.setPlaylist(playableSongs.shuffled(), 0)
                                }
                            }
                        )
                        ListeningActionIconButton(
                            iconRes = R.drawable.ic_player_play,
                            contentDescription = stringResource(R.string.listening_calendar_play_all),
                            active = true,
                            onClick = {
                                if (playableSongs.isNotEmpty()) {
                                    playerViewModel.setPlaylist(playableSongs, 0)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(
                    R.string.listening_calendar_total,
                    day.playCount,
                    formatCalendarTotalDuration(day.totalDurationMs)
                ),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = stringResource(R.string.listening_calendar_unique_songs, day.uniqueSongsCount),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(modifier = Modifier.height(14.dp))

            day.entries.forEachIndexed { index, entry ->
                ListeningTimelineRow(
                    entry = entry,
                    isLast = index == day.entries.lastIndex,
                    mainViewModel = mainViewModel,
                    playerViewModel = playerViewModel,
                    onSongMore = onSongMore,
                    onRemoveHistoryEntry = onRemoveHistoryEntry
                )
            }
        }
    }
}

@Composable
private fun DayRepresentativeCover(
    bitmap: Bitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White.copy(alpha = 0.05f)),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null && !bitmap.isRecycled) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ListeningActionIconButton(
    iconRes: Int,
    contentDescription: String,
    active: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val containerColor = if (active) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f)
    }
    val iconColor = if (active) {
        MiuixTheme.colorScheme.primary
    } else {
        MiuixTheme.colorScheme.onSurface
    }
    Box(
        modifier = modifier
            .size(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = androidx.compose.ui.res.painterResource(id = iconRes),
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.fillMaxSize()
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ListeningTimelineRow(
    entry: ListeningTimelineEntry,
    isLast: Boolean,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    onSongMore: (Song) -> Unit,
    onRemoveHistoryEntry: (PlaybackHistoryEntry) -> Unit
) {
    val song = entry.song
    val canPlaySong = remember(song) { song?.isPlayableCalendarSong() == true }
    val songKey = remember(song) { song?.listeningIdentityKey() }
    val coverBitmap by produceState<Bitmap?>(initialValue = null, songKey, entry.entry.playedAt) {
        value = withContext(Dispatchers.IO) {
            // 听歌历史页逐条加载封面，不限流会并发解码触发 OOM（#133）。
            runCatching {
                CoverLoadLimiter.run {
                    song?.takeIf { canPlaySong }?.let(mainViewModel::getCoverArtBitmap)
                }
            }.getOrNull()
        }
    }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, songKey) {
        value = withContext(Dispatchers.IO) {
            song?.takeIf { canPlaySong }?.let(mainViewModel::getAudioInfo)
        }
    }
    val axisDotColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.82f)
    val axisLineColor = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.32f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.width(58.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(axisDotColor)
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(94.dp)
                        .background(axisLineColor)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatCalendarHistoryClock(entry.entry.playedAt),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            song?.takeIf { canPlaySong }?.let { playerViewModel.playSong(it) }
                        },
                        onLongClick = { onRemoveHistoryEntry(entry.entry) }
                    )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DayRepresentativeCover(
                        bitmap = coverBitmap,
                        modifier = Modifier.size(64.dp)
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: entry.entry.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (entry.entry.source == PlaybackHistorySource.LAST_FM) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.18f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = stringResource(R.string.lastfm_source_badge),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MiuixTheme.colorScheme.primary
                                    )
                                }
                            }
                            audioInfo?.let {
                                audioQualitySummary(it).listTag?.let { tag ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(qualityTagColor(tag))
                                            .padding(horizontal = 5.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = tag,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.Black
                                        )
                                    }
                                }
                            }
                            Text(
                                text = buildString {
                                    append(song?.artist?.ifBlank { entry.entry.artist } ?: entry.entry.artist)
                                    val album = song?.album?.ifBlank { entry.entry.album } ?: entry.entry.album
                                    if (album.isNotBlank()) append(" - $album")
                                },
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                        text = formatTrackDuration(song?.duration ?: entry.entry.durationMs),
                            fontSize = 12.sp,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (song != null && canPlaySong) {
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onSongMore(song) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "\u22ef",
                                    fontSize = 18.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                                )
                            }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onRemoveHistoryEntry(entry.entry) }
                                    .padding(6.dp)
                            ) {
                                Icon(
                                    imageVector = MiuixIcons.Regular.Delete,
                                    contentDescription = stringResource(R.string.listening_calendar_remove_history_title),
                                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Song.isPlayableCalendarSong(): Boolean {
    if (onlineSource.isNotBlank()) return path.isNotBlank()
    if (path.startsWith("content://", ignoreCase = true)) return true
    return path.isNotBlank() && File(path).exists()
}

private fun Song.listeningIdentityKey(): String =
    listOf(
        playlistIdentityKey(),
        id,
        albumId,
        coverUrl,
        dateModified,
        fileSize,
        title,
        artist,
        album,
        duration
    ).joinToString("|")
