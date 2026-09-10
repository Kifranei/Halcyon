package com.ella.music.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRatingFilterTest {

    @Test
    fun matchingUsesFavoriteStatePerRatingBucket() {
        val selection = HomeRatingFilterSelection().toggleFavoriteFilter()

        assertTrue(selection.matches(2, isFavorite = true))
        assertFalse(selection.matches(2, isFavorite = false))
        assertTrue(selection.matches(3, isFavorite = true))
        assertFalse(selection.matches(3, isFavorite = false))
        assertTrue(selection.matches(0, isFavorite = true))
        assertFalse(selection.matches(0, isFavorite = false))
    }

    @Test
    fun allStarsMasterUnchecksEveryStarThenChecksThemAgain() {
        val start = HomeRatingFilterSelection()
        assertTrue(start.areAllStarsIncluded())
        assertTrue(start.isUnratedIncluded())

        val none = start.toggleAllStars()
        assertFalse(none.areAllStarsIncluded())
        (1..5).forEach { rating ->
            assertFalse(none.isRatingIncluded(rating))
        }
        assertTrue(none.isUnratedIncluded())

        val all = none.toggleAllStars()
        assertTrue(all.areAllStarsIncluded())
        (1..5).forEach { rating ->
            assertTrue(all.isRatingIncluded(rating))
        }
        assertTrue(all.isUnratedIncluded())
    }

    @Test
    fun unratedMasterTogglesIndependentlyOfStars() {
        val excluded = HomeRatingFilterSelection().toggleUnrated()
        assertFalse(excluded.isUnratedIncluded())
        assertTrue(excluded.areAllStarsIncluded())
        assertTrue(excluded.toggleUnrated().isUnratedIncluded())
    }

    @Test
    fun filterMenuPutsHeartAboveRatings() {
        val groups = buildRatingFilterGroups(
            selection = HomeRatingFilterSelection(),
            showFavorite = true,
            showRating = true,
            favoriteLabel = "heart",
            allRatingsLabel = "all",
            allStarsLabel = "stars",
            starLabel = { "$it" }
        )
        assertEquals(2, groups.size)
        assertEquals(listOf("heart"), groups[0].rows.map { it.text })
        assertEquals(listOf("all", "stars", "1", "2", "3", "4", "5"), groups[1].rows.map { it.text })
        assertFalse(groups[0].rows.single().selected)
        assertTrue(groups[1].rows.all { it.selected })
    }

    @Test
    fun filterMenuAllStarsRowMirrorsIndividualStars() {
        val none = HomeRatingFilterSelection().toggleAllStars()
        val groups = buildRatingFilterGroups(
            selection = none,
            showFavorite = false,
            showRating = true,
            favoriteLabel = "heart",
            allRatingsLabel = "all",
            allStarsLabel = "stars",
            starLabel = { "$it" }
        )
        val rows = groups.single().rows
        assertTrue(rows.first { it.text == "all" }.selected)
        assertFalse(rows.first { it.text == "stars" }.selected)
        (1..5).forEach { rating ->
            assertFalse(rows.first { it.text == "$rating" }.selected)
        }
        val restored = rows.first { it.text == "stars" }.apply(none)
        assertTrue(restored.areAllStarsIncluded())
    }
}
