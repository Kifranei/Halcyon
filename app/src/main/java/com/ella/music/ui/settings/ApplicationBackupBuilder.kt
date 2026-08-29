package com.ella.music.ui.settings

import android.content.Context
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.PlaylistStore
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal suspend fun buildCompleteApplicationBackupJson(
    context: Context,
    librarySongs: List<Song> = emptyList()
): JSONObject = buildApplicationBackupJson(context, librarySongs = librarySongs)

internal suspend fun buildApplicationBackupJson(
    context: Context,
    selectedTypes: Set<BackupType> = BackupType.entries.toSet(),
    librarySongs: List<Song> = emptyList(),
    includeDeviceLocalAssets: Boolean = false
): JSONObject = withContext(Dispatchers.IO) {
    val filteredSettings = SettingsManager.getInstance(context)
        .exportSettingsJson(includeDeviceLocalAssets = includeDeviceLocalAssets)
        .filterBackupSettings(
            selectedTypes = selectedTypes,
            includeDeviceLocalAssets = includeDeviceLocalAssets
        )
    JSONObject()
        .put("version", if (includeDeviceLocalAssets) 2 else 1)
        .put("exportedAt", System.currentTimeMillis())
        .apply {
            if (filteredSettings.length() > 0) put("settings", filteredSettings)
            if (BackupType.Playlists in selectedTypes) {
                put("playlists", PlaylistStore.getInstance(context).exportJson())
            }
            if (BackupType.PlaybackStats in selectedTypes) {
                put("playback", PlaybackStatsStore.getInstance(context).exportJson(librarySongs))
            }
            if (BackupType.AiConfigAndChat in selectedTypes) {
                put("aiChat", exportAiChatBackupJson(context))
            }
        }
}
