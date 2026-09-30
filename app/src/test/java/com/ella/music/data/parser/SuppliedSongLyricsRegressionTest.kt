package com.ella.music.data.parser

import com.ella.music.data.model.primaryEndMs
import com.ella.music.data.model.isSingingAt
import com.ella.music.ui.player.backingVocalSpans
import com.ella.music.ui.player.activeBackingVocalIndex
import com.ella.music.ui.player.toAppleMusicRenderWords
import com.ella.music.ui.player.moveLeadingSpacesToPreviousWord
import com.ella.music.ui.player.karaokeProgress
import org.junit.Assert.*
import org.junit.Test

class SuppliedSongLyricsRegressionTest {
    private fun kissLines() = EllaLyricsParser.parse(
        javaClass.getResource("/lyrics/kiss-overlapping-bg.ttml")!!.readText()
    ).lyrics

    @Test fun backingVocalContinuesAfterTheNextLeadStarts() {
        val lyrics = kissLines()
        val previous = lyrics[1]
        assertEquals(170889L, previous.backgroundEndMs)
        assertEquals(170889L, previous.primaryEndMs(nextLine = lyrics[2]))
        assertTrue(previous.isSingingAt(170500L, lyrics[2]))
        assertEquals("你吻得太逼真", previous.backgroundText)
        assertEquals(170889L, previous.backgroundWords.last().endMs)
    }

    @Test fun focusWaitsForTheActualBackingEndAndReleasesExactlyOnTime() {
        val spans = backingVocalSpans(kissLines())
        assertEquals(0, activeBackingVocalIndex(spans, 1, 169000L))
        assertEquals(1, activeBackingVocalIndex(spans, 2, 170500L))
        assertNull(activeBackingVocalIndex(spans, 2, 170889L))
        assertNull(activeBackingVocalIndex(spans, 0, 166000L))
        // Seeking straight into this overlap behaves like continuous playback.
        assertEquals(1, activeBackingVocalIndex(spans, 2, 170000L))
    }

    private fun syllableTtml(background: Boolean): String {
        val span = "<span begin=\"1s\" end=\"2s\">Me-</span><span begin=\"2s\" end=\"3s\">e-</span><span begin=\"3s\" end=\"4s\">e</span>"
        val text = if (background) "<span begin=\"1s\" end=\"4s\">Main</span><span ttm:role=\"x-bg\" begin=\"1s\" end=\"4s\">$span</span>" else span
        return "<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\"><body><div><p begin=\"1s\" end=\"4s\">$text</p></div></body></tt>"
    }

    @Test fun ttmlHyphensJoinSyllablesWithoutChangingTheirTimings() {
        for(background in listOf(false, true)) {
            val line = EllaLyricsParser.parse(syllableTtml(background)).lyrics.single()
            val text = if(background) line.backgroundText else line.text
            val words = if(background) line.backgroundWords else line.words
            assertEquals("Me-e-e", text)
            assertEquals(listOf(1000L,2000L,3000L), words.map {it.startMs})
            assertEquals(listOf(2000L,3000L,4000L), words.map {it.endMs})
        }
        assertEquals("Eeh-eeh-eeh", EllaLyricsParser.parse(syllableTtml(true).replace("Me-","Eeh-").replace(">e-<",">eeh-<").replace(">e<",">eeh<")).lyrics.single().backgroundText)
    }

    @Test fun elrcSyllablesStayJoinedWhileIndependentWordsStaySeparated() {
        val line = EllaLyricsParser.parse("[00:01.000]<00:01.000>Me-<00:02.000>e-<00:03.000>e<00:04.000>").lyrics.single()
        assertEquals("Me-e-e", line.text)
        assertEquals(listOf(1000L,2000L,3000L), line.words.map {it.startMs})
        val separate = EllaLyricsParser.parse("[00:01.000]<00:01.000>Hello <00:02.000>world<00:03.000>").lyrics.single()
        assertEquals("Hello world", separate.text)
    }

    @Test fun zeroDurationCreditPunctuationFromRamWireStaysVisibleInKaraoke() {
        val lines = EllaLyricsParser.parse("""
            <tt xmlns="http://www.w3.org/ns/ttml" xmlns:itunes="http://music.apple.com/lyric-ttml-internal" itunes:timing="Word">
              <body><div>
                <p begin="0:06.575" end="0:07.166"><span begin="0:06.575" end="0:06.767">词</span><span begin="0:06.767" end="0:06.767">：</span><span begin="0:06.767" end="0:06.866">ユ</span><span begin="0:06.866" end="0:06.966">ー</span><span begin="0:06.966" end="0:07.166">ズ</span></p>
                <p begin="0:07.166" end="0:08.686"><span begin="0:07.166" end="0:07.566">曲</span><span begin="0:07.566" end="0:07.566">：</span><span begin="0:07.566" end="0:07.667">ユ</span><span begin="0:07.667" end="0:07.767">ー</span><span begin="0:07.767" end="0:07.959">ズ</span><span begin="0:07.959" end="0:07.959"> </span><span begin="0:07.959" end="0:08.222">Monch</span><span begin="0:08.222" end="0:08.222">/</span><span begin="0:08.222" end="0:08.686">RYLL</span></p>
              </div></body>
            </tt>
        """.trimIndent()).lyrics
        assertEquals(listOf("词：ユーズ", "曲：ユーズ Monch/RYLL"), lines.map { it.text })
        for (line in lines) {
            val render = line.words.moveLeadingSpacesToPreviousWord().toAppleMusicRenderWords(line.text, 1200)
            assertEquals(line.text, render.joinToString("") { it.word.text })
            val colon = render.single { it.word.text == "：" }
            assertEquals(colon.word.startMs, colon.word.endMs)
            assertEquals(0f, colon.karaokeProgress(colon.word.startMs - 1L, active = true), 0f)
            assertEquals(1f, colon.karaokeProgress(colon.word.startMs, active = true), 0f)
        }
    }
}
