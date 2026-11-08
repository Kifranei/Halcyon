package com.ella.music.data

import com.ella.music.data.lastfm.fetchLastFmArtistImage
import com.ella.music.data.lastfm.isUsableArtistImageUrl
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** One selectable picture on the manual artist image search page (#746). */
data class ArtistImageCandidate(
    val source: String,
    val name: String,
    val url: String,
    val resolution: String? = null
)

/** Credentials the manual search shares with automatic artist image downloads. */
data class ArtistImageSearchConfig(
    val sources: List<String>,
    val lastFmApiKey: String,
    val lastFmRegion: String,
    val spotifyClientId: String,
    val spotifyClientSecret: String,
    val spotifyRegion: String
)

internal object ArtistImageSearch {
    private const val MAX_RESULTS_PER_SOURCE = 12
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    /** Every source in priority order; failures only empty that source's tab. */
    suspend fun searchAll(query: String, config: ArtistImageSearchConfig): Map<String, List<ArtistImageCandidate>> =
        coroutineScope {
            config.sources.map { source -> async { source to search(query, source, config) } }
                .awaitAll().toMap()
        }

    suspend fun search(query: String, source: String, config: ArtistImageSearchConfig): List<ArtistImageCandidate> =
        withContext(Dispatchers.IO) {
            val name = query.trim()
            if (name.isEmpty()) return@withContext emptyList()
            runCatching {
                when (source) {
                    SettingsManager.ARTIST_IMAGE_SOURCE_QQ -> searchQq(name)
                    SettingsManager.ARTIST_IMAGE_SOURCE_KUGOU -> searchKugou(name)
                    SettingsManager.ARTIST_IMAGE_SOURCE_KUWO -> searchKuwo(name)
                    SettingsManager.ARTIST_IMAGE_SOURCE_NETEASE -> searchNetease(name)
                    SettingsManager.ARTIST_IMAGE_SOURCE_SPOTIFY -> ArtistImageRepository.searchSpotifyArtistImages(
                        name, config.spotifyClientId, config.spotifyClientSecret, config.spotifyRegion
                    )
                    SettingsManager.ARTIST_IMAGE_SOURCE_LASTFM ->
                        com.ella.music.data.lastfm.fetchLastFmArtistImageCandidates(
                            name, config.lastFmApiKey, config.lastFmRegion
                        ).map { (url, size) ->
                            ArtistImageCandidate(source, name, url, resolution = size.takeIf { it.isNotBlank() })
                        }.ifEmpty {
                            listOfNotNull(fetchLastFmArtistImage(name, config.lastFmApiKey, config.lastFmRegion)
                                ?.let { ArtistImageCandidate(source, name, it) })
                        }
                    else -> emptyList()
                }
            }.getOrDefault(emptyList())
                .map { candidate ->
                    val url = preferredArtistImageUrls(candidate.source, candidate.url).first()
                    candidate.copy(url = url, resolution = candidate.resolution ?: artistImageResolution(candidate.source, url))
                }
                .filter { isUsableArtistImageUrl(it.url) }
                .distinctBy { it.url }
                .take(MAX_RESULTS_PER_SOURCE)
        }

    /** Pixel size read from the upgraded CDN URL when the source encodes it (#754). */
    private fun artistImageResolution(source: String, url: String): String? = when (source) {
        SettingsManager.ARTIST_IMAGE_SOURCE_LASTFM -> Regex("/i/u/(\\d+)x(\\d+)/").find(url)?.let { match ->
            val w = match.groupValues[1].toIntOrNull() ?: 0
            val h = match.groupValues[2].toIntOrNull() ?: 0
            if (w > 0 && h > 0) "${w}×${h}" else if (w > 0) "${w}px" else null
        }
        SettingsManager.ARTIST_IMAGE_SOURCE_QQ ->
            Regex("T001R(\\d+)x(\\d+)M").find(url)?.let { "${it.groupValues[1]}×${it.groupValues[2]}" }
        SettingsManager.ARTIST_IMAGE_SOURCE_KUGOU ->
            Regex("/softhead/(\\d+)/").find(url)?.let { "${it.groupValues[1]}×${it.groupValues[1]}" }
        SettingsManager.ARTIST_IMAGE_SOURCE_KUWO ->
            Regex("/starheads/(\\d+)/").find(url)?.let { "${it.groupValues[1]}×${it.groupValues[1]}" }
        SettingsManager.ARTIST_IMAGE_SOURCE_NETEASE ->
            Regex("param=(\\d+)y(\\d+)").find(url)?.let { "${it.groupValues[1]}×${it.groupValues[2]}" }
        else -> null
    }

    private fun getJson(url: String, referer: String): JSONObject? = client.newCall(
        Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").header("Referer", referer).build()
    ).execute().use { response ->
        if (response.isSuccessful) response.body?.string()?.let(::JSONObject) else null
    }

    private fun searchQq(name: String): List<ArtistImageCandidate> {
        val url = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp".toHttpUrl().newBuilder()
            .addQueryParameter("w", name).addQueryParameter("format", "json").addQueryParameter("t", "9")
            .addQueryParameter("page", "1").addQueryParameter("n", "20").build()
        val list = getJson(url.toString(), "https://y.qq.com/")
            ?.optJSONObject("data")?.optJSONObject("singer")?.optJSONArray("list") ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.mapNotNull { singer ->
            val pic = singer.optString("singerPic").httpsUrl() ?: return@mapNotNull null
            ArtistImageCandidate(SettingsManager.ARTIST_IMAGE_SOURCE_QQ, singer.optString("singerName"), pic)
        }
    }

    private fun searchKugou(name: String): List<ArtistImageCandidate> {
        val url = "https://msearch.kugou.com/api/v3/search/singer".toHttpUrl().newBuilder()
            .addQueryParameter("keyword", name).addQueryParameter("format", "json")
            .addQueryParameter("page", "1").addQueryParameter("pagesize", "10").build()
        val list = getJson(url.toString(), "https://www.kugou.com/")?.optJSONArray("data") ?: return emptyList()
        return (0 until minOf(list.length(), 8)).mapNotNull { list.optJSONObject(it) }.mapNotNull { singer ->
            val id = singer.optLong("singerid").takeIf { it > 0 } ?: return@mapNotNull null
            val image = runCatching {
                getJson("https://mobileservice.kugou.com/api/v3/singer/info?singerid=$id", "https://www.kugou.com/")
                    ?.optJSONObject("data")?.optString("imgurl").orEmpty().replace("{size}", "400")
            }.getOrNull()?.httpsUrl() ?: return@mapNotNull null
            ArtistImageCandidate(SettingsManager.ARTIST_IMAGE_SOURCE_KUGOU, singer.optString("singername"), image)
        }
    }

    private fun searchKuwo(name: String): List<ArtistImageCandidate> {
        val url = "https://search.kuwo.cn/r.s".toHttpUrl().newBuilder()
            .addQueryParameter("all", name).addQueryParameter("ft", "artist").addQueryParameter("itemset", "artist")
            .addQueryParameter("client", "kt").addQueryParameter("pn", "0").addQueryParameter("rn", "20")
            .addQueryParameter("rformat", "json").addQueryParameter("encoding", "utf8").build()
        val result = client.newCall(
            Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").header("Referer", "https://www.kuwo.cn/").build()
        ).execute().use { response ->
            if (response.isSuccessful) JSONObject(decodeMusicMetadata(response.body?.bytes() ?: return emptyList())) else null
        } ?: return emptyList()
        val base = result.optString("BASEPICPATH")
        val list = result.optJSONArray("abslist") ?: return emptyList()
        return (0 until list.length()).mapNotNull { list.optJSONObject(it) }.mapNotNull { artist ->
            val image = kuwoArtistImageUrl(base, artist.optString("PICPATH")) ?: return@mapNotNull null
            ArtistImageCandidate(SettingsManager.ARTIST_IMAGE_SOURCE_KUWO, cleanMusicMetadata(artist.optString("ARTIST")), image)
        }
    }

    private fun searchNetease(name: String): List<ArtistImageCandidate> {
        val url = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
            .addQueryParameter("s", name).addQueryParameter("type", "100")
            .addQueryParameter("offset", "0").addQueryParameter("limit", "20").build()
        val artists = getJson(url.toString(), "https://music.163.com/")
            ?.optJSONObject("result")?.optJSONArray("artists") ?: return emptyList()
        return (0 until artists.length()).mapNotNull { artists.optJSONObject(it) }.mapNotNull { artist ->
            val pic = listOf("picUrl", "img1v1Url").firstNotNullOfOrNull { key ->
                artist.optString(key).httpsUrl()?.takeIf(::isUsableArtistImageUrl)
            } ?: return@mapNotNull null
            ArtistImageCandidate(SettingsManager.ARTIST_IMAGE_SOURCE_NETEASE, artist.optString("name"), pic)
        }
    }

    private fun String.httpsUrl(): String? =
        trim().replaceFirst("http://", "https://").takeIf { it.startsWith("https://") }
}
