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
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.ActionMenuLayout
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.decodeNeteaseKey
import com.ella.music.data.detailedAudioInfo
import com.ella.music.data.formatBitRate
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.SongTagInfo
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.data.neteaseAlbumUrl
import com.ella.music.data.neteaseArtistUrl
import com.ella.music.data.neteaseMvUrl
import com.ella.music.data.neteaseSongUrl
import com.ella.music.ui.navigation.LocalAppNavigator
import com.ella.music.viewmodel.MainViewModel
import com.ella.music.viewmodel.extractYear
import com.ella.music.viewmodel.parentFolderPath
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
    onDismiss: () -> Unit,
    onUpdateModifiedTime: (suspend (Long) -> Boolean)? = null,
    leadingContent: @Composable ColumnScope.() -> Unit = {}
) {
    val context = LocalContext.current
    val navigateTo = LocalAppNavigator.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val songInfoLayout by settingsManager.songInfoLayout.collectAsState(initial = "")
    val visibleInfoFields = remember(songInfoLayout) {
        ActionMenuLayout.parse(songInfoLayout, ActionMenuIds.songInfoDefaults)
            .visibleIds(ActionMenuIds.songInfoDefaults)
    }
    val scope = rememberCoroutineScope()
    var showNeteaseKeyInfo by remember(song.id) { mutableStateOf(false) }
    var editingModifiedTime by remember { mutableStateOf(false) }
    var modifiedTimeDraft by remember { mutableStateOf("") }
    var displayedModifiedMs by remember(song.id, song.dateModified) { mutableStateOf(song.dateModified) }
    var showNeteaseArtistPicker by remember(song.id) { mutableStateOf(false) }
    var namePicker by remember(song.id) { mutableStateOf<SongInfoNamePicker?>(null) }
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
    val jumpTo: (String?) -> Unit = { route ->
        if (!route.isNullOrBlank()) {
            onDismiss()
            navigateTo(route)
        }
    }
    val jumpField: (SongInfoJump, String, String) -> Unit = { jump, label, value ->
        val choices = songInfoJumpChoices(jump, value)
        when {
            choices.size > 1 -> namePicker = SongInfoNamePicker(
                title = when (jump) {
                    SongInfoJump.Artist, SongInfoJump.AlbumArtist ->
                        context.getString(R.string.song_more_select_artist)
                    else -> label
                },
                names = choices,
                jump = jump
            )
            else -> jumpTo(songInfoJumpRoute(jump, song, audioInfo, choices.firstOrNull().orEmpty()))
        }
    }

    namePicker?.let { picker ->
        SongSheetColumn {
            Text(
                text = picker.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
            )
            picker.names.forEach { name ->
                SongMenuItem(name, onClick = {
                    jumpTo(songInfoJumpRoute(picker.jump, song, audioInfo, name))
                })
            }
            SongMenuItem(stringResource(R.string.common_back), onClick = { namePicker = null })
        }
        return
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

    val artistValue = tagInfo?.artist?.ifBlank { song.artist } ?: song.artist
    val albumValue = tagInfo?.album?.ifBlank { song.album } ?: song.album
    val albumArtistValue = tagInfo?.albumArtist?.ifBlank { song.albumArtist }.orEmpty()
    val genreValue = tagInfo?.genre?.ifBlank { song.genre }.orEmpty()
    val yearValue = tagInfo?.year?.ifBlank { song.year }.orEmpty()
    val composerValue = tagInfo?.composer?.ifBlank { song.composer }.orEmpty()
    val arrangerValue = tagInfo?.arranger?.ifBlank { song.arranger }.orEmpty()
    val lyricistValue = tagInfo?.lyricist?.ifBlank { song.lyricist }.orEmpty()
    val directoryValue = song.parentFolderPath().orEmpty()
    val artistLabel = stringResource(R.string.player_detail_artist)
    val albumLabel = stringResource(R.string.player_detail_album)
    val albumArtistLabel = stringResource(R.string.song_more_detail_album_artist)
    val genreLabel = stringResource(R.string.song_more_detail_genre)
    val yearLabel = stringResource(R.string.song_more_detail_year)
    val composerLabel = stringResource(R.string.player_detail_composer)
    val arrangerLabel = stringResource(R.string.player_detail_arranger)
    val lyricistLabel = stringResource(R.string.player_detail_lyricist)
    val formatLabelText = stringResource(R.string.song_more_detail_format)
    val bitrateLabel = stringResource(R.string.song_more_detail_bitrate)
    val pathLabel = stringResource(R.string.song_more_detail_path)
    val directoryLabel = stringResource(R.string.song_more_detail_directory)

    val playbackStats = remember(song.id) {
        PlaybackStatsStore.getInstance(context).stats.value.firstOrNull { it.songId == song.id }
    }
    val modifiedLabel = stringResource(R.string.song_more_detail_modified_time)
    SongSheetColumn {
        leadingContent()
        for (fieldId in visibleInfoFields) {
            when (fieldId) {
                ActionMenuIds.SONG_INFO_TITLE ->
                    SongInfoRow(stringResource(R.string.player_detail_song), tagInfo?.title?.ifBlank { song.title } ?: song.title)
                ActionMenuIds.SONG_INFO_ARTIST ->
                    SongInfoRow(artistLabel, artistValue, onClick = { jumpField(SongInfoJump.Artist, artistLabel, artistValue) })
                ActionMenuIds.SONG_INFO_ALBUM ->
                    SongInfoRow(albumLabel, albumValue, onClick = { jumpTo(songInfoJumpRoute(SongInfoJump.Album, song)) })
                ActionMenuIds.SONG_INFO_ALBUM_ARTIST ->
                    SongInfoRow(albumArtistLabel, albumArtistValue, onClick = {
                        jumpField(SongInfoJump.AlbumArtist, albumArtistLabel, albumArtistValue)
                    })
                ActionMenuIds.SONG_INFO_GENRE ->
                    SongInfoRow(genreLabel, genreValue, onClick = { jumpField(SongInfoJump.Genre, genreLabel, genreValue) })
                ActionMenuIds.SONG_INFO_YEAR ->
                    SongInfoRow(
                        yearLabel,
                        yearValue,
                        onClick = yearValue.extractYear()?.let { { jumpField(SongInfoJump.Year, yearLabel, yearValue) } }
                    )
                ActionMenuIds.SONG_INFO_COMPOSER ->
                    SongInfoRow(composerLabel, composerValue, onClick = {
                        jumpField(SongInfoJump.Composer, composerLabel, composerValue)
                    })
                ActionMenuIds.SONG_INFO_ARRANGER ->
                    SongInfoRow(arrangerLabel, arrangerValue, onClick = {
                        jumpField(SongInfoJump.Arranger, arrangerLabel, arrangerValue)
                    })
                ActionMenuIds.SONG_INFO_LYRICIST ->
                    SongInfoRow(lyricistLabel, lyricistValue, onClick = {
                        jumpField(SongInfoJump.Lyricist, lyricistLabel, lyricistValue)
                    })
                ActionMenuIds.SONG_INFO_COMMENT ->
                    SongInfoRow(stringResource(R.string.player_detail_comment), tagInfo?.displayComment.orEmpty())
                ActionMenuIds.SONG_INFO_NETEASE -> if (!tagInfo?.neteaseKey.isNullOrBlank()) {
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
                ActionMenuIds.SONG_INFO_FORMAT -> SongInfoRow(
                    formatLabelText,
                    audioInfo?.let { detailedAudioInfo(it) }.orEmpty(),
                    onClick = audioInfo?.let { { jumpTo(songInfoJumpRoute(SongInfoJump.Format, song, it)) } }
                )
                ActionMenuIds.SONG_INFO_BITRATE -> SongInfoRow(
                    bitrateLabel,
                    audioInfo?.let { formatBitRate(it.bitRate) }.orEmpty(),
                    onClick = audioInfo?.let { { jumpTo(songInfoJumpRoute(SongInfoJump.Bitrate, song, it)) } }
                )
                ActionMenuIds.SONG_INFO_DURATION ->
                    SongInfoRow(stringResource(R.string.song_more_detail_duration), song.durationText)
                ActionMenuIds.SONG_INFO_PLAY_COUNT ->
                    SongInfoRow(
                        stringResource(R.string.song_more_detail_play_count),
                        (playbackStats?.playCount ?: 0).toString()
                    )
                ActionMenuIds.SONG_INFO_LISTENED ->
                    SongInfoRow(
                        stringResource(R.string.song_more_detail_listened_duration),
                        (playbackStats?.listenedMs ?: 0L).formatPlaybackDuration()
                    )
                ActionMenuIds.SONG_INFO_LAST_PLAYED ->
                    SongInfoRow(
                        stringResource(R.string.song_more_detail_last_played),
                        playbackStats?.lastPlayedAt?.takeIf { it > 0L }?.formatSongDateTime().orEmpty()
                    )
                ActionMenuIds.SONG_INFO_SIZE ->
                    SongInfoRow(stringResource(R.string.song_more_detail_size), formatFileSize(song.fileSize))
                ActionMenuIds.SONG_INFO_MODIFIED ->
                    SongInfoRow(
                        modifiedLabel,
                        displayedModifiedMs.formatSongDateTime(),
                        onClick = {
                            modifiedTimeDraft = displayedModifiedMs.formatSongDateTime()
                            editingModifiedTime = true
                        }
                    )
                ActionMenuIds.SONG_INFO_ADDED ->
                    SongInfoRow(stringResource(R.string.song_more_detail_added_time), song.dateAdded.formatSongDateTime())
                ActionMenuIds.SONG_INFO_FILE_NAME ->
                    SongInfoRow(
                        stringResource(R.string.song_more_detail_file_name),
                        song.fileName.ifBlank { song.path.substringAfterLast('/') }
                    )
                ActionMenuIds.SONG_INFO_PATH ->
                    SongInfoRow(pathLabel, song.path, onClick = { jumpTo(songInfoJumpRoute(SongInfoJump.Path, song)) })
                ActionMenuIds.SONG_INFO_DIRECTORY ->
                    SongInfoRow(directoryLabel, directoryValue, onClick = { jumpTo(songInfoJumpRoute(SongInfoJump.Directory, song)) })
                ActionMenuIds.SONG_INFO_MEDIA_INFO ->
                    SongMenuItem(stringResource(R.string.song_more_open_media_info), onOpenMediaInfo)
            }
        }
    }
    val modifiedFocus = remember { androidx.compose.ui.focus.FocusRequester() }
    EllaMiuixDialog(
        show = editingModifiedTime,
        title = modifiedLabel,
        onDismissRequest = { editingModifiedTime = false }
    ) {
        androidx.compose.runtime.LaunchedEffect(editingModifiedTime) {
            if (editingModifiedTime) {
                kotlinx.coroutines.delay(80)
                runCatching { modifiedFocus.requestFocus() }
            }
        }
        EllaMiuixTextField(
            value = modifiedTimeDraft,
            onValueChange = { modifiedTimeDraft = it },
            label = "yyyy-MM-dd HH:mm:ss",
            selectAllOnStart = true,
            focusRequester = modifiedFocus,
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                imeAction = androidx.compose.ui.text.input.ImeAction.Done
            )
        )
        Spacer(modifier = Modifier.padding(top = 12.dp))
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.common_cancel),
            confirmText = stringResource(R.string.common_save),
            onCancel = { editingModifiedTime = false },
            onConfirm = {
                val parsed = parseSongDateTime(modifiedTimeDraft)
                if (parsed == null) {
                    Toast.makeText(context, context.getString(R.string.song_more_modified_time_invalid), Toast.LENGTH_SHORT).show()
                    return@EllaMiuixDialogActions
                }
                scope.launch {
                    val ok = onUpdateModifiedTime?.invoke(parsed) ?: java.io.File(song.path).setLastModified(parsed)
                    if (ok) {
                        displayedModifiedMs = parsed
                        editingModifiedTime = false
                    } else {
                        Toast.makeText(context, context.getString(R.string.song_more_modified_time_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }
}

internal fun parseSongDateTime(text: String): Long? {
    return runCatching {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).parse(text.trim())?.time
    }.getOrNull()
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

private data class SongInfoNamePicker(
    val title: String,
    val names: List<String>,
    val jump: SongInfoJump
)

@Composable
private fun SongInfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    if (value.isBlank()) return
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.38f))
            .combinedClickable(
                onClick = { onClick?.invoke() },
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

