package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class WaveformLevelAudioProcessorTest {

    @Test
    fun passesDecodedPcmThroughAndUpdatesLevel() {
        val processor = WaveformLevelAudioProcessor().apply {
            configure(AudioProcessor.AudioFormat(44_100, 2, C.ENCODING_PCM_16BIT))
        }
        val input = ByteBuffer.allocateDirect(4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putShort(16_384)
                putShort(-8_192)
                flip()
            }

        processor.queueInput(input)

        assertEquals(0, input.remaining())
        assertTrue(processor.level > 0f)
        val output = processor.output.order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16_384.toShort(), output.short)
        assertEquals((-8_192).toShort(), output.short)
    }
}
