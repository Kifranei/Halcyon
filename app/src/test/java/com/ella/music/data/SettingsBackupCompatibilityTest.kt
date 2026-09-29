package com.ella.music.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsBackupCompatibilityTest {
    private val schema = linkedMapOf(
        "gapless_playback" to SettingsBackupValueType.BOOLEAN,
        "theme_mode" to SettingsBackupValueType.INT,
        "app_language" to SettingsBackupValueType.STRING,
        "brand_new_toggle" to SettingsBackupValueType.BOOLEAN
    )

    @Test
    fun identicalBackupFromSameVersionDoesNotWarn() {
        val report = compareSettingsBackup(
            backupSettings = mapOf("gapless_playback" to true, "theme_mode" to 2, "app_language" to "en"),
            backupSchemaKeys = schema.keys,
            backupVersionName = "1.2.9",
            currentVersionName = "1.2.9",
            currentSchema = schema
        )
        assertTrue(report.obsoleteKeys.isEmpty())
        assertEquals(emptyList<String>(), report.newKeys)
        assertTrue(report.changedKeys.isEmpty())
        assertFalse(report.shouldWarn)
    }

    @Test
    fun detectsObsoleteNewAndChangedKeys() {
        val report = compareSettingsBackup(
            backupSettings = mapOf(
                "gapless_playback" to "yes", // was a String, now a Boolean
                "theme_mode" to 1.5, // not an integer
                "app_language" to "de",
                "removed_setting" to 3, // known to the exporter, gone now
                "stale_leftover" to true // not even known to the exporter
            ),
            backupSchemaKeys = setOf("gapless_playback", "theme_mode", "app_language", "removed_setting"),
            backupVersionName = "1.2.10",
            currentVersionName = "1.2.11",
            currentSchema = schema
        )
        assertEquals(listOf("removed_setting"), report.obsoleteKeys)
        assertEquals(listOf("brand_new_toggle"), report.newKeys)
        assertEquals(listOf("gapless_playback", "theme_mode"), report.changedKeys)
        assertEquals(5, report.settingsCount)
        assertTrue(report.shouldWarn)
    }

    @Test
    fun legacyBackupWithoutSchemaCountsUnknownKeysAndCannotCountNewOnes() {
        val report = compareSettingsBackup(
            backupSettings = mapOf("gapless_playback" to true, "lyric_custom_font_enabled" to true),
            backupSchemaKeys = null,
            backupVersionName = null,
            currentVersionName = "1.2.9",
            currentSchema = schema
        )
        assertEquals(listOf("lyric_custom_font_enabled"), report.obsoleteKeys)
        assertNull(report.newKeys)
        assertFalse(report.newKeysKnown)
        assertTrue(report.shouldWarn)
    }

    @Test
    fun dynamicAndIntentionallyUnrestoredKeysAreNotObsolete() {
        val report = compareSettingsBackup(
            backupSettings = mapOf(
                "sort_metadata_category_genre" to 3,
                "pinned_artist" to "a,b",
                "pinned_album" to 7, // dynamic string key with the wrong shape
                "webdav_restore_last_seen_at" to "123"
            ),
            backupSchemaKeys = null,
            backupVersionName = null,
            currentVersionName = "1.2.9",
            currentSchema = schema
        )
        assertTrue(report.obsoleteKeys.isEmpty())
        assertEquals(listOf("pinned_album"), report.changedKeys)
    }

    @Test
    fun newKeysOnlyCountKeysInRestoreScope() {
        val report = compareSettingsBackup(
            backupSettings = mapOf("theme_mode" to 1),
            backupSchemaKeys = setOf("theme_mode"),
            backupVersionName = "1.2.9",
            currentVersionName = "1.2.9",
            currentSchema = schema,
            isInScope = { it == "theme_mode" || it == "app_language" }
        )
        assertEquals(listOf("app_language"), report.newKeys)
    }

    @Test
    fun nullValuesAndEmptySelectionsNeverWarn() {
        val report = compareSettingsBackup(
            backupSettings = mapOf("gapless_playback" to null),
            backupSchemaKeys = null,
            backupVersionName = null,
            currentVersionName = "1.2.9",
            currentSchema = schema
        )
        assertEquals(0, report.settingsCount)
        assertFalse(report.shouldWarn)
    }

    @Test
    fun intAcceptsWholeJsonNumbersOnly() {
        assertTrue(SettingsBackupValueType.INT.accepts(-16777216))
        assertTrue(SettingsBackupValueType.INT.accepts(42L))
        assertTrue(SettingsBackupValueType.INT.accepts(3.0))
        assertFalse(SettingsBackupValueType.INT.accepts(Long.MAX_VALUE))
        assertFalse(SettingsBackupValueType.INT.accepts("42"))
        assertFalse(SettingsBackupValueType.BOOLEAN.accepts("true"))
        assertTrue(SettingsBackupValueType.STRING.accepts(""))
    }
}
