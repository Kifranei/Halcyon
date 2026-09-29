package com.ella.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp

/**
 * Shared state between the player's single [AudioVisualizer] (which owns the FFT capture and the
 * frame clock) and the cover artwork slots that draw the RawS "cover overlay" style inside the
 * album art. Only the visualizer writes the spectrum; slots read it in the draw phase.
 */
@Stable
internal class PlayerCoverVisualizerHost {
    val motion = RawSArtworkSpectrumMotion()

    /** True while the cover-overlay style is selected, enabled and allowed for this layout. */
    var active by mutableStateOf(false)
    var playing by mutableStateOf(false)
    var opacity by mutableFloatStateOf(1f)
    var blurRadiusDp by mutableIntStateOf(0)

    /** Bumped once per display frame; read only inside draw lambdas. */
    var frame by mutableIntStateOf(0)

    /** Number of cover slots currently on screen. Zero means: draw at the bottom instead. */
    var visibleCovers by mutableIntStateOf(0)
        private set

    fun coverVisibilityChanged(visible: Boolean) {
        visibleCovers = (visibleCovers + if (visible) 1 else -1).coerceAtLeast(0)
    }
}

/**
 * Null outside the player, or where the cover exists but is not visible (for example the
 * immersive cover hidden behind the lyric page), so slots there never claim the spectrum.
 */
internal val LocalPlayerCoverVisualizerHost = compositionLocalOf<PlayerCoverVisualizerHost?> { null }

/** Minimum on-screen share of a cover before it takes the spectrum from the bottom overlay. */
internal const val COVER_VISUALIZER_MIN_VISIBLE_FRACTION = 0.5f

internal fun coverVisualizerVisibleFraction(
    visibleWidth: Float,
    visibleHeight: Float,
    width: Int,
    height: Int
): Float {
    if (width <= 0 || height <= 0) return 0f
    return ((visibleWidth.coerceAtLeast(0f) * visibleHeight.coerceAtLeast(0f)) / (width.toFloat() * height))
        .coerceIn(0f, 1f)
}

/**
 * Draws the RawS foreground artwork spectrum over the enclosing cover [BoxScope]. Place it as the
 * last artwork child (before badges/buttons) inside the Box that already clips to the cover shape.
 */
@Composable
internal fun BoxScope.PlayerCoverVisualizerSlot() {
    val host = LocalPlayerCoverVisualizerHost.current ?: return
    if (!host.active) return
    val visibility = remember(host) { booleanArrayOf(false) }
    DisposableEffect(host) {
        onDispose {
            if (visibility[0]) {
                visibility[0] = false
                host.coverVisibilityChanged(false)
            }
        }
    }
    val blurRadius = host.blurRadiusDp
    Canvas(
        modifier = Modifier
            .matchParentSize()
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                val visible = coverVisualizerVisibleFraction(
                    bounds.width, bounds.height, coordinates.size.width, coordinates.size.height
                ) >= COVER_VISUALIZER_MIN_VISIBLE_FRACTION
                if (visible != visibility[0]) {
                    visibility[0] = visible
                    host.coverVisibilityChanged(visible)
                }
            }
            .graphicsLayer {
                // RawS widens the foreground layer by 8% so the outer columns bleed off the cover.
                scaleX = 1.08f
                alpha = (if (host.playing) 1f else 0.42f) * host.opacity.coerceIn(0f, 1f)
            }
            .then(if (blurRadius > 0) Modifier.blur(blurRadius.dp, BlurredEdgeTreatment.Unbounded) else Modifier)
    ) {
        @Suppress("UNUSED_VARIABLE") val frameDependency = host.frame
        drawRawSArtworkSpectrum(host.motion.levels, host.playing)
    }
}
