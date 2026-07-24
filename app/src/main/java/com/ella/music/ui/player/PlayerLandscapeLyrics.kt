package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Photos

@Composable
internal fun LandscapeLyricsOverlay(
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    annotation: String,
    dynamicCoverSource: DynamicCoverSource?,
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    appleMusicWordLiftEnabled: Boolean,
    fontFamily: FontFamily?,
    translationFontFamily: FontFamily? = fontFamily,
    fontPath: String,
    fontWeight: FontWeight,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    lyricTextAlign: Int,
    lyricPerspectiveEffect: Boolean,
    lyricPerspectiveYAngle: Int,
    showTotalDuration: Boolean,
    palette: PlayerPalette,
    flowEffectMode: Int,
    isPlaying: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float,
    beautifulLyricsBackground: Boolean,
    onLineClick: (LyricLine) -> Unit,
    onLineLongClick: (LyricLine) -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onShowCoverPlayer: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val ultraWideLandscape = isUltraWideLandscapePlayerLayout(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp
    )
    val leftPaneWeight = if (ultraWideLandscape) 0.28f else 0.33f
    val rightPaneWeight = if (ultraWideLandscape) 0.72f else 0.67f
    val paneSpacer = if (ultraWideLandscape) 24.dp else 34.dp
    val coverMaxSize = if (ultraWideLandscape) 280.dp else 400.dp
    val topButtonPaddingTop = if (ultraWideLandscape) 18.dp else 26.dp
    val topButtonSize = if (ultraWideLandscape) 48.dp else 56.dp
    val topIconSize = if (ultraWideLandscape) 24.dp else 26.dp

    BackHandler(onBack = onShowCoverPlayer)

    Box(modifier = modifier.background(palette.middle)) {
        if (dynamicCoverSource?.preferLandscapeBackground == true) {
            DynamicCoverVideo(
                source = dynamicCoverSource,
                isPlaying = isPlaying,
                onPlaybackError = {},
                modifier = Modifier.fillMaxSize(),
                cornerRadiusDp = 0f,
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.28f))
            )
        } else if (beautifulLyricsBackground) {
            BeautifulLyricsDynamicBackground(
                palette = palette,
                coverBitmap = embeddedCover ?: paletteBitmap,
                positionMs = currentPosition,
                isPlaying = isPlaying,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            FluidLyricBackground(
                palette = palette,
                positionMs = currentPosition,
                isPlaying = isPlaying,
                flowEffectMode = flowEffectMode,
                modifier = Modifier.fillMaxSize()
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    start = if (ultraWideLandscape) 24.dp else 34.dp,
                    end = if (ultraWideLandscape) 36.dp else 48.dp,
                    top = if (ultraWideLandscape) 14.dp else 22.dp,
                    bottom = if (ultraWideLandscape) 20.dp else 28.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(leftPaneWeight)
                    .widthIn(max = coverMaxSize),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    AlbumArtView(
                        song = song,
                        embeddedCover = embeddedCover,
                        modifier = Modifier
                            .fillMaxWidth(if (ultraWideLandscape) 0.86f else 1f)
                            .widthIn(max = coverMaxSize)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                LandscapeSongTitle(
                    song = song,
                    annotation = annotation,
                    fontFamily = fontFamily,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                LandscapeProgressRow(
                    currentPosition = currentPosition,
                    duration = duration,
                    palette = palette,
                    allowTapSeek = false,
                    showTotalDuration = showTotalDuration,
                    onSeek = onSeek
                )
                LandscapeTransportControls(
                    isPlaying = isPlaying,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    palette = palette,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext
                )
            }
            Spacer(modifier = Modifier.width(paneSpacer))
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(rightPaneWeight)
                    .widthIn(max = if (ultraWideLandscape) 980.dp else 820.dp)
                    .playerLyricPerspective(
                        enabled = lyricPerspectiveEffect,
                        angle = lyricPerspectiveYAngle,
                        lyricTextAlign = lyricTextAlign
                    )
            ) {
                AppleMusicLyricsView(
                        lyrics = lyrics,
                        currentIndex = currentLyricIndex,
                        currentPositionMs = currentPosition,
                        isPlaying = isPlaying,
                        showTranslation = showTranslation,
                        showPronunciation = showPronunciation,
                        fontFamily = fontFamily,
                        translationFontFamily = translationFontFamily,
                        fontWeight = fontWeight,
                        fontScale = fontScale,
                        secondaryFontScale = secondaryFontScale,
                        primaryTextSizeSp = primaryTextSizeSp,
                        secondaryTextSizeSp = secondaryTextSizeSp,
                        lyricTextAlign = lyricTextAlign,
                        contentColor = palette.onBackground,
                        wordLiftEnabled = appleMusicWordLiftEnabled,
                        onLineClick = onLineClick,
                        onLineDoubleClick = {},
                        onLineLongClick = onLineLongClick,
                        topContentPadding = if (ultraWideLandscape) 4.dp else 12.dp,
                        bottomContentPadding = if (ultraWideLandscape) 32.dp else 44.dp,
                        lineSpacing = if (ultraWideLandscape) 18.dp else 21.dp,
                        focusOffsetRatio = if (ultraWideLandscape) 0.20f else 0.22f,
                        nonCurrentLineBlurEnabled = !lyricPerspectiveEffect,
                        modifier = Modifier
                            .fillMaxWidth()
                            .fillMaxHeight()
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = topButtonPaddingTop, end = if (ultraWideLandscape) 76.dp else 92.dp)
                .size(topButtonSize)
                .clip(CircleShape)
                .clickable(onClick = onShowCoverPlayer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = MiuixIcons.Regular.Photos,
                contentDescription = null,
                tint = LocalPlayerContentColor.current.copy(alpha = 0.92f),
                modifier = Modifier.size(topIconSize)
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = topButtonPaddingTop, end = if (ultraWideLandscape) 18.dp else 28.dp)
                .size(topButtonSize)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            CloseIcon(
                color = LocalPlayerContentColor.current.copy(alpha = 0.92f),
                modifier = Modifier.size(topIconSize)
            )
        }
    }

}
