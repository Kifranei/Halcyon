package com.ella.music.data.spotify

import com.ella.music.data.model.Song
import com.ella.music.data.splitArtistNames
import java.io.ByteArrayOutputStream
import java.text.Normalizer
import java.util.Locale
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

internal data class SpotifyCanvasTrack(val uri: String, val title: String, val artists: List<String>, val album: String, val duration: Long)
internal fun normalizeSpotifyTrackText(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
    .lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim().replace(Regex("\\s+"), " ")

internal fun selectSpotifyCanvasTrack(raw: String, song: Song, pathfinder: Boolean): SpotifyCanvasTrack? {
    val root = runCatching { JSONObject(raw) }.getOrNull() ?: return null
    val items = if (pathfinder) root.optJSONObject("data")?.optJSONObject("searchV2")?.optJSONObject("tracksV2")?.optJSONArray("items")
        else root.optJSONObject("tracks")?.optJSONArray("items")
    val candidates = (0 until (items?.length() ?: 0)).mapNotNull { index ->
        var track = items?.optJSONObject(index) ?: return@mapNotNull null
        if (pathfinder) track = track.optJSONObject("item")?.optJSONObject("data") ?: return@mapNotNull null
        val uri = track.optString("uri").ifBlank { track.optString("id").let { "spotify:track:$it" } }
        if (!Regex("spotify:track:[A-Za-z0-9]{22}").matches(uri)) return@mapNotNull null
        val artists = if (pathfinder) track.optJSONObject("artists")?.optJSONArray("items") else track.optJSONArray("artists")
        val names = (0 until (artists?.length() ?: 0)).mapNotNull {
            val artist = artists?.optJSONObject(it) ?: return@mapNotNull null
            (if (pathfinder) artist.optJSONObject("profile")?.optString("name") else artist.optString("name"))?.takeIf(String::isNotBlank)
        }
        SpotifyCanvasTrack(uri, track.optString("name"), names,
            (if (pathfinder) track.optJSONObject("albumOfTrack") else track.optJSONObject("album"))?.optString("name").orEmpty(),
            if (pathfinder) track.optJSONObject("duration")?.optLong("totalMilliseconds") ?: 0 else track.optLong("duration_ms"))
    }
    val wantedArtists = splitArtistNames(song.artist).map(::normalizeSpotifyTrackText).filter(String::isNotBlank)
    if (wantedArtists.isEmpty()) return null
    return candidates.filter { candidate ->
        normalizeSpotifyTrackText(candidate.title) == normalizeSpotifyTrackText(song.title) &&
            wantedArtists.all { artist -> candidate.artists.any { normalizeSpotifyTrackText(it) == artist } } &&
            (song.duration <= 0 || candidate.duration <= 0 || kotlin.math.abs(song.duration - candidate.duration) <= 10_000)
    }.minByOrNull { if (normalizeSpotifyTrackText(it.album) == normalizeSpotifyTrackText(song.album)) 0 else 1 }
}

internal fun safeSpotifyCanvasVideoUrl(raw: String?): String? {
    val url = raw?.toHttpUrlOrNull() ?: return null
    if (url.scheme != "https" || url.username.isNotBlank() || url.password.isNotBlank() || url.port != 443) return null
    if (url.host != "scdn.co" && !url.host.endsWith(".scdn.co")) return null
    if (!url.encodedPath.endsWith(".mp4", ignoreCase = true)) return null
    return url.toString()
}

internal fun spotifyQueryBody(operation: String, variables: JSONObject, hash: String): String = JSONObject()
    .put("operationName", operation).put("variables", variables)
    .put("extensions", JSONObject().put("persistedQuery", JSONObject().put("version", 1).put("sha256Hash", hash))).toString()

internal fun parseSpotifyCanvasQuery(raw: String): String? = runCatching {
    val canvas = JSONObject(raw).optJSONObject("data")?.optJSONObject("trackUnion")?.optJSONObject("canvas")
    safeSpotifyCanvasVideoUrl(canvas?.optString("url"))
}.getOrNull()

internal fun spotifyPersistedQueryHashes(script: String): Map<String, String> =
    Regex("""["'](canvas|searchTracks)["']\s*,\s*["']query["']\s*,\s*["']([0-9a-f]{64})["']""")
        .findAll(script).associate { it.groupValues[1] to it.groupValues[2] }

internal fun spotifyWebPlayerScriptUrls(html: String): List<String> {
    val urls = Regex("""https://open\.spotifycdn\.com/cdn/build/web-player/[A-Za-z0-9._~/-]+\.js""")
        .findAll(html).map { it.value }.toMutableList()
    val block = Regex("""(?is)<script\b[^>]*\bid=["']__CDN_FILE_URLS__["'][^>]*>(.*?)</script>""").find(html)?.groupValues?.get(1)
    if (block != null) runCatching { java.util.Base64.getDecoder().decode(block).toString(Charsets.UTF_8) }.getOrNull()?.let { decoded ->
        urls += Regex("""https://open\.spotifycdn\.com/cdn/build/web-player/[A-Za-z0-9._~/-]+\.js""").findAll(decoded).map { it.value }
    }
    val priorities = listOf("web-player.", "xpui-routes-search.", "xpui-routes-track.", "vendor~web-player.")
    return urls.distinct().sortedBy { url ->
        priorities.indexOfFirst { url.substringAfterLast('/').startsWith(it) }.takeIf { it >= 0 } ?: priorities.size
    }
}

internal fun encodeSpotifyCanvasRequest(uri: String): ByteArray {
    fun field(bytes: ByteArray): ByteArray = ByteArrayOutputStream().apply {
        write(10)
        var length = bytes.size
        while (length > 127) { write((length and 127) or 128); length = length ushr 7 }
        write(length); write(bytes)
    }.toByteArray()
    return field(field(uri.toByteArray(Charsets.UTF_8)))
}

/** Bounded protobuf reader: unknown fields are skipped, and unrelated tracks never supply a clip. */
internal fun decodeSpotifyCanvasResponse(bytes: ByteArray, uri: String): String? = runCatching {
    val records = ProtoReader(bytes).fields().filter { it.first == 1 }.map { ProtoReader(it.second).fields() }
    val hits = records.mapNotNull { fields ->
        val url = safeSpotifyCanvasVideoUrl(fields.firstOrNull { it.first == 2 }?.second?.toString(Charsets.UTF_8)) ?: return@mapNotNull null
        val track = fields.firstOrNull { it.first == 5 }?.second?.toString(Charsets.UTF_8)
        track to url
    }
    hits.firstOrNull { it.first == uri }?.second ?: hits.singleOrNull()?.takeIf { it.first.isNullOrBlank() }?.second
}.getOrNull()

private class ProtoReader(private val bytes: ByteArray) {
    private var position = 0
    private fun varint(): Int {
        var result = 0L
        for (index in 0..9) {
            require(position < bytes.size)
            val byte = bytes[position++].toInt() and 255
            if (index < 5) result = result or ((byte and 127).toLong() shl (index * 7))
            if (byte < 128) return result.toInt()
        }
        error("Malformed protobuf")
    }
    fun fields(): List<Pair<Int, ByteArray>> = buildList {
        while (position < bytes.size) {
            val tag = varint()
            require(tag > 0)
            val type = tag and 7
            val length = when (type) { 0 -> { varint(); 0 }; 1 -> 8; 2 -> varint(); 5 -> 4; else -> error("Unsupported protobuf field") }
            require(length >= 0 && length <= bytes.size - position)
            if (type == 2) add((tag ushr 3) to bytes.copyOfRange(position, position + length))
            position += length
        }
    }
}
