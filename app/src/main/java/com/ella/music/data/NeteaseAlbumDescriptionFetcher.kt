package com.ella.music.data

import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

internal object NeteaseAlbumDescriptionFetcher {
    private val client by lazy {
        OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /**
     * Fetches album description from NetEase Cloud Music.
     *
     * 1. If explicit [neteaseAlbumUrl] is provided, extracts album ID from it.
     * 2. Otherwise searches NetEase using album title and artist.
     * 3. Fetches album details from /api/v1/album/{id} and returns non-blank description.
     */
    suspend fun fetchDescription(
        album: Album?,
        songs: List<Song>,
        neteaseAlbumUrl: String? = null
    ): String? = withContext(Dispatchers.IO) {
        val albumId = extractAlbumId(neteaseAlbumUrl)
            ?: searchAlbumId(
                albumTitle = album?.name.orEmpty().ifBlank { songs.firstOrNull()?.album.orEmpty() },
                artistName = album?.artist.orEmpty().ifBlank { songs.firstOrNull()?.artist.orEmpty() }
            )
            ?: return@withContext null

        fetchDescriptionById(albumId)
    }

    private fun extractAlbumId(url: String?): String? {
        if (url.isNullOrBlank()) return null
        val uri = runCatching { url.toHttpUrl() }.getOrNull() ?: return null
        return uri.queryParameter("id")?.takeIf { it.isNotBlank() }
    }

    private fun searchAlbumId(albumTitle: String, artistName: String): String? {
        val cleanTitle = albumTitle.trim()
        if (cleanTitle.isBlank()) return null
        val cleanArtist = artistName.trim().takeUnless { it == "<unknown>" }.orEmpty()

        // Try searching title + artist first, then fall back to just title
        val queries = if (cleanArtist.isNotBlank()) {
            listOf("$cleanTitle $cleanArtist", cleanTitle)
        } else {
            listOf(cleanTitle)
        }

        for (query in queries) {
            val matchedId = doSearchAlbum(query, cleanTitle, cleanArtist)
            if (matchedId != null) return matchedId
        }
        return null
    }

    private fun doSearchAlbum(query: String, expectedTitle: String, expectedArtist: String): String? {
        val searchUrl = runCatching {
            "https://music.163.com/api/search/get/web".toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .addQueryParameter("type", "10") // 10 = album
                .addQueryParameter("offset", "0")
                .addQueryParameter("limit", "10")
                .build()
                .toString()
        }.getOrNull() ?: return null

        val jsonString = runCatching { executeNeteaseGet(searchUrl) }.getOrNull() ?: return null
        val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return null
        val result = root.optJSONObject("result") ?: return null
        val albums = result.optJSONArray("albums") ?: return null
        if (albums.length() == 0) return null

        val normalizedTitle = expectedTitle.lowercase()
        val normalizedArtist = expectedArtist.lowercase()
        var bestId: String? = null

        for (i in 0 until albums.length()) {
            val item = albums.optJSONObject(i) ?: continue
            val id = item.optLong("id", 0L).takeIf { it > 0L }?.toString() ?: continue
            val name = item.optString("name").trim()
            val artists = item.optJSONArray("artists")
            val artistNames = (0 until (artists?.length() ?: 0)).mapNotNull { j ->
                artists?.optJSONObject(j)?.optString("name")?.trim()?.lowercase()
            }

            if (name.equals(expectedTitle, ignoreCase = true)) {
                if (normalizedArtist.isNotBlank() && artistNames.any { it.contains(normalizedArtist) || normalizedArtist.contains(it) }) {
                    return id
                }
                if (bestId == null) bestId = id
            }
        }

        return bestId ?: albums.optJSONObject(0)?.optLong("id", 0L)?.takeIf { it > 0L }?.toString()
    }

    private fun fetchDescriptionById(albumId: String): String? {
        val url = "https://music.163.com/api/v1/album/$albumId"
        val jsonString = runCatching { executeNeteaseGet(url) }.getOrNull() ?: return null
        val root = runCatching { JSONObject(jsonString) }.getOrNull() ?: return null
        val albumObj = root.optJSONObject("album") ?: return null
        val desc = albumObj.optString("description")
            .ifBlank { albumObj.optString("briefDesc") }
            .trim()
        return desc.takeIf { it.isNotBlank() }
    }

    private fun executeNeteaseGet(url: String): String {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Referer", "https://music.163.com/")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("HTTP ${response.code} for $url")
            response.body?.string().orEmpty()
        }
    }
}
