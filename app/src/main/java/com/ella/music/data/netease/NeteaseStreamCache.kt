package com.ella.music.data.netease

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

/**
 * On-disk LRU cache for NetEase streams. Replaying a song (e.g. skipping back) reads the cached
 * bytes instead of resolving a new CDN URL and downloading it again.
 *
 * The key is the stable virtual URI plus the chosen quality, never the expiring CDN URL, so a
 * cache hit needs no network request at all. Cleared by "清除远程音频缓存".
 */
@OptIn(UnstableApi::class)
internal object NeteaseStreamCache {
    private const val MAX_BYTES = 1024L * 1024L * 1024L
    @Volatile private var cache: SimpleCache? = null
    @Volatile var qualityPreference: String = "auto"

    fun cacheKey(songId: String, quality: String = qualityPreference): String = "netease:$songId:$quality"

    private fun get(context: Context): SimpleCache = cache ?: synchronized(this) {
        cache ?: SimpleCache(
            File(context.applicationContext.cacheDir, "netease_audio"),
            LeastRecentlyUsedCacheEvictor(MAX_BYTES),
            StandaloneDatabaseProvider(context.applicationContext)
        ).also { cache = it }
    }

    /**
     * Wraps [upstream] so only `halcyon-netease://` media goes through the cache; local files and
     * other remote sources keep their existing path untouched.
     */
    fun dataSourceFactory(context: Context, upstream: DataSource.Factory): DataSource.Factory {
        val cached = CacheDataSource.Factory()
            .setCache(get(context))
            .setUpstreamDataSourceFactory(upstream)
            .setCacheKeyFactory { spec ->
                if (spec.uri.scheme == NETEASE_SCHEME) cacheKey(spec.uri.lastPathSegment.orEmpty())
                else spec.key ?: spec.uri.toString()
            }
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        return DataSource.Factory { SchemeRoutingDataSource(upstream.createDataSource(), cached.createDataSource()) }
    }

    fun clear(context: Context) {
        val instance = runCatching { get(context) }.getOrNull() ?: return
        instance.keys.toList().forEach { key -> runCatching { instance.removeResource(key) } }
    }

    private class SchemeRoutingDataSource(
        private val direct: DataSource,
        private val cached: DataSource
    ) : DataSource {
        private var active: DataSource? = null

        override fun addTransferListener(transferListener: TransferListener) {
            direct.addTransferListener(transferListener)
            cached.addTransferListener(transferListener)
        }

        override fun open(dataSpec: DataSpec): Long {
            val target = if (dataSpec.uri.scheme == NETEASE_SCHEME) cached else direct
            active = target
            return target.open(dataSpec)
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            requireNotNull(active) { "DataSource not opened" }.read(buffer, offset, length)

        override fun getUri() = active?.uri

        override fun getResponseHeaders(): Map<String, List<String>> = active?.responseHeaders ?: emptyMap()

        override fun close() {
            try { active?.close() } finally { active = null }
        }
    }
}
