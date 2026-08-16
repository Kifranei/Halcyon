package com.ella.music.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import kotlin.math.abs

/** Shared single-line surface used by the system desktop-lyrics overlay. */
@Composable
internal fun AppleMusicSingleLyricLine(
    line: LyricLine,
    currentPositionMs: Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    translationFontFamily: FontFamily? = fontFamily,
    fontWeight: FontWeight,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    lyricTextAlign: Int,
    contentColor: Color,
    wordLiftEnabled: Boolean,
    singleLine: Boolean,
    inlineStaticSecondaryText: String = "",
    inlineStaticSecondaryWords: List<LyricWord> = emptyList(),
    mergeInlineSecondary: Boolean = false,
    statusBarMarquee: Boolean = false,
    secondaryAlpha: Float = 0.74f,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val pronunciationBelow by remember(context) { SettingsManager.getInstance(context).lyricPronunciationBelow }
        .collectAsState(initial = false)
    val sustainThresholdMs by remember(context) {
        SettingsManager.getInstance(context).appleMusicLyricsSustainThresholdMs
    }.collectAsState(initial = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS)
    val defaultTextAlign = when (lyricTextAlign) {
        SettingsManager.PLAYER_LYRIC_ALIGN_CENTER -> TextAlign.Center
        SettingsManager.PLAYER_LYRIC_ALIGN_RIGHT -> TextAlign.End
        else -> TextAlign.Start
    }
    AppleMusicLyricLine(
        line = line,
        active = true,
        paused = false,
        distance = 0,
        userScrolling = true,
        nonCurrentLineBlurEnabled = false,
        currentPositionMs = currentPositionMs,
        showTranslation = showTranslation,
        showPronunciation = showPronunciation,
        pronunciationBelow = pronunciationBelow,
        fontFamily = fontFamily,
        translationFontFamily = translationFontFamily,
        fontWeight = fontWeight,
        fontScale = fontScale,
        secondaryFontScale = secondaryFontScale,
        primaryTextSizeSp = primaryTextSizeSp,
        secondaryTextSizeSp = secondaryTextSizeSp,
        defaultTextAlign = defaultTextAlign,
        contentColor = contentColor,
        wordLiftEnabled = wordLiftEnabled,
        sustainThresholdMs = sustainThresholdMs,
        singleLine = singleLine,
        inlineStaticSecondaryText = inlineStaticSecondaryText,
        inlineStaticSecondaryWords = inlineStaticSecondaryWords,
        mergeInlineSecondary = mergeInlineSecondary,
        statusBarMarquee = statusBarMarquee,
        secondaryAlpha = secondaryAlpha,
        onClick = {},
        onDoubleClick = {},
        onLongClick = {},
        modifier = modifier
    )
}

@Composable
internal fun AppleMusicLyricLine(
    line: LyricLine,
    active: Boolean,
    paused: Boolean = false,
    distance: Int,
    userScrolling: Boolean,
    nonCurrentLineBlurEnabled: Boolean,
    currentPositionMs: Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    pronunciationBelow: Boolean,
    fontFamily: FontFamily?,
    translationFontFamily: FontFamily?,
    fontWeight: FontWeight,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    defaultTextAlign: TextAlign,
    contentColor: Color,
    wordLiftEnabled: Boolean,
    sustainThresholdMs: Int = SettingsManager.DEFAULT_APPLE_MUSIC_LYRICS_SUSTAIN_THRESHOLD_MS,
    singleLine: Boolean = false,
    inlineStaticSecondaryText: String = "",
    inlineStaticSecondaryWords: List<LyricWord> = emptyList(),
    mergeInlineSecondary: Boolean = false,
    statusBarMarquee: Boolean = false,
    secondaryAlpha: Float = 0.74f,
    reserveExtraLyricSpace: Boolean = false,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val textAlign = line.duetTextAlign(defaultTextAlign)
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.91f,
        animationSpec = spring(dampingRatio = 0.82f, stiffness = 340f),
        label = "appleLyricsScale"
    )
    val alpha by animateFloatAsState(
        targetValue = when {
            active || paused -> 1f
            else -> (0.24f - abs(distance) * 0.025f).coerceAtLeast(0.13f)
        },
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "appleLyricsAlpha"
    )
    val primaryStyle = TextStyle(
        fontSize = (primaryTextSizeSp * fontScale).sp,
        lineHeight = (primaryTextSizeSp * fontScale * 1.18f).sp,
        fontWeight = if (active) fontWeight else FontWeight.Bold,
        fontFamily = fontFamily,
        color = contentColor.copy(alpha = alpha),
        textAlign = textAlign,
        shadow = null
    )
    val secondaryStyle = TextStyle(
        fontSize = (secondaryTextSizeSp * fontScale * secondaryFontScale).sp,
        lineHeight = (secondaryTextSizeSp * fontScale * secondaryFontScale * 1.28f).sp,
        fontWeight = FontWeight.SemiBold,
        fontFamily = translationFontFamily,
        color = contentColor.copy(alpha = alpha * secondaryAlpha.coerceIn(0f, 1f)),
        textAlign = textAlign
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = (distance * -2f) * density
                transformOrigin = TransformOrigin(
                    pivotFractionX = when (textAlign) {
                        TextAlign.End -> 1f
                        TextAlign.Center -> 0.5f
                        else -> 0f
                    },
                    pivotFractionY = 0.5f
                )
            }
            .then(
                if (nonCurrentLineBlurEnabled && !userScrolling && !active && abs(distance) >= 2) {
                    Modifier.blur((2 + abs(distance)).dp)
                } else {
                    Modifier
                }
            )
            .pointerInput(line) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() },
                    onLongPress = { onLongClick() }
                )
            }
            .padding(horizontal = 2.dp),
        horizontalAlignment = when (textAlign) {
            TextAlign.End -> Alignment.End
            TextAlign.Center -> Alignment.CenterHorizontally
            else -> Alignment.Start
        }
    ) {
        val pronunciation = line.pronunciation.orEmpty()
        val showPronunciationAbove = showPronunciation && pronunciation.isNotBlank() && !pronunciationBelow
        val showPronunciationBelow = showPronunciation && pronunciation.isNotBlank() && pronunciationBelow
        if (showPronunciationAbove) {
            BasicText(text = pronunciation, style = secondaryStyle, modifier = Modifier.fillMaxWidth())
        }
        val primaryText = line.text.ifBlank { line.backgroundText.orEmpty().ifBlank { "♪" } }
        val hasInlineSecondary = singleLine && inlineStaticSecondaryText.isNotBlank()
        if (hasInlineSecondary && mergeInlineSecondary) {
            // "Merge secondary into primary" is a presentation mode, not an end-of-line effect:
            // it must stay inline from the first word for both word-timed and line-timed lyrics.
            StatusBarMergedTimedLyricRow(
                primaryText = primaryText,
                primaryWords = line.words,
                secondaryText = inlineStaticSecondaryText,
                positionMs = currentPositionMs,
                active = active,
                primaryStyle = primaryStyle,
                contentColor = contentColor,
                secondaryAlpha = secondaryAlpha,
                wordLiftEnabled = wordLiftEnabled,
                sustainThresholdMs = sustainThresholdMs,
                textAlign = textAlign
            )
        } else if (hasInlineSecondary) {
            StatusBarSeparatedTimedLyricLines(
                primaryText = primaryText,
                primaryWords = line.words,
                secondaryText = inlineStaticSecondaryText,
                secondaryWords = inlineStaticSecondaryWords,
                positionMs = currentPositionMs,
                active = active,
                primaryStyle = primaryStyle,
                secondaryStyle = secondaryStyle,
                contentColor = contentColor,
                wordLiftEnabled = wordLiftEnabled,
                sustainThresholdMs = sustainThresholdMs,
                statusBarMarquee = statusBarMarquee
            )
        } else {
            TimedLyricText(
                text = primaryText,
                words = line.words,
                positionMs = currentPositionMs,
                active = active,
                style = primaryStyle,
                contentColor = contentColor,
                wordLiftEnabled = wordLiftEnabled,
                sustainThresholdMs = sustainThresholdMs,
                singleLine = singleLine,
                statusBarMarquee = statusBarMarquee,
                modifier = Modifier.fillMaxWidth()
            )
        }
        if (showPronunciationBelow) {
            BasicText(
                text = pronunciation,
                style = secondaryStyle,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
            )
        }
        line.translation?.takeIf { showTranslation && it.isNotBlank() }?.let { translation ->
            BasicText(
                text = translation,
                style = secondaryStyle,
                modifier = Modifier.fillMaxWidth().padding(top = 5.dp)
            )
        }
        line.backgroundText?.trim()?.takeIf { it.isNotBlank() && line.text.isNotBlank() }?.let { background ->
            val backgroundActive = line.isBackgroundActiveAt(currentPositionMs)
            val backgroundAlpha by animateFloatAsState(
                targetValue = if (backgroundActive) 1f else 0f,
                animationSpec = tween(
                    durationMillis = if (backgroundActive) 300 else 180,
                    delayMillis = if (backgroundActive) 300 else 0,
                    easing = FastOutSlowInEasing
                ),
                label = "appleLyricsBackgroundAlpha"
            )
            val backgroundContent: @Composable () -> Unit = {
                Column {
                    TimedLyricText(
                        text = background,
                        words = line.backgroundWords,
                        positionMs = currentPositionMs,
                        active = active,
                        style = secondaryStyle.copy(color = contentColor.copy(alpha = alpha * 0.72f)),
                        contentColor = contentColor,
                        wordLiftEnabled = wordLiftEnabled,
                        sustainThresholdMs = sustainThresholdMs,
                        singleLine = singleLine,
                        modifier = Modifier.fillMaxWidth().padding(top = 7.dp)
                    )
                    line.backgroundTranslation?.takeIf { showTranslation && it.isNotBlank() }?.let { translation ->
                        BasicText(
                            text = translation,
                            style = secondaryStyle.copy(color = contentColor.copy(alpha = alpha * 0.62f)),
                            modifier = Modifier.fillMaxWidth().padding(top = 3.dp)
                        )
                    }
                }
            }
            if (reserveExtraLyricSpace) {
                // Keep the x-bg row measured even while it is hidden. The mini preview can then
                // calculate the target offset from the final row height instead of overshooting
                // when the background vocal appears.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(backgroundAlpha)
                ) {
                    backgroundContent()
                }
            } else {
                AnimatedVisibility(
                    visible = backgroundActive,
                    // ConePlayer gives BG vocals their own reveal: the main line settles first,
                    // then the x-bg layer enters after a 300 ms beat.
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = 300,
                            easing = FastOutSlowInEasing
                        )
                    ) + slideInVertically(
                        animationSpec = tween(
                            durationMillis = 300,
                            delayMillis = 300,
                            easing = FastOutSlowInEasing
                        ),
                        initialOffsetY = { it }
                    ),
                    exit = fadeOut(animationSpec = tween(180)) +
                        slideOutVertically(animationSpec = tween(180), targetOffsetY = { it / 3 })
                ) {
                    backgroundContent()
                }
            }
        }
    }
}

@Composable
private fun StatusBarSeparatedTimedLyricLines(
    primaryText: String,
    primaryWords: List<LyricWord>,
    secondaryText: String,
    secondaryWords: List<LyricWord>,
    positionMs: Long,
    active: Boolean,
    primaryStyle: TextStyle,
    secondaryStyle: TextStyle,
    contentColor: Color,
    wordLiftEnabled: Boolean,
    sustainThresholdMs: Int,
    statusBarMarquee: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        TimedLyricText(
            text = primaryText,
            words = primaryWords,
            positionMs = positionMs,
            active = active,
            style = primaryStyle,
            contentColor = contentColor,
            wordLiftEnabled = wordLiftEnabled,
            sustainThresholdMs = sustainThresholdMs,
            singleLine = true,
            statusBarMarquee = statusBarMarquee,
            modifier = Modifier.fillMaxWidth()
        )
        TimedLyricText(
            text = secondaryText,
            words = secondaryWords,
            positionMs = positionMs,
            active = active,
            style = secondaryStyle,
            contentColor = contentColor,
            wordLiftEnabled = wordLiftEnabled,
            sustainThresholdMs = sustainThresholdMs,
            singleLine = true,
            statusBarMarquee = statusBarMarquee,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp)
        )
    }
}

@Composable
private fun StatusBarMergedTimedLyricRow(
    primaryText: String,
    primaryWords: List<LyricWord>,
    secondaryText: String,
    positionMs: Long,
    active: Boolean,
    primaryStyle: TextStyle,
    contentColor: Color,
    secondaryAlpha: Float,
    wordLiftEnabled: Boolean,
    sustainThresholdMs: Int,
    textAlign: TextAlign
) {
    val primaryEndMs = remember(primaryWords) { primaryWords.maxOfOrNull { it.endMs } }
    val secondaryLiftProgress = if (active && wordLiftEnabled && primaryEndMs != null) {
        ((positionMs - primaryEndMs).toFloat() / STATUS_BAR_SECONDARY_LIFT_DURATION_MS)
            .coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedSecondaryLiftProgress by animateFloatAsState(
        targetValue = secondaryLiftProgress,
        animationSpec = tween(durationMillis = STATUS_BAR_SECONDARY_LIFT_ANIMATION_MS),
        label = "status-bar-secondary-lift"
    )
    val secondaryLiftPx = with(LocalDensity.current) {
        if (wordLiftEnabled) {
            maxOf(primaryStyle.fontSize.toPx() * 0.06f, 5f) * animatedSecondaryLiftProgress
        } else {
            0f
        }
    }
    val contentAlignment = when (textAlign) {
        TextAlign.End -> Alignment.CenterEnd
        TextAlign.Center -> Alignment.Center
        else -> Alignment.CenterStart
    }
    // A single marquee owns both runs.  Measuring two independent fill-width Text nodes is what
    // previously made a long or multi-fragment secondary line wrap beneath the primary instead
    // of remaining part of the same status-bar row.
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .basicMarquee(),
        contentAlignment = contentAlignment
    ) {
        Row(
            // `width(IntrinsicSize.Max)` makes the marquee measure the combined primary and
            // secondary text as one unbounded run. Without it, a multi-fragment secondary
            // could receive the viewport constraint and wrap to a second visual row.
            modifier = Modifier
                .width(IntrinsicSize.Max)
                .wrapContentWidth(unbounded = true),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimedLyricText(
                text = primaryText,
                words = primaryWords,
                positionMs = positionMs,
                active = active,
                style = primaryStyle,
                contentColor = contentColor,
                wordLiftEnabled = wordLiftEnabled,
                sustainThresholdMs = sustainThresholdMs,
                singleLine = true,
                modifier = Modifier.wrapContentWidth(unbounded = true)
            )
            BasicText(
                text = " ${secondaryText.trim()}",
                style = primaryStyle.copy(
                    color = contentColor.copy(
                        alpha = primaryStyle.color.alpha * secondaryAlpha.coerceIn(0f, 1f)
                    ),
                    textAlign = TextAlign.Start
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                // A completed word stays lifted in the primary karaoke renderer.  Move the
                // static secondary as a whole by the same amount once the primary line ends so
                // the two pieces keep their baseline instead of visually splitting apart.
                modifier = Modifier.graphicsLayer { translationY = -secondaryLiftPx }
            )
        }
    }
}

private const val STATUS_BAR_SECONDARY_LIFT_DURATION_MS = 120f
private const val STATUS_BAR_SECONDARY_LIFT_ANIMATION_MS = 110

private fun LyricLine.isBackgroundActiveAt(positionMs: Long): Boolean {
    val start = backgroundStartMs ?: backgroundWords.minOfOrNull { it.startMs } ?: return false
    val end = backgroundEndMs ?: backgroundWords.maxOfOrNull { it.endMs } ?: endMs ?: return false
    return positionMs in start until end.coerceAtLeast(start + 1L)
}

internal fun LyricLine.duetTextAlign(default: TextAlign): TextAlign = when {
    agent.equals("v2", true) -> TextAlign.End
    agent.equals("v1", true) -> TextAlign.Start
    else -> default
}
