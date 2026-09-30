package com.ella.music.ui.poster

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.DecayAnimationSpec
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.ui.geometry.Offset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Stable
internal class PosterWallState(initial: PosterCamera = PosterCamera()) {
    var camera by mutableStateOf(initial)
        private set
    var viewport by mutableStateOf(Offset.Zero)
        private set
    var initialized = false
    private var motion: Job? = null

    fun resize(width: Float, height: Float, geometry: PosterWallGeometry) {
        val nextViewport = Offset(width, height)
        if (nextViewport == viewport) return
        stop()
        if (initialized && viewport.x > 0f && viewport.y > 0f) {
            camera = camera.copy(x = camera.x + (width - viewport.x) / 2, y = camera.y + (height - viewport.y) / 2)
        }
        viewport = nextViewport
        camera = camera.clamped(geometry, width, height)
    }

    fun stop() { motion?.cancel(); motion = null }

    fun transform(pan: Offset, zoom: Float, focus: Offset, geometry: PosterWallGeometry) {
        camera = camera.transformed(pan.x, pan.y, zoom, focus.x, focus.y).clamped(geometry, viewport.x, viewport.y)
    }

    fun locate(index: Int, geometry: PosterWallGeometry, scope: CoroutineScope, animate: Boolean = true) {
        if (index !in 0 until geometry.count || viewport.x <= 0 || viewport.y <= 0) return
        stop()
        initialized = true
        val rect = geometry.nearestRect(index, camera.bounds(viewport.x, viewport.y))
        val destination = camera.centeredOn(rect, viewport.x, viewport.y).let {
            if (geometry.infinite) it else it.clamped(geometry, viewport.x, viewport.y)
        }
        if (!animate) { camera = destination.clamped(geometry, viewport.x, viewport.y); return }
        motion = scope.launch {
            Animatable(Offset(camera.x, camera.y), Offset.VectorConverter).animateTo(
                Offset(destination.x, destination.y), spring(stiffness = 240f, dampingRatio = 0.9f)
            ) { camera = camera.copy(x = value.x, y = value.y).clamped(geometry, viewport.x, viewport.y) }
        }
    }

    fun fling(velocity: Offset, geometry: PosterWallGeometry, decay: DecayAnimationSpec<Offset>, scope: CoroutineScope) {
        stop()
        motion = scope.launch {
            Animatable(Offset(camera.x, camera.y), Offset.VectorConverter).animateDecay(velocity, decay) {
                val desired = camera.copy(x = value.x, y = value.y)
                camera = desired.clamped(geometry, viewport.x, viewport.y)
                if (!geometry.infinite && camera != desired) throw CancellationException("Poster wall reached its bounds")
            }
        }
    }

    fun fit(geometry: PosterWallGeometry) {
        camera = camera.clamped(geometry, viewport.x, viewport.y)
    }

    /** Keep a remote selection visible, including its physical copy after an infinite rebase. */
    fun reveal(placement: PosterPlacement, geometry: PosterWallGeometry): PosterPlacement {
        stop()
        val rect = placement.rect
        fun pan(start: Float, end: Float, extent: Float): Float = when {
            end - start > extent - 48f -> extent / 2f - (start + end) / 2f
            start < 24f -> 24f - start
            end > extent - 24f -> extent - 24f - end
            else -> 0f
        }
        val before = camera.copy(
            x = camera.x + pan(camera.x + rect.x * camera.scale, camera.x + rect.right * camera.scale, viewport.x),
            y = camera.y + pan(camera.y + rect.y * camera.scale, camera.y + rect.bottom * camera.scale, viewport.y))
        camera = before.clamped(geometry, viewport.x, viewport.y)
        val adjusted = if (geometry.infinite) rect.copy(x = rect.x + (before.x - camera.x) / camera.scale,
            y = rect.y + (before.y - camera.y) / camera.scale) else rect
        return geometry.visiblePosters(adjusted, overscan = 0f).firstOrNull {
            it.index == placement.index && kotlin.math.abs(it.rect.centerX - adjusted.centerX) < 0.1f &&
                kotlin.math.abs(it.rect.centerY - adjusted.centerY) < 0.1f
        } ?: placement.copy(rect = adjusted)
    }

    companion object {
        val Saver = listSaver<PosterWallState, Float>(
            save = { listOf(it.camera.x, it.camera.y, it.camera.scale, it.viewport.x, it.viewport.y, if (it.initialized) 1f else 0f) },
            restore = { saved -> PosterWallState(PosterCamera(saved[0], saved[1], saved[2])).apply {
                viewport = Offset(saved[3], saved[4]); initialized = saved[5] == 1f
            } }
        )
    }
}
