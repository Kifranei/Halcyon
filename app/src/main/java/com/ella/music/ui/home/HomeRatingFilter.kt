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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** Keeps the library rating choice while moving between tabs, but resets with the app process. */
internal object HomeRatingFilterUiState {
    var selectedRatings by mutableStateOf<Set<Int>>(emptySet())
}

@Composable
internal fun StarRatingFilterRow(
    selectedRatings: Set<Int>,
    onRatingsChange: (Set<Int>) -> Unit
) {
    val allSelected = selectedRatings.isEmpty() || selectedRatings == ALL_RATED_FILTER
    val unratedOnly = selectedRatings == setOf(0)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        StarRatingPill(
            // Cycle all songs → unrated → all rated, without losing a full five-star selection.
            text = stringResource(
                if (selectedRatings == ALL_RATED_FILTER) R.string.rating_filter_rated
                else R.string.rating_filter_all
            ),
            selected = allSelected,
            dim = unratedOnly,
            onClick = {
                onRatingsChange(
                    when (selectedRatings) {
                        emptySet<Int>() -> setOf(0)
                        setOf(0) -> ALL_RATED_FILTER
                        else -> emptySet()
                    }
                )
            }
        )
        (1..5).forEach { rating ->
            StarRatingPill(
                text = stringResource(R.string.rating_filter_star, rating),
                selected = rating in selectedRatings,
                onClick = {
                    val next = selectedRatings.toggleRating(rating)
                    onRatingsChange(next.normalizedRatingFilter())
                }
            )
        }
    }
}

private fun Set<Int>.toggleRating(rating: Int): Set<Int> {
    val safeRating = rating.coerceIn(1, 5)
    return if (isEmpty()) {
        setOf(safeRating)
    } else if (safeRating in this) {
        this - safeRating
    } else {
        this + safeRating
    }
}

internal fun Set<Int>.normalizedRatingFilter(): Set<Int> {
    val normalized = filter { it in 1..5 }.toSortedSet()
    return normalized
}

private val ALL_RATED_FILTER: Set<Int> = (1..5).toSet()

internal fun Set<Int>.summaryLabel(context: Context): String? {
    if (isEmpty()) return null
    if (this == setOf(0)) return context.getString(R.string.rating_filter_unrated)
    return this
        .filter { it in 1..5 }
        .sorted()
        .joinToString(separator = " · ") { rating ->
            context.getString(R.string.rating_filter_star, rating)
        }
        .ifBlank { null }
}

@Composable
private fun StarRatingPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    dim: Boolean = false
) {
    val background = when {
        selected -> MiuixTheme.colorScheme.primary
        dim -> MiuixTheme.colorScheme.onSurface.copy(alpha = 0.16f)
        else -> MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.82f)
    }
    val textColor = when {
        selected -> MiuixTheme.colorScheme.onPrimary
        dim -> MiuixTheme.colorScheme.onSurfaceVariantSummary
        else -> MiuixTheme.colorScheme.onSurface
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
