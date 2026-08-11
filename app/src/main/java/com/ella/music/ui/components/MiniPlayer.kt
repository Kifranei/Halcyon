package com.ella.music.ui.components

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ella.music.R
import com.ella.music.data.BottomBarGlassEffect
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.drawBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import kotlin.math.abs
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float = 0f,
    lyricText: String? = null,
    lyricTranslation: String? = null,
    lyricProgress: Float = 0f,
    lyricPositionMs: Long = 0L,
    lyricTiming: MiniPlayerLyricTiming? = null,
    coverRotationEnabled: Boolean = true,
    albumArtUri: Uri? = null,
    loadCoverArt: ((Song) -> Bitmap?)? = null,
    backdrop: Backdrop? = null,
    liquidGlass: Boolean = false,
    glassEffect: BottomBarGlassEffect = BottomBarGlassEffect.Blur,
    disableRefraction: Boolean = false,
    showQueueButton: Boolean = false,
    swipeUpToOpenPlayer: Boolean = true,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit = {},
    onSkipNext: () -> Unit = {},
    onShowQueue: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val coverState = rememberMiniPlayerCoverModel(song, albumArtUri, loadCoverArt)
    val shape = RoundedCornerShape(if (liquidGlass) 32.dp else 0.dp)
    val glassBackdrop = if (liquidGlass) backdrop else null
    val useGlassLayout = liquidGlass
    val isLight = MiuixTheme.colorScheme.background.simpleLuminance() > 0.5f
    val surfaceContainer = MiuixTheme.colorScheme.surfaceContainer
    val glassSurface = bottomBarGlassContainerColor(
        isLight = isLight,
        glassEffect = glassEffect,
        lightAlpha = 0.44f,
        darkAlpha = 0.50f,
        lightLiquidAlpha = 0.34f,
        darkLiquidAlpha = 0.38f
    )
    val textState = rememberMiniPlayerTextState(song, lyricText, lyricTranslation)
    var transitionDirection by remember { mutableIntStateOf(1) }
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = if (useGlassLayout) 16.dp else 0.dp, vertical = if (useGlassLayout) 2.dp else 0.dp)
            .pointerInput(song.id) {
                var dragAmount = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragAmount = 0f },
                    onHorizontalDrag = { change, amount ->
                        dragAmount += amount
                        change.consume()
                    },
                    onDragEnd = {
                        if (abs(dragAmount) > 96f) {
                            if (dragAmount < 0f) {
                                transitionDirection = 1
                                onSkipNext()
                            } else {
                                transitionDirection = -1
                                onSkipPrevious()
                            }
                        }
                        dragAmount = 0f
                    },
                    onDragCancel = { dragAmount = 0f }
                )
            }
            .pointerInput(song.id, swipeUpToOpenPlayer) {
                var verticalDragAmount = 0f
                detectVerticalDragGestures(
                    onDragStart = { verticalDragAmount = 0f },
                    onVerticalDrag = { change, amount ->
                        verticalDragAmount += amount
                        change.consume()
                    },
                    onDragEnd = {
                        if (swipeUpToOpenPlayer && verticalDragAmount < -48f) onClick()
                        verticalDragAmount = 0f
                    },
                    onDragCancel = { verticalDragAmount = 0f }
                )
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .then(
                if (glassBackdrop != null) {
                    Modifier
                        .dropShadow(
                            shape = shape,
                            shadow = Shadow(
                                radius = 10.dp,
                                color = Color.Black,
                                alpha = if (!isLight) 0.2f else 0.1f,
                            ),
                        )
                        .clip(shape)
                        .drawBackdrop(
                            backdrop = glassBackdrop,
                            shape = { shape },
                            effects = {
                                applyBottomBarGlassEffect(
                                    glassEffect = glassEffect,
                                    blurRadius = 42f,
                                    liquidBlurRadius = 12f,
                                    disableRefraction = disableRefraction
                                )
                            },
                            highlight = {
                                Highlight.Default.copy(
                                    alpha = when (glassEffect) {
                                        BottomBarGlassEffect.Blur -> if (isLight) 0.26f else 0.16f
                                        BottomBarGlassEffect.LiquidGlass -> if (isLight) 0.18f else 0.10f
                                    }
                                )
                            },
                            onDrawSurface = {
                                drawRect(glassSurface)
                            }
                        )
                        .liquidGlassDepthOverlay(
                            enabled = false,
                            isLight = isLight
                        )
                } else if (useGlassLayout) {
                    Modifier
                        .clip(shape)
                        .background(glassSurface, shape)
                        .liquidGlassDepthOverlay(
                            enabled = false,
                            isLight = isLight
                        )
                } else {
                    Modifier.background(surfaceContainer)
                }
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniPlayerCoverProgress(
            coverState = coverState,
            isPlaying = isPlaying,
            progress = progress,
            coverRotationEnabled = coverRotationEnabled,
            coverSize = 44.dp,
            ringSize = 50.dp
        )

        Spacer(modifier = Modifier.width(8.dp))

        MiniPlayerAnimatedText(
            textState = textState,
            transitionDirection = transitionDirection,
            lyricProgress = lyricProgress,
            lyricPositionMs = lyricPositionMs,
            lyricTiming = lyricTiming,
            isPlaying = isPlaying,
            modifier = Modifier.weight(1f)
        )

        if (!showQueueButton) {
            IconButton(
                onClick = {
                    transitionDirection = -1
                    onSkipPrevious()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_skip_previous),
                    contentDescription = stringResource(R.string.common_previous),
                    tint = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        IconButton(
            onClick = onPlayPause,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play_legacy),
                contentDescription = if (isPlaying) stringResource(R.string.common_pause) else stringResource(R.string.common_play),
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }

        IconButton(
            onClick = {
                transitionDirection = 1
                onSkipNext()
            },
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.common_next),
                tint = MiuixTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp)
            )
        }

        if (showQueueButton) {
            IconButton(
                onClick = onShowQueue,
                modifier = Modifier.size(40.dp)
            ) {
                PlayerQueueListIcon(
                    contentDescription = stringResource(R.string.player_queue_title),
                    color = MiuixTheme.colorScheme.onSurface,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}
@Composable
fun CompactMiniPlayer(
    song: Song,
    isPlaying: Boolean,
    progress: Float = 0f,
    lyricText: String? = null,
    lyricTranslation: String? = null,
    lyricProgress: Float = 0f,
    lyricPositionMs: Long = 0L,
    lyricTiming: MiniPlayerLyricTiming? = null,
    coverRotationEnabled: Boolean = true,
    albumArtUri: Uri? = null,
    loadCoverArt: ((Song) -> Bitmap?)? = null,
    backdrop: Backdrop? = null,
    glassEffect: BottomBarGlassEffect = BottomBarGlassEffect.Blur,
    disableRefraction: Boolean = false,
    onClick: () -> Unit,
    onPlayPause: () -> Unit,
    onSkipNext: () -> Unit = {},
    showSkipButton: Boolean = true,
    swipeUpToOpenPlayer: Boolean = true,
    modifier: Modifier = Modifier
) {
    val coverState = rememberMiniPlayerCoverModel(song, albumArtUri, loadCoverArt)
    val textState = rememberMiniPlayerTextState(song, lyricText, lyricTranslation)
    var transitionDirection by remember { mutableIntStateOf(1) }

    GlassPill(
        backdrop = backdrop,
        modifier = modifier.height(64.dp),
        shape = RoundedCornerShape(32.dp),
        glassEffect = glassEffect,
        disableRefraction = disableRefraction
    ) {
        Row(
            modifier = Modifier
                .height(64.dp)
                .padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(64.dp)
                    .pointerInput(song.id, swipeUpToOpenPlayer) {
                        var verticalDragAmount = 0f
                        detectVerticalDragGestures(
                            onDragStart = { verticalDragAmount = 0f },
                            onVerticalDrag = { change, amount ->
                                verticalDragAmount += amount
                                change.consume()
                            },
                            onDragEnd = {
                                if (swipeUpToOpenPlayer && verticalDragAmount < -48f) onClick()
                                verticalDragAmount = 0f
                            },
                            onDragCancel = { verticalDragAmount = 0f }
                        )
                    }
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick
                    ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MiniPlayerCoverProgress(
                    coverState = coverState,
                    isPlaying = isPlaying,
                    progress = progress,
                    coverRotationEnabled = coverRotationEnabled,
                    coverSize = 38.dp,
                    ringSize = 44.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                MiniPlayerAnimatedText(
                    textState = textState,
                    transitionDirection = transitionDirection,
                    lyricProgress = lyricProgress,
                    lyricPositionMs = lyricPositionMs,
                    lyricTiming = lyricTiming,
                    isPlaying = isPlaying,
                    modifier = Modifier.weight(1f),
                    primaryFontSize = 14,
                    primaryFontWeight = FontWeight.SemiBold,
                    secondaryFontSize = 12
                )
            }
            IconButton(
                onClick = onPlayPause,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    painter = painterResource(id = if (isPlaying) R.drawable.ic_player_pause else R.drawable.ic_player_play_legacy),
                    contentDescription = if (isPlaying) stringResource(R.string.common_pause) else stringResource(R.string.common_play),
                    tint = MiuixTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (showSkipButton) {
                IconButton(
                    onClick = {
                        transitionDirection = 1
                        onSkipNext()
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_skip_next),
                        contentDescription = stringResource(R.string.common_next),
                        tint = MiuixTheme.colorScheme.onSurface,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
