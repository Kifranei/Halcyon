package com.ella.music.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import com.ella.music.data.model.Song
import com.ella.music.player.playbackUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Offline decoder: never uses playback's session and never fabricates a waveform on failure. */
internal suspend fun scanPcmWaveform(
    context: Context, song: Song, count: Int,
    onProgress: (FloatArray) -> Unit = {}
): FloatArray {
    // Match playback's MediaStore/scoped-storage and completed remote-cache resolution.
    var source = song.playbackUri()
    val extractor = MediaExtractor()
    var decoder: MediaCodec? = null
    try {
        if (source.scheme == "halcyon-netease") {
            source = android.net.Uri.parse(com.ella.music.data.netease.NeteaseLibraryStore.getInstance(context).streamUrl(source.lastPathSegment.orEmpty()))
        }
        if (source.scheme.equals("http", true) || source.scheme.equals("https", true)) {
            extractor.setDataSource(source.toString(), mapOf("User-Agent" to "Mozilla/5.0"))
        } else if (song.path.startsWith("/") && java.io.File(song.path).canRead()) {
            extractor.setDataSource(song.path)
        } else {
            extractor.setDataSource(context, source, null)
        }
        val track = (0 until extractor.trackCount).firstOrNull {
            extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true
        } ?: return FloatArray(0)
        extractor.selectTrack(track)
        val inputFormat = extractor.getTrackFormat(track)
        val durationUs = if (inputFormat.containsKey(MediaFormat.KEY_DURATION)) inputFormat.getLong(MediaFormat.KEY_DURATION)
            else song.duration * 1000L
        if (durationUs <= 0L) return FloatArray(0)
        val codec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
        decoder = codec
        codec.configure(inputFormat, null, null, 0)
        codec.start()
        val sums = DoubleArray(count)
        val samples = LongArray(count)
        val info = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        val deadline = android.os.SystemClock.elapsedRealtime() + 120_000L
        var nextPreviewAt = 0L
        while (!outputEnded) {
            currentCoroutineContext().ensureActive()
            if (android.os.SystemClock.elapsedRealtime() > deadline) return FloatArray(0)
            if (!inputEnded) {
                val index = codec.dequeueInputBuffer(1_000)
                if (index >= 0) {
                    val buffer = codec.getInputBuffer(index) ?: return FloatArray(0)
                    val bytes = extractor.readSampleData(buffer, 0)
                    if (bytes < 0) {
                        codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        inputEnded = true
                    } else {
                        codec.queueInputBuffer(index, 0, bytes, extractor.sampleTime, 0)
                        extractor.advance()
                    }
                }
            }
            val index = codec.dequeueOutputBuffer(info, 1_000)
            if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                val format = codec.outputFormat
                channels = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
                sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
                encoding = if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) format.getInteger(MediaFormat.KEY_PCM_ENCODING)
                    else AudioFormat.ENCODING_PCM_16BIT
            } else if (index >= 0) {
                try {
                    val buffer = codec.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buffer.order(ByteOrder.LITTLE_ENDIAN)
                        val bytesPerSample = when (encoding) {
                            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
                            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
                            AudioFormat.ENCODING_PCM_8BIT -> 1
                            else -> 2
                        }
                        // Resolve time-to-bucket once per decoded buffer, rather than doing
                        // several divisions for every channel sample. Retain every PCM sample.
                        var bucketPosition = info.presentationTimeUs.toDouble() * count / durationUs
                        val bucketStep = 1_000_000.0 * count / durationUs / sampleRate / channels
                        while (buffer.remaining() >= bytesPerSample) {
                            val amplitude = when (encoding) {
                                AudioFormat.ENCODING_PCM_FLOAT -> buffer.float.toDouble()
                                AudioFormat.ENCODING_PCM_32BIT -> buffer.int / 2147483648.0
                                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                                    val v = (buffer.get().toInt() and 255) or ((buffer.get().toInt() and 255) shl 8) or (buffer.get().toInt() shl 16)
                                    v / 8388608.0
                                }
                                AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 255) - 128) / 128.0
                                else -> buffer.short / 32768.0
                            }
                            val bucket = bucketPosition.toInt().coerceIn(0, count - 1)
                            if (amplitude.isFinite()) {
                                sums[bucket] += amplitude * amplitude
                                samples[bucket]++
                            }
                            bucketPosition += bucketStep
                        }
                    }
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                } finally { codec.releaseOutputBuffer(index, false) }
                val now = android.os.SystemClock.elapsedRealtime()
                if (info.size > 0 && now >= nextPreviewAt) {
                    onProgress(FloatArray(count) { if (samples[it] > 0L) sqrt(sums[it] / samples[it]).toFloat() else 0f })
                    nextPreviewAt = now + 250L
                }
            }
        }
        if (samples.all { it == 0L }) return FloatArray(0)
        return FloatArray(count) { if (samples[it] > 0L) sqrt(sums[it] / samples[it]).toFloat() else 0f }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Log.w("PcmWaveformScanner", "Waveform decoding failed (${error.javaClass.simpleName})")
        return FloatArray(0)
    } finally {
        decoder?.let { runCatching { it.stop() }; runCatching { it.release() } }
        extractor.release()
    }
}
