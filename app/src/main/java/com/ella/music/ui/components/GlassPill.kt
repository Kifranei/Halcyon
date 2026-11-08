package com.ella.music.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.VisibilityThreshold
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ella.music.data.BottomBarGlassEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.material.GlassEdgeStrokes
import top.yukonga.miuix.kmp.material.edgeStroke
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Finger-following spotlight for liquid-glass surfaces: the moment a finger lands, a soft
 * highlight springs up under the touch point and trails the drag; it fades on release.
 * The gesture pipe only observes pointers — it never consumes them, so press/drag handling
 * (click, morph, horizontal swipe) keeps working untouched.
 */
private class TouchHighlight(private val scope: CoroutineScope) {
    private val progressAnim = Animatable(0f)
    private val positionAnim = Animatable(Offset.Zero, Offset.VectorConverter)
    val progress get() = progressAnim.value
    val position get() = positionAnim.value

    fun press(at: Offset) {
        scope.launch { positionAnim.snapTo(at) }
        scope.launch {
            progressAnim.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f))
        }
    }

    fun moveTo(at: Offset) {
        scope.launch {
            positionAnim.animateTo(at, spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = Offset.VisibilityThreshold))
        }
    }

    fun release() {
        scope.launch {
            progressAnim.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f))
        }
    }
}

private fun Modifier.touchHighlight(highlight: TouchHighlight): Modifier =
    pointerInput(highlight) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            highlight.press(down.position)
            var tracking = true
            while (tracking) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { change ->
                    if (change.id == down.id && change.pressed) {
                        highlight.moveTo(change.position)
                    }
                }
                if (event.changes.none { it.pressed }) tracking = false
            }
            highlight.release()
        }
    }.drawWithContent {
        drawContent()
        val progress = highlight.progress
        if (progress > 0.01f) {
            drawRect(Color.White.copy(alpha = 0.07f * progress), blendMode = BlendMode.Plus)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.22f * progress),
                        Color.Transparent
                    ),
                    center = highlight.position,
                    radius = size.minDimension.coerceAtLeast(1f) * 1.2f
                ),
                blendMode = BlendMode.Plus
            )
        }
    }

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
    touchHighlight: Boolean = false,
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
            onDrawSurface = {
                drawRect(containerColor)
            }
        ).edgeStroke(
            shape = resolvedShape,
            stroke = GlassEdgeStrokes.forTheme(
                isDark = isInDark,
                light = GlassEdgeStrokes.SmallLight,
                dark = GlassEdgeStrokes.SmallDark,
            ),
            alpha = bottomBarGlassHighlightAlpha(isLight, glassEffect),
        )
    } else {
        Modifier.background(containerColor, resolvedShape)
    }

    val highlightEnabled = touchHighlight && backdrop != null
    val highlightScope = rememberCoroutineScope()
    val highlight = if (highlightEnabled) remember { TouchHighlight(highlightScope) } else null

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
            .then(glassModifier)
            .then(if (highlight != null) Modifier.touchHighlight(highlight) else Modifier),
        content = content
    )
}
