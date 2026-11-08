package com.ella.music.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.ResolvedTextDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import com.ella.music.R
import kotlin.math.roundToInt
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Pin
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Renders [text] and parks the pin badge at the end of the final visible line. The position
 * comes from the measured text layout, so the icon follows the last glyph — including the
 * ellipsis when the title is truncated — instead of floating at the row's trailing edge.
 *
 * The title always keeps its full width (#748): when the pin does not fit after the last
 * glyph, the tail of the last line fades out with a `DstIn` mask and the pin is drawn at the
 * line's end, so the title is never compressed and the badge never collides with glyphs.
 */
@Composable
internal fun TextWithEndPin(
    text: String,
    pinned: Boolean,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
    textAlign: TextAlign? = null
) {
    if (!pinned) {
        Text(
            text = text,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            textAlign = textAlign,
            modifier = modifier
        )
        return
    }
    val pinContentDescription = stringResource(R.string.common_pin_to_top)
    val pinSize = 13.dp
    val pinGap = 4.dp
    val fadeLength = 14.dp
    val pinTint = MiuixTheme.colorScheme.primary
    SubcomposeLayout(modifier = modifier) { constraints ->
        val pinPx = pinSize.toPx()
        val gapPx = pinGap.toPx()
        val fadePx = fadeLength.toPx()
        fun measureTitle(
            slot: String,
            fade: (Modifier.() -> Modifier)? = null
        ): Pair<androidx.compose.ui.layout.Placeable, TextLayoutResult?> {
            var layout: TextLayoutResult? = null
            val placeable = subcompose(slot) {
                Text(
                    text = text,
                    fontSize = fontSize,
                    lineHeight = lineHeight,
                    fontWeight = fontWeight,
                    color = color,
                    maxLines = maxLines,
                    overflow = overflow,
                    textAlign = textAlign,
                    onTextLayout = { layout = it },
                    modifier = fade?.invoke(Modifier) ?: Modifier
                )
            }.first().measure(constraints.copy(minWidth = 0))
            return placeable to layout
        }
        var (title, layout) = measureTitle("plain")
        val result = layout
        val rtl = result?.takeIf { it.lineCount > 0 }
            ?.let { it.getParagraphDirection(it.lineCount - 1) == ResolvedTextDirection.Rtl }
            ?: false
        val fits = result?.let { res ->
            if (res.hasVisualOverflow || res.lineCount == 0) return@let false
            val last = res.lineCount - 1
            val width = if (constraints.hasBoundedWidth) constraints.maxWidth.toFloat() else Float.POSITIVE_INFINITY
            if (res.getParagraphDirection(last) == ResolvedTextDirection.Rtl) {
                res.getLineLeft(last) - gapPx - pinPx >= 0f
            } else {
                res.getLineRight(last) + gapPx + pinPx <= width
            }
        } ?: false
        var pinX = 0f
        var pinY = 0f
        var showPin = false
        if (result != null && result.lineCount > 0 && text.isNotEmpty()) {
            val lastLine = result.lineCount - 1
            val lineTop = result.getLineTop(lastLine)
            val lineHeightPx = result.getLineBottom(lastLine) - lineTop
            pinY = lineTop + (lineHeightPx - pinPx) / 2f
            showPin = true
            if (fits) {
                val endChar = (result.getLineEnd(lastLine, visibleEnd = true) - 1)
                    .coerceIn(result.getLineStart(lastLine), (text.length - 1).coerceAtLeast(0))
                val charBox = result.getBoundingBox(endChar)
                pinX = if (rtl) charBox.left - gapPx - pinPx else charBox.right + gapPx
            } else {
                // Full-width last line: the badge parks at the line's end and the text tail
                // under it fades out, so the title keeps the same layout it has unpinned.
                pinX = if (rtl) 0f else (title.width.toFloat() - pinPx).coerceAtLeast(0f)
                val bandTop = lineTop
                val bandBottom = lineTop + lineHeightPx
                val fadeStart: Float
                val fadeEnd: Float
                if (rtl) {
                    fadeStart = pinX + pinPx + gapPx
                    fadeEnd = fadeStart + fadePx
                } else {
                    fadeEnd = pinX - gapPx * 0.5f
                    fadeStart = (fadeEnd - fadePx).coerceAtLeast(0f)
                }
                title = measureTitle("faded") {
                    graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
                        .drawWithContent {
                            drawContent()
                            val brush = if (rtl) {
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Black),
                                    startX = fadeStart,
                                    endX = fadeEnd
                                )
                            } else {
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Black, Color.Transparent),
                                    startX = fadeStart,
                                    endX = fadeEnd
                                )
                            }
                            val left = if (rtl) 0f else fadeStart
                            val right = if (rtl) fadeEnd else size.width
                            drawRect(
                                brush = brush,
                                topLeft = Offset(left, bandTop),
                                size = Size(right - left, bandBottom - bandTop),
                                blendMode = BlendMode.DstIn
                            )
                        }
                }.first
            }
        }
        val pin = subcompose("pin") {
            Icon(
                imageVector = MiuixIcons.Regular.Pin,
                contentDescription = pinContentDescription,
                tint = pinTint,
                modifier = Modifier.size(pinSize)
            )
        }.first().measure(androidx.compose.ui.unit.Constraints())
        val width = if (fits && showPin) {
            maxOf(title.width, (pinX + pinPx).roundToInt())
        } else {
            title.width
        }.coerceIn(constraints.minWidth, constraints.maxWidth)
        layout(width, title.height.coerceAtLeast(constraints.minHeight)) {
            title.place(0, 0)
            if (showPin) pin.place(pinX.roundToInt(), pinY.roundToInt())
        }
    }
}
