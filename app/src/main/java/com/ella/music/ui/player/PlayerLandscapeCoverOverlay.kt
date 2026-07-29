package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.SettingsManager
import com.ella.music.MusicVideoOffsetsParser
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
internal fun LandscapeCoverPlaybackOverlay(
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
    palette: PlayerPalette,
    lyrics: List<LyricLine>,
    currentLyricIndex: Int,
    showTranslation: Boolean,
    showPronunciation: Boolean,
    fontFamily: FontFamily?,
    translationFontFamily: FontFamily? = fontFamily,
    fontPath: String,
    fontWeight: FontWeight,
    fontScale: Float,
    secondaryFontScale: Float,
    primaryTextSizeSp: Float,
    secondaryTextSizeSp: Float,
    showTotalDuration: Boolean,
    queueExpanded: Boolean,
    playlist: List<Song>,
    audioSessionId: Int,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float,
    coverSwipeEnabled: Boolean,
    flowEffectMode: Int,
    beautifulLyricsBackground: Boolean,
    hideNeighborCoversInitially: Boolean,
    onDynamicCoverFailed: (String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
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
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    onArtist: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val importedMvOffsets by SettingsManager.getInstance(context).musicVideoOffsetsJson.collectAsState(initial = "")
    val mvOffsetMs = remember(dynamicCoverSource?.uri, importedMvOffsets) {
        dynamicCoverSource
            ?.takeIf { it.role == PlayerVideoRole.MusicVideo }
            ?.let { MusicVideoOffsetsParser.loadForSource(context, it.uri, importedMvOffsets).forSource(it.uri) }
            ?: 0L
    }
    val lyricPosition = (currentPosition - mvOffsetMs).coerceAtLeast(0L)
    val songKey = remember(song) { song?.playlistIdentityKey() }
    val coverItems = remember(playlist, songKey) {
        val source = playlist.takeIf { it.isNotEmpty() } ?: listOfNotNull(song)
        val centerIndex = source.indexOfFirst { it.playlistIdentityKey() == songKey }.takeIf { it >= 0 } ?: 0
        listOf(-3, -2, -1, 0, 1, 2, 3)
            .mapNotNull { offset -> source.getOrNull(centerIndex + offset)?.let { offset to it } }
            .ifEmpty { listOfNotNull(song?.let { 0 to it }) }
    }
    val swipeThresholdPx = with(LocalDensity.current) { 92.dp.toPx() }
    val swipeScope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    var coverControlsVisible by remember(songKey) { mutableStateOf(false) }
    var coverControlsInteraction by remember(songKey) { mutableStateOf(0) }
    // Entering through the cover's MV rotation starts in the focused, Spotify-like layout.
    var hideNeighborCovers by remember(songKey, hideNeighborCoversInitially) {
        mutableStateOf(hideNeighborCoversInitially)
    }
    var ktvLyricsEnabled by remember(songKey) { mutableStateOf(false) }
    LaunchedEffect(coverControlsVisible, coverControlsInteraction) {
        if (coverControlsVisible) {
            delay(2_000L)
            coverControlsVisible = false
        }
    }
    suspend fun PointerInputScope.detectCoverSwipeToSkip() {
        detectHorizontalDragGestures(
            onDragCancel = { swipeScope.launch { dragOffset.animateTo(0f) } },
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
                swipeScope.launch { dragOffset.snapTo(dragOffset.value + dragAmount) }
            }
        )
    }

    Box(
        modifier = modifier
            .background(palette.middle)
            .then(
                if (coverSwipeEnabled) {
                    Modifier.pointerInput(onSwipePrevious, onNext) {
                        detectCoverSwipeToSkip()
                    }
                } else {
                    Modifier
                }
            )
    ) {
        LandscapeCoverModeBackground(
            palette = palette,
            dynamicCoverSource = dynamicCoverSource,
            embeddedCover = embeddedCover,
            paletteBitmap = paletteBitmap,
            currentPosition = currentPosition,
            isPlaying = isPlaying,
            flowEffectMode = flowEffectMode,
            // The MV landscape style keeps this focused layout even when no local MV exists.
            // In that case the background falls back to the animated Apple Music flow (or the
            // selected Beautiful Lyrics flow) instead of becoming a static flat colour.
            dynamicFlowEnabled = dynamicCoverSource?.preferLandscapeBackground != true,
            visualizerEnabled = visualizerEnabled,
            visualizerOpacity = visualizerOpacity,
            customBackgroundUri = "",
            customBackgroundOpacity = 1f,
            customBackgroundDim = 0.26f,
            beautifulLyricsBackground = beautifulLyricsBackground,
            modifier = Modifier.fillMaxSize()
        )
        if (ktvLyricsEnabled) {
            MusicVideoKtvLyrics(
                lyrics = lyrics,
                position = lyricPosition,
                videoAspectRatio = dynamicCoverSource?.aspectRatio,
                avoidBottomStartContent = hideNeighborCovers,
                modifier = Modifier.fillMaxSize()
            )
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (!hideNeighborCovers) {
                Text(
                    text = song?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.app_name),
                    color = LocalPlayerContentColor.current.copy(alpha = 0.96f),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 96.dp)
                )
                Text(
                    text = song?.artist?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_unknown_artist),
                    color = LocalPlayerContentColor.current.copy(alpha = 0.52f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 96.dp, vertical = 2.dp)
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = if (hideNeighborCovers) 0.dp else 18.dp)
                    // Follow the finger (damped) so swiping the cover wall feels direct; the
                    // offset springs back to 0 on release while the song change re-centers.
                    .graphicsLayer { translationX = dragOffset.value * 0.5f }
                    .then(
                        if (coverSwipeEnabled) {
                            Modifier.pointerInput(onSwipePrevious, onNext) {
                                detectCoverSwipeToSkip()
                            }
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                when {
                    ktvLyricsEnabled -> Unit
                    hideNeighborCovers -> {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxWidth(0.52f)
                                .fillMaxHeight()
                                .padding(end = 30.dp)
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
                                lyricTextAlign = 0,
                                 contentColor = LocalPlayerContentColor.current,
                                 onLineClick = onLyricLineClick,
                                 onLineDoubleClick = onPlayPause,
                                onLineLongClick = onLyricLineLongClick,
                                topContentPadding = 24.dp,
                                bottomContentPadding = 24.dp,
                                lineSpacing = 16.dp,
                                focusOffsetRatio = 0.28f,
                                modifier = Modifier.fillMaxSize()
                            )
                            }
                        }
                    }
                    else -> {
                        LandscapeCoverStack(
                            currentSong = song,
                            embeddedCover = embeddedCover,
                            dynamicCoverSource = dynamicCoverSource,
                            isPlaying = isPlaying,
                            coverItems = coverItems,
                            onDynamicCoverFailed = onDynamicCoverFailed,
                            onCenterCoverClick = {
                                coverControlsVisible = true
                                coverControlsInteraction++
                            },
                            centerOverlay = if (coverControlsVisible) {
                                {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(Color.Black.copy(alpha = 0.18f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(74.dp)
                                                .clip(CircleShape)
                                                .background(Color.Black.copy(alpha = 0.34f))
                                                .playerNoIndicationClick {
                                                    coverControlsVisible = true
                                                    coverControlsInteraction++
                                                    onPlayPause()
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            CenteredPlayPauseGlyph(
                                                isPlaying = isPlaying,
                                                tint = LocalPlayerContentColor.current.copy(alpha = 0.96f),
                                                modifier = Modifier.size(42.dp)
                                            )
                                        }
                                    }
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            if (!hideNeighborCovers) {
                Spacer(modifier = Modifier.height(8.dp))
            }
            if (!hideNeighborCovers && lyrics.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 58.dp)
                        .padding(horizontal = 34.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Crossfade(targetState = currentLyricIndex, label = "coverOverlayLyric") { lineIndex ->
                        val line = lyrics.getOrNull(lineIndex)
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = line?.text?.trim().orEmpty(),
                                color = LocalPlayerContentColor.current.copy(alpha = 0.92f),
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = fontFamily,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.fillMaxWidth()
                            )
                            val secondary = line?.translation?.trim()
                                ?.takeIf { showTranslation && it.isNotEmpty() }
                            if (secondary != null) {
                                Text(
                                    text = secondary,
                                    color = LocalPlayerContentColor.current.copy(alpha = 0.55f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = translationFontFamily,
                                    textAlign = TextAlign.Center,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 2.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
            }
        }
        if (hideNeighborCovers) {
            CompactLandscapeNowPlaying(
                song = song,
                embeddedCover = embeddedCover,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(start = 26.dp, bottom = 22.dp)
            )
        }
        if (dynamicCoverSource?.preferLandscapeBackground == true) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = 12.dp, start = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LandscapeOverlayIconButton(
                    icon = MiuixIcons.Regular.Back,
                    description = stringResource(R.string.common_back),
                    selected = false,
                    onClick = onDismiss
                )
                if (lyrics.isNotEmpty()) {
                    LandscapeOverlayIconButton(
                        icon = MiuixIcons.Regular.Mic,
                        description = stringResource(R.string.music_video_ktv),
                        selected = ktvLyricsEnabled,
                        onClick = {
                            ktvLyricsEnabled = !ktvLyricsEnabled
                            if (ktvLyricsEnabled) hideNeighborCovers = true
                        }
                    )
                }
            }
        }
        if (dynamicCoverSource?.preferLandscapeBackground != true) {
            Box(
                modifier = Modifier
                .align(Alignment.TopStart)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 26.dp, start = 28.dp)
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = MiuixIcons.Regular.Back,
                    contentDescription = stringResource(R.string.common_back),
                    tint = LocalPlayerContentColor.current.copy(alpha = 0.92f),
                    modifier = Modifier.size(23.dp)
                )
            }
        }
    }
}

@Composable
private fun LandscapeOverlayIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = if (selected) 0.54f else 0.32f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (selected) Color(0xFF62E968) else LocalPlayerContentColor.current.copy(alpha = 0.92f),
            modifier = Modifier.size(23.dp)
        )
    }
}

@Composable
private fun CompactLandscapeNowPlaying(
    song: Song?,
    embeddedCover: Bitmap?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            val current = song
            if (current != null) {
                LandscapeStackCoverImage(
                    song = current,
                    embeddedCover = embeddedCover,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(
            modifier = Modifier
                .width(184.dp)
                .padding(start = 10.dp, end = 8.dp)
        ) {
            Text(
                text = song?.title?.takeIf { it.isNotBlank() } ?: stringResource(R.string.app_name),
                color = LocalPlayerContentColor.current.copy(alpha = 0.96f),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Clip,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
            )
            Text(
                text = song?.artist?.takeIf { it.isNotBlank() } ?: stringResource(R.string.player_unknown_artist),
                color = LocalPlayerContentColor.current.copy(alpha = 0.62f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
