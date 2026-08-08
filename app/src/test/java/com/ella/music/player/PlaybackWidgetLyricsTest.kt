package com.ella.music.player

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackWidgetLyricsTest {
    @Test
    fun activeWordFollowsWordTiming() {
        val line = LyricLine(
            timeMs = 0L,
            text = "hello world",
            words = listOf(
                LyricWord("hello", 0L, 500L),
                LyricWord("world", 500L, 1_000L)
            )
        )

        assertEquals(0, PlaybackWidgetUpdater.activeLyricWordIndex(line, 0L))
        assertEquals(0, PlaybackWidgetUpdater.activeLyricWordIndex(line, 499L))
        assertEquals(1, PlaybackWidgetUpdater.activeLyricWordIndex(line, 500L))
        assertEquals(-1, PlaybackWidgetUpdater.activeLyricWordIndex(line, 1_000L))
    }

    @Test
    fun wordRangeMatchesRepeatedWordsFromTheCurrentCursor() {
        val text = "go go"
        val first = PlaybackWidgetUpdater.wordRange(text, LyricWord("go", 0L, 100L), 0)
        val second = PlaybackWidgetUpdater.wordRange(text, LyricWord("go", 100L, 200L), 2)

        assertEquals(0..1, first)
        assertEquals(3..4, second)
    }
}
