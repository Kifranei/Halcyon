package com.ella.music.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
import okhttp3.Credentials
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import com.ella.music.data.lastfm.fetchLastFmArtistImage
import com.ella.music.data.lastfm.fetchNeteaseArtistImage
import com.ella.music.data.lastfm.spotifyMarketForLastFmRegion

/** Network-backed artist images with a small app-private disk cache. */
internal object ArtistImageRepository {
    private const val CACHE_DIRECTORY = "artist_images"
    private const val MAX_IMAGE_BYTES = 12L * 1024L * 1024L
    private const val FAILED_LOOKUP_TTL_MS = 15 * 60 * 1_000L

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()
    private val artistLocks = ConcurrentHashMap<String, Mutex>()
    private val failedLookups = ConcurrentHashMap<String, Long>()
    private val networkSlots = Semaphore(2)
    @Volatile
    private var spotifyAccessToken: String? = null
    @Volatile
    private var spotifyAccessTokenExpiresAt: Long = 0L

    suspend fun resolve(
        context: Context,
        artistName: String,
        sourceOrder: List<String>,
        lastFmApiKey: String,
        lastFmRegion: String,
        spotifyClientId: String,
        spotifyClientSecret: String
    ): Uri? = withContext(Dispatchers.IO) {
        val normalizedArtist = artistName.trim()
        if (normalizedArtist.isBlank()) return@withContext null
        val sources = SettingsManager.normalizeArtistImageSources(sourceOrder)
        // Source order and region are part of the cache identity.  Otherwise changing the
        // priority in settings would keep returning an image downloaded under the old policy.
        val cacheKey = artistImageCacheKey(
            artistName = normalizedArtist,
            sourceOrder = sources,
            regionCode = lastFmRegion,
            spotifyClientId = spotifyClientId
        )
        val target = cacheFile(context, cacheKey)
        val lock = artistLocks.getOrPut(cacheKey) { Mutex() }
        lock.withLock {
            cachedUri(target)?.let { return@withLock it }
            val now = System.currentTimeMillis()
            if (now - (failedLookups[cacheKey] ?: 0L) < FAILED_LOOKUP_TTL_MS) {
                return@withLock null
            }
            var resolved: Uri? = null
            for (source in sources) {
                val imageUrl = try {
                    networkSlots.withPermit {
                        when (source) {
                            SettingsManager.ARTIST_IMAGE_SOURCE_LASTFM -> fetchLastFmArtistImage(
                                artistName = normalizedArtist,
                                apiKey = lastFmApiKey,
                                regionCode = lastFmRegion
                            )
                            SettingsManager.ARTIST_IMAGE_SOURCE_SPOTIFY -> fetchSpotifyArtistImage(
                                artistName = normalizedArtist,
                                clientId = spotifyClientId,
                                clientSecret = spotifyClientSecret,
                                marketCode = spotifyMarketForLastFmRegion(lastFmRegion)
                            )
                            SettingsManager.ARTIST_IMAGE_SOURCE_NETEASE -> fetchNeteaseArtistImage(normalizedArtist)
                            else -> null
                        }
                    }
                } catch (_: Throwable) {
                    null
                }
                val usableImageUrl = imageUrl?.takeIf(::isUsableImageUrl) ?: continue
                if (downloadImage(usableImageUrl, target)) {
                    resolved = Uri.fromFile(target)
                    break
                }
            }
            if (resolved == null) failedLookups[cacheKey] = now else failedLookups.remove(cacheKey)
            resolved
        }
    }

    private fun cacheFile(context: Context, cacheKey: String): File =
        File(File(context.filesDir, CACHE_DIRECTORY), "$cacheKey.jpg")

    private fun cachedUri(file: File): Uri? =
        file.takeIf { it.isFile && it.length() > 0L }?.let(Uri::fromFile)

    private fun downloadImage(url: String, target: File): Boolean {
        val parent = target.parentFile ?: return false
        if (!parent.exists() && !parent.mkdirs()) return false
        val temporary = File(parent, "${target.name}.part")
        return try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Halcyon/1.2 (artist image cache)")
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    false
                } else {
                    val body = response.body
                    if (body == null) {
                        false
                    } else {
                        val contentType = body.contentType()?.toString().orEmpty()
                        if (contentType.isNotBlank() && !contentType.startsWith("image/", ignoreCase = true)) {
                            false
                        } else if (body.contentLength() > MAX_IMAGE_BYTES) {
                            false
                        } else {
                            var total = 0L
                            val complete = body.byteStream().use { input ->
                                temporary.outputStream().use { output ->
                                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                                    var valid = true
                                    while (valid) {
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        total += read
                                        if (total > MAX_IMAGE_BYTES) {
                                            valid = false
                                        } else {
                                            output.write(buffer, 0, read)
                                        }
                                    }
                                    valid
                                }
                            }
                            if (!complete || total <= 0L) {
                                false
                            } else if (target.exists() && !target.delete()) {
                                false
                            } else {
                                if (!temporary.renameTo(target)) {
                                    temporary.copyTo(target, overwrite = true)
                                    temporary.delete()
                                }
                                target.isFile && target.length() == total
                            }
                        }
                    }
                }
            }
        } catch (_: Throwable) {
            false
        } finally {
            if (temporary.exists()) temporary.delete()
        }
    }

    private fun fetchSpotifyArtistImage(
        artistName: String,
        clientId: String,
        clientSecret: String,
        marketCode: String
    ): String? {
        if (clientId.isBlank() || clientSecret.isBlank()) return null
        val token = spotifyToken(clientId, clientSecret) ?: return null
        val searchUrl = "https://api.spotify.com/v1/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", artistName)
            .addQueryParameter("type", "artist")
            .addQueryParameter("limit", "5")
            .addQueryParameter("market", marketCode)
            .build()
            .toString()
        val request = Request.Builder()
            .url(searchUrl)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", "Halcyon/1.2 (artist image cache)")
            .build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string()?.let { parseSpotifyArtistImageUrl(it, artistName) }
        }
    }

    @Synchronized
    private fun spotifyToken(clientId: String, clientSecret: String): String? {
        val now = System.currentTimeMillis()
        spotifyAccessToken?.takeIf { now < spotifyAccessTokenExpiresAt }?.let { return it }
        val body = FormBody.Builder()
            .add("grant_type", "client_credentials")
            .build()
        val request = Request.Builder()
            .url("https://accounts.spotify.com/api/token")
            .header("Authorization", Credentials.basic(clientId, clientSecret))
            .header("User-Agent", "Halcyon/1.2 (artist image cache)")
            .post(body)
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    null
                } else {
                    val root = JSONObject(response.body?.string().orEmpty())
                    val token = root.optString("access_token").trim()
                    if (token.isBlank()) {
                        null
                    } else {
                        val expiresIn = root.optLong("expires_in", 3_600L).coerceAtLeast(60L)
                        spotifyAccessToken = token
                        spotifyAccessTokenExpiresAt = now + (expiresIn - 30L).coerceAtLeast(30L) * 1_000L
                        token
                    }
                }
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun parseSpotifyArtistImageUrl(raw: String, artistName: String): String? {
        val items = runCatching {
            JSONObject(raw).optJSONObject("artists")?.optJSONArray("items")
        }.getOrNull() ?: return null
        val requested = artistName.trim()
        val candidates = buildList {
            for (index in 0 until items.length()) {
                items.optJSONObject(index)?.let(::add)
            }
        }.sortedWith(compareBy { artist ->
            if (artist.optString("name").trim() == requested) 0 else 1
        })
        return candidates.asSequence()
            .filter { artist -> artist.optString("name").trim().equals(requested, ignoreCase = true) }
            .mapNotNull { artist ->
                artist.optJSONArray("images")
                    ?.optJSONObject(0)
                    ?.optString("url")
                    ?.trim()
                    ?.takeIf(::isUsableImageUrl)
            }
            .firstOrNull()
    }

    private fun artistImageCacheKey(
        artistName: String,
        sourceOrder: List<String>,
        regionCode: String,
        spotifyClientId: String
    ): String {
        val normalizedArtist = normalizeArtistCoverKey(artistName).ifBlank {
            artistName.trim().lowercase(Locale.ROOT)
        }
        val normalized = listOf(
            normalizedArtist,
            sourceOrder.joinToString(","),
            regionCode.trim().lowercase(Locale.ROOT),
            spotifyClientId.trim().lowercase(Locale.ROOT)
        ).joinToString("|")
        return MessageDigest.getInstance("SHA-256")
            .digest(normalized.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(Locale.ROOT, byte) }
    }

    private fun isUsableImageUrl(url: String): Boolean =
        url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
}
