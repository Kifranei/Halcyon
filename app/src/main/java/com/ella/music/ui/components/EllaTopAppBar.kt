@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.ella.music.ui.components
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.luminance
import com.ella.music.data.SettingsManager
import androidx.compose.runtime.remember
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import kotlin.math.pow
import top.yukonga.miuix.kmp.blur.progressiveTextureBlur
import top.yukonga.miuix.kmp.blur.ProgressiveBlur
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Close
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Settings pages are hosted by the main navigation graph, so the close action is supplied by
 * the graph instead of being threaded through every individual settings screen. A null value
 * keeps ordinary (non-settings) top bars unchanged.
 */
val LocalSettingsCloseAction = staticCompositionLocalOf<(() -> Unit)?> { null }
val LocalTopBarBlurStyle = staticCompositionLocalOf { SettingsManager.TOP_BAR_BLUR_OFF }
val LocalBackdrop = staticCompositionLocalOf<top.yukonga.miuix.kmp.blur.LayerBackdrop?> { null }

@Composable
fun EllaSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = MiuixTheme.colorScheme.surface,
    backdrop: Backdrop? = LocalBackdrop.current,
    enableProgressiveBlur: Boolean = LocalSettingsCloseAction.current != null,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    centeredTitle: Boolean = false,
    titleStartPadding: Dp = 64.dp,
    titleEndPadding: Dp = 128.dp,
    titleWindowInsetsPadding: Boolean = defaultWindowInsetsPadding,
    onDoubleTapTitle: (() -> Unit)? = null,
    bottomContent: @Composable () -> Unit = {},
) {
    val settingsCloseAction = LocalSettingsCloseAction.current
    val wallpaperBackdrop = LocalBackdrop.current
    val effectiveBackdrop = remember(backdrop, wallpaperBackdrop) {
        if (backdrop != null && wallpaperBackdrop != null && backdrop !== wallpaperBackdrop)
            com.ella.music.ui.components.liquid.CombinedBackdrop(wallpaperBackdrop, backdrop)
        else backdrop ?: wallpaperBackdrop
    }
    val canvas = ellaPageCanvasColor()
    val blurSettings = SettingsManager.getInstance(androidx.compose.ui.platform.LocalContext.current)
    val initialProgressive = remember(blurSettings) {
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { blurSettings.progressiveTopBarBlur.first() }
    }
    val progressiveAllowed by blurSettings.progressiveTopBarBlur.collectAsState(initial = initialProgressive)
    // Progressive mode mirrors the Miuix demo's BlurredBar: one variable-radius blur whose tint
    // rides the blur's own colour pipeline, so blur and tint ease out together along the same
    // smoothstep^curve ramp. The layer spills [ProgressiveOverhang] below the bar so the ramp
    // finishes over content instead of on the bar's bottom edge. null = opaque `color` bar.
    val shaderSupported = remember { isRuntimeShaderSupported() }
    // Like the demo's `rememberLayerBackdrop { drawRect(surface); drawContent() }`: a content
    // layer recorded over transparency blurs into opaque halos (the shaders renormalise alpha),
    // so without a wallpaper give it the bar's opaque page colour underneath.
    val blurBackdrop = remember(effectiveBackdrop, wallpaperBackdrop, color) {
        if (effectiveBackdrop != null && wallpaperBackdrop == null && color.alpha >= 1f)
            OpaqueBaseBackdrop(color, effectiveBackdrop)
        else effectiveBackdrop
    }
    val progressiveLayer: Modifier? = if (enableProgressiveBlur && progressiveAllowed) {
        if (blurBackdrop != null && shaderSupported) {
            val tint = BlurDefaults.blurColors(
                blendColors = listOf(BlendColorEntry(color = canvas.copy(alpha = ProgressiveTintAlpha)))
            )
            val blur = Modifier.progressiveTextureBlur(
                backdrop = blurBackdrop,
                shape = RectangleShape,
                blurRadius = ProgressiveBlurRadius,
                gradient = ProgressiveGradient,
                colors = tint
            )
            // Without a content backdrop the blur only samples the wallpaper, whose sharp clear
            // end would still cover the list; fade the layer's alpha on the same curve instead.
            val hasContentBackdrop = backdrop != null && backdrop !== wallpaperBackdrop
            if (hasContentBackdrop) blur else Modifier.easedAlphaFade().then(blur)
        } else {
            // No backdrop, or no RuntimeShader (API < 33, where drawBackdrop is a no-op anyway):
            // tint-only, on the same eased curve.
            val fallbackTint = remember(canvas) { easedFadeBrush(canvas, FallbackTintAlpha) }
            Modifier.background(fallbackTint)
        }
    } else null
    val barBackground = if (progressiveLayer == null) Modifier.background(color) else Modifier

    val effectiveActions: @Composable RowScope.() -> Unit = {
        actions()
        settingsCloseAction?.let { close ->
            IconButton(onClick = close) {
                Icon(
                    imageVector = MiuixIcons.Regular.Close,
                    contentDescription = stringResource(R.string.common_close),
                    tint = titleColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
    // Double-tap-to-top convention: with a visible title only the title area reacts,
    // so the gesture never competes with navigation/action buttons; a title-less bar
    // listens on its whole surface (buttons still win because they consume the tap).
    fun Modifier.doubleTapTitle(): Modifier =
        if (onDoubleTapTitle != null) {
            pointerInput(onDoubleTapTitle) {
                detectTapGestures(onDoubleTap = { onDoubleTapTitle() })
            }
        } else {
            this
        }

    val topInset = if (defaultWindowInsetsPadding) {
        WindowInsets.statusBarsIgnoringVisibility.only(WindowInsetsSides.Top)
    } else {
        WindowInsets(0, 0, 0, 0)
    }

    if (centeredTitle) {
        TopBarSurface(modifier, barBackground, progressiveLayer) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(topInset)
            ) {
                SmallTopAppBar(
                    title = title,
                    color = Color.Transparent,
                    titleColor = titleColor,
                    subtitle = subtitle,
                    subtitleColor = subtitleColor,
                    navigationIcon = navigationIcon,
                    actions = effectiveActions,
                    scrollBehavior = scrollBehavior,
                    defaultWindowInsetsPadding = false,
                    titlePadding = titlePadding,
                    navigationIconPadding = navigationIconPadding,
                    actionIconPadding = actionIconPadding,
                    bottomContent = bottomContent
                )
                if (onDoubleTapTitle != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .width(220.dp)
                            .height(56.dp)
                            .doubleTapTitle()
                    )
                }
            }
        }
        return
    }

    TopBarSurface(modifier, barBackground, progressiveLayer) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(topInset)
                .then(
                    if (title.isBlank()) Modifier.doubleTapTitle() else Modifier
                )
        ) {
            val availableTitleWidth =
                (maxWidth - titleStartPadding - titleEndPadding).coerceAtLeast(0.dp)
            SmallTopAppBar(
                title = "",
                color = Color.Transparent,
                titleColor = titleColor,
                subtitle = subtitle,
                subtitleColor = subtitleColor,
                navigationIcon = navigationIcon,
                actions = effectiveActions,
                scrollBehavior = scrollBehavior,
                defaultWindowInsetsPadding = false,
                titlePadding = titlePadding,
                navigationIconPadding = navigationIconPadding,
                actionIconPadding = actionIconPadding,
                bottomContent = bottomContent
            )
            Text(
                text = title,
                color = titleColor,
                maxLines = 1,
                fontSize = MiuixTheme.textStyles.title3.fontSize,
                fontWeight = FontWeight.Medium,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = titleStartPadding, top = 12.dp)
                    .width(availableTitleWidth)
                    .then(if (title.isNotBlank()) Modifier.doubleTapTitle() else Modifier)
            )
        }
    }
}

/** Miuix demo (example/shared/.../utils/PageUtils.kt `BlurredBar`) progressive-bar values. */
private const val ProgressiveBlurRadius = 10f
private const val ProgressiveTintAlpha = 0.3f
private val ProgressiveGradient = ProgressiveBlur.Top.copy(curve = 2.2f)

/** Tint-only fallback is heavier since there is no blur to separate the title from content. */
private const val FallbackTintAlpha = 0.86f

/** How far the progressive layer spills below the bar so its ramp ends over content. */
private val ProgressiveOverhang = 16.dp

private const val EasedFadeStops = 12

/**
 * Hosts the bar with its background. The progressive layer is a separate matchParentSize child
 * whose drawn height is stretched by [ProgressiveOverhang] without changing the bar's measured
 * height, so padding/scroll math of every caller stays the same.
 */
@Composable
private fun TopBarSurface(
    modifier: Modifier,
    barBackground: Modifier,
    progressiveLayer: Modifier?,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth().then(barBackground)) {
        if (progressiveLayer != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .overhangBelow(ProgressiveOverhang)
                    .then(progressiveLayer)
            )
        }
        content()
    }
}

/** Lays the node out [extra] taller than its slot while reporting the slot size to the parent. */
private fun Modifier.overhangBelow(extra: Dp): Modifier = layout { measurable, constraints ->
    if (!constraints.hasBoundedHeight || !constraints.hasBoundedWidth) {
        val placeable = measurable.measure(constraints)
        return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
    }
    val placeable = measurable.measure(
        Constraints.fixed(constraints.maxWidth, constraints.maxHeight + extra.roundToPx())
    )
    layout(constraints.maxWidth, constraints.maxHeight) { placeable.place(0, 0) }
}

/**
 * Same weight the Miuix progressive shaders use for the tint ramp (`rampWeight`):
 * smoothstep(1 − smoothstep(t)^curve). Zero slope at both ends, so no Mach band where it ends.
 */
private fun easedFadeWeight(t: Float): Float {
    val s = t * t * (3f - 2f * t)
    val w = 1f - s.pow(ProgressiveGradient.curve)
    return w * w * (3f - 2f * w)
}

private fun easedFadeBrush(color: Color, maxAlpha: Float): Brush = Brush.verticalGradient(
    *Array(EasedFadeStops + 1) { i ->
        val t = i / EasedFadeStops.toFloat()
        t to color.copy(alpha = color.alpha * maxAlpha * easedFadeWeight(t))
    }
)

private val EasedAlphaMask: Brush = easedFadeBrush(Color.Black, 1f)

/**
 * Draws [color] under [content] (whose offset residuals it forwards, so downscaled sampling stays
 * aligned). The rect is oversized to also cover the blur's padding ring, recorded under a translate.
 */
@Stable
private class OpaqueBaseBackdrop(
    val color: Color,
    val content: Backdrop,
) : Backdrop {

    override val isCoordinatesDependent: Boolean get() = content.isCoordinatesDependent

    override val offsetResidualX: Float get() = content.offsetResidualX
    override val offsetResidualY: Float get() = content.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Int,
    ) {
        drawRect(
            color = color,
            topLeft = Offset(-size.width, -size.height),
            size = Size(size.width * 3f, size.height * 3f)
        )
        with(content) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
    }
}

/** Masks the following layers' alpha with the eased ramp (offscreen + DstIn). */
private fun Modifier.easedAlphaFade(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(brush = EasedAlphaMask, blendMode = BlendMode.DstIn)
    }
