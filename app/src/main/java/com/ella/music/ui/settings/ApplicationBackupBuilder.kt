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
    portableFontSettingKeys.forEach { key ->
        val path = filteredSettings.optString(key, "")
        if (isBundledFontPath(path)) {
            filteredSettings.put(key, "")
        }
    }
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
            if (BackupType.LibraryAndScan in selectedTypes) {
                // Descriptions are packed as separate files in descriptions/*.properties; omitted from root JSON to avoid duplication
            }
        }
}

internal fun exportDescriptionsJson(context: Context, fileName: String): JSONObject? =
    exportDescriptionsJson(context.filesDir, fileName)

internal fun exportDescriptionsJson(filesDir: java.io.File, fileName: String): JSONObject? {
    val file = java.io.File(filesDir, fileName)
    if (!file.isFile || !file.canRead() || file.length() == 0L) return null
    return runCatching {
        val props = java.util.Properties().apply {
            file.reader(Charsets.UTF_8).use { reader -> load(reader) }
        }
        if (props.isEmpty) return null
        JSONObject().apply {
            props.forEach { (k, v) -> put(k.toString(), v.toString()) }
        }
    }.getOrNull()
}
