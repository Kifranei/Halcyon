package com.ella.music.data.lastfm

import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** Small, signed Last.fm 2.0 API client. All network work stays off the main thread. */
class LastFmApi {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun requestAuthorizationToken(credentials: LastFmCredentials): String = withContext(Dispatchers.IO) {
        require(credentials.hasAppCredentials) { "Missing Last.fm API key or shared secret" }
        signedGet(
            credentials = credentials,
            method = "auth.getToken",
            parameters = emptyMap()
        ).optString("token").takeIf(String::isNotBlank)
            ?: error("Last.fm did not return an authorization token")
    }

    fun authorizationIntent(apiKey: String? = null, token: String? = null): Intent {
        val builder = Uri.parse("https://www.last.fm/api/auth/").buildUpon()
        if (!apiKey.isNullOrBlank()) builder.appendQueryParameter("api_key", apiKey)
        if (!token.isNullOrBlank()) builder.appendQueryParameter("token", token)
        return Intent(Intent.ACTION_VIEW, builder.build())
    }

    suspend fun finishAuthorization(credentials: LastFmCredentials, token: String): LastFmSession = withContext(Dispatchers.IO) {
        require(credentials.hasAppCredentials) { "Missing Last.fm API key or shared secret" }
        val root = signedGet(
            credentials = credentials,
            method = "auth.getSession",
            parameters = mapOf("token" to token)
        )
        val session = root.optJSONObject("session") ?: error("Last.fm did not return a session")
        LastFmSession(
            username = session.optString("name").trim().also { require(it.isNotBlank()) { "Last.fm did not return a username" } },
            sessionKey = session.optString("key").trim().also { require(it.isNotBlank()) { "Last.fm did not return a session key" } }
        )
    }

    suspend fun getRecentTracks(
        apiKey: String,
        username: String,
        page: Int,
        limit: Int = RECENT_TRACK_PAGE_SIZE
    ): LastFmRecentTracksPage = withContext(Dispatchers.IO) {
        require(apiKey.isNotBlank()) { "Missing Last.fm API key" }
        require(username.isNotBlank()) { "Missing Last.fm username" }
        val root = unsignedGet(
            mapOf(
                "method" to "user.getRecentTracks",
                "api_key" to apiKey,
                "user" to username,
                "page" to page.coerceAtLeast(1).toString(),
                "limit" to limit.coerceIn(1, RECENT_TRACK_PAGE_SIZE).toString(),
                "extended" to "0",
                "format" to "json"
            )
        )
        val recentTracks = root.optJSONObject("recenttracks") ?: return@withContext LastFmRecentTracksPage(emptyList(), page, page, 0)
        val attributes = recentTracks.optJSONObject("@attr")
        val pageNumber = attributes?.optInt("page", page)?.coerceAtLeast(1) ?: page.coerceAtLeast(1)
        val totalPages = attributes?.optInt("totalPages", pageNumber)?.coerceAtLeast(pageNumber) ?: pageNumber
        val total = attributes?.optInt("total", 0)?.coerceAtLeast(0) ?: 0
        val rawTracks = recentTracks.opt("track").asJsonArray()
        LastFmRecentTracksPage(
            tracks = rawTracks.mapNotNull { raw ->
                (raw as? JSONObject)?.let(::trackFromJson)
            },
            page = pageNumber,
            totalPages = totalPages,
            totalTracks = total
        )
    }

    suspend fun updateNowPlaying(credentials: LastFmCredentials, track: LastFmPendingScrobble) {
        if (!credentials.isAuthorized) return
        signedPost(
            credentials = credentials,
            method = "track.updateNowPlaying",
            parameters = buildMap {
                put("artist", track.artist)
                put("track", track.track)
                track.album.takeIf(String::isNotBlank)?.let { put("album", it) }
                track.durationSeconds.takeIf { it > 0 }?.let { put("duration", it.toString()) }
            }
        )
    }

    /** Returns false when Last.fm accepted the request but filtered this individual play. */
    suspend fun scrobble(credentials: LastFmCredentials, track: LastFmPendingScrobble): Boolean {
        require(credentials.isAuthorized) { "Last.fm is not authorized" }
        val response = signedPost(
            credentials = credentials,
            method = "track.scrobble",
            parameters = buildMap {
                put("artist", track.artist)
                put("track", track.track)
                put("timestamp", (track.startedAt / 1_000L).coerceAtLeast(0L).toString())
                track.album.takeIf(String::isNotBlank)?.let { put("album", it) }
                track.durationSeconds.takeIf { it > 0 }?.let { put("duration", it.toString()) }
            }
        )
        val scrobbles = response.optJSONObject("scrobbles")
        // The JSON response stores the accepted/ignored counters under @attr, unlike the
        // XML representation where they are attributes of <scrobbles>.
        val accepted = scrobbles
            ?.optJSONObject("@attr")
            ?.optInt("accepted", scrobbles.optInt("accepted", 1))
            ?: scrobbles?.optInt("accepted", 1)
            ?: 1
        return accepted > 0
    }

    private fun trackFromJson(raw: JSONObject): LastFmTrack? {
        val isNowPlaying = raw.optJSONObject("@attr")?.optString("nowplaying") == "true"
        if (isNowPlaying) return null
        val playedAtSeconds = raw.optJSONObject("date")?.optLong("uts", 0L) ?: 0L
        if (playedAtSeconds <= 0L) return null
        val title = raw.optString("name").trim()
        val artist = raw.optJSONObject("artist")?.optString("#text").orEmpty().trim()
        if (title.isBlank() || artist.isBlank()) return null
        val album = raw.optJSONObject("album")?.optString("#text").orEmpty().trim()
        val durationMs = raw.optString("duration").toLongOrNull()?.let { rawDuration ->
            // API documentation calls this seconds, while some clients return milliseconds.
            if (rawDuration in 1..3_600) rawDuration * 1_000L else rawDuration
        } ?: 0L
        val image = raw.opt("image").asJsonArray()
            .mapNotNull { item -> (item as? JSONObject)?.optString("#text")?.trim()?.takeIf(String::isNotBlank) }
            .lastOrNull()
            .orEmpty()
        return LastFmTrack(
            title = title,
            artist = artist,
            album = album,
            playedAt = playedAtSeconds * 1_000L,
            durationMs = durationMs,
            albumArtUrl = image,
            mbid = raw.optString("mbid").trim()
        )
    }

    private fun signedGet(
        credentials: LastFmCredentials,
        method: String,
        parameters: Map<String, String>
    ): JSONObject {
        val signed = signedParameters(credentials, method, parameters)
        return unsignedGet(signed + ("format" to "json"))
    }

    private fun signedPost(
        credentials: LastFmCredentials,
        method: String,
        parameters: Map<String, String>
    ): JSONObject {
        val fields = signedParameters(credentials, method, parameters) + ("format" to "json")
        val form = FormBody.Builder().apply {
            fields.forEach { (key, value) -> add(key, value) }
        }.build()
        return execute(Request.Builder().url(API_URL).post(form).build())
    }

    private fun signedParameters(
        credentials: LastFmCredentials,
        method: String,
        parameters: Map<String, String>
    ): Map<String, String> {
        val fields = linkedMapOf<String, String>()
        fields.putAll(parameters.filterValues(String::isNotBlank))
        fields["api_key"] = credentials.apiKey
        fields["method"] = method
        credentials.sessionKey.takeIf(String::isNotBlank)?.let { fields["sk"] = it }
        val signatureText = fields
            .toSortedMap()
            .entries
            .joinToString(separator = "") { (key, value) -> key + value } + credentials.sharedSecret
        fields["api_sig"] = signatureText.md5()
        return fields
    }

    private fun unsignedGet(parameters: Map<String, String>): JSONObject {
        val url = API_URL.toHttpUrl().newBuilder().apply {
            parameters.forEach { (key, value) -> addQueryParameter(key, value) }
        }.build()
        return execute(Request.Builder().url(url).get().build())
    }

    private fun execute(request: Request): JSONObject {
        client.newCall(request).execute().use { response ->
            val rawBody = response.body?.string().orEmpty()
            val root = runCatching { JSONObject(rawBody) }
                .getOrElse {
                    error("Last.fm request failed: HTTP ${response.code}")
                }
            if (root.has("error")) {
                throw LastFmApiException(
                    code = root.optInt("error", -1),
                    message = root.optString("message").ifBlank { "Last.fm request failed" }
                )
            }
            if (!response.isSuccessful) error("Last.fm request failed: HTTP ${response.code}")
            return root
        }
    }

    private fun Any?.asJsonArray(): List<Any?> = when (this) {
        is JSONArray -> List(length()) { index -> opt(index) }
        is JSONObject -> listOf(this)
        else -> emptyList()
    }

    companion object {
        private const val API_URL = "https://ws.audioscrobbler.com/2.0/"
        const val RECENT_TRACK_PAGE_SIZE = 200
    }
}
