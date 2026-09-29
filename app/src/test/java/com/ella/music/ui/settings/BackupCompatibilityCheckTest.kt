package com.ella.music.ui.settings

import com.ella.music.data.SettingsBackupValueType
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupCompatibilityCheckTest {
    private val schema = linkedMapOf(
        "theme_mode" to SettingsBackupValueType.INT,
        "app_language" to SettingsBackupValueType.STRING,
        "openai_model" to SettingsBackupValueType.STRING,
        "new_toggle" to SettingsBackupValueType.BOOLEAN
    )

    @Test
    fun sectionedBackupReportsVersionSchemaAndSelectedTypesOnly() {
        val root = JSONObject()
            .put("version", 2)
            .put(BACKUP_APP_VERSION_NAME_FIELD, "1.2.9")
            .put(BACKUP_SETTINGS_KEYS_FIELD, JSONArray(listOf("theme_mode", "app_language", "openai_model", "old_key")))
            .put(
                "settings",
                JSONObject()
                    .put("theme_mode", "dark") // format changed
                    .put("app_language", "en")
                    .put("old_key", true) // obsolete
                    .put("openai_model", 5) // AI category, not selected below
            )
            .put("playlists", JSONObject())

        val report = checkBackupSettingsCompatibility(
            root = root,
            selectedTypes = setOf(BackupType.Personalization),
            currentSchema = schema,
            currentVersionName = "1.2.10"
        )

        assertEquals("1.2.9", report.backupVersionName)
        assertEquals(3, report.settingsCount)
        assertEquals(listOf("old_key"), report.obsoleteKeys)
        assertEquals(listOf("new_toggle"), report.newKeys)
        assertEquals(listOf("theme_mode"), report.changedKeys)
        assertTrue(report.shouldWarn)
    }

    @Test
    fun legacyFlatBackupIgnoresRootMetadataAndHasUnknownVersion() {
        val root = JSONObject()
            .put("version", 1)
            .put("exportedAt", 1L)
            .put("theme_mode", 1)

        val report = checkBackupSettingsCompatibility(
            root = root,
            selectedTypes = BackupType.entries.toSet(),
            currentSchema = schema,
            currentVersionName = "1.2.9"
        )

        assertNull(report.backupVersionName)
        assertEquals(1, report.settingsCount)
        assertTrue(report.obsoleteKeys.isEmpty())
        assertNull(report.newKeys)
        assertTrue(report.shouldWarn)
    }

    @Test
    fun backupWithoutSelectedSettingsNeverWarns() {
        val root = JSONObject()
            .put("settings", JSONObject().put("theme_mode", "broken"))
            .put("playlists", JSONObject())

        val report = checkBackupSettingsCompatibility(
            root = root,
            selectedTypes = setOf(BackupType.Playlists),
            currentSchema = schema,
            currentVersionName = "1.2.9"
        )

        assertEquals(0, report.settingsCount)
        assertFalse(report.shouldWarn)
    }
}
