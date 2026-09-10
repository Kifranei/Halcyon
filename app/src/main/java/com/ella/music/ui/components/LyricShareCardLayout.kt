package com.ella.music.ui.components

import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import kotlin.math.roundToInt

internal const val SHARE_CARD_WIDTH = 1080
internal const val SHARE_CARD_MIN_HEIGHT = 1
internal const val SHARE_CARD_MAX_HEIGHT = 1920
internal const val SHARE_CARD_MAX_BLOCKS = 10

/** Apple Music `share_lyrics_sticker_width` (296dp). All sticker metrics scale from this. */
internal const val APPLE_MUSIC_SHARE_STICKER_WIDTH_DP = 296f
internal const val APPLE_MUSIC_SHARE_STICKER_RADIUS_DP = 12f
internal const val APPLE_MUSIC_SHARE_STICKER_PADDING_DP = 16f
internal const val APPLE_MUSIC_SHARE_STICKER_LYRIC_MARGIN_DP = 14f
internal const val APPLE_MUSIC_SHARE_STICKER_CHIN_HEIGHT_DP = 84f
internal const val APPLE_MUSIC_SHARE_STICKER_COVER_DP = 52f
internal const val APPLE_MUSIC_SHARE_STICKER_COVER_RADIUS_DP = 5f
internal const val APPLE_MUSIC_SHARE_STICKER_COVER_TEXT_GAP_DP = 12f
internal const val APPLE_MUSIC_SHARE_STICKER_LOGO_HEIGHT_DP = 12f
internal const val APPLE_MUSIC_SHARE_STICKER_LOGO_GAP_DP = 4f
internal const val APPLE_MUSIC_SHARE_STICKER_LYRIC_BIG_SP = 24f
internal const val APPLE_MUSIC_SHARE_STICKER_LYRIC_SMALL_SP = 20f
internal const val APPLE_MUSIC_SHARE_STICKER_META_SP = 17f
internal const val APPLE_MUSIC_SHARE_STICKER_LINE_ASCENT_BIG_DP = 6f
internal const val APPLE_MUSIC_SHARE_STICKER_LINE_ASCENT_SMALL_DP = 4f
internal const val APPLE_MUSIC_SHARE_BIG_FONT_MAX_LINES = 3
internal const val APPLE_MUSIC_SHARE_BIG_FONT_MAX_CHARS = 80

internal data class MeasuredTextBlock(
    val layout: StaticLayout,
    val gapAfter: Float
)

internal data class MeasuredShareLyricBlock(
    val primary: MeasuredTextBlock,
    val secondary: List<MeasuredTextBlock>,
    val gapAfter: Float
)

internal data class LyricShareCardLayout(
    val canvasWidth: Int,
    val adaptiveCanvasHeight: Int,
    val cardRadius: Float,
    val coverRadius: Float,
    val safePadding: Float,
    val lyricsTop: Float,
    val chinTop: Float,
    val chinHeight: Float,
    val coverRect: RectF,
    val titleLayout: StaticLayout,
    val artistLayout: StaticLayout,
    val titleTop: Float,
    val artistTop: Float,
    val lyricBlocks: List<MeasuredShareLyricBlock>,
    val footerPaint: TextPaint,
    val footerText: String,
    val footerBaseline: Float,
    val style: LyricShareCardStyle
)

internal fun appleMusicShareCardScale(canvasWidth: Int): Float =
    canvasWidth / APPLE_MUSIC_SHARE_STICKER_WIDTH_DP

internal fun shouldUseAppleMusicShareBigFont(blocks: List<ShareLyricBlock>): Boolean {
    val totalChars = blocks.sumOf { it.primary.length }
    return blocks.size <= APPLE_MUSIC_SHARE_BIG_FONT_MAX_LINES &&
        totalChars <= APPLE_MUSIC_SHARE_BIG_FONT_MAX_CHARS
}

internal fun calculateLyricShareLayout(
    content: LyricShareCardContent,
    canvasWidth: Int = SHARE_CARD_WIDTH,
    minHeight: Int = SHARE_CARD_MIN_HEIGHT,
    maxHeight: Int = SHARE_CARD_MAX_HEIGHT,
    shareTypeface: android.graphics.Typeface? = null
): LyricShareCardLayout {
    val scale = appleMusicShareCardScale(canvasWidth)
    val padding = APPLE_MUSIC_SHARE_STICKER_PADDING_DP * scale
    val lyricMargin = APPLE_MUSIC_SHARE_STICKER_LYRIC_MARGIN_DP * scale
    val chinHeight = APPLE_MUSIC_SHARE_STICKER_CHIN_HEIGHT_DP * scale
    val coverSize = APPLE_MUSIC_SHARE_STICKER_COVER_DP * scale
    val coverRadius = APPLE_MUSIC_SHARE_STICKER_COVER_RADIUS_DP * scale
    val coverTextGap = APPLE_MUSIC_SHARE_STICKER_COVER_TEXT_GAP_DP * scale
    val cardRadius = APPLE_MUSIC_SHARE_STICKER_RADIUS_DP * scale
    val logoHeight = APPLE_MUSIC_SHARE_STICKER_LOGO_HEIGHT_DP * scale
    val logoGap = APPLE_MUSIC_SHARE_STICKER_LOGO_GAP_DP * scale
    val useBigFont = shouldUseAppleMusicShareBigFont(content.blocks)
    val lyricWidth = (canvasWidth - padding * 2).roundToInt().coerceAtLeast(1)
    val metaWidth = (canvasWidth - padding * 2 - coverSize - coverTextGap).roundToInt().coerceAtLeast(1)

    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = APPLE_MUSIC_SHARE_STICKER_META_SP * scale
        typeface = shareTypeface.shareCardTypeface(android.graphics.Typeface.BOLD)
    }
    val artistPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = APPLE_MUSIC_SHARE_STICKER_META_SP * scale
        typeface = shareTypeface.shareCardTypeface(android.graphics.Typeface.NORMAL)
    }
    val titleLayout = buildLayout(
        text = content.title,
        paint = titlePaint,
        width = metaWidth,
        maxLines = 1,
        lineSpacingAdd = 0f,
        lineSpacingMult = 1f
    )
    val artistLayout = buildLayout(
        text = content.artist,
        paint = artistPaint,
        width = metaWidth,
        maxLines = 1,
        lineSpacingAdd = 0f,
        lineSpacingMult = 1f
    )
    val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(0x66, 255, 255, 255)
        textSize = logoHeight
        typeface = shareTypeface.shareCardTypeface(android.graphics.Typeface.BOLD)
    }

    val isLegacyTopMetadata = content.style == LyricShareCardStyle.LegacyTopMetadata
    val topMetadataHeight = titleLayout.height + artistLayout.height
    val actualChinHeight = chinHeight
    val lyricsTop = if (isLegacyTopMetadata) {
        chinHeight + lyricMargin
    } else {
        lyricMargin
    }
    val maxLyricsHeight = (
        maxHeight - lyricsTop - lyricMargin - if (isLegacyTopMetadata) 0f else actualChinHeight
        ).coerceAtLeast(coverSize).roundToInt()
    val basePrimary = if (useBigFont) {
        APPLE_MUSIC_SHARE_STICKER_LYRIC_BIG_SP * scale
    } else {
        APPLE_MUSIC_SHARE_STICKER_LYRIC_SMALL_SP * scale
    }
    val lineAscent = (
        if (useBigFont) APPLE_MUSIC_SHARE_STICKER_LINE_ASCENT_BIG_DP
        else APPLE_MUSIC_SHARE_STICKER_LINE_ASCENT_SMALL_DP
        ) * scale
    val candidates = buildList {
        add(basePrimary)
        add(basePrimary * 0.92f)
        add(basePrimary * 0.84f)
        add(APPLE_MUSIC_SHARE_STICKER_LYRIC_SMALL_SP * scale)
        add(APPLE_MUSIC_SHARE_STICKER_LYRIC_SMALL_SP * scale * 0.88f)
        add(APPLE_MUSIC_SHARE_STICKER_LYRIC_SMALL_SP * scale * 0.78f)
    }.distinct()

    val measuredLyrics = candidates
        .firstNotNullOfOrNull { primarySize ->
            measureLyricBlocks(
                blocks = content.blocks,
                width = lyricWidth,
                primarySize = primarySize,
                lineAscent = lineAscent,
                availableHeight = maxLyricsHeight.toFloat(),
                shareTypeface = shareTypeface,
                appendEllipsis = content.appendEllipsis
            )?.takeIf { it.first.isNotEmpty() }
        }
        ?: measureLyricBlocks(
            blocks = content.blocks,
            width = lyricWidth,
            primarySize = candidates.last(),
            lineAscent = lineAscent,
            availableHeight = maxLyricsHeight.toFloat(),
            shareTypeface = shareTypeface,
            forceTruncate = true,
            appendEllipsis = content.appendEllipsis
        )
        ?: (emptyList<MeasuredShareLyricBlock>() to 0f)

    val lyricsHeight = measuredLyrics.second
    val chinTop = if (isLegacyTopMetadata) 0f else lyricsTop + lyricsHeight + lyricMargin
    val adaptiveCanvasHeight = (
        if (isLegacyTopMetadata) lyricsTop + lyricsHeight + lyricMargin
        else chinTop + actualChinHeight
        ).roundToInt()
        .coerceIn(minHeight.coerceAtLeast(1), maxHeight)

    val coverCenterY = chinTop + actualChinHeight / 2f
    val coverRect = RectF(
        padding,
        coverCenterY - coverSize / 2f,
        padding + coverSize,
        coverCenterY + coverSize / 2f
    )
    val stackHeight = topMetadataHeight + logoGap + logoHeight
    val stackTop = chinTop + (actualChinHeight - stackHeight) / 2f
    val titleTop = stackTop
    val artistTop = titleTop + titleLayout.height
    val footerTop = artistTop + artistLayout.height + logoGap
    val footerBaseline = footerTop - footerPaint.fontMetrics.ascent

    return LyricShareCardLayout(
        canvasWidth = canvasWidth,
        adaptiveCanvasHeight = adaptiveCanvasHeight,
        cardRadius = cardRadius,
        coverRadius = coverRadius,
        safePadding = padding,
        lyricsTop = lyricsTop,
        chinTop = chinTop,
        chinHeight = actualChinHeight,
        coverRect = coverRect,
        titleLayout = titleLayout,
        artistLayout = artistLayout,
        titleTop = titleTop,
        artistTop = artistTop,
        lyricBlocks = measuredLyrics.first,
        footerPaint = footerPaint,
        footerText = content.footerText,
        footerBaseline = footerBaseline,
        style = content.style
    )
}

private fun android.graphics.Typeface?.shareCardTypeface(style: Int): android.graphics.Typeface =
    if (this != null) android.graphics.Typeface.create(this, style)
    else android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, style)

private fun measureLyricBlocks(
    blocks: List<ShareLyricBlock>,
    width: Int,
    primarySize: Float,
    lineAscent: Float,
    availableHeight: Float,
    shareTypeface: android.graphics.Typeface? = null,
    forceTruncate: Boolean = false,
    appendEllipsis: Boolean = true
): Pair<List<MeasuredShareLyricBlock>, Float>? {
    val primaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = primarySize
        typeface = shareTypeface.shareCardTypeface(android.graphics.Typeface.BOLD)
    }
    val secondaryPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(186, 255, 255, 255)
        textSize = (primarySize * 0.55f).coerceAtLeast(primarySize * 0.42f)
        typeface = shareTypeface.shareCardTypeface(android.graphics.Typeface.NORMAL)
    }
    val secondaryGap = (primarySize * 0.12f).coerceIn(4f, 10f)

    fun measureBlock(block: ShareLyricBlock, isLast: Boolean): MeasuredShareLyricBlock {
        val primaryLayout = buildLayout(
            text = block.primary,
            paint = primaryPaint,
            width = width,
            maxLines = if (blocks.size <= 2) 6 else 4,
            lineSpacingAdd = 0f,
            lineSpacingMult = 1.02f,
            balanced = true
        )
        val secondaryLayouts = block.secondary.map {
            MeasuredTextBlock(
                layout = buildLayout(
                    text = it,
                    paint = secondaryPaint,
                    width = width,
                    maxLines = 3,
                    lineSpacingAdd = 0f,
                    lineSpacingMult = 1f,
                    balanced = true
                ),
                gapAfter = secondaryGap
            )
        }
        return MeasuredShareLyricBlock(
            primary = MeasuredTextBlock(primaryLayout, if (secondaryLayouts.isEmpty()) 0f else secondaryGap),
            secondary = secondaryLayouts,
            gapAfter = if (isLast) 0f else lineAscent * 2f
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
            if (!appendEllipsis) {
                while (measured.isNotEmpty() && totalHeight > availableHeight) {
                    totalHeight -= blockHeight(measured.removeAt(measured.lastIndex))
                }
                return measured to totalHeight.coerceAtMost(availableHeight)
            }
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
                    measured += last.copy(gapAfter = lineAscent * 2f)
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

private fun buildLayout(
    text: String,
    paint: TextPaint,
    width: Int,
    maxLines: Int,
    lineSpacingAdd: Float,
    lineSpacingMult: Float,
    balanced: Boolean = false
): StaticLayout {
    return StaticLayout.Builder.obtain(text, 0, text.length, paint, width.coerceAtLeast(1))
        .setAlignment(Layout.Alignment.ALIGN_NORMAL)
        .setIncludePad(false)
        .setLineSpacing(lineSpacingAdd, lineSpacingMult)
        .setMaxLines(maxLines)
        .setEllipsize(TextUtils.TruncateAt.END)
        .setBreakStrategy(
            if (balanced) Layout.BREAK_STRATEGY_BALANCED
            else Layout.BREAK_STRATEGY_HIGH_QUALITY
        )
        .build()
}
