package com.ella.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import kotlin.math.abs
import kotlin.math.sin

/** RawS Music-inspired compact waveform/segment timeline adapted to Halcyon's player. */
@Composable
internal fun PlayerWaveformSeekBar(
    value: Float,
    song: Song?,
    duration: Long,
    style: Int,
    onSeek: (Float) -> Unit,
    accent: Color,
    allowTapSeek: Boolean,
    onPreviewProgressChange: (Float?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val safeProgress = value.coerceIn(0f, 1f)
    var draggingProgress by remember { mutableStateOf<Float?>(null) }
    val displayProgress = draggingProgress ?: safeProgress
    val contentColor = LocalPlayerContentColor.current
    val seed = remember(song?.path, song?.id, song?.dateModified, duration) {
        "${song?.path}|${song?.id}|${song?.dateModified}|$duration".hashCode()
    }
    val waveform = remember(seed, style) {
        progressWaveformLevels(
            seed = seed,
            count = if (style == SettingsManager.PLAYER_PROGRESS_STYLE_SEGMENTS) 52 else 76,
            segmented = style == SettingsManager.PLAYER_PROGRESS_STYLE_SEGMENTS
        )
    }

    fun progressAt(width: Float, x: Float): Float =
        (x / width.coerceAtLeast(1f)).coerceIn(0f, 1f)

    Box(modifier = modifier.height(30.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val gap = if (style == SettingsManager.PLAYER_PROGRESS_STYLE_SEGMENTS) 2.3f * density else 1.15f * density
            val slotWidth = size.width / waveform.size.coerceAtLeast(1)
            val barWidth = (slotWidth - gap).coerceAtLeast(1.1f * density)
            val playedX = size.width * displayProgress
            waveform.forEachIndexed { index, level ->
                val x = index * slotWidth + (slotWidth - barWidth) * 0.5f
                val centerX = x + barWidth * 0.5f
                val height = if (style == SettingsManager.PLAYER_PROGRESS_STYLE_SEGMENTS) {
                    (4.5f + level * 5.5f) * density
                } else {
                    (5f * density + level * size.height * 0.76f).coerceAtMost(size.height * 0.92f)
                }
                drawRoundRect(
                    color = if (centerX <= playedX) accent.copy(alpha = 0.92f)
                    else contentColor.copy(alpha = 0.20f),
                    topLeft = Offset(x, (size.height - height) * 0.5f),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth * 0.5f, barWidth * 0.5f)
                )
            }
            val needleWidth = 1.2f * density
            drawRoundRect(
                color = contentColor.copy(alpha = 0.88f),
                topLeft = Offset(playedX - needleWidth * 0.5f, size.height * 0.08f),
                size = Size(needleWidth, size.height * 0.84f),
                cornerRadius = CornerRadius(needleWidth, needleWidth)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(allowTapSeek) {
                    if (!allowTapSeek) return@pointerInput
                    detectTapGestures { offset -> onSeek(progressAt(size.width.toFloat(), offset.x)) }
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            draggingProgress = progressAt(size.width.toFloat(), offset.x)
                                .also(onPreviewProgressChange)
                        },
                        onDragEnd = {
                            draggingProgress?.let(onSeek)
                            draggingProgress = null
                            onPreviewProgressChange(null)
                        },
                        onDragCancel = {
                            draggingProgress = null
                            onPreviewProgressChange(null)
                        }
                    ) { change, _ ->
                        draggingProgress = progressAt(size.width.toFloat(), change.position.x)
                            .also(onPreviewProgressChange)
                    }
                }
        )
    }
}

internal fun progressWaveformLevels(seed: Int, count: Int, segmented: Boolean): List<Float> {
    val safeCount = count.coerceAtLeast(1)
    var state = seed.toLong().let { if (it == 0L) 0x6d2b79f5L else it }
    return List(safeCount) { index ->
        state = state * 1_664_525L + 1_013_904_223L
        val noise = ((state ushr 16) and 0xffff).toFloat() / 65_535f
        if (segmented) {
            0.34f + noise * 0.66f
        } else {
            val envelope = abs(sin((index + (seed and 15)) * 0.19f))
            (0.10f + envelope * 0.48f + noise * 0.42f).coerceIn(0.08f, 1f)
        }
    }
}
