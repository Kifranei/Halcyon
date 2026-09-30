package com.ella.music.ui.components

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * NetEase Cloud Music style lyric card: solid cover-derived background, cover + title/artist
 * header, dashed divider, very large left-aligned lyric paragraphs, a small brand mark at the
 * bottom-left and (when the song has a NetEase id) a QR code linking to the song at the
 * bottom-right. All metrics are fractions of the canvas width so the card renders at the same
 * 1080px output width as the other styles.
 */

private const val NETEASE_PADDING = 0.07f
private const val NETEASE_COVER = 0.125f
private const val NETEASE_COVER_RADIUS_RATIO = 0.08f
private const val NETEASE_META_GAP = 0.035f
private const val NETEASE_TITLE_SIZE = 0.036f
private const val NETEASE_ARTIST_SIZE = 0.030f
private const val NETEASE_TITLE_ARTIST_GAP = 0.012f
private const val NETEASE_DIVIDER_GAP = 0.055f
private const val NETEASE_LYRICS_GAP = 0.07f
private const val NETEASE_LYRIC_SIZE = 0.078f
private const val NETEASE_LYRIC_LINE_SPACING = 1.15f
private const val NETEASE_PARAGRAPH_GAP = 0.05f
private const val NETEASE_SECONDARY_RATIO = 0.5f
private const val NETEASE_SECONDARY_GAP = 0.014f
private const val NETEASE_LYRICS_BOTTOM_GAP = 0.08f
private const val NETEASE_QR = 0.14f
private const val NETEASE_BRAND_MARK_RADIUS = 0.022f
private const val NETEASE_BRAND_TEXT = 0.030f
private const val NETEASE_BRAND_GAP = 0.018f
private const val NETEASE_MIN_ASPECT = 4f / 3f
private const val NETEASE_ARTIST_ALPHA = 0.55f
private const val NETEASE_DIVIDER_ALPHA = 0.25f
private const val NETEASE_SECONDARY_ALPHA = 0.60f
private const val NETEASE_BRAND_ALPHA = 0.55f
private const val NETEASE_SONG_URL = "https://y.music.163.com/m/song?id="

/** Pre-measured geometry for [LyricShareCardStyle.NetEase]; colours are applied at draw time. */
internal class NeteaseShareCardLayout(
    val padding: Float,
    val coverRect: RectF,
    val coverRadius: Float,
    val titleLayout: StaticLayout,
    val artistLayout: StaticLayout,
    val metaLeft: Float,
    val titleTop: Float,
    val artistTop: Float,
    val dividerY: Float,
    val dividerStroke: Float,
    val lyricsTop: Float,
    val lyricBlocks: List<MeasuredShareLyricBlock>,
    val primaryPaint: TextPaint,
    val secondaryPaint: TextPaint,
    val brandPaint: TextPaint,
    val brandText: String,
    val brandMarkCenterX: Float,
    val brandMarkCenterY: Float,
    val brandMarkRadius: Float,
    val brandTextX: Float,
    val brandBaseline: Float,
    val qrModules: Array<BooleanArray>?,
    val qrRect: RectF?
)

internal data class NeteaseShareColors(
    val background: Int,
    val accent: Int,
    val qrDark: Int
)

internal fun neteaseShareSongUrl(songId: String): String? =
    songId.trim().takeIf { id -> id.isNotEmpty() && id.all(Char::isDigit) && id.trimStart('0').isNotEmpty() }
        ?.let { NETEASE_SONG_URL + it }

internal fun calculateNeteaseLyricShareLayout(
    content: LyricShareCardContent,
    canvasWidth: Int,
    maxHeight: Int,
    shareTypeface: Typeface?
): LyricShareCardLayout {
    val w = canvasWidth.toFloat()
    val padding = NETEASE_PADDING * w
    val coverSize = NETEASE_COVER * w
    val coverRadius = coverSize * NETEASE_COVER_RADIUS_RATIO
    val metaLeft = padding + coverSize + NETEASE_META_GAP * w
    val metaWidth = (w - padding - metaLeft).roundToInt().coerceAtLeast(1)
    val contentWidth = (w - padding * 2).roundToInt().coerceAtLeast(1)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = NETEASE_TITLE_SIZE * w
        typeface = shareTypeface.neteaseWeight(600)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = NETEASE_ARTIST_SIZE * w
        typeface = shareTypeface.neteaseWeight(400)
    }
    val titleLayout = neteaseTextLayout(content.title, titlePaint, metaWidth, maxLines = 2, spacingMult = 1.05f)
    val artistLayout = neteaseTextLayout(content.artist, artistPaint, metaWidth, maxLines = 1, spacingMult = 1f)
    val titleArtistGap = NETEASE_TITLE_ARTIST_GAP * w
    val metaHeight = titleLayout.height + titleArtistGap + artistLayout.height
    val headerHeight = maxOf(coverSize, metaHeight)
    val coverRect = RectF(
        padding,
        padding + (headerHeight - coverSize) / 2f,
        padding + coverSize,
        padding + (headerHeight + coverSize) / 2f
    )
    val titleTop = padding + (headerHeight - metaHeight) / 2f
    val artistTop = titleTop + titleLayout.height + titleArtistGap
    val dividerY = padding + headerHeight + NETEASE_DIVIDER_GAP * w
    val lyricsTop = dividerY + NETEASE_LYRICS_GAP * w

    val qrModules = neteaseShareSongUrl(content.neteaseSongId)?.let(::encodeShareQrCode)
    val qrSize = NETEASE_QR * w
    val brandMarkRadius = NETEASE_BRAND_MARK_RADIUS * w
    val footerHeight = if (qrModules != null) qrSize else brandMarkRadius * 2f
    val lyricsBottomGap = NETEASE_LYRICS_BOTTOM_GAP * w
    val availableLyrics = (maxHeight - lyricsTop - lyricsBottomGap - footerHeight - padding)
        .coerceAtLeast(NETEASE_LYRIC_SIZE * w)

    val primaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = shareTypeface.neteaseWeight(700)
    }
    val secondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = shareTypeface.neteaseWeight(400)
    }
    val baseSize = NETEASE_LYRIC_SIZE * w
    val scales = floatArrayOf(1f, 0.9f, 0.8f, 0.7f, 0.6f)
    var measured: Pair<List<MeasuredShareLyricBlock>, Float>? = null
    for (index in scales.indices) {
        val isLast = index == scales.lastIndex
        measured = measureNeteaseBlocks(
            blocks = content.blocks,
            width = contentWidth,
            canvasWidth = w,
            scale = scales[index],
            baseSize = baseSize,
            primaryPaint = primaryPaint,
            secondaryPaint = secondaryPaint,
            availableHeight = availableLyrics,
            forceTruncate = isLast,
            appendEllipsis = content.appendEllipsis
        )
        if (measured != null) break
    }
    val (lyricBlocks, lyricsHeight) = measured ?: (emptyList<MeasuredShareLyricBlock>() to 0f)

    val minHeight = (w * NETEASE_MIN_ASPECT).roundToInt()
    val canvasHeight = (lyricsTop + lyricsHeight + lyricsBottomGap + footerHeight + padding)
        .roundToInt()
        .coerceAtLeast(minHeight)
        .coerceAtMost(maxHeight.coerceAtLeast(minHeight))
    val footerBottom = canvasHeight - padding
    val qrRect = qrModules?.let {
        RectF(w - padding - qrSize, footerBottom - qrSize, w - padding, footerBottom)
    }

    val brandPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = NETEASE_BRAND_TEXT * w
        typeface = shareTypeface.neteaseWeight(600)
    }
    val brandMarkCenterX = padding + brandMarkRadius
    val brandMarkCenterY = footerBottom - brandMarkRadius
    val brandTextX = brandMarkCenterX + brandMarkRadius + NETEASE_BRAND_GAP * w
    val brandBaseline = brandMarkCenterY - (brandPaint.descent() + brandPaint.ascent()) / 2f
    val maxBrandWidth = ((qrRect?.left ?: (w - padding)) - brandTextX - NETEASE_BRAND_GAP * w).coerceAtLeast(1f)
    val brandText = TextUtils.ellipsize(content.brandText, brandPaint, maxBrandWidth, TextUtils.TruncateAt.END)
        .toString()

    val netease = NeteaseShareCardLayout(
        padding = padding,
        coverRect = coverRect,
        coverRadius = coverRadius,
        titleLayout = titleLayout,
        artistLayout = artistLayout,
        metaLeft = metaLeft,
        titleTop = titleTop,
        artistTop = artistTop,
        dividerY = dividerY,
        dividerStroke = (0.0025f * w).coerceAtLeast(1f),
        lyricsTop = lyricsTop,
        lyricBlocks = lyricBlocks,
        primaryPaint = primaryPaint,
        secondaryPaint = secondaryPaint,
        brandPaint = brandPaint,
        brandText = brandText,
        brandMarkCenterX = brandMarkCenterX,
        brandMarkCenterY = brandMarkCenterY,
        brandMarkRadius = brandMarkRadius,
        brandTextX = brandTextX,
        brandBaseline = brandBaseline,
        qrModules = qrModules,
        qrRect = qrRect
    )
    return LyricShareCardLayout(
        canvasWidth = canvasWidth,
        adaptiveCanvasHeight = canvasHeight,
        cardRadius = 0f,
        coverRadius = coverRadius,
        safePadding = padding,
        lyricsTop = lyricsTop,
        chinTop = footerBottom - footerHeight,
        chinHeight = footerHeight,
        coverRect = coverRect,
        titleLayout = titleLayout,
        artistLayout = artistLayout,
        titleTop = titleTop,
        artistTop = artistTop,
        lyricBlocks = lyricBlocks,
        footerPaint = brandPaint,
        footerText = brandText,
        footerBaseline = brandBaseline,
        style = LyricShareCardStyle.NetEase,
        netease = netease
    )
}

private fun measureNeteaseBlocks(
    blocks: List<ShareLyricBlock>,
    width: Int,
    canvasWidth: Float,
    scale: Float,
    baseSize: Float,
    primaryPaint: TextPaint,
    secondaryPaint: TextPaint,
    availableHeight: Float,
    forceTruncate: Boolean,
    appendEllipsis: Boolean
): Pair<List<MeasuredShareLyricBlock>, Float>? {
    primaryPaint.textSize = baseSize * scale
    secondaryPaint.textSize = baseSize * scale * NETEASE_SECONDARY_RATIO
    val paragraphGap = NETEASE_PARAGRAPH_GAP * canvasWidth * scale
    val secondaryGap = NETEASE_SECONDARY_GAP * canvasWidth * scale

    fun measure(block: ShareLyricBlock): MeasuredShareLyricBlock {
        val secondary = block.secondary.map {
            MeasuredTextBlock(
                layout = neteaseTextLayout(it, secondaryPaint, width, maxLines = Int.MAX_VALUE, spacingMult = 1.1f),
                gapAfter = secondaryGap
            )
        }
        val primary = neteaseTextLayout(
            block.primary,
            primaryPaint,
            width,
            maxLines = Int.MAX_VALUE,
            spacingMult = NETEASE_LYRIC_LINE_SPACING
        )
        return MeasuredShareLyricBlock(
            primary = MeasuredTextBlock(primary, if (secondary.isEmpty()) 0f else secondaryGap),
            // The last secondary line should not add trailing space before the paragraph gap.
            secondary = secondary.mapIndexed { i, s -> if (i == secondary.lastIndex) s.copy(gapAfter = 0f) else s },
            gapAfter = paragraphGap
        )
    }

    fun height(block: MeasuredShareLyricBlock, isLast: Boolean): Float =
        block.primary.layout.height + block.primary.gapAfter +
            block.secondary.fold(0f) { acc, s -> acc + s.layout.height + s.gapAfter } +
            if (isLast) 0f else block.gapAfter

    val measured = blocks.map(::measure)
    val total = measured.foldIndexed(0f) { i, acc, b -> acc + height(b, i == measured.lastIndex) }
    if (total <= availableHeight) return finalizeNeteaseBlocks(measured) to total
    if (!forceTruncate) return null

    val kept = mutableListOf<MeasuredShareLyricBlock>()
    var used = 0f
    for (block in measured) {
        val candidate = used + (if (kept.isEmpty()) 0f else kept.last().gapAfter) + height(block, isLast = true)
        if (candidate > availableHeight && kept.isNotEmpty()) break
        kept += block
        used = candidate
    }
    if (appendEllipsis && kept.size < measured.size) {
        val ellipsis = measure(ShareLyricBlock("...", emptyList()))
        val ellipsisHeight = height(ellipsis, isLast = true)
        while (kept.size > 1 && used + kept.last().gapAfter + ellipsisHeight > availableHeight) {
            val removed = kept.removeAt(kept.lastIndex)
            used -= height(removed, isLast = true) + kept.last().gapAfter
        }
        if (used + kept.last().gapAfter + ellipsisHeight <= availableHeight) {
            used += kept.last().gapAfter + ellipsisHeight
            kept += ellipsis
        }
    }
    return finalizeNeteaseBlocks(kept) to used.coerceAtMost(availableHeight)
}

private fun finalizeNeteaseBlocks(blocks: List<MeasuredShareLyricBlock>): List<MeasuredShareLyricBlock> =
    blocks.mapIndexed { i, block -> if (i == blocks.lastIndex) block.copy(gapAfter = 0f) else block }

private fun neteaseTextLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    maxLines: Int,
    spacingMult: Float
): StaticLayout = StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
    .setIncludePad(false)
    .setLineSpacing(0f, spacingMult)
    .setMaxLines(maxLines)
    .setEllipsize(TextUtils.TruncateAt.END)
    .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
    .build()

private fun Typeface?.neteaseWeight(weight: Int): Typeface =
    Typeface.create(this ?: Typeface.DEFAULT, weight, false)

internal fun renderNeteaseLyricShareCard(
    content: LyricShareCardContent,
    layout: LyricShareCardLayout,
    netease: NeteaseShareCardLayout,
    cover: Bitmap?
): Bitmap {
    val bitmap = Bitmap.createBitmap(layout.canvasWidth, layout.adaptiveCanvasHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val colors = neteaseShareColors(cover, content.backgroundColors)
    val accent = colors.accent
    canvas.drawColor(colors.background)

    // Header.
    drawNeteaseCover(canvas, cover, netease.coverRect, netease.coverRadius, accent)
    netease.titleLayout.paint.color = accent
    drawNeteaseText(canvas, netease.titleLayout, netease.metaLeft, netease.titleTop)
    netease.artistLayout.paint.color = accent.withShareAlpha(NETEASE_ARTIST_ALPHA)
    drawNeteaseText(canvas, netease.artistLayout, netease.metaLeft, netease.artistTop)

    // Dashed divider.
    val stroke = netease.dividerStroke
    val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = accent.withShareAlpha(NETEASE_DIVIDER_ALPHA)
        pathEffect = DashPathEffect(floatArrayOf(stroke * 5f, stroke * 3.5f), 0f)
    }
    canvas.drawLine(
        netease.padding,
        netease.dividerY,
        layout.canvasWidth - netease.padding,
        netease.dividerY,
        dividerPaint
    )

    // Lyrics.
    netease.primaryPaint.color = accent
    netease.secondaryPaint.color = accent.withShareAlpha(NETEASE_SECONDARY_ALPHA)
    var y = netease.lyricsTop
    netease.lyricBlocks.forEach { block ->
        drawNeteaseText(canvas, block.primary.layout, netease.padding, y)
        y += block.primary.layout.height + block.primary.gapAfter
        block.secondary.forEach { secondary ->
            drawNeteaseText(canvas, secondary.layout, netease.padding, y)
            y += secondary.layout.height + secondary.gapAfter
        }
        y += block.gapAfter
    }

    // Brand mark: a generic disc with a note, not the NetEase trademark.
    val brandColor = accent.withShareAlpha(NETEASE_BRAND_ALPHA)
    if (netease.brandText.isNotEmpty()) {
        drawNeteaseBrandMark(
            canvas,
            netease.brandMarkCenterX,
            netease.brandMarkCenterY,
            netease.brandMarkRadius,
            brandColor,
            colors.background
        )
        netease.brandPaint.color = brandColor
        canvas.drawText(netease.brandText, netease.brandTextX, netease.brandBaseline, netease.brandPaint)
    }

    // QR code: dark modules on an accent tile so scanners get a normal (dark-on-light) code.
    val modules = netease.qrModules
    val qrRect = netease.qrRect
    if (modules != null && qrRect != null) {
        drawNeteaseQr(canvas, modules, qrRect, accent, colors.qrDark)
    }
    return bitmap
}

private fun drawNeteaseText(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
    val save = canvas.save()
    canvas.translate(x, y)
    layout.draw(canvas)
    canvas.restoreToCount(save)
}

private fun drawNeteaseCover(canvas: Canvas, cover: Bitmap?, rect: RectF, radius: Float, accent: Int) {
    val usable = cover?.takeUnless { it.isRecycled }
    if (usable == null) {
        canvas.drawRoundRect(rect, radius, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.withShareAlpha(0.16f)
        })
        val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accent.withShareAlpha(0.8f)
            textSize = rect.height() * 0.42f
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        val baseline = rect.centerY() - (notePaint.descent() + notePaint.ascent()) / 2f
        canvas.drawText("♪", rect.centerX(), baseline, notePaint)
        return
    }
    val save = canvas.save()
    canvas.clipPath(Path().apply { addRoundRect(rect, radius, radius, Path.Direction.CW) })
    // Center-crop the cover into the square thumbnail.
    val srcSize = minOf(usable.width, usable.height)
    val left = (usable.width - srcSize) / 2
    val top = (usable.height - srcSize) / 2
    canvas.drawBitmap(
        usable,
        android.graphics.Rect(left, top, left + srcSize, top + srcSize),
        rect,
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            isFilterBitmap = true
            isDither = true
        }
    )
    canvas.restoreToCount(save)
}

private fun drawNeteaseBrandMark(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    radius: Float,
    color: Int,
    background: Int
) {
    canvas.drawCircle(cx, cy, radius, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
    val notePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = background
        textSize = radius * 1.25f
        textAlign = Paint.Align.CENTER
        typeface = Typeface.DEFAULT_BOLD
    }
    val baseline = cy - (notePaint.descent() + notePaint.ascent()) / 2f
    canvas.drawText("♪", cx, baseline, notePaint)
}

private fun drawNeteaseQr(canvas: Canvas, modules: Array<BooleanArray>, rect: RectF, light: Int, dark: Int) {
    val count = modules.size
    val quiet = 2
    val tileRadius = rect.width() * 0.06f
    canvas.drawRoundRect(rect, tileRadius, tileRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = light })
    val module = rect.width() / (count + quiet * 2)
    val originX = rect.left + module * quiet
    val originY = rect.top + module * quiet
    val paint = Paint().apply {
        color = dark
        isAntiAlias = false
    }
    for (y in 0 until count) {
        val row = modules[y]
        val top = originY + y * module
        for (x in 0 until count) {
            if (row[x]) {
                val left = originX + x * module
                // Snap to whole pixels so neighbouring modules never leave hairline seams.
                canvas.drawRect(
                    left.roundToInt().toFloat(),
                    top.roundToInt().toFloat(),
                    (left + module).roundToInt().toFloat(),
                    (top + module).roundToInt().toFloat(),
                    paint
                )
            }
        }
    }
}

private fun Int.withShareAlpha(alpha: Float): Int =
    Color.argb((alpha.coerceIn(0f, 1f) * 255f).roundToInt(), Color.red(this), Color.green(this), Color.blue(this))

/**
 * Derives the card palette from the cover: the dominant chromatic hue (saturation-weighted hue
 * histogram) becomes a muted, darkened background and a light pastel accent of the same hue.
 * The background is darkened further until the accent reaches a 4.5:1 contrast ratio, so light
 * and dark covers both stay legible. Falls back to the player palette when no cover is present.
 */
internal fun neteaseShareColors(cover: Bitmap?, fallback: List<Int>): NeteaseShareColors {
    val seed = cover?.takeUnless { it.isRecycled }?.let { runCatching { dominantCoverHsv(it) }.getOrNull() }
        ?: fallback.firstOrNull { Color.alpha(it) > 0 }?.let { argb ->
            FloatArray(3).also { Color.colorToHSV(argb, it) }
        }
        ?: floatArrayOf(322f, 0.5f, 0.5f)
    val hue = seed[0]
    val saturation = seed[1]
    val backgroundSaturation = (saturation * 0.55f).coerceIn(0f, 0.30f)
    val accentSaturation = if (saturation < 0.08f) 0.06f else (backgroundSaturation + 0.03f).coerceIn(0.12f, 0.34f)
    val accent = Color.HSVToColor(floatArrayOf(hue, accentSaturation, 1f))
    var value = 0.44f
    var background = Color.HSVToColor(floatArrayOf(hue, backgroundSaturation, value))
    while (shareContrastRatio(accent, background) < 4.5f && value > 0.16f) {
        value -= 0.03f
        background = Color.HSVToColor(floatArrayOf(hue, backgroundSaturation, value))
    }
    val qrDark = Color.HSVToColor(floatArrayOf(hue, (backgroundSaturation + 0.1f).coerceAtMost(0.45f), value * 0.55f))
    return NeteaseShareColors(background = background, accent = accent, qrDark = qrDark)
}

private fun dominantCoverHsv(cover: Bitmap): FloatArray {
    val source = if (cover.config == Bitmap.Config.HARDWARE) cover.copy(Bitmap.Config.ARGB_8888, false) else cover
    val sample = Bitmap.createScaledBitmap(source, 24, 24, true)
    val pixels = IntArray(sample.width * sample.height)
    sample.getPixels(pixels, 0, sample.width, 0, 0, sample.width, sample.height)
    if (sample !== source) sample.recycle()
    if (source !== cover) source.recycle()

    val bins = 36
    val weight = FloatArray(bins)
    val sinSum = FloatArray(bins)
    val cosSum = FloatArray(bins)
    val satSum = FloatArray(bins)
    val hsv = FloatArray(3)
    var valueSum = 0f
    var counted = 0
    for (pixel in pixels) {
        if (Color.alpha(pixel) < 128) continue
        Color.colorToHSV(pixel, hsv)
        valueSum += hsv[2]
        counted++
        if (hsv[1] < 0.15f || hsv[2] < 0.15f) continue
        val w = hsv[1] * (0.35f + hsv[2])
        val bin = ((hsv[0] / 360f) * bins).toInt().coerceIn(0, bins - 1)
        val radians = Math.toRadians(hsv[0].toDouble())
        weight[bin] += w
        sinSum[bin] += (sin(radians) * w).toFloat()
        cosSum[bin] += (cos(radians) * w).toFloat()
        satSum[bin] += hsv[1] * w
    }
    val averageValue = if (counted > 0) valueSum / counted else 0.5f
    val totalWeight = weight.sum()
    if (counted == 0 || totalWeight < counted * 0.05f) {
        // Essentially greyscale artwork: keep it neutral.
        return floatArrayOf(0f, 0f, averageValue)
    }
    val best = weight.indices.maxByOrNull { i ->
        weight[i] + 0.5f * (weight[(i + bins - 1) % bins] + weight[(i + 1) % bins])
    } ?: 0
    var w = 0f
    var s = 0f
    var c = 0f
    var sat = 0f
    for (offset in -1..1) {
        val i = (best + offset + bins) % bins
        w += weight[i]
        s += sinSum[i]
        c += cosSum[i]
        sat += satSum[i]
    }
    var hue = Math.toDegrees(atan2(s.toDouble(), c.toDouble())).toFloat()
    if (hue < 0f) hue += 360f
    return floatArrayOf(hue, if (w > 0f) sat / w else 0f, averageValue)
}

private fun shareRelativeLuminance(color: Int): Double {
    fun channel(v: Int): Double {
        val c = v / 255.0
        return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
    }
    return 0.2126 * channel(Color.red(color)) + 0.7152 * channel(Color.green(color)) + 0.0722 * channel(Color.blue(color))
}

private fun shareContrastRatio(a: Int, b: Int): Float {
    val la = shareRelativeLuminance(a)
    val lb = shareRelativeLuminance(b)
    return ((maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)).toFloat()
}
