package com.ella.music.data.lastfm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal data class LastFmArtistWiki(
    val text: String,
    val artistUrl: String,
    val wikiUrl: String
)

/** Wiki language switcher. English, Simplified Chinese and Japanese stay first. */
internal data class LastFmWikiRegion(
    val code: String,
    val countryNameRes: Int
)

internal val LAST_FM_WIKI_REGIONS: List<LastFmWikiRegion> = listOf(
    LastFmWikiRegion("en", com.ella.music.R.string.artist_biography_country_us),
    LastFmWikiRegion("zh", com.ella.music.R.string.artist_biography_country_cn),
    LastFmWikiRegion("ja", com.ella.music.R.string.artist_biography_country_jp),
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

internal fun normalizeLastFmWikiRegion(code: String?): String {
    val normalized = code?.trim()?.lowercase(Locale.ROOT).orEmpty()
    return LAST_FM_WIKI_REGIONS.firstOrNull { it.code == normalized }?.code
        ?: DEFAULT_LAST_FM_WIKI_REGION
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
    regionCode: String = DEFAULT_LAST_FM_WIKI_REGION
): LastFmArtistWiki? {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    if (root.has("error")) return null
    val artist = root.optJSONObject("artist") ?: return null
    val bio = artist.optJSONObject("bio") ?: return null
    val content = bio.optString("content").ifBlank { bio.optString("summary") }
    val text = htmlToPlainWikiText(content)
    if (text.isBlank()) return null
    val name = artist.optString("name").ifBlank { "unknown" }
    val artistUrl = artist.optString("url").ifBlank { lastFmArtistPageUrl(name, regionCode) }
    return LastFmArtistWiki(
        text = text,
        artistUrl = artistUrl,
        wikiUrl = lastFmArtistWikiUrl(name, regionCode)
    )
}

internal fun wikipediaLanguage(regionCode: String): String =
    normalizeLastFmWikiRegion(regionCode)

internal fun parseWikipediaSearchTitle(raw: String): String? {
    val search = runCatching { JSONObject(raw) }.getOrNull()
        ?.optJSONObject("query")
        ?.optJSONArray("search")
        ?: return null
    if (search.length() == 0) return null
    return search.optJSONObject(0)?.optString("title")?.takeIf { it.isNotBlank() }
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
    apiKey: String? = null
): LastFmArtistWiki = withContext(Dispatchers.IO) {
    val region = normalizeLastFmWikiRegion(regionCode)
    val client = wikiHttpClient()
    val errors = mutableListOf<Throwable>()

    // Pano Scrobbler-style: Last.fm 2.0 API on ws.audioscrobbler.com, which is reachable in
    // mainland China when www.last.fm HTML is not.
    if (!apiKey.isNullOrBlank()) {
        runCatching { fetchLastFmArtistWikiFromApi(artistName, region, apiKey, client) }
            .onSuccess { if (it.text.isNotBlank()) return@withContext it }
            .onFailure(errors::add)
    }

    runCatching { fetchLastFmArtistWikiFromHtml(artistName, region, client) }
        .onSuccess { if (it.text.isNotBlank()) return@withContext it }
        .onFailure(errors::add)

    runCatching { fetchWikipediaArtistWiki(artistName, region, client) }
        .onSuccess { wiki -> if (wiki != null && wiki.text.isNotBlank()) return@withContext wiki }
        .onFailure(errors::add)

    if (region != DEFAULT_LAST_FM_WIKI_REGION) {
        runCatching { fetchWikipediaArtistWiki(artistName, DEFAULT_LAST_FM_WIKI_REGION, client) }
            .onSuccess { wiki -> if (wiki != null && wiki.text.isNotBlank()) return@withContext wiki }
            .onFailure(errors::add)
    }

    if (errors.isNotEmpty()) throw errors.first()
    LastFmArtistWiki(
        text = "",
        artistUrl = lastFmArtistPageUrl(artistName, region),
        wikiUrl = lastFmArtistWikiUrl(artistName, region)
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
    val url = LAST_FM_API_ROOT.toHttpUrl().newBuilder()
        .addQueryParameter("method", "artist.getinfo")
        .addQueryParameter("artist", artistName)
        .addQueryParameter("api_key", apiKey)
        .addQueryParameter("lang", region)
        .addQueryParameter("autocorrect", "1")
        .addQueryParameter("format", "json")
        .build()
    val raw = client.executeText(url.toString())
    return parseLastFmArtistGetInfoJson(raw, region)
        ?: error("Last.fm artist.getInfo returned no biography")
}

private fun fetchLastFmArtistWikiFromHtml(
    artistName: String,
    region: String,
    client: OkHttpClient
): LastFmArtistWiki {
    // www.last.fm is often blocked in mainland China; fail fast so Wikipedia can take over.
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
        wikiUrl = wikiUrl
    )
}

private fun fetchWikipediaArtistWiki(
    artistName: String,
    regionCode: String,
    client: OkHttpClient
): LastFmArtistWiki? {
    val language = wikipediaLanguage(regionCode)
    val apiRoot = "https://$language.wikipedia.org/w/api.php"
    val searchUrl = apiRoot.toHttpUrl().newBuilder()
        .addQueryParameter("action", "query")
        .addQueryParameter("list", "search")
        .addQueryParameter("srsearch", artistName)
        .addQueryParameter("srlimit", "1")
        .addQueryParameter("format", "json")
        .addQueryParameter("utf8", "1")
        .build()
        .toString()
    val title = parseWikipediaSearchTitle(client.executeText(searchUrl)) ?: artistName
    val extractUrl = apiRoot.toHttpUrl().newBuilder()
        .addQueryParameter("action", "query")
        .addQueryParameter("prop", "extracts")
        .addQueryParameter("exlimit", "1")
        .addQueryParameter("explaintext", "1")
        .addQueryParameter("redirects", "1")
        .addQueryParameter("titles", title)
        .addQueryParameter("format", "json")
        .addQueryParameter("utf8", "1")
        .build()
        .toString()
    val (extract, pageTitle) = parseWikipediaExtract(client.executeText(extractUrl)) ?: return null
    val page = pageTitle.ifBlank { title }
    val pageUrl = "https://$language.wikipedia.org/wiki/${page.replace(" ", "_")}"
    return LastFmArtistWiki(
        text = extract,
        artistUrl = pageUrl,
        wikiUrl = pageUrl
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

internal fun lastFmAcceptLanguage(regionCode: String): String = when (normalizeLastFmWikiRegion(regionCode)) {
    "en" -> "en-US,en;q=0.9"
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
