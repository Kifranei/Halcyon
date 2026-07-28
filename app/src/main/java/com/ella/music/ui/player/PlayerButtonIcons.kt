package com.ella.music.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Icon

internal enum class PlayerHeaderActionKind {
    Favorite,
    More
}

@Composable
internal fun Modifier.playerNoIndicationClick(onClick: () -> Unit): Modifier =
    clickable(
        interactionSource = remember { MutableInteractionSource() },
        indication = null,
        onClick = onClick
    )

@Composable
internal fun PlayerQuickActionRow(
    onSongInfo: () -> Unit,
    onShareSong: () -> Unit,
    onTimer: () -> Unit,
    onEditMetadata: () -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerQuickAction(stringResource(R.string.player_quick_info), PlayerQuickActionKind.Info, onSongInfo)
        PlayerQuickAction(stringResource(R.string.player_quick_share), PlayerQuickActionKind.Share, onShareSong)
        PlayerQuickAction(stringResource(R.string.player_quick_timer), PlayerQuickActionKind.Timer, onTimer)
        PlayerQuickAction(stringResource(R.string.player_quick_edit), PlayerQuickActionKind.Edit, onEditMetadata)
        PlayerQuickAction(stringResource(R.string.player_quick_more), PlayerQuickActionKind.More, onMore)
    }
}

internal enum class PlayerQuickActionKind {
    Info,
    Share,
    Timer,
    Edit,
    More,
    Add,
    PlayNext,
    Speed,
    Equalizer
}

@Composable
internal fun PlayerQuickAction(
    label: String,
    kind: PlayerQuickActionKind,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(42.dp)
            .playerNoIndicationClick(onClick)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape),
            contentAlignment = Alignment.Center
        ) {
            QuickActionIcon(
                kind = kind,
                color = LocalPlayerContentColor.current.copy(alpha = 0.9f),
                modifier = Modifier.size(19.dp)
            )
        }
    }
}

@Composable
internal fun QuickActionIcon(
    kind: PlayerQuickActionKind,
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.10f
        val cx = size.width / 2f
        val cy = size.height / 2f
        when (kind) {
            PlayerQuickActionKind.Info -> {
                drawCircle(color = color, radius = size.minDimension * 0.42f, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, Offset(cx, size.height * 0.46f), Offset(cx, size.height * 0.70f), stroke, cap = StrokeCap.Round)
                drawCircle(color = color, radius = stroke * 0.68f, center = Offset(cx, size.height * 0.30f))
            }
            PlayerQuickActionKind.Share -> {
                val a = Offset(size.width * 0.26f, size.height * 0.58f)
                val b = Offset(size.width * 0.68f, size.height * 0.30f)
                val c = Offset(size.width * 0.70f, size.height * 0.74f)
                drawLine(color, a, b, stroke, cap = StrokeCap.Round)
                drawLine(color, a, c, stroke, cap = StrokeCap.Round)
                listOf(a, b, c).forEach { drawCircle(color = color, radius = stroke * 1.35f, center = it) }
            }
            PlayerQuickActionKind.Timer -> {
                drawCircle(color = color, radius = size.minDimension * 0.40f, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round))
                drawLine(color, Offset(cx, cy), Offset(cx, size.height * 0.30f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(cx, cy), Offset(size.width * 0.66f, size.height * 0.58f), stroke, cap = StrokeCap.Round)
            }
            PlayerQuickActionKind.Edit -> {
                drawLine(color, Offset(size.width * 0.28f, size.height * 0.72f), Offset(size.width * 0.72f, size.height * 0.28f), stroke * 1.3f, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.22f, size.height * 0.78f), Offset(size.width * 0.40f, size.height * 0.72f), stroke, cap = StrokeCap.Round)
            }
            PlayerQuickActionKind.More -> {
                listOf(0.25f, 0.5f, 0.75f).forEach { y ->
                    drawCircle(color = color, radius = stroke * 0.95f, center = Offset(cx, size.height * y))
                }
            }
            PlayerQuickActionKind.Add -> {
                drawLine(color, Offset(cx, size.height * 0.22f), Offset(cx, size.height * 0.78f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.22f, cy), Offset(size.width * 0.78f, cy), stroke, cap = StrokeCap.Round)
                drawRect(
                    color = color,
                    topLeft = Offset(size.width * 0.18f, size.height * 0.18f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.64f),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round)
                )
            }
            PlayerQuickActionKind.PlayNext -> {
                drawLine(color, Offset(size.width * 0.20f, size.height * 0.30f), Offset(size.width * 0.54f, size.height * 0.30f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.20f, size.height * 0.50f), Offset(size.width * 0.54f, size.height * 0.50f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.20f, size.height * 0.70f), Offset(size.width * 0.42f, size.height * 0.70f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.70f, size.height * 0.30f), Offset(size.width * 0.70f, size.height * 0.70f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.58f, size.height * 0.58f), Offset(size.width * 0.70f, size.height * 0.70f), stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.82f, size.height * 0.58f), Offset(size.width * 0.70f, size.height * 0.70f), stroke, cap = StrokeCap.Round)
            }
            PlayerQuickActionKind.Speed -> {
                drawArc(
                    color = color,
                    startAngle = 190f,
                    sweepAngle = 220f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = StrokeCap.Round),
                    topLeft = Offset(size.width * 0.16f, size.height * 0.24f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.68f, size.height * 0.68f)
                )
                drawLine(color, Offset(cx, cy), Offset(size.width * 0.72f, size.height * 0.40f), stroke, cap = StrokeCap.Round)
            }
            PlayerQuickActionKind.Equalizer -> {
                val xs = listOf(0.24f, 0.50f, 0.76f)
                val heights = listOf(0.68f, 0.42f, 0.58f)
                xs.forEachIndexed { index, x ->
                    drawLine(
                        color = color,
                        start = Offset(size.width * x, size.height * 0.22f),
                        end = Offset(size.width * x, size.height * 0.78f),
                        strokeWidth = stroke,
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = color,
                        radius = stroke * 1.35f,
                        center = Offset(size.width * x, size.height * heights[index])
                    )
                }
            }
        }
    }
}

@Composable
internal fun PlayerHeaderAction(
    kind: PlayerHeaderActionKind,
    selected: Boolean = false,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .playerNoIndicationClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        when (kind) {
            PlayerHeaderActionKind.Favorite -> Icon(
                painter = painterResource(
                    id = if (selected) R.drawable.ic_notification_favorite_filled
                    else R.drawable.ic_notification_favorite
                ),
                contentDescription = if (selected) {
                    stringResource(R.string.common_unfavorite)
                } else {
                    stringResource(R.string.common_favorite)
                },
                tint = if (selected) Color(0xFFFF4D6D) else LocalPlayerContentColor.current.copy(alpha = 0.92f),
                modifier = Modifier.size(25.dp)
            )
            PlayerHeaderActionKind.More -> QuickActionIcon(
                kind = PlayerQuickActionKind.More,
                color = LocalPlayerContentColor.current.copy(alpha = 0.96f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
internal fun HeartIcon(
    color: Color,
    filled: Boolean,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.86f)
            cubicTo(w * 0.18f, h * 0.60f, w * 0.04f, h * 0.42f, w * 0.10f, h * 0.24f)
            cubicTo(w * 0.17f, h * 0.04f, w * 0.39f, h * 0.05f, w * 0.50f, h * 0.25f)
            cubicTo(w * 0.61f, h * 0.05f, w * 0.83f, h * 0.04f, w * 0.90f, h * 0.24f)
            cubicTo(w * 0.96f, h * 0.42f, w * 0.82f, h * 0.60f, w * 0.50f, h * 0.86f)
            close()
        }
        if (filled) {
            drawPath(path, color)
        } else {
            drawPath(
                path = path,
                color = color,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = size.minDimension * 0.09f,
                    cap = StrokeCap.Round
                )
            )
        }
    }
}

@Composable
internal fun CloseIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = size.minDimension * 0.12f
        drawLine(
            color = color,
            start = Offset(size.width * 0.22f, size.height * 0.22f),
            end = Offset(size.width * 0.78f, size.height * 0.78f),
            strokeWidth = stroke
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.78f, size.height * 0.22f),
            end = Offset(size.width * 0.22f, size.height * 0.78f),
            strokeWidth = stroke
        )
    }
}
