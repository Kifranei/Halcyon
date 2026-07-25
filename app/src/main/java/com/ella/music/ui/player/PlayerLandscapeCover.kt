package com.ella.music.ui.player

import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun LandscapeCoverPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    annotation: String,
    dynamicCoverSource: DynamicCoverSource?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    audioInfo: AudioInfo?,
    hiResLogoEnabled: Boolean,
    hiResLogoUri: String,
    palette: PlayerPalette,
    lyrics: List<LyricLine>,
    lyricsLoading: Boolean,
    currentLyricIndex: Int,
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
    lyricPerspectiveEffect: Boolean,
    lyricPerspectiveYAngle: Int,
    lyricTextAlign: Int,
    showTotalDuration: Boolean,
    playerTapSeekEnabled: Boolean,
    coverSwipeEnabled: Boolean,
    queueExpanded: Boolean,
    playlist: List<Song>,
    queueLocked: Boolean,
    favoriteSongKeys: Set<String> = emptySet(),
    loadSongRating: (Song) -> Int = { 0 },
    ratingRevision: Int = 0,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float,
    flowEffectMode: Int,
    dynamicFlowEnabled: Boolean,
    customBackgroundUri: String,
    customBackgroundOpacity: Float,
    customBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    onDynamicCoverFailed: (String) -> Unit,
    isFavorite: Boolean,
    onToggleMenu: () -> Unit,
    onToggleFavorite: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onToggleQueueLock: () -> Unit,
    onShowLyrics: () -> Unit,
    onLyricLineClick: (LyricLine) -> Unit,
    onLyricLineLongClick: (LyricLine) -> Unit,
    onSeek: (Float) -> Unit,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onSwipePrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onRemoveQueueSong: (Int) -> Unit,
    onMoveQueueSong: (Int, Int) -> Unit,
    onRandomizeQueue: () -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    onLineClick: () -> Unit,
    onArtist: () -> Unit,
    onDismiss: () -> Unit = {},
    drawBackground: Boolean = true,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val bluetoothDeviceName = rememberBluetoothOutputName()
    val hasLyrics = lyrics.isNotEmpty()
    val songLayoutKey = song?.playlistIdentityKey() ?: song?.id?.toString().orEmpty()
    var lyricDecisionPending by remember(songLayoutKey) { mutableStateOf(songLayoutKey.isNotBlank()) }
    LaunchedEffect(songLayoutKey, lyricsLoading, hasLyrics) {
        when {
            songLayoutKey.isBlank() || hasLyrics -> lyricDecisionPending = false
            !lyricsLoading -> {
                delay(120)
                lyricDecisionPending = false
            }
        }
    }
    val showLyricsPane = hasLyrics || lyricsLoading || lyricDecisionPending
    val ultraWideLandscape = isUltraWideLandscapePlayerLayout(
        screenWidthDp = configuration.screenWidthDp,
        screenHeightDp = configuration.screenHeightDp
    )
    val compactPhoneLandscape = configuration.smallestScreenWidthDp < 600 && !ultraWideLandscape
    val showHiResLogo = hiResLogoEnabled && audioInfo?.isHiResLogoTrack() == true
    val swipeThresholdPx = with(LocalDensity.current) { 84.dp.toPx() }
    val swipeScope = rememberCoroutineScope()
    val dragOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    val leftPaneWeight = if (showLyricsPane && ultraWideLandscape) 0.34f else 0.38f
    val rightPaneWeight = if (ultraWideLandscape) 0.66f else 0.62f
    val headerTitleFontSize = if (showLyricsPane) {
        if (ultraWideLandscape) 18.sp else 20.sp
    } else {
        if (ultraWideLandscape) 20.sp else 24.sp
    }
    val headerArtistFontSize = if (showLyricsPane) {
        if (ultraWideLandscape) 11.sp else 12.sp
    } else {
        if (ultraWideLandscape) 13.sp else 16.sp
    }
    val coverWidthFraction = when {
        ultraWideLandscape && showLyricsPane -> 0.72f
        ultraWideLandscape -> 0.70f
        showLyricsPane -> 0.88f
        else -> 0.78f
    }
    val coverMaxSize = when {
        ultraWideLandscape && showLyricsPane -> 300.dp
        ultraWideLandscape -> 340.dp
        showLyricsPane -> 520.dp
        else -> 620.dp
    }
    val lyricPrimaryTextSize = primaryTextSizeSp
    val lyricSecondaryTextSize = secondaryTextSizeSp
    val lyricTopPadding = if (ultraWideLandscape) 0.dp else 8.dp
    val foregroundDynamicCoverSource = dynamicCoverSource?.takeUnless { it.preferLandscapeBackground }

    if (compactPhoneLandscape) {
        CompactPhoneLandscapeCoverPlayerPage(
            song = song,
            embeddedCover = embeddedCover,
            paletteBitmap = paletteBitmap,
            annotation = annotation,
            dynamicCoverSource = dynamicCoverSource,
            isPlaying = isPlaying,
            currentPosition = currentPosition,
            duration = duration,
            audioInfo = audioInfo,
            showHiResLogo = showHiResLogo,
            hiResLogoUri = hiResLogoUri,
            palette = palette,
            lyrics = lyrics,
            lyricsLoading = lyricsLoading,
            currentLyricIndex = currentLyricIndex,
            showTranslation = showTranslation,
            showPronunciation = showPronunciation,
            appleMusicWordLiftEnabled = appleMusicWordLiftEnabled,
            fontFamily = fontFamily,
            translationFontFamily = translationFontFamily,
            fontPath = fontPath,
            fontWeight = fontWeight,
            fontScale = fontScale,
            secondaryFontScale = secondaryFontScale,
            primaryTextSizeSp = primaryTextSizeSp,
            secondaryTextSizeSp = secondaryTextSizeSp,
            lyricPerspectiveEffect = lyricPerspectiveEffect,
            lyricPerspectiveYAngle = lyricPerspectiveYAngle,
            lyricTextAlign = lyricTextAlign,
            showTotalDuration = showTotalDuration,
            playerTapSeekEnabled = playerTapSeekEnabled,
            coverSwipeEnabled = coverSwipeEnabled,
            audioSessionId = audioSessionId,
            visualizerEnabled = visualizerEnabled,
            visualizerOpacity = visualizerOpacity,
            flowEffectMode = flowEffectMode,
            dynamicFlowEnabled = dynamicFlowEnabled,
            customBackgroundUri = customBackgroundUri,
            customBackgroundOpacity = customBackgroundOpacity,
            customBackgroundDim = customBackgroundDim,
            beautifulLyricsBackground = beautifulLyricsBackground,
            onDynamicCoverFailed = onDynamicCoverFailed,
            isFavorite = isFavorite,
            onToggleMenu = onToggleMenu,
            onToggleFavorite = onToggleFavorite,
            onSeek = onSeek,
            onPrevious = onPrevious,
            onSwipePrevious = onSwipePrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onLyricLineClick = onLyricLineClick,
            onLyricLineLongClick = onLyricLineLongClick,
            onShowLyrics = onShowLyrics,
            onArtist = onArtist,
            drawBackground = drawBackground,
            modifier = modifier
        )
        return
    }

    Box(modifier = modifier.then(if (drawBackground) Modifier.background(palette.middle) else Modifier)) {
        if (drawBackground) {
            LandscapeCoverModeBackground(
                palette = palette,
                dynamicCoverSource = dynamicCoverSource,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                currentPosition = currentPosition,
                isPlaying = isPlaying,
                flowEffectMode = flowEffectMode,
                dynamicFlowEnabled = dynamicFlowEnabled,
                visualizerEnabled = visualizerEnabled,
                visualizerOpacity = visualizerOpacity,
                customBackgroundUri = customBackgroundUri,
                customBackgroundOpacity = customBackgroundOpacity,
                customBackgroundDim = customBackgroundDim,
                beautifulLyricsBackground = beautifulLyricsBackground,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colorStops = arrayOf(
                                0.00f to Color.Black.copy(alpha = 0.04f),
                                0.34f to Color.Transparent,
                                0.50f to palette.middle.copy(alpha = 0.08f),
                                0.66f to Color.Transparent,
                                1.00f to Color.Black.copy(alpha = 0.05f)
                            )
                        )
                    )
            )
        }
        Row(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(
                    horizontal = if (ultraWideLandscape) 24.dp else 32.dp,
                    vertical = if (ultraWideLandscape) 14.dp else 22.dp
                ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (showLyricsPane) Arrangement.Start else Arrangement.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    .then(
                        if (showLyricsPane) Modifier.weight(leftPaneWeight)
                        else Modifier.fillMaxWidth(if (ultraWideLandscape) 0.42f else 0.46f)
                    ),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (showLyricsPane) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerSongMetaText(
                            song = song,
                            annotation = annotation,
                            titleFontSize = headerTitleFontSize,
                            artistFontSize = headerArtistFontSize,
                            artistAlpha = 0.56f,
                            showArtistWithAnnotation = true,
                            contentColor = palette.onBackground,
                            fontFamily = fontFamily,
                            onArtistClick = onArtist,
                            modifier = Modifier.weight(1f)
                        )
                        PlayerHeaderAction(
                            kind = PlayerHeaderActionKind.Favorite,
                            selected = isFavorite,
                            onClick = onToggleFavorite
                        )
                        PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PlayerSongMetaText(
                            song = song,
                            annotation = annotation,
                            titleFontSize = headerTitleFontSize,
                            artistFontSize = headerArtistFontSize,
                            artistAlpha = 0.62f,
                            showArtistWithAnnotation = true,
                            contentColor = palette.onBackground,
                            fontFamily = fontFamily,
                            onArtistClick = onArtist,
                            modifier = Modifier.weight(1f)
                        )
                        PlayerHeaderAction(
                            kind = PlayerHeaderActionKind.Favorite,
                            selected = isFavorite,
                            onClick = onToggleFavorite
                        )
                        PlayerHeaderAction(kind = PlayerHeaderActionKind.More, onClick = onToggleMenu)
                    }
                }
                Spacer(modifier = Modifier.height(if (ultraWideLandscape) 14.dp else 22.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(coverWidthFraction)
                            .widthIn(max = coverMaxSize)
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .then(
                                if (coverSwipeEnabled) {
                                    Modifier.pointerInput(song?.id, onSwipePrevious, onNext) {
                                        detectHorizontalDragGestures(
                                            onDragCancel = {
                                                swipeScope.launch { dragOffset.animateTo(0f) }
                                            },
                                            onDragEnd = {
                                                val travel = dragOffset.value
                                                swipeScope.launch { dragOffset.animateTo(0f) }
                                                when {
                                                    travel > swipeThresholdPx -> onSwipePrevious()
                                                    travel < -swipeThresholdPx -> onNext()
                                                }
                                            },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                swipeScope.launch {
                                                    dragOffset.snapTo(dragOffset.value + dragAmount)
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    Modifier
                                }
                            )
                            .graphicsLayer {
                                translationX = dragOffset.value * 0.35f
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (foregroundDynamicCoverSource != null) {
                            DynamicCoverVideo(
                                source = foregroundDynamicCoverSource,
                                isPlaying = isPlaying,
                                onPlaybackError = { onDynamicCoverFailed(foregroundDynamicCoverSource.failureKey) },
                                modifier = Modifier.fillMaxSize(),
                                cornerRadiusDp = 14f
                            )
                        } else {
                            AlbumArtView(
                                song = song,
                                embeddedCover = embeddedCover,
                                showHiResLogo = showHiResLogo,
                                hiResLogoUri = hiResLogoUri,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        if (showHiResLogo) {
                            HiResLogoBadge(
                                logoUri = hiResLogoUri,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(10.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(if (ultraWideLandscape) 8.dp else 12.dp))
                PlayerProgressBlock(
                    currentPosition = currentPosition,
                    duration = duration,
                    audioInfo = audioInfo,
                    bluetoothDeviceName = bluetoothDeviceName,
                    palette = palette,
                    allowTapSeek = playerTapSeekEnabled,
                    showTotalDuration = showTotalDuration,
                    onSeek = onSeek
                )
                Spacer(modifier = Modifier.height(if (ultraWideLandscape) 0.dp else 4.dp))
                PlayerTransportControls(
                    isPlaying = isPlaying,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    palette = palette,
                    queueExpanded = queueExpanded,
                    playlist = playlist,
                    currentSongKey = song?.playlistIdentityKey(),
                    queueLocked = queueLocked,
                    favoriteSongKeys = favoriteSongKeys,
                    loadSongRating = loadSongRating,
                    ratingRevision = ratingRevision,
                    onCyclePlaybackMode = onCyclePlaybackMode,
                    onToggleQueueLock = onToggleQueueLock,
                    onPrevious = onPrevious,
                    onPlayPause = onPlayPause,
                    onNext = onNext,
                    onToggleQueue = onToggleQueue,
                    onDismissQueue = onDismissQueue,
                    onQueueSongClick = onQueueSongClick,
                    onRemoveQueueSong = onRemoveQueueSong,
                    onMoveQueueSong = onMoveQueueSong,
                    onRandomizeQueue = onRandomizeQueue,
                    onAddQueueToPlaylist = onAddQueueToPlaylist,
                    onClearQueue = onClearQueue
                )
            }
            if (showLyricsPane) {
                Spacer(modifier = Modifier.width(if (ultraWideLandscape) 28.dp else 48.dp))
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(rightPaneWeight)
                        .widthIn(max = if (ultraWideLandscape) 940.dp else 840.dp)
                        .padding(start = 0.dp)
                        .playerLyricPerspective(
                            enabled = lyricPerspectiveEffect,
                            angle = lyricPerspectiveYAngle,
                            lyricTextAlign = lyricTextAlign
                        )
                ) {
                    if (hasLyrics) {
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
                                primaryTextSizeSp = lyricPrimaryTextSize,
                                secondaryTextSizeSp = lyricSecondaryTextSize,
                                lyricTextAlign = lyricTextAlign,
                                contentColor = palette.onBackground,
                                wordLiftEnabled = appleMusicWordLiftEnabled,
                                onLineClick = onLyricLineClick,
                                onLineDoubleClick = {},
                                onLineLongClick = onLyricLineLongClick,
                                topContentPadding = lyricTopPadding,
                                bottomContentPadding = if (ultraWideLandscape) 32.dp else 44.dp,
                                lineSpacing = if (ultraWideLandscape) 18.dp else 21.dp,
                                focusOffsetRatio = if (ultraWideLandscape) 0.20f else 0.22f,
                                // A custom wallpaper is a busy background; blurring far lines makes
                                // them unreadable, so keep all lines sharp when one is set.
                                nonCurrentLineBlurEnabled = customBackgroundUri.isBlank() && !lyricPerspectiveEffect,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight()
                        )
                    }
                }
            }
        }
        AudioVisualizer(
            enabled = visualizerEnabled,
            audioSessionId = audioSessionId,
            isPlaying = isPlaying,
            positionMs = currentPosition,
            opacity = visualizerOpacity,
            accent = palette.accent.copy(alpha = 0.78f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .fillMaxWidth()
                .height(68.dp)
        )
    }
}

@Composable
private fun CompactPhoneLandscapeCoverPlayerPage(
    song: Song?,
    embeddedCover: Bitmap?,
    paletteBitmap: Bitmap?,
    annotation: String,
    dynamicCoverSource: DynamicCoverSource?,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    audioInfo: AudioInfo?,
    showHiResLogo: Boolean,
    hiResLogoUri: String,
    palette: PlayerPalette,
    lyrics: List<LyricLine>,
    lyricsLoading: Boolean,
    currentLyricIndex: Int,
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
    lyricPerspectiveEffect: Boolean,
    lyricPerspectiveYAngle: Int,
    lyricTextAlign: Int,
    showTotalDuration: Boolean,
    playerTapSeekEnabled: Boolean,
    coverSwipeEnabled: Boolean,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float,
    flowEffectMode: Int,
    dynamicFlowEnabled: Boolean,
    customBackgroundUri: String,
    customBackgroundOpacity: Float,
    customBackgroundDim: Float,
    beautifulLyricsBackground: Boolean,
    onDynamicCoverFailed: (String) -> Unit,
    isFavorite: Boolean,
    onToggleMenu: () -> Unit,
    onToggleFavorite: () -> Unit,
    onSeek: (Float) -> Unit,
    onPrevious: () -> Unit,
    onSwipePrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onLyricLineClick: (LyricLine) -> Unit,
    onLyricLineLongClick: (LyricLine) -> Unit,
    onShowLyrics: () -> Unit,
    onArtist: () -> Unit,
    drawBackground: Boolean,
    modifier: Modifier = Modifier
) {
    val swipeThresholdPx = with(LocalDensity.current) { 84.dp.toPx() }
    val swipeScope = rememberCoroutineScope()
    val dragOffset = remember { androidx.compose.animation.core.Animatable(0f) }
    var previewProgress by remember(song?.id, song?.path) { mutableStateOf<Float?>(null) }
    val foregroundDynamicCoverSource = dynamicCoverSource?.takeUnless { it.preferLandscapeBackground }

    fun Modifier.coverSwipeModifier(): Modifier {
        return if (coverSwipeEnabled) {
            pointerInput(song?.id, onSwipePrevious, onNext) {
                detectHorizontalDragGestures(
                    onDragCancel = {
                        swipeScope.launch { dragOffset.animateTo(0f) }
                    },
                    onDragEnd = {
                        val travel = dragOffset.value
                        swipeScope.launch { dragOffset.animateTo(0f) }
                        when {
                            travel > swipeThresholdPx -> onSwipePrevious()
                            travel < -swipeThresholdPx -> onNext()
                        }
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        swipeScope.launch {
                            dragOffset.snapTo(dragOffset.value + dragAmount)
                        }
                    }
                )
            }
        } else {
            this
        }
    }

    Box(modifier = modifier.then(if (drawBackground) Modifier.background(palette.middle) else Modifier)) {
        if (drawBackground) {
            LandscapeCoverModeBackground(
                palette = palette,
                dynamicCoverSource = dynamicCoverSource,
                embeddedCover = embeddedCover,
                paletteBitmap = paletteBitmap,
                currentPosition = currentPosition,
                isPlaying = isPlaying,
                flowEffectMode = flowEffectMode,
                dynamicFlowEnabled = dynamicFlowEnabled,
                visualizerEnabled = visualizerEnabled,
                visualizerOpacity = visualizerOpacity,
                customBackgroundUri = customBackgroundUri,
                customBackgroundOpacity = customBackgroundOpacity,
                customBackgroundDim = customBackgroundDim,
                beautifulLyricsBackground = beautifulLyricsBackground,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f))
            )
        }

        Row(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxHeight()
                    // Keep this half unchanged so the right-side top controls retain their position.
                    .weight(0.50f)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 22.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        // Keep enough space for the right-side title lane on short landscapes.
                        .fillMaxWidth(0.64f)
                        .widthIn(max = 220.dp)
                        .weight(0.82f, fill = false)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(22.dp))
                        .graphicsLayer { translationX = dragOffset.value * 0.32f }
                        .coverSwipeModifier(),
                    contentAlignment = Alignment.Center
                ) {
                    if (foregroundDynamicCoverSource != null) {
                        DynamicCoverVideo(
                            source = foregroundDynamicCoverSource,
                            isPlaying = isPlaying,
                            onPlaybackError = { onDynamicCoverFailed(foregroundDynamicCoverSource.failureKey) },
                            modifier = Modifier.fillMaxSize(),
                            cornerRadiusDp = 22f
                        )
                    } else {
                        PhoneLandscapeCoverImage(
                            song = song,
                            embeddedCover = embeddedCover,
                            showHiResLogo = showHiResLogo,
                            hiResLogoUri = hiResLogoUri,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                GlowSeekBar(
                    value = if (duration > 0L) currentPosition.toFloat() / duration.toFloat() else 0f,
                    onSeek = onSeek,
                    accent = palette.accent,
                    allowTapSeek = playerTapSeekEnabled,
                    onPreviewProgressChange = { previewProgress = it },
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .padding(top = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .padding(top = 3.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = formatTime(currentPosition),
                            fontSize = 12.sp,
                            color = palette.onBackground.copy(alpha = if (previewProgress == null) 0.76f else 0.48f)
                        )
                        previewProgress?.let { progress ->
                            Text(
                                text = formatTime((duration * progress).toLong()),
                                fontSize = 12.sp,
                                color = palette.onBackground.copy(alpha = 0.84f),
                                modifier = Modifier.padding(start = 4.dp)
                            )
                        }
                    }
                    Text(
                        text = formatTime(duration.coerceAtLeast(0L)),
                        fontSize = 12.sp,
                        color = palette.onBackground.copy(alpha = 0.76f)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .weight(0.50f)
                    .padding(start = 18.dp, end = 26.dp)
            ) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        // Stay below the status bar, but keep these controls visually higher.
                        .padding(top = 10.dp),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Keep the title in the same lane as playback controls on compact phones.
                        PlayerSongTitleText(
                            text = song?.title?.takeIf { it.isNotBlank() }
                                ?: stringResource(R.string.app_name),
                            color = palette.onBackground.copy(alpha = 0.96f),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = fontFamily,
                            textAlign = TextAlign.End,
                            modifier = Modifier.weight(1f)
                        )
                        CompactLandscapeIconButton(onClick = onPrevious) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_skip_previous),
                                contentDescription = stringResource(R.string.common_previous),
                                tint = palette.onBackground.copy(alpha = 0.94f),
                                modifier = Modifier.size(27.dp)
                            )
                        }
                        CompactLandscapeIconButton(onClick = onPlayPause) {
                            CenteredPlayPauseGlyph(
                                isPlaying = isPlaying,
                                tint = palette.onBackground.copy(alpha = 0.96f),
                                modifier = Modifier.size(31.dp)
                            )
                        }
                        CompactLandscapeIconButton(onClick = onNext) {
                            Icon(
                                painter = painterResource(id = R.drawable.ic_skip_next),
                                contentDescription = stringResource(R.string.common_next),
                                tint = palette.onBackground.copy(alpha = 0.94f),
                                modifier = Modifier.size(27.dp)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxSize()
                        .widthIn(max = 900.dp)
                        .windowInsetsPadding(WindowInsets.statusBars)
                        .windowInsetsPadding(WindowInsets.navigationBars)
                        // The compact landscape title/playback row only needs a short lane;
                        // a 118dp gap made the lyric panel look detached from it.
                        .padding(top = 82.dp, bottom = 24.dp)
                        .playerLyricPerspective(
                            enabled = lyricPerspectiveEffect,
                            angle = lyricPerspectiveYAngle,
                            lyricTextAlign = lyricTextAlign
                        )
                ) {
                    if (lyrics.isNotEmpty()) {
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
                                onLineClick = onLyricLineClick,
                                onLineDoubleClick = {},
                                onLineLongClick = onLyricLineLongClick,
                                topContentPadding = 0.dp,
                                bottomContentPadding = 28.dp,
                                lineSpacing = 18.dp,
                                focusOffsetRatio = 0.18f,
                                nonCurrentLineBlurEnabled = customBackgroundUri.isBlank() && !lyricPerspectiveEffect,
                                modifier = Modifier.fillMaxSize()
                        )
                    } else if (!lyricsLoading) {
                        Text(
                            text = stringResource(R.string.player_no_lyrics),
                            color = palette.onBackground.copy(alpha = 0.54f),
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = fontFamily,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.Center)
                                .playerNoIndicationClick(onShowLyrics)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactLandscapeIconButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .playerNoIndicationClick(onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun PhoneLandscapeCoverImage(
    song: Song?,
    embeddedCover: Bitmap?,
    showHiResLogo: Boolean,
    hiResLogoUri: String,
    modifier: Modifier = Modifier
) {
    val coverModel = embeddedCover
        ?: song?.coverUrl?.takeIf { it.isNotBlank() }
        ?: song?.albumId
            ?.takeIf { it > 0L }
            ?.let { Uri.parse("content://media/external/audio/albumart/$it") }

    Box(
        modifier = modifier.background(MiuixTheme.colorScheme.surfaceContainer),
        contentAlignment = Alignment.Center
    ) {
        if (coverModel != null) {
            SafeCoverImage(
                model = coverModel,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                sizePx = 960,
                loadOriginal = true,
                showDefaultPlaceholder = false
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
        if (showHiResLogo) {
            HiResLogoBadge(
                logoUri = hiResLogoUri,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp)
            )
        }
    }
}
