package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.ella.music.data.SettingsManager
import kotlin.math.ceil
import kotlin.math.floor
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class PlaybackOutputSettings(
    val backend: Int = SettingsManager.AUDIO_OUTPUT_BACKEND_AUTO,
    val bitDepth: Int = SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_AUTO,
    val sampleRate: Int = SettingsManager.AUDIO_OUTPUT_SAMPLE_RATE_AUTO,
    val usbExclusive: Boolean = false
) {
    val forceFloatOutput: Boolean
        get() = backend == SettingsManager.AUDIO_OUTPUT_BACKEND_HI_RES ||
            bitDepth == SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_FLOAT32

    val needsFormatProcessor: Boolean
        get() = bitDepth != SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_AUTO ||
            sampleRate != SettingsManager.AUDIO_OUTPUT_SAMPLE_RATE_AUTO
}

@UnstableApi
class OutputFormatAudioProcessor(
    private val settings: PlaybackOutputSettings
) : BaseAudioProcessor() {

    private var inputFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET
    private var targetFormat: AudioProcessor.AudioFormat = AudioProcessor.AudioFormat.NOT_SET

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (!settings.needsFormatProcessor) return AudioProcessor.AudioFormat.NOT_SET
        if (!isSupportedPcmEncoding(inputAudioFormat.encoding)) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }

        val outputEncoding = targetEncoding(inputAudioFormat.encoding)
        val outputSampleRate = if (settings.sampleRate == SettingsManager.AUDIO_OUTPUT_SAMPLE_RATE_AUTO) {
            inputAudioFormat.sampleRate
        } else {
            settings.sampleRate
        }

        val targetFormat = AudioProcessor.AudioFormat(
            outputSampleRate,
            inputAudioFormat.channelCount,
            outputEncoding
        )

        return if (
            inputAudioFormat.sampleRate == targetFormat.sampleRate &&
            inputAudioFormat.encoding == targetFormat.encoding
        ) {
            AudioProcessor.AudioFormat.NOT_SET
        } else {
            targetFormat
        }
    }

    override fun onFlush() {
        inputFormat = inputAudioFormat
        targetFormat = outputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (inputFormat == AudioProcessor.AudioFormat.NOT_SET ||
            targetFormat == AudioProcessor.AudioFormat.NOT_SET
        ) {
            return
        }

        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        val inputBytesPerFrame = inputFormat.channelCount * bytesPerSample(inputFormat.encoding)
        val inputFrames = inputBuffer.remaining() / inputBytesPerFrame
        if (inputFrames <= 0) {
            inputBuffer.position(inputBuffer.limit())
            return
        }

        val outputFrames = if (inputFormat.sampleRate == targetFormat.sampleRate) {
            inputFrames
        } else {
            ceil(inputFrames * targetFormat.sampleRate.toDouble() / inputFormat.sampleRate).toInt()
        }
        val outputBytes = outputFrames * targetFormat.channelCount * bytesPerSample(targetFormat.encoding)
        val outputBuffer = replaceOutputBuffer(outputBytes).order(ByteOrder.LITTLE_ENDIAN)

        val inputStart = inputBuffer.position()
        val rateRatio = inputFormat.sampleRate.toDouble() / targetFormat.sampleRate
        for (outputFrame in 0 until outputFrames) {
            val sourceFrame = if (inputFormat.sampleRate == targetFormat.sampleRate) {
                outputFrame.toDouble()
            } else {
                outputFrame * rateRatio
            }
            val baseFrame = floor(sourceFrame).toInt().coerceIn(0, inputFrames - 1)
            val nextFrame = (baseFrame + 1).coerceAtMost(inputFrames - 1)
            val fraction = (sourceFrame - baseFrame).toFloat()

            for (channel in 0 until inputFormat.channelCount) {
                val first = readSample(inputBuffer, inputStart, baseFrame, channel)
                val second = readSample(inputBuffer, inputStart, nextFrame, channel)
                writeSample(outputBuffer, first + (second - first) * fraction)
            }
        }

        inputBuffer.position(inputBuffer.limit())
        outputBuffer.flip()
    }

    override fun onReset() {
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        targetFormat = AudioProcessor.AudioFormat.NOT_SET
    }

    private fun readSample(buffer: ByteBuffer, inputStart: Int, frame: Int, channel: Int): Float {
        val offset = inputStart +
            (frame * inputFormat.channelCount + channel) * bytesPerSample(inputFormat.encoding)
        return when (inputFormat.encoding) {
            C.ENCODING_PCM_16BIT -> buffer.getShort(offset) / 32768f
            C.ENCODING_PCM_24BIT -> {
                val unsigned = (buffer.get(offset).toInt() and 0xFF) or
                    ((buffer.get(offset + 1).toInt() and 0xFF) shl 8) or
                    ((buffer.get(offset + 2).toInt() and 0xFF) shl 16)
                val signed = if (unsigned and 0x800000 != 0) unsigned or -0x1000000 else unsigned
                signed / 8388608f
            }
            C.ENCODING_PCM_32BIT -> buffer.getInt(offset) / 2147483648f
            C.ENCODING_PCM_FLOAT -> buffer.getFloat(offset).coerceIn(-1f, 1f)
            else -> 0f
        }
    }

    private fun writeSample(buffer: ByteBuffer, sample: Float) {
        val clamped = sample.coerceIn(-1f, 1f)
        when (targetFormat.encoding) {
            C.ENCODING_PCM_16BIT -> buffer.putShort((clamped * 32767f).toInt().toShort())
            C.ENCODING_PCM_24BIT -> {
                val value = (clamped * 8388607f).toInt()
                buffer.put((value and 0xFF).toByte())
                buffer.put(((value shr 8) and 0xFF).toByte())
                buffer.put(((value shr 16) and 0xFF).toByte())
            }
            C.ENCODING_PCM_32BIT -> buffer.putInt((clamped * 2147483647f).toLong().toInt())
            C.ENCODING_PCM_FLOAT -> buffer.putFloat(clamped)
        }
    }

    private fun targetEncoding(inputEncoding: @C.PcmEncoding Int): @C.PcmEncoding Int =
        when (settings.bitDepth) {
            SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_16 -> C.ENCODING_PCM_16BIT
            SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_24 -> C.ENCODING_PCM_24BIT
            SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_32 -> C.ENCODING_PCM_32BIT
            SettingsManager.AUDIO_OUTPUT_BIT_DEPTH_FLOAT32 -> C.ENCODING_PCM_FLOAT
            else -> inputEncoding
        }

    private fun bytesPerSample(encoding: @C.PcmEncoding Int): Int =
        when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> 0
        }

    private fun isSupportedPcmEncoding(encoding: @C.PcmEncoding Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT ||
            encoding == C.ENCODING_PCM_24BIT ||
            encoding == C.ENCODING_PCM_32BIT ||
            encoding == C.ENCODING_PCM_FLOAT
}
