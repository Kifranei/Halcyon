package com.ella.music.ui.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricWord
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin

internal fun isInlineRubyPronunciation(text: String): Boolean {
    val compact = text.filterNot { it.isWhitespace() }
    if (compact.isEmpty()) return false
    if (compact.any { it.isAppleMusicLatinLetter() }) return false
    return compact.any { it.isAppleMusicCjkIdeograph() || it.isAppleMusicKana() }
}

private fun Char.isAppleMusicKana(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA
}

internal fun appleMusicKaraokeLiftPx(
    wordLiftEnabled: Boolean,
    textSizePx: Float,
    progress: Float
): Float = if (wordLiftEnabled) {
    maxOf(textSizePx * 0.06f, 5f) * progress
} else {
    0f
}

@Composable
internal fun TimedLyricText(
    text: String,
    words: List<LyricWord>,
    positionMs: Long,
    active: Boolean,
    style: TextStyle,
    contentColor: Color,
    wordLiftEnabled: Boolean,
    sustainThresholdMs: Int = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS,
    singleLine: Boolean = false,
    statusBarMarquee: Boolean = false,
    pronunciation: String = "",
    pronunciationWords: List<LyricWord> = emptyList(),
    rubyStyle: TextStyle? = null,
    modifier: Modifier = Modifier
) {
    // TTML may encode the blank before a word as part of that word. Move it to the prior
    // karaoke unit before wrapping so every v1 line, including wrapped continuations, starts
    // at the same left edge. Right-aligned v2 rows are visually tolerant of this, but v1 is not.
    // wordLiftEnabled controls per-word vertical lift only; timed karaoke fill still renders.
    val timedWords = remember(text, words, sustainThresholdMs) {
        words.moveLeadingSpacesToPreviousWord().toAppleMusicRenderWords(text, sustainThresholdMs)
    }
    val rubies = remember(timedWords, pronunciation, pronunciationWords) {
        rubiesForTimedWords(
            words = timedWords.map { it.word },
            pronunciationWords = pronunciationWords,
            pronunciation = pronunciation
        )
    }
    if (timedWords.isEmpty()) {
        BasicText(
            text = text,
            style = style,
            maxLines = if (singleLine) 1 else Int.MAX_VALUE,
            softWrap = !singleLine,
            overflow = TextOverflow.Clip,
            modifier = modifier.then(if (singleLine && statusBarMarquee) Modifier.basicMarquee() else Modifier)
        )
        return
    }
    // Keep the timed units as individual layout children. This is the same important distinction
    // as the smooth renderer: a long timed line breaks between singable units, not at arbitrary
    // glyphs, so highlighted and dim lines retain identical visual rows.
    val horizontalArrangement = when (style.textAlign) {
        TextAlign.End -> Arrangement.End
        TextAlign.Center -> Arrangement.Center
        else -> Arrangement.Start
    }
    val content: @Composable () -> Unit = {
        timedWords.forEachIndexed { index, renderWord ->
            AppleMusicKaraokeWord(
                renderWord = renderWord,
                positionMs = positionMs,
                active = active,
                baseStyle = style,
                contentColor = contentColor,
                wordLiftEnabled = wordLiftEnabled,
                ruby = rubies.getOrNull(index).orEmpty(),
                rubyStyle = rubyStyle
            )
        }
    }
    if (singleLine) {
        Row(
            modifier = modifier.then(if (statusBarMarquee) Modifier.basicMarquee() else Modifier),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.Bottom
        ) {
            content()
        }
    } else {
        // FlowRow measures each visual row independently. With centered/right-aligned lyrics it
        // therefore lets wrapped rows acquire a different origin than the first row (especially
        // visible for a translation below a long English line). Lay rows out ourselves against
        // the full line width so every row shares the exact same alignment anchor.
        AppleMusicTimedWordRows(
            textAlign = style.textAlign,
            modifier = modifier
        ) {
            content()
        }
    }
}

@Composable
private fun AppleMusicTimedWordRows(
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Layout(
        content = content,
        modifier = modifier
    ) { measurables, constraints ->
        val availableWidth = constraints.maxWidth
            .takeUnless { it == androidx.compose.ui.unit.Constraints.Infinity }
            ?: measurables.sumOf { it.maxIntrinsicWidth(constraints.maxHeight) }
        val childConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxWidth = availableWidth)
        val rows = mutableListOf<MutableList<androidx.compose.ui.layout.Placeable>>()
        val rowWidths = mutableListOf<Int>()
        val rowHeights = mutableListOf<Int>()

        measurables.forEach { measurable ->
            val placeable = measurable.measure(childConstraints)
            val rowIndex = rows.lastIndex
            val currentWidth = rowWidths.getOrElse(rowIndex) { 0 }
            val shouldWrap = rowIndex >= 0 && currentWidth > 0 && currentWidth + placeable.width > availableWidth
            if (shouldWrap) {
                rows += mutableListOf(placeable)
                rowWidths += placeable.width
                rowHeights += placeable.height
            } else if (rowIndex >= 0) {
                rows[rowIndex] += placeable
                rowWidths[rowIndex] = currentWidth + placeable.width
                rowHeights[rowIndex] = maxOf(rowHeights[rowIndex], placeable.height)
            } else {
                rows += mutableListOf(placeable)
                rowWidths += placeable.width
                rowHeights += placeable.height
            }
        }

        val layoutWidth = availableWidth.coerceIn(constraints.minWidth, constraints.maxWidth)
        val layoutHeight = rowHeights.sum().coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(layoutWidth, layoutHeight) {
            var y = 0
            rows.indices.forEach { rowIndex ->
                val rowWidth = rowWidths[rowIndex]
                var x = when (textAlign) {
                    TextAlign.End -> (layoutWidth - rowWidth).coerceAtLeast(0)
                    TextAlign.Center -> ((layoutWidth - rowWidth) / 2).coerceAtLeast(0)
                    else -> 0
                }
                rows[rowIndex].forEach { placeable ->
                    // Bottom-align so furigana grows upward instead of dropping the kanji.
                    placeable.placeRelative(x, y + rowHeights[rowIndex] - placeable.height)
                    x += placeable.width
                }
                y += rowHeights[rowIndex]
            }
        }
    }
}

@Composable
private fun AppleMusicKaraokeWord(
    renderWord: AppleMusicRenderWord,
    positionMs: Long,
    active: Boolean,
    baseStyle: TextStyle,
    contentColor: Color,
    wordLiftEnabled: Boolean,
    ruby: String = "",
    rubyStyle: TextStyle? = null
 ) {
    val word = renderWord.word
    val progress = if (active) ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    else 0f
    val bright = contentColor.copy(alpha = baseStyle.color.alpha)
    val dim = contentColor.copy(alpha = baseStyle.color.alpha * 0.36f)
    val sustainGlow = renderWord.sustainGlowAlpha(positionMs, active)
    val textSizePx = with(LocalDensity.current) { baseStyle.fontSize.toPx() }
    // The reference renderer moves each word independently by 6% of the text size (at least
    // 5 px), then adds only a 3% bottom-anchored scale during the held-note phase. Keeping the
    // transform on the word rather than the whole line is what creates the floating vocal feel.
    val liftPx = appleMusicKaraokeLiftPx(wordLiftEnabled, textSizePx, progress)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            translationY = -liftPx
            // Keep the glyph box stable during a held note. Pulsing scale changes are perceived
            // as character jitter on the desktop overlay, especially with long TTML spans.
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
    ) {
        if (ruby.isNotBlank() && rubyStyle != null) {
            BasicText(
                text = ruby,
                style = rubyStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    Box {
        val glowShadow = sustainGlow.takeIf { it > 0f }?.let { glowAlpha ->
            Shadow(
                color = contentColor.copy(alpha = baseStyle.color.alpha * glowAlpha),
                offset = Offset.Zero,
                blurRadius = 10f * glowAlpha
            )
        }
        when {
            progress <= 0f -> BasicText(text = word.text, style = baseStyle.copy(color = dim))
            progress >= 1f -> BasicText(
                text = word.text,
                style = baseStyle.copy(color = bright, shadow = glowShadow)
            )
            else -> {
                BasicText(text = word.text, style = baseStyle.copy(color = dim))
                val featherStart = (progress - 0.15f).coerceAtLeast(0f)
                BasicText(
                    text = word.text,
                    style = baseStyle.copy(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to bright,
                                featherStart to bright,
                                progress to Color.Transparent,
                                1f to Color.Transparent
                            )
                        ),
                        // The glow belongs to the primary karaoke layer, matching ConePlayer's
                        // TextPaint shadow. Attaching it to the narrow sheen made the halo look
                        // like a hard edge and disappear at the start of a held note.
                        shadow = glowShadow
                    )
                )
                // A narrow material sheen follows the karaoke edge. Long-held words strengthen
                // that band and add a restrained halo; ordinary words keep the feathered fill
                // without inheriting a permanent outline around the entire active line.
                val sheenStart = (progress - 0.20f).coerceAtLeast(0f)
                val sheenPeak = (progress - 0.055f).coerceIn(sheenStart, progress)
                val sheenEnd = (progress + 0.045f).coerceAtMost(1f)
                val sheenAlpha = (0.20f + sustainGlow * 0.42f) * baseStyle.color.alpha
                BasicText(
                    text = word.text,
                    style = baseStyle.copy(
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to Color.Transparent,
                                sheenStart to Color.Transparent,
                                sheenPeak to contentColor.copy(alpha = sheenAlpha),
                                sheenEnd to Color.Transparent,
                                1f to Color.Transparent
                            )
                        )
                    )
                )
            }
        }
    }
    }
}

internal fun rubiesForTimedWords(
    words: List<LyricWord>,
    pronunciationWords: List<LyricWord>,
    pronunciation: String
): List<String> {
    if (words.isEmpty()) return emptyList()
    val blanks = List(words.size) { "" }
    val rubyWords = pronunciationWords
        .map { it.copy(text = it.text.trim()) }
        .filter { it.text.isNotBlank() && it.endMs > it.startMs }
    val rubyText = pronunciation.trim()
    if (rubyWords.isEmpty() && rubyText.isBlank()) return blanks

    if (rubyWords.isNotEmpty()) {
        val spansAllWords = rubyWords.size == 1 &&
            words.size > 1 &&
            words.all { word -> timedRangesOverlap(word, rubyWords.first()) }
        if (spansAllWords) {
            return attachRubyByCorrespondence(words, rubyWords.first().text)
        }
        val assigned = assignRubySpansToWords(words, rubyWords)
        if (assigned.any { it.isNotBlank() }) return assigned
        return attachRubyByCorrespondence(words, rubyWords.joinToString("") { it.text })
    }
    return attachRubyByCorrespondence(words, rubyText)
}

internal fun assignRubySpansToWords(
    words: List<LyricWord>,
    rubyWords: List<LyricWord>
): List<String> {
    if (rubyWords.size == words.size) return rubyWords.map { it.text }
    val result = MutableList(words.size) { "" }
    val usedWords = BooleanArray(words.size)
    rubyWords.forEach { ruby ->
        val match = words.indices
            .filter { index -> !usedWords[index] }
            .maxWithOrNull(
                compareBy<Int> { overlapMs(words[it], ruby) }
                    .thenBy { -kotlin.math.abs(words[it].startMs - ruby.startMs) }
            )
            ?.takeIf { index ->
                overlapMs(words[index], ruby) > 0L ||
                    kotlin.math.abs(words[index].startMs - ruby.startMs) <= 25L
            }
        if (match != null) {
            usedWords[match] = true
            result[match] = ruby.text
        }
    }
    return result
}

internal fun attachRubyByCorrespondence(words: List<LyricWord>, reading: String): List<String> {
    if (words.isEmpty()) return emptyList()
    val readingChars = reading.filterNot { it.isWhitespace() }.toList()
    if (readingChars.isEmpty()) return List(words.size) { "" }

    val charToWord = mutableListOf<Int>()
    val surface = buildString {
        words.forEachIndexed { index, word ->
            word.text.forEach { character ->
                append(character)
                charToWord += index
            }
        }
    }
    val rubyByWord = MutableList(words.size) { StringBuilder() }
    var surfaceIndex = 0
    var readingIndex = 0
    while (surfaceIndex < surface.length) {
        val character = surface[surfaceIndex]
        when {
            character.isWhitespace() -> surfaceIndex++
            character.isAppleMusicCjkIdeograph() -> {
                var runEnd = surfaceIndex + 1
                while (runEnd < surface.length && surface[runEnd].isAppleMusicCjkIdeograph()) {
                    runEnd++
                }
                val consumed = readingConsumedForKanjiRun(
                    rest = surface.substring(runEnd),
                    reading = readingChars,
                    readingIndex = readingIndex
                )
                val rubyEnd = (readingIndex + consumed).coerceAtMost(readingChars.size)
                val runLength = runEnd - surfaceIndex
                val pieces = splitReadingAcrossKanji(
                    reading = readingChars.subList(readingIndex, rubyEnd),
                    kanjiCount = runLength
                )
                pieces.forEachIndexed { offset, piece ->
                    if (piece.isNotEmpty()) {
                        rubyByWord[charToWord[surfaceIndex + offset]].append(piece)
                    }
                }
                readingIndex = rubyEnd
                surfaceIndex = runEnd
            }
            character.isAppleMusicKana() || character.isAppleMusicLatinLetter() || character.isDigit() -> {
                if (readingIndex < readingChars.size && kanaEquals(character, readingChars[readingIndex])) {
                    readingIndex++
                }
                surfaceIndex++
            }
            else -> {
                if (readingIndex < readingChars.size && character == readingChars[readingIndex]) {
                    readingIndex++
                }
                surfaceIndex++
            }
        }
    }
    if (readingIndex < readingChars.size) {
        val leftover = readingChars.subList(readingIndex, readingChars.size).joinToString("")
        val target = rubyByWord.indices.lastOrNull { rubyByWord[it].isNotEmpty() }
            ?: words.indexOfLast { word -> word.text.any { it.isAppleMusicCjkIdeograph() } }
                .takeIf { it >= 0 }
            ?: 0
        rubyByWord[target].append(leftover)
    }
    return rubyByWord.map { it.toString() }
}

private fun readingConsumedForKanjiRun(
    rest: String,
    reading: List<Char>,
    readingIndex: Int
): Int {
    val remaining = reading.size - readingIndex
    if (remaining <= 0) return 0
    val laterKanjiRuns = countIdeographRuns(rest)
    val nextAnchor = rest.firstOrNull { character ->
        character.isAppleMusicKana() || character.isAppleMusicLatinLetter() || character.isDigit()
    }
    if (nextAnchor == null) {
        return if (laterKanjiRuns > 0) {
            (remaining - laterKanjiRuns).coerceAtLeast(1).coerceAtMost(remaining)
        } else {
            remaining
        }
    }
    val anchorAt = reading.subList(readingIndex, reading.size).indexOfFirst { kanaEquals(it, nextAnchor) }
    if (anchorAt >= 0) return anchorAt
    if (laterKanjiRuns <= 0) return remaining
    val keep = laterKanjiRuns.coerceAtMost(remaining - 1)
    return (remaining - keep).coerceAtLeast(1)
}

private fun countIdeographRuns(text: String): Int {
    var count = 0
    var inRun = false
    text.forEach { character ->
        val ideograph = character.isAppleMusicCjkIdeograph()
        if (ideograph && !inRun) count++
        inRun = ideograph
    }
    return count
}

private fun splitReadingAcrossKanji(reading: List<Char>, kanjiCount: Int): List<String> {
    if (kanjiCount <= 0) return emptyList()
    if (kanjiCount == 1) return listOf(reading.joinToString(""))
    if (reading.isEmpty()) return List(kanjiCount) { "" }
    val pieces = MutableList(kanjiCount) { StringBuilder() }
    val guaranteed = minOf(kanjiCount, reading.size)
    repeat(guaranteed) { index -> pieces[index].append(reading[index]) }
    if (reading.size > guaranteed) {
        pieces[kanjiCount - 1].append(reading.subList(guaranteed, reading.size).joinToString(""))
    }
    return pieces.map { it.toString() }
}

private fun kanaEquals(first: Char, second: Char): Boolean {
    fun fold(character: Char): Char {
        if (character in '\u30A1'..'\u30F6') return (character.code - 0x60).toChar()
        return character
    }
    return fold(first) == fold(second)
}

private fun timedRangesOverlap(first: LyricWord, second: LyricWord): Boolean =
    minOf(first.endMs, second.endMs) > maxOf(first.startMs, second.startMs)

private fun overlapMs(first: LyricWord, second: LyricWord): Long =
    (minOf(first.endMs, second.endMs) - maxOf(first.startMs, second.startMs)).coerceAtLeast(0L)

private fun Char.isAppleMusicCjkIdeograph(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
}

private fun AppleMusicRenderWord.sustainGlowAlpha(positionMs: Long, active: Boolean): Float {
    val sustainEndMs = sustainEndMs ?: return 0f
    if (!active || positionMs !in word.startMs until sustainEndMs) return 0f
    val duration = sustainEndMs - word.startMs
    val elapsed = positionMs - word.startMs
    // ConePlayer starts the held-note envelope at the beginning of the marked word; it does not
    // wait for a separate attack delay. This is why its halo is already visible around the first
    // sung glyph in a long "Oh" rather than appearing halfway through the word.
    val progress = (elapsed.toFloat() / duration.coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    return if (progress < 0.7f) {
        sin((progress / 0.7f) * (PI.toFloat() / 2f))
    } else {
        cos(((progress - 0.7f) / 0.3f) * (PI.toFloat() / 2f))
    }.coerceIn(0f, 1f)
}

private data class AppleMusicRenderWord(
    val word: LyricWord,
    val sustainEndMs: Long? = null
)

private fun List<LyricWord>.toAppleMusicRenderWords(
    lineText: String,
    sustainThresholdMs: Int
): List<AppleMusicRenderWord> {
    if (isEmpty() || lineText.isBlank()) return emptyList()
    val result = mutableListOf<AppleMusicRenderWord>()
    var cursor = 0
    forEachIndexed { index, word ->
        if (word.text.isBlank() || word.endMs <= word.startMs) return@forEachIndexed
        val start = lineText.indexOf(word.text, cursor)
        if (start < 0) return emptyList()
        val end = start + word.text.length
        val nextStart = getOrNull(index + 1)?.text?.let { next -> lineText.indexOf(next, end) } ?: -1
        val suffix = when {
            nextStart > end -> lineText.substring(end, nextStart)
            index == lastIndex && end < lineText.length -> lineText.substring(end)
            else -> ""
        }
        val duration = word.endMs - word.startMs
        val splitForCharacters = word.shouldSplitForAppleMusicCharacters(sustainThresholdMs)
        if (splitForCharacters) {
            val chars = word.text.toCharArray()
            val segmentDuration = duration / chars.size
            chars.forEachIndexed { charIndex, char ->
                val segmentStart = word.startMs + segmentDuration * charIndex
                val segmentEnd = if (charIndex == chars.lastIndex) {
                    word.endMs
                } else {
                    segmentStart + segmentDuration
                }
                result += AppleMusicRenderWord(
                    word = LyricWord(
                        text = char.toString() + if (charIndex == chars.lastIndex) suffix else "",
                        startMs = segmentStart,
                        endMs = segmentEnd
                    ),
                    sustainEndMs = word.endMs
                )
            }
        } else {
            // TTML providers sometimes put a short English phrase in a single timed span.
            // Split it at word boundaries so each word gets its own progressive feather.
            result += AppleMusicRenderWord(word.copy(text = word.text + suffix))
                .splitEnglishPhraseForAppleMusic()
        }
        cursor = end + suffix.length
    }
    return result
}

/**
 * A TTML/LRC provider may put a whole long CJK phrase in one timed span. If that span wraps in
 * the player, a single BasicText child gives every visual row the same progress. Split long
 * timed phrases into character-sized children so wrapped rows can complete from top to bottom.
 */
internal fun LyricWord.shouldSplitForAppleMusicCharacters(
    sustainThresholdMs: Int = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS
): Boolean {
    if (endMs - startMs < sustainThresholdMs.coerceAtLeast(0).toLong() || text.length <= 1) return false
    return text.any { it.isAppleMusicLatinLetter() || it.isAppleMusicCjkCharacter() }
}

private fun Char.isAppleMusicLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private fun Char.isAppleMusicCjkCharacter(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
        block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
        block == Character.UnicodeBlock.HIRAGANA ||
        block == Character.UnicodeBlock.KATAKANA ||
        block == Character.UnicodeBlock.HANGUL_SYLLABLES
}

private fun AppleMusicRenderWord.splitEnglishPhraseForAppleMusic(): List<AppleMusicRenderWord> {
    val sourceText = word.text
    if (!sourceText.any { it in 'a'..'z' || it in 'A'..'Z' } || !sourceText.any(Char::isWhitespace)) {
        return listOf(this)
    }
    val segments = Regex("\\S+\\s*").findAll(sourceText).map { it.value }.toList()
    if (segments.size < 2) return listOf(this)

    val totalWeight = segments.sumOf { segment ->
        segment.count { it.isLetterOrDigit() }.coerceAtLeast(1)
    }.coerceAtLeast(1)
    val duration = (word.endMs - word.startMs).coerceAtLeast(1L)
    var elapsed = 0L
    return segments.mapIndexed { index, segment ->
        val weight = segment.count { it.isLetterOrDigit() }.coerceAtLeast(1)
        val startMs = word.startMs + elapsed
        val endMs = if (index == segments.lastIndex) {
            word.endMs
        } else {
            (word.startMs + (duration * (elapsed + weight) / totalWeight)).coerceAtLeast(startMs + 1L)
        }
        elapsed += weight
        AppleMusicRenderWord(
            word = LyricWord(text = segment, startMs = startMs, endMs = endMs),
            // A sustained source span is represented by a glow on the final sung word; this
            // avoids every word in a phrase receiving the same permanent halo.
            sustainEndMs = sustainEndMs?.takeIf { index == segments.lastIndex }
        )
    }
}

/** Keep inter-word whitespace on the previous unit so a wrapped row starts at the shared edge. */
internal fun List<LyricWord>.moveLeadingSpacesToPreviousWord(): List<LyricWord> {
    val result = mutableListOf<LyricWord>()
    forEach { word ->
        val leadingWhitespace = word.text.takeWhile(Char::isWhitespace)
        if (leadingWhitespace.isNotEmpty() && result.isNotEmpty()) {
            val previous = result.removeAt(result.lastIndex)
            result += previous.copy(text = previous.text + leadingWhitespace)
        }
        val visibleText = word.text.drop(leadingWhitespace.length)
        if (visibleText.isNotEmpty()) result += word.copy(text = visibleText)
    }
    return result
}
