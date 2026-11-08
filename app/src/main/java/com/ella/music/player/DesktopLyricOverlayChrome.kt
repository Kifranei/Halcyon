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

/**
 * The floating lyric always occupies the full configured width so long lines can scroll
 * horizontally inside a stable viewport (#727). A locked window is transparent and passes
 * touches through anyway, so wrapping it to the content would only buy position jitter.
 */
internal fun desktopLyricUsesCompactWindow(locked: Boolean, statusBarMode: Boolean): Boolean =
    false
