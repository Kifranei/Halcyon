package com.ella.music.ui.player

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize

/** Actual root-space endpoints, following RawS Music's surface/artwork handoff design.
 * Reference: RawSMusic Contributors (2024–2026), Apache-2.0.
 * Progress is already eased by the controller; gestures must not be eased a second time.
 */
internal class PlayerMorphState(val expansion: () -> Float) {
    var openingProgress by mutableStateOf<Float?>(null)
    var cancelOpening: () -> Unit = {}
    var onOpeningStart: () -> Unit = {}
    var onOpeningComplete: () -> Unit = {}
    var miniBounds by mutableStateOf<Rect?>(null)
    var miniCornerRadius = 0f
    var artworkSourceRadius = 0f
    var miniArtwork by mutableStateOf<Rect?>(null)
    var artworkBounds by mutableStateOf<Rect?>(null)
    var artworkLayer: androidx.compose.ui.graphics.layer.GraphicsLayer? = null
    var origin = Offset.Zero
    var viewportWidth = Float.MAX_VALUE
    val progress get() = (openingProgress ?: expansion()).coerceIn(0f, 1f)
}
internal val LocalPlayerMorphSurface = staticCompositionLocalOf { false }
internal val LocalPlayerMorph = staticCompositionLocalOf<PlayerMorphState?> { null }

internal fun morphRect(start: Rect, end: Rect, progress: Float): Rect {
    val t = progress.coerceIn(0f, 1f)
    fun mix(a: Float, b: Float) = a + (b - a) * t
    return Rect(mix(start.left, end.left), mix(start.top, end.top),
        mix(start.right, end.right), mix(start.bottom, end.bottom))
}

internal fun Modifier.miniMorphAnchor(artwork: Boolean = false, radius: Dp = 0.dp, circular: Boolean = false): Modifier = composed {
    val state = LocalPlayerMorph.current
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    if (state == null) this else onGloballyPositioned {
        val bounds = Rect(it.positionInRoot(), it.size.toSize())
        if (bounds.width > 0f && bounds.height > 0f) {
            if (artwork) {
                state.miniArtwork = bounds
                state.artworkSourceRadius = if (circular) bounds.height / 2f else radiusPx
            } else {
                state.miniBounds = bounds
                state.miniCornerRadius = radiusPx
            }
        }
    }.drawWithContent {
        // The morph draws the player's background and shared artwork only. Never capture
        // or replay the mini-player's labels/buttons into the expanding surface.
        // Keep its layout and input node alive, but give drawing ownership to the overlay.
        if (state.progress <= 0f) drawContent()
    }
}

internal fun Modifier.playerMorphArtwork(): Modifier = composed {
    val state = LocalPlayerMorph.current.takeIf { LocalPlayerMorphSurface.current }
    val layer = rememberGraphicsLayer()
    var bounds by remember { mutableStateOf(Rect.Zero) }
    DisposableEffect(layer) {
        onDispose { if (state?.artworkLayer === layer) state.artworkLayer = null }
    }
    if (state == null) this else onGloballyPositioned {
        bounds = Rect(it.positionInRoot(), it.size.toSize())
    }.drawWithContent {
        // Offscreen pager pages must not take ownership of the shared artwork.
        val visible = bounds.width > 0 && bounds.left >= state.origin.x - 1f &&
            bounds.top >= state.origin.y - 1f && bounds.center.x < state.origin.x +
            state.viewportWidth
        if (visible) {
            layer.record { this@drawWithContent.drawContent() }
            state.artworkLayer = layer
            state.artworkBounds = bounds
            if (state.progress >= 1f || state.progress <= 0f || state.miniArtwork == null) drawLayer(layer)
        } else {
            if (state.artworkLayer === layer) {
                state.artworkLayer = null
                state.artworkBounds = null
            }
            drawContent()
        }
    }
}

internal fun Modifier.playerMorphSurface(state: PlayerMorphState, floating: Boolean): Modifier =
    drawWithContent {
        state.viewportWidth = size.width
        val t = state.progress
        if (t <= 0f) return@drawWithContent
        if (t >= 1f) { drawContent(); return@drawWithContent }
        val origin = state.origin
        val start = state.miniBounds?.translate(-origin)
            ?: Rect(0f, size.height - 72.dp.toPx(), size.width, size.height)
        val surface = morphRect(start, Rect(Offset.Zero, size), t)
        val radius = (if (state.miniBounds != null) state.miniCornerRadius else if (floating) start.height / 2f else 0f) * (1f - t)
        val path = Path().apply { addRoundRect(RoundRect(surface, CornerRadius(radius))) }
        clipPath(path) {
            // Reveal ordinary controls inside the expanding surface. Keep their early frames
            // below the shared cover instead of overlapping it at the collapsed endpoint.
            translate(top = start.top.coerceAtLeast(0f) * 0.67f * (1f - t)) {
                this@drawWithContent.drawContent()
            }
            val source = state.miniArtwork?.translate(-origin)
            val target = state.artworkBounds?.translate(-origin)
            val layer = state.artworkLayer
            if (source != null && target != null && layer != null && layer.size.width > 0 && layer.size.height > 0) {
                val frame = morphRect(source, target, t)
                val artworkClip = Path().apply {
                    addRoundRect(RoundRect(frame, CornerRadius(state.artworkSourceRadius * (1f - t))))
                }
                clipPath(artworkClip) {
                    translate(frame.left, frame.top) {
                        scale(frame.width / layer.size.width, frame.height / layer.size.height, Offset.Zero) {
                            drawLayer(layer)
                        }
                    }
                }
            }
        }
    }
