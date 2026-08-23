package com.ella.music.ui.settings

import android.content.Context
import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.PlaylistStore
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal suspend fun restoreApplicationBackup(
    context: Context,
    root: JSONObject,
    selectedTypes: Set<BackupType>
) = withContext(Dispatchers.IO) {
    val appContext = context.applicationContext
    val filteredSettings = (root.optJSONObject("settings") ?: root).filterBackupSettings(selectedTypes)
    if (filteredSettings.length() > 0) {
        SettingsManager.getInstance(appContext).restoreSettingsJson(filteredSettings)
    }
    if (BackupType.Playlists in selectedTypes) {
        val playlistPayload = root.optJSONObject("playlists") ?: root.takeIf { it.has("playlists") }
        playlistPayload?.let { PlaylistStore.getInstance(appContext).restoreJson(it) }
    }
    if (BackupType.PlaybackStats in selectedTypes) {
        root.optJSONObject("playback")?.let { PlaybackStatsStore.getInstance(appContext).restoreJson(it) }
    }
    if (BackupType.AiConfigAndChat in selectedTypes) {
        root.optJSONObject("aiChat")?.let { restoreAiChatBackupJson(appContext, it) }
    }
}
