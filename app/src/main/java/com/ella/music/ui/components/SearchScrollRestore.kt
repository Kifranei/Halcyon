package com.ella.music.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos

/**
 * Restores the place a user was reading after an inline search is dismissed.
 *
 * Filtering a lazy list can legitimately move its state back to item zero when the old visible
 * item is absent from the filtered result.  Remembering the pre-search index means closing the
 * search returns to the original unfiltered position instead of looking like a page refresh.
 */
@Composable
internal fun RestoreListScrollAfterSearch(
    searchExpanded: Boolean,
    query: String,
    listState: LazyListState
) {
    var wasSearchExpanded by remember { mutableStateOf(searchExpanded) }
    var previousQuery by remember { mutableStateOf(query) }
    var anchor by remember { mutableStateOf<ScrollAnchor?>(null) }
    LaunchedEffect(searchExpanded, query) {
        when {
            searchExpanded && !wasSearchExpanded -> {
                anchor = ScrollAnchor(
                    index = listState.firstVisibleItemIndex,
                    offset = listState.firstVisibleItemScrollOffset
                )
            }

            searchExpanded && query.isNotBlank() && query != previousQuery -> {
                // Let the filtered items commit before moving to their first result.  The extra
                // frame prevents a stale layout from applying the old list's final anchor.
                withFrameNanos { }
                withFrameNanos { }
                if (listState.layoutInfo.totalItemsCount > 0) listState.scrollToItem(0)
            }

            !searchExpanded && wasSearchExpanded && query.isBlank() -> {
                anchor?.let { saved ->
                    // Wait until the cleared query has restored the original items.
                    withFrameNanos { }
                    val lastIndex = listState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) {
                        listState.scrollToItem(saved.index.coerceIn(0, lastIndex), saved.offset)
                    }
                }
                anchor = null
            }
        }
        // Back handlers commonly clear `searchExpanded` and `query` in two state writes. Keep the
        // close transition armed while the old query is still visible, otherwise the first
        // recomposition consumes the transition and the second one cannot restore the anchor.
        if (searchExpanded || query.isBlank()) {
            wasSearchExpanded = searchExpanded
        }
        previousQuery = query
    }
}

@Composable
internal fun RestoreGridScrollAfterSearch(
    searchExpanded: Boolean,
    query: String,
    gridState: LazyGridState
) {
    var wasSearchExpanded by remember { mutableStateOf(searchExpanded) }
    var previousQuery by remember { mutableStateOf(query) }
    var anchor by remember { mutableStateOf<ScrollAnchor?>(null) }
    LaunchedEffect(searchExpanded, query) {
        when {
            searchExpanded && !wasSearchExpanded -> {
                anchor = ScrollAnchor(
                    index = gridState.firstVisibleItemIndex,
                    offset = gridState.firstVisibleItemScrollOffset
                )
            }

            searchExpanded && query.isNotBlank() && query != previousQuery -> {
                withFrameNanos { }
                withFrameNanos { }
                if (gridState.layoutInfo.totalItemsCount > 0) gridState.scrollToItem(0)
            }

            !searchExpanded && wasSearchExpanded && query.isBlank() -> {
                anchor?.let { saved ->
                    withFrameNanos { }
                    val lastIndex = gridState.layoutInfo.totalItemsCount - 1
                    if (lastIndex >= 0) {
                        gridState.scrollToItem(saved.index.coerceIn(0, lastIndex), saved.offset)
                    }
                }
                anchor = null
            }
        }
        if (searchExpanded || query.isBlank()) {
            wasSearchExpanded = searchExpanded
        }
        previousQuery = query
    }
}

private data class ScrollAnchor(
    val index: Int,
    val offset: Int
)
