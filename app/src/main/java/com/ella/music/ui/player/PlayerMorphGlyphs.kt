package com.ella.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp

/**
 * Apple Music 风格形变播放/暂停按钮。
 *
 * 两个暂停竖条在播放时折向右侧汇聚成一个三角形（对应逆向出来的
 * LifeKit 状态机 morph：暂停条 -> 播放三角，外层用阻尼弹簧驱动，
 * 而不是直接切换图标）。
 */
@Composable
internal fun MorphPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    modifier: Modifier = Modifier
) {
    val target = if (isPlaying) 1f else 0f
    val morph = remember { Animatable(0f) }
    val latestTarget by rememberUpdatedState(target)
    LaunchedEffect(target) {
        val from = morph.value
        morph.animateTo(
            targetValue = latestTarget,
            animationSpec = spring(
                dampingRatio = if (latestTarget > from) 0.56f else 0.64f,
                stiffness = Spring.StiffnessMediumLow * 1.6f,
                visibilityThreshold = 0.001f
            )
        )
    }
    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        if (s <= 0f) return@Canvas
        val t = morph.value.coerceIn(0f, 1f)
        val color = tint

        fun lerpF(a: Float, b: Float, k: Float) = a + (b - a) * k

        // 左轮廓：暂停左条 -> 播放三角形（右缘扫向顶点）
        val leftPath = Path().apply {
            moveTo(0.33f * s, 0.24f * s)
            lineTo(lerpF(0.45f, 0.78f, t) * s, lerpF(0.24f, 0.50f, t) * s)
            lineTo(lerpF(0.45f, 0.78f, t) * s, lerpF(0.76f, 0.50f, t) * s)
            lineTo(0.33f * s, 0.76f * s)
            close()
        }
        // 右轮廓：暂停右条 -> 折进三角形顶点（面积收敛为 0）
        val rightPath = Path().apply {
            moveTo(lerpF(0.55f, 0.78f, t) * s, lerpF(0.24f, 0.50f, t) * s)
            lineTo(lerpF(0.67f, 0.78f, t) * s, lerpF(0.24f, 0.50f, t) * s)
            lineTo(lerpF(0.67f, 0.78f, t) * s, lerpF(0.76f, 0.50f, t) * s)
            lineTo(lerpF(0.55f, 0.78f, t) * s, lerpF(0.76f, 0.50f, t) * s)
            close()
        }
        drawPath(leftPath, color)
        drawPath(rightPath, color)
    }
}

/**
 * Apple Music 歌词按钮（quote.bubble 风格）：
 * 圆角对话泡 + 左下角小尾巴 + 两句引号曲线。
 */
@Composable
internal fun AppleLyricsIcon(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        if (s <= 0f) return@Canvas
        val stroke = s * 0.075f
        val tint = if (active) color.copy(alpha = 1f) else color

        // 气泡主体
        val bodyRect = Rect(0.10f * s, 0.14f * s, 0.90f * s, 0.72f * s)
        drawRoundRect(
            color = tint,
            topLeft = bodyRect.topLeft,
            size = bodyRect.size,
            cornerRadius = CornerRadius(s * 0.22f),
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
        // 尾巴
        drawLine(
            color = tint,
            start = Offset(0.27f * s, 0.70f * s),
            end = Offset(0.17f * s, 0.86f * s),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        // 两个引号曲线
        listOf(0.30f, 0.62f).forEach { cx ->
            drawArc(
                color = tint,
                startAngle = 200f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset((cx - 0.07f) * s, 0.33f * s),
                size = Size(0.14f * s, 0.16f * s),
                style = Stroke(width = s * 0.075f, cap = StrokeCap.Round)
            )
        }
    }
}

/**
 * 翻译按钮（"A あ" 风格），与 Apple Music 歌词翻译开关的图标一致：
 * 大号 "A" 在左下，小号 "あ" 在右上。
 */
@Composable
internal fun AppleTranslationIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()
    Canvas(modifier = modifier) {
        if (size.width <= 0f || size.height <= 0f) return@Canvas
        val latin = textMeasurer.measure(
            AnnotatedString("A"),
            style = TextStyle(fontSize = (size.height * 0.58f / density).sp)
        )
        val kana = textMeasurer.measure(
            AnnotatedString("あ"),
            style = TextStyle(fontSize = (size.height * 0.40f / density).sp)
        )
        drawText(
            textLayoutResult = latin,
            color = color,
            topLeft = Offset(size.width * 0.10f, size.height * 0.80f - latin.size.height)
        )
        drawText(
            textLayoutResult = kana,
            color = color.copy(alpha = 0.94f),
            topLeft = Offset(size.width * 0.53f, size.height * 0.05f)
        )
    }
}

/**
 * 伴奏 / 人声按钮（麦克风造型），对应 Apple Music Sing 的 vocal 开关。
 */
@Composable
internal fun AppleMicIcon(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Canvas(modifier = modifier) {
        val s = minOf(size.width, size.height)
        if (s <= 0f) return@Canvas
        val tint = if (active) color.copy(alpha = 1f) else color.copy(alpha = 0.72f)
        val headWidth = s * 0.42f
        val headHeight = s * 0.46f
        drawRoundRect(
            color = tint,
            topLeft = Offset((s - headWidth) / 2f, s * 0.12f),
            size = Size(headWidth, headHeight),
            cornerRadius = CornerRadius(headWidth / 2f, headWidth / 2f)
        )
        drawArc(
            color = tint,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(s * 0.22f, s * 0.44f),
            size = Size(s * 0.56f, s * 0.46f),
            style = Stroke(width = s * 0.075f, cap = StrokeCap.Round)
        )
        drawLine(
            color = tint,
            start = Offset(s * 0.28f, s * 0.88f),
            end = Offset(s * 0.72f, s * 0.88f),
            strokeWidth = s * 0.075f,
            cap = StrokeCap.Round
        )
    }
}