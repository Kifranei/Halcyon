package com.ella.music.ui.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ella.music.data.SettingsManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Apple-Music-style "flowing" cover background, ported from LunaBeat's dynamic background.
 *
 * The key difference from a naive Compose-Canvas flow effect (which recomputes full-screen gradients
 * and blur on the UI thread every frame) is that all pixel work happens **off the UI thread on a
 * tiny downsampled bitmap**, throttled to ~24 fps, and the UI thread merely blits + upscales the
 * result via [Image]. That is what keeps it both good-looking and low-power:
 *
 *  - the frame bitmap is rendered at ~1/16 (1/24 on high-dpi) of viewport size — pixel cost ≈ 1/256;
 *  - three slowly counter-rotating, over-saturated copies of the cover are blended, then box-blurred,
 *    producing an organic "aurora" flow with very cheap, long-period motion (70 s / 90 s / 120 s);
 *  - regeneration is quantized to [FRAME_INTERVAL_MS] buckets and runs on [Dispatchers.Default];
 *  - the animation clock freezes while the player surface is hidden (via [LocalPlayerSurfaceActive]).
 */
private const val FRAME_INTERVAL_MS = 42L

@Composable
internal fun AppleCoverFlowBackground(
    coverBitmap: Bitmap?,
    backgroundColor: Color,
    isDark: Boolean,
    isPlaying: Boolean,
    animate: Boolean = true,
    blur: Float = 60f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val densityDpi = context.resources.displayMetrics.densityDpi
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val speed by settingsManager.playerAppleFlowSpeed.collectAsState(
        initial = SettingsManager.DEFAULT_PLAYER_APPLE_FLOW_SPEED
    )

    // Pre-scale the cover once to a small source; it is drawn into an even smaller frame canvas, so a
    // large source only wastes memory bandwidth.
    val sourceBitmap = remember(coverBitmap) { coverBitmap?.scaledForFlowSource() }

    var viewportSize by remember { mutableStateOf(IntSize.Zero) }

    // Use the process-wide frame clock instead of an elapsed timer local to this Composable.
    // Browsing pages and every player/lyrics surface therefore render the same flow coordinates
    // when one replaces another, rather than restarting the animation at a different phase.
    val sharedClockMs = rememberThrottledFlowTimeMs(sourceBitmap, animate)
    val scaledTimeMs = scaledAppleFlowTimeMs(sharedClockMs, speed)
    val frameTimeMs = (scaledTimeMs / FRAME_INTERVAL_MS) * FRAME_INTERVAL_MS

    val normalizedBlur = blur.coerceIn(30f, 100f)
    val washPrimary = remember(backgroundColor, isDark) {
        if (isDark) {
            blendColors(backgroundColor, Color.Black, 0.28f).copy(alpha = 0.34f)
        } else {
            blendColors(backgroundColor, Color.White, 0.22f).copy(alpha = 0.26f)
        }
    }.toArgb()
    val washSecondary = remember(isDark) {
        if (isDark) Color.Black.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.14f)
    }.toArgb()

    val frameBitmap by produceState<Bitmap?>(
        initialValue = null,
        sourceBitmap,
        viewportSize,
        frameTimeMs,
        normalizedBlur,
        densityDpi,
        washPrimary,
        washSecondary
    ) {
        val cover = sourceBitmap
        val w = viewportSize.width
        val h = viewportSize.height
        if (cover == null || w <= 0 || h <= 0) {
            value = null
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            createAppleFlowFrameBitmap(cover, w, h, frameTimeMs, densityDpi, normalizedBlur, washPrimary, washSecondary)
        }
    }

    Box(modifier = modifier.background(backgroundColor)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clipToBounds()
                .onSizeChanged { viewportSize = it }
        ) {
            val ready = frameBitmap
            val source = sourceBitmap
            when {
                ready != null -> Image(
                    bitmap = ready.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )
                source != null -> Image(
                    bitmap = source.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur((normalizedBlur * 0.45f).dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.72f
                )
            }
        }
        // Soft scrim so foreground text stays legible.
        val scrim = if (isDark) Color.Black else Color.White
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            scrim.copy(alpha = if (isDark) 0.18f else 0.14f),
                            Color.Transparent,
                            scrim.copy(alpha = if (isDark) 0.30f else 0.22f)
                        )
                    )
                )
        )
    }
}

internal fun scaledAppleFlowTimeMs(elapsedMs: Long, speedTenths: Int): Long =
    elapsedMs.coerceAtLeast(0L) * speedTenths.coerceIn(5, 60) / 10L

/**
 * ~24 fps process-wide monotonic clock in milliseconds. Every composed flow background samples
 * the same clock origin, so switching surfaces preserves the current coordinates. It freezes
 * (returns the last value) when [animate] is false or the player surface is hidden.
 */
@Composable
private fun rememberThrottledFlowTimeMs(key: Any?, animate: Boolean): Long {
    val active = animate && LocalPlayerSurfaceActive.current
    var sharedClockMs by remember(key) { mutableLongStateOf(0L) }
    LaunchedEffect(key, active) {
        if (!active) return@LaunchedEffect
        while (true) {
            val now = withFrameNanos { it }
            sharedClockMs = now / 1_000_000L
            delay(FRAME_INTERVAL_MS)
        }
    }
    return sharedClockMs
}

internal fun Bitmap.scaledForFlowSource(maxDimension: Int = 256): Bitmap {
    val longest = max(width, height)
    if (longest <= maxDimension || longest <= 0) return this
    val scale = maxDimension.toFloat() / longest
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
        true
    )
}

private fun appleFlowDownsampleFactor(densityDpi: Int): Float = if (densityDpi >= 420) 24f else 16f

/**
 * Renders one flow frame into a tiny bitmap. All heavy work is confined to this ~1/16-scale canvas.
 */
internal fun createAppleFlowFrameBitmap(
    cover: Bitmap,
    viewportW: Int,
    viewportH: Int,
    timeMs: Long,
    densityDpi: Int,
    blur: Float,
    washPrimaryArgb: Int,
    washSecondaryArgb: Int
): Bitmap {
    val downsample = appleFlowDownsampleFactor(densityDpi)
    val w = ((viewportW * 1.3f) / downsample).roundToInt().coerceAtLeast(1)
    val h = ((viewportH * 1.3f) / downsample).roundToInt().coerceAtLeast(1)
    val frame = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(frame)

    // Oversize the drawn cover so rotation never exposes canvas edges; the excess is cropped later.
    val diagonal = (max(w, h) * 1.3f).roundToInt().coerceAtLeast(1).toFloat()
    val coverScale = diagonal / max(cover.height, 1)
    val translateX = -(diagonal - w) / 2f
    val translateY = -(diagonal - h) / 2f
    val rotatePivot = diagonal / 2f
    val centerX = w / 2f
    val centerY = h / 2f

    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFilterBitmap = true
        colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(2.5f) })
    }

    val rot = (timeMs % 70_000L) / 70_000f * 360f
    drawFlowLayer(
        canvas, cover, paint, coverScale, rotatePivot, translateX, translateY,
        w.toFloat(), h.toFloat(), centerX, centerY,
        rotation = (timeMs % 120_000L) / 120_000f * -360f, offsetXFactor = 0f, offsetYFactor = 0f, extraRotation = null
    )
    drawFlowLayer(
        canvas, cover, paint, coverScale, rotatePivot, translateX, translateY,
        w.toFloat(), h.toFloat(), centerX, centerY,
        rotation = (timeMs % 90_000L) / 90_000f * 360f, offsetXFactor = -0.95f, offsetYFactor = -0.7f, extraRotation = null
    )
    drawFlowLayer(
        canvas, cover, paint, coverScale, rotatePivot, translateX, translateY,
        w.toFloat(), h.toFloat(), centerX, centerY,
        rotation = rot, offsetXFactor = -0.5f, offsetYFactor = 0.7f, extraRotation = rot
    )

    canvas.drawColor(washPrimaryArgb)
    canvas.drawColor(washSecondaryArgb)

    val blurRadius = (((blur.coerceIn(30f, 100f) - 30f) / 70f) * 17f + 8f).roundToInt().coerceIn(8, 25)
    val blurred = blurBitmapFast(frame, blurRadius)

    // Center-crop the 1.3x overscan.
    val cropW = (blurred.width / 1.3f).roundToInt().coerceIn(1, blurred.width)
    val cropH = (blurred.height / 1.3f).roundToInt().coerceIn(1, blurred.height)
    val result = Bitmap.createBitmap(
        blurred,
        ((blurred.width - cropW) / 2).coerceAtLeast(0),
        ((blurred.height - cropH) / 2).coerceAtLeast(0),
        cropW,
        cropH
    )
    if (result !== blurred) blurred.recycle()
    if (frame !== blurred && frame !== result) frame.recycle()
    return result
}

private fun drawFlowLayer(
    canvas: Canvas,
    cover: Bitmap,
    paint: Paint,
    scale: Float,
    rotatePivot: Float,
    translateX: Float,
    translateY: Float,
    viewW: Float,
    viewH: Float,
    centerX: Float,
    centerY: Float,
    rotation: Float,
    offsetXFactor: Float,
    offsetYFactor: Float,
    extraRotation: Float?
) {
    val matrix = Matrix()
    matrix.setScale(scale, scale)
    matrix.postRotate(rotation, rotatePivot, rotatePivot)
    matrix.postTranslate(translateX, translateY)
    if (offsetXFactor != 0f || offsetYFactor != 0f) {
        matrix.postTranslate(viewW * offsetXFactor, viewH * offsetYFactor)
    }
    if (extraRotation != null) {
        matrix.postRotate(extraRotation, centerX, centerY)
    }
    canvas.drawBitmap(cover, matrix, paint)
}

/** Pure-CPU two-pass box blur over a small pixel array; cheap because the input frame is tiny. */
private fun blurBitmapFast(bitmap: Bitmap, radius: Int): Bitmap {
    if (radius <= 0) return bitmap
    val r = radius.coerceIn(1, 25)
    val width = bitmap.width
    val height = bitmap.height
    if (width <= 1 || height <= 1) return bitmap

    val pixels = IntArray(width * height)
    bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
    val window = r * 2 + 1

    // Horizontal pass.
    val temp = IntArray(width * height)
    for (y in 0 until height) {
        val rowStart = y * width
        var a = 0; var red = 0; var green = 0; var blue = 0
        for (k in -r..r) {
            val p = pixels[rowStart + k.coerceIn(0, width - 1)]
            a += (p ushr 24) and 0xff
            red += (p ushr 16) and 0xff
            green += (p ushr 8) and 0xff
            blue += p and 0xff
        }
        for (x in 0 until width) {
            temp[rowStart + x] = ((a / window) shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
            val outIdx = rowStart + (x - r).coerceIn(0, width - 1)
            val inIdx = rowStart + (x + r + 1).coerceIn(0, width - 1)
            val pOut = pixels[outIdx]
            val pIn = pixels[inIdx]
            a += ((pIn ushr 24) and 0xff) - ((pOut ushr 24) and 0xff)
            red += ((pIn ushr 16) and 0xff) - ((pOut ushr 16) and 0xff)
            green += ((pIn ushr 8) and 0xff) - ((pOut ushr 8) and 0xff)
            blue += (pIn and 0xff) - (pOut and 0xff)
        }
    }

    // Vertical pass.
    for (x in 0 until width) {
        var a = 0; var red = 0; var green = 0; var blue = 0
        for (k in -r..r) {
            val p = temp[k.coerceIn(0, height - 1) * width + x]
            a += (p ushr 24) and 0xff
            red += (p ushr 16) and 0xff
            green += (p ushr 8) and 0xff
            blue += p and 0xff
        }
        for (y in 0 until height) {
            pixels[y * width + x] = ((a / window) shl 24) or ((red / window) shl 16) or ((green / window) shl 8) or (blue / window)
            val outIdx = (y - r).coerceIn(0, height - 1) * width + x
            val inIdx = (y + r + 1).coerceIn(0, height - 1) * width + x
            val pOut = temp[outIdx]
            val pIn = temp[inIdx]
            a += ((pIn ushr 24) and 0xff) - ((pOut ushr 24) and 0xff)
            red += ((pIn ushr 16) and 0xff) - ((pOut ushr 16) and 0xff)
            green += ((pIn ushr 8) and 0xff) - ((pOut ushr 8) and 0xff)
            blue += (pIn and 0xff) - (pOut and 0xff)
        }
    }

    val result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    result.setPixels(pixels, 0, width, 0, 0, width, height)
    return result
}

private fun blendColors(a: Color, b: Color, t: Float): Color {
    val s = t.coerceIn(0f, 1f)
    return Color(
        red = a.red + (b.red - a.red) * s,
        green = a.green + (b.green - a.green) * s,
        blue = a.blue + (b.blue - a.blue) * s,
        alpha = a.alpha + (b.alpha - a.alpha) * s
    )
}
