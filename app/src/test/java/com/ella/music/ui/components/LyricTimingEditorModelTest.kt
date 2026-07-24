package com.ella.music.ui.components

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
}
