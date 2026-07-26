package com.ella.music.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableSharedFlow
import com.ella.music.player.FIXED_EQ_BAND_COUNT
import com.ella.music.player.AudioEffectSettings
import com.ella.music.player.PlaybackOutputSettings
import com.ella.music.plugin.source.LyricoPluginManager
import com.ella.music.data.remote.RemoteMusicProvider
import com.ella.music.data.remote.RemoteMusicSourceConfig
import com.ella.music.data.remote.SavedRemoteServer
import com.ella.music.data.remote.toRemoteServersJson
import com.ella.music.data.remote.toSavedRemoteServers
import com.ella.music.data.model.FolderPlaylist
import com.ella.music.data.model.toFolderPlaylistJson
import com.ella.music.data.model.toFolderPlaylists
import androidx.annotation.StringRes
import com.ella.music.R
import org.json.JSONObject
import java.io.File
import java.util.Locale

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ella_settings")

data class LxSourceConfig(
    val id: String,
    val url: String,
    val name: String,
    val script: String
)

data class OnlineSourceSelection(
    val provider: RemoteMusicProvider
)

enum class BottomBarGlassEffect {
    Blur,
    LiquidGlass
}

class SettingsManager(private val context: Context) {

    private data class SortIndexUpdate(
        val key: Preferences.Key<Int>,
        val value: Int
    )

    // DataStore correctly persists sort changes, but its disk-backed flow updates after the click
    // coroutine yields. Emit an in-memory value first so every list re-sorts in the same frame.
    private val immediateSortIndexUpdates = MutableSharedFlow<SortIndexUpdate>(extraBufferCapacity = 32)

    private fun sortIndexFlow(key: Preferences.Key<Int>, defaultValue: Int): Flow<Int> = merge(
        context.dataStore.data.map { it[key] ?: defaultValue },
        immediateSortIndexUpdates
            .filter { it.key == key }
            .map { it.value }
    ).distinctUntilChanged()

    private suspend fun setSortIndex(key: Preferences.Key<Int>, index: Int) {
        val safeIndex = index.coerceAtLeast(0)
        immediateSortIndexUpdates.tryEmit(SortIndexUpdate(key, safeIndex))
        context.dataStore.edit { it[key] = safeIndex }
    }

    companion object {
        @Volatile
        private var instance: SettingsManager? = null

        fun getInstance(context: Context): SettingsManager =
            instance ?: synchronized(this) {
                instance ?: SettingsManager(context.applicationContext).also { instance = it }
            }

        val KEY_LYRICON_ENABLED = booleanPreferencesKey("lyricon_enabled")
        val KEY_LYRICON_TRANSLATION = booleanPreferencesKey("lyricon_translation")
        val KEY_LYRICON_PRONUNCIATION = booleanPreferencesKey("lyricon_pronunciation")
        val KEY_AUTO_SCAN = booleanPreferencesKey("auto_scan")
        val KEY_AUTO_SCAN_LOCAL_PLAYLISTS = booleanPreferencesKey("auto_scan_local_playlists")
        val KEY_GAPLESS = booleanPreferencesKey("gapless_playback")
        val KEY_CROSSFADE_DURATION_MS = intPreferencesKey("crossfade_duration_ms")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        val KEY_MONET_COLOR_MODE = intPreferencesKey("monet_color_mode")
        val KEY_PLAYER_BACKGROUND_THEME = intPreferencesKey("player_background_theme")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_APP_ICON_STYLE = stringPreferencesKey("app_icon_style")
        val KEY_LIBRARY_SOURCE = stringPreferencesKey("library_source")
        val KEY_BOTTOM_BAR_GLASS_EFFECT = stringPreferencesKey("bottom_bar_glass_effect")
        val KEY_BOTTOM_DOCK_ITEMS = stringPreferencesKey("bottom_dock_items")
        val KEY_TICKER_ENABLED = booleanPreferencesKey("ticker_enabled")
        val KEY_TICKER_HIDE_NOTIFICATION = booleanPreferencesKey("ticker_hide_notification")
        val KEY_TICKER_HEADS_UP_LYRICS = booleanPreferencesKey("ticker_heads_up_lyrics")
        val KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION = booleanPreferencesKey("samsung_floating_lyric_translation")
        val KEY_STATUS_BAR_ALLOW_PHONETIC = booleanPreferencesKey("status_bar_allow_phonetic")
        val KEY_DESKTOP_LYRIC_ENABLED = booleanPreferencesKey("desktop_lyric_enabled")
        val KEY_DESKTOP_LYRIC_HIDE_WHEN_PAUSED = booleanPreferencesKey("desktop_lyric_hide_when_paused")
        val KEY_DESKTOP_LYRIC_HIDE_IN_LANDSCAPE = booleanPreferencesKey("desktop_lyric_hide_in_landscape")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_MODE = booleanPreferencesKey("desktop_lyric_status_bar_mode")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_HIDE_WHEN_PAUSED = booleanPreferencesKey("desktop_lyric_status_bar_hide_when_paused")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_HIDE_IN_LANDSCAPE = booleanPreferencesKey("desktop_lyric_status_bar_hide_in_landscape")
        val KEY_DESKTOP_LYRIC_WIDTH = intPreferencesKey("desktop_lyric_width")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_TOP_OFFSET = intPreferencesKey("desktop_lyric_status_bar_top_offset")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_POSITION = intPreferencesKey("desktop_lyric_status_bar_position")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_WIDTH = intPreferencesKey("desktop_lyric_status_bar_width")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_X_OFFSET = intPreferencesKey("desktop_lyric_status_bar_x_offset")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_ALIGN = intPreferencesKey("desktop_lyric_status_bar_text_align")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_VERTICAL_ALIGN = intPreferencesKey("desktop_lyric_status_bar_vertical_align")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY = intPreferencesKey("desktop_lyric_status_bar_secondary")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY_OPACITY = intPreferencesKey("desktop_lyric_status_bar_secondary_opacity")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_MERGE_SECONDARY = booleanPreferencesKey("desktop_lyric_status_bar_merge_secondary")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_FONT_SCALE = intPreferencesKey("desktop_lyric_status_bar_font_scale")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_TRANSLATION_SCALE = intPreferencesKey("desktop_lyric_status_bar_translation_scale")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_OPACITY = intPreferencesKey("desktop_lyric_status_bar_opacity")
        val KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_COLOR = intPreferencesKey("desktop_lyric_status_bar_text_color")
        val KEY_DESKTOP_LYRIC_LOCKED = booleanPreferencesKey("desktop_lyric_locked")
        val KEY_DESKTOP_LYRIC_FONT_SCALE = intPreferencesKey("desktop_lyric_font_scale")
        val KEY_DESKTOP_LYRIC_TRANSLATION_SCALE = intPreferencesKey("desktop_lyric_translation_scale")
        val KEY_DESKTOP_LYRIC_OPACITY = intPreferencesKey("desktop_lyric_opacity")
        val KEY_DESKTOP_LYRIC_TEXT_COLOR = intPreferencesKey("desktop_lyric_text_color")
        val KEY_DESKTOP_LYRIC_X = intPreferencesKey("desktop_lyric_x")
        val KEY_DESKTOP_LYRIC_Y = intPreferencesKey("desktop_lyric_y")
        val KEY_SUPER_LYRIC_ENABLED = booleanPreferencesKey("super_lyric_enabled")
        val KEY_SUPER_LYRIC_TRANSLATION = booleanPreferencesKey("super_lyric_translation")
        val KEY_SUPER_LYRIC_PRONUNCIATION = booleanPreferencesKey("super_lyric_pronunciation")
        val KEY_LYRIC_GETTER_ENABLED = booleanPreferencesKey("lyric_getter_enabled")
        val KEY_MIN_DURATION = intPreferencesKey("min_duration_sec")
        val KEY_REPLAYGAIN_ENABLED = booleanPreferencesKey("replaygain_enabled")
        val KEY_REPLAYGAIN_MODE = intPreferencesKey("replaygain_mode")
        val KEY_RESUME_PLAYBACK_POSITION = booleanPreferencesKey("resume_playback_position")
        val KEY_AUDIO_FOCUS_DISABLED = booleanPreferencesKey("audio_focus_disabled")
        val KEY_SHUFFLE_MODE = intPreferencesKey("shuffle_mode")
        val KEY_PREVIOUS_BUTTON_ACTION = intPreferencesKey("previous_button_action")
        val KEY_LYRIC_SOURCE_MODE = intPreferencesKey("lyric_source_mode")
        val KEY_LYRIC_SOURCE_PRIORITY = stringPreferencesKey("lyric_source_priority")
        val KEY_LYRICO_PLUGIN_ENABLED_IDS = stringPreferencesKey("lyrico_plugin_enabled_ids")
        val KEY_IGNORE_LYRIC_HEADER_TAGS = booleanPreferencesKey("ignore_lyric_header_tags")
        val KEY_LYRIC_LINE_BLACKLIST = stringPreferencesKey("lyric_line_blacklist")
        val KEY_LYRIC_OFFSET_OVERRIDES = stringPreferencesKey("lyric_offset_overrides")
        val KEY_PLAYER_LYRIC_TEXT_ALIGN = intPreferencesKey("player_lyric_text_align")
        val KEY_LYRIC_PRONUNCIATION_BELOW = booleanPreferencesKey("lyric_pronunciation_below")
        val KEY_LYRIC_PAGE_TRANSLATION = booleanPreferencesKey("lyric_page_translation")
        val KEY_LYRIC_PAGE_KEEP_SCREEN_ON = booleanPreferencesKey("lyric_page_keep_screen_on")
        val KEY_APPLE_MUSIC_LYRICS_WORD_LIFT = booleanPreferencesKey("apple_music_lyrics_word_lift")
        val KEY_MINI_PLAYER_LYRIC_TRANSLATION = booleanPreferencesKey("mini_player_lyric_translation")
        val KEY_MINI_PLAYER_LYRIC_SECONDARY = intPreferencesKey("mini_player_lyric_secondary")
        val KEY_MINI_PLAYER_COVER_ROTATION = booleanPreferencesKey("mini_player_cover_rotation")
        val KEY_MINI_PLAYER_LYRICS_ENABLED = booleanPreferencesKey("mini_player_lyrics_enabled")
        val KEY_MINI_PLAYER_RIGHT_BUTTON = intPreferencesKey("mini_player_right_button")
        val KEY_PLAYER_PROGRESS_INFO_INDEX = intPreferencesKey("player_progress_info_index")
        val KEY_TRANSPORT_BUTTON_OUTLINES = booleanPreferencesKey("transport_button_outlines")
        val KEY_PLAYER_TAP_SEEK_ENABLED = booleanPreferencesKey("player_tap_seek_enabled")
        val KEY_PLAYER_SHOW_TOTAL_DURATION = booleanPreferencesKey("player_show_total_duration")
        val KEY_PLAYER_SHOW_SONG_ANNOTATION = booleanPreferencesKey("player_show_song_annotation")
        val KEY_PLAYER_COVER_SWIPE_ENABLED = booleanPreferencesKey("player_cover_swipe_enabled")
        val KEY_LYRIC_PARSER_ENGINE = intPreferencesKey("lyric_parser_engine")
        val KEY_PLAYER_TITLE_POSITION = intPreferencesKey("player_title_position")
        val KEY_PLAYER_KEEP_SCREEN_ON = booleanPreferencesKey("player_keep_screen_on")
        val KEY_PLAYER_HDR_GLOW = booleanPreferencesKey("player_hdr_glow")
        val KEY_PLAYER_IMMERSIVE_COVER = booleanPreferencesKey("player_immersive_cover")
        val KEY_PLAYER_COVER_CONTENT_COLOR = booleanPreferencesKey("player_cover_content_color")
        val KEY_HIDE_SYSTEM_BARS = booleanPreferencesKey("hide_system_bars")
        val KEY_PLAYER_DYNAMIC_FLOW_ENABLED = booleanPreferencesKey("player_dynamic_flow_enabled")
        val KEY_AUDIO_VISUALIZER_ENABLED = booleanPreferencesKey("audio_visualizer_enabled")
        val KEY_AUDIO_VISUALIZER_OPACITY = intPreferencesKey("audio_visualizer_opacity")
        val KEY_EQ_ENABLED = booleanPreferencesKey("audio_eq_enabled")
        val KEY_EQ_PRESET = intPreferencesKey("audio_eq_preset")
        val KEY_EQ_BANDS = stringPreferencesKey("audio_eq_bands")
        val KEY_BASS_BOOST_ENABLED = booleanPreferencesKey("audio_bass_boost_enabled")
        val KEY_BASS_BOOST_STRENGTH = intPreferencesKey("audio_bass_boost_strength")
        val KEY_VIRTUALIZER_ENABLED = booleanPreferencesKey("audio_virtualizer_enabled")
        val KEY_VIRTUALIZER_STRENGTH = intPreferencesKey("audio_virtualizer_strength")
        val KEY_REVERB_PRESET = intPreferencesKey("audio_reverb_preset")
        val KEY_EQ_Q = intPreferencesKey("audio_eq_q")
        val KEY_TONE_BASS_DB = intPreferencesKey("audio_tone_bass_db")
        val KEY_TONE_TREBLE_DB = intPreferencesKey("audio_tone_treble_db")
        val KEY_COMP_ENABLED = booleanPreferencesKey("audio_comp_enabled")
        val KEY_COMP_THRESHOLD_DB = intPreferencesKey("audio_comp_threshold_db")
        val KEY_COMP_RATIO = intPreferencesKey("audio_comp_ratio")
        val KEY_COMP_MAKEUP_DB = intPreferencesKey("audio_comp_makeup_db")
        val KEY_STEREO_WIDTH = intPreferencesKey("audio_stereo_width")
        val KEY_SURROUND_360_ENABLED = booleanPreferencesKey("audio_surround_360_enabled")
        val KEY_SURROUND_360_INTENSITY = intPreferencesKey("audio_surround_360_intensity")
        val KEY_SURROUND_360_ROTATION_SPEED = intPreferencesKey("audio_surround_360_rotation_speed")
        val KEY_PANORAMIC_360_ENABLED = booleanPreferencesKey("audio_panoramic_360_enabled")
        val KEY_PANORAMIC_360_INTENSITY = intPreferencesKey("audio_panoramic_360_intensity")
        val KEY_PANORAMIC_360_AZIMUTH_DEGREES = intPreferencesKey("audio_panoramic_360_azimuth_degrees")
        val KEY_PANORAMIC_360_ELEVATION_DEGREES = intPreferencesKey("audio_panoramic_360_elevation_degrees")
        val KEY_LOUDNESS_BALANCE_ENABLED = booleanPreferencesKey("audio_loudness_balance_enabled")
        val KEY_LOUDNESS_PERCENT = intPreferencesKey("audio_loudness_percent")
        val KEY_CHANNEL_BALANCE = intPreferencesKey("audio_channel_balance")
        val KEY_CROSSFEED_ENABLED = booleanPreferencesKey("audio_crossfeed_enabled")
        val KEY_CROSSFEED_LOW_CUT_HZ = intPreferencesKey("audio_crossfeed_low_cut_hz")
        val KEY_CROSSFEED_HIGH_CUT_HZ = intPreferencesKey("audio_crossfeed_high_cut_hz")
        val KEY_CROSSFEED_ATTENUATION_TENTHS_DB = intPreferencesKey("audio_crossfeed_attenuation_tenths_db")
        val KEY_MONO_BASS_ENABLED = booleanPreferencesKey("audio_mono_bass_enabled")
        val KEY_MONO_BASS_CROSSOVER_HZ = intPreferencesKey("audio_mono_bass_crossover_hz")
        val KEY_MONO_BASS_AMOUNT = intPreferencesKey("audio_mono_bass_amount")
        val KEY_SPEAKER_OUTPUT_ENABLED = booleanPreferencesKey("audio_speaker_output_enabled")
        val KEY_SPEAKER_OUTPUT_MODE = intPreferencesKey("audio_speaker_output_mode")
        val KEY_SPEAKER_OUTPUT_STRENGTH = intPreferencesKey("audio_speaker_output_strength")
        val KEY_DYNAMIC_EQ_ENABLED = booleanPreferencesKey("audio_dynamic_eq_enabled")
        val KEY_DYNAMIC_EQ_INTENSITY = intPreferencesKey("audio_dynamic_eq_intensity")
        val KEY_DE_ESSER_AMOUNT = intPreferencesKey("audio_de_esser_amount")
        val KEY_DE_ESSER_FREQUENCY_HZ = intPreferencesKey("audio_de_esser_frequency_hz")
        val KEY_MOOG_LADDER_ENABLED = booleanPreferencesKey("audio_moog_ladder_enabled")
        val KEY_MOOG_LADDER_MODE = intPreferencesKey("audio_moog_ladder_mode")
        val KEY_MOOG_LADDER_CUTOFF_HZ = intPreferencesKey("audio_moog_ladder_cutoff_hz")
        val KEY_MOOG_LADDER_RESONANCE = intPreferencesKey("audio_moog_ladder_resonance")
        val KEY_MOOG_LADDER_DRIVE_DB = intPreferencesKey("audio_moog_ladder_drive_db")
        val KEY_MOOG_LADDER_MIX = intPreferencesKey("audio_moog_ladder_mix")
        val KEY_PEAK_LIMITER_ENABLED = booleanPreferencesKey("audio_peak_limiter_enabled")
        val KEY_PLATFORM_SPATIAL_AUDIO_ENABLED = booleanPreferencesKey("audio_platform_spatial_audio_enabled")
        val KEY_USB_DAC_MODE = booleanPreferencesKey("usb_dac_mode")
        val KEY_DYNAMIC_COVER_ENABLED = booleanPreferencesKey("dynamic_cover_enabled")
        val KEY_MUSIC_VIDEO_SYNC_ENABLED = booleanPreferencesKey("music_video_sync_enabled")
        val KEY_MUSIC_VIDEO_CAPTURE_SUBTITLES = booleanPreferencesKey("music_video_capture_subtitles")
        val KEY_DYNAMIC_COVER_CUSTOM_FOLDERS = stringPreferencesKey("dynamic_cover_custom_folders")
        val KEY_ARTIST_COVER_FOLDER_URI = stringPreferencesKey("artist_cover_folder_uri")
        val KEY_ARTIST_COVER_CAROUSEL = booleanPreferencesKey("artist_cover_carousel")
        val KEY_STARTUP_POSTER_ENABLED = booleanPreferencesKey("startup_poster_enabled")
        val KEY_STARTUP_POSTER_URI = stringPreferencesKey("startup_poster_uri")
        val KEY_STARTUP_POSTER_DURATION_MS = intPreferencesKey("startup_poster_duration_ms")
        val KEY_APP_WALLPAPER_ENABLED = booleanPreferencesKey("app_wallpaper_enabled")
        val KEY_APP_WALLPAPER_URI = stringPreferencesKey("app_wallpaper_uri")
        val KEY_APP_WALLPAPER_OPACITY = intPreferencesKey("app_wallpaper_opacity")
        val KEY_APP_WALLPAPER_DIM = intPreferencesKey("app_wallpaper_dim")
        val KEY_APP_WALLPAPER_CONTENT_OVERLAY = intPreferencesKey("app_wallpaper_content_overlay")
        val KEY_PLAYER_BACKGROUND_ENABLED = booleanPreferencesKey("player_background_enabled")
        val KEY_PLAYER_BACKGROUND_URI = stringPreferencesKey("player_background_uri")
        val KEY_PLAYER_BACKGROUND_OPACITY = intPreferencesKey("player_background_opacity")
        val KEY_PLAYER_BACKGROUND_DIM = intPreferencesKey("player_background_dim")
        val KEY_PLAYER_BEAUTIFUL_LYRICS_BACKGROUND = booleanPreferencesKey("player_beautiful_lyrics_background")
        val KEY_PLAYER_BEAUTIFUL_LYRICS_SPEED = intPreferencesKey("player_beautiful_lyrics_speed")
        val KEY_PLAYER_BEAUTIFUL_LYRICS_BLUR = intPreferencesKey("player_beautiful_lyrics_blur")
        val KEY_PLAYER_BEAUTIFUL_LYRICS_BRIGHTNESS = intPreferencesKey("player_beautiful_lyrics_brightness")
        val KEY_HOME_CARD_COLOR = stringPreferencesKey("home_card_color")
        val KEY_HOME_CARD_OPACITY = intPreferencesKey("home_card_opacity")
        val KEY_HOME_TILE_COLORS = stringPreferencesKey("home_tile_colors")
        val KEY_HOME_TILE_GRADIENT_ENABLED = booleanPreferencesKey("home_tile_gradient_enabled")
        val KEY_HOME_TILE_GRADIENT_START_COLOR = stringPreferencesKey("home_tile_gradient_start_color")
        val KEY_HI_RES_LOGO_ENABLED = booleanPreferencesKey("hi_res_logo_enabled")
        val KEY_HI_RES_LOGO_URI = stringPreferencesKey("hi_res_logo_uri")
        val KEY_MCP_SERVER_ENABLED = booleanPreferencesKey("mcp_server_enabled")
        val KEY_PLAYLIST_SPECIAL_ENTRIES_VISIBLE = booleanPreferencesKey("playlist_special_entries_visible")
        val KEY_PLAYLIST_CUSTOM_ORDER = stringPreferencesKey("playlist_custom_order")
        val KEY_SHOW_PLAY_NEXT_IN_LISTS = booleanPreferencesKey("show_play_next_in_lists")
        val KEY_EXCLUDE_SEARCH_RESULTS_FROM_PLAYLIST = booleanPreferencesKey("exclude_search_results_from_playlist")
        val KEY_AUTO_SHOW_SEARCH_KEYBOARD = booleanPreferencesKey("auto_show_search_keyboard")
        val KEY_PLAY_NEXT_MODE = intPreferencesKey("play_next_mode")
        val KEY_ADD_TO_PLAYLIST_APPEND_TO_END = booleanPreferencesKey("add_to_playlist_append_to_end")
        val KEY_LYRIC_SHARE_CUSTOM_INFO = stringPreferencesKey("lyric_share_custom_info")
        val KEY_LYRIC_SHARE_USE_LYRIC_FONT = booleanPreferencesKey("lyric_share_use_lyric_font")
        val KEY_SHOW_ALBUM_ARTISTS = booleanPreferencesKey("show_album_artists")
        val KEY_METADATA_EDITOR_ID = stringPreferencesKey("metadata_editor_id")
        val KEY_LYRIC_TIMING_EDITOR_ID = stringPreferencesKey("lyric_timing_editor_id")
        val KEY_SLEEP_TIMER_CUSTOM_MINUTES = intPreferencesKey("sleep_timer_custom_minutes")
        val KEY_SLEEP_TIMER_STOP_AFTER_CURRENT = booleanPreferencesKey("sleep_timer_stop_after_current")
        val KEY_SHORTCUT_LIBRARY_LABEL = stringPreferencesKey("shortcut_library_label")
        val KEY_SHORTCUT_PLAYLISTS_LABEL = stringPreferencesKey("shortcut_playlists_label")
        val KEY_SHORTCUT_FOLDER_LABEL = stringPreferencesKey("shortcut_folder_label")
        val KEY_APP_SHORTCUT_ORDER = stringPreferencesKey("app_shortcut_order")
        val KEY_WEBDAV_URL = stringPreferencesKey("webdav_url")
        val KEY_WEBDAV_USERNAME = stringPreferencesKey("webdav_username")
        val KEY_WEBDAV_PASSWORD = stringPreferencesKey("webdav_password")
        val KEY_WEBDAV_LAST_URL = stringPreferencesKey("webdav_last_url")
        val KEY_WEBDAV_BACKUP_URL = stringPreferencesKey("webdav_backup_url")
        val KEY_WEBDAV_BACKUP_PATH = stringPreferencesKey("webdav_backup_path")
        val KEY_WEBDAV_BACKUP_USERNAME = stringPreferencesKey("webdav_backup_username")
        val KEY_WEBDAV_BACKUP_PASSWORD = stringPreferencesKey("webdav_backup_password")
        val KEY_WEBDAV_AUTO_BACKUP_ENABLED = booleanPreferencesKey("webdav_auto_backup_enabled")
        val KEY_WEBDAV_AUTO_BACKUP_INTERVAL_HOURS = intPreferencesKey("webdav_auto_backup_interval_hours")
        val KEY_WEBDAV_AUTO_BACKUP_LAST_AT = stringPreferencesKey("webdav_auto_backup_last_at")
        val KEY_LX_SOURCE_URL = stringPreferencesKey("lx_source_url")
        val KEY_LX_SOURCE_NAME = stringPreferencesKey("lx_source_name")
        val KEY_LX_SOURCE_SCRIPT = stringPreferencesKey("lx_source_script")
        val KEY_LX_SOURCES_JSON = stringPreferencesKey("lx_sources_json")
        val KEY_LX_SELECTED_SOURCE_ID = stringPreferencesKey("lx_selected_source_id")
        val KEY_ONLINE_SELECTED_PROVIDER = stringPreferencesKey("online_selected_provider")
        val KEY_NAVIDROME_URL = stringPreferencesKey("navidrome_url")
        val KEY_NAVIDROME_USERNAME = stringPreferencesKey("navidrome_username")
        val KEY_NAVIDROME_PASSWORD = stringPreferencesKey("navidrome_password")
        val KEY_EMBY_URL = stringPreferencesKey("emby_url")
        val KEY_EMBY_USERNAME = stringPreferencesKey("emby_username")
        val KEY_EMBY_TOKEN = stringPreferencesKey("emby_token")
        val KEY_EMBY_USER_ID = stringPreferencesKey("emby_user_id")
        val KEY_EMBY_SERVER_NAME = stringPreferencesKey("emby_server_name")
        val KEY_NAVIDROME_SERVERS = stringPreferencesKey("navidrome_servers")
        val KEY_NAVIDROME_ACTIVE_ID = stringPreferencesKey("navidrome_active_id")
        val KEY_OPENSUBSONIC_SERVERS = stringPreferencesKey("opensubsonic_servers")
        val KEY_OPENSUBSONIC_ACTIVE_ID = stringPreferencesKey("opensubsonic_active_id")
        val KEY_EMBY_SERVERS = stringPreferencesKey("emby_servers")
        val KEY_EMBY_ACTIVE_ID = stringPreferencesKey("emby_active_id")
        const val LEGACY_NAVIDROME_SERVER_ID = "navidrome-legacy"
        const val LEGACY_EMBY_SERVER_ID = "emby-legacy"
        val KEY_OPENAI_API_KEY = stringPreferencesKey("openai_api_key")
        val KEY_OPENAI_BASE_URL = stringPreferencesKey("openai_base_url")
        val KEY_OPENAI_MODEL = stringPreferencesKey("openai_model")
        val KEY_OPEN_PLAYER_ON_PLAY = booleanPreferencesKey("online_auto_open_player")
        val KEY_STARTUP_AUTO_PLAY = booleanPreferencesKey("startup_auto_play")
        val KEY_STARTUP_PLAY_MODE = intPreferencesKey("startup_play_mode")
        val KEY_BLUETOOTH_AUTO_PLAY = booleanPreferencesKey("bluetooth_auto_play")
        val KEY_LYRIC_FONT_NAME = stringPreferencesKey("lyric_font_name")
        val KEY_LYRIC_FONT_PATH = stringPreferencesKey("lyric_font_path")
        val KEY_LYRIC_WESTERN_FONT_NAME = stringPreferencesKey("lyric_western_font_name")
        val KEY_LYRIC_WESTERN_FONT_PATH = stringPreferencesKey("lyric_western_font_path")
        val KEY_LYRIC_CJK_FONT_NAME = stringPreferencesKey("lyric_cjk_font_name")
        val KEY_LYRIC_CJK_FONT_PATH = stringPreferencesKey("lyric_cjk_font_path")
        val KEY_GLOBAL_WESTERN_FONT_NAME = stringPreferencesKey("global_western_font_name")
        val KEY_GLOBAL_WESTERN_FONT_PATH = stringPreferencesKey("global_western_font_path")
        val KEY_GLOBAL_CJK_FONT_NAME = stringPreferencesKey("global_cjk_font_name")
        val KEY_GLOBAL_CJK_FONT_PATH = stringPreferencesKey("global_cjk_font_path")
        val KEY_LYRIC_ORIGINAL_WESTERN_FONT_NAME = stringPreferencesKey("lyric_original_western_font_name")
        val KEY_LYRIC_ORIGINAL_WESTERN_FONT_PATH = stringPreferencesKey("lyric_original_western_font_path")
        val KEY_LYRIC_ORIGINAL_CJK_FONT_NAME = stringPreferencesKey("lyric_original_cjk_font_name")
        val KEY_LYRIC_ORIGINAL_CJK_FONT_PATH = stringPreferencesKey("lyric_original_cjk_font_path")
        val KEY_LYRIC_TRANSLATION_WESTERN_FONT_NAME = stringPreferencesKey("lyric_translation_western_font_name")
        val KEY_LYRIC_TRANSLATION_WESTERN_FONT_PATH = stringPreferencesKey("lyric_translation_western_font_path")
        val KEY_LYRIC_TRANSLATION_CJK_FONT_NAME = stringPreferencesKey("lyric_translation_cjk_font_name")
        val KEY_LYRIC_TRANSLATION_CJK_FONT_PATH = stringPreferencesKey("lyric_translation_cjk_font_path")
        val KEY_LYRIC_FONT_WEIGHT = intPreferencesKey("lyric_font_weight")
        val KEY_LYRIC_FONT_SCALE = intPreferencesKey("lyric_font_scale")
        val KEY_LYRIC_SECONDARY_FONT_SCALE = intPreferencesKey("lyric_secondary_font_scale")
        val KEY_LYRIC_COMPACT_PRIMARY_TEXT_SIZE = intPreferencesKey("lyric_compact_primary_text_size")
        val KEY_LYRIC_COMPACT_SECONDARY_TEXT_SIZE = intPreferencesKey("lyric_compact_secondary_text_size")
        val KEY_LYRIC_WIDE_PRIMARY_TEXT_SIZE = intPreferencesKey("lyric_wide_primary_text_size")
        val KEY_LYRIC_WIDE_SECONDARY_TEXT_SIZE = intPreferencesKey("lyric_wide_secondary_text_size")
        val KEY_LYRIC_FONT_ITALIC = booleanPreferencesKey("lyric_font_italic")
        val KEY_LYRIC_FONT_APPLY_TO_PAGE = booleanPreferencesKey("lyric_font_apply_to_page")
        val KEY_LYRIC_FONT_APPLY_TO_DESKTOP = booleanPreferencesKey("lyric_font_apply_to_desktop")
        val KEY_LYRIC_PERSPECTIVE_EFFECT = booleanPreferencesKey("lyric_perspective_effect")
        val KEY_LYRIC_PERSPECTIVE_Y_ANGLE = intPreferencesKey("lyric_perspective_y_angle")
        val KEY_SCAN_INCLUDE_FOLDERS = stringPreferencesKey("scan_include_folders")
        val KEY_SCAN_EXCLUDE_FOLDERS = stringPreferencesKey("scan_exclude_folders")
        val KEY_USB_FOLDER_URIS = stringPreferencesKey("usb_folder_uris")
        val KEY_USE_ANDROID_MEDIA_LIBRARY = booleanPreferencesKey("use_android_media_library")
        val KEY_FULL_TAG_SEARCH_ENABLED = booleanPreferencesKey("full_tag_search_enabled")
        val KEY_FULL_TAG_SEARCH_PROMPT_HANDLED = booleanPreferencesKey("full_tag_search_prompt_handled")
        val KEY_COVER_EXPORT_FOLDER_URI = stringPreferencesKey("cover_export_folder_uri")
        val KEY_SEARCH_ALL_CATEGORY_TYPES = stringPreferencesKey("search_all_category_types")
        val KEY_SEARCH_ALL_SONG_MATCH_TYPES = stringPreferencesKey("search_all_song_match_types")
        val KEY_SONG_RATING_DISPLAY_MODE = intPreferencesKey("song_rating_display_mode")
        val KEY_INITIAL_SCAN_PROMPT_HANDLED = booleanPreferencesKey("initial_scan_prompt_handled")
        val KEY_LOCAL_PLAYLIST_SCAN_PROMPT_HANDLED = booleanPreferencesKey("local_playlist_scan_prompt_handled")
        val KEY_ARTIST_SEPARATORS = stringPreferencesKey("artist_separators")
        val KEY_ARTIST_PROTECTED_NAMES = stringPreferencesKey("artist_protected_names")
        val KEY_GENRE_SEPARATORS = stringPreferencesKey("genre_separators")
        val KEY_GENRE_PROTECTED_NAMES = stringPreferencesKey("genre_protected_names")
        val KEY_TAG_IGNORE_CASE = booleanPreferencesKey("tag_ignore_case")
        val KEY_DECODER_MODE = intPreferencesKey("decoder_mode")
        val KEY_AUDIO_OUTPUT_BACKEND = intPreferencesKey("audio_output_backend")
        val KEY_AUDIO_OUTPUT_BIT_DEPTH = intPreferencesKey("audio_output_bit_depth")
        val KEY_AUDIO_OUTPUT_SAMPLE_RATE = intPreferencesKey("audio_output_sample_rate")
        val KEY_SORT_LIBRARY_SONG = intPreferencesKey("sort_library_song")
        val KEY_SORT_ALBUM_LIST = intPreferencesKey("sort_album_list")
        val KEY_SORT_ARTIST_LIST = intPreferencesKey("sort_artist_list")
        val KEY_SORT_ALBUM_DETAIL_SONG = intPreferencesKey("sort_album_detail_song")
        val KEY_SORT_ARTIST_DETAIL_SONG = intPreferencesKey("sort_artist_detail_song")
        val KEY_SORT_ARTIST_DETAIL_ALBUM = intPreferencesKey("sort_artist_detail_album")
        val KEY_SORT_FOLDER_LIST = intPreferencesKey("sort_folder_list")
        val KEY_SORT_FOLDER_DETAIL_SONG = intPreferencesKey("sort_folder_detail_song")
        val KEY_SORT_FOLDER_PLAYLIST_LIST = intPreferencesKey("sort_folder_playlist_list")
        val KEY_SORT_FOLDER_PLAYLIST_DETAIL_SONG = intPreferencesKey("sort_folder_playlist_detail_song")
        val KEY_SORT_FOLDER_PLAYLIST_DETAIL_FOLDER = intPreferencesKey("sort_folder_playlist_detail_folder")
        val KEY_SORT_PLAYLIST_LIST = intPreferencesKey("sort_playlist_list")
        val KEY_SORT_PLAYLIST_DETAIL_SONG = intPreferencesKey("sort_playlist_detail_song")
        val KEY_CATEGORY_GRID_COLUMNS = intPreferencesKey("category_grid_columns")
        // 0 = only the on-device log, 1 = only Last.fm, 2 = merge both timelines.
        val KEY_LISTENING_HISTORY_SOURCE = intPreferencesKey("listening_history_source")
        val KEY_HOME_DAILY_MIX_VISIBLE = booleanPreferencesKey("home_daily_mix_visible")
        val KEY_HOME_AI_MIX_VISIBLE = booleanPreferencesKey("home_ai_mix_visible")
        val KEY_HOME_SECTION_ORDER = stringPreferencesKey("home_section_order")
        val KEY_HOME_HIDDEN_SECTIONS = stringPreferencesKey("home_hidden_sections")
        val KEY_HOME_LIBRARY_TILE_ORDER = stringPreferencesKey("home_library_tile_order")
        val KEY_HOME_HIDDEN_LIBRARY_TILES = stringPreferencesKey("home_hidden_library_tiles")
        val KEY_HOME_ONLINE_TILE_ORDER = stringPreferencesKey("home_online_tile_order")
        val KEY_HOME_HIDDEN_ONLINE_TILES = stringPreferencesKey("home_hidden_online_tiles")
        val KEY_FOLDER_PLAYLISTS = stringPreferencesKey("folder_playlists")
        val KEY_HOME_TILE_PIN_BUTTONS_VISIBLE = booleanPreferencesKey("home_tile_pin_buttons_visible")
        val KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED = booleanPreferencesKey("notification_permission_prompt_handled")

        const val LYRIC_FONT_SCALE_MIN = 75
        const val LYRIC_FONT_SCALE_PHONE_MAX = 125
        const val LYRIC_FONT_SCALE_WIDE_MAX = 150
        const val LYRIC_FONT_SCALE_ULTRA_WIDE_MAX = 175
        const val LYRIC_SECONDARY_FONT_SCALE_MIN = 75
        const val LYRIC_SECONDARY_FONT_SCALE_PHONE_MAX = 135
        const val LYRIC_SECONDARY_FONT_SCALE_WIDE_MAX = 135
        const val LYRIC_SECONDARY_FONT_SCALE_ULTRA_WIDE_MAX = 150

        const val LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MIN_SP = 20
        const val LYRIC_COMPACT_PRIMARY_TEXT_SIZE_DEFAULT_SP = 28
        const val LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MAX_SP = 42
        const val LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MIN_SP = 12
        const val LYRIC_COMPACT_SECONDARY_TEXT_SIZE_DEFAULT_SP = 15
        const val LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MAX_SP = 24
        const val LYRIC_WIDE_PRIMARY_TEXT_SIZE_MIN_SP = 24
        const val LYRIC_WIDE_PRIMARY_TEXT_SIZE_DEFAULT_SP = 30
        const val LYRIC_WIDE_PRIMARY_TEXT_SIZE_MAX_SP = 54
        const val LYRIC_WIDE_SECONDARY_TEXT_SIZE_MIN_SP = 12
        const val LYRIC_WIDE_SECONDARY_TEXT_SIZE_DEFAULT_SP = 15
        const val LYRIC_WIDE_SECONDARY_TEXT_SIZE_MAX_SP = 30

        val KEY_BLUETOOTH_LYRIC_ENABLED = booleanPreferencesKey("bluetooth_lyric_enabled")
        val KEY_BLUETOOTH_LYRIC_TRANSLATION = booleanPreferencesKey("bluetooth_lyric_translation")
        val KEY_BLUETOOTH_LYRIC_PRONUNCIATION = booleanPreferencesKey("bluetooth_lyric_pronunciation")
        val KEY_COLOROS_LOCK_SCREEN_LYRIC_ENABLED = booleanPreferencesKey("coloros_lock_screen_lyric_enabled")
        val KEY_COLOROS_LOCK_SCREEN_LYRIC_MODE = intPreferencesKey("coloros_lock_screen_lyric_mode")

        const val SHUFFLE_MODE_PSEUDO = 0
        const val SHUFFLE_MODE_TRUE_RANDOM = 1
        const val REPLAY_GAIN_OFF = 0
        const val REPLAY_GAIN_TRACK = 1
        const val REPLAY_GAIN_ALBUM = 2
        const val REPLAY_GAIN_AUTO = 3
        const val PLAYER_TITLE_POSITION_BELOW_COVER = 0
        const val PLAYER_TITLE_POSITION_ABOVE_COVER = 1

        const val PREVIOUS_BUTTON_PREVIOUS = 0
        const val PREVIOUS_BUTTON_REPLAY_CURRENT = 1
        const val PREVIOUS_REPLAY_THRESHOLD_MS = 20_000L

        const val PLAY_NEXT_MODE_REVERSE_STACK = 0
        const val PLAY_NEXT_MODE_FORWARD_STACK = 1

        const val LISTENING_HISTORY_SOURCE_LOCAL = 0
        const val LISTENING_HISTORY_SOURCE_LAST_FM = 1
        const val LISTENING_HISTORY_SOURCE_COMBINED = 2

        const val OPLUS_LYRIC_MODE_SYSTEM = 0
        const val OPLUS_LYRIC_MODE_MODULE = 1

        const val STARTUP_PLAY_OFF = 0
        const val STARTUP_PLAY_RANDOM = 1
        const val STARTUP_PLAY_RESUME = 2

        const val AUDIO_OUTPUT_BACKEND_AUTO = 0
        const val AUDIO_OUTPUT_BACKEND_OPENSLES = 1
        const val AUDIO_OUTPUT_BACKEND_AAUDIO = 2
        const val AUDIO_OUTPUT_BACKEND_HI_RES = 3
        const val AUDIO_OUTPUT_BACKEND_AUDIOTRACK = 4

        const val AUDIO_OUTPUT_BIT_DEPTH_AUTO = 0
        const val AUDIO_OUTPUT_BIT_DEPTH_16 = 16
        const val AUDIO_OUTPUT_BIT_DEPTH_24 = 24
        const val AUDIO_OUTPUT_BIT_DEPTH_32 = 32
        const val AUDIO_OUTPUT_BIT_DEPTH_FLOAT32 = 40

        const val AUDIO_OUTPUT_SAMPLE_RATE_AUTO = 0
        val AUDIO_OUTPUT_SAMPLE_RATES = intArrayOf(
            44_100,
            48_000,
            88_200,
            96_000,
            176_400,
            192_000,
            352_800,
            384_000
        )

        const val PLAYER_BG_THEME_FOLLOW_SYSTEM = 0
        const val PLAYER_BG_THEME_LIGHT = 1
        const val PLAYER_BG_THEME_DARK = 2

        const val LYRIC_SOURCE_AUTO = 0
        const val LYRIC_SOURCE_EXTERNAL = 1
        const val LYRIC_SOURCE_EMBEDDED = 2

        // Lyric parser engine selection
        const val LYRIC_PARSER_ENGINE_AUTO = 0
        const val LYRIC_PARSER_ENGINE_ELLA = 1

        const val LYRIC_SOURCE_EMBEDDED_TTML = "embedded_ttml"
        const val LYRIC_SOURCE_EMBEDDED_PLAIN = "embedded_plain"
        const val LYRIC_SOURCE_EXTERNAL_TTML = "external_ttml"
        const val LYRIC_SOURCE_EXTERNAL_PLAIN = "external_plain"
        const val DEFAULT_LYRIC_SOURCE_PRIORITY =
            "$LYRIC_SOURCE_EMBEDDED_TTML,$LYRIC_SOURCE_EMBEDDED_PLAIN,$LYRIC_SOURCE_EXTERNAL_TTML,$LYRIC_SOURCE_EXTERNAL_PLAIN"

        const val PLAYER_FLOW_EFFECT_DARK = 0
        const val APP_LANGUAGE_SYSTEM = "system"
        // Music-library source: the whole library (songs/artists/albums/genres/years) is served from
        // local storage, or streamed from a configured Navidrome / Emby / WebDAV server.
        const val LIBRARY_SOURCE_LOCAL = "local"
        const val LIBRARY_SOURCE_NAVIDROME = "navidrome"
        const val LIBRARY_SOURCE_OPENSUBSONIC = "opensubsonic"
        const val LIBRARY_SOURCE_EMBY = "emby"
        const val LIBRARY_SOURCE_WEBDAV = "webdav"

        fun normalizeLibrarySource(source: String): String = when (source) {
            LIBRARY_SOURCE_NAVIDROME -> LIBRARY_SOURCE_NAVIDROME
            LIBRARY_SOURCE_OPENSUBSONIC -> LIBRARY_SOURCE_OPENSUBSONIC
            LIBRARY_SOURCE_EMBY -> LIBRARY_SOURCE_EMBY
            LIBRARY_SOURCE_WEBDAV -> LIBRARY_SOURCE_WEBDAV
            else -> LIBRARY_SOURCE_LOCAL
        }

        const val APP_LANGUAGE_ZH_CN = "zh-CN"
        const val APP_LANGUAGE_ZH_TW = "zh-TW"
        const val APP_LANGUAGE_EN = "en"
        const val APP_LANGUAGE_JA = "ja"
        const val APP_LANGUAGE_KO = "ko"
        const val APP_LANGUAGE_DE = "de"
        const val APP_LANGUAGE_FR = "fr"
        const val APP_LANGUAGE_RU = "ru"
        const val APP_ICON_STYLE_DEFAULT = "default"
        const val APP_ICON_STYLE_ANIME = "anime"
        const val APP_ICON_STYLE_BLACK_HAIR = "black_hair"
        const val APP_ICON_STYLE_LOLI = "loli"
        const val BOTTOM_DOCK_ITEM_HOME = "home"
        const val BOTTOM_DOCK_ITEM_LIBRARY = "library"
        // Search stays as a fixed action pill outside the configurable dock tabs.
        const val BOTTOM_DOCK_ITEM_SEARCH = "search"
        const val BOTTOM_DOCK_ITEM_PLAYLISTS = "playlists"
        const val BOTTOM_DOCK_ITEM_FOLDER = "folder"
        const val BOTTOM_DOCK_ITEM_FOLDER_TREE = "folder_tree"
        const val BOTTOM_DOCK_ITEM_ARTIST = "artist"
        const val BOTTOM_DOCK_ITEM_ALBUM = "album"
        const val BOTTOM_DOCK_ITEM_SCAN_SETTINGS = "scan_settings"
        const val BOTTOM_DOCK_ITEM_SETTINGS = "settings"
        const val BOTTOM_DOCK_ITEM_YEAR = "year"
        const val BOTTOM_DOCK_ITEM_GENRE = "genre"
        const val BOTTOM_DOCK_ITEM_COMPOSER = "composer"
        const val BOTTOM_DOCK_ITEM_ARRANGER = "arranger"
        const val BOTTOM_DOCK_ITEM_LYRICIST = "lyricist"
        const val BOTTOM_DOCK_ITEM_ANALYTICS = "analytics"
        const val MAX_BOTTOM_DOCK_ITEMS = 4
        const val DEFAULT_BOTTOM_DOCK_ITEMS = "$BOTTOM_DOCK_ITEM_HOME,$BOTTOM_DOCK_ITEM_LIBRARY,$BOTTOM_DOCK_ITEM_SETTINGS,$BOTTOM_DOCK_ITEM_PLAYLISTS"
        const val DESKTOP_LYRIC_STATUS_POSITION_LEFT = 0
        const val DESKTOP_LYRIC_STATUS_POSITION_CENTER = 1
        const val DESKTOP_LYRIC_STATUS_POSITION_RIGHT = 2
        const val DESKTOP_LYRIC_STATUS_ALIGN_LEFT = 0
        const val DESKTOP_LYRIC_STATUS_ALIGN_CENTER = 1
        const val DESKTOP_LYRIC_STATUS_ALIGN_RIGHT = 2
        const val PLAYER_LYRIC_ALIGN_LEFT = 0
        const val PLAYER_LYRIC_ALIGN_CENTER = 1
        const val PLAYER_LYRIC_ALIGN_RIGHT = 2
        const val DESKTOP_LYRIC_STATUS_VERTICAL_TOP = 0
        const val DESKTOP_LYRIC_STATUS_VERTICAL_CENTER = 1
        const val DESKTOP_LYRIC_STATUS_VERTICAL_BOTTOM = 2
        const val DESKTOP_LYRIC_STATUS_SECONDARY_OFF = 0
        const val DESKTOP_LYRIC_STATUS_SECONDARY_TRANSLATION = 1
        const val DESKTOP_LYRIC_STATUS_SECONDARY_PRONUNCIATION = 2
        const val LYRIC_SECONDARY_OFF = 0
        const val LYRIC_SECONDARY_TRANSLATION = 1
        const val LYRIC_SECONDARY_PRONUNCIATION = 2
        const val MINI_PLAYER_RIGHT_NEXT = 0
        const val MINI_PLAYER_RIGHT_QUEUE = 1
        const val STARTUP_POSTER_DURATION_MIN_MS = 100
        const val STARTUP_POSTER_DURATION_MAX_MS = 3_000
        // Keep the established three-second default; the new control lets users shorten it.
        const val DEFAULT_STARTUP_POSTER_DURATION_MS = 3_000
        const val SONG_RATING_DISPLAY_STAR_NUMBER = 0
        const val SONG_RATING_DISPLAY_STARS = 1
        val SEARCH_ALL_CATEGORY_TYPES = setOf("folder", "composer", "arranger", "lyricist", "genre", "year")
        val SEARCH_ALL_SONG_MATCH_TYPES = setOf(
            "title", "artist", "album", "album_artist", "genre", "year", "composer", "arranger",
            "lyricist", "file_name", "comment", "alias", "tag", "lyrics"
        )

        const val DEFAULT_OPENAI_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_OPENAI_MODEL = "gpt-4.1-mini"
        const val DEFAULT_SHORTCUT_LIBRARY_LABEL = "音乐库"
        const val DEFAULT_SHORTCUT_PLAYLISTS_LABEL = "歌单"
        const val DEFAULT_SHORTCUT_FOLDER_LABEL = "文件夹"

        // Android 7.1+ dynamic shortcuts. Keep the identifiers independent from the screen
        // routes so an existing launcher shortcut does not change identity when a route evolves.
        const val APP_SHORTCUT_LIBRARY = "library"
        const val APP_SHORTCUT_SEARCH = "search"
        const val APP_SHORTCUT_PLAY = "play"
        const val APP_SHORTCUT_SHUFFLE_ALL = "shuffle_all"
        const val APP_SHORTCUT_PLAYLISTS = "playlists"
        const val APP_SHORTCUT_FOLDERS = "folders"
        const val APP_SHORTCUT_FOLDER_TREE = "folder_tree"
        const val APP_SHORTCUT_FOLDER_PLAYLISTS = "folder_playlists"
        const val APP_SHORTCUT_ALBUMS = "albums"
        const val APP_SHORTCUT_ARTISTS = "artists"
        const val APP_SHORTCUT_GENRES = "genres"
        const val APP_SHORTCUT_YEARS = "years"
        const val APP_SHORTCUT_COMPOSERS = "composers"
        const val APP_SHORTCUT_ARRANGERS = "arrangers"
        const val APP_SHORTCUT_LYRICISTS = "lyricists"
        const val APP_SHORTCUT_ANALYTICS = "analytics"
        const val APP_SHORTCUT_SCAN_SETTINGS = "scan_settings"
        const val APP_SHORTCUT_SETTINGS = "settings"
        const val MAX_APP_SHORTCUTS = 5
        val APP_SHORTCUT_IDS = listOf(
            APP_SHORTCUT_LIBRARY,
            APP_SHORTCUT_SEARCH,
            APP_SHORTCUT_PLAY,
            APP_SHORTCUT_SHUFFLE_ALL,
            APP_SHORTCUT_PLAYLISTS,
            APP_SHORTCUT_FOLDERS,
            APP_SHORTCUT_FOLDER_TREE,
            APP_SHORTCUT_FOLDER_PLAYLISTS,
            APP_SHORTCUT_ALBUMS,
            APP_SHORTCUT_ARTISTS,
            APP_SHORTCUT_GENRES,
            APP_SHORTCUT_YEARS,
            APP_SHORTCUT_COMPOSERS,
            APP_SHORTCUT_ARRANGERS,
            APP_SHORTCUT_LYRICISTS,
            APP_SHORTCUT_ANALYTICS,
            APP_SHORTCUT_SCAN_SETTINGS,
            APP_SHORTCUT_SETTINGS
        )
        val DEFAULT_APP_SHORTCUT_ORDER = listOf(
            APP_SHORTCUT_LIBRARY,
            APP_SHORTCUT_SEARCH,
            APP_SHORTCUT_PLAY,
            APP_SHORTCUT_SHUFFLE_ALL
        )

        @StringRes
        val DEFAULT_SHORTCUT_LIBRARY_LABEL_RES = R.string.settings_shortcut_library
        @StringRes
        val DEFAULT_SHORTCUT_PLAYLISTS_LABEL_RES = R.string.settings_shortcut_playlists
        @StringRes
        val DEFAULT_SHORTCUT_FOLDER_LABEL_RES = R.string.settings_shortcut_folder

        fun defaultShortcutLibraryLabel(context: Context): String =
            context.getString(DEFAULT_SHORTCUT_LIBRARY_LABEL_RES)

        fun defaultShortcutPlaylistsLabel(context: Context): String =
            context.getString(DEFAULT_SHORTCUT_PLAYLISTS_LABEL_RES)

        fun defaultShortcutFolderLabel(context: Context): String =
            context.getString(DEFAULT_SHORTCUT_FOLDER_LABEL_RES)
        const val DEFAULT_HOME_SECTION_ORDER = "library,online,recent"
        const val DEFAULT_HOME_LIBRARY_TILE_ORDER = "artist,album,folder,folder_tree,folder_playlist,playlist,analytics,genre,year,composer,arranger,lyricist"
        const val DEFAULT_HOME_ONLINE_TILE_ORDER = "lx,webdav"

        val LYRIC_SOURCE_PRIORITY_IDS = listOf(
            LYRIC_SOURCE_EMBEDDED_TTML,
            LYRIC_SOURCE_EMBEDDED_PLAIN,
            LYRIC_SOURCE_EXTERNAL_TTML,
            LYRIC_SOURCE_EXTERNAL_PLAIN
        )
        val BOTTOM_DOCK_ITEM_IDS = listOf(
            BOTTOM_DOCK_ITEM_HOME,
            BOTTOM_DOCK_ITEM_LIBRARY,
            BOTTOM_DOCK_ITEM_PLAYLISTS,
            BOTTOM_DOCK_ITEM_FOLDER,
            BOTTOM_DOCK_ITEM_FOLDER_TREE,
            BOTTOM_DOCK_ITEM_ARTIST,
            BOTTOM_DOCK_ITEM_ALBUM,
            BOTTOM_DOCK_ITEM_SCAN_SETTINGS,
            BOTTOM_DOCK_ITEM_SETTINGS,
            BOTTOM_DOCK_ITEM_YEAR,
            BOTTOM_DOCK_ITEM_GENRE,
            BOTTOM_DOCK_ITEM_COMPOSER,
            BOTTOM_DOCK_ITEM_ARRANGER,
            BOTTOM_DOCK_ITEM_LYRICIST,
            BOTTOM_DOCK_ITEM_ANALYTICS
        )

        fun normalizeLyricSourcePriority(value: String): String {
            val requested = value
                .split(',', '，', ';', '；')
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it in LYRIC_SOURCE_PRIORITY_IDS }
            return (requested + LYRIC_SOURCE_PRIORITY_IDS)
                .distinct()
                .joinToString(",")
        }

        fun normalizeBottomDockItems(value: String): String {
            val rawItems = value
                .split(',', '，', ';', '；', '\n')
                .map { it.trim().lowercase(Locale.ROOT) }
            val hadSearchSlot = rawItems.any { it == BOTTOM_DOCK_ITEM_SEARCH }
            val requested = rawItems
                .map { itemId ->
                    if (itemId == BOTTOM_DOCK_ITEM_SEARCH) {
                        BOTTOM_DOCK_ITEM_SETTINGS
                    } else {
                        itemId
                    }
                }
                .filter { it in BOTTOM_DOCK_ITEM_IDS }
                .distinct()
                .take(MAX_BOTTOM_DOCK_ITEMS)
            val defaults = DEFAULT_BOTTOM_DOCK_ITEMS.split(',')
            val migrated = if (hadSearchSlot && requested.size < MAX_BOTTOM_DOCK_ITEMS) {
                (requested + defaults)
                    .distinct()
                    .take(MAX_BOTTOM_DOCK_ITEMS)
            } else {
                requested
            }
            return migrated
                .ifEmpty { DEFAULT_BOTTOM_DOCK_ITEMS.split(',') }
                .joinToString(",")
        }

        fun normalizeAppShortcutOrder(value: String): List<String> =
            value
                .split(',', '，', ';', '；', '\n')
                .asSequence()
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it in APP_SHORTCUT_IDS }
                .distinct()
                .take(MAX_APP_SHORTCUTS)
                .toList()
    }

    private fun Int.coerceInOplusLyricMode(): Int =
        if (this == OPLUS_LYRIC_MODE_MODULE) {
            OPLUS_LYRIC_MODE_MODULE
        } else {
            OPLUS_LYRIC_MODE_SYSTEM
        }

    private fun metadataCategorySortKey(type: String): Preferences.Key<Int> =
        intPreferencesKey("sort_metadata_category_${type.safePreferenceSuffix()}")

    private fun metadataCategoryDetailSongSortKey(type: String): Preferences.Key<Int> =
        intPreferencesKey("sort_metadata_category_detail_song_${type.safePreferenceSuffix()}")

    private fun metadataCategoryDetailAlbumSortKey(type: String): Preferences.Key<Int> =
        intPreferencesKey("sort_metadata_category_detail_album_${type.safePreferenceSuffix()}")

    private fun String.safePreferenceSuffix(): String =
        lowercase().replace(Regex("[^a-z0-9_]+"), "_").trim('_').ifBlank { "unknown" }

    private val desktopLyricSettings = DesktopLyricSettings(context.dataStore)

    val desktopLyricEnabled get() = desktopLyricSettings.desktopLyricEnabled
    val desktopLyricHideWhenPaused get() = desktopLyricSettings.desktopLyricHideWhenPaused
    val desktopLyricHideInLandscape get() = desktopLyricSettings.desktopLyricHideInLandscape
    val desktopLyricStatusBarMode get() = desktopLyricSettings.desktopLyricStatusBarMode
    val desktopLyricStatusBarHideWhenPaused get() = desktopLyricSettings.desktopLyricStatusBarHideWhenPaused
    val desktopLyricStatusBarHideInLandscape get() = desktopLyricSettings.desktopLyricStatusBarHideInLandscape
    val desktopLyricWidth get() = desktopLyricSettings.desktopLyricWidth
    val desktopLyricStatusBarTopOffset get() = desktopLyricSettings.desktopLyricStatusBarTopOffset
    val desktopLyricStatusBarPosition get() = desktopLyricSettings.desktopLyricStatusBarPosition
    val desktopLyricStatusBarWidth get() = desktopLyricSettings.desktopLyricStatusBarWidth
    val desktopLyricStatusBarXOffset get() = desktopLyricSettings.desktopLyricStatusBarXOffset
    val desktopLyricStatusBarTextAlign get() = desktopLyricSettings.desktopLyricStatusBarTextAlign
    val desktopLyricStatusBarVerticalAlign get() = desktopLyricSettings.desktopLyricStatusBarVerticalAlign
    val desktopLyricStatusBarSecondary get() = desktopLyricSettings.desktopLyricStatusBarSecondary
    val desktopLyricStatusBarSecondaryOpacity get() = desktopLyricSettings.desktopLyricStatusBarSecondaryOpacity
    val desktopLyricStatusBarMergeSecondary get() = desktopLyricSettings.desktopLyricStatusBarMergeSecondary
    val desktopLyricStatusBarFontScale get() = desktopLyricSettings.desktopLyricStatusBarFontScale
    val desktopLyricStatusBarTranslationScale get() = desktopLyricSettings.desktopLyricStatusBarTranslationScale
    val desktopLyricStatusBarOpacity get() = desktopLyricSettings.desktopLyricStatusBarOpacity
    val desktopLyricStatusBarTextColor get() = desktopLyricSettings.desktopLyricStatusBarTextColor
    val desktopLyricLocked get() = desktopLyricSettings.desktopLyricLocked
    val desktopLyricFontScale get() = desktopLyricSettings.desktopLyricFontScale
    val desktopLyricTranslationScale get() = desktopLyricSettings.desktopLyricTranslationScale
    val desktopLyricOpacity get() = desktopLyricSettings.desktopLyricOpacity
    val desktopLyricTextColor get() = desktopLyricSettings.desktopLyricTextColor
    val desktopLyricX get() = desktopLyricSettings.desktopLyricX
    val desktopLyricY get() = desktopLyricSettings.desktopLyricY

    suspend fun setDesktopLyricEnabled(enabled: Boolean) = desktopLyricSettings.setDesktopLyricEnabled(enabled)
    suspend fun setDesktopLyricHideWhenPaused(enabled: Boolean) = desktopLyricSettings.setDesktopLyricHideWhenPaused(enabled)
    suspend fun setDesktopLyricHideInLandscape(enabled: Boolean) = desktopLyricSettings.setDesktopLyricHideInLandscape(enabled)
    suspend fun setDesktopLyricStatusBarMode(enabled: Boolean) = desktopLyricSettings.setDesktopLyricStatusBarMode(enabled)
    suspend fun setDesktopLyricStatusBarHideWhenPaused(enabled: Boolean) = desktopLyricSettings.setDesktopLyricStatusBarHideWhenPaused(enabled)
    suspend fun setDesktopLyricStatusBarHideInLandscape(enabled: Boolean) = desktopLyricSettings.setDesktopLyricStatusBarHideInLandscape(enabled)
    suspend fun setDesktopLyricWidth(widthPercent: Int) = desktopLyricSettings.setDesktopLyricWidth(widthPercent)
    suspend fun setDesktopLyricStatusBarTopOffset(offsetDp: Int) = desktopLyricSettings.setDesktopLyricStatusBarTopOffset(offsetDp)
    suspend fun setDesktopLyricStatusBarPosition(position: Int) = desktopLyricSettings.setDesktopLyricStatusBarPosition(position)
    suspend fun setDesktopLyricStatusBarWidth(widthPercent: Int) = desktopLyricSettings.setDesktopLyricStatusBarWidth(widthPercent)
    suspend fun setDesktopLyricStatusBarXOffset(offsetDp: Int) = desktopLyricSettings.setDesktopLyricStatusBarXOffset(offsetDp)
    suspend fun setDesktopLyricStatusBarTextAlign(align: Int) = desktopLyricSettings.setDesktopLyricStatusBarTextAlign(align)
    suspend fun setDesktopLyricStatusBarVerticalAlign(align: Int) = desktopLyricSettings.setDesktopLyricStatusBarVerticalAlign(align)
    suspend fun setDesktopLyricStatusBarSecondary(mode: Int) = desktopLyricSettings.setDesktopLyricStatusBarSecondary(mode)
    suspend fun setDesktopLyricStatusBarSecondaryOpacity(opacity: Int) = desktopLyricSettings.setDesktopLyricStatusBarSecondaryOpacity(opacity)
    suspend fun setDesktopLyricStatusBarMergeSecondary(enabled: Boolean) = desktopLyricSettings.setDesktopLyricStatusBarMergeSecondary(enabled)
    suspend fun setDesktopLyricStatusBarFontScale(scale: Int) = desktopLyricSettings.setDesktopLyricStatusBarFontScale(scale)
    suspend fun setDesktopLyricStatusBarTranslationScale(scale: Int) = desktopLyricSettings.setDesktopLyricStatusBarTranslationScale(scale)
    suspend fun setDesktopLyricStatusBarOpacity(opacity: Int) = desktopLyricSettings.setDesktopLyricStatusBarOpacity(opacity)
    suspend fun setDesktopLyricStatusBarTextColor(color: Int) = desktopLyricSettings.setDesktopLyricStatusBarTextColor(color)
    suspend fun setDesktopLyricLocked(locked: Boolean) = desktopLyricSettings.setDesktopLyricLocked(locked)
    suspend fun setDesktopLyricFontScale(scale: Int) = desktopLyricSettings.setDesktopLyricFontScale(scale)
    suspend fun setDesktopLyricTranslationScale(scale: Int) = desktopLyricSettings.setDesktopLyricTranslationScale(scale)
    suspend fun setDesktopLyricOpacity(opacity: Int) = desktopLyricSettings.setDesktopLyricOpacity(opacity)
    suspend fun setDesktopLyricTextColor(color: Int) = desktopLyricSettings.setDesktopLyricTextColor(color)
    suspend fun setDesktopLyricPosition(x: Int, y: Int) = desktopLyricSettings.setDesktopLyricPosition(x, y)
    suspend fun resetDesktopLyricPosition() = desktopLyricSettings.resetDesktopLyricPosition()

    val lyriconEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_ENABLED] ?: false }
    val lyriconTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_TRANSLATION] ?: true }
    val lyriconPronunciation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRICON_PRONUNCIATION] ?: false }
    val autoScan: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUTO_SCAN] ?: false }
    val autoScanLocalPlaylists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_SCAN_LOCAL_PLAYLISTS] ?: false }
    val gaplessPlayback: Flow<Boolean> = context.dataStore.data.map { it[KEY_GAPLESS] ?: true }
    val crossfadeDurationMs: Flow<Int> = context.dataStore.data
        .map { (it[KEY_CROSSFADE_DURATION_MS] ?: 0).coerceIn(0, 12_000) }
    val themeMode: Flow<Int> = context.dataStore.data.map { it[KEY_THEME_MODE] ?: 0 }
    val monetColorMode: Flow<Int> = context.dataStore.data.map { it[KEY_MONET_COLOR_MODE] ?: 0 }
    val playerBackgroundTheme: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BACKGROUND_THEME] ?: PLAYER_BG_THEME_FOLLOW_SYSTEM }
    val appLanguage: Flow<String> =
        context.dataStore.data.map { it[KEY_APP_LANGUAGE] ?: APP_LANGUAGE_SYSTEM }
    val appIconStyle: Flow<String> =
        context.dataStore.data.map { AppIconManager.normalize(it[KEY_APP_ICON_STYLE]) }
    val bottomBarGlassEffect: Flow<BottomBarGlassEffect> = context.dataStore.data.map { preferences ->
        runCatching {
            BottomBarGlassEffect.valueOf(
                preferences[KEY_BOTTOM_BAR_GLASS_EFFECT] ?: BottomBarGlassEffect.LiquidGlass.name
            )
        }.getOrDefault(BottomBarGlassEffect.LiquidGlass)
    }
    val bottomDockItems: Flow<List<String>> =
        context.dataStore.data.map {
            normalizeBottomDockItems(it[KEY_BOTTOM_DOCK_ITEMS] ?: DEFAULT_BOTTOM_DOCK_ITEMS)
                .split(',')
                .filter(String::isNotBlank)
        }
    val tickerEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_ENABLED] ?: false }
    val tickerHideNotification: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_HIDE_NOTIFICATION] ?: true }
    val tickerHeadsUpLyrics: Flow<Boolean> = context.dataStore.data.map { it[KEY_TICKER_HEADS_UP_LYRICS] ?: false }
    val samsungFloatingLyricTranslation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION] ?: false }
    val statusBarAllowPhonetic: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_STATUS_BAR_ALLOW_PHONETIC] ?: false }
    val superLyricEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUPER_LYRIC_ENABLED] ?: false }
    val superLyricTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUPER_LYRIC_TRANSLATION] ?: true }
    val superLyricPronunciation: Flow<Boolean> = context.dataStore.data.map { it[KEY_SUPER_LYRIC_PRONUNCIATION] ?: false }
    val lyricGetterEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_GETTER_ENABLED] ?: false }
    val minDurationSec: Flow<Int> = context.dataStore.data.map { it[KEY_MIN_DURATION] ?: 15 }
    val replayGainEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_REPLAYGAIN_ENABLED] ?: false }
    val replayGainMode: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_REPLAYGAIN_MODE]
            ?: if (preferences[KEY_REPLAYGAIN_ENABLED] == true) REPLAY_GAIN_AUTO else REPLAY_GAIN_OFF
    }.map { it.coerceIn(REPLAY_GAIN_OFF, REPLAY_GAIN_AUTO) }
    val resumePlaybackPosition: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_RESUME_PLAYBACK_POSITION] ?: false }
    val audioFocusDisabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_AUDIO_FOCUS_DISABLED] ?: false }
    val audioOutputBackend: Flow<Int> =
        context.dataStore.data.map { normalizeAudioOutputBackend(it[KEY_AUDIO_OUTPUT_BACKEND]) }
    val audioOutputBitDepth: Flow<Int> =
        context.dataStore.data.map { normalizeAudioOutputBitDepth(it[KEY_AUDIO_OUTPUT_BIT_DEPTH]) }
    val audioOutputSampleRate: Flow<Int> =
        context.dataStore.data.map { normalizeAudioOutputSampleRate(it[KEY_AUDIO_OUTPUT_SAMPLE_RATE]) }
    val playbackOutputSettings: Flow<PlaybackOutputSettings> = combine(
        audioOutputBackend,
        audioOutputBitDepth,
        audioOutputSampleRate,
        context.dataStore.data.map { it[KEY_USB_DAC_MODE] ?: false }
    ) { backend, bitDepth, sampleRate, usbExclusive ->
        PlaybackOutputSettings(
            backend = backend,
            bitDepth = bitDepth,
            sampleRate = sampleRate,
            usbExclusive = usbExclusive
        )
    }
    val shuffleMode: Flow<Int> =
        context.dataStore.data.map { it[KEY_SHUFFLE_MODE] ?: SHUFFLE_MODE_PSEUDO }
    val previousButtonAction: Flow<Int> =
        context.dataStore.data.map { it[KEY_PREVIOUS_BUTTON_ACTION] ?: PREVIOUS_BUTTON_PREVIOUS }
    val lyricSourceMode: Flow<Int> =
        context.dataStore.data.map { it[KEY_LYRIC_SOURCE_MODE] ?: LYRIC_SOURCE_AUTO }
    val lyricSourcePriority: Flow<String> =
        context.dataStore.data.map {
            normalizeLyricSourcePriority(it[KEY_LYRIC_SOURCE_PRIORITY] ?: DEFAULT_LYRIC_SOURCE_PRIORITY)
        }
    val lyricoPluginEnabledIds: Flow<Set<String>> =
        context.dataStore.data.map { LyricoPluginManager.normalizeEnabledIds(it[KEY_LYRICO_PLUGIN_ENABLED_IDS]) }
    val ignoreLyricHeaderTags: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_IGNORE_LYRIC_HEADER_TAGS] ?: true }
    val lyricLineBlacklist: Flow<List<String>> =
        context.dataStore.data.map { parseLyricLineBlacklist(it[KEY_LYRIC_LINE_BLACKLIST]) }
    val lyricOffsetOverrides: Flow<Map<String, Long>> =
        context.dataStore.data.map { parseLyricOffsetOverrides(it[KEY_LYRIC_OFFSET_OVERRIDES]) }
    val playerLyricTextAlign: Flow<Int> =
        context.dataStore.data.map { (it[KEY_PLAYER_LYRIC_TEXT_ALIGN] ?: PLAYER_LYRIC_ALIGN_LEFT).coerceIn(0, 2) }
    // Whether romaji / phonetic guides render BELOW the main lyric line (main → romaji → translation)
    // instead of above it. Default false = above.
    val lyricPronunciationBelow: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LYRIC_PRONUNCIATION_BELOW] ?: false }
    val lyricPageTranslation: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_PAGE_TRANSLATION] ?: true }
    val lyricPageKeepScreenOn: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LYRIC_PAGE_KEEP_SCREEN_ON] ?: false }
    val appleMusicLyricsWordLift: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_APPLE_MUSIC_LYRICS_WORD_LIFT] ?: true }
    val miniPlayerLyricTranslation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_LYRIC_TRANSLATION] ?: true }
    val miniPlayerLyricSecondary: Flow<Int> = context.dataStore.data.map {
        (it[KEY_MINI_PLAYER_LYRIC_SECONDARY]
            ?: if (it[KEY_MINI_PLAYER_LYRIC_TRANSLATION] == false) {
                LYRIC_SECONDARY_OFF
            } else {
                LYRIC_SECONDARY_TRANSLATION
            }).coerceIn(LYRIC_SECONDARY_OFF, LYRIC_SECONDARY_PRONUNCIATION)
    }
    val miniPlayerCoverRotation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_COVER_ROTATION] ?: true }

    val miniPlayerLyricsEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_LYRICS_ENABLED] ?: true }
    val miniPlayerRightButton: Flow<Int> =
        context.dataStore.data.map { it[KEY_MINI_PLAYER_RIGHT_BUTTON] ?: MINI_PLAYER_RIGHT_NEXT }
    val playerProgressInfoIndex: Flow<Int> =
        context.dataStore.data.map { (it[KEY_PLAYER_PROGRESS_INFO_INDEX] ?: 0).coerceAtLeast(0) }
    val transportButtonOutlines: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_TRANSPORT_BUTTON_OUTLINES] ?: false }
    val playerTapSeekEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_TAP_SEEK_ENABLED] ?: true }
    val playerShowTotalDuration: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_SHOW_TOTAL_DURATION] ?: false }
    val playerShowSongAnnotation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_SHOW_SONG_ANNOTATION] ?: true }
    val playerCoverSwipeEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_COVER_SWIPE_ENABLED] ?: false }

    val lyricParserEngine: Flow<Int> =
        context.dataStore.data.map { it[KEY_LYRIC_PARSER_ENGINE] ?: LYRIC_PARSER_ENGINE_ELLA }
    val playerTitlePosition: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_PLAYER_TITLE_POSITION] ?: PLAYER_TITLE_POSITION_BELOW_COVER)
                .coerceIn(PLAYER_TITLE_POSITION_BELOW_COVER, PLAYER_TITLE_POSITION_ABOVE_COVER)
        }
    val playerKeepScreenOn: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_KEEP_SCREEN_ON] ?: false }
    val playerHdrGlow: Flow<Boolean> = context.dataStore.data.map { it[KEY_PLAYER_HDR_GLOW] ?: false }
    val playerImmersiveCover: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_IMMERSIVE_COVER] ?: false }
    val playerCoverContentColor: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_COVER_CONTENT_COLOR] ?: true }

    val hideSystemBars: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HIDE_SYSTEM_BARS] ?: false }
    val playerDynamicFlowEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_DYNAMIC_FLOW_ENABLED] ?: false }
    val audioVisualizerEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUDIO_VISUALIZER_ENABLED] ?: false }
    val audioVisualizerOpacity: Flow<Int> =
        context.dataStore.data.map { it[KEY_AUDIO_VISUALIZER_OPACITY]?.coerceIn(20, 100) ?: 100 }

    val eqEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_EQ_ENABLED] ?: false }
    val eqPreset: Flow<Int> =
        context.dataStore.data.map { it[KEY_EQ_PRESET] ?: AudioEffectSettings.PRESET_CUSTOM }
    val eqBandLevelsMb: Flow<List<Int>> =
        context.dataStore.data.map { parseEqBands(it[KEY_EQ_BANDS]) }
    val bassBoostEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BASS_BOOST_ENABLED] ?: false }
    val bassBoostStrength: Flow<Int> =
        context.dataStore.data.map { it[KEY_BASS_BOOST_STRENGTH] ?: 0 }
    val virtualizerEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_VIRTUALIZER_ENABLED] ?: false }
    val virtualizerStrength: Flow<Int> =
        context.dataStore.data.map { it[KEY_VIRTUALIZER_STRENGTH] ?: 0 }
    val reverbPreset: Flow<Int> =
        context.dataStore.data.map { normalizeReverbPreset(it[KEY_REVERB_PRESET] ?: AudioEffectSettings.REVERB_PRESET_OFF) }
    val eqQ: Flow<Int> =
        context.dataStore.data.map { (it[KEY_EQ_Q] ?: AudioEffectSettings.EQ_Q_DEFAULT).coerceIn(AudioEffectSettings.EQ_Q_MIN, AudioEffectSettings.EQ_Q_MAX) }
    val toneBassDb: Flow<Int> =
        context.dataStore.data.map { (it[KEY_TONE_BASS_DB] ?: 0).coerceIn(AudioEffectSettings.TONE_GAIN_MIN_DB, AudioEffectSettings.TONE_GAIN_MAX_DB) }
    val toneTrebleDb: Flow<Int> =
        context.dataStore.data.map { (it[KEY_TONE_TREBLE_DB] ?: 0).coerceIn(AudioEffectSettings.TONE_GAIN_MIN_DB, AudioEffectSettings.TONE_GAIN_MAX_DB) }
    val compressorEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_COMP_ENABLED] ?: false }
    val compressorThresholdDb: Flow<Int> =
        context.dataStore.data.map { (it[KEY_COMP_THRESHOLD_DB] ?: -18).coerceIn(AudioEffectSettings.COMP_THRESHOLD_MIN_DB, AudioEffectSettings.COMP_THRESHOLD_MAX_DB) }
    val compressorRatio: Flow<Int> =
        context.dataStore.data.map { (it[KEY_COMP_RATIO] ?: 2).coerceIn(AudioEffectSettings.COMP_RATIO_MIN, AudioEffectSettings.COMP_RATIO_MAX) }
    val compressorMakeupDb: Flow<Int> =
        context.dataStore.data.map { (it[KEY_COMP_MAKEUP_DB] ?: 0).coerceIn(AudioEffectSettings.COMP_MAKEUP_MIN_DB, AudioEffectSettings.COMP_MAKEUP_MAX_DB) }
    val stereoWidth: Flow<Int> =
        context.dataStore.data.map { (it[KEY_STEREO_WIDTH] ?: 100).coerceIn(AudioEffectSettings.STEREO_WIDTH_MIN, AudioEffectSettings.STEREO_WIDTH_MAX) }
    val surround360Enabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SURROUND_360_ENABLED] ?: false }
    val surround360Intensity: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_SURROUND_360_INTENSITY] ?: 50).coerceIn(
                AudioEffectSettings.SURROUND_360_INTENSITY_MIN,
                AudioEffectSettings.SURROUND_360_INTENSITY_MAX
            )
        }
    val surround360RotationSpeed: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_SURROUND_360_ROTATION_SPEED] ?: 30).coerceIn(
                AudioEffectSettings.SURROUND_360_ROTATION_MIN,
                AudioEffectSettings.SURROUND_360_ROTATION_MAX
            )
        }
    val panoramic360Enabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PANORAMIC_360_ENABLED] ?: false }
    val panoramic360Intensity: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_PANORAMIC_360_INTENSITY] ?: 50).coerceIn(
                AudioEffectSettings.PANORAMIC_360_INTENSITY_MIN,
                AudioEffectSettings.PANORAMIC_360_INTENSITY_MAX
            )
        }
    val panoramic360AzimuthDegrees: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_PANORAMIC_360_AZIMUTH_DEGREES] ?: 0).coerceIn(
                AudioEffectSettings.PANORAMIC_360_AZIMUTH_MIN,
                AudioEffectSettings.PANORAMIC_360_AZIMUTH_MAX
            )
        }
    val panoramic360ElevationDegrees: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_PANORAMIC_360_ELEVATION_DEGREES] ?: 0).coerceIn(
                AudioEffectSettings.PANORAMIC_360_ELEVATION_MIN,
                AudioEffectSettings.PANORAMIC_360_ELEVATION_MAX
            )
        }
    val loudnessBalanceEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LOUDNESS_BALANCE_ENABLED] ?: false }
    val loudnessPercent: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_LOUDNESS_PERCENT] ?: 35).coerceIn(
                AudioEffectSettings.LOUDNESS_PERCENT_MIN,
                AudioEffectSettings.LOUDNESS_PERCENT_MAX
            )
        }
    val channelBalance: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_CHANNEL_BALANCE] ?: 0).coerceIn(
                AudioEffectSettings.CHANNEL_BALANCE_MIN,
                AudioEffectSettings.CHANNEL_BALANCE_MAX
            )
        }
    val crossfeedEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_CROSSFEED_ENABLED] ?: false }
    val crossfeedLowCutHz: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_CROSSFEED_LOW_CUT_HZ] ?: 300).coerceIn(
                AudioEffectSettings.CROSSFEED_LOW_CUT_MIN_HZ,
                AudioEffectSettings.CROSSFEED_LOW_CUT_MAX_HZ
            )
        }
    val crossfeedHighCutHz: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_CROSSFEED_HIGH_CUT_HZ] ?: 2_000).coerceIn(
                AudioEffectSettings.CROSSFEED_HIGH_CUT_MIN_HZ,
                AudioEffectSettings.CROSSFEED_HIGH_CUT_MAX_HZ
            )
        }
    val crossfeedAttenuationTenthsDb: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_CROSSFEED_ATTENUATION_TENTHS_DB] ?: 60).coerceIn(
                AudioEffectSettings.CROSSFEED_ATTENUATION_MIN_TENTHS_DB,
                AudioEffectSettings.CROSSFEED_ATTENUATION_MAX_TENTHS_DB
            )
        }
    val monoBassEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MONO_BASS_ENABLED] ?: false }
    val monoBassCrossoverHz: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MONO_BASS_CROSSOVER_HZ] ?: 120).coerceIn(
                AudioEffectSettings.MONO_BASS_CROSSOVER_MIN_HZ,
                AudioEffectSettings.MONO_BASS_CROSSOVER_MAX_HZ
            )
        }
    val monoBassAmount: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MONO_BASS_AMOUNT] ?: 100).coerceIn(
                AudioEffectSettings.MONO_BASS_AMOUNT_MIN,
                AudioEffectSettings.MONO_BASS_AMOUNT_MAX
            )
        }
    val speakerOutputEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SPEAKER_OUTPUT_ENABLED] ?: false }
    val speakerOutputMode: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_SPEAKER_OUTPUT_MODE] ?: AudioEffectSettings.SPEAKER_OUTPUT_MODE_ELASTICITY).coerceIn(
                AudioEffectSettings.SPEAKER_OUTPUT_MODE_ELASTICITY,
                AudioEffectSettings.SPEAKER_OUTPUT_MODE_WIDE
            )
        }
    val speakerOutputStrength: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_SPEAKER_OUTPUT_STRENGTH] ?: 82).coerceIn(
                AudioEffectSettings.SPEAKER_OUTPUT_STRENGTH_MIN,
                AudioEffectSettings.SPEAKER_OUTPUT_STRENGTH_MAX
            )
        }
    val dynamicEqEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DYNAMIC_EQ_ENABLED] ?: false }
    val dynamicEqIntensity: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_DYNAMIC_EQ_INTENSITY] ?: 50).coerceIn(
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MIN,
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MAX
            )
        }
    val deEsserAmount: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_DE_ESSER_AMOUNT] ?: 45).coerceIn(
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MIN,
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MAX
            )
        }
    val deEsserFrequencyHz: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_DE_ESSER_FREQUENCY_HZ] ?: 6_500).coerceIn(
                AudioEffectSettings.DE_ESSER_FREQUENCY_MIN_HZ,
                AudioEffectSettings.DE_ESSER_FREQUENCY_MAX_HZ
            )
        }
    val moogLadderEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MOOG_LADDER_ENABLED] ?: false }
    val moogLadderMode: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MOOG_LADDER_MODE] ?: AudioEffectSettings.MOOG_LADDER_MODE_LOW_PASS_24).coerceIn(
                AudioEffectSettings.MOOG_LADDER_MODE_LOW_PASS_24,
                AudioEffectSettings.MOOG_LADDER_MODE_NOTCH
            )
        }
    val moogLadderCutoffHz: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MOOG_LADDER_CUTOFF_HZ] ?: 12_000).coerceIn(
                AudioEffectSettings.MOOG_LADDER_CUTOFF_MIN_HZ,
                AudioEffectSettings.MOOG_LADDER_CUTOFF_MAX_HZ
            )
        }
    val moogLadderResonance: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MOOG_LADDER_RESONANCE] ?: 20).coerceIn(
                AudioEffectSettings.MOOG_LADDER_RESONANCE_MIN,
                AudioEffectSettings.MOOG_LADDER_RESONANCE_MAX
            )
        }
    val moogLadderDriveDb: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MOOG_LADDER_DRIVE_DB] ?: 0).coerceIn(
                AudioEffectSettings.MOOG_LADDER_DRIVE_MIN_DB,
                AudioEffectSettings.MOOG_LADDER_DRIVE_MAX_DB
            )
        }
    val moogLadderMix: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_MOOG_LADDER_MIX] ?: 100).coerceIn(
                AudioEffectSettings.MOOG_LADDER_MIX_MIN,
                AudioEffectSettings.MOOG_LADDER_MIX_MAX
            )
        }
    val peakLimiterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PEAK_LIMITER_ENABLED] ?: true }
    val platformSpatialAudioEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLATFORM_SPATIAL_AUDIO_ENABLED] ?: false }
    private data class CustomDspSettings(
        val eqQ: Int,
        val bassDb: Int,
        val trebleDb: Int,
        val compEnabled: Boolean,
        val compThresholdDb: Int,
        val compRatio: Int,
        val compMakeupDb: Int,
        val stereoWidth: Int,
        val surround360Enabled: Boolean,
        val surround360Intensity: Int,
        val surround360RotationSpeed: Int,
        val panoramic360Enabled: Boolean,
        val panoramic360Intensity: Int,
        val panoramic360AzimuthDegrees: Int,
        val panoramic360ElevationDegrees: Int,
        val loudnessBalanceEnabled: Boolean,
        val loudnessPercent: Int,
        val channelBalance: Int,
        val crossfeedEnabled: Boolean,
        val crossfeedLowCutHz: Int,
        val crossfeedHighCutHz: Int,
        val crossfeedAttenuationTenthsDb: Int,
        val monoBassEnabled: Boolean,
        val monoBassCrossoverHz: Int,
        val monoBassAmount: Int,
        val speakerOutputEnabled: Boolean,
        val speakerOutputMode: Int,
        val speakerOutputStrength: Int,
        val dynamicEqEnabled: Boolean,
        val dynamicEqIntensity: Int,
        val deEsserAmount: Int,
        val deEsserFrequencyHz: Int,
        val moogLadderEnabled: Boolean,
        val moogLadderMode: Int,
        val moogLadderCutoffHz: Int,
        val moogLadderResonance: Int,
        val moogLadderDriveDb: Int,
        val moogLadderMix: Int,
        val peakLimiterEnabled: Boolean
    )
    private data class SpatialEnhancementSettings(
        val loudnessBalanceEnabled: Boolean,
        val loudnessPercent: Int,
        val channelBalance: Int,
        val crossfeedEnabled: Boolean,
        val crossfeedLowCutHz: Int,
        val crossfeedHighCutHz: Int,
        val crossfeedAttenuationTenthsDb: Int,
        val monoBassEnabled: Boolean,
        val monoBassCrossoverHz: Int,
        val monoBassAmount: Int,
        val speakerOutputEnabled: Boolean,
        val speakerOutputMode: Int,
        val speakerOutputStrength: Int,
        val dynamicEqEnabled: Boolean,
        val dynamicEqIntensity: Int,
        val deEsserAmount: Int,
        val deEsserFrequencyHz: Int,
        val moogLadderEnabled: Boolean,
        val moogLadderMode: Int,
        val moogLadderCutoffHz: Int,
        val moogLadderResonance: Int,
        val moogLadderDriveDb: Int,
        val moogLadderMix: Int,
        val peakLimiterEnabled: Boolean
    )
    private val spatialEnhancements: Flow<SpatialEnhancementSettings> = combine(
        combine(loudnessBalanceEnabled, loudnessPercent, channelBalance) { enabled, loudness, balance ->
            Triple(enabled, loudness, balance)
        },
        combine(crossfeedEnabled, crossfeedLowCutHz, crossfeedHighCutHz, crossfeedAttenuationTenthsDb) { enabled, low, high, attenuation ->
            listOf(if (enabled) 1 else 0, low, high, attenuation)
        },
        combine(monoBassEnabled, monoBassCrossoverHz, monoBassAmount) { enabled, crossover, amount ->
            Triple(enabled, crossover, amount)
        },
        combine(speakerOutputEnabled, speakerOutputMode, speakerOutputStrength) { enabled, mode, strength ->
            Triple(enabled, mode, strength)
        },
        combine(
            combine(dynamicEqEnabled, dynamicEqIntensity, deEsserAmount, deEsserFrequencyHz) { enabled, intensity, deEsser, frequency ->
                listOf(if (enabled) 1 else 0, intensity, deEsser, frequency)
            },
            combine(moogLadderEnabled, moogLadderMode, moogLadderCutoffHz) { enabled, mode, cutoff ->
                listOf(if (enabled) 1 else 0, mode, cutoff)
            },
            combine(moogLadderResonance, moogLadderDriveDb, moogLadderMix) { resonance, drive, mix ->
                listOf(resonance, drive, mix)
            },
            peakLimiterEnabled
        ) { dynamicEq, moogPrimary, moogSecondary, limiter ->
            listOf(
                dynamicEq[0], dynamicEq[1], dynamicEq[2], dynamicEq[3],
                moogPrimary[0], moogPrimary[1], moogPrimary[2], moogSecondary[0], moogSecondary[1], moogSecondary[2],
                if (limiter) 1 else 0
            )
        }
    ) { loudness, crossfeed, monoBass, speaker, dynamicEq ->
        SpatialEnhancementSettings(
            loudnessBalanceEnabled = loudness.first,
            loudnessPercent = loudness.second,
            channelBalance = loudness.third,
            crossfeedEnabled = crossfeed[0] == 1,
            crossfeedLowCutHz = crossfeed[1],
            crossfeedHighCutHz = crossfeed[2],
            crossfeedAttenuationTenthsDb = crossfeed[3],
            monoBassEnabled = monoBass.first,
            monoBassCrossoverHz = monoBass.second,
            monoBassAmount = monoBass.third,
            speakerOutputEnabled = speaker.first,
            speakerOutputMode = speaker.second,
            speakerOutputStrength = speaker.third,
            dynamicEqEnabled = dynamicEq[0] == 1,
            dynamicEqIntensity = dynamicEq[1],
            deEsserAmount = dynamicEq[2],
            deEsserFrequencyHz = dynamicEq[3],
            moogLadderEnabled = dynamicEq[4] == 1,
            moogLadderMode = dynamicEq[5],
            moogLadderCutoffHz = dynamicEq[6],
            moogLadderResonance = dynamicEq[7],
            moogLadderDriveDb = dynamicEq[8],
            moogLadderMix = dynamicEq[9],
            peakLimiterEnabled = dynamicEq[10] == 1
        )
    }
    private val toneAndDynamics: Flow<CustomDspSettings> = combine(
        combine(eqQ, toneBassDb, toneTrebleDb) { q, bass, treble -> Triple(q, bass, treble) },
        combine(compressorEnabled, compressorThresholdDb, compressorRatio, compressorMakeupDb) { en, th, ra, mk ->
            listOf(if (en) 1 else 0, th, ra, mk)
        },
        stereoWidth,
        combine(
            combine(surround360Enabled, surround360Intensity, surround360RotationSpeed) { enabled, intensity, speed ->
                Triple(enabled, intensity, speed)
            },
            combine(
                panoramic360Enabled,
                panoramic360Intensity,
                panoramic360AzimuthDegrees,
                panoramic360ElevationDegrees
            ) { enabled, intensity, azimuth, elevation ->
                listOf(if (enabled) 1 else 0, intensity, azimuth, elevation)
            }
        ) { surround, panoramic -> surround to panoramic
        },
        spatialEnhancements
    ) { tone, comp, width, spatial, enhancements ->
        val surround = spatial.first
        val panoramic = spatial.second
        CustomDspSettings(
            eqQ = tone.first,
            bassDb = tone.second,
            trebleDb = tone.third,
            compEnabled = comp[0] == 1,
            compThresholdDb = comp[1],
            compRatio = comp[2],
            compMakeupDb = comp[3],
            stereoWidth = width,
            surround360Enabled = surround.first,
            surround360Intensity = surround.second,
            surround360RotationSpeed = surround.third,
            panoramic360Enabled = panoramic[0] == 1,
            panoramic360Intensity = panoramic[1],
            panoramic360AzimuthDegrees = panoramic[2],
            panoramic360ElevationDegrees = panoramic[3],
            loudnessBalanceEnabled = enhancements.loudnessBalanceEnabled,
            loudnessPercent = enhancements.loudnessPercent,
            channelBalance = enhancements.channelBalance,
            crossfeedEnabled = enhancements.crossfeedEnabled,
            crossfeedLowCutHz = enhancements.crossfeedLowCutHz,
            crossfeedHighCutHz = enhancements.crossfeedHighCutHz,
            crossfeedAttenuationTenthsDb = enhancements.crossfeedAttenuationTenthsDb,
            monoBassEnabled = enhancements.monoBassEnabled,
            monoBassCrossoverHz = enhancements.monoBassCrossoverHz,
            monoBassAmount = enhancements.monoBassAmount,
            speakerOutputEnabled = enhancements.speakerOutputEnabled,
            speakerOutputMode = enhancements.speakerOutputMode,
            speakerOutputStrength = enhancements.speakerOutputStrength,
            dynamicEqEnabled = enhancements.dynamicEqEnabled,
            dynamicEqIntensity = enhancements.dynamicEqIntensity,
            deEsserAmount = enhancements.deEsserAmount,
            deEsserFrequencyHz = enhancements.deEsserFrequencyHz,
            moogLadderEnabled = enhancements.moogLadderEnabled,
            moogLadderMode = enhancements.moogLadderMode,
            moogLadderCutoffHz = enhancements.moogLadderCutoffHz,
            moogLadderResonance = enhancements.moogLadderResonance,
            moogLadderDriveDb = enhancements.moogLadderDriveDb,
            moogLadderMix = enhancements.moogLadderMix,
            peakLimiterEnabled = enhancements.peakLimiterEnabled
        )
    }
    val usbDacMode: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_USB_DAC_MODE] ?: false }

    /** Combined audio-effect snapshot consumed by PlaybackService's AudioEffectController. */
    val audioEffectSettings: Flow<AudioEffectSettings> = combine(
        combine(eqEnabled, eqPreset, eqBandLevelsMb) { enabled, preset, bands ->
            Triple(enabled, preset, bands)
        },
        combine(bassBoostEnabled, bassBoostStrength) { enabled, strength -> enabled to strength },
        combine(virtualizerEnabled, virtualizerStrength) { enabled, strength -> enabled to strength },
        reverbPreset,
        toneAndDynamics
    ) { eq, bass, virt, reverb, dsp ->
        AudioEffectSettings(
            eqEnabled = eq.first,
            eqPreset = eq.second,
            eqBandLevelsMb = eq.third,
            eqQ = dsp.eqQ,
            bassGainDb = dsp.bassDb,
            trebleGainDb = dsp.trebleDb,
            compressorEnabled = dsp.compEnabled,
            compressorThresholdDb = dsp.compThresholdDb,
            compressorRatio = dsp.compRatio,
            compressorMakeupDb = dsp.compMakeupDb,
            stereoWidth = dsp.stereoWidth,
            surround360Enabled = dsp.surround360Enabled,
            surround360Intensity = dsp.surround360Intensity,
            surround360RotationSpeed = dsp.surround360RotationSpeed,
            panoramic360Enabled = dsp.panoramic360Enabled,
            panoramic360Intensity = dsp.panoramic360Intensity,
            panoramic360AzimuthDegrees = dsp.panoramic360AzimuthDegrees,
            panoramic360ElevationDegrees = dsp.panoramic360ElevationDegrees,
            loudnessBalanceEnabled = dsp.loudnessBalanceEnabled,
            loudnessPercent = dsp.loudnessPercent,
            channelBalance = dsp.channelBalance,
            crossfeedEnabled = dsp.crossfeedEnabled,
            crossfeedLowCutHz = dsp.crossfeedLowCutHz,
            crossfeedHighCutHz = dsp.crossfeedHighCutHz,
            crossfeedAttenuationDbTenths = dsp.crossfeedAttenuationTenthsDb,
            monoBassEnabled = dsp.monoBassEnabled,
            monoBassCrossoverHz = dsp.monoBassCrossoverHz,
            monoBassAmount = dsp.monoBassAmount,
            speakerOutputEnabled = dsp.speakerOutputEnabled,
            speakerOutputMode = dsp.speakerOutputMode,
            speakerOutputStrength = dsp.speakerOutputStrength,
            dynamicEqEnabled = dsp.dynamicEqEnabled,
            dynamicEqIntensity = dsp.dynamicEqIntensity,
            deEsserAmount = dsp.deEsserAmount,
            deEsserFrequencyHz = dsp.deEsserFrequencyHz,
            moogLadderEnabled = dsp.moogLadderEnabled,
            moogLadderMode = dsp.moogLadderMode,
            moogLadderCutoffHz = dsp.moogLadderCutoffHz,
            moogLadderResonance = dsp.moogLadderResonance,
            moogLadderDriveDb = dsp.moogLadderDriveDb,
            moogLadderMix = dsp.moogLadderMix,
            peakLimiterEnabled = dsp.peakLimiterEnabled,
            bassBoostEnabled = bass.first,
            bassBoostStrength = bass.second,
            virtualizerEnabled = virt.first,
            virtualizerStrength = virt.second,
            reverbPreset = reverb
        )
    }
    val dynamicCoverEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_DYNAMIC_COVER_ENABLED] ?: false }
    val musicVideoSyncEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MUSIC_VIDEO_SYNC_ENABLED] ?: false }
    val musicVideoCaptureSubtitles: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MUSIC_VIDEO_CAPTURE_SUBTITLES] ?: false }
    val dynamicCoverCustomFoldersRaw: Flow<String> =
        context.dataStore.data.map { normalizeDynamicCoverCustomFolders(it[KEY_DYNAMIC_COVER_CUSTOM_FOLDERS]) }
    val dynamicCoverCustomFolders: Flow<List<String>> =
        dynamicCoverCustomFoldersRaw.map(::parseDynamicCoverCustomFolders)
    val artistCoverFolderUri: Flow<String> =
        context.dataStore.data.map { it[KEY_ARTIST_COVER_FOLDER_URI].orEmpty() }
    // 当某位艺术家有多张封面图时：true=多图轮播，false=随机取一张。
    val artistCoverCarousel: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ARTIST_COVER_CAROUSEL] ?: true }
    val mcpServerEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_MCP_SERVER_ENABLED] ?: false }
    val startupPosterEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_STARTUP_POSTER_ENABLED] ?: false }
    val startupPosterUri: Flow<String> =
        context.dataStore.data.map { it[KEY_STARTUP_POSTER_URI] ?: "" }
    val startupPosterDurationMs: Flow<Int> =
        context.dataStore.data.map {
            (it[KEY_STARTUP_POSTER_DURATION_MS] ?: DEFAULT_STARTUP_POSTER_DURATION_MS)
                .coerceIn(STARTUP_POSTER_DURATION_MIN_MS, STARTUP_POSTER_DURATION_MAX_MS)
        }
    val appWallpaperEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_APP_WALLPAPER_ENABLED] ?: false }
    val appWallpaperUri: Flow<String> =
        context.dataStore.data.map { it[KEY_APP_WALLPAPER_URI] ?: "" }
    val appWallpaperOpacity: Flow<Int> =
        context.dataStore.data.map { it[KEY_APP_WALLPAPER_OPACITY]?.coerceIn(20, 100) ?: 100 }
    val appWallpaperDim: Flow<Int> =
        context.dataStore.data.map { it[KEY_APP_WALLPAPER_DIM]?.coerceIn(0, 80) ?: 30 }
    val appWallpaperContentOverlay: Flow<Int> =
        context.dataStore.data.map { it[KEY_APP_WALLPAPER_CONTENT_OVERLAY]?.coerceIn(0, 80) ?: 24 }
    val playerBackgroundEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_BACKGROUND_ENABLED] ?: false }
    val playerBackgroundUri: Flow<String> =
        context.dataStore.data.map { it[KEY_PLAYER_BACKGROUND_URI] ?: "" }
    val playerBackgroundOpacity: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BACKGROUND_OPACITY]?.coerceIn(20, 100) ?: 100 }
    val playerBackgroundDim: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BACKGROUND_DIM]?.coerceIn(0, 80) ?: 26 }
    val playerBeautifulLyricsBackground: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BACKGROUND] ?: false }
    val playerBeautifulLyricsSpeed: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BEAUTIFUL_LYRICS_SPEED]?.coerceIn(5, 60) ?: 25 }
    val playerBeautifulLyricsBlur: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BLUR]?.coerceIn(0, 80) ?: 32 }
    val playerBeautifulLyricsBrightness: Flow<Int> =
        context.dataStore.data.map { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BRIGHTNESS]?.coerceIn(30, 120) ?: 70 }
    val homeCardColor: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_CARD_COLOR] ?: "" }
    val homeCardOpacity: Flow<Int> =
        context.dataStore.data.map { it[KEY_HOME_CARD_OPACITY]?.coerceIn(20, 100) ?: 58 }
    val homeTileColors: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_TILE_COLORS] ?: "" }
    val homeTileGradientEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HOME_TILE_GRADIENT_ENABLED] ?: false }
    val homeTileGradientStartColor: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_TILE_GRADIENT_START_COLOR] ?: "" }
    val hiResLogoEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HI_RES_LOGO_ENABLED] ?: false }
    val hiResLogoUri: Flow<String> =
        context.dataStore.data.map { it[KEY_HI_RES_LOGO_URI] ?: "" }
    val playlistSpecialEntriesVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_PLAYLIST_SPECIAL_ENTRIES_VISIBLE] ?: false }
    val showPlayNextInLists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_PLAY_NEXT_IN_LISTS] ?: false }
    val excludeSearchResultsFromPlaylist: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_EXCLUDE_SEARCH_RESULTS_FROM_PLAYLIST] ?: false }
    val autoShowSearchKeyboard: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_AUTO_SHOW_SEARCH_KEYBOARD] ?: true }
    val playNextMode: Flow<Int> =
        context.dataStore.data.map {
            it[KEY_PLAY_NEXT_MODE]?.coerceIn(PLAY_NEXT_MODE_REVERSE_STACK, PLAY_NEXT_MODE_FORWARD_STACK)
                ?: PLAY_NEXT_MODE_REVERSE_STACK
        }
    val lyricShareCustomInfo: Flow<String> =
        context.dataStore.data.map { it[KEY_LYRIC_SHARE_CUSTOM_INFO] ?: "" }
    val showAlbumArtists: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SHOW_ALBUM_ARTISTS] ?: true }
    val metadataEditorId: Flow<String> =
        context.dataStore.data.map { it[KEY_METADATA_EDITOR_ID] ?: "" }
    val lyricTimingEditorId: Flow<String> =
        context.dataStore.data.map { it[KEY_LYRIC_TIMING_EDITOR_ID] ?: "" }
    val sleepTimerCustomMinutes: Flow<Int> =
        context.dataStore.data.map { it[KEY_SLEEP_TIMER_CUSTOM_MINUTES]?.coerceIn(5, 120) ?: 45 }
    val sleepTimerStopAfterCurrent: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_SLEEP_TIMER_STOP_AFTER_CURRENT] ?: false }
    val shortcutLibraryLabel: Flow<String> =
        context.dataStore.data.map { it[KEY_SHORTCUT_LIBRARY_LABEL] ?: DEFAULT_SHORTCUT_LIBRARY_LABEL }
    val shortcutPlaylistsLabel: Flow<String> =
        context.dataStore.data.map { it[KEY_SHORTCUT_PLAYLISTS_LABEL] ?: DEFAULT_SHORTCUT_PLAYLISTS_LABEL }
    val shortcutFolderLabel: Flow<String> =
        context.dataStore.data.map { it[KEY_SHORTCUT_FOLDER_LABEL] ?: DEFAULT_SHORTCUT_FOLDER_LABEL }
    val appShortcutOrder: Flow<List<String>> =
        context.dataStore.data.map { preferences ->
            preferences[KEY_APP_SHORTCUT_ORDER]
                ?.let(::normalizeAppShortcutOrder)
                ?: DEFAULT_APP_SHORTCUT_ORDER
        }
    val webDavUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_URL] ?: "" }
    val webDavUsername: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_USERNAME] ?: "" }
    val webDavPassword: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_PASSWORD] ?: "" }
    val webDavLastUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_LAST_URL] ?: "" }
    val webDavBackupUrl: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_BACKUP_URL] ?: "" }
    val webDavBackupPath: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_BACKUP_PATH] ?: "" }
    val webDavBackupUsername: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_BACKUP_USERNAME] ?: "" }
    val webDavBackupPassword: Flow<String> = context.dataStore.data.map { it[KEY_WEBDAV_BACKUP_PASSWORD] ?: "" }
    val webDavAutoBackupEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_WEBDAV_AUTO_BACKUP_ENABLED] ?: false }
    val webDavAutoBackupIntervalHours: Flow<Int> = context.dataStore.data.map {
        (it[KEY_WEBDAV_AUTO_BACKUP_INTERVAL_HOURS] ?: 24).coerceIn(1, 168)
    }
    val webDavAutoBackupLastAt: Flow<Long> = context.dataStore.data.map {
        it[KEY_WEBDAV_AUTO_BACKUP_LAST_AT]?.toLongOrNull() ?: 0L
    }
    val lxSources: Flow<List<LxSourceConfig>> = context.dataStore.data.map { prefs -> prefs.lxSources() }
    val selectedLxSourceId: Flow<String> = context.dataStore.data.map { it[KEY_LX_SELECTED_SOURCE_ID] ?: "" }
    val selectedLxSource: Flow<LxSourceConfig?> = context.dataStore.data.map { prefs ->
        val sources = prefs.lxSources()
        val selectedId = prefs[KEY_LX_SELECTED_SOURCE_ID].orEmpty()
        sources.firstOrNull { it.id == selectedId } ?: sources.firstOrNull()
    }
    val lxSourceUrl: Flow<String> = selectedLxSource.map { it?.url.orEmpty() }
    val lxSourceName: Flow<String> = selectedLxSource.map { it?.name.orEmpty() }
    val lxSourceScript: Flow<String> = selectedLxSource.map { it?.script.orEmpty() }
    val selectedOnlineProvider: Flow<RemoteMusicProvider> =
        context.dataStore.data.map { RemoteMusicProvider.fromId(it[KEY_ONLINE_SELECTED_PROVIDER].orEmpty()) }
    // ---- Multi-address remote sources ----
    // The stored server list is the source of truth. A legacy single config (old KEY_*_URL keys) is
    // synthesized read-only into a one-entry list so existing setups keep working until the user
    // edits them (the first write persists the migrated list).
    private fun readNavidromeServers(prefs: Preferences): List<SavedRemoteServer> {
        val stored = prefs[KEY_NAVIDROME_SERVERS].orEmpty().toSavedRemoteServers(RemoteMusicProvider.Navidrome)
        if (stored.isNotEmpty()) return stored
        val legacyUrl = prefs[KEY_NAVIDROME_URL].orEmpty()
        if (legacyUrl.isBlank()) return emptyList()
        return listOf(
            SavedRemoteServer(
                id = LEGACY_NAVIDROME_SERVER_ID,
                name = legacyUrl.remoteServerDisplayName(),
                config = RemoteMusicSourceConfig(
                    provider = RemoteMusicProvider.Navidrome,
                    baseUrl = legacyUrl,
                    username = prefs[KEY_NAVIDROME_USERNAME].orEmpty(),
                    password = prefs[KEY_NAVIDROME_PASSWORD].orEmpty()
                )
            )
        )
    }

    private fun readEmbyServers(prefs: Preferences): List<SavedRemoteServer> {
        val stored = prefs[KEY_EMBY_SERVERS].orEmpty().toSavedRemoteServers(RemoteMusicProvider.Emby)
        if (stored.isNotEmpty()) return stored
        val legacyUrl = prefs[KEY_EMBY_URL].orEmpty()
        if (legacyUrl.isBlank()) return emptyList()
        return listOf(
            SavedRemoteServer(
                id = LEGACY_EMBY_SERVER_ID,
                name = prefs[KEY_EMBY_SERVER_NAME].orEmpty().ifBlank { legacyUrl.remoteServerDisplayName() },
                config = RemoteMusicSourceConfig(
                    provider = RemoteMusicProvider.Emby,
                    baseUrl = legacyUrl,
                    username = prefs[KEY_EMBY_USERNAME].orEmpty(),
                    token = prefs[KEY_EMBY_TOKEN].orEmpty(),
                    userId = prefs[KEY_EMBY_USER_ID].orEmpty(),
                    serverName = prefs[KEY_EMBY_SERVER_NAME].orEmpty()
                )
            )
        )
    }

    private fun readOpenSubsonicServers(prefs: Preferences): List<SavedRemoteServer> =
        prefs[KEY_OPENSUBSONIC_SERVERS].orEmpty().toSavedRemoteServers(RemoteMusicProvider.OpenSubsonic)

    private fun activeServer(servers: List<SavedRemoteServer>, activeId: String?): SavedRemoteServer? =
        servers.firstOrNull { it.id == activeId } ?: servers.firstOrNull()

    val navidromeServers: Flow<List<SavedRemoteServer>> = context.dataStore.data.map { readNavidromeServers(it) }
    val openSubsonicServers: Flow<List<SavedRemoteServer>> = context.dataStore.data.map { readOpenSubsonicServers(it) }
    val embyServers: Flow<List<SavedRemoteServer>> = context.dataStore.data.map { readEmbyServers(it) }
    val navidromeActiveServerId: Flow<String> = context.dataStore.data.map { prefs ->
        activeServer(readNavidromeServers(prefs), prefs[KEY_NAVIDROME_ACTIVE_ID])?.id.orEmpty()
    }
    val openSubsonicActiveServerId: Flow<String> = context.dataStore.data.map { prefs ->
        activeServer(readOpenSubsonicServers(prefs), prefs[KEY_OPENSUBSONIC_ACTIVE_ID])?.id.orEmpty()
    }
    val embyActiveServerId: Flow<String> = context.dataStore.data.map { prefs ->
        activeServer(readEmbyServers(prefs), prefs[KEY_EMBY_ACTIVE_ID])?.id.orEmpty()
    }
    val navidromeConfig: Flow<RemoteMusicSourceConfig> = context.dataStore.data.map { prefs ->
        activeServer(readNavidromeServers(prefs), prefs[KEY_NAVIDROME_ACTIVE_ID])?.config
            ?: RemoteMusicSourceConfig(provider = RemoteMusicProvider.Navidrome, baseUrl = "")
    }
    val openSubsonicConfig: Flow<RemoteMusicSourceConfig> = context.dataStore.data.map { prefs ->
        activeServer(readOpenSubsonicServers(prefs), prefs[KEY_OPENSUBSONIC_ACTIVE_ID])?.config
            ?: RemoteMusicSourceConfig(provider = RemoteMusicProvider.OpenSubsonic, baseUrl = "")
    }
    val embyConfig: Flow<RemoteMusicSourceConfig> = context.dataStore.data.map { prefs ->
        activeServer(readEmbyServers(prefs), prefs[KEY_EMBY_ACTIVE_ID])?.config
            ?: RemoteMusicSourceConfig(provider = RemoteMusicProvider.Emby, baseUrl = "")
    }
    val librarySource: Flow<String> = context.dataStore.data.map {
        it[KEY_LIBRARY_SOURCE] ?: LIBRARY_SOURCE_LOCAL
    }
    val openAiApiKey: Flow<String> = context.dataStore.data.map { it[KEY_OPENAI_API_KEY] ?: "" }
    val openAiBaseUrl: Flow<String> =
        context.dataStore.data.map { it[KEY_OPENAI_BASE_URL] ?: DEFAULT_OPENAI_BASE_URL }
    val openAiModel: Flow<String> =
        context.dataStore.data.map { it[KEY_OPENAI_MODEL] ?: DEFAULT_OPENAI_MODEL }
    val openPlayerOnPlay: Flow<Boolean> = context.dataStore.data.map { it[KEY_OPEN_PLAYER_ON_PLAY] ?: false }
    val startupAutoPlay: Flow<Boolean> = context.dataStore.data.map { it[KEY_STARTUP_AUTO_PLAY] ?: false }
    val bluetoothAutoPlay: Flow<Boolean> = context.dataStore.data.map { it[KEY_BLUETOOTH_AUTO_PLAY] ?: false }
    val startupPlayMode: Flow<Int> = context.dataStore.data.map {
        it[KEY_STARTUP_PLAY_MODE]
            ?: if (it[KEY_STARTUP_AUTO_PLAY] == true) STARTUP_PLAY_RANDOM else STARTUP_PLAY_OFF
    }
    val lyricFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_FONT_NAME] ?: "" }
    val lyricFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_FONT_PATH] ?: "" }
    val lyricWesternFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_WESTERN_FONT_NAME] ?: "" }
    val lyricWesternFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_WESTERN_FONT_PATH] ?: "" }
    val lyricCjkFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_CJK_FONT_NAME] ?: "" }
    val lyricCjkFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_CJK_FONT_PATH] ?: "" }
    val globalWesternFontName: Flow<String> = context.dataStore.data.map { it[KEY_GLOBAL_WESTERN_FONT_NAME] ?: "" }
    val globalWesternFontPath: Flow<String> = context.dataStore.data.map { it[KEY_GLOBAL_WESTERN_FONT_PATH] ?: "" }
    val globalCjkFontName: Flow<String> = context.dataStore.data.map { it[KEY_GLOBAL_CJK_FONT_NAME] ?: "" }
    val globalCjkFontPath: Flow<String> = context.dataStore.data.map { it[KEY_GLOBAL_CJK_FONT_PATH] ?: "" }
    val lyricOriginalWesternFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_ORIGINAL_WESTERN_FONT_NAME] ?: "" }
    val lyricOriginalWesternFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_ORIGINAL_WESTERN_FONT_PATH] ?: "" }
    val lyricOriginalCjkFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_ORIGINAL_CJK_FONT_NAME] ?: "" }
    val lyricOriginalCjkFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_ORIGINAL_CJK_FONT_PATH] ?: "" }
    val lyricTranslationWesternFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_TRANSLATION_WESTERN_FONT_NAME] ?: "" }
    val lyricTranslationWesternFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_TRANSLATION_WESTERN_FONT_PATH] ?: "" }
    val lyricTranslationCjkFontName: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_TRANSLATION_CJK_FONT_NAME] ?: "" }
    val lyricTranslationCjkFontPath: Flow<String> = context.dataStore.data.map { it[KEY_LYRIC_TRANSLATION_CJK_FONT_PATH] ?: "" }
    val lyricFontWeight: Flow<Int> = context.dataStore.data.map { it[KEY_LYRIC_FONT_WEIGHT] ?: 800 }
    val lyricFontScale: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_FONT_SCALE] ?: 100).coerceIn(LYRIC_FONT_SCALE_MIN, LYRIC_FONT_SCALE_ULTRA_WIDE_MAX)
    }
    val lyricSecondaryFontScale: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_SECONDARY_FONT_SCALE] ?: 100).coerceIn(
            LYRIC_SECONDARY_FONT_SCALE_MIN,
            LYRIC_SECONDARY_FONT_SCALE_ULTRA_WIDE_MAX
        )
    }
    val lyricCompactPrimaryTextSize: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_COMPACT_PRIMARY_TEXT_SIZE] ?: LYRIC_COMPACT_PRIMARY_TEXT_SIZE_DEFAULT_SP)
            .coerceIn(LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MIN_SP, LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MAX_SP)
    }
    val lyricCompactSecondaryTextSize: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_COMPACT_SECONDARY_TEXT_SIZE] ?: LYRIC_COMPACT_SECONDARY_TEXT_SIZE_DEFAULT_SP)
            .coerceIn(LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MIN_SP, LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MAX_SP)
    }
    val lyricWidePrimaryTextSize: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_WIDE_PRIMARY_TEXT_SIZE] ?: LYRIC_WIDE_PRIMARY_TEXT_SIZE_DEFAULT_SP)
            .coerceIn(LYRIC_WIDE_PRIMARY_TEXT_SIZE_MIN_SP, LYRIC_WIDE_PRIMARY_TEXT_SIZE_MAX_SP)
    }
    val lyricWideSecondaryTextSize: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LYRIC_WIDE_SECONDARY_TEXT_SIZE] ?: LYRIC_WIDE_SECONDARY_TEXT_SIZE_DEFAULT_SP)
            .coerceIn(LYRIC_WIDE_SECONDARY_TEXT_SIZE_MIN_SP, LYRIC_WIDE_SECONDARY_TEXT_SIZE_MAX_SP)
    }
    val lyricFontItalic: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_FONT_ITALIC] ?: false }
    val lyricFontApplyToPage: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_FONT_APPLY_TO_PAGE] ?: true }
    val lyricFontApplyToDesktop: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_FONT_APPLY_TO_DESKTOP] ?: true }
    val lyricShareUseLyricFont: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_SHARE_USE_LYRIC_FONT] ?: false }
    val lyricPerspectiveEffect: Flow<Boolean> = context.dataStore.data.map { it[KEY_LYRIC_PERSPECTIVE_EFFECT] ?: false }
    val lyricPerspectiveYAngle: Flow<Int> = context.dataStore.data.map { it[KEY_LYRIC_PERSPECTIVE_Y_ANGLE] ?: 25 }
    val scanIncludeFolders: Flow<String> = context.dataStore.data.map { it[KEY_SCAN_INCLUDE_FOLDERS] ?: "" }
    val scanExcludeFolders: Flow<String> = context.dataStore.data.map { it[KEY_SCAN_EXCLUDE_FOLDERS] ?: "" }
    val usbFolderUris: Flow<String> = context.dataStore.data.map { it[KEY_USB_FOLDER_URIS] ?: "" }
    val useAndroidMediaLibrary: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_USE_ANDROID_MEDIA_LIBRARY] ?: true }
    val fullTagSearchEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_FULL_TAG_SEARCH_ENABLED] ?: false }
    val fullTagSearchPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_FULL_TAG_SEARCH_PROMPT_HANDLED] ?: false }
    val coverExportFolderUri: Flow<String> =
        context.dataStore.data.map { it[KEY_COVER_EXPORT_FOLDER_URI] ?: "" }
    val searchAllCategoryTypes: Flow<Set<String>> = context.dataStore.data.map {
        parseSearchAllCategoryTypes(it[KEY_SEARCH_ALL_CATEGORY_TYPES])
    }
    val searchAllSongMatchTypes: Flow<Set<String>> = context.dataStore.data.map {
        parseSearchAllSongMatchTypes(it[KEY_SEARCH_ALL_SONG_MATCH_TYPES])
    }
    val songRatingDisplayMode: Flow<Int> = context.dataStore.data.map {
        (it[KEY_SONG_RATING_DISPLAY_MODE] ?: SONG_RATING_DISPLAY_STAR_NUMBER)
            .coerceIn(SONG_RATING_DISPLAY_STAR_NUMBER, SONG_RATING_DISPLAY_STARS)
    }
    val listeningHistorySource: Flow<Int> = context.dataStore.data.map {
        (it[KEY_LISTENING_HISTORY_SOURCE] ?: LISTENING_HISTORY_SOURCE_LOCAL)
            .coerceIn(LISTENING_HISTORY_SOURCE_LOCAL, LISTENING_HISTORY_SOURCE_COMBINED)
    }
    val initialScanPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_INITIAL_SCAN_PROMPT_HANDLED] ?: false }
    val localPlaylistScanPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_LOCAL_PLAYLIST_SCAN_PROMPT_HANDLED] ?: false }
    val notificationPermissionPromptHandled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED] ?: false }
    val artistSeparators: Flow<String> = context.dataStore.data.map { it[KEY_ARTIST_SEPARATORS] ?: "" }
    val artistProtectedNames: Flow<String> = context.dataStore.data.map { it[KEY_ARTIST_PROTECTED_NAMES] ?: "" }
    val genreSeparators: Flow<String> = context.dataStore.data.map { it[KEY_GENRE_SEPARATORS] ?: "" }
    val genreProtectedNames: Flow<String> = context.dataStore.data.map { it[KEY_GENRE_PROTECTED_NAMES] ?: "" }
    val tagIgnoreCase: Flow<Boolean> = context.dataStore.data.map { it[KEY_TAG_IGNORE_CASE] ?: false }
    val decoderMode: Flow<Int> = context.dataStore.data.map { it[KEY_DECODER_MODE] ?: 2 }
    val librarySongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_LIBRARY_SONG, 0)
    val albumListSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_ALBUM_LIST, 0)
    val artistListSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_ARTIST_LIST, 0)
    val albumDetailSongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_ALBUM_DETAIL_SONG, 0)
    val artistDetailSongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_ARTIST_DETAIL_SONG, 0)
    val artistDetailAlbumSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_ARTIST_DETAIL_ALBUM, 0)
    val folderListSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_FOLDER_LIST, 0)
    val folderDetailSongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_FOLDER_DETAIL_SONG, 0)
    val folderPlaylistListSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_FOLDER_PLAYLIST_LIST, 2)
    val folderPlaylistDetailSongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_FOLDER_PLAYLIST_DETAIL_SONG, 0)
    val folderPlaylistDetailFolderSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_FOLDER_PLAYLIST_DETAIL_FOLDER, 0)
    val playlistListSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_PLAYLIST_LIST, 2)
    val playlistCustomOrder: Flow<List<String>> = context.dataStore.data.map {
        it[KEY_PLAYLIST_CUSTOM_ORDER]
            .orEmpty()
            .split('\n')
            .map(String::trim)
            .filter(String::isNotBlank)
    }
    val playlistDetailSongSortIndex: Flow<Int> = sortIndexFlow(KEY_SORT_PLAYLIST_DETAIL_SONG, 2)
    val addToPlaylistAppendToEnd: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_ADD_TO_PLAYLIST_APPEND_TO_END] ?: false }
    val categoryGridColumns: Flow<Int> = context.dataStore.data.map {
        val tablet = context.resources.configuration.smallestScreenWidthDp >= 600
        if (tablet) {
            (it[KEY_CATEGORY_GRID_COLUMNS] ?: 5).coerceIn(5, 8)
        } else {
            (it[KEY_CATEGORY_GRID_COLUMNS] ?: 2).coerceIn(1, 4)
        }
    }
    val homeDailyMixVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HOME_DAILY_MIX_VISIBLE] ?: true }
    val homeAiMixVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HOME_AI_MIX_VISIBLE] ?: true }
    val homeSectionOrder: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_SECTION_ORDER] ?: DEFAULT_HOME_SECTION_ORDER }
    val homeHiddenSections: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_HIDDEN_SECTIONS] ?: "" }
    val homeLibraryTileOrder: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_LIBRARY_TILE_ORDER] ?: DEFAULT_HOME_LIBRARY_TILE_ORDER }
    val homeHiddenLibraryTiles: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_HIDDEN_LIBRARY_TILES] ?: "" }
    val homeOnlineTileOrder: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_ONLINE_TILE_ORDER] ?: DEFAULT_HOME_ONLINE_TILE_ORDER }
    val homeHiddenOnlineTiles: Flow<String> =
        context.dataStore.data.map { it[KEY_HOME_HIDDEN_ONLINE_TILES] ?: "" }
    val homeTilePinButtonsVisible: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_HOME_TILE_PIN_BUTTONS_VISIBLE] ?: false }
    val folderPlaylists: Flow<List<FolderPlaylist>> =
        context.dataStore.data.map { it[KEY_FOLDER_PLAYLISTS].orEmpty().toFolderPlaylists() }
    fun metadataCategorySortIndex(type: String): Flow<Int> =
        sortIndexFlow(metadataCategorySortKey(type), 0)

    fun metadataCategoryDetailSongSortIndex(type: String): Flow<Int> =
        sortIndexFlow(metadataCategoryDetailSongSortKey(type), 0)

    fun metadataCategoryDetailAlbumSortIndex(type: String): Flow<Int> =
        sortIndexFlow(metadataCategoryDetailAlbumSortKey(type), 0)

    val bluetoothLyricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BLUETOOTH_LYRIC_ENABLED] ?: false }
    val bluetoothLyricTranslation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BLUETOOTH_LYRIC_TRANSLATION] ?: true }
    val bluetoothLyricPronunciation: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BLUETOOTH_LYRIC_PRONUNCIATION] ?: false }
    val colorOsLockScreenLyricEnabled: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_COLOROS_LOCK_SCREEN_LYRIC_ENABLED] ?: false }
    val colorOsLockScreenLyricMode: Flow<Int> =
        context.dataStore.data.map { (it[KEY_COLOROS_LOCK_SCREEN_LYRIC_MODE] ?: OPLUS_LYRIC_MODE_SYSTEM).coerceInOplusLyricMode() }
    suspend fun setLyriconEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICON_ENABLED] = enabled }
    }

    suspend fun setLyriconTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICON_TRANSLATION] = enabled }
    }

    suspend fun setLyriconPronunciation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRICON_PRONUNCIATION] = enabled }
    }

    suspend fun setAutoScan(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SCAN] = enabled }
    }

    suspend fun setAutoScanLocalPlaylists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SCAN_LOCAL_PLAYLISTS] = enabled }
    }

    suspend fun setGaplessPlayback(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GAPLESS] = enabled }
    }

    suspend fun setCrossfadeDurationMs(durationMs: Int) {
        context.dataStore.edit { it[KEY_CROSSFADE_DURATION_MS] = durationMs.coerceIn(0, 12_000) }
    }

    suspend fun setThemeMode(mode: Int) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode }
    }

    suspend fun setMonetColorMode(mode: Int) {
        context.dataStore.edit { it[KEY_MONET_COLOR_MODE] = mode }
    }

    suspend fun setPlayerBackgroundTheme(mode: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BACKGROUND_THEME] = mode }
    }

    suspend fun setPlayerCoverContentColor(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_COVER_CONTENT_COLOR] = enabled }
    }

    suspend fun setAppLanguage(languageTag: String) {
        val normalized = when (languageTag) {
            APP_LANGUAGE_ZH_CN -> APP_LANGUAGE_ZH_CN
            APP_LANGUAGE_ZH_TW -> APP_LANGUAGE_ZH_TW
            APP_LANGUAGE_EN -> APP_LANGUAGE_EN
            APP_LANGUAGE_JA -> APP_LANGUAGE_JA
            APP_LANGUAGE_KO -> APP_LANGUAGE_KO
            APP_LANGUAGE_DE -> APP_LANGUAGE_DE
            APP_LANGUAGE_FR -> APP_LANGUAGE_FR
            APP_LANGUAGE_RU -> APP_LANGUAGE_RU
            else -> APP_LANGUAGE_SYSTEM
        }
        context.dataStore.edit { it[KEY_APP_LANGUAGE] = normalized }
    }

    suspend fun setAppIconStyle(style: String) {
        context.dataStore.edit { it[KEY_APP_ICON_STYLE] = AppIconManager.normalize(style) }
    }

    suspend fun setBottomBarGlassEffect(effect: BottomBarGlassEffect) {
        context.dataStore.edit { it[KEY_BOTTOM_BAR_GLASS_EFFECT] = effect.name }
    }

    suspend fun setBottomDockItems(items: List<String>) {
        context.dataStore.edit {
            it[KEY_BOTTOM_DOCK_ITEMS] = normalizeBottomDockItems(items.joinToString(","))
        }
    }

    suspend fun setTickerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_ENABLED] = enabled }
    }

    suspend fun setTickerHideNotification(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_HIDE_NOTIFICATION] = enabled }
    }

    suspend fun setTickerHeadsUpLyrics(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TICKER_HEADS_UP_LYRICS] = enabled }
    }

    suspend fun setSamsungFloatingLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION] = enabled }
    }

    suspend fun setStatusBarAllowPhonetic(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STATUS_BAR_ALLOW_PHONETIC] = enabled }
    }

    suspend fun setPlayerLyricTextAlign(align: Int) {
        context.dataStore.edit { it[KEY_PLAYER_LYRIC_TEXT_ALIGN] = align.coerceIn(0, 2) }
    }

    suspend fun setLyricPronunciationBelow(below: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PRONUNCIATION_BELOW] = below }
    }

    suspend fun setLyricLineBlacklist(lines: List<String>) {
        val normalized = normalizeLyricLineBlacklist(lines.asSequence())
        context.dataStore.edit { prefs ->
            if (normalized.isEmpty()) {
                prefs.remove(KEY_LYRIC_LINE_BLACKLIST)
            } else {
                prefs[KEY_LYRIC_LINE_BLACKLIST] = normalized.joinToString("\n")
            }
        }
    }

    suspend fun setIgnoreLyricHeaderTags(enabled: Boolean) {
        context.dataStore.edit { it[KEY_IGNORE_LYRIC_HEADER_TAGS] = enabled }
    }

    suspend fun setSuperLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPER_LYRIC_ENABLED] = enabled }
    }

    suspend fun setSuperLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPER_LYRIC_TRANSLATION] = enabled }
    }

    suspend fun setSuperLyricPronunciation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SUPER_LYRIC_PRONUNCIATION] = enabled }
    }

    suspend fun setLyricGetterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_GETTER_ENABLED] = enabled }
    }

    suspend fun setBluetoothLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_LYRIC_ENABLED] = enabled }
    }

    suspend fun setBluetoothLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_LYRIC_TRANSLATION] = enabled }
    }

    suspend fun setBluetoothLyricPronunciation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_LYRIC_PRONUNCIATION] = enabled }
    }

    suspend fun setColorOsLockScreenLyricEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_COLOROS_LOCK_SCREEN_LYRIC_ENABLED] = enabled }
    }

    suspend fun setColorOsLockScreenLyricMode(mode: Int) {
        context.dataStore.edit { it[KEY_COLOROS_LOCK_SCREEN_LYRIC_MODE] = mode.coerceInOplusLyricMode() }
    }

    suspend fun setMinDurationSec(seconds: Int) {
        context.dataStore.edit { it[KEY_MIN_DURATION] = seconds }
    }

    suspend fun setReplayGainEnabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_REPLAYGAIN_ENABLED] = enabled
            it[KEY_REPLAYGAIN_MODE] = if (enabled) REPLAY_GAIN_AUTO else REPLAY_GAIN_OFF
        }
    }

    suspend fun setReplayGainMode(mode: Int) {
        val safeMode = mode.coerceIn(REPLAY_GAIN_OFF, REPLAY_GAIN_AUTO)
        context.dataStore.edit {
            it[KEY_REPLAYGAIN_MODE] = safeMode
            it[KEY_REPLAYGAIN_ENABLED] = safeMode != REPLAY_GAIN_OFF
        }
    }

    suspend fun setResumePlaybackPosition(enabled: Boolean) {
        context.dataStore.edit { it[KEY_RESUME_PLAYBACK_POSITION] = enabled }
    }

    suspend fun setAudioFocusDisabled(disabled: Boolean) {
        context.dataStore.edit { it[KEY_AUDIO_FOCUS_DISABLED] = disabled }
    }

    suspend fun setShuffleMode(mode: Int) {
        context.dataStore.edit { it[KEY_SHUFFLE_MODE] = mode.coerceIn(SHUFFLE_MODE_PSEUDO, SHUFFLE_MODE_TRUE_RANDOM) }
    }

    suspend fun setPreviousButtonAction(action: Int) {
        context.dataStore.edit {
            it[KEY_PREVIOUS_BUTTON_ACTION] = action.coerceIn(PREVIOUS_BUTTON_PREVIOUS, PREVIOUS_BUTTON_REPLAY_CURRENT)
        }
    }

    suspend fun setLyricSourceMode(mode: Int) {
        context.dataStore.edit { it[KEY_LYRIC_SOURCE_MODE] = mode.coerceIn(LYRIC_SOURCE_AUTO, LYRIC_SOURCE_EMBEDDED) }
    }

    suspend fun setLyricSourcePriority(priority: String) {
        context.dataStore.edit { it[KEY_LYRIC_SOURCE_PRIORITY] = normalizeLyricSourcePriority(priority) }
    }

    suspend fun setLyricoPluginEnabled(id: String, enabled: Boolean) {
        val pluginId = id.trim()
        if (pluginId.isBlank()) return
        context.dataStore.edit { prefs ->
            val current = LyricoPluginManager.normalizeEnabledIds(prefs[KEY_LYRICO_PLUGIN_ENABLED_IDS]).toMutableSet()
            if (enabled) current += pluginId else current -= pluginId
            prefs[KEY_LYRICO_PLUGIN_ENABLED_IDS] = current.joinToString(",")
        }
    }

    suspend fun setLyricOffsetOverride(songKey: String, offsetMs: Long) {
        val key = songKey.trim()
        if (key.isBlank()) return
        context.dataStore.edit { prefs ->
            val offsets = parseLyricOffsetOverrides(prefs[KEY_LYRIC_OFFSET_OVERRIDES]).toMutableMap()
            if (offsetMs == 0L) offsets.remove(key) else offsets[key] = offsetMs.coerceIn(-5000L, 5000L)
            prefs[KEY_LYRIC_OFFSET_OVERRIDES] = JSONObject(offsets.mapValues { it.value }).toString()
        }
    }

    suspend fun setLyricPageTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PAGE_TRANSLATION] = enabled }
    }

    suspend fun setLyricPageKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PAGE_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setAppleMusicLyricsWordLift(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APPLE_MUSIC_LYRICS_WORD_LIFT] = enabled }
    }

    suspend fun setLyricPerspectiveEffect(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_PERSPECTIVE_EFFECT] = enabled }
    }

    suspend fun setLyricPerspectiveYAngle(angle: Int) {
        context.dataStore.edit { it[KEY_LYRIC_PERSPECTIVE_Y_ANGLE] = angle.coerceIn(0, 45) }
    }

    suspend fun setMiniPlayerLyricTranslation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_LYRIC_TRANSLATION] = enabled }
    }

    suspend fun setMiniPlayerLyricSecondary(mode: Int) {
        context.dataStore.edit {
            val safeMode = mode.coerceIn(LYRIC_SECONDARY_OFF, LYRIC_SECONDARY_PRONUNCIATION)
            it[KEY_MINI_PLAYER_LYRIC_SECONDARY] = safeMode
            it[KEY_MINI_PLAYER_LYRIC_TRANSLATION] = safeMode == LYRIC_SECONDARY_TRANSLATION
        }
    }

    suspend fun setMiniPlayerCoverRotation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_COVER_ROTATION] = enabled }
    }

    suspend fun setMiniPlayerLyricsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_LYRICS_ENABLED] = enabled }
    }

    suspend fun setMiniPlayerRightButton(mode: Int) {
        context.dataStore.edit { it[KEY_MINI_PLAYER_RIGHT_BUTTON] = mode.coerceIn(MINI_PLAYER_RIGHT_NEXT, MINI_PLAYER_RIGHT_QUEUE) }
    }

    suspend fun setPlayerProgressInfoIndex(index: Int) {
        context.dataStore.edit { it[KEY_PLAYER_PROGRESS_INFO_INDEX] = index.coerceAtLeast(0) }
    }

    suspend fun setTransportButtonOutlines(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TRANSPORT_BUTTON_OUTLINES] = enabled }
    }

    suspend fun setPlayerHdrGlow(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_HDR_GLOW] = enabled }
    }

    suspend fun setPlayerImmersiveCover(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_IMMERSIVE_COVER] = enabled }
    }


    suspend fun setHideSystemBars(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HIDE_SYSTEM_BARS] = enabled }
    }

    suspend fun setPlayerDynamicFlowEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_DYNAMIC_FLOW_ENABLED] = enabled }
    }

    suspend fun setAudioVisualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUDIO_VISUALIZER_ENABLED] = enabled }
    }

    suspend fun setAudioVisualizerOpacity(opacity: Int) {
        context.dataStore.edit { it[KEY_AUDIO_VISUALIZER_OPACITY] = opacity.coerceIn(20, 100) }
    }

    suspend fun setEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EQ_ENABLED] = enabled }
    }

    suspend fun setEqPreset(preset: Int) {
        context.dataStore.edit { it[KEY_EQ_PRESET] = preset }
    }

    /** Persist preset selection and the concrete band levels it resolves to in one write. */
    suspend fun setEqPresetWithBands(preset: Int, bandLevelsMb: List<Int>) {
        context.dataStore.edit {
            it[KEY_EQ_PRESET] = preset
            it[KEY_EQ_BANDS] = bandLevelsMb.normalizedEqBands().joinToString(",")
        }
    }

    suspend fun setEqBandLevelsMb(bandLevelsMb: List<Int>) {
        context.dataStore.edit {
            it[KEY_EQ_BANDS] = bandLevelsMb.normalizedEqBands().joinToString(",")
            it[KEY_EQ_PRESET] = AudioEffectSettings.PRESET_CUSTOM
        }
    }

    suspend fun setBassBoostEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BASS_BOOST_ENABLED] = enabled }
    }

    suspend fun setBassBoostStrength(strength: Int) {
        context.dataStore.edit { it[KEY_BASS_BOOST_STRENGTH] = strength.coerceIn(0, AudioEffectSettings.STRENGTH_MAX) }
    }

    suspend fun setVirtualizerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_VIRTUALIZER_ENABLED] = enabled }
    }

    suspend fun setVirtualizerStrength(strength: Int) {
        context.dataStore.edit { it[KEY_VIRTUALIZER_STRENGTH] = strength.coerceIn(0, AudioEffectSettings.STRENGTH_MAX) }
    }

    suspend fun setReverbPreset(preset: Int) {
        context.dataStore.edit { it[KEY_REVERB_PRESET] = normalizeReverbPreset(preset) }
    }

    suspend fun setEqQ(q: Int) {
        context.dataStore.edit { it[KEY_EQ_Q] = q.coerceIn(AudioEffectSettings.EQ_Q_MIN, AudioEffectSettings.EQ_Q_MAX) }
    }

    suspend fun setToneBassDb(db: Int) {
        context.dataStore.edit { it[KEY_TONE_BASS_DB] = db.coerceIn(AudioEffectSettings.TONE_GAIN_MIN_DB, AudioEffectSettings.TONE_GAIN_MAX_DB) }
    }

    suspend fun setToneTrebleDb(db: Int) {
        context.dataStore.edit { it[KEY_TONE_TREBLE_DB] = db.coerceIn(AudioEffectSettings.TONE_GAIN_MIN_DB, AudioEffectSettings.TONE_GAIN_MAX_DB) }
    }

    suspend fun setCompressorEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_COMP_ENABLED] = enabled }
    }

    suspend fun setCompressorThresholdDb(db: Int) {
        context.dataStore.edit { it[KEY_COMP_THRESHOLD_DB] = db.coerceIn(AudioEffectSettings.COMP_THRESHOLD_MIN_DB, AudioEffectSettings.COMP_THRESHOLD_MAX_DB) }
    }

    suspend fun setCompressorRatio(ratio: Int) {
        context.dataStore.edit { it[KEY_COMP_RATIO] = ratio.coerceIn(AudioEffectSettings.COMP_RATIO_MIN, AudioEffectSettings.COMP_RATIO_MAX) }
    }

    suspend fun setCompressorMakeupDb(db: Int) {
        context.dataStore.edit { it[KEY_COMP_MAKEUP_DB] = db.coerceIn(AudioEffectSettings.COMP_MAKEUP_MIN_DB, AudioEffectSettings.COMP_MAKEUP_MAX_DB) }
    }

    suspend fun setStereoWidth(width: Int) {
        context.dataStore.edit { it[KEY_STEREO_WIDTH] = width.coerceIn(AudioEffectSettings.STEREO_WIDTH_MIN, AudioEffectSettings.STEREO_WIDTH_MAX) }
    }

    suspend fun setSurround360Enabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_SURROUND_360_ENABLED] = enabled
            if (enabled) it[KEY_PANORAMIC_360_ENABLED] = false
        }
    }

    suspend fun setSurround360Intensity(intensity: Int) {
        context.dataStore.edit {
            it[KEY_SURROUND_360_INTENSITY] = intensity.coerceIn(
                AudioEffectSettings.SURROUND_360_INTENSITY_MIN,
                AudioEffectSettings.SURROUND_360_INTENSITY_MAX
            )
        }
    }

    suspend fun setSurround360RotationSpeed(speed: Int) {
        context.dataStore.edit {
            it[KEY_SURROUND_360_ROTATION_SPEED] = speed.coerceIn(
                AudioEffectSettings.SURROUND_360_ROTATION_MIN,
                AudioEffectSettings.SURROUND_360_ROTATION_MAX
            )
        }
    }

    suspend fun setPanoramic360Enabled(enabled: Boolean) {
        context.dataStore.edit {
            it[KEY_PANORAMIC_360_ENABLED] = enabled
            if (enabled) it[KEY_SURROUND_360_ENABLED] = false
        }
    }

    suspend fun setPanoramic360Intensity(intensity: Int) {
        context.dataStore.edit {
            it[KEY_PANORAMIC_360_INTENSITY] = intensity.coerceIn(
                AudioEffectSettings.PANORAMIC_360_INTENSITY_MIN,
                AudioEffectSettings.PANORAMIC_360_INTENSITY_MAX
            )
        }
    }

    suspend fun setPanoramic360AzimuthDegrees(degrees: Int) {
        context.dataStore.edit {
            it[KEY_PANORAMIC_360_AZIMUTH_DEGREES] = degrees.coerceIn(
                AudioEffectSettings.PANORAMIC_360_AZIMUTH_MIN,
                AudioEffectSettings.PANORAMIC_360_AZIMUTH_MAX
            )
        }
    }

    suspend fun setPanoramic360ElevationDegrees(degrees: Int) {
        context.dataStore.edit {
            it[KEY_PANORAMIC_360_ELEVATION_DEGREES] = degrees.coerceIn(
                AudioEffectSettings.PANORAMIC_360_ELEVATION_MIN,
                AudioEffectSettings.PANORAMIC_360_ELEVATION_MAX
            )
        }
    }

    suspend fun setLoudnessBalanceEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LOUDNESS_BALANCE_ENABLED] = enabled }
    }

    suspend fun setLoudnessPercent(percent: Int) {
        context.dataStore.edit {
            it[KEY_LOUDNESS_PERCENT] = percent.coerceIn(
                AudioEffectSettings.LOUDNESS_PERCENT_MIN,
                AudioEffectSettings.LOUDNESS_PERCENT_MAX
            )
        }
    }

    suspend fun setChannelBalance(balance: Int) {
        context.dataStore.edit {
            it[KEY_CHANNEL_BALANCE] = balance.coerceIn(
                AudioEffectSettings.CHANNEL_BALANCE_MIN,
                AudioEffectSettings.CHANNEL_BALANCE_MAX
            )
        }
    }

    suspend fun setCrossfeedEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_CROSSFEED_ENABLED] = enabled }
    }

    suspend fun setCrossfeedLowCutHz(hz: Int) {
        context.dataStore.edit {
            it[KEY_CROSSFEED_LOW_CUT_HZ] = hz.coerceIn(
                AudioEffectSettings.CROSSFEED_LOW_CUT_MIN_HZ,
                AudioEffectSettings.CROSSFEED_LOW_CUT_MAX_HZ
            )
        }
    }

    suspend fun setCrossfeedHighCutHz(hz: Int) {
        context.dataStore.edit {
            it[KEY_CROSSFEED_HIGH_CUT_HZ] = hz.coerceIn(
                AudioEffectSettings.CROSSFEED_HIGH_CUT_MIN_HZ,
                AudioEffectSettings.CROSSFEED_HIGH_CUT_MAX_HZ
            )
        }
    }

    suspend fun setCrossfeedAttenuationTenthsDb(tenthsDb: Int) {
        context.dataStore.edit {
            it[KEY_CROSSFEED_ATTENUATION_TENTHS_DB] = tenthsDb.coerceIn(
                AudioEffectSettings.CROSSFEED_ATTENUATION_MIN_TENTHS_DB,
                AudioEffectSettings.CROSSFEED_ATTENUATION_MAX_TENTHS_DB
            )
        }
    }

    suspend fun setMonoBassEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MONO_BASS_ENABLED] = enabled }
    }

    suspend fun setMonoBassCrossoverHz(hz: Int) {
        context.dataStore.edit {
            it[KEY_MONO_BASS_CROSSOVER_HZ] = hz.coerceIn(
                AudioEffectSettings.MONO_BASS_CROSSOVER_MIN_HZ,
                AudioEffectSettings.MONO_BASS_CROSSOVER_MAX_HZ
            )
        }
    }

    suspend fun setMonoBassAmount(amount: Int) {
        context.dataStore.edit {
            it[KEY_MONO_BASS_AMOUNT] = amount.coerceIn(
                AudioEffectSettings.MONO_BASS_AMOUNT_MIN,
                AudioEffectSettings.MONO_BASS_AMOUNT_MAX
            )
        }
    }

    suspend fun setSpeakerOutputEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SPEAKER_OUTPUT_ENABLED] = enabled }
    }

    suspend fun setSpeakerOutputMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_SPEAKER_OUTPUT_MODE] = mode.coerceIn(
                AudioEffectSettings.SPEAKER_OUTPUT_MODE_ELASTICITY,
                AudioEffectSettings.SPEAKER_OUTPUT_MODE_WIDE
            )
        }
    }

    suspend fun setSpeakerOutputStrength(strength: Int) {
        context.dataStore.edit {
            it[KEY_SPEAKER_OUTPUT_STRENGTH] = strength.coerceIn(
                AudioEffectSettings.SPEAKER_OUTPUT_STRENGTH_MIN,
                AudioEffectSettings.SPEAKER_OUTPUT_STRENGTH_MAX
            )
        }
    }

    suspend fun setDynamicEqEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_EQ_ENABLED] = enabled }
    }

    suspend fun setDynamicEqIntensity(intensity: Int) {
        context.dataStore.edit {
            it[KEY_DYNAMIC_EQ_INTENSITY] = intensity.coerceIn(
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MIN,
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MAX
            )
        }
    }

    suspend fun setDeEsserAmount(amount: Int) {
        context.dataStore.edit {
            it[KEY_DE_ESSER_AMOUNT] = amount.coerceIn(
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MIN,
                AudioEffectSettings.DYNAMIC_EQ_PERCENT_MAX
            )
        }
    }

    suspend fun setDeEsserFrequencyHz(frequencyHz: Int) {
        context.dataStore.edit {
            it[KEY_DE_ESSER_FREQUENCY_HZ] = frequencyHz.coerceIn(
                AudioEffectSettings.DE_ESSER_FREQUENCY_MIN_HZ,
                AudioEffectSettings.DE_ESSER_FREQUENCY_MAX_HZ
            )
        }
    }

    suspend fun setMoogLadderEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MOOG_LADDER_ENABLED] = enabled }
    }

    suspend fun setMoogLadderMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_MOOG_LADDER_MODE] = mode.coerceIn(
                AudioEffectSettings.MOOG_LADDER_MODE_LOW_PASS_24,
                AudioEffectSettings.MOOG_LADDER_MODE_NOTCH
            )
        }
    }

    suspend fun setMoogLadderCutoffHz(cutoffHz: Int) {
        context.dataStore.edit {
            it[KEY_MOOG_LADDER_CUTOFF_HZ] = cutoffHz.coerceIn(
                AudioEffectSettings.MOOG_LADDER_CUTOFF_MIN_HZ,
                AudioEffectSettings.MOOG_LADDER_CUTOFF_MAX_HZ
            )
        }
    }

    suspend fun setMoogLadderResonance(resonance: Int) {
        context.dataStore.edit {
            it[KEY_MOOG_LADDER_RESONANCE] = resonance.coerceIn(
                AudioEffectSettings.MOOG_LADDER_RESONANCE_MIN,
                AudioEffectSettings.MOOG_LADDER_RESONANCE_MAX
            )
        }
    }

    suspend fun setMoogLadderDriveDb(driveDb: Int) {
        context.dataStore.edit {
            it[KEY_MOOG_LADDER_DRIVE_DB] = driveDb.coerceIn(
                AudioEffectSettings.MOOG_LADDER_DRIVE_MIN_DB,
                AudioEffectSettings.MOOG_LADDER_DRIVE_MAX_DB
            )
        }
    }

    suspend fun setMoogLadderMix(mix: Int) {
        context.dataStore.edit {
            it[KEY_MOOG_LADDER_MIX] = mix.coerceIn(
                AudioEffectSettings.MOOG_LADDER_MIX_MIN,
                AudioEffectSettings.MOOG_LADDER_MIX_MAX
            )
        }
    }

    suspend fun setPeakLimiterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PEAK_LIMITER_ENABLED] = enabled }
    }

    suspend fun setPlatformSpatialAudioEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLATFORM_SPATIAL_AUDIO_ENABLED] = enabled }
    }

    suspend fun setUsbDacMode(enabled: Boolean) {
        context.dataStore.edit { it[KEY_USB_DAC_MODE] = enabled }
    }

    private fun parseEqBands(raw: String?): List<Int> {
        if (raw.isNullOrBlank()) return List(FIXED_EQ_BAND_COUNT) { 0 }
        return raw.split(',').mapNotNull { it.trim().toIntOrNull() }.normalizedEqBands()
    }

    private fun List<Int>.normalizedEqBands(): List<Int> =
        List(FIXED_EQ_BAND_COUNT) { index -> getOrElse(index) { 0 } }

    private fun normalizeReverbPreset(preset: Int): Int =
        when (preset) {
            AudioEffectSettings.REVERB_PRESET_OFF,
            AudioEffectSettings.REVERB_PRESET_STUDIO,
            AudioEffectSettings.REVERB_PRESET_SMALL_ROOM,
            AudioEffectSettings.REVERB_PRESET_MEDIUM_ROOM,
            AudioEffectSettings.REVERB_PRESET_LARGE_ROOM,
            AudioEffectSettings.REVERB_PRESET_HALL,
            AudioEffectSettings.REVERB_PRESET_CHURCH,
            AudioEffectSettings.REVERB_PRESET_PLATE -> preset
            else -> AudioEffectSettings.REVERB_PRESET_OFF
        }

    suspend fun setDynamicCoverEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DYNAMIC_COVER_ENABLED] = enabled }
    }

    suspend fun setMusicVideoSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MUSIC_VIDEO_SYNC_ENABLED] = enabled }
    }

    suspend fun setMusicVideoCaptureSubtitles(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MUSIC_VIDEO_CAPTURE_SUBTITLES] = enabled }
    }

    suspend fun setDynamicCoverCustomFolders(folders: String) {
        context.dataStore.edit { prefs ->
            val normalized = normalizeDynamicCoverCustomFolders(folders)
            if (normalized.isBlank()) {
                prefs.remove(KEY_DYNAMIC_COVER_CUSTOM_FOLDERS)
            } else {
                prefs[KEY_DYNAMIC_COVER_CUSTOM_FOLDERS] = normalized
            }
        }
    }

    suspend fun setArtistCoverCarousel(carousel: Boolean) {
        context.dataStore.edit { it[KEY_ARTIST_COVER_CAROUSEL] = carousel }
    }

    suspend fun setArtistCoverFolderUri(uri: String) {
        context.dataStore.edit { prefs ->
            val safeUri = uri.trim()
            if (safeUri.isBlank()) {
                prefs.remove(KEY_ARTIST_COVER_FOLDER_URI)
            } else {
                prefs[KEY_ARTIST_COVER_FOLDER_URI] = safeUri
            }
        }
    }

    suspend fun setMcpServerEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MCP_SERVER_ENABLED] = enabled }
    }

    suspend fun setStartupPosterEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_STARTUP_POSTER_ENABLED] = enabled }
    }

    suspend fun setStartupPosterUri(uri: String) {
        context.dataStore.edit {
            val safeUri = uri.trim()
            if (safeUri.isBlank()) it.remove(KEY_STARTUP_POSTER_URI) else it[KEY_STARTUP_POSTER_URI] = safeUri
        }
    }

    suspend fun setStartupPosterDurationMs(durationMs: Int) {
        context.dataStore.edit {
            it[KEY_STARTUP_POSTER_DURATION_MS] = durationMs.coerceIn(
                STARTUP_POSTER_DURATION_MIN_MS,
                STARTUP_POSTER_DURATION_MAX_MS
            )
        }
    }

    suspend fun setAppWallpaperEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_APP_WALLPAPER_ENABLED] = enabled }
    }

    suspend fun setAppWallpaperUri(uri: String) {
        context.dataStore.edit {
            val safeUri = uri.trim()
            if (safeUri.isBlank()) it.remove(KEY_APP_WALLPAPER_URI) else it[KEY_APP_WALLPAPER_URI] = safeUri
        }
    }

    suspend fun setAppWallpaperOpacity(opacity: Int) {
        context.dataStore.edit { it[KEY_APP_WALLPAPER_OPACITY] = opacity.coerceIn(20, 100) }
    }

    suspend fun setAppWallpaperDim(dim: Int) {
        context.dataStore.edit { it[KEY_APP_WALLPAPER_DIM] = dim.coerceIn(0, 80) }
    }

    suspend fun setAppWallpaperContentOverlay(strength: Int) {
        context.dataStore.edit { it[KEY_APP_WALLPAPER_CONTENT_OVERLAY] = strength.coerceIn(0, 80) }
    }

    suspend fun setPlayerBackgroundEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_BACKGROUND_ENABLED] = enabled }
    }

    suspend fun setPlayerBackgroundUri(uri: String) {
        context.dataStore.edit {
            val safeUri = uri.trim()
            if (safeUri.isBlank()) it.remove(KEY_PLAYER_BACKGROUND_URI) else it[KEY_PLAYER_BACKGROUND_URI] = safeUri
        }
    }

    suspend fun setPlayerBackgroundOpacity(opacity: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BACKGROUND_OPACITY] = opacity.coerceIn(20, 100) }
    }

    suspend fun setPlayerBackgroundDim(dim: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BACKGROUND_DIM] = dim.coerceIn(0, 80) }
    }

    suspend fun setPlayerBeautifulLyricsBackground(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BACKGROUND] = enabled }
    }

    suspend fun setPlayerBeautifulLyricsSpeed(value: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BEAUTIFUL_LYRICS_SPEED] = value.coerceIn(5, 60) }
    }

    suspend fun setPlayerBeautifulLyricsBlur(value: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BLUR] = value.coerceIn(0, 80) }
    }

    suspend fun setPlayerBeautifulLyricsBrightness(value: Int) {
        context.dataStore.edit { it[KEY_PLAYER_BEAUTIFUL_LYRICS_BRIGHTNESS] = value.coerceIn(30, 120) }
    }

    suspend fun setHomeCardColor(color: String) {
        context.dataStore.edit {
            val safeColor = color.trim()
            if (safeColor.isBlank()) it.remove(KEY_HOME_CARD_COLOR) else it[KEY_HOME_CARD_COLOR] = safeColor
        }
    }

    suspend fun setHomeCardOpacity(opacity: Int) {
        context.dataStore.edit { it[KEY_HOME_CARD_OPACITY] = opacity.coerceIn(20, 100) }
    }

    suspend fun setHomeTileColor(tileId: String, color: String) {
        val safeId = tileId.trim().lowercase(Locale.ROOT).takeIf { it.matches(Regex("""[a-z0-9_]+""")) } ?: return
        val safeColor = color.trim().takeIf { it.isBlank() || it.matches(Regex("""#[0-9A-Fa-f]{8}""")) } ?: return
        context.dataStore.edit { prefs ->
            val json = runCatching { JSONObject(prefs[KEY_HOME_TILE_COLORS].orEmpty()) }.getOrElse { JSONObject() }
            if (safeColor.isBlank()) json.remove(safeId) else json.put(safeId, safeColor.uppercase(Locale.ROOT))
            if (json.length() == 0) prefs.remove(KEY_HOME_TILE_COLORS) else prefs[KEY_HOME_TILE_COLORS] = json.toString()
        }
    }

    suspend fun setHomeTileGradientEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HOME_TILE_GRADIENT_ENABLED] = enabled }
    }

    suspend fun setHomeTileGradientStartColor(color: String) {
        context.dataStore.edit {
            val safeColor = color.trim()
            if (safeColor.isBlank()) it.remove(KEY_HOME_TILE_GRADIENT_START_COLOR) else it[KEY_HOME_TILE_GRADIENT_START_COLOR] = safeColor
        }
    }

    suspend fun setHiResLogoEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_HI_RES_LOGO_ENABLED] = enabled }
    }

    suspend fun setHiResLogoUri(uri: String) {
        context.dataStore.edit {
            val safeUri = uri.trim()
            if (safeUri.isBlank()) it.remove(KEY_HI_RES_LOGO_URI) else it[KEY_HI_RES_LOGO_URI] = safeUri
        }
    }

    suspend fun setPlaylistSpecialEntriesVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_PLAYLIST_SPECIAL_ENTRIES_VISIBLE] = visible }
    }

    suspend fun setShowPlayNextInLists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_PLAY_NEXT_IN_LISTS] = enabled }
    }

    suspend fun setExcludeSearchResultsFromPlaylist(enabled: Boolean) {
        context.dataStore.edit { it[KEY_EXCLUDE_SEARCH_RESULTS_FROM_PLAYLIST] = enabled }
    }

    suspend fun setAutoShowSearchKeyboard(enabled: Boolean) {
        context.dataStore.edit { it[KEY_AUTO_SHOW_SEARCH_KEYBOARD] = enabled }
    }

    suspend fun setPlayNextMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_PLAY_NEXT_MODE] = mode.coerceIn(PLAY_NEXT_MODE_REVERSE_STACK, PLAY_NEXT_MODE_FORWARD_STACK)
        }
    }

    suspend fun setLyricShareCustomInfo(info: String) {
        context.dataStore.edit {
            val trimmed = info.trim().removePrefix("@").trim()
            if (trimmed.isBlank()) {
                it.remove(KEY_LYRIC_SHARE_CUSTOM_INFO)
            } else {
                it[KEY_LYRIC_SHARE_CUSTOM_INFO] = trimmed
            }
        }
    }

    suspend fun setLyricShareUseLyricFont(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_SHARE_USE_LYRIC_FONT] = enabled }
    }

    suspend fun setLibrarySource(source: String) {
        val normalized = normalizeLibrarySource(source)
        context.dataStore.edit { it[KEY_LIBRARY_SOURCE] = normalized }
    }

    suspend fun setShowAlbumArtists(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SHOW_ALBUM_ARTISTS] = enabled }
    }

    suspend fun setMetadataEditorId(id: String) {
        context.dataStore.edit {
            val safeId = id.trim()
            if (safeId.isBlank()) it.remove(KEY_METADATA_EDITOR_ID) else it[KEY_METADATA_EDITOR_ID] = safeId
        }
    }

    suspend fun setLyricTimingEditorId(id: String) {
        context.dataStore.edit {
            val safeId = id.trim()
            if (safeId.isBlank()) it.remove(KEY_LYRIC_TIMING_EDITOR_ID) else it[KEY_LYRIC_TIMING_EDITOR_ID] = safeId
        }
    }

    suspend fun setSleepTimerCustomMinutes(minutes: Int) {
        context.dataStore.edit { it[KEY_SLEEP_TIMER_CUSTOM_MINUTES] = minutes.coerceIn(5, 120) }
    }

    suspend fun setSleepTimerStopAfterCurrent(enabled: Boolean) {
        context.dataStore.edit { it[KEY_SLEEP_TIMER_STOP_AFTER_CURRENT] = enabled }
    }

    suspend fun setShortcutLibraryLabel(label: String) {
        setShortcutLabel(KEY_SHORTCUT_LIBRARY_LABEL, label, DEFAULT_SHORTCUT_LIBRARY_LABEL)
    }

    suspend fun setShortcutPlaylistsLabel(label: String) {
        setShortcutLabel(KEY_SHORTCUT_PLAYLISTS_LABEL, label, DEFAULT_SHORTCUT_PLAYLISTS_LABEL)
    }

    suspend fun setShortcutFolderLabel(label: String) {
        setShortcutLabel(KEY_SHORTCUT_FOLDER_LABEL, label, DEFAULT_SHORTCUT_FOLDER_LABEL)
    }

    suspend fun setAppShortcutOrder(shortcutIds: List<String>) {
        context.dataStore.edit {
            // Persist an explicit empty value too: users may deliberately choose no dynamic
            // shortcuts, while a missing preference means "keep the four legacy defaults".
            it[KEY_APP_SHORTCUT_ORDER] = normalizeAppShortcutOrder(shortcutIds.joinToString(","))
                .joinToString(",")
        }
    }

    private suspend fun setShortcutLabel(
        key: Preferences.Key<String>,
        label: String,
        defaultLabel: String
    ) {
        context.dataStore.edit {
            val safeLabel = label.trim().take(24)
            if (safeLabel.isBlank() || safeLabel == defaultLabel) it.remove(key) else it[key] = safeLabel
        }
    }

    suspend fun setWebDavConfig(url: String, username: String, password: String) {
        context.dataStore.edit {
            it[KEY_WEBDAV_URL] = url.trim()
            it[KEY_WEBDAV_USERNAME] = username
            it[KEY_WEBDAV_PASSWORD] = password
            it[KEY_WEBDAV_LAST_URL] = url.trim()
        }
    }

    suspend fun setWebDavLastUrl(url: String) {
        context.dataStore.edit {
            if (url.isBlank()) it.remove(KEY_WEBDAV_LAST_URL) else it[KEY_WEBDAV_LAST_URL] = url.trim()
        }
    }

    suspend fun clearWebDavConfig() {
        context.dataStore.edit {
            it.remove(KEY_WEBDAV_URL)
            it.remove(KEY_WEBDAV_USERNAME)
            it.remove(KEY_WEBDAV_PASSWORD)
            it.remove(KEY_WEBDAV_LAST_URL)
        }
    }

    suspend fun setWebDavBackupUrl(url: String) {
        context.dataStore.edit {
            if (url.isBlank()) it.remove(KEY_WEBDAV_BACKUP_URL) else it[KEY_WEBDAV_BACKUP_URL] = url.trim()
        }
    }

    suspend fun setWebDavBackupPath(path: String) {
        context.dataStore.edit {
            if (path.isBlank()) it.remove(KEY_WEBDAV_BACKUP_PATH) else it[KEY_WEBDAV_BACKUP_PATH] = path.trim()
        }
    }

    suspend fun setLxSource(url: String, name: String, script: String) {
        context.dataStore.edit {
            val source = LxSourceConfig(
                id = url.toLxSourceId(script),
                url = url.trim(),
                name = name.ifBlank { context.getString(R.string.settings_default_lx_source_name) },
                script = script
            )
            val sources = it.lxSources().filterNot { existing -> existing.id == source.id } + source
            it[KEY_LX_SOURCES_JSON] = sources.toLxSourcesJson()
            it[KEY_LX_SELECTED_SOURCE_ID] = source.id
            it[KEY_LX_SOURCE_URL] = source.url
            it[KEY_LX_SOURCE_NAME] = source.name
            it[KEY_LX_SOURCE_SCRIPT] = source.script
        }
    }

    suspend fun clearLxSource() {
        context.dataStore.edit {
            it.remove(KEY_LX_SOURCES_JSON)
            it.remove(KEY_LX_SELECTED_SOURCE_ID)
            it.remove(KEY_LX_SOURCE_URL)
            it.remove(KEY_LX_SOURCE_NAME)
            it.remove(KEY_LX_SOURCE_SCRIPT)
        }
    }

    suspend fun selectLxSource(id: String) {
        context.dataStore.edit { prefs ->
            val source = prefs.lxSources().firstOrNull { it.id == id } ?: return@edit
            prefs[KEY_LX_SELECTED_SOURCE_ID] = source.id
            prefs[KEY_LX_SOURCE_URL] = source.url
            prefs[KEY_LX_SOURCE_NAME] = source.name
            prefs[KEY_LX_SOURCE_SCRIPT] = source.script
        }
    }

    suspend fun removeLxSource(id: String) {
        context.dataStore.edit { prefs ->
            val sources = prefs.lxSources().filterNot { it.id == id }
            if (sources.isEmpty()) {
                prefs.remove(KEY_LX_SOURCES_JSON)
                prefs.remove(KEY_LX_SELECTED_SOURCE_ID)
                prefs.remove(KEY_LX_SOURCE_URL)
                prefs.remove(KEY_LX_SOURCE_NAME)
                prefs.remove(KEY_LX_SOURCE_SCRIPT)
            } else {
                val selected = sources.firstOrNull { it.id == prefs[KEY_LX_SELECTED_SOURCE_ID] } ?: sources.first()
                prefs[KEY_LX_SOURCES_JSON] = sources.toLxSourcesJson()
                prefs[KEY_LX_SELECTED_SOURCE_ID] = selected.id
                prefs[KEY_LX_SOURCE_URL] = selected.url
                prefs[KEY_LX_SOURCE_NAME] = selected.name
                prefs[KEY_LX_SOURCE_SCRIPT] = selected.script
            }
        }
    }

    suspend fun selectOnlineProvider(provider: RemoteMusicProvider) {
        context.dataStore.edit { it[KEY_ONLINE_SELECTED_PROVIDER] = provider.id }
    }

    private fun String.remoteServerDisplayName(): String =
        substringAfter("://").substringBefore('/').trim().ifBlank { trim() }

    fun newRemoteServerId(): String = "server-${System.currentTimeMillis()}-${(0..9999).random()}"

    suspend fun upsertNavidromeServer(server: SavedRemoteServer) {
        context.dataStore.edit { prefs ->
            val current = readNavidromeServers(prefs)
            val next = if (current.any { it.id == server.id }) {
                current.map { if (it.id == server.id) server else it }
            } else {
                current + server
            }
            prefs[KEY_NAVIDROME_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_NAVIDROME_ACTIVE_ID].isNullOrBlank()) prefs[KEY_NAVIDROME_ACTIVE_ID] = server.id
        }
    }

    suspend fun deleteNavidromeServer(id: String) {
        context.dataStore.edit { prefs ->
            val next = readNavidromeServers(prefs).filterNot { it.id == id }
            prefs[KEY_NAVIDROME_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_NAVIDROME_ACTIVE_ID] == id) {
                val fallback = next.firstOrNull()?.id
                if (fallback == null) prefs.remove(KEY_NAVIDROME_ACTIVE_ID) else prefs[KEY_NAVIDROME_ACTIVE_ID] = fallback
            }
        }
    }

    suspend fun setActiveNavidromeServer(id: String) {
        context.dataStore.edit { it[KEY_NAVIDROME_ACTIVE_ID] = id }
    }

    suspend fun upsertOpenSubsonicServer(server: SavedRemoteServer) {
        context.dataStore.edit { prefs ->
            val current = readOpenSubsonicServers(prefs)
            val next = if (current.any { it.id == server.id }) {
                current.map { if (it.id == server.id) server else it }
            } else {
                current + server
            }
            prefs[KEY_OPENSUBSONIC_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_OPENSUBSONIC_ACTIVE_ID].isNullOrBlank()) prefs[KEY_OPENSUBSONIC_ACTIVE_ID] = server.id
        }
    }

    suspend fun setWebDavBackupCredentials(username: String, password: String) {
        context.dataStore.edit {
            if (username.isBlank()) it.remove(KEY_WEBDAV_BACKUP_USERNAME) else it[KEY_WEBDAV_BACKUP_USERNAME] = username
            if (password.isBlank()) it.remove(KEY_WEBDAV_BACKUP_PASSWORD) else it[KEY_WEBDAV_BACKUP_PASSWORD] = password
        }
    }

    suspend fun setWebDavAutoBackupEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_WEBDAV_AUTO_BACKUP_ENABLED] = enabled }
    }

    suspend fun setWebDavAutoBackupIntervalHours(hours: Int) {
        context.dataStore.edit { it[KEY_WEBDAV_AUTO_BACKUP_INTERVAL_HOURS] = hours.coerceIn(1, 168) }
    }

    suspend fun setWebDavAutoBackupLastAt(timestamp: Long) {
        context.dataStore.edit { it[KEY_WEBDAV_AUTO_BACKUP_LAST_AT] = timestamp.coerceAtLeast(0L).toString() }
    }

    suspend fun deleteOpenSubsonicServer(id: String) {
        context.dataStore.edit { prefs ->
            val next = readOpenSubsonicServers(prefs).filterNot { it.id == id }
            prefs[KEY_OPENSUBSONIC_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_OPENSUBSONIC_ACTIVE_ID] == id) {
                val fallback = next.firstOrNull()?.id
                if (fallback == null) prefs.remove(KEY_OPENSUBSONIC_ACTIVE_ID) else prefs[KEY_OPENSUBSONIC_ACTIVE_ID] = fallback
            }
        }
    }

    suspend fun setActiveOpenSubsonicServer(id: String) {
        context.dataStore.edit { it[KEY_OPENSUBSONIC_ACTIVE_ID] = id }
    }

    suspend fun upsertEmbyServer(server: SavedRemoteServer) {
        context.dataStore.edit { prefs ->
            val current = readEmbyServers(prefs)
            val next = if (current.any { it.id == server.id }) {
                current.map { if (it.id == server.id) server else it }
            } else {
                current + server
            }
            prefs[KEY_EMBY_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_EMBY_ACTIVE_ID].isNullOrBlank()) prefs[KEY_EMBY_ACTIVE_ID] = server.id
        }
    }

    suspend fun deleteEmbyServer(id: String) {
        context.dataStore.edit { prefs ->
            val next = readEmbyServers(prefs).filterNot { it.id == id }
            prefs[KEY_EMBY_SERVERS] = next.toRemoteServersJson()
            if (prefs[KEY_EMBY_ACTIVE_ID] == id) {
                val fallback = next.firstOrNull()?.id
                if (fallback == null) prefs.remove(KEY_EMBY_ACTIVE_ID) else prefs[KEY_EMBY_ACTIVE_ID] = fallback
            }
        }
    }

    suspend fun setActiveEmbyServer(id: String) {
        context.dataStore.edit { it[KEY_EMBY_ACTIVE_ID] = id }
    }

    suspend fun setOpenAiApiKey(apiKey: String) {
        context.dataStore.edit {
            val trimmed = apiKey.trim()
            if (trimmed.isBlank()) it.remove(KEY_OPENAI_API_KEY) else it[KEY_OPENAI_API_KEY] = trimmed
        }
    }

    suspend fun setOpenAiBaseUrl(baseUrl: String) {
        context.dataStore.edit {
            it[KEY_OPENAI_BASE_URL] = baseUrl.trim().ifBlank { DEFAULT_OPENAI_BASE_URL }
        }
    }

    suspend fun setOpenAiModel(model: String) {
        context.dataStore.edit {
            it[KEY_OPENAI_MODEL] = model.trim().ifBlank { DEFAULT_OPENAI_MODEL }
        }
    }

    suspend fun setOpenPlayerOnPlay(enabled: Boolean) {
        context.dataStore.edit { it[KEY_OPEN_PLAYER_ON_PLAY] = enabled }
    }

    suspend fun setStartupAutoPlay(enabled: Boolean) {
        setStartupPlayMode(if (enabled) STARTUP_PLAY_RANDOM else STARTUP_PLAY_OFF)
    }

    suspend fun setBluetoothAutoPlay(enabled: Boolean) {
        context.dataStore.edit { it[KEY_BLUETOOTH_AUTO_PLAY] = enabled }
    }

    suspend fun setStartupPlayMode(mode: Int) {
        val safeMode = mode.coerceIn(STARTUP_PLAY_OFF, STARTUP_PLAY_RESUME)
        context.dataStore.edit {
            it[KEY_STARTUP_PLAY_MODE] = safeMode
            it[KEY_STARTUP_AUTO_PLAY] = safeMode != STARTUP_PLAY_OFF
        }
    }

    suspend fun setLyricFont(name: String, path: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_FONT_NAME] = name.ifBlank { context.getString(R.string.settings_default_custom_font_name) }
            it[KEY_LYRIC_FONT_PATH] = path
        }
    }

    suspend fun clearLyricFont() {
        context.dataStore.edit {
            it.remove(KEY_LYRIC_FONT_NAME)
            it.remove(KEY_LYRIC_FONT_PATH)
        }
    }

    suspend fun setLyricWesternFont(name: String, path: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_WESTERN_FONT_NAME] = name.ifBlank { "Inter" }
            it[KEY_LYRIC_WESTERN_FONT_PATH] = path
        }
    }

    suspend fun clearLyricWesternFont() {
        context.dataStore.edit {
            it.remove(KEY_LYRIC_WESTERN_FONT_NAME)
            it.remove(KEY_LYRIC_WESTERN_FONT_PATH)
        }
    }

    suspend fun setLyricCjkFont(name: String, path: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_CJK_FONT_NAME] = name.ifBlank { context.getString(R.string.settings_default_custom_font_name) }
            it[KEY_LYRIC_CJK_FONT_PATH] = path
        }
    }

    suspend fun clearLyricCjkFont() {
        context.dataStore.edit {
            it.remove(KEY_LYRIC_CJK_FONT_NAME)
            it.remove(KEY_LYRIC_CJK_FONT_PATH)
        }
    }

    suspend fun setGlobalFont(westernName: String, westernPath: String, cjkName: String, cjkPath: String) {
        context.dataStore.edit {
            it[KEY_GLOBAL_WESTERN_FONT_NAME] = westernName
            it[KEY_GLOBAL_WESTERN_FONT_PATH] = westernPath
            it[KEY_GLOBAL_CJK_FONT_NAME] = cjkName
            it[KEY_GLOBAL_CJK_FONT_PATH] = cjkPath
        }
    }

    suspend fun setLyricOriginalFont(westernName: String, westernPath: String, cjkName: String, cjkPath: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_ORIGINAL_WESTERN_FONT_NAME] = westernName
            it[KEY_LYRIC_ORIGINAL_WESTERN_FONT_PATH] = westernPath
            it[KEY_LYRIC_ORIGINAL_CJK_FONT_NAME] = cjkName
            it[KEY_LYRIC_ORIGINAL_CJK_FONT_PATH] = cjkPath
        }
    }

    suspend fun setLyricTranslationFont(westernName: String, westernPath: String, cjkName: String, cjkPath: String) {
        context.dataStore.edit {
            it[KEY_LYRIC_TRANSLATION_WESTERN_FONT_NAME] = westernName
            it[KEY_LYRIC_TRANSLATION_WESTERN_FONT_PATH] = westernPath
            it[KEY_LYRIC_TRANSLATION_CJK_FONT_NAME] = cjkName
            it[KEY_LYRIC_TRANSLATION_CJK_FONT_PATH] = cjkPath
        }
    }

    suspend fun setLyricFontWeight(weight: Int) {
        context.dataStore.edit { it[KEY_LYRIC_FONT_WEIGHT] = weight.coerceIn(100, 900) }
    }

    suspend fun setLyricFontScale(scale: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_FONT_SCALE] = scale.coerceIn(LYRIC_FONT_SCALE_MIN, LYRIC_FONT_SCALE_ULTRA_WIDE_MAX)
        }
    }

    suspend fun setLyricSecondaryFontScale(scale: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_SECONDARY_FONT_SCALE] =
                scale.coerceIn(LYRIC_SECONDARY_FONT_SCALE_MIN, LYRIC_SECONDARY_FONT_SCALE_ULTRA_WIDE_MAX)
        }
    }

    suspend fun setLyricCompactPrimaryTextSize(sizeSp: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_COMPACT_PRIMARY_TEXT_SIZE] =
                sizeSp.coerceIn(LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MIN_SP, LYRIC_COMPACT_PRIMARY_TEXT_SIZE_MAX_SP)
        }
    }

    suspend fun setLyricCompactSecondaryTextSize(sizeSp: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_COMPACT_SECONDARY_TEXT_SIZE] =
                sizeSp.coerceIn(LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MIN_SP, LYRIC_COMPACT_SECONDARY_TEXT_SIZE_MAX_SP)
        }
    }

    suspend fun setLyricWidePrimaryTextSize(sizeSp: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_WIDE_PRIMARY_TEXT_SIZE] =
                sizeSp.coerceIn(LYRIC_WIDE_PRIMARY_TEXT_SIZE_MIN_SP, LYRIC_WIDE_PRIMARY_TEXT_SIZE_MAX_SP)
        }
    }

    suspend fun setLyricWideSecondaryTextSize(sizeSp: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_WIDE_SECONDARY_TEXT_SIZE] =
                sizeSp.coerceIn(LYRIC_WIDE_SECONDARY_TEXT_SIZE_MIN_SP, LYRIC_WIDE_SECONDARY_TEXT_SIZE_MAX_SP)
        }
    }

    suspend fun setLyricFontItalic(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_FONT_ITALIC] = enabled }
    }

    suspend fun setLyricFontApplyToPage(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_FONT_APPLY_TO_PAGE] = enabled }
    }

    suspend fun setLyricFontApplyToDesktop(enabled: Boolean) {
        context.dataStore.edit { it[KEY_LYRIC_FONT_APPLY_TO_DESKTOP] = enabled }
    }

    suspend fun setPlayerTapSeekEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_TAP_SEEK_ENABLED] = enabled }
    }

    suspend fun setPlayerShowTotalDuration(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_SHOW_TOTAL_DURATION] = enabled }
    }

    suspend fun setPlayerShowSongAnnotation(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_SHOW_SONG_ANNOTATION] = enabled }
    }

    suspend fun setPlayerCoverSwipeEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_COVER_SWIPE_ENABLED] = enabled }
    }

    suspend fun setLyricParserEngine(engine: Int) {
        context.dataStore.edit {
            it[KEY_LYRIC_PARSER_ENGINE] = engine.coerceIn(LYRIC_PARSER_ENGINE_AUTO, LYRIC_PARSER_ENGINE_ELLA)
        }
    }

    suspend fun setPlayerTitlePosition(position: Int) {
        context.dataStore.edit {
            it[KEY_PLAYER_TITLE_POSITION] = position.coerceIn(
                PLAYER_TITLE_POSITION_BELOW_COVER,
                PLAYER_TITLE_POSITION_ABOVE_COVER
            )
        }
    }

    suspend fun setPlayerKeepScreenOn(enabled: Boolean) {
        context.dataStore.edit { it[KEY_PLAYER_KEEP_SCREEN_ON] = enabled }
    }

    suspend fun setLibrarySongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_LIBRARY_SONG, index)
    }

    suspend fun setAlbumListSortIndex(index: Int) {
        setSortIndex(KEY_SORT_ALBUM_LIST, index)
    }

    suspend fun setArtistListSortIndex(index: Int) {
        setSortIndex(KEY_SORT_ARTIST_LIST, index)
    }

    suspend fun setAlbumDetailSongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_ALBUM_DETAIL_SONG, index)
    }

    suspend fun setArtistDetailSongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_ARTIST_DETAIL_SONG, index)
    }

    suspend fun setArtistDetailAlbumSortIndex(index: Int) {
        setSortIndex(KEY_SORT_ARTIST_DETAIL_ALBUM, index)
    }

    suspend fun setFolderListSortIndex(index: Int) {
        setSortIndex(KEY_SORT_FOLDER_LIST, index)
    }

    suspend fun setFolderDetailSongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_FOLDER_DETAIL_SONG, index)
    }

    suspend fun setFolderPlaylistListSortIndex(index: Int) {
        setSortIndex(KEY_SORT_FOLDER_PLAYLIST_LIST, index)
    }

    suspend fun setFolderPlaylistDetailSongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_FOLDER_PLAYLIST_DETAIL_SONG, index)
    }

    suspend fun setFolderPlaylistDetailFolderSortIndex(index: Int) {
        setSortIndex(KEY_SORT_FOLDER_PLAYLIST_DETAIL_FOLDER, index)
    }

    suspend fun setPlaylistListSortIndex(index: Int) {
        setSortIndex(KEY_SORT_PLAYLIST_LIST, index)
    }

    suspend fun setPlaylistCustomOrder(ids: List<String>) {
        context.dataStore.edit {
            it[KEY_PLAYLIST_CUSTOM_ORDER] = ids
                .map(String::trim)
                .filter(String::isNotBlank)
                .joinToString(separator = "\n")
        }
    }

    // Generic "pin to top" store, keyed by an arbitrary namespace (e.g. "artist",
    // "album", "category:genre"). The ordered list keeps the most-recently pinned first.
    fun pinnedKeysFlow(namespace: String): Flow<List<String>> =
        context.dataStore.data.map { prefs ->
            prefs[stringPreferencesKey("pinned_$namespace")]
                ?.split("\n")
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?: emptyList()
        }

    suspend fun setPinned(namespace: String, key: String, pinned: Boolean) {
        val trimmed = key.trim()
        if (trimmed.isBlank()) return
        context.dataStore.edit { prefs ->
            val prefKey = stringPreferencesKey("pinned_$namespace")
            val current = prefs[prefKey]
                ?.split("\n")
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.toMutableList()
                ?: mutableListOf()
            current.remove(trimmed)
            if (pinned) current.add(0, trimmed)
            prefs[prefKey] = current.joinToString("\n")
        }
    }

    suspend fun setPlaylistDetailSongSortIndex(index: Int) {
        setSortIndex(KEY_SORT_PLAYLIST_DETAIL_SONG, index)
    }

    suspend fun setAddToPlaylistAppendToEnd(appendToEnd: Boolean) {
        context.dataStore.edit { it[KEY_ADD_TO_PLAYLIST_APPEND_TO_END] = appendToEnd }
    }

    suspend fun setCategoryGridColumns(columns: Int) {
        val tablet = context.resources.configuration.smallestScreenWidthDp >= 600
        context.dataStore.edit { it[KEY_CATEGORY_GRID_COLUMNS] = columns.coerceIn(if (tablet) 5 else 1, if (tablet) 8 else 4) }
    }

    suspend fun setHomeDailyMixVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_HOME_DAILY_MIX_VISIBLE] = visible }
    }

    suspend fun setHomeAiMixVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_HOME_AI_MIX_VISIBLE] = visible }
    }

    suspend fun setHomeSectionOrder(order: String) {
        context.dataStore.edit { it[KEY_HOME_SECTION_ORDER] = order.trim() }
    }

    suspend fun setHomeHiddenSections(hidden: String) {
        context.dataStore.edit { it[KEY_HOME_HIDDEN_SECTIONS] = hidden.trim() }
    }

    suspend fun setHomeLibraryTileOrder(order: String) {
        context.dataStore.edit { it[KEY_HOME_LIBRARY_TILE_ORDER] = order.trim() }
    }

    suspend fun setHomeHiddenLibraryTiles(hidden: String) {
        context.dataStore.edit { it[KEY_HOME_HIDDEN_LIBRARY_TILES] = hidden.trim() }
    }

    suspend fun setHomeOnlineTileOrder(order: String) {
        context.dataStore.edit { it[KEY_HOME_ONLINE_TILE_ORDER] = order.trim() }
    }

    suspend fun setHomeHiddenOnlineTiles(hidden: String) {
        context.dataStore.edit { it[KEY_HOME_HIDDEN_ONLINE_TILES] = hidden.trim() }
    }

    suspend fun setHomeTilePinButtonsVisible(visible: Boolean) {
        context.dataStore.edit { it[KEY_HOME_TILE_PIN_BUTTONS_VISIBLE] = visible }
    }

    suspend fun upsertFolderPlaylist(
        playlistId: String?,
        name: String,
        folders: List<String>
    ): FolderPlaylist? {
        val safeName = name.trim()
        val safeFolders = folders
            .map { it.replace('\\', '/').trim().trimEnd('/').ifBlank { "/" } }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
        if (safeName.isBlank() || safeFolders.isEmpty()) return null
        var saved: FolderPlaylist? = null
        context.dataStore.edit { prefs ->
            val now = System.currentTimeMillis()
            val current = prefs[KEY_FOLDER_PLAYLISTS].orEmpty().toFolderPlaylists()
            val existing = playlistId?.let { id -> current.firstOrNull { it.id == id } }
            if (current.any { it.id != existing?.id && it.name.trim().equals(safeName, ignoreCase = true) }) {
                return@edit
            }
            val nextItem = FolderPlaylist(
                id = existing?.id ?: "folder-playlist-$now",
                name = safeName,
                folders = safeFolders,
                createdAt = existing?.createdAt ?: now,
                updatedAt = now
            )
            saved = nextItem
            val next = if (existing == null) {
                current + nextItem
            } else {
                current.map { if (it.id == existing.id) nextItem else it }
            }
            prefs[KEY_FOLDER_PLAYLISTS] = next.toFolderPlaylistJson()
        }
        return saved
    }

    suspend fun deleteFolderPlaylist(playlistId: String) {
        val safeId = playlistId.trim()
        if (safeId.isBlank()) return
        context.dataStore.edit { prefs ->
            val next = prefs[KEY_FOLDER_PLAYLISTS].orEmpty()
                .toFolderPlaylists()
                .filterNot { it.id == safeId }
            if (next.isEmpty()) prefs.remove(KEY_FOLDER_PLAYLISTS) else prefs[KEY_FOLDER_PLAYLISTS] = next.toFolderPlaylistJson()
        }
    }

    suspend fun setMetadataCategorySortIndex(type: String, index: Int) {
        setSortIndex(metadataCategorySortKey(type), index)
    }

    suspend fun setMetadataCategoryDetailSongSortIndex(type: String, index: Int) {
        setSortIndex(metadataCategoryDetailSongSortKey(type), index)
    }

    suspend fun setMetadataCategoryDetailAlbumSortIndex(type: String, index: Int) {
        setSortIndex(metadataCategoryDetailAlbumSortKey(type), index)
    }

    suspend fun setScanIncludeFolders(folders: String) {
        context.dataStore.edit { it[KEY_SCAN_INCLUDE_FOLDERS] = folders.trim() }
    }

    suspend fun setUseAndroidMediaLibrary(enabled: Boolean) {
        context.dataStore.edit { it[KEY_USE_ANDROID_MEDIA_LIBRARY] = enabled }
    }

    suspend fun setFullTagSearchEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FULL_TAG_SEARCH_ENABLED] = enabled }
    }

    suspend fun setFullTagSearchPromptHandled(handled: Boolean) {
        context.dataStore.edit { it[KEY_FULL_TAG_SEARCH_PROMPT_HANDLED] = handled }
    }

    suspend fun setCoverExportFolderUri(uri: String) {
        context.dataStore.edit { it[KEY_COVER_EXPORT_FOLDER_URI] = uri.trim() }
    }

    suspend fun setSearchAllCategoryTypeEnabled(type: String, enabled: Boolean) {
        val normalized = type.trim().lowercase()
        if (normalized !in SEARCH_ALL_CATEGORY_TYPES) return
        context.dataStore.edit { prefs ->
            val current = parseSearchAllCategoryTypes(prefs[KEY_SEARCH_ALL_CATEGORY_TYPES])
            val next = if (enabled) current + normalized else current - normalized
            prefs[KEY_SEARCH_ALL_CATEGORY_TYPES] = next.sorted().joinToString(",")
        }
    }

    suspend fun setSearchAllSongMatchTypeEnabled(type: String, enabled: Boolean) {
        val normalized = type.trim().lowercase()
        if (normalized !in SEARCH_ALL_SONG_MATCH_TYPES) return
        context.dataStore.edit { prefs ->
            val current = parseSearchAllSongMatchTypes(prefs[KEY_SEARCH_ALL_SONG_MATCH_TYPES])
            val next = if (enabled) current + normalized else current - normalized
            prefs[KEY_SEARCH_ALL_SONG_MATCH_TYPES] = next.sorted().joinToString(",")
        }
    }

    suspend fun setSongRatingDisplayMode(mode: Int) {
        context.dataStore.edit {
            it[KEY_SONG_RATING_DISPLAY_MODE] = mode.coerceIn(
                SONG_RATING_DISPLAY_STAR_NUMBER,
                SONG_RATING_DISPLAY_STARS
            )
        }
    }

    suspend fun setListeningHistorySource(source: Int) {
        context.dataStore.edit {
            it[KEY_LISTENING_HISTORY_SOURCE] = source.coerceIn(
                LISTENING_HISTORY_SOURCE_LOCAL,
                LISTENING_HISTORY_SOURCE_COMBINED
            )
        }
    }

    suspend fun setInitialScanPromptHandled(handled: Boolean) {
        context.dataStore.edit { it[KEY_INITIAL_SCAN_PROMPT_HANDLED] = handled }
    }

    suspend fun setLocalPlaylistScanPromptHandled(handled: Boolean) {
        context.dataStore.edit { it[KEY_LOCAL_PLAYLIST_SCAN_PROMPT_HANDLED] = handled }
    }

    suspend fun setNotificationPermissionPromptHandled(handled: Boolean) {
        context.dataStore.edit { it[KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED] = handled }
    }

    suspend fun setArtistSeparators(separators: String) {
        context.dataStore.edit { it[KEY_ARTIST_SEPARATORS] = separators.trim() }
    }

    suspend fun setArtistProtectedNames(names: String) {
        context.dataStore.edit { it[KEY_ARTIST_PROTECTED_NAMES] = names.trim() }
    }

    suspend fun setGenreSeparators(separators: String) {
        context.dataStore.edit { it[KEY_GENRE_SEPARATORS] = separators.trim() }
    }

    suspend fun setGenreProtectedNames(names: String) {
        context.dataStore.edit { it[KEY_GENRE_PROTECTED_NAMES] = names.trim() }
    }

    suspend fun setTagIgnoreCase(enabled: Boolean) {
        context.dataStore.edit { it[KEY_TAG_IGNORE_CASE] = enabled }
    }

    suspend fun exportSettingsJson(): JSONObject {
        val prefs = context.dataStore.data.first()
        val payload = JSONObject()
        prefs.asMap().forEach { (key, value) ->
            when (value) {
                is Boolean -> payload.put(key.name, value)
                is Int -> payload.put(key.name, value)
                is String -> payload.put(key.name, value)
            }
        }
        return payload
    }

    suspend fun restoreSettingsJson(payload: JSONObject) {
        context.dataStore.edit { prefs ->
            fun setBoolean(key: Preferences.Key<Boolean>) {
                if (payload.has(key.name) && !payload.isNull(key.name)) prefs[key] = payload.optBoolean(key.name)
            }
            fun setInt(key: Preferences.Key<Int>) {
                if (payload.has(key.name) && !payload.isNull(key.name)) prefs[key] = payload.optInt(key.name)
            }
            fun setString(key: Preferences.Key<String>) {
                if (payload.has(key.name) && !payload.isNull(key.name)) prefs[key] = payload.optString(key.name)
            }

            setBoolean(KEY_LYRICON_ENABLED)
            setBoolean(KEY_LYRICON_TRANSLATION)
            setBoolean(KEY_LYRICON_PRONUNCIATION)
            setBoolean(KEY_AUTO_SCAN)
            setBoolean(KEY_AUTO_SCAN_LOCAL_PLAYLISTS)
            setBoolean(KEY_GAPLESS)
            setInt(KEY_CROSSFADE_DURATION_MS)
            setBoolean(KEY_TICKER_ENABLED)
            setBoolean(KEY_TICKER_HIDE_NOTIFICATION)
            setBoolean(KEY_TICKER_HEADS_UP_LYRICS)
            setBoolean(KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION)
            setBoolean(KEY_STATUS_BAR_ALLOW_PHONETIC)
            setBoolean(KEY_DESKTOP_LYRIC_ENABLED)
            setBoolean(KEY_DESKTOP_LYRIC_HIDE_WHEN_PAUSED)
            setBoolean(KEY_DESKTOP_LYRIC_HIDE_IN_LANDSCAPE)
            setBoolean(KEY_DESKTOP_LYRIC_STATUS_BAR_MODE)
            setBoolean(KEY_DESKTOP_LYRIC_STATUS_BAR_HIDE_WHEN_PAUSED)
            setBoolean(KEY_DESKTOP_LYRIC_STATUS_BAR_HIDE_IN_LANDSCAPE)
            setBoolean(KEY_DESKTOP_LYRIC_STATUS_BAR_MERGE_SECONDARY)
            setBoolean(KEY_DESKTOP_LYRIC_LOCKED)
            setBoolean(KEY_SUPER_LYRIC_ENABLED)
            setBoolean(KEY_SUPER_LYRIC_TRANSLATION)
            setBoolean(KEY_SUPER_LYRIC_PRONUNCIATION)
            setBoolean(KEY_LYRIC_GETTER_ENABLED)
            setBoolean(KEY_IGNORE_LYRIC_HEADER_TAGS)
            setBoolean(KEY_REPLAYGAIN_ENABLED)
            setInt(KEY_REPLAYGAIN_MODE)
            setBoolean(KEY_RESUME_PLAYBACK_POSITION)
            setBoolean(KEY_AUDIO_FOCUS_DISABLED)
            setBoolean(KEY_LYRIC_PAGE_TRANSLATION)
            setBoolean(KEY_LYRIC_PAGE_KEEP_SCREEN_ON)
            setBoolean(KEY_APPLE_MUSIC_LYRICS_WORD_LIFT)
            setBoolean(KEY_LYRIC_PRONUNCIATION_BELOW)
            setBoolean(KEY_LYRIC_FONT_ITALIC)
            setBoolean(KEY_LYRIC_FONT_APPLY_TO_PAGE)
            setBoolean(KEY_LYRIC_FONT_APPLY_TO_DESKTOP)
            setBoolean(KEY_LYRIC_PERSPECTIVE_EFFECT)
            setBoolean(KEY_FULL_TAG_SEARCH_ENABLED)
            setBoolean(KEY_FULL_TAG_SEARCH_PROMPT_HANDLED)
            setBoolean(KEY_MINI_PLAYER_LYRIC_TRANSLATION)
            setBoolean(KEY_MINI_PLAYER_COVER_ROTATION)
            setBoolean(KEY_MINI_PLAYER_LYRICS_ENABLED)
            setInt(KEY_MINI_PLAYER_RIGHT_BUTTON)
            setBoolean(KEY_TRANSPORT_BUTTON_OUTLINES)
            setBoolean(KEY_PLAYER_TAP_SEEK_ENABLED)
            setBoolean(KEY_PLAYER_SHOW_TOTAL_DURATION)
            setBoolean(KEY_PLAYER_SHOW_SONG_ANNOTATION)
            setBoolean(KEY_PLAYER_COVER_SWIPE_ENABLED)
            setBoolean(KEY_PLAYER_KEEP_SCREEN_ON)
            setBoolean(KEY_PLAYER_HDR_GLOW)
            setBoolean(KEY_PLAYER_IMMERSIVE_COVER)
            setBoolean(KEY_PLAYER_COVER_CONTENT_COLOR)
            setBoolean(KEY_HIDE_SYSTEM_BARS)
            setBoolean(KEY_PLAYER_DYNAMIC_FLOW_ENABLED)
            setBoolean(KEY_AUDIO_VISUALIZER_ENABLED)
            setBoolean(KEY_DYNAMIC_COVER_ENABLED)
            setBoolean(KEY_MUSIC_VIDEO_SYNC_ENABLED)
            setBoolean(KEY_MUSIC_VIDEO_CAPTURE_SUBTITLES)
            setBoolean(KEY_ARTIST_COVER_CAROUSEL)
            setBoolean(KEY_STARTUP_POSTER_ENABLED)
            setBoolean(KEY_APP_WALLPAPER_ENABLED)
            setBoolean(KEY_PLAYER_BACKGROUND_ENABLED)
            setBoolean(KEY_PLAYER_BEAUTIFUL_LYRICS_BACKGROUND)
            setBoolean(KEY_HI_RES_LOGO_ENABLED)
            setBoolean(KEY_PLAYLIST_SPECIAL_ENTRIES_VISIBLE)
            setBoolean(KEY_SHOW_PLAY_NEXT_IN_LISTS)
            setBoolean(KEY_EXCLUDE_SEARCH_RESULTS_FROM_PLAYLIST)
            setBoolean(KEY_AUTO_SHOW_SEARCH_KEYBOARD)
            setBoolean(KEY_ADD_TO_PLAYLIST_APPEND_TO_END)
            setBoolean(KEY_SHOW_ALBUM_ARTISTS)
            setBoolean(KEY_HOME_TILE_PIN_BUTTONS_VISIBLE)
            setBoolean(KEY_HOME_TILE_GRADIENT_ENABLED)
            setBoolean(KEY_USE_ANDROID_MEDIA_LIBRARY)
            setBoolean(KEY_INITIAL_SCAN_PROMPT_HANDLED)
            setBoolean(KEY_LOCAL_PLAYLIST_SCAN_PROMPT_HANDLED)
            setBoolean(KEY_NOTIFICATION_PERMISSION_PROMPT_HANDLED)
            setBoolean(KEY_TAG_IGNORE_CASE)
            setBoolean(KEY_BLUETOOTH_LYRIC_ENABLED)
            setBoolean(KEY_BLUETOOTH_LYRIC_TRANSLATION)
            setBoolean(KEY_BLUETOOTH_LYRIC_PRONUNCIATION)
            setBoolean(KEY_COLOROS_LOCK_SCREEN_LYRIC_ENABLED)
            setBoolean(KEY_BLUETOOTH_AUTO_PLAY)
            setBoolean(KEY_OPEN_PLAYER_ON_PLAY)
            setBoolean(KEY_STARTUP_AUTO_PLAY)
            setBoolean(KEY_HOME_DAILY_MIX_VISIBLE)
            setBoolean(KEY_HOME_AI_MIX_VISIBLE)
            setBoolean(KEY_MCP_SERVER_ENABLED)
            setBoolean(KEY_SLEEP_TIMER_STOP_AFTER_CURRENT)
            setBoolean(KEY_EQ_ENABLED)
            setBoolean(KEY_COMP_ENABLED)
            setBoolean(KEY_SURROUND_360_ENABLED)
            setBoolean(KEY_PANORAMIC_360_ENABLED)
            setBoolean(KEY_LOUDNESS_BALANCE_ENABLED)
            setBoolean(KEY_CROSSFEED_ENABLED)
            setBoolean(KEY_MONO_BASS_ENABLED)
            setBoolean(KEY_SPEAKER_OUTPUT_ENABLED)
            setBoolean(KEY_DYNAMIC_EQ_ENABLED)
            setBoolean(KEY_MOOG_LADDER_ENABLED)
            setBoolean(KEY_PEAK_LIMITER_ENABLED)
            setBoolean(KEY_PLATFORM_SPATIAL_AUDIO_ENABLED)
            setBoolean(KEY_BASS_BOOST_ENABLED)
            setBoolean(KEY_VIRTUALIZER_ENABLED)
            setBoolean(KEY_LYRIC_SHARE_USE_LYRIC_FONT)
            setBoolean(KEY_WEBDAV_AUTO_BACKUP_ENABLED)
            setBoolean(KEY_USB_DAC_MODE)

            setInt(KEY_THEME_MODE)
            setInt(KEY_MONET_COLOR_MODE)
            setInt(KEY_PLAYER_BACKGROUND_THEME)
            setInt(KEY_EQ_PRESET)
            setInt(KEY_EQ_Q)
            setInt(KEY_TONE_BASS_DB)
            setInt(KEY_TONE_TREBLE_DB)
            setInt(KEY_COMP_THRESHOLD_DB)
            setInt(KEY_COMP_RATIO)
            setInt(KEY_COMP_MAKEUP_DB)
            setInt(KEY_STEREO_WIDTH)
            setInt(KEY_SURROUND_360_INTENSITY)
            setInt(KEY_SURROUND_360_ROTATION_SPEED)
            setInt(KEY_PANORAMIC_360_INTENSITY)
            setInt(KEY_PANORAMIC_360_AZIMUTH_DEGREES)
            setInt(KEY_PANORAMIC_360_ELEVATION_DEGREES)
            setInt(KEY_LOUDNESS_PERCENT)
            setInt(KEY_CHANNEL_BALANCE)
            setInt(KEY_CROSSFEED_LOW_CUT_HZ)
            setInt(KEY_CROSSFEED_HIGH_CUT_HZ)
            setInt(KEY_CROSSFEED_ATTENUATION_TENTHS_DB)
            setInt(KEY_MONO_BASS_CROSSOVER_HZ)
            setInt(KEY_MONO_BASS_AMOUNT)
            setInt(KEY_SPEAKER_OUTPUT_MODE)
            setInt(KEY_SPEAKER_OUTPUT_STRENGTH)
            setInt(KEY_DYNAMIC_EQ_INTENSITY)
            setInt(KEY_DE_ESSER_AMOUNT)
            setInt(KEY_DE_ESSER_FREQUENCY_HZ)
            setInt(KEY_MOOG_LADDER_MODE)
            setInt(KEY_MOOG_LADDER_CUTOFF_HZ)
            setInt(KEY_MOOG_LADDER_RESONANCE)
            setInt(KEY_MOOG_LADDER_DRIVE_DB)
            setInt(KEY_MOOG_LADDER_MIX)
            setInt(KEY_BASS_BOOST_STRENGTH)
            setInt(KEY_VIRTUALIZER_STRENGTH)
            setInt(KEY_REVERB_PRESET)
            setInt(KEY_MIN_DURATION)
            setInt(KEY_SHUFFLE_MODE)
            setInt(KEY_PREVIOUS_BUTTON_ACTION)
            setInt(KEY_PLAY_NEXT_MODE)
            setInt(KEY_STARTUP_PLAY_MODE)
            setInt(KEY_COLOROS_LOCK_SCREEN_LYRIC_MODE)
            setInt(KEY_LYRIC_SOURCE_MODE)
            setInt(KEY_LYRIC_PARSER_ENGINE)
            setInt(KEY_PLAYER_TITLE_POSITION)
            setInt(KEY_PLAYER_LYRIC_TEXT_ALIGN)
            setInt(KEY_DESKTOP_LYRIC_FONT_SCALE)
            setInt(KEY_DESKTOP_LYRIC_WIDTH)
            setInt(KEY_DESKTOP_LYRIC_TRANSLATION_SCALE)
            setInt(KEY_DESKTOP_LYRIC_OPACITY)
            setInt(KEY_DESKTOP_LYRIC_TEXT_COLOR)
            setInt(KEY_DESKTOP_LYRIC_X)
            setInt(KEY_DESKTOP_LYRIC_Y)
            setInt(KEY_DECODER_MODE)
            setInt(KEY_AUDIO_OUTPUT_BACKEND)
            setInt(KEY_AUDIO_OUTPUT_BIT_DEPTH)
            setInt(KEY_AUDIO_OUTPUT_SAMPLE_RATE)
            setInt(KEY_LYRIC_FONT_WEIGHT)
            setInt(KEY_LYRIC_FONT_SCALE)
            setInt(KEY_LYRIC_SECONDARY_FONT_SCALE)
            setInt(KEY_LYRIC_COMPACT_PRIMARY_TEXT_SIZE)
            setInt(KEY_LYRIC_COMPACT_SECONDARY_TEXT_SIZE)
            setInt(KEY_LYRIC_WIDE_PRIMARY_TEXT_SIZE)
            setInt(KEY_LYRIC_WIDE_SECONDARY_TEXT_SIZE)
            setInt(KEY_LYRIC_PERSPECTIVE_Y_ANGLE)
            setInt(KEY_SORT_LIBRARY_SONG)
            setInt(KEY_SORT_ALBUM_LIST)
            setInt(KEY_SORT_ARTIST_LIST)
            setInt(KEY_SORT_ALBUM_DETAIL_SONG)
            setInt(KEY_SORT_ARTIST_DETAIL_SONG)
            setInt(KEY_SORT_ARTIST_DETAIL_ALBUM)
            setInt(KEY_SORT_FOLDER_LIST)
            setInt(KEY_SORT_FOLDER_DETAIL_SONG)
            setInt(KEY_SORT_FOLDER_PLAYLIST_LIST)
            setInt(KEY_SORT_FOLDER_PLAYLIST_DETAIL_SONG)
            setInt(KEY_SORT_FOLDER_PLAYLIST_DETAIL_FOLDER)
            setInt(KEY_SORT_PLAYLIST_LIST)
            setInt(KEY_SORT_PLAYLIST_DETAIL_SONG)
            setInt(KEY_CATEGORY_GRID_COLUMNS)
            setInt(KEY_MINI_PLAYER_LYRIC_SECONDARY)
            setInt(KEY_PLAYER_PROGRESS_INFO_INDEX)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_TOP_OFFSET)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_POSITION)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_WIDTH)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_X_OFFSET)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_ALIGN)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_VERTICAL_ALIGN)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_SECONDARY_OPACITY)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_FONT_SCALE)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_TRANSLATION_SCALE)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_OPACITY)
            setInt(KEY_DESKTOP_LYRIC_STATUS_BAR_TEXT_COLOR)
            setInt(KEY_SLEEP_TIMER_CUSTOM_MINUTES)
            setInt(KEY_STARTUP_POSTER_DURATION_MS)
            setInt(KEY_APP_WALLPAPER_OPACITY)
            setInt(KEY_APP_WALLPAPER_DIM)
            setInt(KEY_APP_WALLPAPER_CONTENT_OVERLAY)
            setInt(KEY_PLAYER_BACKGROUND_OPACITY)
            setInt(KEY_PLAYER_BACKGROUND_DIM)
            setInt(KEY_AUDIO_VISUALIZER_OPACITY)
            setInt(KEY_HOME_CARD_OPACITY)
            setString(KEY_HOME_TILE_COLORS)
            setString(KEY_HOME_TILE_GRADIENT_START_COLOR)
            setString(KEY_HOME_ONLINE_TILE_ORDER)
            setString(KEY_HOME_HIDDEN_ONLINE_TILES)
            setString(KEY_FOLDER_PLAYLISTS)
            setString(KEY_LIBRARY_SOURCE)
            setInt(KEY_PLAYER_BEAUTIFUL_LYRICS_SPEED)
            setInt(KEY_PLAYER_BEAUTIFUL_LYRICS_BLUR)
            setInt(KEY_PLAYER_BEAUTIFUL_LYRICS_BRIGHTNESS)
            setInt(KEY_WEBDAV_AUTO_BACKUP_INTERVAL_HOURS)
            setInt(KEY_LISTENING_HISTORY_SOURCE)
            setInt(KEY_SONG_RATING_DISPLAY_MODE)

            val dynamicSortKeyPrefixes = listOf(
                "sort_metadata_category_",
                "sort_metadata_category_detail_song_",
                "sort_metadata_category_detail_album_"
            )
            val payloadKeys = payload.keys()
            while (payloadKeys.hasNext()) {
                val keyName = payloadKeys.next()
                if (dynamicSortKeyPrefixes.any { keyName.startsWith(it) } && !payload.isNull(keyName)) {
                    prefs[intPreferencesKey(keyName)] = payload.optInt(keyName)
                }
            }

            setString(KEY_WEBDAV_URL)
            setString(KEY_WEBDAV_USERNAME)
            setString(KEY_WEBDAV_PASSWORD)
            setString(KEY_WEBDAV_LAST_URL)
            setString(KEY_WEBDAV_BACKUP_URL)
            setString(KEY_WEBDAV_BACKUP_PATH)
            setString(KEY_WEBDAV_BACKUP_USERNAME)
            setString(KEY_WEBDAV_BACKUP_PASSWORD)
            setString(KEY_WEBDAV_AUTO_BACKUP_LAST_AT)
            setString(KEY_LX_SOURCE_URL)
            setString(KEY_LX_SOURCE_NAME)
            setString(KEY_LX_SOURCE_SCRIPT)
            setString(KEY_LX_SOURCES_JSON)
            setString(KEY_LX_SELECTED_SOURCE_ID)
            setString(KEY_ONLINE_SELECTED_PROVIDER)
            setString(KEY_NAVIDROME_URL)
            setString(KEY_NAVIDROME_USERNAME)
            setString(KEY_NAVIDROME_PASSWORD)
            setString(KEY_NAVIDROME_SERVERS)
            setString(KEY_NAVIDROME_ACTIVE_ID)
            setString(KEY_OPENSUBSONIC_SERVERS)
            setString(KEY_OPENSUBSONIC_ACTIVE_ID)
            setString(KEY_EMBY_URL)
            setString(KEY_EMBY_USERNAME)
            setString(KEY_EMBY_TOKEN)
            setString(KEY_EMBY_USER_ID)
            setString(KEY_EMBY_SERVER_NAME)
            setString(KEY_EMBY_SERVERS)
            setString(KEY_EMBY_ACTIVE_ID)
            setString(KEY_OPENAI_API_KEY)
            setString(KEY_OPENAI_BASE_URL)
            setString(KEY_OPENAI_MODEL)
            setString(KEY_LYRIC_SOURCE_PRIORITY)
            setString(KEY_LYRIC_LINE_BLACKLIST)
            setString(KEY_LYRIC_FONT_NAME)
            setString(KEY_LYRIC_FONT_PATH)
            setString(KEY_LYRIC_WESTERN_FONT_NAME)
            setString(KEY_LYRIC_WESTERN_FONT_PATH)
            setString(KEY_LYRIC_CJK_FONT_NAME)
            setString(KEY_LYRIC_CJK_FONT_PATH)
            setString(KEY_GLOBAL_WESTERN_FONT_NAME)
            setString(KEY_GLOBAL_WESTERN_FONT_PATH)
            setString(KEY_GLOBAL_CJK_FONT_NAME)
            setString(KEY_GLOBAL_CJK_FONT_PATH)
            setString(KEY_LYRIC_ORIGINAL_WESTERN_FONT_NAME)
            setString(KEY_LYRIC_ORIGINAL_WESTERN_FONT_PATH)
            setString(KEY_LYRIC_ORIGINAL_CJK_FONT_NAME)
            setString(KEY_LYRIC_ORIGINAL_CJK_FONT_PATH)
            setString(KEY_LYRIC_TRANSLATION_WESTERN_FONT_NAME)
            setString(KEY_LYRIC_TRANSLATION_WESTERN_FONT_PATH)
            setString(KEY_LYRIC_TRANSLATION_CJK_FONT_NAME)
            setString(KEY_LYRIC_TRANSLATION_CJK_FONT_PATH)
            setString(KEY_LYRIC_SHARE_CUSTOM_INFO)
            setString(KEY_STARTUP_POSTER_URI)
            setString(KEY_APP_WALLPAPER_URI)
            setString(KEY_PLAYER_BACKGROUND_URI)
            setString(KEY_HOME_CARD_COLOR)
            setString(KEY_HI_RES_LOGO_URI)
            setString(KEY_METADATA_EDITOR_ID)
            setString(KEY_LYRIC_TIMING_EDITOR_ID)
            setString(KEY_SHORTCUT_LIBRARY_LABEL)
            setString(KEY_SHORTCUT_PLAYLISTS_LABEL)
            setString(KEY_SHORTCUT_FOLDER_LABEL)
            setString(KEY_APP_SHORTCUT_ORDER)
            setString(KEY_LYRICO_PLUGIN_ENABLED_IDS)
            setString(KEY_SCAN_INCLUDE_FOLDERS)
            setString(KEY_SCAN_EXCLUDE_FOLDERS)
            setString(KEY_USB_FOLDER_URIS)
            setString(KEY_ARTIST_SEPARATORS)
            setString(KEY_ARTIST_PROTECTED_NAMES)
            setString(KEY_GENRE_SEPARATORS)
            setString(KEY_GENRE_PROTECTED_NAMES)
            setString(KEY_HOME_SECTION_ORDER)
            setString(KEY_HOME_HIDDEN_SECTIONS)
            setString(KEY_HOME_LIBRARY_TILE_ORDER)
            setString(KEY_HOME_HIDDEN_LIBRARY_TILES)
            setString(KEY_APP_LANGUAGE)
            setString(KEY_APP_ICON_STYLE)
            setString(KEY_BOTTOM_BAR_GLASS_EFFECT)
            setString(KEY_BOTTOM_DOCK_ITEMS)
            setString(KEY_LYRIC_OFFSET_OVERRIDES)
            setString(KEY_PLAYLIST_CUSTOM_ORDER)
            setString(KEY_EQ_BANDS)
            setString(KEY_DYNAMIC_COVER_CUSTOM_FOLDERS)
            setString(KEY_ARTIST_COVER_FOLDER_URI)
            setString(KEY_COVER_EXPORT_FOLDER_URI)
            setString(KEY_SEARCH_ALL_CATEGORY_TYPES)
            setString(KEY_SEARCH_ALL_SONG_MATCH_TYPES)

            fun clearMissingCustomImage(
                enabledKey: Preferences.Key<Boolean>,
                uriKey: Preferences.Key<String>
            ) {
                val uriString = prefs[uriKey].orEmpty()
                if (uriString.isNotBlank() && !isRestoredCustomImageAvailable(uriString)) {
                    prefs[enabledKey] = false
                    prefs.remove(uriKey)
                }
            }

            clearMissingCustomImage(KEY_STARTUP_POSTER_ENABLED, KEY_STARTUP_POSTER_URI)
            clearMissingCustomImage(KEY_APP_WALLPAPER_ENABLED, KEY_APP_WALLPAPER_URI)
            clearMissingCustomImage(KEY_PLAYER_BACKGROUND_ENABLED, KEY_PLAYER_BACKGROUND_URI)
            clearMissingCustomImage(KEY_HI_RES_LOGO_ENABLED, KEY_HI_RES_LOGO_URI)
        }
    }

    private fun isRestoredCustomImageAvailable(uriString: String): Boolean {
        val uri = runCatching { Uri.parse(uriString) }.getOrNull() ?: return false
        if (uri.scheme != "file") return false
        val path = uri.path ?: return false
        val file = File(path)
        val customImageDir = File(context.filesDir, "custom_images")
        return runCatching {
            val target = file.canonicalFile
            val dir = customImageDir.canonicalFile
            target.path.startsWith(dir.path) && target.isFile && target.canRead() && target.length() > 0L
        }.getOrDefault(false)
    }

    suspend fun setScanExcludeFolders(folders: String) {
        context.dataStore.edit { it[KEY_SCAN_EXCLUDE_FOLDERS] = folders.trim() }
    }

    suspend fun setUsbFolderUris(uris: String) {
        context.dataStore.edit { it[KEY_USB_FOLDER_URIS] = uris.trim() }
    }

    suspend fun addUsbFolderUri(uri: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_USB_FOLDER_URIS].orEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() }
            val updated = (existing + uri.trim()).distinct().joinToString("\n")
            prefs[KEY_USB_FOLDER_URIS] = updated
        }
    }

    suspend fun removeUsbFolderUri(uri: String) {
        context.dataStore.edit { prefs ->
            val existing = prefs[KEY_USB_FOLDER_URIS].orEmpty()
                .split('\n')
                .map { it.trim() }
                .filter { it.isNotBlank() && it != uri.trim() }
            if (existing.isEmpty()) {
                prefs.remove(KEY_USB_FOLDER_URIS)
            } else {
                prefs[KEY_USB_FOLDER_URIS] = existing.joinToString("\n")
            }
        }
    }

    private fun parseDynamicCoverCustomFolders(raw: String): List<String> =
        normalizeDynamicCoverCustomFolders(raw)
            .split('\n')
            .map(String::trim)
            .filter(String::isNotBlank)

    private fun normalizeDynamicCoverCustomFolders(raw: String?): String =
        raw.orEmpty()
            .split(Regex("""[;\r\n]+"""))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("\n")

    private fun parseSearchAllCategoryTypes(raw: String?): Set<String> {
        val saved = raw.orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it in SEARCH_ALL_CATEGORY_TYPES }
            .toSet()
        return if (raw == null) SEARCH_ALL_CATEGORY_TYPES else saved
    }

    private fun parseSearchAllSongMatchTypes(raw: String?): Set<String> {
        val saved = raw.orEmpty()
            .split(',')
            .map { it.trim().lowercase() }
            .filter { it in SEARCH_ALL_SONG_MATCH_TYPES }
            .toSet()
        return if (raw == null) SEARCH_ALL_SONG_MATCH_TYPES else saved
    }

    suspend fun setDecoderMode(mode: Int) {
        context.dataStore.edit { it[KEY_DECODER_MODE] = mode.coerceIn(0, 2) }
    }

    suspend fun setAudioOutputBackend(backend: Int) {
        context.dataStore.edit { it[KEY_AUDIO_OUTPUT_BACKEND] = normalizeAudioOutputBackend(backend) }
    }

    suspend fun setAudioOutputBitDepth(bitDepth: Int) {
        context.dataStore.edit { it[KEY_AUDIO_OUTPUT_BIT_DEPTH] = normalizeAudioOutputBitDepth(bitDepth) }
    }

    suspend fun setAudioOutputSampleRate(sampleRate: Int) {
        context.dataStore.edit { it[KEY_AUDIO_OUTPUT_SAMPLE_RATE] = normalizeAudioOutputSampleRate(sampleRate) }
    }

    private fun normalizeAudioOutputBackend(backend: Int?): Int =
        when (backend) {
            AUDIO_OUTPUT_BACKEND_OPENSLES,
            AUDIO_OUTPUT_BACKEND_AAUDIO,
            AUDIO_OUTPUT_BACKEND_HI_RES,
            AUDIO_OUTPUT_BACKEND_AUDIOTRACK -> backend
            else -> AUDIO_OUTPUT_BACKEND_AUTO
        }

    private fun normalizeAudioOutputBitDepth(bitDepth: Int?): Int =
        when (bitDepth) {
            AUDIO_OUTPUT_BIT_DEPTH_16,
            AUDIO_OUTPUT_BIT_DEPTH_24,
            AUDIO_OUTPUT_BIT_DEPTH_32,
            AUDIO_OUTPUT_BIT_DEPTH_FLOAT32 -> bitDepth
            else -> AUDIO_OUTPUT_BIT_DEPTH_AUTO
        }

    private fun normalizeAudioOutputSampleRate(sampleRate: Int?): Int =
        if (sampleRate != null && AUDIO_OUTPUT_SAMPLE_RATES.contains(sampleRate)) {
            sampleRate
        } else {
            AUDIO_OUTPUT_SAMPLE_RATE_AUTO
        }

    private fun Preferences.lxSources(): List<LxSourceConfig> {
        val defaultName = context.getString(R.string.settings_default_lx_source_name)
        val parsed = parseLxSourcesJson(this[KEY_LX_SOURCES_JSON].orEmpty(), defaultName)
        if (parsed.isNotEmpty()) return parsed

        val legacyUrl = this[KEY_LX_SOURCE_URL].orEmpty()
        val legacyScript = this[KEY_LX_SOURCE_SCRIPT].orEmpty()
        if (legacyUrl.isBlank() && legacyScript.isBlank()) return emptyList()

        return listOf(
            LxSourceConfig(
                id = legacyUrl.toLxSourceId(legacyScript),
                url = legacyUrl,
                name = this[KEY_LX_SOURCE_NAME].orEmpty().ifBlank { defaultName },
                script = legacyScript
            )
        )
    }

    private fun parseLyricOffsetOverrides(json: String?): Map<String, Long> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val root = JSONObject(json)
            root.keys().asSequence()
                .mapNotNull { key ->
                    val value = root.optLong(key, Long.MIN_VALUE)
                        .takeIf { it != Long.MIN_VALUE && it != 0L }
                        ?.coerceIn(-5000L, 5000L)
                    value?.let { key to it }
                }
                .toMap()
        }.getOrDefault(emptyMap())
    }

    private fun parseLyricLineBlacklist(raw: String?): List<String> =
        normalizeLyricLineBlacklist(raw.orEmpty().lineSequence())

    private fun normalizeLyricLineBlacklist(lines: Sequence<String>): List<String> =
        lines
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

}
