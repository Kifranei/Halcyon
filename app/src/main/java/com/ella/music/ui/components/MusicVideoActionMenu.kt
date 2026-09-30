package com.ella.music.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.artistNamesForSong
import com.ella.music.data.model.Song
import com.ella.music.data.tagIdentityKey

/** One MV action surface for artist pages and recent playback, including the shared artist picker. */
@Composable
internal fun MusicVideoActionMenu(
    song: Song,
    onNavigateToArtist: (String) -> Unit,
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
    onArtistPickerRequested: (List<String>) -> Unit
) {
    val context = LocalContext.current
    val artists = remember(song) { artistNamesForSong(song).distinctBy { it.tagIdentityKey() } }
    EllaMiuixSheetColumn(verticalPadding = 8.dp, spacing = 8.dp, showHandle = false) {
        EllaMiuixActionMenuGroup {
            SongMenuItem(stringResource(R.string.common_share), { onDismiss(); onShare() }, icon = actionMenuIcon(ActionMenuIds.SHARE))
            SongMenuItem(stringResource(R.string.artist_music_video_info), { onDismiss(); onInfo() }, icon = actionMenuIcon(ActionMenuIds.INFO))
            SongMenuItem(
                stringResource(R.string.song_more_artist_entry, artists.joinToString(" / ").ifBlank { stringResource(R.string.player_unknown_artist) }),
                {
                    when (artists.size) {
                        0 -> Toast.makeText(context, R.string.song_more_no_artist_jump, Toast.LENGTH_SHORT).show()
                        1 -> { onDismiss(); onNavigateToArtist(artists.first()) }
                        else -> {
                            // Close this sheet before mounting the picker in the parent. Keeping
                            // both sheets alive leaves the old MV menu above the picker and makes
                            // the picker disappear when this composable is removed.
                            onDismiss()
                            onArtistPickerRequested(artists)
                        }
                    }
                }, icon = ActionMenuCommonIcons.artist
            )
            SongMenuItem(stringResource(R.string.song_more_delete_permanently), { onDismiss(); onDelete() }, danger = true, icon = actionMenuIcon(ActionMenuIds.DELETE))
        }
    }
}
