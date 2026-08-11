package com.ella.music.ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import kotlin.math.PI
import kotlin.math.sin
import top.yukonga.miuix.kmp.basic.Text

/**
 * KTV lyrics use the two active vocal tracks when a TTML duet is available. For ordinary lyrics,
 * the incoming phrase is at the lower start and the sung phrase is at the lower end.
 */
@Composable
internal fun MusicVideoKtvLyrics(
    lyrics: List<LyricLine>,
    position: Long,
    videoAspectRatio: Float?,
    fillVideoBounds: Boolean = false,
    avoidBottomStartContent: Boolean = false,
    outlined: Boolean = false,
    alternateCurrentAndNext: Boolean = false,
    modifier: Modifier = Modifier
) {
    val timeline = visibleKtvLyricsTimeline(lyrics)
    val currentIndex = timeline.indexOfLast { it.timeMs <= position }
    val current = timeline.getOrNull(currentIndex)
    val currentEndMs = current?.ktvEndMs()
    val next = timeline.getOrNull(currentIndex + 1)
    val currentAtStart = alternateCurrentAndNext && currentIndex % 2 == 0
    val currentAlignment = if (currentAtStart) Alignment.BottomStart else Alignment.BottomEnd
    val currentTextAlign = if (currentAtStart) TextAlign.Start else TextAlign.End
    val nextAlignment = if (currentAtStart) Alignment.BottomEnd else Alignment.BottomStart
    val nextTextAlign = if (currentAtStart) TextAlign.End else TextAlign.Start
    BoxWithConstraints(modifier = modifier) {
        val screenRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val frameModifier = when {
            fillVideoBounds || videoAspectRatio == null -> Modifier.fillMaxSize()
            videoAspectRatio >= screenRatio -> Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
            else -> Modifier.fillMaxHeight().aspectRatio(videoAspectRatio)
        }.align(Alignment.Center)
        Box(
            modifier = frameModifier.align(Alignment.Center)
        ) {
            val isInterlude = currentEndMs != null && currentEndMs <= position &&
                next != null && next.timeMs - currentEndMs >= 7_000L
            if (isInterlude) {
                KtvInterlude(
                    position = position,
                    startMs = currentEndMs,
                    endMs = next.timeMs,
                    modifier = Modifier
                        .align(if (avoidBottomStartContent) Alignment.BottomEnd else currentAlignment)
                        .padding(
                            start = if (avoidBottomStartContent || !currentAtStart) 0.dp else 44.dp,
                            end = if (avoidBottomStartContent || currentAtStart) 44.dp else 0.dp,
                            bottom = 54.dp
                        )
                )
            } else {
                if (!avoidBottomStartContent) {
                    next?.let { nextLine ->
                        KtvOutlinedText(
                            text = AnnotatedString(nextLine.text),
                            color = KtvBlue.copy(alpha = 0.94f),
                            textAlign = nextTextAlign,
                            outlined = outlined,
                            modifier = Modifier
                                .align(nextAlignment)
                                .fillMaxWidth(0.48f)
                                .padding(
                                    start = if (currentAtStart) 0.dp else 42.dp,
                                    end = if (currentAtStart) 42.dp else 0.dp,
                                    bottom = 54.dp
                                )
                        )
                    }
                }
                current?.let { currentLine ->
                    KtvLyricLine(
                        line = currentLine,
                        position = position,
                        textAlign = currentTextAlign,
                        outlined = outlined,
                        modifier = Modifier
                            .align(currentAlignment)
                            .fillMaxWidth(0.48f)
                            .padding(
                                start = if (currentAtStart) 42.dp else 0.dp,
                                end = if (currentAtStart) 0.dp else 42.dp,
                                bottom = 54.dp
                            )
                    )
                }
            }
        }
    }
}

@Composable
private fun KtvLyricLine(
    line: LyricLine,
    position: Long,
    textAlign: TextAlign,
    outlined: Boolean,
    modifier: Modifier = Modifier
) {
    val text = line.text
    if (text.isBlank()) return
    val completedWordCount = line.words.count { it.endMs <= position }
    val hasWordTiming = line.words.isNotEmpty() && line.words.joinToString(separator = "") { it.text }
        .replace(" ", "") == text.replace(" ", "")
    val annotated = buildAnnotatedString {
        if (hasWordTiming) {
            line.words.forEachIndexed { index, word ->
                withStyle(SpanStyle(color = if (index < completedWordCount) KtvSungYellow else KtvBlue)) {
                    append(word.text)
                }
            }
        } else {
            val end = line.ktvEndMs()
            val progress = ((position - line.timeMs).toFloat() / (end - line.timeMs).coerceAtLeast(1L))
                .coerceIn(0f, 1f)
            val split = (text.length * progress).toInt().coerceIn(0, text.length)
            withStyle(SpanStyle(color = KtvSungYellow)) { append(text.take(split)) }
            withStyle(SpanStyle(color = KtvBlue)) { append(text.drop(split)) }
            if (text.isEmpty()) {
                withStyle(SpanStyle(color = KtvBlue)) {
                    append(text)
                }
            }
        }
    }
    KtvOutlinedText(text = annotated, textAlign = textAlign, outlined = outlined, modifier = modifier)
}

/** Returns only the visible lyric track; TTML x-bg remains metadata for the normal player. */
private fun visibleKtvLyricsTimeline(lyrics: List<LyricLine>): List<LyricLine> =
    lyrics.filter { it.text.isNotBlank() }.sortedBy { it.timeMs }

private fun LyricLine.ktvEndMs(): Long =
    (words.maxOfOrNull { it.endMs } ?: endMs ?: (timeMs + 4_000L)).coerceAtLeast(timeMs + 1L)

private val KtvBlue = Color(0xFF2F6BFF)
private val KtvSungYellow = Color(0xFFFFE600)

@Composable
private fun KtvOutlinedText(
    text: AnnotatedString,
    color: Color = KtvBlue,
    textAlign: TextAlign,
    outlined: Boolean,
    modifier: Modifier = Modifier
) {
    val fontSize = when {
        text.text.length > 52 -> 26.sp
        text.text.length > 34 -> 30.sp
        else -> 38.sp
    }
    if (outlined) {
        Text(
            text = AnnotatedString(text.text),
            color = Color.White,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            textAlign = textAlign,
            maxLines = 4,
            style = TextStyle(drawStyle = Stroke(width = 5f)),
            modifier = modifier.fillMaxWidth()
        )
    }
    Text(
        text = text,
        color = color,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        textAlign = textAlign,
        maxLines = 4,
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun KtvInterlude(
    position: Long,
    startMs: Long,
    endMs: Long,
    modifier: Modifier = Modifier
) {
    val progress = ((position - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L))
        .coerceIn(0f, 1f)
    val pulse = 1f + 0.1f * sin(((position - startMs).toFloat() / 4_000f) * 2f * PI.toFloat())
    Row(modifier = modifier) {
        repeat(4) { index ->
            val alpha = 0.20f + 0.74f * ((progress - index / 4f) * 4f).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.size(22.dp)) {
                drawCircle(
                    color = Color.White.copy(alpha = alpha),
                    radius = 7.dp.toPx() * pulse
                )
                drawCircle(
                    color = KtvBlue.copy(alpha = alpha),
                    radius = 5.dp.toPx() * pulse
                )
            }
        }
    }
}
