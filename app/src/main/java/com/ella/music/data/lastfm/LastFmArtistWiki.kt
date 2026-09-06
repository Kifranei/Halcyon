package com.ella.music.data.lastfm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class LastFmArtistWiki(
    val text: String,
    val artistUrl: String,
    val wikiUrl: String,
    val source: ArtistWikiSource
)

/** Wiki language switcher. English, Simplified Chinese and Japanese stay first. */
internal data class LastFmWikiRegion(
    val code: String,
    val countryNameRes: Int
)

internal enum class ArtistBioMenuSource(val id: String, val iconRes: Int) {
    Wikipedia("wikipedia", com.ella.music.R.drawable.ic_source_wikipedia),
    LastFm("lastfm", com.ella.music.R.drawable.ic_source_lastfm),
    Netease("netease", com.ella.music.R.drawable.ic_source_netease)
}

/** Compact, font-independent labels used by the biography source picker. */
internal val ArtistBioMenuSource.shortLabel: String
    get() = when (this) {
        ArtistBioMenuSource.Netease -> "N"
        ArtistBioMenuSource.LastFm -> "L"
        ArtistBioMenuSource.Wikipedia -> "W"
    }

internal fun normalizeArtistBioSource(value: String?): ArtistBioMenuSource =
    ArtistBioMenuSource.entries.firstOrNull { it.id.equals(value?.trim(), ignoreCase = true) }
        ?: ArtistBioMenuSource.Wikipedia

internal fun artistBioSourcesForLanguage(code: String): List<ArtistBioMenuSource> = when (
    code.trim().lowercase(Locale.ROOT)
) {
    "zh", "zh-cn" -> listOf(
        ArtistBioMenuSource.Wikipedia,
        ArtistBioMenuSource.LastFm,
        ArtistBioMenuSource.Netease
    )
    else -> listOf(ArtistBioMenuSource.Wikipedia, ArtistBioMenuSource.LastFm)
}

internal val ARTIST_BIO_LANGUAGES: List<LastFmWikiRegion> = listOf(
    LastFmWikiRegion("zh", com.ella.music.R.string.artist_biography_lang_zh_cn),
    LastFmWikiRegion("zh-hk", com.ella.music.R.string.artist_biography_lang_zh_hk),
    LastFmWikiRegion("zh-tw", com.ella.music.R.string.artist_biography_lang_zh_tw),
    LastFmWikiRegion("en", com.ella.music.R.string.artist_biography_lang_en),
    LastFmWikiRegion("ja", com.ella.music.R.string.artist_biography_lang_ja),
    LastFmWikiRegion("ko", com.ella.music.R.string.artist_biography_lang_ko)
)

internal val LAST_FM_WIKI_REGIONS: List<LastFmWikiRegion> = listOf(
    LastFmWikiRegion("en", com.ella.music.R.string.artist_biography_country_us),
    LastFmWikiRegion("zh", com.ella.music.R.string.artist_biography_country_cn),
    LastFmWikiRegion("zh-hk", com.ella.music.R.string.artist_biography_lang_zh_hk),
    LastFmWikiRegion("zh-tw", com.ella.music.R.string.artist_biography_lang_zh_tw),
    LastFmWikiRegion("ja", com.ella.music.R.string.artist_biography_country_jp),
    LastFmWikiRegion("ko", com.ella.music.R.string.artist_biography_lang_ko),
    LastFmWikiRegion("de", com.ella.music.R.string.artist_biography_country_de),
    LastFmWikiRegion("es", com.ella.music.R.string.artist_biography_country_es),
    LastFmWikiRegion("fr", com.ella.music.R.string.artist_biography_country_fr),
    LastFmWikiRegion("it", com.ella.music.R.string.artist_biography_country_it),
    LastFmWikiRegion("pl", com.ella.music.R.string.artist_biography_country_pl),
    LastFmWikiRegion("pt", com.ella.music.R.string.artist_biography_country_pt),
    LastFmWikiRegion("ru", com.ella.music.R.string.artist_biography_country_ru),
    LastFmWikiRegion("sv", com.ella.music.R.string.artist_biography_country_se),
    LastFmWikiRegion("tr", com.ella.music.R.string.artist_biography_country_tr)
)

internal const val DEFAULT_LAST_FM_WIKI_REGION = "en"

internal enum class ArtistWikiSource {
    LastFmApi,
    Netease,
    LastFmHtml,
    WikipediaSelected,
    WikipediaEnglish
}

internal fun artistWikiSourceOrder(
    regionCode: String,
    hasApiKey: Boolean,
    preferred: ArtistBioMenuSource? = null
): List<ArtistWikiSource> {
    val region = normalizeLastFmWikiRegion(regionCode)
    return when (preferred) {
        ArtistBioMenuSource.LastFm -> buildList {
            if (hasApiKey) add(ArtistWikiSource.LastFmApi)
            add(ArtistWikiSource.LastFmHtml)
        }
        ArtistBioMenuSource.Netease -> listOf(ArtistWikiSource.Netease)
        ArtistBioMenuSource.Wikipedia -> buildList {
            add(ArtistWikiSource.WikipediaSelected)
            if (region != DEFAULT_LAST_FM_WIKI_REGION) add(ArtistWikiSource.WikipediaEnglish)
        }
        null -> buildList {
            // Keep the legacy overload Last.fm-only. Provider selection is explicit in the UI;
            // a failed/unreachable provider must not silently replace the requested biography
            // with NetEase or Wikipedia (#586).
            if (hasApiKey) add(ArtistWikiSource.LastFmApi)
            add(ArtistWikiSource.LastFmHtml)
        }
    }
}

internal fun normalizeLastFmWikiRegion(code: String?): String {
    val normalized = code?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return LAST_FM_WIKI_REGIONS.firstOrNull { it.code == normalized }?.code
        ?: DEFAULT_LAST_FM_WIKI_REGION
}

/** Maps the existing Last.fm language choice to Spotify's ISO 3166-1 market parameter. */
internal fun spotifyMarketForLastFmRegion(regionCode: String): String = when (
    normalizeLastFmWikiRegion(regionCode)
) {
    "zh" -> "CN"
    "ja" -> "JP"
    "de" -> "DE"
    "es" -> "ES"
    "fr" -> "FR"
    "it" -> "IT"
    "pl" -> "PL"
    "pt" -> "PT"
    "ru" -> "RU"
    "sv" -> "SE"
    "tr" -> "TR"
    else -> "US"
}

internal fun lastFmWikiHostPrefix(regionCode: String): String {
    val code = normalizeLastFmWikiRegion(regionCode)
    return if (code == "en") "" else code
}

internal fun lastFmLanguagePrefix(locale: Locale): String {
    val language = locale.language.lowercase(Locale.ROOT)
    val country = locale.country.uppercase(Locale.ROOT)
    return when (language) {
        "zh" -> if (country == "TW" || country == "HK" || locale.script.equals("Hant", ignoreCase = true)) {
            "zh"
        } else {
            "zh"
        }
        "ja", "ko", "de", "fr", "ru", "es", "it", "pt", "pl", "nl", "sv", "tr" -> language
        "en" -> ""
        else -> language.takeIf { it.length == 2 }.orEmpty()
    }
}

internal fun lastFmArtistSlug(artistName: String): String =
    artistName.trim()
        .replace(Regex("""\s+"""), "+")
        .ifBlank { artistName.trim() }

internal fun lastFmArtistPageUrl(artistName: String, locale: Locale): String =
    lastFmArtistPageUrl(artistName, lastFmLanguagePrefix(locale).ifBlank { "en" })

internal fun lastFmArtistPageUrl(artistName: String, regionCode: String): String {
    val slug = lastFmArtistSlug(artistName)
    val prefix = lastFmWikiHostPrefix(regionCode)
    return if (prefix.isBlank()) {
        "https://www.last.fm/music/$slug"
    } else {
        "https://www.last.fm/$prefix/music/$slug"
    }
}

internal fun lastFmArtistWikiUrl(artistName: String, locale: Locale): String =
    "${lastFmArtistPageUrl(artistName, locale)}/+wiki"

internal fun lastFmArtistWikiUrl(artistName: String, regionCode: String): String =
    "${lastFmArtistPageUrl(artistName, regionCode)}/+wiki"

internal fun parseLastFmWikiHtml(html: String): String {
    if (html.isBlank()) return ""
    val wikiBlock = wikiHtmlBlock(html) ?: return ""
    return htmlToPlainWikiText(wikiBlock)
}

internal fun htmlToPlainWikiText(html: String): String {
    val text = html
        .replace(Regex("(?i)<br\\s*/?>"), "\n")
        .replace(Regex("(?i)</p>"), "\n\n")
        .replace(Regex("(?i)<p[^>]*>"), "")
        .replace(Regex("(?i)<li[^>]*>"), "• ")
        .replace(Regex("(?i)</li>"), "\n")
        .replace(Regex("(?is)<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#34;", "\"")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&apos;", "'")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
    return stripLastFmLicenseFooter(text)
}

internal fun parseLastFmArtistGetInfoJson(
    raw: String,
    regionCode: String = DEFAULT_LAST_FM_WIKI_REGION,
    requestedArtistName: String? = null,
    ignoreCase: Boolean = true
): LastFmArtistWiki? {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (root.has("error")) return null
    val artist = root.optJSONObject("artist") ?: return null
    val bio = artist.optJSONObject("bio") ?: return null
    val content = bio.optString("content").ifBlank { bio.optString("summary") }
    val text = htmlToPlainWikiText(content)
    if (text.isBlank()) return null
    val name = artist.optString("name").ifBlank { "unknown" }
    if (!requestedArtistName.isNullOrBlank() &&
        !name.trim().equals(requestedArtistName.trim(), ignoreCase = ignoreCase)
    ) return null
    val artistUrl = artist.optString("url").ifBlank { lastFmArtistPageUrl(name, regionCode) }
    return LastFmArtistWiki(
        text = text,
        artistUrl = artistUrl,
        wikiUrl = lastFmArtistWikiUrl(name, regionCode),
        source = ArtistWikiSource.LastFmApi
    )
}

/** Extracts the largest usable artist image from Last.fm's artist.getinfo response. */
internal fun parseLastFmArtistImageUrl(
    raw: String,
    requestedArtistName: String? = null,
    ignoreCase: Boolean = true
): String? {
    val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return null
    if (root.containsKey("error")) return null
    val artist = root["artist"]?.jsonObject ?: return null
    val returnedName = artist["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
    if (!requestedArtistName.isNullOrBlank() &&
        !returnedName.equals(requestedArtistName.trim(), ignoreCase = ignoreCase)
    ) return null
    val images = runCatching { artist["image"]?.jsonArray }.getOrNull() ?: return null
    val preferredSizes = listOf("mega", "extralarge", "large", "medium", "small")
    preferredSizes.forEach { preferredSize ->
        for (index in images.indices) {
            val image = runCatching { images[index].jsonObject }.getOrNull() ?: continue
            val size = image["size"]?.jsonPrimitive?.contentOrNull.orEmpty()
            if (size.equals(preferredSize, ignoreCase = true)) {
                image["#text"]?.jsonPrimitive?.contentOrNull
                    ?.trim()
                    ?.takeIf(::isUsableArtistImageUrl)
                    ?.let { return it }
            }
        }
    }
    for (index in images.indices.reversed()) {
        runCatching { images[index].jsonObject }
            .getOrNull()
            ?.get("#text")
            ?.jsonPrimitive
            ?.contentOrNull
            ?.trim()
            ?.takeIf(::isUsableArtistImageUrl)
            ?.let { return it }
    }
    return null
}

internal fun wikipediaLanguage(regionCode: String): String = when (regionCode.trim().lowercase(Locale.ROOT)) {
    "zh", "zh-cn", "zh-hk", "zh-tw" -> "zh"
    "ko" -> "ko"
    else -> normalizeLastFmWikiRegion(regionCode)
}

internal fun wikipediaVariant(regionCode: String): String? = when (regionCode.trim().lowercase(Locale.ROOT)) {
    "zh", "zh-cn" -> "zh-cn"
    "zh-hk" -> "zh-hk"
    "zh-tw" -> "zh-tw"
    else -> null
}

internal fun parseWikipediaSearchTitle(raw: String, artistName: String): String? {
    val search = runCatching {
        Json.parseToJsonElement(raw).jsonObject["query"]?.jsonObject
            ?.get("search")?.jsonArray
    }.getOrNull() ?: return null
    val requested = artistName.trim()
    val titles = buildList {
        search.forEach { element ->
            runCatching { element.jsonObject["title"]?.jsonPrimitive?.contentOrNull }
                .getOrNull()?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }
    }
    return titles.firstOrNull { it == requested }
        ?: titles.firstOrNull { it.equals(requested, ignoreCase = true) }
}

internal fun parseWikipediaExtract(raw: String): Pair<String, String>? {
    val pages = runCatching { JSONObject(raw) }.getOrNull()
        ?.optJSONObject("query")
        ?.optJSONObject("pages")
        ?: return null
    val keys = pages.keys()
    if (!keys.hasNext()) return null
    val page = pages.optJSONObject(keys.next()) ?: return null
    if (page.has("missing")) return null
    val extract = page.optString("extract").trim()
    if (extract.isBlank()) return null
    val title = page.optString("title").takeIf { it.isNotBlank() } ?: return extract to ""
    return extract to title
}

internal fun parseNeteaseArtistId(raw: String, artistName: String): String? {
    val artists = runCatching {
        Json.parseToJsonElement(raw).jsonObject["result"]?.jsonObject
            ?.get("artists")?.jsonArray
    }.getOrNull() ?: return null
    val requested = artistName.trim()
    fun findArtistId(ignoreCase: Boolean): String? {
        artists.forEach { element ->
            val artist = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val name = artist["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (name.equals(requested, ignoreCase = ignoreCase)) {
                return artist["id"]?.jsonPrimitive?.longOrNull?.takeIf { it > 0L }?.toString()
            }
        }
        return null
    }
    findArtistId(ignoreCase = false)?.let { return it }
    findArtistId(ignoreCase = true)?.let { return it }
    return null
}

internal fun parseNeteaseArtistImageUrl(raw: String, artistName: String): String? {
    val artists = runCatching {
        Json.parseToJsonElement(raw).jsonObject["result"]?.jsonObject
            ?.get("artists")?.jsonArray
    }.getOrNull() ?: return null
    val requested = artistName.trim()
    val candidates = buildList {
        artists.forEach { element ->
            val artist = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val name = artist["name"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            if (name == requested || name.equals(requested, ignoreCase = true)) add(artist)
        }
    }.sortedWith(compareBy { artist ->
        val name = artist["name"]?.jsonPrimitive?.contentOrNull.orEmpty()
        if (name == requested) 0 else 1
    })
    return candidates.asSequence()
        .flatMap { artist ->
            sequenceOf("picUrl", "img1v1Url", "coverUrl").mapNotNull { key ->
                artist[key]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf(::isUsableArtistImageUrl)
            }
        }
        .firstOrNull()
}

internal fun parseNeteaseArtistBiography(raw: String): String {
    val root = runCatching { Json.parseToJsonElement(raw).jsonObject }.getOrNull() ?: return ""
    val sections = buildList {
        root["briefDesc"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
            .takeIf { it.isNotBlank() }?.let(::add)
        val introduction = runCatching { root["introduction"]?.jsonArray }.getOrNull()
        if (introduction != null) {
            introduction.forEach { element ->
                runCatching { element.jsonObject }.getOrNull()?.let { section ->
                    val title = section["ti"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    val text = section["txt"]?.jsonPrimitive?.contentOrNull.orEmpty().trim()
                    if (text.isNotBlank()) add(listOf(title, text).filter(String::isNotBlank).joinToString("\n"))
                }
            }
        }
    }
    return sections.distinct().joinToString("\n\n").trim()
}

internal fun stripLastFmLicenseFooter(text: String): String {
    val markers = listOf(
        "User-contributed text is available under the Creative Commons",
        "User-contributed text is available under the Creative Commons By-SA License",
        "用户贡献的文本在知识共享",
        "ユーザーが投稿したテキストはクリエイティブ・コモンズ"
    )
    var result = text
    markers.forEach { marker ->
        val index = result.indexOf(marker, ignoreCase = true)
        if (index >= 0) result = result.substring(0, index).trim()
    }
    return result.trim()
}

internal fun artistBioDownloadAllowed(mode: Int, wifiConnected: Boolean): Boolean = when (mode) {
    ARTIST_BIO_DOWNLOAD_ALWAYS -> true
    ARTIST_BIO_DOWNLOAD_WIFI -> wifiConnected
    ARTIST_BIO_DOWNLOAD_NEVER -> false
    else -> wifiConnected
}

internal const val ARTIST_BIO_DOWNLOAD_ALWAYS = 0
internal const val ARTIST_BIO_DOWNLOAD_WIFI = 1
internal const val ARTIST_BIO_DOWNLOAD_NEVER = 2

internal fun isWifiConnected(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    val network = manager.activeNetwork ?: return false
    val capabilities = manager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
}

internal fun isVpnActive(context: Context): Boolean {
    val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        ?: return false
    return manager.getNetworkCapabilities(manager.activeNetwork)
        ?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
}

internal suspend fun fetchLastFmArtistWiki(
    artistName: String,
    locale: Locale,
    apiKey: String? = null
): LastFmArtistWiki = fetchLastFmArtistWiki(
    artistName,
    lastFmLanguagePrefix(locale).ifBlank { "en" },
    apiKey
)

internal suspend fun fetchLastFmArtistWiki(
    artistName: String,
    regionCode: String,
    apiKey: String? = null,
    preferredSource: ArtistBioMenuSource? = null
): LastFmArtistWiki = withContext(Dispatchers.IO) {
    val region = normalizeLastFmWikiRegion(regionCode)
    val client = wikiHttpClient()
    val errors = mutableListOf<Throwable>()

    for (source in artistWikiSourceOrder(
        region,
        hasApiKey = !apiKey.isNullOrBlank(),
        preferred = preferredSource
    )) {
        val result = runCatching {
            when (source) {
                ArtistWikiSource.LastFmApi ->
                    fetchLastFmArtistWikiFromApi(artistName, region, apiKey.orEmpty(), client)
                ArtistWikiSource.Netease -> fetchNeteaseArtistWiki(artistName, client)
                ArtistWikiSource.LastFmHtml -> fetchLastFmArtistWikiFromHtml(artistName, region, client)
                ArtistWikiSource.WikipediaSelected -> fetchWikipediaArtistWiki(artistName, region, client)
                ArtistWikiSource.WikipediaEnglish ->
                    fetchWikipediaArtistWiki(artistName, DEFAULT_LAST_FM_WIKI_REGION, client)
            }
        }.onFailure(errors::add)
        val wiki = result.getOrNull()
        if (wiki != null && wiki.text.isNotBlank()) return@withContext wiki
    }

    if (errors.isNotEmpty()) throw errors.first()
    LastFmArtistWiki(
        text = "",
        artistUrl = lastFmArtistPageUrl(artistName, region),
        wikiUrl = lastFmArtistWikiUrl(artistName, region),
        source = ArtistWikiSource.LastFmHtml
    )
}

/**
 * Reads an artist image without requiring the Last.fm account session. An API key is used when
 * configured; the public artist page remains a useful fallback for installations without one.
 */
internal suspend fun fetchLastFmArtistImage(
    artistName: String,
    apiKey: String? = null,
    regionCode: String = DEFAULT_LAST_FM_WIKI_REGION
): String? = withContext(Dispatchers.IO) {
    if (artistName.isBlank()) return@withContext null
    val client = wikiHttpClient()
    if (!apiKey.isNullOrBlank()) {
        val apiUrl = LAST_FM_API_ROOT.toHttpUrl().newBuilder()
            .addQueryParameter("method", "artist.getinfo")
            .addQueryParameter("artist", artistName)
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("lang", lastFmApiLanguage(regionCode))
            .addQueryParameter("autocorrect", "1")
            .addQueryParameter("format", "json")
            .build()
            .toString()
        runCatching {
            val raw = client.executeText(apiUrl)
            parseLastFmArtistImageUrl(raw, requestedArtistName = artistName, ignoreCase = false)
                ?: parseLastFmArtistImageUrl(raw, requestedArtistName = artistName, ignoreCase = true)
        }.getOrNull()?.let { return@withContext it }
    }
    runCatching {
        val html = client.newBuilder()
            .connectTimeout(4, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .build()
            .executeText(lastFmArtistPageUrl(artistName, regionCode))
        parseArtistOpenGraphImageUrl(html)
    }.getOrNull()
}

internal suspend fun fetchNeteaseArtistImage(artistName: String): String? = withContext(Dispatchers.IO) {
    if (artistName.isBlank()) return@withContext null
    val searchUrl = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
        .addQueryParameter("s", artistName)
        .addQueryParameter("type", "100")
        .addQueryParameter("offset", "0")
        .addQueryParameter("limit", "5")
        .build()
        .toString()
    runCatching {
        parseNeteaseArtistImageUrl(wikiHttpClient().executeNeteaseText(searchUrl), artistName)
    }.getOrNull()
}

private fun fetchNeteaseArtistWiki(
    artistName: String,
    client: OkHttpClient
): LastFmArtistWiki? {
    val searchUrl = "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
        .addQueryParameter("s", artistName)
        .addQueryParameter("type", "100")
        .addQueryParameter("offset", "0")
        .addQueryParameter("limit", "5")
        .build()
        .toString()
    val artistId = parseNeteaseArtistId(client.executeNeteaseText(searchUrl), artistName) ?: return null
    val biographyUrl = "https://music.163.com/api/artist/introduction".toHttpUrl().newBuilder()
        .addQueryParameter("id", artistId)
        .build()
        .toString()
    val text = parseNeteaseArtistBiography(client.executeNeteaseText(biographyUrl))
    if (text.isBlank()) return null
    val pageUrl = "https://y.music.163.com/m/artist?id=$artistId"
    return LastFmArtistWiki(
        text = text,
        artistUrl = pageUrl,
        wikiUrl = pageUrl,
        source = ArtistWikiSource.Netease
    )
}

private fun wikiHttpClient(): OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .followRedirects(true)
    .build()

private fun fetchLastFmArtistWikiFromApi(
    artistName: String,
    region: String,
    apiKey: String,
    client: OkHttpClient
): LastFmArtistWiki {
    for ((autocorrect, ignoreCase) in listOf("0" to false, "1" to true)) {
        val url = LAST_FM_API_ROOT.toHttpUrl().newBuilder()
            .addQueryParameter("method", "artist.getinfo")
            .addQueryParameter("artist", artistName)
            .addQueryParameter("api_key", apiKey)
            .addQueryParameter("lang", region)
            .addQueryParameter("autocorrect", autocorrect)
            .addQueryParameter("format", "json")
            .build()
        parseLastFmArtistGetInfoJson(
            raw = client.executeText(url.toString()),
            regionCode = region,
            requestedArtistName = artistName,
            ignoreCase = ignoreCase
        )?.let { return it }
    }
    error("Last.fm artist.getInfo returned no matching biography")
}

private fun fetchLastFmArtistWikiFromHtml(
    artistName: String,
    region: String,
    client: OkHttpClient
): LastFmArtistWiki {
    // Keep the selected provider explicit. A blocked Last.fm endpoint should not silently switch
    // the biography to another provider.
    val htmlClient = client.newBuilder()
        .connectTimeout(4, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()
    val wikiUrl = lastFmArtistWikiUrl(artistName, region)
    val html = htmlClient.executeText(
        url = wikiUrl,
        acceptLanguage = lastFmAcceptLanguage(region)
    )
    return LastFmArtistWiki(
        text = parseLastFmWikiHtml(html),
        artistUrl = lastFmArtistPageUrl(artistName, region),
        wikiUrl = wikiUrl,
        source = ArtistWikiSource.LastFmHtml
    )
}

private fun fetchWikipediaArtistWiki(
    artistName: String,
    regionCode: String,
    client: OkHttpClient
): LastFmArtistWiki? {
    val language = wikipediaLanguage(regionCode)
    val variant = wikipediaVariant(regionCode)
    val apiRoot = "https://$language.wikipedia.org/w/api.php"
    val searchUrl = apiRoot.toHttpUrl().newBuilder()
        .addQueryParameter("action", "query")
        .addQueryParameter("list", "search")
        .addQueryParameter("srsearch", artistName)
        .addQueryParameter("srlimit", "10")
        .addQueryParameter("format", "json")
        .addQueryParameter("utf8", "1")
        .apply { variant?.let { addQueryParameter("variant", it) } }
        .build()
        .toString()
    val title = parseWikipediaSearchTitle(client.executeText(searchUrl), artistName) ?: artistName
    val extractUrl = apiRoot.toHttpUrl().newBuilder()
        .addQueryParameter("action", "query")
        .addQueryParameter("prop", "extracts")
        .addQueryParameter("exlimit", "1")
        .addQueryParameter("explaintext", "1")
        .addQueryParameter("redirects", "1")
        .addQueryParameter("titles", title)
        .addQueryParameter("format", "json")
        .addQueryParameter("utf8", "1")
        .apply { variant?.let { addQueryParameter("variant", it) } }
        .build()
        .toString()
    val (extract, pageTitle) = parseWikipediaExtract(client.executeText(extractUrl)) ?: return null
    val page = pageTitle.ifBlank { title }
    val encodedPage = page.replace(" ", "_")
    val pageUrl = if (variant != null && language == "zh") {
        "https://zh.wikipedia.org/$variant/$encodedPage"
    } else {
        "https://$language.wikipedia.org/wiki/$encodedPage"
    }
    return LastFmArtistWiki(
        text = extract,
        artistUrl = pageUrl,
        wikiUrl = pageUrl,
        source = ArtistWikiSource.WikipediaSelected
    )
}

private fun OkHttpClient.executeText(
    url: String,
    acceptLanguage: String? = null
): String {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", HALCYON_WIKI_USER_AGENT)
        .apply {
            if (!acceptLanguage.isNullOrBlank()) header("Accept-Language", acceptLanguage)
        }
        .build()
    return newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code} for $url")
        response.body?.string().orEmpty()
    }
}

private fun OkHttpClient.executeNeteaseText(url: String): String {
    val request = Request.Builder()
        .url(url)
        .header("User-Agent", HALCYON_WIKI_USER_AGENT)
        .header("Referer", "https://music.163.com/")
        .build()
    return newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code} for $url")
        response.body?.string().orEmpty()
    }
}

private fun parseArtistOpenGraphImageUrl(html: String): String? {
    val patterns = listOf(
        Regex("""(?is)<meta[^>]+property=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)[\"'][^>]*>"""),
        Regex("""(?is)<meta[^>]+content=[\"']([^\"']+)[\"'][^>]+property=[\"']og:image[\"'][^>]*>""")
    )
    return patterns.asSequence()
        .mapNotNull { pattern -> pattern.find(html)?.groupValues?.getOrNull(1)?.trim() }
        .firstOrNull(::isUsableArtistImageUrl)
}

internal fun isUsableArtistImageUrl(url: String): Boolean {
    if (!url.startsWith("https://", ignoreCase = true) && !url.startsWith("http://", ignoreCase = true)) {
        return false
    }
    val normalized = url.lowercase(Locale.ROOT)
    return PLACEHOLDER_ARTIST_IMAGE_MARKERS.none { marker -> normalized.contains(marker) }
}

private val PLACEHOLDER_ARTIST_IMAGE_MARKERS = listOf(
    "2a96cbd8b46e442fc41c2b86b821562f", // Last.fm empty star
    "4128a6af4f3f309acc450ee652117ac4",
    "c6f59c1e5e7240a4c0d427abd71f3dbb",
    "5639395138885805", // NetEase default avatar
    "109951162844139717",
    "default_avatar",
    "/default/",
    "no-image",
    "noimage"
)

internal fun lastFmApiLanguage(regionCode: String): String {
    val region = normalizeLastFmWikiRegion(regionCode)
    return when {
        region.startsWith("zh") -> "zh"
        region == "ko" -> "en"
        else -> region
    }
}

internal fun lastFmAcceptLanguage(regionCode: String): String = when (normalizeLastFmWikiRegion(regionCode)) {
    "en" -> "en-US,en;q=0.9"
    "zh-hk" -> "zh-HK,zh;q=0.9"
    "zh-tw" -> "zh-TW,zh;q=0.9"
    "ko" -> "ko-KR,ko;q=0.9"
    "de" -> "de-DE,de;q=0.9"
    "es" -> "es-ES,es;q=0.9"
    "fr" -> "fr-FR,fr;q=0.9"
    "it" -> "it-IT,it;q=0.9"
    "ja" -> "ja-JP,ja;q=0.9"
    "pl" -> "pl-PL,pl;q=0.9"
    "pt" -> "pt-PT,pt;q=0.9"
    "ru" -> "ru-RU,ru;q=0.9"
    "sv" -> "sv-SE,sv;q=0.9"
    "tr" -> "tr-TR,tr;q=0.9"
    "zh" -> "zh-CN,zh;q=0.9"
    else -> "en-US,en;q=0.9"
}

private const val LAST_FM_API_ROOT = "https://ws.audioscrobbler.com/2.0/"
private const val HALCYON_WIKI_USER_AGENT =
    "Halcyon/1.2 (https://github.com/Kifranei/Halcyon; artist biographies)"

private fun wikiHtmlBlock(html: String): String? {
    val patterns = listOf(
        Regex("""(?is)<div[^>]*class="[^"]*wiki-content[^"]*"[^>]*>(.*?)</div>"""),
        Regex("""(?is)<div[^>]*id="wiki"[^>]*>(.*?)</div>"""),
        Regex("""(?is)<section[^>]*class="[^"]*wiki[^"]*"[^>]*>(.*?)</section>""")
    )
    patterns.forEach { pattern ->
        pattern.find(html)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }?.let { return it }
    }
    return null
}
