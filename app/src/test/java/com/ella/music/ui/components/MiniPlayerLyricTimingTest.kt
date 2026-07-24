package com.ella.music.ui.components

import com.ella.music.data.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

class MiniPlayerLyricTimingTest {
    @Test
    fun usesTheLastWordEndBeforeCompletingTheLine() {
        val timing = MiniPlayerLyricTiming(
            lineStartMs = 1_000L,
            lineEndMs = 1_800L,
            words = listOf(
                LyricWord("long", 1_000L, 1_400L),
                LyricWord("note", 1_400L, 2_000L)
            )
        )

        assertEquals(0f, timing.progressAt(1_000L), 0.0001f)
        assertEquals(0.5f, timing.progressAt(1_500L), 0.0001f)
        assertEquals(1f, timing.progressAt(2_000L), 0.0001f)
    }

    @Test
    fun keepsTimedWordRangesWhenDisplayTextNormalizesPunctuation() {
        assertEquals(
            listOf(0 until 5, 6 until 10),
            miniPlayerWordCharacterRanges(
                text = "Don't stop",
                words = listOf(
                    LyricWord("Don’t", 0L, 500L),
                    LyricWord("stop", 500L, 1_000L)
                )
            )
        )
    }

    @Test
    fun assignsOrderedFallbackRangesForMalformedWordTokens() {
        assertEquals(
            listOf(0 until 2, 2 until 4),
            miniPlayerWordCharacterRanges(
                text = "歌词显示",
                words = listOf(
                    LyricWord("错误词", 0L, 500L),
                    LyricWord("另一词", 500L, 1_000L)
                )
            )
        )
    }
}
