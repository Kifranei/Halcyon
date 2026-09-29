package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLyricAlignmentTest {
    @Test
    fun lyricPageUsesTheUpperAnchor() {
        assertEquals(0.22f, resolveLyricPageFocusOffsetRatio(0.22f), 0.0001f)
    }

    @Test
    fun upperAnchorIsClampedToTheViewport() {
        assertEquals(0f, resolveLyricPageFocusOffsetRatio(-1f), 0.0001f)
        assertEquals(1f, resolveLyricPageFocusOffsetRatio(2f), 0.0001f)
    }
}
