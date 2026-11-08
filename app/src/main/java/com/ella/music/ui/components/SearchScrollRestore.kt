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
    // Read during composition, before the first layout. A header-only list clamps a restored
    // index to the top on that layout, so the value has to be captured before that happens (#767).
    val restoredAtComposition = remember(listState) {
        listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
    }
    val entry = LocalViewModelStoreOwner.current as? NavBackStackEntry
    DisposableEffect(entry, listState) {
        onDispose {
            val handle = entry?.savedStateHandle ?: return@onDispose
            val index = listState.firstVisibleItemIndex
            val offset = listState.firstVisibleItemScrollOffset
            val previousIndex = handle.get<IntArray>(storageKey)?.getOrElse(0) { 0 } ?: 0
            val count = listState.layoutInfo.totalItemsCount
            // Leaving while the list is still shorter than the stored index is a clamp, not a scroll.
            if (count <= previousIndex && index < previousIndex) return@onDispose
            handle[storageKey] = intArrayOf(index, offset)
        }
    }
    LaunchedEffect(entry, listState) {
        val saved = entry?.savedStateHandle?.get<IntArray>(storageKey)
        val savedIndex = saved?.getOrElse(0) { 0 } ?: 0
        val savedOffset = saved?.getOrElse(1) { 0 } ?: 0
        val targetIndex = maxOf(restoredAtComposition.first, savedIndex)
        val targetOffset = if (savedIndex >= restoredAtComposition.first) savedOffset else restoredAtComposition.second
        if (targetIndex == 0 && targetOffset == 0) return@LaunchedEffect
        // Detail pages always compose a header, so totalItemsCount > 0 before the songs arrive
        // and a one-shot scrollToItem is clamped to that header (#767). Re-apply as the list grows.
        var lastCount = -1
        repeat(300) {
            val count = listState.layoutInfo.totalItemsCount
            if (count > 0 && count != lastCount) {
                lastCount = count
                val lastIndex = count - 1
                val index = targetIndex.coerceAtMost(lastIndex)
                val offset = if (index == targetIndex) targetOffset else 0
                if (listState.firstVisibleItemIndex != index || listState.firstVisibleItemScrollOffset != offset) {
                    listState.scrollToItem(index, offset)
                }
                if (count > targetIndex) return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }
    return listState
}
