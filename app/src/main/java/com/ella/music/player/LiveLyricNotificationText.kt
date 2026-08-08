package com.ella.music.player

import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord

internal const val LIVE_UPDATE_COMPACT_MAX_CODE_POINTS = 7
private const val LIVE_UPDATE_TITLE_MAX_CODE_POINTS = 40
private val liveLyricWhitespace = Regex("\\s+")

/** The lyric strings used by the normal Live Update and its compact status-bar chip. */
internal data class LiveLyricNotificationText(
    val lyric: String,
    val fullLyric: String,
    val compactLyric: String,
    val wordIndex: Int,
    val allowLongCompactLyric: Boolean
)

/**
 * Resolves the display text without changing the parsed lyric line. Word-timed modes use a
 * window around the active word; translation deliberately remains a whole-line fallback because
 * it does not have an independent timestamp list.
 */
internal fun buildLiveLyricNotificationText(
    line: LyricLine,
    mode: Int,
    positionMs: Long
): LiveLyricNotificationText? {
    val original = line.originalLiveLyricSource() ?: return null
    val selected = when (mode) {
        SettingsManager.LIVE_UPDATE_LYRIC_MODE_TRANSLATION -> {
            val translation = firstLiveLyricText(line.translation, line.backgroundTranslation)
            LiveLyricSource(translation ?: original.text, emptyList())
        }

        SettingsManager.LIVE_UPDATE_LYRIC_MODE_PRONUNCIATION -> {
            val pronunciation = firstLiveLyricText(line.pronunciation)
            if (pronunciation == null) {
                LiveLyricSource(original.text, emptyList())
            } else {
                LiveLyricSource(pronunciation, line.pronunciationWords)
            }
        }

        else -> original
    }

    val normalizedWords = selected.words.normalizedLiveLyricWordsOrNull()
    val wordIndex = if (normalizedWords == null) {
        -1
    } else {
        currentLiveLyricWordIndex(selected.words, positionMs)
    }
    // currentLyricIndexAt intentionally remains on the previous line until the next line starts.
    // When the word timeline has a short gap at that point, showing selected.text would briefly
    // expand the Live Update from a moving word window to the entire sentence. Hold the nearest
    // timed-word window instead; the actual wordIndex remains -1 so this gap does not look like a
    // new sung word to callers that track it.
    val displayWordIndex = if (normalizedWords != null) {
        wordIndex.takeIf { it in normalizedWords.indices }
            ?: nearestLiveLyricWordIndex(selected.words, positionMs)
    } else {
        -1
    }
    val displayLyric = if (normalizedWords != null && displayWordIndex in normalizedWords.indices) {
        buildLiveLyricWindow(
            words = normalizedWords,
            currentWordIndex = displayWordIndex,
            maxCodePoints = LIVE_UPDATE_TITLE_MAX_CODE_POINTS
        )
    } else {
        selected.text
    }
    val hasTimedDisplayWord = normalizedWords != null && displayWordIndex in normalizedWords.indices
    val compactLyric = if (hasTimedDisplayWord) {
        // The compact chip is showing one current word, not an arbitrary long sentence. Keep the
        // complete token when it is longer than the chip's normal seven-code-point budget (for
        // example, "feelings"), otherwise the word becomes misleadingly "feelin…".
        compactLiveLyricText(normalizedWords[displayWordIndex], preserveLongToken = true)
    } else {
        compactLiveLyricText(selected.text)
    }

    return LiveLyricNotificationText(
        lyric = displayLyric,
        fullLyric = selected.text,
        compactLyric = compactLyric,
        wordIndex = wordIndex,
        allowLongCompactLyric = hasTimedDisplayWord
    )
}

internal fun buildLiveLyricSecondaryText(line: LyricLine, mode: Int): String? = when (mode) {
    SettingsManager.LIVE_UPDATE_LYRIC_SECONDARY_MODE_TRANSLATION ->
        firstLiveLyricText(line.translation, line.backgroundTranslation)
    SettingsManager.LIVE_UPDATE_LYRIC_SECONDARY_MODE_PRONUNCIATION ->
        firstLiveLyricText(line.pronunciation)
    else -> null
}

/** Returns the active word, or -1 before/after a word and in a timing gap. */
internal fun currentLiveLyricWordIndex(
    words: List<LyricWord>,
    positionMs: Long
): Int {
    val position = positionMs
    return words.withIndex()
        .asSequence()
        .filter { (_, word) -> position >= word.startMs && position < word.endMs }
        // A few TTML files overlap words. The word that started most recently is the one the
        // reader sees as current, while a gap still correctly has no active word.
        .maxByOrNull { (_, word) -> word.startMs }
        ?.index
        ?: -1
}

private fun nearestLiveLyricWordIndex(
    words: List<LyricWord>,
    positionMs: Long
): Int {
    val previousWord = words.indexOfLast { positionMs >= it.endMs }
    if (previousWord >= 0) return previousWord
    return words.indexOfFirst { positionMs < it.startMs }
}

/**
 * Builds a bounded, code-point-safe window around [currentWordIndex]. The current word is always
 * retained; ellipses only replace context outside the window.
 */
internal fun buildLiveLyricWindow(
    words: List<String>,
    currentWordIndex: Int,
    maxCodePoints: Int = LIVE_UPDATE_TITLE_MAX_CODE_POINTS
): String {
    val tokens = words.map(::normalizeLiveLyricText).filter(String::isNotBlank)
    if (tokens.isEmpty()) return ""
    if (currentWordIndex !in tokens.indices) return joinLiveLyricWords(tokens)

    val maxLength = maxCodePoints.coerceAtLeast(1)
    var start = currentWordIndex
    var end = currentWordIndex

    while (true) {
        val leftText = if (start > 0) {
            formatLiveLyricWindow(tokens, start - 1, end)
        } else {
            null
        }
        val rightText = if (end < tokens.lastIndex) {
            formatLiveLyricWindow(tokens, start, end + 1)
        } else {
            null
        }
        val leftFits = leftText != null && liveLyricCodePointCount(leftText) <= maxLength
        val rightFits = rightText != null && liveLyricCodePointCount(rightText) <= maxLength
        if (!leftFits && !rightFits) break

        when {
            leftFits && !rightFits -> start--
            rightFits && !leftFits -> end++
            // Keep the current word near the center when both sides fit. This also makes the
            // first and last words naturally bias the window toward the available side.
            currentWordIndex - start <= end - currentWordIndex -> start--
            else -> end++
        }
    }

    return formatLiveLyricWindow(tokens, start, end)
}

/** Keeps a compact chip short without splitting a surrogate pair. */
internal fun compactLiveLyricText(
    text: String,
    preserveLongToken: Boolean = false
): String {
    val normalized = normalizeLiveLyricText(text)
    if (normalized.isBlank()) return ""
    if (liveLyricCodePointCount(normalized) <= LIVE_UPDATE_COMPACT_MAX_CODE_POINTS) {
        return normalized
    }
    if (preserveLongToken && normalized.none(Char::isWhitespace)) return normalized
    val visibleCount = (LIVE_UPDATE_COMPACT_MAX_CODE_POINTS - 1).coerceAtLeast(1)
    return normalized.takeCodePoints(visibleCount) + "…"
}

private data class LiveLyricSource(
    val text: String,
    val words: List<LyricWord>
)

private fun LyricLine.originalLiveLyricSource(): LiveLyricSource? {
    val mainText = firstLiveLyricText(text)
    if (mainText != null) return LiveLyricSource(mainText, words)
    val background = firstLiveLyricText(backgroundText) ?: return null
    return LiveLyricSource(background, backgroundWords)
}

private fun firstLiveLyricText(vararg values: String?): String? =
    values.asSequence()
        .mapNotNull { it?.let(::normalizeLiveLyricText) }
        .firstOrNull(String::isNotBlank)

private fun List<LyricWord>.normalizedLiveLyricWordsOrNull(): List<String>? {
    if (isEmpty()) return null
    if (any { it.endMs <= it.startMs }) return null
    val normalized = map { normalizeLiveLyricText(it.text) }
    return normalized.takeIf { it.size == size && it.all(String::isNotBlank) }
}

private fun normalizeLiveLyricText(text: String): String =
    text.replace(liveLyricWhitespace, " ").trim()

private fun formatLiveLyricWindow(tokens: List<String>, start: Int, end: Int): String = buildString {
    if (start > 0) append('…')
    append(joinLiveLyricWords(tokens.subList(start, end + 1)))
    if (end < tokens.lastIndex) append('…')
}

private fun joinLiveLyricWords(words: List<String>): String = buildString {
    words.forEach { word ->
        val token = normalizeLiveLyricText(word)
        if (token.isBlank()) return@forEach
        if (isNotEmpty() && needsLiveLyricSeparator(toString().lastCodePoint(), token.firstCodePoint())) {
            append(' ')
        }
        append(token)
    }
}

private fun needsLiveLyricSeparator(previousCodePoint: Int, nextCodePoint: Int): Boolean {
    if (isCjkCodePoint(previousCodePoint) || isCjkCodePoint(nextCodePoint)) return false
    if (isClosingPunctuation(nextCodePoint) || isOpeningPunctuation(previousCodePoint)) return false
    if (previousCodePoint == '\''.code || nextCodePoint == '\''.code) return false
    if (previousCodePoint == '-'.code || nextCodePoint == '-'.code) return false
    return true
}

private fun isCjkCodePoint(codePoint: Int): Boolean =
    codePoint in 0x2E80..0x9FFF ||
        codePoint in 0xAC00..0xD7AF ||
        codePoint in 0x3040..0x30FF ||
        codePoint in 0x20000..0x323AF

private fun isClosingPunctuation(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
    Character.END_PUNCTUATION.toInt(),
    Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt() -> true
    else -> false
}

private fun isOpeningPunctuation(codePoint: Int): Boolean = when (Character.getType(codePoint)) {
    Character.START_PUNCTUATION.toInt(),
    Character.INITIAL_QUOTE_PUNCTUATION.toInt() -> true
    else -> false
}

private fun String.firstCodePoint(): Int = codePointAt(0)

private fun String.lastCodePoint(): Int = codePointBefore(length)

private fun String.takeCodePoints(count: Int): String {
    if (count <= 0 || isEmpty()) return ""
    val end = offsetByCodePoints(0, count.coerceAtMost(codePointCount(0, length)))
    return substring(0, end)
}

private fun liveLyricCodePointCount(text: String): Int =
    text.codePointCount(0, text.length)
