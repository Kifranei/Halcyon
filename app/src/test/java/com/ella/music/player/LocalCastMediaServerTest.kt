package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocalCastMediaServerTest {
    @Test
    fun `parses bounded byte range`() {
        assertEquals(100L..199L, parseHttpByteRange("bytes=100-199", 1_000L))
    }

    @Test
    fun `parses open ended and suffix ranges`() {
        assertEquals(900L..999L, parseHttpByteRange("bytes=900-", 1_000L))
        assertEquals(900L..999L, parseHttpByteRange("bytes=-100", 1_000L))
    }

    @Test
    fun `rejects invalid byte range`() {
        assertNull(parseHttpByteRange("bytes=1000-1200", 1_000L))
    }
}
