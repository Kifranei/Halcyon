package com.ella.music.ui.poster

import androidx.compose.runtime.*

/** A monotonically increasing request survives source/filter loading and viewport measurement. */
@Composable
internal fun PosterWallPositioning(
    wall: PosterWallState, items: List<PosterWallItem>, geometry: PosterWallGeometry,
    currentIndex: Int, locateRequest: Int
) {
    val scope = rememberCoroutineScope()
    var handledRequest by remember { mutableIntStateOf(0) }
    var previousGeometry by remember(wall) { mutableStateOf<PosterWallGeometry?>(null) }
    LaunchedEffect(wall, items, geometry, wall.viewport, currentIndex, locateRequest) {
        if (items.isEmpty() || wall.viewport.x <= 0f || wall.viewport.y <= 0f) return@LaunchedEffect
        if (geometry !== previousGeometry) {
            wall.stop()
            wall.fit(geometry)
            previousGeometry = geometry
        }
        if (!wall.initialized || locateRequest > handledRequest) {
            wall.locate(currentIndex.takeIf { it in items.indices } ?: 0, geometry, scope, animate = wall.initialized)
            // Do not reset the request that keys this effect: doing so restarted the effect and
            // stopped the camera animation on the very next frame.
            handledRequest = locateRequest
        }
    }
}
