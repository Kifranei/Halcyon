package com.ella.music.ui.components

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.ella.music.data.model.Song
import com.ella.music.data.sanitizeExportFileName
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class LyricTimingSidecar(
    val uri: Uri,
    val displayName: String
)

internal suspend fun writeLyricTimingSidecar(
    context: Context,
    song: Song,
    format: LyricTimingFormat,
    content: String
): Result<LyricTimingSidecar> = withContext(Dispatchers.IO) {
    runCatching {
        val displayName = song.fileName.substringBeforeLast('.', song.fileName)
            .sanitizeExportFileName(fallback = "lyrics", maxLength = 110) + format.fileExtension()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val parent = File(song.path).parentFile
                ?: error("The song has no writable parent directory")
            val target = File(parent, displayName)
            target.writeText(content, Charsets.UTF_8)
            return@runCatching LyricTimingSidecar(Uri.fromFile(target), displayName)
        }

        val location = resolveMediaStoreLocation(context, song.path)
            ?: error("The song folder is not available through MediaStore")
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri(location.volumeName)
        val existing = resolver.query(
            collection,
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.RELATIVE_PATH} = ?",
            arrayOf(displayName, location.relativePath),
            null
        )?.use { cursor ->
            if (!cursor.moveToFirst()) null
            else Uri.withAppendedPath(collection, cursor.getLong(0).toString())
        }
        val target = existing ?: resolver.insert(
            collection,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
                put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType())
                put(MediaStore.MediaColumns.RELATIVE_PATH, location.relativePath)
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
        ) ?: error("Unable to create lyric sidecar")

        try {
            resolver.openOutputStream(target, "wt")?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                writer.write(content)
            } ?: error("Unable to open lyric sidecar")
            if (existing == null) {
                resolver.update(
                    target,
                    ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
                    null,
                    null
                )
            }
            LyricTimingSidecar(target, displayName)
        } catch (error: Throwable) {
            if (existing == null) resolver.delete(target, null, null)
            throw error
        }
    }
}

private data class MediaStoreLocation(
    val volumeName: String,
    val relativePath: String
)

private fun resolveMediaStoreLocation(context: Context, rawPath: String): MediaStoreLocation? {
    val normalizedPath = File(rawPath).absolutePath.replace('\\', '/')
    val primaryRoot = Environment.getExternalStorageDirectory().absolutePath.replace('\\', '/').trimEnd('/')
    if (normalizedPath.startsWith("$primaryRoot/", ignoreCase = true)) {
        return MediaStoreLocation(
            volumeName = MediaStore.VOLUME_EXTERNAL_PRIMARY,
            relativePath = normalizedPath.substring(primaryRoot.length + 1)
                .substringBeforeLast('/', "")
                .let { if (it.isBlank()) "" else "$it/" }
        )
    }

    val storagePrefix = "/storage/"
    if (!normalizedPath.startsWith(storagePrefix, ignoreCase = true)) return null
    val volumeName = normalizedPath.removePrefix(storagePrefix).substringBefore('/').lowercase()
    if (volumeName.isBlank() || volumeName == "emulated") return null
    val availableVolume = MediaStore.getExternalVolumeNames(context)
        .firstOrNull { it.equals(volumeName, ignoreCase = true) }
        ?: return null
    val root = "$storagePrefix${normalizedPath.removePrefix(storagePrefix).substringBefore('/')}"
    val relativePath = normalizedPath.substring(root.length).trimStart('/')
        .substringBeforeLast('/', "")
        .let { if (it.isBlank()) "" else "$it/" }
    return MediaStoreLocation(availableVolume, relativePath)
}

private fun LyricTimingFormat.fileExtension(): String = when (this) {
    LyricTimingFormat.Lrc -> ".lrc"
    LyricTimingFormat.Elrc -> ".elrc"
    LyricTimingFormat.Ttml -> ".ttml"
}

private fun LyricTimingFormat.mimeType(): String = when (this) {
    LyricTimingFormat.Ttml -> "application/ttml+xml"
    LyricTimingFormat.Lrc, LyricTimingFormat.Elrc -> "text/plain"
}
