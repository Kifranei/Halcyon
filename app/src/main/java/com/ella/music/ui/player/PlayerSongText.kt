package com.ella.music.ui.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.ui.components.ExplicitSongTitle
import kotlinx.coroutines.isActive

@Composable
internal fun LandscapeSongTitle(
    song: Song?,
    annotation: String,
    fontFamily: FontFamily? = null,
    modifier: Modifier = Modifier
) {
    PlayerSongMetaText(
        song = song,
        annotation = annotation,
        titleFontSize = 28.sp,
        artistFontSize = 16.sp,
        artistAlpha = 0.50f,
        fallbackTitle = stringResource(R.string.app_name),
        contentColor = LocalPlayerContentColor.current,
        fontFamily = fontFamily,
        modifier = modifier.padding(end = 16.dp)
    )
}

@Composable
internal fun PlayerSongMetaText(
    song: Song?,
    annotation: String,
    titleFontSize: TextUnit,
    artistFontSize: TextUnit,
    artistAlpha: Float,
    modifier: Modifier = Modifier,
    fallbackTitle: String? = null,
    showArtistWithAnnotation: Boolean = false,
    artistOverride: String? = null,
    artistIconRes: Int? = null,
    contentColor: Color = Color.White,
    textAlign: TextAlign = TextAlign.Start,
    fontFamily: FontFamily? = null,
    titleMarqueeEnabled: Boolean = true,
    artistMarqueeEnabled: Boolean = true,
    onArtistClick: (() -> Unit)? = null,
    onAlbumClick: (() -> Unit)? = null
) {
    val artist = song?.artist.orEmpty()
    val displayArtist = artistOverride?.takeIf { it.isNotBlank() } ?: artist
    val artistClickEnabled = artistOverride.isNullOrBlank() && artist.isNotBlank()
    fun clickableMetaModifier(enabled: Boolean, onClick: (() -> Unit)?): Modifier {
        return if (enabled && onClick != null) {
            Modifier
                .clip(RoundedCornerShape(6.dp))
                .clickable(onClick = onClick)
        } else {
            Modifier
        }
    }
    Column(modifier = modifier) {
        PlayerSongTitleText(
            text = song?.title?.takeIf { it.isNotBlank() }
                ?: fallbackTitle?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.player_not_playing),
            fontSize = titleFontSize,
            fontWeight = FontWeight.ExtraBold,
            color = contentColor.copy(alpha = 0.96f),
            textAlign = textAlign,
            fontFamily = fontFamily,
            marqueeEnabled = titleMarqueeEnabled,
            modifier = Modifier.fillMaxWidth()
        )
        if (annotation.isNotBlank()) {
            PlayerMarqueeText(
                text = annotation,
                fontSize = (artistFontSize.value * 0.82f).sp,
                fontWeight = FontWeight.Bold,
                color = contentColor.copy(alpha = (artistAlpha + 0.16f).coerceAtMost(0.82f)),
                textAlign = textAlign,
                fontFamily = fontFamily,
                enabled = artistMarqueeEnabled,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (displayArtist.isNotBlank() && (annotation.isBlank() || showArtistWithAnnotation)) {
            val artistModifier = Modifier
                .fillMaxWidth()
                .then(clickableMetaModifier(artistClickEnabled, onArtistClick))
            if (artistIconRes != null) {
                Row(
                    modifier = artistModifier,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = painterResource(artistIconRes),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(contentColor.copy(alpha = artistAlpha)),
                        modifier = Modifier.size(artistFontSize.value.dp)
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    PlayerMarqueeText(
                        text = displayArtist,
                        fontSize = artistFontSize,
                        fontWeight = FontWeight.Bold,
                        color = contentColor.copy(alpha = artistAlpha),
                        textAlign = textAlign,
                        fontFamily = fontFamily,
                        enabled = artistMarqueeEnabled,
                        modifier = Modifier.weight(1f)
                    )
                }
            } else {
                PlayerMarqueeText(
                    text = displayArtist,
                    fontSize = artistFontSize,
                    fontWeight = FontWeight.Bold,
                    color = contentColor.copy(alpha = artistAlpha),
                    textAlign = textAlign,
                    fontFamily = fontFamily,
                    enabled = artistMarqueeEnabled,
                    modifier = artistModifier
                )
            }
        }
    }
}

@Composable
internal fun PlayerSongTitleText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    textAlign: TextAlign = TextAlign.Start,
    fontFamily: FontFamily? = null,
    marqueeEnabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (marqueeEnabled) {
        ExplicitSongTitle(
            title = text,
            fontSize = fontSize,
            fontWeight = fontWeight,
            color = color,
            fontFamily = fontFamily,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = textAlign,
            modifier = modifier,
            titleModifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
        )
    } else {
        BasicText(
            text = text,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                color = color,
                textAlign = textAlign
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun PlayerMarqueeText(
    text: String,
    fontSize: TextUnit,
    fontWeight: FontWeight,
    color: Color,
    textAlign: TextAlign = TextAlign.Start,
    fontFamily: FontFamily? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (!enabled) {
        BasicText(
            text = text,
            style = TextStyle(
                fontSize = fontSize,
                fontWeight = fontWeight,
                fontFamily = fontFamily,
                color = color,
                textAlign = textAlign
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = modifier.fillMaxWidth()
        )
        return
    }
    LyriconStyleMarqueeText(
        text = AnnotatedString(text),
        style = TextStyle(
            fontSize = fontSize,
            fontWeight = fontWeight,
            fontFamily = fontFamily,
            color = color
        ),
        enabled = true,
        textAlign = textAlign,
        modifier = modifier
    )
}

@Composable
internal fun LyriconStyleMarqueeText(
    text: AnnotatedString,
    style: TextStyle,
    enabled: Boolean,
    textAlign: TextAlign,
    modifier: Modifier = Modifier,
    ghostSpacing: androidx.compose.ui.unit.Dp = 70.dp,
    speedDpPerSecond: Float = 40f,
    initialDelayMs: Long = 300L,
    loopDelayMs: Long = 700L
) {
    val density = LocalDensity.current
    val spacingPx = with(density) { ghostSpacing.toPx() }
    val speedPxPerMs = with(density) { speedDpPerSecond.dp.toPx() } / 1000f
    var elapsedMs by remember(text, enabled) { mutableFloatStateOf(0f) }

    LaunchedEffect(text, enabled) {
        elapsedMs = 0f
        if (!enabled) return@LaunchedEffect
        var lastFrameNanos = 0L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    elapsedMs += (frameNanos - lastFrameNanos) / 1_000_000f
                }
                lastFrameNanos = frameNanos
            }
        }
    }

    Layout(
        content = {
            BasicText(
                text = text,
                style = style.copy(textAlign = textAlign),
                maxLines = 1,
                overflow = TextOverflow.Visible
            )
            BasicText(
                text = text,
                style = style.copy(textAlign = textAlign),
                maxLines = 1,
                overflow = TextOverflow.Visible
            )
        },
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
    ) { measurables, constraints ->
        val textConstraints = constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity)
        val primary = measurables[0].measure(textConstraints)
        val ghost = measurables[1].measure(textConstraints)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else primary.width
        val height = primary.height
        val overflowPx = (primary.width - width).coerceAtLeast(0)
        val shouldScroll = enabled && overflowPx > 0
        val x = if (shouldScroll) {
            -lyriconMarqueeOffsetPx(
                elapsedMs = elapsedMs,
                textWidthPx = primary.width.toFloat(),
                spacingPx = spacingPx,
                speedPxPerMs = speedPxPerMs,
                initialDelayMs = initialDelayMs,
                loopDelayMs = loopDelayMs
            )
        } else {
            when (textAlign) {
                TextAlign.Center -> ((width - primary.width) / 2).coerceAtLeast(0).toFloat()
                TextAlign.End, TextAlign.Right -> (width - primary.width).coerceAtLeast(0).toFloat()
                else -> 0f
            }
        }
        val unit = primary.width + spacingPx

        layout(width, height) {
            primary.placeRelativeWithLayer(0, 0) {
                translationX = x
            }
            if (shouldScroll) {
                ghost.placeRelativeWithLayer(0, 0) {
                    translationX = x + unit
                }
            }
        }
    }
}

internal fun lyriconMarqueeOffsetPx(
    elapsedMs: Float,
    textWidthPx: Float,
    spacingPx: Float,
    speedPxPerMs: Float,
    initialDelayMs: Long,
    loopDelayMs: Long
): Float {
    val activeMs = (elapsedMs - initialDelayMs).coerceAtLeast(0f)
    if (activeMs <= 0f) return 0f
    val unit = textWidthPx + spacingPx
    val travelMs = unit / speedPxPerMs.coerceAtLeast(0.001f)
    val cycleMs = travelMs + loopDelayMs
    val cycleTime = activeMs % cycleMs
    if (cycleTime >= travelMs) return 0f
    return (cycleTime * speedPxPerMs).coerceIn(0f, unit)
}
