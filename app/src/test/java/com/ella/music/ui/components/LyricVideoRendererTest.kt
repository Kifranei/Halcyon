package com.ella.music.ui.components

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
}
