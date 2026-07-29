package com.ella.music.ui.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import java.util.Locale

/**
 * Supplies the container type explicitly for SAF-backed videos. Some document providers return
 * application/octet-stream (or no type at all), so ExoPlayer cannot reliably infer MKV/WebM/MOV
 * from a content URI even though Media3 already has extractors for those containers.
 */
internal fun Context.buildMusicVideoMediaItem(uri: Uri): MediaItem {
    val declaredMimeType = if (uri.scheme.equals("content", ignoreCase = true)) {
        runCatching { contentResolver.getType(uri) }.getOrNull()
    } else {
        null
    }
    val containerMimeType = inferMusicVideoContainerMimeType(uri.toString(), declaredMimeType)
    return MediaItem.Builder()
        .setUri(uri)
        .apply {
            if (containerMimeType != null) setMimeType(containerMimeType)
        }
        .build()
}

internal fun inferMusicVideoContainerMimeType(
    source: String,
    declaredMimeType: String? = null
): String? {
    val normalizedDeclaredType = declaredMimeType
        ?.substringBefore(';')
        ?.trim()
        ?.lowercase(Locale.ROOT)
        ?.takeUnless { it.isBlank() || it == "application/octet-stream" }
    if (normalizedDeclaredType != null) return normalizedDeclaredType

    val decodedSource = runCatching { Uri.decode(source) }.getOrDefault(source)
        .substringBefore('?')
        .substringBefore('#')
    return when (decodedSource.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "mkv" -> MimeTypes.VIDEO_MATROSKA
        "webm" -> MimeTypes.VIDEO_WEBM
        "mov" -> MimeTypes.VIDEO_QUICK_TIME
        "mp4", "m4v" -> MimeTypes.VIDEO_MP4
        else -> null
    }
}
