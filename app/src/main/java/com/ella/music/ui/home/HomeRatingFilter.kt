package com.ella.music.ui.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** One rating bucket can be shown normally, as favorites, as non-favorites, or hidden. */
internal enum class RatingFilterBucketMode {
    All,
    Favorites,
    NonFavorites,
    Excluded
}

internal enum class RatingFilterPillState {
    Normal,
    All,
    Favorite,
    FavoriteDim,
    NonFavorite,
    Unrated,
    Excluded
}

/**
 * Rating and favorite filters are kept per bucket so the heart filter can be combined with
 * individual stars. This is what allows a favorite-only star to become all songs, then
 * non-favorites-only, without losing the other selected stars.
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

    /** Cycle the global scope: all stars -> all rated -> unrated -> all stars. */
    fun cycleGlobalScope(): HomeRatingFilterSelection {
        if (isUnratedOnlyScope()) {
            return when (unrated) {
                // In the unrated scope the pink global pill is the heart filter itself. The
                // first click keeps the unrated scope but switches it to non-favorites, as
                // requested by #424; the next click clears that exception.
                RatingFilterBucketMode.Favorites -> copy(unrated = RatingFilterBucketMode.NonFavorites)
                RatingFilterBucketMode.NonFavorites -> copy(unrated = RatingFilterBucketMode.All)
                RatingFilterBucketMode.All -> withAllStarsScope(RatingFilterBucketMode.All)
                RatingFilterBucketMode.Excluded -> withAllStarsScope(RatingFilterBucketMode.All)
            }
        }
        val commonMode = globalScopeMode() ?: RatingFilterBucketMode.All
        return when {
            isAllStarsScope() -> withRatedScope(commonMode)
            isRatedScope() -> withUnratedScope(commonMode)
            else -> withRatedScope(RatingFilterBucketMode.All)
        }
    }

    /**
     * Cycle a star. Selecting a star from a global scope narrows to that star first; once a
     * favorite filter is active, the next states are all -> non-favorites -> excluded.
     */
    fun cycleRating(rating: Int): HomeRatingFilterSelection {
        val safeRating = rating.coerceIn(1, 5)
        val current = modeFor(safeRating)
        if (current == RatingFilterBucketMode.All && (isAllStarsScope() || isRatedScope())) {
            return copy(
                unrated = RatingFilterBucketMode.Excluded,
                rated = (1..5).associateWith {
                    if (it == safeRating) RatingFilterBucketMode.All
                    else RatingFilterBucketMode.Excluded
                }
            )
        }
        val next = when (current) {
            RatingFilterBucketMode.Favorites -> RatingFilterBucketMode.All
            RatingFilterBucketMode.All -> RatingFilterBucketMode.NonFavorites
            RatingFilterBucketMode.NonFavorites -> RatingFilterBucketMode.Excluded
            RatingFilterBucketMode.Excluded -> {
                if (hasFavoriteFilterMemory()) RatingFilterBucketMode.Favorites
                else RatingFilterBucketMode.All
            }
        }
        return copy(rated = rated + (safeRating to next))
    }

    internal fun globalPillState(): RatingFilterPillState {
        if (isUnratedOnlyScope()) {
            return when (unrated) {
                RatingFilterBucketMode.All -> RatingFilterPillState.Unrated
                RatingFilterBucketMode.Favorites -> RatingFilterPillState.FavoriteDim
                RatingFilterBucketMode.NonFavorites -> RatingFilterPillState.NonFavorite
                RatingFilterBucketMode.Excluded -> RatingFilterPillState.Excluded
            }
        }
        return when (globalScopeMode()) {
            RatingFilterBucketMode.All -> RatingFilterPillState.All
            RatingFilterBucketMode.Favorites -> RatingFilterPillState.Favorite
            RatingFilterBucketMode.NonFavorites -> RatingFilterPillState.NonFavorite
            RatingFilterBucketMode.Excluded -> RatingFilterPillState.Excluded
            null -> {
                // A rated scope with one heart exception remains a blue "全部评分" scope. Once
                // any rated bucket is excluded, the global pill becomes the black reset pill.
                if (rated.values.any { it == RatingFilterBucketMode.Excluded }) {
                    RatingFilterPillState.Excluded
                } else if (hasFavoriteFilterMemory()) {
                    RatingFilterPillState.All
                } else {
                    RatingFilterPillState.Excluded
                }
            }
        }
    }

    internal fun summaryLabel(context: Context): String? {
        if (isUnfiltered()) {
            return null
        }
        val selectedStars = (1..5).filter { modeFor(it) != RatingFilterBucketMode.Excluded }
        if (selectedStars.size == 5) {
            return context.getString(R.string.rating_filter_all)
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

    private fun withAllStarsScope(mode: RatingFilterBucketMode): HomeRatingFilterSelection = copy(
        unrated = mode,
        rated = (1..5).associateWith { mode }
    )

    private fun withRatedScope(mode: RatingFilterBucketMode): HomeRatingFilterSelection = copy(
        unrated = RatingFilterBucketMode.Excluded,
        rated = (1..5).associateWith { mode }
    )

    private fun withUnratedScope(mode: RatingFilterBucketMode): HomeRatingFilterSelection = copy(
        unrated = mode,
        rated = (1..5).associateWith { RatingFilterBucketMode.Excluded }
    )
}

internal object HomeRatingFilterUiState {
    /** Keeps the complete rating/favorite choice while moving between library tabs. */
    var selection by mutableStateOf(HomeRatingFilterSelection())
}

@Composable
internal fun StarRatingFilterRow(
    selection: HomeRatingFilterSelection,
    onSelectionChange: (HomeRatingFilterSelection) -> Unit
) {
    val globalText = stringResource(
        if (selection.isAllStarsScope()) {
            R.string.rating_filter_all
        } else {
            R.string.rating_filter_rated
        }
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StarRatingPill(
            text = globalText,
            state = selection.globalPillState(),
            onClick = { onSelectionChange(selection.cycleGlobalScope()) }
        )
        (1..5).forEach { rating ->
            StarRatingPill(
                text = stringResource(R.string.rating_filter_star, rating),
                state = selection.modeFor(rating).toPillState(),
                onClick = { onSelectionChange(selection.cycleRating(rating)) }
            )
        }
    }
}

private fun RatingFilterBucketMode.toPillState(): RatingFilterPillState = when (this) {
    RatingFilterBucketMode.All -> RatingFilterPillState.All
    RatingFilterBucketMode.Favorites -> RatingFilterPillState.Favorite
    RatingFilterBucketMode.NonFavorites -> RatingFilterPillState.NonFavorite
    RatingFilterBucketMode.Excluded -> RatingFilterPillState.Excluded
}

@Composable
private fun StarRatingPill(
    text: String,
    state: RatingFilterPillState,
    onClick: () -> Unit
) {
    val background = when (state) {
        RatingFilterPillState.All -> MiuixTheme.colorScheme.primary
        RatingFilterPillState.Favorite -> Color(0xFFFF4D6D)
        RatingFilterPillState.FavoriteDim -> Color(0xFFFF4D6D).copy(alpha = 0.38f)
        RatingFilterPillState.NonFavorite,
        RatingFilterPillState.Unrated -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f)
        RatingFilterPillState.Excluded -> Color.Black.copy(alpha = 0.42f)
        RatingFilterPillState.Normal -> MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f)
    }
    val textColor = when (state) {
        RatingFilterPillState.All -> MiuixTheme.colorScheme.onPrimary
        RatingFilterPillState.Favorite -> Color.White
        RatingFilterPillState.FavoriteDim -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        RatingFilterPillState.NonFavorite,
        RatingFilterPillState.Unrated -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        RatingFilterPillState.Excluded -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        RatingFilterPillState.Normal -> MiuixTheme.colorScheme.onSurface
    }
    Text(
        text = text,
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = textColor,
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    )
}
