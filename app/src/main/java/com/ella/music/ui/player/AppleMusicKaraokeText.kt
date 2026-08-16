package com.ella.music.ui.player

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
    modifier: Modifier = Modifier
) {
    // TTML may encode the blank before a word as part of that word. Move it to the prior
    // karaoke unit before wrapping so every v1 line, including wrapped continuations, starts
    // at the same left edge. Right-aligned v2 rows are visually tolerant of this, but v1 is not.
    val timedWords = remember(text, words, sustainThresholdMs) {
        words.moveLeadingSpacesToPreviousWord().toAppleMusicRenderWords(text, sustainThresholdMs)
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
        timedWords.forEach { renderWord ->
            AppleMusicKaraokeWord(
                renderWord = renderWord,
                positionMs = positionMs,
                active = active,
                baseStyle = style,
                contentColor = contentColor,
                wordLiftEnabled = wordLiftEnabled
            )
        }
    }
    if (singleLine) {
        Row(
            modifier = modifier.then(if (statusBarMarquee) Modifier.basicMarquee() else Modifier),
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = Alignment.CenterVertically
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
                    placeable.placeRelative(x, y)
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
    wordLiftEnabled: Boolean
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
    val liftPx = if (wordLiftEnabled) maxOf(textSizePx * 0.06f, 5f) * progress else 0f
    Box(
        modifier = Modifier.graphicsLayer {
            translationY = -liftPx
            scaleX = 1f + 0.03f * sustainGlow
            scaleY = 1f + 0.03f * sustainGlow
            transformOrigin = TransformOrigin(0.5f, 1f)
        }
    ) {
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
