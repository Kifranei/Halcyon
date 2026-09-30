package com.ella.music.ui.player

import android.text.format.Formatter
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.artist.ArtistMusicVideoMetadata
import com.ella.music.ui.components.EllaMiuixActionMenuGroup
import com.ella.music.ui.components.EllaMiuixSheetColumn
import com.ella.music.ui.components.SongInfoRow
import com.ella.music.ui.components.SongMenuItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MusicVideoInfoContent(title: String, metadata: ArtistMusicVideoMetadata, onOpenMediaInfo: () -> Unit) {
    val context = LocalContext.current
    val rows = listOf(
        R.string.player_detail_song to title.ifBlank { metadata.fileName },
        R.string.artist_music_video_file_name to metadata.fileName,
        R.string.artist_music_video_path to metadata.path,
        R.string.artist_music_video_real_path to metadata.realPath,
        R.string.artist_music_video_size to Formatter.formatFileSize(context, metadata.sizeBytes),
        R.string.artist_music_video_modified to (metadata.modifiedAt.takeIf { it > 0L }?.let {
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
        } ?: "—"),
        R.string.artist_music_video_format to metadata.mimeType.ifBlank { "video/*" },
        R.string.artist_music_video_resolution to (if (metadata.width > 0 && metadata.height > 0) "${metadata.width} × ${metadata.height}" else "—"),
        R.string.artist_music_video_duration to (metadata.durationMs.takeIf { it > 0L }?.formatPlaybackDuration() ?: "—"),
        R.string.artist_music_video_video_frame_rate to metadata.videoFrameRate.ifBlank { "—" },
        R.string.artist_music_video_video_bitrate to metadata.videoBitrate.ifBlank { "—" },
        R.string.artist_music_video_audio_sample_rate to metadata.audioSampleRate.ifBlank { "—" },
        R.string.artist_music_video_audio_bitrate to metadata.audioBitrate.ifBlank { "—" }
    )
    EllaMiuixSheetColumn(verticalPadding = 8.dp, spacing = 8.dp, showHandle = false) {
        EllaMiuixActionMenuGroup {
            rows.forEach { (label, value) -> SongInfoRow(label = stringResource(label), value = value) }
        }
        EllaMiuixActionMenuGroup {
            SongMenuItem(stringResource(R.string.artist_music_video_open_media_info), onOpenMediaInfo)
        }
    }
}
