package com.ella.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ella.music.data.BottomBarGlassEffect
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.Shadow
import kotlin.math.abs
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val LocalBottomBarPressedIndex = staticCompositionLocalOf { -1 }

@Composable
private fun RowScope.BottomBarPressScaleProvider(
    pressedIndex: Int,
    content: @Composable RowScope.() -> Unit
) {
    CompositionLocalProvider(LocalBottomBarPressedIndex provides pressedIndex) {
        content()
    }
}

@Composable
fun LiquidGlassBottomBar(
    backdrop: Backdrop?,
    isBlurEnabled: Boolean = true,
    glassEffect: BottomBarGlassEffect = BottomBarGlassEffect.Blur,
    selectedIndex: Int? = null,
    itemCount: Int = 0,
    onSelected: ((Int) -> Unit)? = null,
    content: @Composable RowScope.() -> Unit
) {
    val density = LocalDensity.current
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val selectedIndexState = rememberUpdatedState(selectedIndex)
    val onSelectedState = rememberUpdatedState(onSelected)
    var barWidthPx by remember { mutableIntStateOf(0) }
    var dragCenterX by remember { mutableFloatStateOf(0f) }
    var pressedIndex by remember { mutableIntStateOf(-1) }
    var lastBubbleCenterX by remember { mutableFloatStateOf(0f) }
    var isPressed by remember { mutableStateOf(false) }
    val validItemCount = itemCount.coerceAtLeast(0)
    val enablePressBubble = glassEffect == BottomBarGlassEffect.LiquidGlass
    val sidePaddingPx = with(density) { 4.dp.toPx() }
    val bubbleHeight = 52.dp
    val bubbleHeightPx = with(density) { bubbleHeight.toPx() }
    val tabWidthPx = if (validItemCount > 0) {
        ((barWidthPx - sidePaddingPx * 2f) / validItemCount).coerceAtLeast(1f)
    } else {
        1f
    }
    val tabWidthState = rememberUpdatedState(tabWidthPx)
    val sidePaddingState = rememberUpdatedState(sidePaddingPx)
    val targetBubbleWidthPx = when {
        validItemCount <= 0 -> bubbleHeightPx
        validItemCount <= 2 -> (tabWidthPx * 0.58f).coerceIn(with(density) { 86.dp.toPx() }, with(density) { 112.dp.toPx() })
        validItemCount == 3 -> (tabWidthPx * 0.66f).coerceIn(with(density) { 74.dp.toPx() }, with(density) { 104.dp.toPx() })
        else -> (tabWidthPx * 0.70f).coerceIn(with(density) { 64.dp.toPx() }, with(density) { 92.dp.toPx() })
    }
    val bubbleWidthPx by animateFloatAsState(
        targetValue = targetBubbleWidthPx,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 680f),
        label = "BottomBarBubbleWidth"
    )
    val bubbleCenterX = if (pressedIndex in 0 until validItemCount) {
        sidePaddingPx + tabWidthPx * (pressedIndex + 0.5f)
    } else {
        lastBubbleCenterX.takeIf { it > 0f } ?: dragCenterX
    }
    val bubbleX = if (barWidthPx > 0) {
        (bubbleCenterX - bubbleWidthPx / 2f)
            .coerceIn(0f, (barWidthPx - bubbleWidthPx).coerceAtLeast(0f))
    } else {
        0f
    }
    val bubbleAlpha by animateFloatAsState(
        targetValue = if (enablePressBubble && isPressed && pressedIndex >= 0) 0.72f else 0f,
        animationSpec = spring(dampingRatio = 0.74f, stiffness = 680f),
        label = "BottomBarBubbleAlpha"
    )
    val bubbleScale by animateFloatAsState(
        targetValue = if (enablePressBubble && isPressed && pressedIndex >= 0) 1.035f else 0.98f,
        animationSpec = spring(dampingRatio = 0.66f, stiffness = 760f),
        label = "BottomBarBubbleScale"
    )
    val pressBubbleShape = RoundedCornerShape(bubbleHeight / 2)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(72.dp)
            .onSizeChanged { barWidthPx = it.width }
            .pointerInput(validItemCount, barWidthPx) {
                if (validItemCount <= 0 || onSelectedState.value == null || barWidthPx <= 0) return@pointerInput

                fun hitIndexFor(x: Float): Int? {
                    val tabWidth = tabWidthState.value
                    val sidePadding = sidePaddingState.value
                    if (tabWidth <= 0f) return null
                    val rawIndex = (((x - sidePadding) / tabWidth).toInt()).coerceIn(0, validItemCount - 1)
                    val center = sidePadding + tabWidth * (rawIndex + 0.5f)
                    val activeRadius = tabWidth * 0.50f
                    return rawIndex.takeIf { abs(x - center) <= activeRadius }
                }

                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    dragCenterX = down.position.x
                    isPressed = true
                    var releaseIndex = hitIndexFor(down.position.x)
                    pressedIndex = releaseIndex ?: -1
                    if (releaseIndex != null) {
                        lastBubbleCenterX = sidePaddingPx + tabWidthPx * (releaseIndex + 0.5f)
                    } else {
                        lastBubbleCenterX = down.position.x
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: continue
                        dragCenterX = change.position.x
                        releaseIndex = hitIndexFor(change.position.x)
                        pressedIndex = releaseIndex ?: -1
                        if (releaseIndex != null) {
                            lastBubbleCenterX = sidePaddingPx + tabWidthPx * (releaseIndex + 0.5f)
                        }
                        if (!change.pressed) break
                    }
                    releaseIndex
                        ?.takeIf { it != selectedIndexState.value }
                        ?.let { onSelectedState.value?.invoke(it) }
                    isPressed = false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        GlassPill(
            backdrop = if (isBlurEnabled) backdrop else null,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.Center),
            shape = RoundedCornerShape(32.dp),
            glassEffect = glassEffect
        ) {
            Box(modifier = Modifier.fillMaxSize())
        }

        if (enablePressBubble) {
            val pressBackdropModifier = if (isBlurEnabled && backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    shape = { pressBubbleShape },
                    effects = {
                        blur(4.dp.toPx())
                        runCatching {
                            lens(
                                refractionHeight = 8.dp.toPx(),
                                refractionAmount = 10.dp.toPx()
                            )
                        }
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = if (isLight) 0.10f else 0.06f)
                    },
                    shadow = {
                        Shadow.Default.copy(
                            color = Color.Black.copy(alpha = if (isLight) 0.025f else 0.08f)
                        )
                    },
                    onDrawSurface = {
                        drawRect(
                            if (isLight) {
                                Color.White.copy(alpha = 0.10f)
                            } else {
                                Color.White.copy(alpha = 0.055f)
                            }
                        )
                    }
                )
            } else {
                Modifier
            }
            Box(
                modifier = Modifier
                    .width(with(density) { bubbleWidthPx.toDp() })
                    .height(bubbleHeight)
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(bubbleX.roundToInt(), 0) }
                    .graphicsLayer {
                        alpha = bubbleAlpha
                        scaleX = bubbleScale
                        scaleY = bubbleScale
                    }
                    .clip(pressBubbleShape)
                    .then(pressBackdropModifier)
                    .bottomBarPressGlow(
                        isLight = isLight,
                        cornerRadius = bubbleHeight / 2
                    )
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .align(Alignment.Center)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = {
                BottomBarPressScaleProvider(
                    pressedIndex = if (enablePressBubble && isPressed) pressedIndex else -1,
                    content = content
                )
            }
        )
    }
}

@Composable
fun RowScope.LiquidGlassBottomBarItem(
    selected: Boolean,
    onClick: () -> Unit,
    backdrop: Backdrop?,
    isBlurEnabled: Boolean = true,
    showSelectedIndicator: Boolean = true,
    index: Int? = null,
    icon: @Composable () -> Unit,
    label: @Composable () -> Unit
) {
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val isPressedItem = index != null && LocalBottomBarPressedIndex.current == index
    val pressScale by animateFloatAsState(
        targetValue = if (isPressedItem) 1.035f else 1f,
        animationSpec = spring(dampingRatio = 0.72f, stiffness = 760f),
        label = "BottomBarPressedItemScale"
    )
    val contentScale by animateFloatAsState(
        targetValue = (if (selected) 1.03f else 1f) * pressScale,
        animationSpec = spring(dampingRatio = 0.70f, stiffness = 820f),
        label = "BottomBarItemContentScale"
    )
    val contentAlpha by animateFloatAsState(
        targetValue = if (selected) 1f else 0.62f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 600f),
        label = "BottomBarItemContentAlpha"
    )
    val selectedIndicatorAlpha by animateFloatAsState(
        targetValue = if (selected && showSelectedIndicator) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 700f),
        label = "BottomBarItemSelectedIndicatorAlpha"
    )

    Box(
        modifier = Modifier
            .weight(1f)
            .height(56.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(84.dp)
                .height(52.dp)
                .graphicsLayer { alpha = selectedIndicatorAlpha }
                .background(
                    color = if (isLight) {
                        Color.Black.copy(alpha = 0.045f)
                    } else {
                        Color.White.copy(alpha = 0.045f)
                    },
                    shape = RoundedCornerShape(28.dp)
                )
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = contentAlpha
                    scaleX = contentScale
                    scaleY = contentScale
                }
                .padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box {
                icon()
            }
            Box(modifier = Modifier.graphicsLayer { alpha = 0.88f }) {
                label()
            }
        }
    }
}

private fun Modifier.bottomBarPressGlow(
    isLight: Boolean,
    cornerRadius: androidx.compose.ui.unit.Dp
): Modifier = drawWithCache {
    val radius = cornerRadius.toPx()
    val strokeWidth = 1.dp.toPx()
    val fillBrush = Brush.verticalGradient(
        0f to Color.White.copy(alpha = if (isLight) 0.18f else 0.11f),
        0.58f to Color.White.copy(alpha = if (isLight) 0.07f else 0.045f),
        1f to if (isLight) {
            Color.Black.copy(alpha = 0.025f)
        } else {
            Color.Black.copy(alpha = 0.10f)
        }
    )
    val sheenBrush = Brush.linearGradient(
        0f to Color.White.copy(alpha = if (isLight) 0.18f else 0.10f),
        0.42f to Color.Transparent,
        1f to Color.White.copy(alpha = if (isLight) 0.08f else 0.045f)
    )
    val borderColor = Color.White.copy(alpha = if (isLight) 0.24f else 0.13f)

    onDrawWithContent {
        drawContent()
        drawRoundRect(
            brush = fillBrush,
            cornerRadius = CornerRadius(radius, radius)
        )
        drawRoundRect(
            brush = sheenBrush,
            cornerRadius = CornerRadius(radius, radius),
            blendMode = BlendMode.Plus
        )
        drawRoundRect(
            color = borderColor,
            cornerRadius = CornerRadius(radius, radius),
            style = Stroke(width = strokeWidth),
            blendMode = BlendMode.Plus
        )
    }
}
