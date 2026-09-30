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
    private val storageLock = Any()
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
        File(context.getExternalFilesDir(null) ?: context.filesDir, CACHE_DIRECTORY).deleteRecursively()
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
        customFolderUri: String = "",
        spotifyRegion: String = spotifyMarketForLastFmRegion(lastFmRegion)
    ): Uri? = resolveDetailed(
        context,
        artistName,
        sourceOrder,
        lastFmApiKey,
        lastFmRegion,
        spotifyClientId,
        spotifyClientSecret,
        downloadFolderUri,
        customFolderUri,
        spotifyRegion
    )?.uri

    suspend fun findCached(
        context: Context,
        artistName: String,
        sourceOrder: List<String>,
        lastFmRegion: String,
        spotifyClientId: String,
        downloadFolderUri: String = "",
        customFolderUri: String = "",
        spotifyRegion: String = spotifyMarketForLastFmRegion(lastFmRegion)
    ): ResolvedArtistImage? = withContext(Dispatchers.IO) {
        val normalizedArtist = artistName.trim()
        if (normalizedArtist.isBlank()) return@withContext null
        val safeName = normalizedArtist.sanitizeExportFileName(fallback = "artist")

        // 1. Check custom / download folder if configured
        val targetFolder = artistImageDownloadFolder(downloadFolderUri, customFolderUri)
        if (targetFolder.isNotBlank()) {
            if (targetFolder.startsWith("content://", ignoreCase = true)) {
                runCatching {
                    val treeUri = Uri.parse(targetFolder)
                    DocumentFile.fromTreeUri(context, treeUri)?.let { root ->
                        val key = normalizeArtistCoverKey(safeName, ignoreCase = false)
                        root.listFiles().mapNotNull { doc ->
                            val match = artistCoverMatch(doc.name.orEmpty(), doc.type, ignoreCase = false)
                            if (doc.isFile && doc.length() > 0 && match?.key == key && match.kind == ArtistCoverKind.Image) doc to match.order else null
                        }.minByOrNull { it.second }?.first?.let { doc ->
                            val source = root.findFile("${doc.name}.source")?.let { sidecar ->
                                runCatching { context.contentResolver.openInputStream(sidecar.uri)?.bufferedReader()?.use { it.readLine() } }.getOrNull()
                            }.orEmpty()
                            return@withContext ResolvedArtistImage(doc.uri, source)
                        }
                    }
                }
            } else {
                val dir = File(targetFolder.removePrefix("file://"))
                if (dir.isDirectory) {
                    matchingArtistImageFiles(dir, safeName).firstOrNull()?.let { file ->
                        return@withContext ResolvedArtistImage(Uri.fromFile(file), readCachedSource(file).orEmpty())
                    }
                }
            }
        }

        // 2. App-specific external files, migrated from the previous private/cache paths.
        val internalDir = downloadDirectory(context)
        if (internalDir.isDirectory) {
            matchingArtistImageFiles(internalDir, safeName).firstOrNull()?.let { file ->
                return@withContext ResolvedArtistImage(Uri.fromFile(file), readCachedSource(file).orEmpty())
            }
            // Keep hash-named cache entries in place with their source sidecars. Renaming to
            // an unreserved artist filename could alias a differently cased name on some volumes.
            val sources = SettingsManager.normalizeArtistImageSources(sourceOrder)
            val cacheKey = artistImageCacheKey(
                artistName = normalizedArtist,
                sourceOrder = sources,
                regionCode = "$lastFmRegion|$spotifyRegion",
                spotifyClientId = spotifyClientId
            )
            val legacy = File(internalDir, "$cacheKey.jpg")
            if (legacy.isFile && legacy.length() > 0L) {
                return@withContext ResolvedArtistImage(Uri.fromFile(legacy), readCachedSource(legacy).orEmpty())
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
        customFolderUri: String = "",
        spotifyRegion: String = spotifyMarketForLastFmRegion(lastFmRegion)
    ): ResolvedArtistImage? = withContext(Dispatchers.IO) {
        val normalizedArtist = artistName.trim()
        if (normalizedArtist.isBlank()) return@withContext null
        val sources = SettingsManager.normalizeArtistImageSources(sourceOrder)
        val cacheKey = artistImageCacheKey(
            artistName = normalizedArtist,
            sourceOrder = sources,
            regionCode = "$lastFmRegion|$spotifyRegion",
            spotifyClientId = spotifyClientId
        )

        findCached(
            context = context,
            artistName = normalizedArtist,
            sourceOrder = sources,
            lastFmRegion = lastFmRegion,
            spotifyClientId = spotifyClientId,
            downloadFolderUri = downloadFolderUri,
            customFolderUri = customFolderUri,
            spotifyRegion = spotifyRegion
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
                customFolderUri = customFolderUri,
                spotifyRegion = spotifyRegion
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
                                marketCode = spotifyRegion
                            )
                            SettingsManager.ARTIST_IMAGE_SOURCE_NETEASE -> fetchNeteaseArtistImage(normalizedArtist)
                            SettingsManager.ARTIST_IMAGE_SOURCE_KUGOU -> fetchChineseArtistImage(client, normalizedArtist, false)
                            SettingsManager.ARTIST_IMAGE_SOURCE_QQ -> fetchChineseArtistImage(client, normalizedArtist, true)
                            SettingsManager.ARTIST_IMAGE_SOURCE_KUWO -> fetchKuwoArtistImage(client, normalizedArtist)
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
        val tempFile = File.createTempFile("artist-dl-", ".tmp", context.cacheDir)
        val downloaded = preferredArtistImageUrls(source, imageUrl).any { downloadImage(it, tempFile) }
        if (!downloaded || !tempFile.isFile || tempFile.length() <= 0L) {
            tempFile.delete()
            return null
        }

        val isPng = isPngImage(tempFile, imageUrl)
        val ext = if (isPng) "png" else "jpg"
        val mimeType = if (isPng) "image/png" else "image/jpeg"
        val safeName = artistName.sanitizeExportFileName(fallback = "artist")
        val targetFolder = artistImageDownloadFolder(downloadFolderUri, customFolderUri)

        return try {
            synchronized(storageLock) {
                if (targetFolder.startsWith("content://", ignoreCase = true)) {
                    val root = DocumentFile.fromTreeUri(context, Uri.parse(targetFolder)) ?: return@synchronized null
                    if (!root.canWrite()) return@synchronized null
                    ensureNoMedia(root)
                    val name = nextArtistImageFileName(safeName, ext, root.listFiles().mapNotNull { it.name })
                    val doc = root.createFile(mimeType, name) ?: return@synchronized null
                    try {
                        val output = context.contentResolver.openOutputStream(doc.uri, "wt") ?: throw java.io.IOException("Cannot write artist image")
                        output.use { out -> tempFile.inputStream().use { it.copyTo(out) } }
                        root.createFile("application/octet-stream", "${doc.name}.source")?.let { metadata ->
                            context.contentResolver.openOutputStream(metadata.uri, "wt")?.use { it.write(source.toByteArray(Charsets.UTF_8)) }
                        }
                        ArtistCoverRepository.getInstance(context).clearCache()
                        ResolvedArtistImage(doc.uri, source)
                    } catch (error: Exception) { doc.delete(); throw error }
                } else {
                    val dir = if (targetFolder.isBlank()) downloadDirectory(context) else File(targetFolder.removePrefix("file://"))
                    if (!dir.isDirectory && !dir.mkdirs()) return@synchronized null
                    ensureNoMedia(dir)
                    var target: File
                    do {
                        target = File(dir, nextArtistImageFileName(safeName, ext, dir.list()?.toList().orEmpty()))
                    } while (!target.createNewFile())
                    try {
                        tempFile.copyTo(target, overwrite = true)
                        writeCachedSource(target, source)
                        ArtistCoverRepository.getInstance(context).clearCache()
                        ResolvedArtistImage(Uri.fromFile(target), source)
                    } catch (error: Exception) { target.delete(); throw error }
                }
            }
        } catch (_: Exception) {
            null
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
        SettingsManager.ARTIST_IMAGE_SOURCE_KUGOU -> R.string.artist_image_source_kugou
        SettingsManager.ARTIST_IMAGE_SOURCE_QQ -> R.string.artist_image_source_qq
        SettingsManager.ARTIST_IMAGE_SOURCE_KUWO -> R.string.artist_image_source_kuwo
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

    internal fun parseSpotifyArtistImageUrl(raw: String, artistName: String): String? {
        val items = runCatching {
            JSONObject(raw).optJSONObject("artists")?.optJSONArray("items")
        }.getOrNull() ?: return null
        val requested = artistName.trim()
        val candidates = buildList {
            for (index in 0 until items.length()) {
                items.optJSONObject(index)?.let(::add)
            }
        }.sortedWith(compareBy { artist ->
            artistNameCaseRank(requested, artist.optString("name").trim())
        })
        return candidates.asSequence()
            .filter { artist -> artist.optString("name").trim().equals(requested, ignoreCase = true) }
            .mapNotNull { artist ->
                val images = artist.optJSONArray("images") ?: return@mapNotNull null
                (0 until images.length()).mapNotNull { images.optJSONObject(it) }
                    .sortedByDescending { it.optLong("width") * it.optLong("height") }
                    .firstNotNullOfOrNull { it.optString("url").trim().takeIf(::isUsableArtistImageUrl) }
            }
            .firstOrNull()
    }

    private fun artistImageCacheKey(
        artistName: String,
        sourceOrder: List<String>,
        regionCode: String,
        spotifyClientId: String
    ): String {
        val normalizedArtist = normalizeArtistCoverKey(artistName, ignoreCase = false).ifBlank { artistName.trim() }
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

    private val migratedDirectories = mutableSetOf<String>()

    @Synchronized internal fun downloadDirectory(context: Context): File {
        val target = File(context.getExternalFilesDir(null) ?: context.filesDir, CACHE_DIRECTORY)
        if (target.absolutePath in migratedDirectories) return target
        if (!target.exists() && !target.mkdirs()) return File(context.filesDir, CACHE_DIRECTORY)
        var complete = true
        for (old in listOf(File(context.filesDir, CACHE_DIRECTORY), File(context.cacheDir, CACHE_DIRECTORY))) {
            if (old.absolutePath == target.absolutePath) continue
            old.listFiles().orEmpty().filter { it.isFile }.forEach { file ->
                val migrated = File(target, file.name)
                val success = runCatching {
                    if (!migrated.exists()) file.copyTo(migrated)
                    if (migrated.length() == file.length()) file.delete() else false
                }.getOrDefault(false)
                if (!success) complete = false
            }
        }
        ensureNoMedia(target)
        if (complete) migratedDirectories += target.absolutePath
        return target
    }
}

internal const val ARTIST_IMAGE_INTERNAL_STORAGE = "app-internal"
internal fun artistImageDownloadFolder(downloadFolder: String, coverFolder: String): String =
    if (downloadFolder == ARTIST_IMAGE_INTERNAL_STORAGE) "" else downloadFolder.trim().ifBlank { coverFolder.trim() }
