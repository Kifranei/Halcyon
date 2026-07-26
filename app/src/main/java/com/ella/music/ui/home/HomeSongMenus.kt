package com.ella.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ExplicitSongTitle
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SongActionMenu(
    song: Song,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onSpectrum: () -> Unit,
    onInfo: () -> Unit,
    onAiInterpret: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onEditTag: () -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SheetHandle()
        ExplicitSongTitle(
            title = song.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        LibraryMenuItem(stringResource(R.string.song_more_add_to_playlist), onAddToPlaylist)
        LibraryMenuItem(stringResource(R.string.song_more_play_next), onPlayNext)
        LibraryMenuItem(stringResource(R.string.common_share), onShare)
        LibraryMenuItem(stringResource(R.string.song_more_view_spectrum), onSpectrum)
        LibraryMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        LibraryMenuItem(stringResource(R.string.song_more_view_song_info), onInfo)
        LibraryMenuItem(
            stringResource(
                R.string.song_more_artist_entry,
                song.artist.ifBlank { stringResource(R.string.player_unknown_artist) }
            ),
            onArtist
        )
        LibraryMenuItem(
            stringResource(
                R.string.song_more_album_entry,
                song.album.ifBlank { stringResource(R.string.player_unknown_album) }
            ),
            onAlbum
        )
        LibraryMenuItem(stringResource(R.string.song_more_edit_tags_title), onEditTag)
        LibraryMenuItem(stringResource(R.string.song_more_delete_permanently), onDelete, danger = true)
        LibraryMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}
