package com.ella.music.data.remote

import android.content.Context
import com.ella.music.R
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.requireHttpsRequests
import com.ella.music.data.requireHttpsUrl
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

data class EmbyLoginResult(
    val token: String,
    val userId: String,
    val serverName: String
)

class EmbyService(private val context: Context) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(24, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("EmbyNetwork"))
        .requireHttpsRequests()
        .build()

    suspend fun login(baseUrl: String, username: String, password: String): EmbyLoginResult = withContext(Dispatchers.IO) {
        val url = "${baseUrl.trimEnd('/')}/Users/AuthenticateByName".toHttpUrlOrNull()
            ?.requireHttpsUrl("Emby server")
            ?: error(context.getString(R.string.remote_source_url_invalid))
        val payload = JSONObject()
            .put("Username", username)
            .put("Pw", password)
            .toString()
        val request = Request.Builder()
            .url(url)
            .header("X-Emby-Authorization", embyAuthHeader())
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.remote_source_http_error, response.code))
            val root = JSONObject(response.body?.string().orEmpty())
            EmbyLoginResult(
                token = root.optString("AccessToken"),
                userId = root.optJSONObject("User")?.optString("Id").orEmpty(),
                serverName = root.optJSONObject("SessionInfo")?.optString("ServerName").orEmpty()
            ).also {
                if (it.token.isBlank() || it.userId.isBlank()) error(context.getString(R.string.remote_source_invalid_response))
            }
        }
    }

    suspend fun test(config: RemoteMusicSourceConfig) = withContext(Dispatchers.IO) {
        get(config, "Users/${config.userId}")
    }

    suspend fun search(keyword: String, config: RemoteMusicSourceConfig): List<RemoteOnlineSong> = withContext(Dispatchers.IO) {
        val root = get(
            config,
            "Users/${config.userId}/Items",
            mapOf(
                "Recursive" to "true",
                "IncludeItemTypes" to "Audio",
                "SearchTerm" to keyword.trim(),
                "Fields" to "Genres,MediaSources,AlbumArtist",
                "Limit" to "50"
            )
        )
        val items = root.optJSONArray("Items") ?: return@withContext emptyList()
        List(items.length()) { index -> itemFromJson(items.getJSONObject(index), config) }
            .filter { it.remoteId.isNotBlank() }
    }

    suspend fun listSongs(config: RemoteMusicSourceConfig, limit: Int = Int.MAX_VALUE): List<RemoteOnlineSong> = withContext(Dispatchers.IO) {
        fetchOffsetPagedResults(limit = limit) { offset, pageSize ->
            fetchSongsPage(config, offset, pageSize)
        }
    }

    /** Fetch a single page of the library starting at [offset]. Used for incremental "load more". */
    suspend fun listSongsPage(
        config: RemoteMusicSourceConfig,
        offset: Int,
        count: Int
    ): List<RemoteOnlineSong> = withContext(Dispatchers.IO) {
        fetchSongsPage(config, offset.coerceAtLeast(0), count.coerceAtLeast(1))
    }

    private fun fetchSongsPage(
        config: RemoteMusicSourceConfig,
        offset: Int,
        pageSize: Int
    ): List<RemoteOnlineSong> {
        val root = get(
            config,
            "Users/${config.userId}/Items",
            mapOf(
                "Recursive" to "true",
                "IncludeItemTypes" to "Audio",
                "Fields" to "Genres,MediaSources,AlbumArtist",
                "SortBy" to "SortName",
                "SortOrder" to "Ascending",
                "StartIndex" to offset.toString(),
                "Limit" to pageSize.toString()
            )
        )
        val items = root.optJSONArray("Items") ?: return emptyList()
        return List(items.length()) { index -> itemFromJson(items.getJSONObject(index), config) }
            .filter { it.remoteId.isNotBlank() }
    }

    fun resolvePlayableSong(item: RemoteOnlineSong): Song =
        item.song.copy(path = item.streamUrl, coverUrl = item.coverUrl, onlineSource = RemoteMusicProvider.Emby.id)

    private fun itemFromJson(item: JSONObject, config: RemoteMusicSourceConfig): RemoteOnlineSong {
        val id = item.optString("Id")
        val title = item.optString("Name").ifBlank { context.getString(R.string.common_unknown) }
        val artist = item.optJSONArray("Artists")?.optString(0).orEmpty()
            .ifBlank { item.optString("AlbumArtist") }
            .ifBlank { context.getString(R.string.player_unknown_artist) }
        val album = item.optString("Album").ifBlank { "Emby" }
        val ticks = item.optLong("RunTimeTicks", 0L)
        val durationMs = if (ticks > 0L) ticks / 10_000L else 0L
        val stream = streamUrl(config, id)
        val cover = imageUrl(config, id)
        // Populate album/genre/year/track metadata so remote songs feed the same artist / album /
        // genre / year library views as local songs.
        val genre = item.optJSONArray("Genres")?.optString(0).orEmpty()
        val year = item.optInt("ProductionYear", 0).takeIf { it > 0 }?.toString().orEmpty()
        return RemoteOnlineSong(
            song = Song(
                id = stableId("emby:$id"),
                title = title,
                artist = artist,
                album = album,
                albumId = 0L,
                duration = durationMs,
                path = stream,
                fileName = "$title.mp3",
                mimeType = "audio/*",
                trackNumber = item.optInt("IndexNumber", 0),
                discNumber = item.optInt("ParentIndexNumber", 0),
                albumArtist = item.optString("AlbumArtist"),
                genre = genre,
                year = year,
                coverUrl = cover,
                onlineSource = RemoteMusicProvider.Emby.id,
                onlineId = id
            ),
            provider = RemoteMusicProvider.Emby,
            remoteId = id,
            streamUrl = stream,
            coverUrl = cover
        )
    }

    private fun get(
        config: RemoteMusicSourceConfig,
        path: String,
        params: Map<String, String> = emptyMap()
    ): JSONObject {
        val builder = "${config.baseUrl.trimEnd('/')}/${path.trimStart('/')}".toHttpUrlOrNull()
            ?.requireHttpsUrl("Emby server")
            ?.newBuilder()
            ?: error(context.getString(R.string.remote_source_url_invalid))
        params.forEach { (key, value) -> builder.addQueryParameter(key, value) }
        val request = Request.Builder()
            .url(builder.build())
            .header("X-Emby-Token", config.token)
            .header("X-Emby-Authorization", embyAuthHeader())
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.remote_source_http_error, response.code))
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun streamUrl(config: RemoteMusicSourceConfig, id: String): String =
        "${config.baseUrl.trimEnd('/')}/Audio/$id/universal".toHttpUrlOrNull()
            ?.requireHttpsUrl("Emby server")
            ?.newBuilder()
            ?.addQueryParameter("UserId", config.userId)
            ?.addQueryParameter("api_key", config.token)
            ?.addQueryParameter("Container", "flac,mp3,m4a,opus,ogg,wav")
            ?.addQueryParameter("TranscodingProtocol", "hls")
            ?.addQueryParameter("AudioCodec", "aac,mp3")
            ?.build()
            ?.toString()
            ?: ""

    private fun imageUrl(config: RemoteMusicSourceConfig, id: String): String =
        "${config.baseUrl.trimEnd('/')}/Items/$id/Images/Primary".toHttpUrlOrNull()
            ?.requireHttpsUrl("Emby server")
            ?.newBuilder()
            ?.addQueryParameter("maxWidth", "512")
            ?.addQueryParameter("quality", "90")
            ?.addQueryParameter("api_key", config.token)
            ?.build()
            ?.toString()
            ?: ""

    private fun embyAuthHeader(): String =
        "MediaBrowser Client=\"Halcyon\", Device=\"Android\", DeviceId=\"halcyon-android\", Version=\"1.0\""

    private fun stableId(key: String): Long =
        (key.hashCode().toLong() and Long.MAX_VALUE).takeIf { it != 0L } ?: key.hashCode().toLong().absoluteValue
}
