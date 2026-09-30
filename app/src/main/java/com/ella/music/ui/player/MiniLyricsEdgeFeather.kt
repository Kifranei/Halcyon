package com.ella.music.ui.player

import android.os.Build
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.layer.CompositingStrategy
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp

/** Record the lyrics once. Blend crisp/blurred copies only near the viewport edges, then fade
 * both into the artwork. The central lyrics and the artwork underneath remain untouched. */
internal fun Modifier.miniLyricsEdgeFeather(
    edgeHeight: Dp = 28.dp,
    blurRadius: Dp = 2.8.dp,
    topEdgeHeight: Dp = edgeHeight,
    bottomEdgeHeight: Dp = edgeHeight
): Modifier = composed {
    val source = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val crispMask = rememberGraphicsLayer()
    val blurredMask = rememberGraphicsLayer()
    clipToBounds().drawWithCache {
        fun fraction(height: Dp) = (height.toPx() / size.height.coerceAtLeast(1f)).coerceIn(0f, 0.24f)
        val topFraction = fraction(topEdgeHeight)
        val bottomFraction = fraction(bottomEdgeHeight)
        fun mask(soft: Boolean): Brush {
            val ramp = (0..8).map { step ->
                val t = step / 8f
                val fade = t * t * (3f - 2f * t)
                val alpha = if (soft) fade * (1f - fade) else fade * fade
                t to Color.White.copy(alpha = alpha)
            }
            val solid = if (soft) Color.Transparent else Color.White
            val top = if (topFraction > 0f) ramp.map { topFraction * it.first to it.second }
                else listOf(0f to solid)
            val bottom = if (bottomFraction > 0f) ramp.asReversed().map {
                (1f - bottomFraction * it.first) to it.second
            } else listOf(1f to solid)
            return Brush.verticalGradient(colorStops = (top + bottom).toTypedArray())
        }
        val crispBrush = mask(soft = false)
        val softBrush = mask(soft = true)
        val fallbackBrush = Brush.verticalGradient(colorStops = arrayOf(
            0f to if (topFraction > 0f) Color.Transparent else Color.White,
            topFraction to Color.White,
            1f - bottomFraction to Color.White,
            1f to if (bottomFraction > 0f) Color.Transparent else Color.White))
        crispMask.compositingStrategy = CompositingStrategy.Offscreen
        blurredMask.compositingStrategy = CompositingStrategy.Offscreen
        // Android 10/11 retain the same transparent feather, without an unsupported GPU blur.
        val supportsBlur = Build.VERSION.SDK_INT >= 31
        blurred.renderEffect = if (supportsBlur) BlurEffect(blurRadius.toPx(), blurRadius.toPx(), TileMode.Decal) else null
        onDrawWithContent {
            source.record { this@onDrawWithContent.drawContent() }
            if (supportsBlur) {
                blurred.record { drawLayer(source) }
                blurredMask.record {
                    drawLayer(blurred)
                    drawRect(softBrush, blendMode = BlendMode.DstIn)
                }
                drawLayer(blurredMask)
            }
            crispMask.record {
                drawLayer(source)
                drawRect(if (supportsBlur) crispBrush else fallbackBrush, blendMode = BlendMode.DstIn)
            }
            drawLayer(crispMask)
        }
    }
}
