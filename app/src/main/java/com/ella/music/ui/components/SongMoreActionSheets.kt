package com.ella.music.ui.components

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SongMoreActionSheet(
    song: Song,
    extraTopContent: (@Composable ColumnScope.() -> Unit)?,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onAddToQueue: () -> Unit,
    onPlayNext: () -> Unit,
    onShare: () -> Unit,
    onSpectrum: () -> Unit,
    onInfo: () -> Unit,
    onRating: () -> Unit,
    onAiInterpret: () -> Unit,
    onArtist: () -> Unit,
    onAlbum: () -> Unit,
    onEditTag: (() -> Unit)?,
    onLyricTiming: (() -> Unit)?,
    onAudioTools: (() -> Unit)?,
    onRemoveFromPlaylist: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    showSpectrum: Boolean,
    showAddToQueue: Boolean
) {
    SongSheetColumn {
        extraTopContent?.invoke(this)
        SongMenuItem(stringResource(R.string.song_more_add_to_playlist), onAddToPlaylist)
        if (showAddToQueue) {
            SongMenuItem(stringResource(R.string.common_add_to_queue), onAddToQueue)
        }
        SongMenuItem(stringResource(R.string.song_more_play_next), onPlayNext)
        SongMenuItem(stringResource(R.string.common_share), onShare)
        if (showSpectrum) {
            SongMenuItem(stringResource(R.string.song_more_view_spectrum), onSpectrum)
        }
        SongMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        SongMenuItem(stringResource(R.string.song_more_view_song_info), onInfo)
        SongMenuItem(stringResource(R.string.song_more_set_rating), onRating)
        if (onEditTag != null) {
            SongMenuItem(stringResource(R.string.song_more_edit_tags_title), onEditTag)
        }
        if (onLyricTiming != null) {
            SongMenuItem(stringResource(R.string.song_more_lyric_timing), onLyricTiming)
        }
        if (onAudioTools != null) {
            SongMenuItem(stringResource(R.string.song_more_audio_tools), onAudioTools)
        }
        if (onRemoveFromPlaylist != null) {
            SongMenuItem(stringResource(R.string.playlist_remove_song_title), onRemoveFromPlaylist, danger = true)
        }
        if (onDelete != null) {
            SongMenuItem(stringResource(R.string.song_more_delete_permanently), onDelete, danger = true)
        }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun SongTagEditorSheet(
    song: Song,
    options: List<TagEditorOption>,
    onDismiss: () -> Unit,
    onOptionClick: (TagEditorOption) -> Unit
) {
    SongSheetColumn {
        ExplicitSongTitle(
            title = song.title,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        options.forEach { option -> SongMenuItem(option.label, onClick = { onOptionClick(option) }) }
        SongMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}
