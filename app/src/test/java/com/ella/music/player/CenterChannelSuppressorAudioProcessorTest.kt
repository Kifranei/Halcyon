package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@UnstableApi
class CenterChannelSuppressorAudioProcessorTest {
    @Test
    fun disabledProcessorCopiesStereoPcm16() {
        val processor = CenterChannelSuppressorAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        processor.enabled = false
        val input = stereoShortBuffer(left = 12_000, right = -4_000, frames = 4)
        processor.queueInput(input)
        val output = processor.output
        assertEquals(16, output.remaining())
        assertEquals(12_000.toShort(), output.short)
        assertEquals((-4_000).toShort(), output.short)
    }

    @Test
    fun enabledReducesCenteredVocals() {
        val processor = CenterChannelSuppressorAudioProcessor()
        processor.configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_FLOAT))
        processor.enabled = true
        val input = stereoFloatBuffer(left = 0.8f, right = 0.8f, frames = 8)
        processor.queueInput(input)
        val output = processor.output
        var max = 0f
        while (output.hasRemaining()) {
            max = maxOf(max, abs(output.float))
        }
        assertTrue("center should be reduced, max=$max", max < 0.25f)
    }

    private fun stereoShortBuffer(left: Short, right: Short, frames: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 4).order(ByteOrder.nativeOrder())
        repeat(frames) {
            buffer.putShort(left)
            buffer.putShort(right)
        }
        buffer.flip()
        return buffer
    }

    private fun stereoFloatBuffer(left: Float, right: Float, frames: Int): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(frames * 8).order(ByteOrder.nativeOrder())
        repeat(frames) {
            buffer.putFloat(left)
            buffer.putFloat(right)
        }
        buffer.flip()
        return buffer
    }
}
