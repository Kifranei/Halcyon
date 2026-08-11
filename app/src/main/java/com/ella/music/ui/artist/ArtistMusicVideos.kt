package com.ella.music.ui.artist

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.text.format.Formatter
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.EllaMiuixMenuItem
import com.ella.music.ui.components.EllaMiuixSheetColumn
import com.ella.music.ui.components.EllaMiuixSheetHandle
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.player.DynamicCoverSource
import com.ella.music.ui.player.readMusicVideoDurationMs
import com.ella.music.ui.player.readMusicVideoPreviewFrame
import com.ella.music.ui.player.musicVideoSource
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.More
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.io.File
import java.util.Date
import java.util.Locale
import androidx.compose.ui.graphics.asImageBitmap

internal data class ArtistMusicVideo(
    val song: Song,
    val source: DynamicCoverSource,
    val durationMs: Long,
    val preview: Bitmap?,
    val metadata: ArtistMusicVideoMetadata
)

internal data class ArtistMusicVideoMetadata(
    val fileName: String = "",
    val path: String = "",
    val realPath: String = "",
    val sizeBytes: Long = 0L,
    val modifiedAt: Long = 0L,
    val mimeType: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val durationMs: Long = 0L,
    val audioSampleRate: String = "",
    val audioBitrate: String = "",
    val videoFrameRate: String = "",
    val videoBitrate: String = "",
    val preview: Bitmap? = null
)

internal enum class ArtistMusicVideoSortMode {
    ReleaseAsc,
    ReleaseDesc,
    DurationAsc,
    DurationDesc,
    NameAsc,
    NameDesc
}

private data class CachedArtistMusicVideoSource(
    val source: DynamicCoverSource,
    val cachedAtMs: Long
)

private const val ARTIST_MV_SOURCE_CACHE_TTL_MS = 30_000L
private const val ARTIST_MV_SOURCE_CACHE_MAX_SIZE = 256
private val artistMusicVideoSourceCache = LinkedHashMap<String, CachedArtistMusicVideoSource>(
    ARTIST_MV_SOURCE_CACHE_MAX_SIZE,
    0.75f,
    true
)

internal fun clearArtistMusicVideoSourceCache() {
    synchronized(artistMusicVideoSourceCache) {
        artistMusicVideoSourceCache.clear()
    }
}

@StringRes
internal fun ArtistMusicVideoSortMode.labelRes(): Int = when (this) {
    ArtistMusicVideoSortMode.ReleaseAsc,
    ArtistMusicVideoSortMode.ReleaseDesc -> R.string.playlist_song_sort_year
    ArtistMusicVideoSortMode.DurationAsc,
    ArtistMusicVideoSortMode.DurationDesc -> R.string.artist_music_video_sort_duration
    ArtistMusicVideoSortMode.NameAsc,
    ArtistMusicVideoSortMode.NameDesc -> R.string.artist_music_video_sort_title
}

internal fun ArtistMusicVideoSortMode.isDescending(): Boolean = when (this) {
    ArtistMusicVideoSortMode.ReleaseDesc,
    ArtistMusicVideoSortMode.DurationDesc,
    ArtistMusicVideoSortMode.NameDesc -> true
    else -> false
}

internal suspend fun resolveArtistMusicVideos(
    context: Context,
    songs: List<Song>,
    dynamicCoverFolders: List<String>,
    musicVideoFolders: List<String>
): List<ArtistMusicVideo> = enrichArtistMusicVideos(
    context = context,
    sources = resolveArtistMusicVideoSources(
        context = context,
        songs = songs,
        dynamicCoverFolders = dynamicCoverFolders,
        musicVideoFolders = musicVideoFolders
    )
)

/** Resolve the lightweight source list first so the MV tab can appear before metadata is read. */
internal suspend fun resolveArtistMusicVideoSources(
    context: Context,
    songs: List<Song>,
    dynamicCoverFolders: List<String>,
    musicVideoFolders: List<String>,
    onProgress: suspend (List<ArtistMusicVideo>) -> Unit = {}
): List<ArtistMusicVideo> {
    val resolvedVideos = mutableListOf<ArtistMusicVideo>()
    songs.forEach { song ->
        val sourceCacheKey = listOf(
            song.id,
            song.path,
            song.dateModified,
            song.fileSize,
            song.title,
            song.artist,
            song.album,
            dynamicCoverFolders.joinToString("\u001f"),
            musicVideoFolders.joinToString("\u001f")
        ).joinToString("\u001e")
        val now = System.currentTimeMillis()
        val source = synchronized(artistMusicVideoSourceCache) {
            artistMusicVideoSourceCache[sourceCacheKey]
                ?.takeIf { now - it.cachedAtMs < ARTIST_MV_SOURCE_CACHE_TTL_MS }
                ?.source
        } ?: song.musicVideoSource(
            context = context,
            customRootPaths = dynamicCoverFolders,
            musicVideoCustomFolders = musicVideoFolders
        )?.also { resolved ->
            synchronized(artistMusicVideoSourceCache) {
                artistMusicVideoSourceCache[sourceCacheKey] = CachedArtistMusicVideoSource(resolved, now)
                while (artistMusicVideoSourceCache.size > ARTIST_MV_SOURCE_CACHE_MAX_SIZE) {
                    val iterator = artistMusicVideoSourceCache.entries.iterator()
                    iterator.next()
                    iterator.remove()
                }
            }
        } ?: return@forEach
        resolvedVideos += ArtistMusicVideo(
            song = song,
            source = source,
            durationMs = song.duration,
            preview = null,
            metadata = ArtistMusicVideoMetadata(
                fileName = source.uri.lastPathSegment.orEmpty(),
                path = source.uri.toString(),
                realPath = resolveArtistMusicVideoRealPath(source.uri)
            )
        )
        // Artist pages can contain hundreds of songs. Publish each hit as soon as its source is
        // found so the MV tab and first rows do not wait for the complete SAF/file scan (#422).
        onProgress(resolvedVideos.toList())
    }
    return resolvedVideos
}

internal fun enrichArtistMusicVideos(
    context: Context,
    sources: List<ArtistMusicVideo>
): List<ArtistMusicVideo> = sources.map { item ->
    val metadata = readArtistMusicVideoMetadata(context, item.source)
    item.copy(
        durationMs = metadata.durationMs.takeIf { it > 0L } ?: item.song.duration,
        preview = metadata.preview,
        metadata = metadata
    )
}

internal fun List<ArtistMusicVideo>.sortedForArtistMusicVideo(
    mode: ArtistMusicVideoSortMode
): List<ArtistMusicVideo> = when (mode) {
    ArtistMusicVideoSortMode.ReleaseAsc -> sortedWith(
        compareBy<ArtistMusicVideo> { it.song.year.isBlank() }
            .thenBy { it.song.year.lowercase(Locale.ROOT) }
            .thenBy { it.song.title.lowercase(Locale.ROOT) }
    )
    ArtistMusicVideoSortMode.ReleaseDesc -> sortedWith(
        compareBy<ArtistMusicVideo> { it.song.year.isBlank() }
            .thenByDescending { it.song.year.lowercase(Locale.ROOT) }
            .thenByDescending { it.song.title.lowercase(Locale.ROOT) }
    )
    ArtistMusicVideoSortMode.DurationAsc -> sortedBy(ArtistMusicVideo::durationMs)
    ArtistMusicVideoSortMode.DurationDesc -> sortedByDescending(ArtistMusicVideo::durationMs)
    ArtistMusicVideoSortMode.NameAsc -> sortedBy { it.song.title.lowercase(Locale.ROOT) }
    ArtistMusicVideoSortMode.NameDesc -> sortedByDescending { it.song.title.lowercase(Locale.ROOT) }
}

@Composable
internal fun ArtistMusicVideoRow(
    item: ArtistMusicVideo,
    selectionMode: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMore: () -> Unit
) {
    val container = if (selected) {
        MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
    } else {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.72f)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(container)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val preview = item.preview?.takeUnless { it.isRecycled }
        Box(
            modifier = Modifier
                .weight(0.44f)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(12.dp))
        ) {
            if (preview != null) {
                Image(
                    bitmap = preview.asImageBitmap(),
                    contentDescription = item.song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                SafeCoverImage(
                    model = item.source.uri,
                    contentDescription = item.song.title,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
        }
        Column(
            modifier = Modifier.weight(0.52f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = item.song.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOf(
                    item.durationMs.formatPlaybackDuration(),
                    item.song.year.takeIf(String::isNotBlank)
                ).filterNotNull().joinToString(" · "),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (selectionMode) {
                Text(
                    text = if (selected) "✓" else stringResource(R.string.common_multi_select),
                    fontSize = 12.sp,
                    color = MiuixTheme.colorScheme.primary
                )
            }
        }
        IconButton(onClick = onMore, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = MiuixIcons.Regular.More,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

internal fun deleteArtistMusicVideos(context: Context, items: List<ArtistMusicVideo>): Int =
    items.count { item ->
        val uri = item.source.uri
        when {
            uri.scheme.equals("file", ignoreCase = true) -> File(uri.path.orEmpty()).delete()
            uri.scheme.equals("content", ignoreCase = true) ->
                DocumentFile.fromSingleUri(context, uri)?.delete() == true
            else -> false
        }
    }

private fun readArtistMusicVideoMetadata(
    context: Context,
    source: DynamicCoverSource
): ArtistMusicVideoMetadata = runCatching {
    val uri = source.uri
    val document = if (uri.scheme.equals("content", ignoreCase = true)) {
        DocumentFile.fromSingleUri(context, uri)
    } else {
        null
    }
    val file = uri.path?.let(::File)?.takeIf { uri.scheme.equals("file", ignoreCase = true) }
    val trackMetadata = readArtistMusicVideoTrackMetadata(context, uri)
    MediaMetadataRetriever().use { retriever ->
        if (uri.scheme.equals("content", ignoreCase = true)) {
            retriever.setDataSource(context, uri)
        } else {
            retriever.setDataSource(uri.path.orEmpty())
        }
        val durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            ?.toLongOrNull()
            .orZero()
        val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0
        val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
            ?.toIntOrNull()
            ?.coerceAtLeast(0)
            ?: 0
        val fallbackVideoBitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
            ?.let(::formatBitrate)
            .orEmpty()
        ArtistMusicVideoMetadata(
            fileName = document?.name ?: file?.name ?: uri.lastPathSegment.orEmpty(),
            path = uri.toString(),
            realPath = resolveArtistMusicVideoRealPath(uri),
            sizeBytes = (document?.length() ?: file?.length() ?: 0L).coerceAtLeast(0L),
            modifiedAt = (document?.lastModified() ?: file?.lastModified() ?: 0L).coerceAtLeast(0L),
            mimeType = document?.type ?: guessVideoMimeType(uri),
            width = width,
            height = height,
            durationMs = durationMs,
            audioSampleRate = trackMetadata.audioSampleRate.ifBlank {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_SAMPLERATE).orEmpty()
            },
            audioBitrate = trackMetadata.audioBitrate,
            videoFrameRate = trackMetadata.videoFrameRate,
            videoBitrate = trackMetadata.videoBitrate.ifBlank { fallbackVideoBitrate },
            preview = context.readMusicVideoPreviewFrame(uri)
        )
    }
}.getOrElse {
    ArtistMusicVideoMetadata(
        fileName = source.uri.lastPathSegment.orEmpty(),
        path = source.uri.toString(),
        realPath = resolveArtistMusicVideoRealPath(source.uri),
        sizeBytes = 0L,
        modifiedAt = 0L,
        mimeType = guessVideoMimeType(source.uri),
        width = 0,
        height = 0,
        durationMs = context.readMusicVideoDurationMs(source.uri),
        audioSampleRate = "",
        audioBitrate = "",
        videoFrameRate = "",
        videoBitrate = "",
        preview = context.readMusicVideoPreviewFrame(source.uri)
    )
}

private fun Long?.orZero(): Long = this ?: 0L

private data class ArtistMusicVideoTrackMetadata(
    val audioSampleRate: String = "",
    val audioBitrate: String = "",
    val videoFrameRate: String = "",
    val videoBitrate: String = ""
)

private fun readArtistMusicVideoTrackMetadata(
    context: Context,
    uri: Uri
): ArtistMusicVideoTrackMetadata = runCatching {
    val extractor = MediaExtractor()
    try {
        if (uri.scheme.equals("content", ignoreCase = true)) {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { descriptor ->
                extractor.setDataSource(descriptor.fileDescriptor)
                readArtistMusicVideoTracks(extractor)
            } ?: ArtistMusicVideoTrackMetadata()
        } else {
            extractor.setDataSource(uri.path.orEmpty())
            readArtistMusicVideoTracks(extractor)
        }
    } finally {
        extractor.release()
    }
}.getOrDefault(ArtistMusicVideoTrackMetadata())

private fun readArtistMusicVideoTracks(extractor: MediaExtractor): ArtistMusicVideoTrackMetadata {
    var audioSampleRate = ""
    var audioBitrate = ""
    var videoFrameRate = ""
    var videoBitrate = ""
    repeat(extractor.trackCount) { index ->
        val format = extractor.getTrackFormat(index)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
        val bitrate = format.longFormatValue(MediaFormat.KEY_BIT_RATE)
            ?.takeIf { it > 0 }
            ?.let(::formatBitrate)
            .orEmpty()
        if (mime.startsWith("audio/")) {
            audioBitrate = bitrate
            audioSampleRate = format.intFormatValue(MediaFormat.KEY_SAMPLE_RATE)
                ?.takeIf { it > 0 }
                ?.let { "$it Hz" }
                .orEmpty()
        } else if (mime.startsWith("video/")) {
            videoBitrate = bitrate
            videoFrameRate = format.floatFormatValue(MediaFormat.KEY_FRAME_RATE)
                ?.takeIf { it > 0f }
                ?.let { "%.3f fps".format(Locale.ROOT, it).trimEnd('0').trimEnd('.') }
                .orEmpty()
        }
    }
    return ArtistMusicVideoTrackMetadata(
        audioSampleRate = audioSampleRate,
        audioBitrate = audioBitrate,
        videoFrameRate = videoFrameRate,
        videoBitrate = videoBitrate
    )
}

private fun MediaFormat.intFormatValue(key: String): Int? =
    runCatching { if (containsKey(key)) getInteger(key) else null }.getOrNull()

private fun MediaFormat.longFormatValue(key: String): Long? =
    runCatching { if (containsKey(key)) getLong(key) else null }.getOrNull()
        ?: intFormatValue(key)?.toLong()

private fun MediaFormat.floatFormatValue(key: String): Float? =
    runCatching { if (containsKey(key)) getFloat(key) else null }.getOrNull()
        ?: intFormatValue(key)?.toFloat()

private fun formatBitrate(bitsPerSecond: Long): String =
    if (bitsPerSecond >= 1_000_000L) {
        "%.2f Mbps".format(Locale.ROOT, bitsPerSecond / 1_000_000f).trimEnd('0').trimEnd('.')
    } else {
        "%.0f kbps".format(Locale.ROOT, bitsPerSecond / 1_000f)
    }

private fun resolveArtistMusicVideoRealPath(uri: Uri): String = when {
    uri.scheme.equals("file", ignoreCase = true) -> uri.path.orEmpty()
    uri.scheme.equals("content", ignoreCase = true) -> {
        val documentId = runCatching { DocumentsContract.getDocumentId(uri) }.getOrNull()
        val parts = documentId?.split(':', limit = 2).orEmpty()
        val volume = parts.getOrNull(0).orEmpty()
        val path = parts.getOrNull(1).orEmpty().trim('/')
        when {
            volume.equals("primary", ignoreCase = true) && path.isNotBlank() -> "/storage/emulated/0/$path"
            volume.equals("primary", ignoreCase = true) -> "/storage/emulated/0"
            volume.isNotBlank() && path.isNotBlank() -> "/storage/$volume/$path"
            else -> uri.toString()
        }
    }
    else -> uri.toString()
}

private fun guessVideoMimeType(uri: Uri): String {
    val extension = uri.lastPathSegment.orEmpty().substringAfterLast('.', "").lowercase(Locale.ROOT)
    return when (extension) {
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "m4v" -> "video/x-m4v"
        else -> "video/*"
    }
}

@Composable
internal fun ArtistMusicVideoActionMenu(
    onShare: () -> Unit,
    onInfo: () -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    EllaMiuixSheetColumn {
        EllaMiuixMenuItem(
            text = stringResource(R.string.common_share),
            onClick = { onDismiss(); onShare() }
        )
        EllaMiuixMenuItem(
            text = stringResource(R.string.artist_music_video_info),
            onClick = { onDismiss(); onInfo() }
        )
        EllaMiuixMenuItem(
            text = stringResource(R.string.song_more_delete_permanently),
            onClick = { onDismiss(); onDelete() },
            danger = true
        )
        EllaMiuixMenuItem(
            text = stringResource(R.string.common_cancel),
            onClick = onDismiss
        )
    }
}

@Composable
internal fun ArtistMusicVideoInfoSheet(
    item: ArtistMusicVideo,
    onOpenMediaInfo: () -> Unit,
    onDismiss: () -> Unit
) {
    val metadata = item.metadata
    EllaMiuixSheetColumn(
        maxHeight = 620.dp,
        spacing = 10.dp
    ) {
        Text(
            text = item.song.title,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        ArtistMusicVideoInfoRow(R.string.artist_music_video_file_name, metadata.fileName)
        ArtistMusicVideoInfoRow(R.string.artist_music_video_path, metadata.path)
        ArtistMusicVideoInfoRow(R.string.artist_music_video_real_path, metadata.realPath)
        ArtistMusicVideoInfoRow(
            R.string.artist_music_video_size,
            Formatter.formatFileSize(LocalContext.current, metadata.sizeBytes)
        )
        ArtistMusicVideoInfoRow(
            R.string.artist_music_video_modified,
            metadata.modifiedAt.takeIf { it > 0L }?.let {
                SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(it))
            } ?: "—"
        )
        ArtistMusicVideoInfoRow(R.string.artist_music_video_format, metadata.mimeType.ifBlank { "video/*" })
        ArtistMusicVideoInfoRow(
            R.string.artist_music_video_resolution,
            if (metadata.width > 0 && metadata.height > 0) "${metadata.width} × ${metadata.height}" else "—"
        )
        ArtistMusicVideoInfoRow(
            R.string.artist_music_video_duration,
            metadata.durationMs.takeIf { it > 0L }?.formatPlaybackDuration() ?: "—"
        )
        ArtistMusicVideoInfoRow(
            R.string.artist_music_video_video_frame_rate,
            metadata.videoFrameRate.ifBlank { "—" }
        )
        ArtistMusicVideoInfoRow(
            R.string.artist_music_video_video_bitrate,
            metadata.videoBitrate.ifBlank { "—" }
        )
        ArtistMusicVideoInfoRow(
            R.string.artist_music_video_audio_sample_rate,
            metadata.audioSampleRate.ifBlank { "—" }
        )
        ArtistMusicVideoInfoRow(
            R.string.artist_music_video_audio_bitrate,
            metadata.audioBitrate.ifBlank { "—" }
        )
        EllaMiuixMenuItem(
            text = stringResource(R.string.artist_music_video_open_media_info),
            onClick = onOpenMediaInfo
        )
        EllaMiuixMenuItem(
            text = stringResource(R.string.common_cancel),
            onClick = onDismiss
        )
    }
}

@Composable
private fun ColumnScope.ArtistMusicVideoInfoRow(@StringRes label: Int, value: String) {
    val context = LocalContext.current
    val labelText = stringResource(label)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
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
