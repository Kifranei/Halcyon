package com.ella.music.data.lastfm

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal data class LastFmArtistWiki(
    val text: String,
    val artistUrl: String,
    val wikiUrl: String
)

/** Official Last.fm site language switcher, in the order shown on last.fm. */
internal data class LastFmWikiRegion(
    val code: String,
    val nativeName: String
)

internal val LAST_FM_WIKI_REGIONS: List<LastFmWikiRegion> = listOf(
    LastFmWikiRegion("en", "English"),
    LastFmWikiRegion("de", "Deutsch"),
    LastFmWikiRegion("es", "Español"),
    LastFmWikiRegion("fr", "Français"),
    LastFmWikiRegion("it", "Italiano"),
    LastFmWikiRegion("ja", "日本語"),
    LastFmWikiRegion("pl", "Polski"),
    LastFmWikiRegion("pt", "Português"),
    LastFmWikiRegion("ru", "Русский"),
    LastFmWikiRegion("sv", "Svenska"),
    LastFmWikiRegion("tr", "Türkçe"),
    LastFmWikiRegion("zh", "简体中文")
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
    val text = wikiBlock
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
    locale: Locale
): LastFmArtistWiki = fetchLastFmArtistWiki(artistName, lastFmLanguagePrefix(locale).ifBlank { "en" })

internal suspend fun fetchLastFmArtistWiki(
    artistName: String,
    regionCode: String
): LastFmArtistWiki = withContext(Dispatchers.IO) {
    val region = normalizeLastFmWikiRegion(regionCode)
    val wikiUrl = lastFmArtistWikiUrl(artistName, region)
    val artistUrl = lastFmArtistPageUrl(artistName, region)
    val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    val request = Request.Builder()
        .url(wikiUrl)
        .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) Halcyon/1.2")
        .header("Accept-Language", lastFmAcceptLanguage(region))
        .build()
    val html = client.newCall(request).execute().use { response ->
        response.body?.string().orEmpty()
    }
    LastFmArtistWiki(
        text = parseLastFmWikiHtml(html),
        artistUrl = artistUrl,
        wikiUrl = wikiUrl
    )
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
