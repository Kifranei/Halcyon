package com.ella.music.data.webdav

import android.content.Context
import android.text.Html
import android.util.Log
import android.util.Xml
import com.ella.music.R
import com.ella.music.data.AppLogStore
import com.ella.music.data.AppLogType
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.scanner.supportedAudioFileExtensions
import okhttp3.Credentials
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.RandomAccessFile
import java.net.URI
import java.net.SocketTimeoutException
import java.net.URLDecoder
import java.net.UnknownHostException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLHandshakeException
import org.xmlpull.v1.XmlPullParser
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Returns the end offset of the complete FLAC metadata chain, or null when the supplied bytes
 * stop in the middle of a metadata block. The returned offset is deliberately limited to Int so
 * it can also be used as a safe in-memory prefix length.
 */
internal fun flacMetadataEnd(bytes: ByteArray): Int? {
    val flacOffset = flacMarkerOffset(bytes) ?: return null

    var offset = flacOffset + 4
    while (offset + 4 <= bytes.size) {
        val header = bytes[offset].toInt() and 0xff
        val blockType = header and 0x7f
        if (blockType == 0x7f) return null
        val payloadLength =
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
                ((bytes[offset + 2].toInt() and 0xff) shl 8) or
                (bytes[offset + 3].toInt() and 0xff)
        val nextOffset = offset.toLong() + 4L + payloadLength.toLong()
        if (nextOffset > bytes.size.toLong() || nextOffset > Int.MAX_VALUE) return null
        offset = nextOffset.toInt()
        if ((header and 0x80) != 0) return offset
    }
    return null
}

private fun flacMarkerOffset(bytes: ByteArray): Int? {
    if (bytes.size >= 4 &&
        bytes[0] == 'f'.code.toByte() &&
        bytes[1] == 'L'.code.toByte() &&
        bytes[2] == 'a'.code.toByte() &&
        bytes[3] == 'C'.code.toByte()
    ) return 0
    // A FLAC stream may have an ID3v2 tag before the native fLaC marker. The ID3 size is a
    // four-byte synchsafe integer, so wait for the whole prefix before rejecting the stream.
    if (bytes.size < 3 ||
        bytes[0] != 'I'.code.toByte() ||
        bytes[1] != 'D'.code.toByte() ||
        bytes[2] != '3'.code.toByte()
    ) return null
    if (bytes.size < 10) return null
    val id3Size = (0 until 4).fold(0) { result, index ->
        (result shl 7) or (bytes[6 + index].toInt() and 0x7f)
    }
    val markerOffset = 10L + id3Size.toLong()
    if (markerOffset > Int.MAX_VALUE || markerOffset + 4L > bytes.size.toLong()) return null
    val offset = markerOffset.toInt()
    return offset.takeIf {
        bytes[it] == 'f'.code.toByte() &&
            bytes[it + 1] == 'L'.code.toByte() &&
            bytes[it + 2] == 'a'.code.toByte() &&
            bytes[it + 3] == 'C'.code.toByte()
    }
}

/** Returns true when the downloaded prefix can belong to a FLAC stream. */
private fun flacPrefixLooksValid(file: File): Boolean {
    return runCatching {
        RandomAccessFile(file, "r").use { input ->
            val header = ByteArray(4)
            if (input.read(header) != header.size) return@use false
            (header[0] == 'f'.code.toByte() &&
                header[1] == 'L'.code.toByte() &&
                header[2] == 'a'.code.toByte() &&
                header[3] == 'C'.code.toByte()) ||
                (header[0] == 'I'.code.toByte() &&
                    header[1] == 'D'.code.toByte() &&
                    header[2] == '3'.code.toByte())
        }
    }.getOrDefault(false)
}

/**
 * File-backed counterpart of [flacMetadataEnd]. It only reads four-byte block headers and seeks
 * over payloads, so validating a large embedded picture never allocates a second full prefix.
 */
private fun flacMetadataEnd(file: File): Long? {
    if (!file.isFile || file.length() <= 0L) return null
    return runCatching {
        RandomAccessFile(file, "r").use { input ->
            val flacOffset = flacMarkerOffset(input) ?: return@use null
            val length = input.length()
            val header = ByteArray(4)
            var offset = flacOffset + 4L
            while (offset + header.size <= length) {
                input.seek(offset)
                if (input.read(header) != header.size) return@use null
                val rawHeader = header[0].toInt() and 0xff
                val blockType = rawHeader and 0x7f
                if (blockType == 0x7f) return@use null
                val payloadLength =
                    ((header[1].toInt() and 0xff) shl 16) or
                        ((header[2].toInt() and 0xff) shl 8) or
                        (header[3].toInt() and 0xff)
                val nextOffset = offset + 4L + payloadLength.toLong()
                if (nextOffset < offset || nextOffset > length) return@use null
                offset = nextOffset
                if ((rawHeader and 0x80) != 0) return@use offset
            }
            null
        }
    }.getOrNull()
}

private fun flacMarkerOffset(input: RandomAccessFile): Long? {
    input.seek(0L)
    val header = ByteArray(10)
    val read = input.read(header)
    if (read >= 4 &&
        header[0] == 'f'.code.toByte() &&
        header[1] == 'L'.code.toByte() &&
        header[2] == 'a'.code.toByte() &&
        header[3] == 'C'.code.toByte()
    ) return 0L
    if (read < header.size ||
        header[0] != 'I'.code.toByte() ||
        header[1] != 'D'.code.toByte() ||
        header[2] != '3'.code.toByte()
    ) return null
    val id3Size = (0 until 4).fold(0L) { result, index ->
        (result shl 7) or (header[6 + index].toLong() and 0x7fL)
    }
    val markerOffset = 10L + id3Size
    if (markerOffset < 0L || markerOffset + 4L > input.length()) return null
    input.seek(markerOffset)
    val marker = ByteArray(4)
    if (input.read(marker) != marker.size) return null
    return markerOffset.takeIf {
        marker[0] == 'f'.code.toByte() &&
            marker[1] == 'L'.code.toByte() &&
            marker[2] == 'a'.code.toByte() &&
            marker[3] == 'C'.code.toByte()
    }
}

enum class WebDavAuthMode {
    AUTO,
    BASIC,
    DIGEST
}

data class WebDavConfig(
    val url: String,
    val username: String,
    val password: String,
    val authMode: WebDavAuthMode = WebDavAuthMode.AUTO
) {
    val isConfigured: Boolean get() = url.trim().isNotBlank()
}

data class WebDavItem(
    val name: String,
    val url: String,
    val isDirectory: Boolean,
    val size: Long = 0L,
    val mimeType: String = ""
)

data class WebDavTestResult(
    val ok: Boolean,
    val message: String
)

class WebDavException(message: String) : IOException(message)

object WebDavClient {
    private const val TAG = "WebDavClient"
    private const val DEFAULT_LIST_BATCH_SIZE = 200
    private const val MAX_PROPFIND_ERROR_BODY_CHARS = 8 * 1024
    private const val MAX_PROPFIND_ITEMS = 20_000
    private const val MAX_SPARSE_METADATA_FILE_SIZE = 1L * 1024 * 1024 * 1024
    private const val FLAC_METADATA_INITIAL_BYTES = 64 * 1024L
    private const val FLAC_METADATA_MAX_BYTES = 32 * 1024 * 1024L
    private const val FLAC_METADATA_MARKER_SUFFIX = ".flac-meta"
    private val audioExtensions = supportedAudioFileExtensions

    @Volatile
    private var appContext: Context? = null

    fun initContext(context: Context) {
        appContext = context.applicationContext
    }

    private fun requireContext(): Context =
        appContext ?: throw IllegalStateException("WebDavClient.initContext() must be called before using WebDavClient")

    private val listCache = ConcurrentHashMap<String, List<WebDavItem>>()
    private val xmlMediaType = "application/xml; charset=utf-8".toMediaType()
    private val secureRandom = SecureRandom()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor(AppNetworkLoggingInterceptor(TAG))
        .authenticator { _, response ->
            response.request.tag(WebDavConfig::class.java)?.let { config ->
                authenticate(response, config)
            }
        }
        .build()

    fun newAuthenticatedOkHttpClient(configProvider: () -> WebDavConfig): OkHttpClient {
        return OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor(AppNetworkLoggingInterceptor(TAG))
            .authenticator { _, response -> authenticate(response, configProvider()) }
            .addInterceptor { chain ->
                val config = configProvider()
                val request = chain.request().newBuilder()
                    .tag(WebDavConfig::class.java, config)
                    .apply { applyPreemptiveBasicAuth(config) }
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    fun isAudioFile(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in audioExtensions
    }

    fun test(config: WebDavConfig): Boolean {
        return testDetailed(config).ok
    }

    fun testDetailed(config: WebDavConfig): WebDavTestResult {
        val ctx = requireContext()
        if (!config.isConfigured) {
            return WebDavTestResult(ok = false, message = ctx.getString(R.string.webdav_please_enter_address))
        }
        return runCatching {
            val response = executePropfind(normalizeCollectionUrl(config.url), config, depth = "0")
            if (response.code in 200..399) {
                Log.i(TAG, "WebDAV test succeeded: ${config.url.safeLogUrl()} code=${response.code}")
                WebDavTestResult(ok = true, message = ctx.getString(R.string.webdav_connection_succeeded))
            } else {
                val message = response.toFriendlyMessage(ctx)
                Log.w(TAG, "WebDAV test failed: ${config.url.safeLogUrl()} code=${response.code} message=$message")
                WebDavTestResult(ok = false, message = message)
            }
        }.getOrElse { error ->
            Log.e(TAG, "WebDAV test failed", error)
            WebDavTestResult(ok = false, message = error.toFriendlyMessage(ctx))
        }
    }

    fun listAudioRecursive(
        config: WebDavConfig,
        url: String,
        maxDepth: Int = 12,
        maxItems: Int = 10_000
    ): List<WebDavItem> {
        val result = ArrayList<WebDavItem>()
        val visited = HashSet<String>()
        var firstError: Throwable? = null
        fun walk(dirUrl: String, depth: Int) {
            if (depth > maxDepth || result.size >= maxItems) return
            val visitKey = normalizeCollectionUrl(dirUrl).trimEnd('/').lowercase(Locale.ROOT)
            if (!visited.add(visitKey)) return
            val children = runCatching { list(config, dirUrl) }.getOrElse { error ->
                if (firstError == null) firstError = error
                Log.w(TAG, "WebDAV recursive list failed: ${dirUrl.safeLogUrl()}", error)
                emptyList()
            }
            children.forEach { item ->
                if (result.size >= maxItems) return
                if (item.isDirectory) {
                    walk(item.url, depth + 1)
                } else {
                    result += item
                }
            }
        }
        walk(url, 0)
        // A real empty directory is valid. An empty result after the root request failed is not;
        // propagate that failure so folder actions can show a useful error instead of appearing
        // to do nothing.
        if (result.isEmpty()) firstError?.let { throw it }
        return result
    }

    fun list(
        config: WebDavConfig,
        url: String = config.url,
        forceRefresh: Boolean = false,
        includeNonAudioFiles: Boolean = false
    ): List<WebDavItem> = listBatched(
        config = config,
        url = url,
        forceRefresh = forceRefresh,
        includeNonAudioFiles = includeNonAudioFiles
    )

    /**
     * Reads a directory in bounded UI-sized chunks. PROPFIND itself has no portable offset/limit
     * parameter, so the server response is parsed once and the already sorted result is merged in
     * batches. This keeps large flat directories from making Compose compose thousands of rows in
     * one frame while retaining one consistent snapshot for callers such as the library scanner.
     */
    fun listBatched(
        config: WebDavConfig,
        url: String = config.url,
        forceRefresh: Boolean = false,
        includeNonAudioFiles: Boolean = false,
        batchSize: Int = DEFAULT_LIST_BATCH_SIZE,
        onBatch: (List<WebDavItem>) -> Unit = {}
    ): List<WebDavItem> {
        if (!config.isConfigured) return emptyList()
        val ctx = requireContext()
        val requestUrl = normalizeCollectionUrl(url)
        val cacheKey = "${requestUrl}|${config.username}|${config.authMode}|files=$includeNonAudioFiles"
        if (!forceRefresh) {
            listCache[cacheKey]?.let {
                emitListBatches(it, batchSize, onBatch)
                return it
            }
        }
        val propfind = executePropfindItems(requestUrl, config, depth = "1")
        if (propfind.code !in 200..399) {
            val message = WebDavResponse(propfind.code, propfind.body).toFriendlyMessage(ctx)
            Log.w(TAG, "WebDAV list failed: ${requestUrl.safeLogUrl()} code=${propfind.code} message=$message")
            throw WebDavException(message)
        }

        val result = propfind.items
            .filterNot { normalizeCollectionUrl(it.url) == requestUrl }
            .filter { it.isDirectory || includeNonAudioFiles || isAudioFile(it.name) }
            .sortedWith(compareByDescending<WebDavItem> { it.isDirectory }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        emitListBatches(result, batchSize, onBatch)
        listCache[cacheKey] = result
        return result
    }

    private fun emitListBatches(
        items: List<WebDavItem>,
        batchSize: Int,
        onBatch: (List<WebDavItem>) -> Unit
    ) {
        val safeBatchSize = batchSize.coerceAtLeast(1)
        if (items.isEmpty()) return
        var end = 0
        while (end < items.size) {
            end = minOf(end + safeBatchSize, items.size)
            // Keep the historical cumulative callback contract without copying all previous
            // elements for every batch. The sorted result is immutable after this point, so a
            // sub-list is a stable read-only snapshot and avoids O(n²) allocation for large
            // WebDAV folders.
            onBatch(items.subList(0, end))
        }
    }

    fun clearListCache() {
        listCache.clear()
    }

    fun normalizeFileUrl(url: String): String = normalizeRequestUrl(url)

    fun uploadFile(url: String, config: WebDavConfig, data: ByteArray, contentType: String = "application/json") {
        val ctx = requireContext()
        val requestUrl = normalizeRequestUrl(url)
        ensureParentDirectory(requestUrl, config)
        val body = data.toRequestBody(contentType.toMediaType())
        val request = Request.Builder()
            .url(requestUrl)
            .put(body)
            .tag(WebDavConfig::class.java, config)
            .apply { applyPreemptiveBasicAuth(config) }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..399) {
                throw WebDavException(WebDavResponse(response.code, response.body?.string().orEmpty()).toFriendlyMessage(ctx))
            }
        }
    }

    fun uploadFileFromString(url: String, config: WebDavConfig, content: String, contentType: String = "application/json") {
        uploadFile(url, config, content.toByteArray(Charsets.UTF_8), contentType)
    }

    fun uploadFileFromFile(url: String, config: WebDavConfig, file: File, contentType: String = "application/zip") {
        val ctx = requireContext()
        require(file.isFile) { "Upload file does not exist" }
        val requestUrl = normalizeRequestUrl(url)
        ensureParentDirectory(requestUrl, config)
        val body = file.asRequestBody(contentType.toMediaType())
        val request = Request.Builder()
            .url(requestUrl)
            .put(body)
            .tag(WebDavConfig::class.java, config)
            .apply { applyPreemptiveBasicAuth(config) }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..399) {
                throw WebDavException(WebDavResponse(response.code, response.body?.string().orEmpty()).toFriendlyMessage(ctx))
            }
        }
    }

    fun mkdir(url: String, config: WebDavConfig) {
        val ctx = requireContext()
        val requestUrl = normalizeRequestUrl(url)
        val body = "".toRequestBody(null)
        val request = Request.Builder()
            .url(requestUrl)
            .method("MKCOL", body)
            .tag(WebDavConfig::class.java, config)
            .apply { applyPreemptiveBasicAuth(config) }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..399 && response.code != 405) {
                Log.w(TAG, "WebDAV MKCOL failed: ${requestUrl.safeLogUrl()} code=${response.code}")
            }
        }
    }

    private fun ensureParentDirectory(url: String, config: WebDavConfig) {
        val parentUrl = runCatching {
            val uri = URI(url)
            val parentPath = uri.path?.substringBeforeLast('/')?.let { "$it/" } ?: return
            URI(uri.scheme, uri.authority, parentPath, null, null).toString()
        }.getOrDefault(return)
        mkdir(parentUrl, config)
    }

    fun downloadToFile(url: String, config: WebDavConfig, target: File): File {
        val ctx = requireContext()
        val request = Request.Builder()
            .url(normalizeRequestUrl(url))
            .get()
            .tag(WebDavConfig::class.java, config)
            .apply { applyPreemptiveBasicAuth(config) }
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (response.code !in 200..399) {
                throw WebDavException(WebDavResponse(response.code, response.body?.string().orEmpty()).toFriendlyMessage(ctx))
            }
            val body = response.body ?: throw WebDavException(ctx.getString(R.string.webdav_file_download_failed))
            target.parentFile?.mkdirs()
            target.outputStream().use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
        }
        return target
    }

    fun downloadHeaderToFile(
        url: String,
        config: WebDavConfig,
        target: File,
        maxBytes: Long = 512 * 1024L
    ): File? {
        return runCatching {
            val safeMaxBytes = maxBytes.coerceAtLeast(16 * 1024L)
            val requestUrl = normalizeRequestUrl(url)
            val request = Request.Builder()
                .url(requestUrl)
                .get()
                .tag(WebDavConfig::class.java, config)
                .header("Range", "bytes=0-${safeMaxBytes - 1}")
                .apply { applyPreemptiveBasicAuth(config) }
                .build()

            httpClient.newCall(request).execute().use { response ->
                when (response.code) {
                    200, 206 -> Unit
                    401, 403, 404, 416 -> {
                        Log.w(TAG, "WebDAV header prefetch skipped url=${requestUrl.safeLogUrl()} code=${response.code}")
                        return@use null
                    }
                    else -> {
                        Log.w(TAG, "WebDAV header prefetch failed url=${requestUrl.safeLogUrl()} code=${response.code}")
                        return@use null
                    }
                }
                val body = response.body ?: return@use null
                target.parentFile?.mkdirs()
                flacMetadataMarkerFile(target).delete()
                target.outputStream().use { output ->
                    body.byteStream().use { input ->
                        input.copyToBounded(output, safeMaxBytes)
                    }
                }
                if (target.length() <= 0L) {
                    target.delete()
                    null
                } else {
                    target
                }
            }
        }.getOrElse { error ->
            Log.w(TAG, "WebDAV header prefetch failed url=${url.safeLogUrl()}", error)
            target.delete()
            null
        }
    }

    suspend fun downloadHeaderToFileCancellable(
        url: String,
        config: WebDavConfig,
        target: File,
        maxBytes: Long = 512 * 1024L
    ): File? = suspendCancellableCoroutine { continuation ->
        val safeMaxBytes = maxBytes.coerceAtLeast(16 * 1024L)
        val requestUrl = normalizeRequestUrl(url)
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .tag(WebDavConfig::class.java, config)
            .header("Range", "bytes=0-${safeMaxBytes - 1}")
            .apply { applyPreemptiveBasicAuth(config) }
            .build()
        val temporary = File(target.parentFile, "${target.name}.part")
        val call = httpClient.newCall(request)

        continuation.invokeOnCancellation {
            call.cancel()
            temporary.delete()
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                temporary.delete()
                if (!call.isCanceled()) {
                    Log.w(TAG, "WebDAV header prefetch failed url=${requestUrl.safeLogUrl()}", e)
                }
                if (continuation.isActive) continuation.resume(null) { _, _, _ -> }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.code != 200 && it.code != 206) return@use null
                        val body = it.body ?: return@use null
                        target.parentFile?.mkdirs()
                        flacMetadataMarkerFile(target).delete()
                        temporary.outputStream().use { output ->
                            body.byteStream().use { input ->
                                input.copyToBounded(output, safeMaxBytes)
                            }
                        }
                        if (temporary.length() <= 0L || !continuation.isActive) {
                            temporary.delete()
                            return@use null
                        }
                        if (target.exists()) target.delete()
                        if (!temporary.renameTo(target)) {
                            temporary.copyTo(target, overwrite = true)
                            temporary.delete()
                        }
                        target
                    }
                }.getOrElse { error ->
                    temporary.delete()
                    if (!call.isCanceled()) {
                        Log.w(TAG, "WebDAV header prefetch failed url=${requestUrl.safeLogUrl()}", error)
                    }
                    null
                }
                if (continuation.isActive) continuation.resume(result) { _, _, _ -> }
            }
        })
    }

    /**
     * Caches the metadata-bearing ends of a remote audio file in one sparse local file. MP3 and
     * similar formats usually keep tags at the head, while MP4/M4A often put the moov atom,
     * artwork and lyrics at the tail. FLAC deliberately does not use this path: its metadata chain
     * must remain contiguous. A server that does not support byte ranges returns null and callers
     * can retain the regular head-cache fallback.
     */
    suspend fun downloadMetadataWindowToFileCancellable(
        url: String,
        config: WebDavConfig,
        target: File,
        remoteSize: Long,
        windowBytes: Long = 512 * 1024L
    ): File? {
        val size = remoteSize.takeIf { it > 0L } ?: return null
        if (size > MAX_SPARSE_METADATA_FILE_SIZE) {
            // Do not retain a stale sparse file whose reported size could be mistaken for a
            // complete metadata cache after a server-side file change.
            target.delete()
            return null
        }
        val window = windowBytes.coerceIn(64 * 1024L, 2 * 1024 * 1024L)
        val completeBytes = if (size <= window * 2L) {
            downloadRangeBytesCancellable(url, config, 0L, size - 1L)
                ?: return null
        } else {
            val head = downloadRangeBytesCancellable(url, config, 0L, window - 1L)
                ?: return null
            val tailStart = size - window
            val tail = downloadRangeBytesCancellable(url, config, tailStart, size - 1L)
                ?: return null
            writeSparseMetadataWindow(target, size, head, tail)
            return target
        }

        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.window.part")
        return runCatching {
            temporary.outputStream().use { output -> output.write(completeBytes.bytes) }
            if (temporary.length() <= 0L) return@runCatching null
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
            target
        }.getOrElse { error ->
            temporary.delete()
            Log.w(TAG, "WebDAV metadata window write failed url=${url.safeLogUrl()}", error)
            null
        }
    }

    /**
     * Downloads only the contiguous FLAC metadata chain. FLAC stores STREAMINFO, Vorbis Comment
     * and attached pictures in consecutive metadata blocks at the beginning of the file; a sparse
     * head/tail cache is therefore not safe for tag readers because its zero-filled hole looks like
     * corrupt FLAC data. The prefix grows until the last metadata-block flag is available.
     */
    suspend fun downloadFlacMetadataPrefixToFileCancellable(
        url: String,
        config: WebDavConfig,
        target: File,
        remoteSize: Long,
        initialBytes: Long = FLAC_METADATA_INITIAL_BYTES,
        maxBytes: Long = FLAC_METADATA_MAX_BYTES
    ): File? {
        val size = remoteSize.takeIf { it > 0L } ?: return null
        val safeMaxBytes = maxBytes.coerceAtLeast(16 * 1024L)
        var requestBytes = initialBytes.coerceIn(16 * 1024L, safeMaxBytes)
        val rangeFile = File(target.parentFile, "${target.name}.flac.range.part")

        try {
            while (true) {
                val requestedEnd = minOf(size, requestBytes) - 1L
                if (!downloadRangeToFileCancellable(url, config, 0L, requestedEnd, rangeFile)) {
                    return null
                }
                if (!flacPrefixLooksValid(rangeFile)) return null

                val metadataEnd = flacMetadataEnd(rangeFile)
                if (metadataEnd != null) {
                    return writeFlacMetadataPrefix(target, size, rangeFile, metadataEnd)
                }
                if (requestBytes >= size || requestBytes >= safeMaxBytes) return null
                val nextRequestBytes = minOf(size, safeMaxBytes, requestBytes * 2L)
                if (nextRequestBytes <= requestBytes) return null
                requestBytes = nextRequestBytes
            }
        } finally {
            rangeFile.delete()
        }
    }

    /** Returns true only for a prefix written by the contiguous FLAC cache writer. */
    internal fun isFlacMetadataCacheUsable(target: File, remoteSize: Long = 0L): Boolean {
        if (!target.isFile || target.length() <= 0L) return false
        val marker = flacMetadataMarkerFile(target)
        if (!marker.isFile || marker.length() <= 0L) return false
        val markerValues = runCatching {
            marker.readText().trim().split(':').map(String::toLong)
        }.getOrNull() ?: return false
        val markerRemoteSize = markerValues.getOrNull(0) ?: return false
        val markerPrefixLength = markerValues.getOrNull(1) ?: return false
        if (remoteSize > 0L && markerRemoteSize != remoteSize) return false
        if (markerPrefixLength <= 0L || markerPrefixLength != target.length()) return false
        if (markerPrefixLength > FLAC_METADATA_MAX_BYTES || markerPrefixLength > Int.MAX_VALUE) return false

        return flacMetadataEnd(target) == markerPrefixLength
    }

    internal fun clearFlacMetadataCacheMarker(target: File) {
        flacMetadataMarkerFile(target).delete()
    }

    private data class RangeBytes(val start: Long, val bytes: ByteArray)

    /** Streams a byte range to disk without retaining the response in a ByteArray. */
    private suspend fun downloadRangeToFileCancellable(
        url: String,
        config: WebDavConfig,
        start: Long,
        end: Long,
        target: File
    ): Boolean = suspendCancellableCoroutine { continuation ->
        val requestUrl = normalizeRequestUrl(url)
        val expectedBytes = (end - start + 1L).coerceAtLeast(1L)
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.download.part")
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .tag(WebDavConfig::class.java, config)
            .header("Range", "bytes=$start-$end")
            .apply { applyPreemptiveBasicAuth(config) }
            .build()
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation {
            call.cancel()
            temporary.delete()
        }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                temporary.delete()
                if (!call.isCanceled()) {
                    Log.w(TAG, "WebDAV metadata range failed url=${requestUrl.safeLogUrl()}", e)
                }
                if (continuation.isActive) continuation.resume(false) { _, _, _ -> }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.code != 200 && it.code != 206) return@use false
                        val actualStart = if (it.code == 206) {
                            parseContentRangeStart(it.header("Content-Range")) ?: start
                        } else {
                            0L
                        }
                        if (actualStart != start) return@use false
                        val body = it.body ?: return@use false
                        val written = temporary.outputStream().use { output ->
                            body.byteStream().use { input -> input.copyToBounded(output, expectedBytes) }
                        }
                        if (written <= 0L || (it.code == 206 && written < expectedBytes)) {
                            temporary.delete()
                            return@use false
                        }
                        if (target.exists()) target.delete()
                        if (!temporary.renameTo(target)) {
                            temporary.copyTo(target, overwrite = true)
                            temporary.delete()
                        }
                        target.isFile && target.length() >= expectedBytes
                    }
                }.getOrElse { error ->
                    temporary.delete()
                    if (!call.isCanceled()) {
                        Log.w(TAG, "WebDAV metadata range failed url=${requestUrl.safeLogUrl()}", error)
                    }
                    false
                }
                if (continuation.isActive) continuation.resume(result) { _, _, _ -> }
            }
        })
    }

    private suspend fun downloadRangeBytesCancellable(
        url: String,
        config: WebDavConfig,
        start: Long,
        end: Long
    ): RangeBytes? = suspendCancellableCoroutine { continuation ->
        val requestUrl = normalizeRequestUrl(url)
        val expectedBytes = (end - start + 1L).coerceAtLeast(1L)
        val request = Request.Builder()
            .url(requestUrl)
            .get()
            .tag(WebDavConfig::class.java, config)
            .header("Range", "bytes=$start-$end")
            .apply { applyPreemptiveBasicAuth(config) }
            .build()
        val call = httpClient.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (!call.isCanceled()) {
                    Log.w(TAG, "WebDAV metadata range failed url=${requestUrl.safeLogUrl()}", e)
                }
                if (continuation.isActive) continuation.resume(null) { _, _, _ -> }
            }

            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.code != 200 && it.code != 206) return@use null
                        val actualStart = if (it.code == 206) {
                            parseContentRangeStart(it.header("Content-Range")) ?: start
                        } else {
                            0L
                        }
                        // A 200 response to a non-zero range is the beginning of the file, not
                        // the requested tail. Never place it at the tail offset.
                        if (actualStart != start) return@use null
                        val body = it.body ?: return@use null
                        val output = ByteArrayOutputStream(expectedBytes.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
                        body.byteStream().use { input -> input.copyToBounded(output, expectedBytes) }
                        output.toByteArray().takeIf { bytes ->
                            // A successful 206 must contain the complete requested range. A
                            // truncated response must never be written into the FLAC prefix or
                            // the M4A tail cache as if it were valid metadata.
                            it.code != 206 || bytes.size.toLong() >= expectedBytes
                        }?.takeIf { bytes -> bytes.isNotEmpty() }
                            ?.let { bytes -> RangeBytes(start = actualStart, bytes = bytes) }
                    }
                }.getOrElse { error ->
                    if (!call.isCanceled()) {
                        Log.w(TAG, "WebDAV metadata range failed url=${requestUrl.safeLogUrl()}", error)
                    }
                    null
                }
                if (continuation.isActive) continuation.resume(result) { _, _, _ -> }
            }
        })
    }

    private fun writeSparseMetadataWindow(
        target: File,
        remoteSize: Long,
        head: RangeBytes,
        tail: RangeBytes
    ) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "${target.name}.window.part")
        runCatching {
            RandomAccessFile(temporary, "rw").use { file ->
                file.setLength(remoteSize)
                file.seek(head.start)
                file.write(head.bytes)
                file.seek(tail.start)
                file.write(tail.bytes)
            }
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }
        }.onFailure { error ->
            temporary.delete()
            throw error
        }
    }

    private fun writeFlacMetadataPrefix(
        target: File,
        remoteSize: Long,
        bytes: ByteArray,
        metadataEnd: Int
    ): File? {
        if (metadataEnd <= 0 || metadataEnd > bytes.size) return null
        val temporary = File(target.parentFile, "${target.name}.flac.window.part")
        val marker = flacMetadataMarkerFile(target)
        val markerTemporary = File(target.parentFile, "${marker.name}.part")
        return runCatching {
            target.parentFile?.mkdirs()
            temporary.outputStream().use { output -> output.write(bytes, 0, metadataEnd) }
            if (temporary.length() != metadataEnd.toLong()) {
                temporary.delete()
                return@runCatching null
            }
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }

            markerTemporary.writeText("$remoteSize:$metadataEnd", Charsets.UTF_8)
            if (marker.exists()) marker.delete()
            if (!markerTemporary.renameTo(marker)) {
                markerTemporary.copyTo(marker, overwrite = true)
                markerTemporary.delete()
            }
            target
        }.getOrElse { error ->
            temporary.delete()
            markerTemporary.delete()
            marker.delete()
            target.delete()
            Log.w(TAG, "WebDAV FLAC metadata prefix write failed url=${target.name}", error)
            null
        }
    }

    private fun writeFlacMetadataPrefix(
        target: File,
        remoteSize: Long,
        source: File,
        metadataEnd: Long
    ): File? {
        if (metadataEnd <= 0L || metadataEnd > source.length()) return null
        val temporary = File(target.parentFile, "${target.name}.flac.window.part")
        val marker = flacMetadataMarkerFile(target)
        val markerTemporary = File(target.parentFile, "${marker.name}.part")
        return runCatching {
            target.parentFile?.mkdirs()
            source.inputStream().use { input ->
                temporary.outputStream().use { output ->
                    input.copyToBounded(output, metadataEnd)
                }
            }
            if (temporary.length() != metadataEnd) {
                temporary.delete()
                return@runCatching null
            }
            if (target.exists()) target.delete()
            if (!temporary.renameTo(target)) {
                temporary.copyTo(target, overwrite = true)
                temporary.delete()
            }

            markerTemporary.writeText("$remoteSize:$metadataEnd", Charsets.UTF_8)
            if (marker.exists()) marker.delete()
            if (!markerTemporary.renameTo(marker)) {
                markerTemporary.copyTo(marker, overwrite = true)
                markerTemporary.delete()
            }
            target
        }.getOrElse { error ->
            temporary.delete()
            markerTemporary.delete()
            marker.delete()
            target.delete()
            Log.w(TAG, "WebDAV FLAC metadata prefix write failed url=${target.name}", error)
            null
        }
    }

    private fun flacMetadataMarkerFile(target: File): File =
        File(target.parentFile, "${target.name}$FLAC_METADATA_MARKER_SUFFIX")

    private fun parseContentRangeStart(value: String?): Long? {
        val match = Regex("bytes\\s+(\\d+)-\\d+/.*", RegexOption.IGNORE_CASE).matchEntire(value.orEmpty().trim())
        return match?.groupValues?.getOrNull(1)?.toLongOrNull()
    }

    private fun parseItems(
        input: InputStream,
        baseUrl: String,
        maxItems: Int = MAX_PROPFIND_ITEMS
    ): List<WebDavItem> {
        return runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            // Keep the PROPFIND response as a stream. Converting a large directory listing to a
            // String first temporarily keeps both the response bytes and a UTF-16 copy alive.
            parser.setInput(input, null)
            val result = ArrayList<WebDavItem>(minOf(maxItems.coerceAtLeast(0), 1024))
            var current: WebDavItemBuilder? = null
            var textTag: String? = null
            val text = StringBuilder()

            while (true) {
                when (parser.eventType) {
                    XmlPullParser.START_TAG -> {
                        val tag = parser.name.orEmpty().substringAfterLast(':').lowercase(Locale.ROOT)
                        if (tag == "response") {
                            current = WebDavItemBuilder()
                        } else if (current != null) {
                            when (tag) {
                                "collection" -> current.isDirectory = true
                                "href", "getcontentlength", "getcontenttype" -> {
                                    textTag = tag
                                    text.setLength(0)
                                }
                            }
                        }
                    }
                    XmlPullParser.TEXT, XmlPullParser.CDSECT -> {
                        if (textTag != null) text.append(parser.text.orEmpty())
                    }
                    XmlPullParser.END_TAG -> {
                        val tag = parser.name.orEmpty().substringAfterLast(':').lowercase(Locale.ROOT)
                        if (tag == textTag) {
                            current?.setText(tag, text.toString())
                            textTag = null
                            text.setLength(0)
                        }
                        if (tag == "response") {
                            current?.toItem(baseUrl)?.let {
                                if (result.size < maxItems) result += it
                            }
                            current = null
                            textTag = null
                            text.setLength(0)
                        }
                        if (result.size >= maxItems) break
                    }
                }
                if (parser.eventType == XmlPullParser.END_DOCUMENT) break
                parser.next()
            }
            result
        }.getOrElse { error ->
            Log.e(TAG, "WebDAV XML parse failed: ${baseUrl.safeLogUrl()}", error)
            AppLogStore.error(
                requireContext(),
                TAG,
                "WebDAV XML parse failed: ${baseUrl.safeLogUrl()}",
                error,
                AppLogType.NETWORK
            )
            emptyList()
        }
    }

    private class WebDavItemBuilder {
        var href: String = ""
        var size: Long = 0L
        var mimeType: String = ""
        var isDirectory: Boolean = false

        fun setText(tag: String, value: String) {
            when (tag) {
                "href" -> href = value.trim()
                "getcontentlength" -> size = value.trim().toLongOrNull() ?: 0L
                "getcontenttype" -> mimeType = value.substringBefore(';').trim().lowercase(Locale.ROOT)
            }
        }

        fun toItem(baseUrl: String): WebDavItem? {
            if (href.isBlank()) return null
            return runCatching {
                val itemUrl = resolveHref(baseUrl, href)
                val fallbackName = itemUrl.trimEnd('/').substringAfterLast('/').decodeUrlPart()
                val pathName = runCatching { URI(itemUrl).path.orEmpty().substringAfterLast('/').decodeUrlPart() }
                    .getOrDefault(fallbackName)
                val displayName = pathName.ifBlank { fallbackName }.ifBlank { itemUrl }
                WebDavItem(
                    name = Html.fromHtml(displayName, Html.FROM_HTML_MODE_LEGACY)?.toString() ?: displayName,
                    url = itemUrl,
                    isDirectory = isDirectory || itemUrl.endsWith('/'),
                    size = size,
                    mimeType = mimeType
                )
            }.getOrNull()
        }
    }

    private fun executePropfind(url: String, config: WebDavConfig, depth: String): WebDavResponse {
        return executePropfind(url, config, depth, useXmlBody = true).let { response ->
            if (response.code == 400) {
                Log.w(TAG, "WebDAV PROPFIND got 400, retrying with empty body: ${normalizeRequestUrl(url).safeLogUrl()}")
                executePropfind(url, config, depth, useXmlBody = false)
            } else {
                response
            }
        }
    }

    /**
     * Parses a successful PROPFIND response directly from OkHttp's stream. A flat WebDAV folder
     * with several thousand songs can otherwise create a second, large UTF-16 response copy and
     * trigger memory pressure before the list reaches the UI.
     */
    private fun executePropfindItems(url: String, config: WebDavConfig, depth: String): ParsedPropfind {
        return executePropfindItems(url, config, depth, useXmlBody = true).let { response ->
            if (response.code == 400) {
                Log.w(TAG, "WebDAV PROPFIND got 400, retrying with empty body: ${normalizeRequestUrl(url).safeLogUrl()}")
                executePropfindItems(url, config, depth, useXmlBody = false)
            } else {
                response
            }
        }
    }

    private fun executePropfindItems(
        url: String,
        config: WebDavConfig,
        depth: String,
        useXmlBody: Boolean
    ): ParsedPropfind {
        val requestUrl = normalizeRequestUrl(url)
        Log.i(TAG, "WebDAV PROPFIND depth=$depth body=${if (useXmlBody) "xml" else "empty"} url=${requestUrl.safeLogUrl()}")
        val body = (if (useXmlBody) BASIC_PROPFIND else "").toRequestBody(xmlMediaType)
        val request = Request.Builder()
            .url(requestUrl)
            .method("PROPFIND", body)
            .tag(WebDavConfig::class.java, config)
            .header("Depth", depth)
            .header("Accept", "application/xml, text/xml, */*")
            .header("Content-Type", "application/xml; charset=utf-8")
            .apply { applyPreemptiveBasicAuth(config) }
            .build()

        return httpClient.newCall(request).execute().use { response ->
            Log.i(TAG, "WebDAV PROPFIND response depth=$depth url=${requestUrl.safeLogUrl()} code=${response.code}")
            val responseBody = response.body
            if (response.code in 200..399) {
                val items = responseBody?.byteStream()?.use { input -> parseItems(input, requestUrl) }.orEmpty()
                ParsedPropfind(code = response.code, items = items)
            } else {
                ParsedPropfind(
                    code = response.code,
                    body = responseBody?.byteStream()?.use { input ->
                        val bytes = ByteArrayOutputStream(MAX_PROPFIND_ERROR_BODY_CHARS)
                        input.copyToBounded(bytes, MAX_PROPFIND_ERROR_BODY_CHARS.toLong())
                        String(bytes.toByteArray(), Charsets.UTF_8)
                    }.orEmpty()
                )
            }
        }
    }

    private fun executePropfind(url: String, config: WebDavConfig, depth: String, useXmlBody: Boolean): WebDavResponse {
        val requestUrl = normalizeRequestUrl(url)
        Log.i(TAG, "WebDAV PROPFIND depth=$depth body=${if (useXmlBody) "xml" else "empty"} url=${requestUrl.safeLogUrl()}")
        val body = (if (useXmlBody) BASIC_PROPFIND else "").toRequestBody(xmlMediaType)
        val request = Request.Builder()
            .url(requestUrl)
            .method("PROPFIND", body)
            .tag(WebDavConfig::class.java, config)
            .header("Depth", depth)
            .header("Accept", "application/xml, text/xml, */*")
            .header("Content-Type", "application/xml; charset=utf-8")
            .apply { applyPreemptiveBasicAuth(config) }
            .build()

        return httpClient.newCall(request).execute().use { response ->
            Log.i(TAG, "WebDAV PROPFIND response depth=$depth url=${requestUrl.safeLogUrl()} code=${response.code}")
            WebDavResponse(
                code = response.code,
                body = response.body?.string().orEmpty()
            )
        }
    }

    private data class ParsedPropfind(
        val code: Int,
        val items: List<WebDavItem> = emptyList(),
        val body: String = ""
    )

    private data class WebDavResponse(val code: Int, val body: String)

    private fun Request.Builder.applyPreemptiveBasicAuth(config: WebDavConfig) {
        if ((config.username.isNotBlank() || config.password.isNotBlank()) && config.authMode != WebDavAuthMode.DIGEST) {
            header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
        }
    }

    private fun authenticate(response: Response, config: WebDavConfig): Request? {
        if (config.username.isBlank()) return null
        if (responseCount(response) >= 3) {
            Log.w(TAG, "WebDAV auth retry limit reached: ${response.request.url.toString().safeLogUrl()}")
            return null
        }
        val challenges = parseAuthChallenges(response.headers("WWW-Authenticate"))
        val existingAuth = response.request.header("Authorization")
        val digestHeader = challenges["Digest"]
        val basicHeader = challenges["Basic"]
        return when (config.authMode) {
            WebDavAuthMode.DIGEST -> digestHeader?.let { response.digestRequest(config, it) }
            WebDavAuthMode.BASIC -> basicHeader?.let { response.basicRequest(config) }
            WebDavAuthMode.AUTO -> {
                digestHeader?.let { response.digestRequest(config, it) }
                    ?: if (existingAuth?.startsWith("Basic", ignoreCase = true) != true) {
                        basicHeader?.let { response.basicRequest(config) }
                    } else {
                        null
                    }
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }

    private fun parseAuthChallenges(headers: List<String>): Map<String, String> {
        val result = linkedMapOf<String, String>()
        headers.forEach { header ->
            val value = header.trim()
            when {
                value.startsWith("Digest", ignoreCase = true) -> result["Digest"] = value
                value.startsWith("Basic", ignoreCase = true) -> result["Basic"] = value
                value.contains("Digest", ignoreCase = true) -> {
                    value.substring(value.indexOf("Digest", ignoreCase = true)).let { result["Digest"] = it }
                }
                value.contains("Basic", ignoreCase = true) -> {
                    value.substring(value.indexOf("Basic", ignoreCase = true)).let { result["Basic"] = it }
                }
            }
        }
        return result
    }

    private fun Response.basicRequest(config: WebDavConfig): Request {
        Log.i(TAG, "WebDAV using Basic auth: ${request.url.toString().safeLogUrl()}")
        return request.newBuilder()
            .header("Authorization", Credentials.basic(config.username, config.password, Charsets.UTF_8))
            .build()
    }

    private fun Response.digestRequest(config: WebDavConfig, authHeader: String): Request? {
        return runCatching {
            val realm = authHeader.authParam("realm") ?: return null
            val nonce = authHeader.authParam("nonce") ?: return null
            val opaque = authHeader.authParam("opaque")
            val algorithm = authHeader.authParam("algorithm") ?: "MD5"
            val qop = authHeader.authParam("qop")
                ?.split(',')
                ?.map { it.trim().trim('"') }
                ?.firstOrNull { it.equals("auth", ignoreCase = true) }
            val url = request.url
            val digestUri = url.encodedPath + url.encodedQuery?.let { "?$it" }.orEmpty()
            val method = request.method
            val cnonce = generateCnonce()
            val nc = "00000001"
            val hash = algorithm.substringBefore("-").uppercase(Locale.ROOT)
            val ha1Base = digestHash(hash, "${config.username}:$realm:${config.password}")
            val ha1 = if (algorithm.endsWith("-sess", ignoreCase = true)) {
                digestHash(hash, "$ha1Base:$nonce:$cnonce")
            } else {
                ha1Base
            }
            val ha2 = digestHash(hash, "$method:$digestUri")
            val digestResponse = if (qop != null) {
                digestHash(hash, "$ha1:$nonce:$nc:$cnonce:$qop:$ha2")
            } else {
                digestHash(hash, "$ha1:$nonce:$ha2")
            }
            val authValue = buildString {
                append("Digest ")
                append("""username="${config.username}", """)
                append("""realm="$realm", """)
                append("""nonce="$nonce", """)
                append("""uri="$digestUri", """)
                append("""response="$digestResponse"""")
                append(""", algorithm=$algorithm""")
                if (opaque != null) append(""", opaque="$opaque"""")
                if (qop != null) {
                    append(""", qop=$qop""")
                    append(""", nc=$nc""")
                    append(""", cnonce="$cnonce"""")
                }
            }
            Log.i(TAG, "WebDAV using Digest auth: ${request.url.toString().safeLogUrl()} algorithm=$algorithm")
            request.newBuilder()
                .header("Authorization", authValue)
                .build()
        }.getOrElse { error ->
            Log.w(TAG, "WebDAV Digest auth failed", error)
            null
        }
    }

    private fun String.authParam(name: String): String? {
        val quoted = Regex("""(?i)(?:^|,\s*)${Regex.escape(name)}\s*=\s*"([^"]*)"""").find(this)
        if (quoted != null) return quoted.groupValues[1]
        return Regex("""(?i)(?:^|,\s*)${Regex.escape(name)}\s*=\s*([^,\s]+)""")
            .find(this)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim('"')
    }

    private fun digestHash(algorithm: String, input: String): String {
        val digestAlgorithm = when (algorithm.uppercase(Locale.ROOT)) {
            "SHA-256" -> "SHA-256"
            else -> "MD5"
        }
        return MessageDigest.getInstance(digestAlgorithm)
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun generateCnonce(): String {
        val bytes = ByteArray(8)
        secureRandom.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun WebDavResponse.toFriendlyMessage(ctx: Context): String {
        return when (code) {
            400 -> ctx.getString(R.string.webdav_http_400)
            401 -> ctx.getString(R.string.webdav_http_401)
            403 -> ctx.getString(R.string.webdav_http_403)
            404 -> ctx.getString(R.string.webdav_http_404)
            405 -> ctx.getString(R.string.webdav_http_405)
            in 300..399 -> ctx.getString(R.string.webdav_http_redirect, code)
            else -> {
                val detail = body.lineSequence()
                    .map { it.trim() }
                    .firstOrNull { it.isNotBlank() }
                    ?.take(120)
                    .orEmpty()
                if (detail.isBlank()) ctx.getString(R.string.webdav_http_server_error, code)
                else ctx.getString(R.string.webdav_http_server_error_detail, code, detail)
            }
        }
    }

    private fun Throwable.toFriendlyMessage(ctx: Context): String {
        val rawMessage = localizedMessage.orEmpty()
        return when (this) {
            is IllegalArgumentException -> rawMessage.ifBlank { ctx.getString(R.string.webdav_address_format_invalid) }
            is UnknownHostException -> ctx.getString(R.string.webdav_host_unresolvable)
            is SocketTimeoutException -> ctx.getString(R.string.webdav_connection_timeout)
            is SSLHandshakeException -> ctx.getString(R.string.webdav_tls_handshake_failed)
            is WebDavException -> rawMessage.ifBlank { ctx.getString(R.string.webdav_load_failed) }
            is IOException -> {
                if (rawMessage.contains("CLEARTEXT", ignoreCase = true)) {
                    ctx.getString(R.string.webdav_cleartext_blocked)
                } else {
                    rawMessage.ifBlank { ctx.getString(R.string.webdav_network_failed) }
                }
            }
            else -> rawMessage.ifBlank { ctx.getString(R.string.webdav_connection_failed) }
        }
    }

    private fun resolveHref(baseUrl: String, href: String): String {
        return runCatching {
            normalizeRequestUrl(URI(baseUrl).resolve(href).toString())
        }.getOrElse { href }
    }

    private fun normalizeCollectionUrl(url: String): String {
        val trimmed = normalizeRequestUrl(url)
        return if (trimmed.endsWith("/")) trimmed else "$trimmed/"
    }

    private fun normalizeRequestUrl(url: String): String {
        val trimmed = url.trim()
        require(trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            "WebDAV URL must start with http:// or https://"
        }
        trimmed.toHttpUrlOrNull()?.let { return it.toString() }
        return runCatching {
            val uri = URI(trimmed.replace(" ", "%20"))
            val normalized = URI(
                uri.scheme,
                uri.userInfo,
                uri.host,
                uri.port,
                uri.path.orEmpty(),
                uri.query,
                uri.fragment
            ).toASCIIString()
            normalized.toHttpUrlOrNull()?.toString() ?: normalized
        }.getOrDefault(trimmed)
    }

    fun displayUrl(url: String): String {
        return runCatching {
            val uri = URI(url)
            val decodedPath = uri.rawPath.orEmpty().decodeUrlPart()
            buildString {
                append(uri.scheme).append("://").append(uri.host ?: "")
                if (uri.port >= 0) append(":").append(uri.port)
                append(decodedPath)
                if (!uri.rawQuery.isNullOrBlank()) append("?").append(uri.rawQuery.decodeUrlPart())
                if (!uri.rawFragment.isNullOrBlank()) append("#").append(uri.rawFragment.decodeUrlPart())
            }
        }.getOrDefault(url.decodeUrlPart())
    }

    private fun String.decodeUrlPart(): String {
        return runCatching { URLDecoder.decode(this, "UTF-8") }.getOrDefault(this)
    }

    private fun String.safeLogUrl(): String {
        return runCatching {
            val uri = URI(this)
            if (uri.userInfo == null) {
                this
            } else {
                URI(uri.scheme, "***", uri.host, uri.port, uri.path, uri.query, uri.fragment).toString()
            }
        }.getOrDefault(this)
    }

    private fun java.io.InputStream.copyToBounded(
        output: java.io.OutputStream,
        maxBytes: Long
    ): Long {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (total < maxBytes) {
            val allowed = minOf(buffer.size.toLong(), maxBytes - total).toInt()
            val read = read(buffer, 0, allowed)
            if (read <= 0) break
            output.write(buffer, 0, read)
            total += read
        }
        return total
    }

    private val BASIC_PROPFIND = """
        <?xml version="1.0" encoding="utf-8" ?>
        <D:propfind xmlns:D="DAV:">
          <D:prop>
            <D:resourcetype/>
            <D:getcontentlength/>
            <D:getcontenttype/>
          </D:prop>
        </D:propfind>
    """.trimIndent().trim()
}
