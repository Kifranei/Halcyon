package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.*
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.data.model.*
import top.yukonga.miuix.kmp.basic.Text

/** Based on the early phone landscape split; the artwork melts into the shared player canvas. */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
internal fun ClassicSplitLandscapePlayer(
    song: Song?, embeddedCover: Bitmap?, paletteBitmap: Bitmap?, beautifulLyricsBackground: Boolean, dynamicCoverSource: DynamicCoverSource?,
    isPlaying: Boolean, currentPosition: Long, palette: PlayerPalette, lyrics: List<LyricLine>, currentLyricIndex: Int,
    showTranslation: Boolean, showPronunciation: Boolean, fontFamily: FontFamily?, translationFontFamily: FontFamily?,
    fontWeight: FontWeight, fontScale: Float, secondaryFontScale: Float, primaryTextSizeSp: Float, secondaryTextSizeSp: Float,
    isFavorite: Boolean, onToggleFavorite: () -> Unit,
    onLyricLineClick: (LyricLine) -> Unit, onLyricLineLongClick: (LyricLine) -> Unit,
    onPrevious: () -> Unit, onPlayPause: () -> Unit,
    onNext: () -> Unit, onArtist: () -> Unit, onDismiss: () -> Unit, onDynamicCoverFailed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val compact = LocalConfiguration.current.screenHeightDp < 400
    val foreground = LocalPlayerContentColor.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val settings = remember(context) { com.ella.music.data.SettingsManager.getInstance(context) }
    val dynamicFlow by settings.playerDynamicFlowEnabled.collectAsState(
        initial = com.ella.music.data.SettingsManager.DEFAULT_PLAYER_DYNAMIC_FLOW_ENABLED)
    val backgroundCover by produceState<Bitmap?>(embeddedCover ?: paletteBitmap,
        song?.playlistIdentityKey(), song?.coverUrl, song?.dateModified, embeddedCover, paletteBitmap) {
        value = embeddedCover?.takeUnless { it.isRecycled } ?: paletteBitmap?.takeUnless { it.isRecycled }
        if (value == null && song != null) value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            com.ella.music.data.repository.MusicRepository.getInstance(context)
                .getCoverArtBitmap(song, 512, com.ella.music.data.repository.CoverUsage.Player)
        }
    }
    // The existing AM/Beautiful pipeline takes its colors from the real cover. A neutral wash
    // cannot introduce the old palette's unrelated dominant hue into that textured background.
    val neutral = if (palette.isLight) Color.White else Color.Black
    val flowPalette = palette.copy(top = neutral, middle = neutral, bottom = neutral)
    Box(modifier.fillMaxSize().background(neutral).clipToBounds()) {
        SharedPlayerPageBackground(song, backgroundCover, backgroundCover, flowPalette,
            currentPosition, isPlaying, playerBackgroundEnabled = false, playerBackgroundUri = "",
            playerBackgroundOpacity = 1f, playerBackgroundDim = 0f,
            beautifulLyricsBackground = beautifulLyricsBackground, dynamicFlowEnabled = dynamicFlow,
            useBlurBackground = false, modifier = Modifier.fillMaxSize())
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.weight(0.45f).fillMaxHeight().playerMorphArtwork().graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }.drawWithContent {
            drawContent()
            drawRect(Brush.horizontalGradient(0f to Color.White, 0.72f to Color.White,
                0.90f to Color.White.copy(alpha = 0.55f), 1f to Color.Transparent), blendMode = BlendMode.DstIn)
        }) {
            if (dynamicCoverSource != null && !dynamicCoverSource.preferLandscapeBackground) {
                DynamicCoverVideo(dynamicCoverSource, isPlaying,
                    onPlaybackError = { onDynamicCoverFailed(dynamicCoverSource.failureKey) },
                    modifier = Modifier.fillMaxSize(), cornerRadiusDp = 0f, resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
            } else {
                AlbumArtView(song, embeddedCover, cornerRadius = 0.dp, contentScale = ContentScale.Crop, loadOriginal = false,
                    modifier = Modifier.fillMaxSize())
            }

        }
        Column(Modifier.weight(0.55f).fillMaxHeight().windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars).padding(start = 12.dp, end = 18.dp, top = 4.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).playerNoIndicationClick(onArtist)) {
                    Text(song?.title.orEmpty(), fontFamily = fontFamily, fontSize = if (compact) 20.sp else 24.sp,
                        color = foreground, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
                    Text(song?.artist.orEmpty(), fontFamily = fontFamily, fontSize = 13.sp,
                        color = foreground.copy(alpha = 0.65f), maxLines = 1, softWrap = false,
                        modifier = Modifier.fillMaxWidth().basicMarquee(iterations = Int.MAX_VALUE))
                }
                PlayerCommentHeaderAction(song)
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                    PlayerTransportIconButton(onClick = onPrevious, buttonSize = 42.dp) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            painter = androidx.compose.ui.res.painterResource(com.ella.music.R.drawable.ic_skip_previous),
                            contentDescription = androidx.compose.ui.res.stringResource(com.ella.music.R.string.common_previous),
                            tint = foreground, modifier = Modifier.size(24.dp))
                    }
                    PlayerTransportIconButton(onClick = onPlayPause, buttonSize = 42.dp) {
                        CenteredPlayPauseGlyph(isPlaying, foreground, Modifier.size(28.dp))
                    }
                    PlayerTransportIconButton(onClick = onNext, buttonSize = 42.dp) {
                        top.yukonga.miuix.kmp.basic.Icon(
                            painter = androidx.compose.ui.res.painterResource(com.ella.music.R.drawable.ic_skip_next),
                            contentDescription = androidx.compose.ui.res.stringResource(com.ella.music.R.string.common_next),
                            tint = foreground, modifier = Modifier.size(24.dp))
                    }
                }
                PlayerHeaderAction(PlayerHeaderActionKind.Favorite, selected = isFavorite, onClick = onToggleFavorite)
            }
            Box(Modifier.fillMaxWidth().weight(1f).clipToBounds()) {
                AppleMusicLyricsView(lyrics, currentLyricIndex, currentPosition, isPlaying,
                    showTranslation = showTranslation, showPronunciation = showPronunciation,
                    fontFamily = fontFamily, translationFontFamily = translationFontFamily, fontWeight = fontWeight,
                    fontScale = fontScale, secondaryFontScale = secondaryFontScale,
                    primaryTextSizeSp = primaryTextSizeSp, secondaryTextSizeSp = secondaryTextSizeSp,
                    lyricTextAlign = 0, contentColor = foreground, onLineClick = onLyricLineClick,
                    onLineDoubleClick = onPlayPause, onLineLongClick = onLyricLineLongClick,
                    topContentPadding = 10.dp, bottomContentPadding = 18.dp,
                    focusOffsetRatio = 0.18f, modifier = Modifier.fillMaxSize())
            }
        }
    }
    }
}
