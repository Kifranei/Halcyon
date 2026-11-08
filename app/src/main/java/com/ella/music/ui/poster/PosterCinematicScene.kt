package com.ella.music.ui.poster

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFontFamilyResolver
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.LyricLine
import top.yukonga.miuix.kmp.basic.Text
import kotlin.math.*
import kotlin.random.Random

/** Native scene compositions informed by Folia's Tempera, Lumiere, Claddagh and Sonnet.
 * Layouts and glyph metrics are cached; only drawing reads the live playback clock. */
@Composable
internal fun PosterCinematicScene(
    style: PosterLyricStyle, lyrics: List<LyricLine>, currentIndex: Int, position: State<Long>, time: State<Float>,
    fallbackTitle: String, showTranslation: Boolean, showPronunciation: Boolean,
    fontFamily: FontFamily?, translationFontFamily: FontFamily?, fontWeight: FontWeight, fontScale: Float,
    modifier: Modifier = Modifier
) {
    val resolvedTypeface by LocalFontFamilyResolver.current.resolve(fontFamily, fontWeight)
    val density = LocalDensity.current
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    val fontPx = with(density) {
        (minOf(viewport.width * 0.065f, viewport.height * 0.155f).coerceAtLeast(1f) / this.density).sp.toPx()
    } * fontScale
    val paint = remember(resolvedTypeface, fontPx) { Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = resolvedTypeface as? Typeface ?: Typeface.DEFAULT
        textSize = fontPx
        textAlign = Paint.Align.LEFT
    } }
    val active = lyrics.getOrNull(currentIndex)
    val window = remember(lyrics, currentIndex, paint, style) {
        (currentIndex - (if (style == PosterLyricStyle.Glow) 6 else if (style == PosterLyricStyle.Orbit) 4 else 1)..currentIndex + (if (style == PosterLyricStyle.Orbit) 3 else 1)).mapNotNull { index ->
            val line = lyrics.getOrNull(index) ?: return@mapNotNull null
            posterCinematicLine(line, lyrics.getOrNull(index + 1), paint)
        }
    }
    val accent = top.yukonga.miuix.kmp.theme.MiuixTheme.colorScheme.primary
    val primary = window.firstOrNull { it.source == active }
    val collage = remember(primary, viewport, fontPx, style) {
        posterTypeLayout(primary?.words?.map { it.width }.orEmpty(), fontPx * 1.12f,
            viewport.width.toFloat(), viewport.height * 0.65f, active?.text?.hashCode() ?: 0,
            sonnet = style == PosterLyricStyle.Sonnet)
    }
    val particles = remember { Random(615).let { r -> List(100) { Triple(r.nextFloat(), r.nextFloat(), r.nextFloat()) } } }
    Box(modifier.fillMaxSize().onSizeChanged { viewport = it }) {
        Canvas(Modifier.fillMaxSize().testTag("theater-current-lyric")) {
            val seconds = position.value / 1000f
            val elapsed = if (active == null) seconds else (position.value - active.timeMs) / 1000f
            val seed = active?.text?.hashCode() ?: fallbackTitle.hashCode()
            cinematicBackdrop(style, seconds, seed, particles, paint, primary?.source?.text.orEmpty())
            val native = drawContext.canvas.nativeCanvas
            if (primary == null) {
                paint.color = Color.White.copy(alpha = 0.8f).toArgb()
                paint.style = Paint.Style.FILL; paint.clearShadowLayer()
                val fit = minOf(1f, size.width * 0.8f / paint.measureText(fallbackTitle).coerceAtLeast(1f))
                native.save(); native.translate(size.width / 2, size.height / 2); native.scale(fit, fit)
                native.drawText(fallbackTitle, -paint.measureText(fallbackTitle) / 2, 0f, paint); native.restore()
            } else when (style) {
                PosterLyricStyle.Orbit -> drawOrbitElliptical(window, primary, position.value, paint)
                PosterLyricStyle.Glow -> drawCinematicGlow(window, primary, collage, position.value, seconds, paint, showPronunciation)
                PosterLyricStyle.ColorField -> drawColorFieldShots(primary, position.value, seconds, seed, paint, showPronunciation, accent)
                PosterLyricStyle.Sonnet -> drawSonnetComposition(primary, collage, position.value, seconds, seed, particles, paint, showPronunciation)
                else -> {
                    // Generic collage fallback for styles without a dedicated composition.
                    val zoom = 1.02f + sin(elapsed * 0.23f) * 0.025f
                    native.save()
                    native.translate(size.width / 2, size.height * 0.51f)
                    native.scale(zoom, zoom)
                    primary.words.forEachIndexed { i, word ->
                        val box = collage.getOrNull(i) ?: return@forEachIndexed
                        native.save(); native.translate(box.x, box.y); native.rotate(box.rotation)
                        native.scale(box.scale, box.scale)
                        val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2
                        word.glyphs.forEachIndexed { g, glyph ->
                            val advance = word.offsets[g + 1] - word.offsets[g]
                            val entrance = smooth(((position.value - glyph.startMs + 450f) / 450f).coerceIn(0f, 1f))
                            val motion = (1f - entrance) * fontPx * 0.9f
                            val direction = (i + g) % 4
                            val x = word.offsets[g] - word.width / 2f +
                                when (direction) { 0 -> -motion; 1 -> motion; else -> 0f }
                            val y = baseline + when (direction) { 2 -> motion; 3 -> -motion; else -> 0f }
                            drawLitGlyph(native, paint, glyph, x, y, advance, position.value,
                                Color.White, unlit = 0.27f, opacity = entrance)
                        }
                        if (showPronunciation && !word.line.pronunciation.isNullOrBlank()) {
                            drawReading(native, paint, word.line.pronunciation, -word.width / 2f, -fontPx * 0.67f)
                        }
                        native.restore()
                    }
                    native.restore()
                }
            }
        }
        // Keep secondary text readable and stationary while the primary scene fills the stage.
        val previous = lyrics.getOrNull(currentIndex - 1)
        if (style != PosterLyricStyle.Orbit && previous != null) {
            Text(previous.text, color = Color.White.copy(alpha = 0.54f), fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold, fontSize = 15.sp * fontScale, textAlign = TextAlign.Center,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(0.74f).padding(top = with(density) { (viewport.height * 0.14f).toDp() }))
        }
        if (active != null) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.8f)
            .padding(bottom = with(density) { (viewport.height * 0.11f).toDp() }), horizontalAlignment = Alignment.CenterHorizontally) {
            if (showPronunciation && active.pronunciationWords.isEmpty() && !active.pronunciation.isNullOrBlank())
                Text(active.pronunciation, color = Color.White.copy(alpha = 0.42f), fontFamily = translationFontFamily,
                    fontSize = 13.sp * fontScale, maxLines = 2, textAlign = TextAlign.Center)
            if (showTranslation && !active.translation.isNullOrBlank())
                Text(active.translation, color = Color.White.copy(alpha = 0.58f), fontFamily = translationFontFamily,
                    fontSize = 16.sp * fontScale, maxLines = 2, textAlign = TextAlign.Center)
            if (!active.backgroundText.isNullOrBlank())
                Text(active.backgroundText, color = Color.White.copy(alpha = 0.36f), fontFamily = fontFamily,
                    fontSize = 12.sp * fontScale, maxLines = 1, textAlign = TextAlign.Center)
        }
    }
}

private fun smooth(value: Float) = value * value * (3f - 2f * value)

/** Same stage geometry as the paper artwork. Select the more distant ink/paper tone,
 * as Folia's backdrop filter does, rather than coloring a whole glyph from its center. */
internal fun cinematicInkRegion(size: Size, seconds: Float, seed: Int): android.graphics.Path {
    val w = size.width; val h = size.height
    val travel = sin(seconds * 0.17f) * h * 0.035f
    fun band(degrees: Float, y: Float, height: Float): android.graphics.Path = android.graphics.Path().apply {
        addRect(-w * 0.4f, y, w * 1.4f, y + height, android.graphics.Path.Direction.CW)
        transform(android.graphics.Matrix().apply { setRotate(degrees, w / 2f, h * 0.48f) })
    }
    return when (Math.floorMod(seed, 4)) {
        // Each translucent band alone is dark. Only their overlap crosses the tone midpoint.
        0 -> band(23f, h * 0.35f + travel, h * 0.24f).apply {
            op(band(-23f, h * 0.35f - travel, h * 0.24f), android.graphics.Path.Op.INTERSECT)
        }
        1 -> android.graphics.Path().apply {
            addRect(0f, h * 0.47f + travel, w, h * 1.47f + travel, android.graphics.Path.Direction.CW)
        }
        // The remaining artwork stays below mid-gray after alpha compositing.
        else -> android.graphics.Path()
    }
}

private fun drawReading(canvas: android.graphics.Canvas, paint: Paint, text: String, x: Float, y: Float) {
    val size = paint.textSize
    paint.clearShadowLayer(); paint.style = Paint.Style.FILL
    paint.textSize = size * 0.27f; paint.color = Color.White.copy(alpha = 0.55f).toArgb()
    canvas.drawText(text, x, y, paint); paint.textSize = size
}

internal fun drawLitGlyph(canvas: android.graphics.Canvas, paint: Paint, glyph: PosterTimedGlyph,
    x: Float, baseline: Float, advance: Float, position: Long, color: Color, unlit: Float,
    opacity: Float = 1f, glow: Float = 0f, outline: Boolean = false
) {
    val progress = posterGlyphProgress(glyph, position)
    paint.clearShadowLayer()
    paint.style = if (outline) Paint.Style.STROKE else Paint.Style.FILL
    paint.strokeWidth = paint.textSize * 0.009f
    paint.color = color.copy(alpha = (unlit * opacity).coerceIn(0f, 1f)).toArgb()
    canvas.drawText(glyph.text, x, baseline, paint)
    if (progress <= 0f || glyph.text.isBlank()) return
    val padding = paint.textSize * 0.15f + glow
    val left = x - padding; val right = x + advance + padding
    val top = baseline + paint.fontMetrics.top - padding
    val bottom = baseline + paint.fontMetrics.bottom + padding
    // A bounded glyph layer keeps the sweep soft without allocating a full-stage bitmap.
    if (progress < 1f) canvas.saveLayer(left, top, right, bottom, null) else canvas.save()
    paint.style = Paint.Style.FILL
    paint.color = color.copy(alpha = opacity.coerceIn(0f, 1f)).toArgb()
    if (glow > 0f) paint.setShadowLayer(glow, 0f, 0f, color.copy(alpha = opacity * 0.7f).toArgb())
    canvas.drawText(glyph.text, x, baseline, paint)
    paint.clearShadowLayer()
    if (progress < 1f) {
        val feather = minOf(advance * 0.32f, paint.textSize * 0.14f).coerceAtLeast(1f)
        val edge = x - feather + (advance + feather * 2f) * progress
        paint.shader = android.graphics.LinearGradient(edge - feather, 0f, edge + feather, 0f,
            intArrayOf(android.graphics.Color.WHITE, android.graphics.Color.TRANSPARENT), null, android.graphics.Shader.TileMode.CLAMP)
        paint.xfermode = android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_IN)
        paint.color = android.graphics.Color.WHITE
        canvas.drawRect(left, top, right, bottom, paint)
        paint.xfermode = null; paint.shader = null
    }
    canvas.restore()

}

/** Whole lines ride a tilted elliptical track; the sung line holds the front focus while
 * the queue wraps behind it, shrinking and fading with depth. Original implementation. */
private fun DrawScope.drawOrbitElliptical(window: List<CinematicLine>, active: CinematicLine, position: Long, paint: Paint) {
    val canvas = drawContext.canvas.nativeCanvas
    val centerX = size.width * 0.5f; val centerY = size.height * 0.50f
    val rx = size.width * 0.42f; val ry = size.height * 0.30f
    val tilt = -24f * PI.toFloat() / 180f
    val slot = 0.62f
    val activeIndex = window.indexOf(active).coerceAtLeast(0)
    val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    // The track creeps forward as the line is sung, easing the next line toward the front.
    var lineProgress = 0f
    val glyphCount = active.words.sumOf { it.glyphs.size }.coerceAtLeast(1)
    active.words.forEach { word -> word.glyphs.forEach { glyph ->
        lineProgress += posterGlyphProgress(glyph, position) / glyphCount
    } }
    lineProgress = lineProgress.coerceIn(0f, 1f)
    data class Slot(val line: CinematicLine, val x: Float, val y: Float, val scale: Float, val alpha: Float, val depth: Float, val current: Boolean)
    val slots = window.mapIndexedNotNull { index, line ->
        val offset = index - activeIndex
        val theta = offset * slot - lineProgress * slot * 0.28f
        val depth = ((cos(theta) + 1f) * 0.5f).coerceIn(0f, 1f)
        if (depth <= 0.02f && line !== active) return@mapIndexedNotNull null
        val ex = sin(theta) * rx; val ey = -cos(theta) * ry
        val x = centerX + ex * cos(tilt) - ey * sin(tilt)
        val y = centerY + ex * sin(tilt) + ey * cos(tilt) + ry * 0.52f
        val focus = (1f - abs(theta) / (slot * 1.4f)).coerceIn(0f, 1f)
        val scale = (0.40f + depth * 0.44f) * if (line === active) 1.22f else 1f
        val alpha = if (line === active) 1f else (0.10f + depth * 0.55f) * (0.5f + focus * 0.5f)
        Slot(line, x, y, scale, alpha.coerceIn(0f, 1f), depth, line === active)
    }
    slots.sortedBy { it.depth }.forEach { slotEntry ->
        val line = slotEntry.line
        canvas.save()
        canvas.translate(slotEntry.x, slotEntry.y)
        canvas.scale(slotEntry.scale, slotEntry.scale)
        val text = line.source.text.trim()
        if (slotEntry.current) {
            var left = -paint.measureText(text) / 2f
            line.words.forEach { word ->
                word.glyphs.forEachIndexed { g, glyph ->
                    val advance = word.offsets[g + 1] - word.offsets[g]
                    drawLitGlyph(canvas, paint, glyph, left + word.offsets[g], baseline,
                        advance, position, Color.White, unlit = 0.45f, glow = paint.textSize * 0.10f)
                }
                left += word.width + paint.textSize * 0.12f
            }
        } else {
            paint.clearShadowLayer()
            paint.style = Paint.Style.FILL
            paint.color = Color.White.copy(alpha = slotEntry.alpha).toArgb()
            canvas.drawText(text, -paint.measureText(text) / 2f, baseline, paint)
        }
        canvas.restore()
    }
}

private fun DrawScope.drawCinematicGlow(window: List<CinematicLine>, active: CinematicLine,
    boxes: List<PosterTypeBox>, position: Long, seconds: Float, paint: Paint, readings: Boolean
) {
    val canvas = drawContext.canvas.nativeCanvas
    val warm = Color(0xFFFFF4D8)
    val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    // Finite, low-contrast word echoes follow the swaying light field. Never retain
    // expired glyphs at a fixed point or move the main sung text into that decoration.
    window.forEach { line -> line.words.forEachIndexed { index, word ->
        val pose = posterGlowEchoPose(word.glyphs.firstOrNull()?.startMs ?: line.source.timeMs,
            position, word.line.text.hashCode() xor line.source.timeMs.hashCode() xor index * 131)
        if (pose.alpha <= 0.001f) return@forEachIndexed
        canvas.save(); canvas.translate(size.width * pose.x, size.height * pose.y)
        canvas.rotate(pose.rotation); canvas.scale(pose.scale, pose.scale)
        paint.style = Paint.Style.STROKE; paint.strokeWidth = paint.textSize * 0.009f
        paint.clearShadowLayer(); paint.color = warm.copy(alpha = pose.alpha).toArgb()
        word.glyphs.forEachIndexed { glyphIndex, glyph ->
            val angle = glyphIndex * 2.39996f + index
            val drift = paint.textSize * pose.spread
            canvas.save()
            canvas.translate(word.offsets[glyphIndex] - word.width / 2f + cos(angle) * drift,
                sin(angle) * drift)
            canvas.rotate(sin(angle) * pose.spread * 12f)
            canvas.drawText(glyph.text, 0f, baseline, paint); canvas.restore()
        }
        canvas.restore()
    } }
    active.words.forEachIndexed { i, word ->
        val box = boxes.getOrNull(i) ?: return@forEachIndexed
        word.glyphs.forEachIndexed { g, glyph ->
            val x0 = size.width / 2f + box.x + (word.offsets[g] - word.width / 2f) * box.scale
            val y0 = size.height * 0.51f + box.y
            val seed = (glyph.text.hashCode() xor glyph.startMs.hashCode()).toFloat()
            val incoming = (1f - smooth(((position - glyph.startMs + 650f) / 650f).coerceIn(0f, 1f)))
            val drift = incoming * 0.7f
            val dx = sin(seed) * size.width * 0.10f * drift
            val dy = -size.height * (0.06f + abs(cos(seed)) * 0.16f) * drift
            val x = x0 + dx + sin(seconds * 0.7f + g) * paint.textSize * 0.018f
            val y = y0 + dy + cos(seconds * 0.6f + i) * paint.textSize * 0.014f
            val progress = posterGlyphProgress(glyph, position)
            if (progress > 0f) {
                val trail = Path().apply {
                    moveTo(x + paint.textSize * 0.15f, y - paint.textSize * 0.3f)
                    cubicTo(x - paint.textSize * 0.1f, y + paint.textSize * 0.4f,
                        x0 + sin(seed) * paint.textSize * 0.5f, y0 + paint.textSize, x0, y0 + paint.textSize * 1.8f)
                }
                drawPath(trail, warm.copy(alpha = 0.10f * incoming), style = Stroke(0.7f))
                val flash = (1f - (position - glyph.startMs).coerceAtLeast(0) / 230f).coerceIn(0f, 1f)
                if (flash > 0f) cinematicSpark(Offset(x + paint.textSize * 0.2f, y), paint.textSize * 0.20f * flash, warm, flash)
            }
            canvas.save(); canvas.translate(x, y); canvas.rotate(box.rotation)
            canvas.scale(box.scale, box.scale)
            val advance = word.offsets[g + 1] - word.offsets[g]
            drawLitGlyph(canvas, paint, glyph, 0f, baseline, advance, position, warm,
                unlit = 0.18f, opacity = 1f, glow = paint.textSize * 0.22f, outline = true)
            if (readings && g == 0 && !word.line.pronunciation.isNullOrBlank()) drawReading(canvas, paint, word.line.pronunciation, 0f, -paint.textSize * 0.67f)
            canvas.restore()
        }
    }
}

private fun DrawScope.cinematicSpark(at: Offset, radius: Float, color: Color, alpha: Float) {
    if (radius <= 0f) return
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha * 0.45f), Color.Transparent), at, radius * 4f), radius * 4f, at)
    drawLine(color.copy(alpha = alpha * 0.6f), at - Offset(radius * 2f, 0f), at + Offset(radius * 2f, 0f), 0.8f)
    drawLine(color.copy(alpha = alpha), at - Offset(0f, radius), at + Offset(0f, radius), 0.8f)
    drawCircle(color.copy(alpha = alpha), radius * 0.1f, at)
}

private fun DrawScope.cinematicBackdrop(style: PosterLyricStyle, seconds: Float, seed: Int,
    particles: List<Triple<Float, Float, Float>>, paint: Paint, text: String
) {
    val w = size.width; val h = size.height
    val center = Offset(w * 0.5f, h * 0.48f)
    drawRect(Color(0xFF060709))
    when (style) {
        PosterLyricStyle.Orbit -> {
            // The same tilted track the lyric lines ride on, drawn as a faint guide.
            val trackCenter = Offset(w * 0.5f, h * 0.50f + h * 0.30f * 0.52f)
            val rx = w * 0.42f; val ry = h * 0.30f
            rotate(-24f, trackCenter) {
                drawOval(Color.White.copy(alpha = 0.10f),
                    trackCenter - Offset(rx, ry), Size(rx * 2f, ry * 2f), style = Stroke(1f))
                drawOval(Color.White.copy(alpha = 0.05f),
                    trackCenter - Offset(rx * 0.62f, ry * 0.62f), Size(rx * 1.24f, ry * 1.24f), style = Stroke(0.7f))
                repeat(9) { i ->
                    val a = i * PI.toFloat() * 2f / 9f + seconds * 0.06f
                    val dot = trackCenter + Offset(cos(a) * rx, sin(a) * ry)
                    drawCircle(Color.White.copy(alpha = 0.14f), 1.4f, dot)
                }
            }
            drawLine(Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.30f), Color.Transparent),
                Offset(w * 0.5f, h * 0.24f), Offset(w * 0.5f, h * 0.76f)),
                Offset(w * 0.5f, h * 0.24f), Offset(w * 0.5f, h * 0.76f), 0.9f)
        }
        PosterLyricStyle.ColorField -> Unit // Shot backdrops are drawn per cut by drawColorFieldShots.
        PosterLyricStyle.Glow -> {
            val warm = Color(0xFFFFE5AB)
            val light = Offset(w * (0.51f + sin(seconds * 0.13f) * 0.045f), h * 0.39f)
            drawCircle(Brush.radialGradient(listOf(warm.copy(alpha = 0.3f), warm.copy(alpha = 0.025f), Color.Transparent), light, h * 0.29f), h * 0.29f, light)
            drawLine(Brush.linearGradient(listOf(Color.Transparent, warm.copy(alpha = 0.5f), Color.Transparent),
                Offset(0f, light.y), Offset(w, light.y)), Offset(0f, light.y), Offset(w, light.y), 1.0f)
            rotate(-17f + sin(seconds * 0.15f) * 5f, light) {
                repeat(7) { i ->
                    val rx = w * (0.1f + i * 0.037f); val ry = h * (0.035f + i * 0.012f)
                    drawOval(warm.copy(alpha = 0.075f), light - Offset(rx, ry), Size(rx * 2, ry * 2), style = Stroke(0.7f))
                }
                drawLine(warm.copy(alpha = 0.065f), light - Offset(w * 0.65f, 0f), light + Offset(w * 0.65f, 0f), h * 0.02f)
            }
            particles.forEachIndexed { i, p ->
                val at = Offset(p.first * w, ((p.second + seconds * (0.005f + p.third * 0.006f)) % 1f) * h)
                val alpha = 0.12f + (sin(seconds * 1.1f + i) + 1f) * 0.20f
                if (i % 4 == 0) cinematicSpark(at, h * (0.0015f + p.third * 0.004f), warm, alpha)
                else drawCircle(warm.copy(alpha = alpha), 0.7f + p.third * 0.7f, at)
            }
            cinematicSpark(light, h * 0.024f, Color(0xFFFFF8DE), 0.88f)
            for (x in listOf(w * 0.1f, w * 0.9f)) for (y in listOf(h * 0.12f, h * 0.86f)) {
                val sx = if (x < w / 2) 1 else -1; val sy = if (y < h / 2) 1 else -1
                drawLine(warm.copy(alpha = 0.26f), Offset(x, y), Offset(x + sx * h * 0.045f, y), 0.8f)
                drawLine(warm.copy(alpha = 0.26f), Offset(x, y), Offset(x, y + sy * h * 0.045f), 0.8f)
            }
        }
        else -> Unit
    }
    if (style != PosterLyricStyle.Orbit && style != PosterLyricStyle.ColorField &&
        style != PosterLyricStyle.Sonnet && text.isNotBlank()) {
        val canvas = drawContext.canvas.nativeCanvas
        val original = paint.textSize
        paint.clearShadowLayer(); paint.style = Paint.Style.STROKE
        paint.strokeWidth = original * 0.012f; paint.color = Color.White.copy(alpha = 0.09f).toArgb()
        paint.textSize = original * if (style == PosterLyricStyle.Glow) 2.5f else 2f
        val words = text.split(' ').filter(String::isNotBlank)
        for (i in 0 until minOf(3, words.size)) {
            canvas.save(); canvas.translate(w * (0.25f + i * 0.2f), h * (0.3f + i * 0.22f))
            canvas.rotate(-18f + i * 12f)
            canvas.drawText(words[i], -paint.measureText(words[i]) / 2f, 0f, paint); canvas.restore()
        }
        paint.textSize = original; paint.style = Paint.Style.FILL
    }
}

private data class ColorFieldShot(val words: List<CinematicWord>, val seed: Int)

/** Split a line into 2..4-word shots; every cut lands on a word boundary. */
private fun posterColorFieldShots(words: List<CinematicWord>, seed: Int): List<ColorFieldShot> {
    if (words.isEmpty()) return emptyList()
    val random = Random(seed)
    val shots = mutableListOf<ColorFieldShot>()
    var index = 0
    while (index < words.size) {
        val take = minOf(words.size - index, 2 + random.nextInt(3))
        shots += ColorFieldShot(words.subList(index, index + take), seed * 31 + index)
        index += take
    }
    return shots
}

/** PV-style cut composition: each shot repaints the stage (two-tone, grayscale or
 * accent-block variants), then its words enter glyph by glyph with an inverted pass
 * where the paper and ink regions overlap. Original implementation. */
private fun DrawScope.drawColorFieldShots(
    active: CinematicLine, position: Long, seconds: Float, seed: Int,
    paint: Paint, readings: Boolean, accent: Color
) {
    val w = size.width; val h = size.height
    val shots = posterColorFieldShots(active.words, seed)
    if (shots.isEmpty()) return
    var shotIndex = 0
    shots.forEachIndexed { i, shot ->
        val startMs = shot.words.first().glyphs.firstOrNull()?.startMs ?: Long.MAX_VALUE
        if (startMs <= position) shotIndex = i
    }
    val shot = shots[shotIndex]
    val fontPx = paint.textSize
    val inkRegion = cinematicInkRegion(size, seconds, shot.seed)
    colorFieldBackdrop(shot.seed, accent, seconds)
    val canvas = drawContext.canvas.nativeCanvas
    val boxes = posterTypeLayout(shot.words.map { it.width }, fontPx * 1.12f,
        w, h * 0.65f, shot.seed, sonnet = false)
    val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    val zoom = 1.02f + sin(seconds * 0.23f + shotIndex) * 0.025f
    repeat(2) { pass ->
        canvas.save()
        if (pass == 1) canvas.clipPath(inkRegion) else canvas.clipOutPath(inkRegion)
        canvas.translate(w / 2f, h * 0.51f)
        canvas.scale(zoom, zoom)
        shot.words.forEachIndexed { i, word ->
            val box = boxes.getOrNull(i) ?: return@forEachIndexed
            canvas.save(); canvas.translate(box.x, box.y); canvas.rotate(box.rotation); canvas.scale(box.scale, box.scale)
            word.glyphs.forEachIndexed { g, glyph ->
                val advance = word.offsets[g + 1] - word.offsets[g]
                val entrance = smooth(((position - glyph.startMs + 450f) / 450f).coerceIn(0f, 1f))
                val motion = (1f - entrance) * fontPx * 0.9f
                val direction = (i + g) % 4
                val x = word.offsets[g] - word.width / 2f +
                    when (direction) { 0 -> -motion; 1 -> motion; else -> 0f }
                val y = baseline + when (direction) { 2 -> motion; 3 -> -motion; else -> 0f }
                drawLitGlyph(canvas, paint, glyph, x, y, advance, position,
                    if (pass == 1) Color(0xFF090909) else Color.White,
                    unlit = 0.45f, opacity = entrance)
            }
            if (pass == 0 && readings && !word.line.pronunciation.isNullOrBlank()) {
                drawReading(canvas, paint, word.line.pronunciation, -word.width / 2f, -fontPx * 0.67f)
            }
            canvas.restore()
        }
        canvas.restore()
    }
}

private fun DrawScope.colorFieldBackdrop(seed: Int, accent: Color, seconds: Float) {
    val w = size.width; val h = size.height
    val center = Offset(w * 0.5f, h * 0.48f)
    val travel = sin(seconds * 0.17f) * h * 0.035f
    val paper = Color(0xFFB5B5B5)
    when (Math.floorMod(seed, 6)) {
        // Two-tone diagonal bands crossing over the sung area.
        0 -> {
            rotate(23f, center) { drawRect(paper.copy(alpha = 0.52f), Offset(-w * 0.4f, h * 0.35f + travel), Size(w * 1.8f, h * 0.24f)) }
            rotate(-23f, center) { drawRect(accent.copy(alpha = 0.30f), Offset(-w * 0.4f, h * 0.35f - travel), Size(w * 1.8f, h * 0.24f)) }
        }
        // Paper lower half with an accent slab off to the right.
        1 -> {
            drawRect(paper, Offset(0f, h * 0.47f + travel), Size(w, h))
            drawRect(accent.copy(alpha = 0.85f), Offset(w * 0.72f, 0f), Size(w * 0.28f, h * 0.47f))
        }
        // Ascending translucent strips like PV title cards.
        2 -> rotate(-17f, center) {
            repeat(3) { i -> drawRect(if (i == 1) accent.copy(alpha = 0.24f) else paper.copy(alpha = 0.22f + i * 0.18f),
                Offset(-w * 0.2f, h * (0.12f + i * 0.28f) + travel), Size(w * 1.4f, h * 0.18f)) }
        }
        // Grayscale: dark half on the left, paper on the right, thin accent seam.
        3 -> {
            drawRect(paper, Offset(w * 0.58f, 0f), Size(w * 0.42f, h))
            drawRect(Color(0xFF1A1A1A), Offset(0f, 0f), Size(w * 0.58f, h))
            drawRect(accent.copy(alpha = 0.8f), Offset(w * 0.565f, 0f), Size(w * 0.015f, h))
        }
        // Accent circle behind the focus block with a paper side panel.
        4 -> {
            drawCircle(accent.copy(alpha = 0.55f), h * 0.45f, center + Offset(w * 0.13f, 0f))
            drawCircle(Color(0xFF141414), h * 0.28f, center + Offset(w * 0.13f, 0f))
            drawRect(paper.copy(alpha = 0.36f), Offset(0f, h * 0.32f), Size(w * 0.44f, h * 0.19f))
        }
        // Near-black with a single accent band at lyric height.
        else -> {
            drawRect(accent.copy(alpha = 0.42f), Offset(0f, h * 0.42f), Size(w, h * 0.17f))
            drawRect(paper.copy(alpha = 0.10f), Offset(0f, h * 0.59f), Size(w, h * 0.015f))
        }
    }
    // Screen-print halftone, frame guides and diagonal hatching span the whole stage.
    for (row in 0 until 24) for (column in 0 until 48) {
        val p = posterHalftonePoint(row, column)
        drawCircle(Color.White.copy(alpha = 0.025f + p * 0.015f), 0.7f,
            Offset((column + p) * w / 48, (row + p) * h / 24))
    }
    rotate(7f, center) {
        drawLine(Color.White.copy(alpha = 0.16f), Offset(w * 0.81f, -h), Offset(w * 0.81f, h * 2), 0.8f)
        drawLine(Color.White.copy(alpha = 0.16f), Offset(-w, h * 0.38f), Offset(w * 2, h * 0.38f), 0.8f)
        repeat(25) { i -> drawLine(Color.White.copy(alpha = 0.055f), Offset(i * w * 0.02f, 0f), Offset(i * w * 0.02f + h * 0.3f, h * 0.3f), 0.7f) }
    }
}

private fun posterHalftonePoint(row: Int, column: Int): Float {
    var mixed = row * 73856093 xor column * 19349663
    mixed = (mixed xor (mixed ushr 16)) * 0x45d9f3b
    return ((mixed xor (mixed ushr 16)) and 0xffff).toFloat() / 65535f
}

/** Editorial print composition: the sung line sits inside a ruled page frame with
 * registration marks, an oversized outlined initial, film grain and a vignette.
 * Lit glyphs separate into red/cyan channels before resolving to white. */
private fun DrawScope.drawSonnetComposition(
    active: CinematicLine, boxes: List<PosterTypeBox>, position: Long, seconds: Float,
    seed: Int, particles: List<Triple<Float, Float, Float>>, paint: Paint, readings: Boolean
) {
    val w = size.width; val h = size.height
    sonnetBackdrop(seed, seconds, particles, paint, active.source.text)
    val canvas = drawContext.canvas.nativeCanvas
    val fontPx = paint.textSize
    val baseline = -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    val lineElapsed = ((position - active.source.timeMs) / 1000f).coerceAtLeast(0f)
    val sceneIn = smooth((lineElapsed / 0.55f).coerceIn(0f, 1f))
    val zoom = 0.97f + sceneIn * 0.03f + sin(seconds * 0.19f) * 0.012f
    canvas.save()
    canvas.translate(w / 2f, h * 0.51f)
    canvas.scale(zoom, zoom)
    active.words.forEachIndexed { i, word ->
        val box = boxes.getOrNull(i) ?: return@forEachIndexed
        canvas.save(); canvas.translate(box.x, box.y); canvas.rotate(box.rotation); canvas.scale(box.scale, box.scale)
        word.glyphs.forEachIndexed { g, glyph ->
            val advance = word.offsets[g + 1] - word.offsets[g]
            val entrance = smooth(((position - glyph.startMs + 450f) / 450f).coerceIn(0f, 1f)) * sceneIn
            val motion = (1f - entrance) * fontPx * 0.9f
            val direction = (i + g) % 4
            val x = word.offsets[g] - word.width / 2f + when (direction) { 0 -> -motion; 1 -> motion; else -> 0f }
            val y = baseline + when (direction) { 2 -> motion; 3 -> -motion; else -> 0f }
            canvas.save()
            canvas.rotate((1f - entrance) * if (i % 2 == 0) 14f else -14f, x + advance / 2f, y - fontPx / 2f)
            val lit = posterGlyphProgress(glyph, position)
            if (lit > 0f && lit < 1f && !glyph.text.isBlank()) {
                // Chromatic ghosts converge on the white master as the glyph lights up.
                val split = fontPx * 0.045f * (1f - lit)
                paint.clearShadowLayer(); paint.style = Paint.Style.FILL
                paint.color = Color(0xFFFF4A3D).copy(alpha = 0.38f * entrance).toArgb()
                canvas.drawText(glyph.text, x - split, y, paint)
                paint.color = Color(0xFF4AD8FF).copy(alpha = 0.38f * entrance).toArgb()
                canvas.drawText(glyph.text, x + split, y, paint)
            }
            drawLitGlyph(canvas, paint, glyph, x, y, advance, position, Color.White,
                unlit = 0.27f, opacity = entrance, glow = fontPx * 0.12f)
            canvas.restore()
        }
        if (readings && !word.line.pronunciation.isNullOrBlank()) {
            drawReading(canvas, paint, word.line.pronunciation, -word.width / 2f, -fontPx * 0.67f)
        }
        canvas.restore()
    }
    canvas.restore()
}

private fun DrawScope.sonnetBackdrop(seed: Int, seconds: Float,
    particles: List<Triple<Float, Float, Float>>, paint: Paint, text: String
) {
    val w = size.width; val h = size.height
    val random = Random(seed)
    val rule = Color.White.copy(alpha = 0.30f)
    // Page frame: outer rule, inner hairline, corner registration ticks.
    val fx = w * 0.07f; val fy = h * 0.13f; val fw = w * 0.86f; val fh = h * 0.72f
    drawRect(rule, Offset(fx, fy), Size(fw, fh), style = Stroke(1.1f))
    drawRect(rule.copy(alpha = 0.16f), Offset(fx + w * 0.012f, fy + w * 0.012f),
        Size(fw - w * 0.024f, fh - w * 0.024f), style = Stroke(0.6f))
    listOf(Offset(fx, fy), Offset(fx + fw, fy), Offset(fx, fy + fh), Offset(fx + fw, fy + fh)).forEach { corner ->
        val sx = if (corner.x < w / 2) 1f else -1f; val sy = if (corner.y < h / 2) 1f else -1f
        drawLine(rule, corner, corner + Offset(sx * h * 0.028f, 0f), 1.2f)
        drawLine(rule, corner, corner + Offset(0f, sy * h * 0.028f), 1.2f)
    }
    // Oversized outlined initial drifting behind the frame.
    val initial = text.firstOrNull { !it.isWhitespace() }?.uppercase() ?: return
    val canvas = drawContext.canvas.nativeCanvas
    val original = paint.textSize
    paint.clearShadowLayer(); paint.style = Paint.Style.STROKE
    paint.strokeWidth = original * 0.014f; paint.color = Color.White.copy(alpha = 0.10f).toArgb()
    paint.textSize = h * 0.62f
    canvas.save()
    canvas.translate(w * (0.72f + sin(seconds * 0.07f) * 0.015f), h * 0.62f)
    canvas.rotate(-6f + sin(seconds * 0.11f) * 2f)
    canvas.drawText(initial, -paint.measureText(initial) / 2f, 0f, paint)
    canvas.restore()
    paint.textSize = original; paint.style = Paint.Style.FILL
    // Short marker rules and a numbered slug along the left rail, like print marginalia.
    paint.textSize = original * 0.24f; paint.color = Color.White.copy(alpha = 0.5f).toArgb()
    val slug = "NO.%02d".format(Math.floorMod(seed, 42) + 1)
    canvas.save(); canvas.translate(fx + h * 0.012f, fy + fh * 0.6f); canvas.rotate(-90f)
    canvas.drawText(slug, 0f, 0f, paint); canvas.restore()
    repeat(5) { i ->
        val y = fy + fh * (0.12f + i * 0.19f)
        drawLine(rule.copy(alpha = 0.24f), Offset(fx, y), Offset(fx + h * (0.018f + random.nextFloat() * 0.03f), y), 1f)
        drawLine(rule.copy(alpha = 0.24f), Offset(fx + fw, y), Offset(fx + fw - h * (0.018f + random.nextFloat() * 0.03f), y), 1f)
    }
    paint.textSize = original
    // Horizontal tracks above and below the focus zone.
    drawLine(rule.copy(alpha = 0.20f), Offset(fx, h * 0.335f), Offset(fx + fw, h * 0.335f), 0.7f)
    drawLine(rule.copy(alpha = 0.20f), Offset(fx, h * 0.685f), Offset(fx + fw, h * 0.685f), 0.7f)
    // Film grain: jittered dots reseeded per frame slice.
    particles.take(160).forEachIndexed { i, p ->
        var mixed = (i * 31 + (seconds * 24).toInt()) * -0x61c88647
        mixed = (mixed xor (mixed ushr 16)) * 0x45d9f3b
        mixed = mixed xor (mixed ushr 16)
        val gx = (mixed and 0xffff).toFloat() / 65535f * w
        val gy = ((mixed ushr 16) and 0xffff).toFloat() / 65535f * h
        drawCircle(Color.White.copy(alpha = 0.05f * p.third), 0.6f, Offset(gx, gy))
    }
    // Halftone patch in the lower-left quadrant.
    for (row in 0 until 9) for (column in 0 until 14) {
        val dot = posterHalftonePoint(row + seed, column)
        drawCircle(Color.White.copy(alpha = 0.05f + dot * 0.06f), 0.5f + dot * 0.9f,
            Offset(fx + w * 0.02f + column * w * 0.014f, fy + fh * 0.72f + row * w * 0.014f))
    }
    // Vignette.
    drawRect(Brush.radialGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.42f)),
        Offset(w / 2f, h * 0.5f), h * 0.78f))
}
