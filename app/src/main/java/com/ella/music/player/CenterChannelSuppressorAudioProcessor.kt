package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A realtime center-channel reducer for karaoke playback. It attenuates common centered content
 * (usually lead vocals) in stereo mixes; it is deliberately not presented as AI stem separation.
 */
@UnstableApi
class CenterChannelSuppressorAudioProcessor : AudioProcessor {
    @Volatile
    var enabled: Boolean = false

    private var inputFormat = AudioProcessor.AudioFormat.NOT_SET
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false
    private var reusableOutputBuffer: ByteBuffer? = null

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        inputFormat = if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT &&
            inputAudioFormat.channelCount in 1..2
        ) {
            inputAudioFormat
        } else {
            AudioProcessor.AudioFormat.NOT_SET
        }
        return inputFormat
    }

    override fun isActive(): Boolean = inputFormat != AudioProcessor.AudioFormat.NOT_SET

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val byteCount = limit - position
        if (byteCount == 0) return

        val output = replaceOutputBuffer(byteCount)
        if (!enabled || inputFormat.channelCount != 2) {
            output.put(inputBuffer.duplicate())
        } else {
            // A stereo mix can be represented as its center (mid) plus its side channel. Keeping
            // only a small amount of mid attenuates centered vocals while preserving the sides.
            inputBuffer.order(ByteOrder.nativeOrder())
            for (offset in position until limit step 4) {
                val left = inputBuffer.getShort(offset) / 32768f
                val right = inputBuffer.getShort(offset + 2) / 32768f
                val mid = (left + right) * 0.5f
                val side = (left - right) * 0.5f
                output.putShort(((side + mid * 0.15f).coerceIn(-1f, 1f) * 32767f).toInt().toShort())
                output.putShort(((-side + mid * 0.15f).coerceIn(-1f, 1f) * 32767f).toInt().toShort())
            }
        }
        output.flip()
        outputBuffer = output
        inputBuffer.position(limit)
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Media3 retains this callback for AudioProcessor compatibility")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
    }

    @Suppress("DEPRECATION")
    override fun reset() {
        flush()
        inputFormat = AudioProcessor.AudioFormat.NOT_SET
        reusableOutputBuffer = null
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        val buffer = reusableOutputBuffer?.takeIf { it.capacity() >= size }
            ?: ByteBuffer.allocateDirect(size).also { reusableOutputBuffer = it }
        return buffer.apply {
            clear()
            order(ByteOrder.nativeOrder())
        }
    }

    private companion object {
        val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
