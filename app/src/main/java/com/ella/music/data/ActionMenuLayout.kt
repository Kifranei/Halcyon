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

    const val SONG_INFO_TITLE = "song_info_title"
    const val SONG_INFO_ARTIST = "song_info_artist"
    const val SONG_INFO_ALBUM = "song_info_album"
    const val SONG_INFO_ALBUM_ARTIST = "song_info_album_artist"
    const val SONG_INFO_GENRE = "song_info_genre"
    const val SONG_INFO_YEAR = "song_info_year"
    const val SONG_INFO_COMPOSER = "song_info_composer"
    const val SONG_INFO_ARRANGER = "song_info_arranger"
    const val SONG_INFO_LYRICIST = "song_info_lyricist"
    const val SONG_INFO_COMMENT = "song_info_comment"
    const val SONG_INFO_NETEASE = "song_info_netease"
    const val SONG_INFO_FORMAT = "song_info_format"
    const val SONG_INFO_BITRATE = "song_info_bitrate"
    const val SONG_INFO_DURATION = "song_info_duration"
    const val SONG_INFO_PLAY_COUNT = "song_info_play_count"
    const val SONG_INFO_LISTENED = "song_info_listened"
    const val SONG_INFO_LAST_PLAYED = "song_info_last_played"
    const val SONG_INFO_SIZE = "song_info_size"
    const val SONG_INFO_MODIFIED = "song_info_modified"
    const val SONG_INFO_ADDED = "song_info_added"
    const val SONG_INFO_FILE_NAME = "song_info_file_name"
    const val SONG_INFO_PATH = "song_info_path"
    const val SONG_INFO_DIRECTORY = "song_info_directory"
    const val SONG_INFO_MEDIA_INFO = "song_info_media_info"

    val songInfoDefaults = listOf(
        SONG_INFO_TITLE, SONG_INFO_ARTIST, SONG_INFO_ALBUM, SONG_INFO_ALBUM_ARTIST, SONG_INFO_GENRE,
        SONG_INFO_YEAR, SONG_INFO_COMPOSER, SONG_INFO_ARRANGER, SONG_INFO_LYRICIST, SONG_INFO_COMMENT,
        SONG_INFO_NETEASE, SONG_INFO_FORMAT, SONG_INFO_BITRATE, SONG_INFO_DURATION, SONG_INFO_PLAY_COUNT,
        SONG_INFO_LISTENED, SONG_INFO_LAST_PLAYED, SONG_INFO_SIZE, SONG_INFO_MODIFIED, SONG_INFO_ADDED,
        SONG_INFO_FILE_NAME, SONG_INFO_PATH, SONG_INFO_DIRECTORY, SONG_INFO_MEDIA_INFO
    )

    const val QUEUE_LOCK = "queue_lock"
    const val QUEUE_SHUFFLE = "queue_shuffle"
    const val QUEUE_ADD_PLAYLIST = "queue_add_playlist"
    const val QUEUE_LOCATE = "queue_locate"
    const val QUEUE_LIBRARY_SOURCE = "queue_library_source"
    const val QUEUE_CLEAR = "queue_clear"

    val queueToolbarDefaults = listOf(
        QUEUE_LOCK, QUEUE_SHUFFLE, QUEUE_ADD_PLAYLIST, QUEUE_LOCATE, QUEUE_CLEAR
    )
}
