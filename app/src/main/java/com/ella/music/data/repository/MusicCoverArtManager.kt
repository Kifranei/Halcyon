package com.ella.music.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import android.util.LruCache
import com.ella.music.data.isContentAudioSource
import com.ella.music.data.isHttpAudioSource
import com.ella.music.data.isMediaStoreAlbumArtworkUri
import com.ella.music.data.model.Song
import com.ella.music.data.metadata.AudioTagRepository
import com.ella.music.data.SettingsManager
import okhttp3.OkHttpClient
import java.io.File
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap

/**
 * Library surfaces (list rows, two-column rows, grid cards) all render at or below this size, so
 * a single decoded "master" bitmap per song can serve every layout: grid-sized requests decode
 * once at this size and smaller rows derive from the master with [Bitmap.createScaledBitmap].
 * Switching library display modes therefore never re-reads the artwork source.
 */
private const val MASTER_COVER_SIZE = 512

internal class MusicCoverArtManager(
    private val context: Context,
    private val audioTagRepository: AudioTagRepository,
    private val settingsManager: SettingsManager,
    private val httpClient: OkHttpClient,
    private val remoteAudioCacheDir: File,
    private val remoteMetadataHeaderCacheDir: File
) {
    private sealed class CoverDataState {
        data object Found : CoverDataState()
        data object Missing : CoverDataState()
        data class Error(val message: String?) : CoverDataState()
    }

    private val coverArtCache = object : LruCache<String, ByteArray>(8 * 1024) {
        override fun sizeOf(key: String, value: ByteArray): Int = value.size / 1024
    }
    // Large enough to keep per-song master covers plus the derived list thumbnails alive while
    // the user flips between library layouts; without the headroom grid decoding evicted every
    // list entry and each layout switch reloaded the same covers.
    private val coverBitmapCache = object : LruCache<String, Bitmap>(64 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }
    // Sidecar discovery walks directories and stats many candidate files; memoizing it per song
    // keeps repeated bitmap-cache hits free of disk IO.
    private val sidecarFileMemo = ConcurrentHashMap<String, Optional<File>>()

    private fun songSidecarFile(
        song: Song
    ): File? {
        val memoKey = "${song.coverDataCacheKey()}:sidecar:named"
        sidecarFileMemo[memoKey]?.let { return it.orElse(null) }
        val file = song.songNamedSidecarCoverFile()
        sidecarFileMemo[memoKey] = Optional.ofNullable(file)
        return file
    }
    private val coverArtLock = Any()
    private val coverDataStates = ConcurrentHashMap<String, CoverDataState>()

    fun getCoverArt(song: Song): ByteArray? {
        val cacheKey = song.coverDataCacheKey()
        coverArtCache.get(cacheKey)?.let { return it }
        when (coverDataStates[cacheKey]) {
            // Missing artwork is cheap to re-check, and treating a temporary provider/read
            // failure as permanent is what made every library cell become a default cover (#172).
            CoverDataState.Missing -> Unit
            is CoverDataState.Error -> Unit
            CoverDataState.Found, null -> Unit
        }
        synchronized(coverArtLock) {
            coverArtCache.get(cacheKey)?.let { return it }
            val metadataPath = song.effectiveLocalPathForMetadataBlocking(settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
            val shouldPersistFailureState = !(song.isWebDavRemoteSong() && metadataPath == song.path)
            var transientFailure = false
            val art = try {
                if (song.isWebDavRemoteSong() && metadataPath == song.path) {
                    null
                } else {
                    audioTagRepository.readEmbeddedCoverDataBlocking(metadataPath)
                        ?: if (metadataPath.isHttpAudioSource()) null
                        else readEmbeddedPictureWithRetriever(metadataPath)
                }
            } catch (error: Throwable) {
                if (error is OutOfMemoryError) {
                    transientFailure = true
                    clearArtworkCachesAfterOom()
                }
                Log.w("MusicRepo", "Failed to extract cover art for ${song.path}", error)
                if (shouldPersistFailureState && !transientFailure) {
                    coverDataStates[cacheKey] = CoverDataState.Error(error.message)
                }
                null
            }
            if (art != null) {
                coverArtCache.put(cacheKey, art)
                coverDataStates[cacheKey] = CoverDataState.Found
            } else if (shouldPersistFailureState && !transientFailure) {
                coverDataStates[cacheKey] = CoverDataState.Missing
            }
            return art
        }
    }

    fun getCoverArtBitmap(
        song: Song,
        maxSize: Int = 512,
        usage: CoverUsage = CoverUsage.ListThumbnail
    ): Bitmap? {
        val targetSize = maxSize.coerceIn(64, 3000)
        val sidecar = songSidecarFile(song)
        val sidecarStamp = sidecar?.let { "${it.absolutePath}:${it.lastModified()}:${it.length()}" } ?: "none"
        val exactKey = "${song.coverDataCacheKey()}:sidecar=$sidecarStamp:${usage.name}:v2:$targetSize"
        coverBitmapCache.get(exactKey)?.let { return it }
        return synchronized(coverArtLock) {
            coverBitmapCache.get(exactKey)?.let { return it }
            if (targetSize <= MASTER_COVER_SIZE) {
                // Library covers share one master bitmap per song: any layout at or below the
                // master size derives from it, so switching layouts never decodes the same
                // artwork twice.
                val masterKey = "${song.coverDataCacheKey()}:sidecar=$sidecarStamp:v2"
                var master = coverBitmapCache.get(masterKey)
                if (master == null) {
                    master = decodeCoverMaster(song, sidecar)
                    if (master != null) coverBitmapCache.put(masterKey, master)
                }
                if (master == null) return null
                if (targetSize == MASTER_COVER_SIZE) return master
                val scaled = scaleCoverBitmap(master, targetSize)
                if (scaled !== master) coverBitmapCache.put(exactKey, scaled)
                return scaled
            }
            decodeCoverBitmapDirect(song, sidecar, targetSize, usage, exactKey)
        }
    }

    /**
     * One resolution order for every surface: song-named sidecar, embedded picture,
     * folder `cover.jpg`, then MediaStore album art. List thumbnails must not skip
     * embedded artwork in favor of a shared album URI.
     */
    private fun decodeCoverMaster(song: Song, sidecar: File?): Bitmap? {
        if (sidecar != null) {
            decodeBitmapFile(sidecar, MASTER_COVER_SIZE, Bitmap.Config.ARGB_8888)?.let { return it }
        }
        getCoverArt(song)?.let { data ->
            decodeCoverDataToBitmap(data, MASTER_COVER_SIZE)?.let { return it }
        }
        song.folderAlbumCoverFile()?.let { folderCover ->
            decodeBitmapFile(folderCover, MASTER_COVER_SIZE, Bitmap.Config.ARGB_8888)?.let { return it }
        }
        return getSharedAlbumArtBitmap(song.albumId)
    }

    /** Full-size decode for surfaces above master size (player, metadata editor, notification). */
    private fun decodeCoverBitmapDirect(
        song: Song,
        sidecar: File?,
        targetSize: Int,
        usage: CoverUsage,
        exactKey: String
    ): Bitmap? {
        val config = if (usage == CoverUsage.ListThumbnail) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
        if (sidecar != null) {
            decodeBitmapFile(sidecar, targetSize, config)
                ?.also { coverBitmapCache.put(exactKey, it) }
                ?.let { return it }
        }
        getCoverArt(song)?.let { data ->
            decodeCoverDataToBitmap(data, targetSize)
                ?.also { coverBitmapCache.put(exactKey, it) }
                ?.let { return it }
        }
        song.folderAlbumCoverFile()?.let { folderCover ->
            decodeBitmapFile(folderCover, targetSize, config)
                ?.also { coverBitmapCache.put(exactKey, it) }
                ?.let { return it }
        }
        return decodeAlbumArtBitmap(song.albumId, targetSize, usage)
    }

    private fun decodeCoverDataToBitmap(data: ByteArray, targetSize: Int): Bitmap? {
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) sampleSize *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            BitmapFactory.decodeByteArray(data, 0, data.size, options)
        }.getOrElse { error ->
            if (error is OutOfMemoryError) {
                clearArtworkCachesAfterOom()
            }
            Log.w("MusicRepo", "Failed to decode cover bitmap", error)
            null
        }
    }

    /** Downscales a master cover proportionally; returns the source when already small enough. */
    private fun scaleCoverBitmap(source: Bitmap, targetSize: Int): Bitmap {
        val maxSide = maxOf(source.width, source.height)
        if (targetSize <= 0 || maxSide <= targetSize) return source
        val scale = targetSize.toFloat() / maxSide
        val width = (source.width * scale).toInt().coerceAtLeast(1)
        val height = (source.height * scale).toInt().coerceAtLeast(1)
        return runCatching { Bitmap.createScaledBitmap(source, width, height, true) }.getOrDefault(source)
    }

    /** Album-level artwork decoded once at master size and shared by every song in the album. */
    private fun getSharedAlbumArtBitmap(albumId: Long): Bitmap? {
        if (albumId <= 0L) return null
        val albumCacheKey = "album:$albumId:m"
        coverBitmapCache.get(albumCacheKey)?.let { return it }
        val albumArtUri = getAlbumArtUri(albumId) ?: return null
        val decoded = runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(albumArtUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > MASTER_COVER_SIZE || (bounds.outHeight / sampleSize) > MASTER_COVER_SIZE) sampleSize *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(albumArtUri)?.use { BitmapFactory.decodeStream(it, null, options) }
        }.getOrElse { error ->
            if (error is OutOfMemoryError) clearArtworkCachesAfterOom()
            Log.d("MusicRepo", "Failed to decode album art bitmap for albumId=$albumId", error)
            null
        }
        if (decoded != null) coverBitmapCache.put(albumCacheKey, decoded)
        return decoded
    }

    /**
     * Returns the unscaled source for detail surfaces and export. Keeping this separate from
     * [getCoverArtBitmap] prevents a rendering bitmap from being mistaken for the source image.
     */
    fun getOriginalCoverModel(song: Song): Any? {
        // Online artwork is the actual source for remote songs. For local files prefer embedded
        // bytes, but do not let a stale embedded thumbnail replace an explicitly supplied cover.
        return song.coverUrl.takeIf {
            it.isNotBlank() && !it.isMediaStoreAlbumArtworkUri()
        }
            ?: song.songNamedSidecarCoverFile()
            ?: getCoverArt(song)
            ?: song.folderAlbumCoverFile()
            ?: readableAlbumArtUri(song.albumId)
    }

    /**
     * Original artwork source for artist detail headers. Do not return a shared external
     * thumbnail here: those files can be stale or partially written while MediaStore is
     * rebuilding its cache, and Coil then never reaches the embedded/album fallback.
     */
    fun getArtistCoverModel(song: Song): Any? {
        return song.coverUrl.takeIf {
            it.isNotBlank() && !it.isMediaStoreAlbumArtworkUri()
        }
            ?: song.songNamedSidecarCoverFile()
            ?: getCoverArt(song)
            ?: song.folderAlbumCoverFile()
            ?: readableAlbumArtUri(song.albumId)
    }

    fun getAlbumArtUri(albumId: Long): Uri? {
        return mediaStoreAlbumArtUri(albumId)
    }

    private fun readableAlbumArtUri(albumId: Long): Uri? {
        val uri = getAlbumArtUri(albumId) ?: return null
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { }
            uri
        }.getOrNull()
    }

    fun clearCache() {
        coverArtCache.evictAll()
        coverBitmapCache.evictAll()
        coverDataStates.clear()
        sidecarFileMemo.clear()
    }

    private fun clearArtworkCachesAfterOom() {
        coverArtCache.evictAll()
        coverBitmapCache.evictAll()
        // OOM is a transient decode failure. Never leave permanent Missing/Error sentinels.
        coverDataStates.clear()
        sidecarFileMemo.clear()
    }

    fun clearMetadataCache(song: Song) {
        val keyPrefix = song.coverCacheKey()
        coverDataStates.keys.removeAll { it.startsWith(keyPrefix) }
        coverArtCache.remove(song.coverDataCacheKey())
        val bitmapKeyPrefix = "${song.coverDataCacheKey()}:"
        val bitmapKeys = mutableListOf<String>()
        synchronized(coverArtLock) {
            for (key in coverBitmapCache.snapshot().keys) {
                if (key.startsWith(bitmapKeyPrefix)) bitmapKeys += key
            }
            bitmapKeys.forEach(coverBitmapCache::remove)
        }
    }

    private fun readEmbeddedPictureWithRetriever(path: String): ByteArray? {
        if (path.isBlank()) return null
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (path.isContentAudioSource()) retriever.setDataSource(context, Uri.parse(path))
                else retriever.setDataSource(path)
                retriever.embeddedPicture?.takeIf { it.isNotEmpty() }
            } finally { retriever.release() }
        }.getOrElse { error ->
            if (error is OutOfMemoryError) throw error
            Log.d("MusicRepo", "MediaMetadataRetriever embedded picture unavailable for $path", error)
            null
        }
    }

    private fun decodeBitmapFile(file: File, targetSize: Int, preferredConfig: Bitmap.Config): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) sampleSize *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sampleSize.coerceAtLeast(1)
            inPreferredConfig = preferredConfig
        }
        return BitmapFactory.decodeFile(file.absolutePath, options)
    }

    private fun decodeAlbumArtBitmap(albumId: Long, targetSize: Int, usage: CoverUsage): Bitmap? {
        if (albumId <= 0L) return null
        val albumCacheKey = "album:$albumId:${usage.name}:$targetSize"
        coverBitmapCache.get(albumCacheKey)?.let { return it }
        val albumArtUri = getAlbumArtUri(albumId) ?: return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(albumArtUri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
            var sampleSize = 1
            while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) sampleSize *= 2
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize.coerceAtLeast(1)
                inPreferredConfig = if (usage == CoverUsage.ListThumbnail) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(albumArtUri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?.also { coverBitmapCache.put(albumCacheKey, it) }
        }.getOrElse { error ->
            if (error is OutOfMemoryError) clearArtworkCachesAfterOom()
            Log.d("MusicRepo", "Failed to decode album art bitmap for albumId=$albumId", error)
            null
        }
    }

    private fun Song.songNamedSidecarCoverFile(): File? {
        val metadataPath = effectiveLocalPathForMetadataBlocking(
            settingsManager,
            httpClient,
            remoteAudioCacheDir,
            remoteMetadataHeaderCacheDir
        )
        val songFile = File(metadataPath)
        return songNamedCoverFileCandidates(
            songDirectory = songFile.parentFile,
            fileName = fileName.ifBlank { songFile.name },
            path = metadataPath,
            songId = id,
            musicThumbnailsDir = File(
                android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC),
                ".thumbnails"
            )
        ).firstOrNull { it.exists() && it.isFile && it.length() > 0L }
    }

    private fun Song.folderAlbumCoverFile(): File? {
        val metadataPath = effectiveLocalPathForMetadataBlocking(
            settingsManager,
            httpClient,
            remoteAudioCacheDir,
            remoteMetadataHeaderCacheDir
        )
        val directory = File(metadataPath).parentFile ?: return null
        if (!directory.isDirectory) return null
        return folderAlbumCoverFileCandidates(directory)
            .firstOrNull { it.exists() && it.isFile && it.length() > 0L }
    }
}
