package com.ella.music

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ella.music.data.SettingsManager
import com.ella.music.data.splitArtistNames
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.theme.EllaTheme
import com.ella.music.ui.theme.THEME_FOLLOW_SYSTEM
import com.ella.music.player.CenterChannelSuppressorAudioProcessor
import com.ella.music.player.EllaRenderersFactory
import com.ella.music.ui.player.GlowSeekBar
import com.ella.music.ui.player.MusicVideoKtvLyrics
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Trim
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.basic.Check

/** Independent, audible MV player used exclusively by the song-detail MV action. */
class MusicVideoActivity : ComponentActivity() {
    internal var activePlayer: ExoPlayer? = null
    private var landscapeImmersive = false
    private var resumeAfterArtistNavigation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val song = MusicVideoLauncher.songFrom(intent) ?: run {
            finish()
            return
        }
        val source = MusicVideoLauncher.sourceUriFrom(intent) ?: run {
            finish()
            return
        }
        val videoAspectRatio = MusicVideoLauncher.sourceAspectRatioFrom(intent)
        setContent {
            val settings = remember { SettingsManager.getInstance(this) }
            val themeMode by settings.themeMode.collectAsState(initial = THEME_FOLLOW_SYSTEM)
            EllaTheme(themeMode = themeMode) {
                DetailMusicVideoScreen(
                    song = song,
                    source = source,
                    videoAspectRatio = videoAspectRatio,
                    onBack = ::finish
                )
            }
        }
    }

    override fun onStop() {
        // An MV opened from the detail page is audible. It must never continue as an invisible
        // second player after navigating to an artist, sharing, or opening another MV.
        activePlayer?.pause()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus && landscapeImmersive) applyLandscapeImmersiveMode()
    }

    internal fun setLandscapeImmersive(enabled: Boolean) {
        landscapeImmersive = enabled
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (enabled) applyLandscapeImmersiveMode()
        else WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
    }

    private fun applyLandscapeImmersiveMode() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    internal fun pauseForArtistNavigation() {
        resumeAfterArtistNavigation = true
        activePlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        if (resumeAfterArtistNavigation) {
            resumeAfterArtistNavigation = false
            activePlayer?.play()
        }
    }

    override fun onDestroy() {
        landscapeImmersive = false
        WindowCompat.getInsetsController(window, window.decorView)
            .show(WindowInsetsCompat.Type.systemBars())
        activePlayer?.release()
        activePlayer = null
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        // A singleTop MV route can be reused while it is visible. Recompose it from the new
        // intent after releasing its old decoder instead of layering another audible player.
        activePlayer?.pause()
        recreate()
    }
}

@Composable
private fun DetailMusicVideoScreen(
    song: Song,
    source: Uri,
    videoAspectRatio: Float?,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as MusicVideoActivity
    var landscape by remember { mutableStateOf(false) }
    var captionsEnabled by remember { mutableStateOf(false) }
    var ktvLyricsEnabled by remember { mutableStateOf(false) }
    var accompanimentEnabled by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    var captionOffset by remember { mutableStateOf<Offset?>(null) }
    var showCaptureActions by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    val captureSubtitles by SettingsManager.getInstance(context).musicVideoCaptureSubtitles.collectAsState(initial = false)
    val repository = remember(context) { MusicRepository.getInstance(context) }
    val lyricsNeeded = captionsEnabled || ktvLyricsEnabled
    val lyrics by produceState<List<LyricLine>>(emptyList(), song.path, lyricsNeeded) {
        value = if (lyricsNeeded) withContext(Dispatchers.IO) { repository.getLyrics(song) } else emptyList()
    }
    val accompanimentProcessor = remember { CenterChannelSuppressorAudioProcessor() }
    val player = remember(source) {
        val renderersFactory = EllaRenderersFactory(context).apply {
            setExtraAudioProcessors(listOf(accompanimentProcessor))
        }
        ExoPlayer.Builder(context, renderersFactory).build().apply {
            setMediaItem(MediaItem.fromUri(source))
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(accompanimentEnabled) {
        accompanimentProcessor.enabled = accompanimentEnabled
    }
    DisposableEffect(activity, player) {
        activity.activePlayer = player
        onDispose {
            if (activity.activePlayer === player) activity.activePlayer = null
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) { isPlaying = value }
            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration.coerceAtLeast(0L)
            }
            override fun onPlayerError(error: PlaybackException) {
                Toast.makeText(context, R.string.music_video_play_failed, Toast.LENGTH_SHORT).show()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            // onDestroy releases the player first when the Activity is torn down. Checking the
            // owner prevents the composable teardown from issuing a second release call.
            if (activity.activePlayer === player) {
                activity.activePlayer = null
                player.release()
            }
        }
    }
    LaunchedEffect(player) {
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(100L)
        }
    }
    LaunchedEffect(landscape) {
        activity.requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
        controlsVisible = true
        activity.setLandscapeImmersive(landscape)
    }
    LaunchedEffect(landscape, controlsVisible, isPlaying) {
        if (landscape && controlsVisible && isPlaying) {
            delay(3_000L)
            controlsVisible = false
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity.setLandscapeImmersive(false)
        }
    }
    val captionsAvailable = song.duration > 0L && duration > 0L && abs(song.duration - duration) <= 10_000L
    LaunchedEffect(captionsAvailable) {
        if (!captionsAvailable) captionsEnabled = false
    }

    Box(modifier = Modifier.fillMaxSize().background(ComposeColor.Black)) {
        if (landscape) {
            LandscapeMusicVideoLayout(
                song = song,
                player = player,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                lyrics = lyrics,
                videoAspectRatio = videoAspectRatio,
                captionsEnabled = captionsEnabled,
                captionsAvailable = captionsAvailable,
                ktvLyricsEnabled = ktvLyricsEnabled,
                accompanimentEnabled = accompanimentEnabled,
                controlsLocked = controlsLocked,
                captionOffset = captionOffset,
                controlsVisible = controlsVisible,
                onBack = { player.pause(); onBack() },
                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                onSeek = { player.seekTo(it) },
                onToggleCaptions = {
                    captionsEnabled = !captionsEnabled
                    if (captionsEnabled) ktvLyricsEnabled = false
                },
                onCaptionOffsetChange = { captionOffset = it },
                onToggleKtvLyrics = {
                    ktvLyricsEnabled = !ktvLyricsEnabled
                    if (ktvLyricsEnabled) captionsEnabled = false
                },
                onToggleAccompaniment = { accompanimentEnabled = !accompanimentEnabled },
                onToggleLock = { controlsLocked = !controlsLocked },
                onPortrait = { landscape = false },
                onCapture = { showCaptureActions = true },
                onShare = { MusicVideoLauncher.share(context, source, song.title) },
                onControlsVisibleChange = { controlsVisible = it }
            )
        } else {
            PortraitMusicVideoLayout(
                song = song,
                player = player,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                onBack = { player.pause(); onBack() },
                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                onSeek = { player.seekTo(it) },
                onLandscape = { landscape = true },
                onShare = { MusicVideoLauncher.share(context, source, song.title) }
            )
        }
        if (showCaptureActions) {
            CaptureChoiceOverlay(
                includeCaptions = captureSubtitles,
                onIncludeCaptionsChange = { enabled ->
                    activity.lifecycleScope.launch { SettingsManager.getInstance(context).setMusicVideoCaptureSubtitles(enabled) }
                },
                onDismiss = { showCaptureActions = false },
                onSave = {
                    val capturePosition = player.currentPosition
                    val captureLyrics = lyrics
                    activity.lifecycleScope.launch {
                        val saved = withContext(Dispatchers.IO) {
                            captureVideoFrame(context, source, capturePosition, captureSubtitles, captureLyrics)
                        }
                        Toast.makeText(context, if (saved) R.string.music_video_capture_saved else R.string.music_video_capture_failed, Toast.LENGTH_SHORT).show()
                    }
                    showCaptureActions = false
                },
                onShare = {
                    val capturePosition = player.currentPosition
                    val captureLyrics = lyrics
                    activity.lifecycleScope.launch {
                        val file = withContext(Dispatchers.IO) {
                            captureVideoFrameFile(context, source, capturePosition, captureSubtitles, captureLyrics)
                        }
                        if (file != null) MusicVideoLauncher.share(context, Uri.fromFile(file), song.title)
                    }
                    showCaptureActions = false
                }
            )
        }
    }
}

@Composable
private fun PortraitMusicVideoLayout(
    song: Song,
    player: ExoPlayer,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onLandscape: () -> Unit,
    onShare: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            VideoIconButton(MiuixIcons.Regular.Back, stringResource(R.string.common_back), onBack)
            VideoIconButton(MiuixIcons.Regular.Share, stringResource(R.string.common_share), onShare)
        }
        VideoSurface(
            player = player,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(onTogglePlay) { detectTapGestures(onDoubleTap = { onTogglePlay() }) }
        )
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            ArtistTitleBlock(song = song)
            VideoTransport(
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                onTogglePlay = onTogglePlay,
                onSeek = onSeek,
                trailingLabel = stringResource(R.string.player_music_video_landscape),
                trailingIconRes = R.drawable.ic_music_video_landscape,
                onTrailing = onLandscape
            )
        }
    }
}

@Composable
private fun LandscapeMusicVideoLayout(
    song: Song,
    player: ExoPlayer,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    lyrics: List<LyricLine>,
    videoAspectRatio: Float?,
    captionsEnabled: Boolean,
    captionsAvailable: Boolean,
    ktvLyricsEnabled: Boolean,
    accompanimentEnabled: Boolean,
    controlsLocked: Boolean,
    captionOffset: Offset?,
    controlsVisible: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onToggleCaptions: () -> Unit,
    onCaptionOffsetChange: (Offset) -> Unit,
    onToggleKtvLyrics: () -> Unit,
    onToggleAccompaniment: () -> Unit,
    onToggleLock: () -> Unit,
    onPortrait: () -> Unit,
    onCapture: () -> Unit,
    onShare: () -> Unit,
    onControlsVisibleChange: (Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        VideoSurface(
            player = player,
            modifier = Modifier.fillMaxSize()
        )
        // Keep the whole view tappable; controls sit above this layer and retain their own actions.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(onTogglePlay, controlsVisible) {
                    detectTapGestures(
                        onTap = { onControlsVisibleChange(!controlsVisible) },
                        onDoubleTap = { onTogglePlay() }
                    )
                }
        )
        if (controlsVisible && ktvLyricsEnabled) {
            MusicVideoKtvLyrics(
                lyrics = lyrics,
                position = position,
                videoAspectRatio = videoAspectRatio,
                modifier = Modifier.fillMaxSize()
            )
        } else if (controlsVisible && captionsEnabled) {
            MusicVideoCaptions(
                lyrics = lyrics,
                position = position,
                videoAspectRatio = videoAspectRatio,
                positionOffset = captionOffset,
                locked = controlsLocked,
                onPositionOffsetChange = onCaptionOffsetChange,
                modifier = Modifier.fillMaxSize()
            )
        }
        if (controlsVisible && !controlsLocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    VideoIconButton(MiuixIcons.Regular.Back, stringResource(R.string.common_back), onBack)
                    Text(song.title.ifBlank { song.fileName }, color = ComposeColor.White, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                    ArtistChip(song = song)
                    IconButton(onClick = onShare) {
                        com.ella.music.ui.player.QuickActionIcon(
                            kind = com.ella.music.ui.player.PlayerQuickActionKind.Share,
                            color = ComposeColor.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    IconButton(onClick = onPortrait) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(R.drawable.ic_music_video_landscape),
                            contentDescription = stringResource(R.string.player_music_video_landscape),
                            tint = ComposeColor.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                    VideoIconButton(MiuixIcons.Regular.Lock, stringResource(R.string.music_video_lock), onToggleLock)
                }
                VideoTransport(
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    onTogglePlay = onTogglePlay,
                    onSeek = onSeek,
                    secondaryTrailingLabel = stringResource(R.string.music_video_accompaniment),
                    onSecondaryTrailing = onToggleAccompaniment,
                    secondaryTrailingSelected = accompanimentEnabled,
                    trailingLabel = stringResource(R.string.music_video_captions),
                    onTrailing = onToggleCaptions,
                    trailingSelected = captionsEnabled,
                    showTrailing = captionsAvailable
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(end = 14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                VideoIconButton(
                    MiuixIcons.Regular.Mic,
                    stringResource(R.string.music_video_ktv),
                    onToggleKtvLyrics,
                    selected = ktvLyricsEnabled
                )
                VideoIconButton(MiuixIcons.Regular.Trim, stringResource(R.string.music_video_capture), onCapture)
            }
        } else if (controlsVisible) {
            VideoTextButton(
                text = stringResource(R.string.music_video_unlock),
                onClick = onToggleLock,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = 14.dp)
            )
        }
    }
}

@Composable
private fun VideoSurface(player: ExoPlayer, modifier: Modifier) {
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(Color.BLACK)
                this.player = player
            }
        },
        update = { it.player = player },
        modifier = modifier
    )
}

@Composable
private fun ArtistTitleBlock(song: Song) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        ArtistChip(song = song)
        Text(song.title.ifBlank { song.fileName }, color = ComposeColor.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ArtistChip(song: Song) {
    val context = LocalContext.current
    val artists = remember(song.artist) { splitArtistNames(song.artist) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (artists.isEmpty()) {
            Text(
                song.artist.ifBlank { stringResource(R.string.player_unknown_artist) },
                color = ComposeColor.White.copy(alpha = 0.82f),
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else {
            artists.forEachIndexed { index, artist ->
                Text(
                    text = artist,
                    color = ComposeColor.White.copy(alpha = 0.82f),
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(start = if (index == 0) 0.dp else 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { openArtistFromVideo(context, artist) }
                        .padding(horizontal = 2.dp)
                )
                if (index != artists.lastIndex) {
                    Text("/", color = ComposeColor.White.copy(alpha = 0.50f), fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
private fun VideoTransport(
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    secondaryTrailingLabel: String? = null,
    onSecondaryTrailing: (() -> Unit)? = null,
    secondaryTrailingSelected: Boolean = false,
    trailingLabel: String,
    trailingIconRes: Int? = null,
    onTrailing: () -> Unit,
    trailingSelected: Boolean = false,
    showTrailing: Boolean = true
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        VideoIconButton(if (isPlaying) MiuixIcons.Regular.Pause else MiuixIcons.Regular.Play, stringResource(if (isPlaying) R.string.common_pause else R.string.common_play), onTogglePlay)
        Text(position.formatVideoTime(), color = ComposeColor.White.copy(alpha = 0.85f), fontSize = 12.sp)
        GlowSeekBar(
            value = position.toFloat() / duration.coerceAtLeast(1L).toFloat(),
            onSeek = { progress -> onSeek((progress * duration.coerceAtLeast(0L)).toLong()) },
            accent = ComposeColor.White,
            allowTapSeek = true,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        )
        Text(duration.formatVideoTime(), color = ComposeColor.White.copy(alpha = 0.85f), fontSize = 12.sp)
        if (secondaryTrailingLabel != null && onSecondaryTrailing != null) {
            VideoTextButton(
                secondaryTrailingLabel,
                onSecondaryTrailing,
                selected = secondaryTrailingSelected,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        if (trailingIconRes != null) {
            IconButton(onClick = onTrailing, modifier = Modifier.padding(start = 8.dp)) {
                Icon(
                    painter = androidx.compose.ui.res.painterResource(trailingIconRes),
                    contentDescription = trailingLabel,
                    tint = ComposeColor.White,
                    modifier = Modifier.size(25.dp)
                )
            }
        } else if (showTrailing) {
            VideoTextButton(trailingLabel, onTrailing, selected = trailingSelected, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun VideoIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    IconButton(onClick = onClick) {
        Icon(
            icon,
            description,
            tint = if (selected) ComposeColor(0xFF62E968) else ComposeColor.White,
            modifier = Modifier.size(25.dp)
        )
    }
}

@Composable
private fun VideoTextButton(text: String, onClick: () -> Unit, selected: Boolean = false, modifier: Modifier = Modifier) {
    Text(text, color = ComposeColor.White, fontSize = 13.sp, modifier = modifier.clip(RoundedCornerShape(16.dp)).background(if (selected) ComposeColor(0xFF4D7CFE) else ComposeColor.Black.copy(alpha = 0.42f)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 7.dp))
}

@Composable
private fun MusicVideoCaptions(
    lyrics: List<LyricLine>,
    position: Long,
    videoAspectRatio: Float?,
    positionOffset: Offset?,
    locked: Boolean,
    onPositionOffsetChange: (Offset) -> Unit,
    modifier: Modifier
) {
    val primary = lyrics.lastOrNull { it.timeMs <= position }
    if (primary == null) return
    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        // PlayerView uses FIT, so place captions in this same fitted box rather than its black bars.
        val frameModifier = when {
            videoAspectRatio == null -> Modifier.fillMaxSize()
            videoAspectRatio >= screenRatio -> Modifier.fillMaxWidth().aspectRatio(videoAspectRatio)
            else -> Modifier.fillMaxHeight().aspectRatio(videoAspectRatio)
        }.align(Alignment.Center)
        BoxWithConstraints(modifier = frameModifier) {
            val frameWidthPx = with(density) { maxWidth.toPx() }
            val frameHeightPx = with(density) { maxHeight.toPx() }
            var captionSize by remember { mutableStateOf(IntSize.Zero) }
            val defaultOffset = Offset(0.5f, 0.78f)
            val requestedOffset = positionOffset ?: defaultOffset
            val minX = (captionSize.width / 2f / frameWidthPx.coerceAtLeast(1f)).coerceIn(0f, 0.5f)
            val maxX = 1f - minX
            val minY = (captionSize.height / 2f / frameHeightPx.coerceAtLeast(1f)).coerceIn(0f, 0.5f)
            val maxY = 1f - minY
            val constrainedOffset = Offset(
                requestedOffset.x.coerceIn(minX, maxX),
                requestedOffset.y.coerceIn(minY, maxY)
            )
            CaptionBlock(
                primary = primary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (constrainedOffset.x * frameWidthPx - captionSize.width / 2f).roundToInt(),
                            (constrainedOffset.y * frameHeightPx - captionSize.height / 2f).roundToInt()
                        )
                    }
                    .fillMaxWidth(0.82f)
                    .onSizeChanged { captionSize = it }
                    .then(
                        if (locked) {
                            Modifier
                        } else {
                            Modifier.pointerInput(constrainedOffset, captionSize) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    onPositionOffsetChange(
                                        Offset(
                                            (constrainedOffset.x + dragAmount.x / frameWidthPx)
                                                .coerceIn(minX, maxX),
                                            (constrainedOffset.y + dragAmount.y / frameHeightPx)
                                                .coerceIn(minY, maxY)
                                        )
                                    )
                                }
                            }
                        }
                    ),
                textAlign = TextAlign.Center,
                backgroundFirst = true,
                horizontalAlignment = Alignment.CenterHorizontally
            )
        }
    }
}

@Composable
private fun CaptionBlock(
    primary: LyricLine,
    modifier: Modifier,
    textAlign: TextAlign,
    backgroundFirst: Boolean,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        if (backgroundFirst) CaptionLine(primary.backgroundText, textAlign)
        CaptionLine(primary.text, textAlign)
        if (!backgroundFirst) CaptionLine(primary.backgroundText, textAlign)
    }
}

@Composable
private fun CaptionLine(text: String?, textAlign: TextAlign) {
    text?.takeIf { it.isNotBlank() }?.let {
        Text(
            it,
            color = ComposeColor.White,
            fontSize = 21.sp,
            fontWeight = FontWeight.Bold,
            textAlign = textAlign,
            modifier = Modifier
                .wrapContentWidth(if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start)
                .clip(RoundedCornerShape(5.dp))
                .background(ComposeColor.Black.copy(alpha = 0.42f))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun CaptureChoiceOverlay(
    includeCaptions: Boolean,
    onIncludeCaptionsChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onShare: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(ComposeColor.Black.copy(alpha = 0.58f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.width(260.dp).clip(RoundedCornerShape(20.dp)).background(ComposeColor(0xFF252833)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.music_video_capture), color = ComposeColor.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            CaptureCaptionCheckbox(
                checked = includeCaptions,
                label = stringResource(R.string.music_video_capture_with_captions),
                onCheckedChange = onIncludeCaptionsChange
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                VideoTextButton(stringResource(R.string.common_save), onSave, modifier = Modifier.weight(1f))
                VideoTextButton(stringResource(R.string.common_share), onShare, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CaptureCaptionCheckbox(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit
) {
    val activeColor = ComposeColor(0xFF4D7CFE)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(if (checked) activeColor else ComposeColor.Transparent)
                .border(1.5.dp, if (checked) activeColor else ComposeColor.White.copy(alpha = 0.68f), RoundedCornerShape(5.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    imageVector = MiuixIcons.Basic.Check,
                    contentDescription = null,
                    tint = ComposeColor.White,
                    modifier = Modifier.size(15.dp)
                )
            }
        }
        Text(
            text = label,
            color = ComposeColor.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(start = 10.dp)
        )
    }
}

private fun Long.formatVideoTime(): String {
    val seconds = (this / 1_000L).coerceAtLeast(0L)
    return "%02d:%02d".format(Locale.US, seconds / 60L, seconds % 60L)
}

private fun openArtistFromVideo(context: Context, artist: String) {
    if (artist.isBlank()) return
    (context as? MusicVideoActivity)?.pauseForArtistNavigation()
    context.startActivity(Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("halcyon://artist/${Uri.encode(artist)}")
    })
}

private fun captureVideoFrame(
    context: Context,
    source: Uri,
    positionMs: Long,
    includeCaptions: Boolean,
    lyrics: List<LyricLine>
): Boolean {
    val frame = decodeVideoFrame(context, source, positionMs) ?: return false
    val result = if (includeCaptions) frame.withCaptionOverlay(lyrics, positionMs) else frame
    return runCatching {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "Halcyon_MV_${System.currentTimeMillis()}.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Halcyon")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val output = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: return false
        context.contentResolver.openOutputStream(output)?.use { stream ->
            result.compress(Bitmap.CompressFormat.PNG, 100, stream)
        } ?: return false
        context.contentResolver.update(output, ContentValues().apply {
            put(MediaStore.Images.Media.IS_PENDING, 0)
        }, null, null)
        true
    }.getOrDefault(false).also {
        if (result !== frame) result.recycle()
        frame.recycle()
    }
}

private fun captureVideoFrameFile(
    context: Context,
    source: Uri,
    positionMs: Long,
    includeCaptions: Boolean,
    lyrics: List<LyricLine>
): File? {
    val frame = decodeVideoFrame(context, source, positionMs) ?: return null
    val result = if (includeCaptions) frame.withCaptionOverlay(lyrics, positionMs) else frame
    val dir = File(context.cacheDir, "music_video_capture").apply { mkdirs() }
    val file = File(dir, "halcyon_mv_${System.currentTimeMillis()}.png")
    return runCatching {
        FileOutputStream(file).use { result.compress(Bitmap.CompressFormat.PNG, 100, it) }
        file
    }.getOrNull().also {
        if (result !== frame) result.recycle()
        frame.recycle()
    }
}

private fun decodeVideoFrame(context: Context, source: Uri, positionMs: Long): Bitmap? = runCatching {
    android.media.MediaMetadataRetriever().use { retriever ->
        retriever.setDataSource(context, source)
        retriever.getFrameAtTime(positionMs.coerceAtLeast(0L) * 1_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST)
    }
}.getOrNull()

private fun Bitmap.withCaptionOverlay(lyrics: List<LyricLine>, position: Long): Bitmap {
    val line = lyrics.lastOrNull { it.timeMs <= position } ?: return this
    val target = copy(config ?: Bitmap.Config.ARGB_8888, true)
    val canvas = android.graphics.Canvas(target)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = (target.height * 0.055f).coerceAtLeast(24f)
        typeface = android.graphics.Typeface.DEFAULT_BOLD
        setShadowLayer(5f, 0f, 2f, Color.BLACK)
    }
    val text = line.text.ifBlank { line.backgroundText.orEmpty() }
    val y = when {
        line.agent.equals("v1", true) -> paint.textSize * 1.8f
        line.agent.equals("v2", true) -> target.height - paint.textSize * 1.4f
        else -> target.height - paint.textSize * 1.5f
    }
    val x = when {
        line.agent.equals("v2", true) -> (target.width - paint.measureText(text) - target.width * 0.06f).coerceAtLeast(0f)
        else -> target.width * 0.06f
    }
    canvas.drawText(text, x, y, paint)
    return target
}
