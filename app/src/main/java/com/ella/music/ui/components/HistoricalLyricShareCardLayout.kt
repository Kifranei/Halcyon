package com.ella.music.ui.components

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.max
import kotlin.math.roundToInt

internal const val HISTORICAL_SHARE_CARD_WIDTH = 1080
internal const val HISTORICAL_SHARE_CARD_MIN_HEIGHT = 720
internal const val HISTORICAL_SHARE_CARD_MAX_HEIGHT = Int.MAX_VALUE
private const val HISTORICAL_SHARE_CARD_HORIZONTAL_PADDING = 92f
private const val HISTORICAL_SHARE_CARD_TOP_PADDING = 88f
private const val HISTORICAL_SHARE_CARD_BOTTOM_PADDING = 72f
private const val HISTORICAL_SHARE_CARD_COVER_SIZE = 156f
private const val HISTORICAL_SHARE_CARD_HEADER_GAP = 60f
private const val HISTORICAL_SHARE_CARD_FOOTER_GAP = 60f
private const val HISTORICAL_SHARE_CARD_SECONDARY_GAP = 10f
private const val HISTORICAL_SHARE_CARD_MIN_BLOCK_GAP = 20f
private const val HISTORICAL_SHARE_CARD_MAX_BLOCK_GAP = 34f


internal data class HistoricalLyricShareCardLayout(
    val canvasWidth: Int,
    val adaptiveCanvasHeight: Int,
    val safePadding: Float,
    val headerTop: Float,
    val headerHeight: Float,
    val lyricsTop: Float,
    val lyricsHeight: Float,
    val footerTop: Float,
    val footerHeight: Float,
    val viaTextBaseline: Float,
    val songInfoTop: Float,
    val songInfoHeight: Float,
    val coverRect: RectF,
    val titleLayout: StaticLayout,
    val annotationLayout: StaticLayout?,
    val artistLayout: StaticLayout,
    val lyricBlocks: List<MeasuredShareLyricBlock>,
    val footerPaint: TextPaint,
    val footerText: String
)

private data class HistoricalLyricSizingCandidate(
    val primarySize: Float,
    val secondarySize: Float,
    val primaryMaxLines: Int,
    val secondaryMaxLines: Int,
    val primaryLineSpacingAdd: Float,
    val secondaryLineSpacingAdd: Float,
    val blockGap: Float
)

internal fun historicalCalculateLyricShareLayout(
    content: LyricShareCardContent,
    canvasWidth: Int = HISTORICAL_SHARE_CARD_WIDTH,
    minHeight: Int = HISTORICAL_SHARE_CARD_MIN_HEIGHT,
    maxHeight: Int = HISTORICAL_SHARE_CARD_MAX_HEIGHT,
    shareTypeface: android.graphics.Typeface? = null
): HistoricalLyricShareCardLayout {
    val safePadding = HISTORICAL_SHARE_CARD_HORIZONTAL_PADDING
    val coverRect = RectF(
        safePadding,
        HISTORICAL_SHARE_CARD_TOP_PADDING,
        safePadding + HISTORICAL_SHARE_CARD_COVER_SIZE,
        HISTORICAL_SHARE_CARD_TOP_PADDING + HISTORICAL_SHARE_CARD_COVER_SIZE
    )
    val textLeft = coverRect.right + 28f
    val textWidth = (canvasWidth - textLeft - safePadding).roundToInt()

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 38f
        typeface = shareTypeface.historicalShareCardTypeface(android.graphics.Typeface.BOLD)
        setShadowLayer(18f, 0f, 8f, Color.argb(76, 0, 0, 0))
    }
    val annotationPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(214, 255, 255, 255)
        textSize = 27f
        typeface = shareTypeface.historicalShareCardTypeface(android.graphics.Typeface.BOLD)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(168, 255, 255, 255)
        textSize = 26f
        typeface = shareTypeface.historicalShareCardTypeface(android.graphics.Typeface.NORMAL)
    }
    val titleLayout = historicalBuildLayout(
        text = content.title,
        paint = titlePaint,
        width = textWidth,
        maxLines = 2,
        lineSpacingAdd = 4f,
        lineSpacingMult = 1.02f
    )
    val annotationLayout = content.annotation.takeIf { it.isNotBlank() }?.let {
        historicalBuildLayout(
            text = it,
            paint = annotationPaint,
            width = textWidth,
            maxLines = 1,
            lineSpacingAdd = 2f,
            lineSpacingMult = 1f
        )
    }
    val artistLayout = historicalBuildLayout(
        text = content.artist,
        paint = artistPaint,
        width = textWidth,
        maxLines = 2,
        lineSpacingAdd = 2f,
        lineSpacingMult = 1f
    )
    val songInfoHeight = max(
        HISTORICAL_SHARE_CARD_COVER_SIZE,
        (
            titleLayout.height +
                (annotationLayout?.height?.plus(10) ?: 0) +
                12 +
                artistLayout.height
            ).toFloat()
    )
    val headerTop = HISTORICAL_SHARE_CARD_TOP_PADDING
    val headerHeight = songInfoHeight
    val songInfoTop = headerTop

    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(112, 255, 255, 255)
        textSize = 24f
        typeface = shareTypeface.historicalShareCardTypeface(android.graphics.Typeface.BOLD)
    }
    val footerHeight = footerPaint.fontMetrics.run { bottom - top } + 8f
    val lyricsTop = headerTop + headerHeight + HISTORICAL_SHARE_CARD_HEADER_GAP
    val maxLyricsHeight = (
        maxHeight -
            HISTORICAL_SHARE_CARD_BOTTOM_PADDING -
            footerHeight -
            HISTORICAL_SHARE_CARD_FOOTER_GAP -
            lyricsTop
        ).coerceAtLeast(120f).roundToInt()

    val candidates = historicalBuildLyricSizingCandidates(content.blocks)
    val measuredLyrics = candidates
        .firstNotNullOfOrNull { candidate ->
            historicalMeasureLyricBlocks(
                blocks = content.blocks,
                width = (canvasWidth - safePadding * 2).roundToInt(),
                candidate = candidate,
                availableHeight = maxLyricsHeight.toFloat(),
                shareTypeface = shareTypeface
            )?.takeIf { it.first.isNotEmpty() }
        }
        ?: historicalMeasureLyricBlocks(
            blocks = content.blocks,
            width = (canvasWidth - safePadding * 2).roundToInt(),
            candidate = candidates.last(),
            availableHeight = maxLyricsHeight.toFloat(),
            shareTypeface = shareTypeface,
            forceTruncate = true
        )
        ?: (emptyList<MeasuredShareLyricBlock>() to 0f)

    val lyricsHeight = measuredLyrics.second
    val footerTop = (lyricsTop + lyricsHeight + HISTORICAL_SHARE_CARD_FOOTER_GAP)
        .coerceAtLeast(lyricsTop + lyricsHeight + 48f)
    val adaptiveCanvasHeight = (
        footerTop +
            footerHeight +
            HISTORICAL_SHARE_CARD_BOTTOM_PADDING
        ).roundToInt().coerceIn(minHeight, maxHeight)
    val viaTextBaseline = adaptiveCanvasHeight - HISTORICAL_SHARE_CARD_BOTTOM_PADDING - footerPaint.fontMetrics.descent

    return HistoricalLyricShareCardLayout(
        canvasWidth = canvasWidth,
        adaptiveCanvasHeight = adaptiveCanvasHeight,
        safePadding = safePadding,
        headerTop = headerTop,
        headerHeight = headerHeight,
        lyricsTop = lyricsTop,
        lyricsHeight = lyricsHeight,
        footerTop = footerTop,
        footerHeight = footerHeight,
        viaTextBaseline = viaTextBaseline,
        songInfoTop = songInfoTop,
        songInfoHeight = songInfoHeight,
        coverRect = coverRect,
        titleLayout = titleLayout,
        annotationLayout = annotationLayout,
        artistLayout = artistLayout,
        lyricBlocks = measuredLyrics.first,
        footerPaint = footerPaint,
        footerText = content.footerText
    )
}

private fun android.graphics.Typeface?.historicalShareCardTypeface(style: Int): android.graphics.Typeface =
    if (this != null) android.graphics.Typeface.create(this, style)
    else android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, style)

private fun historicalBuildLyricSizingCandidates(blocks: List<ShareLyricBlock>): List<HistoricalLyricSizingCandidate> {
    val longestLine = blocks.maxOfOrNull { it.primary.length } ?: 0
    val preferredSizes = buildList {
        add(historicalBaseShareLyricTextSize(blocks.size, longestLine))
        add(78f)
        add(72f)
        add(66f)
        add(60f)
        add(54f)
        add(48f)
        add(44f)
    }.distinct()

    return preferredSizes.map { primarySize ->
        HistoricalLyricSizingCandidate(
            primarySize = primarySize,
            secondarySize = (primarySize * 0.42f).coerceIn(22f, 34f),
            primaryMaxLines = Int.MAX_VALUE,
            secondaryMaxLines = Int.MAX_VALUE,
            primaryLineSpacingAdd = (primarySize * 0.07f).coerceIn(4f, 10f),
            secondaryLineSpacingAdd = 4f,
            blockGap = (primarySize * 0.24f).coerceIn(HISTORICAL_SHARE_CARD_MIN_BLOCK_GAP, HISTORICAL_SHARE_CARD_MAX_BLOCK_GAP)
        )
    }
}

private fun historicalMeasureLyricBlocks(
    blocks: List<ShareLyricBlock>,
    width: Int,
    candidate: HistoricalLyricSizingCandidate,
    availableHeight: Float,
    shareTypeface: android.graphics.Typeface? = null,
    forceTruncate: Boolean = false
): Pair<List<MeasuredShareLyricBlock>, Float>? {
    val primaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = candidate.primarySize
        typeface = shareTypeface.historicalShareCardTypeface(android.graphics.Typeface.BOLD)
        setShadowLayer(20f, 0f, 8f, Color.argb(92, 0, 0, 0))
    }
    val secondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(186, 255, 255, 255)
        textSize = candidate.secondarySize
        typeface = shareTypeface.historicalShareCardTypeface(android.graphics.Typeface.NORMAL)
        setShadowLayer(10f, 0f, 4f, Color.argb(70, 0, 0, 0))
    }

    fun measureBlock(block: ShareLyricBlock, isLast: Boolean): MeasuredShareLyricBlock {
        val primaryLayout = historicalBuildLayout(
            text = block.primary,
            paint = primaryPaint,
            width = width,
            maxLines = candidate.primaryMaxLines,
            lineSpacingAdd = candidate.primaryLineSpacingAdd,
            lineSpacingMult = 1f
        )
        val secondaryLayouts = block.secondary.map {
            MeasuredTextBlock(
                layout = historicalBuildLayout(
                    text = it,
                    paint = secondaryPaint,
                    width = width,
                    maxLines = candidate.secondaryMaxLines,
                    lineSpacingAdd = candidate.secondaryLineSpacingAdd,
                    lineSpacingMult = 1f
                ),
                gapAfter = HISTORICAL_SHARE_CARD_SECONDARY_GAP
            )
        }
        return MeasuredShareLyricBlock(
            primary = MeasuredTextBlock(primaryLayout, 12f),
            secondary = secondaryLayouts,
            gapAfter = if (isLast) 0f else candidate.blockGap
        )
    }

    fun blockHeight(block: MeasuredShareLyricBlock): Float {
        return block.primary.layout.height +
            block.primary.gapAfter +
            block.secondary.fold(0f) { total, secondary ->
                total + secondary.layout.height + secondary.gapAfter
            } +
            block.gapAfter
    }

    val measured = mutableListOf<MeasuredShareLyricBlock>()
    var totalHeight = 0f
    blocks.forEachIndexed { index, block ->
        val candidateBlock = measureBlock(block, isLast = index == blocks.lastIndex)
        val nextHeight = totalHeight + blockHeight(candidateBlock)
        if (nextHeight <= availableHeight) {
            measured += candidateBlock
            totalHeight = nextHeight
        } else {
            if (!forceTruncate) return null
            if (measured.isEmpty()) {
                measured += candidateBlock
                totalHeight = blockHeight(candidateBlock)
            } else {
                val ellipsisBlock = measureBlock(
                    block = ShareLyricBlock("...", emptyList()),
                    isLast = true
                )
                val ellipsisHeight = blockHeight(ellipsisBlock)
                while (measured.isNotEmpty() && totalHeight + ellipsisHeight > availableHeight) {
                    val removed = measured.removeAt(measured.lastIndex)
                    totalHeight -= blockHeight(removed)
                }
                if (measured.isNotEmpty()) {
                    val last = measured.removeAt(measured.lastIndex)
                    totalHeight -= blockHeight(last)
                    measured += last.copy(gapAfter = candidate.blockGap)
                    totalHeight += blockHeight(measured.last())
                }
                if (totalHeight + ellipsisHeight <= availableHeight || measured.isEmpty()) {
                    measured += ellipsisBlock
                    totalHeight += ellipsisHeight
                }
            }
            return measured to totalHeight.coerceAtMost(availableHeight)
        }
    }
    return measured to totalHeight
}

private fun historicalBuildLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    maxLines: Int,
    lineSpacingAdd: Float,
    lineSpacingMult: Float
): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(lineSpacingAdd, lineSpacingMult)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .build()
}

private fun historicalBaseShareLyricTextSize(blockCount: Int, longestLine: Int): Float {
    return when {
        blockCount <= 1 && longestLine <= 18 -> 90f
        blockCount <= 2 && longestLine <= 22 -> 82f
        blockCount <= 3 && longestLine <= 24 -> 76f
        blockCount <= 4 -> 70f
        blockCount <= 6 -> 62f
        else -> 56f
    }
}
