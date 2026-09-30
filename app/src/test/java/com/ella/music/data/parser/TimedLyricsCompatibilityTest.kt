package com.ella.music.data.parser

import android.app.Application
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class TimedLyricsCompatibilityTest {
    @Test fun issue689ChineseWithFinalSpaceStillReachesTheWordRenderer() {
        val line = LrcParser.parse("[00:00.000] <00:00.00>中<00:01.00>文 <00:02.00>").lyrics.single()
        assertEquals("中文", line.text)
        assertEquals(listOf("中", "文"), line.words.map { it.text })
        assertEquals(listOf(0L, 1000L), line.words.map { it.startMs })
        assertEquals(listOf(1000L, 2000L), line.words.map { it.endMs })
        assertEquals(2000L, line.endMs)
        assertWordRendererAccepts(line)
    }

    @Test fun issue689MixedChineseAndEnglishKeepsTheInternalSpaceAndWordTimes() {
        val line = LrcParser.parse("[00:00.000] <00:00.00>中<00:01.00>文 <00:02.00>english <00:03.00>").lyrics.single()
        assertEquals("中文 english", line.text)
        assertEquals(listOf("中", "文 ", "english"), line.words.map { it.text })
        assertEquals(listOf(0L, 1000L, 2000L), line.words.map { it.startMs })
        assertWordRendererAccepts(line)
    }

    @Test fun qrcUsesAbsoluteWordTimesAndNeverDisplaysTheTimingSyntax() {
        val line = LrcParser.parse("[721,179]混(721,22)音(744,22)师(766,22) (789,22)Mixing (811,22)Engineer：(834,22)测(857,22)试(879,22)").lyrics.single()
        assertEquals(721L, line.timeMs)
        assertEquals("混音师 Mixing Engineer：测试", line.text)
        assertEquals(listOf(721L,744L,766L,811L,834L,857L,879L), line.words.map { it.startMs })
        assertTrue(line.words.all { it.endMs - it.startMs == 22L })
        assertFalse(line.isTtml)
        assertWordRendererAccepts(line)
    }

    @Test fun qrcKeepsMetadataOffsetAndTheFullLineDuration() {
        val parsed = LrcParser.parse("[ti:Sample]\n[ar:Artist]\n[al:Album]\n[offset:25]\n[500,400]中(500,100)文(600,200)")
        assertEquals("Sample", parsed.title)
        assertEquals("Artist", parsed.artist)
        assertEquals("Album", parsed.album)
        assertEquals(25L, parsed.offset)
        val line = parsed.lyrics.single()
        assertEquals(475L, line.timeMs)
        assertEquals(listOf(475L,575L), line.words.map { it.startMs })
        assertEquals(875L, line.endMs)
    }

    @Test fun qrcPreservesLiteralParenthesesAndUntimedClosingPunctuation() {
        val line = LrcParser.parse("[0,600]版(0,100) ((100,100)试(200,100))").lyrics.single()
        assertEquals("版 (试)", line.text)
        assertEquals("试)", line.words.last().text)
        assertEquals(200L, line.words.last().startMs)
        assertEquals(600L, line.endMs)
        assertWordRendererAccepts(line)
    }

    @Test fun krcWithLiteralNumericParenthesesKeepsItsOwnDecoderAndRelativeTimes() {
        val line = LrcParser.parse("[1000,500]<0,100,0>值(100,20)<100,400,0>末").lyrics.single()
        assertEquals("值(100,20)末", line.text)
        assertEquals(listOf(1000L,1100L), line.words.map { it.startMs })
    }

    @Test fun whitespaceOnlyBoundaryTokensDoNotDiscardTheActualTimedWords() {
        val line = LrcParser.parse("[00:10.000]<00:10.000> <00:10.200>中<00:10.600>文<00:11.000> <00:11.200>").lyrics.single()
        assertEquals("中文", line.text)
        assertEquals(listOf("中", "文"), line.words.map { it.text })
        assertEquals(listOf(10200L,10600L), line.words.map { it.startMs })
        assertEquals(11000L, line.endMs)
        assertWordRendererAccepts(line)
    }

    @Test fun androidPlainTextCleaningPreservesSpacesBeforeLatinWords() {
        val line = LrcParser.parse("[00:12.000]<00:12.000>中<00:13.000>文<00:14.000> Day<00:15.000> by<00:16.000> day<00:17.000>").lyrics.single()
        assertEquals("中文 Day by day", line.text)
        assertEquals(listOf("中", "文", " Day", " by", " day"), line.words.map { it.text })
        assertWordRendererAccepts(line)
    }

    @Test fun theAttachedLrcKeepsEveryTimedLineAndItsLastWord() {
        val path = System.getenv("HALCYON_LRC_FIXTURE")
        assumeTrue(!path.isNullOrBlank() && File(path).isFile)
        val content = File(checkNotNull(path)).readText(Charsets.UTF_8).removePrefix("\uFEFF")
        val expected = content.lineSequence().count { it.startsWith('[') && it.contains('<') }
        val lines = LrcParser.parse(content).lyrics
        assertEquals(expected + 1, lines.size)
        assertEquals(expected, lines.count { it.hasExplicitWordTiming })
        lines.filter { it.hasExplicitWordTiming }.forEach(::assertWordRendererAccepts)
        val first = lines.first { it.timeMs == 27463L }
        assertEquals("眶", first.words.last().text)
        assertEquals(32678L, first.words.last().startMs)
        assertEquals(33400L, first.words.last().endMs)
    }

    @Test fun theAttachedMp3UsltQrcProducesAll48SynchronizedLines() {
        val path = System.getenv("HALCYON_MP3_LYRICS_FIXTURE")
        assumeTrue(!path.isNullOrBlank() && File(path).isFile)
        val parsed = LrcParser.parse(File(checkNotNull(path)).readText(Charsets.UTF_8))
        assertEquals("一笑江湖 (清水er版)", parsed.title)
        assertEquals(48, parsed.lyrics.size)
        assertEquals("混音师 Mixing Engineer：唐瑜", parsed.lyrics.first { it.timeMs == 721L }.text)
        assertTrue(parsed.lyrics.all { it.words.isNotEmpty() && !it.isTtml })
        parsed.lyrics.forEach(::assertWordRendererAccepts)
    }

    private fun assertWordRendererAccepts(line: LyricLine) {
        assertEquals(line.text, line.words.joinToString("") { it.text })
        // Exercise the player's actual timed-word matcher: a single unmatched trailing space
        // used to return an empty list here and send the entire CJK line to its plain fallback.
        val mapper = Class.forName("com.ella.music.ui.player.AppleMusicKaraokeTextKt")
            .getDeclaredMethod("toAppleMusicRenderWords", List::class.java, String::class.java, Int::class.javaPrimitiveType)
            .apply { isAccessible = true }
        val rendered = mapper.invoke(null, line.words, line.text, 500) as List<*>
        assertTrue("The player must accept timed words for ${line.timeMs}", rendered.isNotEmpty())
        val text = rendered.joinToString("") { unit ->
            val word = checkNotNull(unit).javaClass.getDeclaredMethod("getWord")
                .apply { isAccessible = true }.invoke(unit) as LyricWord
            word.text
        }
        assertEquals("The rendered words must keep every visible character and separator", line.text, text)
    }
}
