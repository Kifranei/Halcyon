package com.ella.music.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricVideoRendererTest {
    @Test
    fun interLineGapStartsAfterKaraokeAndEndsAtNextLine() {
        assertFalse(isLyricVideoInterLineGap(timeMs = 499L, karaokeEndMs = 500L, nextLineStartMs = 1_000L))
        assertTrue(isLyricVideoInterLineGap(timeMs = 500L, karaokeEndMs = 500L, nextLineStartMs = 1_000L))
        assertTrue(isLyricVideoInterLineGap(timeMs = 999L, karaokeEndMs = 500L, nextLineStartMs = 1_000L))
        assertFalse(isLyricVideoInterLineGap(timeMs = 1_000L, karaokeEndMs = 500L, nextLineStartMs = 1_000L))
        assertFalse(isLyricVideoInterLineGap(timeMs = 900L, karaokeEndMs = 500L, nextLineStartMs = null))
    }
    @Test
    fun nextSentenceExclusivelyOwnsTextEvenDuringPreviousDissolveWindow() {
        val starts = listOf(0L, 1_000L, 2_000L)
        assertEquals(0, lyricVideoActiveLineIndex(starts, 999L))
        for (time in listOf(1_000L, 1_300L, 1_899L)) {
            // Previous sentence would dissolve at 1300..1900. It must no longer
            // supply either the original text, translation, or effect bitmap.
            val index = lyricVideoActiveLineIndex(starts, time)
            assertEquals(1, index)
            assertEquals(LyricVideoDissolveState.Pending,
                lyricVideoDissolveState(time, starts[index] + 1_300L, starts[index] + 1_900L))
        }
    }

    @Test
    fun finalSentenceDissolvesThenStaysGone() {
        assertEquals(LyricVideoDissolveState.Pending, lyricVideoDissolveState(1_299L, 1_300L, 1_900L))
        assertEquals(LyricVideoDissolveState.Dissolving, lyricVideoDissolveState(1_300L, 1_300L, 1_900L))
        assertEquals(LyricVideoDissolveState.Dissolving, lyricVideoDissolveState(1_899L, 1_300L, 1_900L))
        assertEquals(LyricVideoDissolveState.Finished, lyricVideoDissolveState(1_900L, 1_300L, 1_900L))
    }

    @Test
    fun simultaneousAndSkippedLinesSelectOnlyLatestSentence() {
        val starts = listOf(0L, 1_000L, 1_000L, 2_000L)
        assertEquals(-1, lyricVideoActiveLineIndex(starts, -1L))
        assertEquals(2, lyricVideoActiveLineIndex(starts, 1_000L))
        assertEquals(3, lyricVideoActiveLineIndex(starts, 2_500L))
    }
}
