package com.ella.music.ui.analytics

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.model.Song
import com.ella.music.ui.components.CoverLoadLimiter
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.EllaLoadingIndicator
import com.ella.music.viewmodel.MainViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun SummaryCard(
    songs: List<Song>,
    playbackStats: List<SongPlaybackStats>
) {
    val context = LocalContext.current
    val listenedMs = playbackStats.sumOf { it.listenedMs }
    val playCount = playbackStats.sumOf { it.playCount }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = analyticsWallpaperCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.analytics_overview),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            StatLine(stringResource(R.string.analytics_library_song_count), stringResource(R.string.analytics_song_count_value, songs.size))
            StatLine(stringResource(R.string.analytics_library_size), formatFileSize(songs.sumOf { it.fileSize }))
            StatLine(stringResource(R.string.analytics_total_plays), stringResource(R.string.analytics_times_count, playCount))
            StatLine(stringResource(R.string.analytics_total_listen), formatListenDuration(context, listenedMs))
        }
    }
}

internal fun getCylinderSliceIndex(
    touchY: Float,
    viewHeight: Float,
    density: Float,
    sliceCount: Int,
    fractions: List<Float>
): Int? {
    if (sliceCount <= 0 || fractions.isEmpty()) return null
    val padY = 6f * density
    val cylWidth = 94f * density - 8f * density
    val rx = cylWidth / 2f
    val ry = rx * 0.18f
    val cylTopY = padY + ry
    val cylBotY = viewHeight - padY - ry
    val bodyHeight = (cylBotY - cylTopY).coerceAtLeast(10f)
    val glassH = (bodyHeight * 0.22f).coerceAtLeast(16f * density)
    val coloredTotalH = bodyHeight - glassH
    val coloredTopY = cylTopY + glassH

    if (touchY < coloredTopY - 24f * density || touchY > cylBotY + 24f * density) {
        return null
    }

    var currY = coloredTopY
    for (i in 0 until sliceCount) {
        val frac = fractions.getOrElse(i) { 0f }
        val sliceH = (coloredTotalH * frac).coerceAtLeast(0f)
        val yTop = currY
        val yBottom = if (i == sliceCount - 1) cylBotY else (currY + sliceH).coerceAtMost(cylBotY)
        if (touchY in yTop..yBottom) {
            return i
        }
        currY = yBottom
    }
    if (touchY <= coloredTopY) return 0
    if (touchY >= cylBotY) return sliceCount - 1
    return null
}

internal fun getCylinderSliceCenterY(
    index: Int,
    viewHeight: Float,
    density: Float,
    sliceCount: Int,
    fractions: List<Float>
): Float {
    val padY = 6f * density
    val cylWidth = 94f * density - 8f * density
    val rx = cylWidth / 2f
    val ry = rx * 0.18f
    val cylTopY = padY + ry
    val cylBotY = viewHeight - padY - ry
    val bodyHeight = (cylBotY - cylTopY).coerceAtLeast(10f)
    val glassH = (bodyHeight * 0.22f).coerceAtLeast(16f * density)
    val coloredTotalH = bodyHeight - glassH
    val coloredTopY = cylTopY + glassH

    var currY = coloredTopY
    for (i in 0 until sliceCount) {
        val frac = fractions.getOrElse(i) { 0f }
        val sliceH = (coloredTotalH * frac).coerceAtLeast(0f)
        val yTop = currY
        val yBottom = if (i == sliceCount - 1) cylBotY else (currY + sliceH).coerceAtMost(cylBotY)
        if (i == index) {
            return (yTop + yBottom) / 2f
        }
        currY = yBottom
    }
    return coloredTopY
}

@Composable
internal fun Xiaomi3DCylinderStorageCard(
    title: String,
    loadingText: String,
    buckets: List<AnalysisBucket>?,
    total: Int,
    totalSizeBytes: Long,
    palette: List<Color>,
    metric: AnalysisMetric = AnalysisMetric.SIZE,
    onBucketClick: ((AnalysisBucket) -> Unit)? = null,
    onBucketLongClick: ((AnalysisBucket) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        colors = analyticsWallpaperCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when {
                buckets == null -> Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 40.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EllaLoadingIndicator()
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = loadingText,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                total == 0 || buckets.isEmpty() -> Text(
                    text = stringResource(R.string.analytics_no_songs),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(vertical = 40.dp)
                )
                else -> {
                    val displayBuckets = remember(buckets, metric) {
                        if (buckets.size <= 7) {
                            buckets
                        } else {
                            val top6 = buckets.take(6)
                            val remainder = buckets.drop(6)
                            val otherCount = remainder.sumOf { it.count }
                            val otherSize = remainder.sumOf { it.sizeBytes }
                            val otherKeys = remainder.flatMap { it.songKeys }
                            top6 + AnalysisBucket(
                                label = "OTHER",
                                count = otherCount,
                                sizeBytes = otherSize,
                                songKeys = otherKeys
                            )
                        }
                    }

                    val totalWeight = remember(displayBuckets, metric, total, totalSizeBytes) {
                        val sum = displayBuckets.sumOf { if (metric == AnalysisMetric.SIZE) it.sizeBytes else it.count.toLong() }
                        if (sum > 0L) sum.toFloat() else 1f
                    }

                    val rawFractions = displayBuckets.map { b ->
                        val w = if (metric == AnalysisMetric.SIZE) b.sizeBytes.toFloat() else b.count.toFloat()
                        (w / totalWeight).coerceIn(0f, 1f)
                    }

                    val nonZeroCount = rawFractions.count { it > 0f }
                    val minFrac = if (nonZeroCount > 0) (0.22f / nonZeroCount).coerceAtMost(0.06f) else 0f
                    val remainingSpace = (1f - minFrac * nonZeroCount).coerceAtLeast(0.1f)
                    val visualFractions = rawFractions.map { f ->
                        if (f > 0f) minFrac + f * remainingSpace else 0f
                    }
                    val sumVisual = visualFractions.sum().takeIf { it > 0f } ?: 1f
                    val normalizedFractions = if (nonZeroCount == 0 && displayBuckets.isNotEmpty()) {
                        List(displayBuckets.size) { 1f / displayBuckets.size }
                    } else {
                        visualFractions.map { it / sumVisual }
                    }

                    val animatedFractions = normalizedFractions.map { frac ->
                        animateFloatAsState(
                            targetValue = frac,
                            animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
                            label = "CylinderSliceFraction"
                        ).value
                    }

                    val itemCount = displayBuckets.size
                    val cylinderHeight = remember(itemCount) {
                        (56.dp * itemCount).coerceIn(300.dp, 440.dp)
                    }

                    val density = LocalDensity.current.density
                    var activeSliceIndex by remember { mutableStateOf<Int?>(null) }
                    var rootCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    var cylinderCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
                    val dotPositions = remember { mutableStateMapOf<Int, Offset>() }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onGloballyPositioned { rootCoordinates = it }
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Xiaomi3DCylinderStorageView(
                                displayBuckets = displayBuckets,
                                animatedFractions = animatedFractions,
                                palette = palette,
                                activeSliceIndex = activeSliceIndex,
                                onActiveSliceChange = { activeSliceIndex = it },
                                onCoordinatesChanged = { cylinderCoords = it },
                                modifier = Modifier
                                    .width(94.dp)
                                    .height(cylinderHeight)
                            )

                            Spacer(modifier = Modifier.width(16.dp))

                            XiaomiStorageItemList(
                                buckets = displayBuckets,
                                palette = palette,
                                total = total,
                                totalSizeBytes = totalSizeBytes,
                                metric = metric,
                                activeSliceIndex = activeSliceIndex,
                                onDotPositioned = { idx, pos ->
                                    dotPositions[idx] = pos
                                },
                                rootCoordinates = rootCoordinates,
                                onBucketClick = onBucketClick,
                                onBucketLongClick = onBucketLongClick,
                                modifier = Modifier.weight(1f)
                            )
                        }

                        // Connecting indicator line overlay (matches HyperOS storage settings look)
                        Canvas(modifier = Modifier.matchParentSize()) {
                            val active = activeSliceIndex ?: return@Canvas
                            val targetDot = dotPositions[active] ?: return@Canvas
                            val cyl = cylinderCoords ?: return@Canvas
                            val root = rootCoordinates ?: return@Canvas
                            if (!cyl.isAttached || !root.isAttached) return@Canvas

                            val cylOffset = root.localPositionOf(cyl, Offset.Zero)
                            val padX = 4.dp.toPx()
                            val cylWidth = 94.dp.toPx() - 2 * padX
                            val rx = cylWidth / 2f
                            val cx = padX + rx
                            val startX = cylOffset.x + (cx + rx)
                            val sliceCenterY = getCylinderSliceCenterY(
                                index = active,
                                viewHeight = cyl.size.height.toFloat(),
                                density = density,
                                sliceCount = displayBuckets.size,
                                fractions = animatedFractions
                            )
                            val startY = cylOffset.y + sliceCenterY
                            val targetX = targetDot.x
                            val targetY = targetDot.y

                            val stubX = startX + 6.dp.toPx()
                            val elbowX = (targetX - 14.dp.toPx()).coerceAtLeast(stubX + 2.dp.toPx())
                            val elbowY = targetY

                            val path = Path().apply {
                                moveTo(startX, startY)
                                if (elbowX > stubX) {
                                    lineTo(stubX, startY)
                                    lineTo(elbowX, elbowY)
                                }
                                lineTo(targetX, targetY)
                            }

                            val strokeColor = palette[active % palette.size]
                            drawPath(
                                path = path,
                                color = strokeColor,
                                style = Stroke(
                                    width = 2.dp.toPx(),
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun Xiaomi3DCylinderStorageView(
    displayBuckets: List<AnalysisBucket>,
    animatedFractions: List<Float>,
    palette: List<Color>,
    activeSliceIndex: Int? = null,
    onActiveSliceChange: (Int?) -> Unit = {},
    onCoordinatesChanged: (LayoutCoordinates) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = MiuixTheme.colorScheme.background.luminance() < 0.5f
    val view = LocalView.current
    val density = LocalDensity.current.density

    Canvas(
        modifier = modifier
            .onGloballyPositioned { coords ->
                onCoordinatesChanged(coords)
            }
            .pointerInput(displayBuckets, animatedFractions) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val h = size.height.toFloat()
                    var currentIdx = getCylinderSliceIndex(down.position.y, h, density, displayBuckets.size, animatedFractions)
                    if (currentIdx != null) {
                        down.consume()
                        if (currentIdx != activeSliceIndex) {
                            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        }
                        onActiveSliceChange(currentIdx)
                    }

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (change.pressed) {
                            if (currentIdx != null) {
                                change.consume()
                            }
                            val newIdx = getCylinderSliceIndex(change.position.y, h, density, displayBuckets.size, animatedFractions)
                            if (newIdx != null && newIdx != currentIdx) {
                                currentIdx = newIdx
                                change.consume()
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                onActiveSliceChange(newIdx)
                            }
                        } else {
                            onActiveSliceChange(null)
                            break
                        }
                    }
                }
            }
    ) {
        val w = size.width
        val h = size.height
        val padX = 4.dp.toPx()
        val padY = 6.dp.toPx()
        val cylWidth = w - 2 * padX
        val rx = cylWidth / 2f
        val ry = rx * 0.18f
        val cx = padX + rx
        val cylTopY = padY + ry
        val cylBotY = h - padY - ry
        val bodyHeight = (cylBotY - cylTopY).coerceAtLeast(10f)

        // Top translucent glass chamber height (~22% of total height, mirroring Xiaomi's remaining capacity look)
        val glassH = (bodyHeight * 0.22f).coerceAtLeast(16.dp.toPx())
        val coloredTotalH = bodyHeight - glassH
        val coloredTopY = cylTopY + glassH

        // 1. Ground drop shadow beneath cylinder base
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent),
                center = Offset(cx, cylBotY + ry * 0.35f),
                radius = rx * 1.15f
            ),
            topLeft = Offset(cx - rx * 1.15f, cylBotY - ry * 0.25f),
            size = Size(rx * 2.3f, ry * 1.6f)
        )

        // Calculate positions from TOP to BOTTOM so item 0 is at the top of the stack (matches list order!)
        val sliceCount = displayBuckets.size
        val sliceBounds = mutableListOf<Pair<Float, Float>>()
        var currY = coloredTopY
        for (i in 0 until sliceCount) {
            val frac = animatedFractions.getOrElse(i) { 0f }
            val sliceH = (coloredTotalH * frac).coerceAtLeast(0f)
            val yTop = currY
            val yBottom = if (i == sliceCount - 1) cylBotY else (currY + sliceH).coerceAtMost(cylBotY)
            sliceBounds.add(yTop to yBottom)
            currY = yBottom
        }

        // Draw colored slices from bottom to top (Painter's algorithm: lower slices drawn first)
        for (i in (sliceCount - 1) downTo 0) {
            val (yTop, yBottom) = sliceBounds[i]
            if (yBottom <= yTop + 0.5f) continue

            val baseColor = palette[i % palette.size]
            val lateralBrush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to baseColor.darken(0.12f),
                    0.15f to baseColor.darken(0.04f),
                    0.50f to baseColor,
                    0.85f to baseColor.darken(0.04f),
                    1.00f to baseColor.darken(0.12f)
                ),
                startX = cx - rx,
                endX = cx + rx
            )

            val slicePath = Path().apply {
                moveTo(cx - rx, yTop)
                lineTo(cx - rx, yBottom)
                arcTo(
                    rect = Rect(cx - rx, yBottom - ry, cx + rx, yBottom + ry),
                    startAngleDegrees = 180f,
                    sweepAngleDegrees = -180f,
                    forceMoveTo = false
                )
                lineTo(cx + rx, yTop)
                arcTo(
                    rect = Rect(cx - rx, yTop - ry, cx + rx, yTop + ry),
                    startAngleDegrees = 0f,
                    sweepAngleDegrees = 180f,
                    forceMoveTo = false
                )
                close()
            }
            drawPath(path = slicePath, brush = lateralBrush)

            // Curved downward seam line between adjacent slices (matte soft seam)
            if (i > 0) {
                drawArc(
                    brush = Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.00f to Color.Black.copy(alpha = 0.18f),
                            0.50f to Color.Black.copy(alpha = 0.10f),
                            1.00f to Color.Black.copy(alpha = 0.20f)
                        ),
                        startX = cx - rx,
                        endX = cx + rx
                    ),
                    startAngle = 0f,
                    sweepAngle = 180f,
                    useCenter = false,
                    topLeft = Offset(cx - rx, yTop - ry),
                    size = Size(rx * 2f, ry * 2f),
                    style = Stroke(width = 0.75.dp.toPx())
                )
            }
        }

        // Draw top face oval of Slice 0 (natural matte top cap of the colored stack)
        val topColor = palette[0 % palette.size]
        drawOval(
            brush = Brush.verticalGradient(
                colors = listOf(
                    topColor.lighten(0.10f),
                    topColor
                ),
                startY = coloredTopY - ry,
                endY = coloredTopY + ry
            ),
            topLeft = Offset(cx - rx, coloredTopY - ry),
            size = Size(rx * 2f, ry * 2f)
        )
        drawOval(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to Color.Black.copy(alpha = 0.10f),
                    0.50f to Color.Black.copy(alpha = 0.04f),
                    1.00f to Color.Black.copy(alpha = 0.12f)
                ),
                startX = cx - rx,
                endX = cx + rx
            ),
            topLeft = Offset(cx - rx, coloredTopY - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = 0.75.dp.toPx())
        )

        // 3. Draw translucent empty glass chamber from cylTopY down to coloredTopY
        val glassPath = Path().apply {
            moveTo(cx - rx, cylTopY)
            lineTo(cx - rx, coloredTopY)
            arcTo(
                rect = Rect(cx - rx, coloredTopY - ry, cx + rx, coloredTopY + ry),
                startAngleDegrees = 180f,
                sweepAngleDegrees = -180f,
                forceMoveTo = false
            )
            lineTo(cx + rx, cylTopY)
            arcTo(
                rect = Rect(cx - rx, cylTopY - ry, cx + rx, cylTopY + ry),
                startAngleDegrees = 0f,
                sweepAngleDegrees = 180f,
                forceMoveTo = false
            )
            close()
        }
        val glassBrush = if (isDark) {
            Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to Color(0x38000000),
                    0.20f to Color(0x22000000),
                    0.50f to Color(0x1C000000),
                    0.80f to Color(0x22000000),
                    1.00f to Color(0x40000000)
                ),
                startX = cx - rx,
                endX = cx + rx
            )
        } else {
            Brush.horizontalGradient(
                colorStops = arrayOf(
                    0.00f to Color(0x24000000),
                    0.20f to Color(0x12000000),
                    0.50f to Color(0x0C000000),
                    0.80f to Color(0x12000000),
                    1.00f to Color(0x2C000000)
                ),
                startX = cx - rx,
                endX = cx + rx
            )
        }
        drawPath(path = glassPath, brush = glassBrush)

        // 4. Top rim oval at cylTopY
        val topRimStroke = if (isDark) Color(0x26FFFFFF) else Color(0x20000000)
        val topRimFill = if (isDark) Color(0x12FFFFFF) else Color(0x08000000)
        drawOval(
            color = topRimFill,
            topLeft = Offset(cx - rx, cylTopY - ry),
            size = Size(rx * 2f, ry * 2f)
        )
        drawOval(
            color = topRimStroke,
            topLeft = Offset(cx - rx, cylTopY - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = 0.75.dp.toPx())
        )

        // 5. Bottom rim arc at cylBotY
        drawArc(
            brush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.25f),
                    Color.Black.copy(alpha = 0.10f),
                    Color.Black.copy(alpha = 0.30f)
                ),
                startX = cx - rx,
                endX = cx + rx
            ),
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - rx, cylBotY - ry),
            size = Size(rx * 2f, ry * 2f),
            style = Stroke(width = 0.75.dp.toPx())
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun XiaomiStorageItemList(
    buckets: List<AnalysisBucket>,
    palette: List<Color>,
    total: Int,
    totalSizeBytes: Long,
    metric: AnalysisMetric,
    activeSliceIndex: Int? = null,
    onDotPositioned: ((Int, Offset) -> Unit)? = null,
    rootCoordinates: LayoutCoordinates? = null,
    onBucketClick: ((AnalysisBucket) -> Unit)? = null,
    onBucketLongClick: ((AnalysisBucket) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val dotCoordsMap = remember { mutableStateMapOf<Int, LayoutCoordinates>() }

    LaunchedEffect(rootCoordinates) {
        val root = rootCoordinates ?: return@LaunchedEffect
        if (!root.isAttached) return@LaunchedEffect
        dotCoordsMap.forEach { (idx, coords) ->
            if (coords.isAttached) {
                val local = root.localPositionOf(coords, Offset.Zero)
                onDotPositioned?.invoke(
                    idx,
                    Offset(local.x + coords.size.width / 2f, local.y + coords.size.height / 2f)
                )
            }
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        buckets.forEachIndexed { index, bucket ->
            val color = palette[index % palette.size]
            val valueStr = if (metric == AnalysisMetric.SIZE) {
                formatFileSize(bucket.sizeBytes)
            } else {
                stringResource(R.string.analytics_song_count_value, bucket.count)
            }
            val percent = if (metric == AnalysisMetric.SIZE && totalSizeBytes > 0L) {
                bucket.sizeBytes * 100f / totalSizeBytes.toFloat()
            } else if (total > 0) {
                bucket.count * 100f / total.toFloat()
            } else 0f

            val isSelected = activeSliceIndex == index
            val isDimmed = activeSliceIndex != null && !isSelected
            val itemAlpha by animateFloatAsState(
                targetValue = if (isDimmed) 0.35f else 1f,
                animationSpec = tween(durationMillis = 180),
                label = "StorageItemAlpha"
            )
            val dotSize by animateDpAsState(
                targetValue = if (isSelected) 8.5.dp else 7.dp,
                animationSpec = tween(durationMillis = 180),
                label = "StorageDotSize"
            )

            val displayLabel = if (bucket.label == "OTHER") {
                stringResource(R.string.settings_other)
            } else {
                bucket.label
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer { alpha = itemAlpha }
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (onBucketClick != null || onBucketLongClick != null) {
                            Modifier.combinedClickable(
                                onClick = { onBucketClick?.invoke(bucket) },
                                onLongClick = onBucketLongClick?.let { cb -> { cb(bucket) } }
                            )
                        } else Modifier
                    )
                    .padding(vertical = 7.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    // Line 1: Dot + Category Name + Right Arrow (matching Xiaomi settings)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(dotSize)
                                .clip(CircleShape)
                                .background(color)
                                .onGloballyPositioned { dotCoords ->
                                    dotCoordsMap[index] = dotCoords
                                    rootCoordinates?.let { root ->
                                        if (root.isAttached && dotCoords.isAttached) {
                                            val local = root.localPositionOf(dotCoords, Offset.Zero)
                                            onDotPositioned?.invoke(
                                                index,
                                                Offset(
                                                    x = local.x + dotCoords.size.width / 2f,
                                                    y = local.y + dotCoords.size.height / 2f
                                                )
                                            )
                                        }
                                    }
                                }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = displayLabel,
                            fontSize = 13.5.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isSelected) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = MiuixIcons.Basic.ArrowRight,
                            contentDescription = null,
                            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    // Line 2: Large Bold Value + percentage indented under label
                    Row(
                        modifier = Modifier.padding(start = 15.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = valueStr,
                            fontSize = 17.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MiuixTheme.colorScheme.onSurface
                        )
                        if (percent > 0f) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = formatPercent(percent),
                                fontSize = 12.5.sp,
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                modifier = Modifier.padding(bottom = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Color.lighten(fraction: Float): Color {
    val r = (red + (1f - red) * fraction).coerceIn(0f, 1f)
    val g = (green + (1f - green) * fraction).coerceIn(0f, 1f)
    val b = (blue + (1f - blue) * fraction).coerceIn(0f, 1f)
    return Color(r, g, b, alpha)
}

private fun Color.darken(fraction: Float): Color {
    val factor = (1f - fraction).coerceIn(0f, 1f)
    return Color(red * factor, green * factor, blue * factor, alpha)
}


@Composable
internal fun DonutChartCard(
    title: String,
    loadingText: String,
    buckets: List<AnalysisBucket>?,
    total: Int,
    totalSizeBytes: Long,
    palette: List<Color>,
    onBucketClick: ((AnalysisBucket) -> Unit)? = null,
    onBucketLongClick: ((AnalysisBucket) -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = analyticsWallpaperCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))
            when {
                buckets == null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    EllaLoadingIndicator()
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = loadingText,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                    )
                }
                total == 0 -> Text(
                    text = stringResource(R.string.analytics_no_songs),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
                else -> {
                    DonutChart(
                        buckets = buckets,
                        total = total,
                        colors = palette,
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(220.dp)
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    buckets.forEachIndexed { index, bucket ->
                        BucketLegendRow(
                            bucket = bucket,
                            total = total,
                            color = palette[index % palette.size],
                            onClick = onBucketClick?.let { callback -> { callback(bucket) } },
                            onLongClick = onBucketLongClick?.let { callback -> { callback(bucket) } }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    StatLine(
                        stringResource(R.string.analytics_all_label),
                        stringResource(R.string.analytics_all_summary, total, formatFileSize(totalSizeBytes))
                    )
                }
            }
        }
    }
}

@Composable
private fun DonutChart(
    buckets: List<AnalysisBucket>,
    total: Int,
    colors: List<Color>,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val strokeWidth = 34.dp.toPx()
        val diameter = size.minDimension - strokeWidth
        val topLeft = androidx.compose.ui.geometry.Offset(
            x = (size.width - diameter) / 2f,
            y = (size.height - diameter) / 2f
        )
        val chartSize = androidx.compose.ui.geometry.Size(diameter, diameter)

        drawArc(
            color = Color.White.copy(alpha = 0.10f),
            startAngle = -90f,
            sweepAngle = 360f,
            useCenter = false,
            topLeft = topLeft,
            size = chartSize,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
        )

        var startAngle = -90f
        buckets.forEachIndexed { index, bucket ->
            val sweep = (bucket.count.toFloat() / total.toFloat()) * 360f
            drawArc(
                color = colors[index % colors.size],
                startAngle = startAngle,
                sweepAngle = sweep,
                useCenter = false,
                topLeft = topLeft,
                size = chartSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            startAngle += sweep
        }
    }
}

@Composable
private fun BucketLegendRow(
    bucket: AnalysisBucket,
    total: Int,
    color: Color,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null
) {
    val percent = if (total > 0) bucket.count * 100f / total.toFloat() else 0f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(
                        onClick = { onClick?.invoke() },
                        onLongClick = onLongClick
                    )
                } else Modifier
            )
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = bucket.label,
            fontSize = 13.sp,
            color = MiuixTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = stringResource(
                R.string.analytics_bucket_summary,
                bucket.count,
                formatPercent(percent),
                formatFileSize(bucket.sizeBytes)
            ),
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
internal fun RankingCard(
    title: String,
    emptyText: String,
    stats: List<SongPlaybackStats>,
    libraryById: Map<Long, Song>,
    libraryByStatsKey: Map<String, Song>,
    mainViewModel: MainViewModel,
    valueText: (SongPlaybackStats) -> String
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = analyticsWallpaperCardColors()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (stats.isEmpty()) {
                Text(
                    text = emptyText,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary
                )
            } else {
                stats.forEachIndexed { index, stat ->
                    RankingRow(
                        index = index + 1,
                        stat = stat,
                        value = valueText(stat),
                        song = libraryById[stat.songId] ?: libraryByStatsKey[stat.analyticsStatsKey()],
                        mainViewModel = mainViewModel
                    )
                }
            }
        }
    }
}

@Composable
private fun RankingRow(
    index: Int,
    stat: SongPlaybackStats,
    value: String,
    song: Song?,
    mainViewModel: MainViewModel
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = index.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MiuixTheme.colorScheme.primary,
            modifier = Modifier.width(28.dp)
        )
        AnalyticsSongCover(
            song = song,
            mainViewModel = mainViewModel,
            modifier = Modifier.size(42.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song?.title ?: stat.title,
                fontSize = 14.sp,
                color = MiuixTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = song?.artist ?: stat.artist,
                fontSize = 12.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = value,
            fontSize = 12.sp,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary
        )
    }
}

@Composable
internal fun AnalyticsSongCover(
    song: Song?,
    mainViewModel: MainViewModel,
    modifier: Modifier = Modifier,
    coverSize: Int = 128,
    loadOriginal: Boolean = false
) {
    val originalModel by produceState<Any?>(
        initialValue = null,
        song?.id,
        song?.dateModified,
        song?.fileSize,
        loadOriginal
    ) {
        value = if (!loadOriginal) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching { song?.let(mainViewModel::getOriginalCoverModel) }.getOrNull()
            }
        }
    }
    if (loadOriginal && originalModel != null) {
        Box(
            modifier = modifier
                .clip(RoundedCornerShape(10.dp))
                .background(MiuixTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center
        ) {
            SafeCoverImage(
                model = originalModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sizePx = 3000,
                loadOriginal = true
            )
        }
        return
    }
    val coverBitmap by produceState<Bitmap?>(initialValue = null, song?.id, song?.dateModified, song?.fileSize) {
        value = withContext(Dispatchers.IO) {
            // Analytics 页同时渲染 40-50 个封面，不限流会并发解码大量 bitmap 触发 OOM，
            // 进而被系统杀进程重启（#133）。经 CoverLoadLimiter 排队后最多 2 个并发，其余
            // 排队等待，内存峰值大幅降低。
            runCatching {
                CoverLoadLimiter.run {
                    song?.let { s ->
                        if (coverSize > 128) mainViewModel.getAlbumCoverArtBitmap(s)
                        else mainViewModel.getCoverArtBitmap(s)
                    }
                }
            }.getOrNull()
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (coverBitmap != null && !coverBitmap!!.isRecycled) {
            Image(
                bitmap = coverBitmap!!.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
internal fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            fontWeight = FontWeight.Medium,
            color = MiuixTheme.colorScheme.onSurface
        )
    }
}

@Composable
internal fun analyticsWallpaperCardColors(alpha: Float = 0.42f) =
    CardDefaults.defaultColors(color = analyticsWallpaperCardColor(alpha))

@Composable
private fun analyticsWallpaperCardColor(alpha: Float): Color {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val wallpaperEnabled by settingsManager.appWallpaperEnabled.collectAsState(initial = false)
    val wallpaperUri by settingsManager.appWallpaperUri.collectAsState(initial = "")
    return if (wallpaperEnabled && wallpaperUri.isNotBlank()) {
        MiuixTheme.colorScheme.surfaceContainer.copy(alpha = alpha)
    } else {
        MiuixTheme.colorScheme.surfaceContainer
    }
}
