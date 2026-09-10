package com.ella.music.ui.settings

/**
 * Destination for a settings_* resource that is not listed in the hand-maintained search index.
 *
 * When a preference moves between pages, this mapping has to move with it; otherwise search
 * still opens the old category (#610).
 */
internal sealed class SettingsSearchFallbackDestination {
    data class Appearance(val highlight: String) : SettingsSearchFallbackDestination()
    data class Home(val highlight: String) : SettingsSearchFallbackDestination()
    data class Library(val highlight: String) : SettingsSearchFallbackDestination()
    data class Lyrics(val highlight: String) : SettingsSearchFallbackDestination()
    data class Audio(val highlight: String) : SettingsSearchFallbackDestination()
    data class Backup(val highlight: String) : SettingsSearchFallbackDestination()
    data class Integration(val highlight: String) : SettingsSearchFallbackDestination()
    data class CoverMedia(val highlight: String) : SettingsSearchFallbackDestination()
}

internal fun settingsSearchFallbackDestination(
    name: String
): SettingsSearchFallbackDestination {
    val appearanceKey = name.removePrefix("settings_")
    return when {
        name == "settings_enable_live_update_lyric" ->
            SettingsSearchFallbackDestination.Lyrics("live_update_lyric")
        name == "settings_live_update_lyric_content" ->
            SettingsSearchFallbackDestination.Lyrics("live_update_lyric_content")
        name == "settings_live_update_lyric_display" ->
            SettingsSearchFallbackDestination.Lyrics("live_update_lyric_display")
        name == "settings_live_update_lyric_secondary" ->
            SettingsSearchFallbackDestination.Lyrics("live_update_lyric_secondary")
        name == "settings_enable_vivo_atom_walkman_whitelist" ->
            SettingsSearchFallbackDestination.Lyrics("vivo_atom_walkman_whitelist")
        name.contains("beautiful_lyrics") || name.contains("apple_flow") ||
            name.contains("player_dynamic_flow") || name.contains("app_wallpaper") ||
            name.contains("now_playing_flow") || name.contains("player_background") ||
            name.contains("system_bars") || name.contains("startup_poster") ||
            name.contains("player_bg_theme") ->
            SettingsSearchFallbackDestination.Appearance(appearanceKey)
        name.contains("category_grid") || name.contains("search_click") ||
            name.contains("search_reopen") || name.contains("auto_show_search") ||
            name.contains("playlist_special") || name.contains("playlist_show_") ||
            name.contains("library_show_") ||
            name.contains("mini_player_long_press") || name.contains("open_player_on_play") ||
            name.contains("song_info_layout") || name.contains("queue_toolbar") ||
            name.contains("list_action") || name.contains("exclude_search") ||
            name.contains("play_next_in_lists") || name.contains("remove_from_playlist") ->
            SettingsSearchFallbackDestination.Appearance(appearanceKey)
        name.contains("mini_player") ->
            SettingsSearchFallbackDestination.Lyrics(
                if (appearanceKey == "mini_player_lyrics") "mini_lyrics" else appearanceKey
            )
        (name.contains("player_") && !name.contains("lyric") && !name.contains("mini_player")) ||
            name.contains("hi_res") || name.contains("transport_button") ||
            name.contains("open_player_from_notification") ->
            SettingsSearchFallbackDestination.Appearance(appearanceKey)
        name.contains("theme_mode") || name.contains("monet") || name.contains("app_icon") ||
            name.contains("font_scale") || name.contains("display_scale") ||
            name.contains("widget_safe") || name.contains("bottom_bar_style") ||
            name == "settings_language" ->
            SettingsSearchFallbackDestination.Appearance(appearanceKey)
        name.contains("backup") ->
            SettingsSearchFallbackDestination.Backup("backup_settings")
        name.contains("openai") || name.contains("mcp") || name.contains("lastfm") ||
            name.contains("ai_") ->
            SettingsSearchFallbackDestination.Integration("ai")
        name.contains("lyric") || name.contains("desktop") || name.contains("status_") ||
            name.contains("coloros") || name.contains("flyme") ->
            SettingsSearchFallbackDestination.Lyrics("lyric_basic")
        name.contains("audio") || name.contains("decoder") || name.contains("usb") ||
            name.contains("crossfade") || name.contains("replay") || name.contains("gapless") ||
            name.contains("karaoke") || name.contains("accompaniment") ||
            name.contains("shuffle") || name.contains("playback") || name.contains("previous_button") ||
            name.contains("resume_") || name.contains("startup_play") ->
            SettingsSearchFallbackDestination.Audio("audio_playback")
        name.contains("dynamic_cover") || name.contains("music_video") ||
            name.contains("artist_cover") || name.contains("cover_export") ||
            name.contains("artist_image") || name.contains("spotify_client") ||
            name.contains("cover_media") ->
            SettingsSearchFallbackDestination.CoverMedia("cover_media")
        name.contains("scan") || name.contains("library") || name.contains("metadata") ||
            name.contains("tag_") || name.contains("artist_") || name.contains("genre_") ||
            name.contains("full_tag") ->
            SettingsSearchFallbackDestination.Library("scan")
        name.contains("home_") || name.contains("bottom_dock") ->
            SettingsSearchFallbackDestination.Home("home_sections")
        else -> SettingsSearchFallbackDestination.Appearance(appearanceKey)
    }
}
