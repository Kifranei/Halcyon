package com.ella.music.ui.poster

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.ui.player.AppleMusicSingleLyricLine
import com.ella.music.ui.player.LocalKaraokeRainbowOverride
import com.ella.music.ui.player.hasMiniLyric
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.flow.collectLatest
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported

/**
 * Original stacked-lyric stage in the spirit of theatrical karaoke walls: the active
 * line stays sharp and word-lit at center while neighbours drift away with distance —
 * dimmer, smaller and softly defocused — and the whole column springs after the
 * playback position. Layout, falloff curves and motion are all native; only the
 * shared karaoke renderer is reused.
 */
@Composable
internal fun PosterFlowLyrics(
    lyrics: List<LyricLine>, currentIndex: Int, position: State<Long>, time: State<Float>,
    fallbackTitle: String, showTranslation: Boolean, showPronunciation: Boolean,
    fontFamily: FontFamily?, translationFontFamily: FontFamily?, fontWeight: FontWeight,
    fontScale: Float, wordLiftEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val safeIndex = currentIndex.takeIf { it in lyrics.indices }
        ?: lyrics.indexOfFirst { it.hasMiniLyric() }
    CompositionLocalProvider(LocalKaraokeRainbowOverride provides false) {
        BoxWithConstraints(
            modifier.fillMaxSize().clipToBounds().background(Color(0xFF06070C))
                .testTag("lyric-scene-Flow")
        ) {
            PosterFlowBackdrop(time, Modifier.fillMaxSize())
            if (safeIndex < 0) {
                Text(
                    fallbackTitle.ifBlank { stringResource(R.string.player_no_lyrics) },
                    color = Color.White, fontSize = 34.sp, fontFamily = fontFamily,
                    fontWeight = fontWeight, textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 32.dp)
                )
                return@BoxWithConstraints
            }
            val density = LocalDensity.current
            val viewportWidth = maxWidth
            val viewportHeightPx = with(density) { maxHeight.toPx() }
            val viewportCenterY = viewportHeightPx * 0.46f
            val columnWidth = viewportWidth * 0.80f
            val gapPx = with(density) { 30.dp.toPx() }
            val estimatedPx = with(density) { 58.dp.toPx() }
            val blurAvailable = remember { isRenderEffectSupported() }
            val primarySp = (maxHeight.value * 0.096f) * fontScale
            val neighbourSp = (maxHeight.value * 0.045f) * fontScale

            // Heights feed both focus targeting and per-line offsets; unmeasured lines
            // fall back to the estimate so the stack stays stable while new rows mount.
            val heights = remember { mutableStateMapOf<Int, Int>() }
            fun topOf(index: Int): Float {
                var top = 0f
                for (i in 0 until index) top += (heights[i]?.toFloat() ?: estimatedPx) + gapPx
                return top
            }
            fun centerOf(index: Int): Float = topOf(index) + (heights[index]?.toFloat() ?: estimatedPx) / 2f

            val focusY = remember { Animatable(0f) }
            var anchored by remember { mutableStateOf(false) }
            LaunchedEffect(safeIndex) {
                snapshotFlow { centerOf(safeIndex) }
                    .collectLatest { target ->
                        if (!anchored) {
                            focusY.snapTo(target)
                            anchored = true
                        } else {
                            focusY.animateTo(target, spring(dampingRatio = 0.84f, stiffness = Spring.StiffnessMediumLow))
                        }
                    }
            }

            val first = (safeIndex - 7).coerceAtLeast(0)
            val last = (safeIndex + 12).coerceAtMost(lyrics.lastIndex)
            val indices = (first..last).filter { lyrics.getOrNull(it)?.text?.isNotBlank() == true }
            Box(Modifier.align(Alignment.TopStart).width(columnWidth).padding(start = viewportWidth * 0.11f)) {
                indices.forEach { index ->
                    val line = lyrics[index]
                    val distance = abs(index - safeIndex)
                    val past = index < safeIndex
                    val active = index == safeIndex
                    val alpha = when {
                        active -> 1f
                        past -> (0.40f - 0.055f * distance).coerceAtLeast(0.08f)
                        else -> (0.30f - 0.045f * distance).coerceAtLeast(0.06f)
                    }
                    val scale = (1f - 0.05f * distance).coerceAtLeast(0.78f)
                    val blurPx = if (blurAvailable && distance > 0)
                        ((distance - 0.35f) * 4.5f).coerceIn(0f, 16f) else 0f
                    Box(
                        Modifier.fillMaxWidth()
                            .onSizeChanged { size -> heights[index] = size.height }
                            .graphicsLayer {
                                translationY = viewportCenterY + centerOf(index) - focusY.value
                                this.alpha = alpha
                                scaleX = scale
                                scaleY = scale
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
                            }
                            .then(if (blurPx > 0.5f) Modifier.blur(blurPx.dp) else Modifier)
                    ) {
                        if (active) {
                            AppleMusicSingleLyricLine(
                                line = line,
                                currentPositionMs = position.value,
                                currentPositionState = position,
                                showTranslation = showTranslation,
                                showPronunciation = showPronunciation,
                                fontFamily = fontFamily,
                                translationFontFamily = translationFontFamily,
                                fontWeight = fontWeight,
                                fontScale = fontScale,
                                secondaryFontScale = 0.62f,
                                primaryTextSizeSp = primarySp,
                                secondaryTextSizeSp = neighbourSp * 0.72f,
                                lyricTextAlign = SettingsManager.PLAYER_LYRIC_ALIGN_LEFT,
                                contentColor = Color.White,
                                wordLiftEnabled = wordLiftEnabled,
                                singleLine = false,
                                interactive = false,
                                primaryGlowColor = Color.White.copy(alpha = 0.22f),
                                primaryGlowRadius = 10f,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            Text(
                                line.text,
                                color = Color.White,
                                fontSize = neighbourSp.sp,
                                fontFamily = fontFamily,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.style.TextOverflow.Clip,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            // Edge falloff so lines dissolve into the backdrop instead of clipping.
            Canvas(Modifier.fillMaxSize()) {
                val fade = size.height * 0.17f
                drawRect(
                    Brush.verticalGradient(listOf(Color(0xFF06070C), Color.Transparent),
                        startY = 0f, endY = fade),
                    size = androidx.compose.ui.geometry.Size(size.width, fade)
                )
                drawRect(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0xFF06070C)),
                        startY = size.height - fade, endY = size.height),
                    topLeft = Offset(0f, size.height - fade)
                )
            }
        }
    }
}

/** Slow ambient wash behind the stack: two drifting glows and a fine grain field. */
@Composable
private fun PosterFlowBackdrop(time: State<Float>, modifier: Modifier = Modifier) {
    val grain = remember { Random(29).let { r -> List(420) { Offset(r.nextFloat(), r.nextFloat()) } } }
    Canvas(modifier) {
        val phase = time.value
        val first = Offset(
            size.width * (0.30f + sin(phase * 0.11f) * 0.10f),
            size.height * (0.34f + sin(phase * 0.07f) * 0.08f)
        )
        val second = Offset(
            size.width * (0.74f + sin(phase * 0.09f + 2f) * 0.08f),
            size.height * (0.68f + sin(phase * 0.13f + 1f) * 0.07f)
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF6E7FAE).copy(alpha = 0.16f), Color.Transparent),
                first, size.width * 0.38f
            ),
            size.width * 0.38f, first
        )
        drawCircle(
            Brush.radialGradient(
                listOf(Color(0xFF4C3F66).copy(alpha = 0.14f), Color.Transparent),
                second, size.width * 0.32f
            ),
            size.width * 0.32f, second
        )
        grain.forEach { point ->
            drawCircle(
                Color.White.copy(alpha = 0.028f),
                0.55.dp.toPx(),
                Offset(point.x * size.width, point.y * size.height)
            )
        }
    }
}
