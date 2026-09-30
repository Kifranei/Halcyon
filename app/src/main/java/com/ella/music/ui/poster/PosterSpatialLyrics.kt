package com.ella.music.ui.poster

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.ui.player.AppleMusicSingleLyricLine
import kotlin.math.*

/** Each independently transformed word still uses the player's original karaoke/ruby renderer. */
@Composable
internal fun PosterSpatialLyrics(
    style: PosterLyricStyle, line: LyricLine, nextLine: LyricLine?,
    position: State<Long>, time: State<Float>,
    showPronunciation: Boolean, fontFamily: FontFamily?, translationFontFamily: FontFamily?,
    fontWeight: FontWeight, fontScale: Float, wordLiftEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val tokens = remember(line, nextLine) { posterLyricWords(line, nextLine) }
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    BoxWithConstraints(modifier.testTag("theater-current-lyric")) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val scattered = style == PosterLyricStyle.ColorField
        val desiredSize = (if (scattered) 46f else 36f) * fontScale /
            sqrt((tokens.size / 8f).coerceAtLeast(1f))
        val measured = remember(tokens, fontFamily, fontWeight, desiredSize, showPronunciation, density) {
            tokens.map { token ->
                val size = textMeasurer.measure(token.text, TextStyle(
                    fontFamily = fontFamily, fontWeight = fontWeight, fontSize = desiredSize.sp
                )).size
                PosterWordSize(size.width.toFloat() + with(density) { 16.dp.toPx() },
                    maxOf(size.height.toFloat(), with(density) { (desiredSize * 1.5f).sp.toPx() }) +
                        if (showPronunciation && !token.pronunciation.isNullOrBlank()) with(density) { 20.dp.toPx() } else 0f)
            }
        }
        val fit = remember(measured, widthPx, heightPx, scattered) {
            minOf(1f,
                widthPx * (if (scattered) 0.62f else 0.45f) / (measured.maxOfOrNull { it.width } ?: 1f).coerceAtLeast(1f),
                if (scattered) sqrt(widthPx * heightPx * 0.31f / measured.sumOf { (it.width * it.height).toDouble() }.toFloat().coerceAtLeast(1f)) else 1f)
        }
        val sizes = remember(measured, fit) { measured.map { PosterWordSize(it.width * fit, it.height * fit) } }
        val drift = with(density) { 3.dp.toPx() }
        val placements = remember(sizes, widthPx, heightPx, line.timeMs, line.text, scattered) {
            if (scattered) posterScatteredWords(sizes, widthPx, heightPx,
                line.text.hashCode() xor line.timeMs.hashCode(), drift) else emptyList()
        }
        val (radius, orbitHeight) = posterOrbitDimensions(widthPx, heightPx,
            (sizes.maxOfOrNull { it.width } ?: 0f) + drift * 2f,
            (sizes.maxOfOrNull { it.height } ?: 0f) + drift * 2f)
        tokens.forEachIndexed { index, token ->
            key(index, token.timeMs, token.text) {
                AppleMusicSingleLyricLine(
                    line = token,
                    // The live clock is read during glyph drawing and layer transforms, not
                    // during composition, so movement doesn't remeasure every word per frame.
                    currentPositionMs = token.timeMs, currentPositionState = position,
                    showTranslation = false, showPronunciation = showPronunciation,
                    fontFamily = fontFamily, translationFontFamily = translationFontFamily,
                    fontWeight = fontWeight, fontScale = 1f, secondaryFontScale = 1f,
                    primaryTextSizeSp = desiredSize * fit, secondaryTextSizeSp = 12f,
                    lyricTextAlign = SettingsManager.PLAYER_LYRIC_ALIGN_CENTER,
                    contentColor = Color.White, wordLiftEnabled = wordLiftEnabled,
                    singleLine = true, interactive = false, showBackgroundText = false,
                    modifier = Modifier.align(Alignment.Center)
                        .width(with(density) { sizes[index].width.toDp() })
                        .graphicsLayer {
                            if (scattered) {
                                val placement = placements[index]
                                translationX = placement.x - widthPx / 2f + sin(time.value * 0.25f + placement.phase) * drift
                                translationY = placement.y - heightPx / 2f + cos(time.value * 0.21f + placement.phase) * drift
                                rotationZ = placement.rotation
                            } else {
                                val frame = posterOrbitFrame(index, tokens.size, time.value, radius, orbitHeight).onPosterAxis()
                                translationX = frame.x
                                translationY = frame.y
                                rotationY = frame.rotationY
                                scaleX = frame.scale; scaleY = frame.scale
                                alpha = frame.alpha
                                cameraDistance = 24.dp.toPx()
                            }
                        }
                        .testTag("theater-word-$index")
                )
            }
        }
    }
}
