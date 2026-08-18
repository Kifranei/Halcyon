package com.ella.music.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoContentFrameTest {
    @Test
    fun fitLetterboxesWideVideoOnTallerScreen() {
        val (width, height) = videoContentFrameDimensions(
            videoAspectRatio = 16f / 9f,
            containerWidth = 20f,
            containerHeight = 9f,
            cover = false
        )

        assertEquals(16f, width, 0.001f)
        assertEquals(9f, height, 0.001f)
        assertTrue(videoContentShouldFillHeight(16f / 9f, 20f / 9f, cover = false))
    }

    @Test
    fun fitLetterboxesTallVideoOnWiderScreen() {
        val (width, height) = videoContentFrameDimensions(
            videoAspectRatio = 9f / 16f,
            containerWidth = 16f,
            containerHeight = 9f,
            cover = false
        )

        assertEquals(9f * 9f / 16f, width, 0.001f)
        assertEquals(9f, height, 0.001f)
        assertTrue(videoContentShouldFillHeight(9f / 16f, 16f / 9f, cover = false))
    }

    @Test
    fun zoomCropsWideVideoOnTallerScreen() {
        val (width, height) = videoContentFrameDimensions(
            videoAspectRatio = 16f / 9f,
            containerWidth = 20f,
            containerHeight = 9f,
            cover = true
        )

        assertEquals(20f, width, 0.001f)
        assertEquals(20f * 9f / 16f, height, 0.001f)
        assertFalse(videoContentShouldFillHeight(16f / 9f, 20f / 9f, cover = true))
    }

    @Test
    fun zoomCropsTallVideoOnWiderScreen() {
        val (width, height) = videoContentFrameDimensions(
            videoAspectRatio = 9f / 16f,
            containerWidth = 16f,
            containerHeight = 9f,
            cover = true
        )

        assertEquals(16f, width, 0.001f)
        assertEquals(16f * 16f / 9f, height, 0.001f)
        assertFalse(videoContentShouldFillHeight(9f / 16f, 16f / 9f, cover = true))
    }
}
