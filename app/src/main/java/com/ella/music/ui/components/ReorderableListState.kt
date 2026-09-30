package com.ella.music.ui.components

import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import sh.calvin.reorderable.ReorderableLazyListState
import sh.calvin.reorderable.rememberReorderableLazyListState

/** Keep the original 48dp edge ramp and viewport-relative speed on every sortable list. */
@Composable
internal fun rememberEllaReorderableLazyListState(
    lazyListState: LazyListState,
    onMove: suspend CoroutineScope.(from: LazyListItemInfo, to: LazyListItemInfo) -> Unit
): ReorderableLazyListState {
    return rememberReorderableLazyListState(
        lazyListState = lazyListState,
        onMove = onMove
    )
}
