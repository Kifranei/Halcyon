package com.ella.music.ui.home

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ella.music.R

/** One rating bucket can be shown normally, as favorites, as non-favorites, or hidden. */
internal enum class RatingFilterBucketMode {
    All,
    Favorites,
    NonFavorites,
    Excluded
}

/**
 * Rating and favorite filters are kept per bucket so the heart filter can be combined with
 * individual stars.
 */
internal data class HomeRatingFilterSelection(
    val unrated: RatingFilterBucketMode = RatingFilterBucketMode.All,
    val rated: Map<Int, RatingFilterBucketMode> = (1..5).associateWith {
        RatingFilterBucketMode.All
    }
) {
    fun modeFor(rating: Int): RatingFilterBucketMode = if (rating in 1..5) {
        rated[rating] ?: RatingFilterBucketMode.Excluded
    } else {
        unrated
    }

    fun isUnfiltered(): Boolean =
        unrated == RatingFilterBucketMode.All && rated.values.all { it == RatingFilterBucketMode.All }

    fun hasRatingConstraint(): Boolean =
        unrated != RatingFilterBucketMode.All || rated.values.any { it != RatingFilterBucketMode.All }

    /** True when the visible filter is currently showing favorite songs only. */
    fun hasFavoriteFilter(): Boolean =
        sequenceOf(unrated).plus(rated.values.asSequence()).any {
            it == RatingFilterBucketMode.Favorites
        }

    /** Keeps the toolbar heart active while a bucket is cycling through its non-favorite state. */
    fun hasFavoriteFilterMemory(): Boolean =
        sequenceOf(unrated).plus(rated.values.asSequence()).any {
            it == RatingFilterBucketMode.Favorites || it == RatingFilterBucketMode.NonFavorites
        }

    fun requiresFavoriteKeys(): Boolean =
        sequenceOf(unrated).plus(rated.values.asSequence()).any {
            it == RatingFilterBucketMode.Favorites || it == RatingFilterBucketMode.NonFavorites
        }

    fun isAllStarsScope(): Boolean =
        unrated != RatingFilterBucketMode.Excluded && rated.values.all { it == unrated }

    fun isRatedScope(): Boolean =
        unrated == RatingFilterBucketMode.Excluded &&
            rated.values.distinct().size == 1 &&
            rated.values.firstOrNull() != RatingFilterBucketMode.Excluded

    fun isUnratedOnlyScope(): Boolean =
        unrated != RatingFilterBucketMode.Excluded &&
            rated.values.all { it == RatingFilterBucketMode.Excluded }

    fun matches(rating: Int, isFavorite: Boolean): Boolean = when (modeFor(rating)) {
        RatingFilterBucketMode.All -> true
        RatingFilterBucketMode.Favorites -> isFavorite
        RatingFilterBucketMode.NonFavorites -> !isFavorite
        RatingFilterBucketMode.Excluded -> false
    }

    fun isRatingIncluded(rating: Int): Boolean = modeFor(rating) != RatingFilterBucketMode.Excluded

    fun isUnratedIncluded(): Boolean = unrated != RatingFilterBucketMode.Excluded

    fun areAllStarsIncluded(): Boolean = (1..5).all { isRatingIncluded(it) }

    fun hasAnyFilter(): Boolean = hasRatingConstraint() || hasFavoriteFilterMemory()

    fun toggleUnrated(): HomeRatingFilterSelection {
        val includedMode = includedMode()
        return copy(unrated = if (unrated == RatingFilterBucketMode.Excluded) includedMode else RatingFilterBucketMode.Excluded)
    }

    fun toggleAllStars(): HomeRatingFilterSelection {
        val includedMode = includedMode()
        val next = if (areAllStarsIncluded()) RatingFilterBucketMode.Excluded else includedMode
        return copy(rated = (1..5).associateWith { next })
    }

    fun toggleStar(rating: Int): HomeRatingFilterSelection {
        val safeRating = rating.coerceIn(1, 5)
        val includedMode = includedMode()
        val next = if (modeFor(safeRating) == RatingFilterBucketMode.Excluded) {
            includedMode
        } else {
            RatingFilterBucketMode.Excluded
        }
        return copy(rated = rated + (safeRating to next))
    }

    private fun includedMode(): RatingFilterBucketMode =
        if (hasFavoriteFilter()) RatingFilterBucketMode.Favorites else RatingFilterBucketMode.All

    /** Toggle the heart for the currently included rating scope. */
    fun toggleFavoriteFilter(): HomeRatingFilterSelection {
        val included = (0..5).filter { modeFor(it) != RatingFilterBucketMode.Excluded }
        if (included.isEmpty()) {
            return copy(
                unrated = RatingFilterBucketMode.Favorites,
                rated = (1..5).associateWith { RatingFilterBucketMode.Favorites }
            )
        }
        val allFavorites = included.all { modeFor(it) == RatingFilterBucketMode.Favorites }
        val next = if (allFavorites) RatingFilterBucketMode.All else RatingFilterBucketMode.Favorites
        return withModes(included, next)
    }

    internal fun summaryLabel(context: Context): String? {
        if (isUnfiltered()) {
            return null
        }
        val selectedStars = (1..5).filter { modeFor(it) != RatingFilterBucketMode.Excluded }
        if (selectedStars.size == 5) {
            return context.getString(R.string.rating_filter_rated)
        }
        if (isAllStarsScope() && globalScopeMode() == RatingFilterBucketMode.All) {
            return null
        }
        if (isRatedScope() && globalScopeMode() == RatingFilterBucketMode.All) {
            return context.getString(R.string.rating_filter_rated)
        }
        if (isUnratedOnlyScope() && globalScopeMode() == RatingFilterBucketMode.All) {
            return context.getString(R.string.rating_filter_unrated)
        }
        return selectedStars
            .joinToString(separator = " · ") { context.getString(R.string.rating_filter_star, it) }
            .ifBlank { null }
    }

    private fun globalScopeMode(): RatingFilterBucketMode? = when {
        isAllStarsScope() -> unrated
        isRatedScope() -> rated.values.firstOrNull()
        isUnratedOnlyScope() -> unrated
        else -> null
    }

    private fun withModes(
        buckets: List<Int>,
        mode: RatingFilterBucketMode
    ): HomeRatingFilterSelection {
        var nextUnrated = unrated
        val nextRated = rated.toMutableMap()
        buckets.forEach { bucket ->
            if (bucket == 0) nextUnrated = mode else nextRated[bucket] = mode
        }
        return copy(unrated = nextUnrated, rated = nextRated)
    }
}

internal object HomeRatingFilterUiState {
    /** Keeps the complete rating/favorite choice while moving between library tabs. */
    var selection by mutableStateOf(HomeRatingFilterSelection())
}
