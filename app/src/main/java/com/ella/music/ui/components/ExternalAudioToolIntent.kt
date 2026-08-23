package com.ella.music.ui.components

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.core.net.toUri
import com.ella.music.data.model.Song
import java.io.File

internal fun Context.songFromExternalAudioToolIntent(intent: Intent): Song? {
    val uri = intent.audioToolUri() ?: return null
    val cursorInfo = if (uri.scheme == "content") {
        runCatching {
            contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                (if (nameIndex >= 0) cursor.getString(nameIndex).orEmpty() else "") to
                    (if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else 0L)
            }
        }.getOrNull()
    } else {
        val file = uri.path?.let(::File)
        file?.name.orEmpty() to (file?.length() ?: 0L)
    }
    val metadata = runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(this, uri)
            ExternalAudioToolMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty(),
                artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty(),
                album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty(),
                duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L
            )
        } finally {
            retriever.release()
        }
    }.getOrDefault(ExternalAudioToolMetadata())
    val fileName = cursorInfo?.first.orEmpty().ifBlank {
        uri.lastPathSegment?.substringAfterLast('/').orEmpty().ifBlank { "audio" }
    }
    return Song(
        id = (uri.toString().hashCode().toLong() and Long.MAX_VALUE).coerceAtLeast(1L),
        title = metadata.title.ifBlank { fileName.substringBeforeLast('.', fileName) },
        artist = metadata.artist,
        album = metadata.album,
        albumId = 0L,
        duration = metadata.duration,
        path = uri.toString(),
        fileName = fileName,
        fileSize = cursorInfo?.second ?: 0L,
        mimeType = intent.type.orEmpty().ifBlank { contentResolver.getType(uri).orEmpty() }
    )
}

private fun Intent.audioToolUri(): Uri? {
    if (action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE) {
        sharedAudioUri()?.let { return it }
    }
    data?.let { intentData ->
        if (intentData.scheme.equals("halcyon", ignoreCase = true)) {
            AUDIO_URI_KEYS.asSequence()
                .mapNotNull { key -> intentData.getQueryParameter(key)?.trim()?.takeIf(String::isNotBlank) }
                .firstOrNull()
                ?.let(::parseExternalAudioUri)
                ?.let { return it }
        } else {
            return intentData
        }
    }
    sharedAudioUri()?.let { return it }
    AUDIO_URI_KEYS
        .asSequence()
        .mapNotNull { key -> getStringExtra(key)?.trim()?.takeIf(String::isNotBlank) }
        .firstOrNull()
        ?.let(::parseExternalAudioUri)
        ?.let { return it }
    return null
}

private fun Intent.sharedAudioUri(): Uri? {
    val stream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
    } else {
        @Suppress("DEPRECATION")
        getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
    }
    stream?.let { return it }
    clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri?.let { return it }
    return null
}

private fun parseExternalAudioUri(raw: String): Uri =
    if (raw.startsWith("/") || raw.matches(Regex("^[A-Za-z]:[\\\\/].*"))) {
        Uri.fromFile(File(raw))
    } else {
        raw.toUri()
    }

private val AUDIO_URI_KEYS = listOf(
    "uri",
    "contentUri",
    "content_uri",
    "path",
    "filePath",
    "file_path"
)

private data class ExternalAudioToolMetadata(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val duration: Long = 0L
)
