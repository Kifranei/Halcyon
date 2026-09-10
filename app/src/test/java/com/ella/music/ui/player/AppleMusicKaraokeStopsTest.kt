package com.ella.music.ui.player

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Karaoke sweep / sheen gradients must never hand a linear brush a descending stop list,
 * or the feathered edge inverts mid-word. Also guards QZ trail soft-edge + 七彩 spectrum.
 */
class AppleMusicKaraokeStopsTest {

    private fun Array<Pair<Float, Color>>.assertAscending(label: String) {
        assertTrue("$label: stops outside 0..1 -> ${map { it.first }}", all { it.first in 0f..1f })
        toList().zipWithNext().forEach { (a, b) ->
            assertTrue(
                "$label: ${a.first} > ${b.first} in ${map { it.first }}",
                a.first <= b.first
            )
        }
    }

    @Test
    fun fillStopsStayOrderedDefaultAndQz() {
        var progress = 0f
        while (progress <= 1f) {
            listOf(0.15f, 0.38f, 0.55f).forEach { feather ->
                karaokeFillStops(progress, Color.White, isRtl = false, feather = feather)
                    .assertAscending("ltr@$progress/$feather")
                karaokeFillStops(progress, Color.White, isRtl = true, feather = feather)
                    .assertAscending("rtl@$progress/$feather")
                karaokeFillStops(progress, Color.White, isRtl = false, feather = feather, qzCentered = true)
                    .assertAscending("qz-ltr@$progress/$feather")
                karaokeFillStops(progress, Color.White, isRtl = true, feather = feather, qzCentered = true)
                    .assertAscending("qz-rtl@$progress/$feather")
            }
            progress += 0.01f
        }
    }

    @Test
    fun rainbowFillStopsStayOrdered() {
        var progress = 0f
        while (progress <= 1f) {
            listOf(0.15f, 0.38f).forEach { feather ->
                karaokeRainbowFillStops(progress, 1f, isRtl = false, feather = feather)
                    .assertAscending("rainbow-ltr@$progress/$feather")
                karaokeRainbowFillStops(progress, 1f, isRtl = true, feather = feather)
                    .assertAscending("rainbow-rtl@$progress/$feather")
            }
            progress += 0.01f
        }
    }

    @Test
    fun sheenStopsStayOrderedWithTrailWidths() {
        var progress = 0f
        while (progress <= 1f) {
            listOf(0.06f, 0.5f, 1f).forEach { glow ->
                listOf(0.20f, 0.34f, 0.45f).forEach { trail ->
                    karaokeSheenStops(progress, Color.White, glow, 1f, isRtl = false, trailWidth = trail)
                        .assertAscending("ltr@$progress/$glow/$trail")
                    karaokeSheenStops(progress, Color.White, glow, 1f, isRtl = true, trailWidth = trail)
                        .assertAscending("rtl@$progress/$glow/$trail")
                }
            }
            progress += 0.01f
        }
    }

    @Test
    fun qzTrailFeatherUsesCharacterRelativeSoftEdge() {
        // Soft ≈ 2.5 Latin letters × 0.5em = 1.25em. At 40px font / 200px word -> 0.25
        val f = karaokeTrailFeather(
            fullTrailBreath = true,
            wordWidthPx = 200f,
            fontSizePx = 40f
        )
        assertTrue("expected ~0.25 got $f", f in 0.24f..0.26f)
        val fallback = karaokeTrailFeather(fullTrailBreath = true, wordWidthPx = 200f)
        assertTrue("fallback feather $fallback", fallback in 0.24f..0.26f)
        val off = karaokeTrailFeather(fullTrailBreath = false, wordWidthPx = 200f, fontSizePx = 40f)
        assertTrue("default feather $off", off == DefaultKaraokeFeather)
        assertTrue("dim factor $QzKaraokeDimAlphaFactor", QzKaraokeDimAlphaFactor in 0.34f..0.36f)
        assertTrue("soft-edge em $QzKaraokeSoftEdgeEm", QzKaraokeSoftEdgeEm in 1.24f..1.26f)
    }

    @Test
    fun spectrumRainbowHasSevenStopsAndContinuousHue() {
        assertTrue(
            "expected 7 ROYGBIV stops, got ${LyricSpectrumRainbowColors.size}",
            LyricSpectrumRainbowColors.size == 7
        )
        // No legacy Google four-stop palette
        val packed = LyricSpectrumRainbowColors.map {
            ((it.red * 255).toInt() shl 16) or ((it.green * 255).toInt() shl 8) or (it.blue * 255).toInt()
        }
        assertTrue("must not use Google #EA4335", 0xEA4335 !in packed)
        assertTrue("must not use Google #4285F4", 0x4285F4 !in packed)

        val redish = karaokeRainbowColor(0f, 1f)
        val mid = karaokeRainbowColor(0.5f, 1f)
        val violetish = karaokeRainbowColor(1f, 1f)
        assertTrue("pos0 should be red-dominant", redish.red > redish.blue && redish.red >= redish.green)
        assertTrue(
            "pos1 should be blue/violet-dominant",
            violetish.blue >= violetish.green * 0.5f || (violetish.red > 0.25f && violetish.blue > 0.25f)
        )
        assertTrue("mid should differ from ends", mid != redish && mid != violetish)
        assertTrue("HDR ratio is 1.5", LyricHdrBrightnessRatio == 1.5f)
    }
}
