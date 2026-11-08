package com.ella.music.data.sourceplugin

import android.content.Context
import com.ella.music.R
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.OnlinePlaybackQuality
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.toPlaylistSong
import com.ella.music.data.model.toSong
import com.ella.music.data.model.toUserPlaylist
import com.ella.music.data.netease.NeteaseComment
import com.ella.music.data.netease.NeteaseCommentPage
import com.ella.music.data.netease.NeteaseCommentSort
import com.ella.music.data.netease.NeteaseCommentUser
import com.ella.music.data.netease.NeteaseFloorPage
import com.ella.music.data.netease.NeteaseRepliedComment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Thrown when a plugin call failed or returned an error envelope. */
open class SourcePluginException(message: String) : IOException(message)

data class SourcePluginSearchPage(val songs: List<Song>, val isEnd: Boolean)

/**
 * Typed facade over SourcePluginRuntime. Every call loads the script, injects ctx
 * ({auth = stored credential, quality}), dispatches plugin.<method>(ctx, ...args), and unwraps
 * the {ok,value,error} envelope. Music items are persisted verbatim in SourcePluginItemStore so
 * playback/lyrics/comments can rebuild them later.
 */
class SourcePluginClient(private val context: Context) {
    private val store = SourcePluginStore.getInstance(context)
    private val accounts = SourcePluginAccountStore.getInstance(context)
    private val settings = SettingsManager.getInstance(context)
    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("SourcePluginClient"))
        .build()

    private fun itemStore(pluginId: String) = SourcePluginItemStore(context, pluginId)

    private suspend fun ctxJson(config: SourcePluginConfig): String {
        val ctx = JSONObject()
        val account = accounts.account(config.id)
        val credential = account?.credentialJson.orEmpty()
        if (credential.isNotBlank()) {
            val parsed = runCatching { JSONObject(credential) }.getOrNull()
                ?: runCatching { JSONArray(credential) }.getOrNull()
            ctx.put("auth", parsed ?: credential)
        } else {
            ctx.put("auth", JSONObject.NULL)
        }
        account?.let {
            if (it.userId.isNotBlank()) ctx.put("userId", it.userId)
            if (it.nickname.isNotBlank()) ctx.put("nickname", it.nickname)
        }
        ctx.put("quality", OnlinePlaybackQuality.normalize(settings.onlinePlaybackQuality.first()))
        ctx.put("pluginId", config.id)
        return ctx.toString()
    }

    /** Runs plugin.<method>(ctx, ...args); returns the envelope's `value`. */
    private suspend fun call(
        config: SourcePluginConfig,
        method: String,
        vararg args: Any?
    ): Any? = withContext(Dispatchers.IO) {
        val script = store.scriptOf(config.id)
        if (script.isBlank()) throw SourcePluginException(context.getString(R.string.source_plugin_missing_script))
        val argsJson = JSONArray().apply { args.forEach { put(it ?: JSONObject.NULL) } }.toString()
        val ctx = ctxJson(config)
        SourcePluginRuntime(context, client, kvPrefs(config.id)).use { runtime ->
            val envelope = runtime.call(script, method, ctx, argsJson)
            if (!envelope.optBoolean("ok")) {
                val error = envelope.optString("error")
                if (error.startsWith("PLUGIN_NO_METHOD:")) throw SourcePluginNoMethodException(method)
                throw SourcePluginException(error.ifBlank { context.getString(R.string.source_plugin_call_failed, method) })
            }
            envelope.opt("value")?.takeUnless { it == JSONObject.NULL }
        }
    }

    private fun kvPrefs(pluginId: String) = "source_plugin_kv_$pluginId"

    class SourcePluginNoMethodException(val method: String) : SourcePluginException("插件未实现 $method")

    // ------------------------------------------------------------- account

    suspend fun login(config: SourcePluginConfig, cookie: String): SourcePluginAccount {
        val value = call(config, "login", JSONObject().put("cookie", cookie))
            as? JSONObject ?: throw SourcePluginException(context.getString(R.string.source_plugin_login_failed))
        val account = SourcePluginAccount(
            pluginId = config.id,
            userId = value.optFirstString("userId", "uid", "id"),
            nickname = value.optFirstString("nickname", "name", "userName"),
            avatar = value.optFirstString("avatar", "avatarUrl", "pic"),
            credentialJson = value.opt("credential")?.toString().orEmpty()
                .ifBlank { value.optString("cookie") }
        )
        if (account.userId.isBlank() && account.credentialJson.isBlank()) {
            throw SourcePluginException(context.getString(R.string.source_plugin_login_failed))
        }
        accounts.save(account)
        return account
    }

    suspend fun getQrCode(config: SourcePluginConfig): JSONObject {
        val value = call(config, "getQrCode") as? JSONObject
            ?: throw SourcePluginException(context.getString(R.string.source_plugin_qrcode_failed))
        if (value.optFirstString("qr", "qrCode", "qrImg", "image", "url").isBlank()) {
            throw SourcePluginException(context.getString(R.string.source_plugin_qrcode_failed))
        }
        return value
    }

    suspend fun checkQrCode(config: SourcePluginConfig, key: String): JSONObject {
        return call(config, "checkQrCode", key) as? JSONObject ?: JSONObject()
    }

    /** Saves the credential a confirmed QR login produced. */
    suspend fun saveQrLogin(config: SourcePluginConfig, result: JSONObject): SourcePluginAccount {
        val account = SourcePluginAccount(
            pluginId = config.id,
            userId = result.optFirstString("userId", "uid", "id"),
            nickname = result.optFirstString("nickname", "name", "userName"),
            avatar = result.optFirstString("avatar", "avatarUrl", "pic"),
            credentialJson = result.opt("credential")?.toString().orEmpty()
        )
        accounts.save(account)
        return account
    }

    suspend fun logout(config: SourcePluginConfig) = accounts.clear(config.id)

    // ------------------------------------------------------------ library

    suspend fun collections(config: SourcePluginConfig): SourcePluginCollections {
        val value = call(config, "getCollections") as? JSONObject
            ?: throw SourcePluginException(context.getString(R.string.source_plugin_sync_failed))
        return parseCollections(config, value)
    }

    private fun parseCollections(config: SourcePluginConfig, value: JSONObject): SourcePluginCollections {
        val items = linkedMapOf<String, String>()
        fun songs(array: JSONArray?): List<Song> {
            if (array == null) return emptyList()
            return (0 until array.length()).mapNotNull { i ->
                array.optJSONObject(i)?.sourcePluginSong(config.id)?.also { (song, raw) ->
                    items[song.onlineId] = raw
                }?.first
            }
        }
        val favorites = songs(value.optJSONArray("favorites") ?: value.optJSONArray("songs"))
        val playlists = (value.optJSONArray("playlists") ?: JSONArray()).let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                val p = arr.optJSONObject(i) ?: return@mapNotNull null
                val pid = p.optFirstString("id", "playlistId", "disstid")
                val songs = songs(p.optJSONArray("songs"))
                val name = p.optFirstString("name", "title", "dissName").ifBlank { "歌单" }
                UserPlaylist(
                    id = "remote:sourceplugin:${config.id}:$pid",
                    name = name,
                    songs = songs.map { it.toPlaylistSong() },
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    remoteSource = sourcePluginSourceKey(config.id),
                    remoteServerId = accounts.account(config.id)?.userId.orEmpty(),
                    remotePlaylistId = pid
                )
            }
        }
        return SourcePluginCollections(
            favorites = favorites,
            playlists = playlists,
            favoritePlaylistId = value.optFirstString("favoritePlaylistId", "favPlaylistId", "likedPlaylistId"),
            items = items
        )
    }

    suspend fun persistItems(config: SourcePluginConfig, items: Map<String, String>) =
        itemStore(config.id).putAll(items)

    suspend fun itemJson(config: SourcePluginConfig, song: Song): String {
        itemStore(config.id).item(song.onlineId)?.let { return it }
        // The item vanished from the store (cache cleared / imported library). Rebuild a minimal
        // one so the plugin can still resolve by id + metadata.
        return JSONObject()
            .put("id", song.onlineId)
            .put("title", song.title).put("artist", song.artist).put("album", song.album)
            .put("artwork", song.coverUrl).put("duration", song.duration / 1000.0)
            .put("mvId", song.onlineMvId)
            .toString()
    }

    // ------------------------------------------------------------- search

    suspend fun search(config: SourcePluginConfig, keyword: String, page: Int): SourcePluginSearchPage {
        val value = call(config, "search", keyword, page.coerceAtLeast(1))
        val obj = when (value) {
            is JSONObject -> value
            is JSONArray -> JSONObject().put("data", value)
            else -> JSONObject()
        }
        val items = linkedMapOf<String, String>()
        val data = obj.optJSONArray("data") ?: obj.optJSONArray("list") ?: JSONArray()
        val songs = (0 until data.length()).mapNotNull { i ->
            data.optJSONObject(i)?.sourcePluginSong(config.id)?.also { (song, raw) ->
                items[song.onlineId] = raw
            }?.first
        }
        persistItems(config, items)
        val isEnd = when {
            obj.has("isEnd") -> obj.optBoolean("isEnd")
            obj.has("hasMore") -> !obj.optBoolean("hasMore")
            else -> songs.isEmpty()
        }
        return SourcePluginSearchPage(songs, isEnd)
    }

    // ---------------------------------------------------------- playback

    suspend fun getMediaSource(config: SourcePluginConfig, song: Song): SourcePluginStreamInfo {
        val itemJson = itemJson(config, song)
        val value = call(config, "getMediaSource", JSONObject(itemJson),
            OnlinePlaybackQuality.normalize(settings.onlinePlaybackQuality.first()))
        val obj = value as? JSONObject
            ?: (value as? String)?.let { JSONObject().put("url", it) }
            ?: throw SourcePluginException(context.getString(R.string.source_plugin_no_url))
        return obj.toSourcePluginStreamInfo()
    }

    suspend fun getLyric(config: SourcePluginConfig, song: Song): Song {
        if (!config.hasCapability("lyric")) return song
        val itemJson = itemJson(config, song)
        val value = runCatching { call(config, "getLyric", JSONObject(itemJson)) }.getOrNull() as? JSONObject
            ?: return song
        val lyrics = value.optFirstString("rawLrc", "rawLrcTxt", "lrc", "lyric", "lyrics", "plainLyric")
        val translation = value.optFirstString("translation", "transLyric", "tlyric", "lyricTranslation")
        val pronunciation = value.optFirstString("pronunciation", "romanization", "romalrc", "romaji")
        if (lyrics.isBlank() && translation.isBlank() && pronunciation.isBlank()) return song
        return song.copy(
            onlineLyrics = lyrics.ifBlank { song.onlineLyrics },
            onlineLyricTranslation = translation.ifBlank { song.onlineLyricTranslation },
            onlineLyricPronunciation = pronunciation.ifBlank { song.onlineLyricPronunciation }
        )
    }

    // ------------------------------------------------------- recent plays

    suspend fun getRecentPlays(config: SourcePluginConfig): List<SourcePluginRecentPlay> {
        val value = call(config, "getRecentPlays") ?: return emptyList()
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("data") ?: value.optJSONArray("list") ?: return emptyList()
            else -> return emptyList()
        }
        val items = linkedMapOf<String, String>()
        val plays = (0 until array.length()).mapNotNull { i ->
            val row = array.optJSONObject(i) ?: return@mapNotNull null
            val item = row.optJSONObject("item") ?: row
            val (song, raw) = item.sourcePluginSong(config.id) ?: return@mapNotNull null
            items[song.onlineId] = raw
            val playedAt = row.optLong("playedAt", row.optLong("playTime", 0L))
            SourcePluginRecentPlay(song, if (playedAt > 0L) playedAt else System.currentTimeMillis())
        }
        persistItems(config, items)
        return plays.sortedByDescending { it.playedAt }
    }

    // ------------------------------------------------------------ favorite

    suspend fun setFavorite(config: SourcePluginConfig, songId: String, favorite: Boolean) {
        call(config, "setFavorite", songId, favorite)
    }

    // --------------------------------------------------------- extra feeds

    suspend fun getDailySongs(config: SourcePluginConfig): List<Song> {
        val value = call(config, "getDailySongs") ?: return emptyList()
        val (songs, items) = parseSongList(config, value)
        persistItems(config, items)
        return songs
    }

    suspend fun getDailyPlaylists(config: SourcePluginConfig): List<UserPlaylist> {
        val value = call(config, "getDailyPlaylists") ?: return emptyList()
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("data") ?: value.optJSONArray("playlists") ?: return emptyList()
            else -> return emptyList()
        }
        val items = linkedMapOf<String, String>()
        val playlists = (0 until array.length()).mapNotNull { i ->
            val p = array.optJSONObject(i) ?: return@mapNotNull null
            val pid = p.optFirstString("id", "playlistId")
            val songsArr = p.optJSONArray("songs")
            val songs = (0 until (songsArr?.length() ?: 0)).mapNotNull { j ->
                songsArr!!.optJSONObject(j)?.sourcePluginSong(config.id)?.also { (song, raw) ->
                    items[song.onlineId] = raw
                }?.first
            }
            UserPlaylist(
                id = "remote:sourceplugin:${config.id}:daily:$pid",
                name = p.optFirstString("name", "title").ifBlank { "推荐歌单" },
                songs = songs.map { it.toPlaylistSong() },
                createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
                remoteSource = sourcePluginSourceKey(config.id),
                remoteServerId = accounts.account(config.id)?.userId.orEmpty(),
                remotePlaylistId = "daily:$pid"
            )
        }
        persistItems(config, items)
        return playlists
    }

    suspend fun getRadio(config: SourcePluginConfig, seed: Song): List<Song> {
        val value = call(config, "getRadio", JSONObject(itemJson(config, seed))) ?: return emptyList()
        return parseSongList(config, value).first
    }

    private fun parseSongList(config: SourcePluginConfig, value: Any): Pair<List<Song>, Map<String, String>> {
        val array = when (value) {
            is JSONArray -> value
            is JSONObject -> value.optJSONArray("data") ?: value.optJSONArray("songs") ?: value.optJSONArray("list")
            else -> null
        } ?: return emptyList<Song>() to emptyMap()
        val items = linkedMapOf<String, String>()
        val songs = (0 until array.length()).mapNotNull { i ->
            array.optJSONObject(i)?.sourcePluginSong(config.id)?.also { (song, raw) ->
                items[song.onlineId] = raw
            }?.first
        }
        return songs to items
    }

    // ------------------------------------------------------------ comments

    internal suspend fun getComments(
        config: SourcePluginConfig, song: Song,
        sortType: Int, pageNo: Int, cursor: String, pageSize: Int = 20
    ): NeteaseCommentPage {
        val itemJson = itemJson(config, song)
        val params = JSONObject()
            .put("pageNo", pageNo).put("page", pageNo)
            .put("sortType", sortType).put("cursor", cursor).put("pageSize", pageSize)
        val value = call(config, "getComments", JSONObject(itemJson), params) as? JSONObject
            ?: throw SourcePluginException(context.getString(R.string.source_plugin_comments_failed))
        return parseCommentPage(value, sortType, pageNo)
    }

    internal suspend fun getCommentFloor(
        config: SourcePluginConfig, song: Song,
        parentCommentId: Long, time: Long, limit: Int = 20
    ): NeteaseFloorPage {
        val itemJson = itemJson(config, song)
        val params = JSONObject().put("commentId", parentCommentId).put("time", time).put("limit", limit)
        val value = call(config, "getCommentFloor", JSONObject(itemJson), params) as? JSONObject
            ?: throw SourcePluginException(context.getString(R.string.source_plugin_comments_failed))
        val replies = (value.optJSONArray("comments") ?: value.optJSONArray("replies"))
            ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toPluginComment() } }
            .orEmpty()
        val nextTime = value.optLong("time", replies.lastOrNull()?.timeMs ?: -1L)
        return NeteaseFloorPage(
            replies = replies,
            totalCount = value.optInt("totalCount", replies.size),
            hasMore = value.optBoolean("hasMore", false) && replies.isNotEmpty() && nextTime > 0,
            nextTime = nextTime
        )
    }

    internal suspend fun likeComment(config: SourcePluginConfig, song: Song, commentId: Long, like: Boolean) {
        call(config, "likeComment", JSONObject()
            .put("commentId", commentId).put("like", like)
            .put("item", JSONObject(itemJson(config, song))))
    }

    internal suspend fun postComment(
        config: SourcePluginConfig, song: Song, content: String,
        replyToCommentId: Long = 0L, parentCommentId: Long = 0L
    ): NeteaseComment? {
        val value = call(config, "postComment", JSONObject()
            .put("content", content).put("replyToCommentId", replyToCommentId)
            .put("parentCommentId", parentCommentId)
            .put("item", JSONObject(itemJson(config, song)))) as? JSONObject
        return value?.optJSONObject("comment")?.toPluginComment()
    }

    private fun parseCommentPage(value: JSONObject, requestedSort: Int, pageNo: Int): NeteaseCommentPage {
        val comments = (value.optJSONArray("comments") ?: value.optJSONArray("list"))
            ?.let { arr -> (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toPluginComment() } }
            .orEmpty()
        return NeteaseCommentPage(
            comments = comments.distinctBy { it.id },
            totalCount = value.optLong("totalCount", comments.size.toLong()),
            hasMore = value.optBoolean("hasMore", !value.optBoolean("isEnd") && comments.isNotEmpty()),
            cursor = value.optString("cursor"),
            sortType = value.optInt("sortType", requestedSort).takeIf { it > 0 } ?: requestedSort,
            pageNo = pageNo
        )
    }

    private fun JSONObject.toPluginComment(): NeteaseComment? {
        val id = opt("commentId")?.let { (it as? Number)?.toLong() }
            ?: opt("id")?.let { (it as? Number)?.toLong() ?: it.toString().toLongOrNull() }
            ?: optFirstString("id", "commentId").hashCode().toLong()
        val userObj = optJSONObject("user") ?: optJSONObject("author")
        val user = NeteaseCommentUser(
            userId = userObj?.optLong("userId") ?: userObj?.optLong("id") ?: 0L,
            nickname = userObj?.optFirstString("nickname", "name", "userName")
                ?: optFirstString("nickname", "userName", "author"),
            avatarUrl = userObj?.optFirstString("avatar", "avatarUrl", "pic")
                ?: optFirstString("avatar", "avatarUrl"),
            vipIconUrl = userObj?.optFirstString("vipIconUrl", "vipIcon") ?: ""
        )
        val replied = (optJSONObject("replied") ?: optJSONObject("replyTo") ?: optJSONObject("beReplied"))
            ?.let { t ->
                NeteaseRepliedComment(
                    commentId = t.optLong("commentId", t.optLong("id")),
                    nickname = t.optJSONObject("user")?.optFirstString("nickname", "name")
                        ?: t.optFirstString("nickname", "userName", "name"),
                    content = t.optFirstString("content", "text")
                )
            }
        return NeteaseComment(
            id = id,
            user = user,
            content = optFirstString("content", "text"),
            timeMs = optLong("time", optLong("timeMs")),
            timeStr = optFirstString("timeStr", "timeText", "timeDesc"),
            ipLocation = optFirstString("ipLocation", "location", "ip"),
            likedCount = optLong("likedCount", optLong("likeCount")),
            replyCount = optInt("replyCount", optInt("replies")),
            parentCommentId = optLong("parentCommentId"),
            replyTo = replied,
            liked = optBoolean("liked")
        )
    }

    // ----------------------------------------------------------------- mv

    suspend fun getMvUrl(config: SourcePluginConfig, song: Song, mvId: String, resolution: Int = 720): JSONObject {
        val value = call(config, "getMvUrl", mvId, resolution) ?: throw SourcePluginException("MV 地址获取失败")
        return when (value) {
            is JSONObject -> value
            is String -> JSONObject().put("url", value)
            else -> throw SourcePluginException("MV 地址获取失败")
        }
    }

    suspend fun getMvCover(config: SourcePluginConfig, mvId: String): String {
        val value = runCatching { call(config, "getMvCover", mvId) }.getOrNull()
        return when (value) {
            is String -> value
            is JSONObject -> value.optFirstString("url", "cover", "pic")
            else -> ""
        }
    }
}
