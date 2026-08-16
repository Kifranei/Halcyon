package com.ella.music.ui.player

import android.os.SystemClock
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

@Composable
internal fun PlayerDismissMotionHost(
    openToken: Int,
    onDismissProgressChange: (Float) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    backEnabled: Boolean = true,
    overlayContent: @Composable () -> Unit = {},
    content: @Composable (dismissingPlayer: Boolean) -> Unit
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    val dragDismissOffset = remember { Animatable(0f) }
    var dismissingPlayer by remember { mutableStateOf(false) }
    var predictiveGestureGeneration by remember { mutableIntStateOf(0) }
    val topDragLimitPx = with(density) { 132.dp.toPx() }
    val dismissThresholdPx = with(density) { 240.dp.toPx() }
    val dismissVelocityThresholdPx = with(density) { 1250.dp.toPx() }
    val dismissTargetPx = remember(view.height) {
        view.height.takeIf { it > 0 }?.toFloat() ?: with(density) { 760.dp.toPx() }
    }
    // HyperOS exposes the exact physical screen corner in framework resources. Reusing it here
    // keeps the player's largest dismiss state aligned with the device bezel instead of the old
    // fixed 30.dp approximation (which is visibly too small on Xiaomi phones).
    val screenCornerRadius = remember(view.resources, density) {
        val resourceId = view.resources.getIdentifier(
            "rounded_corner_radius_top",
            "dimen",
            "android"
        )
        val radiusPx = resourceId
            .takeIf { it != 0 }
            ?.let { id -> runCatching { view.resources.getDimensionPixelSize(id) }.getOrNull() }
            ?.takeIf { it > 0 }
        radiusPx?.let { with(density) { it.toDp() } } ?: 30.dp
    }
    val dismissProgress = (dragDismissOffset.value / dismissThresholdPx).coerceIn(0f, 1f)
    val dragCornerRadius = screenCornerRadius * dismissProgress

    fun dismissWithMotion() {
        if (dismissingPlayer) return
        scope.launch {
            if (dismissingPlayer) return@launch
            dismissingPlayer = true
            dragDismissOffset.stop()
            dragDismissOffset.animateTo(
                targetValue = dismissTargetPx,
                animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
            )
            latestOnDismiss()
        }
    }

    LaunchedEffect(openToken) {
        dismissingPlayer = false
        dragDismissOffset.snapTo(0f)
        onDismissProgressChange(0f)
    }
    SideEffect {
        onDismissProgressChange(dismissProgress)
    }
    DisposableEffect(Unit) {
        onDispose { onDismissProgressChange(0f) }
    }
    PredictiveBackHandler(enabled = backEnabled) { progress ->
        val gestureGeneration = ++predictiveGestureGeneration
        try {
            dragDismissOffset.stop()
            progress.collect { backEvent ->
                // Reuse the player's existing vertical-dismiss motion so the destination below
                // the resident overlay is revealed continuously during the system back gesture.
                dragDismissOffset.snapTo(
                    dismissTargetPx * FastOutSlowInEasing.transform(backEvent.progress)
                )
            }
            if (!dismissingPlayer) {
                dismissingPlayer = true
                dragDismissOffset.animateTo(
                    targetValue = dismissTargetPx,
                    animationSpec = tween(durationMillis = 120, easing = LinearOutSlowInEasing)
                )
                latestOnDismiss()
            }
        } catch (_: CancellationException) {
            // Do not use NonCancellable here. On ColorOS a cancelled predictive gesture can be
            // followed immediately by another one; a non-cancellable rebound held the handler
            // on the old gesture and left the player frozen on the system's last preview frame.
            scope.launch {
                dragDismissOffset.stop()
                if (gestureGeneration != predictiveGestureGeneration || dismissingPlayer) return@launch
                dragDismissOffset.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                )
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Transparent)
            .pointerInput(dismissingPlayer, dismissTargetPx, dismissThresholdPx) {
                var closeGesture = false
                var gestureOffset = 0f
                val velocityTracker = VelocityTracker()
                detectDragGestures(
                    onDragStart = { offset ->
                        closeGesture = !dismissingPlayer && offset.y <= topDragLimitPx
                        gestureOffset = dragDismissOffset.value
                        velocityTracker.resetTracking()
                        velocityTracker.addPosition(SystemClock.uptimeMillis(), offset)
                        if (closeGesture) {
                            scope.launch { dragDismissOffset.stop() }
                        }
                    },
                    onDrag = { change, dragAmount ->
                        if (!closeGesture) return@detectDragGestures
                        gestureOffset = (gestureOffset + if (dragAmount.y > 0f) {
                            dragAmount.y
                        } else {
                            dragAmount.y * 0.36f
                        }).coerceIn(0f, dismissTargetPx)
                        velocityTracker.addPosition(change.uptimeMillis, change.position)
                        scope.launch { dragDismissOffset.snapTo(gestureOffset) }
                        if (gestureOffset > 0f) change.consume()
                    },
                    onDragCancel = {
                        closeGesture = false
                        scope.launch {
                            dragDismissOffset.animateTo(
                                targetValue = 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                )
                            )
                        }
                    },
                    onDragEnd = {
                        if (!closeGesture) return@detectDragGestures
                        closeGesture = false
                        val velocityY = velocityTracker.calculateVelocity().y
                        scope.launch {
                            if (gestureOffset >= dismissThresholdPx || velocityY >= dismissVelocityThresholdPx) {
                                if (!dismissingPlayer) {
                                    dismissingPlayer = true
                                    dragDismissOffset.animateTo(
                                        targetValue = dismissTargetPx,
                                        animationSpec = tween(durationMillis = 260, easing = LinearOutSlowInEasing)
                                    )
                                    latestOnDismiss()
                                }
                            } else {
                                dragDismissOffset.animateTo(
                                    targetValue = 0f,
                                    animationSpec = spring(
                                        dampingRatio = Spring.DampingRatioNoBouncy,
                                        stiffness = Spring.StiffnessMediumLow
                                    )
                                )
                            }
                        }
                    }
                )
            }
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragDismissOffset.value
                    scaleX = 1f
                    scaleY = 1f
                    transformOrigin = TransformOrigin(0.5f, 0f)
                    alpha = 1f
                }
                .clip(
                    RoundedCornerShape(
                        topStart = dragCornerRadius,
                        topEnd = dragCornerRadius
                    )
                )
        ) {
            content(dismissingPlayer)
        }

        overlayContent()
    }
}
