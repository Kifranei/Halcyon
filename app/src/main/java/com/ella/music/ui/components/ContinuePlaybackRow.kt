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
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
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
    playbackStats: List<SongPlaybackStats>,
    currentSong: Song? = null,
    onContinue: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (songs.isEmpty()) return
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val visible by settingsManager.continuePlaybackRowVisible.collectAsState(initial = true)
    if (!visible) return
    var dismissed by remember { mutableStateOf(false) }
    if (dismissed) return
    if (currentSong?.let { current -> songs.any { it.playlistIdentityKey() == current.playlistIdentityKey() } } == true) return

    val resumeIndex = remember(songs, playbackStats) {
        val latestById = playbackStats.associateBy(SongPlaybackStats::songId)
        songs.indices.maxByOrNull { index -> latestById[songs[index].id]?.lastPlayedAt ?: 0L }
            ?.takeIf { index -> (latestById[songs[index].id]?.lastPlayedAt ?: 0L) > 0L }
            ?: -1
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
                    song.title.ifBlank { song.fileName },
                    song.artist
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
