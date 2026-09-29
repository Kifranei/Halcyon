package com.ella.music.data.parser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TtmlTimingTest {

    @Test
    fun timeExpressionsSupportOffsetClockAndSmpteForms() {
        assertEquals(12_500L, parseTtmlTimeExpression("12.5s"))
        assertEquals(500L, parseTtmlTimeExpression("500ms"))
        assertEquals(90_000L, parseTtmlTimeExpression("1.5m"))
        assertEquals(3_600_000L, parseTtmlTimeExpression("1h"))
        assertEquals(500L, parseTtmlTimeExpression("15f", frameRate = 30.0))
        assertEquals(2_000L, parseTtmlTimeExpression("20t", tickRate = 10.0))
        assertEquals(62_500L, parseTtmlTimeExpression("00:01:02.500"))
        assertEquals(1_500L, parseTtmlTimeExpression("00:00:01:15", frameRate = 30.0))
        // Lenient forms used by Apple Music / AMLL exports keep their historic values.
        assertEquals(83_456L, parseTtmlTimeExpression("1:23.456"))
        assertEquals(12_345L, parseTtmlTimeExpression("12.345"))
        assertEquals(12_000L, parseTtmlTimeExpression("12"))
        assertEquals(4_100L, parseTtmlTimeExpression("0:04.1"))
        assertEquals(4_123L, parseTtmlTimeExpression("4.12345"))
    }

    @Test
    fun invalidTimeExpressionsReturnNull() {
        assertNull(parseTtmlTimeExpression(""))
        assertNull(parseTtmlTimeExpression("abc"))
        assertNull(parseTtmlTimeExpression("12.5x"))
        assertNull(parseTtmlTimeExpression("1:2:3:4:5"))
        assertNull(parseTtmlTimeExpression("-1s"))
        assertNull(parseTtmlTimeExpression("."))
    }

    @Test
    fun metaOffsetShiftsLinesEarlierLikeLrcOffset() {
        val plain = EllaLyricsParser.parse(ttmlWithHead(""))
        val shifted = EllaLyricsParser.parse(ttmlWithHead("""<amll:meta key="offset" value="500"/>"""))

        assertEquals(listOf(2_000L, 5_000L), plain.lyrics.map { it.timeMs })
        assertEquals(listOf(1_500L, 4_500L), shifted.lyrics.map { it.timeMs })
        assertEquals(listOf(1_500L, 2_000L), shifted.lyrics.first().words.map { it.startMs })
        assertEquals(500L, shifted.offset)

        // Same sign convention as LRC: [offset:500] makes lyrics appear 500 ms earlier.
        val lrc = EllaLyricsParser.parse("[offset:500]\n[00:02.000]Hello world\n[00:05.000]Second line")
        assertEquals(shifted.lyrics.map { it.timeMs }, lrc.lyrics.map { it.timeMs })
    }

    @Test
    fun rootOffsetAttributeAcceptsTimeExpression() {
        val result = EllaLyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" offset="0.25s">
              <body><div>
                <p begin="2.000" end="3.000">Hello</p>
              </div></body>
            </tt>
            """.trimIndent()
        )
        assertEquals(1_750L, result.lyrics.single().timeMs)
    }

    @Test
    fun divBeginIsAddedToRelativeChildTimes() {
        val result = EllaLyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
              <body>
                <div begin="1s">
                  <p begin="0.000" end="1.000"><span begin="0.000" end="0.500">Hello</span> <span begin="0.500" end="1.000">there</span></p>
                  <p begin="2.000" end="3.000">
                    Second line
                    <span ttm:role="x-bg"><span begin="2.200" end="2.800">(ooh)</span></span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )

        assertEquals(listOf(1_000L, 3_000L), result.lyrics.map { it.timeMs })
        assertEquals(listOf(2_000L, 4_000L), result.lyrics.map { it.endMs })
        assertEquals(listOf(1_000L, 1_500L), result.lyrics.first().words.map { it.startMs })
        assertEquals(3_200L, result.lyrics[1].backgroundStartMs)
        assertEquals(3_800L, result.lyrics[1].backgroundEndMs)
    }

    @Test
    fun appleStyleDivWithAbsoluteChildTimesIsNotShifted() {
        val result = EllaLyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word">
              <body dur="30.000">
                <div begin="10.000" end="20.000" itunes:songPart="Verse">
                  <p begin="10.000" end="12.000" itunes:key="L1"><span begin="10.000" end="11.000">Hello</span> <span begin="11.000" end="12.000">world</span></p>
                  <p begin="15.000" end="20.000" itunes:key="L2"><span begin="15.000" end="20.000">Again</span></p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )
        assertEquals(listOf(10_000L, 15_000L), result.lyrics.map { it.timeMs })
        assertEquals(listOf(10_000L, 11_000L), result.lyrics.first().words.map { it.startMs })
    }

    @Test
    fun paragraphWithoutValidBeginInheritsFirstTimedSpan() {
        val result = EllaLyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml">
              <body><div>
                <p begin="bogus"><span begin="3.000" end="3.500">Late</span> <span begin="3.500" end="4.000">start</span></p>
                <p begin="bogus">Untimed line</p>
                <p begin="1.000" end="2.000">First</p>
              </div></body>
            </tt>
            """.trimIndent()
        )
        assertEquals(listOf("First", "Late start"), result.lyrics.map { it.text })
        assertEquals(listOf(1_000L, 3_000L), result.lyrics.map { it.timeMs })
    }

    @Test
    fun smpteTimesUseDocumentFrameRate() {
        val result = EllaLyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttp="http://www.w3.org/ns/ttml#parameter" ttp:frameRate="25">
              <body><div>
                <p begin="00:00:01:05" end="00:00:02:00">Hello</p>
                <p begin="50f" end="75f">World</p>
              </div></body>
            </tt>
            """.trimIndent()
        )
        assertEquals(listOf(1_200L, 2_000L), result.lyrics.map { it.timeMs })
        assertEquals(listOf(2_000L, 3_000L), result.lyrics.map { it.endMs })
    }

    @Test
    fun plainAppleTtmlKeepsHistoricTimes() {
        val result = EllaLyricsParser.parse(
            """
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" itunes:timing="Word">
              <head><metadata><ttm:agent type="person" xml:id="v1"/></metadata></head>
              <body dur="1:30.000">
                <div>
                  <p begin="0:04.120" end="0:06.5" itunes:key="L1" ttm:agent="v1"><span begin="0:04.120" end="0:05.000">Hello</span> <span begin="0:05.000" end="0:06.5">world</span></p>
                  <p begin="62.345" end="1:04.000" itunes:key="L2" ttm:agent="v1">
                    <span begin="62.345" end="63.000">Second</span> <span begin="63.000" end="1:04.000">line</span>
                    <span ttm:role="x-bg"><span begin="1:03.100" end="1:03.900">(ooh)</span></span>
                  </p>
                </div>
              </body>
            </tt>
            """.trimIndent()
        )

        assertEquals(listOf(4_120L, 62_345L), result.lyrics.map { it.timeMs })
        assertEquals(listOf(6_500L, 64_000L), result.lyrics.map { it.endMs })
        assertEquals(listOf(4_120L, 5_000L), result.lyrics[0].words.map { it.startMs })
        assertEquals(listOf(5_000L, 6_500L), result.lyrics[0].words.map { it.endMs })
        assertEquals(listOf(62_345L, 63_000L), result.lyrics[1].words.map { it.startMs })
        assertEquals(63_100L, result.lyrics[1].backgroundStartMs)
        assertEquals(63_900L, result.lyrics[1].backgroundEndMs)
        assertEquals(0L, result.offset)
    }

    private fun ttmlWithHead(headMeta: String): String =
        """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:amll="http://www.example.com/ns/amll">
          <head><metadata>$headMeta</metadata></head>
          <body>
            <div>
              <p begin="2.000" end="4.000"><span begin="2.000" end="2.500">Hello</span> <span begin="2.500" end="4.000">world</span></p>
              <p begin="5.000" end="6.000">Second line</p>
            </div>
          </body>
        </tt>
        """.trimIndent()
}
