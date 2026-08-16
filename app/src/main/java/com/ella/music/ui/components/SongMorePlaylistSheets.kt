package com.ella.music.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun AddToPlaylistSheet(
    playlists: List<UserPlaylist>,
    songsToAdd: List<Song> = emptyList(),
    songCount: Int? = null,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>, Boolean) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val savedAppendToEnd by settingsManager.addToPlaylistAppendToEnd.collectAsState(initial = false)
    val playlistCustomOrderIds by settingsManager.playlistCustomOrder.collectAsState(initial = emptyList())
    // Server playlists are shown as read-only snapshots. Keep this picker limited to writable
    // local playlists so a tap never looks successful while changing nothing remotely.
    val writablePlaylists = remember(playlists) { playlists.filter { !it.isRemote || it.remoteWritable } }
    var selectedIds by remember(writablePlaylists) { mutableStateOf(emptySet<String>()) }
    var query by remember { mutableStateOf("") }
    var multiSelect by remember { mutableStateOf(false) }
    var appendToEnd by remember(savedAppendToEnd) { mutableStateOf(savedAppendToEnd) }
    var sortMode by remember { mutableStateOf(AddPlaylistSortMode.Custom) }
    val sortedPlaylists = remember(writablePlaylists, playlistCustomOrderIds, sortMode) {
        writablePlaylists.sortedForAddToPlaylist(
            mode = sortMode,
            customOrderIds = playlistCustomOrderIds
        )
    }
    val visiblePlaylists = remember(sortedPlaylists, query) {
        query.trim().takeIf { it.isNotBlank() }?.let { q ->
            sortedPlaylists.filter { it.name.contains(q, ignoreCase = true) }
        } ?: sortedPlaylists
    }
    val selectedPlaylists = writablePlaylists.filter { it.id in selectedIds }
    val targetSongKeys = remember(songsToAdd) { songsToAdd.mapTo(mutableSetOf()) { it.playlistIdentityKey() } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(MiuixTheme.colorScheme.background)
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .heightIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        songCount?.let { count ->
            Text(
                text = stringResource(R.string.library_selected_count, count),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }
        EllaMiuixTextField(
            value = query,
            onValueChange = { query = it },
            label = stringResource(R.string.common_search),
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                SortDropdownMenuContent(
                    items = directionalSortModeDropdownItems(
                        fields = listOf(
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_sort_custom),
                                ascendingMode = AddPlaylistSortMode.Custom,
                                descendingMode = AddPlaylistSortMode.CustomDesc
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_sort_updated_at),
                                ascendingMode = AddPlaylistSortMode.UpdatedAtAsc,
                                descendingMode = AddPlaylistSortMode.UpdatedAt
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_sort_created_at),
                                ascendingMode = AddPlaylistSortMode.CreatedAtAsc,
                                descendingMode = AddPlaylistSortMode.CreatedAt
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_sort_name),
                                ascendingMode = AddPlaylistSortMode.Name,
                                descendingMode = AddPlaylistSortMode.NameDesc
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_sort_song_count),
                                ascendingMode = AddPlaylistSortMode.SongCountAsc,
                                descendingMode = AddPlaylistSortMode.SongCount
                            ),
                            DirectionalSortModeField(
                                text = stringResource(R.string.playlist_sort_duration),
                                ascendingMode = AddPlaylistSortMode.DurationAsc,
                                descendingMode = AddPlaylistSortMode.Duration
                            )
                        ),
                        selectedMode = sortMode,
                        onSelect = { sortMode = it }
                    )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(999.dp))
                            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                            .padding(horizontal = 10.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.common_sort) + ": " + stringResource(sortMode.labelRes),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            AddPlaylistChip(
                text = if (appendToEnd) stringResource(R.string.song_more_add_position_end) else stringResource(R.string.song_more_add_position_start),
                onClick = {
                    appendToEnd = !appendToEnd
                    scope.launch { settingsManager.setAddToPlaylistAppendToEnd(appendToEnd) }
                },
                modifier = Modifier.weight(1f)
            )
            AddPlaylistChip(
                text = stringResource(R.string.common_multi_select),
                selected = multiSelect,
                onClick = {
                    multiSelect = !multiSelect
                    if (!multiSelect) selectedIds = emptySet()
                },
                modifier = Modifier.weight(1f)
            )
        }
        SongMenuItem(stringResource(R.string.song_more_create_playlist), onCreatePlaylist)
        if (writablePlaylists.isEmpty()) {
            Text(
                text = stringResource(R.string.song_more_no_custom_playlists),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
            ) {
                items(visiblePlaylists, key = { it.id }) { playlist ->
                    val selected = playlist.id in selectedIds
                    val alreadyContainsAll = targetSongKeys.isNotEmpty() &&
                        targetSongKeys.all { targetKey -> playlist.songs.any { it.key == targetKey } }
                    AddToPlaylistRow(
                        playlist = playlist,
                        selected = selected,
                        enabled = !alreadyContainsAll,
                        onClick = {
                            if (multiSelect) {
                                selectedIds = if (selected) {
                                    selectedIds - playlist.id
                                } else {
                                    selectedIds + playlist.id
                                }
                            } else {
                                onPlaylistsConfirm(listOf(playlist), appendToEnd)
                            }
                        }
                    )
                }
            }
        }
        EllaMiuixActionRow(
            actions = if (multiSelect) {
                listOf(
                    EllaMiuixAction(text = stringResource(R.string.common_cancel), onClick = onDismiss),
                    EllaMiuixAction(
                        text = stringResource(R.string.song_more_done_selected, selectedIds.size),
                        onClick = {
                            if (selectedPlaylists.isNotEmpty()) {
                                onPlaylistsConfirm(selectedPlaylists, appendToEnd)
                            }
                        },
                        primary = true
                    )
                )
            } else {
                listOf(
                    EllaMiuixAction(text = stringResource(R.string.common_cancel), onClick = onDismiss)
                )
            }
        )
    }
}

@Composable
private fun AddToPlaylistRow(
    playlist: UserPlaylist,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val coverModel = remember(playlist.id, playlist.songs) { playlist.coverModel() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier.size(42.dp),
                    contentScale = ContentScale.Crop,
                    sizePx = 128,
                    showDefaultPlaceholder = false
                )
            } else {
                DefaultAlbumCover(modifier = Modifier.size(42.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.name,
                fontSize = 15.sp,
                color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.song_count, playlist.songs.size),
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = if (enabled) 1f else 0.55f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (selected) {
            Text(
                text = "\u2713",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.primary
            )
        }
    }
}

private fun UserPlaylist.coverModel(): Any? {
    val song = songs.firstOrNull() ?: return null
    return song.coverUrl.takeIf { it.isNotBlank() }
        ?: song.albumId.takeIf { it > 0L }?.let { Uri.parse("content://media/external/audio/albumart/$it") }
}

@Composable
private fun AddPlaylistChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp)
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun ArtistPickerContent(
    artists: List<String>,
    mainViewModel: com.ella.music.viewmodel.MainViewModel,
    onArtistSelected: (String) -> Unit,
    onDismiss: () -> Unit
) {
    SongSheetColumn {
        ArtistPickerRows(
            artists = artists,
            mainViewModel = mainViewModel,
            onArtistSelected = onArtistSelected
        )
        BasicComponent(
            title = stringResource(R.string.common_cancel),
            onClick = onDismiss
        )
    }
}

@Composable
fun CreatePlaylistAndAddSheet(
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
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
                confirmText = stringResource(R.string.common_create),
                onCancel = onDismiss,
                onConfirm = { onCreate(name) }
            )
        }
    }
}
