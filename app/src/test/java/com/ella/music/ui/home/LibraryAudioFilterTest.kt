package com.ella.music.ui.home

import com.ella.music.data.model.AudioInfo
import org.junit.Assert.*
import org.junit.Test

class LibraryAudioFilterTest {
    @Test fun qualityUnionAndFormatIntersection() {
        val filter = LibraryAudioFilter(qualities = setOf("SQ", "HR"), formats = setOf("FLAC"))
        assertTrue(filter.matches(AudioInfo("FLAC", sampleRate = 44100, bitDepth = 16)))
        assertTrue(filter.matches(AudioInfo("FLAC", sampleRate = 96000, bitDepth = 24)))
        assertFalse(filter.matches(AudioInfo("ALAC", sampleRate = 96000, bitDepth = 24)))
        assertFalse(filter.matches(AudioInfo("MP3", bitRate = 320000)))
    }
    @Test fun depthAndRateGroupsIntersectAndHrRemovalClearsBoth() {
        val filter = LibraryAudioFilter(setOf("HR"), setOf("FLAC"), setOf(24), setOf(44100, 48000))
        assertTrue(filter.matches(AudioInfo("FLAC", sampleRate = 44100, bitDepth = 24)))
        assertTrue(filter.matches(AudioInfo("FLAC", sampleRate = 48000, bitDepth = 24)))
        assertFalse(filter.matches(AudioInfo("FLAC", sampleRate = 96000, bitDepth = 24)))
        assertFalse(filter.matches(AudioInfo("FLAC", sampleRate = 48000, bitDepth = 16)))
        val cleared = filter.toggleQuality("HR")
        assertTrue(cleared.depths.isEmpty())
        assertTrue(cleared.rates.isEmpty())
    }
    @Test fun unfilteredAndOtherFormatRules() {
        assertTrue(LibraryAudioFilter().matches(AudioInfo("")))
        val filter = LibraryAudioFilter(formats = setOf(OTHER_AUDIO_FORMAT))
        assertFalse(filter.matches(AudioInfo("AC3")))
        assertFalse(filter.matches(AudioInfo("FLAC")))
    }
}
