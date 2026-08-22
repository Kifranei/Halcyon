package com.ella.music.player

internal fun desktopLyricControlPanelVisible(
    locked: Boolean,
    statusBarMode: Boolean,
    controlsVisible: Boolean
): Boolean = !locked && !statusBarMode && controlsVisible

internal fun desktopLyricPassThroughTouches(statusBarMode: Boolean): Boolean = statusBarMode

internal fun desktopLyricUsesCompactWindow(locked: Boolean, statusBarMode: Boolean): Boolean =
    locked && !statusBarMode
