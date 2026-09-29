package com.ella.music.ui.player

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerMorphTest {
    @Test fun endpointsAndOverscrollRemainBounded() {
        val mini = Rect(220f, 700f, 980f, 764f)
        val screen = Rect(0f, 0f, 1000f, 800f)
        assertEquals(mini, morphRect(mini, screen, -0.2f))
        assertEquals(screen, morphRect(mini, screen, 1.2f))
    }
    @Test fun landscapeSidebarOffsetAndBottomInsetInterpolateTogether() {
        val mini = Rect(220f, 700f, 980f, 764f)
        val screen = Rect(0f, 0f, 1000f, 800f)
        assertEquals(Rect(110f, 350f, 990f, 782f), morphRect(mini, screen, .5f))
    }
    @Test fun reversingGestureRetracesTheSameBounds() {
        val mini = Rect(16f, 900f, 384f, 964f)
        val screen = Rect(0f, 0f, 400f, 1000f)
        val opening = listOf(0f, .25f, .5f, .75f, 1f).map { morphRect(mini, screen, it) }
        val closing = listOf(1f, .75f, .5f, .25f, 0f).map { morphRect(mini, screen, it) }
        assertEquals(opening, closing.reversed())
    }
}
