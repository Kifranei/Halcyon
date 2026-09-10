package com.ella.music.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.ella.music.data.sanitizeExportFileName
import com.ella.music.data.ArtistCoverRepository
import com.ella.music.R
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
import com.ella.music.data.lastfm.isUsableArtistImageUrl
import com.ella.music.data.lastfm.spotifyMarketForLastFmRegion

internal data class ResolvedArtistImage(
    val uri: Uri,
    val source: String
)

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

    suspend fun clearDownloadedCache(context: Context) = withContext(Dispatchers.IO) {
        // Current downloads live in filesDir so they survive the platform cache eviction. Also
        // remove the old cacheDir location for installations upgraded from the earlier build.
        File(context.filesDir, CACHE_DIRECTORY).deleteRecursively()
        File(context.cacheDir, CACHE_DIRECTORY).deleteRecursively()
        artistLocks.clear()
        failedLookups.clear()
        spotifyAccessToken = null
        spotifyAccessTokenExpiresAt = 0L
    }

    suspend fun resolve(
        context: Context,
        artistName: String,
        sourceOrder: List<String>,
        lastFmApiKey: String,
        lastFmRegion: String,
        spotifyClientId: String,
        spotifyClientSecret: String,
        downloadFolderUri: String = "",
        customFolderUri: String = ""
    ): Uri? = resolveDetailed(
        context,
        artistName,
        sourceOrder,
        lastFmApiKey,
        lastFmRegion,
        spotifyClientId,
        spotifyClientSecret,
        downloadFolderUri,
        customFolderUri
    )?.uri

    suspend fun findCached(
        context: Context,
        artistName: String,
        sourceOrder: List<String>,
        lastFmRegion: String,
        spotifyClientId: String,
        downloadFolderUri: String = "",
        customFolderUri: String = ""
    ): ResolvedArtistImage? = withContext(Dispatchers.IO) {
        val normalizedArtist = artistName.trim()
        if (normalizedArtist.isBlank()) return@withContext null
        val safeName = normalizedArtist.sanitizeExportFileName(fallback = "artist")

        // 1. Check custom / download folder if configured
        val targetFolder = downloadFolderUri.trim().ifBlank { customFolderUri.trim() }
        if (targetFolder.isNotBlank()) {
            if (targetFolder.startsWith("content://", ignoreCase = true)) {
                runCatching {
                    val treeUri = Uri.parse(targetFolder)
                    DocumentFile.fromTreeUri(context, treeUri)?.let { root ->
                        for (ext in listOf("png", "jpg", "jpeg", "webp")) {
                            root.findFile("$safeName.$ext")?.takeIf { it.isFile && it.length() > 0L }?.let { doc ->
                                return@withContext ResolvedArtistImage(doc.uri, "")
                            }
                        }
                    }
                }
            } else {
                val dir = File(targetFolder.removePrefix("file://"))
                if (dir.isDirectory) {
                    for (ext in listOf("png", "jpg", "jpeg", "webp")) {
                        val f = File(dir, "$safeName.$ext")
                        if (f.isFile && f.length() > 0L) {
                            return@withContext ResolvedArtistImage(Uri.fromFile(f), "")
                        }
                    }
                }
            }
        }

        // 2. Check internal directory context.filesDir/artist_images
        val internalDir = File(context.filesDir, CACHE_DIRECTORY)
        if (internalDir.isDirectory) {
            for (ext in listOf("png", "jpg", "jpeg", "webp")) {
                val f = File(internalDir, "$safeName.$ext")
                if (f.isFile && f.length() > 0L) {
                    return@withContext ResolvedArtistImage(Uri.fromFile(f), readCachedSource(f) ?: "")
                }
            }
            // Check legacy cacheKey.jpg and auto-migrate to safeName.jpg
            val sources = SettingsManager.normalizeArtistImageSources(sourceOrder)
            val cacheKey = artistImageCacheKey(
                artistName = normalizedArtist,
                sourceOrder = sources,
                regionCode = lastFmRegion,
                spotifyClientId = spotifyClientId
            )
            val legacy = File(internalDir, "$cacheKey.jpg")
            if (legacy.isFile && legacy.length() > 0L) {
                val migrated = File(internalDir, "$safeName.jpg")
                if (!migrated.exists()) legacy.renameTo(migrated)
                val finalFile = if (migrated.isFile) migrated else legacy
                return@withContext ResolvedArtistImage(Uri.fromFile(finalFile), readCachedSource(finalFile) ?: "")
            }
        }

        null
    }

    suspend fun resolveDetailed(
        context: Context,
        artistName: String,
        sourceOrder: List<String>,
        lastFmApiKey: String,
        lastFmRegion: String,
        spotifyClientId: String,
        spotifyClientSecret: String,
        downloadFolderUri: String = "",
        customFolderUri: String = ""
    ): ResolvedArtistImage? = withContext(Dispatchers.IO) {
        val normalizedArtist = artistName.trim()
        if (normalizedArtist.isBlank()) return@withContext null
        val sources = SettingsManager.normalizeArtistImageSources(sourceOrder)
        val cacheKey = artistImageCacheKey(
            artistName = normalizedArtist,
            sourceOrder = sources,
            regionCode = lastFmRegion,
            spotifyClientId = spotifyClientId
        )

        findCached(
            context = context,
            artistName = normalizedArtist,
            sourceOrder = sources,
            lastFmRegion = lastFmRegion,
            spotifyClientId = spotifyClientId,
            downloadFolderUri = downloadFolderUri,
            customFolderUri = customFolderUri
        )?.let { return@withContext it }

        val lock = artistLocks.getOrPut(cacheKey) { Mutex() }
        lock.withLock {
            findCached(
                context = context,
                artistName = normalizedArtist,
                sourceOrder = sources,
                lastFmRegion = lastFmRegion,
                spotifyClientId = spotifyClientId,
                downloadFolderUri = downloadFolderUri,
                customFolderUri = customFolderUri
            )?.let { return@withLock it }

            val now = System.currentTimeMillis()
            if (now - (failedLookups[cacheKey] ?: 0L) < FAILED_LOOKUP_TTL_MS) {
                return@withLock null
            }
            var resolved: ResolvedArtistImage? = null
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
                val usableImageUrl = imageUrl?.takeIf(::isUsableArtistImageUrl) ?: continue
                resolved = saveDownloadedArtistImage(
                    context = context,
                    artistName = normalizedArtist,
                    source = source,
                    imageUrl = usableImageUrl,
                    downloadFolderUri = downloadFolderUri,
                    customFolderUri = customFolderUri
                )
                if (resolved != null) {
                    break
                }
            }
            if (resolved == null) failedLookups[cacheKey] = now else failedLookups.remove(cacheKey)
            resolved
        }
    }

    private fun ensureNoMedia(folder: DocumentFile) {
        runCatching {
            if (folder.findFile(".nomedia") == null) {
                folder.createFile("application/octet-stream", ".nomedia")
            }
        }
    }

    private fun ensureNoMedia(folder: File) {
        runCatching {
            if (!folder.exists()) folder.mkdirs()
            val file = File(folder, ".nomedia")
            if (!file.exists()) file.createNewFile()
        }
    }

    private fun isPngImage(file: File, url: String): Boolean {
        if (url.substringBefore('?').endsWith(".png", ignoreCase = true)) return true
        return runCatching {
            file.inputStream().use { stream ->
                val header = ByteArray(8)
                val read = stream.read(header)
                read == 8 &&
                    header[0] == 0x89.toByte() &&
                    header[1] == 0x50.toByte() &&
                    header[2] == 0x4E.toByte() &&
                    header[3] == 0x47.toByte()
            }
        }.getOrDefault(false)
    }

    private fun saveDownloadedArtistImage(
        context: Context,
        artistName: String,
        source: String,
        imageUrl: String,
        downloadFolderUri: String,
        customFolderUri: String
    ): ResolvedArtistImage? {
        val tempFile = File(context.cacheDir, "artist_dl_${System.currentTimeMillis()}.tmp")
        val downloaded = downloadImage(imageUrl, tempFile)
        if (!downloaded || !tempFile.isFile || tempFile.length() <= 0L) {
            tempFile.delete()
            return null
        }

        val isPng = isPngImage(tempFile, imageUrl)
        val ext = if (isPng) "png" else "jpg"
        val mimeType = if (isPng) "image/png" else "image/jpeg"
        val safeName = artistName.sanitizeExportFileName(fallback = "artist")
        val targetFileName = "$safeName.$ext"
        val targetFolder = downloadFolderUri.trim().ifBlank { customFolderUri.trim() }

        return try {
            if (targetFolder.isNotBlank()) {
                if (targetFolder.startsWith("content://", ignoreCase = true)) {
                    val root = DocumentFile.fromTreeUri(context, Uri.parse(targetFolder))
                    if (root != null && root.canWrite()) {
                        ensureNoMedia(root)
                        val existing = root.findFile(targetFileName)
                        val docFile = existing ?: root.createFile(mimeType, targetFileName)
                        if (docFile != null) {
                            context.contentResolver.openOutputStream(docFile.uri, "wt")?.use { out ->
                                tempFile.inputStream().use { input -> input.copyTo(out) }
                            }
                            ArtistCoverRepository.getInstance(context).clearCache()
                            ResolvedArtistImage(docFile.uri, source)
                        } else null
                    } else null
                } else {
                    val dir = File(targetFolder.removePrefix("file://"))
                    if (!dir.exists()) dir.mkdirs()
                    ensureNoMedia(dir)
                    val targetFile = File(dir, targetFileName)
                    if (tempFile.renameTo(targetFile) || runCatching { tempFile.copyTo(targetFile, overwrite = true); tempFile.delete(); true }.getOrDefault(false)) {
                        ArtistCoverRepository.getInstance(context).clearCache()
                        ResolvedArtistImage(Uri.fromFile(targetFile), source)
                    } else null
                }
            } else {
                val dir = File(context.filesDir, CACHE_DIRECTORY)
                if (!dir.exists()) dir.mkdirs()
                ensureNoMedia(dir)
                val targetFile = File(dir, targetFileName)
                if (tempFile.renameTo(targetFile) || runCatching { tempFile.copyTo(targetFile, overwrite = true); tempFile.delete(); true }.getOrDefault(false)) {
                    writeCachedSource(targetFile, source)
                    ResolvedArtistImage(Uri.fromFile(targetFile), source)
                } else null
            }
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }

    private fun sourceFile(imageFile: File): File = File(imageFile.parentFile, "${imageFile.name}.source")

    private fun writeCachedSource(imageFile: File, source: String) {
        runCatching { sourceFile(imageFile).writeText(source) }
    }

    private fun readCachedSource(imageFile: File): String? =
        runCatching { sourceFile(imageFile).takeIf { it.isFile }?.readText()?.trim() }
            .getOrNull()
            ?.takeIf { it.isNotBlank() }

    internal fun sourceLabelRes(source: String): Int? = when (source) {
        SettingsManager.ARTIST_IMAGE_SOURCE_LASTFM -> R.string.artist_image_source_lastfm
        SettingsManager.ARTIST_IMAGE_SOURCE_SPOTIFY -> R.string.artist_image_source_spotify
        SettingsManager.ARTIST_IMAGE_SOURCE_NETEASE -> R.string.artist_image_source_netease
        else -> null
    }

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
                    ?.takeIf(::isUsableArtistImageUrl)
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

}
