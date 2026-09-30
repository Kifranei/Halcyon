package com.ella.music.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrollThumbGeometryTest {
    @Test
    fun largeLibraryKeepsAGrabbableThumb() {
        val geometry = scrollThumbGeometry(500f, 6f / 10_000f, 64f)
        assertEquals(64f, geometry.height, 0.001f)
        assertEquals(436f, geometry.travel, 0.001f)
    }

    @Test
    fun shortTrackNeverLetsTheThumbExtendPastItsEnd() {
        val geometry = scrollThumbGeometry(40f, 0.01f, 64f)
        assertEquals(40f, geometry.height, 0.001f)
        assertEquals(0f, geometry.travel, 0.001f)
        assertEquals(0f, geometry.progress(100f), 0.001f)
    }

    @Test
    fun drawingAndDraggingUseTheSameTravelAtEveryPosition() {
        val geometry = scrollThumbGeometry(500f, 0.25f, 64f)
        listOf(0f, 0.2f, 0.5f, 0.9f, 1f).forEach { progress ->
            assertEquals(progress, geometry.progress(geometry.offset(progress)), 0.001f)
        }
    }

    @Test
    fun grabAtCurrentPositionDoesNotJumpToTheFingerPosition() {
        val geometry = scrollThumbGeometry(500f, 0.01f, 64f)
        val grabbedTop = geometry.offset(0.4f)
        // The gesture starts from the thumb's top; the grab point inside the thumb is irrelevant.
        assertEquals(0.4f, geometry.progress(grabbedTop), 0.001f)
        assertEquals(0.5f, geometry.progress(grabbedTop + geometry.travel * 0.1f), 0.001f)
    }

    @Test
    fun draggingPastEitherEndClampsToTheTrack() {
        val geometry = scrollThumbGeometry(500f, 0.01f, 64f)
        assertEquals(0f, geometry.progress(-30f), 0.001f)
        assertEquals(1f, geometry.progress(600f), 0.001f)
    }

    @Test
    fun trackEndReachesLastItemEvenWithPaddingAndPartialRows() {
        assertEquals(0, scrollThumbTargetIndex(0f, 9994, 10000))
        assertEquals(4997, scrollThumbTargetIndex(0.5f, 9994, 10000))
        assertEquals(9999, scrollThumbTargetIndex(1f, 9994, 10000))
        assertEquals(144, scrollThumbTargetIndex(1f, 138, 145))
    }

    @Test
    fun emptyAndChangingLibrariesNeverProduceInvalidTargets() {
        assertEquals(0, scrollThumbTargetIndex(1f, 0, 0))
        assertEquals(0, scrollThumbTargetIndex(-1f, 90, 100))
        assertEquals(19, scrollThumbTargetIndex(0.9f, 90, 20))
        assertEquals(19, scrollThumbTargetIndex(2f, 90, 20))
    }
}
