package com.ella.music.ui.player

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.primaryEndMs

/**
 * #781 「逐行歌词逐字化」: synthesize karaoke timings for line-timed lyrics so the player can
 * sweep them like real word-timed rows. Each line's span [timeMs, primaryEndMs) is distributed
 * over display units — Latin text splits at word boundaries, CJK reveals per character — and
 * every unit's duration is weighted by its visible character count.
 */
internal fun List<LyricLine>.withPlayerFauxWordTiming(): List<LyricLine> =
    mapIndexed { index, line ->
        line.withPlayerFauxWordTiming(nextLine = getOrNull(index + 1))
    }

internal fun LyricLine.withPlayerFauxWordTiming(nextLine: LyricLine? = null): LyricLine {
    // Real karaoke (and TTML syllables) keep their authored timings; the opening title card
    // has its own scrub interaction; background-only rows are handled by the backing-vocal path.
    if (hasExplicitWordTiming || isOpeningMetadata) return this
    val units = splitPlayerFauxWordUnits(text)
    if (units.size < 2) return this
    val startMs = timeMs
    val endMs = primaryEndMs(nextLine = nextLine).coerceAtLeast(startMs + 1L)
    val duration = endMs - startMs
    val weights = units.map { unit -> unit.count { !it.isWhitespace() }.coerceAtLeast(1) }
    val totalWeight = weights.sum().coerceAtLeast(1)
    val words = ArrayList<LyricWord>(units.size)
    var elapsedWeight = 0
    units.forEachIndexed { index, unit ->
        val wordStart = startMs + duration * elapsedWeight / totalWeight
        elapsedWeight += weights[index]
        val wordEnd = if (index == units.lastIndex) {
            endMs
        } else {
            (startMs + duration * elapsedWeight / totalWeight).coerceAtLeast(wordStart + 1L)
        }
        words += LyricWord(text = unit, startMs = wordStart, endMs = wordEnd)
    }
    // Synthesized words count as explicit timing so the line renders through the karaoke path
    // instead of the line-timed whole-line alpha.
    return copy(words = words, hasExplicitWordTiming = true)
}

/**
 * Split [text] into consecutive reveal units whose concatenation reproduces the input exactly,
 * including whitespace: Latin runs end at word boundaries (trailing spaces stay on the unit),
 * CJK code points become single-character units, and bare punctuation attaches to the
 * preceding unit so it never flashes alone between characters.
 */
private fun splitPlayerFauxWordUnits(text: String): List<String> {
    if (text.isEmpty()) return emptyList()
    val units = ArrayList<String>()
    val pending = StringBuilder()
    fun flushPending() {
        if (pending.isEmpty()) return
        val segment = pending.toString()
        if (segment.none { it.isLetterOrDigit() }) {
            if (units.isNotEmpty()) {
                pending.clear()
                units[units.lastIndex] = units.last() + segment
            }
            // Leading whitespace stays pending so it merges into the first emitted unit.
            return
        }
        pending.clear()
        units += segment
    }
    var index = 0
    while (index < text.length) {
        val char = text[index]
        when {
            char.isPlayerFauxCjkCharacter() -> {
                flushPending()
                val unit = StringBuilder().append(char)
                index++
                // Keep the spaces behind a CJK glyph inside its unit, matching the Latin rule.
                while (index < text.length && text[index].isWhitespace()) {
                    unit.append(text[index])
                    index++
                }
                units += unit.toString()
            }
            char.isWhitespace() -> {
                pending.append(char)
                index++
            }
            else -> {
                // A new Latin word starts after whitespace: close the previous unit.
                if (pending.isNotEmpty() && pending.last().isWhitespace()) flushPending()
                pending.append(char)
                index++
            }
        }
    }
    flushPending()
    return units
}

/** Same script classes the karaoke renderer treats as per-character CJK units. */
private fun Char.isPlayerFauxCjkCharacter(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.HANGUL_SYLLABLES
}
