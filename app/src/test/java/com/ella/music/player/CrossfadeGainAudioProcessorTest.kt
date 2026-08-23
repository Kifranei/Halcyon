package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Test

@UnstableApi
class CrossfadeGainAudioProcessorTest {

    @Test
    fun `zero gain silences pcm without changing persistent player volume`() {
        val processor = configuredProcessor(gain = 0f)

        processor.queueInput(pcm16(12_000, -12_000))

        assertEquals(listOf<Short>(0, 0), readPcm16(processor.output))
    }

    @Test
    fun `gain can recover after a muted transition`() {
        val processor = configuredProcessor(gain = 0f)
        processor.queueInput(pcm16(12_000))
        assertEquals(listOf<Short>(0), readPcm16(processor.output))

        processor.gain = 1f
        processor.queueInput(pcm16(12_000))

        assertEquals(listOf<Short>(12_000), readPcm16(processor.output))
    }

    @Test
    fun `audio sink reset does not override coordinator envelope`() {
        val processor = configuredProcessor(gain = 0f)

        processor.reset()

        assertEquals(0f, processor.gain)
    }

    private fun configuredProcessor(gain: Float): CrossfadeGainAudioProcessor =
        CrossfadeGainAudioProcessor().apply {
            this.gain = gain
            configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        }

    private fun pcm16(vararg samples: Int): ByteBuffer =
        ByteBuffer.allocateDirect(samples.size * Short.SIZE_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                samples.forEach { putShort(it.toShort()) }
                flip()
            }

    private fun readPcm16(buffer: ByteBuffer): List<Short> {
        val input = buffer.order(ByteOrder.LITTLE_ENDIAN)
        return buildList {
            while (input.remaining() >= Short.SIZE_BYTES) add(input.short)
        }
    }
}
