package com.ella.music.data.webdav

import android.content.Context
import android.text.Html
import android.util.Log
import android.util.Xml
import com.ella.music.R
import com.ella.music.data.AppLogStore
import com.ella.music.data.AppLogType
import com.ella.music.data.AppNetworkLoggingInterceptor
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.File
import java.io.IOException
import java.io.StringReader
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
    private val audioExtensions = setOf("mp3", "m4a", "flac", "wav", "ogg", "opus", "aac", "alac")

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
        maxDepth: Int = 6,
        maxItems: Int = 1_500
    ): List<WebDavItem> {
        val result = ArrayList<WebDavItem>()
        fun walk(dirUrl: String, depth: Int) {
            if (depth > maxDepth || result.size >= maxItems) return
            val children = runCatching { list(config, dirUrl) }.getOrDefault(emptyList())
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
        val propfind = executePropfind(requestUrl, config, depth = "1")
        val response = propfind.body
        if (propfind.code !in 200..399) {
            val message = propfind.toFriendlyMessage(ctx)
            Log.w(TAG, "WebDAV list failed: ${requestUrl.safeLogUrl()} code=${propfind.code} message=$message")
            throw WebDavException(message)
        }
        if (response.isBlank()) return emptyList()

        val result = parseItems(response, requestUrl)
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
        val merged = ArrayList<WebDavItem>(items.size)
        items.chunked(safeBatchSize).forEach { batch ->
            merged += batch
            onBatch(merged.toList())
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

    private fun parseItems(xml: String, baseUrl: String): List<WebDavItem> {
        return runCatching {
            val parser = Xml.newPullParser()
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
            parser.setInput(StringReader(xml))
            val result = mutableListOf<WebDavItem>()
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
                            current?.toItem(baseUrl)?.let(result::add)
                            current = null
                            textTag = null
                            text.setLength(0)
                        }
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
