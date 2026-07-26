package com.ella.music.ui.folder

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.Song
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.directionalSortModeDropdownItems

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LinkToFolderPlaylistSheet(
    show: Boolean,
    songs: List<Song>,
    folderPlaylists: List<FolderPlaylist>,
    onDismiss: () -> Unit,
    onLink: (FolderPlaylist) -> Unit
) {
    if (!show) return
    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = stringResource(R.string.folder_playlist_associate),
        onDismissRequest = onDismiss
    ) {
        if (folderPlaylists.isEmpty()) {
            Text(
                text = stringResource(R.string.folder_playlist_empty),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(20.dp)
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 400.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
            ) {
                items(folderPlaylists, key = { it.id }) { playlist ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onLink(playlist) }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FolderOutlineIcon(
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(
                                text = playlist.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(R.string.folder_playlist_card_summary, playlist.folders.size, 0),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                    }
                }
            }
        }
    }
}
@Composable
internal fun FolderPlaylistEditorSheet(
    show: Boolean,
    target: FolderPlaylist?,
    availableFolders: List<String>,
    songs: List<Song>,
    coverModel: Any?,
    draftName: String,
    onDraftNameChange: (String) -> Unit,
    selectedFolders: Set<String>,
    onSelectedFoldersChange: (Set<String>) -> Unit,
    pinnedFolders: Set<String>,
    onPinnedFoldersChange: (Set<String>) -> Unit,
    onDismiss: () -> Unit,
    onSave: (FolderPlaylist?, String, List<String>) -> Unit
) {
    if (!show) return
    var searchQuery by remember { mutableStateOf("") }
    var editorSort by remember { mutableStateOf(EditorFolderSort.Name) }

    val filteredFolders = remember(availableFolders, searchQuery) {
        if (searchQuery.isBlank()) availableFolders
        else availableFolders.filter { folder ->
            folder.contains(searchQuery, ignoreCase = true) ||
                folder.substringAfterLast('/').contains(searchQuery, ignoreCase = true)
        }
    }
    // Calculating every nested folder's stats can be expensive in a large library. Keep the
    // editor sheet responsive, then fill the optional sort data after it has appeared.
    val editorFolderStats by produceState<Map<String, EditorFolderStats>>(
        initialValue = emptyMap(),
        availableFolders,
        songs
    ) {
        value = withContext(Dispatchers.Default) {
            val songsByFolder = songs.groupBy { it.folderPath().normalizeFolderPath() }
            availableFolders.associateWith { folder ->
                val folderSongs = songsByFolder[folder.normalizeFolderPath()].orEmpty()
                EditorFolderStats(
                    songCount = folderSongs.size,
                    dateModified = folderSongs.maxOfOrNull(Song::dateModified) ?: 0L
                )
            }
        }
    }

    // Pin folders to the top using the session-persistent pinnedFolders set, which only grows as
    // the user selects new folders and never shrinks on uncheck. This keeps a previously-selected
    // folder pinned even after an accidental mis-tap, until the editor target changes.
    val sortedFilteredFolders = remember(filteredFolders, editorSort, pinnedFolders, editorFolderStats) {
        val base = when (editorSort) {
            EditorFolderSort.Name -> filteredFolders.sortedBy { it.substringAfterLast('/').lowercase() }
            EditorFolderSort.NameDesc -> filteredFolders.sortedByDescending { it.substringAfterLast('/').lowercase() }
            EditorFolderSort.ModifiedTime -> filteredFolders.sortedWith(
                compareByDescending<String> { editorFolderStats[it]?.dateModified ?: 0L }
                    .thenBy { it.substringAfterLast('/').lowercase() }
            )
            EditorFolderSort.ModifiedTimeAsc -> filteredFolders.sortedWith(
                compareBy<String> { editorFolderStats[it]?.dateModified ?: 0L }
                    .thenBy { it.substringAfterLast('/').lowercase() }
            )
            EditorFolderSort.SongCount -> filteredFolders.sortedWith(
                compareByDescending<String> { editorFolderStats[it]?.songCount ?: 0 }
                    .thenBy { it.substringAfterLast('/').lowercase() }
            )
            EditorFolderSort.SongCountAsc -> filteredFolders.sortedWith(
                compareBy<String> { editorFolderStats[it]?.songCount ?: 0 }
                    .thenBy { it.substringAfterLast('/').lowercase() }
            )
        }
        base.sortedWith(
            compareByDescending<String> { it in pinnedFolders }
                .thenBy { base.indexOf(it) }
        )
    }

    // Each folder row shows that folder's own cover (first song in it), not the playlist cover.
    val folderCovers = remember(sortedFilteredFolders, songs) {
        val firstByFolder = HashMap<String, Song>()
        songs.forEach { song ->
            val normalized = song.folderPath().normalizeFolderPath()
            if (normalized !in firstByFolder) firstByFolder[normalized] = song
        }
        sortedFilteredFolders.associateWith { folder ->
            val normalized = folder.normalizeFolderPath()
            firstByFolder[normalized].folderPlaylistCoverModel()
        }
    }

    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = if (target == null) {
            stringResource(R.string.folder_playlist_create)
        } else {
            stringResource(R.string.folder_playlist_edit)
        },
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(86.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MiuixTheme.colorScheme.surfaceContainer)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                if (coverModel != null) {
                    SafeCoverImage(
                        model = coverModel,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        sizePx = 384
                    )
                } else {
                    FolderOutlineIcon(
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(18.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            EllaMiuixTextField(
                value = draftName,
                onValueChange = onDraftNameChange,
                label = stringResource(R.string.playlist_name_label),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (availableFolders.size > 6) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EllaMiuixTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        label = stringResource(R.string.common_search),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    SortDropdownMenu(
                        items = directionalSortModeDropdownItems(
                            fields = listOf(
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_song_sort_date_modified),
                                    ascendingMode = EditorFolderSort.ModifiedTimeAsc,
                                    descendingMode = EditorFolderSort.ModifiedTime
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_name),
                                    ascendingMode = EditorFolderSort.Name,
                                    descendingMode = EditorFolderSort.NameDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_song_count),
                                    ascendingMode = EditorFolderSort.SongCountAsc,
                                    descendingMode = EditorFolderSort.SongCount
                                )
                            ),
                            selectedMode = editorSort,
                            onSelect = { editorSort = it }
                        )
                    )
                }
            }
            Text(
                text = stringResource(R.string.folder_playlist_selected_count, selectedFolders.size),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 430.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                sortedFilteredFolders.forEach { folder ->
                    val checked = folder in selectedFolders
                    fun toggle(next: Boolean) {
                        if (next) {
                            onSelectedFoldersChange(selectedFolders + folder)
                            onPinnedFoldersChange(pinnedFolders + folder)
                        } else {
                            onSelectedFoldersChange(selectedFolders - folder)
                            // Intentionally do NOT remove from pinnedFolders so the folder
                            // stays pinned for the rest of this editor session.
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { toggle(!checked) }
                            .padding(horizontal = 4.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val cover = folderCovers[folder]
                            if (cover != null) {
                                SafeCoverImage(
                                    model = cover,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    sizePx = 192
                                )
                            } else {
                                FolderOutlineIcon(
                                    tint = MiuixTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(9.dp)
                                )
                            }
                        }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        ) {
                            Text(
                                text = folder.folderDisplayName(stringResource(R.string.folder_root)),
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = folder,
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Switch(
                            checked = checked,
                            onCheckedChange = { toggle(it) }
                        )
                    }
                }
            }
            Button(
                onClick = { onSave(target, draftName, selectedFolders.toList()) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
            ) {
                Text(text = stringResource(R.string.common_save))
            }
        }
    }
}

private data class EditorFolderStats(
    val songCount: Int,
    val dateModified: Long
)

