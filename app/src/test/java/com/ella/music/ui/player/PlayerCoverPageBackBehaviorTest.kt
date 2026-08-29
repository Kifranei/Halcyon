package com.ella.music.ui.player

import com.ella.music.data.SettingsManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerCoverPageBackBehaviorTest {
    @Test
    fun largeScreenDefaultAppleMusicLyricsDoNotConsumeBack() {
        assertFalse(
            shouldInterceptAppleMusicLyricsBack(
                showLyrics = true,
                playerPageStyle = SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC,
                preserveLyricsOnBack = true
            )
        )
    }

    @Test
    fun phoneAppleMusicLyricsStillConsumeBack() {
        assertTrue(
            shouldInterceptAppleMusicLyricsBack(
                showLyrics = true,
                playerPageStyle = SettingsManager.PLAYER_PAGE_STYLE_APPLE_MUSIC,
                preserveLyricsOnBack = false
            )
        )
    }

    @Test
    fun nonAppleMusicPagesNeverUseAppleMusicLyricsBackHandler() {
        assertFalse(
            shouldInterceptAppleMusicLyricsBack(
                showLyrics = true,
                playerPageStyle = SettingsManager.PLAYER_PAGE_STYLE_HALCYON,
                preserveLyricsOnBack = false
            )
        )
    }
}
