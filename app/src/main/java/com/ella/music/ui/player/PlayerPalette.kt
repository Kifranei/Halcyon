package com.ella.music.ui.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import com.ella.music.data.model.Song
import java.net.URL
import kotlin.math.abs
import kotlin.math.max

internal fun loadPaletteCoverBitmap(context: Context, song: Song): Bitmap? {
    val cacheKey = song.paletteCoverCacheKey()
    PaletteCoverBitmapCache.get(cacheKey)?.let { return it }
    val bitmap = runCatching {
        when {
            song.coverUrl.isNotBlank() -> URL(song.coverUrl).openStream().use { input ->
                BitmapFactory.decodeStream(input)
            }
            song.albumId > 0L -> context.contentResolver
                .openInputStream(Uri.parse("content://media/external/audio/albumart/${song.albumId}"))
                ?.use { input -> BitmapFactory.decodeStream(input) }
            else -> null
        }?.scaledForPalette()
    }.getOrNull()
    bitmap?.let { PaletteCoverBitmapCache.put(cacheKey, it) }
    return bitmap
}

private fun Bitmap.scaledForPalette(): Bitmap {
    val longest = max(width, height)
    if (longest <= 480) return this
    val scale = 480f / longest.toFloat()
    return Bitmap.createScaledBitmap(
        this,
        (width * scale).toInt().coerceAtLeast(1),
        (height * scale).toInt().coerceAtLeast(1),
        true
    )
}

private fun Song.paletteCoverCacheKey(): String =
    when {
        coverUrl.isNotBlank() -> "url|$coverUrl"
        albumId > 0L -> "album|$albumId|$dateModified|$fileSize"
        else -> listOf(path, dateModified, fileSize, title, artist, album).joinToString("|")
    }

private object PaletteCoverBitmapCache {
    private val cache = object : LruCache<String, Bitmap>(6 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.bitmapSizeInKb()
    }

    fun get(key: String): Bitmap? = synchronized(cache) { cache.get(key) }

    fun put(key: String, bitmap: Bitmap) {
        synchronized(cache) {
            cache.put(key, bitmap)
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.evictAll()
        }
    }
}

internal fun clearPlayerPaletteMemoryCache() {
    PaletteCoverBitmapCache.clear()
}

private fun Bitmap.bitmapSizeInKb(): Int {
    val bytes = runCatching { allocationByteCount }.getOrElse { rowBytes * height }
    return (bytes / 1024).coerceAtLeast(1)
}

/**
 * Primary content color (text / icons) for the player surface. Provided once at the player root
 * from the active [PlayerPalette.onBackground] so the many small components that don't receive a
 * palette can still flip between white (dark background) and dark (light background).
 */
internal val LocalPlayerContentColor = androidx.compose.runtime.compositionLocalOf { androidx.compose.ui.graphics.Color.White }

/**
 * Whether the player surface is currently visible to the user.
 *
 * The player surface stays resident in the composition tree once opened (see MainActivity's
 * `playerResident`), and is merely slid off-screen via `translationY` when closed. Without this
 * signal, every continuous background animation (flow drift, beautiful-lyrics blobs, visualizer)
 * keeps redrawing full-screen — often blurred — canvases at the display refresh rate even while the
 * user is looking at another screen, wasting significant power. Animations gate on this so they
 * freeze the moment the surface is hidden and resume when it is shown again.
 */
internal val LocalPlayerSurfaceActive = androidx.compose.runtime.compositionLocalOf { true }

internal data class PlayerPalette(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val accent: Color,
    /** Primary content color (text / icons) that reads against this background. */
    val onBackground: Color = Color.White,
    val isLight: Boolean = false
) {
    /** Muted content color for secondary text; alpha-blended from [onBackground]. */
    val onBackgroundMuted: Color get() = onBackground.copy(alpha = 0.6f)

    companion object {
        private val LightContent = Color(0xFF16181C)

        val Default = PlayerPalette(
            top = Color(0xFF171717),
            middle = Color(0xFF0B0B0D),
            bottom = Color.Black,
            accent = Color(0xFF2F7DFF)
        )

        val LightDefault = PlayerPalette(
            top = Color(0xFFFCFCFD),
            middle = Color(0xFFF1F2F5),
            bottom = Color(0xFFE6E8EC),
            accent = Color(0xFF2F7DFF),
            onBackground = LightContent,
            isLight = true
        )

        fun from(bitmap: Bitmap?, light: Boolean = false): PlayerPalette {
            return fromCoverBackground(bitmap, light)
        }

        /** A single representative, vibrancy-boosted color from the cover, for use as a Monet seed. */
        fun seedColor(bitmap: Bitmap?): Color? = representativeAccent(bitmap)?.toPlayerAccent()

        fun pairFrom(bitmap: Bitmap?, light: Boolean = false): Pair<PlayerPalette, PlayerPalette> {
            // The cover and lyric surfaces share one background underneath the page transition.
            // Returning one palette prevents a visible color jump when immersive lyrics opens.
            val palette = fromCoverBackground(bitmap, light)
            return palette to palette
        }

        fun fromCoverBackground(bitmap: Bitmap?, light: Boolean = false): PlayerPalette {
            val representative = representativeAccent(bitmap) ?: return if (light) LightDefault else Default
            val accent = representative.toPlayerAccent()
            return if (light) lightPalette(accent) else PlayerPalette(
                top = accent.darken(0.40f),
                middle = accent.darken(0.66f),
                bottom = accent.darken(0.86f),
                accent = accent
            )
        }

        fun fromLyricBackground(bitmap: Bitmap?, light: Boolean = false): PlayerPalette {
            val accent = representativeAccent(bitmap)?.toPlayerAccent() ?: return if (light) LightDefault else Default
            return if (light) lightPalette(accent) else PlayerPalette(
                top = accent.darken(0.42f),
                middle = accent.darken(0.68f),
                bottom = accent.darken(0.88f),
                accent = accent
            )
        }

        /**
         * A tinted-light background derived from the cover accent, mirroring the dark gradient:
         * keep a visible pastel of the cover (not near-white) that fades lighter toward the bottom,
         * with dark content on top.
         */
        private fun lightPalette(accent: Color): PlayerPalette = PlayerPalette(
            top = accent.lighten(0.55f),
            middle = accent.lighten(0.72f),
            bottom = accent.lighten(0.86f),
            accent = accent.darken(0.18f),
            onBackground = LightContent,
            isLight = true
        )

        private fun representativeAccent(bitmap: Bitmap?): Color? {
            if (bitmap == null || bitmap.width <= 0 || bitmap.height <= 0) return null
            val sampleStep = (minOf(bitmap.width, bitmap.height) / 36).coerceAtLeast(1)
            val buckets = linkedMapOf<Int, LongArray>()
            val fallback = LongArray(4)
            val hsv = FloatArray(3)
            var sampled = 0
            var brightNeutral = 0
            var eligible = 0

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
                        val saturation = hsv[1]
                        val value = hsv[2]

                        sampled++
                        fallback[0]++
                        fallback[1] += r.toLong()
                        fallback[2] += g.toLong()
                        fallback[3] += b.toLong()
                        if (value > 0.78f && saturation < 0.18f) brightNeutral++

                        if (value > 0.08f && !(value > 0.94f && saturation < 0.20f)) {
                            eligible++
                            val key = ((r ushr 4) shl 8) or ((g ushr 4) shl 4) or (b ushr 4)
                            val bucket = buckets.getOrPut(key) { LongArray(4) }
                            bucket[0]++
                            bucket[1] += r.toLong()
                            bucket[2] += g.toLong()
                            bucket[3] += b.toLong()
                        }
                    }
                    x += sampleStep
                }
                y += sampleStep
            }
            if (fallback[0] == 0L) return null
            if (
                sampled > 0 &&
                brightNeutral.toFloat() / sampled.toFloat() > 0.56f &&
                eligible.toFloat() / sampled.toFloat() < 0.24f
            ) {
                val count = fallback[0].coerceAtLeast(1L)
                return Color(
                    (fallback[1] / count).toInt(),
                    (fallback[2] / count).toInt(),
                    (fallback[3] / count).toInt()
                )
            }

            val best = buckets.values.maxByOrNull { bucket ->
                val count = bucket[0].coerceAtLeast(1L)
                val r = (bucket[1] / count).toInt()
                val g = (bucket[2] / count).toInt()
                val b = (bucket[3] / count).toInt()
                AndroidColor.RGBToHSV(r, g, b, hsv)
                val luminance = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                val balance = 1f - abs(luminance - 0.50f).coerceIn(0f, 0.50f) * 1.25f
                count.toFloat() * (0.55f + hsv[1] * 1.65f) * (0.75f + balance * 0.55f)
            } ?: fallback

            val count = best[0].coerceAtLeast(1L)
            val r = (best[1] / count).toInt()
            val g = (best[2] / count).toInt()
            val b = (best[3] / count).toInt()
            return Color(r, g, b)
        }
    }
}

internal fun Color.darken(amount: Float): Color = Color(
    red = red * (1f - amount),
    green = green * (1f - amount),
    blue = blue * (1f - amount),
    alpha = 1f
)

internal fun Color.lighten(amount: Float): Color = Color(
    red = red + (1f - red) * amount,
    green = green + (1f - green) * amount,
    blue = blue + (1f - blue) * amount,
    alpha = alpha
)

internal fun Color.boosted(): Color {
    val max = maxOf(red, green, blue).coerceAtLeast(0.01f)
    val scale = (0.86f / max).coerceIn(1f, 2.4f)
    return Color(
        red = (red * scale).coerceAtMost(1f),
        green = (green * scale).coerceAtMost(1f),
        blue = (blue * scale).coerceAtMost(1f),
        alpha = 1f
    )
}

/** Accent-colored player content with a luminance floor so text and icons remain legible. */
internal fun PlayerPalette.coverContentColor(): Color = accent.boosted().let { accentColor ->
    if (isLight) accentColor.darken(0.28f) else accentColor.lighten(0.16f)
}

internal fun PlayerPalette.withCoverContentColor(): PlayerPalette =
    copy(onBackground = coverContentColor())

private fun Color.toPlayerAccent(): Color {
    val r = (red * 255f).toInt().coerceIn(0, 255)
    val g = (green * 255f).toInt().coerceIn(0, 255)
    val b = (blue * 255f).toInt().coerceIn(0, 255)
    val hsv = FloatArray(3)
    AndroidColor.RGBToHSV(r, g, b, hsv)
    if (hsv[1] < 0.12f) return Color(0xFF4D72B8)
    hsv[1] = hsv[1].coerceAtLeast(0.34f)
    hsv[2] = hsv[2].coerceIn(0.46f, 0.88f)
    return Color(AndroidColor.HSVToColor(hsv))
}
