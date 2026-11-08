package com.ella.music.ui.components.liquid

import androidx.compose.runtime.Stable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BackdropEffectScope

/**
 * Samples [source] with its hue removed. OS4 popup glass keeps the backdrop's chroma
 * (SoftLight / HardLight / Luminosity), so a now-playing flow recorded as the backdrop
 * dyes the whole sheet. Luminance stays, so the blur still reads as glass.
 */
@Stable
class ChromaFreeBackdrop(
    private val source: Backdrop,
) : Backdrop {

    override val isCoordinatesDependent: Boolean get() = source.isCoordinatesDependent

    override val offsetResidualX: Float get() = source.offsetResidualX
    override val offsetResidualY: Float get() = source.offsetResidualY

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
        downscaleFactor: Float,
    ) {
        with(source) { drawBackdrop(density, coordinates, layerBlock, downscaleFactor) }
        val padding = (density as? BackdropEffectScope)?.padding ?: 0f
        val scaledPadding = padding / downscaleFactor
        drawRect(
            color = Color.White,
            topLeft = Offset(-scaledPadding, -scaledPadding),
            size = Size(size.width + scaledPadding * 2f, size.height + scaledPadding * 2f),
            blendMode = BlendMode.Saturation,
        )
    }
}
