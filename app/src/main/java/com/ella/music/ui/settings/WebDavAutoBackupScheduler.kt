package com.ella.music.ui.settings

import android.content.Context
import android.util.Log
import com.ella.music.data.SettingsManager
import com.ella.music.data.webdav.WebDavClient
import com.ella.music.data.webdav.WebDavConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONObject

data class WebDavCloudBackupCandidate(
    val root: JSONObject,
    val exportedAt: Long
)

object WebDavCloudRestoreCoordinator {
    private val _pending = MutableStateFlow<WebDavCloudBackupCandidate?>(null)
    val pending = _pending.asStateFlow()

    internal fun offer(candidate: WebDavCloudBackupCandidate) {
        if ((_pending.value?.exportedAt ?: 0L) < candidate.exportedAt) _pending.value = candidate
    }

    suspend fun dismiss(context: Context, candidate: WebDavCloudBackupCandidate) {
        SettingsManager.getInstance(context.applicationContext).setWebDavRestoreLastSeenAt(candidate.exportedAt)
        if (_pending.value?.exportedAt == candidate.exportedAt) _pending.value = null
    }

    suspend fun restore(
        context: Context,
        candidate: WebDavCloudBackupCandidate,
        selectedTypes: Set<BackupType>
    ) {
        restoreApplicationBackup(context, candidate.root, selectedTypes)
        val settings = SettingsManager.getInstance(context.applicationContext)
        settings.setWebDavRestoreLastSeenAt(candidate.exportedAt)
        settings.setWebDavAutoBackupLastAt(candidate.exportedAt)
        if (_pending.value?.exportedAt == candidate.exportedAt) _pending.value = null
    }
}

object WebDavAutoBackupScheduler {
    private const val CHECK_INTERVAL_MS = 15 * 60 * 1000L

    fun start(context: Context, scope: CoroutineScope) {
        val appContext = context.applicationContext
        val settings = SettingsManager.getInstance(appContext)
        scope.launch {
            while (isActive) {
                runCatching { backupIfDue(appContext, settings) }
                    .onFailure { Log.w("WebDavAutoBackup", "Automatic backup failed", it) }
                delay(CHECK_INTERVAL_MS)
            }
        }
    }

    private suspend fun backupIfDue(context: Context, settings: SettingsManager) {
        val baseUrl = settings.webDavBackupUrl.first().ifBlank { settings.webDavUrl.first() }.trim()
        if (baseUrl.isBlank()) return
        val config = WebDavConfig(
            url = baseUrl,
            username = settings.webDavBackupUsername.first().ifBlank { settings.webDavUsername.first() },
            password = settings.webDavBackupPassword.first().ifBlank { settings.webDavPassword.first() }
        )
        val path = settings.webDavBackupPath.first().trim().ifBlank { "halcyon_backup" }
        val backupDirUrl = "${baseUrl.trimEnd('/')}/$path/"
        val targetUrl = "${backupDirUrl}halcyon_backup_auto_latest.json"
        // Cloud restore detection belongs to the WebDAV configuration, not to automatic upload.
        // A second device must still be warned about newer backups when auto-backup is disabled.
        if (offerNewerCloudBackup(context, settings, config, backupDirUrl)) return
        if (!settings.webDavAutoBackupEnabled.first()) return

        val now = System.currentTimeMillis()
        val intervalMs = settings.webDavAutoBackupIntervalHours.first() * 60L * 60L * 1000L
        if (now - settings.webDavAutoBackupLastAt.first() < intervalMs) return
        val backup = buildCompleteApplicationBackupJson(context).toString(2)
        WebDavClient.uploadFileFromString(targetUrl, config, backup)
        settings.setWebDavAutoBackupLastAt(now)
        Log.i("WebDavAutoBackup", "Automatic backup completed")
    }

    private suspend fun offerNewerCloudBackup(
        context: Context,
        settings: SettingsManager,
        config: WebDavConfig,
        backupDirUrl: String
    ): Boolean {
        val localWatermark = maxOf(
            settings.webDavAutoBackupLastAt.first(),
            settings.webDavRestoreLastSeenAt.first()
        )
        val files = runCatching {
            WebDavClient.list(
                config = config,
                url = backupDirUrl,
                forceRefresh = true,
                includeNonAudioFiles = true
            )
        }.getOrNull().orEmpty()
            .asSequence()
            .filterNot { it.isDirectory }
            .filter { it.name.startsWith("halcyon_backup") && it.name.endsWith(".json") }
            .sortedByDescending { it.name }
            .toList()
        // Timestamped manual files sort chronologically by name. Probe only the newest manual
        // file plus the rolling automatic file, rather than downloading the whole backup history.
        val probeFiles = listOfNotNull(
            files.firstOrNull { it.name == "halcyon_backup_auto_latest.json" },
            files.firstOrNull { it.name != "halcyon_backup_auto_latest.json" }
        ).distinctBy { it.url }
        var newestRoot: JSONObject? = null
        var newestExportedAt = 0L
        probeFiles.forEachIndexed { index, item ->
            val tempFile = java.io.File(context.cacheDir, "webdav_restore_probe_$index.json")
            val root = runCatching {
                WebDavClient.downloadToFile(item.url, config, tempFile)
                JSONObject(tempFile.readText(Charsets.UTF_8))
            }.getOrNull().also {
                runCatching { tempFile.delete() }
            } ?: return@forEachIndexed
            val exportedAt = root.optLong("exportedAt", 0L)
            if (exportedAt > newestExportedAt) {
                newestExportedAt = exportedAt
                newestRoot = root
            }
        }
        val root = newestRoot ?: return false
        val exportedAt = newestExportedAt
        if (exportedAt <= localWatermark) return false
        WebDavCloudRestoreCoordinator.offer(WebDavCloudBackupCandidate(root, exportedAt))
        Log.i("WebDavAutoBackup", "Newer cloud backup detected; automatic upload paused")
        return true
    }
}
