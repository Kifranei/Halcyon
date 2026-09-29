package com.ella.music.data

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal fun fetchChineseArtistImage(client: OkHttpClient, name: String, qq: Boolean): String? {
    fun get(url: String): JSONObject? = client.newCall(
        Request.Builder().url(url).header("User-Agent", "Mozilla/5.0")
            .header("Referer", if (qq) "https://y.qq.com/" else "https://www.kugou.com/").build()
    ).execute().use { response ->
        if (response.isSuccessful) response.body?.string()?.let(::JSONObject) else null
    }
    val url = (if (qq) "https://c.y.qq.com/soso/fcgi-bin/client_search_cp"
        else "https://msearch.kugou.com/api/v3/search/singer").toHttpUrl().newBuilder()
        .addQueryParameter(if (qq) "w" else "keyword", name)
        .addQueryParameter("format", "json").addQueryParameter("t", "9")
        .addQueryParameter("page", "1").addQueryParameter("n", "20").build()
    val result = get(url.toString()) ?: return null
    val singers = if (qq) result.optJSONObject("data")?.optJSONObject("singer")?.optJSONArray("list")
        else result.optJSONArray("data")
    singers ?: return null
    val candidates = (0 until singers.length()).mapNotNull { singers.optJSONObject(it) }
    val names = candidates.map { it.optString(if (qq) "singerName" else "singername") }
    val match = candidates.getOrNull(preferredArtistImageMatch(name, names)) ?: return null
    val image = if (qq) match.optString("singerPic") else {
        val id = match.optLong("singerid").takeIf { it > 0 } ?: return null
        get("https://mobileservice.kugou.com/api/v3/singer/info?singerid=$id")
            ?.optJSONObject("data")?.optString("imgurl").orEmpty().replace("{size}", "400")
    }
    return image.replaceFirst("http://", "https://").takeIf { it.startsWith("https://") }
}

/**
 * Kuwo artist search (search.kuwo.cn/r.s). The list carries a relative "PICPATH" under
 * "BASEPICPATH" (…/starheads/240/…); the CDN also serves 500 and 1000 px variants of the same path.
 */
internal fun fetchKuwoArtistImage(client: OkHttpClient, name: String): String? {
    val url = "https://search.kuwo.cn/r.s".toHttpUrl().newBuilder()
        .addQueryParameter("all", name).addQueryParameter("ft", "artist").addQueryParameter("itemset", "artist")
        .addQueryParameter("client", "kt").addQueryParameter("pn", "0").addQueryParameter("rn", "20")
        .addQueryParameter("rformat", "json").addQueryParameter("encoding", "utf8").build()
    val result = client.newCall(
        Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").header("Referer", "https://www.kuwo.cn/").build()
    ).execute().use { response ->
        if (response.isSuccessful) JSONObject(decodeMusicMetadata(response.body?.bytes() ?: return null)) else null
    } ?: return null
    val artists = result.optJSONArray("abslist") ?: return null
    val candidates = (0 until artists.length()).mapNotNull { artists.optJSONObject(it) }
    val names = candidates.map { cleanMusicMetadata(it.optString("ARTIST")) }
    val match = candidates.getOrNull(preferredArtistImageMatch(name, names)) ?: return null
    return kuwoArtistImageUrl(result.optString("BASEPICPATH"), match.optString("PICPATH"))
}

internal fun kuwoArtistImageUrl(basePath: String, picPath: String, size: Int = 1000): String? {
    val relative = picPath.trim().trimStart('/')
    if (relative.isBlank()) return null
    val base = basePath.trim().ifBlank { "https://img1.kuwo.cn/star/starheads/" }.let { if (it.endsWith('/')) it else "$it/" }
    val sized = relative.replaceFirst(Regex("""^\d+/"""), "$size/")
    return (base + sized).replaceFirst("http://", "https://").takeIf { it.startsWith("https://") }
}

internal fun artistImageNameMatches(query: String, candidate: String): Boolean {
    fun normalize(value: String) = value.trim().lowercase(java.util.Locale.ROOT)
    val target = normalize(query)
    return target.isNotEmpty() && (normalize(candidate) == target ||
        normalize(candidate.substringBefore('(').substringBefore('（')) == target)
}

internal fun preferredArtistImageMatch(query: String, names: List<String>): Int {
    val target = query.trim()
    if (target.isEmpty()) return -1
    val exact = names.indexOfFirst { it.trim() == target }
    if (exact >= 0) return exact
    val decorated = names.indexOfFirst { it.substringBefore('(').substringBefore('（').trim() == target }
    if (decorated >= 0) return decorated
    return names.indexOfFirst { artistImageNameMatches(target, it) }
}
