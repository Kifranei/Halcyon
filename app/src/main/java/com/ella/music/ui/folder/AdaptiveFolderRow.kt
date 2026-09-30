package com.ella.music.ui.folder

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.ui.components.*
import top.yukonga.miuix.kmp.basic.*
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.icon.extended.Refresh

@Composable internal fun <T> AdaptiveFolderRow(items: List<T>, columns: Int, itemKey: (T) -> Any, content: @Composable (T) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = if (columns > 1) 12.dp else 0.dp, vertical = 4.dp)) {
        val cellWidth = (maxWidth - 8.dp * (columns - 1).coerceAtLeast(0)) / columns.coerceAtLeast(1)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)) {
            items.forEach { item ->
                key(itemKey(item)) { Box(Modifier.width(cellWidth)) { content(item) } }
            }
        }
    }
}

@Composable internal fun FolderHierarchyTile(folder: FolderTreeEntry, onClick: () -> Unit, onLongClick: () -> Unit) {
    val scale = rememberFolderDisplaySettings().sizePercent / 100f
    Card(modifier = frostedCardModifier(Modifier.fillMaxWidth()).combinedClickable(interactionSource = null, indication = null, onClick = onClick, onLongClick = onLongClick),
        cornerRadius = 16.dp, insideMargin = PaddingValues(10.dp),
        colors = CardDefaults.defaultColors(color = frostedCardColor())) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val edge = minOf(maxWidth, (80 * scale).dp)
                FolderHierarchyCover(folder, Modifier.size(edge).clip(RoundedCornerShape(12.dp)))
            }
            Spacer(Modifier.height(8.dp))
            Text(folder.name, fontSize = (16 * scale).sp, lineHeight = (20 * scale).sp, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.song_count, folder.songCount), fontSize = (12 * scale).sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
    }
}

@Composable internal fun FolderPlaylistTile(name: String, songCount: Int, cover: Any?, onClick: () -> Unit,
    onLongClick: () -> Unit, onMore: () -> Unit, onSync: () -> Unit) {
    val scale = rememberFolderDisplaySettings().sizePercent / 100f
    Card(modifier = frostedCardModifier(Modifier.fillMaxWidth()).combinedClickable(interactionSource = null, indication = null, onClick = onClick, onLongClick = onLongClick),
        cornerRadius = 16.dp, insideMargin = PaddingValues(10.dp),
        colors = CardDefaults.defaultColors(color = frostedCardColor())) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                val edge = minOf(maxWidth, (80 * scale).dp)
                if (cover != null) SafeCoverImage(cover, name, Modifier.size(edge).clip(RoundedCornerShape(12.dp)), sizePx = 320)
                else FolderOutlineIcon(tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(edge))
            }
            Spacer(Modifier.height(8.dp))
            Text(name, fontSize = (16 * scale).sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(stringResource(R.string.song_count, songCount), fontSize = (12 * scale).sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                IconButton(onClick = onSync) { Icon(MiuixIcons.Regular.Refresh, stringResource(R.string.folder_playlist_more_refresh), modifier = Modifier.size(20.dp)) }
                IconButton(onClick = onMore) { Icon(MiuixIcons.Regular.More, stringResource(R.string.player_more_actions), modifier = Modifier.size(20.dp)) }
            }
        }
    }
}
