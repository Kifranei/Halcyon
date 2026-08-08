package com.ella.music.ui.home

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeRatingFilterTest {

    @Test
    fun globalScopeCyclesAllStarsToRatedThenUnrated() {
        val allStars = HomeRatingFilterSelection()
        val allRated = allStars.cycleGlobalScope()
        val unrated = allRated.cycleGlobalScope()
        val backToAllStars = unrated.cycleGlobalScope()

        assertTrue(allStars.isAllStarsScope())
        assertTrue(allRated.isRatedScope())
        assertTrue(unrated.isUnratedOnlyScope())
        assertTrue(backToAllStars.isAllStarsScope())
    }

    @Test
    fun selectingOneStarFromGlobalScopeNarrowsToThatStar() {
        val selected = HomeRatingFilterSelection().cycleRating(3)

        assertEquals(RatingFilterBucketMode.All, selected.modeFor(3))
        assertEquals(RatingFilterBucketMode.Excluded, selected.modeFor(2))
        assertEquals(RatingFilterBucketMode.Excluded, selected.modeFor(4))
        assertEquals(RatingFilterBucketMode.Excluded, selected.modeFor(0))
        assertEquals(RatingFilterPillState.Excluded, selected.globalPillState())
        assertEquals(
            RatingFilterPillState.All,
            selected.cycleGlobalScope().globalPillState()
        )
    }

    @Test
    fun favoriteStarCyclesToAllThenNonFavoritesThenExcluded() {
        val favoriteOnly = HomeRatingFilterSelection().toggleFavoriteFilter()
        val allSongs = favoriteOnly.cycleRating(3)
        val nonFavorites = allSongs.cycleRating(3)
        val excluded = nonFavorites.cycleRating(3)

        assertEquals(RatingFilterBucketMode.Favorites, favoriteOnly.modeFor(3))
        assertEquals(RatingFilterBucketMode.All, allSongs.modeFor(3))
        assertEquals(RatingFilterBucketMode.NonFavorites, nonFavorites.modeFor(3))
        assertEquals(RatingFilterBucketMode.Excluded, excluded.modeFor(3))
        assertTrue(excluded.hasFavoriteFilter())
    }

    @Test
    fun matchingUsesFavoriteStatePerRatingBucket() {
        val selection = HomeRatingFilterSelection().toggleFavoriteFilter().cycleRating(3)

        assertTrue(selection.matches(2, isFavorite = true))
        assertFalse(selection.matches(2, isFavorite = false))
        assertTrue(selection.matches(3, isFavorite = true))
        assertTrue(selection.matches(3, isFavorite = false))
        assertTrue(selection.matches(0, isFavorite = true))
        assertFalse(selection.matches(0, isFavorite = false))
    }

    @Test
    fun heartCycleKeepsRatedGlobalBlueUntilAStarIsExcluded() {
        val allRatedFavorite = HomeRatingFilterSelection()
            .cycleGlobalScope()
            .toggleFavoriteFilter()
        val canceledForThree = allRatedFavorite.cycleRating(3)
        val nonFavoritesOnlyForThree = canceledForThree.cycleRating(3)
        val excludedThree = nonFavoritesOnlyForThree.cycleRating(3)

        assertEquals(RatingFilterPillState.Favorite, allRatedFavorite.globalPillState())
        assertEquals(RatingFilterBucketMode.All, canceledForThree.modeFor(3))
        assertEquals(RatingFilterPillState.All, canceledForThree.globalPillState())
        assertTrue(canceledForThree.hasFavoriteFilterMemory())
        assertEquals(RatingFilterBucketMode.NonFavorites, nonFavoritesOnlyForThree.modeFor(3))
        assertEquals(RatingFilterPillState.All, nonFavoritesOnlyForThree.globalPillState())
        assertTrue(nonFavoritesOnlyForThree.hasFavoriteFilterMemory())
        assertEquals(RatingFilterBucketMode.Excluded, excludedThree.modeFor(3))
        assertEquals(RatingFilterPillState.Excluded, excludedThree.globalPillState())
    }

    @Test
    fun unratedHeartUsesDimmedFavoriteStateAndKeepsHeartMemory() {
        val unratedFavorite = HomeRatingFilterSelection()
            .cycleGlobalScope()
            .cycleGlobalScope()
            .toggleFavoriteFilter()

        assertEquals(RatingFilterPillState.FavoriteDim, unratedFavorite.globalPillState())
        assertTrue(unratedFavorite.hasFavoriteFilterMemory())
        assertEquals(
            RatingFilterPillState.NonFavorite,
            unratedFavorite.cycleGlobalScope().globalPillState()
        )
    }
}
