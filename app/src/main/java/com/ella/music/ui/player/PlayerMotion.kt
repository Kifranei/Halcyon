package com.ella.music.ui.player

import androidx.compose.animation.core.CubicBezierEasing

/**
 * Shared motion for Apple Music cover → lyrics morph.
 */
internal object PlayerMotion {
    val CoverMorphEasing = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)
    const val CoverMorphDurationMs = 520

    fun lyricsCornerActionsVisible(chromeVisible: Boolean): Boolean = chromeVisible
}
