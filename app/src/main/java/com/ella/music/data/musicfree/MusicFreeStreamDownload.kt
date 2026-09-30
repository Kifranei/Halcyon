package com.ella.music.data.musicfree

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.annotation.StringRes
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.AudioContainerFormat
import com.ella.music.data.detectAudioContainerFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.UUID

/** Header-bearing downloads use the same origin guard as playback, not system DownloadManager. */
internal object MusicFreeStreamDownload {
    private class DownloadFailure(@StringRes val messageRes: Int, vararg val args: Any) : IOException()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS).callTimeout(30, TimeUnit.MINUTES).build()

    fun enqueue(context: Context, song: Song, fileName: String) {
        val app = context.applicationContext
        scope.launch {
            val message = try {
                save(app, song, fileName)
                app.getString(R.string.musicfree_download_complete, song.title)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                val reason = failureReason(app, error) ?: song.title
                app.getString(R.string.musicfree_download_failed, reason)
            }
            withContext(Dispatchers.Main) { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
        }
    }

    internal fun failureReason(context: Context, error: Throwable): String? =
        if (error is DownloadFailure) context.getString(error.messageRes, *error.args) else error.localizedMessage

    private suspend fun save(context: Context, song: Song, fileName: String) {
        val stage = File.createTempFile("musicfree-download-", ".part", context.cacheDir)
        try {
            val format = transfer(song.path, stage, MusicFreeStreamHeaders.getInstance(context), http,
                song.fileName, song.mimeType)
            currentCoroutineContext().ensureActive()
            publish(context, song.copy(mimeType = format.mimeType), stage, fileNameForFormat(fileName, format))
        } finally {
            stage.delete()
        }
    }

    internal suspend fun transfer(
        markedUrl: String,
        target: File,
        headers: MusicFreeStreamHeaders,
        baseClient: OkHttpClient,
        fallbackFileName: String = markedUrl,
        fallbackMimeType: String = ""
    ) = withContext(Dispatchers.IO) {
        val resolved = headers.resolveStream(markedUrl, baseClient)
            ?: throw DownloadFailure(R.string.musicfree_stream_request_expired)
        val call = resolved.client.newCall(Request.Builder().url(resolved.url).build())
        // Interrupting a socket read alone may wait for its timeout. Closing the active call
        // when the parent job is cancelled promptly unblocks both response and body reads.
        val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        try {
            runInterruptible { call.execute() }.use { response ->
                if (!response.isSuccessful) throw DownloadFailure(R.string.musicfree_download_http_error, response.code)
                val body = response.body ?: throw DownloadFailure(R.string.musicfree_download_empty)
                // A server may return a preview-sized range even when no Range was requested.
                // Accept 206 only when it explicitly describes the complete representation.
                val completeRangeLength = if (response.code == 206) {
                    val range = Regex("bytes\\s+0-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
                        .matchEntire(response.header("Content-Range").orEmpty().trim())
                    val end = range?.groupValues?.get(1)?.toLongOrNull()
                    val total = range?.groupValues?.get(2)?.toLongOrNull()
                    if (total == null || total <= 0L || end != total - 1L) {
                        throw DownloadFailure(R.string.musicfree_download_incomplete)
                    }
                    total
                } else null
                val format = inspectResponse(response, fallbackFileName, fallbackMimeType)
                val expected = body.contentLength()
                var copied = 0L
                target.outputStream().buffered().use { output ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = runInterruptible { input.read(buffer) }
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                        }
                    }
                }
                if (copied == 0L || (expected >= 0L && copied != expected) ||
                    (completeRangeLength != null && copied != completeRangeLength)
                ) {
                    throw DownloadFailure(R.string.musicfree_download_incomplete)
                }
                format
            }
        } catch (error: Exception) {
            call.cancel()
            target.delete()
            // call.cancel() unblocks reads with SocketException; cancellation must retain the
            // parent's CancellationException rather than fail its sibling jobs with that I/O.
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            cancellationWatcher.cancel()
        }
    }

    /** A bounded Range probe gives DownloadManager a real filename and MIME for unscoped streams. */
    internal suspend fun probeFormat(url: String, fileName: String, mimeType: String): AudioContainerFormat =
        withContext(Dispatchers.IO) {
            val call = http.newBuilder().callTimeout(30, TimeUnit.SECONDS).build()
                .newCall(Request.Builder().url(url).header("Range", "bytes=0-511").build())
            val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
                try { awaitCancellation() } finally { call.cancel() }
            }
            try {
                runInterruptible { call.execute() }.use { response ->
                    if (!response.isSuccessful) throw DownloadFailure(R.string.musicfree_download_http_error, response.code)
                    inspectResponse(response, fileName, mimeType)
                }
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                throw error
            } finally {
                cancellationWatcher.cancel()
                call.cancel()
            }
        }

    internal fun fileNameForFormat(fileName: String, format: AudioContainerFormat): String {
        val extension = fileName.substringAfterLast('.', "")
        val stem = if (extension.length in 2..5) fileName.substringBeforeLast('.') else fileName
        return "$stem.${format.extension}"
    }

    private suspend fun inspectResponse(response: Response, fileName: String, mimeType: String): AudioContainerFormat {
        val body = response.body ?: throw DownloadFailure(R.string.musicfree_download_empty)
        val type = body.contentType()?.toString().orEmpty()
        val source = body.source()
        // Peek at the real bytes; reading the prefix must not remove it from the downloaded file.
        runInterruptible { source.request(512L) }
        val prefix = source.peek().readByteArray(minOf(512L, source.buffer.size))
        val isPlaylist = prefix.toString(Charsets.UTF_8).trimStart { it.isWhitespace() || it == '\uFEFF' }
            .startsWith("#EXTM3U", ignoreCase = true)
        if (isPlaylist || type.contains("mpegurl", ignoreCase = true) ||
            response.request.url.encodedPath.endsWith(".m3u8", true)
        ) throw DownloadFailure(R.string.musicfree_download_hls_unsupported)
        return detectAudioContainerFormat(prefix, type, fileName, mimeType)
    }

    private fun publish(context: Context, song: Song, file: File, name: String) {
        val mime = song.mimeType.ifBlank { "audio/mpeg" }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val directory = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), "Ella")
                .apply { mkdirs() }
            val requested = File(directory, name)
            val target = if (requested.createNewFile()) requested else {
                File(directory, "${UUID.randomUUID()}-$name").also {
                    if (!it.createNewFile()) throw DownloadFailure(R.string.musicfree_download_create_failed)
                }
            }
            try {
                file.copyTo(target, overwrite = true)
                MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(mime), null)
            } catch (error: Exception) {
                target.delete()
                throw error
            }
            return
        }
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/Ella/")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
            put(MediaStore.Audio.Media.TITLE, song.title)
            put(MediaStore.Audio.Media.ARTIST, song.artist)
            put(MediaStore.Audio.Media.ALBUM, song.album)
            put(MediaStore.Audio.Media.DURATION, song.duration)
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
        }
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw DownloadFailure(R.string.musicfree_download_create_failed)
        try {
            resolver.openOutputStream(uri, "w")?.use { output ->
                file.inputStream().use { it.copyTo(output) }
            } ?: throw DownloadFailure(R.string.musicfree_download_save_failed)
            if (resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null) <= 0) {
                throw DownloadFailure(R.string.musicfree_download_save_failed)
            }
        } catch (error: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw error
        }
    }
}
