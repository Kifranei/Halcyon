package com.ella.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.unit.dp
import com.ella.music.data.BottomBarGlassEffect
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GlassPill(
    backdrop: Backdrop?,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape? = null,
    cornerRadiusDp: Float? = null,
    blurRadius: Float = 34f,
    glassEffect: BottomBarGlassEffect = BottomBarGlassEffect.Blur,
    disableRefraction: Boolean = false,
    liquidGlassConfig: BottomBarLiquidGlassConfig? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val resolvedShape = shape ?: RoundedCornerShape(
        (cornerRadiusDp ?: LocalBottomBarCornerRadiusDp.current).coerceIn(0f, 32f).dp
    )
    val resolvedLiquidGlassConfig = liquidGlassConfig ?: LocalBottomBarLiquidGlassConfig.current
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val isInDark = !isLight
    val containerColor = bottomBarGlassContainerColor(
        isLight = isLight,
        glassEffect = glassEffect,
        lightAlpha = 0.56f,
        darkAlpha = 0.58f,
        lightLiquidAlpha = 0.34f,
        darkLiquidAlpha = 0.38f
    )

    val glassModifier = if (backdrop != null) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { resolvedShape },
            effects = {
                applyBottomBarGlassEffect(
                    glassEffect = glassEffect,
                    blurRadius = blurRadius,
                    liquidBlurRadius = resolvedLiquidGlassConfig.blurRadiusDp,
                    liquidRefractionHeight = resolvedLiquidGlassConfig.refractionHeightDp,
                    liquidRefractionAmount = resolvedLiquidGlassConfig.refractionAmountDp,
                    liquidChromaticAberration = resolvedLiquidGlassConfig.chromaticAberration,
                    disableRefraction = disableRefraction,
                )
            },
            highlight = {
                Highlight.Default.copy(
                    alpha = bottomBarGlassHighlightAlpha(isLight, glassEffect)
                )
            },
            onDrawSurface = {
                drawRect(containerColor)
            }
        )
    } else {
        Modifier.background(containerColor, resolvedShape)
    }

    Box(
        modifier = modifier
            .clip(resolvedShape)
            .dropShadow(
                shape = resolvedShape,
                shadow = Shadow(
                    radius = 10.dp,
                    color = Color.Black,
                    alpha = if (isInDark) 0.2f else 0.1f,
                ),
            )
            .then(glassModifier),
        content = content
    )
}
