package com.ella.music.data.spotify

import android.content.Context
import android.util.AtomicFile
import com.ella.music.data.model.Song
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject

internal enum class SpotifyCanvasConnection { NotConfigured, Connecting, Connected, Failed }
internal data class SpotifyCanvasClip(val file: File, val trackUri: String)

/** The listener's credential goes only to Spotify's own web player. MP4 downloads carry no auth. */
internal class SpotifyCanvasRepository(
    private val context: Context,
    private val client: OkHttpClient = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).followRedirects(false).build(),
    private val sessionProvider: suspend (String, Boolean) -> SpotifyWebSession? = SpotifyWebSessionProvider(context)::session,
    private val now: () -> Long = System::currentTimeMillis
) {
    private val cacheDir = File(context.cacheDir, "spotify_canvas")
    private val locks = ConcurrentHashMap<String, Mutex>()
    private val failedLookups = ConcurrentHashMap<String, Long>()
    private val hashMutex = Mutex()
    private val cacheMutex = Mutex()
    private var hashesCheckedAt = 0L
    private val hashes = mutableMapOf(
        "canvas" to "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f",
        "searchTracks" to "b02683192a98dde7966b5e6655a79eeb62713eab703eda9902c932818dd52751"
    )
    private var lastAuthFailureCookie = ""
    private var lastAuthFailureAt = 0L
    private val mutableConnection = MutableStateFlow(SpotifyCanvasConnection.NotConfigured)
    val connection = mutableConnection.asStateFlow()
    fun credentialChanged() { mutableConnection.value = SpotifyCanvasConnection.NotConfigured }

    suspend fun connect(cookie: String): Boolean = withContext(Dispatchers.IO) {
        failedLookups.clear()
        val session = obtainSession(cookie, true)
        session != null
    }

    private suspend fun obtainSession(cookie: String, refresh: Boolean): SpotifyWebSession? {
        if (cookie.isBlank()) { mutableConnection.value = SpotifyCanvasConnection.NotConfigured; return null }
        if (!refresh && lastAuthFailureCookie == cookie && now() - lastAuthFailureAt < 5 * 60_000) return null
        mutableConnection.value = SpotifyCanvasConnection.Connecting
        return try {
            sessionProvider(cookie, refresh).also {
                mutableConnection.value = if (it == null) SpotifyCanvasConnection.Failed else SpotifyCanvasConnection.Connected
                if (it == null) { lastAuthFailureCookie = cookie; lastAuthFailureAt = now() } else lastAuthFailureCookie = ""
            }
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            lastAuthFailureCookie = cookie; lastAuthFailureAt = now()
            mutableConnection.value = SpotifyCanvasConnection.Failed
            null
        }
    }

    suspend fun resolve(song: Song, cookie: String): SpotifyCanvasClip? = withContext(Dispatchers.IO) {
        if (cookie.isBlank() || song.title.isBlank() || song.artist.isBlank()) return@withContext null
        val key = digest(listOf(song.title, song.artist, song.album, song.duration.toString()).joinToString("|"))
        val failureKey = key + digest(cookie)
        locks.getOrPut(key) { Mutex() }.withLock {
            cached(key)?.let { return@withLock it }
            failedLookups[failureKey]?.let { failedAt ->
                if (now() - failedAt < 15 * 60_000) return@withLock null
            }
            try {
                var session = obtainSession(cookie, false) ?: return@withLock null
                var hit = findTrack(song, session)
                if (hit == null && mutableConnection.value == SpotifyCanvasConnection.Failed) {
                    session = obtainSession(cookie, true) ?: return@withLock null
                    hit = findTrack(song, session)
                }
                val track = hit ?: return@withLock miss(failureKey)
                val url = findCanvas(track.uri, session) ?: return@withLock miss(failureKey)
                val clip = download(key, track.uri, url) ?: return@withLock miss(failureKey)
                failedLookups.remove(failureKey)
                clip
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                miss(failureKey)
            }
        }
    }

    private fun miss(key: String): SpotifyCanvasClip? {
        if (failedLookups.size >= 512) failedLookups.clear()
        failedLookups[key] = now()
        return null
    }

    private fun cached(key: String): SpotifyCanvasClip? = runCatching {
        val file = File(cacheDir, "$key.mp4")
        if (!file.isFile || file.length() <= 0 || now() - file.lastModified() > 7 * 24 * 60 * 60_000L) return null
        val metadata = JSONObject(File(cacheDir, "$key.json").readText(Charsets.UTF_8))
        file.setLastModified(now())
        SpotifyCanvasClip(file, metadata.getString("trackUri"))
    }.getOrNull()

    private suspend fun findTrack(song: Song, session: SpotifyWebSession): SpotifyCanvasTrack? {
        val variables = JSONObject().put("searchTerm", listOf(song.title, song.artist, song.album).filter(String::isNotBlank).joinToString(" "))
            .put("offset", 0).put("limit", 10).put("numberOfTopResults", 5)
            .put("includeAudiobooks", false).put("includePreReleases", false)
        val query = "https://api-partner.spotify.com/pathfinder/v1/query".toHttpUrl().newBuilder()
            .addQueryParameter("operationName", "searchTracks").addQueryParameter("variables", variables.toString())
            .addQueryParameter("extensions", JSONObject(spotifyQueryBody("searchTracks", variables, queryHash("searchTracks"))).getJSONObject("extensions").toString()).build()
        api(Request.Builder().url(query).get(), session)?.let { selectSpotifyCanvasTrack(it, song, true) }?.let { return it }
        val rest = "https://api.spotify.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("type", "track").addQueryParameter("limit", "10")
            .addQueryParameter("q", "track:${song.title} artist:${song.artist}").build()
        return api(Request.Builder().url(rest).get(), session)?.let { selectSpotifyCanvasTrack(it, song, false) }
    }

    private suspend fun findCanvas(uri: String, session: SpotifyWebSession): String? {
        var hash = queryHash("canvas")
        for (attempt in 0..1) {
            val body = spotifyQueryBody("canvas", JSONObject().put("trackUri", uri), hash)
            val raw = api(Request.Builder().url("https://api-partner.spotify.com/pathfinder/v2/query")
                .post(body.toByteArray().toRequestBody("application/json".toMediaType())), session)
            raw?.let(::parseSpotifyCanvasQuery)?.let { return it }
            if (attempt == 0 && raw?.contains("PersistedQueryNotFound", true) == true) hash = queryHash("canvas", true)
            else break
        }
        val response = bytes(Request.Builder().url("https://spclient.wg.spotify.com/canvaz-cache/v0/canvases")
            .post(encodeSpotifyCanvasRequest(uri).toRequestBody("application/protobuf".toMediaType()))
            .auth(session).header("User-Agent", "Spotify/9.0.34.593 iOS/18.4 (iPhone15,3)")
            .header("Accept", "application/protobuf").build(), 2 * 1024 * 1024)
        return if (response.code in 200..299) decodeSpotifyCanvasResponse(response.body, uri) else null
    }

    private suspend fun queryHash(operation: String, refresh: Boolean = false): String = hashMutex.withLock {
        if (!refresh && hashesCheckedAt != 0L && now() - hashesCheckedAt < 12 * 60 * 60_000) return@withLock hashes.getValue(operation)
        hashesCheckedAt = now()
        try {
            val html = bytes(Request.Builder().url("https://open.spotify.com/").header("User-Agent", SPOTIFY_WEB_USER_AGENT).build(), 4 * 1024 * 1024)
            if (html.code == 200) {
                val found = mutableMapOf<String, String>()
                for (url in spotifyWebPlayerScriptUrls(html.body.toString(Charsets.UTF_8)).take(8)) {
                    val script = bytes(Request.Builder().url(url).build(), 8 * 1024 * 1024)
                    if (script.code == 200) found.putAll(spotifyPersistedQueryHashes(script.body.toString(Charsets.UTF_8)))
                    if (found.keys.containsAll(hashes.keys)) break
                }
                hashes.putAll(found)
            }
        } catch (error: Exception) { if (error is CancellationException) throw error }
        hashes.getValue(operation)
    }

    private suspend fun api(builder: Request.Builder, session: SpotifyWebSession): String? {
        val response = bytes(builder.auth(session).header("Accept", "application/json")
            .header("App-platform", "WebPlayer").build(), 4 * 1024 * 1024)
        if (response.code == 401 || response.code == 403) mutableConnection.value = SpotifyCanvasConnection.Failed
        return response.body.toString(Charsets.UTF_8).takeIf { response.code in 200..299 }
    }
    private fun Request.Builder.auth(session: SpotifyWebSession): Request.Builder =
        header("Authorization", "Bearer ${session.accessToken}").header("User-Agent", SPOTIFY_WEB_USER_AGENT).apply {
            session.clientToken?.let { header("Client-Token", it) }
        }

    private suspend fun download(key: String, uri: String, url: String): SpotifyCanvasClip? = cacheMutex.withLock {
        if (!cacheDir.isDirectory && !cacheDir.mkdirs()) return@withLock null
        // Canvas has no audio role and is a short plain MP4. Cache it once, then loop the file.
        var currentUrl = url
        var response = bytes(Request.Builder().url(currentUrl).build(), 20 * 1024 * 1024)
        var redirects = 0
        while (response.code in setOf(301, 302, 303, 307, 308) && redirects++ < 3) {
            currentUrl = safeSpotifyCanvasVideoUrl(response.location?.let { currentUrl.toHttpUrl().resolve(it)?.toString() }) ?: return@withLock null
            response = bytes(Request.Builder().url(currentUrl).build(), 20 * 1024 * 1024)
        }
        if (response.code !in 200..299 || response.body.size < 12 || response.body.copyOfRange(4, 8).toString(Charsets.US_ASCII) != "ftyp") return@withLock null
        val file = AtomicFile(File(cacheDir, "$key.mp4"))
        val output = file.startWrite()
        try { output.write(response.body); file.finishWrite(output) }
        catch (error: Throwable) { file.failWrite(output); throw error }
        File(cacheDir, "$key.json").writeText(JSONObject().put("trackUri", uri).toString(), Charsets.UTF_8)
        // Evict oldest clips first and keep the current lookup intact.
        val files = cacheDir.listFiles().orEmpty().filter { it.extension == "mp4" }.sortedBy { it.lastModified() }
        var total = files.sumOf(File::length)
        for (old in files) {
            if (total <= 120L * 1024 * 1024) break
            if (old.nameWithoutExtension == key) continue
            total -= old.length()
            old.delete(); File(cacheDir, "${old.nameWithoutExtension}.json").delete()
        }
        SpotifyCanvasClip(file.baseFile, uri)
    }

    private data class Payload(val code: Int, val body: ByteArray, val location: String?)
    private suspend fun bytes(request: Request, maximumBytes: Int): Payload = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) { if (continuation.isActive) continuation.resumeWith(Result.failure(e)) }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        require(response.body.contentLength() <= maximumBytes) { "Spotify response exceeds size limit" }
                        val body = response.body.byteStream().use { input ->
                            val output = java.io.ByteArrayOutputStream()
                            val buffer = ByteArray(8192)
                            while (true) {
                                val read = input.read(buffer)
                                if (read < 0) break
                                require(output.size() + read <= maximumBytes) { "Spotify response exceeds size limit" }
                                output.write(buffer, 0, read)
                            }
                            output.toByteArray()
                        }
                        Payload(response.code, body, response.header("Location"))
                    }
                }
                if (continuation.isActive) continuation.resumeWith(result)
            }
        })
    }

    companion object {
        @Volatile private var instance: SpotifyCanvasRepository? = null
        fun getInstance(context: Context): SpotifyCanvasRepository = instance ?: synchronized(this) {
            instance ?: SpotifyCanvasRepository(context.applicationContext).also { instance = it }
        }
        private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
