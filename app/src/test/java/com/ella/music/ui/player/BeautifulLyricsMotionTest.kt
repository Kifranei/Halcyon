package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class BeautifulLyricsMotionTest {
    @Test
    fun blobPathsCloseWithoutAVisibleCycleJump() {
        val start = beautifulLyricsBlobCenters(0f)
        val end = beautifulLyricsBlobCenters(1f)

        start.zip(end).forEach { (first, last) ->
            assertEquals(first.x, last.x, 0.00001f)
            assertEquals(first.y, last.y, 0.00001f)
        }
    }
}
