package com.ella.music.data.musicfree

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID

/** Keeps plugin credentials bound to one resolved stream without altering Song or other sources. */
internal class MusicFreeStreamHeaders internal constructor(private val storageFile: File? = null) {
    private data class StreamScope(val url: HttpUrl, val headers: Map<String, String>)
    internal data class ResolvedStream(val url: String, val client: OkHttpClient)

    private val streams = LinkedHashMap<String, StreamScope>()

    init {
        restore()
    }

    @Synchronized
    fun register(url: String, result: JSONObject): String {
        val original = url.toHttpUrlOrNull() ?: throw IOException("插件返回的播放地址无效")
        val headers = normalizedHeaders(result)
        if (headers.isEmpty()) return url
        if (original.username.isNotEmpty() || original.password.isNotEmpty()) {
            throw IOException("插件播放地址不能包含用户名或密码")
        }
        val marked = original.newBuilder().fragment(MARKER + UUID.randomUUID()).build().toString()
        streams[marked] = StreamScope(original, headers)
        while (streams.size > MAX_STREAMS) streams.remove(streams.keys.first())
        persist()
        return marked
    }

    val requestInterceptor = Interceptor { chain ->
        chain.proceed(scopeRequest(chain.request()))
    }

    val originInterceptor = Interceptor { chain ->
        chain.proceed(requestForHop(chain.request()))
    }

    fun isMarked(url: String): Boolean = url.toHttpUrlOrNull()?.fragment?.startsWith(MARKER) == true

    /** Every request made by this client belongs to the same stream, including HLS children. */
    fun resolveStream(markedUrl: String, baseClient: OkHttpClient): ResolvedStream? {
        if (!isMarked(markedUrl)) return null
        val normalizedUrl = markedUrl.toHttpUrlOrNull()?.toString()
        val scope = synchronized(this) { streams[normalizedUrl] }
            ?: throw IOException("音源请求信息已过期，请从在线音乐页重新播放")
        val client = baseClient.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().tag(StreamScope::class.java, scope).build())
            }
            .addNetworkInterceptor(originInterceptor)
            .build()
        return ResolvedStream(scope.url.toString(), client)
    }

    internal fun scopeRequest(request: Request): Request {
        if (request.url.fragment?.startsWith(MARKER) != true) return request
        val scope = synchronized(this) { streams[request.url.toString()] }
            ?: throw IOException("音源请求信息已过期，请从在线音乐页重新播放")
        return request.newBuilder().url(scope.url).tag(StreamScope::class.java, scope).build()
    }

    internal fun requestForHop(request: Request): Request {
        val scope = request.tag(StreamScope::class.java) ?: return request
        if (scope.url.isHttps && !request.url.isHttps) {
            throw IOException("音源不能从 HTTPS 重定向到 HTTP")
        }
        val sameOrigin = request.url.scheme == scope.url.scheme &&
            request.url.host == scope.url.host && request.url.port == scope.url.port
        return request.newBuilder().apply {
            scope.headers.forEach { (name, value) ->
                if (sameOrigin) header(name, value) else removeHeader(name)
            }
        }.build()
    }

    private fun normalizedHeaders(result: JSONObject): Map<String, String> {
        val normalized = Headers.Builder()
        result.optJSONObject("headers")?.let { headers ->
            headers.keys().forEach { name ->
                if (TRANSPORT_HEADERS.none { it.equals(name, ignoreCase = true) }) {
                    val value = headers.opt(name)
                    if (value != null && value != JSONObject.NULL) {
                        require(value is String) { "插件请求头必须是字符串" }
                        require(value.length <= MAX_HEADER_LENGTH) { "插件请求头过长" }
                        normalized.set(name, value)
                    }
                }
            }
        }
        val userAgent = result.opt("userAgent") as? String
        if (!userAgent.isNullOrBlank() && normalized["User-Agent"] == null) {
            require(userAgent.length <= MAX_HEADER_LENGTH) { "插件 User-Agent 过长" }
            normalized.set("User-Agent", userAgent)
        }
        val built = normalized.build()
        require(built.size <= MAX_HEADERS) { "插件请求头过多" }
        return built.names().associateWith { built[it].orEmpty() }
    }

    private fun restore() {
        val file = storageFile ?: return
        try {
            if (!file.exists() && !File(file.path + ".bak").exists()) return
            val bytes = AtomicFile(file).openRead().use { input ->
                if (input.channel.size() > MAX_STORED_BYTES) return
                input.readBytes()
            }
            if (bytes.size > MAX_STORED_BYTES) return
            val saved = JSONArray(bytes.toString(Charsets.UTF_8))
            for (index in 0 until saved.length().coerceAtMost(MAX_STREAMS)) {
                val row = saved.optJSONObject(index) ?: continue
                val marked = row.optString("marked").toHttpUrlOrNull() ?: continue
                val original = row.optString("url").toHttpUrlOrNull() ?: continue
                if (marked.fragment?.startsWith(MARKER) != true ||
                    original.username.isNotEmpty() || original.password.isNotEmpty()
                ) continue
                val headers = normalizedHeaders(row)
                if (headers.isNotEmpty()) streams[marked.toString()] = StreamScope(original, headers)
            }
        } catch (_: Exception) {
            // Expired/invalid saved credentials never become global request defaults.
            Log.w(TAG, "Could not restore MusicFree stream request settings")
        }
    }

    private fun persist() {
        val file = storageFile ?: return
        try {
            fun encoded(): ByteArray = JSONArray(streams.map { (marked, scope) ->
                JSONObject().put("marked", marked).put("url", scope.url.toString())
                    .put("headers", JSONObject(scope.headers))
            }).toString().toByteArray(Charsets.UTF_8)
            var bytes = encoded()
            while (bytes.size > MAX_STORED_BYTES && streams.size > 1) {
                streams.remove(streams.keys.first())
                bytes = encoded()
            }
            val atomic = AtomicFile(file)
            val output = atomic.startWrite()
            try {
                output.write(bytes)
                atomic.finishWrite(output)
            } catch (error: Exception) {
                atomic.failWrite(output)
                throw error
            }
        } catch (_: Exception) {
            Log.w(TAG, "Could not persist MusicFree stream request settings")
        }
    }

    companion object {
        private const val TAG = "MusicFreeStreamHeaders"
        private const val MARKER = "halcyon-mf-headers="
        private const val MAX_STREAMS = 2_048
        private const val MAX_HEADERS = 32
        private const val MAX_HEADER_LENGTH = 8_192
        private const val MAX_STORED_BYTES = 1_048_576
        private val TRANSPORT_HEADERS = setOf(
            "Host", "Connection", "Keep-Alive", "Proxy-Authorization", "Proxy-Connection", "TE", "Trailer",
            "Transfer-Encoding", "Upgrade", "Content-Length", "Range", "Accept-Encoding"
        )

        @Volatile private var instance: MusicFreeStreamHeaders? = null

        fun getInstance(context: Context): MusicFreeStreamHeaders = instance ?: synchronized(this) {
            instance ?: MusicFreeStreamHeaders(
                File(context.applicationContext.noBackupFilesDir, "musicfree_stream_headers.json")
            ).also { instance = it }
        }
    }
}
