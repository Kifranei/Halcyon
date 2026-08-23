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
        if (!settings.webDavAutoBackupEnabled.first()) return
        val baseUrl = settings.webDavBackupUrl.first().ifBlank { settings.webDavUrl.first() }.trim()
        if (baseUrl.isBlank()) return
        val config = WebDavConfig(
            url = baseUrl,
            username = settings.webDavBackupUsername.first().ifBlank { settings.webDavUsername.first() },
            password = settings.webDavBackupPassword.first().ifBlank { settings.webDavPassword.first() }
        )
        val path = settings.webDavBackupPath.first().trim().ifBlank { "halcyon_backup" }
        val targetUrl = "${baseUrl.trimEnd('/')}/$path/halcyon_backup_auto_latest.json"
        if (offerNewerCloudBackup(context, settings, config, targetUrl)) return

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
        targetUrl: String
    ): Boolean {
        val localWatermark = maxOf(
            settings.webDavAutoBackupLastAt.first(),
            settings.webDavRestoreLastSeenAt.first()
        )
        val tempFile = java.io.File(context.cacheDir, "webdav_auto_restore_probe.json")
        val root = runCatching {
            WebDavClient.downloadToFile(targetUrl, config, tempFile)
            JSONObject(tempFile.readText(Charsets.UTF_8))
        }.getOrNull().also {
            runCatching { tempFile.delete() }
        } ?: return false
        val exportedAt = root.optLong("exportedAt", 0L)
        if (exportedAt <= localWatermark) return false
        WebDavCloudRestoreCoordinator.offer(WebDavCloudBackupCandidate(root, exportedAt))
        Log.i("WebDavAutoBackup", "Newer cloud backup detected; automatic upload paused")
        return true
    }
}
