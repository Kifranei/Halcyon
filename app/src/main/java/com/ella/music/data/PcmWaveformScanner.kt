package com.ella.music.data

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.decoder.ffmpeg.FfmpegLibrary
import androidx.media3.decoder.ffmpeg.OfflineFfmpegDecoder
import androidx.media3.inspector.MediaExtractorCompat
import com.ella.music.data.model.Song
import com.ella.music.player.playbackUri
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/** Offline decoder: never uses playback's session and never fabricates a waveform on failure. */
internal suspend fun scanPcmWaveform(
    context: Context, song: Song, count: Int,
    onProgress: (FloatArray) -> Unit = {}
): FloatArray = withContext(Dispatchers.IO) {
    if (count <= 0) return@withContext FloatArray(0)
    var source = song.playbackUri()
    // Media3 also extracts ALAC on devices whose platform extractor does not understand it.
    val extractor = MediaExtractorCompat(context)
    try {
        if (source.scheme == "halcyon-netease") {
            source = android.net.Uri.parse(com.ella.music.data.netease.NeteaseLibraryStore.getInstance(context)
                .streamUrl(source.lastPathSegment.orEmpty()))
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
        } ?: return@withContext FloatArray(0)
        extractor.selectTrack(track)
        val format = extractor.getTrackFormat(track)
        val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION)
            else song.duration * 1000L
        if (durationUs <= 0L) return@withContext FloatArray(0)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return@withContext FloatArray(0)
        val deadline = SystemClock.elapsedRealtime() + 120_000L
        if (mime == MimeTypes.AUDIO_ALAC) {
            scanFfmpegPcm(extractor, format, durationUs, count, deadline, onProgress)
        } else {
            try {
                scanPlatformPcm(extractor, format, durationUs, count, deadline, onProgress)
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                if (!FfmpegLibrary.supportsFormat(mime)) throw error
                extractor.seekTo(0, MediaExtractorCompat.SEEK_TO_CLOSEST_SYNC)
                scanFfmpegPcm(extractor, format, durationUs, count, deadline, onProgress)
            }
        }
    } catch (cancel: CancellationException) {
        throw cancel
    } catch (error: Exception) {
        Log.w("PcmWaveformScanner", "Waveform decoding failed (${error.javaClass.simpleName})")
        FloatArray(0)
    } finally {
        extractor.release()
    }
}

/** The ALAC csd buffer is the magic cookie; the bundled decoder wraps it in an ALAC atom
 * exactly as it does during playback. Duplicate buffers so scanning never consumes the CSD. */
internal fun waveformDecoderFormat(format: MediaFormat): Format {
    val initialization = buildList {
        var index = 0
        while (format.containsKey("csd-$index")) {
            format.getByteBuffer("csd-$index")?.duplicate()?.let { data ->
                add(ByteArray(data.remaining()).also(data::get))
            }
            index++
        }
    }
    return Format.Builder().setSampleMimeType(format.getString(MediaFormat.KEY_MIME))
        .setChannelCount(format.getInteger(MediaFormat.KEY_CHANNEL_COUNT))
        .setSampleRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
        .setInitializationData(initialization).build()
}

private suspend fun scanFfmpegPcm(
    extractor: MediaExtractorCompat, format: MediaFormat, durationUs: Long, count: Int,
    deadline: Long, onProgress: (FloatArray) -> Unit
): FloatArray {
    val buckets = PcmWaveformBuckets(count, durationUs)
    OfflineFfmpegDecoder(waveformDecoderFormat(format)).use { decoder ->
        var inputEnded = false
        var outputEnded = false
        var nextPreviewAt = 0L
        while (!outputEnded) {
            currentCoroutineContext().ensureActive()
            if (SystemClock.elapsedRealtime() > deadline) return FloatArray(0)
            var worked = false
            if (!inputEnded) decoder.dequeueInputBuffer()?.let { input ->
                input.clear()
                val sampleSize = extractor.sampleSize
                if (sampleSize < 0L) {
                    input.addFlag(C.BUFFER_FLAG_END_OF_STREAM)
                    inputEnded = true
                } else {
                    require(sampleSize <= Int.MAX_VALUE)
                    input.ensureSpaceForWrite(sampleSize.toInt())
                    val data = checkNotNull(input.data)
                    val bytes = extractor.readSampleData(data, 0)
                    check(bytes >= 0)
                    input.timeUs = extractor.sampleTime
                    data.position(bytes)
                    input.flip()
                    extractor.advance()
                }
                decoder.queueInputBuffer(input)
                worked = true
            }
            decoder.dequeueOutputBuffer()?.let { output ->
                try {
                    outputEnded = output.isEndOfStream
                    output.data?.takeIf { !outputEnded && it.hasRemaining() }?.let { data ->
                        buckets.add(data, output.timeUs, decoder.sampleRate, decoder.channelCount, decoder.encoding)
                        val now = SystemClock.elapsedRealtime()
                        if (now >= nextPreviewAt) { onProgress(buckets.values()); nextPreviewAt = now + 250L }
                    }
                } finally { output.release() }
                worked = true
            }
            if (!worked) delay(1)
        }
    }
    return buckets.result()
}

private suspend fun scanPlatformPcm(
    extractor: MediaExtractorCompat, inputFormat: MediaFormat, durationUs: Long, count: Int,
    deadline: Long, onProgress: (FloatArray) -> Unit
): FloatArray {
    val codec = MediaCodec.createDecoderByType(inputFormat.getString(MediaFormat.KEY_MIME)!!)
    try {
        codec.configure(inputFormat, null, null, 0)
        codec.start()
        val buckets = PcmWaveformBuckets(count, durationUs)
        val info = MediaCodec.BufferInfo()
        var inputEnded = false
        var outputEnded = false
        var channels = inputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1)
        var sampleRate = inputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE).coerceAtLeast(1)
        var encoding = AudioFormat.ENCODING_PCM_16BIT
        var nextPreviewAt = 0L
        while (!outputEnded) {
            currentCoroutineContext().ensureActive()
            if (SystemClock.elapsedRealtime() > deadline) return FloatArray(0)
            if (!inputEnded) {
                val index = codec.dequeueInputBuffer(1_000)
                if (index >= 0) {
                    val buffer = checkNotNull(codec.getInputBuffer(index))
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
                    codec.getOutputBuffer(index)?.takeIf { info.size > 0 }?.let { buffer ->
                        buffer.position(info.offset)
                        buffer.limit(info.offset + info.size)
                        buckets.add(buffer, info.presentationTimeUs, sampleRate, channels, encoding)
                    }
                    outputEnded = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                } finally { codec.releaseOutputBuffer(index, false) }
                val now = SystemClock.elapsedRealtime()
                if (info.size > 0 && now >= nextPreviewAt) { onProgress(buckets.values()); nextPreviewAt = now + 250L }
            }
        }
        return buckets.result()
    } finally {
        runCatching { codec.stop() }
        codec.release()
    }
}

/** Every channel sample contributes energy; opposite-phase stereo cannot cancel a waveform. */
internal class PcmWaveformBuckets(private val count: Int, private val durationUs: Long) {
    private val sums = DoubleArray(count)
    private val samples = LongArray(count)

    fun add(buffer: ByteBuffer, presentationTimeUs: Long, sampleRate: Int, channels: Int, encoding: Int) {
        require(sampleRate > 0 && channels > 0)
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        val bytesPerSample = when (encoding) {
            AudioFormat.ENCODING_PCM_FLOAT, AudioFormat.ENCODING_PCM_32BIT -> 4
            AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
            AudioFormat.ENCODING_PCM_8BIT -> 1
            else -> 2
        }
        var bucketPosition = presentationTimeUs.toDouble() * count / durationUs
        val bucketStep = 1_000_000.0 * count / durationUs / sampleRate / channels
        while (buffer.remaining() >= bytesPerSample) {
            val amplitude = when (encoding) {
                AudioFormat.ENCODING_PCM_FLOAT -> buffer.float.toDouble()
                AudioFormat.ENCODING_PCM_32BIT -> buffer.int / 2147483648.0
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                    val value = (buffer.get().toInt() and 255) or ((buffer.get().toInt() and 255) shl 8) or (buffer.get().toInt() shl 16)
                    value / 8388608.0
                }
                AudioFormat.ENCODING_PCM_8BIT -> ((buffer.get().toInt() and 255) - 128) / 128.0
                else -> buffer.short / 32768.0
            }
            val bucket = bucketPosition.toInt().coerceIn(0, count - 1)
            if (amplitude.isFinite()) { sums[bucket] += amplitude * amplitude; samples[bucket]++ }
            bucketPosition += bucketStep
        }
    }

    fun values(): FloatArray = FloatArray(count) { if (samples[it] > 0L) sqrt(sums[it] / samples[it]).toFloat() else 0f }
    fun result(): FloatArray = if (samples.any { it > 0L }) values() else FloatArray(0)
}
