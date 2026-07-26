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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.primaryEndMs
import kotlin.math.PI
import kotlin.math.sin
import top.yukonga.miuix.kmp.basic.Text

/**
 * KTV lyrics reserve the lower video frame for the line being sung and keep the next line above
 * it. This avoids obscuring the picture with a conventional centered lyric stack.
 */
@Composable
internal fun MusicVideoKtvLyrics(
    lyrics: List<LyricLine>,
    position: Long,
    videoAspectRatio: Float?,
    avoidBottomStartContent: Boolean = false,
    modifier: Modifier = Modifier
) {
    val currentIndex = lyrics.indexOfLast { it.timeMs <= position }
    val current = lyrics.getOrNull(currentIndex)
    val next = lyrics.getOrNull(currentIndex + 1)
    BoxWithConstraints(modifier = modifier) {
        val screenRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        val frameModifier = when {
            videoAspectRatio == null -> Modifier.fillMaxSize()
            videoAspectRatio >= screenRatio -> Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
            else -> Modifier.fillMaxHeight().aspectRatio(videoAspectRatio)
        }.align(Alignment.Center)
        Box(
            modifier = frameModifier.align(Alignment.Center)
        ) {
            val isInterlude = current != null && current.primaryEndMs(next) <= position &&
                next != null && next.timeMs - current.primaryEndMs(next) >= 7_000L
            if (isInterlude) {
                KtvInterlude(
                    position = position,
                    startMs = current.primaryEndMs(next),
                    endMs = next.timeMs,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 44.dp, bottom = 54.dp)
                )
            } else {
                next?.takeIf { it.text.isNotBlank() }?.let { nextLine ->
                Text(
                    text = nextLine.text,
                    color = KtvBlue.copy(alpha = 0.94f),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth(if (avoidBottomStartContent) 0.58f else 0.74f)
                        .padding(end = 42.dp, bottom = 112.dp)
                )
            }
                current?.let { currentLine ->
                    KtvLyricLine(
                        line = currentLine,
                        nextLine = next,
                        position = position,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .fillMaxWidth(if (avoidBottomStartContent) 0.58f else 0.74f)
                            .padding(end = 42.dp, bottom = 42.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun KtvLyricLine(
    line: LyricLine,
    nextLine: LyricLine?,
    position: Long,
    modifier: Modifier = Modifier
) {
    val text = line.text.ifBlank { line.backgroundText.orEmpty() }
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
            val end = line.primaryEndMs(nextLine)
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
    Text(
        text = annotated,
        color = KtvBlue,
        fontSize = 36.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Start,
        maxLines = 2,
        modifier = modifier
    )
}

private val KtvBlue = Color(0xFF2F6BFF)
private val KtvSungYellow = Color(0xFFFFE600)

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
        repeat(3) { index ->
            val alpha = 0.20f + 0.74f * ((progress - index / 3f) * 3f).coerceIn(0f, 1f)
            Canvas(modifier = Modifier.size(18.dp)) {
                drawCircle(
                    color = KtvBlue.copy(alpha = alpha),
                    radius = 5.dp.toPx() * pulse
                )
            }
        }
    }
}
