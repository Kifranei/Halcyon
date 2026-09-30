package com.ella.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.ui.components.SongInfoSheet
import com.ella.music.ui.components.SongMenuItem
import com.ella.music.ui.components.openSongWithMediaInfo
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SongInfoMenu(
    song: Song,
    audioInfoLoader: (Song) -> AudioInfo,
    tagInfoLoader: (Song) -> SongTagInfo,
    onAiInterpret: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    SongInfoSheet(
        song = song,
        audioInfoLoader = audioInfoLoader,
        tagInfoLoader = tagInfoLoader,
        onOpenMediaInfo = {
            onDismiss()
            openSongWithMediaInfo(context, song)
        },
        onDismiss = onDismiss,
        leadingContent = {
            SongMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        }
    )
}

@Composable
internal fun SongInfoActionRow(label: String, value: String, onClick: () -> Unit) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

@Composable
internal fun SongInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    val pathLabel = stringResource(R.string.song_more_detail_path)
    val rawNeteaseKeyLabel = stringResource(R.string.song_more_raw_netease_key)
    val decodedJsonLabel = stringResource(R.string.library_decoded_json)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = when (label) {
                pathLabel -> 3
                rawNeteaseKeyLabel, decodedJsonLabel -> 6
                else -> 2
            },
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
