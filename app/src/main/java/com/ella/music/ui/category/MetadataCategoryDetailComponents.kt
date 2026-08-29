package com.ella.music.ui.category

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetActions
import com.ella.music.ui.components.EllaMiuixTextField
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.rememberSongArtworkState
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CategoryAddSelectedSongsToPlaylistSheet(
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
            .padding(bottom = 18.dp)
            .heightIn(max = 400.dp)
            .verticalScroll(scrollState),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = stringResource(R.string.category_selected_songs, songCount),
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        )
        CategorySheetItem(stringResource(R.string.song_more_create_playlist), onCreatePlaylist)
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
                CategorySheetItem("${if (selected) "✓ " else ""}${playlist.name} · ${playlist.songs.size} ${stringResource(R.string.library_search_song_unit)}") {
                    selectedPlaylistIds = if (selected) {
                        selectedPlaylistIds - playlist.id
                    } else {
                        selectedPlaylistIds + playlist.id
                    }
                }
            }
        }
        if (playlists.isNotEmpty()) {
            CategorySheetItem(stringResource(R.string.song_more_done_selected, selectedPlaylistIds.size)) {
                if (selectedPlaylists.isNotEmpty()) {
                    onPlaylistsConfirm(selectedPlaylists)
                }
            }
        }
        CategorySheetItem(stringResource(R.string.common_cancel), onDismiss)
    }
}

@Composable
internal fun CategoryCreatePlaylistAndAddSelectedSheet(
    songCount: Int,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var playlistName by remember { mutableStateOf("") }
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
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary
            )
            EllaMiuixTextField(
                value = playlistName,
                onValueChange = { playlistName = it },
                label = stringResource(R.string.playlist_name_label),
                focusRequester = focusRequester
            )
            EllaMiuixSheetActions(
                cancelText = stringResource(R.string.common_cancel),
                confirmText = stringResource(R.string.common_create),
                onCancel = onDismiss,
                onConfirm = { onCreate(playlistName) }
            )
        }
    }
}

@Composable
internal fun MetadataDetailLinkChip(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = MiuixTheme.colorScheme.primary,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    )
}

@Composable
internal fun CategorySheetItem(
    text: String,
    onClick: () -> Unit
) {
    Text(
        text = text,
        fontSize = 14.sp,
        color = MiuixTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 12.dp)
    )
}

@Composable
internal fun MetadataAlbumRow(
    album: Album,
    duration: Long,
    albumArtUri: Uri?,
    representativeSong: Song? = null,
    loadCoverArt: ((Song) -> Bitmap?)? = null,
    contextPersonName: String? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val artworkState = rememberSongArtworkState(
        song = representativeSong,
        albumArtUri = albumArtUri,
        loadCoverArt = loadCoverArt,
        usage = ArtworkUsage.ListThumbnail,
        showDefaultWhenMissing = false
    )
    val summary = buildList {
        add(context.getString(R.string.analytics_song_count_value, album.songCount))
        add(duration.formatDuration())
        if (album.year.isNotBlank()) add(album.year)
        album.contextualAlbumArtist(contextPersonName)?.let(::add)
    }.joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                if (selected) MiuixTheme.colorScheme.primary.copy(alpha = 0.10f)
                else Color.Transparent
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            if (artworkState.model != null) {
                SafeCoverImage(
                    model = artworkState.model,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    sizePx = 128,
                    showDefaultPlaceholder = false
                )
            } else {
                DefaultAlbumCover(modifier = Modifier.fillMaxSize())
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = album.name
                    .takeUnless(com.ella.music.data.LibraryNormalizer::isGeneratedUnknownAlbumPlaceholder)
                    ?: stringResource(R.string.player_unknown_album),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = summary,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun Album.contextualAlbumArtist(contextPersonName: String?): String? {
    val currentName = contextPersonName?.trim().orEmpty()
    val albumArtist = albumArtist.trim()
    return albumArtist.takeIf {
        it.isNotBlank() && (currentName.isBlank() || !it.equals(currentName, ignoreCase = true))
    }
}
