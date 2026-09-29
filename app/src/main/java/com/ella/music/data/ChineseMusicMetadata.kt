package com.ella.music.data

import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.TimeUnit

enum class AlbumInfoSource(val id: String, val labelRes: Int) {
    QQ("qq", R.string.artist_image_source_qq),
    Kugou("kugou", R.string.artist_image_source_kugou),
    Kuwo("kuwo", R.string.artist_image_source_kuwo),
    Netease("netease", R.string.artist_image_source_netease);

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: Netease
    }
}

internal data class ChineseArtistBiography(val text: String, val url: String)

/** Public provider metadata only. Never substitute another provider after an empty result. */
internal object ChineseMusicMetadata {
    private val client = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

    private fun get(base: String, vararg parameters: Pair<String, String>): String {
        val url = base.toHttpUrl().newBuilder().apply { parameters.forEach { (k, v) -> addQueryParameter(k, v) } }.build()
        return client.newCall(Request.Builder().url(url).header("User-Agent", "Mozilla/5.0")
            .header("Referer", when {
                url.host.endsWith("qq.com") -> "https://y.qq.com/"
                url.host.endsWith("kuwo.cn") -> "https://www.kuwo.cn/"
                else -> "https://www.kugou.com/"
            }).build()).execute().use { response ->
            check(response.isSuccessful) { "Metadata HTTP ${response.code}" }
            decodeMusicMetadata(response.body?.bytes() ?: byteArrayOf())
        }
    }

    private fun json(base: String, vararg parameters: Pair<String, String>) = JSONObject(get(base, *parameters))
    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else
        (0 until length()).mapNotNull { optJSONObject(it) }

    private fun qqSearch(name: String, type: String, key: String): List<JSONObject> =
        json("https://c.y.qq.com/soso/fcgi-bin/client_search_cp", "w" to name, "t" to type,
            "format" to "json", "inCharset" to "utf-8", "outCharset" to "utf-8", "p" to "1", "n" to "20")
            .optJSONObject("data")?.optJSONObject(key)?.optJSONArray("list").objects()

    private fun kugouSearch(name: String, kind: String): List<JSONObject> {
        val root = json("https://msearch.kugou.com/api/v3/search/$kind", "keyword" to name,
            "page" to "1", "pagesize" to "20")
        return (root.optJSONArray("data") ?: root.optJSONObject("data")?.optJSONArray("info")).objects()
    }

    private fun kuwoSearch(name: String, kind: String): List<JSONObject> {
        val root = json("https://search.kuwo.cn/r.s", "all" to name, "ft" to kind, "itemset" to kind,
            "client" to "kt", "pn" to "0", "rn" to "20", "rformat" to "json", "encoding" to "utf8")
        return (root.optJSONArray("abslist") ?: root.optJSONArray("albumlist")).objects()
    }

    suspend fun artist(name: String, source: AlbumInfoSource): ChineseArtistBiography? = withContext(Dispatchers.IO) {
        val result = when (source) {
            AlbumInfoSource.QQ -> {
                val artists = qqSearch(name, "9", "singer")
                val found = artists.getOrNull(preferredArtistImageMatch(name, artists.map { it.optString("singerName") }))
                    ?: return@withContext null
                val mid = found.optString("singerMID").ifBlank { found.optString("singerMid") }
                if (mid.isBlank()) return@withContext null
                val xml = get("https://c.y.qq.com/splcloud/fcgi-bin/fcg_get_singer_desc.fcg", "singermid" to mid, "format" to "xml")
                val text = Regex("""<desc>([\s\S]*?)</desc>""").find(xml)?.groupValues?.get(1).orEmpty()
                ChineseArtistBiography(cleanMusicMetadata(text), "https://y.qq.com/n/ryqq/singer/$mid")
            }
            AlbumInfoSource.Kugou -> {
                val artists = kugouSearch(name, "singer")
                val found = artists.getOrNull(preferredArtistImageMatch(name, artists.map { it.optString("singername") }))
                    ?: return@withContext null
                val id = found.optLong("singerid").takeIf { it > 0 } ?: return@withContext null
                val data = json("https://mobileservice.kugou.com/api/v3/singer/info", "singerid" to "$id").optJSONObject("data")
                ChineseArtistBiography(cleanMusicMetadata(data?.optString("profile").orEmpty()), "https://www.kugou.com/singer/$id.html")
            }
            AlbumInfoSource.Kuwo -> {
                val artists = kuwoSearch(name, "artist")
                val found = artists.getOrNull(preferredArtistImageMatch(name, artists.map { cleanMusicMetadata(it.optString("ARTIST")) }))
                    ?: return@withContext null
                val id = found.optString("ARTISTID").takeIf { it.toLongOrNull()?.let { n -> n > 0 } == true } ?: return@withContext null
                val data = json("https://search.kuwo.cn/r.s", "stype" to "artistinfo", "artistid" to id, "rformat" to "json", "encoding" to "utf8")
                ChineseArtistBiography(cleanMusicMetadata(data.optString("info").ifBlank { data.optString("desc") }), "https://www.kuwo.cn/singer_detail/$id")
            }
            AlbumInfoSource.Netease -> return@withContext null
        }
        result.takeIf { it.text.isNotBlank() }
    }

    suspend fun album(source: AlbumInfoSource, album: Album?, songs: List<Song>, neteaseUrl: String?): String? = withContext(Dispatchers.IO) {
        if (source == AlbumInfoSource.Netease) return@withContext NeteaseAlbumDescriptionFetcher.fetchDescription(album, songs, neteaseUrl)
        val name = album?.name.orEmpty().ifBlank { songs.firstOrNull()?.album.orEmpty() }.trim()
        val artist = album?.artist.orEmpty().ifBlank { songs.firstOrNull()?.artist.orEmpty() }.trim()
        if (name.isBlank()) return@withContext null
        val description = when (source) {
            AlbumInfoSource.QQ -> {
                val candidates = qqSearch(name, "8", "album")
                val found = candidates.firstOrNull { metadataAlbumMatches(name, artist, it.optString("albumName"), it.optString("singerName")) }
                    ?: return@withContext null
                val mid = found.optString("albumMID").takeIf(String::isNotBlank) ?: return@withContext null
                val payload = JSONObject().put("comm", JSONObject().put("ct", 24).put("cv", 0))
                    .put("req", JSONObject().put("module", "music.musichallAlbum.AlbumInfoServer")
                        .put("method", "GetAlbumDetail").put("param", JSONObject().put("albumMId", mid)))
                json("https://u.y.qq.com/cgi-bin/musicu.fcg", "data" to payload.toString())
                    .optJSONObject("req")?.optJSONObject("data")?.optJSONObject("basicInfo")?.optString("desc").orEmpty()
            }
            AlbumInfoSource.Kugou -> {
                val candidates = kugouSearch(name, "album")
                val found = candidates.firstOrNull { metadataAlbumMatches(name, artist, it.optString("albumname"), it.optString("singername")) }
                    ?: return@withContext null
                val id = found.optLong("albumid").takeIf { it > 0 } ?: return@withContext null
                json("https://mobileservice.kugou.com/api/v3/album/info", "albumid" to "$id")
                    .optJSONObject("data")?.optString("intro").orEmpty()
            }
            AlbumInfoSource.Kuwo -> {
                val candidates = kuwoSearch(name, "album")
                val found = candidates.firstOrNull { metadataAlbumMatches(name, artist,
                    cleanMusicMetadata(it.optString("ALBUM").ifBlank { it.optString("name") }),
                    cleanMusicMetadata(it.optString("ARTIST").ifBlank { it.optString("artist") })) }
                    ?: return@withContext null
                val id = found.optString("ALBUMID").ifBlank { found.optString("albumid") }.takeIf(String::isNotBlank) ?: return@withContext null
                json("https://search.kuwo.cn/r.s", "stype" to "albuminfo", "albumid" to id, "rformat" to "json", "encoding" to "utf8")
                    .optString("info")
            }
            AlbumInfoSource.Netease -> ""
        }
        cleanMusicMetadata(description).takeIf(String::isNotBlank)
    }
}

internal fun decodeMusicMetadata(bytes: ByteArray): String = runCatching {
    Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
}.getOrElse { String(bytes, charset("GB18030")) }

internal fun cleanMusicMetadata(value: String): String = value
    .replace("<![CDATA[", "").replace("]]>", "")
    .replace(Regex("(?i)<br\\s*/?>|</p>"), "\n")
    .replace(Regex("<[^>]+>"), "")
    .replace("&nbsp;", " ").replace("&amp;", "&").replace("&quot;", "\"")
    .replace("&apos;", "'").replace(Regex("""\\+n;?"""), "\n")
    .replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'")
    .trim().takeUnless { it == "null" }.orEmpty()

internal fun metadataAlbumMatches(title: String, artist: String, candidateTitle: String, candidateArtist: String): Boolean {
    if (!title.trim().equals(candidateTitle.trim(), ignoreCase = true)) return false
    if (artist.isBlank() || artist == "<unknown>") return true
    if (artistImageNameMatches(artist, candidateArtist)) return true
    val names = artist.split(Regex("\\s*[/、;&]\\s*"))
    return names.any { artistImageNameMatches(it, candidateArtist) } ||
        candidateArtist.split(Regex("\\s*[/、;&]\\s*")).any { candidate -> names.any { artistImageNameMatches(it, candidate) } }
}
