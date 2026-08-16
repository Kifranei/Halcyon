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
import androidx.compose.ui.layout.ContentScale
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
import com.ella.music.ui.components.EllaMiuixAction
import com.ella.music.ui.components.EllaMiuixActionRow
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.FolderOutlineIcon
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.DirectionalSortModeField
import com.ella.music.ui.components.SortDropdownMenu
import com.ella.music.ui.components.SortDropdownMenuContent
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
    selectedFolderCount: Int,
    folderPlaylists: List<FolderPlaylist>,
    onDismiss: () -> Unit,
    onLink: (List<FolderPlaylist>) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    if (!show) return
    var query by remember { mutableStateOf("") }
    var multiSelect by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<String>()) }
    var sortMode by remember { mutableStateOf(FolderPlaylistSortMode.Custom) }
    var creating by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf("") }
    val sortedPlaylists = remember(folderPlaylists, songs, sortMode) {
        folderPlaylists.sortedForFolderPlaylists(
            mode = sortMode,
            songCountProvider = { playlist -> songs.songsForFolderPlaylist(playlist.folders).size },
            durationProvider = { playlist -> songs.songsForFolderPlaylist(playlist.folders).sumOf(Song::duration) }
        )
    }
    val visiblePlaylists = remember(sortedPlaylists, query) {
        query.trim().takeIf(String::isNotBlank)?.let { target ->
            sortedPlaylists.filter { it.name.contains(target, ignoreCase = true) }
        } ?: sortedPlaylists
    }
    val covers = remember(folderPlaylists, songs) {
        folderPlaylists.associate { playlist ->
            playlist.id to songs.songsForFolderPlaylist(playlist.folders).firstOrNull().folderPlaylistCoverModel()
        }
    }
    EllaMiuixBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = stringResource(R.string.folder_playlist_associate),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.folder_playlist_selected_count, selectedFolderCount),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
            )
            EllaMiuixTextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.common_search),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SortDropdownMenuContent(
                        items = directionalSortModeDropdownItems(
                            fields = listOf(
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_custom),
                                    ascendingMode = FolderPlaylistSortMode.Custom,
                                    descendingMode = FolderPlaylistSortMode.CustomDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_updated_at),
                                    ascendingMode = FolderPlaylistSortMode.DateUpdatedAsc,
                                    descendingMode = FolderPlaylistSortMode.DateUpdated
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_created_at),
                                    ascendingMode = FolderPlaylistSortMode.DateCreated,
                                    descendingMode = FolderPlaylistSortMode.DateCreatedDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_name),
                                    ascendingMode = FolderPlaylistSortMode.Name,
                                    descendingMode = FolderPlaylistSortMode.NameDesc
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.folder_playlist_sort_folder_count),
                                    ascendingMode = FolderPlaylistSortMode.FolderCountAsc,
                                    descendingMode = FolderPlaylistSortMode.FolderCount
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_song_count),
                                    ascendingMode = FolderPlaylistSortMode.SongCountAsc,
                                    descendingMode = FolderPlaylistSortMode.SongCount
                                ),
                                DirectionalSortModeField(
                                    text = stringResource(R.string.playlist_sort_duration),
                                    ascendingMode = FolderPlaylistSortMode.DurationAsc,
                                    descendingMode = FolderPlaylistSortMode.Duration
                                )
                            ),
                            selectedMode = sortMode,
                            onSelect = { sortMode = it }
                        )
                    ) {
                        LinkFolderPlaylistChip(
                            text = stringResource(R.string.common_sort) + ": " + stringResource(sortMode.labelRes),
                            clickableEnabled = false,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                LinkFolderPlaylistChip(
                    text = stringResource(R.string.common_multi_select),
                    selected = multiSelect,
                    onClick = {
                        multiSelect = !multiSelect
                        if (!multiSelect) selectedIds = emptySet()
                    },
                    modifier = Modifier.weight(1f)
                )
                LinkFolderPlaylistChip(
                    text = stringResource(R.string.folder_playlist_create),
                    selected = creating,
                    onClick = { creating = !creating },
                    modifier = Modifier.weight(1f)
                )
            }
            if (creating) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    EllaMiuixTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = stringResource(R.string.playlist_name_label),
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = {
                            if (draftName.isNotBlank()) onCreatePlaylist(draftName.trim())
                        }
                    ) {
                        Text(stringResource(R.string.common_create))
                    }
                }
            }
        }
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
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp)
            ) {
                items(visiblePlaylists, key = { it.id }) { playlist ->
                    val selected = playlist.id in selectedIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (multiSelect) {
                                    selectedIds = if (selected) selectedIds - playlist.id else selectedIds + playlist.id
                                } else {
                                    onLink(listOf(playlist))
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(MiuixTheme.colorScheme.surfaceContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            val cover = covers[playlist.id]
                            if (cover != null) {
                                SafeCoverImage(
                                    model = cover,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                    sizePx = 192
                                )
                            } else {
                                DefaultAlbumCover(modifier = Modifier.fillMaxSize())
                            }
                        }
                        Column(modifier = Modifier.padding(start = 14.dp)) {
                            Text(
                                text = playlist.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = MiuixTheme.colorScheme.onSurface
                            )
                            Text(
                                text = stringResource(
                                    R.string.folder_playlist_card_summary,
                                    playlist.folders.size,
                                    songs.songsForFolderPlaylist(playlist.folders).size
                                ),
                                fontSize = 12.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                            )
                        }
                        if (multiSelect) {
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(
                                checked = selected,
                                onCheckedChange = {
                                    selectedIds = if (it) selectedIds + playlist.id else selectedIds - playlist.id
                                }
                            )
                        }
                    }
                }
            }
        }
        EllaMiuixActionRow(
            actions = buildList {
                add(EllaMiuixAction(text = stringResource(R.string.common_cancel), onClick = onDismiss))
                if (multiSelect) {
                    add(
                        EllaMiuixAction(
                            text = stringResource(R.string.song_more_done_selected, selectedIds.size),
                            onClick = {
                                val targets = folderPlaylists.filter { it.id in selectedIds }
                                if (targets.isNotEmpty()) onLink(targets)
                            },
                            primary = true
                        )
                    )
                }
            }
        )
    }
}

@Composable
private fun LinkFolderPlaylistChip(
    text: String,
    selected: Boolean = false,
    onClick: () -> Unit = {},
    clickableEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        color = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                else MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
            )
            .then(if (clickableEnabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    )
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

