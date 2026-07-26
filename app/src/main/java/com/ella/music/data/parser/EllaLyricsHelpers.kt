package com.ella.music.data.parser

import android.text.Html
import com.ella.music.data.model.LyricWord

internal val lrcGenericMetaPattern = Regex("""^\[[A-Za-z][A-Za-z0-9 _\-]*:[^\]]*]$""")

internal fun String.parseFlexibleTime(): Int {
    val value = trim().replace(',', '.')
    if (value.isBlank()) return 0

    if (value.endsWith("ms", ignoreCase = true)) return value.dropLast(2).toDoubleOrNull()?.toInt() ?: 0
    if (value.endsWith("s", ignoreCase = true)) return ((value.dropLast(1).toDoubleOrNull() ?: 0.0) * 1000).toInt()

    val parts = value.split(":")
    fun secondsMs(part: String): Int {
        val pieces = part.split(".")
        val seconds = pieces.getOrNull(0)?.toIntOrNull()?.times(1000) ?: 0
        val msRaw = pieces.getOrNull(1).orEmpty()
        val ms = when (msRaw.length) {
            0 -> 0
            1 -> msRaw.toIntOrNull()?.times(100) ?: 0
            2 -> msRaw.toIntOrNull()?.times(10) ?: 0
            else -> msRaw.take(3).toIntOrNull() ?: 0
        }
        return seconds + ms
    }
    return when (parts.size) {
        1 -> secondsMs(parts[0])
        2 -> (parts[0].toIntOrNull() ?: 0) * 60_000 + secondsMs(parts[1])
        3 -> (parts[0].toIntOrNull() ?: 0) * 3_600_000 + (parts[1].toIntOrNull() ?: 0) * 60_000 + secondsMs(parts[2])
        else -> 0
    }
}

internal fun String.cleanLyricText(): String =
    decodeHtmlCompat()
        .replace(Regex("""[ \t\r\n]+"""), " ")
        .trim()

internal fun String.cleanLyricSecondaryText(): String =
    decodeHtmlCompat()
        .replace(Regex("""[ \t\r\n]+"""), " ")
        .trim()

internal fun String.isIgnorableLyricText(): Boolean =
    isBlank() || isMusicSymbolOnly() || EllaLyricsParser.isPlaceholderOnlyLine(this) || lrcGenericMetaPattern.matches(cleanLyricText())

internal fun String.decodeHtmlCompat(): String =
    runCatching { Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString() }
        .getOrElse { this }

internal fun List<LyricWord>.joinLyricText(): String {
    val raw = joinToString("") { it.text }.cleanLyricText()
    if (raw.isBlank() || raw.hasCjk() || raw.contains(' ')) return raw
    return map { it.text.cleanLyricText() }.filter { it.isNotBlank() }.joinToString(" ")
}

internal fun List<LyricWord>.toDisplayWords(lineText: String): List<LyricWord> {
    if (isEmpty() || lineText.isBlank()) return this
    val normalized = lineText.cleanLyricText()
    if (normalized.hasCjk()) return withSpacing(normalized)
    val existingText = joinToString("") { it.text }.cleanLyricText()
    if (existingText == normalized) {
        return mapIndexed { index, word ->
            if (index == lastIndex) word.copy(text = word.text.trimEnd()) else word
        }
    }
    // If the line text has no spaces but we have multiple words, the text was likely
    // concatenated from TTML spans without inter-span whitespace (e.g. x-bg spans that
    // are directly adjacent). Don't try token matching — it would collapse all words
    // into a single blob. Return the individual words directly; they already have
    // proper per-word text and timing.
    if (!normalized.contains(' ') && size > 1) return this
    val tokens = Regex("""\S+\s*""").findAll(normalized).map { it.value }.toList()
    if (tokens.isEmpty()) return withSpacing(normalized)
    val result = mutableListOf<LyricWord>()
    var index = 0
    tokens.forEach { token ->
        if (index >= size) return@forEach
        val startIndex = index
        val target = token.trim()
        val builder = StringBuilder()
        var endMs = this[index].endMs
        while (index < size && builder.length < target.length) {
            builder.append(this[index].text.trimTimedWordToken())
            endMs = this[index].endMs
            index++
        }
        if (builder.toString() == target) {
            result += this[startIndex].copy(text = token, endMs = endMs)
        }
    }
    val resultText = result.joinToString("") { it.text }.cleanLyricText()
    return if (result.isNotEmpty() && resultText == normalized) {
        result
    } else {
        withSpacing(normalized)
    }
}

private fun List<LyricWord>.withSpacing(lineText: String): List<LyricWord> {
    var cursor = 0
    return mapIndexed { index, word ->
        val start = lineText.indexOf(word.text, cursor)
        if (start < 0) return@mapIndexed word
        val end = start + word.text.length
        val next = getOrNull(index + 1)?.text
        val nextStart = if (next != null) lineText.indexOf(next, end) else -1
        val suffix = when {
            nextStart > end -> lineText.substring(end, nextStart)
            next == null && end < lineText.length -> lineText.substring(end)
            else -> ""
        }
        cursor = end + suffix.length
        word.copy(text = word.text + suffix)
    }
}

private fun String.trimTimedWordToken(): String = trim {
    it.isWhitespace() || it == '\u00A0' || it == '\u200B' || it == '\u2060'
}

internal fun estimateDuration(text: String): Long =
    (text.cleanLyricText().length * 150L).coerceIn(180L, 2_200L)

internal fun String.isMusicSymbolOnly(): Boolean {
    val content = trim()
    if (content.isBlank()) return true
    return content.all { char ->
        char.isWhitespace() ||
            char in setOf('♪', '♫', '♬', '♩', '♭', '♯', '♮', '☆', '★', '·', '.', '。', '…') ||
            Character.UnicodeBlock.of(char) == Character.UnicodeBlock.MUSICAL_SYMBOLS
    }
}

internal fun String.hasCjk(): Boolean =
    any { it.isCjkChar() }

internal fun Char.isCjkChar(): Boolean =
    Character.UnicodeBlock.of(this) in setOf(
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
        Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
        Character.UnicodeBlock.HIRAGANA,
        Character.UnicodeBlock.KATAKANA,
        Character.UnicodeBlock.HANGUL_SYLLABLES
    )
