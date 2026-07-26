package com.ella.music.ui.components

import android.app.Activity
import android.content.ContentUris
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.ella.music.R
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.model.Song
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.launch

/**
 * Returns a callback that deletes the given songs from disk. On Android 11+ this routes
 * MediaStore-owned files through the system delete-confirmation dialog (no special
 * permission required); files the app can delete directly are removed immediately.
 */
@Composable
fun rememberSongDeleteRequester(mainViewModel: MainViewModel): (List<Song>) -> Unit {
    val context = LocalContext.current
    var pendingSystemDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val songsToDelete = pendingSystemDeleteSongs
        pendingSystemDeleteSongs = emptyList()
        if (result.resultCode == Activity.RESULT_OK && songsToDelete.isNotEmpty()) {
            mainViewModel.removeSongsFromLibrary(songsToDelete)
            Toast.makeText(
                context,
                context.getString(R.string.library_deleted_songs, songsToDelete.size),
                Toast.LENGTH_SHORT
            ).show()
        } else if (songsToDelete.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.library_delete_cancelled), Toast.LENGTH_SHORT).show()
        }
    }

    fun deleteDirectly(songs: List<Song>) {
        mainViewModel.deleteSongs(songs)
        Toast.makeText(
            context,
            context.getString(R.string.library_deleting_songs, songs.size),
            Toast.LENGTH_SHORT
        ).show()
    }

    return { songsToDelete ->
        if (songsToDelete.isNotEmpty()) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val uris = songsToDelete
                    .filter { it.id > 0L }
                    .map { ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, it.id) }
                if (uris.isNotEmpty()) {
                    runCatching {
                        pendingSystemDeleteSongs = songsToDelete
                        val request = MediaStore.createDeleteRequest(context.contentResolver, uris)
                        deleteRequestLauncher.launch(IntentSenderRequest.Builder(request.intentSender).build())
                    }.onFailure {
                        pendingSystemDeleteSongs = emptyList()
                        deleteDirectly(songsToDelete)
                    }
                } else {
                    deleteDirectly(songsToDelete)
                }
            } else {
                deleteDirectly(songsToDelete)
            }
        }
    }
}

/**
 * Returns a callback that deletes the given songs via [MainViewModel.deleteSongsResult],
 * falling back to the system write-permission dialog when the repository reports
 * [WritePermissionRequiredException]. [onDeleted] runs after a successful delete
 * (e.g. to leave selection mode).
 */
@Composable
fun rememberSongDeleteResultHandler(
    mainViewModel: MainViewModel,
    onDeleted: () -> Unit = {},
): (List<Song>) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingSystemDeleteSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    val deleteRequestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val songsToDelete = pendingSystemDeleteSongs
        pendingSystemDeleteSongs = emptyList()
        if (result.resultCode == Activity.RESULT_OK && songsToDelete.isNotEmpty()) {
            mainViewModel.removeSongsFromLibrary(songsToDelete)
            Toast.makeText(context, context.getString(R.string.library_deleted_songs, songsToDelete.size), Toast.LENGTH_SHORT).show()
            onDeleted()
        } else if (songsToDelete.isNotEmpty()) {
            Toast.makeText(context, context.getString(R.string.library_delete_cancelled), Toast.LENGTH_SHORT).show()
        }
    }
    return { songsToDelete ->
        if (songsToDelete.isNotEmpty()) {
            scope.launch {
                val result = mainViewModel.deleteSongsResult(songsToDelete)
                if (result.isSuccess) {
                    Toast.makeText(context, context.getString(R.string.library_deleted_songs, songsToDelete.size), Toast.LENGTH_SHORT).show()
                    onDeleted()
                    return@launch
                }
                val error = result.exceptionOrNull()
                if (error is WritePermissionRequiredException) {
                    pendingSystemDeleteSongs = songsToDelete
                    deleteRequestLauncher.launch(IntentSenderRequest.Builder(error.intentSender).build())
                } else {
                    Toast.makeText(context, error?.localizedMessage ?: context.getString(R.string.song_more_metadata_save_failed), Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
