package com.ella.music.player

import androidx.media3.cast.DefaultMediaItemConverter
import androidx.media3.cast.MediaItemConverter
import androidx.media3.common.MediaItem
import com.google.android.gms.cast.MediaQueueItem

internal class HalcyonCastMediaItemConverter(
    private val mediaServer: LocalCastMediaServer
) : MediaItemConverter {
    private val delegate = DefaultMediaItemConverter()

    override fun toMediaQueueItem(mediaItem: MediaItem): MediaQueueItem {
        val url = mediaServer.urlFor(mediaItem)
        val castItem = if (url != null) mediaItem.buildUpon().setUri(url).build() else mediaItem
        return delegate.toMediaQueueItem(castItem)
    }

    override fun toMediaItem(mediaQueueItem: MediaQueueItem): MediaItem {
        val converted = delegate.toMediaItem(mediaQueueItem)
        val url = converted.localConfiguration?.uri?.toString().orEmpty()
        return mediaServer.originalFor(url) ?: converted
    }
}
