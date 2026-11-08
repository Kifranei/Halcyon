package com.ella.music.data.sourceplugin

import android.net.Uri
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Halcyon Source Plugin API v1 — contract and models.
 *
 * A source plugin is a user-supplied QuickJS module that behaves like the built-in NetEase
 * account integration: it can sign in (cookie / QR / anonymous), expose the account's favorites
 * and playlists as a full music-library source, search, resolve playback URLs and lyrics, and
 * optionally provide recent plays, favorites sync, comments and MVs. The app ships zero plugin
 * code — everything lives in user-imported scripts, keeping platform logic out of the binary.
 *
 * Plugin module shape:
 * ```js
 * module.exports = {
 *   platform: "汽水音乐", id: "qishui", version: "1.0.0", author: "", apiVersion: 1,
 *   account: { methods: ["cookie"] },               // cookie | qrcode | none
 *   capabilities: { collections: true, search: true, lyric: true, recentPlays: true,
 *                   setFavorite: true, comments: false, mv: false, daily: false, radio: false },
 *   async login(ctx, { cookie }) -> { userId, nickname, avatar, credential },
 *   async getQrCode(ctx) -> { qr, key },            // qr: image URL / data:URI / base64
 *   async checkQrCode(ctx, key) -> { status: "waiting"|"scanned"|"confirmed"|"expired"|"failed",
 *                                  userId?, nickname?, avatar?, credential? },
 *   async getCollections(ctx) -> { favorites: [item], favoritePlaylistId,
 *                                  playlists: [{ id, name, coverUrl, description, songs: [item] }] },
 *   async getDailySongs(ctx) -> [item],             // optional pseudo playlists
 *   async getDailyPlaylists(ctx) -> [{ id, name, coverUrl, songs: [item] }],
 *   async getRadio(ctx, seedItem) -> [item],
 *   async search(ctx, keyword, page) -> { data: [item], isEnd },
 *   async getMediaSource(ctx, item, quality) -> { url, headers?, mimeType?, format?,
 *                                               bitRate?, sampleRate?, qualityLabel?, isTrial? },
 *   async getLyric(ctx, item) -> { rawLrc, translation?, pronunciation? },
 *   async getRecentPlays(ctx) -> [{ item, playedAt }],
 *   async setFavorite(ctx, songId, favorite),
 *   async getComments(ctx, item, page) -> { comments: [...], totalCount, hasMore, cursor, pageNo },
 *   async getCommentFloor(ctx, item, { commentId, time }) -> { comments: [...], hasMore, time },
 *   async likeComment(ctx, { item, commentId, like }),
 *   async postComment(ctx, { item, content, replyToCommentId? }) -> { comment? },
 *   async getMvUrl(ctx, mvId, resolution) -> { url, headers? },
 *   async getMvCover(ctx, mvId) -> "url",
 * }
 * ```
 *
 * `ctx` = `{ auth: <credential returned by login>, quality: "auto"|"128k"|"320k"|"flac"|"flac24bit" }`.
 * `item` (musicItem) needs at least `{ id, title }`; `artist`, `album`, `duration` (s or ms),
 * `artwork`, `mvId`, `lrc` are mapped onto Song; the raw object is persisted verbatim per
 * (plugin, id) and handed back to every call.
 */

const val SOURCE_PLUGIN_API_VERSION = 1
const val SOURCE_PLUGIN_SCHEME = "halcyon-plugin"
const val SOURCE_PLUGIN_PREFIX = "plugin:"
const val SOURCE_PLUGIN_SOURCE = "source_plugin"

fun isSourcePluginKey(source: String?): Boolean = source?.startsWith(SOURCE_PLUGIN_PREFIX) == true

fun sourcePluginKeyOf(source: String): String =
    if (isSourcePluginKey(source)) source.substring(SOURCE_PLUGIN_PREFIX.length) else ""

fun sourcePluginSourceKey(pluginId: String): String = SOURCE_PLUGIN_PREFIX + pluginId

/** Stable play URI embedded in Song.path; resolved to a real URL at prepare time. */
fun sourcePluginSongUri(pluginId: String, onlineId: String): String =
    "$SOURCE_PLUGIN_SCHEME://$pluginId/${Uri.encode(onlineId)}"

fun Song.isSourcePluginStream(): Boolean {
    if (!isSourcePluginKey(onlineSource)) return false
    val localFile = path.startsWith("/") ||
        path.startsWith("file:", ignoreCase = true) ||
        path.startsWith("content:", ignoreCase = true)
    return !localFile
}

data class SourcePluginConfig(
    val id: String,
    val name: String,
    val version: String = "",
    val author: String = "",
    val url: String = "",
    val enabled: Boolean = true,
    val importedAt: Long = 0L,
    val methods: List<String> = emptyList(),
    val capabilities: List<String> = emptyList()
) {
    fun hasCapability(name: String): Boolean = name in capabilities
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("name", name).put("version", version).put("author", author)
        .put("url", url).put("enabled", enabled).put("importedAt", importedAt)
        .put("methods", JSONArray().apply { methods.forEach(::put) })
        .put("capabilities", JSONArray().apply { capabilities.forEach(::put) })

    companion object {
        fun fromJson(json: JSONObject): SourcePluginConfig = SourcePluginConfig(
            id = json.optString("id"),
            name = json.optString("name"),
            version = json.optString("version"),
            author = json.optString("author"),
            url = json.optString("url"),
            enabled = json.optBoolean("enabled", true),
            importedAt = json.optLong("importedAt"),
            methods = json.optJSONArray("methods")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty(),
            capabilities = json.optJSONArray("capabilities")?.let { a -> (0 until a.length()).map { a.optString(it) } }.orEmpty()
        )

        /** Ids become file names, prefs keys and URI host segments — whitelist them. */
        fun sanitizeId(raw: String): String = raw.trim()
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9_-]+"), "-")
            .trim('-')
            .take(48)
    }
}

/** A signed-in plugin account; [credentialJson] is the opaque object login()/checkQrCode() returned. */
data class SourcePluginAccount(
    val pluginId: String,
    val userId: String = "",
    val nickname: String = "",
    val avatar: String = "",
    val credentialJson: String = ""
) {
    val loggedIn: Boolean get() = credentialJson.isNotBlank() || anonymous
    val anonymous: Boolean get() = userId.isBlank() && nickname.isBlank() && credentialJson.isBlank()

    fun toJson(): JSONObject = JSONObject()
        .put("userId", userId).put("nickname", nickname)
        .put("avatar", avatar).put("credential", credentialJson)

    companion object {
        fun anonymous(pluginId: String) = SourcePluginAccount(pluginId)
        fun fromJson(pluginId: String, json: JSONObject) = SourcePluginAccount(
            pluginId = pluginId,
            userId = json.optString("userId"),
            nickname = json.optString("nickname"),
            avatar = json.optString("avatar"),
            credentialJson = json.optString("credential")
        )
    }
}

data class SourcePluginRecentPlay(val song: Song, val playedAt: Long)

/** Deterministic id: the same cloud listen maps to the same entry across refreshes. */
internal fun SourcePluginRecentPlay.toPlaybackHistoryEntry(): com.ella.music.data.PlaybackHistoryEntry =
    com.ella.music.data.PlaybackHistoryEntry(
        entryId = "plugin:${song.onlineSource}:${song.onlineId}:$playedAt",
        songId = song.id,
        title = song.title,
        artist = song.artist,
        album = song.album,
        playedAt = playedAt,
        durationMs = song.duration,
        source = song.onlineSource,
        playCounted = true,
        onlineSource = song.onlineSource,
        onlineId = song.onlineId,
        coverUrl = song.coverUrl
    )

/** Rebuilds a playable plugin [Song] from a history record when it is not in the loaded library. */
internal fun com.ella.music.data.PlaybackHistoryEntry.toSourcePluginHistorySong(): Song? {
    val pluginId = sourcePluginKeyOf(onlineSource).takeIf { it.isNotBlank() } ?: return null
    if (mediaUri.isNotBlank() || onlineId.isBlank()) return null
    return Song(
        id = songId.takeIf { it != 0L } ?: -onlineId.hashCode().toLong(),
        title = title, artist = artist, album = album, albumId = 0L, duration = durationMs,
        path = sourcePluginSongUri(pluginId, onlineId), fileName = title, mimeType = "",
        coverUrl = coverUrl, onlineSource = onlineSource, onlineId = onlineId
    )
}

data class SourcePluginCollections(
    val favorites: List<Song>,
    val playlists: List<UserPlaylist>,
    val favoritePlaylistId: String,
    /** onlineId -> verbatim plugin musicItem JSON, persisted so playback can rebuild items. */
    val items: Map<String, String>
)

/** What the plugin's getMediaSource returned, normalized for the player + quality badge. */
data class SourcePluginStreamInfo(
    val url: String,
    val qualityLabel: String = "",
    val mimeType: String = "",
    val bitRate: Int = 0,
    val sampleRate: Int = 0,
    val isTrial: Boolean = false,
    val headersJson: String = ""
)

internal fun JSONObject.sourcePluginSong(pluginId: String): Pair<Song, String>? {
    val title = optFirstString("title", "name", "songname", "songName").trim()
    if (title.isBlank()) return null
    val artist = optPluginArtist().ifBlank { "未知歌手" }.trim()
    val album = optFirstString("album", "albumName", "albumname", "album_name").trim()
    val onlineId = optFirstString("id", "songmid", "mid", "hash", "rid", "songId", "song_id", "rid_str")
        .ifBlank { "$title:$artist:$album" }
    val artwork = optFirstString("artwork", "cover", "coverImg", "coverUrl", "pic", "picUrl", "albumArt", "img")
    val mvId = optFirstString("mvId", "mvid", "mv_id", "vid")
    val lyrics = optFirstString("rawLrc", "rawLrcTxt", "lrc", "lyric", "lyrics", "plainLyric")
        .ifBlank { optJSONObject("lyric")?.optString("rawLrc").orEmpty() }
    val translation = optFirstString("translation", "transLyric", "tlyric", "lyricTranslation")
    val durationMs = parsePluginDurationMs()
    val id = "sourceplugin_${pluginId}_$onlineId".hashCode().toLong()
    val song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = 0L,
        duration = durationMs,
        path = sourcePluginSongUri(pluginId, onlineId),
        fileName = "$title-$artist.mp3",
        mimeType = "audio/mpeg",
        coverUrl = artwork,
        onlineSource = sourcePluginSourceKey(pluginId),
        onlineId = onlineId,
        onlineMvId = mvId,
        onlineLyrics = lyrics,
        onlineLyricTranslation = translation
    )
    return song to toString()
}

internal fun JSONObject.optFirstString(vararg keys: String): String {
    return keys.firstNotNullOfOrNull { key ->
        opt(key)?.takeUnless { it == JSONObject.NULL }?.let { value ->
            when (value) {
                is String -> value
                is Number -> value.toString()
                else -> null
            }?.takeIf { it.isNotBlank() }
        }
    }.orEmpty()
}

private fun JSONObject.optPluginArtist(): String {
    optFirstString("artist", "singer", "author", "creator", "singerName").takeIf { it.isNotBlank() }?.let { return it }
    val artistArray = optJSONArray("artists") ?: optJSONArray("singers") ?: optJSONArray("ar") ?: optJSONArray("singer")
    if (artistArray != null) {
        val names = List(artistArray.length()) { index ->
            when (val value = artistArray.opt(index)) {
                is JSONObject -> value.optFirstString("name", "title", "singerName")
                is String -> value
                else -> ""
            }
        }.filter { it.isNotBlank() }
        if (names.isNotEmpty()) return names.joinToString(", ")
    }
    optJSONObject("artist")?.optFirstString("name", "title")?.takeIf { it.isNotBlank() }?.let { return it }
    optJSONObject("singer")?.optFirstString("name", "title")?.takeIf { it.isNotBlank() }?.let { return it }
    return ""
}

private fun JSONObject.parsePluginDurationMs(): Long {
    val raw = optFirstString("duration", "durationMs", "interval", "time", "dt", "playTime", "duration_ms")
    if (raw.isBlank()) return 0L
    if (":" in raw) {
        val parts = raw.split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isNotEmpty()) {
            val seconds = parts.fold(0L) { acc, part -> acc * 60L + part }
            return seconds * 1000L
        }
    }
    val number = raw.toDoubleOrNull() ?: return 0L
    return when {
        number <= 0.0 -> 0L
        raw.contains(".") && number < 86_400.0 -> (number * 1000L).toLong()
        number in 1.0..86_400.0 -> number.toLong() * 1000L
        else -> number.toLong()
    }
}

/** getMediaSource result -> normalized stream info; throws when no playable URL came back. */
internal fun JSONObject.toSourcePluginStreamInfo(): SourcePluginStreamInfo {
    val url = optString("url").takeIf { it.startsWith("http://") || it.startsWith("https://") }
        ?: throw java.io.IOException("插件没有返回播放地址")
    val headers = optJSONObject("headers")?.toString()
        ?: opt("header")?.takeIf { it is JSONObject }?.toString()
        ?: ""
    return SourcePluginStreamInfo(
        url = url,
        qualityLabel = optFirstString("qualityLabel", "quality", "level", "qualityName"),
        mimeType = optFirstString("mimeType", "format", "type"),
        bitRate = opt("bitRate")?.let { (it as? Number)?.toInt() } ?: optInt("bitRate", 0),
        sampleRate = opt("sampleRate")?.let { (it as? Number)?.toInt() } ?: optInt("sampleRate", 0),
        isTrial = optBoolean("isTrial") || optBoolean("trial"),
        headersJson = headers
    )
}
