package com.ella.music.ui.poster

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ella.music.R
import com.ella.music.data.model.LyricLine
import com.ella.music.ui.player.LocalKaraokeRainbowOverride
import com.ella.music.ui.player.hasMiniLyric
import kotlin.math.*

internal enum class PosterLyricStyle(val titleRes: Int) {
    Orbit(R.string.poster_lyric_orbit), Clock(R.string.poster_lyric_clock),
    ColorField(R.string.poster_lyric_color), Glow(R.string.poster_lyric_glow),
    Sonnet(R.string.poster_lyric_sonnet), Flow(R.string.poster_lyric_flow);
    companion object {
        fun fromSetting(value: Int) = entries.getOrElse(value) { Orbit }
    }
}

/** A local visual clock; pausing or leaving the theater suspends the frame loop. */
@Composable
internal fun rememberPosterSceneTime(playing: Boolean): State<Float> {
    val elapsed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var previous = 0L
        while (true) {
            val frame = withFrameNanos { it }
            if (previous != 0L) elapsed.floatValue = (elapsed.floatValue + (frame - previous) / 1_000_000_000f) % 3600f
            previous = frame
        }
    }
    return elapsed
}

/** Original native compositions following the reference layouts. The existing karaoke
 * renderer owns glyph timing, ruby, translation, backing vocals, fonts and HDR highlights. */
@Composable
internal fun PosterLyricScene(
    style: PosterLyricStyle,
    lyrics: List<LyricLine>, currentIndex: Int,
    position: State<Long>, time: State<Float>,
    fallbackTitle: String,
    showTranslation: Boolean, showPronunciation: Boolean,
    fontFamily: FontFamily? = null, translationFontFamily: FontFamily? = fontFamily,
    fontWeight: FontWeight = FontWeight.ExtraBold,
    fontScale: Float = 1f, wordLiftEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    val safeIndex = currentIndex.takeIf { it in lyrics.indices }
        ?: lyrics.indexOfFirst { it.hasMiniLyric() }
    CompositionLocalProvider(LocalKaraokeRainbowOverride provides false) {
        Box(modifier.fillMaxSize().clipToBounds().background(Color(0xFF07080C)).testTag("lyric-scene-${style.name}")) {
            when (style) {
                PosterLyricStyle.Clock -> PosterPendoloScene(lyrics, safeIndex, position, time,
                    fallbackTitle, showTranslation, fontFamily, translationFontFamily,
                    fontWeight, fontScale, Modifier.fillMaxSize())
                PosterLyricStyle.Flow -> PosterFlowLyrics(lyrics, safeIndex, position, time, fallbackTitle,
                    showTranslation, showPronunciation, fontFamily, translationFontFamily,
                    fontWeight, fontScale, wordLiftEnabled, Modifier.fillMaxSize())
                else -> PosterCinematicScene(style, lyrics, safeIndex, position, time, fallbackTitle,
                    showTranslation, showPronunciation, fontFamily, translationFontFamily, fontWeight, fontScale)
            }
        }
    }
}

internal fun posterGearPath(center: Offset, radius: Float, teeth: Int) = Path().apply {
    repeat(teeth * 4) { step ->
        val angle = step * PI.toFloat() * 2f / (teeth * 4)
        val r = radius * if (step % 4 < 2) 1f else 0.94f
        val x = center.x + cos(angle) * r; val y = center.y + sin(angle) * r
        if (step == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}
