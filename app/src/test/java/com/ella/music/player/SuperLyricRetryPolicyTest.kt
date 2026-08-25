package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SuperLyricRetryPolicyTest {
    @Test
    fun `retry delay grows exponentially and caps at five minutes`() {
        assertEquals(30_000L, superLyricRetryDelayMs(0))
        assertEquals(30_000L, superLyricRetryDelayMs(1))
        assertEquals(60_000L, superLyricRetryDelayMs(2))
        assertEquals(120_000L, superLyricRetryDelayMs(3))
        assertEquals(240_000L, superLyricRetryDelayMs(4))
        assertEquals(300_000L, superLyricRetryDelayMs(5))
        assertEquals(300_000L, superLyricRetryDelayMs(100))
    }
}
