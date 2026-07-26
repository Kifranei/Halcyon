package com.ella.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.primaryEndMs
import kotlin.math.PI
import kotlin.math.sin

/** Matches Apple Music's instrumental marker: three 10dp dots, separated by 6dp. */
@Composable
internal fun AppleMusicInterlude(
    interlude: AppleMusicInterlude,
    positionMs: Long,
    contentColor: Color,
    textAlign: TextAlign
) {
    val visible = interlude.isActiveAt(positionMs)
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically(spring(dampingRatio = 0.78f, stiffness = 360f)) + fadeIn(),
        exit = shrinkVertically(spring(dampingRatio = 0.9f, stiffness = 480f)) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            contentAlignment = when (textAlign) {
                TextAlign.End -> Alignment.CenterEnd
                TextAlign.Center -> Alignment.Center
                else -> Alignment.CenterStart
            }
        ) {
            Row {
                // Match the legacy renderer's Apple Music-inspired four-second breath,
                // while keeping the compact dot group within a restrained 0.9x–1.1x range.
                val pulseScale = 1f + 0.1f * sin(
                    ((positionMs - interlude.startMs).toFloat() / 4_000f) * 2f * PI.toFloat()
                )
                val progress = ((positionMs - interlude.startMs).toFloat() /
                    (interlude.endMs - interlude.startMs - 800L).coerceAtLeast(1L))
                    .coerceIn(0f, 1f)
                // Each dot gets a 16dp cell (10dp dot + 6dp spacing).  Scaling the drawing
                // within that cell reserves enough room for the 1.1x breath and prevents the
                // third dot from being clipped by Compose's animated item layer.
                Row {
                    repeat(3) { index ->
                        val dotProgress = ((progress - index / 3f) * 3f).coerceIn(0f, 1f)
                        val dotAlpha by animateFloatAsState(
                            targetValue = 0.18f + 0.67f * dotProgress,
                            animationSpec = spring(dampingRatio = 0.9f, stiffness = 440f),
                            label = "appleInterludeDot$index"
                        )
                        Canvas(modifier = Modifier.size(16.dp)) {
                            drawCircle(
                                color = contentColor.copy(alpha = dotAlpha),
                                radius = 5.dp.toPx() * pulseScale
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val INTERLUDE_MIN_GAP_MS = 7_000L

internal data class AppleMusicInterlude(
    val startMs: Long,
    val endMs: Long,
    val nextLineIndex: Int
) {
    fun isActiveAt(positionMs: Long): Boolean = positionMs in startMs until endMs
}

internal fun List<LyricLine>.interludes(): List<AppleMusicInterlude> {
    if (isEmpty()) return emptyList()
    val lines = this
    return buildList {
        lines.first().takeIf { it.timeMs >= INTERLUDE_MIN_GAP_MS }?.let { firstLine ->
            add(AppleMusicInterlude(startMs = 0L, endMs = firstLine.timeMs, nextLineIndex = 0))
        }
        for (index in 1..lines.lastIndex) {
            val previous = lines[index - 1]
            val next = lines[index]
            val gapStart = previous.primaryEndMs(nextLine = next)
            if (next.timeMs - gapStart >= INTERLUDE_MIN_GAP_MS) {
                add(AppleMusicInterlude(startMs = gapStart, endMs = next.timeMs, nextLineIndex = index))
            }
        }
    }
}
