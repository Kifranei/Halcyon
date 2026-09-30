// Adapted from RawS Music, Copyright 2024–2026 RawSMusic Contributors, Apache-2.0.
package com.ella.music.ui.player

import kotlin.math.abs

internal enum class PlayerTimelineGestureAxis {
    Undecided,
    HorizontalSeek,
    VerticalScene,
}

/**
 * Resolves ownership between the full-screen PLAYER vertical scene gesture and a timeline seek.
 * The timeline must not become a seek owner on DOWN: a vertical drag that starts on the progress
 * area belongs to PLAYER -> LYRIC / PLAYER -> MAIN just like the rest of the screen.
 */
internal fun resolvePlayerTimelineGestureAxis(
    dx: Float,
    dy: Float,
    touchSlop: Float,
    dominance: Float = 1.2f,
): PlayerTimelineGestureAxis {
    val absX = abs(dx)
    val absY = abs(dy)
    if (absX <= touchSlop && absY <= touchSlop) {
        return PlayerTimelineGestureAxis.Undecided
    }
    return when {
        absX > absY * dominance -> PlayerTimelineGestureAxis.HorizontalSeek
        absY > absX * dominance -> PlayerTimelineGestureAxis.VerticalScene
        else -> PlayerTimelineGestureAxis.Undecided
    }
}

/**
 * Maps a tap on the fixed-width seconds timeline to the second shown under that x position.
 * The playhead is always the physical centre: taps left seek backwards and taps right seek forward.
 */
internal fun resolveSecondTimelineTapSecond(
    currentSecond: Float,
    tapX: Float,
    widthPx: Float,
    barStepPx: Float,
    totalSeconds: Float,
): Float {
    if (widthPx <= 0f || barStepPx <= 0f || totalSeconds <= 0f) return currentSecond.coerceAtLeast(0f)
    val deltaSeconds = (tapX - widthPx * 0.5f) / barStepPx
    return (currentSecond + deltaSeconds).coerceIn(0f, totalSeconds)
}
