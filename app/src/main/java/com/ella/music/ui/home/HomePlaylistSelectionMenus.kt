package com.ella.music.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.UserPlaylist
import androidx.compose.ui.focus.focusRequester
import top.yukonga.miuix.kmp.basic.TextField
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun AddToPlaylistMenu(
    playlists: List<UserPlaylist>,
    songCount: Int,
    onDismiss: () -> Unit,
    onCreatePlaylist: () -> Unit,
    onPlaylistsConfirm: (List<UserPlaylist>) -> Unit
) {
    var selectedPlaylistIds by remember(playlists) { mutableStateOf(emptySet<String>()) }
    val selectedPlaylists = playlists.filter { it.id in selectedPlaylistIds }
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp)
            .heightIn(max = 400.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SheetHandle()
        Text(
            text = stringResource(R.string.library_add_to_playlist_count, songCount),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        LibraryMenuItem(stringResource(R.string.song_more_create_playlist), onCreatePlaylist)
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
                LibraryMenuItem(
                    text = stringResource(
                        R.string.song_more_playlist_item_summary,
                        if (selected) "\u2713 " else "",
                        playlist.name,
                        playlist.songs.size
                    ),
                    onClick = {
                        selectedPlaylistIds = if (selected) {
                            selectedPlaylistIds - playlist.id
                        } else {
                            selectedPlaylistIds + playlist.id
                        }
                    }
                )
            }
        }
        if (playlists.isNotEmpty()) {
            LibraryMenuItem(
                text = stringResource(R.string.song_more_done_selected, selectedPlaylistIds.size),
                onClick = {
                    if (selectedPlaylists.isNotEmpty()) {
                        onPlaylistsConfirm(selectedPlaylists)
                    }
                }
            )
        }
        LibraryMenuItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun CreatePlaylistAndAddSheet(
    songCount: Int,
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
            Text(
                text = stringResource(R.string.library_create_playlist_add_count, songCount),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            TextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.playlist_name_label),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
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
