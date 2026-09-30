package com.ella.music.data

internal val VIDEO_PLAYBACK_SPEEDS = listOf(0.5f, 1f, 1.25f, 1.5f, 2f, 2.5f, 3f, 3.5f, 4f, 5f)
internal fun normalizeVideoHoldSpeedPercent(value: Int): Int = if (value in 50..500) value else 200
internal fun videoSpeedLabel(value: Float): String = value.toString().removeSuffix(".0") + "x"

/** Selected speed is preserved while a pointer temporarily overrides it. */
internal data class VideoPlaybackSpeedState(val selected: Float = 1f, val held: Float? = null) {
    val effective: Float get() = held ?: selected
    fun select(value: Float) = copy(selected = VIDEO_PLAYBACK_SPEEDS.firstOrNull { it == value } ?: 1f)
    fun hold(value: Float): VideoPlaybackSpeedState {
        val valid = if (value.isFinite() && value in 0.5f..5f) value else 2f
        return copy(held = valid)
    }
    fun release() = copy(held = null)
}
