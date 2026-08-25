package com.ella.music.ui.player

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal class PlayerCoverDismissHandle(
    val begin: () -> Boolean,
    val onVerticalDrag: (dy: Float) -> Unit,
    val onDragEnd: (velocityY: Float) -> Unit,
    val onDragCancel: () -> Unit
)

internal val LocalPlayerCoverDismiss = staticCompositionLocalOf<PlayerCoverDismissHandle?> { null }

internal fun Modifier.playerCoverGestures(
    swipeEnabled: Boolean,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    dismissHandle: PlayerCoverDismissHandle?
): Modifier {
    if (!swipeEnabled && dismissHandle == null) return this
    return pointerInput(swipeEnabled, onSwipePrevious, onSwipeNext, dismissHandle) {
        val touchSlop = viewConfiguration.touchSlop
        val swipeThresholdPx = 84.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var lockedHorizontal = false
            var lockedVertical = false
            var totalDx = 0f
            var totalDy = 0f
            var dismissActive = false
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)
            do {
                // Observe the raw motion before lyric rows consume it for tap/long-press
                // detection. Horizontal switching is still gated by swipeEnabled below.
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) {
                    if (lockedHorizontal) {
                        when {
                            totalDx <= -swipeThresholdPx -> onSwipeNext()
                            totalDx >= swipeThresholdPx -> onSwipePrevious()
                        }
                    } else if (dismissActive) {
                        dismissHandle?.onDragEnd(velocityTracker.calculateVelocity().y)
                    }
                    break
                }
                val delta = change.position - change.previousPosition
                totalDx += delta.x
                totalDy += delta.y
                velocityTracker.addPosition(change.uptimeMillis, change.position)
                if (!lockedHorizontal && !lockedVertical) {
                    val absX = abs(totalDx)
                    val absY = abs(totalDy)
                    when {
                        absX > touchSlop && absX > absY && swipeEnabled -> {
                            lockedHorizontal = true
                            change.consume()
                        }
                        absY > touchSlop && absY > absX && totalDy > 0f && dismissHandle != null -> {
                            lockedVertical = true
                            dismissActive = dismissHandle.begin()
                            if (dismissActive) {
                                dismissHandle.onVerticalDrag(totalDy)
                                change.consume()
                            }
                        }
                    }
                } else if (lockedHorizontal) {
                    change.consume()
                } else if (dismissActive) {
                    dismissHandle?.onVerticalDrag(delta.y)
                    change.consume()
                }
            } while (true)
        }
    }
}
