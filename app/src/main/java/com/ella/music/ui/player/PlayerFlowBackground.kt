package com.ella.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.graphics.Brush
import kotlinx.coroutines.delay

internal fun playerContentSurfaceBrush(
    palette: PlayerPalette,
    flowEffectMode: Int
): Brush {
    return Brush.verticalGradient(
        colorStops = arrayOf(
            // This joins the immersive cover's bottom gradient at the exact same color.
            0.0f to palette.middle,
            0.16f to palette.middle.copy(alpha = 0.94f),
            1.0f to palette.middle.copy(alpha = 0.90f)
        )
    )
}

@Composable
internal fun rememberSharedFlowProgress(
    durationMillis: Int,
    animate: Boolean,
    reverse: Boolean = false,
    fallback: Float = 0f,
    // Keep the animation clock on the display frame cadence. These backgrounds are intentionally
    // soft, but dropping updates to a timer cadence makes the flow visibly step on 60/120 Hz
    // devices. Callers may still opt into a cap when a future surface is explicitly static.
    frameIntervalMs: Long = 0L
): Float {
    // Freeze the animation clock whenever the player surface is slid off-screen but still resident.
    val effectiveAnimate = animate && LocalPlayerSurfaceActive.current
    var frameTimeNs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(durationMillis, effectiveAnimate, frameIntervalMs) {
        if (!effectiveAnimate) {
            frameTimeNs = 0L
            return@LaunchedEffect
        }
        while (true) {
            frameTimeNs = withFrameNanos { it }
            if (frameIntervalMs > 0L) delay(frameIntervalMs)
        }
    }
    if (!effectiveAnimate || frameTimeNs == 0L) return fallback
    val safeDuration = durationMillis.coerceAtLeast(1)
    val elapsedMs = frameTimeNs / 1_000_000L
    val cycle = elapsedMs / safeDuration
    val fraction = (elapsedMs % safeDuration).toFloat() / safeDuration.toFloat()
    return if (reverse && cycle % 2L == 1L) 1f - fraction else fraction
}
