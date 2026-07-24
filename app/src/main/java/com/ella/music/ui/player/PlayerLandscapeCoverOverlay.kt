package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Text

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
    onDynamicCoverFailed: (String) -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
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
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    onArtist: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            dynamicFlowEnabled = false,
            visualizerEnabled = visualizerEnabled,
            visualizerOpacity = visualizerOpacity,
            customBackgroundUri = "",
            customBackgroundOpacity = 1f,
            customBackgroundDim = 0.26f,
            beautifulLyricsBackground = beautifulLyricsBackground,
            modifier = Modifier.fillMaxSize()
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 18.dp)
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
            Spacer(modifier = Modifier.height(8.dp))
            if (lyrics.isNotEmpty()) {
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
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 26.dp, end = 28.dp)
                .size(56.dp)
                .clip(CircleShape)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            CloseIcon(
                color = LocalPlayerContentColor.current.copy(alpha = 0.92f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
