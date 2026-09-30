package com.ella.music.ui.player

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.parser.parseTtml
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    // Exact word and reading spans from the supplied Hallelujah FLAC, including Latin "So".
    private fun hallelujahLine() = parseTtml("""
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word">
          <head><metadata><iTunesMetadata xmlns="http://music.apple.com/lyric-ttml-internal">
            <transliterations><transliteration xml:lang="en-Latn"><text for="L19">
              <span begin="1:17.130" end="1:17.320">ソー</span>
              <span begin="1:18.970" end="1:19.880">さくら</span>
              <span begin="1:19.880" end="1:20.370">うみ</span>
              <span begin="1:20.370" end="1:21.090">もみじ</span>
              <span begin="1:21.090" end="1:21.260">ゆき</span>
              <span begin="1:21.260" end="1:21.500">け</span>
              <span begin="1:21.500" end="1:22.070">しょう</span>
            </text></transliteration></transliterations>
          </iTunesMetadata></metadata></head>
          <body><div><p begin="1:17.130" end="1:22.070" itunes:key="L19">
            <span begin="1:17.130" end="1:17.320">So</span> <span begin="1:17.320" end="1:17.870">ベ</span><span begin="1:17.870" end="1:18.010">リ</span><span begin="1:18.010" end="1:18.160">ー</span> <span begin="1:18.160" end="1:18.320">ベ</span><span begin="1:18.320" end="1:18.470">リ</span><span begin="1:18.470" end="1:18.620">ー</span> <span begin="1:18.970" end="1:19.880">桜</span> <span begin="1:19.880" end="1:20.370">海</span> <span begin="1:20.370" end="1:21.090">紅葉</span> <span begin="1:21.090" end="1:21.260">雪</span><span begin="1:21.260" end="1:21.500">化</span><span begin="1:21.500" end="1:22.070">粧</span>
          </p></div></body>
        </tt>
    """.trimIndent())!!.lyrics.single()

    @Test fun kanaReadingOfLatinSoKeepsItsSourceWordTiming() {
        val line = hallelujahLine()
        assertEquals("ソー", line.pronunciationWords.first().text)
        assertEquals(77_130L, line.pronunciationWords.first().startMs)
        assertEquals(77_320L, line.pronunciationWords.first().endMs)
    }

    @Test fun sparseReadingsAndCompoundMomijiStayOnTheirOriginalWords() {
        val line = hallelujahLine()
        val words = line.words.moveLeadingSpacesToPreviousWord()
        assertTrue(rubySpansAlignWithWords(words, line.pronunciationWords))
        assertEquals(listOf("ソー", "", "", "", "", "", "", "さくら", "うみ", "もみじ", "ゆき", "け", "しょう"),
            rubiesForTimedWords(words, line.pronunciationWords, line.pronunciation.orEmpty()))
        assertEquals(80_370L, line.pronunciationWords[3].startMs)
        assertEquals(81_090L, line.pronunciationWords[3].endMs)
    }

    @Test fun kanaKeepsOriginalWordTimesWhenDisplayWordsIncludeTimedPunctuation() {
        for (inline in listOf(false, true)) {
            val lines = punctuatedLatinRubyLines(inline)
            assertEquals(listOf("Lie, Lie, Lie, Lie,", "Why? Why? Why? Why?", "曖昧, My, Mine"),
                lines.map { it.text })
            val expectedReadings = listOf(
                List(4) { "ライ" }, List(4) { "ワイ" }, listOf("あい", "まい", "マイ", "マイン")
            )
            val expectedEnds = listOf(
                listOf(16291L, 16730L, 17389L, 17790L),
                listOf(18301L, 18605L, 19295L, 19720L),
                listOf(34137L, 34289L, 34907L, 35547L)
            )
            lines.forEachIndexed { index, line ->
                assertEquals(expectedReadings[index], line.pronunciationWords.map { it.text })
                assertEquals(expectedEnds[index], line.pronunciationWords.map { it.endMs })
                val words = line.words.moveLeadingSpacesToPreviousWord()
                assertEquals(line.text, words.joinToString("") { it.text })
                assertTrue(rubySpansAlignWithWords(words, line.pronunciationWords))
            }
            assertEquals(listOf(16340L, 16861L, 17450L, 17866L), lines[0].words.map { it.endMs })
            assertEquals(List(4) { "ライ" }, rubiesForTimedWords(lines[0].words,
                lines[0].pronunciationWords, lines[0].pronunciation.orEmpty()))
            assertEquals(List(4) { "ワイ" }, rubiesForTimedWords(lines[1].words,
                lines[1].pronunciationWords, lines[1].pronunciation.orEmpty()))
            assertEquals(listOf("あい", "まい", "", "マイ", "", "マイン"),
                rubiesForTimedWords(lines[2].words, lines[2].pronunciationWords, ""))
        }
    }

    @Test fun punctuationExceptionDoesNotAcceptAPartialPhraseOrKanjiReading() {
        val reading = listOf(LyricWord("ライ", 0, 200))
        for (punctuation in listOf(",", "?", "，", "？", "!", "…")) {
            assertEquals(listOf("ライ"), groupRubySpansByWord(
                listOf(LyricWord("Lie$punctuation ", 0, 400)), reading))
        }
        for (text in listOf("Lie", "Hello world?", "紅葉？")) {
            assertNull(groupRubySpansByWord(listOf(LyricWord(text, 0, 400)), reading))
        }
    }
}

/** Minimal word/reading spans from the supplied Lie, Lie, Lie MP3, without the full song. */
internal fun punctuatedLatinRubyLines(inlinePronunciation: Boolean = false): List<LyricLine> {
    val main = listOf(
        listOf(LyricWord("Lie", 16147, 16291), LyricWord(",", 16291, 16340),
            LyricWord("Lie", 16340, 16730), LyricWord(",", 16730, 16861),
            LyricWord("Lie", 16861, 17389), LyricWord(",", 17389, 17450),
            LyricWord("Lie", 17450, 17790), LyricWord(",", 17790, 17866)),
        listOf(LyricWord("Why", 18073, 18301), LyricWord("?", 18301, 18377),
            LyricWord("Why", 18377, 18605), LyricWord("?", 18605, 18683),
            LyricWord("Why", 18683, 19295), LyricWord("?", 19295, 19501),
            LyricWord("Why", 19501, 19720), LyricWord("?", 19720, 19794)),
        listOf(LyricWord("曖", 33730, 34137), LyricWord("昧", 34137, 34289),
            LyricWord(",", 34289, 34441), LyricWord("My", 34441, 34907),
            LyricWord(",", 34907, 35141), LyricWord("Mine", 35141, 35547))
    )
    val readingTexts = listOf(List(4) { "ライ" }, List(4) { "ワイ" }, listOf("あい", "まい", "マイ", "マイン"))
    fun span(word: LyricWord) = "<span begin=\"${word.startMs}ms\" end=\"${word.endMs}ms\">${word.text}</span>"
    val readings = main.mapIndexed { index, words ->
        words.filter { it.text != "," && it.text != "?" }
            .mapIndexed { i, word -> span(word.copy(text = readingTexts[index][i])) }.joinToString("")
    }
    val metadata = if (inlinePronunciation) "" else main.indices.joinToString("") { index ->
        "<text for=\"L$index\">${readings[index]}</text>"
    }.let {
        "<head><metadata><iTunesMetadata xmlns=\"http://music.apple.com/lyric-ttml-internal\">" +
            "<transliterations><transliteration xml:lang=\"en-Latn\">$it</transliteration>" +
            "</transliterations></iTunesMetadata></metadata></head>"
    }
    val paragraphs = main.mapIndexed { index, words ->
        val spans = words.mapIndexed { i, word ->
            span(word) + if (i != words.lastIndex && word.text in listOf(",", "?")) " " else ""
        }.joinToString("")
        val reading = if (inlinePronunciation) "<span ttm:role=\"x-roman\">${readings[index]}</span>" else ""
        "<p begin=\"${words.first().startMs}ms\" end=\"${words.last().endMs}ms\" itunes:key=\"L$index\">$spans$reading</p>"
    }.joinToString("")
    return parseTtml("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\" " +
        "xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" itunes:timing=\"Word\">" +
        "$metadata<body><div>$paragraphs</div></body></tt>")!!.lyrics
}
