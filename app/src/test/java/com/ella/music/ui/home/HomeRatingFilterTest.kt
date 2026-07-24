package com.ella.music.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeRatingFilterTest {

    @Test
    fun allFiveStarsRemainAnExplicitRatedFilter() {
        assertEquals((1..5).toSet(), (1..5).toSet().normalizedRatingFilter())
    }

    @Test
    fun removingTheLastStarClearsTheStarFilter() {
        assertEquals(emptySet<Int>(), setOf(1).toggleRatingForTest(1).normalizedRatingFilter())
    }

    private fun Set<Int>.toggleRatingForTest(rating: Int): Set<Int> =
        if (rating in this) this - rating else this + rating
}
