package com.ella.music.ui.poster

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.primaryEndMs
import java.text.BreakIterator
import java.util.Locale
import kotlin.math.*
import kotlin.random.Random

/** Split visual words without losing the timing of syllables inside an English word. */
internal fun posterLyricWords(line: LyricLine, nextLine: LyricLine? = null): List<LyricLine> {
    val sourceText = line.text.ifBlank { line.backgroundText.orEmpty().ifBlank { "♪" } }
    val sourceWords = line.words.ifEmpty { if (line.text.isBlank()) line.backgroundWords else emptyList() }
    val words = sourceWords.ifEmpty {
        listOf(LyricWord(sourceText, line.timeMs, line.primaryEndMs(nextLine)))
    }
    val text = words.joinToString("") { it.text }
    val ranges = posterWordRanges(text)
    var wordOffset = 0
    val timedRanges = words.map { word ->
        val start = wordOffset
        wordOffset += word.text.length
        Triple(start, wordOffset, word)
    }
    return ranges.map { range ->
        val timed = timedRanges.mapNotNull { (start, end, word) ->
            val from = maxOf(start, range.first)
            val to = minOf(end, range.last + 1)
            if (from >= to) return@mapNotNull null
            val duration = (word.endMs - word.startMs).coerceAtLeast(0L)
            // Count code points rather than UTF-16 units, so supplementary characters don't
            // take twice the share of the source word's duration.
            val length = word.text.codePointCount(0, word.text.length).coerceAtLeast(1)
            fun at(offset: Int) = word.startMs + duration * word.text.codePointCount(0, offset) / length
            LyricWord(text.substring(from, to), at(from - start), at(to - start))
        }
        val readings = line.pronunciationWords.filter { reading ->
            timed.any { it.startMs < reading.endMs && it.endMs > reading.startMs }
        }
        line.copy(
            timeMs = timed.minOf { it.startMs },
            text = text.substring(range), words = timed, endMs = timed.maxOf { it.endMs },
            translation = null,
            pronunciation = readings.joinToString("") { it.text }.ifBlank { null },
            pronunciationWords = readings,
            backgroundText = null, backgroundWords = emptyList(), backgroundTranslation = null,
            backgroundStartMs = null, backgroundEndMs = null
        )
    }
}

private fun posterWordRanges(text: String): List<IntRange> {
    val characters = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
    val ranges = mutableListOf<IntRange>()
    var start = characters.first()
    var groupStart = -1
    var groupEnd = -1
    fun flush() {
        if (groupStart >= 0) ranges += groupStart until groupEnd
        groupStart = -1
    }
    var end = characters.next()
    while (end != BreakIterator.DONE) {
        val part = text.substring(start, end)
        val codePoint = text.codePointAt(start)
        val script = Character.UnicodeScript.of(codePoint)
        val separateCharacter = script == Character.UnicodeScript.HAN ||
            script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA ||
            codePoint >= 0x1F000
        when {
            part.isBlank() -> flush()
            separateCharacter -> { flush(); ranges += start until end }
            part.all { !it.isLetterOrDigit() && it != '\'' && it != '’' } && groupStart < 0 && ranges.isNotEmpty() -> {
                ranges[ranges.lastIndex] = ranges.last().first until end
            }
            else -> { if (groupStart < 0) groupStart = start; groupEnd = end }
        }
        start = end
        end = characters.next()
    }
    flush()
    return ranges
}
