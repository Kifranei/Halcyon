package com.ella.music.data

internal object SettingsSearchHistory {
    const val LIMIT = 12

    fun decode(raw: String?): List<String> =
        raw.orEmpty()
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .toList()

    fun encode(items: List<String>): String = items.joinToString("\n")

    fun record(current: List<String>, query: String): List<String> {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return current
        return (listOf(trimmed) + current.filterNot { it.equals(trimmed, ignoreCase = true) })
            .take(LIMIT)
    }
}
