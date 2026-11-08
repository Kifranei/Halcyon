package com.ella.music.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun CoverFlowLyricPreview(
    lyrics: List<LyricLine>, index: Int, sampledMs: Long, playing: Boolean, offsetMs: Long,
    showTranslation: Boolean, font: FontFamily?, translationFont: FontFamily?, onSeek: (Long) -> Unit
) {
    val position = rememberLyricFramePosition(sampledMs, playing, offsetMs = offsetMs)
    val gaps = remember(lyrics) { lyrics.interludes() }
    val waiting by remember(gaps, position) {
        derivedStateOf { gaps.firstOrNull { it.isActiveAt(position.value) } }
    }
    val gap = waiting
    if (gap != null) {
        AppleMusicInterlude(gap, sampledMs, LocalPlayerContentColor.current, TextAlign.Center,
            touchFeedbackEnabled = false, onSeek = onSeek, positionState = position)
    } else Crossfade(targetState = index, label = "coverOverlayLyric") { lineIndex ->
        // Neighbouring lines join the active row: one dimmed line above and one below,
        // the stack centred so the singing line always sits in the middle.
        val neighborTint = LocalPlayerContentColor.current.copy(alpha = 0.5f)
        Column(
            Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            lyrics.getOrNull(lineIndex - 1)?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, color = neighborTint, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    fontFamily = font, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth())
            }
            val line = lyrics.getOrNull(lineIndex)
            Text(line?.text?.trim().orEmpty(), color = LocalPlayerContentColor.current.copy(alpha = 0.92f),
                fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = font,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            line?.translation?.trim()?.takeIf { showTranslation && it.isNotEmpty() }?.let {
                Text(it, color = LocalPlayerContentColor.current.copy(alpha = 0.55f), fontSize = 13.sp,
                    fontWeight = FontWeight.Bold, fontFamily = translationFont, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp))
            }
            lyrics.getOrNull(lineIndex + 1)?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                Text(it, color = neighborTint, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    fontFamily = font, textAlign = TextAlign.Center,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
