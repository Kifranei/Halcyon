package com.ella.music.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Makes phone-local media reachable by Chromecast and DLNA renderers on the same LAN. */
internal class LocalCastMediaServer(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entries = ConcurrentHashMap<String, Entry>()
    private val originals = ConcurrentHashMap<String, MediaItem>()
    private var serverSocket: ServerSocket? = null
    private var acceptJob: Job? = null

    @Synchronized
    fun urlFor(mediaItem: MediaItem): String? {
        val uri = mediaItem.localConfiguration?.uri ?: return null
        if (uri.scheme.equals("http", true) || uri.scheme.equals("https", true)) {
            return uri.toString()
        }
        val address = runCatching { localIpv4Address() }.getOrNull() ?: return null
        if (runCatching { ensureStarted() }.isFailure) return null
        val socket = serverSocket ?: return null
        val token = mediaItem.mediaId.takeIf(String::isNotBlank)
            ?.let { "${it.hashCode().toUInt().toString(16)}-${uri.toString().hashCode().toUInt().toString(16)}" }
            ?: UUID.randomUUID().toString()
        val mimeType = mediaItem.localConfiguration?.mimeType
            ?: appContext.contentResolver.getType(uri)
            ?: "audio/mpeg"
        entries[token] = Entry(uri, mimeType)
        val url = "http://${address.hostAddress}:${socket.localPort}/media/$token"
        originals[url] = mediaItem
        return url
    }

    fun originalFor(url: String): MediaItem? = originals[url]

    @Synchronized
    private fun ensureStarted() {
        if (serverSocket?.isClosed == false) return
        val socket = ServerSocket(0).also { it.reuseAddress = true }
        serverSocket = socket
        acceptJob = scope.launch {
            while (isActive && !socket.isClosed) {
                val client = runCatching { socket.accept() }.getOrNull() ?: break
                launch { client.use(::serve) }
            }
        }
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 15_000
        val input = BufferedInputStream(socket.getInputStream())
        val output = BufferedOutputStream(socket.getOutputStream())
        val requestLine = input.readAsciiLine() ?: return
        val requestParts = requestLine.split(' ')
        val method = requestParts.getOrNull(0).orEmpty().uppercase()
        val token = requestParts.getOrNull(1)?.substringBefore('?')?.substringAfterLast('/')
        var rangeHeader: String? = null
        while (true) {
            val line = input.readAsciiLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Range:", true)) rangeHeader = line.substringAfter(':').trim()
        }
        val entry = token?.let(entries::get)
        if (method !in setOf("GET", "HEAD") || entry == null) {
            output.writeResponse("HTTP/1.1 404 Not Found", 0, "text/plain")
            output.flush()
            return
        }
        val source = openSource(entry.uri)
        if (source == null || source.length <= 0L) {
            source?.close()
            output.writeResponse("HTTP/1.1 404 Not Found", 0, "text/plain")
            output.flush()
            return
        }
        source.use {
            val requested = parseRange(rangeHeader, source.length)
            val start = requested?.first ?: 0L
            val end = requested?.last ?: (source.length - 1L)
            val count = (end - start + 1L).coerceAtLeast(0L)
            val status = if (requested != null) "HTTP/1.1 206 Partial Content" else "HTTP/1.1 200 OK"
            output.writeAscii("$status\r\n")
            output.writeAscii("Content-Type: ${entry.mimeType}\r\n")
            output.writeAscii("Content-Length: $count\r\n")
            output.writeAscii("Accept-Ranges: bytes\r\n")
            if (requested != null) output.writeAscii("Content-Range: bytes $start-$end/${source.length}\r\n")
            output.writeAscii("Connection: close\r\n\r\n")
            if (method == "GET") source.copyRangeTo(output, start, count)
            output.flush()
        }
    }

    private fun openSource(uri: Uri): SeekableSource? {
        if (uri.scheme.equals("content", true)) {
            val afd = appContext.contentResolver.openAssetFileDescriptor(uri, "r") ?: return null
            val length = afd.length.takeIf { it >= 0L } ?: afd.parcelFileDescriptor.statSize
            if (length <= 0L) {
                afd.close()
                return null
            }
            return SeekableSource(
                stream = FileInputStream(afd.fileDescriptor),
                offset = afd.startOffset,
                length = length,
                closeExtra = afd::close
            )
        }
        val file = when {
            uri.scheme.equals("file", true) -> uri.path?.let(::File)
            uri.scheme.isNullOrBlank() -> File(uri.toString())
            else -> null
        } ?: return null
        if (!file.isFile) return null
        return SeekableSource(FileInputStream(file), 0L, file.length())
    }

    fun release() {
        runCatching { serverSocket?.close() }
        serverSocket = null
        acceptJob?.cancel()
        acceptJob = null
        entries.clear()
        originals.clear()
        scope.cancel()
    }

    private data class Entry(val uri: Uri, val mimeType: String)

    private class SeekableSource(
        private val stream: FileInputStream,
        private val offset: Long,
        val length: Long,
        private val closeExtra: () -> Unit = {}
    ) : AutoCloseable {
        fun copyRangeTo(output: BufferedOutputStream, start: Long, count: Long) {
            stream.channel.position(offset + start)
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var remaining = count
            while (remaining > 0L) {
                val read = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                if (read < 0) break
                output.write(buffer, 0, read)
                remaining -= read
            }
        }

        override fun close() {
            runCatching { stream.close() }
            runCatching(closeExtra)
        }
    }
}

private fun BufferedInputStream.readAsciiLine(): String? {
    val bytes = ArrayList<Byte>(128)
    while (true) {
        val value = read()
        if (value < 0) return bytes.takeIf { it.isNotEmpty() }?.toByteArray()?.toString(Charsets.US_ASCII)
        if (value == '\n'.code) return bytes.toByteArray().toString(Charsets.US_ASCII).trimEnd('\r')
        if (bytes.size >= 16_384) return null
        bytes += value.toByte()
    }
}

private fun BufferedOutputStream.writeAscii(value: String) = write(value.toByteArray(Charsets.US_ASCII))

private fun BufferedOutputStream.writeResponse(status: String, length: Long, type: String) {
    writeAscii("$status\r\nContent-Type: $type\r\nContent-Length: $length\r\nConnection: close\r\n\r\n")
}

internal fun parseHttpByteRange(header: String?, length: Long): LongRange? = parseRange(header, length)

private fun parseRange(header: String?, length: Long): LongRange? {
    if (length <= 0L || header.isNullOrBlank() || !header.startsWith("bytes=", true)) return null
    val raw = header.substringAfter('=').substringBefore(',').trim()
    val startRaw = raw.substringBefore('-').trim()
    val endRaw = raw.substringAfter('-', "").trim()
    val range = if (startRaw.isEmpty()) {
        val suffixLength = endRaw.toLongOrNull()?.coerceAtMost(length) ?: return null
        (length - suffixLength)..(length - 1L)
    } else {
        val start = startRaw.toLongOrNull() ?: return null
        val end = endRaw.toLongOrNull() ?: (length - 1L)
        start..minOf(end, length - 1L)
    }
    return range.takeIf { it.first in 0 until length && it.last >= it.first }
}

private fun localIpv4Address(): Inet4Address? = Collections.list(NetworkInterface.getNetworkInterfaces())
    .asSequence()
    .filter { it.isUp && !it.isLoopback }
    .flatMap { Collections.list(it.inetAddresses).asSequence() }
    .filterIsInstance<Inet4Address>()
    .firstOrNull { it.isSiteLocalAddress }
