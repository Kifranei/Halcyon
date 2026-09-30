package com.ella.music.ui.poster

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class PosterLyricWordsTest {
    @Test fun tiltedHelixSharesAFortyFiveDegreeAxisAndFitsItsViewportThroughoutRotation() {
        for ((width, height) in listOf(700f to 220f, 340f to 150f)) {
            val wordWidth = 72f
            val wordHeight = 54f
            val (radius, orbitHeight) = posterOrbitDimensions(width, height, wordWidth, wordHeight)
            val top = posterOrbitFrame(0, 8, 0f, radius, orbitHeight).onPosterAxis()
            val bottom = posterOrbitFrame(7, 8, 0f, 0f, orbitHeight).onPosterAxis()
            assertEquals(-1f, (bottom.y - top.y) / (bottom.x - top.x), 0.001f)
            repeat(120) { phase ->
                repeat(8) { word ->
                    val frame = posterOrbitFrame(word, 8, phase.toFloat(), radius, orbitHeight).onPosterAxis()
                    assertTrue(abs(frame.x) + wordWidth / 2 <= width / 2 + 0.01f)
                    assertTrue(abs(frame.y) + wordHeight / 2 <= height / 2 + 0.01f)
                }
            }
        }
    }
    @Test fun multiwordTimingSplitsButSyllablesStayInsideTheirVisualWord() {
        val line = LyricLine(500, "Weave that prosaic poem", words = listOf(
            LyricWord("Weave that ", 500, 850), LyricWord("pro", 850, 1000),
            LyricWord("saic ", 1000, 1250), LyricWord("poem", 1300, 1800)))
        val tokens = posterLyricWords(line)
        assertEquals(listOf("Weave", "that", "prosaic", "poem"), tokens.map { it.text })
        assertEquals(listOf("pro", "saic"), tokens[2].words.map { it.text })
        assertEquals(850, tokens[2].words.first().startMs)
        assertEquals(1000, tokens[2].words[1].startMs)
        assertEquals(1800L, tokens.last().endMs)
    }

    @Test fun lineTimedWordsUseSuccessiveWindowsAndKeepGraphemesIntact() {
        val line = LyricLine(1000, "星光🙂 don't e\u0301vanish", endMs = 5000)
        val tokens = posterLyricWords(line)
        assertEquals(listOf("星", "光", "🙂", "don't", "e\u0301vanish"), tokens.map { it.text })
        assertTrue(tokens.zipWithNext().all { (a, b) -> a.endMs!! <= b.timeMs })
        assertEquals(1000, tokens.first().timeMs)
        assertEquals(5000L, tokens.last().endMs)
    }

    @Test fun readingsFollowTheCorrespondingTimedCharacterAndBackgroundOnlyLinesWork() {
        val line = LyricLine(0, "星光", words = listOf(LyricWord("星", 0, 500), LyricWord("光", 500, 1000)),
            pronunciationWords = listOf(LyricWord("xing", 0, 500), LyricWord("guang", 500, 1000)))
        assertEquals(listOf("xing", "guang"), posterLyricWords(line).map { it.pronunciation })
        assertEquals(listOf("Backing", "vocal"), posterLyricWords(LyricLine(0, "", backgroundText = "Backing vocal")).map { it.text })
    }

    @Test fun orbitMovesAroundBothSidesOfTheAxisAndChangesDepth() {
        val front = posterOrbitFrame(0, 6, 0f, 100f, 160f)
        val side = posterOrbitFrame(0, 6, PI.toFloat() / (2f * 0.48f), 100f, 160f)
        val back = posterOrbitFrame(0, 6, PI.toFloat() / 0.48f, 100f, 160f)
        val otherSide = posterOrbitFrame(0, 6, 3f * PI.toFloat() / (2f * 0.48f), 100f, 160f)
        assertEquals(0f, front.x, 0.001f)
        assertEquals(100f, side.x, 0.001f)
        assertEquals(-100f, otherSide.x, 0.001f)
        assertTrue(front.scale > back.scale)
        assertTrue(front.alpha > back.alpha)
        assertTrue(side.rotationY > 0f && otherSide.rotationY < 0f)
        assertEquals(front.y, back.y, 0.001f)
    }

    @Test fun scatteredWordsAreStableSeparatedAndInsideTheViewportIncludingRotationAndDrift() {
        val sizes = List(8) { PosterWordSize(95f + it * 3f, 40f) }
        val positions = posterScatteredWords(sizes, 800f, 280f, 71, 3f)
        assertEquals(positions, posterScatteredWords(sizes, 800f, 280f, 71, 3f))
        assertNotEquals(positions, posterScatteredWords(sizes, 800f, 280f, 72, 3f))
        positions.forEachIndexed { index, p ->
            val angle = p.rotation * PI.toFloat() / 180f
            val halfWidth = (abs(cos(angle)) * sizes[index].width + abs(sin(angle)) * sizes[index].height) / 2 + 3
            val halfHeight = (abs(sin(angle)) * sizes[index].width + abs(cos(angle)) * sizes[index].height) / 2 + 3
            assertTrue(p.x - halfWidth >= 0 && p.x + halfWidth <= 800f)
            assertTrue(p.y - halfHeight >= 0 && p.y + halfHeight <= 280f)
            positions.take(index).forEach { other ->
                assertTrue(abs(p.x - other.x) > 60f || abs(p.y - other.y) > 38f)
            }
        }
    }
}
