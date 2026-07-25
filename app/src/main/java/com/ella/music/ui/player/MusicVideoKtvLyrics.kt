package com.ella.music.ui.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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

/** KTV-style current and upcoming lyrics, aligned to the same fitted video frame as PlayerView. */
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
        Column(
            modifier = frameModifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 42.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            KtvLyricLine(line = current, nextLine = next, position = position)
            next?.takeIf { it.text.isNotBlank() }?.let {
                Text(
                    text = it.text,
                    color = Color.White.copy(alpha = 0.82f),
                    fontSize = 23.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun KtvLyricLine(line: LyricLine, nextLine: LyricLine?, position: Long) {
    val text = line.text.ifBlank { line.backgroundText.orEmpty() }
    if (text.isBlank()) return
    val completedWordCount = line.words.count { it.endMs <= position }
    val hasWordTiming = line.words.isNotEmpty() && line.words.joinToString(separator = "") { it.text }
        .replace(" ", "") == text.replace(" ", "")
    val annotated = buildAnnotatedString {
        if (hasWordTiming) {
            line.words.forEachIndexed { index, word ->
                withStyle(SpanStyle(color = if (index < completedWordCount) Color(0xFF62E968) else Color.White)) {
                    append(word.text)
                }
            }
        } else {
            val end = line.primaryEndMs(nextLine)
            withStyle(SpanStyle(color = if (position >= end) Color(0xFF62E968) else Color.White)) {
                append(text)
            }
        }
    }
    Text(
        text = annotated,
        color = Color.White,
        fontSize = 31.sp,
        fontWeight = FontWeight.ExtraBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
}
