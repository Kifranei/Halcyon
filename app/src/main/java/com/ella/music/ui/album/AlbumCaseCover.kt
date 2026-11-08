package com.ella.music.ui.album

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ella.music.ui.components.SafeCoverImage
import kotlinx.coroutines.launch
import kotlin.math.min

/**
 * 专辑盒封面：闭合时带透视的实体 CD 盒（书脊在左、右侧厚度、下方投影）；
 * 点击以左缘为轴翻开盒盖，露出托盘里的碟片。长按仍查看大图。
 * 原创实现，仅参照 Salt Player 公开的界面效果，不含其代码。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun AlbumCaseCover(
    coverModel: Any?,
    onPreview: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 8.dp,
    enabled: Boolean = true
) {
    val scope = rememberCoroutineScope()
    // 开度由 Animatable 直接驱动：点按播放弹簧动画。
    val openAnim = remember { Animatable(0f) }
    val open = openAnim.value
    val discSpin = open * 30f
    val openSpec = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessLow)
    // 整盒双轴倾斜：拖动累计转角跟手（MT 管理器 LOGO 式），松手弹性回正。
    val tiltX = remember { Animatable(0f) }
    val tiltY = remember { Animatable(0f) }
    val tiltSpec = spring<Float>(dampingRatio = 0.62f, stiffness = Spring.StiffnessMediumLow)
    val density = LocalDensity.current
    fun toggleOpen() {
        val target = if (openAnim.value >= 0.5f) 0f else 1f
        scope.launch { openAnim.animateTo(target, openSpec) }
    }
    fun rubberBand(degrees: Float): Float {
        val limit = kotlin.math.abs(degrees)
        if (limit <= 60f) return degrees
        val over = (limit - 60f) * 0.35f
        return Math.copySign((60f + over).coerceAtMost(80f), degrees)
    }
    val dragModifier = if (enabled) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    scope.launch { tiltX.animateTo(0f, tiltSpec) }
                    scope.launch { tiltY.animateTo(0f, tiltSpec) }
                },
                onDragCancel = {
                    scope.launch { tiltX.animateTo(0f, tiltSpec) }
                    scope.launch { tiltY.animateTo(0f, tiltSpec) }
                }
            ) { change, dragAmount ->
                change.consume()
                val w = size.width.coerceAtLeast(1)
                val h = size.height.coerceAtLeast(1)
                // 横拖绕 Y 轴、竖拖绕 X 轴；拖满约一个边长转 ~90°。
                scope.launch { tiltY.snapTo(rubberBand(tiltY.value + dragAmount.x / w * 120f)) }
                scope.launch { tiltX.snapTo(rubberBand(tiltX.value - dragAmount.y / h * 120f)) }
            }
        }
    } else Modifier
    val clickModifier = if (enabled) {
        Modifier.combinedClickable(
            onClick = { toggleOpen() },
            onLongClick = onPreview
        )
    } else {
        Modifier.combinedClickable(onClick = onPreview, onLongClick = onPreview)
    }
    val cornerShape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .graphicsLayer {
                rotationX = tiltX.value
                rotationY = tiltY.value
                cameraDistance = 14f * density.density
            }
            .then(dragModifier).then(clickModifier),
        contentAlignment = Alignment.Center
    ) {
        // 下方投影：随开合加深、拉宽。
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val shadowW = w * (0.88f + 0.10f * open)
            val shadowH = h * 0.075f
            val cx = w / 2f
            val cy = h * 0.965f
            drawOval(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.30f + 0.12f * open),
                        Color.Transparent
                    ),
                    center = Offset(cx, cy),
                    radius = shadowW / 2f
                ),
                topLeft = Offset(cx - shadowW / 2f, cy - shadowH / 2f),
                size = Size(shadowW, shadowH)
            )
        }

        // 托盘 + 碟片：开盖后显现，碟片微微右移并转动。
        if (enabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        alpha = open
                        translationX = size.width * 0.07f * open
                        scaleX = 0.86f + 0.14f * open
                        scaleY = 0.86f + 0.14f * open
                    },
                contentAlignment = Alignment.Center
            ) {
                // 托盘内衬：浅色磨砂底。
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.97f)
                        .clip(RoundedCornerShape(cornerRadius * 0.8f))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawRect(Color(0xFF2E3138).copy(alpha = 0.92f))
                    }
                }
                // 碟片。
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.9f)
                        .aspectRatio(1f)
                        .graphicsLayer { rotationZ = discSpin },
                    contentAlignment = Alignment.Center
                ) {
                    // 透明外圈 + 印刷内圈 + 中心孔。
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val r = size.minDimension / 2f
                        drawCircle(Color.White.copy(alpha = 0.16f), radius = r)
                        drawCircle(
                            Color.White.copy(alpha = 0.28f),
                            radius = r,
                            style = Stroke(width = r * 0.015f)
                        )
                        drawCircle(
                            Color(0xFF1B1D21).copy(alpha = 0.9f),
                            radius = r * 0.115f
                        )
                        drawCircle(
                            Color.White.copy(alpha = 0.35f),
                            radius = r * 0.075f,
                            style = Stroke(width = r * 0.012f)
                        )
                    }
                    SafeCoverImage(
                        model = coverModel,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize(0.72f)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop,
                        sizePx = 512,
                        loadOriginal = true,
                        showDefaultPlaceholder = false
                    )
                }
            }
        }

        // 盒盖：左缘为轴，闭合 -14° 透视 → 打开 -112°。
        val lidRotation = if (enabled) -14f + (-112f + 14f) * open else 0f
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0f, 0.5f)
                    rotationY = lidRotation
                    cameraDistance = 16f * density.density
                }
                .clip(cornerShape)
        ) {
            SafeCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sizePx = 512,
                loadOriginal = true
            )
            if (enabled) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val closed = 1f - open
                    // 左侧书脊阴影带。
                    val spineW = w * 0.045f
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.42f * closed),
                                Color.Transparent
                            ),
                            startX = 0f,
                            endX = spineW * 2.2f
                        ),
                        topLeft = Offset.Zero,
                        size = Size(spineW * 2.2f, h)
                    )
                    // 右侧厚度边（盒体侧面）。
                    val edgeW = w * 0.032f
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.5f * closed)
                            ),
                            startX = w - edgeW * 2.4f,
                            endX = w
                        ),
                        topLeft = Offset(w - edgeW * 2.4f, 0f),
                        size = Size(edgeW * 2.4f, h)
                    )
                    // 翻开后的盒盖内面：压暗读作内衬。
                    if (open > 0.02f) {
                        drawRect(Color.Black.copy(alpha = 0.5f * open))
                    }
                }
            }
        }
    }
}
