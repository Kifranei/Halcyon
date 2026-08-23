package com.ella.music.player

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@UnstableApi
class OutputProbeAudioProcessorTest {
    @Test
    fun probeReportsFormatWithoutJoiningAudioPipeline() {
        var observed = AudioProcessor.AudioFormat.NOT_SET
        val processor = OutputProbeAudioProcessor { observed = it }
        val input = AudioProcessor.AudioFormat(96_000, 2, C.ENCODING_PCM_FLOAT)

        val output = processor.configure(input)

        assertEquals(input, observed)
        assertEquals(AudioProcessor.AudioFormat.NOT_SET, output)
        assertFalse(processor.isActive)
    }
}
