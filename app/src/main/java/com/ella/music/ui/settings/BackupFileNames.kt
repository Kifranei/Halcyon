package com.ella.music.ui.settings

internal fun String.isHalcyonBackupFileName(): Boolean =
    startsWith("halcyon_backup_") || startsWith("ella_backup_")

internal fun String.toBackupDisplayName(): String =
    removePrefix("halcyon_backup_")
        .removePrefix("ella_backup_")
        .removeSuffix(".json")
        .removeSuffix(".zip")
        .ifBlank { this }
