package com.ella.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitVerticalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.verticalDrag
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal fun shouldExpandMiniPlayer(progress: Float, velocity: Float, flingThreshold: Float): Boolean =
    when {
        velocity >= flingThreshold -> false
        velocity <= -flingThreshold -> true
        else -> progress >= 0.25f
    }

internal val LocalMiniPlayerGestureOwnedByDock = staticCompositionLocalOf { false }

/** The pointer stays on the stationary mini-player throughout expansion, including reversal. */
internal fun Modifier.miniPlayerOpeningGesture(
    key: Any, enabled: Boolean, onClick: () -> Unit, dockOwner: Boolean = false
): Modifier = composed {
    val ownedByDock = LocalMiniPlayerGestureOwnedByDock.current
    var inputOrigin by remember { mutableStateOf(Offset.Zero) }
    val state = LocalPlayerMorph.current
    val scope = rememberCoroutineScope()
    val latestClick by rememberUpdatedState(onClick)
    val flingThreshold = with(LocalDensity.current) { 1000.dp.toPx() }
    val minTravel = with(LocalDensity.current) { 240.dp.toPx() }
    var settle by remember { mutableStateOf<Job?>(null) }
    if (!enabled || (ownedByDock && !dockOwner)) this else
        onGloballyPositioned { inputOrigin = it.positionInRoot() }.pointerInput(key, enabled, state, dockOwner) {
        var distance = 0f
        var travel = minTravel
        val tracker = VelocityTracker()
        fun finish(cancel: Boolean) {
            if (state == null) {
                if (!cancel && distance > 48f) latestClick()
                return
            }
            val initial = state.openingProgress ?: return
            val expand = !cancel && shouldExpandMiniPlayer(initial, tracker.calculateVelocity().y, flingThreshold)
            settle = scope.launch {
                Animatable(initial).animateTo(
                    if (expand) 1f else 0f,
                    spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow)
                ) { state.openingProgress = value }
                if (expand) state.onOpeningComplete() else state.openingProgress = null
            }
        }
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            // The stable dock parent owns the gesture without changing its layout mode.
            // Navigation buttons outside the original mini-player remain ordinary dock input.
            if (dockOwner && state?.miniBounds?.contains(down.position + inputOrigin) != true) return@awaitEachGesture
            var firstDelta = 0f
            val drag = awaitVerticalTouchSlopOrCancellation(down.id) { change, overSlop ->
                if (overSlop < 0f) { firstDelta = overSlop; change.consume() }
            } ?: return@awaitEachGesture
            settle?.cancel()
            tracker.resetTracking()
            tracker.addPosition(down.uptimeMillis, down.position + inputOrigin)
            tracker.addPosition(drag.uptimeMillis, drag.position + inputOrigin)
            travel = ((state?.miniBounds?.top ?: minTravel) - (state?.origin?.y ?: 0f)).coerceAtLeast(minTravel)
            distance = (state?.openingProgress ?: 0f) * travel - firstDelta
            if (state != null) {
                state.cancelOpening = { settle?.cancel(); finish(true) }
                state.onOpeningStart()
                state.openingProgress = (distance / travel).coerceIn(0f, 1f)
            }
            val completed = verticalDrag(drag.id) { change ->
                tracker.addPosition(change.uptimeMillis, change.position + inputOrigin)
                travel = ((state?.miniBounds?.top ?: minTravel) - (state?.origin?.y ?: 0f)).coerceAtLeast(minTravel)
                distance = (distance - (change.position.y - change.previousPosition.y)).coerceIn(0f, travel)
                state?.openingProgress = distance / travel
                change.consume()
            }
            finish(cancel = !completed)
        }
    }
}
