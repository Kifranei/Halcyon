package com.ella.music.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.assertEquals
import org.junit.Test

class CoverPreviewGestureMathTest {

    @Test
    fun zoomKeepsTheTappedContentUnderTheSameScreenPoint() {
        val offset = coverPreviewZoomOffsetForFocalPoint(
            currentOffset = Offset.Zero,
            currentScale = 1f,
            targetScale = 4f,
            focalPoint = Offset(250f, 500f),
            viewportSize = IntSize(1_000, 1_000)
        )

        // The tap is left of center. A zoom-in must translate the image right, not mirror the
        // focal point to the other side of the viewport.
        assertEquals(750f, offset.x, 0.001f)
        assertEquals(0f, offset.y, 0.001f)
    }

    @Test
    fun panBoundsKeepAFittedLandscapeCoverOnScreenAfterZooming() {
        val bounds = coverPreviewPanBounds(
            resolution = CoverResolution(width = 2_000, height = 1_000),
            viewportSize = IntSize(1_000, 1_000),
            scale = 5f
        )

        // The cover is fitted as 1000 x 500. At 5x, only 750 px may be moved vertically
        // before the lower edge would expose the black preview background.
        assertEquals(2_000f, bounds.x, 0.001f)
        assertEquals(750f, bounds.y, 0.001f)
    }
}
