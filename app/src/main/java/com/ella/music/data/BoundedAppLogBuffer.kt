package com.ella.music.data

/** A recent UI snapshot; full export continues to read Android's own logcat ring buffer. */
internal class BoundedAppLogBuffer(
    private val maxChars: Int = 1_048_576,
    private val maxEntryChars: Int = 32_768
) {
    private val entries = ArrayDeque<AppLogEntry>()
    var retainedChars: Int = 0
        private set

    fun add(entry: AppLogEntry) {
        val safe = entry.boundedForSnapshot(minOf(maxEntryChars, maxChars - ENTRY_OVERHEAD - 432))
        val previous = entries.lastOrNull()
        if (previous != null && previous.time == safe.time && previous.level == safe.level &&
            previous.tag == safe.tag && previous.message == safe.message && previous.detail == safe.detail) return
        entries.addLast(safe)
        retainedChars += safe.snapshotCost()
        while (retainedChars > maxChars && entries.isNotEmpty()) {
            retainedChars -= entries.removeFirst().snapshotCost()
        }
    }

    fun snapshot(): List<AppLogEntry> = entries.toList().asReversed()
    fun clear() { entries.clear(); retainedChars = 0 }

    private fun AppLogEntry.snapshotCost(): Int = ENTRY_OVERHEAD +
        tag.length + level.length + type.length + message.length + (detail?.length ?: 0) + (relatedId?.length ?: 0)

    companion object { private const val ENTRY_OVERHEAD = 128 }
}

internal fun AppLogEntry.boundedForSnapshot(maxTextChars: Int = 32_768): AppLogEntry {
    val budget = maxTextChars.coerceAtLeast(0)
    val message = message.take(budget)
    return copy(tag = tag.take(128), level = level.take(16), type = type.take(32), relatedId = relatedId?.take(256),
        message = message, detail = detail?.take((budget - message.length).coerceAtLeast(0)))
}
