package com.ella.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.CategoryResumeStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ContinuePlaybackRow(
    songs: List<Song>,
    categoryKey: String,
    playbackStats: List<SongPlaybackStats> = emptyList(),
    currentSong: Song? = null,
    onContinue: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val visibility = if (categoryKey == com.ella.music.data.CategoryResumeKeys.HOME) {
        settingsManager.continuePlaybackRowVisible
    } else {
        settingsManager.categoryContinuePlaybackRowVisible
    }
    val visible by visibility.collectAsState(initial = true)
    if (!visible) return
    // This row is commonly hosted inside a LazyColumn. A plain remember would be lost when the
    // item leaves the viewport, making a dismissed row reappear while scrolling. Key the state by
    // the category contents so dismissal remains local to the current category screen.
    val dismissalKey = remember(songs) {
        songs.fold(17) { hash, song ->
            31 * hash + song.playlistIdentityKey().hashCode()
        }.toString() + ":" + songs.size
    }
    var dismissed by rememberSaveable(dismissalKey) { mutableStateOf(false) }
    if (dismissed) return
    val playbackSourceKey by com.ella.music.data.PlaybackSourceNavigation.sourceKey.collectAsState()
    val recentHistory by com.ella.music.data.RecentPlaybackStore.getInstance(context).history.collectAsState()
    val latestEntry = remember(recentHistory, categoryKey) {
        com.ella.music.data.latestCategoryPlayback(recentHistory, categoryKey)
    }
    val storedResumeKey = CategoryResumeStore.getInstance(context).lastSongKey(categoryKey)
    val currentSongKey = currentSong?.playlistIdentityKey()
    val resumeIndex = remember(songs, categoryKey, playbackSourceKey, currentSongKey, storedResumeKey, latestEntry) {
        resolveContinuePlaybackIndex(
            songs = songs,
            categoryKey = categoryKey,
            playbackSourceKey = playbackSourceKey,
            currentSong = currentSong,
            storedResumeKey = storedResumeKey,
            latestEntry = latestEntry
        )
    }
    if (resumeIndex < 0) return
    val song = songs[resumeIndex]

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onContinue(resumeIndex) }
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Play,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = stringResource(
                    R.string.continue_playback,
                    latestEntry?.title?.takeIf { it.isNotBlank() } ?: song.title.ifBlank { song.fileName },
                    latestEntry?.artist ?: song.artist
                ),
                color = MiuixTheme.colorScheme.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { dismissed = true },
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f))
        )
    }
}

internal fun resolveContinuePlaybackIndex(
    songs: List<Song>,
    categoryKey: String,
    playbackSourceKey: String?,
    currentSong: Song?,
    storedResumeKey: String?,
    latestEntry: com.ella.music.data.PlaybackHistoryEntry? = null
): Int {
    if (playbackSourceKey == categoryKey && currentSong != null) {
        // The current list is already the playback surface. Showing a "continue" row here after
        // the user just tapped a song is both redundant and misleading; it also used to leave a
        // stale-looking header above the newly playing item on library and playlist pages.
        return -1
    }
    if (latestEntry != null) return com.ella.music.data.categoryPlaybackSongIndex(songs, latestEntry)
    if (storedResumeKey.isNullOrBlank()) return -1
    return songs.indexOfFirst { it.playlistIdentityKey() == storedResumeKey }
}
