package com.ella.music.ui.player

import com.ella.music.data.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpeningLyricSeekTest {
    @Test
    fun `opening metadata maps the whole row width to its full duration`() {
        val line = LyricLine(
            timeMs = 0L,
            text = "Title - Artist",
            endMs = 20_000L,
            isOpeningMetadata = true
        )

        assertEquals(0L, resolveOpeningLyricSeekPosition(line, 0f))
        assertEquals(10_000L, resolveOpeningLyricSeekPosition(line, 0.5f))
        assertEquals(20_000L, resolveOpeningLyricSeekPosition(line, 1f))
    }

    @Test
    fun `regular lyric line does not become a progress seek surface`() {
        assertNull(resolveOpeningLyricSeekPosition(LyricLine(5_000L, "line", endMs = 8_000L), 0.5f))
    }
}
