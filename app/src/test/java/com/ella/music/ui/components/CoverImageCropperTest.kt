package com.ella.music.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoverImageCropperTest {
    @Test
    fun initialSquareCropFillsTheShorterImageSide() {
        val bounds = Rect(0f, 0f, 200f, 100f)
        val crop = initialSquareCropRect(bounds, aspectRatio = 1f)
        assertEquals(100f, crop.width, 0.01f)
        assertEquals(100f, crop.height, 0.01f)
        assertEquals(50f, crop.left, 0.01f)
        assertEquals(0f, crop.top, 0.01f)
    }

    @Test
    fun centerDragStaysInsideImageBounds() {
        val bounds = Rect(0f, 0f, 200f, 200f)
        val current = Rect(20f, 20f, 80f, 80f)
        val moved = moveCropRect(
            handle = CropHandle.CENTER,
            oldRect = current,
            dragAmount = Offset(200f, -40f),
            bounds = bounds,
            minSize = 40f,
            aspectRatio = 1f
        )
        assertEquals(140f, moved.left, 0.01f)
        assertEquals(0f, moved.top, 0.01f)
        assertEquals(200f, moved.right, 0.01f)
        assertEquals(60f, moved.bottom, 0.01f)
    }

    @Test
    fun cornerHitTestPrefersTheNearestHandle() {
        val rect = Rect(10f, 10f, 90f, 90f)
        assertEquals(CropHandle.TOP_LEFT, hitCropHandle(Offset(10f, 10f), rect, 12f))
        assertEquals(CropHandle.CENTER, hitCropHandle(Offset(50f, 50f), rect, 12f))
        assertEquals(CropHandle.NONE, hitCropHandle(Offset(200f, 200f), rect, 12f))
    }

    @Test
    fun squareAspectKeepsWidthAndHeightEqualWhenResizing() {
        val bounds = Rect(0f, 0f, 300f, 300f)
        val current = Rect(50f, 50f, 150f, 150f)
        val resized = moveCropRect(
            handle = CropHandle.BOTTOM_RIGHT,
            oldRect = current,
            dragAmount = Offset(40f, 10f),
            bounds = bounds,
            minSize = 40f,
            aspectRatio = 1f
        )
        assertEquals(resized.width, resized.height, 0.01f)
        assertTrue(resized.right <= bounds.right + 0.01f)
        assertTrue(resized.bottom <= bounds.bottom + 0.01f)
    }
}
