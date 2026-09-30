package com.ella.music.ui.poster

import androidx.compose.animation.rememberSplineBasedDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.input.key.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Constraints
import com.ella.music.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.max

internal data class PosterWallItem(val song: Song, val sourceIndex: Int, val key: String)

@Composable
internal fun PosterWallCanvas(
    items: List<PosterWallItem>, geometry: PosterWallGeometry, state: PosterWallState,
    currentIndex: Int, artwork: @Composable (Song, Modifier, Int) -> Unit, scope: CoroutineScope,
    onExpand: (Int, PosterRect) -> Unit, onMore: (Song) -> Unit,
    remoteEnabled: Boolean = true, fullscreen: Boolean = false, searching: Boolean = false
) {
    val density = LocalDensity.current.density
    val decay = rememberSplineBasedDecay<Offset>()
    val inputMode = LocalInputModeManager.current
    val television = LocalConfiguration.current.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK ==
        android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
    val remoteFocus = remember { FocusRequester() }
    var hasFocus by remember { mutableStateOf(false) }
    var selection by remember(items, geometry) { mutableStateOf<PosterPlacement?>(null) }
    var confirmHeld by remember { mutableStateOf(false) }
    fun initialSelection(): PosterPlacement? {
        val bounds = state.camera.bounds(state.viewport.x, state.viewport.y)
        val shown = geometry.visiblePosters(bounds, overscan = 0f)
        return shown.firstOrNull { it.index == currentIndex } ?: shown.minByOrNull {
            val dx = it.rect.centerX - bounds.centerX
            val dy = it.rect.centerY - bounds.centerY
            dx * dx + dy * dy
        }
    }
    fun activeSelection() = selection?.takeIf { it.rect.intersects(state.camera.bounds(state.viewport.x, state.viewport.y)) }
        ?: initialSelection()
    LaunchedEffect(remoteEnabled, inputMode.inputMode, television, fullscreen, searching) {
        if (remoteEnabled && !searching && television) inputMode.requestInputMode(InputMode.Keyboard)
        if (remoteEnabled && !searching && (television || inputMode.inputMode == InputMode.Keyboard)) remoteFocus.requestFocus()
    }
    val visible = remember(items, geometry, state) {
        // The derived list changes only when a card crosses the overscan boundary. Panning
        // updates placement/drawing without recomposing every poster on every finger sample.
        derivedStateOf { geometry.visiblePosters(state.camera.bounds(state.viewport.x, state.viewport.y)) }
    }.value
    // Capture this composition's list for both content and layout. A delegated State getter
    // would reread it during measure/place: resize() can already have changed the viewport and
    // camera there, while the measurables still belong to the previous visible list.
    Canvas(Modifier.fillMaxSize()) {
        val stride = 32 * density * state.camera.scale
        if (stride > 0f) {
            val phaseX = (state.camera.x * density % stride + stride) % stride
            val phaseY = (state.camera.y * density % stride + stride) % stride
            var x = phaseX
            while (x < size.width) {
                var y = phaseY
                while (y < size.height) { drawCircle(Color.White.copy(alpha = 0.045f), 0.7f * density, Offset(x, y)); y += stride }
                x += stride
            }
        }
    }
    Layout(
        modifier = Modifier.fillMaxSize().clipToBounds().testTag("poster-wall-canvas").focusRequester(remoteFocus)
            .onFocusChanged { hasFocus = it.hasFocus }
            .onPreviewKeyEvent { event ->
                if (!remoteEnabled) return@onPreviewKeyEvent false
                val direction = when (event.key) {
                    Key.DirectionLeft -> PosterDirection.Left
                    Key.DirectionRight -> PosterDirection.Right
                    Key.DirectionUp -> PosterDirection.Up
                    Key.DirectionDown -> PosterDirection.Down
                    else -> null
                }
                if (direction != null) {
                    if (event.type == KeyEventType.KeyUp) return@onPreviewKeyEvent true
                    val from = activeSelection() ?: return@onPreviewKeyEvent false
                    val next = nextPosterPlacement(from, geometry, direction) ?: return@onPreviewKeyEvent false
                    selection = state.reveal(next, geometry)
                    true
                } else if (event.key in listOf(Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Menu)) {
                    val selected = activeSelection() ?: return@onPreviewKeyEvent false
                    if (event.type == KeyEventType.KeyDown) {
                        confirmHeld = event.nativeKeyEvent.repeatCount > 0 || event.nativeKeyEvent.isLongPress
                    }
                    if (event.type == KeyEventType.KeyUp) {
                        val item = items[selected.index]
                        if (event.key == Key.Menu || confirmHeld) onMore(item.song) else {
                            val camera = state.camera
                            val rect = selected.rect
                            onExpand(selected.index, PosterRect(camera.x + rect.x * camera.scale, camera.y + rect.y * camera.scale,
                                rect.width * camera.scale, rect.height * camera.scale))
                        }
                        confirmHeld = false
                    }
                    true
                } else false
            }.focusable(enabled = remoteEnabled).onSizeChanged {
            state.resize(it.width / density, it.height / density, geometry)
        }.pointerInput(state, geometry, density) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                selection = null
                state.stop()
                val tracker = VelocityTracker().apply { addPosition(down.uptimeMillis, down.position) }
                var accumulatedPan = Offset.Zero
                var accumulatedZoom = 1f
                var dragging = false
                var zoomed = false
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    if (event.changes.any { it.isConsumed }) break
                    val pan = event.calculatePan()
                    val zoom = event.calculateZoom()
                    accumulatedPan += pan
                    accumulatedZoom *= zoom
                    val multiplePointers = event.changes.count { it.pressed } > 1
                    if (!dragging) {
                        dragging = accumulatedPan.getDistance() > viewConfiguration.touchSlop ||
                            abs(1 - accumulatedZoom) * 160 * density > viewConfiguration.touchSlop
                    }
                    if (dragging) {
                        zoomed = zoomed || multiplePointers
                        val centroid = event.calculateCentroid(useCurrent = false)
                        if (centroid.isSpecified) state.transform(pan / density, zoom, centroid / density, geometry)
                        event.changes.forEach { it.consume() }
                    }
                    event.changes.firstOrNull { it.id == down.id }?.let { tracker.addPosition(it.uptimeMillis, it.position) }
                } while (event.changes.any { it.pressed })
                if (dragging && !zoomed) {
                    val velocity = tracker.calculateVelocity()
                    state.fling(Offset(velocity.x / density, velocity.y / density), geometry, decay, scope)
                }
            }
        },
        content = {
            val selectedKey = if (remoteEnabled && hasFocus) activeSelection()?.key else null
            visible.forEach { placement ->
                val index = placement.index
                val item = items[index]
                key(item.key, placement.key) {
                    val rect = placement.rect
                    val coverSize by remember(state, rect, density) {
                        derivedStateOf { posterCoverSize(max(rect.width, rect.height) * density * state.camera.scale) }
                    }
                    PosterTile(item.song, rect, index == currentIndex,
                        selected = selectedKey == placement.key,
                        artwork = { song, modifier -> artwork(song, modifier, coverSize) },
                        onExpand = {
                            val camera = state.camera
                            onExpand(index, PosterRect(camera.x + rect.x * camera.scale, camera.y + rect.y * camera.scale, rect.width * camera.scale, rect.height * camera.scale))
                        }, onMore = { onMore(item.song) })
                }
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.mapIndexed { position, measurable ->
            val rect = visible[position].rect
            measurable.measure(Constraints.fixed((rect.width * density).roundToInt(), (rect.height * density).roundToInt()))
        }
        layout(constraints.maxWidth, constraints.maxHeight) {
            val camera = state.camera
            placeables.forEachIndexed { position, placeable ->
                val rect = visible[position].rect
                placeable.placeWithLayer(((camera.x + rect.x * camera.scale) * density).roundToInt(), ((camera.y + rect.y * camera.scale) * density).roundToInt()) {
                    scaleX = camera.scale; scaleY = camera.scale; transformOrigin = TransformOrigin(0f, 0f)
                }
            }
        }
    }
}

internal fun posterCoverSize(pixels: Float): Int = when {
    pixels <= 192 -> 192
    pixels <= 320 -> 320
    pixels <= 512 -> 512
    else -> 768
}
