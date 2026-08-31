package com.ella.music.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.CancellationException
import kotlin.math.abs
import kotlin.math.sqrt

// Gesture state machine adapted from RawS-Music's ComposePowerListState: pinch progress
// follows the fingers, velocity decides commit vs rollback, and the layout edges respond
// with a damped elastic over-pull.
private const val SNAP_DURATION_MS = 500
private const val LAYOUT_COMMIT_DURATION_MS = 250
private const val LAYOUT_ROLLBACK_DURATION_MS = 500
private const val ELASTIC_REBOUND_DURATION_MS = 350
private const val VELOCITY_THRESHOLD_DP = 500f
private const val POSITION_THRESHOLD = 0.3f

@Stable
internal class LibraryPinchState(initialLayout: Int) {

    /** Layout settled after the last interaction; drives rendering while a pinch owns the screen. */
    var currentLayout by mutableIntStateOf(initialLayout)
        private set

    var sourceLayout by mutableIntStateOf(initialLayout)
        private set

    var targetLayout by mutableIntStateOf(initialLayout)
        private set

    var transitionProgress by mutableFloatStateOf(1f)
        private set

    /** Extra rubber-band scale while the drag over-pulls an in-flight transition. */
    var transitionScaleFactor by mutableFloatStateOf(1f)
        private set

    /** Damped scale applied to the settled list when pinching past the first/last layout. */
    var boundaryElasticScale by mutableFloatStateOf(1f)
        private set

    var isTransitioning by mutableStateOf(false)
        private set

    var isPinching by mutableStateOf(false)
        internal set

    private var boundaryAnimationGeneration = 0
    private var boundaryRawOverPull by mutableFloatStateOf(0f)

    val isZoomInTransition: Boolean
        get() = layoutOrder(targetLayout) > layoutOrder(sourceLayout)

    fun beginPinch() {
        boundaryAnimationGeneration += 1
        isPinching = true
    }

    /**
     * @param rawDelta accumulated pinch ratio minus one; positive when the fingers spread
     * (zoom in, towards the detailed list), negative when they close (towards the grid).
     * @param velocityDp current pinch velocity in dp/s.
     */
    fun updatePinch(rawDelta: Float, velocityDp: Float) {
        val isZoomIn = rawDelta > 0f
        if (!isTransitioning) {
            val target = nextLayout(isZoomIn)
            if (target == null) {
                updateBoundaryElastic(rawDelta, isZoomIn)
                return
            }
            beginTransition(target)
        }
        val signedDelta = if (isZoomIn == isZoomInTransition) abs(rawDelta) else -abs(rawDelta)
        transitionProgress = signedDelta.coerceIn(0f, 1f)
        transitionScaleFactor = elasticScale(signedDelta, isZoomInTransition)
    }

    suspend fun finishPinch(velocityDp: Float): Boolean {
        isPinching = false
        if (!isTransitioning) {
            animateBoundaryBack()
            return false
        }
        val confirm = if (abs(velocityDp) >= VELOCITY_THRESHOLD_DP) {
            (velocityDp > 0f) == isZoomInTransition
        } else {
            transitionProgress > POSITION_THRESHOLD
        }
        animateTransition(confirm, velocityDp)
        return confirm
    }

    /**
     * Snaps out of an interrupted gesture (coroutine cancelled mid-pinch or mid-animation) by
     * rolling back to the source layout, which is the one the settled list state is anchored to.
     */
    fun cancelPinch() {
        if (!isTransitioning && !isPinching) return
        isPinching = false
        if (isTransitioning) {
            completeTransition(false)
        } else {
            boundaryRawOverPull = 0f
            boundaryElasticScale = 1f
        }
    }

    /** Applies a layout change that did not come from the pinch gesture (toggle button, prefs). */
    fun onExternalLayout(layout: Int) {
        if (layout == currentLayout || isTransitioning || isPinching) return
        currentLayout = layout
        sourceLayout = layout
        targetLayout = layout
        transitionProgress = 1f
        transitionScaleFactor = 1f
        boundaryRawOverPull = 0f
        boundaryElasticScale = 1f
    }

    private fun beginTransition(target: Int) {
        sourceLayout = currentLayout
        targetLayout = target
        transitionProgress = 0f
        transitionScaleFactor = 1f
        boundaryRawOverPull = 0f
        boundaryElasticScale = 1f
        isTransitioning = true
    }

    private suspend fun animateTransition(confirm: Boolean, releaseVelocityDp: Float) {
        val start = transitionProgress
        val end = if (confirm) 1f else 0f
        val baseDuration = if (confirm) LAYOUT_COMMIT_DURATION_MS else LAYOUT_ROLLBACK_DURATION_MS
        val velocityDuration = computeVelocityDuration(start, end, releaseVelocityDp)
        val duration = velocityDuration
            ?: (abs(end - start) * baseDuration).toInt().coerceIn(
                if (confirm) 100 else baseDuration / 3,
                baseDuration
            )
        val animationDuration = if (abs(transitionScaleFactor - 1f) >= 0.001f) {
            maxOf(duration, ELASTIC_REBOUND_DURATION_MS)
        } else {
            duration
        }
        try {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = animationDuration, easing = LinearEasing)
            ) { elapsedFraction, _ ->
                val transitionFraction = (
                    elapsedFraction * animationDuration / duration.coerceAtLeast(1)
                    ).coerceIn(0f, 1f)
                val elasticFraction = (
                    elapsedFraction * animationDuration / ELASTIC_REBOUND_DURATION_MS
                    ).coerceIn(0f, 1f)
                transitionProgress = lerp(start, end, transitionFraction)
                transitionScaleFactor = lerp(transitionScaleFactor, 1f, cubicEaseOut(elasticFraction))
            }
            completeTransition(confirm)
        } catch (cancelled: CancellationException) {
            throw cancelled
        }
    }

    private fun completeTransition(confirm: Boolean) {
        val finalLayout = if (confirm) targetLayout else sourceLayout
        currentLayout = finalLayout
        sourceLayout = finalLayout
        targetLayout = finalLayout
        transitionProgress = 1f
        transitionScaleFactor = 1f
        boundaryRawOverPull = 0f
        boundaryElasticScale = 1f
        isTransitioning = false
    }

    private fun updateBoundaryElastic(rawDelta: Float, expands: Boolean) {
        val settledLayout = currentLayout
        sourceLayout = settledLayout
        targetLayout = settledLayout
        transitionProgress = 1f
        boundaryRawOverPull = if (expands) abs(rawDelta) else -abs(rawDelta)
        boundaryElasticScale = computeBoundaryElasticScale(boundaryRawOverPull)
        transitionScaleFactor = 1f
        isTransitioning = false
    }

    private suspend fun animateBoundaryBack() {
        val generation = boundaryAnimationGeneration
        val start = boundaryRawOverPull
        val startScale = boundaryElasticScale
        if (abs(start) < 0.001f) {
            boundaryRawOverPull = 0f
            boundaryElasticScale = 1f
            return
        }
        animate(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = tween(durationMillis = ELASTIC_REBOUND_DURATION_MS, easing = LinearEasing)
        ) { fraction, _ ->
            if (generation == boundaryAnimationGeneration) {
                val eased = cubicEaseOut(fraction)
                boundaryRawOverPull = lerp(start, 0f, eased)
                boundaryElasticScale = lerp(startScale, 1f, eased)
            }
        }
        if (generation == boundaryAnimationGeneration) {
            boundaryRawOverPull = 0f
            boundaryElasticScale = 1f
        }
    }

    private fun nextLayout(isZoomIn: Boolean): Int? {
        val order = layoutOrder(currentLayout)
        val targetOrder = if (isZoomIn) {
            (order + 1).takeIf { it <= LAYOUT_ORDER_LIST }
        } else {
            (order - 1).takeIf { it >= LAYOUT_ORDER_GRID }
        }
        return targetOrder?.let(::layoutForOrder)
    }

    companion object {
        // Layout "zoom" order: GRID is the most zoomed-out (many small covers), LIST the most
        // zoomed-in (single column, large rows). Spreading the fingers moves up this order.
        internal const val LAYOUT_ORDER_GRID = 0
        internal const val LAYOUT_ORDER_MULTI_ROW = 1
        internal const val LAYOUT_ORDER_LIST = 2

        internal fun layoutOrder(layout: Int): Int = when (layout) {
            SettingsManager.LIBRARY_LAYOUT_LIST -> LAYOUT_ORDER_LIST
            SettingsManager.LIBRARY_LAYOUT_MULTI_ROW -> LAYOUT_ORDER_MULTI_ROW
            else -> LAYOUT_ORDER_GRID
        }

        private fun layoutForOrder(order: Int): Int = when (order.coerceIn(
            LAYOUT_ORDER_GRID,
            LAYOUT_ORDER_LIST
        )) {
            LAYOUT_ORDER_LIST -> SettingsManager.LIBRARY_LAYOUT_LIST
            LAYOUT_ORDER_MULTI_ROW -> SettingsManager.LIBRARY_LAYOUT_MULTI_ROW
            else -> SettingsManager.LIBRARY_LAYOUT_GRID
        }
    }
}

private fun elasticScale(signedDelta: Float, isZoomIn: Boolean): Float {
    val overPull = when {
        signedDelta > 1f -> signedDelta - 1f
        signedDelta < 0f -> signedDelta
        else -> return 1f
    }
    val direction = if (isZoomIn) 1f else -1f
    val elasticOffset = computeElasticOverpullOffset(overPull)
    return (1f + elasticOffset * direction).coerceIn(0.9105f, 1.0895f)
}

private fun computeBoundaryElasticScale(rawOverPull: Float): Float {
    val easedOffset = computeElasticOverpullOffset(rawOverPull)
    return (1f + easedOffset).coerceIn(0.9105f, 1.0895f)
}

private fun computeElasticOverpullOffset(rawOverPull: Float): Float = when {
    rawOverPull > 0f -> {
        boundaryEasing((rawOverPull.coerceAtMost(3f) / 3f).coerceIn(0f, 1f))
    }
    rawOverPull < 0f -> {
        val reverse = (1f - (1f + rawOverPull).coerceIn(0.1f, 1f)) / 0.9f
        -boundaryEasing(reverse.coerceIn(0f, 1f))
    }
    else -> 0f
}

private fun boundaryEasing(value: Float): Float {
    val clamped = value.coerceIn(0f, 1f)
    return sqrt(clamped * 0.2f) * 0.2f
}

private fun cubicEaseOut(value: Float): Float {
    val remaining = 1f - value.coerceIn(0f, 1f)
    return 1f - remaining * remaining * remaining
}

private fun lerp(start: Float, end: Float, fraction: Float): Float =
    start + (end - start) * fraction.coerceIn(0f, 1f)

private fun computeVelocityDuration(start: Float, end: Float, velocityDp: Float): Int? {
    val velocity = abs(velocityDp)
    if (velocity < VELOCITY_THRESHOLD_DP) return null
    val distance = abs(end - start).coerceAtLeast(0.0001f)
    val normalizedVelocity = (velocity / VELOCITY_THRESHOLD_DP).coerceIn(1f, 4f)
    return ((SNAP_DURATION_MS * distance) / normalizedVelocity).toInt().coerceIn(80, SNAP_DURATION_MS)
}
