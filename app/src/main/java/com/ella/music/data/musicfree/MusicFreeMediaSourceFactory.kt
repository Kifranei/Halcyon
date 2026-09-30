package com.ella.music.data.musicfree

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.util.ReleasableExecutor
import androidx.media3.extractor.text.SubtitleParser
import com.ella.music.player.toSongFromMediaItemExtras
import com.google.common.base.Supplier
import okhttp3.OkHttpClient
import java.io.IOException

internal const val EXTRA_PLUGIN_TRANSPORT = "halcyon.pluginTransport"

/** Plugin media uses its own transport, without the private library's authentication or redirects. */
@UnstableApi
internal class MusicFreeMediaSourceFactory(
    private val context: Context,
    private val fallback: MediaSource.Factory,
    private val httpClient: OkHttpClient,
    private val streamHeaders: MusicFreeStreamHeaders = MusicFreeStreamHeaders.getInstance(context)
) : MediaSource.Factory by fallback {
    internal data class ScopedSource(val item: MediaItem, val dataSourceFactory: DataSource.Factory)
    private val configurations = mutableListOf<(MediaSource.Factory) -> Unit>()

    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory =
        configure { it.setDrmSessionManagerProvider(provider) }

    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory =
        configure { it.setLoadErrorHandlingPolicy(policy) }

    override fun setCmcdConfigurationFactory(factory: CmcdConfiguration.Factory): MediaSource.Factory =
        configure { it.setCmcdConfigurationFactory(factory) }

    override fun setSubtitleParserFactory(factory: SubtitleParser.Factory): MediaSource.Factory =
        configure { it.setSubtitleParserFactory(factory) }

    override fun setDownloadExecutor(executor: Supplier<ReleasableExecutor>): MediaSource.Factory =
        configure { it.setDownloadExecutor(executor) }

    private fun configure(configuration: (MediaSource.Factory) -> Unit): MediaSource.Factory {
        configuration(fallback)
        configurations += configuration
        return this
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val scoped = scopedSource(mediaItem) ?: return fallback.createMediaSource(mediaItem)
        val factory = DefaultMediaSourceFactory(scoped.dataSourceFactory)
        configurations.forEach { it(factory) }
        return factory.createMediaSource(scoped.item)
    }

    internal fun scopedSource(mediaItem: MediaItem): ScopedSource? {
        val uri = mediaItem.localConfiguration?.uri ?: return null
        if (uri.scheme !in setOf("http", "https")) return null
        if (!uri.userInfo.isNullOrEmpty()) throw IOException("Playback URLs must not contain embedded credentials")
        val url = uri.toString()
        val resolved = streamHeaders.resolveStream(url, httpClient)
        if (resolved == null) {
            val extras = mediaItem.mediaMetadata.extras
            val source = mediaItem.toSongFromMediaItemExtras()?.onlineSource.orEmpty()
            if (extras?.getBoolean(EXTRA_PLUGIN_TRANSPORT) != true &&
                source !in setOf("kw", "wy", "kg", "tx", "mg", "qs", "sd") &&
                !source.startsWith("musicfree:")
            ) return null
            return ScopedSource(mediaItem, DefaultDataSource.Factory(context, OkHttpDataSource.Factory(httpClient)))
        }
        val markedUri = mediaItem.localConfiguration!!.uri
        val transportUri = Uri.parse(resolved.url)
        // Keep the logical URI so queues copied from Player/MediaSource retain their header scope.
        // Only the actual request loses the marker; HLS child requests already use clean URIs.
        return ScopedSource(
            mediaItem,
            ResolvingDataSource.Factory(
                DefaultDataSource.Factory(context, OkHttpDataSource.Factory(resolved.client)),
                ResolvingDataSource.Resolver { dataSpec ->
                    if (dataSpec.uri == markedUri) dataSpec.withUri(transportUri) else dataSpec
                }
            )
        )
    }
}
