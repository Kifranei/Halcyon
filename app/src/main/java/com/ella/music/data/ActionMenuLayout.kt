package com.ella.music.data

data class ActionMenuLayout(
    val order: List<String>,
    val hidden: Set<String>
) {
    fun visibleIds(defaultOrder: List<String>): List<String> = normalized(defaultOrder).order.filterNot(hidden::contains)

    fun normalized(defaultOrder: List<String>): ActionMenuLayout {
        val known = defaultOrder.toSet()
        val savedOrder = order.filter { it in known }
        val normalizedOrder = (savedOrder + defaultOrder).distinct().toMutableList().apply {
            if (ActionMenuIds.CASTING in defaultOrder && ActionMenuIds.CASTING !in savedOrder) {
                remove(ActionMenuIds.CASTING)
                val audioOutputIndex = indexOf(ActionMenuIds.AUDIO_OUTPUT)
                add(if (audioOutputIndex >= 0) audioOutputIndex + 1 else size, ActionMenuIds.CASTING)
            }
        }
        return ActionMenuLayout(normalizedOrder, hidden.intersect(known))
    }

    fun serialize(): String = order.joinToString(",") + ";" + hidden.sorted().joinToString(",")

    companion object {
        fun parse(raw: String, defaultOrder: List<String>): ActionMenuLayout {
            val sections = raw.split(';', limit = 2)
            val order = sections.getOrNull(0).orEmpty().split(',').filter(String::isNotBlank)
            val hidden = sections.getOrNull(1).orEmpty().split(',').filter(String::isNotBlank).toSet()
            return ActionMenuLayout(order, hidden).normalized(defaultOrder)
        }
    }
}

object ActionMenuIds {
    const val ADD_TO_PLAYLIST = "add_to_playlist"
    const val ADD_TO_QUEUE = "add_to_queue"
    const val PLAY_NEXT = "play_next"
    const val SHARE = "share"
    const val SPECTRUM = "spectrum"
    const val AI = "ai"
    const val INFO = "info"
    const val RATING = "rating"
    const val EDIT_TAGS = "edit_tags"
    const val LYRIC_TIMING = "lyric_timing"
    const val AUDIO_TOOLS = "audio_tools"
    const val REMOVE_FROM_PLAYLIST = "remove_from_playlist"
    const val DELETE = "delete"
    const val AUDIO_OUTPUT = "audio_output"
    const val CASTING = "casting"
    const val AB_REPEAT = "ab_repeat"
    const val REMOTE_QUALITY = "remote_quality"
    const val LANDSCAPE = "landscape"
    const val LYRICS_DISPLAY = "lyrics_display"
    const val DYNAMIC_COVER = "dynamic_cover"
    const val VISUALIZER = "visualizer"
    const val ONLINE_LYRICS = "online_lyrics"
    const val LYRIC_OFFSET = "lyric_offset"
    const val KEEP_SCREEN_ON = "keep_screen_on"
    const val DOWNLOAD = "download"

    val listDefaults = listOf(
        ADD_TO_PLAYLIST, ADD_TO_QUEUE, PLAY_NEXT, SHARE, SPECTRUM, AI, INFO, RATING,
        EDIT_TAGS, LYRIC_TIMING, AUDIO_TOOLS, REMOVE_FROM_PLAYLIST, DELETE
    )

    val playerDefaults = listOf(
        ADD_TO_QUEUE, SHARE, AI, INFO, AUDIO_OUTPUT, CASTING, AB_REPEAT, REMOTE_QUALITY, LANDSCAPE,
        LYRICS_DISPLAY, SPECTRUM, RATING, DYNAMIC_COVER, VISUALIZER, EDIT_TAGS,
        LYRIC_TIMING, ONLINE_LYRICS, LYRIC_OFFSET, KEEP_SCREEN_ON, DOWNLOAD, DELETE
    )
}
