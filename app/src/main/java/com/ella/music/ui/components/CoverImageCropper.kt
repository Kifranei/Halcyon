package com.ella.music.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import com.ella.music.R
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownItem
import top.yukonga.miuix.kmp.preference.WindowSpinnerPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

internal class CoverImageCropperState internal constructor(
    internal val originalBitmap: Bitmap,
    internal val displayBitmap: Bitmap
) {
    internal var imageBounds by mutableStateOf(Rect.Zero)
    internal var cropRect by mutableStateOf(Rect.Zero)
    var aspectRatio by mutableStateOf<Float?>(1f)

    fun crop(): Bitmap = cropCoverBitmap(originalBitmap, displayBitmap, imageBounds, cropRect)
}

@Composable
internal fun rememberCoverImageCropperState(bitmap: Bitmap): CoverImageCropperState {
    return remember(bitmap) {
        val display = downscaleForDisplay(bitmap)
        CoverImageCropperState(originalBitmap = bitmap, displayBitmap = display)
    }
}

@Composable
internal fun CoverImageCropper(
    state: CoverImageCropperState,
    modifier: Modifier = Modifier
) {
    val bitmap = state.displayBitmap
    var viewSize by remember { mutableStateOf(Size.Zero) }
    val density = LocalDensity.current
    val touchTolerance = with(density) { 24.dp.toPx() }
    val minCropSize = with(density) { 60.dp.toPx() }
    val imageBitmap: ImageBitmap = remember(bitmap) { bitmap.asImageBitmap() }
    val bitmapAspectRatio = remember(bitmap) {
        bitmap.width.toFloat() / bitmap.height.toFloat().coerceAtLeast(1f)
    }

    LaunchedEffect(viewSize, bitmap) {
        if (viewSize.width == 0f || viewSize.height == 0f) return@LaunchedEffect
        val scale = min(viewSize.width / bitmap.width, viewSize.height / bitmap.height)
        val imageWidth = bitmap.width * scale
        val imageHeight = bitmap.height * scale
        val left = (viewSize.width - imageWidth) / 2f
        val top = (viewSize.height - imageHeight) / 2f
        state.imageBounds = Rect(left, top, left + imageWidth, top + imageHeight)
        state.cropRect = initialSquareCropRect(state.imageBounds, state.aspectRatio)
    }

    LaunchedEffect(state.aspectRatio) {
        if (state.cropRect.width <= 0f || state.imageBounds.width <= 0f) return@LaunchedEffect
        val ratio = state.aspectRatio ?: return@LaunchedEffect
        state.cropRect = constrainedRectForRatio(state.cropRect, state.imageBounds, ratio)
    }

    val cropRectRef = rememberUpdatedState(state.cropRect)
    val imageBoundsRef = rememberUpdatedState(state.imageBounds)
    val aspectRatioRef = rememberUpdatedState(state.aspectRatio)
    var activeHandle by remember { mutableStateOf(CropHandle.NONE) }
    val freeRatio = stringResource(R.string.song_more_metadata_cover_ratio_free)
    val ratioOptions = remember(freeRatio) {
        listOf(
            1f to "1:1",
            null to freeRatio,
            4f / 3f to "4:3",
            16f / 9f to "16:9"
        )
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.padding(bottom = 8.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer)
        ) {
            WindowSpinnerPreference(
                items = ratioOptions.map { DropdownItem(title = it.second) },
                selectedIndex = ratioOptions.indexOfFirst { it.first == state.aspectRatio }.coerceAtLeast(0),
                title = stringResource(R.string.song_more_metadata_cover_ratio),
                onSelectedIndexChange = { index ->
                    state.aspectRatio = ratioOptions[index].first
                }
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(bitmapAspectRatio.coerceIn(0.5f, 2f))
                .clipToBounds()
                .background(Color.Black)
                .onGloballyPositioned { viewSize = it.size.toSize() }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            activeHandle = hitCropHandle(offset, cropRectRef.value, touchTolerance)
                        },
                        onDragEnd = { activeHandle = CropHandle.NONE },
                        onDragCancel = { activeHandle = CropHandle.NONE },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            state.cropRect = moveCropRect(
                                handle = activeHandle,
                                oldRect = cropRectRef.value,
                                dragAmount = dragAmount,
                                bounds = imageBoundsRef.value,
                                minSize = minCropSize,
                                aspectRatio = aspectRatioRef.value
                            )
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val imageBounds = state.imageBounds
                val cropRect = state.cropRect
                if (imageBounds.isEmpty) return@Canvas
                drawImage(
                    image = imageBitmap,
                    srcSize = IntSize(bitmap.width, bitmap.height),
                    dstOffset = IntOffset(imageBounds.left.toInt(), imageBounds.top.toInt()),
                    dstSize = IntSize(imageBounds.width.toInt(), imageBounds.height.toInt())
                )
                drawCropOverlay(imageBounds, cropRect)
            }
        }
    }
}

internal enum class CropHandle {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT,
    TOP, BOTTOM, LEFT, RIGHT, CENTER, NONE
}

internal fun cropCoverBitmap(
    originalBitmap: Bitmap,
    displayBitmap: Bitmap,
    imageDisplayBounds: Rect,
    cropRect: Rect
): Bitmap {
    val displayScaleX = displayBitmap.width / imageDisplayBounds.width.coerceAtLeast(1f)
    val displayScaleY = displayBitmap.height / imageDisplayBounds.height.coerceAtLeast(1f)
    val originalScaleX = originalBitmap.width.toFloat() / displayBitmap.width.toFloat()
    val originalScaleY = originalBitmap.height.toFloat() / displayBitmap.height.toFloat()
    val totalScaleX = displayScaleX * originalScaleX
    val totalScaleY = displayScaleY * originalScaleY
    val cropX = ((cropRect.left - imageDisplayBounds.left) * totalScaleX).toInt()
    val cropY = ((cropRect.top - imageDisplayBounds.top) * totalScaleY).toInt()
    val cropWidth = (cropRect.width * totalScaleX).toInt()
    val cropHeight = (cropRect.height * totalScaleY).toInt()
    val safeX = max(0, cropX)
    val safeY = max(0, cropY)
    val safeWidth = min(originalBitmap.width - safeX, cropWidth).coerceAtLeast(1)
    val safeHeight = min(originalBitmap.height - safeY, cropHeight).coerceAtLeast(1)
    val software = if (originalBitmap.config == Bitmap.Config.HARDWARE) {
        originalBitmap.copy(Bitmap.Config.ARGB_8888, false) ?: originalBitmap
    } else {
        originalBitmap
    }
    return Bitmap.createBitmap(software, safeX, safeY, safeWidth, safeHeight)
}

internal fun decodeBitmapForCoverCrop(context: Context, uri: Uri): Bitmap? {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    return decodeCoverBytes(bytes)
}

internal fun decodeCoverBytes(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    val maxSide = 4096
    while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply {
        inSampleSize = sample
        inPreferredConfig = Bitmap.Config.ARGB_8888
    }
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
    return applyExifOrientation(decoded, bytes)
}

internal fun decodeCoverSource(context: Context, source: Any?): Bitmap? {
    return when (source) {
        null -> null
        is Bitmap -> source.takeUnless { it.isRecycled }
        is ByteArray -> decodeCoverBytes(source)
        is Uri -> decodeBitmapForCoverCrop(context, source)
        is File -> runCatching { source.takeIf { it.isFile }?.readBytes()?.let(::decodeCoverBytes) }.getOrNull()
        is String -> {
            val asUri = runCatching { Uri.parse(source) }.getOrNull()
            if (asUri != null && !asUri.scheme.isNullOrBlank() && asUri.scheme != "file") {
                decodeBitmapForCoverCrop(context, asUri)
            } else {
                runCatching { decodeCoverBytes(File(source.removePrefix("file://")).readBytes()) }.getOrNull()
            }
        }
        else -> null
    }
}

internal fun Bitmap.toEmbeddedCoverJpeg(maxSide: Int = 3000, quality: Int = 92): ByteArray {
    val longest = max(width, height)
    val scaled = if (longest > maxSide) {
        val scale = maxSide.toFloat() / longest.toFloat()
        Bitmap.createScaledBitmap(
            this,
            (width * scale).toInt().coerceAtLeast(1),
            (height * scale).toInt().coerceAtLeast(1),
            true
        )
    } else {
        this
    }
    val output = ByteArrayOutputStream()
    scaled.compress(Bitmap.CompressFormat.JPEG, quality, output)
    if (scaled !== this) scaled.recycle()
    return output.toByteArray()
}

private fun applyExifOrientation(bitmap: Bitmap, bytes: ByteArray): Bitmap {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes))
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
    val degrees = when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> 90f
        ExifInterface.ORIENTATION_ROTATE_180 -> 180f
        ExifInterface.ORIENTATION_ROTATE_270 -> 270f
        else -> 0f
    }
    if (degrees == 0f) return bitmap
    val matrix = Matrix().apply { postRotate(degrees) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

private fun downscaleForDisplay(bitmap: Bitmap, maxPixels: Long = 16_000_000L): Bitmap {
    val currentPixels = bitmap.width.toLong() * bitmap.height.toLong()
    if (currentPixels <= maxPixels) return bitmap
    val scale = sqrt(maxPixels.toFloat() / currentPixels.toFloat())
    return Bitmap.createScaledBitmap(
        bitmap,
        (bitmap.width * scale).toInt().coerceAtLeast(1),
        (bitmap.height * scale).toInt().coerceAtLeast(1),
        true
    )
}

internal fun initialSquareCropRect(imageBounds: Rect, aspectRatio: Float?): Rect {
    val initSize = min(imageBounds.width, imageBounds.height)
    val ratio = aspectRatio ?: 1f
    val (width, height) = if (ratio >= 1f) {
        val width = initSize
        width to width / ratio
    } else {
        val height = initSize
        (height * ratio) to height
    }
    val left = imageBounds.left + (imageBounds.width - width) / 2f
    val top = imageBounds.top + (imageBounds.height - height) / 2f
    return Rect(left, top, left + width, top + height)
}

private fun constrainedRectForRatio(current: Rect, bounds: Rect, ratio: Float): Rect {
    val center = current.center
    val candidateW = min(current.width, bounds.width)
    val candidateH = candidateW / ratio
    val (width, height) = if (candidateH <= bounds.height) {
        candidateW to candidateH
    } else {
        val height = min(current.height, bounds.height)
        (height * ratio) to height
    }
    var left = center.x - width / 2f
    var top = center.y - height / 2f
    var right = center.x + width / 2f
    var bottom = center.y + height / 2f
    if (left < bounds.left) {
        left = bounds.left
        right = left + width
    }
    if (right > bounds.right) {
        right = bounds.right
        left = right - width
    }
    if (top < bounds.top) {
        top = bounds.top
        bottom = top + height
    }
    if (bottom > bounds.bottom) {
        bottom = bounds.bottom
        top = bottom - height
    }
    return Rect(left, top, right, bottom)
}

internal fun hitCropHandle(touch: Offset, rect: Rect, tolerance: Float): CropHandle {
    val leftHit = touch.x in (rect.left - tolerance)..(rect.left + tolerance)
    val rightHit = touch.x in (rect.right - tolerance)..(rect.right + tolerance)
    val topHit = touch.y in (rect.top - tolerance)..(rect.top + tolerance)
    val bottomHit = touch.y in (rect.bottom - tolerance)..(rect.bottom + tolerance)
    return when {
        leftHit && topHit -> CropHandle.TOP_LEFT
        rightHit && topHit -> CropHandle.TOP_RIGHT
        leftHit && bottomHit -> CropHandle.BOTTOM_LEFT
        rightHit && bottomHit -> CropHandle.BOTTOM_RIGHT
        leftHit && touch.y in rect.top..rect.bottom -> CropHandle.LEFT
        rightHit && touch.y in rect.top..rect.bottom -> CropHandle.RIGHT
        topHit && touch.x in rect.left..rect.right -> CropHandle.TOP
        bottomHit && touch.x in rect.left..rect.right -> CropHandle.BOTTOM
        touch.x in rect.left..rect.right && touch.y in rect.top..rect.bottom -> CropHandle.CENTER
        else -> CropHandle.NONE
    }
}

internal fun moveCropRect(
    handle: CropHandle,
    oldRect: Rect,
    dragAmount: Offset,
    bounds: Rect,
    minSize: Float,
    aspectRatio: Float?
): Rect {
    if (handle == CropHandle.NONE) return oldRect
    val safeMinW = min(minSize, bounds.width)
    val safeMinH = min(minSize, bounds.height)
    if (handle == CropHandle.CENTER) {
        val maxDx = (bounds.right - oldRect.right).coerceAtLeast(0f)
        val minDx = (bounds.left - oldRect.left).coerceAtMost(0f)
        val maxDy = (bounds.bottom - oldRect.bottom).coerceAtLeast(0f)
        val minDy = (bounds.top - oldRect.top).coerceAtMost(0f)
        return oldRect.translate(
            dragAmount.x.coerceIn(minDx, maxDx),
            dragAmount.y.coerceIn(minDy, maxDy)
        )
    }

    var left = oldRect.left
    var top = oldRect.top
    var right = oldRect.right
    var bottom = oldRect.bottom
    when (handle) {
        CropHandle.TOP_LEFT -> { left += dragAmount.x; top += dragAmount.y }
        CropHandle.TOP_RIGHT -> { right += dragAmount.x; top += dragAmount.y }
        CropHandle.BOTTOM_LEFT -> { left += dragAmount.x; bottom += dragAmount.y }
        CropHandle.BOTTOM_RIGHT -> { right += dragAmount.x; bottom += dragAmount.y }
        CropHandle.LEFT -> left += dragAmount.x
        CropHandle.RIGHT -> right += dragAmount.x
        CropHandle.TOP -> top += dragAmount.y
        CropHandle.BOTTOM -> bottom += dragAmount.y
        CropHandle.CENTER, CropHandle.NONE -> Unit
    }
    left = left.coerceIn(bounds.left, maxOf(bounds.left, right - safeMinW))
    right = right.coerceIn(minOf(bounds.right, left + safeMinW), bounds.right)
    top = top.coerceIn(bounds.top, maxOf(bounds.top, bottom - safeMinH))
    bottom = bottom.coerceIn(minOf(bounds.bottom, top + safeMinH), bounds.bottom)
    if (aspectRatio == null) return Rect(left, top, right, bottom)

    var width = right - left
    var height = bottom - top
    val useWidthAsBasis = when (handle) {
        CropHandle.LEFT, CropHandle.RIGHT -> true
        CropHandle.TOP, CropHandle.BOTTOM -> false
        else -> abs(dragAmount.x) >= abs(dragAmount.y)
    }
    if (useWidthAsBasis) {
        height = width / aspectRatio
        if (height > bounds.height) {
            height = bounds.height
            width = height * aspectRatio
        }
        if (height < safeMinH) {
            height = safeMinH
            width = height * aspectRatio
        }
    } else {
        width = height * aspectRatio
        if (width > bounds.width) {
            width = bounds.width
            height = width / aspectRatio
        }
        if (width < safeMinW) {
            width = safeMinW
            height = width / aspectRatio
        }
    }
    when (handle) {
        CropHandle.TOP_LEFT -> { left = right - width; top = bottom - height }
        CropHandle.TOP_RIGHT -> { right = left + width; top = bottom - height }
        CropHandle.BOTTOM_LEFT -> { left = right - width; bottom = top + height }
        CropHandle.BOTTOM_RIGHT -> { right = left + width; bottom = top + height }
        CropHandle.LEFT -> {
            left = right - width
            val cy = (top + bottom) / 2f
            top = cy - height / 2f
            bottom = cy + height / 2f
        }
        CropHandle.RIGHT -> {
            right = left + width
            val cy = (top + bottom) / 2f
            top = cy - height / 2f
            bottom = cy + height / 2f
        }
        CropHandle.TOP -> {
            top = bottom - height
            val cx = (left + right) / 2f
            left = cx - width / 2f
            right = cx + width / 2f
        }
        CropHandle.BOTTOM -> {
            bottom = top + height
            val cx = (left + right) / 2f
            left = cx - width / 2f
            right = cx + width / 2f
        }
        CropHandle.CENTER, CropHandle.NONE -> Unit
    }
    if (left < bounds.left) {
        val offset = bounds.left - left
        left += offset
        right += offset
    }
    if (right > bounds.right) {
        val offset = right - bounds.right
        left -= offset
        right -= offset
    }
    if (top < bounds.top) {
        val offset = bounds.top - top
        top += offset
        bottom += offset
    }
    if (bottom > bounds.bottom) {
        val offset = bottom - bounds.bottom
        top -= offset
        bottom -= offset
    }
    return Rect(left, top, right, bottom)
}

private fun DrawScope.drawCropOverlay(imageBounds: Rect, cropRect: Rect) {
    val overlay = Color.Black.copy(alpha = 0.6f)
    if (cropRect.top > imageBounds.top) {
        drawRect(overlay, Offset(imageBounds.left, imageBounds.top), Size(imageBounds.width, cropRect.top - imageBounds.top))
    }
    if (cropRect.bottom < imageBounds.bottom) {
        drawRect(overlay, Offset(imageBounds.left, cropRect.bottom), Size(imageBounds.width, imageBounds.bottom - cropRect.bottom))
    }
    if (cropRect.left > imageBounds.left) {
        drawRect(overlay, Offset(imageBounds.left, cropRect.top), Size(cropRect.left - imageBounds.left, cropRect.height))
    }
    if (cropRect.right < imageBounds.right) {
        drawRect(overlay, Offset(cropRect.right, cropRect.top), Size(imageBounds.right - cropRect.right, cropRect.height))
    }
    drawRect(Color.White, cropRect.topLeft, cropRect.size, style = Stroke(2.dp.toPx()))
    val thirdW = cropRect.width / 3f
    val thirdH = cropRect.height / 3f
    val gridColor = Color.White.copy(alpha = 0.4f)
    val gridStroke = 1.dp.toPx()
    for (i in 1..2) {
        val x = cropRect.left + thirdW * i
        drawLine(gridColor, Offset(x, cropRect.top), Offset(x, cropRect.bottom), gridStroke)
        val y = cropRect.top + thirdH * i
        drawLine(gridColor, Offset(cropRect.left, y), Offset(cropRect.right, y), gridStroke)
    }
    val length = 24.dp.toPx()
    val cornerStroke = 4.dp.toPx()
    drawLine(Color.White, Offset(cropRect.left, cropRect.top), Offset(cropRect.left + length, cropRect.top), cornerStroke)
    drawLine(Color.White, Offset(cropRect.left, cropRect.top), Offset(cropRect.left, cropRect.top + length), cornerStroke)
    drawLine(Color.White, Offset(cropRect.right, cropRect.top), Offset(cropRect.right - length, cropRect.top), cornerStroke)
    drawLine(Color.White, Offset(cropRect.right, cropRect.top), Offset(cropRect.right, cropRect.top + length), cornerStroke)
    drawLine(Color.White, Offset(cropRect.left, cropRect.bottom), Offset(cropRect.left + length, cropRect.bottom), cornerStroke)
    drawLine(Color.White, Offset(cropRect.left, cropRect.bottom), Offset(cropRect.left, cropRect.bottom - length), cornerStroke)
    drawLine(Color.White, Offset(cropRect.right, cropRect.bottom), Offset(cropRect.right - length, cropRect.bottom), cornerStroke)
    drawLine(Color.White, Offset(cropRect.right, cropRect.bottom), Offset(cropRect.right, cropRect.bottom - length), cornerStroke)
}
