package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerWaveformSeekBarTest {
    @Test
    fun waveformLevelsAreStableAndBounded() {
        val first = progressWaveformLevels(seed = 539, count = 76, segmented = false)
        val second = progressWaveformLevels(seed = 539, count = 76, segmented = false)

        assertEquals(first, second)
        assertEquals(76, first.size)
        assertTrue(first.all { it in 0.08f..1f })
    }

    @Test
    fun segmentedTimelineKeepsBarsVisible() {
        val levels = progressWaveformLevels(seed = 542, count = 52, segmented = true)

        assertEquals(52, levels.size)
        assertTrue(levels.all { it in 0.34f..1f })
    }

    @Test
    fun waveformEndsTaperForDifferentSongsAndNarrowTimelines() {
        for (seed in listOf(0, 539, -1, Int.MAX_VALUE)) {
            for (count in listOf(1, 2, 20, 76)) {
                val levels = progressWaveformLevels(seed, count, segmented = false)
                assertEquals(0.08f, levels.first(), 0.0001f)
                assertEquals(0.08f, levels.last(), 0.0001f)
                assertTrue(levels.all { it.isFinite() && it in 0.08f..1f })
            }
        }
    }
}
