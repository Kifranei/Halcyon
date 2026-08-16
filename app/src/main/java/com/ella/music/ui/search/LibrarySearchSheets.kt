package com.ella.music.ui.search

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.ConfirmDangerDialog
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.SongMoreActionHost
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.ui.components.requestPinnedEllaShortcut
import com.ella.music.ui.components.shareLocalSongs
import com.ella.music.ui.folder.FolderBlockDialog
import com.ella.music.ui.folder.LinkToFolderPlaylistSheet
import com.ella.music.ui.folder.normalizeFolderPath
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.PlayerViewModel
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun LibrarySearchAuxiliarySurfaces(
    mainViewModel: MainViewModel,
    playerViewModel: PlayerViewModel,
    settingsManager: SettingsManager,
    allSongs: List<Song>,
    playlists: List<UserPlaylist>,
    blockedFolders: List<String>,
    actionSong: Song?,
    onActionSongChange: (Song?) -> Unit,
    actionTarget: SearchActionTarget?,
    onActionTargetChange: (SearchActionTarget?) -> Unit,
    playlistPickerSongs: List<Song>?,
    onPlaylistPickerSongsChange: (List<Song>?) -> Unit,
    createPlaylistSongs: List<Song>?,
    onCreatePlaylistSongsChange: (List<Song>?) -> Unit,
    showClearHistoryConfirm: Boolean,
    onShowClearHistoryConfirmChange: (Boolean) -> Unit,
    onClearHistoryConfirmed: () -> Unit,
    pendingDeleteSongs: List<Song>,
    onPendingDeleteSongsChange: (List<Song>) -> Unit,
    onRequestDeleteSongs: (List<Song>) -> Unit,
    onFinishSelectionMode: () -> Unit,
    songsForActionTarget: (SearchActionTarget) -> List<Song>,
    shortcutIdForActionTarget: (SearchActionTarget) -> String,
    shortcutRouteForActionTarget: (SearchActionTarget) -> String,
    onNavigateToAlbum: (Long) -> Unit,
    onNavigateToArtist: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var folderToBlock by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }
    val folderPlaylists by settingsManager.folderPlaylists.collectAsState(initial = emptyList())
    var associateFolderPath by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<String?>(null) }

    actionTarget?.let { target ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = target.title,
            onDismissRequest = { onActionTargetChange(null) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_share),
                    onClick = {
                        shareLocalSongs(context, songsForActionTarget(target))
                        onActionTargetChange(null)
                    }
                )
                if (target is SearchActionTarget.CategoryTarget && target.type == "folder") {
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.folder_playlist_associate),
                        onClick = {
                            associateFolderPath = target.item.name.normalizeFolderPath()
                            onActionTargetChange(null)
                        }
                    )
                }
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_add_to_playlist),
                    onClick = {
                        onPlaylistPickerSongsChange(songsForActionTarget(target))
                        onActionTargetChange(null)
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_to_queue),
                    onClick = {
                        playerViewModel.addToPlaylist(songsForActionTarget(target))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_queue), Toast.LENGTH_SHORT).show()
                        onActionTargetChange(null)
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.song_more_play_next),
                    onClick = {
                        playerViewModel.playNext(songsForActionTarget(target))
                        Toast.makeText(context, context.getString(R.string.song_more_added_to_play_next), Toast.LENGTH_SHORT).show()
                        onActionTargetChange(null)
                    }
                )
                EllaMiuixMenuItem(
                    text = stringResource(R.string.common_add_desktop_shortcut),
                    onClick = {
                        val ok = requestPinnedEllaShortcut(
                            context = context,
                            id = shortcutIdForActionTarget(target),
                            label = target.title,
                            route = shortcutRouteForActionTarget(target)
                        )
                        Toast.makeText(
                            context,
                            if (ok) context.getString(R.string.playlist_shortcut_requested, target.title) else context.getString(R.string.playlist_shortcut_unsupported),
                            Toast.LENGTH_SHORT
                        ).show()
                        onActionTargetChange(null)
                    }
                )
                if (target is SearchActionTarget.CategoryTarget && target.type == "folder") {
                    EllaMiuixMenuItem(
                        text = stringResource(R.string.folder_block_folder),
                        onClick = {
                            folderToBlock = target.item.name.normalizeFolderPath()
                            onActionTargetChange(null)
                        }
                    )
                }
            }
        }
    }

    associateFolderPath?.let { folderPath ->
        LinkToFolderPlaylistSheet(
            show = true,
            songs = allSongs,
            selectedFolderCount = 1,
            folderPlaylists = folderPlaylists,
            onDismiss = { associateFolderPath = null },
            onLink = { targets ->
                scope.launch {
                    targets.forEach { target ->
                        mainViewModel.settingsManager.upsertFolderPlaylist(
                            target.id,
                            target.name,
                            (target.folders + folderPath).distinctBy { it.lowercase() }
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
                associateFolderPath = null
            },
            onCreatePlaylist = { name ->
                scope.launch {
                    mainViewModel.settingsManager.upsertFolderPlaylist(null, name, listOf(folderPath))
                }
                associateFolderPath = null
            }
        )
    }

    folderToBlock?.let { folderPath ->
        FolderBlockDialog(
            folderPath = folderPath,
            onDismiss = { folderToBlock = null },
            onBlock = {
                scope.launch {
                    val nextBlockedFolders = (blockedFolders + folderPath)
                        .distinctBy { it.normalizeFolderPath().lowercase(Locale.ROOT) }
                    settingsManager.setScanExcludeFolders(nextBlockedFolders.joinToString("；"))
                    mainViewModel.scanMusic()
                }
                folderToBlock = null
            }
        )
    }

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_add_to_playlist_title),
            onDismissRequest = { onPlaylistPickerSongsChange(null) }
        ) {
            AddToPlaylistSheet(
                playlists = playlists,
                songsToAdd = songsToAdd,
                songCount = songsToAdd.size,
                onDismiss = { onPlaylistPickerSongsChange(null) },
                onCreatePlaylist = {
                    onCreatePlaylistSongsChange(songsToAdd)
                    onPlaylistPickerSongsChange(null)
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd, appendToEnd)
                    }
                    onPlaylistPickerSongsChange(null)
                    Toast.makeText(context, context.getString(R.string.player_added_to_playlists, selectedPlaylists.size), Toast.LENGTH_SHORT).show()
                    onFinishSelectionMode()
                }
            )
        }
    }

    createPlaylistSongs?.let { songsToAdd ->
        com.ella.music.ui.playlist.CreatePlaylistDialog(
            onDismiss = { onCreatePlaylistSongsChange(null) },
            onCreate = { playlistName ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, playlistName) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                    onCreatePlaylistSongsChange(null)
                    onFinishSelectionMode()
                }
            }
        )
    }

    SongMoreActionHost(
        actionSong = actionSong,
        mainViewModel = mainViewModel,
        playerViewModel = playerViewModel,
        onDismissAction = { onActionSongChange(null) },
        onNavigateToAlbum = onNavigateToAlbum,
        onNavigateToArtist = onNavigateToArtist
    )

    ConfirmDangerDialog(
        show = showClearHistoryConfirm,
        title = stringResource(R.string.library_search_clear_history_title),
        message = stringResource(R.string.library_search_clear_history_message),
        confirmText = stringResource(R.string.common_clear),
        onDismiss = { onShowClearHistoryConfirmChange(false) },
        onConfirm = {
            onClearHistoryConfirmed()
            onShowClearHistoryConfirmChange(false)
        }
    )

    ConfirmDangerDialog(
        show = pendingDeleteSongs.isNotEmpty(),
        title = stringResource(R.string.song_more_delete_song_title),
        message = stringResource(R.string.library_delete_selected_message, pendingDeleteSongs.size),
        confirmText = stringResource(R.string.song_more_delete_permanently),
        onDismiss = { onPendingDeleteSongsChange(emptyList()) },
        onConfirm = {
            onRequestDeleteSongs(pendingDeleteSongs)
            onPendingDeleteSongsChange(emptyList())
            onFinishSelectionMode()
        }
    )
}
