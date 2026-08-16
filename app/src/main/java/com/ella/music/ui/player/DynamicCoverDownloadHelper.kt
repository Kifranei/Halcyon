package com.ella.music.ui.player

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.ella.music.data.SettingsManager
import com.ella.music.data.sanitizeExportFileName
import com.ella.music.data.model.Song
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.InputStream

/**
 * Downloads a dynamic cover video to the appropriate directory.
 *
 * Priority:
 * 1. Song's parent folder as {albumName}.mp4
 * 2. Movies/Halcyon/DynamicCovers/Album/{albumName}.mp4
 */
internal class DynamicCoverDownloadHelper(
    private val context: Context,
    private val song: Song
) {
    private val okHttpClient = OkHttpClient.Builder()
        .build()
    private val settingsManager by lazy { SettingsManager.getInstance(context) }

    suspend fun downloadVideo(videoUrl: String) {
        val fileName = determineFileName()
        val target = determineTarget(fileName)

        val request = Request.Builder()
            .url(videoUrl)
            .build()

        val response = okHttpClient.newCall(request).execute()
        if (!response.isSuccessful) {
            throw Exception("HTTP ${response.code}: ${response.message}")
        }

        response.body?.byteStream()?.use { input ->
            target.write(input)
        } ?: throw Exception("Empty response body")

        target.scan()
    }

    private fun determineFileName(): String {
        val albumName = song.album.ifBlank { "Unknown" }
        return "${albumName.sanitizeExportFileName(fallback = "Unknown", maxLength = 120)}.mp4"
    }

    private suspend fun determineTarget(fileName: String): DynamicCoverDownloadTarget {
        // Try song's parent folder first
        val songFile = song.path
            .takeUnless { it.startsWith("http://") || it.startsWith("https://") }
            ?.let { File(it) }

        val songFolder = songFile?.parentFile
        if (songFolder != null && songFolder.exists() && songFolder.isDirectory) {
            val candidate = File(songFolder, fileName)
            // Check if we can write here (not on read-only storage)
            runCatching {
                if (!candidate.exists()) {
                    candidate.createNewFile()
                    candidate.delete()
                }
                return DynamicCoverDownloadTarget.FileTarget(context, File(songFolder, fileName))
            }
        }

        settingsManager.dynamicCoverCustomFolders.first()
            .asSequence()
            .map(String::trim)
            .filter(String::isNotBlank)
            .mapNotNull { root ->
                if (root.startsWith("content://", ignoreCase = true)) {
                    resolveCustomRootTargetDocument(root, fileName)
                } else {
                    resolveCustomRootTargetFile(File(root), fileName)
                }
            }
            .firstOrNull()
            ?.let { return it }

        // Fallback: Movies/Halcyon/DynamicCovers/Album/
        val publicDir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            "Halcyon/DynamicCovers/Album"
        )
        return DynamicCoverDownloadTarget.FileTarget(context, File(publicDir, fileName))
    }

    private fun resolveCustomRootTargetFile(root: File, fileName: String): DynamicCoverDownloadTarget? {
        val baseRoot = runCatching {
            if (root.exists()) {
                root.takeIf { it.isDirectory }
            } else {
                root.mkdirs()
                root.takeIf { it.isDirectory }
            }
        }.getOrNull() ?: return null

        val albumFolder = when {
            baseRoot.name.equals("album", ignoreCase = true) -> baseRoot
            File(baseRoot, "Album").isDirectory -> File(baseRoot, "Album")
            File(baseRoot, "album").isDirectory -> File(baseRoot, "album")
            else -> baseRoot
        }

        return runCatching {
            if (!albumFolder.exists()) {
                albumFolder.mkdirs()
            }
            DynamicCoverDownloadTarget.FileTarget(context, File(albumFolder, fileName))
        }.getOrNull()
    }

    private fun resolveCustomRootTargetDocument(rootUri: String, fileName: String): DynamicCoverDownloadTarget? {
        val root = runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(rootUri))
        }.getOrNull() ?: return null
        if (!root.canWrite()) return null

        val albumFolder = when {
            root.name.equals("album", ignoreCase = true) -> root
            else -> root.findChildDirectoryIgnoreCase("Album")
                ?: root.findChildDirectoryIgnoreCase("album")
                ?: root.createDirectory("Album")
                ?: root
        }
        if (!albumFolder.canWrite()) return null

        val target = albumFolder.findFile(fileName)
            ?: albumFolder.createFile("video/mp4", fileName)
            ?: return null
        return DynamicCoverDownloadTarget.DocumentTarget(context, target)
    }

    private fun DocumentFile.findChildDirectoryIgnoreCase(name: String): DocumentFile? =
        listFiles().firstOrNull { it.isDirectory && it.name.equals(name, ignoreCase = true) }

    private sealed interface DynamicCoverDownloadTarget {
        fun write(input: InputStream)
        fun scan()

        class FileTarget(
            private val context: Context,
            private val file: File
        ) : DynamicCoverDownloadTarget {
            override fun write(input: InputStream) {
                file.parentFile?.mkdirs()
                file.outputStream().use { output -> input.copyTo(output) }
            }

            override fun scan() {
                try {
                    android.media.MediaScannerConnection.scanFile(
                        context,
                        arrayOf(file.absolutePath),
                        arrayOf("video/mp4"),
                        null
                    )
                } catch (e: Exception) {
                    Log.d("DynamicCoverDownload", "MediaStore scan failed", e)
                }
            }
        }

        class DocumentTarget(
            private val context: Context,
            private val file: DocumentFile
        ) : DynamicCoverDownloadTarget {
            override fun write(input: InputStream) {
                context.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
                    input.copyTo(output)
                } ?: throw IllegalStateException("Cannot open dynamic cover output stream: ${file.uri}")
            }

            override fun scan() = Unit
        }
    }
}
