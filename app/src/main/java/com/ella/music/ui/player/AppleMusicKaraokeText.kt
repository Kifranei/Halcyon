package com.ella.music.ui.player

import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricWord
import com.ella.music.data.parser.isKanjiOrHangul
import com.ella.music.data.parser.isRtlText
import kotlin.math.cos
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.roundToInt

/** Specialized lyric scenes retain their own palette while the normal player follows settings. */
internal val LocalKaraokeRainbowOverride = staticCompositionLocalOf<Boolean?> { null }

internal fun isInlineRubyPronunciation(text: String): Boolean {
    val compact = text.filterNot { it.isWhitespace() }
    if (compact.isEmpty()) return false
    if (compact.any { it.isAppleMusicLatinLetter() }) return false
    return compact.any { it.isAppleMusicKana() || it.isKanjiOrHangul() }
}

private fun Char.isAppleMusicKana(): Boolean {
    val block = Character.UnicodeBlock.of(this)
    return block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA
}

internal fun appleMusicKaraokeLiftPx(
    wordLiftEnabled: Boolean,
    textSizePx: Float,
    progress: Float,
    wordLiftScale: Float = 1f
): Float = if (wordLiftEnabled) {
    maxOf(textSizePx * 0.06f, 5f) * progress * wordLiftScale.coerceIn(0f, 1f)
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
    wordLiftScale: Float = 1f,
    sustainThresholdMs: Int = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS,
    sustainGlowScale: Float = 1f,
    singleLine: Boolean = false,
    statusBarMarquee: Boolean = false,
    followWordFocus: Boolean = false,
    pronunciation: String = "",
    pronunciationWords: List<LyricWord> = emptyList(),
    rubyBelow: Boolean = false,
    splitRubyByCharacter: Boolean = false,
    rubyStyle: TextStyle? = null,
    outlineColor: Color? = null,
    outlineWidth: Float = 0f,
    glowColor: Color? = null,
    glowRadius: Float = 0f,
    onWordClick: ((Long) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    positionClock: State<Long>? = null,
    modifier: Modifier = Modifier
) {
    // TTML may encode the blank before a word as part of that word. Move it to the prior
    // karaoke unit before wrapping so every v1 line, including wrapped continuations, starts
    // at the same left edge. Right-aligned v2 rows are visually tolerant of this, but v1 is not.
    // wordLiftEnabled controls per-word vertical lift only; timed karaoke fill still renders.
    val sourceWords = remember(text, words, splitRubyByCharacter, pronunciationWords) {
        words.moveLeadingSpacesToPreviousWord().let { source ->
            // Timed readings that already line up with whole words (Apple Music TTML
            // transliterations: "nan" spans exactly "なん") stay on those words, so the reading is
            // centred over the whole word instead of landing on one split character.
            if (splitRubyByCharacter && !rubySpansAlignWithWords(source, pronunciationWords)) {
                source.splitForAppleMusicRuby()
            } else source
        }
    }
    val timedWords = remember(text, sourceWords, sustainThresholdMs) {
        sourceWords.toAppleMusicRenderWords(text, sustainThresholdMs)
    }
    val rubies = remember(timedWords, pronunciation, pronunciationWords) {
        rubiesForTimedWords(
            words = timedWords.map { it.word },
            pronunciationWords = pronunciationWords,
            pronunciation = pronunciation
        )
    }
    if (timedWords.isEmpty()) {
        val textModifier = Modifier
            .fillMaxWidth()
            .then(if (singleLine && statusBarMarquee) Modifier.basicMarquee() else Modifier)
        val textGlow = if (glowColor != null && glowRadius > 0f) {
            Shadow(
                color = glowColor.copy(alpha = glowColor.alpha * style.color.alpha),
                offset = Offset.Zero,
                blurRadius = glowRadius
            )
        } else null
        if (outlineColor != null && outlineWidth > 0f) {
            Box(modifier = modifier) {
                BasicText(
                    text = text,
                    style = style.copy(color = outlineColor, drawStyle = Stroke(width = outlineWidth), shadow = null),
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    softWrap = !singleLine,
                    overflow = TextOverflow.Clip,
                    modifier = textModifier
                )
                BasicText(
                    text = text,
                    style = style.copy(shadow = textGlow),
                    maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                    softWrap = !singleLine,
                    overflow = TextOverflow.Clip,
                    modifier = textModifier
                )
            }
        } else {
            BasicText(
                text = text,
                style = style.copy(shadow = textGlow),
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                softWrap = !singleLine,
                overflow = TextOverflow.Clip,
                modifier = modifier.then(if (singleLine && statusBarMarquee) Modifier.basicMarquee() else Modifier)
            )
        }
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
    // The clock ticks every frame. Hand the words a State they read in the draw/layer phase
    // instead of a value they take as a parameter, so a frame no longer recomposes the whole
    // line's worth of word subtrees just to move one feathered edge.
    val context = LocalContext.current
    val savedRainbowEnabled by remember(context) {
        SettingsManager.getInstance(context).lyricRainbowEnabled
    }.collectAsState(initial = false)
    val rainbowEnabled = LocalKaraokeRainbowOverride.current ?: savedRainbowEnabled
    val hdrHighlightEnabled by remember(context) {
        SettingsManager.getInstance(context).lyricHdrHighlightEnabled
    }.collectAsState(initial = false)
    val positionState = positionClock ?: rememberUpdatedState(positionMs)
    val content: @Composable () -> Unit = {
        timedWords.forEachIndexed { index, renderWord ->
            AppleMusicKaraokeWord(
                renderWord = renderWord,
                positionMs = positionState,
                active = active,
                baseStyle = style,
                contentColor = contentColor,
                wordLiftEnabled = wordLiftEnabled,
                wordLiftScale = wordLiftScale,
                sustainGlowScale = sustainGlowScale,
                outlineColor = outlineColor,
                outlineWidth = outlineWidth,
                glowColor = glowColor,
                glowRadius = glowRadius,
                rainbowEnabled = rainbowEnabled,
                hdrHighlightEnabled = hdrHighlightEnabled,
                ruby = rubies.getOrNull(index).orEmpty(),
                rubyStyle = rubyStyle,
                rubyBelow = rubyBelow,
                onWordClick = onWordClick,
                onLongPress = onLongPress
            )
        }
    }
    if (singleLine) {
        if (followWordFocus) {
            AppleMusicFocusedTimedRow(
                timedWords = timedWords,
                positionMs = positionState.value,
                active = active,
                horizontalArrangement = horizontalArrangement,
                rubyBelow = rubyBelow,
                modifier = modifier,
                content = content
            )
        } else {
            Row(
                modifier = modifier.then(if (statusBarMarquee) Modifier.basicMarquee() else Modifier),
                horizontalArrangement = horizontalArrangement,
                verticalAlignment = if (rubyBelow) Alignment.Top else Alignment.Bottom
            ) {
                content()
            }
        }
    } else {
        // FlowRow measures each visual row independently. With centered/right-aligned lyrics it
        // therefore lets wrapped rows acquire a different origin than the first row (especially
        // visible for a translation below a long English line). Lay rows out ourselves against
        // the full line width so every row shares the exact same alignment anchor.
        AppleMusicTimedWordRows(
            textAlign = style.textAlign,
            rubyBelow = rubyBelow,
            breakBefore = lyricUnitBreakAllowedBefore(timedWords.map { it.word.text }),
            modifier = modifier
        ) {
            content()
        }
    }
}

/**
 * Scroll a long single-line lyric to the currently sung word. Unlike basicMarquee this has a
 * stable end position: the first word stays at the leading edge until focus reaches it, and the
 * last word stays visible after the line finishes instead of restarting from the beginning.
 */
@Composable
private fun AppleMusicFocusedTimedRow(
    timedWords: List<AppleMusicRenderWord>,
    positionMs: Long,
    active: Boolean,
    horizontalArrangement: Arrangement.Horizontal,
    rubyBelow: Boolean = false,
    modifier: Modifier,
    content: @Composable () -> Unit
) {
    BoxWithConstraints(modifier = modifier.clipToBounds()) {
        val viewportWidthPx = with(LocalDensity.current) { maxWidth.toPx() }
        var contentWidthPx by remember(timedWords) { mutableIntStateOf(0) }
        val focusIndex = if (!active || timedWords.isEmpty()) {
            0
        } else {
            timedWords.indexOfLast { positionMs >= it.word.startMs }
                .coerceIn(0, timedWords.lastIndex)
        }
        val focusFraction = if (timedWords.size <= 1) {
            0f
        } else {
            focusIndex.toFloat() / (timedWords.lastIndex).coerceAtLeast(1)
        }
        val targetOffset = ((contentWidthPx - viewportWidthPx).coerceAtLeast(0f) * focusFraction)
        val animatedOffset = remember { Animatable(0f) }
        LaunchedEffect(targetOffset) {
            animatedOffset.animateTo(
                targetValue = targetOffset,
                animationSpec = tween(durationMillis = 90)
            )
        }
        val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
        Row(
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .wrapContentWidth(unbounded = true)
                .onSizeChanged { contentWidthPx = it.width }
                .graphicsLayer { translationX = if (isRtl) animatedOffset.value else -animatedOffset.value },
            horizontalArrangement = horizontalArrangement,
            verticalAlignment = if (rubyBelow) Alignment.Top else Alignment.Bottom
        ) {
            content()
        }
    }
}

@Composable
private fun AppleMusicTimedWordRows(
    textAlign: TextAlign,
    rubyBelow: Boolean = false,
    breakBefore: BooleanArray = BooleanArray(0),
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
        val placeables = measurables.map { it.measure(childConstraints) }
        val breaks = if (breakBefore.size == placeables.size) {
            breakBefore
        } else {
            BooleanArray(placeables.size) { true }
        }
        val rowRanges = wrapLyricUnitRows(
            widths = placeables.map { it.width }.toIntArray(),
            breakBefore = breaks,
            availableWidth = availableWidth
        )
        val rows = rowRanges.map { range -> placeables.slice(range) }
        val rowWidths = rows.map { row -> row.sumOf { it.width } }
        val rowHeights = rows.map { row -> row.maxOf { it.height } }

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
                    // Bottom-align when ruby is above so furigana grows upward without dropping the kanji.
                    // Top-align when ruby is below so furigana grows downward without raising the kanji.
                    val placeableY = if (rubyBelow) y else y + rowHeights[rowIndex] - placeable.height
                    placeable.placeRelative(x, placeableY)
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
    positionMs: State<Long>,
    active: Boolean,
    baseStyle: TextStyle,
    contentColor: Color,
    wordLiftEnabled: Boolean,
    wordLiftScale: Float = 1f,
    sustainGlowScale: Float = 1f,
    outlineColor: Color? = null,
    outlineWidth: Float = 0f,
    glowColor: Color? = null,
    glowRadius: Float = 0f,
    rainbowEnabled: Boolean = false,
    hdrHighlightEnabled: Boolean = false,
    ruby: String = "",
    rubyStyle: TextStyle? = null,
    rubyBelow: Boolean = false,
    onWordClick: ((Long) -> Unit)? = null,
    onLongPress: (() -> Unit)? = null
) {
    val referenceMotion = LocalReferenceLyricMotion.current
    val word = renderWord.word
    var leftRoom by remember { mutableFloatStateOf(0f) }
    var rightRoom by remember { mutableFloatStateOf(0f) }
    // 整句彩虹 is painted from the lyric line's own width, so each unit needs to know where its
    // box sits inside that line. Only the active rainbow line subscribes: dim lines keep their
    // cheap layer-only motion.
    val lineGeometry = remember { KaraokeLineGeometry() }
    val glyphLayout = remember { KaraokeGlyphLayout() }
    val rubyContent: @Composable () -> Unit = {
        if (ruby.isNotBlank() && rubyStyle != null) {
            val tracking = when {
                ruby.length >= 5 -> (-0.55).sp
                ruby.length >= 4 -> (-0.30).sp
                else -> (-0.10).sp
            }
            val scaledRubyStyle = if (ruby.length >= 5) {
                rubyStyle.copy(
                    fontSize = rubyStyle.fontSize * 0.88f,
                    letterSpacing = tracking
                )
            } else {
                rubyStyle.copy(letterSpacing = tracking)
            }
            BasicText(
                text = ruby,
                style = scaledRubyStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
    Layout(
        modifier = Modifier
            .onGloballyPositioned { coordinates ->
                val parent = coordinates.parentLayoutCoordinates
                if (parent != null) {
                    val x = parent.localPositionOf(coordinates, Offset.Zero).x
                    leftRoom = x.coerceAtLeast(0f)
                    rightRoom = (parent.size.width - x - coordinates.size.width).coerceAtLeast(0f)
                    if (rainbowEnabled) {
                        lineGeometry.offsetX = x
                        lineGeometry.boxWidth = coordinates.size.width.toFloat()
                        lineGeometry.width = parent.size.width.toFloat()
                    }
                }
            }
            .then(
                if (onWordClick != null) {
                    Modifier.pointerInput(word.startMs, onWordClick, onLongPress) {
                        detectTapGestures(
                            onTap = { onWordClick(word.startMs) },
                            onLongPress = onLongPress?.let { press -> { press() } }
                        )
                    }
                } else Modifier
            )
            // The reference renderer moves each word independently by 6% of the text size (at
            // least 5 px), then adds only a 3% bottom-anchored scale during the held-note phase.
            // Keeping the transform on the word rather than the whole line is what creates the
            // floating vocal feel. Reading the clock inside the layer block keeps the lift on the
            // layer phase, so a 20-word line no longer recomposes 20 subtrees per frame.
            .graphicsLayer {
                translationY = if (referenceMotion && wordLiftEnabled && active) {
                    -2.dp.toPx() * appleReferenceWordLift((positionMs.value - word.startMs).coerceAtLeast(0L)) * wordLiftScale.coerceIn(0f, 1f)
                } else -appleMusicKaraokeLiftPx(
                    wordLiftEnabled = wordLiftEnabled,
                    textSizePx = baseStyle.fontSize.toPx(),
                    progress = renderWord.karaokeProgress(if (active) positionMs.value else 0L, active),
                    wordLiftScale = wordLiftScale
                )
                scaleX = 1f
                scaleY = 1f
                if (referenceMotion && renderWord.sustainEndMs != null && active &&
                    !flamingoWaveEligible(referenceMotion, renderWord, word.text.length)) {
                    val duration = renderWord.sustainDurationMs
                    val amount = ((duration / 1000f).coerceIn(1f, 2f) - 1f) * .03f
                    val emphasis = appleReferenceEmphasis(positionMs.value - word.startMs, duration)
                    scaleX = 1f + amount * emphasis * sustainGlowScale.coerceIn(0f, 1f)
                    scaleY = scaleX
                }
                // Preserve line wrapping and spacing. Only nudge an enlarged edge word inward.
                if (size.width > 0f) {
                    scaleX = scaleX.coerceAtMost(1f + (leftRoom + rightRoom) / size.width)
                }
                val overflow = size.width * (scaleX - 1f) / 2f
                translationX = (overflow - leftRoom).coerceAtLeast(0f) -
                    (overflow - rightRoom).coerceAtLeast(0f)
                transformOrigin = TransformOrigin(0.5f, if (rubyBelow) 0f else 1f)
            },
        content = {
            AppleMusicKaraokeGlyphs(
                renderWord = renderWord,
                positionMs = positionMs,
                active = active,
                baseStyle = baseStyle,
                contentColor = contentColor,
                sustainGlowScale = sustainGlowScale,
                layoutCache = glyphLayout,
                outlineColor = outlineColor,
                outlineWidth = outlineWidth,
                glowColor = glowColor,
                glowRadius = glowRadius,
                rainbowEnabled = rainbowEnabled,
                hdrHighlightEnabled = hdrHighlightEnabled,
                lineGeometry = lineGeometry
            )
            rubyContent()
        }
    ) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val glyphs = measurables.first().measure(loose)
        val reading = measurables.getOrNull(1)?.measure(loose)
        val width = constraints.constrainWidth(maxOf(glyphs.width, reading?.width ?: 0))
        val readingHeight = reading?.height ?: 0
        val height = constraints.constrainHeight(glyphs.height + readingHeight)
        val glyphX = (width - glyphs.width) / 2
        // Inter-word spaces and edge punctuation stay in the karaoke unit, but must not
        // shift the reading away from the visible "So"/kanji glyphs.
        val center = glyphLayout.value?.visibleTextCenterX() ?: (glyphs.width / 2f)
        val readingX = (glyphX + center - (reading?.width ?: 0) / 2f).roundToInt()
            .coerceIn(0, (width - (reading?.width ?: 0)).coerceAtLeast(0))
        layout(width, height) {
            glyphs.place(glyphX, if (rubyBelow) 0 else readingHeight)
            reading?.place(readingX, if (rubyBelow) glyphs.height else 0)
        }
    }
}

private fun TextLayoutResult.visibleTextCenterX(): Float {
    var left = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    val text = layoutInput.text.text
    val start = text.indexOfFirst { !it.isWhitespace() && !it.isLyricPunctuation() }
    val end = text.indexOfLast { !it.isWhitespace() && !it.isLyricPunctuation() }
    if (start < 0) return size.width / 2f
    for (index in start..end) {
        val character = text[index]
        if (!character.isWhitespace()) {
            val bounds = getBoundingBox(index)
            left = minOf(left, bounds.left)
            right = maxOf(right, bounds.right)
        }
    }
    return if (left.isFinite() && right.isFinite()) (left + right) / 2f else size.width / 2f
}

private fun Char.isLyricPunctuation(): Boolean = when (Character.getType(this)) {
    Character.CONNECTOR_PUNCTUATION.toInt(), Character.DASH_PUNCTUATION.toInt(),
    Character.START_PUNCTUATION.toInt(), Character.END_PUNCTUATION.toInt(),
    Character.INITIAL_QUOTE_PUNCTUATION.toInt(), Character.FINAL_QUOTE_PUNCTUATION.toInt(),
    Character.OTHER_PUNCTUATION.toInt() -> true
    else -> false
}

/**
 * Where one karaoke unit sits inside its lyric line.
 *
 * The whole-line 七彩 gradient needs the line's pixel width and the unit's left edge, both of
 * which the line's layout already knows. They stay snapshot state rather than plain fields so a
 * unit that moves (a re-wrap, a scroll) still repaints its slice of the spectrum. Only the active
 * rainbow line reads them, so the extra invalidation stays inside drawing.
 */
internal class KaraokeLineGeometry {
    var offsetX by mutableFloatStateOf(0f)
    var width by mutableFloatStateOf(0f)
    var boxWidth by mutableFloatStateOf(0f)
}

/** Holds the last measured paragraph so the draw phase can reuse it without re-laying out text. */
private class KaraokeGlyphLayout {
    var value: TextLayoutResult? = null
}

/**
 * One karaoke unit, measured once and repainted from the playback clock.
 *
 * The previous renderer stacked up to four [BasicText] children per word and rebuilt their
 * [TextStyle]s from `positionMs` on every frame, so an active line re-entered composition — and
 * the sweeping word re-entered text layout — 120 times a second. Every visual pass is kept here
 * (dim base, feathered sweep, sustain halo and its travelling sheen, held-note glow); they are
 * simply issued as `drawText` calls against one cached [TextLayoutResult], which is the one thing
 * that must not be recomputed per frame.
 */
@Composable
private fun AppleMusicKaraokeGlyphs(
    renderWord: AppleMusicRenderWord,
    positionMs: State<Long>,
    active: Boolean,
    baseStyle: TextStyle,
    contentColor: Color,
    sustainGlowScale: Float,
    layoutCache: KaraokeGlyphLayout,
    outlineColor: Color? = null,
    outlineWidth: Float = 0f,
    glowColor: Color? = null,
    glowRadius: Float = 0f,
    rainbowEnabled: Boolean = false,
    hdrHighlightEnabled: Boolean = false,
    lineGeometry: KaraokeLineGeometry? = null
) {
    val referenceMotion = LocalReferenceLyricMotion.current
    val word = renderWord.word
    val measurer = rememberTextMeasurer()
    val cache = layoutCache
    val baseAlpha = baseStyle.color.alpha
    val bright = contentColor.copy(alpha = baseAlpha)
    val dimFactor = DefaultKaraokeDimAlphaFactor
    val dim = contentColor.copy(alpha = baseAlpha * dimFactor)
    val sustainDurationMs = renderWord.sustainDurationMs
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl || word.text.isRtlText()
    val useRainbow = rainbowEnabled && active
    val maskedGlow = if (glowColor != null && glowRadius > 0f) rememberGraphicsLayer() else null
    val trailSustainGlowScale = sustainGlowScale
    // lyric_hdr_highlight_enabled: ~1.5× sustain glow / bright highlight (settings summary).
    val effectiveSustainGlowScale = if (hdrHighlightEnabled) {
        (trailSustainGlowScale * LyricHdrBrightnessRatio).coerceAtMost(2.2f)
    } else {
        trailSustainGlowScale
    }
    val glowCap = if (hdrHighlightEnabled) 2.2f else 1.5f
    val hdrHalo = remember { HdrGlyphHalo() }
    val fontFamilyResolver = androidx.compose.ui.platform.LocalFontFamilyResolver.current
    // The HDR halo draws the real glyphs natively, so it needs the same typeface as the text.
    val hdrTypeface = remember(fontFamilyResolver, baseStyle.fontFamily, baseStyle.fontWeight, baseStyle.fontStyle, baseStyle.fontSynthesis) {
        runCatching {
            fontFamilyResolver.resolve(
                baseStyle.fontFamily,
                baseStyle.fontWeight ?: androidx.compose.ui.text.font.FontWeight.Normal,
                baseStyle.fontStyle ?: androidx.compose.ui.text.font.FontStyle.Normal,
                baseStyle.fontSynthesis ?: androidx.compose.ui.text.font.FontSynthesis.All
            ).value as? android.graphics.Typeface
        }.getOrNull()
    }

    // Body of one full-word karaoke draw; reused per letter by the Flamingo sustain wave below.
    val drawKaraokeWord: androidx.compose.ui.graphics.drawscope.DrawScope.(androidx.compose.ui.text.TextLayoutResult) -> Unit = { layout ->
            // Inactive words have static fill/lift. Do not subscribe every off-focus glyph
            // layer to the 60 Hz playback clock; active rainbow/glow/lift keep their cadence.
            val clock = if (active) positionMs.value else 0L
            val progress = renderWord.karaokeProgress(clock, active)
            if (outlineColor != null && outlineWidth > 0f) {
                drawText(
                    textLayoutResult = layout,
                    color = outlineColor,
                    drawStyle = Stroke(width = outlineWidth)
                )
            }
            val envelope = if (referenceMotion && active && renderWord.sustainEndMs != null) {
                appleReferenceEmphasis(clock - word.startMs, renderWord.sustainDurationMs)
            } else renderWord.sustainGlowAlpha(clock, active)
            val glow = envelope * effectiveSustainGlowScale.coerceIn(0f, glowCap)
            val fontSizePx = baseStyle.fontSize.toPx().coerceAtLeast(1f)
            val wordWidth = layout.size.width.toFloat().coerceAtLeast(1f)
            val feather = if (referenceMotion) {
                (30.dp.toPx() / wordWidth).coerceIn(0.15f, 1f)
            } else DefaultKaraokeFeather
            val revealStops = karaokeHaloMaskStops(progress, isRtl, feather)

            // Blur the already-revealed glyph, never a shadow of the complete word. Blurring
            // first exposed golden contours ahead of the pale fill, especially on CJK units.
            if (maskedGlow != null && glowColor != null && progress > 0f) {
                val padding = kotlin.math.ceil(glowRadius * 3f).toInt()
                val glyphY = layout.size.height.toFloat()
                maskedGlow.compositingStrategy = CompositingStrategy.Offscreen
                maskedGlow.renderEffect = if (android.os.Build.VERSION.SDK_INT >= 31)
                    BlurEffect(glowRadius, glowRadius, TileMode.Decal) else null
                maskedGlow.record(size = IntSize(layout.size.width + padding * 2, layout.size.height * 3)) {
                    drawText(textLayoutResult = layout, color = glowColor.copy(alpha = glowColor.alpha * baseAlpha),
                        topLeft = Offset(padding.toFloat(), glyphY))
                    drawRect(Brush.horizontalGradient(
                        colorStops = revealStops.map { (x, color) -> x to color.copy(alpha = color.alpha * color.alpha) }.toTypedArray(),
                        startX = padding.toFloat(), endX = padding + wordWidth), blendMode = BlendMode.DstIn)
                }
                withTransform({ translate(-padding.toFloat(), -glyphY) }) { drawLayer(maskedGlow) }
            }
            if (glow > 0f && progress > 0f) {
                val haloWidth = layout.size.width.toFloat().coerceAtLeast(1f)
                val haloBounds = androidx.compose.ui.geometry.Rect(
                    -24.dp.toPx(), -layout.size.height.toFloat(),
                    haloWidth + 24.dp.toPx(), layout.size.height * 2f
                )
                val haloCanvas = drawContext.canvas
                haloCanvas.saveLayer(haloBounds, androidx.compose.ui.graphics.Paint())
                try {
                    // Only the sung letters contribute to a held-note halo.
                    val durationScale = ((sustainDurationMs - 600L).coerceAtLeast(0L) / 2_400f)
                        .coerceIn(0f, 1f)
                    val hdrGain = if (hdrHighlightEnabled && active) LyricHdrWindow.gain.floatValue else 1f
                    if (hdrGain > 1.01f) {
                        // Real HDR: Compose text shadows are clamped to 8-bit sRGB, so draw the halo from the
                        // glyph outlines with an extended-range colour that exceeds SDR white.
                        hdrHalo.draw(
                            scope = this,
                            layout = layout,
                            typeface = hdrTypeface,
                            fontSizePx = baseStyle.fontSize.toPx(),
                            letterSpacingEm = if (baseStyle.letterSpacing.isEm) baseStyle.letterSpacing.value
                                else if (baseStyle.letterSpacing.isSp && baseStyle.fontSize.isSp && baseStyle.fontSize.value > 0f)
                                    baseStyle.letterSpacing.value / baseStyle.fontSize.value
                                else 0f,
                            color = contentColor,
                            alpha = ((0.32f + durationScale * 0.14f) * glow * baseAlpha).coerceIn(0f, 1f),
                            blurRadius = (8f + durationScale * 6f) * glow,
                            gain = hdrGain
                        )
                    } else {
                        val haloColor = contentColor.withHdrHighlightBoost(hdrHighlightEnabled && active)
                        drawText(
                            textLayoutResult = layout,
                            color = haloColor.copy(
                                alpha = ((0.05f + durationScale * 0.08f) * glow * baseAlpha).coerceIn(0f, 1f)
                            ),
                            shadow = Shadow(
                                color = haloColor.copy(
                                    alpha = ((0.32f + durationScale * 0.14f) * glow * baseAlpha).coerceIn(0f, 1f)
                                ),
                                offset = Offset.Zero,
                                blurRadius = (8f + durationScale * 6f) * glow
                            )
                        )
                    }
                    if (progress < 1f) {
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colorStops = revealStops,
                                startX = 0f, endX = haloWidth
                            ),
                            topLeft = haloBounds.topLeft, size = haloBounds.size,
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                        )
                    }
                } finally {
                    haloCanvas.restore()
                }
            }
            val glowShadow = glow.takeIf { it > 0.05f }?.let { glowAlpha ->
                val shadowBase = contentColor.withHdrHighlightBoost(hdrHighlightEnabled && active)
                Shadow(
                    color = shadowBase.copy(alpha = 0.24f * baseAlpha * glowAlpha),
                    offset = Offset.Zero,
                    blurRadius = 6f * glowAlpha
                )
            }
            // 整句彩虹: one 七彩 sweep belongs to the whole lyric line, so a unit paints the slice of
            // that spectrum its own box covers instead of restarting ROYGBIV on every character.
            // These are draw-phase reads of layout facts, so a scrolling line never recomposes.
            // Read the geometry only for a rainbow line: writes would otherwise invalidate the
            // drawing of dim lines that never look at it.
            val lineWidth = if (useRainbow) lineGeometry?.width ?: 0f else 0f
            val lineRainbow = useRainbow && lineWidth > wordWidth + 0.5f
            val lineOffsetX = if (lineRainbow) {
                // Ruby can make the word box wider than its glyph run; keep the spectrum aligned
                // with the glyphs that are actually painted.
                (lineGeometry?.offsetX ?: 0f) +
                    ((lineGeometry?.boxWidth ?: wordWidth) - wordWidth).coerceAtLeast(0f) / 2f
            } else {
                0f
            }
            val fillColor = if (useRainbow) {
                karaokeRainbowColor(0.5f, baseAlpha).withHdrHighlightBoost(hdrHighlightEnabled)
            } else {
                bright.withHdrHighlightBoost(hdrHighlightEnabled && active)
            }
            val rainbowAlpha = if (hdrHighlightEnabled) {
                (baseAlpha * LyricHdrBrightnessRatio).coerceAtMost(1f)
            } else {
                baseAlpha
            }
            when {
                progress <= 0f -> drawText(textLayoutResult = layout, color = dim)
                progress >= 1f -> {
                    if (useRainbow) {
                        drawText(
                            textLayoutResult = layout,
                            brush = if (lineRainbow) {
                                karaokeRainbowLineBrush(
                                    alpha = rainbowAlpha,
                                    lineWidth = lineWidth,
                                    lineOffsetX = lineOffsetX,
                                    isRtl = isRtl
                                )
                            } else {
                                karaokeRainbowBrush(
                                    alpha = rainbowAlpha,
                                    wordWidth = wordWidth,
                                    isRtl = isRtl
                                )
                            },
                            shadow = glowShadow
                        )
                    } else {
                        drawText(
                            textLayoutResult = layout,
                            color = fillColor,
                            shadow = glowShadow
                        )
                    }
                }
                else -> {
                    drawText(textLayoutResult = layout, color = dim)
                    // Paint the bright glyph through exactly the same reveal mask as its halo.
                    // Reference motion still controls lift/breathe; it cannot advance the fill edge.
                    val fillCanvas = drawContext.canvas
                    fillCanvas.saveLayer(
                        androidx.compose.ui.geometry.Rect(0f, -layout.size.height.toFloat(), wordWidth, layout.size.height * 2f),
                        androidx.compose.ui.graphics.Paint()
                    )
                    try {
                        if (useRainbow) {
                            drawText(
                                textLayoutResult = layout,
                                brush = if (lineRainbow) karaokeRainbowLineBrush(
                                    rainbowAlpha, lineWidth, lineOffsetX, isRtl
                                ) else karaokeRainbowBrush(rainbowAlpha, wordWidth, isRtl)
                            )
                        } else {
                            drawText(textLayoutResult = layout, color = fillColor)
                        }
                        drawRect(
                            brush = Brush.horizontalGradient(colorStops = revealStops, startX = 0f, endX = wordWidth),
                            topLeft = Offset(0f, -layout.size.height.toFloat()),
                            size = androidx.compose.ui.geometry.Size(wordWidth, layout.size.height * 3f),
                            blendMode = androidx.compose.ui.graphics.BlendMode.DstIn
                        )
                    } finally {
                        fillCanvas.restore()
                    }
                }
            }
    }
    Layout(
        modifier = Modifier.drawBehind {
            val layout = cache.value ?: return@drawBehind
            val waveClock = if (active) positionMs.value else 0L
            val letterCount = layout.layoutInput.text.length
            if (!(active && flamingoWaveActiveAt(referenceMotion, renderWord, letterCount, waveClock))) {
                if (glowColor != null && glowRadius > 0f) {
                    // A completed word's wide golden shadow must not tint the next unsung
                    // word. Keep the vertical bloom while confining it to this timed unit.
                    clipRect(left = 0f, top = -size.height, right = size.width, bottom = size.height * 2f) {
                        drawKaraokeWord(layout)
                    }
                } else drawKaraokeWord(layout)
                return@drawBehind
            }
            // Flamingo sustain wave: each letter swells in turn (sin² bell over 68% of the word,
            // starts spread over the other 32%), so the swell sweeps across and the last letter
            // settles exactly at the word end — the "甩尾" after the breathe.
            val durationMs = renderWord.sustainDurationMs
            val elapsed = waveClock - word.startMs
            val strength = flamingoWaveStrength(durationMs) * sustainGlowScale.coerceIn(0f, 1f)
            val liftPx = 1.dp.toPx()
            for (index in 0 until letterCount) {
                val box = layout.getBoundingBox(index)
                val left = if (index == 0) 0f else box.left
                val right = if (index == letterCount - 1) size.width else box.right
                if (right <= left) continue
                val bell = flamingoLetterEmphasis(elapsed, durationMs, index, letterCount)
                val scale = 1f + (0.06f + 0.06f * strength) * bell * strength.coerceAtLeast(0.35f)
                clipRect(left = left, top = -size.height, right = right, bottom = size.height * 2f) {
                    withTransform({
                        translate(top = -liftPx * bell * strength)
                        scale(scale, scale, pivot = Offset((box.left + box.right) / 2f, box.bottom))
                    }) {
                        drawKaraokeWord(layout)
                    }
                }
            }
        }
    ) { _, constraints ->
        val cached = cache.value
        val layout = if (
            cached != null &&
            cached.layoutInput.text.text == word.text &&
            cached.layoutInput.style == baseStyle &&
            cached.layoutInput.constraints == constraints &&
            cached.layoutInput.layoutDirection == layoutDirection &&
            cached.layoutInput.density.density == density &&
            cached.layoutInput.density.fontScale == fontScale
        ) {
            cached
        } else {
            measurer.measure(
                text = word.text,
                style = baseStyle,
                overflow = TextOverflow.Clip,
                constraints = constraints,
                layoutDirection = layoutDirection,
                density = this
            ).also { cache.value = it }
        }
        layout(layout.size.width, layout.size.height) {}
    }
}

internal fun AppleMusicRenderWord.karaokeProgress(positionMs: Long, active: Boolean): Float =
    if (active) {
        if (word.endMs == word.startMs) {
            if (positionMs >= word.startMs) 1f else 0f
        } else {
            ((positionMs - word.startMs).toFloat() / (word.endMs - word.startMs).coerceAtLeast(1L))
                .coerceIn(0f, 1f)
        }
    } else {
        0f
    }

internal const val DefaultKaraokeDimAlphaFactor = 0.36f
internal const val DefaultKaraokeFeather = 0.15f

/** Feather behind the sung edge; even a wide halo must not reveal unsung letter contours. */
internal fun karaokeHaloMaskStops(progress: Float, isRtl: Boolean, feather: Float): Array<Pair<Float, Color>> = when {
    progress <= 0f -> arrayOf(0f to Color.Transparent, 1f to Color.Transparent)
    progress >= 1f -> arrayOf(0f to Color.White, 1f to Color.White)
    else -> karaokeFillStops(progress, Color.White, isRtl, feather)
}

/**
 * Classic 七彩 / ROYGBIV spectrum anchors (赤橙黄绿青蓝紫).
 * Used as brush colorStops; [karaokeRainbowColor] samples a continuous HSV sweep.
 */
internal val LyricSpectrumRainbowColors: List<Color> = listOf(
    Color(0xFFFF3B30), // 赤 red
    Color(0xFFFF9500), // 橙 orange
    Color(0xFFFFCC00), // 黄 yellow
    Color(0xFF34C759), // 绿 green
    Color(0xFF5AC8FA), // 青 cyan
    Color(0xFF007AFF), // 蓝 blue
    Color(0xFFAF52DE)  // 紫 violet
)

/** Lyricon-style HDR highlight brightness ratio (BasicStyle hdrBrightnessRatio default). */
internal val LyricHdrBrightnessRatio: Float get() = LyricHdrWindow.userRatio.floatValue

/**
 * Sample a continuous 七彩 spectrum at position t in 0..1.
 * HSV hue sweeps 0°→300° (red→violet) so karaoke fill looks like a real rainbow.
 */
internal fun karaokeRainbowColor(position: Float, alpha: Float): Color {
    val t = position.coerceIn(0f, 1f)
    return Color.hsv(
        hue = t * 300f,
        saturation = 0.88f,
        value = 1f,
        alpha = alpha.coerceIn(0f, 1f)
    )
}

/**
 * Static soft multi-stop horizontal 七彩 rainbow across [wordWidth].
 * Seven HSV-sampled stops (ROYGBIV) for a continuous spectrum brush.
 */
internal fun karaokeRainbowBrush(
    alpha: Float,
    wordWidth: Float,
    isRtl: Boolean
): Brush {
    val stopCount = LyricSpectrumRainbowColors.size.coerceAtLeast(2)
    val stops = Array(stopCount) { i ->
        val p = i / (stopCount - 1).toFloat()
        val sample = if (isRtl) 1f - p else p
        p to karaokeRainbowColor(sample, alpha)
    }
    return Brush.horizontalGradient(colorStops = stops, startX = 0f, endX = wordWidth.coerceAtLeast(1f))
}

/**
 * 整句七彩: the same seven-stop spectrum stretched across the whole lyric line, then shifted into a
 * single unit's drawing space. [lineOffsetX] is that unit's left edge inside the line, so the first
 * unit paints the start of the sweep and every later unit continues the spectrum exactly where the
 * previous one stopped instead of restarting ROYGBIV on each character.
 */
internal fun karaokeRainbowLineBrush(
    alpha: Float,
    lineWidth: Float,
    lineOffsetX: Float,
    isRtl: Boolean
): Brush {
    val span = lineWidth.coerceAtLeast(1f)
    val stopCount = LyricSpectrumRainbowColors.size.coerceAtLeast(2)
    val stops = Array(stopCount) { i ->
        val p = i / (stopCount - 1).toFloat()
        val sample = if (isRtl) 1f - p else p
        p to karaokeRainbowColor(sample, alpha)
    }
    return Brush.horizontalGradient(
        colorStops = stops,
        startX = -lineOffsetX,
        endX = span - lineOffsetX
    )
}

/**
 * SDR stand-in for Lyricon HDR highlight boost (~1.5× luminance).
 * Compose text does not practically wire DesiredHdrHeadroom; we brighten toward white.
 */
internal fun Color.withHdrHighlightBoost(enabled: Boolean, ratio: Float = LyricHdrBrightnessRatio): Color {
    if (!enabled || ratio <= 1f) return this
    val t = (1f - 1f / ratio).coerceIn(0f, 1f)
    return Color(
        red = (red + (1f - red) * t).coerceIn(0f, 1f),
        green = (green + (1f - green) * t).coerceIn(0f, 1f),
        blue = (blue + (1f - blue) * t).coerceIn(0f, 1f),
        alpha = alpha
    )
}

/**
 * Karaoke fill color stops.
 * Default / experimental trail: the soft edge traverses and clears the glyph by the timed end.
 * [qzCentered]: legacy soft zone centered on progress (half ahead / half behind) with
 * expanded domain so the edge fully clears at 0 and 1 — unused by the trail toggle.
 */
internal fun karaokeFillStops(
    progress: Float,
    bright: Color,
    isRtl: Boolean,
    feather: Float = DefaultKaraokeFeather,
    qzCentered: Boolean = false
): Array<Pair<Float, Color>> {
    val soft = feather.coerceIn(0.05f, 0.85f)
    if (!qzCentered) {
        // Let the feather leave the glyph by the timed word's end, as in 1.2.9. Clamping
        // the edge to progress left the final glyph pixels dim until the 100% branch,
        // which made every short CJK unit flash when the next word began.
        val edge = progress.coerceIn(0f, 1f) * (1f + soft)
        val solid = (edge - soft).coerceIn(0f, 1f)
        val clear = edge.coerceIn(0f, 1f)
        fun colorAt(x: Float): Color = bright.copy(
            alpha = bright.alpha * ((edge - x) / soft).coerceIn(0f, 1f)
        )
        val stops = sortedSetOf(0f, solid, clear, 1f).map { it to colorAt(it) }
        return if (isRtl) stops.asReversed().map { (x, color) -> 1f - x to color }.toTypedArray()
            else stops.toTypedArray()
    }
    val half = soft * 0.5f
    val center = ((1f + soft) * progress.coerceIn(0f, 1f)) - half
    val solidEnd = center - half
    val dimStart = center + half
    return if (isRtl) {
        val a = (1f - dimStart).coerceIn(0f, 1f)
        val b = (1f - solidEnd).coerceIn(a, 1f)
        arrayOf(
            0f to Color.Transparent,
            a to Color.Transparent,
            b to bright,
            1f to bright
        )
    } else {
        val a = solidEnd.coerceIn(0f, 1f)
        val b = dimStart.coerceIn(a, 1f)
        arrayOf(
            0f to bright,
            a to bright,
            b to Color.Transparent,
            1f to Color.Transparent
        )
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
        groupRubySpansByWord(words, rubyWords)?.let { return it }
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

/**
 * Groups timed readings under whole words when they line up with them: every reading lies inside
 * one word, and each word's readings together start and end with that word (within [toleranceMs]).
 * A Latin word's trailing punctuation may extend its display timing beyond its reading.
 * A word may carry several readings ("ima" + "mo" over "今も"); they are joined with a space.
 * Returns null when the readings do not follow word boundaries.
 */
internal fun groupRubySpansByWord(
    words: List<LyricWord>,
    pronunciationWords: List<LyricWord>,
    toleranceMs: Long = 25L
): List<String>? {
    val rubies = pronunciationWords
        .map { it.copy(text = it.text.trim()) }
        .filter { it.text.isNotBlank() && it.endMs > it.startMs }
    if (rubies.isEmpty() || words.isEmpty()) return null
    val grouped = List(words.size) { mutableListOf<LyricWord>() }
    for (ruby in rubies) {
        val owner = words.indices.firstOrNull { i ->
            ruby.startMs >= words[i].startMs - toleranceMs && ruby.endMs <= words[i].endMs + toleranceMs
        } ?: return null
        grouped[owner] += ruby
    }
    grouped.forEachIndexed { i, group ->
        if (group.isEmpty()) return@forEachIndexed
        if (kotlin.math.abs(group.first().startMs - words[i].startMs) > toleranceMs) return null
        if (kotlin.math.abs(group.last().endMs - words[i].endMs) > toleranceMs) {
            val text = words[i].text.trim()
            val stem = text.dropLastWhile { it.isLyricPunctuation() }
            val punctuatedLatinWord = stem.length < text.length &&
                stem.any { it.isAppleMusicLatinLetter() } &&
                stem.all { it.isLetterOrDigit() || it in "'’-" }
            if (!punctuatedLatinWord) return null
        }
    }
    return grouped.map { group -> group.joinToString(" ") { it.text } }
}

/** True when timed readings follow whole-word boundaries and must not be redistributed per character. */
internal fun rubySpansAlignWithWords(
    words: List<LyricWord>,
    pronunciationWords: List<LyricWord>,
    toleranceMs: Long = 25L
): Boolean = groupRubySpansByWord(words, pronunciationWords, toleranceMs) != null

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

private fun attachRubyBySyllables(words: List<LyricWord>, reading: String): List<String> {
    val tokens = reading.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
    if (tokens.isEmpty()) return List(words.size) { "" }

    val charToWord = mutableListOf<Int>()
    val surface = buildString {
        words.forEachIndexed { index, word ->
            word.text.forEach { character ->
                append(character)
                charToWord += index
            }
        }
    }
    val rubyByWord = MutableList(words.size) { mutableListOf<String>() }
    var sIdx = 0
    var tIdx = 0

    while (sIdx < surface.length) {
        val character = surface[sIdx]
        if (character.isWhitespace()) {
            sIdx++
            continue
        }

        if (character.isKanjiOrHangul()) {
            var runEnd = sIdx + 1
            while (runEnd < surface.length && surface[runEnd].isKanjiOrHangul()) {
                runEnd++
            }
            val runLength = runEnd - sIdx

            // Look ahead for the next Latin anchor in surface
            var nextLatinMatch: Int? = null
            var restIdx = runEnd
            while (restIdx < surface.length) {
                val restChar = surface[restIdx]
                if (restChar.isAppleMusicLatinLetter()) {
                    var wordEnd = restIdx + 1
                    while (wordEnd < surface.length && (surface[wordEnd].isAppleMusicLatinLetter() || surface[wordEnd] in "'’-")) {
                        wordEnd++
                    }
                    val latinWord = surface.substring(restIdx, wordEnd).filter { it.isAppleMusicLatinLetter() }.lowercase()
                    if (latinWord.isNotEmpty()) {
                        for (tokI in tIdx until tokens.size) {
                            val normTok = tokens[tokI].filter { it.isAppleMusicLatinLetter() }.lowercase()
                            if (normTok == latinWord) {
                                nextLatinMatch = tokI
                                break
                            }
                        }
                    }
                    break
                }
                restIdx++
            }

            val consumed = if (nextLatinMatch != null) {
                maxOf(1, nextLatinMatch - tIdx)
            } else {
                val remTokens = tokens.size - tIdx
                val remKanji = (runEnd until surface.length).count { surface[it].isKanjiOrHangul() }
                if (remKanji > 0) {
                    maxOf(1, minOf(runLength, remTokens - remKanji))
                } else {
                    remTokens
                }
            }

            val tokEnd = minOf(tokens.size, tIdx + consumed)
            val assignedTokens = tokens.subList(tIdx, tokEnd)
            tIdx = tokEnd

            if (assignedTokens.size == runLength) {
                assignedTokens.forEachIndexed { i, tok ->
                    rubyByWord[charToWord[sIdx + i]].add(tok)
                }
            } else if (assignedTokens.size == 1) {
                rubyByWord[charToWord[sIdx]].add(assignedTokens[0])
            } else {
                for (i in 0 until minOf(runLength, assignedTokens.size)) {
                    val tok = if (i == runLength - 1 && assignedTokens.size > runLength) {
                        assignedTokens.subList(i, assignedTokens.size).joinToString(" ")
                    } else {
                        assignedTokens[i]
                    }
                    rubyByWord[charToWord[sIdx + i]].add(tok)
                }
            }
            sIdx = runEnd
        } else if (character.isAppleMusicLatinLetter()) {
            var wordEnd = sIdx + 1
            while (wordEnd < surface.length && (surface[wordEnd].isAppleMusicLatinLetter() || surface[wordEnd] in "'’-")) {
                wordEnd++
            }
            val latinWord = surface.substring(sIdx, wordEnd).filter { it.isAppleMusicLatinLetter() }.lowercase()
            if (tIdx < tokens.size) {
                val normTok = tokens[tIdx].filter { it.isAppleMusicLatinLetter() }.lowercase()
                if (normTok == latinWord) {
                    tIdx++
                }
            }
            sIdx = wordEnd
        } else {
            if (tIdx < tokens.size && tokens[tIdx] == character.toString()) {
                tIdx++
            }
            sIdx++
        }
    }

    return rubyByWord.map { it.joinToString(" ") }
}

internal fun attachRubyByCorrespondence(words: List<LyricWord>, reading: String): List<String> {
    if (words.isEmpty()) return emptyList()
    val cleanReading = reading.trim()
    if (cleanReading.isBlank()) return List(words.size) { "" }

    if (cleanReading.contains(' ') || cleanReading.any { it.isAppleMusicLatinLetter() }) {
        val syllableAligned = attachRubyBySyllables(words, cleanReading)
        if (syllableAligned.any { it.isNotBlank() }) {
            return syllableAligned
        }
    }

    val readingChars = cleanReading.filterNot { it.isWhitespace() }.toList()
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

private fun Char.isAppleMusicCjkIdeograph(): Boolean = isKanjiOrHangul()

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

internal data class AppleMusicRenderWord(
    val word: LyricWord,
    val sustainEndMs: Long? = null
) {
    val sustainDurationMs: Long get() = (sustainEndMs ?: word.endMs) - word.startMs
}

internal fun List<LyricWord>.toAppleMusicRenderWords(
    lineText: String,
    sustainThresholdMs: Int
): List<AppleMusicRenderWord> {
    if (isEmpty() || lineText.isBlank()) return emptyList()
    val result = mutableListOf<AppleMusicRenderWord>()
    var cursor = 0
    forEachIndexed { index, word ->
        // TTML credit punctuation often has begin == end. Keep it visible and reveal it
        // at that timestamp instead of silently dropping the colon/slash from the line.
        if (word.text.isBlank() || word.endMs < word.startMs) return@forEachIndexed
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
            result += AppleMusicRenderWord(
                word = word.copy(text = word.text + suffix),
                sustainEndMs = word.endMs.takeIf { duration >= sustainThresholdMs.coerceAtLeast(0) }
            )
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
    // Latin words should never be split into characters across line wraps.
    // Whole words are kept intact so that "stranger" never breaks into "stra" and "nger".
    if (text.any { it.isAppleMusicLatinLetter() }) return false
    return text.any { it.isAppleMusicCjkCharacter() }
}

private fun Char.isAppleMusicLatinLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

/**
 * Apple word-timed lyrics split English into sung syllables (`heart` / `brea` / `ker`).
 * Those pieces must wrap as one word. A break is allowed only at whitespace or where a
 * Latin word starts, so CJK can still wrap per character.
 */
internal fun lyricUnitBreakAllowedBefore(texts: List<String>): BooleanArray =
    BooleanArray(texts.size) { index ->
        index == 0 || !continuesLatinWord(texts[index - 1], texts[index])
    }

private fun continuesLatinWord(previous: String, current: String): Boolean {
    if (previous.isEmpty() || current.isEmpty()) return false
    if (previous.last().isWhitespace() || current.first().isWhitespace()) return false
    val prevChar = previous.last()
    val nextChar = current.first()
    val prevJoins = prevChar.isAppleMusicLatinLetter() || prevChar == '\'' || prevChar == '’' || prevChar == '-'
    val nextJoins = nextChar.isAppleMusicLatinLetter() || nextChar == '\'' || nextChar == '’' || nextChar == '-'
    return prevJoins && nextJoins &&
        (prevChar.isAppleMusicLatinLetter() || nextChar.isAppleMusicLatinLetter())
}

/**
 * @return inclusive index ranges, one per visual row.
 * A Latin syllable run moves to the next row together when it fits there. A run wider than
 * the row still breaks, so one enormous word cannot overflow the screen.
 */
internal fun wrapLyricUnitRows(
    widths: IntArray,
    breakBefore: BooleanArray,
    availableWidth: Int
): List<IntRange> {
    if (widths.isEmpty()) return emptyList()
    val rows = mutableListOf<IntRange>()
    var rowStart = 0
    var rowWidth = 0
    var index = 0
    while (index < widths.size) {
        val width = widths[index]
        if (rowWidth == 0 || rowWidth + width <= availableWidth) {
            rowWidth += width
            index++
            continue
        }
        if (index > rowStart && index < breakBefore.size && !breakBefore[index]) {
            var runStart = index
            while (runStart > rowStart && !breakBefore[runStart]) runStart--
            var runWidth = width
            for (cursor in runStart until index) runWidth += widths[cursor]
            if (runStart > rowStart && runWidth <= availableWidth) {
                rows += rowStart until runStart
                rowStart = runStart
                rowWidth = runWidth - width
                continue
            }
        }
        if (index > rowStart) {
            rows += rowStart until index
            rowStart = index
            rowWidth = 0
            continue
        }
        rows += index..index
        index++
        rowStart = index
        rowWidth = 0
    }
    if (rowStart < widths.size) rows += rowStart until widths.size
    return rows
}

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

/**
 * TTML ruby often arrives as one timed span for a whole Japanese phrase. Split that span into
 * character-sized timed units before attaching ruby text so the reading can sit below its own
 * character instead of becoming one second line under the whole phrase.
 */
internal fun List<LyricWord>.splitForAppleMusicRuby(): List<LyricWord> {
    if (isEmpty()) return this
    return flatMap { word ->
        val pieces = mutableListOf<StringBuilder>()
        word.text.forEach { character ->
            if (character.isWhitespace() && pieces.isNotEmpty()) {
                pieces.last().append(character)
            } else {
                pieces += StringBuilder().append(character)
            }
        }
        val hasMultipleCjkCharacters = pieces.size > 1 &&
            pieces.any { piece -> piece.any { it.isAppleMusicCjkCharacter() } }
        val duration = word.endMs - word.startMs
        if (!hasMultipleCjkCharacters || duration < pieces.size.toLong()) {
            listOf(word)
        } else {
            pieces.mapIndexed { index, piece ->
                val start = word.startMs + duration * index / pieces.size
                val end = if (index == pieces.lastIndex) {
                    word.endMs
                } else {
                    word.startMs + duration * (index + 1) / pieces.size
                }
                LyricWord(
                    text = piece.toString(),
                    startMs = start,
                    endMs = end.coerceAtLeast(start + 1L)
                )
            }
        }
    }
}

/**
 * Extended-range sustain halo. Draws the word's real glyphs (same typeface, size and line layout
 * as the Compose text) with a blur mask and a colour above SDR white. Compose text shadows are
 * clamped to 8-bit sRGB, and TextLayoutResult.getPathForRange returns selection boxes, not glyphs.
 */
internal class HdrGlyphHalo {
    private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    private var blurKey = -1

    fun draw(
        scope: androidx.compose.ui.graphics.drawscope.DrawScope,
        layout: androidx.compose.ui.text.TextLayoutResult,
        typeface: android.graphics.Typeface?,
        fontSizePx: Float,
        letterSpacingEm: Float,
        color: Color,
        alpha: Float,
        blurRadius: Float,
        gain: Float
    ) {
        if (alpha <= 0f || fontSizePx <= 0f) return
        val text = layout.layoutInput.text.text
        if (text.isEmpty()) return
        val key = (blurRadius * 2f).toInt()
        if (key != blurKey) {
            blurKey = key
            paint.maskFilter = if (blurRadius >= 0.5f) {
                android.graphics.BlurMaskFilter(blurRadius, android.graphics.BlurMaskFilter.Blur.NORMAL)
            } else null
        }
        paint.typeface = typeface
        paint.textSize = fontSizePx
        paint.letterSpacing = letterSpacingEm
        paint.setColor(LyricHdrWindow.extendedColor(color.red, color.green, color.blue, alpha, gain))
        scope.drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            for (line in 0 until layout.lineCount) {
                val start = layout.getLineStart(line)
                val end = layout.getLineEnd(line, visibleEnd = true)
                if (end <= start) continue
                native.drawText(text, start, end, layout.getLineLeft(line), layout.getLineBaseline(line), paint)
            }
        }
    }
}

/** Flamingo applies the letter wave to held words of 2–10 characters lasting at least one second. */
private fun flamingoWaveEligible(referenceMotion: Boolean, renderWord: AppleMusicRenderWord, letterCount: Int): Boolean =
    referenceMotion && renderWord.sustainEndMs != null &&
        renderWord.sustainDurationMs >= 1000L && letterCount in 2..10

/** smoothstep((duration - 1000) / 2000): longer notes get the fuller 1.12× swell. */
internal fun flamingoWaveStrength(durationMs: Long): Float {
    val t = ((durationMs - 1000L) / 2000f).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** Unsung and completed units need one text pass, rather than one full pass per letter. */
internal fun flamingoWaveActiveAt(referenceMotion: Boolean, renderWord: AppleMusicRenderWord, letterCount: Int, positionMs: Long): Boolean =
    flamingoWaveEligible(referenceMotion, renderWord, letterCount) &&
        positionMs > renderWord.word.startMs && positionMs < (renderWord.sustainEndMs ?: renderWord.word.endMs)
