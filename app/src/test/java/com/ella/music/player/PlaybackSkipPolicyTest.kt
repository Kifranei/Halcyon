package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackSkipPolicyTest {
    @Test
    fun nextSongUsesFollowingIndex() {
        assertEquals(2, adjacentPlaylistIndex(currentIndex = 1, offset = 1, queueSize = 4, wrap = true))
    }

    @Test
    fun previousSongUsesPrecedingIndex() {
        assertEquals(0, adjacentPlaylistIndex(currentIndex = 1, offset = -1, queueSize = 4, wrap = true))
    }

    @Test
    fun wrapAroundAtEndsWhenRepeatAll() {
        assertEquals(0, adjacentPlaylistIndex(currentIndex = 3, offset = 1, queueSize = 4, wrap = true))
        assertEquals(3, adjacentPlaylistIndex(currentIndex = 0, offset = -1, queueSize = 4, wrap = true))
    }

    @Test
    fun noWrapAtEndWhenRepeatOff() {
        assertNull(adjacentPlaylistIndex(currentIndex = 3, offset = 1, queueSize = 4, wrap = false))
        assertNull(adjacentPlaylistIndex(currentIndex = 0, offset = -1, queueSize = 4, wrap = false))
    }

    @Test
    fun singleItemWrapRestartsTheSameIndex() {
        assertEquals(0, adjacentPlaylistIndex(currentIndex = 0, offset = 1, queueSize = 1, wrap = true))
    }

    @Test
    fun emptyOrInvalidQueueHasNoTarget() {
        assertNull(adjacentPlaylistIndex(currentIndex = 0, offset = 1, queueSize = 0, wrap = true))
        assertNull(adjacentPlaylistIndex(currentIndex = -1, offset = 1, queueSize = 3, wrap = true))
        assertNull(adjacentPlaylistIndex(currentIndex = 0, offset = 0, queueSize = 3, wrap = true))
    }
}
