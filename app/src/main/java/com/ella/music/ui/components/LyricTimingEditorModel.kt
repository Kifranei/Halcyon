package com.ella.music.ui.components

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song

/** The editable form preserves the rich TTML information that the lyric parser already exposes. */
internal data class LyricTimingLine(
    val text: String,
    val timeMs: Long? = null,
    val words: List<LyricWord> = emptyList(),
    val translation: String? = null,
    val pronunciation: String? = null,
    val pronunciationWords: List<LyricWord> = emptyList(),
    val agent: String? = null,
    val agentName: String? = null,
    val backgroundText: String? = null,
    val backgroundWords: List<LyricWord> = emptyList(),
    val backgroundTranslation: String? = null,
    val backgroundStartMs: Long? = null,
    val backgroundEndMs: Long? = null,
    val endMs: Long? = null
)

internal enum class LyricTimingFormat {
    Lrc,
    Elrc,
    Ttml
}

internal fun LyricLine.toLyricTimingLine() = LyricTimingLine(
    text = text,
    timeMs = timeMs,
    words = words,
    translation = translation,
    pronunciation = pronunciation,
    pronunciationWords = pronunciationWords,
    agent = agent,
    agentName = agentName,
    backgroundText = backgroundText,
    backgroundWords = backgroundWords,
    backgroundTranslation = backgroundTranslation,
    backgroundStartMs = backgroundStartMs,
    backgroundEndMs = backgroundEndMs,
    endMs = endMs
)

internal fun String.toLyricTimingLines(existing: List<LyricTimingLine>): List<LyricTimingLine> {
    val previousByIndex = existing.withIndex().associate { it.index to it.value }
    val editedPrimaryLines = lineSequence()
        .map(String::trim)
        .filter(String::isNotBlank)
        .mapIndexed { index, text ->
            previousByIndex[index]
                ?.takeIf { it.text == text }
                ?.copy(text = text)
                ?: LyricTimingLine(text = text)
        }
        .toList()
    // The free-form text area represents primary vocals only. Keep independent x-bg paragraphs
    // that arrived from an existing TTML/ELRC file, even though they have no primary text row.
    // Keep the text-editor order while timing. Sorting here used to reshuffle unfinished lines
    // after each tap, which made sequential line and word timing unreliable. Exporters sort only
    // when they need chronological output.
    return editedPrimaryLines + existing.filter { it.text.isBlank() && !it.backgroundText.isNullOrBlank() }
}

internal fun List<LyricTimingLine>.toEmbeddedLrc(): String =
    sortedTimed().flatMap { line ->
        val tag = line.timeMs!!.toLrcTimestamp()
        buildList {
            add("$tag${line.text}")
            line.translation?.takeIf(String::isNotBlank)?.let { add("$tag$it") }
        }
    }.joinToString(separator = "\n")

/**
 * Serializes enhanced LRC without throwing away word timing, duet labels, or background vocals.
 * Background lyrics use the format already accepted by [com.ella.music.data.parser.EllaLyricsParser].
 */
internal fun List<LyricTimingLine>.toEmbeddedElrc(): String =
    sortedTimed().flatMapIndexed { index, line ->
        val start = line.timeMs ?: return@flatMapIndexed emptyList()
        val tag = start.toLrcTimestamp()
        val nextStart = getOrNull(index + 1)?.timeMs
        val end = line.resolvedEnd(nextStart)
        buildList {
            if (line.text.isNotBlank()) {
                val agentPrefix = line.agent?.takeIf { it.equals("v1", true) || it.equals("v2", true) }
                    ?.lowercase()
                    ?.let { "$it: " }
                    .orEmpty()
                add("$tag$agentPrefix${line.words.toElrcTokens(line.text, start, end)}")
                line.translation?.takeIf(String::isNotBlank)?.let { add("$tag$it") }
            }
            line.backgroundText?.takeIf(String::isNotBlank)?.let { background ->
                val backgroundStart = line.backgroundStartMs ?: start
                val backgroundEnd = line.backgroundEndMs ?: end
                val words = line.backgroundWords.toElrcTokens(background, backgroundStart, backgroundEnd)
                add("$tag[bg:$words]")
                line.backgroundTranslation?.takeIf(String::isNotBlank)?.let { translation ->
                    add("$tag[bg:<${backgroundStart.toElrcTimestamp()}>$translation<${backgroundEnd.toElrcTimestamp()}>]")
                }
            }
        }
    }.joinToString(separator = "\n")

internal fun List<LyricTimingLine>.toEmbeddedTtml(song: Song): String {
    val lines = sortedTimed()
    if (lines.isEmpty()) return ""
    val agents = lines.mapNotNull { it.agent?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .filter { it.matches(Regex("[A-Za-z_][A-Za-z0-9_.-]*")) }
    val totalEnd = lines.mapIndexed { index, line -> line.resolvedEnd(lines.getOrNull(index + 1)?.timeMs) }
        .maxOrNull()
        ?: 0L

    return buildString {
        appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        appendLine("<tt xmlns=\"http://www.w3.org/ns/ttml\" xmlns:itunes=\"http://music.apple.com/lyric-ttml-internal\" xmlns:ttm=\"http://www.w3.org/ns/ttml#metadata\" itunes:timing=\"Word\">")
        appendLine("  <head>")
        appendLine("    <metadata>")
        appendLine("      <ttm:title>${song.title.xmlEscape()}</ttm:title>")
        appendLine("      <ttm:agent type=\"person\" xml:id=\"v1\"/>")
        appendLine("      <ttm:agent type=\"person\" xml:id=\"v2\"/>")
        agents.filterNot { it == "v1" || it == "v2" }.forEach { agent ->
            appendLine("      <ttm:agent type=\"person\" xml:id=\"${agent.xmlEscape()}\"/>")
        }
        appendLine("    </metadata>")
        appendLine("  </head>")
        appendLine("  <body dur=\"${totalEnd.toTtmlTimestamp()}\">")
        appendLine("    <div begin=\"${lines.first().timeMs!!.toTtmlTimestamp()}\" end=\"${totalEnd.toTtmlTimestamp()}\">")
        lines.forEachIndexed { index, line ->
            val start = line.timeMs!!
            val end = line.resolvedEnd(lines.getOrNull(index + 1)?.timeMs)
            val agent = line.agent?.trim()?.takeIf { it in agents }?.let { " ttm:agent=\"${it.xmlEscape()}\"" }.orEmpty()
            append("      <p begin=\"${start.toTtmlTimestamp()}\" end=\"${end.toTtmlTimestamp()}\"$agent>")
            append(line.words.toTtmlSpans(line.text, start, end))
            line.translation?.takeIf(String::isNotBlank)?.let {
                append("<span ttm:role=\"x-translation\" xml:lang=\"zh-CN\">${it.xmlEscape()}</span>")
            }
            line.pronunciation?.takeIf(String::isNotBlank)?.let {
                append("<span ttm:role=\"x-roman\">${it.xmlEscape()}</span>")
            }
            line.backgroundText?.takeIf(String::isNotBlank)?.let { background ->
                val backgroundStart = line.backgroundStartMs ?: start
                val backgroundEnd = line.backgroundEndMs ?: end
                append("<span ttm:role=\"x-bg\" begin=\"${backgroundStart.toTtmlTimestamp()}\" end=\"${backgroundEnd.toTtmlTimestamp()}\">")
                append(line.backgroundWords.toTtmlSpans(background, backgroundStart, backgroundEnd))
                line.backgroundTranslation?.takeIf(String::isNotBlank)?.let {
                    append("<span ttm:role=\"x-translation\" xml:lang=\"zh-CN\">${it.xmlEscape()}</span>")
                }
                append("</span>")
            }
            appendLine("</p>")
        }
        appendLine("    </div>")
        appendLine("  </body>")
        appendLine("</tt>")
    }
}

internal fun Long.toLrcTimestamp(): String {
    val centiseconds = (coerceAtLeast(0L) / 10L)
    val minutes = centiseconds / 6_000L
    val seconds = (centiseconds % 6_000L) / 100L
    val fraction = centiseconds % 100L
    return "[%02d:%02d.%02d]".format(minutes, seconds, fraction)
}

private fun Long.toElrcTimestamp(): String = toLrcTimestamp().removePrefix("[").removeSuffix("]")

private fun Long.toTtmlTimestamp(): String {
    val milliseconds = coerceAtLeast(0L)
    val minutes = milliseconds / 60_000L
    val seconds = (milliseconds % 60_000L) / 1_000L
    val fraction = milliseconds % 1_000L
    return "%02d:%02d.%03d".format(minutes, seconds, fraction)
}

private fun List<LyricTimingLine>.sortedTimed(): List<LyricTimingLine> =
    filter { it.timeMs != null && (it.text.isNotBlank() || !it.backgroundText.isNullOrBlank()) }
        .sortedBy { it.timeMs }

private fun LyricTimingLine.resolvedEnd(nextStart: Long?): Long {
    val primaryEnd = words.maxOfOrNull { it.endMs }
    val backgroundEnd = backgroundEndMs ?: backgroundWords.maxOfOrNull { it.endMs }
    return listOfNotNull(endMs, primaryEnd, backgroundEnd)
        .maxOrNull()
        ?.coerceAtLeast((timeMs ?: 0L) + 1L)
        ?: nextStart?.coerceAtLeast((timeMs ?: 0L) + 1L)
        ?: (timeMs ?: 0L) + 4_000L
}

private fun List<LyricWord>.toElrcTokens(text: String, start: Long, end: Long): String {
    val timedWords = if (isNotEmpty()) this else listOf(LyricWord(text, start, end))
    return buildString {
        timedWords.forEach { word -> append("<${word.startMs.toElrcTimestamp()}>${word.text}") }
        val finalEnd = timedWords.maxOfOrNull { it.endMs } ?: end
        append("<${finalEnd.toElrcTimestamp()}>")
    }
}

private fun List<LyricWord>.toTtmlSpans(text: String, start: Long, end: Long): String {
    val timedWords = if (isNotEmpty()) this else listOf(LyricWord(text, start, end))
    return timedWords.joinToString(separator = "") { word ->
        "<span begin=\"${word.startMs.toTtmlTimestamp()}\" end=\"${word.endMs.toTtmlTimestamp()}\">${word.text.xmlEscape()}</span>"
    }
}

private fun String.xmlEscape(): String = replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

internal fun Long.toTimingDisplay(): String {
    val milliseconds = coerceAtLeast(0L)
    val minutes = milliseconds / 60_000L
    val seconds = (milliseconds % 60_000L) / 1_000L
    val fraction = (milliseconds % 1_000L) / 10L
    return "%02d:%02d.%02d".format(minutes, seconds, fraction)
}

/** Creates editable word cells while retaining spaces in the serialized TTML text. */
internal fun LyricTimingLine.withGeneratedWords(startMs: Long, endMs: Long): LyricTimingLine {
    if (words.isNotEmpty() || text.isBlank()) return this
    val tokens = text.timingTokens()
    if (tokens.isEmpty()) return this
    val safeStart = startMs.coerceAtLeast(0L)
    val safeEnd = endMs.coerceAtLeast(safeStart + tokens.size)
    val span = (safeEnd - safeStart).toDouble() / tokens.size
    return copy(
        words = tokens.mapIndexed { index, token ->
            val wordStart = (safeStart + span * index).toLong()
            val wordEnd = if (index == tokens.lastIndex) safeEnd else (safeStart + span * (index + 1)).toLong()
            LyricWord(token, wordStart, wordEnd.coerceAtLeast(wordStart + 1L))
        }
    )
}

private fun String.timingTokens(): List<String> {
    val tokens = mutableListOf<String>()
    val buffer = StringBuilder()
    fun flush() {
        if (buffer.isNotEmpty()) {
            tokens += buffer.toString()
            buffer.clear()
        }
    }
    for (char in this) {
        when {
            char.isWhitespace() -> {
                buffer.append(char)
                flush()
            }
            Character.UnicodeScript.of(char.code) in setOf(
                Character.UnicodeScript.HAN,
                Character.UnicodeScript.HIRAGANA,
                Character.UnicodeScript.KATAKANA,
                Character.UnicodeScript.HANGUL
            ) -> {
                flush()
                tokens += char.toString()
            }
            else -> buffer.append(char)
        }
    }
    flush()
    return tokens
}
