package com.ella.music.ui.playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.PlaylistExportFormat
import com.ella.music.data.PlaylistImportMode
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.EllaMiuixAction
import com.ella.music.ui.components.EllaMiuixActionRow
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.SelectionCheck
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.StarRate
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete
import top.yukonga.miuix.kmp.icon.extended.FavoritesFill
import top.yukonga.miuix.kmp.icon.extended.Playlist
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun PlaylistToolbarChip(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PlaylistRow(
    playlist: UserPlaylist,
    coverModel: Any? = null,
    countOverride: Int? = null,
    durationOverride: Long? = null,
    accent: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    draggedSelectionCount: Int? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onMore: (() -> Unit)? = null,
    trailingContent: (@Composable (() -> Unit))? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 10.dp)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        cornerRadius = 16.dp,
        colors = CardDefaults.defaultColors(
            color = if (selected) {
                MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
            } else {
                wallpaperAwarePlaylistCardColor()
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectionMode) {
                    SelectionCheck(
                        selected = selected,
                        size = 22.dp,
                        checkColor = Color.White
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            color = if (accent) MiuixTheme.colorScheme.primary.copy(alpha = 0.18f)
                            else MiuixTheme.colorScheme.surfaceContainer,
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    if (coverModel != null) {
                        SafeCoverImage(
                            model = coverModel,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop,
                            sizePx = 160
                        )
                    }
                    if (coverModel == null) {
                        Icon(
                            imageVector = when {
                                playlist.isFavorites -> MiuixIcons.Regular.FavoritesFill
                                playlist.isFiveStarRating -> Icons.Rounded.StarRate
                                else -> MiuixIcons.Regular.Playlist
                            },
                            contentDescription = null,
                            tint = if (accent) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                            modifier = Modifier.size(25.dp)
                        )
                    } else if (playlist.isFavorites || playlist.isFiveStarRating) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(5.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(Color.Black.copy(alpha = 0.42f))
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (playlist.isFavorites) MiuixIcons.Regular.FavoritesFill else Icons.Rounded.StarRate,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = playlist.name,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MiuixTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(
                            R.string.playlist_song_count_duration,
                            countOverride ?: playlist.songs.size,
                            (durationOverride ?: playlist.songs.sumOf { it.duration }).formatPlaylistDuration()
                        ),
                        fontSize = 13.sp,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                if (onMore != null) {
                    IconButton(onClick = onMore) {
                        Icon(
                            imageVector = MiuixIcons.Regular.More,
                            contentDescription = stringResource(R.string.player_quick_more),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                } else if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = MiuixIcons.Regular.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = Color(0xFFE5484D),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (trailingContent != null) {
                    trailingContent()
                }
            }
            draggedSelectionCount?.let { count ->
                Text(
                    text = stringResource(R.string.playlist_drag_selection_count, count),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(MiuixTheme.colorScheme.primary)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
internal fun CreatePlaylistDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
    initialName: String = "",
    title: String? = null,
    confirmText: String? = null
) {
    var name by remember { mutableStateOf(initialName) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    EllaMiuixBottomSheet(
        show = true,
        title = title ?: stringResource(R.string.playlist_create_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EllaMiuixTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.playlist_name_label),
                focusRequester = focusRequester
            )
            EllaMiuixSheetActions(
                cancelText = stringResource(R.string.common_cancel),
                confirmText = confirmText ?: stringResource(R.string.common_create),
                onCancel = onDismiss,
                onConfirm = { onCreate(name) }
            )
        }
    }
}

@Composable
internal fun ImportPlaylistModeSheet(
    count: Int,
    onDismiss: () -> Unit,
    onModeSelected: (PlaylistImportMode) -> Unit
) {
    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.playlist_import_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.playlist_import_selected_files, count),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
            )
            ImportModeItem(
                title = stringResource(R.string.playlist_import_replace_all),
                summary = stringResource(R.string.playlist_import_replace_all_summary),
                onClick = { onModeSelected(PlaylistImportMode.ReplaceAll) }
            )
            ImportModeItem(
                title = stringResource(R.string.playlist_import_merge_replace),
                summary = stringResource(R.string.playlist_import_merge_replace_summary),
                onClick = { onModeSelected(PlaylistImportMode.MergeReplaceExisting) }
            )
            ImportModeItem(
                title = stringResource(R.string.playlist_import_merge_keep),
                summary = stringResource(R.string.playlist_import_merge_keep_summary),
                onClick = { onModeSelected(PlaylistImportMode.MergeKeepExisting) }
            )
            EllaMiuixActionRow(
                actions = listOf(
                    EllaMiuixAction(text = stringResource(R.string.common_cancel), onClick = onDismiss)
                )
            )
        }
    }
}

@Composable
internal fun ExportPlaylistFormatSheet(
    onDismiss: () -> Unit,
    onFormatSelected: (PlaylistExportFormat) -> Unit
) {
    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.playlist_export_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ImportModeItem(
                title = stringResource(R.string.playlist_export_txt),
                summary = stringResource(R.string.playlist_export_txt_summary),
                onClick = { onFormatSelected(PlaylistExportFormat.PlainText) }
            )
            ImportModeItem(
                title = stringResource(R.string.playlist_export_m3u8),
                summary = stringResource(R.string.playlist_export_m3u8_summary),
                onClick = { onFormatSelected(PlaylistExportFormat.M3u8) }
            )
            ImportModeItem(
                title = stringResource(R.string.playlist_export_m3u),
                summary = stringResource(R.string.playlist_export_m3u_summary),
                onClick = { onFormatSelected(PlaylistExportFormat.M3u) }
            )
            EllaMiuixActionRow(
                actions = listOf(
                    EllaMiuixAction(text = stringResource(R.string.common_cancel), onClick = onDismiss)
                )
            )
        }
    }
}

@Composable
private fun ImportModeItem(
    title: String,
    summary: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        colors = CardDefaults.defaultColors(color = wallpaperAwarePlaylistCardColor(alpha = 0.50f)),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 3.dp)
            )
        }
    }
}

@Composable
internal fun wallpaperAwarePlaylistCardColor(alpha: Float = 0.42f): Color {
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
