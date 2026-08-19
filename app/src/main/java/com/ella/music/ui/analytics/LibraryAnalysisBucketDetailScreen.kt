package com.ella.music.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.SongItem
import com.ella.music.ui.components.ellaPageBackground
import com.ella.music.viewmodel.MainViewModel
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

private enum class AnalysisSongSort { Size, Title, FileName, Added, Modified, Release, Duration }

@Composable
internal fun LibraryAnalysisBucketDetailScreen(
    title: String,
    bucketLabel: String,
    songs: List<Song>,
    totalLibraryCount: Int,
    mainViewModel: MainViewModel,
    onBack: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(AnalysisSongSort.Size) }
    var selectedKeys by remember { mutableStateOf(emptySet<String>()) }
    val shownSongs = remember(songs, query, sort) {
        val filtered = songs.filter { song ->
            query.isBlank() || listOf(song.title, song.artist, song.album, song.fileName)
                .any { it.contains(query.trim(), ignoreCase = true) }
        }
        when (sort) {
            AnalysisSongSort.Size -> filtered.sortedByDescending(Song::fileSize)
            AnalysisSongSort.Title -> filtered.sortedBy { it.title.lowercase() }
            AnalysisSongSort.FileName -> filtered.sortedBy { it.fileName.lowercase() }
            AnalysisSongSort.Added -> filtered.sortedByDescending(Song::dateAdded)
            AnalysisSongSort.Modified -> filtered.sortedByDescending(Song::dateModified)
            AnalysisSongSort.Release -> filtered.sortedByDescending { it.year }
            AnalysisSongSort.Duration -> filtered.sortedByDescending(Song::duration)
        }
    }
    val sortLabels = listOf(
        AnalysisSongSort.Size to R.string.analytics_sort_size,
        AnalysisSongSort.Title to R.string.analytics_sort_title,
        AnalysisSongSort.FileName to R.string.analytics_sort_file_name,
        AnalysisSongSort.Added to R.string.analytics_sort_added,
        AnalysisSongSort.Modified to R.string.analytics_sort_modified,
        AnalysisSongSort.Release to R.string.analytics_sort_release,
        AnalysisSongSort.Duration to R.string.analytics_sort_duration
    )
    val percent = if (totalLibraryCount > 0) songs.size * 100f / totalLibraryCount else 0f

    Column(
        modifier = Modifier.fillMaxSize().background(ellaPageBackground())
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(MiuixIcons.Regular.Back, stringResource(R.string.common_back), modifier = Modifier.size(24.dp))
            }
            Column(modifier = Modifier.padding(start = 8.dp)) {
                Text(title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(bucketLabel, fontSize = 13.sp, color = MiuixTheme.colorScheme.primary)
            }
        }
        EllaMiuixTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.common_search),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            sortLabels.forEach { (value, labelRes) ->
                val active = sort == value
                Text(
                    stringResource(labelRes),
                    color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.clip(RoundedCornerShape(14.dp))
                        .background(MiuixTheme.colorScheme.primary.copy(alpha = if (active) 0.16f else 0.06f))
                        .clickable { sort = value }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
        Text(
            stringResource(
                R.string.analytics_detail_summary,
                songs.size,
                formatPercent(percent),
                formatFileSize(songs.sumOf(Song::fileSize)),
                stringResource(sortLabels.first { it.first == sort }.second)
            ),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 140.dp)
        ) {
            items(shownSongs, key = { it.playlistIdentityKey() }) { song ->
                val key = song.playlistIdentityKey()
                SongItem(
                    song = song,
                    isCurrent = false,
                    albumArtUri = mainViewModel.getAlbumArtUri(song.albumId),
                    loadCoverArt = mainViewModel::getMiniPlayerCoverArtBitmap,
                    loadAudioInfo = mainViewModel::getAudioInfo,
                    loadSongTagInfo = mainViewModel::getSongTagInfo,
                    selectionMode = selectedKeys.isNotEmpty(),
                    selected = key in selectedKeys,
                    onClick = {
                        if (selectedKeys.isNotEmpty()) {
                            selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
                        }
                    },
                    onLongClick = {
                        selectedKeys = if (key in selectedKeys) selectedKeys - key else selectedKeys + key
                    }
                )
            }
        }
    }
}
