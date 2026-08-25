package com.ella.music.ui.player

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
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.primaryEndMs
import kotlin.math.PI

/** Apple Music-style instrumental marker, reduced slightly for compact lyric surfaces. */
@Composable
internal fun AppleMusicInterlude(
    interlude: AppleMusicInterlude,
    positionMs: Long,
    contentColor: Color,
    textAlign: TextAlign,
    touchFeedbackEnabled: Boolean,
    onSeek: (Long) -> Unit
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
                .height(48.dp)
                .appleMusicTouchRipple(
                    key = interlude,
                    color = contentColor,
                    feedbackEnabled = touchFeedbackEnabled,
                    onTap = { offset, width ->
                        val fraction = (offset.x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)
                        onSeek(
                            interlude.startMs +
                                ((interlude.endMs - interlude.startMs) * fraction).toLong()
                        )
                    }
                ),
            contentAlignment = when (textAlign) {
                TextAlign.End -> Alignment.CenterEnd
                TextAlign.Center -> Alignment.Center
                else -> Alignment.CenterStart
            }
        ) {
            val state = resolveAppleMusicInterludeGroupState(interlude, positionMs)
            // Apple's Android renderer animates the three-dot root as one group: a four-second
            // 1.0 -> 1.2 -> 1.0 breath, followed by a 750 ms swell and a 250 ms shrink/fade.
            // Drive it from playback time so pausing and seeking cannot leave a half-finished
            // animation running independently of the lyric clock.
            // Keep real layout space around the scaled child. A graphics-layer scale does not
            // enlarge measurement bounds, so without this inset LazyColumn clips the breathing
            // dots at their top/side edges.
            Box(modifier = Modifier.padding(INTERLUDE_BREATH_INSET_DP.dp)) {
                Row(
                    modifier = Modifier.graphicsLayer {
                        scaleX = state.scale
                        scaleY = state.scale
                        alpha = state.alpha
                    }
                ) {
                    repeat(INTERLUDE_DOT_COUNT) { visualIndex ->
                        val timelineIndex = if (textAlign == TextAlign.End) {
                            INTERLUDE_DOT_COUNT - 1 - visualIndex
                        } else {
                            visualIndex
                        }
                        val timelineAlpha = resolveAppleMusicInterludeDotAlpha(
                            interlude = interlude,
                            positionMs = positionMs,
                            dotIndex = timelineIndex
                        )
                        Canvas(modifier = Modifier.size(INTERLUDE_DOT_CELL_DP.dp)) {
                            drawCircle(
                                color = contentColor.copy(
                                    alpha = INTERLUDE_DOT_ALPHA * timelineAlpha
                                ),
                                radius = INTERLUDE_DOT_RADIUS_DP.dp.toPx()
                            )
                        }
                    }
                }
            }
        }
    }
}

private const val INTERLUDE_MIN_GAP_MS = 7_000L
private const val INTERLUDE_DOT_COUNT = 3
private const val INTERLUDE_BREATH_MS = 4_000L
private const val INTERLUDE_EXIT_SWELL_MS = 750L
private const val INTERLUDE_EXIT_SHRINK_MS = 250L
private const val INTERLUDE_DOT_FINAL_FADE_MS = 750L
private const val INTERLUDE_DOT_CELL_DP = 11
private const val INTERLUDE_DOT_RADIUS_DP = 3.5f
private const val INTERLUDE_DOT_ALPHA = 0.85f
private const val INTERLUDE_BREATH_INSET_DP = 4

internal data class AppleMusicInterludeGroupState(
    val scale: Float,
    val alpha: Float
)

internal fun resolveAppleMusicInterludeGroupState(
    interlude: AppleMusicInterlude,
    positionMs: Long
): AppleMusicInterludeGroupState {
    val remainingMs = (interlude.endMs - positionMs).coerceAtLeast(0L)
    val elapsedMs = (positionMs - interlude.startMs).coerceAtLeast(0L)
    val totalExitMs = INTERLUDE_EXIT_SWELL_MS + INTERLUDE_EXIT_SHRINK_MS
    if (remainingMs <= INTERLUDE_EXIT_SHRINK_MS) {
        val fraction = remainingMs.toFloat() / INTERLUDE_EXIT_SHRINK_MS
        return AppleMusicInterludeGroupState(
            scale = 0.5f + 0.7f * fraction,
            alpha = fraction
        )
    }
    if (remainingMs <= totalExitMs) {
        val fraction = (totalExitMs - remainingMs).toFloat() / INTERLUDE_EXIT_SWELL_MS
        return AppleMusicInterludeGroupState(
            scale = 1f + 0.2f * fraction.coerceIn(0f, 1f),
            alpha = 1f
        )
    }
    val phase = (elapsedMs % INTERLUDE_BREATH_MS).toFloat() /
        INTERLUDE_BREATH_MS.toFloat() * 2f * PI.toFloat()
    return AppleMusicInterludeGroupState(
        scale = 1.1f - 0.1f * kotlin.math.cos(phase),
        alpha = 1f
    )
}

/**
 * Apple divides the instrumental duration (plus its final 750 ms colour fade) into three
 * consecutive dot windows. Model that from playback time so seeking/pausing remains exact while
 * the dots retire one by one instead of all disappearing together at the next lyric.
 */
internal fun resolveAppleMusicInterludeDotAlpha(
    interlude: AppleMusicInterlude,
    positionMs: Long,
    dotIndex: Int
): Float {
    val durationMs = (interlude.endMs - interlude.startMs).coerceAtLeast(1L).toFloat()
    val segmentMs = (durationMs + INTERLUDE_DOT_FINAL_FADE_MS) / INTERLUDE_DOT_COUNT
    val safeIndex = dotIndex.coerceIn(0, INTERLUDE_DOT_COUNT - 1)
    val startMs = safeIndex * segmentMs
    val endMs = if (safeIndex == INTERLUDE_DOT_COUNT - 1) {
        durationMs
    } else {
        (safeIndex + 1) * segmentMs
    }
    val elapsedMs = (positionMs - interlude.startMs).coerceAtLeast(0L).toFloat()
    if (elapsedMs <= startMs) return 1f
    if (elapsedMs >= endMs) return 0f
    return 1f - ((elapsedMs - startMs) / (endMs - startMs).coerceAtLeast(1f))
}

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
