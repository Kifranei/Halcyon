package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.animation.core.spring
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.runtime.mutableIntStateOf
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.ui.AspectRatioFrameLayout
import com.ella.music.data.SettingsManager
import com.ella.music.data.repository.CoverUsage
import com.ella.music.data.repository.MusicRepository
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.rememberSongArtworkState
import kotlin.math.abs

@Composable
internal fun LandscapeCoverModeBackground(
    palette: PlayerPalette,
    dynamicCoverSource: DynamicCoverSource? = null,
    embeddedCover: Bitmap? = null,
    paletteBitmap: Bitmap? = null,
    currentPosition: Long,
    duration: Long,
    isPlaying: Boolean,
    flowEffectMode: Int,
    dynamicFlowEnabled: Boolean,
    visualizerEnabled: Boolean,
    visualizerOpacity: Float = 1f,
    customBackgroundUri: String,
    customBackgroundOpacity: Float = 1f,
    customBackgroundDim: Float = 0.26f,
    beautifulLyricsBackground: Boolean = false,
    modifier: Modifier = Modifier
) {
    val musicVideoSource = dynamicCoverSource?.takeIf { it.preferLandscapeBackground }
    Box(modifier = modifier.background(if (musicVideoSource != null) Color.Black else palette.middle)) {
        if (musicVideoSource != null) {
            val context = LocalContext.current
            val stretchEnabled by SettingsManager.getInstance(context).musicVideoStretchEnabled
                .collectAsState(initial = SettingsManager.DEFAULT_MUSIC_VIDEO_STRETCH_ENABLED)
            DynamicCoverVideo(
                source = musicVideoSource,
                isPlaying = isPlaying,
                syncPositionMs = currentPosition,
                syncDurationMs = duration,
                onPlaybackError = {},
                modifier = Modifier.fillMaxSize(),
                cornerRadiusDp = 0f,
                // FIT keeps the authored frame. PlayerView resize modes are ignored by SurfaceView
                // in Compose, so DynamicCoverVideo applies this as a real layout constraint.
                resizeMode = if (stretchEnabled) {
                    AspectRatioFrameLayout.RESIZE_MODE_FILL
                } else {
                    AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.24f))
            )
        } else if (customBackgroundUri.isNotBlank()) {
            PlayerCustomBackground(
                uri = customBackgroundUri,
                imageAlpha = customBackgroundOpacity,
                dimAlpha = customBackgroundDim,
                modifier = Modifier.fillMaxSize()
            )
        } else if (beautifulLyricsBackground) {
            BeautifulLyricsDynamicBackground(
                palette = palette,
                coverBitmap = embeddedCover ?: paletteBitmap,
                positionMs = currentPosition,
                isPlaying = isPlaying,
                animate = true,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val flowAlpha = if (visualizerEnabled) visualizerOpacity.coerceIn(0f, 1f) else 1f
            AppleCoverFlowBackground(
                coverBitmap = embeddedCover ?: paletteBitmap,
                backgroundColor = palette.middle,
                isDark = !palette.isLight,
                isPlaying = isPlaying,
                animate = dynamicFlowEnabled && !visualizerEnabled,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = flowAlpha }
            )
        }
    }
}

/** One pager position drives dragging, settling and every card transform. */
@Composable
internal fun LandscapeCoverStack(
    currentSong: Song?,
    embeddedCover: Bitmap?,
    dynamicCoverSource: DynamicCoverSource?,
    isPlaying: Boolean,
    playlist: List<Song>,
    selectedQueueIndex: Int = -1,
    onSelectSong: (Int) -> Unit,
    swipeEnabled: Boolean,
    onDynamicCoverFailed: (String) -> Unit,
    coverWidthFraction: Float = 0.30f,
    onCenterCoverClick: (() -> Unit)? = null,
    centerOverlay: (@Composable BoxScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val songs = remember(playlist, currentSong) { playlist.ifEmpty { listOfNotNull(currentSong) } }
    if (songs.isEmpty()) return
    val songKey = currentSong?.playlistIdentityKey()
    val selectedIndex = remember(songs, songKey, selectedQueueIndex) {
        resolveCoverFlowQueueIndex(songs, songKey, selectedQueueIndex)
    }
    val pager = rememberPagerState(initialPage = selectedIndex) { songs.size }
    val latestSelected by rememberUpdatedState(selectedIndex)
    val latestSelect by rememberUpdatedState(onSelectSong)
    val dragging by pager.interactionSource.collectIsDraggedAsState()
    val latestDragging by rememberUpdatedState(dragging)
    val sync = remember(pager, songs) { CoverFlowPlaybackSync() }
    var reconcileRevision by remember { mutableIntStateOf(0) }
    LaunchedEffect(pager, sync) {
        coroutineScope {
            var animation: Job? = null
            var acknowledgementTimeout: Job? = null
            snapshotFlow {
                CoverFlowSyncSnapshot(latestSelected, pager.settledPage,
                    pager.isScrollInProgress, latestDragging, reconcileRevision)
            }.collect { state ->
                when (val command = sync.update(state)) {
                    CoverFlowSyncCommand.None -> Unit
                    CoverFlowSyncCommand.CancelAnimation -> animation?.cancel()
                    is CoverFlowSyncCommand.Select -> {
                        animation?.cancel()
                        latestSelect(command.page)
                        acknowledgementTimeout?.cancel()
                        val serial = sync.requestSerial
                        acknowledgementTimeout = launch {
                            delay(1_500L)
                            if (sync.expirePending(serial)) reconcileRevision++
                        }
                    }
                    is CoverFlowSyncCommand.Scroll -> {
                        animation?.cancel()
                        animation = launch {
                            pager.animateScrollToPage(command.page,
                                animationSpec = spring(dampingRatio = 1f, stiffness = 380f))
                        }
                    }
                }
            }
        }
    }
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val coverSize = minOf(maxHeight * 0.84f, maxWidth * coverWidthFraction).coerceAtLeast(118.dp)
        val step = minOf(coverSize * 0.74f, 126.dp)
        HorizontalPager(
            state = pager, modifier = Modifier.fillMaxSize(),
            pageSize = PageSize.Fixed(step),
            contentPadding = PaddingValues(horizontal = ((maxWidth - step) / 2f).coerceAtLeast(0.dp)),
            beyondViewportPageCount = 2, userScrollEnabled = swipeEnabled && songs.size > 1,
            key = { page -> "${songs[page].playlistIdentityKey()}:$page" },
            flingBehavior = PagerDefaults.flingBehavior(pager,
                snapAnimationSpec = spring(dampingRatio = 1f, stiffness = 380f))
        ) { page ->
            val itemSong = songs[page]
            val isCenter = page == pager.currentPage
            val isCurrent = itemSong.playlistIdentityKey() == songKey
            val coverModifier = Modifier.requiredSize(coverSize)
                .zIndex(10f - abs(page - pager.currentPage))
                .graphicsLayer {
                    val position = (page - pager.currentPage).toFloat() - pager.currentPageOffsetFraction
                    val distance = abs(position)
                    translationY = 8.dp.toPx() * distance
                    scaleX = (1f - distance * 0.13f).coerceAtLeast(0.58f)
                    scaleY = scaleX
                    alpha = (1f - distance * 0.14f).coerceAtLeast(0.34f)
                    rotationY = -position * 13f
                    cameraDistance = 18f * density
                }
            Box(modifier = if (isCenter && onCenterCoverClick != null)
                coverModifier.playerNoIndicationClick(onCenterCoverClick) else coverModifier,
                contentAlignment = Alignment.Center) {
                LandscapeCoverReflection(itemSong, embeddedCover.takeIf { isCurrent },
                    cornerRadius = 14.dp, alpha = 0.28f)
                Box(Modifier.matchParentSize().playerMorphArtwork(enabled = isCenter).clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.10f)), contentAlignment = Alignment.Center) {
                    val video = dynamicCoverSource?.takeUnless { it.preferLandscapeBackground }
                    if (isCenter && isCurrent && video != null) {
                        DynamicCoverVideo(video, isPlaying,
                            onPlaybackError = { onDynamicCoverFailed(video.failureKey) },
                            modifier = Modifier.fillMaxSize(), cornerRadiusDp = 14f)
                    } else {
                        LandscapeStackCoverImage(itemSong, embeddedCover.takeIf { isCurrent }, Modifier.fillMaxSize())
                    }
                    if (!isCenter) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.20f)))
                    if (isCenter && isCurrent && centerOverlay != null) centerOverlay()
                }
            }
        }
    }
}

@Composable
internal fun BoxScope.LandscapeCoverReflection(
    song: Song,
    embeddedCover: Bitmap?,
    cornerRadius: androidx.compose.ui.unit.Dp,
    alpha: Float
) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .align(Alignment.BottomCenter)
            .graphicsLayer {
                scaleY = -0.34f
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
                this.alpha = alpha
            }
            .clip(RoundedCornerShape(cornerRadius))
    ) {
        LandscapeStackCoverImage(
            song = song,
            embeddedCover = embeddedCover,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black,
                        0.52f to Color.Black.copy(alpha = 0.36f),
                        1f to Color.Transparent
                    )
                )
        )
    }
}

@Composable
internal fun LandscapeStackCoverImage(
    song: Song,
    embeddedCover: Bitmap?,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { MusicRepository.getInstance(context) }
    val albumArtUri = remember(song.albumId) {
        repository.getAlbumArtUri(song.albumId)
    }
    val artworkState = rememberSongArtworkState(
        song = song,
        albumArtUri = albumArtUri,
        loadCoverArt = { target ->
            repository.getCoverArtBitmap(target, 512, CoverUsage.Player)
        },
        usage = ArtworkUsage.MiniPlayer,
        showDefaultWhenMissing = false
    )
    val coverModel = embeddedCover ?: artworkState.model
    if (coverModel != null) {
        SafeCoverImage(
            model = coverModel,
            contentDescription = null,
            modifier = modifier,
            contentScale = ContentScale.Fit,
            sizePx = 512,
            loadOriginal = true,
            showDefaultPlaceholder = false
        )
    } else {
        DefaultAlbumCover(modifier = modifier)
    }
}
