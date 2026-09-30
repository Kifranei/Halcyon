package com.ella.music.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.ActionMenuIds
import com.ella.music.data.ActionMenuLayout
import com.ella.music.ui.components.ReorderableSelectionItem
import com.ella.music.ui.components.ReorderableSelectionSheet

internal fun actionMenuStringRes(id: String): Int = when (id) {
    ActionMenuIds.ADD_TO_PLAYLIST -> R.string.song_more_add_to_playlist
    ActionMenuIds.ADD_TO_QUEUE -> R.string.common_add_to_queue
    ActionMenuIds.PLAY_NEXT -> R.string.song_more_play_next
    ActionMenuIds.SHARE -> R.string.common_share
    ActionMenuIds.SPECTRUM -> R.string.song_more_view_spectrum
    ActionMenuIds.AI -> R.string.song_more_ai_title
    ActionMenuIds.INFO -> R.string.song_more_view_song_info
    ActionMenuIds.RATING -> R.string.song_more_set_rating
    ActionMenuIds.EDIT_TAGS -> R.string.song_more_edit_tags_title
    ActionMenuIds.LYRIC_TIMING -> R.string.song_more_lyric_timing
    ActionMenuIds.AUDIO_TOOLS -> R.string.song_more_audio_tools
    ActionMenuIds.REMOVE_FROM_PLAYLIST -> R.string.playlist_remove_song_title
    ActionMenuIds.DELETE -> R.string.song_more_delete_permanently
    ActionMenuIds.AUDIO_OUTPUT -> R.string.player_audio_output_info
    ActionMenuIds.CASTING -> R.string.casting_devices_title
    ActionMenuIds.AB_REPEAT -> R.string.player_repeat_mode
    ActionMenuIds.REMOTE_QUALITY -> R.string.settings_action_menu_remote_quality
    ActionMenuIds.LANDSCAPE -> R.string.player_landscape_lyrics
    ActionMenuIds.POSTER_WALL -> R.string.poster_wall_title
    ActionMenuIds.LYRICS_DISPLAY -> R.string.player_lyrics_display
    ActionMenuIds.DYNAMIC_COVER -> R.string.player_match_dynamic_cover
    ActionMenuIds.VISUALIZER -> R.string.player_visualizer_settings
    ActionMenuIds.ONLINE_LYRICS -> R.string.player_match_online_lyrics
    ActionMenuIds.LYRIC_OFFSET -> R.string.player_lyric_offset
    ActionMenuIds.TIMER -> R.string.player_sleep_timer
    ActionMenuIds.KEEP_SCREEN_ON -> R.string.settings_action_menu_keep_screen_on
    ActionMenuIds.SPEED -> R.string.player_speed_pitch
    ActionMenuIds.EQUALIZER -> R.string.player_equalizer
    ActionMenuIds.DOWNLOAD -> R.string.netease_download_song
    ActionMenuIds.DOWNLOAD_MV -> R.string.netease_download_mv
    ActionMenuIds.VIEW_MV -> R.string.player_view_music_video
    ActionMenuIds.SONG_COMMENTS -> R.string.player_view_song_comments
    ActionMenuIds.MV_COMMENTS -> R.string.player_view_mv_comments
    com.ella.music.ui.player.PlayerExtraActionIds.LYRIC_SHARE -> R.string.player_lyric_share
    ActionMenuIds.REMOVE_FROM_RECENT_PLAYBACK -> R.string.recent_playback_remove_from_recent
    ActionMenuIds.DELETE_SINGLE_RECENT_PLAYBACK -> R.string.recent_playback_remove_from_recent
    ActionMenuIds.CLEAR_RECENT_PLAYBACK -> R.string.recent_playback_remove_from_recent
    ActionMenuIds.SONG_INFO_TITLE -> R.string.player_detail_song
    ActionMenuIds.SONG_INFO_ARTIST -> R.string.player_detail_artist
    ActionMenuIds.SONG_INFO_ALBUM -> R.string.player_detail_album
    ActionMenuIds.SONG_INFO_ALBUM_ARTIST -> R.string.song_more_detail_album_artist
    ActionMenuIds.SONG_INFO_GENRE -> R.string.song_more_detail_genre
    ActionMenuIds.SONG_INFO_YEAR -> R.string.song_more_detail_year
    ActionMenuIds.SONG_INFO_COMPOSER -> R.string.player_detail_composer
    ActionMenuIds.SONG_INFO_ARRANGER -> R.string.player_detail_arranger
    ActionMenuIds.SONG_INFO_LYRICIST -> R.string.player_detail_lyricist
    ActionMenuIds.SONG_INFO_COMMENT -> R.string.player_detail_comment
    ActionMenuIds.SONG_INFO_NETEASE -> R.string.song_more_netease_key
    ActionMenuIds.SONG_INFO_FORMAT -> R.string.song_more_detail_format
    ActionMenuIds.SONG_INFO_BITRATE -> R.string.song_more_detail_bitrate
    ActionMenuIds.SONG_INFO_DURATION -> R.string.song_more_detail_duration
    ActionMenuIds.SONG_INFO_PLAY_COUNT -> R.string.song_more_detail_play_count
    ActionMenuIds.SONG_INFO_LISTENED -> R.string.song_more_detail_listened_duration
    ActionMenuIds.SONG_INFO_LAST_PLAYED -> R.string.song_more_detail_last_played
    ActionMenuIds.SONG_INFO_SIZE -> R.string.song_more_detail_size
    ActionMenuIds.SONG_INFO_MODIFIED -> R.string.song_more_detail_modified_time
    ActionMenuIds.SONG_INFO_ADDED -> R.string.song_more_detail_added_time
    ActionMenuIds.SONG_INFO_FILE_NAME -> R.string.song_more_detail_file_name
    ActionMenuIds.SONG_INFO_PATH -> R.string.song_more_detail_path
    ActionMenuIds.SONG_INFO_DIRECTORY -> R.string.song_more_detail_directory
    ActionMenuIds.SONG_INFO_MEDIA_INFO -> R.string.song_more_open_media_info
    ActionMenuIds.QUEUE_LOCK -> R.string.player_lock_queue
    ActionMenuIds.QUEUE_SHUFFLE -> R.string.player_randomize_queue
    ActionMenuIds.QUEUE_ADD_PLAYLIST -> R.string.player_add_to_playlist
    ActionMenuIds.QUEUE_LOCATE -> R.string.player_locate_current_song
    ActionMenuIds.QUEUE_LIBRARY_SOURCE -> R.string.player_queue_source
    ActionMenuIds.QUEUE_CLEAR -> R.string.player_clear_queue
    else -> R.string.player_more_actions
}

@Composable
internal fun actionMenuLabel(id: String): String = stringResource(actionMenuStringRes(id))

@Composable
internal fun ActionMenuReorderableSheet(
    show: Boolean,
    title: String,
    subtitle: String? = null,
    savedLayout: String,
    defaultOrder: List<String>,
    onDismissRequest: () -> Unit,
    onSave: (String) -> Unit
) {
    val context = LocalContext.current
    val layout = remember(savedLayout, defaultOrder) {
        ActionMenuLayout.parse(savedLayout, defaultOrder)
    }
    val items = remember(layout, context) {
        layout.order.map { id ->
            ReorderableSelectionItem(
                id = id,
                title = context.getString(actionMenuStringRes(id)),
                enabled = id !in layout.hidden
            )
        }
    }
    val defaultItems = remember(defaultOrder, context) {
        defaultOrder.map { id ->
            ReorderableSelectionItem(
                id = id,
                title = context.getString(actionMenuStringRes(id)),
                enabled = true
            )
        }
    }
    ReorderableSelectionSheet(
        show = show,
        title = title,
        subtitle = subtitle,
        items = items,
        defaultItems = defaultItems,
        onDismissRequest = onDismissRequest,
        onSave = { updated ->
            val newOrder = updated.map { it.id }
            val newHidden = updated.filterNot { it.enabled }.map { it.id }.toSet()
            val newLayout = ActionMenuLayout(order = newOrder, hidden = newHidden)
            onSave(newLayout.serialize())
        }
    )
}
