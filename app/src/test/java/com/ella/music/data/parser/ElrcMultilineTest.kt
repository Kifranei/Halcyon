package com.ella.music.data.parser

import com.ella.music.data.model.*
import org.junit.Assert.*
import org.junit.Test

class ElrcMultilineTest {
    @Test fun simultaneousTimedRowsRemainIndependent() {
        val lyrics = EllaLyricsParser.parse("""
            [00:01.00]<00:01.00>Hello <00:02.00>there<00:04.00>
            [00:01.00]<00:01.00>Answer <00:02.50>back<00:05.00>
        """.trimIndent()).lyrics
        assertEquals(2, lyrics.size)
        assertTrue(lyrics.all { it.words.isNotEmpty() && it.translation == null })
        assertEquals(4000L, lyrics[0].primaryEndMs(nextLine = lyrics[1]))
    }
    @Test fun staggeredTimedRowsPreserveSungEnd() {
        val lyrics = EllaLyricsParser.parse("""
            [00:01.00]<00:01.00>Hello <00:02.00>there<00:04.00>
            [00:03.00]<00:03.00>Answer <00:04.00>back<00:05.00>
        """.trimIndent()).lyrics
        assertEquals(2, lyrics.size)
        assertEquals(4000L, lyrics[0].primaryEndMs(nextLine = lyrics[1]))
    }
    @Test fun singleWordVocalsWithExplicitMarkersRemainIndependent() {
        val lyrics = EllaLyricsParser.parse("""
            [00:01.00]<00:01.00>Hello<00:04.00>
            [00:01.00]<00:01.00>Answer<00:05.00>
        """.trimIndent()).lyrics
        assertEquals(2, lyrics.size)
        assertTrue(lyrics.all { it.translation == null })
    }

}
