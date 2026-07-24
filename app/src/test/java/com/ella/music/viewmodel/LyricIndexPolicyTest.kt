package com.ella.music.viewmodel

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricIndexPolicyTest {
    @Test
    fun zeroTimestampPlaceholderIsSuppressedAtSongStart() {
        val result = currentLyricIndexAt(
            positionMs = 0L,
            lyrics = listOf(LyricLine(timeMs = 0L, text = "Opening line")),
            suppressLeadingZero = true
        )

        assertEquals(-1, result.index)
        assertTrue(result.suppressedLeadingZero)
    }

    @Test
    fun zeroTimestampLineDisplaysAfterSuppressionWindow() {
        val result = currentLyricIndexAt(
            positionMs = LEADING_ZERO_LYRIC_SUPPRESSION_MS,
            lyrics = listOf(LyricLine(timeMs = 0L, text = "Opening line")),
            suppressLeadingZero = true
        )

        assertEquals(0, result.index)
        assertFalse(result.suppressedLeadingZero)
    }

    @Test
    fun timestampOnlyLineIsIgnored() {
        val lyrics = listOf(
            LyricLine(timeMs = 0L, text = "[00:00.000]"),
            LyricLine(timeMs = 1_000L, text = "First real line")
        )

        assertEquals(
            -1,
            currentLyricIndexAt(positionMs = 500L, lyrics = lyrics, suppressLeadingZero = false).index
        )
        assertEquals(
            1,
            currentLyricIndexAt(positionMs = 1_200L, lyrics = lyrics, suppressLeadingZero = false).index
        )
    }

    @Test
    fun visibleZeroLineAfterTimestampOnlyLineIsStillSuppressedAtSongStart() {
        val lyrics = listOf(
            LyricLine(timeMs = 0L, text = "[00:00.000]"),
            LyricLine(timeMs = 0L, text = "First real line")
        )

        val result = currentLyricIndexAt(positionMs = 0L, lyrics = lyrics, suppressLeadingZero = true)

        assertEquals(-1, result.index)
        assertTrue(result.suppressedLeadingZero)
    }

    @Test
    fun timedWordMarkerOnlyLineIsIgnored() {
        val lyrics = listOf(
            LyricLine(timeMs = 0L, text = "<00:00.000><00:01.000>"),
            LyricLine(timeMs = 2_000L, text = "Visible")
        )

        assertEquals(
            -1,
            currentLyricIndexAt(positionMs = 1_500L, lyrics = lyrics, suppressLeadingZero = false).index
        )
    }

    @Test
    fun firstNonZeroLineBehavesNormally() {
        val lyrics = listOf(LyricLine(timeMs = 500L, text = "First line"))

        assertEquals(
            -1,
            currentLyricIndexAt(positionMs = 0L, lyrics = lyrics, suppressLeadingZero = true).index
        )
        assertEquals(
            0,
            currentLyricIndexAt(positionMs = 500L, lyrics = lyrics, suppressLeadingZero = true).index
        )
    }

    @Test
    fun retainsPreviousVisibleLineDuringGapBeforeNextLine() {
        val lyrics = listOf(
            LyricLine(timeMs = 0L, text = "First line", endMs = 1_000L),
            LyricLine(timeMs = 2_000L, text = "Second line")
        )

        assertEquals(
            0,
            currentLyricIndexAt(positionMs = 1_500L, lyrics = lyrics, suppressLeadingZero = false).index
        )
    }

    @Test
    fun ttmlWordTimingIsStillDisplayable() {
        val lyrics = listOf(
            LyricLine(
                timeMs = 0L,
                text = "",
                words = listOf(LyricWord("Hello", 0L, 1_200L)),
                isTtml = true
            )
        )

        assertEquals(
            0,
            currentLyricIndexAt(
                positionMs = LEADING_ZERO_LYRIC_SUPPRESSION_MS,
                lyrics = lyrics,
                suppressLeadingZero = true
            ).index
        )
        assertEquals("Hello", lyrics.single().words.single().text)
    }
}
