package com.ella.music.ui.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.*
import org.junit.Test

class ReferenceLyricMotionTest {
    @Test fun springPreservesPositionAndVelocityAtRetarget() {
        val first = ReferenceLyricSpring(100f, 0f, 0f)
        val position = first.position(.2f)
        val velocity = first.velocity(.2f)
        val second = ReferenceLyricSpring(position, velocity, -100f)
        assertEquals(position, second.position(0f), .0001f)
        assertEquals(velocity, second.velocity(0f), .001f)
        assertEquals(-100f, second.position(4f), .01f)
    }
    @Test fun springIsIndependentOfRefreshRate() {
        fun stepAt(rate: Int): Float {
            var position = 100f
            var velocity = -20f
            repeat(rate) {
                val spring = ReferenceLyricSpring(position, velocity, 0f)
                position = spring.position(1f / rate)
                velocity = spring.velocity(1f / rate)
            }
            return position
        }
        val direct = ReferenceLyricSpring(100f, -20f, 0f).position(1f)
        assertEquals(direct, stepAt(60), .002f)
        assertEquals(direct, stepAt(120), .002f)
    }
    @Test fun wordLiftUsesElapsedTimeAndSettles() {
        assertEquals(0f, appleReferenceWordLift(-100L), 0f)
        assertEquals(0f, appleReferenceWordLift(0L), 0f)
        assertTrue(appleReferenceWordLift(100L) < appleReferenceWordLift(400L))
        assertEquals(1f, appleReferenceWordLift(4000L), .001f)
    }
    @Test fun sustainedEmphasisReleasesAndShortWordsDoNotGrow() {
        assertEquals(0f, appleReferenceEmphasis(300L, 500L), 0f)
        assertEquals(1f, appleReferenceEmphasis(2000L, 2000L), .001f)
        assertEquals(0f, appleReferenceEmphasis(2500L, 2000L), .001f)
    }
    @Test fun maskIsOrderedAndMirrorsForRtl() {
        for (p in listOf(0f, .1f, .5f, .9f, 1f)) {
            val left = appleReferenceFillStops(p, Color.White, false, .3f)
            val right = appleReferenceFillStops(p, Color.White, true, .3f)
            assertTrue(left.toList().zipWithNext().all { it.first.first <= it.second.first })
            left.indices.forEach { i ->
                assertEquals(1f - left[i].first, right[3-i].first, .0001f)
                assertEquals(left[i].second, right[3-i].second)
            }
        }
    }
}
