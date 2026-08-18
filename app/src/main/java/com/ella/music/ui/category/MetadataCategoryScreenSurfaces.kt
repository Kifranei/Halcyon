package com.ella.music.ui.category

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.folder.LinkToFolderPlaylistSheet
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.shareLocalSongs
import androidx.compose.foundation.background
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.folder.FolderBlockDialog
import com.ella.music.ui.folder.normalizeFolderPath
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.MetadataCategoryItem
import com.ella.music.viewmodel.PlayerViewModel
import java.util.Locale
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MetadataCategoryScreenSurfaces(
    context: Context,
    type: String,
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    playlists: List<UserPlaylist>,
    blockedFolders: List<String>,
    categoryMenuItem: MetadataCategoryItem?,
    onCategoryMenuItemChange: (MetadataCategoryItem?) -> Unit,
    pinnedCategoryKeys: List<String>,
    folderToBlock: String?,
    onFolderToBlockChange: (String?) -> Unit,
    playlistPickerSongs: List<Song>?,
    onPlaylistPickerSongsChange: (List<Song>?) -> Unit,
    createPlaylistSongs: List<Song>?,
    onCreatePlaylistSongsChange: (List<Song>?) -> Unit,
    pendingDeleteSongs: List<Song>,
    onPendingDeleteSongsChange: (List<Song>) -> Unit,
    onRequestDeleteSongs: (List<Song>) -> Unit,
    loadDetailSongs: suspend (String, String) -> List<Song>
) {
    val scope = rememberCoroutineScope()
    val folderPlaylists by mainViewModel.settingsManager.folderPlaylists.collectAsState(initial = emptyList())
    val librarySongs by mainViewModel.songs.collectAsState()
    var associateFolderPaths by remember { mutableStateOf<List<String>?>(null) }

    categoryMenuItem?.let { item ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = item.name.substringAfterLast('/').ifBlank { item.name },
            onDismissRequest = { onCategoryMenuItemChange(null) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                    .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val isPinned = item.name in pinnedCategoryKeys
                CategorySheetItem(
                    stringResource(if (isPinned) R.string.common_unpin else R.string.common_pin_to_top)
                ) {
                    scope.launch {
                        mainViewModel.settingsManager.setPinned("category:$type", item.name, !isPinned)
                    }
                    onCategoryMenuItemChange(null)
                }
                if (type == "folder") {
                    CategorySheetItem(stringResource(R.string.folder_block_folder)) {
                        onFolderToBlockChange(item.name.normalizeFolderPath())
                        onCategoryMenuItemChange(null)
                    }
                }
                CategorySheetItem(stringResource(R.string.common_share)) {
                    val selectedSongs = mainViewModel.getSongsForMetadataCategory(type, item.name)
                    shareLocalSongs(context, selectedSongs)
                    onCategoryMenuItemChange(null)
                }
                if (type == "folder") {
                    CategorySheetItem(stringResource(R.string.folder_playlist_associate)) {
                        associateFolderPaths = listOf(item.name)
                        onCategoryMenuItemChange(null)
                    }
                }
                CategorySheetItem(stringResource(R.string.song_more_add_to_playlist)) {
                    scope.launch {
                        onPlaylistPickerSongsChange(loadDetailSongs(type, item.name))
                    }
                    onCategoryMenuItemChange(null)
                }
                CategorySheetItem(stringResource(R.string.common_add_to_queue)) {
                    scope.launch {
                        val selectedSongs = loadDetailSongs(type, item.name)
                        playerViewModel.addToPlaylist(selectedSongs)
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                    }
                    onCategoryMenuItemChange(null)
                }
                CategorySheetItem(stringResource(R.string.song_more_play_next)) {
                    scope.launch {
                        val selectedSongs = loadDetailSongs(type, item.name)
                        playerViewModel.playNext(selectedSongs)
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                    }
                    onCategoryMenuItemChange(null)
                }
                CategorySheetItem(stringResource(R.string.common_add_desktop_shortcut)) {
                    val ok = requestPinnedEllaShortcut(
                        context = context,
                        id = "category_${type}_${item.name}",
                        label = item.name,
                        route = Screen.MetadataCategoryDetail.createRoute(type, item.name)
                    )
                    Toast.makeText(
                        context,
                        if (ok) context.getString(R.string.playlist_shortcut_requested, item.name) else context.getString(R.string.playlist_shortcut_unsupported),
                        Toast.LENGTH_SHORT
                    ).show()
                    onCategoryMenuItemChange(null)
                }
                if (type != "folder") {
                    CategorySheetItem(stringResource(R.string.song_more_delete_permanently)) {
                        onPendingDeleteSongsChange(mainViewModel.getSongsForMetadataCategory(type, item.name))
                        onCategoryMenuItemChange(null)
                    }
                }
                CategorySheetItem(stringResource(R.string.common_cancel)) {
                    onCategoryMenuItemChange(null)
                }
            }
        }
    }

    associateFolderPaths?.let { sourceFolders ->
        LinkToFolderPlaylistSheet(
            show = true,
            songs = librarySongs,
            selectedFolderCount = sourceFolders.size,
            folderPlaylists = folderPlaylists,
            onDismiss = { associateFolderPaths = null },
            onLink = { targets ->
                scope.launch {
                    targets.forEach { target ->
                        mainViewModel.settingsManager.upsertFolderPlaylist(
                            target.id,
                            target.name,
                            (target.folders + sourceFolders).distinctBy { it.lowercase() }
                        )
                    }
                    Toast.makeText(
                        context,
                        if (targets.size == 1) {
                            context.getString(R.string.folder_playlist_associate_done, targets.first().name)
                        } else {
                            context.getString(R.string.folder_playlist_associate_multi_done, targets.size)
                        },
                        Toast.LENGTH_SHORT
                    ).show()
                }
                associateFolderPaths = null
            },
            onCreatePlaylist = { name ->
                scope.launch { mainViewModel.settingsManager.upsertFolderPlaylist(null, name, sourceFolders) }
                associateFolderPaths = null
            }
        )
    }

    folderToBlock?.let { folderPath ->
        FolderBlockDialog(
            folderPath = folderPath,
            onDismiss = { onFolderToBlockChange(null) },
            onBlock = {
                scope.launch {
                    val normalizedPath = folderPath.normalizeFolderPath()
                    mainViewModel.settingsManager.setScanExcludeFolders(
                        (blockedFolders + normalizedPath)
                            .distinctBy { it.normalizeFolderPath().lowercase(Locale.ROOT) }
                            .joinToString("；")
                    )
                    mainViewModel.scanMusic()
                }
                onFolderToBlockChange(null)
            }
        )
    }

    playlistPickerSongs?.let { songs ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_add_to_playlist_title),
            onDismissRequest = { onPlaylistPickerSongsChange(null) }
        ) {
            AddToPlaylistSheet(
                playlists = playlists,
                songsToAdd = songs,
                songCount = songs.size,
                onDismiss = { onPlaylistPickerSongsChange(null) },
                onCreatePlaylist = {
                    onCreatePlaylistSongsChange(songs)
                    onPlaylistPickerSongsChange(null)
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songs, appendToEnd)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    onPlaylistPickerSongsChange(null)
                }
            )
        }
    }

    createPlaylistSongs?.let { songs ->
        CreatePlaylistAndAddSheet(
            onDismiss = { onCreatePlaylistSongsChange(null) },
            onCreate = { name ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, songs)
                    onCreatePlaylistSongsChange(null)
                }
            }
        )
    }

    if (pendingDeleteSongs.isNotEmpty()) {
        ConfirmDangerDialog(
            show = true,
            title = stringResource(R.string.song_more_delete_song_title),
            message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
            confirmText = stringResource(R.string.song_more_delete_permanently),
            onDismiss = { onPendingDeleteSongsChange(emptyList()) },
            onConfirm = {
                onRequestDeleteSongs(pendingDeleteSongs)
                onPendingDeleteSongsChange(emptyList())
            }
        )
    }
}
