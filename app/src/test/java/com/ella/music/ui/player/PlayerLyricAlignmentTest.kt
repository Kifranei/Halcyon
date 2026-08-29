package com.ella.music.ui.player

import com.ella.music.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerLyricAlignmentTest {
    @Test
    fun retiredCenterValueFallsBackToTheUpperAnchor() {
        assertEquals(
            0.22f,
            resolveLyricPageFocusOffsetRatio(
                SettingsManager.LYRIC_PAGE_VERTICAL_ALIGN_CENTER,
                0.22f
            ),
            0.0001f
        )
    }

    @Test
    fun upperAnchorIsClampedToTheViewport() {
        assertEquals(0f, resolveLyricPageFocusOffsetRatio(0, -1f), 0.0001f)
        assertEquals(1f, resolveLyricPageFocusOffsetRatio(0, 2f), 0.0001f)
    }
}
