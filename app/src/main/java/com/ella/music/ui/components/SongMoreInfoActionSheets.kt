package com.ella.music.ui.components

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.model.Song
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun SongMoreInfoActionSheets(
    context: Context,
    scope: CoroutineScope,
    mainViewModel: MainViewModel,
    ratingSong: Song?,
    onRatingSongChange: (Song?) -> Unit,
    infoSong: Song?,
    onInfoSongChange: (Song?) -> Unit,
    aiSong: Song?,
    onAiSongChange: (Song?) -> Unit,
    aiInterpretTitle: String,
    onWritePermissionRequired: (WritePermissionRequiredException, suspend () -> Unit) -> Unit
) {
    ratingSong?.let { song ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.song_more_rating_title),
            onDismissRequest = { onRatingSongChange(null) }
        ) {
            RatingSheet(
                currentRating = mainViewModel.getSongRating(song),
                onRatingSelected = { rating ->
                    scope.launch {
                        val result = mainViewModel.writeSongRating(song, rating)
                        if (result.isSuccess) {
                            Toast.makeText(context, context.getString(R.string.song_more_rating_saved), Toast.LENGTH_SHORT).show()
                            onRatingSongChange(null)
                        } else {
                            val error = result.exceptionOrNull()
                            if (error is WritePermissionRequiredException) {
                                onWritePermissionRequired(error) {
                                    val retryResult = mainViewModel.writeSongRating(song, rating)
                                    if (retryResult.isSuccess) {
                                        Toast.makeText(context, context.getString(R.string.song_more_rating_saved), Toast.LENGTH_SHORT).show()
                                        onRatingSongChange(null)
                                    } else {
                                        Toast.makeText(
                                            context,
                                            retryResult.exceptionOrNull()?.localizedMessage
                                                ?: context.getString(R.string.song_more_rating_failed),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    error?.localizedMessage ?: context.getString(R.string.song_more_rating_failed),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    }
                }
            )
        }
    }

    infoSong?.let { song ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = stringResource(R.string.player_song_details),
            onDismissRequest = { onInfoSongChange(null) }
        ) {
            SongInfoSheet(
                song = song,
                audioInfoLoader = mainViewModel::getAudioInfo,
                tagInfoLoader = mainViewModel::getSongTagInfo,
                onOpenMediaInfo = { openSongWithMediaInfo(context, song) },
                onDismiss = { onInfoSongChange(null) }
            )
        }
    }

    aiSong?.let { song ->
        EllaMiuixBottomSheet(
            show = true,
            enableNestedScroll = false,
            title = aiInterpretTitle,
            onDismissRequest = { onAiSongChange(null) }
        ) {
            SongAiInterpretationSheet(
                song = song,
                mainViewModel = mainViewModel,
                onDismiss = { onAiSongChange(null) }
            )
        }
    }
}
