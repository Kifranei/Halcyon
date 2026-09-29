package com.ella.music.data

/** Value shape a restorable DataStore setting is written with by [SettingsManager.restoreSettingsJson]. */
enum class SettingsBackupValueType {
    BOOLEAN,
    INT,
    STRING;

    /** Whether a raw JSON value from a backup has the shape this setting expects. */
    fun accepts(value: Any?): Boolean = when (this) {
        BOOLEAN -> value is Boolean
        STRING -> value is String
        INT -> value is Number && value.toDouble().let { number ->
            number == Math.floor(number) &&
                number >= Int.MIN_VALUE.toDouble() &&
                number <= Int.MAX_VALUE.toDouble()
        }
    }
}

/**
 * DataStore keys the current app owns but deliberately never restores from a backup. They are
 * neither "obsolete" when found in a backup nor "new" when missing from one.
 */
internal val knownNonRestoredSettingKeys: Set<String> = setOf(
    "webdav_restore_last_seen_at",
    "progressive_top_bar_blur",
    "library_analysis_by_size"
)

/** Keys restored through the prefix-based dynamic path of restoreSettingsJson. */
internal fun dynamicRestorableSettingType(key: String): SettingsBackupValueType? = when {
    key.startsWith("sort_metadata_category_") -> SettingsBackupValueType.INT
    isRestorableDynamicStringPreferenceKey(key) -> SettingsBackupValueType.STRING
    else -> null
}

data class SettingsBackupCompatibilityReport(
    /** App version that wrote the backup, or null for backups made before it was recorded. */
    val backupVersionName: String?,
    val currentVersionName: String,
    /** Number of non-null settings in the part of the backup that is about to be restored. */
    val settingsCount: Int,
    /** Settings in the backup the current app no longer knows (restore skips them). */
    val obsoleteKeys: List<String>,
    /**
     * Settings the current app has that the exporting app did not know about. Null when the
     * backup predates the recorded settings list, so the number cannot be determined.
     */
    val newKeys: List<String>?,
    /** Settings whose stored value no longer matches the shape the current app expects. */
    val changedKeys: List<String>
) {
    val newKeysKnown: Boolean get() = newKeys != null

    /**
     * Warn when settings are about to be restored and either the backup cannot be verified
     * (legacy backup without version / settings list) or any key-level difference was found.
     */
    val shouldWarn: Boolean
        get() = settingsCount > 0 && (
            backupVersionName.isNullOrBlank() ||
                newKeys == null ||
                obsoleteKeys.isNotEmpty() ||
                newKeys.isNotEmpty() ||
                changedKeys.isNotEmpty()
            )
}

/**
 * Pure comparison between a backup's settings and the current app's restorable settings schema.
 *
 * @param backupSettings the settings entries that would be restored (already filtered to the
 *   selected backup categories); null values are ignored exactly like restore ignores them.
 * @param backupSchemaKeys the full list of restorable keys recorded by the exporting app, or null
 *   for legacy backups that do not carry it.
 * @param currentSchema every key the current restore code applies, with its expected type.
 * @param isInScope whether a current-schema key belongs to the categories being restored; used so
 *   that "new settings" only counts keys the user actually asked to restore.
 */
fun compareSettingsBackup(
    backupSettings: Map<String, Any?>,
    backupSchemaKeys: Set<String>?,
    backupVersionName: String?,
    currentVersionName: String,
    currentSchema: Map<String, SettingsBackupValueType>,
    isInScope: (String) -> Boolean = { true }
): SettingsBackupCompatibilityReport {
    val obsolete = mutableListOf<String>()
    val changed = mutableListOf<String>()
    var count = 0
    backupSettings.forEach { (key, value) ->
        if (value == null || value == org.json.JSONObject.NULL) return@forEach
        count++
        val expected = currentSchema[key] ?: dynamicRestorableSettingType(key)
        if (expected != null) {
            if (!expected.accepts(value)) changed.add(key)
            return@forEach
        }
        if (key in knownNonRestoredSettingKeys) return@forEach
        // A key the exporting app itself did not know was already a stale leftover when the
        // backup was written; it tells nothing about this version and restore ignores it.
        if (backupSchemaKeys != null && key !in backupSchemaKeys) return@forEach
        obsolete.add(key)
    }
    val newKeys = backupSchemaKeys?.let { exported ->
        currentSchema.keys
            .filter { it !in exported && it !in knownNonRestoredSettingKeys && isInScope(it) }
            .sorted()
    }
    return SettingsBackupCompatibilityReport(
        backupVersionName = backupVersionName?.takeIf(String::isNotBlank),
        currentVersionName = currentVersionName,
        settingsCount = count,
        obsoleteKeys = obsolete.sorted(),
        newKeys = newKeys,
        changedKeys = changed.sorted()
    )
}
