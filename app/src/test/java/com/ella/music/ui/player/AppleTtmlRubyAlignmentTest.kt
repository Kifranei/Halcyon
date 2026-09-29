package com.ella.music.ui.player

import com.ella.music.data.parser.parseTtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Apple Music TTML transliterations: "nan" spans exactly the word "なん" (JANE DOE, line 2). */
class AppleTtmlRubyAlignmentTest {
    private val ttml = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word" xml:lang="ja"><head><metadata><iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal"><transliterations><transliteration xml:lang="ja-Latn"><text for="L2"><span begin="25.165" end="26.056" xmlns="http://www.w3.org/ns/ttml">nan</span><span begin="26.056" end="26.917" xmlns="http://www.w3.org/ns/ttml">te</span> <span begin="26.917" end="27.840" xmlns="http://www.w3.org/ns/ttml">suko</span><span begin="27.840" end="28.365" xmlns="http://www.w3.org/ns/ttml">shi</span></text></transliteration></transliterations></iTunesMetadata></metadata></head><body><div><p begin="25.165" end="28.365" itunes:key="L2" ttm:agent="v1"><span begin="25.165" end="26.056">なん</span><span begin="26.056" end="26.917">て</span><span begin="26.917" end="27.840">少</span><span begin="27.840" end="28.365">し</span></p></div></body></tt>"""

    @Test fun wholeWordReadingsStayOnTheirWords() {
        val line = parseTtml(ttml)!!.lyrics.single { it.words.isNotEmpty() }
        assertEquals(listOf("なん", "て", "少", "し"), line.words.map { it.text.trim() })
        assertTrue(rubySpansAlignWithWords(line.words, line.pronunciationWords))
        val rubies = rubiesForTimedWords(line.words, line.pronunciationWords, "")
        assertEquals(listOf("nan", "te", "suko", "shi"), rubies)
    }

    @Test fun splitCharactersStillUsedWhenReadingsDoNotAlign() {
        val line = parseTtml(ttml)!!.lyrics.single { it.words.isNotEmpty() }
        val single = listOf(line.pronunciationWords.first().copy(endMs = line.words.last().endMs, text = "nantesukoshi"))
        assertTrue(!rubySpansAlignWithWords(line.words, single))
    }

    @Test fun severalReadingsInsideOneWordAreJoined() {
        val words = listOf(
            com.ella.music.data.model.LyricWord("を", 24001, 24505),
            com.ella.music.data.model.LyricWord(" 今も", 24953, 26141),
            com.ella.music.data.model.LyricWord("思い", 26141, 26815)
        )
        val readings = listOf(
            com.ella.music.data.model.LyricWord("o", 24001, 24505),
            com.ella.music.data.model.LyricWord("ima", 24953, 25547),
            com.ella.music.data.model.LyricWord("mo", 25547, 26141),
            com.ella.music.data.model.LyricWord("omoi", 26141, 26815)
        )
        assertTrue(rubySpansAlignWithWords(words, readings))
        assertEquals(listOf("o", "ima mo", "omoi"), rubiesForTimedWords(words, readings, ""))
    }
}
