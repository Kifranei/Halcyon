package com.ella.music.ui.player

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.artist.ArtistMusicVideoMetadata
import com.ella.music.ui.artist.readArtistMusicVideoMetadata
import com.ella.music.ui.components.EllaMiuixBottomSheet
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.EllaMiuixSheetColumn
import com.ella.music.ui.components.openVideoWithMediaInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Displays the same metadata surface as the artist MV tab. Keeping this menu backed by the
 * shared metadata reader means a long-press from the player does not lose file/track details,
 * and every row keeps the artist page's long-press-to-copy affordance.
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
            maxHeight = 620.dp,
            spacing = 10.dp
        ) {
            Text(
                text = title.ifBlank { resolvedMetadata.fileName },
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            MusicVideoInfoRow(R.string.artist_music_video_file_name, resolvedMetadata.fileName)
            MusicVideoInfoRow(R.string.artist_music_video_path, resolvedMetadata.path)
            MusicVideoInfoRow(R.string.artist_music_video_real_path, resolvedMetadata.realPath)
            MusicVideoInfoRow(
                R.string.artist_music_video_size,
                Formatter.formatFileSize(context, resolvedMetadata.sizeBytes)
            )
            MusicVideoInfoRow(
                R.string.artist_music_video_modified,
                resolvedMetadata.modifiedAt.takeIf { it > 0L }?.let {
                    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
                } ?: "—"
            )
            MusicVideoInfoRow(
                R.string.artist_music_video_format,
                resolvedMetadata.mimeType.ifBlank { "video/*" }
            )
            MusicVideoInfoRow(
                R.string.artist_music_video_resolution,
                if (resolvedMetadata.width > 0 && resolvedMetadata.height > 0) {
                    "${resolvedMetadata.width} × ${resolvedMetadata.height}"
                } else {
                    "—"
                }
            )
            MusicVideoInfoRow(
                R.string.artist_music_video_duration,
                resolvedMetadata.durationMs.takeIf { it > 0L }?.formatPlaybackDuration() ?: "—"
            )
            MusicVideoInfoRow(
                R.string.artist_music_video_video_frame_rate,
                resolvedMetadata.videoFrameRate.ifBlank { "—" }
            )
            MusicVideoInfoRow(
                R.string.artist_music_video_video_bitrate,
                resolvedMetadata.videoBitrate.ifBlank { "—" }
            )
            MusicVideoInfoRow(
                R.string.artist_music_video_audio_sample_rate,
                resolvedMetadata.audioSampleRate.ifBlank { "—" }
            )
            MusicVideoInfoRow(
                R.string.artist_music_video_audio_bitrate,
                resolvedMetadata.audioBitrate.ifBlank { "—" }
            )
            EllaMiuixMenuItem(
                text = stringResource(R.string.artist_music_video_open_media_info),
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
            EllaMiuixMenuItem(
                text = stringResource(R.string.common_cancel),
                onClick = onDismiss
            )
        }
    }
}

@Composable
private fun ColumnScope.MusicVideoInfoRow(@StringRes label: Int, value: String) {
    val context = LocalContext.current
    val labelText = stringResource(label)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText(labelText, value))
                    Toast.makeText(
                        context,
                        context.getString(R.string.artist_music_video_info_item_copied, labelText),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            ),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = labelText,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
        Text(
            text = value,
            fontSize = 14.sp,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis
        )
    }
}
