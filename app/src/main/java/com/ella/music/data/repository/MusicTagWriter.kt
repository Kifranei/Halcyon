package com.ella.music.data.repository

import android.content.ContentUris
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import com.ella.music.data.exception.WritePermissionRequiredException
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.metadata.AudioCoverInfo
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.metadata.AudioTagRepository
import com.ella.music.data.model.Song

/**
 * Low-level tag/cover writing against MediaStore URIs with file-path fallback, plus the
 * write-permission plumbing (MediaStore write-request intent senders). Logic moved verbatim from
 * [MusicRepository]; library-state updates (StateFlows, snapshots, cache refresh) stay in the
 * repository's public write methods.
 *
 * [metadataPathResolver] must be the repository's `Song.effectiveLocalPathForMetadata()` and
 * [clearMetadataCache] the repository's `clearMetadataCache(Song)` so cache invalidation behavior
 * stays identical.
 */
internal class MusicTagWriter(
    private val context: Context,
    private val audioTagRepository: AudioTagRepository,
    private val metadataPathResolver: (Song) -> String,
    private val clearMetadataCache: (Song) -> Unit
) {
    suspend fun writeSongTags(song: Song, tags: AudioTagInfo): Result<Unit> {
        if (song.isWebDavRemoteSong()) {
            return Result.failure(IllegalArgumentException("Online / WebDAV songs are not supported for tag editing"))
        }
        val path = metadataPathResolver(song)
        val writableUri = song.writableAudioUri()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && writableUri != null) {
            val uriResult = runCatching {
                val pfd = context.contentResolver.openFileDescriptor(writableUri, "rw")
                    ?: error("Unable to open audio file for editing")
                pfd.use { descriptor ->
                    audioTagRepository.writeTags(descriptor, tags).getOrThrow()
                }
            }
            if (uriResult.isSuccess) {
                audioTagRepository.clear(path)
                return Result.success(Unit)
            }

            val error = uriResult.exceptionOrNull()
            if (error is SecurityException || error?.isWritePermissionError() == true) {
                return Result.failure(error)
            }
            Log.w("MusicRepo", "MediaStore tag write failed for ${song.path}, falling back to file path", error)
        }
        return audioTagRepository.writeTags(path, tags)
    }

    suspend fun writeSongCover(song: Song, cover: AudioCoverInfo?): Result<Unit> {
        if (song.isWebDavRemoteSong()) {
            return Result.failure(IllegalArgumentException("Online / WebDAV songs are not supported for cover editing"))
        }
        val path = metadataPathResolver(song)
        val writableUri = song.writableAudioUri()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && writableUri != null) {
            val uriResult = runCatching {
                val pfd = context.contentResolver.openFileDescriptor(writableUri, "rw")
                    ?: error("Unable to open audio file for cover editing")
                pfd.use { descriptor ->
                    if (cover == null) {
                        audioTagRepository.removeEmbeddedCover(descriptor, path).getOrThrow()
                    } else {
                        audioTagRepository.writeEmbeddedCover(descriptor, path, cover).getOrThrow()
                    }
                }
            }
            if (uriResult.isSuccess) {
                clearMetadataCache(song)
                return Result.success(Unit)
            }

            val error = uriResult.exceptionOrNull()
            if (error is SecurityException || error?.isWritePermissionError() == true) {
                return Result.failure(error)
            }
            Log.w("MusicRepo", "MediaStore cover write failed for ${song.path}, falling back to file path", error)
        }
        return if (cover == null) {
            audioTagRepository.removeEmbeddedCover(path)
        } else {
            audioTagRepository.writeEmbeddedCover(path, cover)
        }
    }

    fun createWritePermissionIntentSender(song: Song): IntentSender? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uri = song.writableAudioUri() ?: return null
        return runCatching {
            MediaStore.createWriteRequest(context.contentResolver, listOf(uri)).intentSender
        }.getOrNull()
    }

    fun writePermissionRequestIfNeeded(result: Result<Unit>, song: Song): Result<Song?>? {
        val error = result.exceptionOrNull() ?: return null
        if (!error.isWritePermissionError()) return null
        val sender = createWritePermissionIntentSender(song) ?: return null
        return Result.failure(WritePermissionRequiredException(sender))
    }

    private fun Song.writableAudioUri(): Uri? {
        if (path.isContentAudioSource()) return Uri.parse(path)
        if (id > 0L) return ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
        return null
    }

    private fun Throwable.isWritePermissionError(): Boolean {
        var current: Throwable? = this
        while (current != null) {
            if (current is SecurityException) return true
            val message = current.message.orEmpty()
            if (
                message.contains("permission", ignoreCase = true) ||
                message.contains("denied", ignoreCase = true) ||
                message.contains("EACCES", ignoreCase = true) ||
                message.contains("EPERM", ignoreCase = true) ||
                message.contains("Operation not permitted", ignoreCase = true)
            ) {
                return true
            }
            current = current.cause
        }
        return false
    }
}
