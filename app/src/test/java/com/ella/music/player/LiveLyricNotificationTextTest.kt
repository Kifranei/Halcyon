package com.ella.music.player

import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveLyricNotificationTextTest {
    @Test
    fun currentWordHandlesBoundariesGapsOverlapsAndSeek() {
        val words = listOf(
            LyricWord("one", 0L, 500L),
            LyricWord("two", 500L, 1_000L),
            LyricWord("three", 900L, 1_300L)
        )

        assertEquals(-1, currentLiveLyricWordIndex(words, -1L))
        assertEquals(0, currentLiveLyricWordIndex(words, 0L))
        assertEquals(0, currentLiveLyricWordIndex(words, 500L - 1L))
        assertEquals(1, currentLiveLyricWordIndex(words, 500L))
        assertEquals(2, currentLiveLyricWordIndex(words, 950L))
        assertEquals(2, currentLiveLyricWordIndex(words, 1_200L))
        assertEquals(-1, currentLiveLyricWordIndex(words, 1_300L))

        val gapWords = listOf(
            LyricWord("one", 0L, 500L),
            LyricWord("two", 700L, 1_000L)
        )
        assertEquals(-1, currentLiveLyricWordIndex(gapWords, 600L))
    }

    @Test
    fun windowMovesFromFirstToMiddleToLastWithoutClippingCurrentWord() {
        val words = listOf("one", "two", "three", "four", "five", "six")

        val first = buildLiveLyricWindow(words, currentWordIndex = 0, maxCodePoints = 14)
        val middle = buildLiveLyricWindow(words, currentWordIndex = 3, maxCodePoints = 14)
        val last = buildLiveLyricWindow(words, currentWordIndex = 5, maxCodePoints = 14)

        assertTrue(first.contains("one"))
        assertFalse(first.startsWith("…"))
        assertTrue(first.endsWith("…"))
        assertTrue(middle.contains("four"))
        assertTrue(middle.startsWith("…"))
        assertTrue(middle.endsWith("…"))
        assertTrue(last.contains("six"))
        assertTrue(last.startsWith("…"))
        assertFalse(last.endsWith("…"))
    }

    @Test
    fun originalWordsDriveCompactTextAndLongWordIsNotDropped() {
        val line = LyricLine(
            timeMs = 0L,
            text = "one supercalifragilistic word",
            words = listOf(
                LyricWord("one", 0L, 500L),
                LyricWord("supercalifragilistic", 500L, 1_000L),
                LyricWord("word", 1_000L, 1_500L)
            )
        )

        val display = buildLiveLyricNotificationText(
            line = line,
            mode = SettingsManager.LIVE_UPDATE_LYRIC_MODE_ORIGINAL,
            positionMs = 700L
        )

        assertEquals(1, display?.wordIndex)
        assertTrue(display?.lyric?.contains("supercalifragilistic") == true)
        assertEquals(line.text, display?.fullLyric)
        assertEquals("supercalifragilistic", display?.compactLyric)
        assertTrue(display?.allowLongCompactLyric == true)
    }

    @Test
    fun timedLineGapKeepsNearestWordWindowInsteadOfExpandingToWholeLine() {
        val words = (1..12).map { number ->
            val startMs = (number - 1) * 500L + if (number == 12) 500L else 0L
            LyricWord(
                text = "word$number",
                startMs = startMs,
                endMs = startMs + 500L
            )
        }
        val line = LyricLine(
            timeMs = 0L,
            text = words.joinToString(" ") { it.text },
            words = words
        )

        val display = buildLiveLyricNotificationText(
            line = line,
            mode = SettingsManager.LIVE_UPDATE_LYRIC_MODE_ORIGINAL,
            positionMs = 5_750L
        )

        assertEquals(-1, display?.wordIndex)
        assertTrue(display?.lyric?.contains("word11") == true)
        assertTrue(display?.lyric != line.text)
        assertEquals("word11", display?.compactLyric)
    }

    @Test
    fun emptyWordsKeepWholeLineAndCompactTextIsCodePointSafe() {
        val line = LyricLine(
            timeMs = 0L,
            text = "😀这是没有逐字时间轴的长歌词",
            words = emptyList()
        )

        val display = buildLiveLyricNotificationText(
            line = line,
            mode = SettingsManager.LIVE_UPDATE_LYRIC_MODE_ORIGINAL,
            positionMs = 900L
        )

        assertEquals(line.text, display?.lyric)
        assertEquals(-1, display?.wordIndex)
        val compact = requireNotNull(display).compactLyric
        assertTrue(compact.codePointCount(0, compact.length) <= 7)
        assertFalse(compact.endsWith("\uD83D"))
    }

    @Test
    fun chineseWordsAreJoinedWithoutArtificialSpaces() {
        val line = LyricLine(
            timeMs = 0L,
            text = "我想和你看海",
            words = listOf(
                LyricWord("我", 0L, 300L),
                LyricWord("想", 300L, 600L),
                LyricWord("和", 600L, 900L),
                LyricWord("你", 900L, 1_200L),
                LyricWord("看", 1_200L, 1_500L),
                LyricWord("海", 1_500L, 1_800L)
            )
        )

        val display = buildLiveLyricNotificationText(
            line = line,
            mode = SettingsManager.LIVE_UPDATE_LYRIC_MODE_ORIGINAL,
            positionMs = 1_000L
        )

        assertEquals(3, display?.wordIndex)
        assertTrue(display?.lyric?.contains("你") == true)
        assertFalse(display?.lyric?.contains(' ') == true)
        assertEquals("你", display?.compactLyric)
    }

    @Test
    fun translationDoesNotFollowOriginalWordIndex() {
        val line = LyricLine(
            timeMs = 0L,
            text = "hello world",
            words = listOf(
                LyricWord("hello", 0L, 500L),
                LyricWord("world", 500L, 1_000L)
            ),
            translation = "你好，世界"
        )

        val display = buildLiveLyricNotificationText(
            line = line,
            mode = SettingsManager.LIVE_UPDATE_LYRIC_MODE_TRANSLATION,
            positionMs = 700L
        )

        assertEquals("你好，世界", display?.lyric)
        assertEquals("你好，世界", display?.fullLyric)
        assertEquals(-1, display?.wordIndex)

        val missingTranslation = buildLiveLyricNotificationText(
            line = line.copy(translation = null),
            mode = SettingsManager.LIVE_UPDATE_LYRIC_MODE_TRANSLATION,
            positionMs = 700L
        )
        assertEquals("hello world", missingTranslation?.lyric)
        assertEquals(-1, missingTranslation?.wordIndex)
    }

    @Test
    fun pronunciationUsesItsOwnWordTimelineAndFallsBackToWholeLine() {
        val line = LyricLine(
            timeMs = 0L,
            text = "你好",
            words = listOf(LyricWord("你好", 0L, 1_000L)),
            pronunciation = "ni hao",
            translation = "你好",
            pronunciationWords = listOf(
                LyricWord("ni", 0L, 400L),
                LyricWord("hao", 400L, 800L)
            )
        )
        val pronunciation = buildLiveLyricNotificationText(
            line = line,
            mode = SettingsManager.LIVE_UPDATE_LYRIC_MODE_PRONUNCIATION,
            positionMs = 500L
        )

        assertEquals(1, pronunciation?.wordIndex)
        assertEquals("hao", pronunciation?.compactLyric)

        assertEquals(
            "你好",
            buildLiveLyricSecondaryText(
                line,
                SettingsManager.LIVE_UPDATE_LYRIC_SECONDARY_MODE_TRANSLATION
            )
        )
        assertEquals(
            "ni hao",
            buildLiveLyricSecondaryText(
                line,
                SettingsManager.LIVE_UPDATE_LYRIC_SECONDARY_MODE_PRONUNCIATION
            )
        )

        val fallback = buildLiveLyricNotificationText(
            line = line.copy(pronunciation = null, pronunciationWords = emptyList()),
            mode = SettingsManager.LIVE_UPDATE_LYRIC_MODE_PRONUNCIATION,
            positionMs = 500L
        )
        assertEquals("你好", fallback?.lyric)
        assertEquals(-1, fallback?.wordIndex)
    }
}
