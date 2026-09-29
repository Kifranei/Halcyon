package com.ella.music.data

import org.junit.Assert.*
import org.junit.Test

class WaveformResampleTest {
    @Test fun downsamplingPreservesShortTransientPeaks() {
        assertArrayEquals(floatArrayOf(1f, .8f), resampleWaveform(floatArrayOf(.1f, 1f, .8f, .1f), 2), .0001f)
    }
    @Test fun upsamplingPreservesEndpointsAndSilence() {
        assertArrayEquals(floatArrayOf(0f, .25f, .5f, .75f, 1f), resampleWaveform(floatArrayOf(0f, 1f), 5), .0001f)
        assertTrue(resampleWaveform(FloatArray(8), 32).all { it == 0f })
    }
    @Test fun fullSizeReusesTheSharedEnvelope() {
        val master = FloatArray(3600)
        assertSame(master, resampleWaveform(master, 3600))
    }
}
