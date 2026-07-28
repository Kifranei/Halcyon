package com.ella.music.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.formatBitRate
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseMvUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SongInfoSheet(
    song: Song,
    audioInfoLoader: (Song) -> AudioInfo,
    tagInfoLoader: (Song) -> SongTagInfo,
    onOpenMediaInfo: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var showNeteaseKeyInfo by remember(song.id) { mutableStateOf(false) }
    var showNeteaseArtistPicker by remember(song.id) { mutableStateOf(false) }
    val audioInfo by produceState<AudioInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { audioInfoLoader(song) }
    }
    val tagInfo by produceState<SongTagInfo?>(initialValue = null, song.id, song.dateModified, song.fileSize) {
        value = withContext(Dispatchers.IO) { tagInfoLoader(song) }
    }
    val neteaseInfo = remember(tagInfo?.neteaseKey) { decodeNeteaseKey(tagInfo?.neteaseKey.orEmpty()) }
    val neteaseArtists = remember(neteaseInfo) {
        neteaseInfo?.artists.orEmpty().filter { it.id.isNotBlank() }
    }

    if (showNeteaseArtistPicker && neteaseArtists.isNotEmpty()) {
        SongSheetColumn {
            Text(
                text = stringResource(R.string.player_choose_netease_artist),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            neteaseArtists.forEach { artist ->
                SongMenuItem(artist.name.ifBlank { "ID ${artist.id}" }, onClick = {
                    openUrl(context, neteaseArtistUrl(artist.id))
                })
            }
            SongMenuItem(stringResource(R.string.song_more_back_to_netease_key), onClick = { showNeteaseArtistPicker = false })
        }
        return
    }

    if (showNeteaseKeyInfo && neteaseInfo != null) {
        SongSheetColumn {
            Text(
                text = stringResource(R.string.song_more_netease_key),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            neteaseInfo.musicName.takeIf { it.isNotBlank() }?.let { SongInfoRow(stringResource(R.string.player_detail_song), it) }
            neteaseInfo.aliases
                .joinToString(" / ")
                .takeIf { it.isNotBlank() }
                ?.let { SongInfoRow(stringResource(R.string.song_more_alias), it) }
            neteaseInfo.artists
                .joinToString(" / ") { it.name.ifBlank { it.id } }
                .takeIf { it.isNotBlank() }
                ?.let { SongInfoRow(stringResource(R.string.player_detail_artist), it) }
            neteaseInfo.albumName.takeIf { it.isNotBlank() }?.let { SongInfoRow(stringResource(R.string.player_detail_album), it) }
            neteaseInfo.comment.takeIf { it.isNotBlank() }?.let { SongInfoRow(stringResource(R.string.player_detail_comment), it) }
            neteaseInfo.musicId.takeIf { it.isNotBlank() }?.let { id ->
                SongMenuItem(stringResource(R.string.player_netease_song_page), onClick = { openUrl(context, neteaseSongUrl(id)) })
            }
            neteaseInfo.mvId.takeIf { it.isNotBlank() }?.let { id ->
                SongMenuItem(
                    stringResource(R.string.player_netease_music_video),
                    onClick = { openUrl(context, neteaseMvUrl(id)) }
                )
            }
            if (neteaseArtists.isNotEmpty()) {
                SongMenuItem(
                    title = stringResource(R.string.player_netease_artist_page),
                    onClick = {
                        if (neteaseArtists.size == 1) {
                            openUrl(context, neteaseArtistUrl(neteaseArtists.first().id))
                        } else {
                            showNeteaseArtistPicker = true
                        }
                    }
                )
            }
            neteaseInfo.albumId.takeIf { it.isNotBlank() }?.let { id ->
                SongMenuItem(stringResource(R.string.player_netease_album_page), onClick = { openUrl(context, neteaseAlbumUrl(id)) })
            }
            SongInfoRow(stringResource(R.string.song_more_raw_netease_key), neteaseInfo.raw)
            neteaseInfo.decodedJson.takeIf { it.isNotBlank() }?.let {
                SongInfoRow(stringResource(R.string.library_decoded_json), it)
            }
            SongMenuItem(stringResource(R.string.common_back), onClick = { showNeteaseKeyInfo = false })
        }
        return
    }

    SongSheetColumn {
        SongInfoRow(stringResource(R.string.player_detail_song), tagInfo?.title?.ifBlank { song.title } ?: song.title)
        SongInfoRow(stringResource(R.string.player_detail_artist), tagInfo?.artist?.ifBlank { song.artist } ?: song.artist)
        SongInfoRow(stringResource(R.string.player_detail_album), tagInfo?.album?.ifBlank { song.album } ?: song.album)
        SongInfoRow(stringResource(R.string.song_more_detail_album_artist), tagInfo?.albumArtist?.ifBlank { song.albumArtist }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_genre), tagInfo?.genre?.ifBlank { song.genre }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_year), tagInfo?.year?.ifBlank { song.year }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_composer), tagInfo?.composer?.ifBlank { song.composer }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_arranger), tagInfo?.arranger?.ifBlank { song.arranger }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_lyricist), tagInfo?.lyricist?.ifBlank { song.lyricist }.orEmpty())
        SongInfoRow(stringResource(R.string.player_detail_comment), tagInfo?.displayComment.orEmpty())
        if (!tagInfo?.neteaseKey.isNullOrBlank()) {
            SongInfoActionRow(
                label = stringResource(R.string.song_more_netease_key),
                value = neteaseInfo?.musicName?.ifBlank { null }
                    ?: neteaseInfo?.musicId?.takeIf { it.isNotBlank() }?.let {
                        context.getString(R.string.song_more_netease_song_id, it)
                    }
                    ?: stringResource(R.string.song_more_view_netease_info),
                onClick = { showNeteaseKeyInfo = true }
            )
        }
        SongInfoRow(stringResource(R.string.song_more_detail_format), audioInfo?.let { detailedAudioInfo(it) }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_bitrate), audioInfo?.let { formatBitRate(it.bitRate) }.orEmpty())
        SongInfoRow(stringResource(R.string.song_more_detail_duration), song.durationText)
        SongInfoRow(stringResource(R.string.song_more_detail_size), formatFileSize(song.fileSize))
        SongInfoRow(stringResource(R.string.song_more_detail_modified_time), song.dateModified.formatSongDateTime())
        SongInfoRow(stringResource(R.string.song_more_detail_added_time), song.dateAdded.formatSongDateTime())
        SongInfoRow(stringResource(R.string.song_more_detail_file_name), song.fileName.ifBlank { song.path.substringAfterLast('/') })
        SongInfoRow(stringResource(R.string.song_more_detail_path), song.path)
        SongMenuItem(stringResource(R.string.song_more_open_media_info), onOpenMediaInfo)
    }
}

@Composable
internal fun SongAiInterpretationSheet(
    song: Song,
    mainViewModel: MainViewModel,
    onDismiss: () -> Unit
) {
    val result by produceState<Result<String>?>(initialValue = null, song.id) {
        value = runCatching { mainViewModel.interpretSongWithOpenAi(song) }
    }
    SongSheetColumn {
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
        SongMenuItem(stringResource(R.string.common_close), onDismiss)
    }
}

@Composable
private fun SongInfoRow(label: String, value: String) {
    if (value.isBlank()) return
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.38f))
            .combinedClickable(
                onClick = {},
                onLongClick = { copySongInfoValue(context, label, value) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun SongInfoActionRow(label: String, value: String, onClick: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.primary.copy(alpha = 0.18f))
            .combinedClickable(
                onClick = onClick,
                onLongClick = { copySongInfoValue(context, label, value) }
            )
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

private fun copySongInfoValue(context: Context, label: String, value: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
    Toast.makeText(context, context.getString(R.string.song_more_copied, label), Toast.LENGTH_SHORT).show()
}

private fun openUrl(context: Context, url: String) {
    if (url.isBlank()) return
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return ""
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1 -> String.format(Locale.ROOT, "%.2f GB", gb)
        mb >= 1 -> String.format(Locale.ROOT, "%.2f MB", mb)
        else -> String.format(Locale.ROOT, "%.0f KB", kb)
    }
}

private fun Long.formatSongDateTime(): String {
    if (this <= 0L) return ""
    val millis = if (this < 10_000_000_000L) this * 1000L else this
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))
}
