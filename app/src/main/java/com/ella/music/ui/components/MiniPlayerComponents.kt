package com.ella.music.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
import kotlinx.coroutines.isActive
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun MiniPlayerAnimatedText(
    textState: MiniPlayerTextState,
    transitionDirection: Int,
    lyricProgress: Float,
    lyricPositionMs: Long,
    lyricTiming: MiniPlayerLyricTiming?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    primaryFontSize: Int = 14,
    primaryFontWeight: FontWeight = FontWeight.Medium,
    secondaryFontSize: Int = 12
) {
    // PlayerViewModel deliberately samples the controller at a modest cadence.  Keep the
    // high-frequency interpolation local to this tiny canvas instead of invalidating the
    // whole main screen for every animation frame.
    val smoothedPositionMs = rememberSmoothedMiniPlayerLyricPosition(
        sampledPositionMs = lyricPositionMs,
        isPlaying = isPlaying,
        timing = lyricTiming
    )
    AnimatedContent(
        targetState = textState,
        transitionSpec = {
            val direction = transitionDirection
            val outOffset = { width: Int -> -direction * width / 3 }
            val inOffset = { width: Int -> direction * width / 3 }
            val enter = slideInHorizontally(
                animationSpec = tween(450, easing = FastOutSlowInEasing),
                initialOffsetX = inOffset
            ) + fadeIn(
                animationSpec = tween(450, easing = FastOutSlowInEasing),
                initialAlpha = 0.15f
            )
            val exit = slideOutHorizontally(
                animationSpec = tween(300, easing = FastOutLinearInEasing),
                targetOffsetX = outOffset
            ) + fadeOut(
                animationSpec = tween(300, easing = FastOutLinearInEasing),
                targetAlpha = 0f
            )
            enter togetherWith exit using SizeTransform(clip = false)
        },
        label = "MiniPlayerSongText",
        modifier = modifier
    ) { state ->
        Column(modifier = Modifier.fillMaxWidth()) {
            MiniPlayerTextRow(
                text = state.primary,
                explicit = state.primaryIsExplicit,
                fontSize = primaryFontSize,
                fontWeight = primaryFontWeight,
                color = if (state.showingLyric) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                enabled = state.showingLyric,
                highlightWithProgress = state.showingLyric,
                fallbackProgress = lyricProgress,
                smoothedPositionMs = smoothedPositionMs,
                lyricTiming = lyricTiming,
                wordTiming = if (state.showingLyric) lyricTiming?.words.orEmpty() else emptyList<LyricWord>()
            )
            MiniPlayerTextRow(
                text = state.secondary,
                explicit = state.secondaryIsExplicit,
                fontSize = secondaryFontSize,
                fontWeight = FontWeight.Normal,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                enabled = state.scrollSecondary,
                highlightWithProgress = state.highlightSecondaryWithProgress,
                fallbackProgress = lyricProgress,
                smoothedPositionMs = smoothedPositionMs,
                lyricTiming = lyricTiming,
                wordTiming = emptyList<LyricWord>()
            )
        }
    }
}

@Composable
private fun MiniPlayerTextRow(
    text: String,
    explicit: Boolean,
    fontSize: Int,
    fontWeight: FontWeight,
    color: Color,
    enabled: Boolean,
    highlightWithProgress: Boolean,
    fallbackProgress: Float,
    smoothedPositionMs: State<Long>,
    lyricTiming: MiniPlayerLyricTiming?,
    wordTiming: List<LyricWord>
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val textStyle = remember(fontSize, fontWeight) {
            TextStyle(fontSize = fontSize.sp, fontWeight = fontWeight)
        }
        val measuredTextWidth = remember(text, textStyle, textMeasurer, density) {
            with(density) {
                textMeasurer.measure(
                    text = AnnotatedString(text),
                    style = textStyle,
                    maxLines = 1,
                    softWrap = false
                ).size.width.toDp()
            }
        }
        // Avoid reserving the whole row for a short title: the advisory badge belongs right
        // beside its title, while an overflowing title still receives all remaining marquee room.
        val badgeReservation = if (explicit) 20.dp else 0.dp
        val textWidth = measuredTextWidth.coerceAtLeast(1.dp)
            .coerceAtMost((maxWidth - badgeReservation).coerceAtLeast(1.dp))
        Row(
            modifier = Modifier.width(textWidth + badgeReservation),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AutoScrollingMiniText(
                text = text,
                fontSize = fontSize,
                fontWeight = fontWeight,
                color = color,
                enabled = enabled,
                highlightWithProgress = highlightWithProgress,
                fallbackProgress = fallbackProgress,
                smoothedPositionMs = smoothedPositionMs,
                lyricTiming = lyricTiming,
                wordTiming = wordTiming,
                modifier = Modifier.width(textWidth)
            )
            if (explicit) {
                Spacer(modifier = Modifier.width(2.dp))
                ExplicitBadge(contentColor = color, height = 12.dp)
            }
        }
    }
}

@Composable
internal fun MiniPlayerCoverProgress(
    coverState: MiniPlayerCoverState,
    isPlaying: Boolean,
    progress: Float,
    coverRotationEnabled: Boolean,
    coverSize: Dp,
    ringSize: Dp,
    modifier: Modifier = Modifier
) {
    val coverModel = coverState.model
    var coverRotation by remember(coverModel) { mutableFloatStateOf(0f) }

    LaunchedEffect(coverModel, isPlaying, coverRotationEnabled) {
        if (!coverRotationEnabled) {
            coverRotation = 0f
            return@LaunchedEffect
        }
        if (!isPlaying) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val elapsedMs = (frameNanos - lastFrameNanos) / 1_000_000f
                    coverRotation = (coverRotation + elapsedMs * 360f / 20_000f) % 360f
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(coverSize)
                .graphicsLayer { rotationZ = coverRotation }
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.surface),
            contentAlignment = Alignment.Center
        ) {
            if (coverModel != null) {
                SafeCoverImage(
                    model = coverModel,
                    contentDescription = null,
                    modifier = Modifier
                        .size(coverSize)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                    sizePx = 128,
                    showDefaultPlaceholder = false
                )
            } else if (coverState.showDefaultCover) {
                DefaultAlbumCover(modifier = Modifier.size(coverSize))
            }
        }
        CircularProgressRing(
            progress = progress,
            color = MiuixTheme.colorScheme.primary,
            trackColor = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            modifier = Modifier.size(ringSize)
        )
    }
}

@Composable
private fun CircularProgressRing(
    progress: Float,
    color: Color,
    trackColor: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier.graphicsLayer { rotationZ = -90f }) {
        val strokeWidth = 2.dp.toPx()
        val inset = strokeWidth / 2f
        val arcSize = size.copy(width = size.width - strokeWidth, height = size.height - strokeWidth)
        drawArc(
            color = trackColor,
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = 0f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(inset, inset),
            size = arcSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

@Composable
internal fun rememberMiniPlayerCoverModel(
    song: Song,
    albumArtUri: Uri?,
    loadCoverArt: ((Song) -> Bitmap?)?
): MiniPlayerCoverState {
    val coverState = rememberSongArtworkState(
        song = song,
        albumArtUri = albumArtUri,
        loadCoverArt = loadCoverArt,
        usage = ArtworkUsage.MiniPlayer
    )
    return MiniPlayerCoverState(
        model = coverState.model,
        showDefaultCover = coverState.showDefaultCover
    )
}

internal data class MiniPlayerCoverState(
    val model: Any?,
    val showDefaultCover: Boolean
)

internal fun rememberMiniPlayerTextState(
    song: Song,
    lyricText: String?,
    lyricTranslation: String?
): MiniPlayerTextState {
    val songTitle = song.title.toSongTitlePresentation()
    val hasTranslation = !lyricTranslation.isNullOrBlank()
    return MiniPlayerTextState(
        songId = song.id,
        primary = lyricText ?: songTitle.text,
        secondary = when {
            lyricText != null && hasTranslation -> lyricTranslation.orEmpty()
            lyricText != null -> "${songTitle.text} - ${song.artist}"
            else -> song.artist
        },
        showingLyric = lyricText != null,
        scrollSecondary = lyricText != null && hasTranslation,
        highlightSecondaryWithProgress = false,
        primaryIsExplicit = lyricText == null && songTitle.isExplicit,
        secondaryIsExplicit = lyricText != null && !hasTranslation && songTitle.isExplicit
    )
}

@Composable
private fun AutoScrollingMiniText(
    text: String,
    fontSize: Int,
    fontWeight: FontWeight,
    color: Color,
    enabled: Boolean,
    highlightWithProgress: Boolean,
    fallbackProgress: Float,
    smoothedPositionMs: State<Long>,
    lyricTiming: MiniPlayerLyricTiming?,
    wordTiming: List<LyricWord>,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val highlightedColor = MiuixTheme.colorScheme.onSurface
    var autoScrollElapsedMs by remember(text, enabled) { mutableFloatStateOf(0f) }

    LaunchedEffect(text, enabled) {
        autoScrollElapsedMs = 0f
        if (!enabled) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    autoScrollElapsedMs += (frameNanos - lastFrameNanos) / 1_000_000f
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    val textStyle = TextStyle(
        fontSize = fontSize.sp,
        fontWeight = fontWeight
    )
    val textLayout = remember(text, textStyle, textMeasurer) {
        textMeasurer.measure(
            text = AnnotatedString(text),
            style = textStyle,
            maxLines = 1,
            softWrap = false
        )
    }
    val canvasHeight = with(density) { textLayout.size.height.toDp() }
    val horizontalPaddingPx = with(density) { 2.dp.toPx() }
    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(canvasHeight)
            .clipToBounds()
    ) {
        val viewportPx = size.width.toInt().coerceAtLeast(1)
        val textWidth = textLayout.size.width.toFloat()
        val smoothPositionMs = smoothedPositionMs.value
        val timingProgress = lyricTiming?.progressAt(smoothPositionMs)
        val safeProgress = if (highlightWithProgress && wordTiming.isNotEmpty() && lyricTiming != null) {
            miniPlayerWordProgress(
                text = text,
                textLayout = textLayout,
                words = wordTiming,
                positionMs = smoothPositionMs,
                fallback = timingProgress
            )
        } else {
            timingProgress ?: fallbackProgress
        }.coerceIn(0f, 1f)
        val overflowPx = (textWidth - size.width).coerceAtLeast(0f)
        val scrollProgress = if (enabled && overflowPx > 0f) {
            miniMarqueeProgress(
                progress = safeProgress,
                overflowPx = overflowPx,
                viewportPx = viewportPx,
                autoScrollElapsedMs = autoScrollElapsedMs
            )
        } else {
            0f
        }
        val offsetPx = overflowPx * scrollProgress
        val highlightRight = (textWidth * safeProgress - offsetPx + horizontalPaddingPx)
            .coerceIn(0f, size.width)
        val topLeft = Offset(-offsetPx, 0f)

        drawText(
            textLayoutResult = textLayout,
            color = color,
            topLeft = topLeft
        )
        if (enabled && highlightWithProgress && highlightRight > 0f) {
            // Draw a solid sung section, then blend it into the dim copy.  The previous
            // rectangular clip made every word boundary snap hard in the mini player.
            // Once the last word is complete, do not leave the trailing feather visible:
            // its transparent edge would otherwise keep the final glyphs permanently dim.
            val featherWidth = if (safeProgress >= 0.995f) 0f else {
                with(density) { 18.dp.toPx() }.coerceAtMost(highlightRight)
            }
            val solidHighlightRight = (highlightRight - featherWidth).coerceAtLeast(0f)
            clipRect(
                left = 0f,
                top = 0f,
                right = solidHighlightRight,
                bottom = size.height
            ) {
                drawText(
                    textLayoutResult = textLayout,
                    color = highlightedColor,
                    topLeft = topLeft
                )
            }
            if (featherWidth > 0f) {
                clipRect(
                    left = solidHighlightRight,
                    top = 0f,
                    right = highlightRight,
                    bottom = size.height
                ) {
                    drawText(
                        textLayoutResult = textLayout,
                        brush = Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0f to highlightedColor,
                                1f to Color.Transparent
                            ),
                            startX = solidHighlightRight,
                            endX = highlightRight
                        ),
                        topLeft = topLeft
                    )
                }
            }
        }
    }
}

/** Timing data for a displayed mini-player lyric line. */
data class MiniPlayerLyricTiming(
    val lineStartMs: Long,
    val lineEndMs: Long,
    val words: List<LyricWord>
) {
    fun progressAt(positionMs: Long): Float {
        val effectiveEnd = maxOf(lineEndMs, words.maxOfOrNull { it.endMs } ?: lineEndMs)
        return ((positionMs - lineStartMs).toFloat() /
            (effectiveEnd - lineStartMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
    }
}

@Composable
private fun rememberSmoothedMiniPlayerLyricPosition(
    sampledPositionMs: Long,
    isPlaying: Boolean,
    timing: MiniPlayerLyricTiming?
): State<Long> {
    val smoothPositionMs = remember(timing?.lineStartMs, timing?.lineEndMs) {
        mutableLongStateOf(sampledPositionMs)
    }

    LaunchedEffect(sampledPositionMs, isPlaying, timing?.lineStartMs, timing?.lineEndMs) {
        if (!isPlaying || timing == null) {
            smoothPositionMs.longValue = sampledPositionMs
            return@LaunchedEffect
        }

        val anchorPositionMs = sampledPositionMs
        val terminalPositionMs = maxOf(timing.lineEndMs, timing.words.maxOfOrNull { it.endMs } ?: timing.lineEndMs)
        var anchorFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (anchorFrameNanos == 0L) anchorFrameNanos = frameNanos
                smoothPositionMs.longValue = (anchorPositionMs +
                    (frameNanos - anchorFrameNanos) / 1_000_000L)
                    .coerceAtMost(terminalPositionMs)
            }
        }
    }
    return smoothPositionMs
}

/**
 * Converts word timing to the same horizontal extent that the mini player draws.  This avoids
 * treating a short word and a long word as equal-width chunks, which was the visible source of
 * the old step-like karaoke sweep.
 */
private fun miniPlayerWordProgress(
    text: String,
    textLayout: TextLayoutResult,
    words: List<LyricWord>,
    positionMs: Long,
    fallback: Float?
): Float {
    if (text.isBlank() || words.isEmpty()) return fallback ?: 0f
    val lastWordEndMs = words.maxOfOrNull { it.endMs } ?: return fallback ?: 0f
    if (positionMs >= lastWordEndMs) return 1f

    var highlightedRight = 0f
    val wordRanges = miniPlayerWordCharacterRanges(text, words)
    words.zip(wordRanges).forEach { (word, range) ->
        val start = range.first
        val endExclusive = range.last + 1
        if (endExclusive <= start) return@forEach

        val wordLeft = textLayout.getBoundingBox(start).left
        val wordRight = textLayout.getBoundingBox(endExclusive - 1).right
        when {
            positionMs >= word.endMs -> highlightedRight = maxOf(highlightedRight, wordRight)
            positionMs > word.startMs -> {
                val fraction = ((positionMs - word.startMs).toFloat() /
                    (word.endMs - word.startMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
                highlightedRight = maxOf(
                    highlightedRight,
                    wordLeft + (wordRight - wordLeft) * fraction
                )
            }
        }
    }

    return (highlightedRight / textLayout.size.width.coerceAtLeast(1)).coerceIn(0f, 1f)
}

/**
 * Match TTML/ELRC words to the exact text rendered by the mini player. Some sources differ only
 * in whitespace, punctuation, Unicode apostrophes, or a cleaned display string. Preserve the
 * timed word path in all of those cases instead of falling back to a whole-line sweep.
 */
internal fun miniPlayerWordCharacterRanges(text: String, words: List<LyricWord>): List<IntRange> {
    if (text.isEmpty() || words.isEmpty()) return emptyList()

    var cursor = 0
    return words.mapIndexed { index, word ->
        val match = word.text.takeIf { it.isNotEmpty() }
            ?.let { text.indexOf(it, startIndex = cursor) }
            ?.takeIf { it >= 0 }
            ?.let { start -> MiniPlayerWordMatch(start, start + word.text.length) }
            ?: findNormalizedWordMatch(text, word.text, cursor)
        val remainingWords = (words.size - index).coerceAtLeast(1)
        val start = (match?.start ?: cursor).coerceIn(0, text.length)
        val endExclusive = if (match != null) {
            match.endExclusive.coerceIn(start, text.length)
        } else {
            // A malformed word token must not demote the entire line to linear highlighting.
            // Split the remaining displayed characters in source order as a stable fallback.
            (start + ((text.length - start).toFloat() / remainingWords).toInt().coerceAtLeast(1))
                .coerceAtMost(text.length)
        }
        cursor = endExclusive
        start until endExclusive
    }
}

private data class MiniPlayerWordMatch(val start: Int, val endExclusive: Int)

private fun findNormalizedWordMatch(text: String, word: String, cursor: Int): MiniPlayerWordMatch? {
    val normalizedWord = word.normalizedMiniLyricToken()
    if (normalizedWord.isEmpty()) return null

    val compactText = StringBuilder()
    val originalIndexes = mutableListOf<Int>()
    for (index in cursor.coerceAtLeast(0) until text.length) {
        val character = text[index]
        if (character.isLetterOrDigit()) {
            compactText.append(character.lowercaseChar())
            originalIndexes += index
        }
    }
    val compactStart = compactText.indexOf(normalizedWord)
    if (compactStart < 0) return null
    val compactEnd = compactStart + normalizedWord.length
    return MiniPlayerWordMatch(
        start = originalIndexes[compactStart],
        endExclusive = originalIndexes[compactEnd - 1] + 1
    )
}

private fun String.normalizedMiniLyricToken(): String = buildString {
    this@normalizedMiniLyricToken.forEach { character ->
        if (character.isLetterOrDigit()) append(character.lowercaseChar())
    }
}

private fun miniMarqueeProgress(
    progress: Float,
    overflowPx: Float,
    viewportPx: Int,
    autoScrollElapsedMs: Float
): Float {
    val overflowRatio = overflowPx / viewportPx.coerceAtLeast(1).toFloat()
    val startAt = 0.04f
    val endAt = when {
        overflowRatio >= 1.1f -> 0.76f
        overflowRatio >= 0.55f -> 0.82f
        else -> 0.9f
    }
    val lyricDrivenProgress = ((progress - startAt) / (endAt - startAt)).coerceIn(0f, 1f)
    val autoDelayMs = 420f
    val autoScrollSpeedPxPerSecond = (viewportPx * 0.12f).coerceIn(22f, 42f)
    val autoDrivenProgress = (
        ((autoScrollElapsedMs - autoDelayMs).coerceAtLeast(0f) / 1000f) *
            autoScrollSpeedPxPerSecond /
            overflowPx.coerceAtLeast(1f)
        ).coerceIn(0f, 1f)
    return maxOf(lyricDrivenProgress, autoDrivenProgress)
}

internal data class MiniPlayerTextState(
    val songId: Long,
    val primary: String,
    val secondary: String,
    val showingLyric: Boolean,
    val scrollSecondary: Boolean,
    val highlightSecondaryWithProgress: Boolean,
    val primaryIsExplicit: Boolean,
    val secondaryIsExplicit: Boolean
)

@Composable
internal fun PlayerQueueListIcon(
    color: Color,
    contentDescription: String? = null,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(R.drawable.ic_playlist),
        contentDescription = contentDescription,
        tint = color,
        modifier = modifier
    )
}
