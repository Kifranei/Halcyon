package com.ella.music.ui.settings

internal fun Set<BackupType>.toWebDavRestoreSetting(): String =
    sortedBy(BackupType::ordinal).joinToString(",", transform = BackupType::name)

internal fun String.toWebDavRestoreTypes(): Set<BackupType> {
    if (isBlank()) return emptySet()
    return split(',')
        .mapNotNull { saved -> BackupType.entries.firstOrNull { it.name == saved.trim() } }
        .toSet()
}
