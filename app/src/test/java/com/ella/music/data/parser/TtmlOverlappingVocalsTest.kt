package com.ella.music.data.parser

import com.ella.music.data.model.*
import org.junit.Assert.*
import org.junit.Test

class TtmlOverlappingVocalsTest {
    @Test fun againAndOhAreBothSingingWithoutSpeakerLabels() {
        val lyrics = EllaLyricsParser.parse("""
            <tt xmlns="http://www.w3.org/ns/ttml"><body><div>
              <p begin="6s" end="8s"><span begin="6s" end="8s">See you again</span></p>
              <p begin="7s" end="9s"><span begin="7s" end="9s">Oh, oh</span></p>
            </div></body></tt>
        """.trimIndent()).lyrics
        assertEquals(2, lyrics.size)
        assertTrue(lyrics[0].isSingingAt(7500, lyrics[1]))
        assertTrue(lyrics[1].isSingingAt(7500))
        assertFalse(lyrics[0].isSingingAt(8000, lyrics[1]))
    }
    @Test fun sameAgentWithDifferentTextRetainsOwnEnd() {
        val first = LyricLine(1000,"again", listOf(LyricWord("again",1000,4000)),agent="v1",isTtml=true)
        val next = LyricLine(3000,"oh", listOf(LyricWord("oh",3000,5000)),agent="v1",isTtml=true)
        assertEquals(4000L, first.primaryEndMs(nextLine=next))
        assertTrue(first.isSingingAt(3500,next))
        assertTrue(next.isSingingAt(3500))
    }
}
