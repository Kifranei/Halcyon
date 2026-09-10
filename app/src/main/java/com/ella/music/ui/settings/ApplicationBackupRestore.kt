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
    val isArchive = root.has("_portableAssetFiles") || root.has("_portableAssetDir")
    try {
        if (isArchive) {
            materializeApplicationBackupAssets(appContext, root, selectedTypes)
        }
        val filteredSettings = (root.optJSONObject("settings") ?: root).filterBackupSettings(
            selectedTypes = selectedTypes,
            includeDeviceLocalAssets = isArchive
        )
        if (filteredSettings.length() > 0) {
            SettingsManager.getInstance(appContext).restoreSettingsJson(
                payload = filteredSettings,
                restoreDeviceLocalAssets = isArchive
            )
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
        if (BackupType.LibraryAndScan in selectedTypes) {
            root.optJSONObject("artistDescriptions")?.let {
                restoreDescriptionsFromJson(appContext, "artist_descriptions.properties", it)
            }
            root.optJSONObject("albumDescriptions")?.let {
                restoreDescriptionsFromJson(appContext, "album_descriptions.properties", it)
            }
        }
    } finally {
        if (isArchive) cleanupApplicationBackupAssets(appContext, root)
    }
}

internal fun restoreDescriptionsFromJson(context: Context, fileName: String, json: JSONObject) =
    restoreDescriptionsFromJson(context.filesDir, fileName, json)

internal fun restoreDescriptionsFromJson(filesDir: java.io.File, fileName: String, json: JSONObject) {
    if (json.length() == 0) return
    runCatching {
        val target = java.io.File(filesDir, fileName)
        val props = if (target.isFile) {
            runCatching {
                java.util.Properties().apply {
                    target.reader(Charsets.UTF_8).use { reader -> load(reader) }
                }
            }.getOrDefault(java.util.Properties())
        } else {
            java.util.Properties()
        }
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            props.setProperty(key, json.optString(key, ""))
        }
        target.parentFile?.mkdirs()
        val temp = java.io.File(target.parentFile, "${target.name}.tmp_${System.currentTimeMillis()}")
        temp.writer(Charsets.UTF_8).buffered().use { writer ->
            props.store(writer, null)
        }
        if (!temp.renameTo(target)) {
            temp.copyTo(target, overwrite = true)
            temp.delete()
        }
    }
}
