package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.*
import org.junit.Test

class AudioFormatHandoffTest {
    private val stereo = AudioProcessor.AudioFormat(44100, 2, C.ENCODING_PCM_16BIT)
    private val surround = AudioProcessor.AudioFormat(48000, 6, C.ENCODING_PCM_16BIT)
    private val floating = AudioProcessor.AudioFormat(48000, 2, C.ENCODING_PCM_FLOAT)

    private fun pcm() = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).apply {
        putShort(12000.toShort()); putShort((-12000).toShort()); flip()
    }

    @Test fun equalizerKeepsDrainingStereoWhenNextTrackIsSurround() {
        val processor = EqualizerAudioProcessor()
        processor.setSettings(EqualizerSettings(peakLimiterEnabled = false))
        processor.configure(stereo)
        processor.flush()
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, processor.configure(surround))
        val input = pcm()
        processor.queueInput(input)
        assertFalse(input.hasRemaining())
        assertEquals(4, processor.output.remaining())
        processor.queueEndOfStream()
        assertTrue(processor.isEnded)
        processor.flush()
        assertFalse(processor.isActive)
        processor.configure(stereo)
        processor.flush()
        processor.queueInput(pcm())
        assertEquals(4, processor.output.remaining())
    }

    @Test fun gainDoesNotInterpretOldShortSamplesAsNextTrackFloats() {
        val processor = CrossfadeGainAudioProcessor()
        processor.configure(stereo)
        processor.flush()
        processor.configure(floating)
        processor.queueInput(pcm())
        val output = processor.output.order(ByteOrder.nativeOrder())
        assertEquals(12000.toShort(), output.short)
        assertEquals((-12000).toShort(), output.short)
        processor.flush()
        val input = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder()).putFloat(0.5f)
        input.flip()
        processor.queueInput(input)
        assertEquals(0.5f, processor.output.order(ByteOrder.nativeOrder()).float, 0.0001f)
    }
}
