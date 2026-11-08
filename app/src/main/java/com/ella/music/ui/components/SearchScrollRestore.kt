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
 * Independent scroll container for an expanded inline search (#777). Point the page's lazy
 * list at this state while searching instead of sharing the page's own state: the result list
 * then behaves like a separate page — scrolling it can never bleed back into the page's saved
 * position, and navigating to a result and back restores query + position together (#772)
 * because both live in the same saved-state entry. Each fresh expansion gets a clean state.
 */
@Composable
internal fun rememberSearchListState(searchExpanded: Boolean, query: String): LazyListState {
    var epoch by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var wasExpanded by rememberSaveable { mutableStateOf(false) }
    if (searchExpanded && !wasExpanded) epoch++
    wasExpanded = searchExpanded
    val state = rememberSaveable(epoch, saver = LazyListState.Saver) { LazyListState() }
    var previousQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query, searchExpanded) {
        if (searchExpanded && query.isNotBlank() && query != previousQuery) {
            withFrameNanos { }
            withFrameNanos { }
            if (state.layoutInfo.totalItemsCount > 0) state.scrollToItem(0)
        }
        previousQuery = query
    }
    return state
}

/** [rememberSearchListState] for `LazyGrid` result panes. */
@Composable
internal fun rememberSearchGridState(searchExpanded: Boolean, query: String): LazyGridState {
    var epoch by rememberSaveable { androidx.compose.runtime.mutableIntStateOf(0) }
    var wasExpanded by rememberSaveable { mutableStateOf(false) }
    if (searchExpanded && !wasExpanded) epoch++
    wasExpanded = searchExpanded
    val state = rememberSaveable(epoch, saver = LazyGridState.Saver) { LazyGridState() }
    var previousQuery by remember { mutableStateOf(query) }
    LaunchedEffect(query, searchExpanded) {
        if (searchExpanded && query.isNotBlank() && query != previousQuery) {
            withFrameNanos { }
            withFrameNanos { }
            if (state.layoutInfo.totalItemsCount > 0) state.scrollToItem(0)
        }
        previousQuery = query
    }
    return state
}

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
