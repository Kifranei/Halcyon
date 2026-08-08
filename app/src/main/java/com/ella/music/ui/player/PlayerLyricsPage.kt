package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import kotlin.math.abs

@Composable
internal fun LyricsPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    annotation: String,
    lyrics: List<LyricLine>,
    lyricsLoading: Boolean,
    currentLyricIndex: Int,
    currentPosition: Long,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    keepScreenOn: Boolean,
    appleMusicWordLiftEnabled: Boolean,
    lyricFormatAvailability: MusicRepository.LyricFormatAvailability,
    preferTtmlLyrics: Boolean?,
    lyricSourceMode: Int,
    lyricParserEngine: Int,
    layoutProfile: PlayerLyricLayoutProfile,
    fontFamily: FontFamily?,
    translationFontFamily: FontFamily? = fontFamily,
    fontPath: String,
    fontWeight: FontWeight,
    italic: Boolean,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    perspectiveEffect: Boolean,
    perspectiveYAngle: Int,
    lyricTextAlign: Int,
    palette: PlayerPalette,
    flowEffectMode: Int,
    currentPositionMs: Long,
    isPlaying: Boolean,
    isPaused: Boolean = !isPlaying,
    playerBackgroundEnabled: Boolean,
    playerBackgroundUri: String,
    playerBackgroundOpacity: Float,
    playerBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    dynamicFlowEnabled: Boolean,
    isFavorite: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float,
    onLineClick: (LyricLine) -> Unit,
    onLineDoubleClick: () -> Unit,
    onLineLongClick: (LyricLine) -> Unit,
    onDismissLyrics: () -> Unit,
    onTogglePronunciation: () -> Unit,
    onToggleTranslation: () -> Unit,
    onToggleKeepScreenOn: () -> Unit,
    onTogglePerspectiveEffect: () -> Unit,
    onPerspectiveYAngle: (Int) -> Unit,
    onToggleFavorite: () -> Unit,
    onFontScale: (Float) -> Unit,
    onSecondaryFontScale: (Float) -> Unit,
    onLyricSourceMode: (Int) -> Unit,
    onLyricFormatPreference: (Boolean) -> Unit,
    onLyricParserEngine: (Int) -> Unit,
    onArtist: () -> Unit,
    onPrimaryTextSize: (Float) -> Unit,
    onSecondaryTextSize: (Float) -> Unit,
    enableSwipeDismiss: Boolean,
    backEnabled: Boolean = true,
    pageVisible: Boolean = true,
    useBlurBackground: Boolean,
    drawBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    var lyricMenuExpanded by remember { mutableStateOf(false) }
    var dismissDragX by remember { mutableFloatStateOf(0f) }
    val activeAgentLabel = remember(lyrics, currentPosition) {
        lyrics.activeTtmlAgentLabel(currentPosition)
    }

    val swipeDismissModifier = if (enableSwipeDismiss) {
        Modifier.pointerInput(onDismissLyrics) {
            detectDragGestures(
                onDragStart = { dismissDragX = 0f },
                onDrag = { change, dragAmount ->
                    if (abs(dragAmount.x) > abs(dragAmount.y)) {
                        dismissDragX += dragAmount.x
                        change.consume()
                    }
                },
                onDragCancel = { dismissDragX = 0f },
                onDragEnd = {
                    if (dismissDragX > 96.dp.toPx()) {
                        onDismissLyrics()
                    }
                    dismissDragX = 0f
                }
            )
        }
    } else {
        Modifier
    }

    BackHandler(enabled = backEnabled, onBack = onDismissLyrics)

    Box(modifier = modifier.then(swipeDismissModifier)) {
        val useCustomPlayerBackground = playerBackgroundEnabled && playerBackgroundUri.isNotBlank() && !useBlurBackground
        if (drawBackground) {
            SharedPlayerPageBackground(
                song = song,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                palette = palette,
                currentPositionMs = currentPositionMs,
                isPlaying = isPlaying,
                playerBackgroundEnabled = playerBackgroundEnabled,
                playerBackgroundUri = playerBackgroundUri,
                playerBackgroundOpacity = playerBackgroundOpacity,
                playerBackgroundDim = playerBackgroundDim,
                beautifulLyricsBackground = beautifulLyricsBackground,
                dynamicFlowEnabled = dynamicFlowEnabled,
                useBlurBackground = useBlurBackground,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(horizontal = 28.dp)
        ) {
            LyricsPlayerHeader(
                song = song,
                embeddedCover = embeddedCover,
                annotation = annotation,
                activeSinger = activeAgentLabel,
                isFavorite = isFavorite,
                onDismissLyrics = onDismissLyrics,
                onArtist = onArtist,
                onToggleFavorite = onToggleFavorite,
                onShowMenu = { lyricMenuExpanded = true },
                fontFamily = fontFamily,
                modifier = Modifier.padding(top = 28.dp)
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .playerLyricPerspective(
                        enabled = perspectiveEffect,
                        angle = perspectiveYAngle,
                        lyricTextAlign = lyricTextAlign
                    )
            ) {
        if (!lyricsLoading) {
                    AppleMusicLyricsView(
                        lyrics = lyrics,
                        currentIndex = currentLyricIndex,
                        currentPositionMs = currentPositionMs,
                        isPlaying = isPlaying,
                        isPaused = isPaused,
                        pageVisible = pageVisible,
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
                        onLineDoubleClick = onLineDoubleClick,
                        onLineLongClick = onLineLongClick,
                        nonCurrentLineBlurEnabled = !useCustomPlayerBackground,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        AudioVisualizer(
            enabled = visualizerEnabled && pageVisible,
            audioSessionId = audioSessionId,
            isPlaying = isPlaying && pageVisible,
            positionMs = currentPositionMs,
            opacity = visualizerOpacity,
            accent = palette.onBackground.copy(alpha = 0.86f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(42.dp)
        )

        LyricsPlayerMenuSheet(
            show = lyricMenuExpanded,
            showPronunciation = showPronunciation,
            showTranslation = showTranslation,
            keepScreenOn = keepScreenOn,
            perspectiveEffect = perspectiveEffect,
            perspectiveYAngle = perspectiveYAngle,
            lyricFormatAvailability = lyricFormatAvailability,
            preferTtmlLyrics = preferTtmlLyrics,
            lyricSourceMode = lyricSourceMode,
            lyricParserEngine = lyricParserEngine,
            layoutProfile = layoutProfile,
            fontScale = fontScale,
            secondaryFontScale = secondaryFontScale,
            primaryTextSizeSp = primaryTextSizeSp,
            secondaryTextSizeSp = secondaryTextSizeSp,
            onDismiss = { lyricMenuExpanded = false },
            onTogglePronunciation = {
                lyricMenuExpanded = false
                onTogglePronunciation()
            },
            onToggleTranslation = {
                lyricMenuExpanded = false
                onToggleTranslation()
            },
            onToggleKeepScreenOn = {
                lyricMenuExpanded = false
                onToggleKeepScreenOn()
            },
            onTogglePerspectiveEffect = {
                onTogglePerspectiveEffect()
            },
            onPerspectiveYAngle = onPerspectiveYAngle,
            onLyricSourceMode = { mode ->
                lyricMenuExpanded = false
                onLyricSourceMode(mode)
            },
            onLyricFormatPreference = { preferTtml ->
                lyricMenuExpanded = false
                onLyricFormatPreference(preferTtml)
            },
            onLyricParserEngine = { engine ->
                lyricMenuExpanded = false
                onLyricParserEngine(engine)
            },
            onFontScale = onFontScale,
            onSecondaryFontScale = onSecondaryFontScale,
            onPrimaryTextSize = onPrimaryTextSize,
            onSecondaryTextSize = onSecondaryTextSize,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun List<LyricLine>.activeTtmlAgentLabel(positionMs: Long): String? {
    val names = asSequence()
        .filter { line ->
            line.isTtml &&
                !line.agentName.isNullOrBlank() &&
                positionMs >= line.timeMs &&
                positionMs < (line.endMs ?: (line.timeMs + 3_500L))
        }
        .mapNotNull { it.agentName?.trim()?.takeIf(String::isNotBlank) }
        .distinct()
        .take(3)
        .toList()
    if (names.isEmpty()) return null
    return names.joinToString("/")
}
