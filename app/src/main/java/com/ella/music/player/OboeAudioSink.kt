package com.ella.music.player

import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.audio.AudioProcessingPipeline
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import com.google.common.collect.ImmutableList
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A Media3 [AudioSink] that outputs decoded PCM through a native Oboe stream (AAudio / OpenSL ES)
 * instead of the Java AudioTrack used by DefaultAudioSink. The configured [processors] (EQ, output
 * format conversion) run inside an [AudioProcessingPipeline] before the PCM is handed to Oboe.
 *
 * This is a first, functional implementation; playback-speed changes and per-sink volume are not
 * applied to the Oboe stream yet (system volume still works), and it needs on-device tuning.
 *
 * [audioApi]: 1 = AAudio, 2 = OpenSL ES. [exclusive]: request exclusive sharing mode (USB / bit-perfect).
 */
@UnstableApi
@Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
class OboeAudioSink(
    private val audioApi: Int,
    private val exclusive: Boolean,
    processors: List<AudioProcessor>,
    private val deviceId: Int = 0
) : AudioSink {

    private val pipeline = AudioProcessingPipeline(ImmutableList.copyOf(processors))

    private var listener: AudioSink.Listener? = null
    private var oboe: OboeAudioOutput? = null

    private var configuredFormat: Format? = null
    private var outputSampleRate = 0
    private var outputChannelCount = 0
    private var outputEncoding = C.ENCODING_PCM_16BIT
    private var outputFrameSize = 4
    private var oboeEncodingId = 0

    private var pendingOutput: ByteBuffer? = null
    private var directScratch: ByteBuffer = EMPTY

    private var startMediaTimeUs = C.TIME_UNSET
    private var framesSubmitted = 0L
    private var inputEnded = false
    private var playing = false

    private var volume = 1f
    private var audioAttributes = AudioAttributes.DEFAULT
    private var playbackParameters = PlaybackParameters.DEFAULT
    private var skipSilenceEnabled = false

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean =
        getFormatSupport(format) != AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun getFormatSupport(format: Format): Int =
        if (MimeTypes.AUDIO_RAW == format.sampleMimeType && Util.isEncodingLinearPcm(format.pcmEncoding)) {
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        } else {
            AudioSink.SINK_FORMAT_UNSUPPORTED
        }

    @Throws(AudioSink.ConfigurationException::class)
    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        configuredFormat = inputFormat
        val inputAudioFormat = AudioProcessor.AudioFormat(
            inputFormat.sampleRate,
            inputFormat.channelCount,
            inputFormat.pcmEncoding
        )
        val outputAudioFormat = try {
            val configured = pipeline.configure(inputAudioFormat)
            pipeline.flush()
            if (pipeline.isOperational) configured else inputAudioFormat
        } catch (e: AudioProcessor.UnhandledAudioFormatException) {
            throw AudioSink.ConfigurationException(e, inputFormat)
        }

        outputSampleRate = outputAudioFormat.sampleRate
        outputChannelCount = outputAudioFormat.channelCount
        outputEncoding = outputAudioFormat.encoding
        oboeEncodingId = encodingToOboeId(outputEncoding)
        outputFrameSize = outputChannelCount * bytesPerSample(outputEncoding)

        oboe?.close()
        val output = OboeAudioOutput()
        if (!output.open(audioApi, outputSampleRate, outputChannelCount, oboeEncodingId, exclusive, deviceId)) {
            throw AudioSink.ConfigurationException("Unable to open Oboe output stream", inputFormat)
        }
        oboe = output
        if (!playing) output.pause()
        resetPlaybackState()
    }

    override fun play() {
        playing = true
        oboe?.start()
    }

    override fun pause() {
        playing = false
        oboe?.pause()
    }

    override fun handleDiscontinuity() {
        startMediaTimeUs = C.TIME_UNSET
    }

    @Throws(AudioSink.InitializationException::class, AudioSink.WriteException::class)
    override fun handleBuffer(buffer: ByteBuffer, presentationTimeUs: Long, encodedAccessUnitCount: Int): Boolean {
        if (oboe == null) return false
        if (startMediaTimeUs == C.TIME_UNSET && buffer.hasRemaining()) {
            startMediaTimeUs = presentationTimeUs
        }

        if (pipeline.isOperational) {
            if (!drainPipeline()) return false
            if (buffer.hasRemaining()) pipeline.queueInput(buffer)
            if (!drainPipeline()) return false
            return !buffer.hasRemaining()
        }
        return writeDirect(buffer)
    }

    @Throws(AudioSink.WriteException::class)
    override fun playToEndOfStream() {
        if (!inputEnded) {
            inputEnded = true
            if (pipeline.isOperational) pipeline.queueEndOfStream()
        }
        if (pipeline.isOperational) drainPipeline()
    }

    override fun isEnded(): Boolean = inputEnded && !hasPendingData()

    override fun hasPendingData(): Boolean {
        if (pendingOutput?.hasRemaining() == true) return true
        if (pipeline.isOperational && !pipeline.isEnded()) return true
        val read = oboe?.framesRead() ?: 0L
        return framesSubmitted > read
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        this.playbackParameters = playbackParameters
    }

    override fun getPlaybackParameters(): PlaybackParameters = playbackParameters

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        this.skipSilenceEnabled = skipSilenceEnabled
    }

    override fun getSkipSilenceEnabled(): Boolean = skipSilenceEnabled

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        this.audioAttributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes = audioAttributes

    override fun setAudioSessionId(audioSessionId: Int) {}

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) {}

    override fun getAudioTrackBufferSizeUs(): Long = C.TIME_UNSET

    override fun enableTunnelingV21() {}

    override fun disableTunneling() {}

    override fun setVolume(volume: Float) {
        this.volume = volume
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val output = oboe ?: return AudioSink.CURRENT_POSITION_NOT_SET
        if (startMediaTimeUs == C.TIME_UNSET || outputSampleRate <= 0) {
            return AudioSink.CURRENT_POSITION_NOT_SET
        }
        val framesRead = output.framesRead()
        return startMediaTimeUs + framesRead * C.MICROS_PER_SECOND / outputSampleRate
    }

    override fun flush() {
        pipeline.flush()
        pendingOutput = null
        // Reopen the stream so getFramesRead() restarts from zero after a seek.
        val output = oboe
        if (output != null) {
            output.close()
            val reopened = OboeAudioOutput()
            if (reopened.open(audioApi, outputSampleRate, outputChannelCount, oboeEncodingId, exclusive, deviceId)) {
                oboe = reopened
                if (!playing) reopened.pause()
            } else {
                oboe = null
            }
        }
        resetPlaybackState()
    }

    override fun reset() {
        pipeline.reset()
        oboe?.close()
        oboe = null
        pendingOutput = null
        configuredFormat = null
        resetPlaybackState()
    }

    private fun resetPlaybackState() {
        startMediaTimeUs = C.TIME_UNSET
        framesSubmitted = 0L
        inputEnded = false
    }

    /** Drains any pending output and everything the pipeline can currently produce. */
    @Throws(AudioSink.WriteException::class)
    private fun drainPipeline(): Boolean {
        while (true) {
            val pending = pendingOutput
            if (pending != null && pending.hasRemaining()) {
                if (!writeToOboe(pending)) return false
            }
            val next = pipeline.getOutput()
            if (!next.hasRemaining()) {
                pendingOutput = null
                return true
            }
            applyGain(next)
            pendingOutput = next
        }
    }

    @Throws(AudioSink.WriteException::class)
    private fun writeDirect(buffer: ByteBuffer): Boolean {
        if (!buffer.hasRemaining()) return true
        // Copy into a direct scratch when the input isn't direct OR when we must apply volume gain
        // (we must never modify the decoder's own buffer in place).
        val needsCopy = !buffer.isDirect || volume != 1f
        val toWrite = if (!needsCopy) {
            buffer
        } else {
            val remaining = buffer.remaining()
            if (directScratch.capacity() < remaining) {
                directScratch = ByteBuffer.allocateDirect(remaining).order(ByteOrder.nativeOrder())
            }
            directScratch.clear()
            directScratch.put(buffer.duplicate())
            directScratch.flip()
            applyGain(directScratch)
            directScratch
        }
        val startPos = toWrite.position()
        val done = writeToOboe(toWrite)
        val consumed = toWrite.position() - startPos
        if (needsCopy) {
            buffer.position(buffer.position() + consumed)
        }
        return done && !buffer.hasRemaining()
    }

    /** Scales the buffer's [position, limit) region in place by [volume], per PCM encoding. */
    private fun applyGain(buffer: ByteBuffer) {
        val v = volume
        if (v == 1f) return
        val previousOrder = buffer.order()
        buffer.order(ByteOrder.LITTLE_ENDIAN)
        var pos = buffer.position()
        val limit = buffer.limit()
        when (outputEncoding) {
            C.ENCODING_PCM_16BIT -> while (pos + 2 <= limit) {
                val scaled = (buffer.getShort(pos) * v).toInt().coerceIn(-32768, 32767)
                buffer.putShort(pos, scaled.toShort())
                pos += 2
            }
            C.ENCODING_PCM_32BIT -> while (pos + 4 <= limit) {
                val scaled = (buffer.getInt(pos) * v.toDouble()).toLong()
                    .coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong())
                buffer.putInt(pos, scaled.toInt())
                pos += 4
            }
            C.ENCODING_PCM_FLOAT -> while (pos + 4 <= limit) {
                buffer.putFloat(pos, (buffer.getFloat(pos) * v).coerceIn(-1f, 1f))
                pos += 4
            }
            C.ENCODING_PCM_24BIT -> while (pos + 3 <= limit) {
                var sample = (buffer.get(pos).toInt() and 0xFF) or
                    ((buffer.get(pos + 1).toInt() and 0xFF) shl 8) or
                    ((buffer.get(pos + 2).toInt() and 0xFF) shl 16)
                if (sample and 0x800000 != 0) sample = sample or -0x1000000
                val scaled = (sample * v).toInt().coerceIn(-8388608, 8388607)
                buffer.put(pos, (scaled and 0xFF).toByte())
                buffer.put(pos + 1, ((scaled shr 8) and 0xFF).toByte())
                buffer.put(pos + 2, ((scaled shr 16) and 0xFF).toByte())
                pos += 3
            }
        }
        buffer.order(previousOrder)
    }

    @Throws(AudioSink.WriteException::class)
    private fun writeToOboe(buffer: ByteBuffer): Boolean {
        val output = oboe ?: return false
        if (!buffer.hasRemaining()) return true
        val n = output.write(buffer, buffer.position(), buffer.remaining(), WRITE_TIMEOUT_NS)
        if (n < 0) {
            val recoverable = n == -2
            throw AudioSink.WriteException(n, configuredFormat ?: Format.Builder().build(), recoverable)
        }
        if (outputFrameSize > 0) framesSubmitted += n / outputFrameSize
        buffer.position(buffer.position() + n)
        return !buffer.hasRemaining()
    }

    private fun encodingToOboeId(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 0
        C.ENCODING_PCM_24BIT -> 1
        C.ENCODING_PCM_32BIT -> 2
        C.ENCODING_PCM_FLOAT -> 3
        else -> 0
    }

    private fun bytesPerSample(encoding: Int): Int = when (encoding) {
        C.ENCODING_PCM_16BIT -> 2
        C.ENCODING_PCM_24BIT -> 3
        C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
        else -> 2
    }

    companion object {
        // Bound blocking writes so a stalled/disconnected device can't wedge the audio thread.
        private const val WRITE_TIMEOUT_NS = 200_000_000L
        private val EMPTY: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
