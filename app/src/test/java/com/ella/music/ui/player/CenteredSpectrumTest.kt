package com.ella.music.ui.player

import org.junit.Assert.*
import org.junit.Test

class CenteredSpectrumTest {
    @Test fun silenceHasNoArtificialFloor() {
        assertTrue(centeredSpectrumTargets(ByteArray(1024), 48000).all { it == 0f })
    }

    @Test fun lowFrequencyIsCenteredAndHighFrequencyIsAtEdges() {
        val low = ByteArray(1024).apply { this[2] = 100 }
        val bass = centeredSpectrumTargets(low, 48000)
        assertTrue(bass[31] > 0f)
        assertEquals(bass[31], bass[32], .00001f)
        assertEquals(0f, bass[0], 0f)
        val high = ByteArray(1024).apply { this[1020] = 100 }
        val treble = centeredSpectrumTargets(high, 48000)
        assertTrue(treble[0] > 0f)
        assertEquals(treble[0], treble[63], .00001f)
        assertEquals(0f, treble[31], 0f)
    }

    @Test fun smoothingIsRefreshRateIndependent() {
        val sixty = CenteredSpectrumMotion(2)
        val oneTwenty = CenteredSpectrumMotion(2)
        repeat(60) { sixty.advance(floatArrayOf(1f, 1f), 1f / 60) }
        repeat(120) { oneTwenty.advance(floatArrayOf(1f, 1f), 1f / 120) }
        assertEquals(sixty.levels[0], oneTwenty.levels[0], .0001f)
        repeat(60) { sixty.advance(floatArrayOf(0f, 0f), 1f / 60) }
        repeat(120) { oneTwenty.advance(floatArrayOf(0f, 0f), 1f / 120) }
        assertEquals(sixty.levels[0], oneTwenty.levels[0], .0001f)
        assertTrue(sixty.levels[0] > 0f)
    }
}
