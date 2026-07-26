package com.ella.music.ui.artist

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.ExplicitSongTitle
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixSheetColumn
import com.ella.music.ui.components.EllaMiuixSheetHandle
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.TagEditorOptionKind
import com.ella.music.ui.components.buildTagEditorOptions
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlinx.coroutines.withContext

@Composable
internal fun ArtistSongActionMenu(
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
    ArtistSheetColumn {
        ArtistSheetHandle()
        ExplicitSongTitle(
            title = song.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ArtistMenuItem(stringResource(R.string.player_add_to_playlist), onAddToPlaylist)
        ArtistMenuItem(stringResource(R.string.song_more_play_next), onPlayNext)
        ArtistMenuItem(stringResource(R.string.common_share), onShare)
        ArtistMenuItem(stringResource(R.string.song_more_view_spectrum), onSpectrum)
        ArtistMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        ArtistMenuItem(stringResource(R.string.song_more_view_song_info), onInfo)
        ArtistMenuItem(stringResource(R.string.song_more_artist_entry, song.artist.ifBlank { stringResource(R.string.player_unknown_artist) }), onArtist)
        ArtistMenuItem(stringResource(R.string.song_more_album_entry, song.album.ifBlank { stringResource(R.string.player_unknown_album) }), onAlbum)
        ArtistMenuItem(stringResource(R.string.song_more_edit_tags_title), onEditTag)
        ArtistMenuItem(stringResource(R.string.song_more_delete_permanently), onDelete, danger = true)
        ArtistMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun ArtistAddToPlaylistMenu(
    playlists: List<UserPlaylist>,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>, Boolean) -> Unit
) {
    var selectedPlaylistIds by remember(playlists) { mutableStateOf(emptySet<String>()) }
    val selectedPlaylists = playlists.filter { it.id in selectedPlaylistIds }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = stringResource(R.string.player_add_to_playlist),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ArtistMenuItem(stringResource(R.string.song_more_create_playlist), onCreatePlaylist)
        if (playlists.isEmpty()) {
            Text(
                text = stringResource(R.string.song_more_no_custom_playlists),
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 18.dp)
            )
        } else {
            playlists.forEach { playlist ->
                val selected = playlist.id in selectedPlaylistIds
                ArtistMenuItem(stringResource(R.string.song_more_playlist_item_summary, if (selected) "✓ " else "", playlist.name, playlist.songs.size), onClick = {
                    selectedPlaylistIds = if (selected) {
                        selectedPlaylistIds - playlist.id
                    } else {
                        selectedPlaylistIds + playlist.id
                    }
                })
            }
        }
        if (playlists.isNotEmpty()) {
            ArtistMenuItem(stringResource(R.string.song_more_done_selected, selectedPlaylistIds.size), onClick = {
                if (selectedPlaylists.isNotEmpty()) {
                    onPlaylistsConfirm(selectedPlaylists, false)
                }
            })
        }
        ArtistMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun ArtistCreatePlaylistSheet(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        delay(220L)
        focusRequester.requestFocus()
        keyboardController?.show()
    }
    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.playlist_create_title),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier.padding(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            EllaMiuixTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.playlist_name_label),
                focusRequester = focusRequester
            )
            EllaMiuixSheetActions(
                cancelText = stringResource(R.string.common_cancel),
                confirmText = stringResource(R.string.common_create),
                onCancel = onDismiss,
                onConfirm = { onCreate(name) }
            )
        }
    }
}

@Composable
internal fun ArtistTagEditorMenu(
    song: Song,
    onDismiss: () -> Unit,
    onOptionClick: (com.ella.music.ui.components.TagEditorOption) -> Unit
) {
    val context = LocalContext.current
    val options = remember(song) {
        buildTagEditorOptions(context, song).filter { it.kind == TagEditorOptionKind.Metadata }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = stringResource(R.string.song_more_edit_tags_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ExplicitSongTitle(
            title = song.title,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
        options.forEach { option ->
            ArtistMenuItem(option.label, onClick = { onOptionClick(option) })
        }
        ArtistMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun ArtistSongInfoMenu(
    song: Song,
    mainViewModel: MainViewModel,
    onAiInterpret: () -> Unit,
    onDismiss: () -> Unit
) {
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getAudioInfo(song) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { mainViewModel.getSongTagInfo(song) }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = stringResource(R.string.player_song_info),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        ArtistMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        ArtistInfoRow(stringResource(R.string.song_more_metadata_title), tagInfo?.title?.ifBlank { song.title } ?: song.title)
        ArtistInfoRow(stringResource(R.string.song_more_metadata_artist), tagInfo?.artist?.ifBlank { song.artist } ?: song.artist)
        ArtistInfoRow(stringResource(R.string.song_more_metadata_album), tagInfo?.album?.ifBlank { song.album } ?: song.album)
        ArtistInfoRow(stringResource(R.string.song_more_metadata_album_artist), tagInfo?.albumArtist?.ifBlank { song.albumArtist }.orEmpty())
        ArtistInfoRow(stringResource(R.string.song_more_metadata_comment), tagInfo?.displayComment.orEmpty())
        ArtistInfoRow(stringResource(R.string.artist_info_audio), audioInfo?.let { detailedAudioInfo(it) }.orEmpty())
        ArtistInfoRow(stringResource(R.string.song_more_detail_path), song.path)
    }
}

@Composable
internal fun ArtistAiInterpretationMenu(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<String>?>(initialValue = null, song.id) {
        value = runCatching { mainViewModel.interpretSongWithOpenAi(song) }
    }
    ArtistSheetColumn {
        ArtistSheetHandle()
        Text(
            text = stringResource(R.string.song_more_ai_title),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        Text(
            text = when {
                result == null -> stringResource(R.string.song_more_loading_ai)
                result?.isSuccess == true -> result?.getOrNull().orEmpty()
                else -> result?.exceptionOrNull()?.message ?: stringResource(R.string.song_more_ai_failed)
            },
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f))
                .padding(horizontal = 16.dp, vertical = 14.dp)
        )
        ArtistMenuItem(stringResource(R.string.common_close), onDismiss)
    }
}

@Composable
internal fun ArtistSheetColumn(content: @Composable ColumnScope.() -> Unit) {
    EllaMiuixSheetColumn(maxHeight = 400.dp, verticalPadding = 16.dp, showHandle = false, content = content)
}

@Composable
internal fun ArtistSheetHandle() {
    EllaMiuixSheetHandle()
}

@Composable
internal fun ArtistMenuItem(
    text: String,
    onClick: () -> Unit,
    danger: Boolean = false
) {
    EllaMiuixMenuItem(text = text, onClick = onClick, danger = danger)
}

@Composable
internal fun ArtistInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = if (label == stringResource(R.string.song_more_detail_path) || label == stringResource(R.string.artist_info_audio)) 4 else 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}
