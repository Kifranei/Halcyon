// Kotlin port/adaptation of CatClawMusic.Plugins.Netease's Eapi/OpenApi client.
// Copyright (c) 2026 kankejiang, MIT. Upstream dbb4db84904cd339fbebaf5a43f6b87e5818d17d.
package com.ella.music.data.netease

import android.content.Context
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.toPlaylistSong
import com.ella.music.data.model.toSong
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal const val NETEASE_SOURCE = "netease"
private val mvCoverCache = java.util.concurrent.ConcurrentHashMap<String, String>()
internal const val NETEASE_SCHEME = "halcyon-netease"
internal data class NeteaseCollections(val favorites: List<Song>, val playlists: List<UserPlaylist>, val favoritePlaylistId: String)
internal class NeteaseApiException(val code: Int) : IOException("NetEase API ($code)")
/** Quality actually served for a resolved stream; drives the player badge and live-output sheet. */
internal data class NeteaseStreamInfo(val url: String, val level: String, val type: String, val bitRate: Int, val sampleRate: Int)

internal class CatClawNeteaseClient(context: Context) {
    private val accounts = NeteaseAccountStore.getInstance(context)
    // API cookies never enter the CDN/player client, logs, or third-party fallback services.
    private val http = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS).callTimeout(45, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    suspend fun login(cookie: String, userIdHint: Long = 0): NeteaseAccount = withContext(Dispatchers.IO) {
        val normalized = neteaseCookies(cookie).entries.joinToString("; ") { "${it.key}=${it.value}" }
        require(neteaseCookies(normalized)["MUSIC_U"].orEmpty().isNotBlank()) { "MUSIC_U session required" }
        val root = request("/eapi/nuser/account/get", JSONObject(), normalized)
        val profile = root.optJSONObject("profile")
        val id = profile?.optLong("userId")?.takeIf { it > 0 }
            ?: root.optJSONObject("account")?.optLong("id")?.takeIf { it > 0 } ?: userIdHint
        require(id > 0) { "User ID required" }
        val name = profile?.optString("nickname").orEmpty().ifBlank { id.toString() }
        NeteaseAccount(normalized, id, name)
    }

    suspend fun collections(account: NeteaseAccount): NeteaseCollections = withContext(Dispatchers.IO) {
        check(account.loggedIn) { "NetEase login required" }
        val metadata = mutableListOf<JSONObject>()
        val seen = mutableSetOf<Long>()
        var offset = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            val page = request("/eapi/user/playlist", JSONObject().put("uid", account.userId)
                .put("offset", offset).put("limit", 200), account.cookie)
            val rows = page.optJSONArray("playlist") ?: throw IOException("Missing NetEase playlists")
            var added = 0
            for (i in 0 until rows.length()) {
                val row = rows.getJSONObject(i)
                if (seen.add(row.getLong("id"))) { metadata += row; added++ }
            }
            if (!page.optBoolean("more", rows.length() == 200)) break
            if (added == 0) throw IOException("NetEase playlist pagination did not advance")
            offset += rows.length()
        }
        val liked = metadata.firstOrNull {
            it.optInt("specialType") == 5 && it.optJSONObject("creator")?.optLong("userId") == account.userId
        } ?: metadata.firstOrNull { it.optJSONObject("creator")?.optLong("userId") == account.userId && !it.optBoolean("subscribed") }
        val playlists = metadata.map { row ->
            currentCoroutineContext().ensureActive()
            val id = row.getLong("id")
            val songs = playlistSongs(id, account.cookie)
            UserPlaylist(
                id = "remote:netease:${account.userId}:$id", name = row.optString("name"),
                songs = songs.map { it.toPlaylistSong() }, createdAt = row.optLong("createTime"),
                updatedAt = row.optLong("updateTime"), remoteSource = NETEASE_SOURCE,
                remoteServerId = account.userId.toString(), remotePlaylistId = id.toString(), remoteWritable = false
            )
        }
        val likedId = liked?.optLong("id")?.toString()
        val favorites = playlists.firstOrNull { it.remotePlaylistId == likedId }?.songs.orEmpty()
        NeteaseCollections(favorites.map { it.toSong() }, playlists, likedId.orEmpty())
    }

    private suspend fun playlistSongs(id: Long, cookie: String): List<Song> {
        val pl = request("/eapi/v6/playlist/detail", JSONObject().put("id", id).put("n", 1000).put("s", 0), cookie)
            .optJSONObject("playlist") ?: throw IOException("Missing NetEase playlist")
        val tracks = pl.optJSONArray("tracks") ?: JSONArray()
        val byId = linkedMapOf<Long, Song>()
        for (i in 0 until tracks.length()) parseNeteaseSong(tracks.getJSONObject(i))?.let { byId[it.onlineId.toLong()] = it }
        val ids = pl.optJSONArray("trackIds")?.let { array ->
            List(array.length()) { array.getJSONObject(it).getLong("id") }
        }.orEmpty()
        // The preview can be capped at 1,000 songs; trackIds is the authoritative full order.
        for (batch in ids.filterNot { it in byId }.chunked(500)) {
            currentCoroutineContext().ensureActive()
            val c = JSONArray().apply { batch.forEach { put(JSONObject().put("id", it)) } }
            val detail = request("/eapi/v3/song/detail", JSONObject().put("c", c.toString()), cookie)
                .optJSONArray("songs") ?: throw IOException("Missing NetEase song details")
            for (i in 0 until detail.length()) parseNeteaseSong(detail.getJSONObject(i))?.let { byId[it.onlineId.toLong()] = it }
        }
        return reconcileNeteaseTracks(ids, byId)
    }

    suspend fun songDetail(songId: String): JSONObject = withContext(Dispatchers.IO) {
        require((songId.toLongOrNull() ?: 0) > 0)
        request("/eapi/v3/song/detail", JSONObject().put("c", JSONArray()
            .put(JSONObject().put("id", songId)).toString()), accounts.account.value.cookie)
            .getJSONArray("songs").getJSONObject(0)
    }

    suspend fun musicVideoUrl(mvId: String, resolution: Int = 720): String = withContext(Dispatchers.IO) {
        require((mvId.toLongOrNull() ?: 0L) > 0L)
        val data = request("/eapi/song/enhance/play/mv/url",
            JSONObject().put("id", mvId).put("r", resolution), accounts.account.value.cookie)
            .optJSONObject("data") ?: throw IOException("Missing MV")
        val url = data.optString("url").toHttpUrlOrNull()
            ?: throw IOException("MV unavailable")
        url.newBuilder().scheme("https").build().toString()
    }

    suspend fun searchSongs(keyword: String, page: Int = 1): List<Song> = withContext(Dispatchers.IO) {
        if (keyword.isBlank()) return@withContext emptyList()
        val root = request("/eapi/cloudsearch/pc", JSONObject()
            .put("s", keyword.trim()).put("type", 1).put("limit", 30)
            .put("offset", (page.coerceAtLeast(1) - 1) * 30).put("total", true), accounts.account.value.cookie)
        val rows = root.optJSONObject("result")?.optJSONArray("songs") ?: JSONArray()
        (0 until rows.length()).mapNotNull { rows.optJSONObject(it)?.let(::parseNeteaseSong) }
    }

    suspend fun streamUrl(songId: String, cookie: String, quality: String = "auto"): String =
        resolveStream(songId, cookie, quality).url

    suspend fun resolveStream(songId: String, cookie: String, quality: String = "auto"): NeteaseStreamInfo = withContext(Dispatchers.IO) {
        var failureCode = -1
        var trial: NeteaseStreamInfo? = null
        for (level in neteaseQualityLevels(quality)) {
            currentCoroutineContext().ensureActive()
            val parameters = neteaseQualityRequest(songId, level)
            val root = request("/eapi/song/enhance/player/url/v1", parameters, cookie)
            val data = root.optJSONArray("data")?.optJSONObject(0) ?: throw NeteaseApiException(-1)
            val url = data.optString("url").takeUnless { it == "null" }.orEmpty()
            failureCode = data.optInt("code", -1)
            if (failureCode == 200 && url.isNotBlank()) {
                val parsed = url.toHttpUrlOrNull() ?: throw IOException("Invalid NetEase stream URL")
                val resolved = NeteaseStreamInfo(
                    url = parsed.newBuilder().scheme("https").build().toString(),
                    level = data.optString("level").ifBlank { level },
                    type = data.optString("type").ifBlank { data.optString("encodeType") },
                    bitRate = data.optInt("br"),
                    sampleRate = data.optInt("sr")
                )
                if (data.optJSONObject("freeTrialInfo") != null) {
                    if (trial == null) trial = resolved
                    continue
                }
                return@withContext resolved
            }
            // Only unavailable quality/resource responses fall back; authentication and
            // rate-limit errors must remain visible and must not fan out into more requests.
            if (failureCode !in setOf(200, 403, 404)) throw NeteaseApiException(failureCode)
        }
        trial ?: throw NeteaseApiException(failureCode)
    }

    /** Account "最近播放" (newest first). Each row carries the song object and its play time. */
    suspend fun recentPlays(cookie: String, limit: Int = 300): List<NeteaseRecentPlay> = withContext(Dispatchers.IO) {
        val root = request("/eapi/play-record/song/list", JSONObject().put("limit", limit.coerceIn(1, 1000)), cookie)
        parseNeteaseRecentPlays(root)
    }

    /** MV cover image URL (https), cached per id for the session. */
    suspend fun musicVideoCover(mvId: String): String? = withContext(Dispatchers.IO) {
        if ((mvId.toLongOrNull() ?: 0L) <= 0L) return@withContext null
        mvCoverCache[mvId]?.let { return@withContext it }
        val root = request("/eapi/v1/mv/detail", JSONObject().put("id", mvId), accounts.account.value.cookie)
        root.optJSONObject("data")?.optString("cover")
            ?.takeIf { it.startsWith("http") }
            ?.replaceFirst("http://", "https://")
            ?.also { mvCoverCache[mvId] = it }
    }

    suspend fun lyrics(songId: String, cookie: String): NeteaseLyrics = withContext(Dispatchers.IO) {
        val root = request("/eapi/song/lyric/v1", JSONObject().put("id", songId).put("lv", -1)
            .put("tv", -1).put("rv", -1).put("yv", -1).put("ytv", -1).put("yrv", -1), cookie)
        parseNeteaseLyricsResponse(root)
    }

    /**
     * Song comments (thread `R_SO_4_<id>`). First page: pageNo = 1, cursor = "". Later pages must
     * pass back the previous page's [NeteaseCommentPage.sortType] and [NeteaseCommentPage.cursor].
     */
    suspend fun songComments(
        songId: String,
        sortType: Int = NeteaseCommentSort.Recommend.apiValue,
        pageNo: Int = 1,
        cursor: String = "",
        pageSize: Int = 20
    ): NeteaseCommentPage = withContext(Dispatchers.IO) {
        require((songId.toLongOrNull() ?: 0L) > 0L)
        val page = pageNo.coerceAtLeast(1)
        val root = request("/eapi/v2/resource/comments", JSONObject()
            .put("threadId", neteaseSongThreadId(songId)).put("pageNo", page)
            .put("pageSize", pageSize.coerceIn(1, 50)).put("sortType", sortType)
            .put("cursor", if (page == 1) "" else cursor).put("showInner", true),
            accounts.account.value.cookie)
        parseNeteaseCommentPage(root, sortType, page)
    }

    /** Floor replies under [parentCommentId]; first page time = -1, then [NeteaseFloorPage.nextTime]. */
    suspend fun commentFloor(
        songId: String,
        parentCommentId: Long,
        time: Long = -1L,
        limit: Int = 20
    ): NeteaseFloorPage = withContext(Dispatchers.IO) {
        require((songId.toLongOrNull() ?: 0L) > 0L && parentCommentId > 0L)
        val root = request("/eapi/resource/comment/floor/get", JSONObject()
            .put("parentCommentId", parentCommentId).put("threadId", neteaseSongThreadId(songId))
            .put("limit", limit.coerceIn(1, 50)).put("time", time), accounts.account.value.cookie)
        parseNeteaseFloorPage(root)
    }

    /** The signed-in account, or null when comment writes would be answered with 301. */
    fun signedInAccount(): NeteaseAccount? = accounts.account.value.takeIf { it.loggedIn }

    /** Likes or unlikes a song comment; throws [NeteaseApiException] (301 = not signed in). */
    suspend fun likeComment(songId: String, commentId: Long, like: Boolean) = withContext(Dispatchers.IO) {
        require((songId.toLongOrNull() ?: 0L) > 0L && commentId > 0L)
        val account = signedInAccount() ?: throw NeteaseApiException(301)
        request(if (like) "/eapi/v1/comment/like" else "/eapi/v1/comment/unlike", JSONObject()
            .put("threadId", neteaseSongThreadId(songId)).put("commentId", commentId), account.cookie)
        Unit
    }

    /**
     * Posts a top-level song comment, or a reply to [replyToCommentId] when it is > 0. The reply
     * lands in floor [parentCommentId] (the top-level comment). Returns the created comment, or
     * null when the server accepted it without echoing it back.
     */
    suspend fun postComment(
        songId: String,
        content: String,
        replyToCommentId: Long = 0L,
        parentCommentId: Long = 0L
    ): NeteaseComment? = withContext(Dispatchers.IO) {
        val text = content.trim()
        require((songId.toLongOrNull() ?: 0L) > 0L && text.isNotEmpty() && neteaseCommentLength(text) <= NETEASE_COMMENT_MAX_LENGTH)
        val account = signedInAccount() ?: throw NeteaseApiException(301)
        val params = JSONObject().put("threadId", neteaseSongThreadId(songId)).put("content", text)
        val root = if (replyToCommentId > 0L) {
            request("/eapi/resource/comments/reply", params.put("commentId", replyToCommentId), account.cookie)
        } else {
            request("/eapi/resource/comments/add", params, account.cookie)
        }
        parseNeteaseCreatedComment(root, if (replyToCommentId > 0L) parentCommentId else 0L)
    }

    suspend fun setFavorite(playlistId: String, songId: String, favorite: Boolean, cookie: String) = withContext(Dispatchers.IO) {
        request("/eapi/playlist/manipulate/tracks", JSONObject().put("pid", playlistId)
            .put("trackIds", "[$songId]").put("op", if (favorite) "add" else "del"), cookie)
        Unit
    }

    private fun request(path: String, parameters: JSONObject, cookie: String): JSONObject {
        require(path.startsWith("/eapi/") && !path.contains('?'))
        val jar = neteaseCookies(cookie)
        val dolby = parameters.optString("level") == "vivid"
        val requestOs = if (dolby) "android" else "pc"
        val requestAppVersion = if (dolby) "9.5.61" else "3.1.3.203419"
        val header = JSONObject().put("os", requestOs).put("appver", requestAppVersion)
            .put("deviceId", accounts.deviceId).put("requestId", System.currentTimeMillis().toString())
            .put("osver", "Microsoft-Windows-10").put("clientSign", accounts.deviceId)
        jar.forEach { (name, value) -> header.put(name, value) }
        header.put("os", requestOs).put("appver", requestAppVersion)
        parameters.put("header", header.toString()).put("e_r", true)
        val body = FormBody.Builder().add("params", CatClawNeteaseCrypto.encrypt(path, parameters.toString())).build()
        val request = Request.Builder().url("https://interface.music.163.com$path").post(body)
            .header("Referer", "https://music.163.com/")
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) NeteaseMusicDesktop/3.1.3.203419")
            .header("Cookie", (jar + mapOf("os" to requestOs, "appver" to requestAppVersion, "deviceId" to accounts.deviceId))
                .entries.joinToString("; ") { "${it.key}=${it.value}" }).build()
        return http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("NetEase HTTP ${response.code}")
            val body = response.body ?: throw IOException("Empty NetEase response")
            val source = body.source()
            if (source.request(32L * 1024 * 1024 + 1)) throw IOException("NetEase response too large")
            val bytes = source.readByteArray()
            val json = JSONObject(CatClawNeteaseCrypto.decryptResponse(bytes))
            val code = json.optInt("code", 200)
            if (code != 200) throw NeteaseApiException(code)
            json
        }
    }
}

internal fun parseNeteaseSong(json: JSONObject): Song? {
    val id = json.optLong("id").takeIf { it > 0 } ?: return null
    val artists = json.optJSONArray("ar") ?: json.optJSONArray("artists") ?: JSONArray()
    val names = List(artists.length()) { artists.getJSONObject(it).optString("name") }.filter(String::isNotBlank)
    val album = json.optJSONObject("al") ?: json.optJSONObject("album") ?: JSONObject()
    val title = json.optString("name")
    return Song(id = -id, title = title, artist = names.joinToString(" / "), album = album.optString("name"),
        albumId = -album.optLong("id"), duration = json.optLong("dt", json.optLong("duration")),
        path = "$NETEASE_SCHEME://song/$id", fileName = title, mimeType = "",
        coverUrl = album.optString("picUrl").replace("http://", "https://"), onlineSource = NETEASE_SOURCE, onlineId = id.toString(),
        onlineMvId = json.optLong("mv", json.optLong("mvid")).takeIf { it > 0 }?.toString().orEmpty())
}

/** Preserve full playlist order, including removed tracks whose metadata endpoint omits them. */
internal fun reconcileNeteaseTracks(ids: List<Long>, byId: Map<Long, Song>): List<Song> =
    if (ids.isEmpty()) byId.values.toList() else ids.filter { it > 0 }.map { id ->
        byId[id] ?: requireNotNull(parseNeteaseSong(JSONObject().put("id", id).put("name", "#$id")))
    }
