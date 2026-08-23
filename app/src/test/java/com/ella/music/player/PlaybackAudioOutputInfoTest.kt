package com.ella.music.player

import androidx.media3.common.C
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackAudioOutputInfoTest {
    @Test
    fun `bit depth reduction counts as output format conversion`() {
        assertTrue(
            playbackFormatRequiresConversion(
                sourceSampleRate = 48_000,
                outputSampleRate = 48_000,
                sourceBitDepth = 24,
                outputEncoding = C.ENCODING_PCM_16BIT
            )
        )
    }

    @Test
    fun `matching sample rate and bit depth is not converted`() {
        assertFalse(
            playbackFormatRequiresConversion(
                sourceSampleRate = 44_100,
                outputSampleRate = 44_100,
                sourceBitDepth = 16,
                outputEncoding = C.ENCODING_PCM_16BIT
            )
        )
    }

    @Test
    fun `sample rate change counts as output format conversion`() {
        assertTrue(
            playbackFormatRequiresConversion(
                sourceSampleRate = 96_000,
                outputSampleRate = 48_000,
                sourceBitDepth = 24,
                outputEncoding = C.ENCODING_PCM_24BIT
            )
        )
    }
}
