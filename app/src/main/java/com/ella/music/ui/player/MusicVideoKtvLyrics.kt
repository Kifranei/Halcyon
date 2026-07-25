package com.ella.music.ui.player

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
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
    modifier: Modifier = Modifier
) {
    val currentIndex = lyrics.indexOfLast { it.timeMs <= position }
    val current = lyrics.getOrNull(currentIndex) ?: return
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
            next?.takeIf { it.text.isNotBlank() }?.let { nextLine ->
                Text(
                    text = nextLine.text,
                    color = Color.White.copy(alpha = 0.94f),
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.End,
                    maxLines = 2,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth(0.72f)
                        .padding(end = 38.dp, bottom = 108.dp)
                )
            }
            KtvLyricLine(
                line = current,
                nextLine = next,
                position = position,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(0.82f)
                    .padding(start = 38.dp, bottom = 38.dp)
            )
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
                withStyle(SpanStyle(color = if (index < completedWordCount) Color(0xFFFFE600) else Color(0xFF2F6BFF))) {
                    append(word.text)
                }
            }
        } else {
            val end = line.primaryEndMs(nextLine)
            val progress = ((position - line.timeMs).toFloat() / (end - line.timeMs).coerceAtLeast(1L))
                .coerceIn(0f, 1f)
            val split = (text.length * progress).toInt().coerceIn(0, text.length)
            withStyle(SpanStyle(color = Color(0xFFFFE600))) { append(text.take(split)) }
            withStyle(SpanStyle(color = Color(0xFF2F6BFF))) { append(text.drop(split)) }
            if (text.isEmpty()) {
                withStyle(SpanStyle(color = Color(0xFF2F6BFF))) {
                    append(text)
                }
            }
        }
    }
    Text(
        text = annotated,
        color = Color.White,
        fontSize = 43.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Start,
        maxLines = 2,
        modifier = modifier
    )
}
