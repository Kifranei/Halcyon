package com.ella.music.ui.components

import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class LyricTimingEditorModelTest {
    @Test
    fun formatsLrcTimestampsWithCentisecondPrecision() {
        assertEquals("[01:02.34]", 62_349L.toLrcTimestamp())
        assertEquals("[00:00.00]", (-10L).toLrcTimestamp())
    }

    @Test
    fun serializesTimedLinesInPlaybackOrder() {
        val lrc = listOf(
            LyricTimingLine("second", 2_050L),
            LyricTimingLine("untimed"),
            LyricTimingLine("first", 1_000L)
        ).toEmbeddedLrc()

        assertEquals("[00:01.00]first\n[00:02.05]second", lrc)
    }

    @Test
    fun preservesTimingForUnchangedEditedLines() {
        val lines = "first\nsecond".toLyricTimingLines(
            listOf(LyricTimingLine("first", 500L), LyricTimingLine("old", 1_000L))
        )

        assertEquals(500L, lines[0].timeMs)
        assertEquals(null, lines[1].timeMs)
    }

    @Test
    fun serializesEnhancedLrcWithTimedWordsAndBackground() {
        val elrc = listOf(
            LyricTimingLine(
                text = "hello",
                timeMs = 1_000L,
                words = listOf(LyricWord("hel", 1_000L, 1_300L), LyricWord("lo", 1_300L, 1_600L)),
                agent = "v1",
                backgroundText = "ah",
                backgroundStartMs = 1_200L,
                backgroundEndMs = 1_600L
            )
        ).toEmbeddedElrc()

        assertEquals("[00:01.00]v1: <00:01.00>hel<00:01.30>lo<00:01.60>\n[00:01.00][bg:<00:01.20>ah<00:01.60>]", elrc)
    }

    @Test
    fun serializesAppleTtmlWithDuetAndBackground() {
        val song = Song(1, "Song & Test", "Artist", "Album", 1, 10_000L, "/song.mp3", "song.mp3")
        val ttml = listOf(
            LyricTimingLine(
                text = "main",
                timeMs = 1_000L,
                words = listOf(LyricWord("main", 1_000L, 1_500L)),
                agent = "v2",
                backgroundText = "bg",
                backgroundStartMs = 1_100L,
                backgroundEndMs = 1_400L
            )
        ).toEmbeddedTtml(song)

        assert(ttml.contains("ttm:agent=\"v2\""))
        assert(ttml.contains("ttm:role=\"x-bg\""))
        assert(ttml.contains("Song &amp; Test"))
    }
}
