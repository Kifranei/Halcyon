package com.ella.music.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.ui.res.stringResource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Icon
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
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
 * Apple Music 歌词按钮：
 * 正常态为描边图标（ic_nowplaying_lyrics），激活态为填充图标（ic_nowplaying_lyricson）。
 */
@Composable
internal fun AppleLyricsIcon(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Icon(
        painter = painterResource(if (active) R.drawable.ic_nowplaying_lyricson else R.drawable.ic_nowplaying_lyrics),
        contentDescription = null,
        tint = if (active) color.copy(alpha = 1f) else color,
        modifier = modifier
    )
}

/**
 * 歌词翻译按钮图标（Apple Music 风格双气泡 A 文）：
 */
@Composable
internal fun AppleTranslationIcon(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Icon(
        painter = painterResource(if (active) R.drawable.ic_nowplaying_translateon else R.drawable.ic_nowplaying_translate),
        contentDescription = stringResource(R.string.player_show_translation),
        tint = color,
        modifier = modifier
    )
}

/**
 * Apple Music 伴奏按钮图标（麦克风 + 星光）：
 * 正常态为浅底（ic_nowplaying_vocal），激活态为更深底（ic_nowplaying_vocalon）。
 */
@Composable
internal fun AppleVocalIcon(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Icon(
        painter = painterResource(if (active) R.drawable.ic_nowplaying_vocalon else R.drawable.ic_nowplaying_vocal),
        contentDescription = stringResource(R.string.player_accompaniment),
        tint = color,
        modifier = modifier
    )
}


/**
 * Apple Music 播放/暂停图标：
 * 播放时展示实心双竖圆柱（pause.fill），暂停时展示圆角实心右三角（play.fill）。
 */
@Composable
internal fun ApplePlayPauseIcon(
    isPlaying: Boolean,
    color: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(if (isPlaying) R.drawable.ic_nowplaying_pause else R.drawable.ic_nowplaying_play),
        contentDescription = stringResource(if (isPlaying) R.string.common_pause else R.string.common_play),
        tint = color,
        modifier = modifier
    )
}

/**
 * Apple Music 上一曲图标（backward.fill）：双实心左向圆角箭头。
 */
@Composable
internal fun AppleSkipPreviousIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(R.drawable.ic_nowplaying_rewind),
        contentDescription = stringResource(R.string.common_previous),
        tint = color,
        modifier = modifier
    )
}

/**
 * Apple Music 下一曲图标（forward.fill）：双实心右向圆角箭头。
 */
@Composable
internal fun AppleSkipNextIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(R.drawable.ic_nowplaying_fforward),
        contentDescription = stringResource(R.string.common_next),
        tint = color,
        modifier = modifier
    )
}

/**
 * Apple Music 队列/待播清单图标（list.bullet）：
 * 正常态为三条横线圆点（ic_nowplaying_queue），激活态为反白高亮胶囊（ic_nowplaying_queueon）。
 */
@Composable
internal fun AppleQueueIcon(
    color: Color,
    modifier: Modifier = Modifier,
    active: Boolean = false
) {
    Icon(
        painter = painterResource(if (active) R.drawable.ic_nowplaying_queueon else R.drawable.ic_nowplaying_queue),
        contentDescription = stringResource(R.string.player_queue),
        tint = if (active) color.copy(alpha = 1f) else color,
        modifier = modifier
    )
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

/**
 * Apple Music 风格底部中间投放图标（AirPlay / 投放设备）：
 * 替换原来的 Chromecast 图标。
 */
@Composable
internal fun AppleChromecastIcon(
    color: Color,
    modifier: Modifier = Modifier
) {
    Icon(
        painter = painterResource(R.drawable.ic_nowplaying_airplay),
        contentDescription = stringResource(R.string.casting_devices_title),
        tint = color,
        modifier = modifier
    )
}