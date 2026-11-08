package com.ella.music.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.navigation.NavBackStackEntry

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

private const val DetailScrollStateKey = "detail_list_scroll_state"

/**
 * List state for detail pages a shortcut/deeplink destination can cover. `rememberSaveable`
 * alone does not always survive that dispose/recompose cycle, so the position is also parked
 * in the back-stack entry's `savedStateHandle` and scrolled back when the page composes again
 * (#750). `scrollToItem` defers itself until the lazy list has laid out, so it is safe before
 * the content finishes loading.
 */
@Composable
internal fun rememberDetailListState(vararg keys: Any?, storageKey: String = DetailScrollStateKey): LazyListState {
    val listState = rememberSaveable(*keys, saver = LazyListState.Saver) { LazyListState() }
    val entry = LocalViewModelStoreOwner.current as? NavBackStackEntry
    DisposableEffect(entry, listState) {
        onDispose {
            entry?.savedStateHandle?.set(
                storageKey,
                intArrayOf(listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            )
        }
    }
    LaunchedEffect(entry, listState) {
        val saved = entry?.savedStateHandle?.get<IntArray>(storageKey) ?: return@LaunchedEffect
        // Category/folder detail pages feed the list through produceState chains, so the
        // content is still empty when the restored position is applied and LazyList would
        // clamp the request to zero (#750). Wait for real items first.
        var waitedFrames = 0
        while (listState.layoutInfo.totalItemsCount == 0 && waitedFrames++ < 300) {
            withFrameNanos { }
        }
        val lastIndex = listState.layoutInfo.totalItemsCount - 1
        if (lastIndex >= 0) {
            listState.scrollToItem(
                saved.getOrElse(0) { 0 }.coerceIn(0, lastIndex),
                saved.getOrElse(1) { 0 }
            )
        }
    }
    return listState
}
