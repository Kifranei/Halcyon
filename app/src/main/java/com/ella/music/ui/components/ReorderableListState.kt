package com.ella.music.ui.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState
import sh.calvin.reorderable.rememberScroller

/** Shared edge scrolling: a 72dp ramp, reaching 480dp/s at either viewport edge. */
@Composable
internal fun rememberEllaReorderableLazyListState(
    lazyListState: LazyListState,
    onMove: suspend CoroutineScope.(from: LazyListItemInfo, to: LazyListItemInfo) -> Unit
): ReorderableLazyListState {
    // The library multiplies this speed by 0..5 as the handle approaches an edge.
    val scroller = rememberScroller(lazyListState, pixelPerSecond = with(LocalDensity.current) { 96.dp.toPx() })
    return rememberReorderableLazyListState(
        lazyListState = lazyListState,
        scrollThreshold = 72.dp,
        scroller = scroller,
        onMove = onMove
    )
}
