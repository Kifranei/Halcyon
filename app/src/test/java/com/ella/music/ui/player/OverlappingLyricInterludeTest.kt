package com.ella.music.ui.player

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import org.junit.Assert.*
import org.junit.Test

class OverlappingLyricInterludeTest {
    @Test fun shorterVocalDoesNotProduceFalseInterlude() {
        val lyrics = listOf(
            LyricLine(0, "long", listOf(LyricWord("long", 0, 14000))),
            LyricLine(1000, "short", listOf(LyricWord("short", 1000, 2000))),
            LyricLine(13000, "next", listOf(LyricWord("next", 13000, 15000)))
        )
        assertTrue(lyrics.interludes().isEmpty())
    }
    @Test fun realGapStartsAfterAllOverlappingVocalsEnd() {
        val lyrics = listOf(
            LyricLine(0, "long", listOf(LyricWord("long", 0, 10000))),
            LyricLine(1000, "short", listOf(LyricWord("short", 1000, 2000))),
            LyricLine(18000, "next", listOf(LyricWord("next", 18000, 19000)))
        )
        val gap = lyrics.interludes().single()
        assertEquals(10000L, gap.startMs)
        assertEquals(18000L, gap.endMs)
    }
}
