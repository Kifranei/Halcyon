package com.ella.music.ui.player

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

internal class PlayerCoverDismissHandle(
    val begin: () -> Boolean,
    val onVerticalDrag: (dy: Float) -> Unit,
    val onDragEnd: (velocityY: Float) -> Unit,
    val onDragCancel: () -> Unit
)

internal val LocalPlayerCoverDismiss = staticCompositionLocalOf<PlayerCoverDismissHandle?> { null }

/** Travel at which releasing commits the track change. */
private val SwipeCommitThreshold = 84.dp

/** The finger can keep going; the surface asymptotically stops here. */
private val SwipeMaxTravel = 120.dp

/**
 * Rubber band: follows the finger closely at first, then eases to [max] so a long drag never
 * tears the cover off the page.
 */
private fun rubberBand(distance: Float, max: Float): Float =
    sign(distance) * max * (1f - exp(-abs(distance) / max))

/**
 * Horizontal track switching with a visible affordance: the surface follows the finger and a
 * "previous / next track" chip is revealed on the side the new track will come from, so the
 * gesture says what it is about to do before the user commits to it.
 */
@Composable
internal fun Modifier.playerCoverGestures(
    swipeEnabled: Boolean,
    onSwipePrevious: () -> Unit,
    onSwipeNext: () -> Unit,
    dismissHandle: PlayerCoverDismissHandle?,
    hintColor: Color = Color.White,
    previousSongTitle: String? = null,
    nextSongTitle: String? = null
): Modifier {
    if (!swipeEnabled && dismissHandle == null) return this
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    // Plain float state rather than an Animatable: the drag runs inside awaitPointerEventScope,
    // which cannot suspend on one, and launching a coroutine per move event would allocate 120
    // of them a second. Only the release is animated, from a single coroutine.
    var travelPx by remember { mutableFloatStateOf(0f) }
    var hintStrength by remember { mutableFloatStateOf(0f) }
    var settleJob by remember { mutableStateOf<Job?>(null) }
    val previousLabel = stringResource(R.string.common_previous)
    val nextLabel = stringResource(R.string.common_next)
    val measurer = rememberTextMeasurer()
    val hintStyle = remember(hintColor) {
        TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold, color = hintColor)
    }
    val titleStyle = remember(hintColor) {
        TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Normal, color = hintColor)
    }
    val maxTitleWidthPx = with(LocalDensity.current) { 140.dp.toPx() }.toInt()
    val previousHint = remember(previousLabel, hintStyle) { measurer.measure(previousLabel, hintStyle) }
    val nextHint = remember(nextLabel, hintStyle) { measurer.measure(nextLabel, hintStyle) }
    val previousTitleHint = remember(previousSongTitle, titleStyle, maxTitleWidthPx) {
        previousSongTitle?.takeIf { it.isNotBlank() }?.let {
            measurer.measure(
                text = it,
                style = titleStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxTitleWidthPx)
            )
        }
    }
    val nextTitleHint = remember(nextSongTitle, titleStyle, maxTitleWidthPx) {
        nextSongTitle?.takeIf { it.isNotBlank() }?.let {
            measurer.measure(
                text = it,
                style = titleStyle,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxTitleWidthPx)
            )
        }
    }
    val currentPrevious by rememberUpdatedState(onSwipePrevious)
    val currentNext by rememberUpdatedState(onSwipeNext)

    val gestures = Modifier.pointerInput(swipeEnabled, dismissHandle) {
        val touchSlop = viewConfiguration.touchSlop
        val swipeThresholdPx = SwipeCommitThreshold.toPx()
        val maxTravelPx = SwipeMaxTravel.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            var lockedHorizontal = false
            var lockedVertical = false
            var totalDx = 0f
            var totalDy = 0f
            var dismissActive = false
            var passedThreshold = false
            val velocityTracker = VelocityTracker()
            velocityTracker.addPosition(down.uptimeMillis, down.position)

            fun settle(commit: Int) {
                settleJob?.cancel()
                settleJob = scope.launch {
                    if (commit < 0) currentNext() else if (commit > 0) currentPrevious()
                    animate(
                        initialValue = travelPx,
                        targetValue = 0f,
                        animationSpec = spring(dampingRatio = 0.78f, stiffness = 380f)
                    ) { value, _ ->
                        travelPx = value
                        hintStrength = hintStrength * 0.86f
                    }
                    travelPx = 0f
                    hintStrength = 0f
                }
            }

            fun finishIfUp(pressed: Boolean): Boolean {
                if (pressed) return false
                if (lockedHorizontal) {
                    settle(
                        when {
                            totalDx <= -swipeThresholdPx -> -1
                            totalDx >= swipeThresholdPx -> 1
                            else -> 0
                        }
                    )
                } else if (dismissActive) {
                    dismissHandle?.onDragEnd(velocityTracker.calculateVelocity().y)
                }
                return true
            }

            do {
                val initial = awaitPointerEvent(PointerEventPass.Initial)
                val initialChange = initial.changes.firstOrNull { it.id == down.id } ?: break
                if (finishIfUp(initialChange.pressed)) break
                val delta = initialChange.position - initialChange.previousPosition
                totalDx += delta.x
                totalDy += delta.y
                velocityTracker.addPosition(initialChange.uptimeMillis, initialChange.position)
                if (!lockedHorizontal && !lockedVertical) {
                    val absX = abs(totalDx)
                    val absY = abs(totalDy)
                    if (absY > touchSlop && absY > absX && totalDy > 0f && dismissHandle != null) {
                        lockedVertical = true
                        dismissActive = dismissHandle.begin()
                        if (dismissActive) {
                            dismissHandle.onVerticalDrag(totalDy)
                            initialChange.consume()
                        }
                    }
                } else if (dismissActive) {
                    dismissHandle?.onVerticalDrag(delta.y)
                    initialChange.consume()
                }
                if (lockedVertical || !swipeEnabled) continue
                val main = awaitPointerEvent(PointerEventPass.Main)
                val mainChange = main.changes.firstOrNull { it.id == down.id } ?: break
                if (finishIfUp(mainChange.pressed)) break
                if (lockedHorizontal) {
                    mainChange.consume()
                } else if (!mainChange.isConsumed) {
                    val absX = abs(totalDx)
                    val absY = abs(totalDy)
                    if (absX > touchSlop && absX > absY) {
                        lockedHorizontal = true
                        mainChange.consume()
                    }
                }
                if (!lockedHorizontal) continue
                settleJob?.cancel()
                // Discount the slop so the surface starts from rest under the finger.
                travelPx = rubberBand(totalDx - sign(totalDx) * touchSlop, maxTravelPx)
                hintStrength = (abs(totalDx) / swipeThresholdPx).coerceIn(0f, 1f)
                val nowPastThreshold = abs(totalDx) >= swipeThresholdPx
                if (nowPastThreshold != passedThreshold) {
                    passedThreshold = nowPastThreshold
                    // Confirm the commit point without looking away from the artwork.
                    if (nowPastThreshold) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                }
            } while (true)
        }
    }

    if (!swipeEnabled) return this.then(gestures)

    return this
        .drawWithCache {
            val chipPadding = 10.dp.toPx()
            val chipInset = 12.dp.toPx()
            val chevron = 4.5.dp.toPx()
            val lineGap = 1.5.dp.toPx()
            onDrawWithContent {
                drawContent()
                val travel = travelPx
                val strength = hintStrength
                if (strength <= 0.02f || travel == 0f) return@onDrawWithContent
                // Dragging left uncovers the right edge, which is where the next track arrives.
                val towardsPrevious = travel > 0f
                val hint = if (towardsPrevious) previousHint else nextHint
                val titleHint = if (towardsPrevious) previousTitleHint else nextTitleHint
                val chipHeight = if (titleHint != null) 42.dp.toPx() else 28.dp.toPx()
                val contentWidth = if (titleHint != null) maxOf(hint.size.width, titleHint.size.width) else hint.size.width
                val chipWidth = contentWidth + chipPadding * 3f + chevron * 2f
                val left = if (towardsPrevious) {
                    chipInset
                } else {
                    size.width - chipInset - chipWidth
                }
                val top = (size.height - chipHeight) / 2f
                val eased = (strength * strength * (3f - 2f * strength)).coerceIn(0f, 1f)
                drawRoundRect(
                    color = hintColor.copy(alpha = 0.16f * eased),
                    topLeft = Offset(left, top),
                    size = Size(chipWidth, chipHeight),
                    cornerRadius = CornerRadius(chipHeight / 2f)
                )
                val textAlpha = 0.95f * eased
                val chevronX = if (towardsPrevious) left + chipPadding + chevron else left + chipWidth - chipPadding - chevron
                val midY = top + chipHeight / 2f
                val direction = if (towardsPrevious) -1f else 1f
                listOf(-chevron, chevron).forEach { dy ->
                    drawLine(
                        color = hintColor.copy(alpha = textAlpha),
                        start = Offset(chevronX - direction * chevron, midY + dy),
                        end = Offset(chevronX, midY),
                        strokeWidth = 1.6.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
                if (titleHint != null) {
                    val totalHeight = hint.size.height + titleHint.size.height + lineGap
                    val startY = midY - totalHeight / 2f
                    if (towardsPrevious) {
                        val textX = left + chipPadding * 2f + chevron * 2f
                        drawText(
                            textLayoutResult = hint,
                            color = hintColor.copy(alpha = textAlpha),
                            topLeft = Offset(textX, startY)
                        )
                        drawText(
                            textLayoutResult = titleHint,
                            color = hintColor.copy(alpha = textAlpha * 0.85f),
                            topLeft = Offset(textX, startY + hint.size.height + lineGap)
                        )
                    } else {
                        val textRight = left + chipWidth - chipPadding * 2f - chevron * 2f
                        drawText(
                            textLayoutResult = hint,
                            color = hintColor.copy(alpha = textAlpha),
                            topLeft = Offset(textRight - hint.size.width, startY)
                        )
                        drawText(
                            textLayoutResult = titleHint,
                            color = hintColor.copy(alpha = textAlpha * 0.85f),
                            topLeft = Offset(textRight - titleHint.size.width, startY + hint.size.height + lineGap)
                        )
                    }
                } else {
                    drawText(
                        textLayoutResult = hint,
                        color = hintColor.copy(alpha = textAlpha),
                        topLeft = Offset(
                            x = if (towardsPrevious) {
                                left + chipPadding * 2f + chevron * 2f
                            } else {
                                left + chipPadding
                            },
                            y = midY - hint.size.height / 2f
                        )
                    )
                }
            }
        }
        .graphicsLayer {
            translationX = travelPx
            // A touch of recession sells the card as something being dragged off the stack.
            val recede = 1f - 0.035f * (abs(travelPx) / SwipeMaxTravel.toPx()).coerceIn(0f, 1f)
            scaleX = recede
            scaleY = recede
        }
        .then(gestures)
}
