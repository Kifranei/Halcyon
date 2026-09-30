package com.ella.music.player

internal fun desktopLyricControlPanelVisible(
    locked: Boolean,
    statusBarMode: Boolean,
    controlsVisible: Boolean
): Boolean = !locked && !statusBarMode && controlsVisible

// Locked desktop and status-bar lyrics pass touches through the entire window.
// Android 12+ requires its window opacity limit for touches reaching other apps.
internal fun desktopLyricPassThroughTouches(statusBarMode: Boolean, locked: Boolean = false): Boolean = statusBarMode || locked

internal fun desktopLyricWindowAlpha(
    passThroughTouches: Boolean,
    supportsTouchOpacityLimit: Boolean,
    maximumTouchOpacity: Float
): Float = if (passThroughTouches && supportsTouchOpacityLimit) maximumTouchOpacity.coerceIn(0f, 1f) else 1f

internal fun desktopLyricUsesCompactWindow(locked: Boolean, statusBarMode: Boolean): Boolean =
    locked && !statusBarMode
