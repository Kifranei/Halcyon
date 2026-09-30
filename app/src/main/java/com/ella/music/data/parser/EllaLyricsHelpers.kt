package com.ella.music.data.parser

import android.text.Html
import com.ella.music.data.model.LyricWord

internal val lrcGenericMetaPattern = Regex("""^\[(?!bg:)[A-Za-z][A-Za-z0-9 _\-]*:[^\]]*]$""", RegexOption.IGNORE_CASE)

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
    // Html.fromHtml drops leading/standalone whitespace even for plain text. Timed words
    // rely on those literal spaces to stay aligned with the line shown by the player.
    if ('<' !in this && '&' !in this) this else
    runCatching { Html.fromHtml(this, Html.FROM_HTML_MODE_LEGACY).toString() }
        .getOrElse { this }

internal fun List<LyricWord>.joinLyricText(): String {
    val raw = joinToString("") { it.text }.cleanLyricText()
    if (raw.isBlank() || raw.hasCjk() || raw.contains(' ')) return raw
    return joinLatinLyricUnits()
}

/** A hyphen connects timed sung syllables; it is not a separate display word. */
internal fun List<LyricWord>.joinLatinLyricUnits(): String = buildString {
    var previous = ""
    this@joinLatinLyricUnits.forEach { word ->
        val text = word.text.cleanLyricText()
        if (text.isNotBlank()) {
            if (isNotEmpty() && !previous.endsWith('-') && !text.startsWith('-')) append(' ')
            append(text)
            previous = text
        }
    }
}

internal fun List<LyricWord>.toDisplayWords(lineText: String): List<LyricWord> {
    if (isEmpty() || lineText.isBlank()) return this
    // Line text is trimmed for display. Trim only the OUTER edges of its timed words too;
    // otherwise a CJK final word such as "文 " cannot be found in "中文", and karaoke
    // falls back to a whole-line renderer. Spaces between CJK/Latin tokens stay intact.
    val words = dropWhile { it.text.isBlank() }.dropLastWhile { it.text.isBlank() }
        .let { tokens -> tokens.mapIndexed { index, word ->
            var text = word.text
            if (index == 0) text = text.trimStart()
            if (index == tokens.lastIndex) text = text.trimEnd()
            if (text == word.text) word else word.copy(text = text)
        } }
    val normalized = lineText.cleanLyricText()
    if (normalized.hasCjk()) return words.withSpacing(normalized)
    val existingText = words.joinToString("") { it.text }.cleanLyricText()
    if (existingText == normalized) {
        return words
    }
    // If the line text has no spaces but we have multiple words, the text was likely
    // concatenated from TTML spans without inter-span whitespace (e.g. x-bg spans that
    // are directly adjacent). Don't try token matching — it would collapse all words
    // into a single blob. Return the individual words directly; they already have
    // proper per-word text and timing.
    if (!normalized.contains(' ') && words.size > 1) return words
    val tokens = Regex("""\S+\s*""").findAll(normalized).map { it.value }.toList()
    if (tokens.isEmpty()) return words.withSpacing(normalized)
    val result = mutableListOf<LyricWord>()
    var index = 0
    tokens.forEach { token ->
        if (index >= words.size) return@forEach
        val startIndex = index
        val target = token.trim()
        val builder = StringBuilder()
        var endMs = words[index].endMs
        while (index < words.size && builder.length < target.length) {
            builder.append(words[index].text.trimTimedWordToken())
            endMs = words[index].endMs
            index++
        }
        if (builder.toString() == target) {
            result += words[startIndex].copy(text = token, endMs = endMs)
        }
    }
    val resultText = result.joinToString("") { it.text }.cleanLyricText()
    return if (result.isNotEmpty() && resultText == normalized) {
        result
    } else {
        words.withSpacing(normalized)
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

internal fun Char.isKanjiOrHangul(): Boolean =
    Character.UnicodeBlock.of(this) in setOf(
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
        Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
        Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
        Character.UnicodeBlock.HANGUL_SYLLABLES,
        Character.UnicodeBlock.HANGUL_JAMO,
        Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
    )

internal fun String.isRtlText(): Boolean {
    var rtlCount = 0
    var ltrCount = 0
    var i = 0
    while (i < length) {
        val cp = codePointAt(i)
        when (Character.getDirectionality(cp).toInt()) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT.toInt(),
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC.toInt() -> rtlCount++
            Character.DIRECTIONALITY_LEFT_TO_RIGHT.toInt() -> ltrCount++
        }
        i += Character.charCount(cp)
    }
    return rtlCount > 0 && rtlCount >= ltrCount
}
