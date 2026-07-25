package com.ella.music.ui.playlist

import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import sh.calvin.reorderable.DragGestureDetector

/**
 * Start the reorder gesture on the handle's initial press and claim that press before the row's
 * combined-clickable receives a long-click. This makes both immediate drag and hold-then-drag
 * reliable, while keeping a long press on a handle from entering multi-selection mode.
 */
internal object ImmediateOrLongPressDragGestureDetector : DragGestureDetector {
    override suspend fun PointerInputScope.detect(
        onDragStart: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onDrag: (PointerInputChange, Offset) -> Unit
    ) {
        awaitEachGesture {
            // Consume at the initial pass so a held handle cannot also become a row long-click.
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            down.consume()
            onDragStart(down.position)
            while (true) {
                val change = awaitPointerEvent().changes.firstOrNull { it.id == down.id } ?: run {
                    onDragCancel()
                    break
                }
                if (!change.pressed || change.changedToUpIgnoreConsumed()) {
                    onDragEnd()
                    break
                }
                val dragAmount = change.positionChange()
                if (dragAmount != Offset.Zero) {
                    onDrag(change, dragAmount)
                    change.consume()
                }
            }
        }
    }
}
