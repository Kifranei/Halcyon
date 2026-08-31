package com.ella.music.ui.player

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song

private const val BEAUTIFUL_LYRICS_NEUTRAL_FALLBACK_SHARE = 0.90f
private const val BEAUTIFUL_LYRICS_NEUTRAL_MAX_CHANNEL_SPREAD = 32

@Composable
internal fun FluidLyricBackground(
    palette: PlayerPalette,
    positionMs: Long,
    isPlaying: Boolean,
    flowEffectMode: Int = SettingsManager.PLAYER_FLOW_EFFECT_DARK,
    animate: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Freeze the drift while the player surface is hidden but still resident.
    val animateActive = animate && LocalPlayerSurfaceActive.current
    val drift = if (animateActive) {
        val transition = rememberInfiniteTransition(label = "fluid_lyric_background")
        val value by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 18_000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "fluid_lyric_background_drift"
        )
        value
    } else {
        0.36f
    }
    val pulse = if (animateActive && isPlaying) {
        0.5f + 0.5f * kotlin.math.sin(positionMs / 900.0).toFloat()
    } else {
        0.28f
    }

    Canvas(modifier = modifier.background(palette.middle)) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    palette.top.copy(alpha = 0.98f),
                    palette.middle.copy(alpha = 0.98f),
                    palette.bottom.copy(alpha = 1f)
                )
            )
        )
        val w = size.width
        val h = size.height
        val t = drift * kotlin.math.PI.toFloat() * 2f
        val centers = listOf(
            Offset((0.18f + 0.04f * kotlin.math.sin(t)) * w, (0.24f + 0.08f * kotlin.math.cos(t * 0.7f)) * h),
            Offset((0.82f + 0.05f * kotlin.math.cos(t * 0.8f)) * w, (0.20f + 0.06f * kotlin.math.sin(t)) * h),
            Offset((0.48f + 0.08f * kotlin.math.sin(t * 0.55f)) * w, (0.62f + 0.05f * kotlin.math.cos(t * 0.9f)) * h),
            Offset((0.72f + 0.06f * kotlin.math.sin(t * 0.95f)) * w, (0.86f + 0.04f * kotlin.math.cos(t * 0.6f)) * h)
        )
        val colors = listOf(
            palette.accent.copy(alpha = 0.22f + pulse * 0.05f),
            Color.White.copy(alpha = 0.10f),
            palette.top.copy(alpha = 0.20f),
            Color.Black.copy(alpha = 0.20f)
        )
        centers.forEachIndexed { index, center ->
            val radius = minOf(w, h) * (0.34f + index * 0.055f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(colors[index], Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(
                    Color.Black.copy(alpha = 0.10f),
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.42f)
                )
            )
        )
    }
}

@Composable
internal fun BeautifulLyricsDynamicBackground(
    palette: PlayerPalette,
    coverBitmap: Bitmap? = null,
    positionMs: Long,
    isPlaying: Boolean,
    animate: Boolean = true,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val speed by settingsManager.playerBeautifulLyricsSpeed.collectAsState(initial = 25)
    val blurPx by settingsManager.playerBeautifulLyricsBlur.collectAsState(initial = 32)
    val brightness by settingsManager.playerBeautifulLyricsBrightness.collectAsState(initial = 70)
    val durationMs = (960_000 / speed.coerceIn(5, 60)).coerceIn(16_000, 192_000)
    val activeDrift = rememberSharedFlowProgress(
        durationMillis = durationMs,
        animate = animate,
        fallback = 0.42f,
        // Output is heavily blurred, so ~15 fps drift is indistinguishable from full refresh rate
        // while roughly halving the per-frame full-screen blur cost.
        frameIntervalMs = 64L
    )
    val pulse = if (isPlaying) {
        0.5f + 0.5f * kotlin.math.sin(positionMs / 760.0).toFloat()
    } else {
        0.36f
    }
    
    val brightnessAlpha = (brightness.coerceIn(30, 120) / 100f).coerceIn(0.3f, 1.2f)
    val sampledColors = remember(coverBitmap, palette) { beautifulLyricsSampleColors(coverBitmap, palette) }

    Canvas(
        modifier = modifier
            .background(palette.middle)
            .blur(blurPx.coerceIn(0, 80).dp)
            .graphicsLayer {
                scaleX = 1.08f
                scaleY = 1.08f
            }
    ) {
        val w = size.width
        val h = size.height

        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    sampledColors[0].copy(alpha = 1f),
                    sampledColors[1].copy(alpha = (0.92f * brightnessAlpha).coerceIn(0.55f, 1f)),
                    sampledColors[2].copy(alpha = 1f)
                ),
                start = Offset(0f, 0f),
                end = Offset(w, h)
            )
        )

        val centers = beautifulLyricsBlobCenters(activeDrift)
        val blobs = listOf(
            Triple(
                sampledColors[3].copy(alpha = (0.70f + pulse * 0.18f) * brightnessAlpha),
                Offset(centers[0].x * w, centers[0].y * h),
                0.70f
            ),
            Triple(
                sampledColors[4].copy(alpha = 0.66f * brightnessAlpha),
                Offset(centers[1].x * w, centers[1].y * h),
                0.62f
            ),
            Triple(
                sampledColors[5].copy(alpha = 0.58f * brightnessAlpha),
                Offset(centers[2].x * w, centers[2].y * h),
                0.58f
            ),
            Triple(
                sampledColors[6].copy(alpha = 0.72f * brightnessAlpha),
                Offset(centers[3].x * w, centers[3].y * h),
                0.72f
            )
        )
        blobs.forEach { (color, center, radiusFactor) ->
            val radius = maxOf(w, h) * radiusFactor
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color, Color.Transparent),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        drawRect(
            brush = Brush.verticalGradient(
                colorStops = arrayOf(
                    0.0f to Color.Transparent,
                    0.68f to Color.Transparent,
                    1.0f to Color.Black.copy(alpha = 0.14f)
                )
            )
        )
    }
}

/** Closed paths keep the last frame of a drift cycle continuous with the first one. */
internal fun beautifulLyricsBlobCenters(progress: Float): List<Offset> {
    val t = progress.coerceIn(0f, 1f) * kotlin.math.PI.toFloat() * 2f
    return listOf(
        Offset(0.12f + 0.42f * kotlin.math.sin(t), 0.18f + 0.34f * kotlin.math.cos(t)),
        Offset(0.86f + 0.36f * kotlin.math.cos(t * 2f + 0.35f), 0.26f + 0.36f * kotlin.math.sin(t + 0.8f)),
        Offset(0.44f + 0.46f * kotlin.math.sin(t * 2f + 1.4f), 0.58f + 0.32f * kotlin.math.cos(t * 3f + 0.25f)),
        Offset(0.72f + 0.42f * kotlin.math.cos(t + 2.1f), 0.84f + 0.28f * kotlin.math.sin(t * 2f + 1.1f))
    )
}

private fun Color.beautifulLyricsVibrant(): Color {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(r, g, b, hsv)
    if (hsv[1] < 0.08f) return this
    hsv[1] = (hsv[1] * 1.55f).coerceIn(0.42f, 1f)
    hsv[2] = (hsv[2] * 1.18f).coerceIn(0.50f, 1f)
    return Color(AndroidColor.HSVToColor(hsv))
}

private fun beautifulLyricsSampleColors(bitmap: Bitmap?, palette: PlayerPalette): List<Color> {
    if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) {
        return listOf(palette.top, palette.accent, palette.bottom, palette.accent, palette.top, Color.White, palette.bottom)
    }
    beautifulLyricsNeutralPalette(bitmap, palette)?.let { return it }
    val points = listOf(
        0.18f to 0.18f,
        0.78f to 0.20f,
        0.52f to 0.50f,
        0.22f to 0.78f,
        0.82f to 0.72f,
        0.50f to 0.28f,
        0.64f to 0.88f
    )
    return points.map { (fx, fy) ->
        val x = (bitmap.width * fx).toInt().coerceIn(0, bitmap.width - 1)
        val y = (bitmap.height * fy).toInt().coerceIn(0, bitmap.height - 1)
        Color(bitmap.getPixel(x, y)).beautifulLyricsVibrant()
    }
}

private fun beautifulLyricsNeutralPalette(bitmap: Bitmap, palette: PlayerPalette): List<Color>? {
    val sampleStep = (minOf(bitmap.width, bitmap.height) / 42).coerceAtLeast(1)
    val hsv = FloatArray(3)
    var total = 0
    var strictNeutral = 0
    var brightNeutral = 0
    var midNeutral = 0
    var darkNeutral = 0
    var rSum = 0L
    var gSum = 0L
    var bSum = 0L

    var y = 0
    while (y < bitmap.height) {
        var x = 0
        while (x < bitmap.width) {
            val pixel = bitmap.getPixel(x, y)
            val alpha = AndroidColor.alpha(pixel)
            if (alpha > 24) {
                val r = AndroidColor.red(pixel)
                val g = AndroidColor.green(pixel)
                val b = AndroidColor.blue(pixel)
                AndroidColor.RGBToHSV(r, g, b, hsv)
                total++
                rSum += r.toLong()
                gSum += g.toLong()
                bSum += b.toLong()
                val channelSpread = maxOf(r, g, b) - minOf(r, g, b)
                val isStrictNeutral =
                    hsv[1] < 0.18f && channelSpread <= BEAUTIFUL_LYRICS_NEUTRAL_MAX_CHANNEL_SPREAD
                if (isStrictNeutral) {
                    strictNeutral++
                    when {
                        hsv[2] > 0.72f -> brightNeutral++
                        hsv[2] < 0.28f -> darkNeutral++
                        else -> midNeutral++
                    }
                }
            }
            x += sampleStep
        }
        y += sampleStep
    }
    if (total == 0) return null
    val strictNeutralShare = strictNeutral.toFloat() / total.toFloat()
    if (strictNeutralShare < BEAUTIFUL_LYRICS_NEUTRAL_FALLBACK_SHARE) return null

    val avgR = (rSum / total).toInt().coerceIn(0, 255)
    val avgG = (gSum / total).toInt().coerceIn(0, 255)
    val avgB = (bSum / total).toInt().coerceIn(0, 255)
    val luma = (avgR * 0.299f + avgG * 0.587f + avgB * 0.114f).toInt().coerceIn(0, 255)
    val base = Color(luma, luma, luma)
    val soft = when {
        brightNeutral > maxOf(midNeutral, darkNeutral) -> Color(210, 210, 210)
        darkNeutral > maxOf(brightNeutral, midNeutral) -> Color(42, 42, 42)
        luma >= 160 -> Color(210, 210, 210)
        luma <= 96 -> Color(42, 42, 42)
        else -> Color(126, 126, 126)
    }
    val accentGray = when {
        brightNeutral > maxOf(midNeutral, darkNeutral) -> Color(150, 150, 150)
        darkNeutral > maxOf(brightNeutral, midNeutral) -> Color(92, 92, 92)
        else -> Color(108, 108, 108)
    }
    return listOf(
        soft.lighten(0.10f),
        base,
        soft.darken(0.18f),
        soft,
        accentGray,
        base,
        soft.darken(0.28f)
    )
}

@Composable
internal fun PlayerBlurBackground(
    song: Song?,
    embeddedCover: Bitmap?,
    palette: PlayerPalette,
    motion: Float,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    val coverModel = resolveCoverPreviewModel(song, embeddedCover)
    val movingScale = 2.90f
    val movingOffset = 0f
    // On a light player theme, wash the blurred cover toward white (dark lyrics on top) instead of
    // darkening it like the dark theme does.
    val isLight = palette.isLight
    val scrim = if (isLight) Color.White else Color.Black
    Box(modifier = modifier.background(palette.middle)) {
        if (coverModel != null) {
            PlayerCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = movingScale
                        scaleY = movingScale
                        translationX = movingOffset
                        translationY = -movingOffset * 0.65f
                        alpha = 0.78f
                    }
                    .blur(48.dp),
                contentScale = ContentScale.Crop,
                sizePx = 1200
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            palette.accent.copy(alpha = if (isLight) 0.18f else 0.28f),
                            palette.top.copy(alpha = if (isLight) 0.34f else 0.42f),
                            scrim.copy(alpha = 0.34f)
                        ),
                        start = Offset.Zero,
                        end = Offset.Infinite
                    )
                )
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.06f),
                            Color.Transparent,
                            scrim.copy(alpha = 0.32f)
                        )
                    )
                )
        )
    }
}
