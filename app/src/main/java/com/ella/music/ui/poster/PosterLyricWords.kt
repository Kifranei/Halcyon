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

internal data class PosterOrbitFrame(val x: Float, val y: Float, val depth: Float, val rotationY: Float) {
    val scale: Float get() = 0.68f + depth * 0.32f
    val alpha: Float get() = 0.22f + depth * 0.78f
}

internal const val POSTER_ORBIT_AXIS_DEGREES = 45f

/** Rotate positions around the shared scene center; keep glyphs upright and readable. */
internal fun PosterOrbitFrame.onPosterAxis(): PosterOrbitFrame {
    val radians = POSTER_ORBIT_AXIS_DEGREES * PI.toFloat() / 180f
    return copy(x = x * cos(radians) - y * sin(radians),
        y = x * sin(radians) + y * cos(radians))
}

internal fun posterOrbitDimensions(width: Float, height: Float, wordWidth: Float, wordHeight: Float): Pair<Float, Float> {
    // At 45 degrees both projected extents are (radius + height/2)/sqrt(2).
    val available = minOf((width - wordWidth) / 2f, (height - wordHeight) / 2f).coerceAtLeast(0f)
    val extent = available * sqrt(2f)
    return extent * 0.36f to extent * 1.28f
}

/** A helix on a cylinder around the same vertical axis drawn by the scene backdrop. */
internal fun posterOrbitFrame(index: Int, count: Int, seconds: Float, radius: Float, height: Float): PosterOrbitFrame {
    val angle = seconds * 0.48f + index * (2f * PI.toFloat() / count.coerceIn(4, 8))
    val depth = (cos(angle) + 1f) / 2f
    return PosterOrbitFrame(
        x = sin(angle) * radius,
        y = if (count <= 1) 0f else (index.toFloat() / (count - 1) - 0.5f) * height,
        depth = depth, rotationY = sin(angle) * 48f
    )
}

internal data class PosterWordSize(val width: Float, val height: Float)
internal data class PosterScatteredWord(val x: Float, val y: Float, val rotation: Float, val phase: Float)

/** Seeded placement uses measured glyph sizes and accounts for rotation and subsequent drift. */
internal fun posterScatteredWords(
    sizes: List<PosterWordSize>, width: Float, height: Float, seed: Int, drift: Float
): List<PosterScatteredWord> {
    if (width <= 0f || height <= 0f) return emptyList()
    val random = Random(seed)
    data class Occupied(val x: Float, val y: Float, val halfWidth: Float, val halfHeight: Float)
    val occupied = mutableListOf<Occupied>()
    val placed = arrayOfNulls<PosterScatteredWord>(sizes.size)
    // Give the longest words room first; the resulting list still follows the sung order.
    sizes.indices.sortedByDescending { sizes[it].width * sizes[it].height }.forEach { index ->
        val word = sizes[index]
        val rotation = (random.nextFloat() - 0.5f) * 28f
        val radians = rotation * PI.toFloat() / 180f
        val halfWidth = (abs(cos(radians)) * word.width + abs(sin(radians)) * word.height) / 2f + drift
        val halfHeight = (abs(sin(radians)) * word.width + abs(cos(radians)) * word.height) / 2f + drift
        var best: Occupied? = null
        var bestScore = Float.POSITIVE_INFINITY
        repeat(240) {
            val candidate = Occupied(
                halfWidth + random.nextFloat() * (width - 2f * halfWidth).coerceAtLeast(0f),
                halfHeight + random.nextFloat() * (height - 2f * halfHeight).coerceAtLeast(0f),
                halfWidth, halfHeight
            )
            val score = occupied.sumOf { other ->
                (maxOf(0f, candidate.halfWidth + other.halfWidth - abs(candidate.x - other.x)) *
                    maxOf(0f, candidate.halfHeight + other.halfHeight - abs(candidate.y - other.y))).toDouble()
            }.toFloat()
            if (score < bestScore) { best = candidate; bestScore = score }
        }
        val selected = checkNotNull(best)
        occupied += selected
        placed[index] = PosterScatteredWord(selected.x, selected.y, rotation, random.nextFloat() * 2f * PI.toFloat())
    }
    return placed.map { checkNotNull(it) }
}
