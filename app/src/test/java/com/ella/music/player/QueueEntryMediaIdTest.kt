package com.ella.music.player

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueEntryMediaIdTest {
    @Test
    fun duplicateSongsReceiveDistinctQueueEntryIds() {
        val first = queueEntryMediaId(songId = 8L, sequence = 41L)
        val second = queueEntryMediaId(songId = 8L, sequence = 42L)

        assertNotEquals(first, second)
        assertTrue(first.startsWith("queue:8:"))
    }
}
