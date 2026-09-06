package com.ella.music.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import com.ella.music.data.BottomBarGlassEffect
import com.ella.music.ui.components.liquid.lens
import top.yukonga.miuix.kmp.blur.BackdropEffectScope
import top.yukonga.miuix.kmp.blur.blur

/**
 * Runtime knobs for the refractive bottom-bar surfaces. Defaults intentionally match the
 * existing Halcyon look; settings only opt into stronger/weaker lens and blur treatment.
 */
@Immutable
data class BottomBarLiquidGlassConfig(
    val blurRadiusDp: Float = 6f,
    val refractionHeightDp: Float = 24f,
    val refractionAmountDp: Float = 24f,
    val chromaticAberration: Float = 0f,
)

/** Shared only by the bottom dock subtree; other glass surfaces keep their own defaults. */
val LocalBottomBarCornerRadiusDp = staticCompositionLocalOf { 32f }
val LocalBottomBarLiquidGlassConfig = staticCompositionLocalOf { BottomBarLiquidGlassConfig() }

internal fun BackdropEffectScope.applyBottomBarGlassEffect(
    glassEffect: BottomBarGlassEffect,
    blurRadius: Float,
    liquidBlurRadius: Float,
    disableRefraction: Boolean = false,
    liquidRefractionHeight: Float = liquidBlurRadius,
    liquidRefractionAmount: Float = liquidBlurRadius,
    liquidChromaticAberration: Float = 0f,
) {
    when (glassEffect) {
        BottomBarGlassEffect.Blur -> {
            blur(blurRadius.dp.toPx())
        }

        BottomBarGlassEffect.LiquidGlass -> {
            blur(liquidBlurRadius.coerceAtLeast(0f).dp.toPx())
            if (!disableRefraction) {
                runCatching {
                    val refractionHeight = liquidRefractionHeight.coerceAtLeast(0f).dp.toPx()
                    val refractionAmount = liquidRefractionAmount.coerceAtLeast(0f).dp.toPx()
                    lens(
                        refractionHeight = refractionHeight,
                        refractionAmount = refractionAmount,
                        chromaticAberration = liquidChromaticAberration.coerceIn(0f, 1f),
                    )
                }
            }
        }
    }
}

internal fun bottomBarGlassContainerColor(
    isLight: Boolean,
    glassEffect: BottomBarGlassEffect,
    lightAlpha: Float,
    darkAlpha: Float,
    lightLiquidAlpha: Float = 0.38f,
    darkLiquidAlpha: Float = 0.42f
): Color {
    val alpha = when (glassEffect) {
        BottomBarGlassEffect.Blur -> if (isLight) lightAlpha else darkAlpha
        BottomBarGlassEffect.LiquidGlass -> if (isLight) lightLiquidAlpha else darkLiquidAlpha
    }
    return if (isLight) {
        Color.White.copy(alpha = alpha)
    } else {
        Color(0xFF111114).copy(alpha = alpha)
    }
}

internal fun bottomBarGlassHighlightAlpha(isLight: Boolean, glassEffect: BottomBarGlassEffect): Float =
    when (glassEffect) {
        BottomBarGlassEffect.Blur -> if (isLight) 0.22f else 0.14f
        BottomBarGlassEffect.LiquidGlass -> if (isLight) 0.18f else 0.10f
    }

internal fun bottomBarGlassShadowAlpha(isLight: Boolean, glassEffect: BottomBarGlassEffect): Float =
    when (glassEffect) {
        BottomBarGlassEffect.Blur -> if (isLight) 0.12f else 0.30f
        BottomBarGlassEffect.LiquidGlass -> if (isLight) 0.12f else 0.26f
    }

internal fun Color.simpleLuminance(): Float {
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

internal fun Modifier.liquidGlassDepthOverlay(
    enabled: Boolean,
    isLight: Boolean,
    cornerRadius: Dp = 32.dp
): Modifier {
    if (!enabled) return this
    return drawWithCache {
        val radius = cornerRadius.toPx()
        val strokeWidth = 1.dp.toPx()
        val topHighlight = if (isLight) {
            Color.White.copy(alpha = 0.36f)
        } else {
            Color.White.copy(alpha = 0.18f)
        }
        val sideHighlight = if (isLight) {
            Color.White.copy(alpha = 0.20f)
        } else {
            Color.White.copy(alpha = 0.12f)
        }
        val bottomShade = if (isLight) {
            Color.Black.copy(alpha = 0.08f)
        } else {
            Color.Black.copy(alpha = 0.22f)
        }
        val topBrush = Brush.verticalGradient(
            0f to topHighlight,
            0.28f to sideHighlight,
            1f to Color.Transparent
        )
        val bottomBrush = Brush.verticalGradient(
            0f to Color.Transparent,
            0.72f to Color.Transparent,
            1f to bottomShade
        )

        onDrawWithContent {
            drawContent()
            drawRoundRect(
                brush = topBrush,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                blendMode = BlendMode.Plus
            )
            drawRoundRect(
                brush = bottomBrush,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                blendMode = BlendMode.Multiply
            )
            drawRoundRect(
                color = topHighlight,
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius, radius),
                style = Stroke(width = strokeWidth),
                blendMode = BlendMode.Plus
            )
        }
    }
}
