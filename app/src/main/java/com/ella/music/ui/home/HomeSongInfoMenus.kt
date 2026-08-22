package com.ella.music.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.NeteaseKeyInfo
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.formatBitRate
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.ui.components.SongInfoSheet
import com.ella.music.ui.components.SongMenuItem
import com.ella.music.ui.components.openSongWithMediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SongInfoMenu(
    song: Song,
    audioInfoLoader: (Song) -> AudioInfo,
    tagInfoLoader: (Song) -> SongTagInfo,
    onAiInterpret: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    SongInfoSheet(
        song = song,
        audioInfoLoader = audioInfoLoader,
        tagInfoLoader = tagInfoLoader,
        onOpenMediaInfo = {
            onDismiss()
            openSongWithMediaInfo(context, song)
        },
        onDismiss = onDismiss,
        leadingContent = {
            SongMenuItem(stringResource(R.string.song_more_ai_title), onAiInterpret)
        }
    )
}

@Composable
internal fun NeteaseKeyInfoMenu(
    info: NeteaseKeyInfo,
    onOpenUrl: (String) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    var showArtistPicker by remember(info) { mutableStateOf(false) }
    val neteaseArtists = remember(info) { info.artists.filter { it.id.isNotBlank() } }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.88f)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MiuixTheme.colorScheme.background.copy(alpha = 0.98f))
            .verticalScroll(rememberScrollState())
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        SheetHandle()
        Text(
            text = if (showArtistPicker) stringResource(R.string.player_choose_netease_artist) else stringResource(R.string.song_more_netease_key),
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )
        if (showArtistPicker) {
            neteaseArtists.forEach { artist ->
                LibraryMenuItem(
                    text = artist.name.ifBlank { "ID ${artist.id}" },
                    onClick = { onOpenUrl(neteaseArtistUrl(artist.id)) }
                )
            }
            LibraryMenuItem(stringResource(R.string.song_more_back_to_netease_key), onClick = { showArtistPicker = false })
            return@Column
        }
        if (!info.hasDecodedContent) {
            SongInfoRow(stringResource(R.string.library_status_label), stringResource(R.string.library_netease_info_unavailable))
        }
        if (info.musicId.isNotBlank()) {
            SongInfoActionRow(
                label = stringResource(R.string.player_netease_song_page),
                value = listOf(info.musicName, "ID ${info.musicId}").filter { it.isNotBlank() }.joinToString(" · "),
                onClick = { onOpenUrl(neteaseSongUrl(info.musicId)) }
            )
        }
        info.aliases
            .joinToString(" / ")
            .takeIf { it.isNotBlank() }
            ?.let { SongInfoRow(stringResource(R.string.song_more_alias), it) }
        if (info.albumId.isNotBlank()) {
            SongInfoActionRow(
                label = stringResource(R.string.player_netease_album_page),
                value = listOf(info.albumName, "ID ${info.albumId}").filter { it.isNotBlank() }.joinToString(" · "),
                onClick = { onOpenUrl(neteaseAlbumUrl(info.albumId)) }
            )
        }
        val artistSummary = info.artists
            .joinToString(" / ") { it.name.ifBlank { it.id } }
            .takeIf { it.isNotBlank() }
        if (neteaseArtists.isNotEmpty()) {
            SongInfoActionRow(
                label = stringResource(R.string.player_netease_artist_page),
                value = artistSummary.orEmpty(),
                onClick = {
                    if (neteaseArtists.size == 1) {
                        onOpenUrl(neteaseArtistUrl(neteaseArtists.first().id))
                    } else {
                        showArtistPicker = true
                    }
                }
            )
        } else {
            artistSummary?.let { SongInfoRow(stringResource(R.string.player_netease_artist_page), it) }
        }
        SongInfoRow(stringResource(R.string.player_detail_comment), info.comment)
        SongInfoRow(stringResource(R.string.song_more_raw_netease_key), info.raw)
        SongInfoRow(stringResource(R.string.library_decoded_json), info.decodedJson)
        LibraryMenuItem(stringResource(R.string.library_back_to_song_info), onBack)
    }
}

@Composable
internal fun SongInfoActionRow(label: String, value: String, onClick: () -> Unit) {
    if (value.isBlank()) return
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

internal fun openNeteaseUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, context.getString(R.string.library_open_netease_failed), Toast.LENGTH_SHORT).show()
    }
}

@Composable
internal fun SongInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    val pathLabel = stringResource(R.string.song_more_detail_path)
    val rawNeteaseKeyLabel = stringResource(R.string.song_more_raw_netease_key)
    val decodedJsonLabel = stringResource(R.string.library_decoded_json)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.62f))
            .padding(horizontal = 16.dp, vertical = 10.dp)
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
            maxLines = when (label) {
                pathLabel -> 3
                rawNeteaseKeyLabel, decodedJsonLabel -> 6
                else -> 2
            },
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
}

internal fun formatLibraryFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024.0) {
        "%.2f GB".format(mb / 1024.0)
    } else {
        "%.1f MB".format(mb)
    }
}
