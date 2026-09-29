package com.ella.music.ui.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.SettingsBackupCompatibilityReport
import com.ella.music.data.SettingsBackupValueType
import com.ella.music.data.compareSettingsBackup
import com.ella.music.ui.about.compareVersionNames
import com.ella.music.ui.components.EllaMiuixDialog
import com.ella.music.ui.components.EllaMiuixDialogActions
import org.json.JSONObject

/** App version that wrote the backup (absent in backups made before 1.2.9). */
internal const val BACKUP_APP_VERSION_NAME_FIELD = "appVersionName"
internal const val BACKUP_APP_VERSION_CODE_FIELD = "appVersionCode"

/** Every restorable settings key the exporting app knew, so a later version can spot new ones. */
internal const val BACKUP_SETTINGS_KEYS_FIELD = "settingsKeys"

/** Root-level bookkeeping fields; never settings, even when a legacy root doubles as settings. */
private val backupRootMetadataKeys = setOf(
    "version",
    "exportedAt",
    BACKUP_APP_VERSION_NAME_FIELD,
    BACKUP_APP_VERSION_CODE_FIELD,
    BACKUP_SETTINGS_KEYS_FIELD,
    PORTABLE_ASSETS_FIELD,
    BACKUP_LYRICO_PLUGINS_FIELD,
    "_portableAssetFiles",
    "_portableAssetDir"
)

/**
 * Compares the settings part of [root] that a restore of [selectedTypes] would apply with the
 * current app's restorable settings. Mirrors the filtering done by restoreApplicationBackup.
 */
internal fun checkBackupSettingsCompatibility(
    root: JSONObject,
    selectedTypes: Set<BackupType>,
    currentSchema: Map<String, SettingsBackupValueType>,
    currentVersionName: String
): SettingsBackupCompatibilityReport {
    val isArchive = root.has("_portableAssetFiles") || root.has("_portableAssetDir")
    val hasSectionedPayload = root.has("settings") || root.has("playlists") ||
        root.has("playback") || root.has("aiChat")
    val rawSettings = if (hasSectionedPayload) root.optJSONObject("settings") ?: JSONObject() else root
    val settings = rawSettings.filterBackupSettings(
        selectedTypes = selectedTypes,
        includeDeviceLocalAssets = isArchive
    )
    val values = linkedMapOf<String, Any?>()
    settings.keys().forEach { key ->
        if (key !in backupRootMetadataKeys) values[key] = settings.opt(key)
    }
    val schemaKeys = root.optJSONArray(BACKUP_SETTINGS_KEYS_FIELD)?.let { array ->
        (0 until array.length())
            .mapNotNull { index -> array.optString(index).takeIf(String::isNotBlank) }
            .toSet()
    }
    return compareSettingsBackup(
        backupSettings = values,
        backupSchemaKeys = schemaKeys,
        backupVersionName = root.optString(BACKUP_APP_VERSION_NAME_FIELD).takeIf(String::isNotBlank),
        currentVersionName = currentVersionName,
        currentSchema = currentSchema,
        isInScope = { key ->
            key.backupType() in selectedTypes && (isArchive || !key.isBackupExcludedSettingKey())
        }
    )
}

@Composable
internal fun BackupCompatibilityDialog(
    report: SettingsBackupCompatibilityReport?,
    onReconfigure: () -> Unit,
    onRestoreAnyway: () -> Unit,
    onDismiss: () -> Unit
) {
    val backupVersion = report?.backupVersionName
    val currentVersion = report?.currentVersionName.orEmpty()
    val intro = when {
        report == null -> ""
        backupVersion == null -> stringResource(R.string.backup_compat_from_older_unknown, currentVersion)
        backupVersion == currentVersion -> stringResource(R.string.backup_compat_same_version, currentVersion)
        compareVersionNames(backupVersion, currentVersion) > 0 ->
            stringResource(R.string.backup_compat_from_newer, backupVersion, currentVersion)
        else -> stringResource(R.string.backup_compat_from_older, backupVersion, currentVersion)
    }
    val details = when {
        report == null -> ""
        report.newKeys != null -> stringResource(
            R.string.backup_compat_details,
            report.obsoleteKeys.size,
            report.newKeys.size,
            report.changedKeys.size
        )
        else -> stringResource(
            R.string.backup_compat_details_legacy,
            report.obsoleteKeys.size,
            report.changedKeys.size
        )
    }
    EllaMiuixDialog(
        show = report != null,
        title = stringResource(R.string.backup_compat_title),
        summary = "$intro\n\n$details",
        onDismissRequest = onDismiss
    ) {
        EllaMiuixDialogActions(
            cancelText = stringResource(R.string.backup_compat_restore_anyway),
            confirmText = stringResource(R.string.backup_compat_reconfigure),
            onCancel = onRestoreAnyway,
            onConfirm = onReconfigure
        )
    }
}
