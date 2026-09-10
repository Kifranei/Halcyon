package com.ella.music.ui.settings

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BACKUP_DATE_TIME_REGEX = Regex("""^\d{4}-\d{2}-\d{2}--\d{2}-\d{2}-\d{2}.*""")

internal fun generateBackupFileName(extension: String = "zip"): String {
    val timestamp = SimpleDateFormat("yyyy-MM-dd--HH-mm-ss", Locale.getDefault()).format(Date())
    return "$timestamp.$extension"
}

internal fun String.isHalcyonBackupFileName(): Boolean =
    startsWith("halcyon_backup_") ||
        startsWith("ella_backup_") ||
        startsWith("halcyon_settings_") ||
        matches(BACKUP_DATE_TIME_REGEX)

internal fun String.toBackupDisplayName(): String =
    removePrefix("halcyon_backup_")
        .removePrefix("ella_backup_")
        .removePrefix("halcyon_settings_")
        .removeSuffix(".json")
        .removeSuffix(".zip")
        .ifBlank { this }
