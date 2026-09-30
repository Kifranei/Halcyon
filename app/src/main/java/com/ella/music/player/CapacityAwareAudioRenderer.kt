package com.ella.music.player

import android.content.Context
import android.os.Handler
import androidx.media3.common.Format
import androidx.media3.common.PlaybackException
import androidx.media3.common.util.UnstableApi
import androidx.media3.decoder.DecoderInputBuffer
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.audio.AudioRendererEventListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.MediaCodecAudioRenderer
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector

/** A platform buffer rejection must reach Player.onPlayerError, rather than silently discarding
 * the song. Media3's default handler only logs InsufficientCapacityException and skips frames. */
@UnstableApi
internal class CapacityAwareAudioRenderer(
    context: Context, codecAdapterFactory: MediaCodecAdapter.Factory,
    selector: MediaCodecSelector, fallback: Boolean, handler: Handler,
    listener: AudioRendererEventListener, sink: AudioSink
) : MediaCodecAudioRenderer(context, codecAdapterFactory, selector, fallback, handler, listener, sink) {
    private var sourceFormat: Format? = null

    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
        sourceFormat = formatHolder.format
        return super.onInputFormatChanged(formatHolder)
    }

    override fun onCodecError(codecError: Exception) {
        super.onCodecError(codecError)
        if (codecError is DecoderInputBuffer.InsufficientCapacityException) {
            throw createRendererException(codecError, sourceFormat, PlaybackException.ERROR_CODE_DECODING_FAILED)
        }
    }
}
