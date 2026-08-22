package com.ella.music.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.FolderOutlineIcon
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AddSelectedSongsToPlaylistSheet(
    playlists: List<UserPlaylist>,
    songCount: Int,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>) -> Unit
) {
    var selectedPlaylistIds by remember(playlists) { mutableStateOf(emptySet<String>()) }
    val selectedPlaylists = playlists.filter { it.id in selectedPlaylistIds }
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .padding(bottom = 18.dp)
            .heightIn(max = 400.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.library_selected_count, songCount),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        FolderSheetItem(stringResource(R.string.playlist_create_title), onCreatePlaylist)
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.song_more_no_custom_playlists),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            playlists.forEach { playlist ->
                val selected = playlist.id in selectedPlaylistIds
                FolderSheetItem(
                    stringResource(
                        R.string.song_more_playlist_item_summary,
                        if (selected) "✓ " else "",
                        playlist.name,
                        playlist.songs.size
                    )
                ) {
                    selectedPlaylistIds = if (selected) {
                        selectedPlaylistIds - playlist.id
                    } else {
                        selectedPlaylistIds + playlist.id
                    }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            FolderSheetItem(stringResource(R.string.song_more_done_selected, selectedPlaylistIds.size)) {
                if (selectedPlaylists.isNotEmpty()) {
                    onPlaylistsConfirm(selectedPlaylists)
                }
            }
        }
        FolderSheetItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun CreatePlaylistAndAddSelectedSheet(
    songCount: Int,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.playlist_create_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = stringResource(R.string.library_create_playlist_add_count, songCount),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            EllaMiuixTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.playlist_name_label),
                focusRequester = focusRequester
            )
            EllaMiuixSheetActions(
                cancelText = stringResource(R.string.common_cancel),
                confirmText = stringResource(R.string.common_create),
                onCancel = onDismiss,
                onConfirm = { onCreate(name) }
            )
        }
    }
}

@Composable
private fun FolderSheetItem(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

@Composable
internal fun ChildFolderRow(
    folder: FolderTreeEntry,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
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
            Text(
                text = folder.name,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.folder_child_summary, folder.songCount, folder.path),
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
internal fun FolderBreadcrumbRow(
    crumbs: List<FolderBreadcrumb>,
    currentPath: String,
    onCrumbClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (crumbs.isEmpty()) return
    val current = currentPath.normalizeFolderPath()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        crumbs.forEachIndexed { index, crumb ->
            if (index > 0) {
                Text(
                    text = " > ",
                    fontSize = 13.sp,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            }
            val isCurrent = crumb.path.normalizeFolderPath().equals(current, ignoreCase = true)
            Text(
                text = crumb.label,
                fontSize = 13.sp,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
                color = if (isCurrent) {
                    MiuixTheme.colorScheme.onSurface
                } else {
                    MiuixTheme.colorScheme.onSurfaceVariantSummary
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = if (isCurrent) {
                    Modifier
                } else {
                    Modifier.clickable { onCrumbClick(crumb.path) }
                }
            )
        }
    }
}
