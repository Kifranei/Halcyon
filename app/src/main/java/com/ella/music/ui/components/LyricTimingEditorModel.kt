package com.ella.music.ui.components

internal data class LyricTimingLine(
    val text: String,
    val timeMs: Long? = null
)

internal fun String.toLyricTimingLines(existing: List<LyricTimingLine>): List<LyricTimingLine> {
    val previousByIndex = existing.withIndex().associate { it.index to it.value }
    return lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .mapIndexed { index, text ->
            previousByIndex[index]
                ?.takeIf { it.text == text }
                ?.copy(text = text)
                ?: LyricTimingLine(text = text)
        }
        .toList()
}

internal fun List<LyricTimingLine>.toEmbeddedLrc(): String =
    asSequence()
        .filter { it.timeMs != null && it.text.isNotBlank() }
        .sortedBy { it.timeMs }
        .joinToString(separator = "\n") { line ->
            "${line.timeMs!!.toLrcTimestamp()}${line.text}"
        }

internal fun Long.toLrcTimestamp(): String {
    val centiseconds = (coerceAtLeast(0L) / 10L)
    val minutes = centiseconds / 6_000L
    val seconds = (centiseconds % 6_000L) / 100L
    val fraction = centiseconds % 100L
    return "[%02d:%02d.%02d]".format(minutes, seconds, fraction)
}

internal fun Long.toTimingDisplay(): String {
    val milliseconds = coerceAtLeast(0L)
    val minutes = milliseconds / 60_000L
    val seconds = (milliseconds % 60_000L) / 1_000L
    val fraction = (milliseconds % 1_000L) / 10L
    return "%02d:%02d.%02d".format(minutes, seconds, fraction)
}
