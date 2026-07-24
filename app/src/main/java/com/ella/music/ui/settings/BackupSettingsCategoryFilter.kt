package com.ella.music.ui.settings

import org.json.JSONObject

internal fun JSONObject.filterBackupSettings(selectedTypes: Set<BackupType>): JSONObject {
    if (selectedTypes.isEmpty()) return JSONObject()
    val filtered = JSONObject()
    val iterator = keys()
    while (iterator.hasNext()) {
        val key = iterator.next()
        if (key.backupType() in selectedTypes) {
            filtered.put(key, opt(key))
        }
    }
    return filtered
}

private fun String.backupType(): BackupType = when {
    isEqualizerSettingKey() -> BackupType.Equalizer
    isOnlineSourceSettingKey() -> BackupType.OnlineSources
    isAiSettingKey() -> BackupType.AiConfigAndChat
    isFolderPlaylistSettingKey() -> BackupType.FolderPlaylists
    isPlaylistSettingKey() -> BackupType.Playlists
    isLibraryAndScanSettingKey() -> BackupType.LibraryAndScan
    else -> BackupType.Personalization
}

internal fun JSONObject.availableBackupTypes(): Set<BackupType> {
    val available = linkedSetOf<BackupType>()
    val hasSectionedPayload = has("settings") || has("playlists") || has("playback") || has("aiChat")
    val settings = if (hasSectionedPayload) optJSONObject("settings") ?: JSONObject() else this
    val keys = settings.keys()
    while (keys.hasNext()) {
        available += keys.next().backupType()
    }
    if (has("playlists")) available += BackupType.Playlists
    if (has("playback")) available += BackupType.PlaybackStats
    if (has("aiChat")) available += BackupType.AiConfigAndChat
    return available
}

private fun String.isAiSettingKey(): Boolean =
    this == "openai_api_key" ||
        this == "openai_base_url" ||
        this == "openai_model"

private fun String.isEqualizerSettingKey(): Boolean =
    startsWith("audio_eq_") ||
        startsWith("audio_surround_360_") ||
        startsWith("audio_bass_boost_") ||
        startsWith("audio_virtualizer_") ||
        startsWith("audio_reverb_")

private fun String.isOnlineSourceSettingKey(): Boolean =
    startsWith("webdav_") ||
        startsWith("lx_") ||
    startsWith("navidrome_") ||
        startsWith("opensubsonic_") ||
        startsWith("emby_") ||
        this == "online_selected_provider"

private fun String.isFolderPlaylistSettingKey(): Boolean =
    this == "folder_playlists" ||
        this == "sort_folder_playlist_list" ||
        this == "pinned_folder_playlist"

private fun String.isPlaylistSettingKey(): Boolean =
    this == "sort_playlist_list" ||
        this == "sort_playlist_detail_song" ||
        this == "playlist_special_entries_visible" ||
        this == "playlist_custom_order" ||
        this == "add_to_playlist_append_to_end"

private fun String.isLibraryAndScanSettingKey(): Boolean =
    this == "auto_scan" ||
        this == "auto_scan_local_playlists" ||
        this == "scan_include_folders" ||
        this == "scan_exclude_folders" ||
        this == "usb_folder_uris" ||
        this == "use_android_media_library" ||
        this == "full_tag_search_enabled" ||
        this == "initial_scan_prompt_handled" ||
        this == "local_playlist_scan_prompt_handled" ||
        this == "artist_separators" ||
        this == "artist_protected_names" ||
        this == "genre_separators" ||
        this == "genre_protected_names" ||
        this == "tag_ignore_case" ||
        this == "show_album_artists" ||
        this == "category_grid_columns" ||
        this == "sort_library_song" ||
        this == "sort_album_list" ||
        this == "sort_artist_list" ||
        this == "sort_album_detail_song" ||
        this == "sort_artist_detail_song" ||
        this == "sort_artist_detail_album" ||
        this == "sort_folder_list" ||
        this == "sort_folder_detail_song" ||
        startsWith("sort_metadata_category_") ||
        startsWith("sort_metadata_category_detail_song_") ||
        startsWith("sort_metadata_category_detail_album_") ||
        (startsWith("pinned_") && this != "pinned_folder_playlist")
