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
import com.ella.music.data.model.Song
import com.ella.music.data.metadata.AudioTagRepository
import com.ella.music.data.SettingsManager
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.ConcurrentHashMap

private val embeddedArtworkThumbnailExtensions = setOf(
    "m4a", "mp4", "alac", "flac", "wav", "wave", "aif", "aiff"
)

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
    private val coverBitmapCache = object : LruCache<String, Bitmap>(16 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
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
        val cacheKey = "${song.coverDataCacheKey()}:${usage.name}:$targetSize"
        coverBitmapCache.get(cacheKey)?.let { return it }
        return synchronized(coverArtLock) {
            coverBitmapCache.get(cacheKey)?.let { return it }
            if (usage == CoverUsage.ListThumbnail) {
                decodeExternalThumbnailBitmap(song, targetSize, cacheKey)?.let { return it }
            }
            if (usage == CoverUsage.ListThumbnail && !song.prefersEmbeddedArtworkForThumbnail()) {
                decodeAlbumArtBitmap(song.albumId, targetSize, usage)?.let { return it }
            }
            val data = getCoverArt(song)
            if (data == null) return decodeAlbumArtBitmap(song.albumId, targetSize, usage)
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
                var sampleSize = 1
                while ((bounds.outWidth / sampleSize) > targetSize || (bounds.outHeight / sampleSize) > targetSize) sampleSize *= 2
                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize.coerceAtLeast(1)
                    inPreferredConfig = if (usage == CoverUsage.ListThumbnail) Bitmap.Config.RGB_565 else Bitmap.Config.ARGB_8888
                }
                BitmapFactory.decodeByteArray(data, 0, data.size, options)
                    ?.also { coverBitmapCache.put(cacheKey, it) }
            }.getOrElse { error ->
                if (error is OutOfMemoryError) {
                    clearArtworkCachesAfterOom()
                }
                Log.w("MusicRepo", "Failed to decode cover bitmap for ${song.path}", error)
                null
            }
        }
    }

    /**
     * Returns the unscaled source for detail surfaces and export. Keeping this separate from
     * [getCoverArtBitmap] prevents a rendering bitmap from being mistaken for the source image.
     */
    fun getOriginalCoverModel(song: Song): Any? {
        // Online artwork is the actual source for remote songs. For local files prefer embedded
        // bytes, but do not let a stale embedded thumbnail replace an explicitly supplied cover.
        return song.coverUrl.takeIf { it.isNotBlank() }
            ?: getCoverArt(song)
            ?: getAlbumArtUri(song.albumId)
    }

    fun getAlbumArtUri(albumId: Long): Uri? {
        if (albumId <= 0L) return null
        return android.content.ContentUris.withAppendedId(
            android.provider.MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI, albumId
        )
    }

    fun clearCache() {
        coverArtCache.evictAll()
        coverBitmapCache.evictAll()
        coverDataStates.clear()
    }

    private fun clearArtworkCachesAfterOom() {
        coverArtCache.evictAll()
        coverBitmapCache.evictAll()
        // OOM is a transient decode failure. Never leave permanent Missing/Error sentinels.
        coverDataStates.clear()
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

    private fun decodeExternalThumbnailBitmap(song: Song, targetSize: Int, cacheKey: String): Bitmap? {
        val thumbnail = song.externalThumbnailCandidates()
            .firstOrNull { it.exists() && it.isFile && it.length() > 0L } ?: return null
        return runCatching {
            decodeBitmapFile(thumbnail, targetSize, Bitmap.Config.RGB_565)
                ?.also { coverBitmapCache.put(cacheKey, it) }
        }.getOrElse { error ->
            if (error is OutOfMemoryError) clearArtworkCachesAfterOom()
            Log.d("MusicRepo", "Failed to decode external thumbnail ${thumbnail.absolutePath}", error)
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

    private fun Song.prefersEmbeddedArtworkForThumbnail(): Boolean =
        fileName.substringAfterLast('.', path.substringAfterLast('.')).lowercase() in embeddedArtworkThumbnailExtensions

    private fun Song.externalThumbnailCandidates(): List<File> {
        val metadataPath = effectiveLocalPathForMetadataBlocking(settingsManager, httpClient, remoteAudioCacheDir, remoteMetadataHeaderCacheDir)
        val songFile = File(metadataPath)
        if (!songFile.isFile) return emptyList()
        val fileNameBase = fileName.ifBlank { songFile.name }
        val stem = fileNameBase.substringBeforeLast('.').ifBlank { songFile.nameWithoutExtension }
        val directories = buildList {
            songFile.parentFile?.let { add(File(it, ".thumbnails")) }
            add(File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MUSIC), ".thumbnails"))
        }.distinctBy { it.absolutePath }
        val keys = listOf(
            stem,
            fileNameBase,
            id.takeIf { it > 0L }?.toString().orEmpty(),
            albumId.takeIf { it > 0L }?.toString().orEmpty(),
            path.sha256()
        ).filter { it.isNotBlank() }.distinct()
        val extensions = listOf("jpg", "jpeg", "png", "webp")
        return directories.flatMap { dir ->
            keys.flatMap { key ->
                extensions.map { ext -> File(dir, "$key.$ext") }
            }
        }
    }
}
