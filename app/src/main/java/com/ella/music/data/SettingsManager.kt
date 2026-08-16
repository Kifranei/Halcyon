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
import kotlinx.coroutines.flow.first
import com.ella.music.data.remote.RemoteMusicProvider
import androidx.annotation.StringRes
import com.ella.music.R
import org.json.JSONObject
import java.io.File
import java.util.Locale

internal val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "ella_settings")

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

class SettingsManager(private val context: Context) :
    SystemLyricSettingsAccess by SystemLyricSettingsAccessImpl(context),
    PlaybackSettingsAccess by PlaybackSettingsAccessImpl(context),
    AudioEffectSettingsAccess by AudioEffectSettingsAccessImpl(context),
    LyricSettingsAccess by LyricSettingsAccessImpl(context),
    PlayerUiSettingsAccess by PlayerUiSettingsAccessImpl(context),
    AppearanceSettingsAccess by AppearanceSettingsAccessImpl(context),
    LibrarySettingsAccess by LibrarySettingsAccessImpl(context),
    SortSettingsAccess by SortSettingsAccessImpl(context),
    RemoteSourceSettingsAccess by RemoteSourceSettingsAccessImpl(context) {

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
        val KEY_CROSSFADE_CURVE = intPreferencesKey("crossfade_curve")
        val KEY_THEME_MODE = intPreferencesKey("theme_mode")
        val KEY_MONET_COLOR_MODE = intPreferencesKey("monet_color_mode")
        val KEY_PLAYER_BACKGROUND_THEME = intPreferencesKey("player_background_theme")
        val KEY_APP_FONT_SCALE_PERCENT = intPreferencesKey("app_font_scale_percent")
        val KEY_APP_DISPLAY_SCALE_PERCENT = intPreferencesKey("app_display_scale_percent")
        val KEY_APP_LANGUAGE = stringPreferencesKey("app_language")
        val KEY_APP_ICON_STYLE = stringPreferencesKey("app_icon_style")
        val KEY_WIDGET_SAFE_LAYOUT = booleanPreferencesKey("widget_safe_layout")
        val KEY_LIBRARY_SOURCE = stringPreferencesKey("library_source")
        val KEY_BOTTOM_BAR_GLASS_EFFECT = stringPreferencesKey("bottom_bar_glass_effect")
        val KEY_BOTTOM_DOCK_ITEMS = stringPreferencesKey("bottom_dock_items")
        val KEY_TICKER_ENABLED = booleanPreferencesKey("ticker_enabled")
        val KEY_TICKER_HIDE_NOTIFICATION = booleanPreferencesKey("ticker_hide_notification")
        val KEY_TICKER_HEADS_UP_LYRICS = booleanPreferencesKey("ticker_heads_up_lyrics")
        val KEY_MEDIA_NOTIFICATION_BUTTONS = stringPreferencesKey("media_notification_buttons")
        val KEY_LIVE_UPDATE_LYRIC_ENABLED = booleanPreferencesKey("live_update_lyric_enabled")
        val KEY_LIVE_UPDATE_LYRIC_MODE = intPreferencesKey("live_update_lyric_mode")
        val KEY_LIVE_UPDATE_LYRIC_DISPLAY_MODE = intPreferencesKey("live_update_lyric_display_mode")
        val KEY_LIVE_UPDATE_LYRIC_SECONDARY_MODE = intPreferencesKey("live_update_lyric_secondary_mode")
        val KEY_XIAOMI_SUPER_ISLAND_LYRIC_ENABLED = booleanPreferencesKey("xiaomi_super_island_lyric_enabled")
        val KEY_XIAOMI_SUPER_ISLAND_SETTINGS = stringPreferencesKey("xiaomi_super_island_settings")
        val KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION = booleanPreferencesKey("samsung_floating_lyric_translation")
        val KEY_STATUS_BAR_ALLOW_PHONETIC = booleanPreferencesKey("status_bar_allow_phonetic")
        val KEY_DESKTOP_LYRIC_ENABLED = booleanPreferencesKey("desktop_lyric_enabled")
        val KEY_DESKTOP_LYRIC_HIDE_WHEN_PAUSED = booleanPreferencesKey("desktop_lyric_hide_when_paused")
        val KEY_DESKTOP_LYRIC_HIDE_IN_LANDSCAPE = booleanPreferencesKey("desktop_lyric_hide_in_landscape")
        val KEY_DESKTOP_LYRIC_HIDE_ON_PLAYER_PAGE = booleanPreferencesKey("desktop_lyric_hide_on_player_page")
        val KEY_DESKTOP_LYRIC_HIDE_ON_LYRICS_PAGE = booleanPreferencesKey("desktop_lyric_hide_on_lyrics_page")
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
        val KEY_HIDE_LYRIC_EXTRA_INFO = booleanPreferencesKey("hide_lyric_extra_info")
        val KEY_LYRIC_LINE_BLACKLIST = stringPreferencesKey("lyric_line_blacklist")
        val KEY_LYRIC_OFFSET_OVERRIDES = stringPreferencesKey("lyric_offset_overrides")
        val KEY_PLAYER_LYRIC_TEXT_ALIGN = intPreferencesKey("player_lyric_text_align")
        val KEY_LYRIC_PRONUNCIATION_BELOW = booleanPreferencesKey("lyric_pronunciation_below")
        val KEY_LYRIC_PAGE_TRANSLATION = booleanPreferencesKey("lyric_page_translation")
        val KEY_LYRIC_PAGE_KEEP_SCREEN_ON = booleanPreferencesKey("lyric_page_keep_screen_on")
        val KEY_APPLE_MUSIC_LYRICS_WORD_LIFT = booleanPreferencesKey("apple_music_lyrics_word_lift")
        val KEY_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS = intPreferencesKey("apple_music_lyrics_sustain_threshold_ms")
        const val DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS = 1_200
        const val MIN_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS = 300
        const val MAX_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS = 3_000
        const val STEP_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS = 100
        val KEY_MINI_PLAYER_LYRIC_TRANSLATION = booleanPreferencesKey("mini_player_lyric_translation")
        val KEY_MINI_PLAYER_LYRIC_SECONDARY = intPreferencesKey("mini_player_lyric_secondary")
        val KEY_MINI_PLAYER_COVER_ROTATION = booleanPreferencesKey("mini_player_cover_rotation")
        val KEY_MINI_PLAYER_LYRICS_ENABLED = booleanPreferencesKey("mini_player_lyrics_enabled")
        val KEY_MINI_PLAYER_RIGHT_BUTTON = intPreferencesKey("mini_player_right_button")
        val KEY_MINI_PLAYER_SWIPE_TO_OPEN_PLAYER = booleanPreferencesKey("mini_player_swipe_to_open_player")
        val KEY_PLAYER_PROGRESS_INFO_INDEX = intPreferencesKey("player_progress_info_index")
        val KEY_TRANSPORT_BUTTON_OUTLINES = booleanPreferencesKey("transport_button_outlines")
        val KEY_PLAYER_TAP_SEEK_ENABLED = booleanPreferencesKey("player_tap_seek_enabled")
        val KEY_PLAYER_SHOW_TOTAL_DURATION = booleanPreferencesKey("player_show_total_duration")
        val KEY_PLAYER_SHOW_SONG_ANNOTATION = booleanPreferencesKey("player_show_song_annotation")
        val KEY_PLAYER_COVER_SWIPE_ENABLED = booleanPreferencesKey("player_cover_swipe_enabled")
        val KEY_LYRIC_PARSER_ENGINE = intPreferencesKey("lyric_parser_engine")
        val KEY_PLAYER_TITLE_POSITION = intPreferencesKey("player_title_position")
        val KEY_PLAYER_PAGE_STYLE = intPreferencesKey("player_page_style")
        val KEY_PLAYER_LANDSCAPE_STYLE = intPreferencesKey("player_landscape_style")
        val KEY_PLAYER_KEEP_SCREEN_ON = booleanPreferencesKey("player_keep_screen_on")
        val KEY_PLAYER_HDR_GLOW = booleanPreferencesKey("player_hdr_glow")
        val KEY_PLAYER_IMMERSIVE_COVER = booleanPreferencesKey("player_immersive_cover")
        val KEY_PLAYER_COVER_CONTENT_COLOR = booleanPreferencesKey("player_cover_content_color")
        val KEY_SYSTEM_BARS_MODE = intPreferencesKey("system_bars_mode")
        val KEY_SYSTEM_BARS_RESERVE_SPACE =
            booleanPreferencesKey("system_bars_reserve_space")
        // Kept so older backups and installations can migrate the former all-or-nothing switch.
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
        val KEY_MUSIC_VIDEO_ORIENTATION = intPreferencesKey("music_video_orientation")
        val KEY_MUSIC_VIDEO_OFFSETS_JSON = stringPreferencesKey("music_video_offsets_json")
        val KEY_DYNAMIC_COVER_CUSTOM_FOLDERS = stringPreferencesKey("dynamic_cover_custom_folders")
        val KEY_MUSIC_VIDEO_CUSTOM_FOLDERS = stringPreferencesKey("music_video_custom_folders")
        val KEY_SHOW_LOCAL_MV_IN_LISTS = booleanPreferencesKey("show_local_mv_in_lists")
        val KEY_SHOW_ONLINE_MV_IN_LISTS = booleanPreferencesKey("show_online_mv_in_lists")
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
        val KEY_WEB_MUSIC_SERVER_ENABLED = booleanPreferencesKey("web_music_server_enabled")
        val KEY_PLAYLIST_SPECIAL_ENTRIES_VISIBLE = booleanPreferencesKey("playlist_special_entries_visible")
        val KEY_PLAYLIST_CUSTOM_ORDER = stringPreferencesKey("playlist_custom_order")
        val KEY_FOLDER_PLAYLIST_CUSTOM_ORDER = stringPreferencesKey("folder_playlist_custom_order")
        val KEY_SHOW_PLAY_NEXT_IN_LISTS = booleanPreferencesKey("show_play_next_in_lists")
        val KEY_SHOW_REMOVE_FROM_PLAYLIST_BUTTON = booleanPreferencesKey("show_remove_from_playlist_button")
        val KEY_EXCLUDE_SEARCH_RESULTS_FROM_PLAYLIST = booleanPreferencesKey("exclude_search_results_from_playlist")
        val KEY_AUTO_SHOW_SEARCH_KEYBOARD = booleanPreferencesKey("auto_show_search_keyboard")
        val KEY_PLAY_NEXT_MODE = intPreferencesKey("play_next_mode")
        val KEY_ADD_TO_PLAYLIST_APPEND_TO_END = booleanPreferencesKey("add_to_playlist_append_to_end")
        val KEY_LYRIC_SHARE_CUSTOM_INFO = stringPreferencesKey("lyric_share_custom_info")
        val KEY_LYRIC_SHARE_USE_LYRIC_FONT = booleanPreferencesKey("lyric_share_use_lyric_font")
        val KEY_SHOW_ALBUM_ARTISTS = booleanPreferencesKey("show_album_artists")
        val KEY_METADATA_EDITOR_ID = stringPreferencesKey("metadata_editor_id")
        val KEY_LYRIC_TIMING_EDITOR_ID = stringPreferencesKey("lyric_timing_editor_id")
        val KEY_SPECTRUM_VIEWER_ID = stringPreferencesKey("spectrum_viewer_id")
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
        val KEY_CONTINUE_PLAYBACK_ROW_VISIBLE = booleanPreferencesKey("continue_playback_row_visible")
        val KEY_HOME_RECENT_SECTION_MODE = intPreferencesKey("home_recent_section_mode")
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
        const val PLAYER_PAGE_STYLE_HALCYON = 0
        const val PLAYER_PAGE_STYLE_APPLE_MUSIC = 1
        const val PLAYER_PAGE_STYLE_IMMERSIVE_LYRICS = 2
        const val DEFAULT_PLAYER_PAGE_STYLE = PLAYER_PAGE_STYLE_HALCYON
        const val PLAYER_LANDSCAPE_STYLE_WIDE = 0
        const val PLAYER_LANDSCAPE_STYLE_COVER_FLOW = 2
        const val PLAYER_LANDSCAPE_STYLE_MUSIC_VIDEO = 3
        const val DEFAULT_PLAYER_LANDSCAPE_STYLE = PLAYER_LANDSCAPE_STYLE_WIDE

        fun normalizePlayerLandscapeStyle(style: Int?): Int = when (style) {
            PLAYER_LANDSCAPE_STYLE_WIDE,
            PLAYER_LANDSCAPE_STYLE_COVER_FLOW,
            PLAYER_LANDSCAPE_STYLE_MUSIC_VIDEO -> style
            else -> DEFAULT_PLAYER_LANDSCAPE_STYLE
        }

        fun normalizePlayerPageStyle(style: Int?): Int = when (style) {
            PLAYER_PAGE_STYLE_HALCYON,
            PLAYER_PAGE_STYLE_APPLE_MUSIC,
            PLAYER_PAGE_STYLE_IMMERSIVE_LYRICS -> style
            else -> DEFAULT_PLAYER_PAGE_STYLE
        }

        const val SYSTEM_BARS_MODE_SHOW_BOTH = 0
        const val SYSTEM_BARS_MODE_HIDE_STATUS = 1
        const val SYSTEM_BARS_MODE_HIDE_NAVIGATION = 2
        const val SYSTEM_BARS_MODE_HIDE_BOTH = 3
        const val DEFAULT_SYSTEM_BARS_RESERVE_SPACE = true

        const val DEFAULT_APP_FONT_SCALE_PERCENT = 100
        const val APP_FONT_SCALE_MIN_PERCENT = 75
        const val APP_FONT_SCALE_MAX_PERCENT = 175
        const val DEFAULT_APP_DISPLAY_SCALE_PERCENT = 100
        const val APP_DISPLAY_SCALE_MIN_PERCENT = 80
        const val APP_DISPLAY_SCALE_MAX_PERCENT = 160

        fun resolveSystemBarsMode(storedMode: Int?, legacyHideSystemBars: Boolean): Int =
            (storedMode ?: if (legacyHideSystemBars) {
                SYSTEM_BARS_MODE_HIDE_BOTH
            } else {
                SYSTEM_BARS_MODE_SHOW_BOTH
            }).coerceIn(SYSTEM_BARS_MODE_SHOW_BOTH, SYSTEM_BARS_MODE_HIDE_BOTH)

        const val PREVIOUS_BUTTON_PREVIOUS = 0
        const val PREVIOUS_BUTTON_REPLAY_CURRENT = 1

        const val CROSSFADE_CURVE_EQUAL_POWER = 0
        const val CROSSFADE_CURVE_LINEAR = 1
        const val CROSSFADE_CURVE_SMOOTH = 2
        const val CROSSFADE_CURVE_FLAT = 3
        const val PREVIOUS_REPLAY_THRESHOLD_MS = 20_000L

        const val PLAY_NEXT_MODE_REVERSE_STACK = 0
        const val PLAY_NEXT_MODE_FORWARD_STACK = 1

        const val LISTENING_HISTORY_SOURCE_LOCAL = 0
        const val LISTENING_HISTORY_SOURCE_LAST_FM = 1
        const val LISTENING_HISTORY_SOURCE_COMBINED = 2

        const val OPLUS_LYRIC_MODE_SYSTEM = 0
        const val OPLUS_LYRIC_MODE_MODULE = 1

        const val LIVE_UPDATE_LYRIC_MODE_ORIGINAL = 0
        const val LIVE_UPDATE_LYRIC_MODE_TRANSLATION = 1
        const val LIVE_UPDATE_LYRIC_MODE_PRONUNCIATION = 2

        const val LIVE_UPDATE_LYRIC_DISPLAY_MODE_COMPACT = 0
        const val LIVE_UPDATE_LYRIC_DISPLAY_MODE_FULL = 1

        const val LIVE_UPDATE_LYRIC_SECONDARY_MODE_SONG = 0
        const val LIVE_UPDATE_LYRIC_SECONDARY_MODE_TRANSLATION = 1
        const val LIVE_UPDATE_LYRIC_SECONDARY_MODE_PRONUNCIATION = 2

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
        const val DEFAULT_PLAYER_DYNAMIC_FLOW_ENABLED = true
        const val DEFAULT_TRANSPORT_BUTTON_OUTLINES = true
        const val DEFAULT_PLAYER_SHOW_TOTAL_DURATION = true
        const val DEFAULT_MUSIC_VIDEO_SYNC_ENABLED = true
        const val MUSIC_VIDEO_ORIENTATION_SYSTEM = 0
        const val MUSIC_VIDEO_ORIENTATION_VIDEO = 1
        const val MUSIC_VIDEO_ORIENTATION_LANDSCAPE = 2
        const val MUSIC_VIDEO_ORIENTATION_PORTRAIT = 3
        const val DEFAULT_MUSIC_VIDEO_ORIENTATION = MUSIC_VIDEO_ORIENTATION_VIDEO

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
        // Keep startup responsive while still allowing the poster to be noticed.
        const val DEFAULT_STARTUP_POSTER_DURATION_MS = 1_000
        const val SONG_RATING_DISPLAY_STAR_NUMBER = 0
        const val SONG_RATING_DISPLAY_STARS = 1
        const val MEDIA_NOTIFICATION_BUTTON_PLAYBACK_MODE = "playback_mode"
        const val MEDIA_NOTIFICATION_BUTTON_DESKTOP_LYRIC = "desktop_lyric"
        const val MEDIA_NOTIFICATION_BUTTON_FAVORITE = "favorite"
        val DEFAULT_MEDIA_NOTIFICATION_BUTTON_IDS = listOf(
            MEDIA_NOTIFICATION_BUTTON_PLAYBACK_MODE,
            MEDIA_NOTIFICATION_BUTTON_FAVORITE
        )
        private val MEDIA_NOTIFICATION_BUTTON_IDS = setOf(
            MEDIA_NOTIFICATION_BUTTON_PLAYBACK_MODE,
            MEDIA_NOTIFICATION_BUTTON_DESKTOP_LYRIC,
            MEDIA_NOTIFICATION_BUTTON_FAVORITE
        )

        fun normalizeMediaNotificationButtonIds(value: String): List<String> {
            val selected = value
                .split(',', '，', ';', '；', '\n')
                .asSequence()
                .map { it.trim().lowercase(Locale.ROOT) }
                .filter { it in MEDIA_NOTIFICATION_BUTTON_IDS }
                .distinct()
                .take(2)
                .toList()
            return (selected + DEFAULT_MEDIA_NOTIFICATION_BUTTON_IDS.filterNot(selected::contains))
                .distinct()
                .take(2)
        }
        val SEARCH_ALL_CATEGORY_TYPES = setOf("folder", "composer", "arranger", "lyricist", "genre", "year")
        val SEARCH_ALL_SONG_MATCH_TYPES = linkedSetOf(
            "title", "artist", "album", "file_name", "translated_name", "alias", "comment", "tag",
            "lyricist", "composer", "arranger", "album_artist", "genre", "year", "lyrics"
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
        const val HOME_RECENT_SECTION_MODE_PLAYED = 0
        const val HOME_RECENT_SECTION_MODE_ADDED = 1
        const val DEFAULT_HOME_LIBRARY_TILE_ORDER = "artist,album,folder,folder_tree,folder_playlist,playlist,analytics,genre,year,composer,arranger,lyricist"
        const val DEFAULT_HOME_ONLINE_TILE_ORDER = "lx,webdav"
        const val DEFAULT_ARTIST_SEPARATORS = "/\nfeat.\n&\n,"
        const val DEFAULT_GENRE_SEPARATORS = ";"

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

    private val desktopLyricSettings = DesktopLyricSettings(context.dataStore)

    val desktopLyricEnabled get() = desktopLyricSettings.desktopLyricEnabled
    val desktopLyricHideWhenPaused get() = desktopLyricSettings.desktopLyricHideWhenPaused
    val desktopLyricHideInLandscape get() = desktopLyricSettings.desktopLyricHideInLandscape
    val desktopLyricHideOnPlayerPage get() = desktopLyricSettings.desktopLyricHideOnPlayerPage
    val desktopLyricHideOnLyricsPage get() = desktopLyricSettings.desktopLyricHideOnLyricsPage
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
    suspend fun setDesktopLyricHideOnPlayerPage(enabled: Boolean) = desktopLyricSettings.setDesktopLyricHideOnPlayerPage(enabled)
    suspend fun setDesktopLyricHideOnLyricsPage(enabled: Boolean) = desktopLyricSettings.setDesktopLyricHideOnLyricsPage(enabled)
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
            setInt(KEY_CROSSFADE_CURVE)
            setBoolean(KEY_TICKER_ENABLED)
            setBoolean(KEY_TICKER_HIDE_NOTIFICATION)
            setBoolean(KEY_TICKER_HEADS_UP_LYRICS)
            setBoolean(KEY_LIVE_UPDATE_LYRIC_ENABLED)
            setBoolean(KEY_XIAOMI_SUPER_ISLAND_LYRIC_ENABLED)
            setString(KEY_XIAOMI_SUPER_ISLAND_SETTINGS)
            setBoolean(KEY_SAMSUNG_FLOATING_LYRIC_TRANSLATION)
            setBoolean(KEY_STATUS_BAR_ALLOW_PHONETIC)
            setBoolean(KEY_DESKTOP_LYRIC_ENABLED)
            setBoolean(KEY_DESKTOP_LYRIC_HIDE_WHEN_PAUSED)
            setBoolean(KEY_DESKTOP_LYRIC_HIDE_IN_LANDSCAPE)
            setBoolean(KEY_DESKTOP_LYRIC_HIDE_ON_PLAYER_PAGE)
            setBoolean(KEY_DESKTOP_LYRIC_HIDE_ON_LYRICS_PAGE)
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
            setBoolean(KEY_HIDE_LYRIC_EXTRA_INFO)
            setBoolean(KEY_REPLAYGAIN_ENABLED)
            setInt(KEY_REPLAYGAIN_MODE)
            setBoolean(KEY_RESUME_PLAYBACK_POSITION)
            setBoolean(KEY_AUDIO_FOCUS_DISABLED)
            setBoolean(KEY_LYRIC_PAGE_TRANSLATION)
            setBoolean(KEY_LYRIC_PAGE_KEEP_SCREEN_ON)
            setBoolean(KEY_APPLE_MUSIC_LYRICS_WORD_LIFT)
            setInt(KEY_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS)
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
            setBoolean(KEY_MINI_PLAYER_SWIPE_TO_OPEN_PLAYER)
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
            setBoolean(KEY_WIDGET_SAFE_LAYOUT)
            setInt(KEY_SYSTEM_BARS_MODE)
            setBoolean(KEY_SYSTEM_BARS_RESERVE_SPACE)
            setBoolean(KEY_HIDE_SYSTEM_BARS)
            setBoolean(KEY_PLAYER_DYNAMIC_FLOW_ENABLED)
            setBoolean(KEY_AUDIO_VISUALIZER_ENABLED)
            setBoolean(KEY_DYNAMIC_COVER_ENABLED)
            setBoolean(KEY_MUSIC_VIDEO_SYNC_ENABLED)
            setBoolean(KEY_MUSIC_VIDEO_CAPTURE_SUBTITLES)
            setBoolean(KEY_SHOW_LOCAL_MV_IN_LISTS)
            setBoolean(KEY_SHOW_ONLINE_MV_IN_LISTS)
            setBoolean(KEY_ARTIST_COVER_CAROUSEL)
            setBoolean(KEY_STARTUP_POSTER_ENABLED)
            setBoolean(KEY_APP_WALLPAPER_ENABLED)
            setBoolean(KEY_PLAYER_BACKGROUND_ENABLED)
            setBoolean(KEY_PLAYER_BEAUTIFUL_LYRICS_BACKGROUND)
            setBoolean(KEY_HI_RES_LOGO_ENABLED)
            setBoolean(KEY_PLAYLIST_SPECIAL_ENTRIES_VISIBLE)
            setBoolean(KEY_SHOW_PLAY_NEXT_IN_LISTS)
            setBoolean(KEY_SHOW_REMOVE_FROM_PLAYLIST_BUTTON)
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
            setBoolean(KEY_CONTINUE_PLAYBACK_ROW_VISIBLE)
            setBoolean(KEY_MCP_SERVER_ENABLED)
            setBoolean(KEY_WEB_MUSIC_SERVER_ENABLED)
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
            setInt(KEY_APP_FONT_SCALE_PERCENT)
            setInt(KEY_APP_DISPLAY_SCALE_PERCENT)
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
            setInt(KEY_LIVE_UPDATE_LYRIC_MODE)
            setInt(KEY_LIVE_UPDATE_LYRIC_DISPLAY_MODE)
            setInt(KEY_LIVE_UPDATE_LYRIC_SECONDARY_MODE)
            setInt(KEY_LYRIC_SOURCE_MODE)
            setInt(KEY_LYRIC_PARSER_ENGINE)
            setInt(KEY_PLAYER_TITLE_POSITION)
            setInt(KEY_PLAYER_PAGE_STYLE)
            setInt(KEY_PLAYER_LANDSCAPE_STYLE)
            setInt(KEY_MUSIC_VIDEO_ORIENTATION)
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
            setInt(KEY_HOME_RECENT_SECTION_MODE)
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
            setString(KEY_MEDIA_NOTIFICATION_BUTTONS)
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
            setString(KEY_FOLDER_PLAYLIST_CUSTOM_ORDER)
            setString(KEY_EQ_BANDS)
            setString(KEY_DYNAMIC_COVER_CUSTOM_FOLDERS)
            setString(KEY_MUSIC_VIDEO_CUSTOM_FOLDERS)
            setString(KEY_MUSIC_VIDEO_OFFSETS_JSON)
            setString(KEY_ARTIST_COVER_FOLDER_URI)
            setString(KEY_COVER_EXPORT_FOLDER_URI)
            setString(KEY_SEARCH_ALL_CATEGORY_TYPES)
            setString(KEY_SEARCH_ALL_SONG_MATCH_TYPES)
            setString(KEY_SPECTRUM_VIEWER_ID)

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
}
