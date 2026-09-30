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
        assertTrue("HDR ratio within the 1.5x-2.5x setting range", LyricHdrBrightnessRatio in 1.5f..2.5f)
    }
}
