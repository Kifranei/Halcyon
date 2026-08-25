package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertTrue
import org.junit.Test

@UnstableApi
class EqualizerMasterGainTest {

    @Test
    fun `positive master gain raises pcm independently of eq bands`() {
        val output = processSample(sample = 1_000, gainDb = 6f)

        assertTrue(kotlin.math.abs(output.toInt() - 1_995) <= 2)
    }

    @Test
    fun `negative master gain attenuates pcm independently of eq bands`() {
        val output = processSample(sample = 1_000, gainDb = -6f)

        assertTrue(kotlin.math.abs(output.toInt() - 501) <= 2)
    }

    private fun processSample(sample: Int, gainDb: Float): Short {
        val processor = EqualizerAudioProcessor().apply {
            setSettings(EqualizerSettings(masterGainDb = gainDb, peakLimiterEnabled = false))
            configure(AudioProcessor.AudioFormat(48_000, 1, C.ENCODING_PCM_16BIT))
        }
        processor.queueInput(
            ByteBuffer.allocateDirect(Short.SIZE_BYTES)
                .order(ByteOrder.nativeOrder())
                .apply {
                    putShort(sample.toShort())
                    flip()
                }
        )
        return processor.output.order(ByteOrder.nativeOrder()).short
    }
}
