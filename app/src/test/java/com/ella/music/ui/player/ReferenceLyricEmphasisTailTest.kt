package com.ella.music.ui.player

import androidx.compose.animation.core.CubicBezierEasing
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos

class ReferenceLyricEmphasisTailTest {
    private val attackEasing = CubicBezierEasing(.25f, .1f, .25f, 1f)

    @Test fun attackIsUnchanged() {
        for (duration in listOf(1000L, 2000L, 3000L, 6000L)) {
            val attack = duration.coerceAtMost(3000L).toFloat()
            for (elapsed in listOf(1L, 100L, 500L, 999L, 1500L, 2999L, 3000L, 5000L, duration)) {
                if (elapsed > duration) continue
                val expected = attackEasing.transform((elapsed / attack).coerceIn(0f, 1f))
                assertEquals("d=$duration t=$elapsed", expected, appleReferenceEmphasis(elapsed, duration), 1e-6f)
            }
        }
        assertEquals(0f, appleReferenceEmphasis(0L, 2000L), 0f)
        assertEquals(0f, appleReferenceEmphasis(-50L, 2000L), 0f)
        assertEquals(0f, appleReferenceEmphasis(800L, 999L), 0f)
    }

    @Test fun releaseIsFlamingoWaveTailAndSettles() {
        val duration = 2000L
        // Continuous with the held peak, S-shaped, gone after 500 ms; no overshoot (Flamingo has none).
        assertEquals(1f, appleReferenceEmphasis(duration + 1L, duration), .001f)
        assertEquals(.5f, appleReferenceEmphasis(duration + 250L, duration), .001f)
        assertEquals(0f, appleReferenceEmphasis(duration + 500L, duration), 0f)
        assertEquals(0f, appleReferenceEmphasis(duration + 5000L, duration), 0f)
        var previous = 1f
        for (ms in 0..600) {
            val value = appleReferenceEmphasis(duration + ms, duration)
            assertFalse(value.isNaN())
            assertTrue(value in 0f..1f)
            assertTrue("monotone at $ms", value <= previous + 1e-6f)
            previous = value
        }
    }

    @Test fun tailMatchesMeanOfStaggeredLetterFalls() {
        val decay = .34f / .66f
        val spread = 1f - decay
        for (step in 0..20) {
            val u = step / 20f
            var sum = 0.0
            val letters = 4000
            for (k in 0 until letters) {
                val v = (u - spread * k / (letters - 1)) / decay
                sum += if (v <= 0f) 1.0 else if (v >= 1f) 0.0 else cos(PI * v / 2).let { it * it }
            }
            assertEquals("u=$u", (sum / letters).toFloat(), flamingoEmphasisTail(u), .002f)
        }
        // Soft ends: almost flat right after the peak and right before rest.
        assertTrue(1f - flamingoEmphasisTail(.05f) < .005f)
        assertTrue(flamingoEmphasisTail(.95f) < .005f)
        assertEquals(0f, flamingoEmphasisTail(Float.NaN), 0f)
        assertEquals(1f, flamingoEmphasisTail(Float.NEGATIVE_INFINITY), 0f)
        assertEquals(0f, flamingoEmphasisTail(Float.POSITIVE_INFINITY), 0f)
    }

    @Test fun flamingoLettersSwellInSequenceAndRestAtWordEnd() {
        val duration = 2000L
        // First letter peaks at 34% of the word, last at 66%, both back to rest by the word end.
        assertEquals(1f, flamingoLetterEmphasis(680L, duration, 0, 5), .001f)
        assertEquals(1f, flamingoLetterEmphasis(1320L, duration, 4, 5), .001f)
        assertEquals(0f, flamingoLetterEmphasis(1360L, duration, 0, 5), .001f)
        assertEquals(0f, flamingoLetterEmphasis(duration, duration, 4, 5), .001f)
        assertEquals(0f, flamingoLetterEmphasis(600L, duration, 4, 5), 0f)
        // A single letter is centred; short words and empty words never swell.
        assertEquals(1f, flamingoLetterEmphasis(1000L, duration, 0, 1), .001f)
        assertEquals(0f, flamingoLetterEmphasis(500L, 999L, 0, 3), 0f)
        assertEquals(0f, flamingoLetterEmphasis(500L, duration, 0, 0), 0f)
        for (t in listOf(Long.MIN_VALUE, -1L, 0L, 1234L, Long.MAX_VALUE)) {
            val value = flamingoLetterEmphasis(t, duration, 2, 5)
            assertFalse(value.isNaN())
            assertTrue(value in 0f..1f)
        }
    }
}
