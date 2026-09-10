package com.ella.music.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.ella.music.data.copyToBoundedOrThrow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class ExternalUriResolver(private val context: Context) {
    data class ResolvedAudioUri(
        val playbackUri: Uri,
        val copiedToCache: Boolean
    )

    suspend fun resolveForPlayback(
        uri: Uri,
        grantFlags: Int,
        preferredName: String? = null
    ): ResolvedAudioUri = withContext(Dispatchers.IO) {
        when (uri.scheme?.lowercase()) {
            "content" -> resolveContentUri(uri, grantFlags, preferredName)
            else -> ResolvedAudioUri(uri, copiedToCache = false)
        }
    }

    private fun resolveContentUri(
        uri: Uri,
        grantFlags: Int,
        preferredName: String?
    ): ResolvedAudioUri {
        ensureReadable(uri)
        persistReadGrantIfPossible(uri, grantFlags)
        val cached = copyContentUriToCache(uri, preferredName)
        return ResolvedAudioUri(Uri.fromFile(cached), copiedToCache = true)
    }

    private fun ensureReadable(uri: Uri) {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { return }
            ?: error("External audio uri is not readable: $uri")
    }

    private fun persistReadGrantIfPossible(uri: Uri, flags: Int): Boolean {
        if (flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION == 0) return false
        return runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrDefault(false)
    }

    private fun copyContentUriToCache(uri: Uri, preferredName: String?): File {
        val safeName = (preferredName ?: queryDisplayName(uri) ?: uri.lastPathSegment ?: "external_audio")
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("""[^\w.\-()\[\] ]+"""), "_")
            .ifBlank { "external_audio" }
        val extension = safeName.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.let { ".$it" }.orEmpty()
        val baseName = safeName.substringBeforeLast('.', safeName).take(64).ifBlank { "external_audio" }
        val dir = File(context.cacheDir, "external_audio").apply { mkdirs() }
        val target = File(dir, "${baseName}_${UUID.randomUUID()}$extension")
        synchronized(CACHE_LOCK) {
            val existingBytes = dir.listFiles().orEmpty().sumOf { file -> if (file.isFile) file.length() else 0L }
            val remainingBytes = MAX_EXTERNAL_AUDIO_CACHE_BYTES - existingBytes
            require(remainingBytes > 0L) { "External audio cache is full" }
            val maxCopyBytes = minOf(MAX_EXTERNAL_AUDIO_FILE_BYTES, remainingBytes)
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        input.copyToBoundedOrThrow(output, maxCopyBytes)
                    }
                } ?: error("External audio uri stream is not readable: $uri")
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
            if (target.length() <= 0L) {
                target.delete()
                error("External audio uri is empty: $uri")
            }
        }
        return target
    }

    private companion object {
        val CACHE_LOCK = Any()
        const val MAX_EXTERNAL_AUDIO_FILE_BYTES = 512L * 1024L * 1024L
        const val MAX_EXTERNAL_AUDIO_CACHE_BYTES = 2L * 1024L * 1024L * 1024L
    }

    private fun queryDisplayName(uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && !cursor.isNull(index)) cursor.getString(index) else null
                } else {
                    null
                }
            }
        }.getOrNull()
}
