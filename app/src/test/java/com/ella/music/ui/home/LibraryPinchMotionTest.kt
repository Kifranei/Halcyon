package com.ella.music.ui.home

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.ella.music.data.SettingsManager
import com.ella.music.ui.components.libraryMorphRect
import org.junit.Assert.*
import org.junit.Test

class LibraryPinchMotionTest {
    @Test fun placingTwoFingersDoesNotChooseALayout() {
        val state = LibraryPinchState(SettingsManager.LIBRARY_LAYOUT_MULTI_ROW)
        state.beginPinch(Offset(150f, 420f))
        state.updatePinch(0f, 0f)
        assertFalse(state.isTransitioning)
        assertEquals(Offset(150f, 420f), state.focalPoint)
    }

    @Test fun reversingMidGestureRetracesTheSamePair() {
        val state = LibraryPinchState(SettingsManager.LIBRARY_LAYOUT_LIST)
        state.beginPinch()
        state.updatePinch(.6f, 0f)
        state.updatePinch(.2f, 0f)
        assertEquals(SettingsManager.LIBRARY_LAYOUT_MULTI_ROW, state.targetLayout)
        assertEquals(.2f, state.transitionProgress, .0001f)
        state.updatePinch(0f, 0f)
        assertEquals(0f, state.transitionProgress, .0001f)
    }

    @Test fun nextPinchContinuesAnInterruptedTransitionWithoutJumping() {
        val state = LibraryPinchState(SettingsManager.LIBRARY_LAYOUT_LIST)
        state.beginPinch()
        state.updatePinch(.6f, 0f)
        state.beginPinch()
        state.updatePinch(-.1f, 0f)
        assertEquals(.5f, state.transitionProgress, .0001f)
        state.cancelPinch()
        assertEquals(SettingsManager.LIBRARY_LAYOUT_LIST, state.currentLayout)
        assertFalse(state.isTransitioning)
    }

    @Test fun reverseFromTerminalGridRespondsBeforeCrossingTouchDownSpan() {
        val state = LibraryPinchState(SettingsManager.LIBRARY_LAYOUT_GRID)
        state.beginPinch()
        state.updatePinch(.4f, 0f)
        assertFalse(state.isTransitioning)
        state.updatePinch(.3f, 0f)
        assertEquals(SettingsManager.LIBRARY_LAYOUT_DETAILS, state.targetLayout)
        assertEquals(.1f, state.transitionProgress, .0001f)
    }

    @Test fun coverMotionUsesBothMeasuredEndpointsAndClampsOverscroll() {
        val list = Rect(16f, 180f, 64f, 228f)
        val grid = Rect(150f, 320f, 290f, 460f)
        assertEquals(list, libraryMorphRect(list, grid, -1f))
        assertEquals(grid, libraryMorphRect(list, grid, 2f))
        assertEquals(Rect(83f, 250f, 177f, 344f), libraryMorphRect(list, grid, .5f))
    }
}
