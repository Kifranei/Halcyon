// Copyright 2026, compose-miuix-ui contributors
// SPDX-License-Identifier: Apache-2.0
// Adapted anew from Miuix v0.9.4 (39c40f99844227b853f0049a0933b1f3ae6c00ba),
// based on Kyant0/AndroidLiquidGlass (Apache-2.0).
// Halcyon modifications: public content API, visual settings, TV fallback, focus feedback and one-way pager tracking.
package com.ella.music.ui.components

// Adapted from Kyant0/AndroidLiquidGlass — https://github.com/Kyant0/AndroidLiquidGlass (Apache 2.0).

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import com.ella.music.ui.animation.DampedDragAnimation
import com.ella.music.ui.animation.InteractiveHighlight
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.blur
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.blur.Backdrop
import com.ella.music.ui.components.liquid.InnerShadow
import com.ella.music.ui.components.liquid.innerShadow
import com.ella.music.ui.components.liquid.lens
import com.ella.music.ui.components.liquid.vibrancy
import com.ella.music.ui.components.liquid.rememberCombinedBackdrop

private val LocalIosTabScale = staticCompositionLocalOf { { 1f } }

val LocalFloatingBottomBarTabScale = LocalIosTabScale
val LocalFloatingBottomBarContentColor = staticCompositionLocalOf { Color.Unspecified }
val LocalFloatingBottomBarActiveIndex = compositionLocalOf { -1 }
val LocalFloatingBottomBarIsDragging = compositionLocalOf { false }
private val LocalFloatingBottomBarPassive = staticCompositionLocalOf { false }

@Immutable
class FloatingBottomBarColors(val containerColor: Color, val indicatorColor: Color,
    val contentColor: Color, val activeContentColor: Color)

object FloatingBottomBarDefaults {
    @Composable fun colors(containerColor: Color = MiuixTheme.colorScheme.surfaceContainer,
        indicatorColor: Color = MiuixTheme.colorScheme.primary,
        contentColor: Color = MiuixTheme.colorScheme.onSurface,
        activeContentColor: Color = indicatorColor) =
        FloatingBottomBarColors(containerColor, indicatorColor, contentColor, activeContentColor)
}

enum class FloatingBottomBarMode { LiquidGlass, Blur, None }

@Composable
fun RowScope.FloatingBottomBarItem(onClick: () -> Unit, modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit) {
    var focused by remember { mutableStateOf(false) }
    val scale = LocalFloatingBottomBarTabScale.current
    val color = LocalFloatingBottomBarContentColor.current
    val passive = LocalFloatingBottomBarPassive.current
    Column(modifier.weight(1f).fillMaxHeight().clip(CircleShape)
        .onFocusChanged { focused = it.hasFocus }
        .border(if (focused) 3.dp else 0.dp, if (focused) MiuixTheme.colorScheme.primary else Color.Transparent, CircleShape)
        .then(if (passive) Modifier else Modifier.clickable(interactionSource = null, indication = null, role = Role.Tab, onClick = onClick))
        .graphicsLayer { scaleX = scale() * if (focused) 1.06f else 1f; scaleY = scaleX },
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        CompositionLocalProvider(LocalContentColor provides color) { content() }
    }
}

@Composable
fun FloatingBottomBar(modifier: Modifier = Modifier, selectedIndex: () -> Int,
    onSelected: (Int) -> Unit, backdrop: Backdrop?, tabsCount: Int,
    mode: FloatingBottomBarMode = FloatingBottomBarMode.LiquidGlass,
    disableRefraction: Boolean = false, cornerRadiusDp: Float? = null,
    liquidGlassConfig: BottomBarLiquidGlassConfig? = null,
    colors: FloatingBottomBarColors = FloatingBottomBarDefaults.colors(),
    content: @Composable RowScope.() -> Unit) {
    if (tabsCount <= 0) return
    val selectedIndexValue = selectedIndex().coerceIn(0, tabsCount - 1)
    val context = LocalContext.current
    val television = remember(context) { com.ella.music.util.isTelevisionDevice(context) }
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val radius = (cornerRadiusDp ?: LocalBottomBarCornerRadiusDp.current).coerceIn(0f, 32f)
    val pillShape = remember(radius) { RoundedCornerShape(radius.dp) }
    val config = liquidGlassConfig ?: LocalBottomBarLiquidGlassConfig.current
    val isBlurActive = mode == FloatingBottomBarMode.LiquidGlass && backdrop != null && !television && !disableRefraction
    val blurOnly = mode == FloatingBottomBarMode.Blur || television || (mode == FloatingBottomBarMode.LiquidGlass && disableRefraction)
    val accentColor = colors.activeContentColor
    val tabContentColor = colors.contentColor
    val surfaceContainer = colors.containerColor
    val containerColor = if (isBlurActive) surfaceContainer.copy(alpha = 0.4f) else surfaceContainer

    val tabsBackdrop = rememberLayerBackdrop()
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    var tabWidthPx by remember { mutableFloatStateOf(0f) }
    var totalWidthPx by remember { mutableFloatStateOf(0f) }

    val offsetAnimation = remember { Animatable(0f) }
    val rubberBandPx = with(density) { 4.dp.toPx() }
    val panelOffset by remember(rubberBandPx) {
        derivedStateOf {
            if (totalWidthPx == 0f) {
                0f
            } else {
                val fraction = (offsetAnimation.value / totalWidthPx).coerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }
    }

    var currentIndex by remember { mutableIntStateOf(selectedIndexValue) }
    val onItemClickUpdated by rememberUpdatedState(onSelected)

    fun indexAt(positionX: Float): Int {
        if (tabWidthPx == 0f) return currentIndex
        val horizontalPaddingPx = with(density) { 4.dp.toPx() }
        val logicalX = if (isLtr) positionX else totalWidthPx - positionX
        return ((logicalX - horizontalPaddingPx) / tabWidthPx)
            .toInt()
            .coerceIn(0, tabsCount - 1)
    }

    val dragTravel = remember { floatArrayOf(0f) }
    val touchSlop = androidx.compose.ui.platform.LocalViewConfiguration.current.touchSlop
    val dampedDrag = remember(animationScope, tabsCount, density, isLtr, touchSlop) {
        DampedDragAnimation(
            animationScope = animationScope,
            initialValue = selectedIndexValue.toFloat(),
            valueRange = 0f..(tabsCount - 1).toFloat(),
            visibilityThreshold = 0.001f,
            initialScale = 1f,
            pressedScale = 78f / 56f,
            canDrag = { position ->
                position.x in 0f..totalWidthPx
            },
            onDragStarted = { position ->
                dragTravel[0] = 0f
                updateValue(indexAt(position.x).toFloat())
            },
            onDragStopped = {
                val targetIndex = targetValue.roundToInt().coerceIn(0, tabsCount - 1)
                if (abs(dragTravel[0]) >= touchSlop && currentIndex != targetIndex) {
                    currentIndex = targetIndex
                    onItemClickUpdated(targetIndex)
                }
                updateValue(currentIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDragCancelled = {
                updateValue(currentIndex.toFloat())
                animationScope.launch {
                    offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                }
            },
            onDrag = { _, dragAmount ->
                dragTravel[0] += dragAmount.x
                if (tabWidthPx > 0f && dragAmount.x != 0f) {
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .coerceIn(0f, (tabsCount - 1).toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                }
            },
        )
    }

    LaunchedEffect(selectedIndexValue) {
        if (currentIndex != selectedIndexValue) {
            currentIndex = selectedIndexValue
            dampedDrag.animateToValue(selectedIndexValue.toFloat(), animatePress = false)
        }
    }

    fun activateTab(index: Int) {
        if (currentIndex != index) {
            currentIndex = index
            onItemClickUpdated(index)
        }
        dampedDrag.animateToValue(index.toFloat())
    }

    // Keyed on dampedDrag: the position lambda captures it; a stale capture would freeze the press spot.
    val interactiveHighlight = remember(animationScope, isLtr, dampedDrag) {
        InteractiveHighlight(
            animationScope = animationScope,
            position = { layerSize, _ ->
                Offset(
                    x = if (isLtr) {
                        (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                    } else {
                        layerSize.width - (dampedDrag.value + 0.5f) * tabWidthPx + panelOffset
                    },
                    y = layerSize.height / 2f,
                )
            },
        )
    }

    val liquidStroke = GlassEdgeStrokes.forTheme(
        isDark = isDark,
        light = GlassEdgeStrokes.SmallLight,
        dark = GlassEdgeStrokes.SmallDark,
    )

    val combinedBackdrop = backdrop?.let { rememberCombinedBackdrop(it, tabsBackdrop) }

    val activeIndex by remember(dampedDrag, tabsCount) { derivedStateOf { dampedDrag.value.roundToInt().coerceIn(0, tabsCount - 1) } }
    val dragging by remember { derivedStateOf { dampedDrag.pressProgress > 0.001f } }
    val tabsContent = content

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                                .fillMaxWidth().then(if (television) Modifier else dampedDrag.modifier),
            contentAlignment = Alignment.CenterStart,
        ) {
            CompositionLocalProvider(LocalContentColor provides tabContentColor,
                LocalFloatingBottomBarContentColor provides tabContentColor,
                LocalFloatingBottomBarActiveIndex provides activeIndex,
                LocalFloatingBottomBarIsDragging provides dragging) {
                Row(
                    modifier = Modifier
                        .selectableGroup()
                        .onSizeChanged { coords ->
                            totalWidthPx = coords.width.toFloat()
                            val contentWidthPx = totalWidthPx - with(density) { 8.dp.toPx() }
                            tabWidthPx = (contentWidthPx / tabsCount).coerceAtLeast(0f)
                        }
                        .graphicsLayer { translationX = panelOffset }
                        .dropShadow(
                            shape = pillShape,
                            shadow = Shadow(
                                radius = 10.dp,
                                color = Color.Black,
                                // Lighter in light theme to avoid a visible gray fringe.
                                alpha = if (isDark) 0.2f else 0.1f,
                            ),
                        )
                        .then(
                            if (isBlurActive && backdrop != null) {
                                Modifier.drawBackdrop(
                                    backdrop = backdrop,
                                    shape = { pillShape },
                                    effects = {
                                        // 24dp lens refraction + 16dp press-scale reach, raised before blur() reads it.
                                        padding = maxOf(padding, 40.dp.toPx())
                                        vibrancy()
                                        blur(
                                            4.dp.toPx(),
                                            4.dp.toPx(),
                                        )
                                        lens(
                                            refractionHeight = config.refractionHeightDp.coerceAtLeast(0f).dp.toPx(),
                                            refractionAmount = config.refractionAmountDp.coerceAtLeast(0f).dp.toPx(),
                                            chromaticAberration = config.chromaticAberration,
                                        )
                                    },
                                    layerBlock = {
                                        val width = size.width.coerceAtLeast(1f)
                                        val s = lerp(1f, 1f + 16.dp.toPx() / width, dampedDrag.pressProgress)
                                        scaleX = s
                                        scaleY = s
                                    },
                                    onDrawSurface = { drawRect(containerColor) },
                                ).edgeStroke(pillShape, liquidStroke, alpha = 0.75f)
                            } else if (blurOnly && backdrop != null) {
                                Modifier.drawBackdrop(backdrop = backdrop, shape = { pillShape },
                                    effects = { blur(25.dp.toPx(), 25.dp.toPx()) },
                                    onDrawSurface = { drawRect(containerColor.copy(alpha = 0.65f)) })
                            } else {
                                Modifier.background(containerColor, pillShape)
                            },
                        )
                        .then(
                            if (isBlurActive) {
                                interactiveHighlight.modifier.then(interactiveHighlight.gestureModifier)
                            } else {
                                Modifier
                            },
                        )
                        .height(64.dp)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    content = tabsContent,
                )
            }

            if (isBlurActive && backdrop != null) {
                CompositionLocalProvider(
                    LocalIosTabScale provides { lerp(1f, 1.2f, dampedDrag.pressProgress) },
                    LocalContentColor provides accentColor,
                    LocalFloatingBottomBarPassive provides true,
                    LocalFloatingBottomBarContentColor provides accentColor,
                    LocalFloatingBottomBarActiveIndex provides activeIndex,
                    LocalFloatingBottomBarIsDragging provides dragging,
                ) {
                    Row(
                        modifier = Modifier
                            .clearAndSetSemantics {}
                            .alpha(0f)
                            .layerBackdrop(tabsBackdrop)
                            .graphicsLayer { translationX = panelOffset }
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { pillShape },
                                effects = {
                                    vibrancy()
                                    blur(config.blurRadiusDp.coerceAtLeast(0f).dp.toPx(), config.blurRadiusDp.coerceAtLeast(0f).dp.toPx())
                                    lens(
                                        refractionHeight = config.refractionHeightDp.coerceAtLeast(0f).dp.toPx(),
                                        refractionAmount = config.refractionAmountDp.coerceAtLeast(0f).dp.toPx(),
                                            chromaticAberration = config.chromaticAberration,
                                    )
                                },
                                onDrawSurface = { drawRect(containerColor) },
                            )
                            .then(interactiveHighlight.modifier)
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = tabsContent,
                    )
                }
            }

            if (tabWidthPx > 0f) {
                val tabWidthDp = with(density) { tabWidthPx.toDp() }
                if (isBlurActive && combinedBackdrop != null) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                val singleTabWidth = tabWidthPx
                                val progressOffset = dampedDrag.value * singleTabWidth
                                translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                            }
                            .drawBackdrop(
                                backdrop = combinedBackdrop,
                                shape = { pillShape },
                                effects = {
                                    val progress = dampedDrag.pressProgress
                                    lens(
                                        refractionHeight = 10.dp.toPx() * progress,
                                        refractionAmount = 14.dp.toPx() * progress,
                                        depthEffect = true,
                                        chromaticAberration = 0.5f,
                                    )
                                },
                                layerBlock = {
                                    scaleX = dampedDrag.scaleX
                                    scaleY = dampedDrag.scaleY
                                    val v = dampedDrag.velocity / 10f
                                    scaleX /= 1f - (v * 0.75f).coerceIn(-0.2f, 0.2f)
                                    scaleY *= 1f - (v * 0.25f).coerceIn(-0.2f, 0.2f)
                                },
                                onDrawSurface = {
                                    val progress = dampedDrag.pressProgress
                                    drawRect(
                                        color = if (!isDark) Color.Black.copy(alpha = 0.1f) else Color.White.copy(alpha = 0.1f),
                                        alpha = 1f - progress,
                                    )
                                    drawRect(Color.Black.copy(alpha = 0.03f * progress))
                                },
                            )
                            .edgeStroke(pillShape, liquidStroke, alpha = dampedDrag.pressProgress)
                            .innerShadow(shape = pillShape) {
                                InnerShadow(
                                    radius = 8.dp * dampedDrag.pressProgress,
                                    color = Color.Black.copy(alpha = 0.15f),
                                    alpha = dampedDrag.pressProgress,
                                )
                            }
                            .height(56.dp)
                            .width(tabWidthDp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .graphicsLayer {
                                val progressOffset = dampedDrag.value * tabWidthPx
                                translationX = if (isLtr) progressOffset + panelOffset else -progressOffset + panelOffset
                            }
                            .clip(pillShape)
                            .background(accentColor.copy(alpha = 0.15f), pillShape)
                            .height(56.dp)
                            .width(tabWidthDp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        CompositionLocalProvider(LocalFloatingBottomBarPassive provides true, LocalContentColor provides accentColor, LocalFloatingBottomBarContentColor provides accentColor, LocalFloatingBottomBarActiveIndex provides activeIndex, LocalFloatingBottomBarIsDragging provides dragging) {
                            Row(
                                modifier = Modifier
                                    .clearAndSetSemantics {}
                                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                                    .requiredWidth(with(density) { (totalWidthPx - 8.dp.toPx()).toDp() })
                                    .height(56.dp)
                                    .graphicsLayer {
                                        val progressOffset = dampedDrag.value * tabWidthPx
                                        translationX = if (isLtr) -progressOffset else progressOffset
                                    },
                                verticalAlignment = Alignment.CenterVertically,
                                content = tabsContent,
                            )
                        }
                    }
                }
            }
        }
    }
}
