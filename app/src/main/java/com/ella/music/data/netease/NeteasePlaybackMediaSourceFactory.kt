package com.ella.music.data.netease

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.CmcdConfiguration
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.util.ReleasableExecutor
import androidx.media3.extractor.text.SubtitleParser
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.musicfree.EXTRA_PLUGIN_TRANSPORT
import com.ella.music.player.toSongFromMediaItemExtras
import com.google.common.base.Supplier
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Stable account queue items resolve when prepared, so expiring plugin URLs are never persisted. */
@UnstableApi
internal class NeteasePlaybackMediaSourceFactory(
    private val context: Context,
    private val fallback: MediaSource.Factory,
    private val resolver: NeteasePlaybackResolver = NeteasePlaybackResolver(context)
) : MediaSource.Factory by fallback {
    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        fallback.setDrmSessionManagerProvider(provider)
    }
    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        fallback.setLoadErrorHandlingPolicy(policy)
    }
    override fun setCmcdConfigurationFactory(factory: CmcdConfiguration.Factory): MediaSource.Factory = apply {
        fallback.setCmcdConfigurationFactory(factory)
    }
    override fun setSubtitleParserFactory(factory: SubtitleParser.Factory): MediaSource.Factory = apply {
        fallback.setSubtitleParserFactory(factory)
    }
    override fun setDownloadExecutor(executor: Supplier<ReleasableExecutor>): MediaSource.Factory = apply {
        fallback.setDownloadExecutor(executor)
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val uri = mediaItem.localConfiguration?.uri ?: return synchronized(fallback) { fallback.createMediaSource(mediaItem) }
        if (uri.scheme != NETEASE_SCHEME) return synchronized(fallback) { fallback.createMediaSource(mediaItem) }
        val id = uri.lastPathSegment.orEmpty()
        val song = mediaItem.toSongFromMediaItemExtras() ?: Song(
            id = -(id.toLongOrNull() ?: 0L), title = mediaItem.mediaMetadata.title?.toString().orEmpty(),
            artist = mediaItem.mediaMetadata.artist?.toString().orEmpty(),
            album = mediaItem.mediaMetadata.albumTitle?.toString().orEmpty(), albumId = 0L,
            duration = 0L, path = uri.toString(), fileName = "", onlineSource = NETEASE_SOURCE, onlineId = id
        )
        return DeferredNeteaseMediaSource(mediaItem, song, fallback, resolver,
            context.getString(R.string.netease_playback_resolve_failed))
    }
}

/** Media type is chosen after resolution, allowing an account song to become an HLS source. */
@UnstableApi
internal class DeferredNeteaseMediaSource(
    @Volatile private var logicalItem: MediaItem,
    private val song: Song,
    private val factory: MediaSource.Factory,
    private val resolver: NeteasePlaybackResolver,
    private val failureMessage: String
) : CompositeMediaSource<Unit>() {
    private var preparationScope: CoroutineScope? = null
    private var delegate: MediaSource? = null
    private var childTimeline: Timeline? = null
    private var preparationError: IOException? = null

    override fun getMediaItem(): MediaItem = logicalItem

    override fun canUpdateMediaItem(mediaItem: MediaItem): Boolean =
        logicalItem.mediaId == mediaItem.mediaId && logicalItem.localConfiguration == mediaItem.localConfiguration &&
            logicalItem.clippingConfiguration == mediaItem.clippingConfiguration && logicalItem.liveConfiguration == mediaItem.liveConfiguration

    override fun updateMediaItem(mediaItem: MediaItem) {
        logicalItem = mediaItem
        childTimeline?.let(::publishTimeline)
    }

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        val scope = CoroutineScope(SupervisorJob() + Handler(requireNotNull(Looper.myLooper())).asCoroutineDispatcher())
        preparationScope = scope
        scope.launch {
            try {
                val resolved = withContext(Dispatchers.IO) { resolver.resolve(song) }
                currentCoroutineContext().ensureActive()
                val childItem = if (resolved == null) logicalItem else logicalItem.buildUpon()
                    .setUri(resolved.path).setMimeType(resolved.mimeType.takeIf(String::isNotBlank))
                    .setMediaMetadata(logicalItem.mediaMetadata.buildUpon()
                        .setExtras(Bundle(logicalItem.mediaMetadata.extras ?: Bundle()).apply {
                            putBoolean(EXTRA_PLUGIN_TRANSPORT, true)
                        }).build())
                    .build()
                val source = synchronized(factory) { factory.createMediaSource(childItem) }
                delegate = source
                prepareChildSource(Unit, source)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                currentCoroutineContext().ensureActive()
                preparationError = if (error is IOException) error else IOException(
                    error.localizedMessage?.takeIf(String::isNotBlank) ?: failureMessage, error
                )
            }
        }
    }

    override fun maybeThrowSourceInfoRefreshError() {
        preparationError?.let { throw it }
        super.maybeThrowSourceInfoRefreshError()
    }

    override fun onChildSourceInfoRefreshed(id: Unit, mediaSource: MediaSource, newTimeline: Timeline) {
        childTimeline = newTimeline
        publishTimeline(newTimeline)
    }

    private fun publishTimeline(timeline: Timeline) {
        refreshSourceInfo(object : ForwardingTimeline(timeline) {
            override fun getWindow(windowIndex: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window =
                super.getWindow(windowIndex, window, defaultPositionProjectionUs).also { it.mediaItem = logicalItem }
        })
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
        requireNotNull(delegate).createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) { requireNotNull(delegate).releasePeriod(mediaPeriod) }

    override fun releaseSourceInternal() {
        preparationScope?.cancel()
        preparationScope = null
        super.releaseSourceInternal()
        delegate = null
        childTimeline = null
        preparationError = null
    }
}
