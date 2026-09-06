package com.ella.music.data.scanner

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

internal object MediaStoreLibraryIndexer {
    private const val TAG = "MediaStoreIndexer"
    private const val SCAN_TIMEOUT_MS = 1_200L
    private const val PLACEHOLDER_CUSTOM_FOLDER = "__ella_no_custom_folder__"

    fun scanRoots(includeFolders: List<String>): List<String> {
        val roots = LinkedHashSet<String>()
        runCatching { Environment.getExternalStorageDirectory()?.absolutePath }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }
            ?.let(roots::add)
        runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC)?.absolutePath
        }.getOrNull()?.takeIf { it.isNotBlank() }?.let(roots::add)
        includeFolders.asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && it != PLACEHOLDER_CUSTOM_FOLDER }
            .forEach(roots::add)
        return roots.toList()
    }

    fun audioCollectionUris(context: Context): List<Uri> {
        val uris = LinkedHashSet<Uri>()
        // Salt Player always queries EXTERNAL_CONTENT_URI. Keep it first so a volume-name
        // lookup failure cannot hide the primary library.
        uris += MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        runCatching { MediaStore.getExternalVolumeNames(context) }.getOrNull().orEmpty().forEach { volume ->
            runCatching { MediaStore.Audio.Media.getContentUri(volume) }.getOrNull()?.let(uris::add)
        }
        return uris.toList()
    }

    fun reconstructStoragePath(
        data: String?,
        relativePath: String?,
        displayName: String?,
        volumeName: String?
    ): String {
        if (!data.isNullOrBlank()) return data
        val name = displayName.orEmpty().trim()
        if (name.isBlank()) return ""
        val relative = relativePath.orEmpty().replace('\\', '/').trim('/')
        val root = storageRootForVolume(volumeName)
        return if (relative.isBlank()) "$root/$name" else "$root/$relative/$name"
    }

    fun mediaStoreLibrarySyncKey(id: Long, path: String): String =
        if (id > 0L) {
            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
        } else {
            path
        }

    suspend fun refreshIndexedAudio(
        context: Context,
        folders: List<String>,
        extraPaths: List<String> = emptyList()
    ) {
        val roots = scanRoots(folders)
        broadcastDirectoryScan(context, roots)
        val scanTargets = (roots + extraPaths)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
        if (scanTargets.isEmpty()) return
        waitForMediaScanner(context, scanTargets)
    }

    @Suppress("DEPRECATION")
    private fun broadcastDirectoryScan(context: Context, roots: List<String>) {
        roots.forEach { path ->
            runCatching {
                val intent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).apply {
                    data = Uri.fromFile(File(path))
                }
                context.sendBroadcast(intent)
            }.onFailure { error ->
                Log.w(TAG, "MEDIA_SCANNER_SCAN_FILE broadcast failed for $path", error)
            }
        }
    }

    private suspend fun waitForMediaScanner(context: Context, paths: List<String>) {
        withTimeoutOrNull(SCAN_TIMEOUT_MS) {
            suspendCancellableCoroutine { continuation ->
                val remaining = AtomicInteger(paths.size)
                val completed = AtomicBoolean(false)
                fun finish() {
                    if (completed.compareAndSet(false, true) && continuation.isActive) {
                        continuation.resume(Unit)
                    }
                }
                runCatching {
                    MediaScannerConnection.scanFile(
                        context.applicationContext,
                        paths.toTypedArray(),
                        null
                    ) { _, _ ->
                        if (remaining.decrementAndGet() <= 0) finish()
                    }
                }.onFailure { error ->
                    Log.w(TAG, "MediaScannerConnection.scanFile failed", error)
                    finish()
                }
                continuation.invokeOnCancellation { completed.set(true) }
            }
        }
    }

    private fun storageRootForVolume(volumeName: String?): String {
        val primary = runCatching { Environment.getExternalStorageDirectory()?.absolutePath }
            .getOrNull()
            .orEmpty()
            .ifBlank { "/storage/emulated/0" }
        if (volumeName.isNullOrBlank() ||
            volumeName.equals(MediaStore.VOLUME_EXTERNAL_PRIMARY, ignoreCase = true) ||
            volumeName.equals("external_primary", ignoreCase = true)
        ) {
            return primary.trimEnd('/')
        }
        return "/storage/${volumeName.trim()}"
    }
}
