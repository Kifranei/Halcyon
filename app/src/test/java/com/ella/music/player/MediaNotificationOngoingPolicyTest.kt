package com.ella.music.player

import androidx.media3.common.Player
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaNotificationOngoingPolicyTest {

    @Test
    fun playingIsOngoing() {
        assertTrue(
            isMediaNotificationOngoing(
                playWhenReady = true,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun bufferingWhilePlayWhenReadyIsOngoing() {
        assertTrue(
            isMediaNotificationOngoing(
                playWhenReady = true,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun pausedIsNotOngoing() {
        assertFalse(
            isMediaNotificationOngoing(
                playWhenReady = false,
                playbackState = Player.STATE_READY
            )
        )
    }

    @Test
    fun pausedWhileBufferingIsNotOngoing() {
        assertFalse(
            isMediaNotificationOngoing(
                playWhenReady = false,
                playbackState = Player.STATE_BUFFERING
            )
        )
    }

    @Test
    fun endedIsNotOngoing() {
        assertFalse(
            isMediaNotificationOngoing(
                playWhenReady = true,
                playbackState = Player.STATE_ENDED
            )
        )
    }

    @Test
    fun idleIsNotOngoing() {
        assertFalse(
            isMediaNotificationOngoing(
                playWhenReady = true,
                playbackState = Player.STATE_IDLE
            )
        )
    }
}
