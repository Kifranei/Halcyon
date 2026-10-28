package com.ella.music.ui.navigation

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import kotlin.math.abs

internal enum class BackDragDirection { Pending, Back, Rejected }

internal fun backDragDirection(x: Float, y: Float, touchSlop: Float): BackDragDirection = when {
    x > touchSlop && x > abs(y) * 1.2f -> BackDragDirection.Back
    abs(y) > touchSlop || x < -touchSlop -> BackDragDirection.Rejected
    else -> BackDragDirection.Pending
}

internal fun shouldCompleteBackDrag(distance: Float, width: Float, velocity: Float, density: Float): Boolean {
    if (width <= 0f || velocity < -500f * density) return false
    return distance >= width * 0.33f || (distance >= 24f * density && velocity >= 900f * density)
}

/** Observes unclaimed horizontal drags; sliders, pagers and vertical scrolling keep priority. */
internal fun Modifier.fullScreenBackGesture(
    enabled: Boolean,
    gestureKey: Any?,
    canStart: () -> Boolean,
    onStarted: (Offset) -> Unit,
    onProgressed: (Offset, Float) -> Unit,
    onCancelled: () -> Unit,
    onCompleted: () -> Unit
): Modifier = if (!enabled) this else pointerInput(gestureKey) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!canStart()) return@awaitEachGesture
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var started = false
        try {
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.count { it.pressed } > 1) break
                tracker.addPosition(change.uptimeMillis, change.position)
                val delta = change.position - down.position
                val distance = (delta.x - viewConfiguration.touchSlop).coerceIn(0f, size.width.toFloat())
                if (!started) {
                    if (change.isConsumed || !change.pressed) break
                    when (backDragDirection(delta.x, delta.y, viewConfiguration.touchSlop)) {
                        BackDragDirection.Rejected -> break
                        BackDragDirection.Pending -> continue
                        BackDragDirection.Back -> {
                            if (!canStart()) break
                            started = true
                            onStarted(down.position)
                        }
                    }
                }
                change.consume()
                onProgressed(change.position, distance / size.width.coerceAtLeast(1))
                if (!change.pressed) {
                    val complete = shouldCompleteBackDrag(distance, size.width.toFloat(), tracker.calculateVelocity().x, density)
                    started = false
                    if (complete) onCompleted() else onCancelled()
                    break
                }
            }
        } finally {
            // A second finger, a route change, or disposal cancels without popping the stack.
            if (started) onCancelled()
        }
    }
}
