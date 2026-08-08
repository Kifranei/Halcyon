package com.ella.music.ui.folder

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SelectionCheck
import com.ella.music.ui.playlist.wallpaperAwarePlaylistCardColor
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun FolderPlaylistCard(
    playlist: FolderPlaylist,
    songCount: Int,
    duration: Long,
    coverModel: Any?,
    isPinned: Boolean,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    draggedSelectionCount: Int? = null,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onSync: () -> Unit,
    onMore: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(
            color = if (selected) {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                wallpaperAwarePlaylistCardColor()
            }
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                SelectionCheck(selected = selected, checkColor = Color.White)
                Spacer(modifier = Modifier.size(12.dp))
            }
            Box(modifier = Modifier.size(52.dp).clip(RoundedCornerShape(14.dp))) {
                if (coverModel != null) {
                    SafeCoverImage(coverModel, playlist.name, Modifier.fillMaxSize(), sizePx = 320)
                } else {
                    FolderOutlineIcon(
                        tint = if (isPinned) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.fillMaxSize().padding(9.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = playlist.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${stringResource(R.string.folder_playlist_card_summary, playlist.folders.size, songCount)} · ${duration.formatPlaybackDuration()}",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            draggedSelectionCount?.let { count ->
                Text(
                    text = count.toString(),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(MiuixTheme.colorScheme.primary)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
            trailingContent?.invoke()
            if (!selectionMode) {
                IconButton(onClick = onSync) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = stringResource(R.string.folder_playlist_more_refresh),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(onClick = onMore) {
                    Icon(
                        imageVector = MiuixIcons.Regular.More,
                        contentDescription = stringResource(R.string.player_more_actions),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
