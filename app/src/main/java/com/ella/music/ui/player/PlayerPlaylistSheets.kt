package com.ella.music.ui.player

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.model.FAVORITES_PLAYLIST_ID
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.AddToPlaylistSheet
import com.ella.music.ui.components.CreatePlaylistAndAddSheet
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.createPlaylistOrShowDuplicateToast
import com.ella.music.viewmodel.MainViewModel

@Composable
internal fun PlayerPlaylistSheets(
    context: Context,
    mainViewModel: MainViewModel,
    playlists: List<UserPlaylist>,
    playlistPickerSong: Song?,
    onPlaylistPickerSongChange: (Song?) -> Unit,
    playlistPickerSongs: List<Song>?,
    onPlaylistPickerSongsChange: (List<Song>?) -> Unit,
    createPlaylistSong: Song?,
    onCreatePlaylistSongChange: (Song?) -> Unit,
    createPlaylistSongs: List<Song>?,
    onCreatePlaylistSongsChange: (List<Song>?) -> Unit
) {
    playlistPickerSong?.let { currentSong ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_add_to_playlist),
            onDismissRequest = { onPlaylistPickerSongChange(null) }
        ) {
            AddToPlaylistSheet(
                playlists = playlists.sortedForPlayerSheet(),
                songsToAdd = listOf(currentSong),
                onDismiss = { onPlaylistPickerSongChange(null) },
                onCreatePlaylist = {
                    onCreatePlaylistSongChange(currentSong)
                    onPlaylistPickerSongChange(null)
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, listOf(currentSong), appendToEnd)
                    }
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlists, selectedPlaylists.size),
                        Toast.LENGTH_SHORT
                    ).show()
                    onPlaylistPickerSongChange(null)
                }
            )
        }
    }

    playlistPickerSongs?.let { songsToAdd ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_add_to_playlist),
            onDismissRequest = { onPlaylistPickerSongsChange(null) }
        ) {
            AddToPlaylistSheet(
                playlists = playlists.sortedForPlayerSheet(),
                songsToAdd = songsToAdd,
                onDismiss = { onPlaylistPickerSongsChange(null) },
                onCreatePlaylist = {
                    onCreatePlaylistSongsChange(songsToAdd)
                    onPlaylistPickerSongsChange(null)
                },
                onPlaylistsConfirm = { selectedPlaylists, appendToEnd ->
                    selectedPlaylists.forEach { playlist ->
                        mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd, appendToEnd)
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

    createPlaylistSong?.let { currentSong ->
        CreatePlaylistAndAddSheet(
            onDismiss = { onCreatePlaylistSongChange(null) },
            onCreate = { name ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, listOf(currentSong))
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlist_named, playlist.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    onCreatePlaylistSongChange(null)
                }
            }
        )
    }

    createPlaylistSongs?.let { songsToAdd ->
        CreatePlaylistAndAddSheet(
            onDismiss = { onCreatePlaylistSongsChange(null) },
            onCreate = { name ->
                mainViewModel.createPlaylistOrShowDuplicateToast(context, name) { playlist ->
                    mainViewModel.addSongsToPlaylist(playlist.id, songsToAdd)
                    Toast.makeText(
                        context,
                        context.getString(R.string.player_added_to_playlist_named, playlist.name),
                        Toast.LENGTH_SHORT
                    ).show()
                    onCreatePlaylistSongsChange(null)
                }
            }
        )
    }
}

private fun List<UserPlaylist>.sortedForPlayerSheet(): List<UserPlaylist> =
    sortedWith(
        compareByDescending<UserPlaylist> { it.id == FAVORITES_PLAYLIST_ID }
            .thenByDescending { it.createdAt }
    )
