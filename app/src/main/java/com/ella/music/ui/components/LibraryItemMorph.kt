// Copyright 2024–2026 RawSMusic Contributors, Apache-2.0.
// Adapted from RawS VirtualList's retained-holder source/target geometry to Compose layers.
package com.ella.music.ui.components

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.toSize

internal data class LibraryMorphKey(val layout: Int, val song: String, val part: String, val laneId: Boolean)
internal data class LibraryArtworkKey(val laneId: Boolean, val song: String)
internal class LibraryMorphEntry(val layer: GraphicsLayer) {
    var bounds by mutableStateOf(Rect.Zero)
    var recorded by mutableStateOf(false)
    var hasArtwork by mutableStateOf(false)
    var coordinates: LayoutCoordinates? = null
}

internal class LibraryMorphScene(
    val requested: () -> Boolean,
    val source: () -> Int,
    val target: () -> Int,
    val progress: () -> Float,
    val settledLane: () -> Boolean,
) {
    val entries = mutableStateMapOf<LibraryMorphKey, LibraryMorphEntry>()
    val artworkReady = mutableStateMapOf<LibraryArtworkKey, Boolean>()
    var prepared by mutableStateOf(false)
    fun active(): Boolean = requested() && prepared
    fun hasPreparedPair(): Boolean = entries.any { (key, entry) ->
        key.laneId == settledLane() && key.layout == source() && key.part == "cover" &&
            entry.recorded && counterpart(key) != null
    }
    var origin by mutableStateOf(Offset.Zero)
    var coordinates: LayoutCoordinates? = null
    fun localBounds(entry: LibraryMorphEntry): Rect {
        val fallback = entry.bounds.translate(-origin)
        val root = coordinates?.takeIf { it.isAttached } ?: return fallback
        val child = entry.coordinates?.takeIf { it.isAttached } ?: return fallback
        return Rect(root.localPositionOf(child, Offset.Zero), entry.bounds.size)
    }
    fun counterpart(key: LibraryMorphKey): LibraryMorphEntry? = entries[
        key.copy(layout = if (key.layout == source()) target() else source(), laneId = !key.laneId)
    ]?.takeIf { it.recorded && it.bounds.width > 0f }
    fun previousArtwork(key: LibraryMorphKey): LibraryMorphEntry? = entries.entries.firstOrNull {
        it.key.laneId != key.laneId && it.key.song == key.song && it.key.part == "cover" &&
            it.value.recorded && it.value.hasArtwork
    }?.value
}

internal data class LibraryMorphLane(val scene: LibraryMorphScene, val layout: Int, val id: Boolean)
internal val LocalLibraryMorphLane = staticCompositionLocalOf<LibraryMorphLane?> { null }

/** Only library endpoints capture; SongItem elsewhere pays no layer/recording cost. */
internal fun Modifier.libraryMorphPart(song: String, part: String): Modifier = composed {
    val lane = LocalLibraryMorphLane.current
    if (lane == null) this else {
        val layer = rememberGraphicsLayer()
        val entry = remember(layer) { LibraryMorphEntry(layer) }
        val key = LibraryMorphKey(lane.layout, song, part, lane.id)
        DisposableEffect(lane.scene, key, entry) {
            lane.scene.entries[key] = entry
            onDispose { if (lane.scene.entries[key] === entry) lane.scene.entries.remove(key) }
        }
        onGloballyPositioned {
            entry.coordinates = it
            entry.bounds = Rect(it.positionInRoot(), it.size.toSize())
        }.drawWithContent {
            val ready = lane.scene.artworkReady[LibraryArtworkKey(lane.id, song)] == true
            // A pending replacement cannot clear pixels already admitted for this holder.
            if ((!lane.scene.requested() || !entry.recorded || (part == "cover" && ready && !entry.hasArtwork)) &&
                (part != "cover" || ready || !entry.hasArtwork)) {
                layer.record { this@drawWithContent.drawContent() }
                entry.recorded = true
                if (part == "cover" && ready) entry.hasArtwork = true
            }
            if (!lane.scene.active() || lane.scene.counterpart(key) == null) {
                val fallback = if (part == "cover" && !entry.hasArtwork) lane.scene.previousArtwork(key) else null
                val visibleLayer = fallback?.layer ?: layer
                if (visibleLayer.size.width > 0 && visibleLayer.size.height > 0) {
                    scale(size.width / visibleLayer.size.width, size.height / visibleLayer.size.height, Offset.Zero) {
                        drawLayer(visibleLayer)
                    }
                }
            }
        }
    }
}

internal fun libraryMorphRect(a: Rect, b: Rect, progress: Float): Rect {
    val t = progress.coerceIn(0f, 1f)
    fun mix(x: Float, y: Float) = x + (y - x) * t
    return Rect(mix(a.left, b.left), mix(a.top, b.top), mix(a.right, b.right), mix(a.bottom, b.bottom))
}

/** Cover bounds move and resize together; text moves without stretching its glyphs. */
internal fun Modifier.libraryMorphSurface(scene: LibraryMorphScene): Modifier =
    onGloballyPositioned { scene.coordinates = it; scene.origin = it.positionInRoot() }.drawWithContent {
        drawContent()
        if (!scene.active()) return@drawWithContent
        val t = scene.progress().coerceIn(0f, 1f)
        clipRect {
            scene.entries.forEach { (key, source) ->
                if (key.layout != scene.source() || key.laneId != scene.settledLane() || !source.recorded) return@forEach
                val target = scene.counterpart(key) ?: return@forEach
                val frame = libraryMorphRect(scene.localBounds(source), scene.localBounds(target), t)
                if (frame.bottom < 0f || frame.top > size.height) return@forEach
                fun drawEntry(entry: LibraryMorphEntry, opacity: Float) {
                    if (opacity <= 0f || entry.layer.size.width <= 0 || entry.layer.size.height <= 0) return
                    translate(frame.left, frame.top) {
                        clipRect(0f, 0f, frame.width, frame.height) {
                            val canvas = drawContext.canvas
                            canvas.saveLayer(Rect(Offset.Zero, frame.size), Paint().apply { alpha = opacity })
                            if (key.part == "cover") {
                                scale(frame.width / entry.layer.size.width, frame.height / entry.layer.size.height, Offset.Zero) {
                                    drawLayer(entry.layer)
                                }
                            } else drawLayer(entry.layer)
                            canvas.restore()
                        }
                    }
                }
                if (key.part == "cover") {
                    // One accepted artwork actor throughout the pinch; never blend a cold
                    // destination request/placeholder over the already-visible source cover.
                    drawEntry(if (source.hasArtwork || !target.hasArtwork) source else target, 1f)
                } else {
                    drawEntry(source, 1f - t)
                    drawEntry(target, t)
                }
            }
        }
    }
