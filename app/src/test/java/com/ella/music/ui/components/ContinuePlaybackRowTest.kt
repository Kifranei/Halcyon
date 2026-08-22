package com.ella.music.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuePlaybackRowTest {
    @Test
    fun hidesOnlyWhenThisCategoryIsThePlaybackSource() {
        assertTrue(
            isContinuePlaybackHiddenForCurrentSource(
                playbackSourceKey = "playlist:favorites",
                categoryKey = "playlist:favorites"
            )
        )
        assertFalse(
            isContinuePlaybackHiddenForCurrentSource(
                playbackSourceKey = "album:11",
                categoryKey = "playlist:favorites"
            )
        )
        assertFalse(
            isContinuePlaybackHiddenForCurrentSource(
                playbackSourceKey = null,
                categoryKey = "playlist:favorites"
            )
        )
    }
}
