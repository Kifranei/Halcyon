package com.ella.music.ui.poster

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.*
import kotlin.random.Random

/**
 * Original clockwork scene following the pendolo layout: a gear train hugs the left edge,
 * lyric lines ride the wheel's right rim, and a needle points at the line in focus.
 * Glyphs keep the shared sung-progress renderer; gears use a stepped escapement tick.
 */
@Composable
internal fun PosterPendoloScene(
    lyrics: List<LyricLine>, currentIndex: Int, position: State<Long>, seconds: State<Float>,
    fallbackTitle: String, showTranslation: Boolean,
    fontFamily: FontFamily?, translationFontFamily: FontFamily?,
    fontWeight: FontWeight, fontScale: Float, modifier: Modifier = Modifier
) {
    val resolvedTypeface by LocalFontFamilyResolver.current.resolve(fontFamily, fontWeight)
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    // The focus line is far larger than rim text; rim lines reuse the same paint at a fraction.
    val fontPx = with(density) {
        (minOf(viewport.width * 0.052f, viewport.height * 0.115f).coerceAtLeast(1f) / density.density).sp.toPx()
    } * fontScale
    val paint = remember(resolvedTypeface, fontPx) {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = resolvedTypeface as? Typeface ?: Typeface.DEFAULT
            textSize = fontPx
            textAlign = Paint.Align.LEFT
        }
    }
    val stars = remember { Random(88).let { r -> List(80) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    val start = currentIndex - 5
    val window = remember(lyrics, currentIndex, paint) {
        (start..currentIndex + 2).mapNotNull { i ->
            lyrics.getOrNull(i)?.let { i to posterCinematicLine(it, lyrics.getOrNull(i + 1), paint) }
        }
    }
    Box(modifier.fillMaxSize().onSizeChanged { viewport = it }) {
        Canvas(Modifier.fillMaxSize().testTag("theater-current-lyric")) {
            val t = seconds.value
            val w = size.width; val h = size.height
            val tint = Color(0xFFB9CDEB)
            drawRect(Color(0xFF0A0E1A))
            stars.forEachIndexed { i, s ->
                val twinkle = (sin(t * (0.35f + s.third * 0.7f) + i * 1.3f) + 1f) / 2f
                drawCircle(tint.copy(alpha = 0.05f + 0.14f * twinkle),
                    0.8f + s.third * 1.6f, Offset(s.first * w, s.second * h))
            }
            val wheel = Offset(w * 0.30f, h * 0.50f)
            val radius = h * 0.62f
            drawCircle(
                Brush.radialGradient(
                    listOf(Color(0xFFA9C4E8).copy(alpha = 0.15f), Color(0xFF152238).copy(alpha = 0.10f), Color.Transparent),
                    wheel, radius * 0.88f
                ), radius * 0.88f, wheel
            )
            // Escapement: the train creeps, then lands a short visible tick each beat.
            val beat = (t % 1.7f) / 1.7f
            val wheelDeg = t * 1.1f + beat * beat * (3f - 2f * beat) * 0.45f
            rotate(wheelDeg, wheel) {
                drawPath(posterGearPath(wheel, radius, 64), tint.copy(alpha = 0.28f), style = Stroke(1.6f))
                drawCircle(tint.copy(alpha = 0.22f), radius * 0.80f, wheel, style = Stroke(1.1f))
                drawCircle(tint.copy(alpha = 0.15f), radius * 0.62f, wheel, style = Stroke(0.8f))
                repeat(6) { i ->
                    val a = i * PI.toFloat() / 3f
                    drawLine(
                        tint.copy(alpha = 0.13f),
                        wheel + Offset(cos(a) * radius * 0.16f, sin(a) * radius * 0.16f),
                        wheel + Offset(cos(a) * radius * 0.80f, sin(a) * radius * 0.80f), 1.4f
                    )
                }
                repeat(6) { i ->
                    val a = i * PI.toFloat() / 3f + PI.toFloat() / 6f
                    drawCircle(tint.copy(alpha = 0.20f), radius * 0.15f,
                        wheel + Offset(cos(a) * radius * 0.55f, sin(a) * radius * 0.55f), style = Stroke(1f))
                }
                drawPath(posterGearPath(wheel, radius * 0.34f, 22), tint.copy(alpha = 0.26f), style = Stroke(1.2f))
                drawCircle(tint.copy(alpha = 0.20f), radius * 0.10f, wheel, style = Stroke(1f))
            }
            listOf(
                wheel + Offset(radius * 0.42f, radius * 0.62f) to radius * 0.20f,
                wheel + Offset(radius * 0.16f, -radius * 0.55f) to radius * 0.30f
            ).forEach { (gear, r) ->
                rotate(-wheelDeg * 2.4f + 11f, gear) {
                    drawPath(posterGearPath(gear, r, 20), tint.copy(alpha = 0.34f), style = Stroke(1.2f))
                    drawCircle(tint.copy(alpha = 0.20f), r * 0.5f, gear, style = Stroke(0.8f))
                    drawCircle(tint.copy(alpha = 0.16f), r * 0.16f, gear, style = Stroke(0.8f))
                }
            }
            // The needle always holds at three o'clock toward the focused line.
            val focusX = wheel.x + radius * 1.04f
            drawLine(tint.copy(alpha = 0.5f), wheel + Offset(radius * 0.28f, 0f),
                Offset(focusX - radius * 0.18f, wheel.y), 1.1f)
            drawCircle(tint.copy(alpha = 0.75f), 3.2f, Offset(focusX - radius * 0.16f, wheel.y))
            val native = drawContext.canvas.nativeCanvas
            val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
            val active = window.firstOrNull { it.first == currentIndex }
            if (active == null) {
                paint.clearShadowLayer(); paint.style = Paint.Style.FILL
                paint.color = Color.White.copy(alpha = 0.8f).toArgb()
                val fit = minOf(1f, w * 0.7f / paint.measureText(fallbackTitle).coerceAtLeast(1f))
                native.save(); native.translate(w / 2f, h / 2f); native.scale(fit, fit)
                native.drawText(fallbackTitle, -paint.measureText(fallbackTitle) / 2f, 0f, paint)
                native.restore()
            } else {
                window.forEach { (lineIndex, line) ->
                    val offset = lineIndex - currentIndex
                    val arc = radius * 0.96f
                    if (offset == 0) {
                        // Focus: centred past the rim, upright, sung fill sweeping per glyph.
                        val total = line.words.sumOf { it.width.toDouble() }.toFloat() +
                            paint.textSize * 0.12f * (line.words.size - 1).coerceAtLeast(0)
                        var x = focusX - total / 2f
                        line.words.forEach { word ->
                            word.glyphs.forEachIndexed { g, glyph ->
                                val advance = word.offsets[g + 1] - word.offsets[g]
                                drawLitGlyph(native, paint, glyph, x + word.offsets[g], wheel.y + baseline,
                                    advance, position.value, Color.White, unlit = 0.38f,
                                    opacity = 1f, glow = fontPx * 0.06f)
                            }
                            x += word.width + paint.textSize * 0.12f
                        }
                        if (showTranslation && !line.source.translation.isNullOrBlank()) {
                            paint.clearShadowLayer(); paint.style = Paint.Style.FILL
                            val original = paint.textSize
                            paint.textSize = original * 0.42f
                            paint.color = tint.copy(alpha = 0.62f).toArgb()
                            native.drawText(line.source.translation.trim(),
                                focusX - paint.measureText(line.source.translation.trim()) / 2f,
                                wheel.y + fontPx * 0.98f, paint)
                            paint.textSize = original
                        }
                    } else {
                        // Rim lines hug the wheel: angle grows with distance from focus and the
                        // baseline tips along the rim so the queue reads as a turning wheel.
                        val angleDeg = (offset * 24f).coerceIn(-120f, 120f)
                        val angle = angleDeg * PI.toFloat() / 180f
                        val at = wheel + Offset(cos(angle) * arc, sin(angle) * arc)
                        val depth = (1f - abs(offset) / 6.5f).coerceIn(0.08f, 1f)
                        val scale = 0.30f + depth * 0.30f
                        val alpha = (0.16f + depth * 0.44f) * if (offset < 0) 1f else 0.8f
                        native.save()
                        native.translate(at.x, at.y)
                        native.rotate(angleDeg * 0.85f)
                        native.scale(scale, scale)
                        paint.clearShadowLayer(); paint.style = Paint.Style.FILL
                        paint.color = tint.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
                        val text = line.source.text.trim()
                        native.drawText(text, -paint.measureText(text) / 2f, baseline, paint)
                        native.restore()
                    }
                }
            }
        }
    }
}
