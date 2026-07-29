package com.ella.music.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class CrossfadeTransitionMathTest {

    @Test
    fun `fade follows prebuffered incoming position`() {
        assertEquals(0f, CrossfadeTransitionMath.fadeProgress(0L, 3_000L), 0.0001f)
        assertEquals(0.5f, CrossfadeTransitionMath.fadeProgress(1_500L, 3_000L), 0.0001f)
        assertEquals(1f, CrossfadeTransitionMath.fadeProgress(3_000L, 3_000L), 0.0001f)
        assertEquals(1f, CrossfadeTransitionMath.fadeProgress(3_500L, 3_000L), 0.0001f)
    }

    @Test
    fun `equal power curve is audible early and keeps total power constant`() {
        listOf(0f, 0.2f, 0.4f, 0.5f, 0.8f, 1f).forEach { progress ->
            val gains = CrossfadeTransitionMath.gains(
                progress,
                CrossfadeTransitionMath.CURVE_EQUAL_POWER
            )
            assertEquals(
                1f,
                gains.incoming * gains.incoming + gains.outgoing * gains.outgoing,
                0.0001f
            )
        }
        val twoSecondsIntoFive = CrossfadeTransitionMath.gains(
            0.4f,
            CrossfadeTransitionMath.CURVE_EQUAL_POWER
        )
        assertTrue(twoSecondsIntoFive.incoming > 0.58f)
        assertEquals(sqrt(0.5f), CrossfadeTransitionMath.gains(
            0.5f,
            CrossfadeTransitionMath.CURVE_EQUAL_POWER
        ).incoming, 0.0001f)
    }

    @Test
    fun `six second fade starts immediately instead of staying silent until the end`() {
        val firstSecond = CrossfadeTransitionMath.gains(
            CrossfadeTransitionMath.fadeProgress(1_000L, 6_000L),
            CrossfadeTransitionMath.CURVE_EQUAL_POWER
        )
        val secondSecond = CrossfadeTransitionMath.gains(
            CrossfadeTransitionMath.fadeProgress(2_000L, 6_000L),
            CrossfadeTransitionMath.CURVE_EQUAL_POWER
        )

        assertTrue(firstSecond.incoming > 0.25f)
        assertTrue(secondSecond.incoming >= 0.5f)
        assertTrue(secondSecond.incoming > firstSecond.incoming)
    }

    @Test
    fun `curve modes keep their documented shapes`() {
        val linear = CrossfadeTransitionMath.gains(0.25f, CrossfadeTransitionMath.CURVE_LINEAR)
        assertEquals(0.25f, linear.incoming, 0.0001f)
        assertEquals(0.75f, linear.outgoing, 0.0001f)

        val smooth = CrossfadeTransitionMath.gains(0.25f, CrossfadeTransitionMath.CURVE_SMOOTH)
        assertEquals(0.15625f, smooth.incoming, 0.0001f)
        assertEquals(0.84375f, smooth.outgoing, 0.0001f)

        val flat = CrossfadeTransitionMath.gains(0.25f, CrossfadeTransitionMath.CURVE_FLAT)
        assertEquals(1f, flat.incoming, 0.0001f)
        assertEquals(1f, flat.outgoing, 0.0001f)
    }

    @Test
    fun `handoff resync ignores decoder jitter but corrects audible drift`() {
        assertFalse(CrossfadeTransitionMath.shouldResyncHandoff(120L))
        assertFalse(CrossfadeTransitionMath.shouldResyncHandoff(-120L))
        assertTrue(CrossfadeTransitionMath.shouldResyncHandoff(121L))
        assertTrue(CrossfadeTransitionMath.shouldResyncHandoff(-121L))
    }
}
