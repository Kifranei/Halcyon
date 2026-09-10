package com.ella.music.ui.player

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.artist.ArtistMusicVideoMetadata
import com.ella.music.ui.artist.readArtistMusicVideoMetadata
import com.ella.music.ui.components.EllaMiuixActionMenuGroup
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixSheetColumn
import com.ella.music.ui.components.SongInfoRow
import com.ella.music.ui.components.SongMenuItem
import com.ella.music.ui.components.openVideoWithMediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Displays the same metadata surface as the artist MV tab, styled identically to SongInfoSheet.
 */
@Composable
internal fun MusicVideoInfoDialog(
    source: DynamicCoverSource,
    title: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val metadata by produceState<ArtistMusicVideoMetadata?>(
        initialValue = null,
        source.failureKey,
        source.uri
    ) {
        value = withContext(Dispatchers.IO) {
            readArtistMusicVideoMetadata(context, source)
        }
    }
    val resolvedMetadata = metadata ?: ArtistMusicVideoMetadata(
        fileName = source.uri.lastPathSegment.orEmpty(),
        path = source.uri.toString(),
        realPath = source.uri.toString(),
        mimeType = "video/*"
    )

    EllaMiuixBottomSheet(
        show = true,
        title = stringResource(R.string.artist_music_video_info),
        onDismissRequest = onDismiss
    ) {
        EllaMiuixSheetColumn(
            verticalPadding = 8.dp,
            spacing = 8.dp,
            showHandle = false
        ) {
            EllaMiuixActionMenuGroup {
                SongInfoRow(
                    label = stringResource(R.string.player_detail_song),
                    value = title.ifBlank { resolvedMetadata.fileName }
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_file_name),
                    value = resolvedMetadata.fileName
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_path),
                    value = resolvedMetadata.path
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_real_path),
                    value = resolvedMetadata.realPath
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_size),
                    value = Formatter.formatFileSize(context, resolvedMetadata.sizeBytes)
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_modified),
                    value = resolvedMetadata.modifiedAt.takeIf { it > 0L }?.let {
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
                    } ?: "—"
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_format),
                    value = resolvedMetadata.mimeType.ifBlank { "video/*" }
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_resolution),
                    value = if (resolvedMetadata.width > 0 && resolvedMetadata.height > 0) {
                        "${resolvedMetadata.width} × ${resolvedMetadata.height}"
                    } else {
                        "—"
                    }
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_duration),
                    value = resolvedMetadata.durationMs.takeIf { it > 0L }?.formatPlaybackDuration() ?: "—"
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_video_frame_rate),
                    value = resolvedMetadata.videoFrameRate.ifBlank { "—" }
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_video_bitrate),
                    value = resolvedMetadata.videoBitrate.ifBlank { "—" }
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_audio_sample_rate),
                    value = resolvedMetadata.audioSampleRate.ifBlank { "—" }
                )
                SongInfoRow(
                    label = stringResource(R.string.artist_music_video_audio_bitrate),
                    value = resolvedMetadata.audioBitrate.ifBlank { "—" }
                )
            }
            EllaMiuixActionMenuGroup {
                SongMenuItem(
                    title = stringResource(R.string.artist_music_video_open_media_info),
                    onClick = {
                        onDismiss()
                        openVideoWithMediaInfo(
                            context = context,
                            uri = source.uri,
                            title = resolvedMetadata.fileName.ifBlank { title },
                            mimeType = resolvedMetadata.mimeType.ifBlank { "video/*" }
                        )
                    }
                )
            }
        }
    }
}
