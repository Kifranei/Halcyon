package com.ella.music.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.primaryEndMs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal class LyricVideoRenderer(
    private val cover: Bitmap?,
    private val lines: List<LyricLine>,
    private val includeTranslation: Boolean,
    typeface: Typeface? = null
) {
    companion object {
        const val VIDEO_SIZE = 1080
        const val FPS = 30
        private const val FADE_IN_MS = 200L
        private const val HOLD_AFTER_MS = 300L
        const val DISSOLVE_MS = 600L
        private const val FEATHER_WIDTH = 80f
        private const val MAIN_TEXT_SIZE = 72f
        private const val TRANS_TEXT_SIZE = 36f
        private const val TRANS_GAP = 24f
        private const val DIM_ALPHA = 120
        private const val COVER_TEXTURE_SIZE = 720
    }

    private data class BackgroundAssets(
        val texture: Bitmap?,
        val gradientColors: IntArray,
        val blobColors: IntArray
    )

    private val backgroundAssets: BackgroundAssets = prepareBackgroundAssets()
    private val textureShader = backgroundAssets.texture?.let {
        BitmapShader(it, Shader.TileMode.MIRROR, Shader.TileMode.MIRROR)
    }
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val texturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { isFilterBitmap = true }
    private val blobPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textureMatrix = Matrix()
    private val mainPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = MAIN_TEXT_SIZE
        this.typeface = typeface?.let { Typeface.create(it, Typeface.BOLD) }
            ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(12f, 0f, 4f, Color.argb(120, 0, 0, 0))
    }
    private val dimPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(DIM_ALPHA, 255, 255, 255)
        textSize = MAIN_TEXT_SIZE
        this.typeface = typeface?.let { Typeface.create(it, Typeface.BOLD) }
            ?: Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(12f, 0f, 4f, Color.argb(80, 0, 0, 0))
    }
    private val transPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        textSize = TRANS_TEXT_SIZE
        this.typeface = typeface ?: Typeface.DEFAULT
        setShadowLayer(8f, 0f, 3f, Color.argb(80, 0, 0, 0))
    }
    private val dimTransPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(120, 255, 255, 255)
        textSize = TRANS_TEXT_SIZE
        this.typeface = typeface ?: Typeface.DEFAULT
        setShadowLayer(8f, 0f, 3f, Color.argb(48, 0, 0, 0))
    }
    private val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private data class VideoWordInfo(
        val word: LyricWord,
        val text: String,
        val x: Float,
        val y: Float,
        val w: Float,
        val visualLine: Int
    )

    private data class LineTimeline(
        val line: LyricLine,
        val startMs: Long,
        val karaokeEndMs: Long,
        val holdEndMs: Long,
        val dissolveEndMs: Long,
        val translation: String?
    )

    private val timelines: List<LineTimeline> = buildTimelines()

    val totalDurationMs: Long
        get() = if (timelines.isEmpty()) 0L else timelines.last().dissolveEndMs

    private fun buildTimelines(): List<LineTimeline> {
        if (lines.isEmpty()) return emptyList()
        val result = mutableListOf<LineTimeline>()
        val globalStartMs = lines.first().timeMs
        for (i in lines.indices) {
            val line = lines[i]
            val nextLine = lines.getOrNull(i + 1)
            val relativeStart = line.timeMs - globalStartMs
            val lineEndMs = line.primaryEndMs(nextLine = nextLine)
            val karaokeEnd = lineEndMs - globalStartMs
            val holdEnd = karaokeEnd + HOLD_AFTER_MS
            val dissolveEnd = holdEnd + DISSOLVE_MS
            val translation = if (includeTranslation) line.translation?.takeIf { it.isNotBlank() } else null
            result.add(LineTimeline(line, relativeStart, karaokeEnd, holdEnd, dissolveEnd, translation))
        }
        return result
    }

    fun totalFrames(): Int = ((totalDurationMs * FPS) / 1000).toInt().coerceAtLeast(1)

    private var activeParticleEffect: LyricVideoParticleEffect? = null
    private var particleLineIndex = -1
    private var textBitmapCache: Bitmap? = null

    fun drawFrame(canvas: Canvas, frameIndex: Int) {
        val timeMs = (frameIndex * 1000L) / FPS
        drawDynamicBackground(canvas, timeMs)

        val activeIndex = timelines.indexOfLast { timeMs >= it.startMs }.takeIf { it >= 0 } ?: return
        val activeTimeline = timelines[activeIndex]
        val nextTimeline = timelines.getOrNull(activeIndex + 1)
        if (isLyricVideoInterLineGap(
                timeMs = timeMs,
                karaokeEndMs = activeTimeline.karaokeEndMs,
                nextLineStartMs = nextTimeline?.startMs
            )
        ) {
            // The previous line remains the timeline anchor through a short gap, but its karaoke
            // sweep has finished. Keep it dim until the next line starts instead of briefly
            // presenting the whole sentence as newly highlighted.
            clearParticleEffect()
            drawLyricLine(
                canvas = canvas,
                timeline = activeTimeline,
                timeMs = timeMs,
                alpha = 1f,
                scale = 1f,
                completed = true
            )
            return
        }
        val previousTimeline = timelines.getOrNull(activeIndex - 1)
        val dissolvingTimeline = when {
            previousTimeline != null &&
                timeMs >= previousTimeline.holdEndMs &&
                timeMs < previousTimeline.dissolveEndMs -> previousTimeline to (activeIndex - 1)
            timeMs >= activeTimeline.holdEndMs &&
                timeMs < activeTimeline.dissolveEndMs -> activeTimeline to activeIndex
            else -> null
        }

        dissolvingTimeline?.let { (timeline, lineIndex) ->
            drawDissolvingLine(canvas, timeline, lineIndex)
        } ?: clearParticleEffect()

        val shouldDrawActiveLine =
            dissolvingTimeline == null || dissolvingTimeline.second != activeIndex || timeMs < activeTimeline.holdEndMs
        if (!shouldDrawActiveLine) return

        when {
            timeMs < activeTimeline.startMs + FADE_IN_MS -> {
                val progress = ((timeMs - activeTimeline.startMs).toFloat() / FADE_IN_MS).coerceIn(0f, 1f)
                val easedProgress = easeOutCubic(progress)
                drawLyricLine(
                    canvas = canvas,
                    timeline = activeTimeline,
                    timeMs = timeMs,
                    alpha = 0.58f + 0.42f * easedProgress,
                    scale = 0.92f + 0.08f * easedProgress,
                    translateY = (1f - easedProgress) * 32f
                )
            }
            timeMs < activeTimeline.karaokeEndMs || timeMs < activeTimeline.holdEndMs -> {
                drawLyricLine(canvas, activeTimeline, timeMs, alpha = 1f, scale = 1f)
            }
            else -> {
                drawLyricLine(canvas, activeTimeline, timeMs, alpha = 1f, scale = 1f)
            }
        }
    }

    private fun drawDissolvingLine(
        canvas: Canvas,
        timeline: LineTimeline,
        lineIndex: Int
    ) {
        if (particleLineIndex != lineIndex) {
            particleLineIndex = lineIndex
            val textBmp = renderLineToBitmap(timeline)
            textBitmapCache?.recycle()
            textBitmapCache = textBmp
            val drawY = calculateLineY(timeline)
            activeParticleEffect = LyricVideoParticleEffect(
                textBitmap = textBmp,
                destX = calculateLineX(timeline, textBmp.width),
                destY = drawY,
                totalFrames = LyricVideoParticleEffect.DISSOLVE_FRAMES
            )
        }
        activeParticleEffect?.let {
            it.advanceFrame()
            it.draw(canvas)
        }
    }

    private fun clearParticleEffect() {
        activeParticleEffect = null
        particleLineIndex = -1
    }

    private fun drawLyricLine(
        canvas: Canvas,
        timeline: LineTimeline,
        timeMs: Long,
        alpha: Float,
        scale: Float,
        translateY: Float = 0f,
        completed: Boolean = false
    ) {
        val line = timeline.line
        val text = line.text.ifBlank { line.backgroundText.orEmpty() }
        if (text.isBlank()) return

        val textWidth = (VIDEO_SIZE * 0.84f).toInt()
        val mainLayoutPaint = if (completed) dimPaint else mainPaint
        val translationLayoutPaint = if (completed) dimTransPaint else transPaint
        val mainLayout = buildVideoLayout(text, mainLayoutPaint, textWidth)
        val transLayout = timeline.translation?.let { buildVideoLayout(it, translationLayoutPaint, textWidth) }

        val totalHeight = mainLayout.height + (transLayout?.let { it.height + TRANS_GAP } ?: 0f)
        val startY = (VIDEO_SIZE - totalHeight) / 2f
        val startX = (VIDEO_SIZE - textWidth) / 2f

        canvas.save()
        if (translateY != 0f) {
            canvas.translate(0f, translateY)
        }
        if (scale != 1f) {
            canvas.scale(scale, scale, VIDEO_SIZE / 2f, VIDEO_SIZE / 2f)
        }

        val saveAlpha = canvas.saveLayerAlpha(0f, 0f, VIDEO_SIZE.toFloat(), VIDEO_SIZE.toFloat(), (alpha * 255).toInt())

        if (line.words.isNotEmpty()) {
            drawKaraokeText(
                canvas = canvas,
                line = line,
                timeline = timeline,
                timeMs = timeMs,
                startX = startX,
                startY = startY,
                availableWidth = textWidth.toFloat(),
                allowProgress = !completed
            )
        } else {
            drawStaticText(canvas, mainLayout, startX, startY)
        }

        transLayout?.let {
            val transY = startY + mainLayout.height + TRANS_GAP
            drawStaticLayout(canvas, it, transPaint, startX, transY)
        }

        canvas.restoreToCount(saveAlpha)
        canvas.restore()
    }

    private fun drawKaraokeText(
        canvas: Canvas,
        line: LyricLine,
        timeline: LineTimeline,
        timeMs: Long,
        startX: Float,
        startY: Float,
        availableWidth: Float,
        allowProgress: Boolean
    ) {
        val globalStartMs = lines.first().timeMs
        val absoluteTimeMs = timeMs + globalStartMs
        val words = line.words
        val wordInfos = layoutWords(words, mainPaint, startX, startY + (-mainPaint.fontMetrics.ascent), availableWidth)
        if (wordInfos.isEmpty()) return

        for (info in wordInfos) {
            canvas.drawText(info.text, info.x, info.y, dimPaint)
        }

        if (!allowProgress) return

        val sweepFraction = calculateSweepFraction(words, absoluteTimeMs, mainPaint)
        if (sweepFraction <= 0f) return

        val lineGroups = wordInfos.groupBy { it.visualLine }.toSortedMap()
        val lineWidths = lineGroups.mapValues { (_, infos) -> infos.sumOf { it.w.toDouble() }.toFloat() }
        val totalW = lineWidths.values.sumOf { it.toDouble() }.toFloat()
        if (totalW <= 0f) return

        val sungWidth = sweepFraction.coerceIn(0f, 1f) * totalW
        val fmTop = mainPaint.fontMetrics.top
        val fmBottom = mainPaint.fontMetrics.bottom
        var lineCumBefore = 0f

        for ((_, lineWords) in lineGroups) {
            if (lineWords.isEmpty()) continue
            val lineStartX = lineWords.first().x
            val lineEndX = lineWords.last().x + lineWords.last().w
            val lineW = lineEndX - lineStartX
            val lineY = lineWords.first().y
            val mTop = lineY + fmTop - 4f
            val mBottom = lineY + fmBottom + 4f
            val lineCumAfter = lineCumBefore + lineW
            if (sungWidth <= lineCumBefore) break

            val effectiveSungInLine = (sungWidth - lineCumBefore).coerceIn(0f, lineW)
            val sweepX = lineStartX + effectiveSungInLine
            val effectiveFeatherPx = min(FEATHER_WIDTH, lineEndX - sweepX)
            val featherStart = (sweepX - effectiveFeatherPx).coerceAtLeast(lineStartX)

            val saveCount = canvas.saveLayer(lineStartX, mTop, lineEndX, mBottom, null)
            for (info in lineWords) {
                canvas.drawText(info.text, info.x, info.y, mainPaint)
            }
            val maskColors = intArrayOf(
                Color.argb(255, 0, 0, 0),
                Color.argb(255, 0, 0, 0),
                Color.argb(0, 0, 0, 0),
                Color.argb(0, 0, 0, 0)
            )
            val maskPositions = floatArrayOf(
                0f,
                ((featherStart - lineStartX) / lineW).coerceIn(0f, 1f),
                ((sweepX - lineStartX) / lineW).coerceIn(0f, 1f),
                1f
            )
            maskPaint.shader = LinearGradient(
                lineStartX, 0f, lineEndX, 0f,
                maskColors, maskPositions, Shader.TileMode.CLAMP
            )
            maskPaint.xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
            canvas.drawRect(lineStartX, mTop, lineEndX, mBottom, maskPaint)
            maskPaint.xfermode = null
            maskPaint.shader = null
            canvas.restoreToCount(saveCount)
            lineCumBefore = lineCumAfter
        }
    }

    private fun calculateSweepFraction(
        words: List<LyricWord>,
        positionMs: Long,
        paint: TextPaint
    ): Float {
        if (words.isEmpty()) return 0f
        var completedWidth = 0f
        var totalWidth = 0f
        for (word in words) {
            val wordWidth = paint.measureText(word.text)
            totalWidth += wordWidth
            when {
                positionMs >= word.endMs -> completedWidth += wordWidth
                positionMs > word.startMs -> {
                    val wordProgress = ((positionMs - word.startMs).toFloat() /
                        (word.endMs - word.startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
                    completedWidth += wordWidth * wordProgress
                }
            }
        }
        return if (totalWidth > 0f) completedWidth / totalWidth else 0f
    }

    private fun layoutWords(
        words: List<LyricWord>,
        paint: TextPaint,
        startX: Float,
        baseline: Float,
        availableWidth: Float
    ): List<VideoWordInfo> {
        val infos = mutableListOf<VideoWordInfo>()
        val lineSpacing = paint.fontSpacing
        var cursorX = startX
        var cursorY = baseline
        var visualLine = 0

        for (word in words) {
            val rawText = word.text
            var wordText = if (cursorX == startX) rawText.trimStart() else rawText
            if (wordText.isBlank()) continue
            var wordW = paint.measureText(wordText)
            if (cursorX + wordW > startX + availableWidth && cursorX > startX) {
                cursorX = startX
                cursorY += lineSpacing
                visualLine++
                wordText = rawText.trimStart()
                if (wordText.isBlank()) continue
                wordW = paint.measureText(wordText)
            }
            infos.add(VideoWordInfo(word, wordText, cursorX, cursorY, wordW, visualLine))
            cursorX += wordW
        }

        if (infos.isEmpty()) return infos
        val lineOffsets = infos.groupBy { it.visualLine }.mapValues { (_, lineInfos) ->
            val lineStart = lineInfos.minOf { it.x }
            val lineEnd = lineInfos.maxOf { it.x + it.w }
            startX + (availableWidth - (lineEnd - lineStart)) / 2f - lineStart
        }
        return infos.map { info ->
            info.copy(x = info.x + (lineOffsets[info.visualLine] ?: 0f))
        }
    }

    private fun drawStaticText(canvas: Canvas, layout: StaticLayout, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun drawStaticLayout(canvas: Canvas, layout: StaticLayout, paint: TextPaint, x: Float, y: Float) {
        canvas.save()
        canvas.translate(x, y)
        layout.draw(canvas)
        canvas.restore()
    }

    private fun renderLineToBitmap(timeline: LineTimeline): Bitmap {
        val text = timeline.line.text.ifBlank { timeline.line.backgroundText.orEmpty() }
        val textWidth = (VIDEO_SIZE * 0.84f).toInt()
        val mainLayout = buildVideoLayout(text, dimPaint, textWidth)
        val transLayout = timeline.translation?.let { buildVideoLayout(it, dimTransPaint, textWidth) }
        val totalHeight = (mainLayout.height + (transLayout?.let { it.height + TRANS_GAP } ?: 0f)).roundToInt()
        val bmp = Bitmap.createBitmap(textWidth, totalHeight.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.save()
        c.translate(0f, 0f)
        mainLayout.draw(c)
        c.restore()
        transLayout?.let {
            c.save()
            c.translate(0f, mainLayout.height + TRANS_GAP)
            it.draw(c)
            c.restore()
        }
        return bmp
    }

    private fun calculateLineY(timeline: LineTimeline): Float {
        val text = timeline.line.text.ifBlank { timeline.line.backgroundText.orEmpty() }
        val textWidth = (VIDEO_SIZE * 0.84f).toInt()
        val mainLayout = buildVideoLayout(text, mainPaint, textWidth)
        val transLayout = timeline.translation?.let { buildVideoLayout(it, transPaint, textWidth) }
        val totalHeight = mainLayout.height + (transLayout?.let { it.height + TRANS_GAP } ?: 0f)
        return (VIDEO_SIZE - totalHeight) / 2f
    }

    private fun calculateLineX(timeline: LineTimeline, bitmapWidth: Int): Float {
        return (VIDEO_SIZE - bitmapWidth) / 2f
    }

    private fun buildVideoLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, max(1, width))
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setLineSpacing(8f, 1f)
            .setIncludePad(true)
            .build()
    }

    private fun prepareBackgroundAssets(): BackgroundAssets {
        val fallbackGradient = intArrayOf(
            Color.rgb(42, 46, 72),
            Color.rgb(27, 31, 54),
            Color.rgb(14, 17, 30)
        )
        val fallbackBlobs = intArrayOf(
            Color.rgb(117, 140, 255),
            Color.rgb(255, 111, 173),
            Color.rgb(94, 229, 197),
            Color.rgb(255, 196, 108)
        )
        val localCover = cover ?: return BackgroundAssets(
            texture = null,
            gradientColors = fallbackGradient,
            blobColors = fallbackBlobs
        )

        val cropped = centerCropScale(localCover, COVER_TEXTURE_SIZE, COVER_TEXTURE_SIZE)
        val boosted = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        val saturationMatrix = ColorMatrix().apply { setSaturation(2.5f) }
        val brightnessMatrix = ColorMatrix(
            floatArrayOf(
                1.05f, 0f, 0f, 0f, 0f,
                0f, 1.05f, 0f, 0f, 0f,
                0f, 0f, 1.05f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f
            )
        )
        saturationMatrix.postConcat(brightnessMatrix)
        Canvas(boosted).drawBitmap(
            cropped,
            0f,
            0f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                isFilterBitmap = true
                colorFilter = ColorMatrixColorFilter(saturationMatrix)
            }
        )
        if (cropped !== localCover) cropped.recycle()
        stackBlur(boosted, 25)

        val colors = sampleFlowingBackgroundColors(localCover)
        return BackgroundAssets(
            texture = boosted,
            gradientColors = intArrayOf(colors[0], colors[1], colors[2]),
            blobColors = intArrayOf(colors[3], colors[4], colors[5], colors[6])
        )
    }

    private fun drawDynamicBackground(canvas: Canvas, timeMs: Long) {
        val size = VIDEO_SIZE.toFloat()
        backgroundPaint.shader = LinearGradient(
            0f,
            0f,
            size,
            size,
            backgroundAssets.gradientColors,
            null,
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size, size, backgroundPaint)
        backgroundPaint.shader = null

        textureShader?.let { shader ->
            drawTextureLayer(canvas, shader, size, timeMs, 100_000L, 360f, 1.78f, 0f, 0f, 92)
            drawTextureLayer(canvas, shader, size, timeMs, 70_000L, -360f, 1.82f, -0.95f, -0.70f, 80)
            drawTextureLayer(canvas, shader, size, timeMs, 40_000L, -360f, 1.68f, -0.50f, 0.70f, 108)
        }

        overlayPaint.color = Color.parseColor("#52000000")
        canvas.drawRect(0f, 0f, size, size, overlayPaint)
        overlayPaint.color = Color.parseColor("#1A000000")
        canvas.drawRect(0f, 0f, size, size, overlayPaint)

        overlayPaint.shader = LinearGradient(
            0f,
            0f,
            0f,
            size,
            intArrayOf(
                Color.argb(62, 0, 0, 0),
                Color.argb(108, 0, 0, 0),
                Color.argb(154, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size, size, overlayPaint)
        overlayPaint.shader = null

        overlayPaint.shader = RadialGradient(
            size * 0.5f,
            size * 0.42f,
            size * 0.82f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(54, 0, 0, 0),
                Color.argb(118, 0, 0, 0)
            ),
            floatArrayOf(0f, 0.72f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, size, size, overlayPaint)
        overlayPaint.shader = null
    }

    private fun drawTextureLayer(
        canvas: Canvas,
        shader: BitmapShader,
        canvasSize: Float,
        timeMs: Long,
        durationMs: Long,
        fullRotationDeg: Float,
        scale: Float,
        translateXFactor: Float,
        translateYFactor: Float,
        alpha: Int
    ) {
        val textureSize = backgroundAssets.texture?.width?.toFloat()?.takeIf { it > 0f } ?: return
        val progress = (timeMs % durationMs).toFloat() / durationMs.toFloat()
        val scaleFactor = (canvasSize / textureSize) * scale
        textureMatrix.reset()
        textureMatrix.setScale(scaleFactor, scaleFactor)
        textureMatrix.postRotate(fullRotationDeg * progress, canvasSize / 2f, canvasSize / 2f)
        textureMatrix.postTranslate(canvasSize * translateXFactor, canvasSize * translateYFactor)
        shader.setLocalMatrix(textureMatrix)
        texturePaint.shader = shader
        texturePaint.alpha = alpha.coerceIn(0, 255)
        canvas.drawRect(0f, 0f, canvasSize, canvasSize, texturePaint)
        texturePaint.shader = null
    }

    private fun drawBlob(
        canvas: Canvas,
        color: Int,
        centerX: Float,
        centerY: Float,
        radius: Float,
        alpha: Int
    ) {
        blobPaint.shader = RadialGradient(
            centerX,
            centerY,
            radius,
            intArrayOf(color.withAlpha(alpha), Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(centerX, centerY, radius, blobPaint)
        blobPaint.shader = null
    }

    private fun sampleFlowingBackgroundColors(bitmap: Bitmap): IntArray {
        val points = listOf(
            0.16f to 0.18f,
            0.74f to 0.20f,
            0.54f to 0.52f,
            0.20f to 0.78f,
            0.82f to 0.72f,
            0.52f to 0.26f,
            0.66f to 0.88f
        )
        return points.map { (fx, fy) ->
            val x = (bitmap.width * fx).toInt().coerceIn(0, bitmap.width - 1)
            val y = (bitmap.height * fy).toInt().coerceIn(0, bitmap.height - 1)
            bitmap.getPixel(x, y).boostFlowingColor()
        }.toIntArray()
    }

    private fun centerCropScale(source: Bitmap, targetW: Int, targetH: Int): Bitmap {
        val scale = max(targetW / source.width.toFloat(), targetH / source.height.toFloat())
        val scaledW = (source.width * scale).toInt().coerceAtLeast(targetW)
        val scaledH = (source.height * scale).toInt().coerceAtLeast(targetH)
        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        val left = ((scaledW - targetW) / 2).coerceAtLeast(0)
        val top = ((scaledH - targetH) / 2).coerceAtLeast(0)
        val cropped = Bitmap.createBitmap(scaled, left, top, targetW, targetH)
        if (scaled !== source && scaled !== cropped) scaled.recycle()
        return cropped
    }

    fun recycle() {
        backgroundAssets.texture?.recycle()
        textBitmapCache?.recycle()
        textBitmapCache = null
    }

    private fun easeOutCubic(value: Float): Float {
        val clamped = value.coerceIn(0f, 1f)
        return 1f - (1f - clamped) * (1f - clamped) * (1f - clamped)
    }
}

internal fun isLyricVideoInterLineGap(
    timeMs: Long,
    karaokeEndMs: Long,
    nextLineStartMs: Long?
): Boolean = nextLineStartMs != null && timeMs >= karaokeEndMs && timeMs < nextLineStartMs

private fun Int.boostFlowingColor(): Int {
    val hsv = FloatArray(3)
    Color.colorToHSV(this, hsv)
    hsv[1] = (hsv[1] * 1.55f).coerceIn(0.40f, 1f)
    hsv[2] = (hsv[2] * 1.16f).coerceIn(0.48f, 1f)
    return Color.HSVToColor(hsv)
}

private fun Int.withAlpha(alpha: Int): Int =
    Color.argb(
        alpha.coerceIn(0, 255),
        Color.red(this),
        Color.green(this),
        Color.blue(this)
    )

private fun stackBlur(bitmap: Bitmap, radius: Int) {
    if (radius < 1) return
    val w = bitmap.width
    val h = bitmap.height
    val pix = IntArray(w * h)
    bitmap.getPixels(pix, 0, w, 0, 0, w, h)

    val wm = w - 1
    val hm = h - 1
    val wh = w * h
    val div = radius + radius + 1

    val r = IntArray(wh)
    val g = IntArray(wh)
    val b = IntArray(wh)
    var rsum: Int; var gsum: Int; var bsum: Int
    var rinsum: Int; var ginsum: Int; var binsum: Int
    var routsum: Int; var goutsum: Int; var boutsum: Int
    var p: Int; var yp: Int
    val vmin = IntArray(max(w, h))
    var divsum = (div + 1) shr 1
    divsum *= divsum
    val dv = IntArray(256 * divsum)
    for (i in dv.indices) dv[i] = i / divsum

    var yi = 0
    val stack = Array(div) { IntArray(3) }
    var stackpointer: Int; var stackstart: Int; var sir: IntArray
    var rbs: Int

    for (y in 0 until h) {
        rinsum = 0; ginsum = 0; binsum = 0
        routsum = 0; goutsum = 0; boutsum = 0
        rsum = 0; gsum = 0; bsum = 0
        for (i in -radius..radius) {
            p = pix[yi + min(wm, max(i, 0))]
            sir = stack[i + radius]
            sir[0] = (p and 0xff0000) shr 16
            sir[1] = (p and 0x00ff00) shr 8
            sir[2] = p and 0x0000ff
            rbs = radius + 1 - kotlin.math.abs(i)
            rsum += sir[0] * rbs
            gsum += sir[1] * rbs
            bsum += sir[2] * rbs
            if (i > 0) {
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            } else {
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            }
        }
        stackpointer = radius
        for (x in 0 until w) {
            r[yi] = dv[rsum]; g[yi] = dv[gsum]; b[yi] = dv[bsum]
            rsum -= routsum; gsum -= goutsum; bsum -= boutsum
            stackstart = stackpointer - radius + div
            sir = stack[stackstart % div]
            routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
            if (y == 0) vmin[x] = min(x + radius + 1, wm)
            p = pix[vmin[x] + y * w]  // read from horizontal neighbor
            sir[0] = (p and 0xff0000) shr 16
            sir[1] = (p and 0x00ff00) shr 8
            sir[2] = p and 0x0000ff
            rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            rsum += rinsum; gsum += ginsum; bsum += binsum
            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer % div]
            routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
            yi++
        }
    }
    for (x in 0 until w) {
        rinsum = 0; ginsum = 0; binsum = 0
        routsum = 0; goutsum = 0; boutsum = 0
        rsum = 0; gsum = 0; bsum = 0
        yp = -radius * w
        for (i in -radius..radius) {
            yi = max(0, yp) + x
            sir = stack[i + radius]
            sir[0] = r[yi]; sir[1] = g[yi]; sir[2] = b[yi]
            rbs = radius + 1 - kotlin.math.abs(i)
            rsum += r[yi] * rbs; gsum += g[yi] * rbs; bsum += b[yi] * rbs
            if (i > 0) {
                rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            } else {
                routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            }
            if (i < hm) yp += w
        }
        yi = x
        stackpointer = radius
        for (y in 0 until h) {
            pix[yi] = (0xff000000.toInt() and pix[yi]) or (dv[rsum] shl 16) or (dv[gsum] shl 8) or dv[bsum]
            rsum -= routsum; gsum -= goutsum; bsum -= boutsum
            stackstart = stackpointer - radius + div
            sir = stack[stackstart % div]
            routsum -= sir[0]; goutsum -= sir[1]; boutsum -= sir[2]
            if (x == 0) vmin[y] = min(y + radius + 1, hm) * w
            p = x + vmin[y]
            sir[0] = r[p]; sir[1] = g[p]; sir[2] = b[p]
            rinsum += sir[0]; ginsum += sir[1]; binsum += sir[2]
            rsum += rinsum; gsum += ginsum; bsum += binsum
            stackpointer = (stackpointer + 1) % div
            sir = stack[stackpointer]
            routsum += sir[0]; goutsum += sir[1]; boutsum += sir[2]
            rinsum -= sir[0]; ginsum -= sir[1]; binsum -= sir[2]
            yi += w
        }
    }
    bitmap.setPixels(pix, 0, w, 0, 0, w, h)
}
