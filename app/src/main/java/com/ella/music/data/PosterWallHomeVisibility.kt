package com.ella.music.data

/** Introduce the new home entry once, while preserving an explicit hide/move afterwards. */
internal fun resolvePosterWallHomeFeatureItems(features: String, shortcuts: String, configured: Boolean, hidden: String): String {
    fun String.ids() = split(',').map { it.trim() }.filter { it.isNotBlank() }
    if (configured || "poster_wall" in features.ids() || "poster_wall" in shortcuts.ids() || "poster_wall" in hidden.ids()) return features
    return (features.ids() + "poster_wall").distinct().joinToString(",")
}
