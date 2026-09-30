package com.ella.music.ui.components

import kotlin.math.roundToInt

/** Drawing and dragging share the same thumb travel, including the minimum thumb size. */
internal data class ScrollThumbGeometry(val height: Float, val travel: Float) {
    fun offset(progress: Float): Float = travel * progress.coerceIn(0f, 1f)

    fun progress(offset: Float): Float =
        if (travel > 0f) (offset / travel).coerceIn(0f, 1f) else 0f
}

internal fun scrollThumbGeometry(
    trackHeight: Float,
    visibleFraction: Float,
    minimumHeight: Float
): ScrollThumbGeometry {
    val track = trackHeight.coerceAtLeast(0f)
    val height = (track * visibleFraction.coerceIn(0f, 1f))
        .coerceAtLeast(minimumHeight).coerceAtMost(track)
    return ScrollThumbGeometry(height = height, travel = track - height)
}

internal fun scrollThumbTargetIndex(progress: Float, maxFirst: Int, totalCount: Int): Int {
    if (totalCount <= 0) return 0
    // Request the last item at the end of the track; the lazy layout clamps it to the
    // actual bottom, even with partial rows, content padding or unequal item heights.
    if (progress >= 1f) return totalCount - 1
    return (progress.coerceAtLeast(0f) * maxFirst.coerceAtLeast(0)).roundToInt()
        .coerceIn(0, totalCount - 1)
}
