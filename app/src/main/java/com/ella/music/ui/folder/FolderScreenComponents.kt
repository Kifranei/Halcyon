package com.ella.music.ui.folder

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.webdav.WebDavItem
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.wallpaperAwareCardColors
import com.ella.music.ui.playlist.wallpaperAwarePlaylistCardColor
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
@OptIn(ExperimentalFoundationApi::class)
internal fun FolderListRow(
    folder: FolderTreeEntry,
    sortMode: FolderListSortMode,
    isPinned: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val context = LocalContext.current
    val rowColor = wallpaperAwarePlaylistCardColor()
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(rowColor)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 18.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        FolderOutlineIcon(
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(42.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = folder.name,
                    fontSize = 17.sp,
                    lineHeight = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MiuixTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (isPinned) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Pin,
                        contentDescription = stringResource(R.string.common_pin_to_top),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(16.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "${folder.summaryFor(context, sortMode)} · ${folder.path}",
                fontSize = 13.sp,
                lineHeight = 17.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
internal fun LibraryAnalysisEntryCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = CardDefaults.defaultColors(color = wallpaperAwarePlaylistCardColor()),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.analytics_library_analysis),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.folder_library_analysis_summary),
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            Icon(
                imageVector = MiuixIcons.Basic.ArrowRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
internal fun WebDavBrowserCard(
    currentUrl: String,
    canGoParent: Boolean,
    loading: Boolean,
    error: String?,
    items: List<WebDavItem>,
    onRefresh: () -> Unit,
    onGoParent: () -> Unit,
    onItemClick: (WebDavItem) -> Unit,
    onAddToQueue: (WebDavItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        colors = wallpaperAwareCardColors(defaultAlpha = 0.50f)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.webdav_directory),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = MiuixTheme.colorScheme.onSurface
                    )
                    Text(
                        text = currentUrl,
                        fontSize = 12.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                if (canGoParent) {
                    IconButton(onClick = onGoParent) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Back,
                            contentDescription = stringResource(R.string.folder_parent),
                            tint = MiuixTheme.colorScheme.onSurface
                        )
                    }
                }
                IconButton(onClick = onRefresh) {
                    Icon(
                        imageVector = MiuixIcons.Regular.Refresh,
                        contentDescription = stringResource(R.string.library_refresh),
                        tint = MiuixTheme.colorScheme.onSurface
                    )
                }
            }
            when {
                loading -> Text(stringResource(R.string.webdav_loading_directory), color = MiuixTheme.colorScheme.primary)
                error != null -> Text(error, color = MiuixTheme.colorScheme.primary)
                items.isEmpty() -> Text(stringResource(R.string.webdav_empty_directory), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                else -> items.forEach { item ->
                    WebDavItemRow(
                        item = item,
                        onClick = { onItemClick(item) },
                        onAddToQueue = { onAddToQueue(item) }
                    )
                }
            }
        }
    }
}

@Composable
internal fun FolderActionSheet(
    title: String,
    isPinned: Boolean,
    onDismiss: () -> Unit,
    onTogglePin: () -> Unit,
    onShare: () -> Unit,
    onAssociate: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onAddShortcut: () -> Unit,
    onBlock: () -> Unit
) {
    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = title,
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            EllaMiuixMenuItem(
                text = stringResource(if (isPinned) R.string.common_unpin else R.string.common_pin_to_top),
                onClick = onTogglePin
            )
            EllaMiuixMenuItem(
                text = stringResource(R.string.common_share),
                onClick = onShare
            )
            EllaMiuixMenuItem(
                text = stringResource(R.string.folder_playlist_associate),
                onClick = onAssociate
            )
            EllaMiuixMenuItem(
                text = stringResource(R.string.song_more_add_to_playlist),
                onClick = onAddToPlaylist
            )
            EllaMiuixMenuItem(
                text = stringResource(R.string.common_add_to_queue),
                onClick = onAddToQueue
            )
            EllaMiuixMenuItem(
                text = stringResource(R.string.song_more_play_next),
                onClick = onPlayNext
            )
            EllaMiuixMenuItem(
                text = stringResource(R.string.common_add_desktop_shortcut),
                onClick = onAddShortcut
            )
            EllaMiuixMenuItem(
                text = stringResource(R.string.folder_block_folder),
                onClick = onBlock
            )
        }
    }
}
