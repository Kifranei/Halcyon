package com.ella.music.data.lx

import android.content.Context
import com.ella.music.R
import com.ella.music.data.AppNetworkLoggingInterceptor
import com.ella.music.data.model.Song
import com.ella.music.data.LxSourceConfig
import com.ella.music.data.readUtf8Bounded
import com.ella.music.data.requireHttpsRequests
import com.ella.music.data.requireHttpsUrl
import com.ella.music.data.detectAudioContainerFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URLEncoder
import java.io.InputStream
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

data class LxOnlineQuality(
    val type: String,
    val hash: String = ""
)

data class LxOnlineSong(
    val song: Song,
    val source: String,
    val songmid: String,
    val quality: String,
    val coverUrl: String = "",
    val qualities: List<LxOnlineQuality> = emptyList(),
    val sourceMetadata: Map<String, String> = emptyMap()
)

internal const val MAX_LX_SOURCE_BYTES = 9_000_000L

internal fun InputStream.readLxSourceText(): String = readUtf8Bounded(MAX_LX_SOURCE_BYTES)

enum class LxSearchPlatform(
    val source: String,
    val displayName: String,
    val supportsNameSearch: Boolean = true
) {
    Kuwo("kw", "酷我"),
    Netease("wy", "网易云"),
    QQ("tx", "QQ音乐"),
    Kugou("kg", "酷狗"),
    Migu("mg", "咪咕音乐"),
    Qishui("qs", "汽水音乐（测试）", supportsNameSearch = false);

    // Source scripts use both identifiers for Qishui; keep their own key for requests.
    val sourceKeys: List<String>
        get() = if (this == Qishui) listOf("qs", "sd") else listOf(source)

    fun declaredSourceKey(declaredSources: Set<String>): String? =
        sourceKeys.firstOrNull { it in declaredSources }
}

private val QISHUI_TRACK_ID = Regex("[1-9][0-9]{0,19}")

private fun isQishuiTrackUrl(url: HttpUrl): Boolean =
    url.isHttps && url.port == 443 && url.username.isEmpty() && url.password.isEmpty() && when (url.host) {
        "qishui.douyin.com" -> Regex("/s/[A-Za-z0-9]+/?").matches(url.encodedPath)
        "music.douyin.com" -> url.encodedPath.trimEnd('/') == "/qishui/share/track"
        "www.douyin.com", "douyin.com" -> Regex("/qishui/song/[1-9][0-9]{0,19}/?").matches(url.encodedPath)
        else -> false
    }

private fun qishuiTrackInputUrl(input: String): HttpUrl? {
    val text = input.trim()
    val candidates = sequenceOf(text) + Regex("https://[^\\s<>\\\"“”]+")
        .findAll(text).map { it.value.trimEnd('.', ',', ';', '!', '。', '，', '；', '！', '）', ')') }
    return candidates.mapNotNull { it.toHttpUrlOrNull() }.firstOrNull(::isQishuiTrackUrl)
}

/** Keep the platform's identifier as text; numeric IDs can exceed JavaScript's safe integers. */
internal fun parseQishuiTrackId(input: String): String? {
    val text = input.trim()
    if (QISHUI_TRACK_ID.matches(text)) return text
    val url = qishuiTrackInputUrl(text) ?: return null
    val id = when (url.host) {
        "music.douyin.com" -> url.queryParameter("track_id")
        "www.douyin.com", "douyin.com" -> url.pathSegments.lastOrNull { it.isNotBlank() }
        else -> null
    }
    return id?.takeIf { QISHUI_TRACK_ID.matches(it) }
}

internal data class QishuiTrackDetails(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val coverUrl: String
)

/** Read public song metadata only; the page's audio URLs and playback permissions stay untouched. */
internal fun parseQishuiTrackPage(html: String, expectedId: String): QishuiTrackDetails? = runCatching {
    val start = Regex("\\b_ROUTER_DATA\\s*=\\s*").find(html)?.range?.last?.plus(1) ?: return null
    val root = JSONTokener(html.substring(start)).nextValue() as? JSONObject ?: return null
    val page = root.optJSONObject("loaderData")?.optJSONObject("track_page") ?: return null
    if (page.optString("track_id") != expectedId) return null
    val audio = page.optJSONObject("audioWithLyricsOption") ?: return null
    val track = audio.optJSONObject("trackInfo") ?: return null
    if (track.optString("id") != expectedId) return null
    val artists = track.optJSONArray("artists")
    val artist = if (artists == null) "" else (0 until artists.length())
        .map { artists.optJSONObject(it)?.optString("name").orEmpty() }.filter { it.isNotBlank() }.joinToString("、")
    QishuiTrackDetails(
        title = track.optString("name").ifBlank { audio.optString("trackName") },
        artist = artist.ifBlank { audio.optString("artistName") },
        album = track.optJSONObject("album")?.optString("name").orEmpty(),
        durationMs = track.optLong("duration").coerceAtLeast(0L),
        coverUrl = audio.optString("coverURL").takeIf { it.toHttpUrlOrNull()?.isHttps == true }.orEmpty()
    )
}.getOrNull()

private const val MAX_QISHUI_PAGE_BYTES = 2_000_000L

class LxOnlineService internal constructor(
    private val context: Context,
    httpClient: OkHttpClient? = null,
    private val importedSourceResolver: (suspend (LxOnlineSong, String) -> String?)?
) {
    constructor(context: Context) : this(context, null, null)
    private val client = httpClient ?: OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(AppNetworkLoggingInterceptor("LxNetwork"))
        .build()
    private val sourceHttpClient = client.newBuilder()
        .requireHttpsRequests()
        .build()
    private val qishuiPageClient = client.newBuilder()
        .followRedirects(false)
        .followSslRedirects(false)
        .requireHttpsRequests()
        .build()

    suspend fun importSource(url: String): Pair<String, String> = withContext(Dispatchers.IO) {
        val secureUrl = url.requireHttpsUrl("LX source")
        val request = Request.Builder()
            .url(secureUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        sourceHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.lx_service_import_failed_http, response.code))
            require(response.request.url.isHttps) { "LX source redirects must remain HTTPS" }
            val responseText = response.body?.byteStream()?.use { it.readLxSourceText() }.orEmpty()
            val script = unwrapImportedSource(secureUrl, responseText)
            // Validate downloaded sources with the same QuickJS runtime used for playback.
            // This catches encrypted/obfuscated sources that fail during initialization instead
            // of accepting them and surfacing an opaque error only when a song is played.
            importSourceScript(script, allowRuntimeInspect = true)
        }
    }

    fun importSourceScript(script: String, allowRuntimeInspect: Boolean = true): Pair<String, String> {
        val normalized = script.trim()
        if (looksLikeHtmlDocument(normalized)) {
            error(context.getString(R.string.lx_service_source_html_page))
        }
        if (normalized.length !in 50..9_000_000) error(context.getString(R.string.lx_service_source_script_abnormal))
        val name = extractSourceName(normalized)
        if (allowRuntimeInspect) {
            LxUserApiRuntime(context, sourceHttpClient).use { runtime ->
                runtime.load(normalized, normalized.hashCode().toString(), name, "")
                    ?: error(context.getString(R.string.lx_service_source_init_failed))
            }
        }
        return name to normalized
    }

    /** Read the initialized contract, including obfuscated scripts, rather than guessing names. */
    suspend fun supportedSources(config: LxSourceConfig): Set<String> = withContext(Dispatchers.IO) {
        LxUserApiRuntime(context, sourceHttpClient).use { runtime ->
            val info = runtime.load(config.script, config.id, config.name, "")
                ?: error("LX source did not declare capabilities")
            val sources = info.optJSONObject("sources") ?: return@withContext emptySet()
            sources.keys().asSequence().filter { key ->
                val actions = sources.optJSONObject(key)?.optJSONArray("actions")
                actions != null && (0 until actions.length()).any { actions.optString(it) == "musicUrl" }
            }.toSet()
        }
    }

    suspend fun search(
        keyword: String,
        sourceConfig: LxSourceConfig?,
        page: Int = 1,
        platform: LxSearchPlatform = LxSearchPlatform.Kuwo,
        declaredSources: Set<String>? = null
    ): List<LxOnlineSong> = withContext(Dispatchers.IO) {
        if (sourceConfig == null) error(context.getString(R.string.lx_service_select_source_first))
        when (platform) {
            LxSearchPlatform.Kuwo -> searchKuwo(keyword, page)
            LxSearchPlatform.Netease -> searchNetease(keyword, page)
            LxSearchPlatform.QQ -> searchQQ(keyword, page)
            LxSearchPlatform.Kugou -> searchKugou(keyword, page)
            LxSearchPlatform.Migu -> searchMigu(keyword, page)
            LxSearchPlatform.Qishui -> if (page == 1) {
                val sourceKey = platform.declaredSourceKey(declaredSources.orEmpty()) ?: platform.source
                listOf(qishuiTrack(keyword, sourceKey))
            } else emptyList()
        }
    }

    private fun qishuiTrack(input: String, sourceKey: String): LxOnlineSong {
        val suppliedId = parseQishuiTrackId(input)
        val shortLink = qishuiTrackInputUrl(input)?.takeIf { it.host == "qishui.douyin.com" }
        if (suppliedId == null && shortLink == null) error(context.getString(R.string.lx_qishui_invalid_track))
        val page = if (shortLink != null) readQishuiSharePage(shortLink) else runCatching {
            readQishuiSharePage(HttpUrl.Builder().scheme("https").host("music.douyin.com")
                .addPathSegments("qishui/share/track").addQueryParameter("track_id", suppliedId).build())
        }.getOrNull()
        val id = suppliedId ?: page?.first?.toString()?.let(::parseQishuiTrackId)
            ?: error(context.getString(R.string.lx_qishui_link_failed))
        val details = page?.second?.let { parseQishuiTrackPage(it, id) }
        val title = details?.title?.takeIf { it.isNotBlank() } ?: context.getString(R.string.lx_qishui_track_title, id)
        val cover = details?.coverUrl.orEmpty()
        return LxOnlineSong(
            song = Song(
                id = "lx_qs_$id".hashCode().toLong(),
                title = title,
                artist = details?.artist?.takeIf { it.isNotBlank() } ?: context.getString(R.string.player_unknown_artist),
                album = details?.album?.takeIf { it.isNotBlank() } ?: context.getString(R.string.player_unknown_album),
                albumId = 0L,
                duration = details?.durationMs ?: 0L,
                path = "",
                fileName = "$title.mp3",
                mimeType = "audio/mpeg",
                coverUrl = cover,
                onlineSource = sourceKey,
                onlineId = id
            ),
            source = sourceKey,
            songmid = id,
            quality = "320k",
            coverUrl = cover
        )
    }

    private fun readQishuiSharePage(initialUrl: HttpUrl): Pair<HttpUrl, String> {
        var url = initialUrl
        repeat(5) {
            if (!isQishuiTrackUrl(url)) error(context.getString(R.string.lx_qishui_link_failed))
            val page = qishuiPageClient.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
                .execute().use { response ->
                    if (response.code in listOf(301, 302, 303, 307, 308)) {
                        url = response.header("Location")?.let { url.resolve(it) }
                            ?: error(context.getString(R.string.lx_qishui_link_failed))
                        null
                    } else {
                        if (!response.isSuccessful) error(context.getString(R.string.lx_service_search_failed_http, response.code))
                        url to response.body?.byteStream()?.use { it.readUtf8Bounded(MAX_QISHUI_PAGE_BYTES) }.orEmpty()
                    }
                }
            if (page != null) return page
        }
        error(context.getString(R.string.lx_qishui_link_failed))
    }

    private fun searchKuwo(keyword: String, page: Int): List<LxOnlineSong> {
        val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
        val url = "http://search.kuwo.cn/r.s?client=kt&all=$encoded&pn=${(page - 1).coerceAtLeast(0)}&rn=30" +
            "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1&newver=1" +
            "&ft=music&cluster=0&strategy=2012&encoding=utf8&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.lx_service_search_failed_http, response.code))
            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            val list = root.optJSONArray("abslist") ?: return@use emptyList()
            List(list.length()) { index ->
                val item = list.getJSONObject(index)
                val mid = item.optString("MUSICRID").removePrefix("MUSIC_").ifBlank { item.optString("DC_TARGETID") }
                val title = decodeHtml(item.optString("SONGNAME"))
                val artist = decodeHtml(item.optString("ARTIST")).replace("&", "、")
                val album = decodeHtml(item.optString("ALBUM"))
                val durationMs = item.optLong("DURATION", 0L) * 1000L
                val id = "lx_kw_$mid".hashCode().toLong()
                val coverUrl = buildKuwoCoverUrl(item.optString("web_albumpic_short"))
                LxOnlineSong(
                    song = Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = 0L,
                        duration = durationMs,
                        path = "",
                        fileName = "$title-$artist.mp3",
                        mimeType = "audio/mpeg",
                        coverUrl = coverUrl,
                        onlineSource = "kw",
                        onlineId = mid
                    ),
                    source = "kw",
                    songmid = mid,
                    quality = pickQuality(item.optString("N_MINFO")),
                    coverUrl = coverUrl
                )
            }.filter { it.songmid.isNotBlank() && it.song.title.isNotBlank() }
        }
    }

    private fun searchNetease(keyword: String, page: Int): List<LxOnlineSong> {
        val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
        val offset = (page - 1).coerceAtLeast(0) * 30
        val url = "https://music.163.com/api/search/get/web?csrf_token=&hlpretag=&hlposttag=&s=$encoded&type=1&offset=$offset&total=true&limit=30"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.lx_service_search_failed_http, response.code))
            val root = JSONObject(response.body?.string().orEmpty())
            val songs = root.optJSONObject("result")?.optJSONArray("songs") ?: return@use emptyList()
            val parsed = List(songs.length()) { index ->
                val item = songs.getJSONObject(index)
                val idValue = item.optLong("id", 0L).takeIf { it > 0L }?.toString().orEmpty()
                val title = decodeHtml(item.optString("name"))
                val artists = item.optJSONArray("artists")
                val artist = if (artists != null) {
                    List(artists.length()) { artistIndex ->
                        decodeHtml(artists.optJSONObject(artistIndex)?.optString("name").orEmpty())
                    }.filter { it.isNotBlank() }.joinToString("、")
                } else {
                    ""
                }
                val album = item.optJSONObject("album")
                val albumName = decodeHtml(album?.optString("name").orEmpty())
                val coverUrl = album?.optString("picUrl").orEmpty()
                val durationMs = item.optLong("duration", 0L)
                val id = "lx_wy_$idValue".hashCode().toLong()
                LxOnlineSong(
                    song = Song(
                        id = id,
                        title = title,
                        artist = artist,
                        album = albumName,
                        albumId = 0L,
                        duration = durationMs,
                        path = "",
                        fileName = "$title-$artist.mp3",
                        mimeType = "audio/mpeg",
                        coverUrl = coverUrl,
                        onlineSource = "wy",
                        onlineId = idValue
                    ),
                    source = "wy",
                    songmid = idValue,
                    quality = "128k",
                    coverUrl = coverUrl
                )
            }.filter { it.songmid.isNotBlank() && it.song.title.isNotBlank() }
            val missingCoverIds = parsed.filter { it.coverUrl.isBlank() }.map { it.songmid }
            if (missingCoverIds.isEmpty()) return@use parsed
            val coverById = loadNeteaseCoverUrls(missingCoverIds)
            parsed.map { onlineSong ->
                val coverUrl = onlineSong.coverUrl.ifBlank { coverById[onlineSong.songmid].orEmpty() }
                onlineSong.copy(
                    song = onlineSong.song.copy(coverUrl = coverUrl),
                    coverUrl = coverUrl
                )
            }
        }
    }

    private fun loadNeteaseCoverUrls(songIds: List<String>): Map<String, String> {
        if (songIds.isEmpty()) return emptyMap()
        val encodedIds = URLEncoder.encode(JSONArray(songIds).toString(), "UTF-8")
        val request = Request.Builder()
            .url("https://music.163.com/api/song/detail/?ids=$encodedIds")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyMap()
                val details = JSONObject(response.body?.string().orEmpty()).optJSONArray("songs")
                    ?: return@use emptyMap()
                buildMap {
                    repeat(details.length()) { index ->
                        val item = details.optJSONObject(index) ?: return@repeat
                        val id = item.optLong("id", 0L).takeIf { it > 0L }?.toString() ?: return@repeat
                        val coverUrl = item.optJSONObject("album")?.optString("picUrl").orEmpty()
                        if (coverUrl.isNotBlank()) put(id, coverUrl)
                    }
                }
            }
        }.getOrDefault(emptyMap())
    }

    private fun searchQQ(keyword: String, page: Int): List<LxOnlineSong> {
        val body = createQqSearchBody(keyword, page).toString()
        val signedRequest = Request.Builder()
            .url("https://u.y.qq.com/cgi-bin/musics.fcg?sign=${createQqSearchSignature(body)}")
            .header("User-Agent", "QQMusic 14090508(android 12)")
            .header("Referer", "https://y.qq.com/")
            .post(body.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        val songs = try {
            requestQqSearchSongs(signedRequest, signed = true)
        } catch (error: Exception) {
            if (error is CancellationException) throw error
            // Compatibility for regions where the public desktop search endpoint is unavailable.
            // This only searches metadata; it never substitutes the selected playback source.
            try {
                requestQqSearchSongs(legacyQqSearchRequest(keyword, page), signed = false)
            } catch (fallbackError: Exception) {
                fallbackError.addSuppressed(error)
                throw fallbackError
            }
        }
        return parseQqSearchSongs(songs)
    }

    private fun legacyQqSearchRequest(keyword: String, page: Int): Request {
        val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp?p=${page.coerceAtLeast(1)}" +
            "&n=30&w=$encoded&format=json&new_json=1"
        return Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://y.qq.com/")
            .build()
    }

    private fun requestQqSearchSongs(request: Request, signed: Boolean): JSONArray =
        sourceHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.lx_service_search_failed_http, response.code))
            val root = JSONObject(response.body?.string().orEmpty())
            if (root.optInt("code", -1) != 0) error(context.getString(R.string.lx_online_search_failed))
            val data = if (signed) {
                val result = root.optJSONObject("music.search.SearchCgiService") ?: root.optJSONObject("req")
                    ?: error(context.getString(R.string.lx_online_search_failed))
                if (result.optInt("code", -1) != 0) error(context.getString(R.string.lx_online_search_failed))
                result.optJSONObject("data")?.optJSONObject("body")
            } else root.optJSONObject("data")
            data?.optJSONObject("song")?.optJSONArray("list")
                ?: error(context.getString(R.string.lx_online_search_failed))
        }

    private fun parseQqSearchSongs(songs: JSONArray): List<LxOnlineSong> =
            List(songs.length()) { index ->
                val item = songs.getJSONObject(index)
                val mid = item.optString("mid").ifBlank { item.optString("songmid") }
                val title = decodeHtml(item.optString("title").ifBlank { item.optString("name") }.ifBlank { item.optString("songname") })
                val singers = item.optJSONArray("singer")
                val artist = if (singers != null) {
                    List(singers.length()) { singerIndex ->
                        decodeHtml(singers.optJSONObject(singerIndex)?.optString("name").orEmpty())
                    }.filter { it.isNotBlank() }.joinToString("、")
                } else {
                    ""
                }
                val album = item.optJSONObject("album")
                val albumName = decodeHtml(album?.optString("name").orEmpty())
                val albumMid = album?.optString("mid").orEmpty()
                val coverUrl = albumMid.takeIf { it.isNotBlank() }
                    ?.let { "https://y.gtimg.cn/music/photo_new/T002R500x500M000$it.jpg" }
                    ?: singers?.optJSONObject(0)?.optString("mid")?.takeIf { it.isNotBlank() }
                        ?.let { "https://y.gtimg.cn/music/photo_new/T001R500x500M000$it.jpg" }
                    .orEmpty()
                val file = item.optJSONObject("file")
                val qualities = buildList {
                    if ((file?.let { it.optLong("size_128mp3", it.optLong("size_128")) } ?: 0L) > 0L) add(LxOnlineQuality("128k"))
                    if ((file?.let { it.optLong("size_320mp3", it.optLong("size_320")) } ?: 0L) > 0L) add(LxOnlineQuality("320k"))
                    if ((file?.optLong("size_flac") ?: 0L) > 0L) add(LxOnlineQuality("flac"))
                    if ((file?.optLong("size_hires") ?: 0L) > 0L) add(LxOnlineQuality("flac24bit"))
                }.ifEmpty { listOf(LxOnlineQuality("128k")) }
                val quality = qualities.bestQuality()
                val songId = item.optLong("id", 0L).takeIf { it > 0L }?.toString().orEmpty()
                val strMediaMid = file?.optString("media_mid").orEmpty().ifBlank { mid }
                LxOnlineSong(
                    song = Song(
                        id = "lx_tx_$mid".hashCode().toLong(),
                        title = title,
                        artist = artist,
                        album = albumName,
                        albumId = 0L,
                        duration = item.optLong("interval", 0L) * 1000L,
                        path = "",
                        fileName = "$title-$artist.mp3",
                        mimeType = "audio/mpeg",
                        coverUrl = coverUrl,
                        onlineSource = LxSearchPlatform.QQ.source,
                        onlineId = mid
                    ),
                    source = LxSearchPlatform.QQ.source,
                    songmid = mid,
                    quality = quality,
                    coverUrl = coverUrl,
                    qualities = qualities,
                    sourceMetadata = buildMap {
                        if (strMediaMid.isNotBlank()) put("strMediaMid", strMediaMid)
                        if (albumMid.isNotBlank()) put("albumMid", albumMid)
                        if (songId.isNotBlank()) put("songId", songId)
                    }
                )
            }.filter { it.songmid.isNotBlank() && it.song.title.isNotBlank() }

    private fun searchKugou(keyword: String, page: Int): List<LxOnlineSong> {
        val encoded = URLEncoder.encode(keyword.trim(), "UTF-8")
        val url = "https://songsearch.kugou.com/song_search_v2?keyword=$encoded" +
            "&page=${page.coerceAtLeast(1)}&pagesize=30&platform=WebFilter&userid=-1&clientver=2000"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://www.kugou.com/")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.lx_service_search_failed_http, response.code))
            val songs = JSONObject(response.body?.string().orEmpty())
                .optJSONObject("data")
                ?.optJSONArray("lists")
                ?: return@use emptyList()
            List(songs.length()) { index ->
                val item = songs.getJSONObject(index)
                val hash = item.optString("FileHash").ifBlank { item.optString("Hash") }
                val mid = item.optString("Audioid").ifBlank { item.optString("AudioID") }
                val title = decodeSearchMarkup(item.optString("SongName").ifBlank { item.optString("FileName") })
                val artist = decodeSearchMarkup(item.optString("SingerName"))
                val album = decodeSearchMarkup(item.optString("AlbumName"))
                val albumId = item.optString("AlbumID")
                val coverUrl = item.optString("Image")
                    .replace("{size}", "500")
                    .replace("http://", "https://")
                val qualities = buildList {
                    if (item.optLong("FileSize") > 0L && hash.isNotBlank()) {
                        add(LxOnlineQuality("128k", hash))
                    }
                    val highHash = item.optString("HQFileHash")
                    if (item.optLong("HQFileSize") > 0L && highHash.isNotBlank()) {
                        add(LxOnlineQuality("320k", highHash))
                    }
                    val losslessHash = item.optString("SQFileHash")
                    if (item.optLong("SQFileSize") > 0L && losslessHash.isNotBlank()) {
                        add(LxOnlineQuality("flac", losslessHash))
                    }
                    val hiResHash = item.optString("ResFileHash")
                    if (item.optLong("ResFileSize") > 0L && hiResHash.isNotBlank()) {
                        add(LxOnlineQuality("flac24bit", hiResHash))
                    }
                }.ifEmpty { listOf(LxOnlineQuality("128k", hash)) }
                val quality = qualities.bestQuality()
                LxOnlineSong(
                    song = Song(
                        id = "lx_kg_${mid}_$hash".hashCode().toLong(),
                        title = title,
                        artist = artist,
                        album = album,
                        albumId = 0L,
                        duration = item.optLong("Duration", 0L) * 1000L,
                        path = "",
                        fileName = "$title-$artist.mp3",
                        mimeType = "audio/mpeg",
                        coverUrl = coverUrl,
                        onlineSource = LxSearchPlatform.Kugou.source,
                        onlineId = mid
                    ),
                    source = LxSearchPlatform.Kugou.source,
                    songmid = mid,
                    quality = quality,
                    coverUrl = coverUrl,
                    qualities = qualities,
                    sourceMetadata = buildMap {
                        if (hash.isNotBlank()) put("hash", hash)
                        if (albumId.isNotBlank()) put("albumId", albumId)
                    }
                )
            }.filter {
                it.songmid.isNotBlank() &&
                    it.sourceMetadata["hash"].orEmpty().isNotBlank() &&
                    it.song.title.isNotBlank()
            }
        }
    }

    private fun searchMigu(keyword: String, page: Int): List<LxOnlineSong> {
        val normalizedKeyword = keyword.trim()
        val timestamp = System.currentTimeMillis().toString()
        val deviceId = MIGU_DEVICE_ID
        val signature = createMiguSearchSignature(normalizedKeyword, timestamp, deviceId)
        val encoded = URLEncoder.encode(normalizedKeyword, "UTF-8")
        val url = "https://jadeite.migu.cn/music_search/v3/search/searchAll?isCorrect=0&isCopyright=1" +
            "&searchSwitch=%7B%22song%22%3A1%2C%22album%22%3A0%2C%22singer%22%3A0%2C%22tagSong%22%3A1%2C%22mvSong%22%3A0%2C%22bestShow%22%3A1%2C%22songlist%22%3A0%2C%22lyricSong%22%3A0%7D" +
            "&pageSize=30&text=$encoded&pageNo=${page.coerceAtLeast(1)}&sort=0&sid=USS"
        val request = Request.Builder()
            .url(url)
            .header("uiVersion", "A_music_3.6.1")
            .header("deviceId", deviceId)
            .header("timestamp", timestamp)
            .header("sign", signature)
            .header("channel", "0146921")
            .header("User-Agent", MIGU_USER_AGENT)
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.lx_service_search_failed_http, response.code))
            val root = JSONObject(response.body?.string().orEmpty())
            if (root.optString("code") != "000000") {
                error(root.optString("info").ifBlank { context.getString(R.string.lx_online_search_failed) })
            }
            val groups = root.optJSONObject("songResultData")?.optJSONArray("resultList")
                ?: return@use emptyList()
            val seenCopyrightIds = mutableSetOf<String>()
            buildList {
                repeat(groups.length()) { groupIndex ->
                    val group = groups.optJSONArray(groupIndex) ?: return@repeat
                    repeat(group.length()) { itemIndex ->
                        val item = group.optJSONObject(itemIndex) ?: return@repeat
                        val songmid = item.optString("songId")
                        val copyrightId = item.optString("copyrightId")
                        if (songmid.isBlank() || copyrightId.isBlank() || !seenCopyrightIds.add(copyrightId)) {
                            return@repeat
                        }
                        val title = decodeHtml(item.optString("name").ifBlank { item.optString("songName") })
                        if (title.isBlank()) return@repeat
                        val singers = item.optJSONArray("singerList")
                        val artist = buildList {
                            if (singers != null) repeat(singers.length()) { singerIndex ->
                                singers.optJSONObject(singerIndex)?.optString("name")
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { singerName -> add(singerName) }
                            }
                        }.joinToString("、")
                        val album = decodeHtml(item.optString("album"))
                        val albumId = item.optString("albumId")
                        val rawCover = item.optString("img3")
                            .ifBlank { item.optString("img2") }
                            .ifBlank { item.optString("img1") }
                        val coverUrl = when {
                            rawCover.startsWith("https://") -> rawCover
                            rawCover.startsWith("http://") -> rawCover.replaceFirst("http://", "https://")
                            rawCover.isNotBlank() -> "https://d.musicapp.migu.cn$rawCover"
                            else -> ""
                        }
                        val qualities = buildList {
                            val formats = item.optJSONArray("audioFormats")
                            if (formats != null) repeat(formats.length()) { formatIndex ->
                                when (formats.optJSONObject(formatIndex)?.optString("formatType")) {
                                    "PQ" -> add(LxOnlineQuality("128k"))
                                    "HQ" -> add(LxOnlineQuality("320k"))
                                    "SQ" -> add(LxOnlineQuality("flac"))
                                    "ZQ24" -> add(LxOnlineQuality("flac24bit"))
                                }
                            }
                        }.distinctBy { it.type }.ifEmpty { listOf(LxOnlineQuality("128k")) }
                        val quality = qualities.bestQuality()
                        add(
                            LxOnlineSong(
                                song = Song(
                                    id = "lx_mg_$copyrightId".hashCode().toLong(),
                                    title = title,
                                    artist = artist,
                                    album = album,
                                    albumId = 0L,
                                    duration = item.optLong("duration", 0L) * 1000L,
                                    path = "",
                                    fileName = "$title-$artist.mp3",
                                    mimeType = "audio/mpeg",
                                    coverUrl = coverUrl,
                                    onlineSource = LxSearchPlatform.Migu.source,
                                    onlineId = copyrightId
                                ),
                                source = LxSearchPlatform.Migu.source,
                                songmid = songmid,
                                quality = quality,
                                coverUrl = coverUrl,
                                qualities = qualities,
                                sourceMetadata = buildMap {
                                    put("copyrightId", copyrightId)
                                    if (albumId.isNotBlank()) put("albumId", albumId)
                                    item.optString("lrcUrl").takeIf { it.isNotBlank() }?.let { put("lrcUrl", it) }
                                    item.optString("mrcurl").takeIf { it.isNotBlank() }?.let { put("mrcUrl", it) }
                                    item.optString("trcUrl").takeIf { it.isNotBlank() }?.let { put("trcUrl", it) }
                                }
                            )
                        )
                    }
                }
            }
        }
    }

    suspend fun resolvePlayableSong(item: LxOnlineSong, sourceScript: String = ""): Song = withContext(Dispatchers.IO) {
        val importedResolution = runCatching {
            if (importedSourceResolver != null) importedSourceResolver.invoke(item, sourceScript)
            else resolveByImportedSource(item, sourceScript)
        }
        importedResolution.exceptionOrNull()?.let { error ->
            // An explicitly selected source must report its own failure. Quietly switching to
            // a built-in URL can substitute a preview and hide the source's actual error.
            if (error is CancellationException || sourceScript.isNotBlank()) throw error
        }
        if (sourceScript.isNotBlank() && importedResolution.getOrNull().isNullOrBlank()) {
            error(context.getString(R.string.lx_service_source_no_playback_url))
        }
        importedResolution.getOrNull()
            ?.let { playableUrl ->
            val extension = playableUrl.substringBefore('?')
                .substringAfterLast('.', missingDelimiterValue = "")
                .takeIf { it.length in 2..5 }
                ?: if (item.quality.startsWith("flac")) "flac" else "mp3"
            return@withContext item.song.copy(path = playableUrl, fileName = "${item.song.title}.$extension")
        }

        if (item.source == LxSearchPlatform.Netease.source) {
            val url = "https://music.163.com/song/media/outer/url?id=${item.songmid}.mp3"
            return@withContext item.song.copy(path = url, fileName = "${item.song.title}.mp3")
        }

        if (item.source != LxSearchPlatform.Kuwo.source) {
            importedResolution.exceptionOrNull()?.let { throw it }
            error(context.getString(R.string.lx_service_resolve_url_failed))
        }

        val format = when (item.quality) {
            "flac", "flac24bit" -> "flac"
            else -> "mp3"
        }
        val url = "http://antiserver.kuwo.cn/anti.s?type=convert_url&rid=MUSIC_${item.songmid}&format=$format&response=url"
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .build()
        val playableUrl = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.lx_service_resolve_url_failed_http, response.code))
            response.body?.string()?.trim().orEmpty()
        }
        if (!playableUrl.startsWith("http")) error(context.getString(R.string.lx_service_resolve_url_failed))
        item.song.copy(path = playableUrl, fileName = "${item.song.title}.${if (format == "flac") "flac" else "mp3"}")
    }

    private suspend fun resolveByImportedSource(item: LxOnlineSong, sourceScript: String): String? {
        if (sourceScript.isNotBlank()) {
            runCatching {
                val requestedQuality = com.ella.music.data.SettingsManager.getInstance(context).onlinePlaybackQuality.first()
                return LxUserApiRuntime(context, sourceHttpClient).use { runtime ->
                    runtime.requestMusicUrl(item, sourceScript, extractSourceName(sourceScript), requestedQuality)
                }
            }.onFailure { quickJsError ->
                if (quickJsError is CancellationException) throw quickJsError
                val config = extractRenderApiConfig(sourceScript)
                if (config == null) throw quickJsError
            }
        }
        val config = extractRenderApiConfig(sourceScript) ?: return null
        val requestedQuality = com.ella.music.data.SettingsManager.getInstance(context).onlinePlaybackQuality.first()
        val quality = config.qualitys[item.source].orEmpty().let { available ->
            com.ella.music.data.OnlinePlaybackQuality.lxTier(requestedQuality, available)
        } ?: config.bestQuality(item.source, item.quality)
        val sourceId = when (item.source) {
            LxSearchPlatform.Kugou.source -> item.sourceMetadata["hash"]
            LxSearchPlatform.Migu.source -> item.sourceMetadata["copyrightId"]
            else -> null
        }.orEmpty().ifBlank { item.songmid }
        val url = "${config.apiUrl.trimEnd('/')}/url/${item.source}/$sourceId/$quality"
            .requireHttpsUrl("LX source API")
        val request = Request.Builder()
            .url(url)
            .header("Content-Type", "application/json")
            .header("User-Agent", "lx-music-mobile/1.0.0")
            .header("X-Request-Key", config.apiKey)
            .build()
        return sourceHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.lx_service_source_resolve_failed_http, response.code))
            val body = response.body?.string().orEmpty()
            val root = JSONObject(body)
            when (root.optInt("code", -1)) {
                0 -> root.optString("url").takeIf { it.startsWith("http") } ?: error(context.getString(R.string.lx_service_source_no_playback_url))
                1 -> error(context.getString(R.string.lx_service_source_resolve_ip_restricted))
                2 -> error(context.getString(R.string.lx_service_source_resolve_url_fetch_failed))
                4 -> error(context.getString(R.string.lx_service_source_resolve_internal_error))
                5 -> error(context.getString(R.string.lx_service_source_resolve_too_frequent))
                6 -> error(context.getString(R.string.lx_service_source_resolve_param_error))
                else -> error(root.optString("msg").ifBlank { context.getString(R.string.lx_service_source_resolve_failed) })
            }
        }
    }

    private fun extractSourceName(script: String): String {
        val currentInfoName = Regex("""name\s*:\s*['"]([^'"]+)['"]""").find(script)?.groupValues?.getOrNull(1)
        val commentName = Regex("""@name\s+(.+)""").find(script)?.groupValues?.getOrNull(1)?.trim()
        return currentInfoName ?: commentName ?: context.getString(R.string.lx_service_default_source_name)
    }

    private fun pickQuality(raw: String): String {
        return when {
            "bitrate:4000" in raw -> "flac24bit"
            "bitrate:2000" in raw -> "flac"
            "bitrate:320" in raw -> "320k"
            else -> "128k"
        }
    }

    private fun buildKuwoCoverUrl(path: String): String {
        val normalized = path.trim()
        return when {
            normalized.startsWith("http://") || normalized.startsWith("https://") -> normalized
            normalized.isNotBlank() -> {
                val highResPath = normalized.replace(Regex("""^\d+/"""), "500/")
                "https://img1.kuwo.cn/star/albumcover/$highResPath"
            }
            else -> ""
        }
    }

    private fun extractRenderApiConfig(script: String): RenderApiConfig? {
        if (script.isBlank() || "/url/" !in script || "X-Request-Key" !in script) return null
        val apiUrl = Regex("""API_URL\s*=\s*['"]([^'"]+)['"]""").find(script)
            ?.groupValues
            ?.getOrNull(1)
            ?.trimEnd('/')
            ?: return null
        val apiKey = Regex("""API_KEY\s*=\s*['"]([^'"]+)['"]""").find(script)
            ?.groupValues
            ?.getOrNull(1)
            ?: return null
        val qualitys = mutableMapOf<String, List<String>>()
        Regex("""(\w+)\s*:\s*\[([^\]]+)]""").findAll(script.substringAfter("MUSIC_QUALITY", script)).forEach { match ->
            val source = match.groupValues[1]
            val values = Regex("""['"]([^'"]+)['"]""").findAll(match.groupValues[2]).map { it.groupValues[1] }.toList()
            if (values.isNotEmpty()) qualitys[source] = values
        }
        return RenderApiConfig(apiUrl = apiUrl, apiKey = apiKey, qualitys = qualitys)
    }

    private data class RenderApiConfig(
        val apiUrl: String,
        val apiKey: String,
        val qualitys: Map<String, List<String>>
    ) {
        fun bestQuality(source: String, requested: String): String {
            val available = qualitys[source].orEmpty()
            if (available.isEmpty() || requested in available) return requested
            return when {
                "flac24bit" in available -> "flac24bit"
                "flac" in available -> "flac"
                "320k" in available -> "320k"
                "128k" in available -> "128k"
                else -> available.last()
            }
        }
    }

    private fun decodeHtml(value: String): String = value
        .replace("&amp;", "&")
        .replace("&nbsp;", " ")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .trim()

    private fun decodeSearchMarkup(value: String): String = decodeHtml(value)
        .replace(Regex("<[^>]+>"), "")
        .trim()

    private fun unwrapImportedSource(requestUrl: String, body: String): String {
        val trimmed = body.trim()
        if (looksLikeSharePage(requestUrl) && !trimmed.contains("EVENT_NAMES")) {
            val jsUrl = pickLxScriptUrl(trimmed, requestUrl)
                ?: error(context.getString(R.string.lx_service_source_share_page))
            return fetchImportedScript(jsUrl)
        }
        if (looksLikeHtmlDocument(trimmed)) {
            val jsUrl = pickLxScriptUrl(trimmed, requestUrl)
                ?: error(context.getString(R.string.lx_service_source_html_page))
            return fetchImportedScript(jsUrl)
        }
        if (looksLikeJsonDocument(trimmed)) {
            return unwrapJsonSource(requestUrl, trimmed)
        }
        return trimmed
    }

    private fun unwrapJsonSource(requestUrl: String, json: String): String {
        val parsed = runCatching { JSONTokener(json).nextValue() }.getOrNull()
            ?: return json
        if (parsed is JSONObject && isQingMusicOrNonLxCatalog(parsed)) {
            error(context.getString(R.string.lx_service_source_json_not_lx))
        }
        val embeddedScript = (parsed as? JSONObject)?.optString("script").orEmpty()
            .ifBlank { (parsed as? JSONObject)?.optString("rawScript").orEmpty() }
        if (embeddedScript.contains("EVENT_NAMES") || embeddedScript.contains("globalThis.lx")) {
            return embeddedScript
        }
        val jsUrl = pickLxScriptUrl(json, requestUrl)
            ?: collectJsonScriptUrls(parsed).firstOrNull()
            ?: error(context.getString(R.string.lx_service_source_json_not_lx))
        return fetchImportedScript(jsUrl)
    }

    private fun fetchImportedScript(jsUrl: String): String {
        val secureUrl = jsUrl.requireHttpsUrl("LX script")
        val request = Request.Builder()
            .url(secureUrl)
            .header("User-Agent", USER_AGENT)
            .build()
        return sourceHttpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error(context.getString(R.string.lx_service_import_failed_http, response.code))
            require(response.request.url.isHttps) { "LX script redirects must remain HTTPS" }
            val script = response.body?.byteStream()?.use { it.readLxSourceText() }.orEmpty().trim()
            if (looksLikeHtmlDocument(script) ||
                (looksLikeJsonDocument(script) && "EVENT_NAMES" !in script && "globalThis.lx" !in script)
            ) {
                error(context.getString(R.string.lx_service_source_html_page))
            }
            script
        }
    }

    suspend fun fetchLyrics(item: LxOnlineSong, sourceScript: String = ""): String? = withContext(Dispatchers.IO) {
        if (sourceScript.isNotBlank()) {
            runCatching {
                LxUserApiRuntime(context, sourceHttpClient).use { runtime ->
                    runtime.requestLyric(item, sourceScript, extractSourceName(sourceScript))
                }
            }.getOrNull()?.takeIf { it.isNotBlank() }?.let { return@withContext it }
        }
        when (item.source) {
            LxSearchPlatform.Kuwo.source -> fetchKuwoLyric(item.songmid)
            LxSearchPlatform.Netease.source -> fetchNeteaseLyric(item.songmid)
            else -> null
        }
    }

    suspend fun downloadWithMetadata(
        item: LxOnlineSong,
        sourceScript: String,
        targetFile: java.io.File
    ): File = withContext(Dispatchers.IO) {
        val playable = resolvePlayableSong(item, sourceScript)
        val downloaded = downloadAudioToFile(playable, targetFile)
        val actualFile = File(downloaded.path)
        val lyrics = runCatching { fetchLyrics(item, sourceScript) }.getOrNull()
        val coverBytes = item.coverUrl.takeIf { it.startsWith("http") }?.let { url ->
            runCatching {
                client.newCall(Request.Builder().url(url).header("User-Agent", USER_AGENT).build())
                    .execute()
                    .use { response ->
                        if (!response.isSuccessful) null
                        else response.body?.bytes()?.takeIf { it.isNotEmpty() }
                    }
            }.getOrNull()
        }
        val writer = com.ella.music.data.metadata.AudioTagRepository(
            com.ella.music.data.metadata.LyricoAudioTagReaderWriter(context)
        )
        writer.writeTags(
            actualFile.absolutePath,
            com.ella.music.data.metadata.AudioTagInfo(
                title = item.song.title,
                artist = item.song.artist,
                album = item.song.album,
                lyrics = lyrics?.takeIf { it.isNotBlank() }
            )
        )
        if (coverBytes != null) {
            writer.writeEmbeddedCover(
                actualFile.absolutePath,
                com.ella.music.data.metadata.AudioCoverInfo(coverBytes, "image/jpeg")
            )
        }
        android.media.MediaScannerConnection.scanFile(context, arrayOf(actualFile.path), arrayOf(downloaded.mimeType), null)
        actualFile
    }

    internal suspend fun downloadAudioToFile(playable: Song, targetFile: File): Song = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(playable.path)
            .header("User-Agent", USER_AGENT)
            .build()
        val call = client.newCall(request)
        val cancellationWatcher = launch(start = CoroutineStart.UNDISPATCHED) {
            try { awaitCancellation() } finally { call.cancel() }
        }
        var stage: File? = null
        var destination: File? = null
        try {
            runInterruptible { call.execute() }.use { response ->
                if (!response.isSuccessful) error(context.getString(R.string.lx_service_resolve_url_failed_http, response.code))
                val body = response.body ?: throw IOException(context.getString(R.string.lx_online_download_failed))
                val completeRange = if (response.code == 206) {
                    val range = Regex("bytes\\s+0-(\\d+)/(\\d+)", RegexOption.IGNORE_CASE)
                        .matchEntire(response.header("Content-Range").orEmpty().trim())
                    val end = range?.groupValues?.get(1)?.toLongOrNull()
                    val total = range?.groupValues?.get(2)?.toLongOrNull()
                    if (total == null || total <= 0L || end != total - 1L) {
                        throw IOException(context.getString(R.string.lx_online_download_failed))
                    }
                    total
                } else null
                val source = body.source()
                runInterruptible { source.request(512L) }
                val prefix = source.peek().readByteArray(minOf(512L, source.buffer.size))
                val textPrefix = prefix.toString(Charsets.UTF_8).trimStart { it.isWhitespace() || it == '\uFEFF' }
                if (textPrefix.startsWith("#EXTM3U", true) || textPrefix.startsWith("<!DOCTYPE", true) ||
                    textPrefix.startsWith("<html", true) || body.contentType()?.subtype?.contains("mpegurl", true) == true
                ) throw IOException(context.getString(R.string.lx_online_download_failed))
                val format = detectAudioContainerFormat(prefix, body.contentType()?.toString(), playable.fileName, playable.mimeType)
                val temporary = File.createTempFile("lx-download-", ".${format.extension}", context.cacheDir)
                stage = temporary
                var copied = 0L
                body.byteStream().use { input ->
                    temporary.outputStream().buffered().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            currentCoroutineContext().ensureActive()
                            val count = runInterruptible { input.read(buffer) }
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            copied += count
                        }
                    }
                }
                if (copied == 0L || (body.contentLength() >= 0L && copied != body.contentLength()) ||
                    (completeRange != null && copied != completeRange)
                ) throw IOException(context.getString(R.string.lx_online_download_failed))
                currentCoroutineContext().ensureActive()
                val directory = targetFile.absoluteFile.parentFile!!
                if (!directory.isDirectory && !directory.mkdirs()) throw IOException(context.getString(R.string.lx_online_download_failed))
                val stem = targetFile.nameWithoutExtension
                val desired = File(directory, "$stem.${format.extension}")
                val actual = if (desired.createNewFile()) desired else {
                    File(directory, "$stem-${UUID.randomUUID()}.${format.extension}").also {
                        if (!it.createNewFile()) throw IOException(context.getString(R.string.lx_online_download_failed))
                    }
                }
                destination = actual
                temporary.copyTo(actual, overwrite = true)
                playable.copy(path = actual.absolutePath, fileName = actual.name, mimeType = format.mimeType, fileSize = copied)
            }
        } catch (error: Exception) {
            call.cancel()
            destination?.delete()
            currentCoroutineContext().ensureActive()
            throw error
        } finally {
            stage?.delete()
            cancellationWatcher.cancel()
        }
    }

    private fun fetchKuwoLyric(songmid: String): String? {
        if (songmid.isBlank()) return null
        val request = Request.Builder()
            .url("https://www.kuwo.cn/newh5/singles/songinfoandlrc?musicId=$songmid")
            .header("User-Agent", USER_AGENT)
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val list = JSONObject(response.body?.string().orEmpty())
                    .optJSONObject("data")
                    ?.optJSONArray("lrclist") ?: return@use null
                List(list.length()) { index ->
                    val item = list.getJSONObject(index)
                    val time = item.optString("time").toDoubleOrNull() ?: 0.0
                    val minutes = (time / 60).toInt()
                    val seconds = time % 60.0
                    val text = item.optString("lineLyric").trim()
                    if (text.isBlank()) "" else String.format(java.util.Locale.US, "[%02d:%05.2f]%s", minutes, seconds, text)
                }.filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    private fun fetchNeteaseLyric(songmid: String): String? {
        if (songmid.isBlank()) return null
        val request = Request.Builder()
            .url("https://music.163.com/api/song/lyric?id=$songmid&lv=-1&tv=-1")
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .build()
        return runCatching {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val root = JSONObject(response.body?.string().orEmpty())
                val original = root.optJSONObject("lrc")?.optString("lyric").orEmpty()
                val translation = root.optJSONObject("tlyric")?.optString("lyric").orEmpty()
                listOf(original, translation).filter { it.isNotBlank() }.joinToString("\n").takeIf { it.isNotBlank() }
            }
        }.getOrNull()
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Halcyon/1.0"
        private const val MIGU_DEVICE_ID = "963B7AA0D21511ED807EE5846EC87D20"
        private const val MIGU_USER_AGENT =
            "Mozilla/5.0 (Linux; U; Android 11.0.0; zh-cn; MI 11 Build/OPR1.170623.032) " +
                "AppleWebKit/534.30 (KHTML, like Gecko) Version/4.0 Mobile Safari/534.30"
    }
}

internal fun looksLikeHtmlDocument(script: String): Boolean {
    val trimmed = script.trimStart().take(240).lowercase()
    return trimmed.startsWith("<!doctype html") ||
        trimmed.startsWith("<html") ||
        trimmed.startsWith("<head") ||
        (trimmed.startsWith("<") && ("<html" in trimmed || "<body" in trimmed || "<script" in trimmed))
}

internal fun looksLikeJsonDocument(script: String): Boolean {
    val trimmed = script.trimStart()
    return (trimmed.startsWith("{") || trimmed.startsWith("[")) && !looksLikeHtmlDocument(trimmed)
}

internal fun looksLikeSharePage(url: String): Boolean {
    val lower = url.lowercase()
    return "pan.quark.cn" in lower ||
        "alipan.com" in lower ||
        "aliyundrive.com" in lower ||
        "lanzou" in lower ||
        "/s/" in lower && ("quark" in lower || "pan." in lower)
}

internal fun isQingMusicOrNonLxCatalog(json: JSONObject): Boolean {
    val lines = json.optJSONArray("lines")
    if (lines != null && lines.length() > 0) {
        val first = lines.optJSONObject(0)
        if (first != null && (first.has("searchApi") || first.has("detailApi"))) return true
    }
    return false
}

internal fun pickLxScriptUrl(text: String, pageUrl: String = ""): String? {
    val candidates = Regex(
        """https?://[^\s"'<>\\]+?\.js(?:\?[^\s"'<>\\]*)?""",
        RegexOption.IGNORE_CASE
    ).findAll(text).map { it.value.trimEnd('.', ',', ';') }.toList()
    val relative = Regex(
        """(?:href|src)\s*=\s*['"]([^'"]+\.js(?:\?[^'"]*)?)['"]""",
        RegexOption.IGNORE_CASE
    ).findAll(text).map { it.groupValues[1] }.toList()
    val resolved = (candidates + relative.map { candidate ->
        runCatching { java.net.URI(pageUrl.ifBlank { "https://local.invalid/" }).resolve(candidate).toString() }
            .getOrDefault(candidate)
    }).distinct()
    return resolved.maxByOrNull(::lxScriptUrlScore)?.takeIf { lxScriptUrlScore(it) > 0 }
}

internal fun lxScriptUrlScore(url: String): Int {
    val lower = url.lowercase()
    if (listOf(
            "jquery", "bootstrap", "fancybox", "highlight", "analytics", "gtag",
            "disqus", "chunk", "webpack", "baomitu", "alicdn", "cloudflare",
            "comment", "main.css", "polyfill"
        ).any { it in lower }
    ) {
        return -1
    }
    if (!lower.contains(".js")) return -1
    var score = 1
    if (listOf("latest.js", "script.js", "source.js", "user-api", "render_api").any { it in lower }) score += 50
    if (listOf("juhe", "ikun", "sixyin", "huibq", "qdy", "lx-music").any { it in lower }) score += 20
    if ("raw.githubusercontent.com" in lower || "jsdelivr" in lower || "ghproxy" in lower || "github" in lower) {
        score += 10
    }
    return score
}

private fun collectJsonScriptUrls(parsed: Any): List<String> {
    val urls = mutableListOf<String>()
    fun walk(value: Any?) {
        when (value) {
            is JSONObject -> {
                val keys = value.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val child = value.opt(key)
                    if (child is String && (child.endsWith(".js", ignoreCase = true) || "http" in child && ".js" in child)) {
                        if (lxScriptUrlScore(child) > 0) urls += child
                    } else {
                        walk(child)
                    }
                }
            }
            is JSONArray -> {
                for (index in 0 until value.length()) walk(value.opt(index))
            }
            is String -> if (lxScriptUrlScore(value) > 0) urls += value
        }
    }
    walk(parsed)
    return urls.distinct()
}

private fun List<LxOnlineQuality>.bestQuality(): String = when {
    any { it.type == "flac24bit" } -> "flac24bit"
    any { it.type == "flac" } -> "flac"
    any { it.type == "320k" } -> "320k"
    else -> "128k"
}

// QQ metadata search protocol adapted from LX Music Mobile v1.9.1 (Apache-2.0),
// commit fb8480728d875fa5e0da25eebd3a26bb71723aae, musicSdk/tx/musicSearch.js and utils/crypto.js.
internal fun createQqSearchBody(
    keyword: String,
    page: Int,
    searchId: String = UUID.randomUUID().toString().replace("-", "").uppercase(Locale.ROOT) +
        ThreadLocalRandom.current().nextInt(100000).toString().padStart(5, '0')
): JSONObject = JSONObject()
    .put("comm", JSONObject()
        .put("_channelid", "0")
        .put("_os_version", "6.2.9200-2")
        .put("ct", "19")
        .put("cv", "2151")
        .put("guid", "1F70E520B2EAA7D25E11760783C53CA9")
        .put("patch", "118")
        .put("psrf_access_token_expiresAt", 0)
        .put("psrf_qqaccess_token", "")
        .put("psrf_qqopenid", "")
        .put("psrf_qqunionid", "")
        .put("tmeAppID", "qqmusic")
        .put("tmeLoginType", 0)
        .put("uin", "0")
        .put("wid", "7223299733393904640"))
    .put("music.search.SearchCgiService", JSONObject()
        .put("module", "music.search.SearchCgiService")
        .put("method", "DoSearchForQQMusicDesktop")
        .put("param", JSONObject()
            .put("grp", 1)
            .put("num_per_page", 30)
            .put("page_num", page.coerceAtLeast(1))
            .put("query", keyword.trim())
            .put("remoteplace", "txt.newclient.top")
            .put("search_type", 0)
            .put("searchid", searchId)))

/** Sign the exact UTF-8 JSON text sent in the public search request. */
internal fun createQqSearchSignature(body: String): String {
    val hash = MessageDigest.getInstance("SHA-1").digest(body.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte.toInt() and 0xff) }
    fun pick(indices: List<Int>) = indices.mapNotNull(hash::getOrNull).joinToString("")
    val scramble = listOf(89, 39, 179, 150, 218, 82, 58, 252, 177, 52, 186, 123, 120, 64, 242, 133, 143, 161, 121, 179)
    val bytes = ByteArray(scramble.size) { index ->
        (scramble[index] xor hash.substring(index * 2, index * 2 + 2).toInt(16)).toByte()
    }
    val encoded = Base64.getEncoder().encodeToString(bytes).filterNot { it in "\\/+=" }
    return ("zzc" + pick(listOf(23, 14, 6, 36, 16, 40, 7, 19)) + encoded +
        pick(listOf(16, 1, 32, 12, 19, 27, 8, 5))).lowercase(Locale.ROOT)
}

internal fun createMiguSearchSignature(keyword: String, timestamp: String, deviceId: String): String {
    val raw = keyword + MIGU_SIGNATURE_PREFIX + deviceId + timestamp
    return MessageDigest.getInstance("MD5")
        .digest(raw.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}

private const val MIGU_SIGNATURE_PREFIX =
    "6cdc72a439cef99a3418d2a78aa28c73yyapp2d16148780a1dcc7408e06336b98cfd50"
