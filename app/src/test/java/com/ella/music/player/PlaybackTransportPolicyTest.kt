package com.ella.music.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTransportPolicyTest {
    @Test
    fun pausedControllerProjectsPlayImmediately() {
        val projection = projectTransportState(
            actualIsPlaying = false,
            actualPlayWhenReady = false,
            pendingTarget = true
        )

        assertTrue(projection.isPlaying)
        assertTrue(projection.playWhenReady)
        assertFalse(projection.acknowledged)
    }

    @Test
    fun playProjectionStaysStableUntilAudioCallbackArrives() {
        val projection = projectTransportState(
            actualIsPlaying = false,
            actualPlayWhenReady = true,
            pendingTarget = true
        )

        assertTrue(projection.isPlaying)
        assertTrue(projection.playWhenReady)
        assertFalse(projection.acknowledged)
    }

    @Test
    fun actualPlayingCallbackAcknowledgesPlayProjection() {
        val projection = projectTransportState(
            actualIsPlaying = true,
            actualPlayWhenReady = true,
            pendingTarget = true
        )

        assertTrue(projection.isPlaying)
        assertTrue(projection.playWhenReady)
        assertTrue(projection.acknowledged)
    }

    @Test
    fun pauseProjectionAlwaysShowsPlayGlyph() {
        val projection = projectTransportState(
            actualIsPlaying = true,
            actualPlayWhenReady = true,
            pendingTarget = false
        )

        assertFalse(projection.isPlaying)
        assertFalse(projection.playWhenReady)
        assertFalse(projection.acknowledged)
    }

    @Test
    fun pausedCallbackAcknowledgesPauseProjection() {
        val projection = projectTransportState(
            actualIsPlaying = false,
            actualPlayWhenReady = false,
            pendingTarget = false
        )

        assertFalse(projection.isPlaying)
        assertFalse(projection.playWhenReady)
        assertTrue(projection.acknowledged)
    }
}
