package com.ella.music.data

import com.ella.music.data.model.AudioInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioQualityTest {
    @Test
    fun hiResExceedsCdQualityOnEitherAxis() {
        assertEquals(
            "Hi-Res",
            audioQualitySummary(AudioInfo(format = "FLAC", bitDepth = 16, sampleRate = 48_000)).compactLabel
        )
        assertEquals(
            "Hi-Res",
            audioQualitySummary(AudioInfo(format = "FLAC", bitDepth = 24, sampleRate = 44_100)).compactLabel
        )
    }

    @Test
    fun normalizedAudioFormatRecognizesAc4Variants() {
        assertEquals("AC4", normalizedAudioFormat("audio/ac4"))
        assertEquals("AC4", normalizedAudioFormat("AC4 A-JOC"))
        assertEquals("AC4", normalizedAudioFormat("AC4 Immersive Stereo"))
    }

    @Test
    fun ac4AjocUsesDolbyAtmosBadgeAndDetail() {
        val summary = audioQualitySummary(
            AudioInfo(
                format = "AC4 A-JOC",
                bitRate = 448_000,
                sampleRate = 48_000,
                channels = 2
            )
        )

        assertEquals("$DOLBY_MARK Dolby Atmos", summary.compactLabel)
        assertEquals(DOLBY_MARK, summary.listTag)
        assertTrue(summary.showDolbyLogo)
        assertEquals("AC4", summary.analyticsLabel)
        assertEquals("ac4 / Dolby Atmos (A-JOC) / 48kHz / 2ch", summary.detailLabel)
    }

    @Test
    fun dolbyEc3DoesNotStackHiResMobiusMark() {
        val summary = audioQualitySummary(
            AudioInfo(
                format = "EC3",
                bitRate = 876_000,
                sampleRate = 48_000,
                channels = 6
            )
        )
        assertEquals("$DOLBY_MARK Dolby Atmos", summary.compactLabel)
        assertEquals(DOLBY_MARK, summary.listTag)
        assertTrue(!summary.showMobius)
        assertTrue(!summary.showWaveform)
    }

    @Test
    fun losslessFlacUsesWaveformBadgeInsteadOfMobius() {
        val summary = audioQualitySummary(
            AudioInfo(format = "FLAC", bitDepth = 16, sampleRate = 44_100)
        )
        assertEquals("Lossless", summary.compactLabel)
        assertTrue(summary.showWaveform)
        assertTrue(!summary.showMobius)
    }

    @Test
    fun appleLosslessCdQualityUsesLosslessLabel() {
        val summary = audioQualitySummary(
            AudioInfo(format = "ALAC", bitDepth = 16, sampleRate = 44_100)
        )
        assertEquals("Lossless", summary.compactLabel)
        assertTrue(summary.showWaveform)
    }

    @Test
    fun ac4ImmersiveStereoDetailIsPreserved() {
        val detail = detailedAudioInfo(
            AudioInfo(
                format = "AC4 Immersive Stereo",
                bitRate = 256_000,
                sampleRate = 48_000,
                channels = 2
            )
        )

        assertTrue(detail.contains("Dolby Atmos (Immersive Stereo)"))
    }
}
