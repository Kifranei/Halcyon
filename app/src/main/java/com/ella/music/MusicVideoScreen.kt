package com.ella.music

import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.rememberUpdatedState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.ella.music.data.SettingsManager
import com.ella.music.data.splitArtistNames
import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import com.ella.music.data.model.shiftedBy
import com.ella.music.data.repository.MusicRepository
import com.ella.music.player.CenterChannelSuppressorAudioProcessor
import com.ella.music.player.EllaRenderersFactory
import com.ella.music.ui.player.GlowSeekBar
import com.ella.music.ui.player.MusicVideoKtvLyrics
import com.ella.music.ui.player.buildMusicVideoMediaItem
import com.ella.music.viewmodel.lyricIdentityKey
import com.ella.music.viewmodel.LyricBlacklistRule
import com.ella.music.viewmodel.filterBlacklistedLyricLines
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Slider
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Play
import top.yukonga.miuix.kmp.icon.extended.Pause
import top.yukonga.miuix.kmp.icon.extended.Share
import top.yukonga.miuix.kmp.icon.extended.Trim
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.icon.extended.Mic
import top.yukonga.miuix.kmp.icon.basic.Check

@Composable
internal fun DetailMusicVideoScreen(
    song: Song,
    source: Uri,
    videoAspectRatio: Float?,
    initialLandscape: Boolean,
    initialOrientationMode: Int,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as MusicVideoActivity
    val inPictureInPictureMode = activity.pictureInPictureMode
    var landscape by remember { mutableStateOf(initialLandscape) }
    var orientationMode by remember { mutableStateOf(initialOrientationMode) }
    var captionsEnabled by remember { mutableStateOf(false) }
    var ktvLyricsEnabled by remember { mutableStateOf(false) }
    var accompanimentEnabled by remember { mutableStateOf(false) }
    var controlsLocked by remember { mutableStateOf(false) }
    val captionPreferences = remember(context) {
        context.getSharedPreferences(MUSIC_VIDEO_CAPTION_PREFERENCES, Context.MODE_PRIVATE)
    }
    var captionTranslationEnabled by remember {
        mutableStateOf(
            captionPreferences.getBoolean(
                MUSIC_VIDEO_CAPTION_TRANSLATION_ENABLED,
                true
            )
        )
    }
    var captionStyle by remember {
        mutableStateOf(MusicVideoCaptionStyle.load(captionPreferences))
    }
    var captionOffset by remember {
        mutableStateOf(Offset(captionStyle.positionX, captionStyle.positionY))
    }
    var captionSyncOffsetMs by remember(source) {
        mutableStateOf(captionPreferences.getLong(source.captionSyncPreferenceKey(), 0L))
    }
    var videoResizeMode by remember(captionPreferences) {
        mutableStateOf(
            captionPreferences.getInt(
                MUSIC_VIDEO_RESIZE_MODE,
                AspectRatioFrameLayout.RESIZE_MODE_FIT
            ).normalizedMusicVideoResizeMode()
        )
    }
    var showCaptionSettings by remember { mutableStateOf(false) }
    var showCaptureActions by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val captureSubtitles by settingsManager.musicVideoCaptureSubtitles.collectAsState(initial = false)
    val lyricOffsets by settingsManager.lyricOffsetOverrides.collectAsState(initial = emptyMap())
    val lyricLineBlacklist by settingsManager.lyricLineBlacklist.collectAsState(initial = emptyList())
    val hideLyricExtraInfo by settingsManager.hideLyricExtraInfo.collectAsState(initial = true)
    val importedMvOffsets by settingsManager.musicVideoOffsetsJson.collectAsState(initial = "")
    val effectiveMvOffsetMs = remember(source, importedMvOffsets) {
        MusicVideoOffsetsParser.loadForSource(context, source, importedMvOffsets).forSource(source)
    }
    val repository = remember(context) { MusicRepository.getInstance(context) }
    val lyricsNeeded = captionsEnabled || ktvLyricsEnabled || (showCaptureActions && captureSubtitles)
    val lyrics by produceState<List<LyricLine>>(
        emptyList(),
        song.path,
        lyricsNeeded,
        lyricOffsets[song.lyricIdentityKey()]
        , effectiveMvOffsetMs,
        captionSyncOffsetMs,
        lyricLineBlacklist,
        hideLyricExtraInfo
    ) {
        value = if (lyricsNeeded) {
            withContext(Dispatchers.IO) { repository.getLyrics(song) }
                .filterBlacklistedLyricLines(
                    rules = lyricLineBlacklist.map(::LyricBlacklistRule),
                    hideExtraInfo = hideLyricExtraInfo
                )
                .shiftedBy(
                    (lyricOffsets[song.lyricIdentityKey()] ?: 0L) +
                        effectiveMvOffsetMs +
                        captionSyncOffsetMs
                )
        } else {
            emptyList()
        }
    }
    val accompanimentProcessor = remember { CenterChannelSuppressorAudioProcessor() }
    val player = remember(source) {
        val trackSelector = DefaultTrackSelector(context).apply {
            // MV subtitle tracks are often undetermined-language TTML/WebVTT tracks. Keep them
            // selectable so PlayerView can render the embedded KTV line when the video provides it.
            parameters = buildUponParameters()
                .setSelectUndeterminedTextLanguage(true)
                .build()
        }
        val renderersFactory = EllaRenderersFactory(context).apply {
            setExtraAudioProcessors(listOf(accompanimentProcessor))
            setEnableDecoderFallback(true)
            setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
        }
        ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .build().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            setMediaItem(context.buildMusicVideoMediaItem(source))
            prepare()
            playWhenReady = true
        }
    }
    LaunchedEffect(accompanimentEnabled) {
        accompanimentProcessor.enabled = accompanimentEnabled
    }
    DisposableEffect(activity, player) {
        activity.attachMusicVideoPlayer(player)
        activity.configurePictureInPicture(
            aspectRatio = videoAspectRatio,
            autoEnter = player.isPlaying
        )
        onDispose {
            activity.configurePictureInPicture(autoEnter = false)
            if (activity.detachMusicVideoPlayer(player)) player.release()
        }
    }
    var isPlaying by remember { mutableStateOf(true) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(value: Boolean) {
                isPlaying = value
                activity.configurePictureInPicture(
                    aspectRatio = videoAspectRatio,
                    autoEnter = value
                )
            }
            override fun onPlaybackStateChanged(state: Int) {
                duration = player.duration.coerceAtLeast(0L)
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(
                    "MusicVideo",
                    "Playback failed (${error.errorCodeName}) for $source",
                    error
                )
                Toast.makeText(context, R.string.music_video_play_failed, Toast.LENGTH_SHORT).show()
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
        }
    }
    LaunchedEffect(player) {
        while (isActive) {
            position = player.currentPosition.coerceAtLeast(0L)
            duration = player.duration.coerceAtLeast(0L)
            delay(100L)
        }
    }
    LaunchedEffect(landscape, orientationMode) {
        activity.requestedOrientation = when (orientationMode) {
            SettingsManager.MUSIC_VIDEO_ORIENTATION_SYSTEM -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            else -> if (landscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
        controlsVisible = true
        activity.setLandscapeImmersive(landscape)
    }
    LaunchedEffect(landscape, controlsVisible, isPlaying, controlsLocked) {
        if (landscape && controlsVisible && isPlaying && !controlsLocked) {
            delay(3_000L)
            controlsVisible = false
        }
    }
    LaunchedEffect(inPictureInPictureMode) {
        if (inPictureInPictureMode) {
            controlsVisible = false
            showCaptionSettings = false
            showCaptureActions = false
        } else {
            controlsVisible = true
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
        if (inPictureInPictureMode) {
            VideoSurface(
                player = player,
                resizeMode = videoResizeMode,
                modifier = Modifier.fillMaxSize()
            )
            if (captionsEnabled) {
                MusicVideoCaptions(
                    lyrics = lyrics,
                    position = position,
                    videoAspectRatio = videoAspectRatio,
                    fillVideoBounds = videoResizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    positionOffset = captionOffset,
                    style = captionStyle,
                    showTranslation = captionTranslationEnabled,
                    locked = true,
                    onPositionOffsetChange = {},
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else if (landscape) {
            LandscapeMusicVideoLayout(
                song = song,
                player = player,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                lyrics = lyrics,
                videoAspectRatio = videoAspectRatio,
                videoResizeMode = videoResizeMode,
                captionsEnabled = captionsEnabled,
                captionTranslationEnabled = captionTranslationEnabled,
                captionsAvailable = captionsAvailable,
                ktvLyricsEnabled = ktvLyricsEnabled,
                accompanimentEnabled = accompanimentEnabled,
                controlsLocked = controlsLocked,
                captionOffset = captionOffset,
                captionStyle = captionStyle,
                controlsVisible = controlsVisible,
                onBack = { player.pause(); onBack() },
                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                onSeek = { player.seekTo(it) },
                onOpenCaptionSettings = { showCaptionSettings = true },
                onCaptionOffsetChange = {
                    captionOffset = it
                    captionStyle = captionStyle.copy(positionX = it.x, positionY = it.y)
                    captionStyle.save(captionPreferences)
                },
                onToggleKtvLyrics = {
                    ktvLyricsEnabled = !ktvLyricsEnabled
                    if (ktvLyricsEnabled) captionsEnabled = false
                },
                onToggleAccompaniment = { accompanimentEnabled = !accompanimentEnabled },
                onToggleLock = { controlsLocked = !controlsLocked },
                onPortrait = {
                    orientationMode = SettingsManager.MUSIC_VIDEO_ORIENTATION_PORTRAIT
                    landscape = false
                },
                onCapture = { showCaptureActions = true },
                onPictureInPicture = {
                    if (!activity.enterMusicVideoPictureInPicture(videoAspectRatio)) {
                        Toast.makeText(
                            context,
                            R.string.music_video_picture_in_picture_failed,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onShare = { MusicVideoLauncher.share(context, source, song.title) },
                onControlsVisibleChange = { controlsVisible = it }
            )
        } else {
            PortraitMusicVideoLayout(
                song = song,
                player = player,
                videoResizeMode = videoResizeMode,
                isPlaying = isPlaying,
                position = position,
                duration = duration,
                controlsVisible = controlsVisible,
                onBack = { player.pause(); onBack() },
                onTogglePlay = { if (player.isPlaying) player.pause() else player.play() },
                onSeek = { player.seekTo(it) },
                onLandscape = {
                    orientationMode = SettingsManager.MUSIC_VIDEO_ORIENTATION_LANDSCAPE
                    landscape = true
                },
                onShare = { MusicVideoLauncher.share(context, source, song.title) },
                onControlsVisibleChange = { controlsVisible = it }
            )
        }
        if (showCaptionSettings) {
            MusicVideoCaptionSettingsOverlay(
                enabled = captionsEnabled,
                translationEnabled = captionTranslationEnabled,
                syncOffsetMs = captionSyncOffsetMs,
                videoResizeMode = videoResizeMode,
                style = captionStyle,
                onEnabledChange = { enabled ->
                    captionsEnabled = enabled
                    if (enabled) ktvLyricsEnabled = false
                },
                onTranslationEnabledChange = { enabled ->
                    captionTranslationEnabled = enabled
                    captionPreferences.edit()
                        .putBoolean(MUSIC_VIDEO_CAPTION_TRANSLATION_ENABLED, enabled)
                        .apply()
                },
                onSyncOffsetChange = { offsetMs ->
                    captionSyncOffsetMs = offsetMs.coerceIn(-60_000L, 60_000L)
                    captionPreferences.edit()
                        .putLong(source.captionSyncPreferenceKey(), captionSyncOffsetMs)
                        .apply()
                },
                onVideoResizeModeChange = { resizeMode ->
                    videoResizeMode = resizeMode.normalizedMusicVideoResizeMode()
                    captionPreferences.edit()
                        .putInt(MUSIC_VIDEO_RESIZE_MODE, videoResizeMode)
                        .apply()
                },
                onStyleChange = { style ->
                    captionStyle = style
                    captionOffset = Offset(style.positionX, style.positionY)
                    style.save(captionPreferences)
                },
                onDismiss = { showCaptionSettings = false }
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
                            captureVideoFrame(
                                context = context,
                                source = source,
                                positionMs = capturePosition,
                                includeCaptions = captureSubtitles,
                                lyrics = captureLyrics,
                                captionStyle = captionStyle,
                                includeTranslation = true
                            )
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
                            captureVideoFrameFile(
                                context = context,
                                source = source,
                                positionMs = capturePosition,
                                includeCaptions = captureSubtitles,
                                lyrics = captureLyrics,
                                captionStyle = captionStyle,
                                includeTranslation = true
                            )
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
    videoResizeMode: Int,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    controlsVisible: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onLandscape: () -> Unit,
    onShare: () -> Unit,
    onControlsVisibleChange: (Boolean) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        VideoSurface(
            player = player,
            resizeMode = videoResizeMode,
            modifier = Modifier.fillMaxSize()
        )
        // Keep the gesture surface behind the controls but above the video. This makes a tap on
        // any video area (including fitted black bars) toggle the chrome, while the top/bottom
        // buttons keep their own click targets.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(controlsVisible, onTogglePlay) {
                    detectTapGestures(
                        onTap = { onControlsVisibleChange(!controlsVisible) },
                        onDoubleTap = { onTogglePlay() }
                    )
                }
        )
        if (controlsVisible) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    VideoIconButton(MiuixIcons.Regular.Back, stringResource(R.string.common_back), onBack)
                    IconButton(onClick = onShare) {
                        com.ella.music.ui.player.QuickActionIcon(
                            kind = com.ella.music.ui.player.PlayerQuickActionKind.Share,
                            color = ComposeColor.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
                }
                Column(modifier = Modifier.fillMaxWidth()) {
                    ArtistTitleBlock(song = song)
                    VideoTransport(
                        isPlaying = isPlaying,
                        position = position,
                        duration = duration,
                        onTogglePlay = onTogglePlay,
                        onSeek = onSeek,
                        trailingLabel = stringResource(R.string.player_music_video_landscape),
                        trailingIconRes = R.drawable.ic_fullscreen,
                        onTrailing = onLandscape
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapeMusicVideoLayout(
    song: Song,
    player: ExoPlayer,
    videoResizeMode: Int,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    lyrics: List<LyricLine>,
    videoAspectRatio: Float?,
    captionsEnabled: Boolean,
    captionTranslationEnabled: Boolean,
    captionsAvailable: Boolean,
    ktvLyricsEnabled: Boolean,
    accompanimentEnabled: Boolean,
    controlsLocked: Boolean,
    captionOffset: Offset?,
    captionStyle: MusicVideoCaptionStyle,
    controlsVisible: Boolean,
    onBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Long) -> Unit,
    onOpenCaptionSettings: () -> Unit,
    onCaptionOffsetChange: (Offset) -> Unit,
    onToggleKtvLyrics: () -> Unit,
    onToggleAccompaniment: () -> Unit,
    onToggleLock: () -> Unit,
    onPortrait: () -> Unit,
    onCapture: () -> Unit,
    onPictureInPicture: () -> Unit,
    onShare: () -> Unit,
    onControlsVisibleChange: (Boolean) -> Unit
) {
    var gestureFeedback by remember { mutableStateOf<MusicVideoGestureFeedback?>(null) }
    var gestureFeedbackRevision by remember { mutableStateOf(0) }
    LaunchedEffect(gestureFeedbackRevision) {
        if (gestureFeedbackRevision > 0) {
            delay(850L)
            gestureFeedback = null
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        VideoSurface(
            player = player,
            resizeMode = videoResizeMode,
            modifier = Modifier.fillMaxSize()
        )
        // Keep the whole view tappable; controls sit above this layer and retain their own actions.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .musicVideoGestureControls(
                    player = player,
                    duration = duration,
                    onSeek = onSeek,
                    onFeedback = { feedback ->
                        gestureFeedback = feedback
                        gestureFeedbackRevision += 1
                    },
                    onTap = { onControlsVisibleChange(!controlsVisible) },
                    onDoubleTap = onTogglePlay
                )
        )
        gestureFeedback?.let { feedback ->
            MusicVideoGestureFeedbackOverlay(feedback = feedback)
        }
        if (ktvLyricsEnabled) {
            MusicVideoKtvLyrics(
                lyrics = lyrics,
                position = position,
                videoAspectRatio = videoAspectRatio,
                fillVideoBounds = videoResizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                outlined = true,
                alternateCurrentAndNext = true,
                modifier = Modifier.fillMaxSize()
            )
        } else if (captionsEnabled) {
            MusicVideoCaptions(
                lyrics = lyrics,
                position = position,
                videoAspectRatio = videoAspectRatio,
                fillVideoBounds = videoResizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                positionOffset = captionOffset,
                style = captionStyle,
                showTranslation = captionTranslationEnabled,
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
                    IconButton(onClick = onPictureInPicture) {
                        Icon(
                            painter = androidx.compose.ui.res.painterResource(
                                R.drawable.ic_picture_in_picture
                            ),
                            contentDescription = stringResource(
                                R.string.music_video_picture_in_picture
                            ),
                            tint = ComposeColor.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
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
                            contentDescription = stringResource(R.string.player_music_video_portrait),
                            tint = ComposeColor.White,
                            modifier = Modifier.size(25.dp)
                        )
                    }
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
                    onTrailing = onOpenCaptionSettings,
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
        }
        if (controlsVisible || controlsLocked) {
            VideoIconButton(
                icon = if (controlsLocked) MiuixIcons.Regular.Lock else MiuixIcons.Regular.Unlock,
                description = stringResource(
                    if (controlsLocked) R.string.music_video_lock else R.string.music_video_unlock
                ),
                onClick = onToggleLock,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = 14.dp)
            )
        }
    }
}

private enum class MusicVideoGestureMode {
    Seek,
    Brightness,
    Volume
}

private data class MusicVideoGestureFeedback(
    val mode: MusicVideoGestureMode,
    val fraction: Float,
    val targetPositionMs: Long = 0L,
    val positionDeltaMs: Long = 0L
)

@Composable
private fun Modifier.musicVideoGestureControls(
    player: ExoPlayer,
    duration: Long,
    onSeek: (Long) -> Unit,
    onFeedback: (MusicVideoGestureFeedback) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: () -> Unit
): Modifier {
    val context = LocalContext.current
    val activity = context as? MusicVideoActivity
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val currentDuration by rememberUpdatedState(duration)
    val currentOnSeek by rememberUpdatedState(onSeek)
    val currentOnFeedback by rememberUpdatedState(onFeedback)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    return pointerInput(
        player,
        activity,
        audioManager
    ) {
        coroutineScope {
        var startTouch = Offset.Zero
        var accumulated = Offset.Zero
        var mode: MusicVideoGestureMode? = null
        var startPositionMs = 0L
        var startBrightness = 0.5f
        var startVolume = 0
        var pendingSingleTap: Job? = null
        var lastTapTimeMs = 0L
        var lastTapPosition = Offset.Zero
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            startTouch = down.position
            accumulated = Offset.Zero
            mode = null
            startPositionMs = player.currentPosition.coerceAtLeast(0L)
            startBrightness = activity?.window?.attributes?.screenBrightness
                ?.takeIf { it >= 0f }
                ?: (
                    Settings.System.getInt(
                        context.contentResolver,
                        Settings.System.SCREEN_BRIGHTNESS,
                        128
                    ) / 255f
                    )
            startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            var latestSeekTarget: Long? = null
            var releaseTimeMs = down.uptimeMillis
            var releasePosition = down.position
            var pointerPressed = true
            while (pointerPressed) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                pointerPressed = change.pressed
                releaseTimeMs = change.uptimeMillis
                releasePosition = change.position
                accumulated = change.position - startTouch
                if (
                    mode == null &&
                    accumulated.getDistance() >= viewConfiguration.touchSlop
                ) {
                    val horizontal = abs(accumulated.x)
                    val vertical = abs(accumulated.y)
                    mode = when {
                        horizontal >= vertical * 1.12f -> MusicVideoGestureMode.Seek
                        vertical >= horizontal * 1.12f && startTouch.x < size.width / 2f ->
                            MusicVideoGestureMode.Brightness
                        vertical >= horizontal * 1.12f -> MusicVideoGestureMode.Volume
                        else -> null
                    }
                }
                if (mode != null) change.consume()
                when (mode) {
                    MusicVideoGestureMode.Seek -> {
                        val safeDuration = currentDuration.coerceAtLeast(0L)
                        val startFraction = if (safeDuration > 0L) {
                            startPositionMs.toFloat() / safeDuration
                        } else {
                            0f
                        }
                        // One half-screen swipe can traverse most of the timeline, even on
                        // ultra-wide car displays. The player itself seeks only after release.
                        val targetFraction = (
                            startFraction +
                                accumulated.x / size.width.coerceAtLeast(1) * 1.8f
                            ).coerceIn(0f, 1f)
                        val targetPosition = (targetFraction * safeDuration).toLong()
                        latestSeekTarget = targetPosition
                        currentOnFeedback(
                            MusicVideoGestureFeedback(
                                mode = MusicVideoGestureMode.Seek,
                                fraction = targetFraction,
                                targetPositionMs = targetPosition,
                                positionDeltaMs = targetPosition - startPositionMs
                            )
                        )
                    }
                    MusicVideoGestureMode.Brightness -> {
                        val brightness = (
                            startBrightness -
                                accumulated.y / size.height.coerceAtLeast(1) * 1.65f
                            ).coerceIn(0.02f, 1f)
                        activity?.window?.attributes = activity.window.attributes.apply {
                            screenBrightness = brightness
                        }
                        currentOnFeedback(
                            MusicVideoGestureFeedback(
                                mode = MusicVideoGestureMode.Brightness,
                                fraction = brightness
                            )
                        )
                    }
                    MusicVideoGestureMode.Volume -> {
                        val maxVolume = audioManager
                            .getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                            .coerceAtLeast(1)
                        val volumeDelta = (
                            -accumulated.y / size.height.coerceAtLeast(1) *
                                maxVolume * 1.65f
                            ).roundToInt()
                        val volume = (startVolume + volumeDelta).coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(
                            AudioManager.STREAM_MUSIC,
                            volume,
                            0
                        )
                        currentOnFeedback(
                            MusicVideoGestureFeedback(
                                mode = MusicVideoGestureMode.Volume,
                                fraction = volume.toFloat() / maxVolume
                            )
                        )
                    }
                    null -> Unit
                }
            }
            latestSeekTarget?.let(currentOnSeek)
            if (
                mode == null &&
                accumulated.getDistance() < viewConfiguration.touchSlop
            ) {
                val elapsed = releaseTimeMs - lastTapTimeMs
                val isDoubleTap = lastTapTimeMs > 0L &&
                    elapsed in 40L..300L &&
                    (releasePosition - lastTapPosition).getDistance() <=
                    viewConfiguration.touchSlop * 4f
                if (isDoubleTap) {
                    pendingSingleTap?.cancel()
                    pendingSingleTap = null
                    lastTapTimeMs = 0L
                    currentOnDoubleTap()
                } else {
                    lastTapTimeMs = releaseTimeMs
                    lastTapPosition = releasePosition
                    pendingSingleTap?.cancel()
                    pendingSingleTap = launch {
                        delay(300L)
                        if (lastTapTimeMs == releaseTimeMs) {
                            lastTapTimeMs = 0L
                            currentOnTap()
                        }
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun MusicVideoGestureFeedbackOverlay(
    feedback: MusicVideoGestureFeedback
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (feedback.mode) {
            MusicVideoGestureMode.Seek -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ComposeColor.Black.copy(alpha = 0.58f))
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = feedback.targetPositionMs.formatVideoTime(),
                        color = ComposeColor.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = feedback.positionDeltaMs.formatSignedVideoTime(),
                        color = ComposeColor.White.copy(alpha = 0.9f),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            MusicVideoGestureMode.Brightness,
            MusicVideoGestureMode.Volume -> {
                val isBrightness = feedback.mode == MusicVideoGestureMode.Brightness
                Column(
                    modifier = Modifier
                        .align(if (isBrightness) Alignment.CenterEnd else Alignment.CenterStart)
                        .padding(horizontal = 30.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(ComposeColor.Black.copy(alpha = 0.58f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${(feedback.fraction.coerceIn(0f, 1f) * 100).roundToInt()}",
                        color = ComposeColor.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .padding(vertical = 8.dp)
                            .width(5.dp)
                            .height(132.dp)
                            .clip(RoundedCornerShape(99.dp))
                            .background(ComposeColor.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(feedback.fraction.coerceIn(0f, 1f))
                                .background(ComposeColor(0xFF4D8DFF))
                        )
                    }
                    Text(
                        text = stringResource(
                            if (isBrightness) {
                                R.string.music_video_gesture_brightness
                            } else {
                                R.string.music_video_gesture_volume
                            }
                        ),
                        color = ComposeColor.White.copy(alpha = 0.9f),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

internal data class MusicVideoCaptionStyle(
    val positionX: Float = 0.5f,
    val positionY: Float = 0.78f,
    val fontSizeSp: Float = 21f,
    val scale: Float = 1f,
    val fontFamily: Int = 0,
    val textColorArgb: Int = 0xFFFFFFFF.toInt(),
    val bold: Boolean = true,
    val backgroundColorArgb: Int = 0xFF000000.toInt(),
    val backgroundAlpha: Float = 0.42f
) {
    val resolvedFontFamily: FontFamily
        get() = when (fontFamily) {
            1 -> FontFamily.Serif
            2 -> FontFamily.Monospace
            else -> FontFamily.SansSerif
        }

    fun save(preferences: android.content.SharedPreferences) {
        preferences.edit()
            .putFloat("position_x", positionX)
            .putFloat("position_y", positionY)
            .putFloat("font_size_sp", fontSizeSp)
            .putFloat("scale", scale)
            .putInt("font_family", fontFamily)
            .putInt("text_color", textColorArgb)
            .putBoolean("bold", bold)
            .putInt("background_color", backgroundColorArgb)
            .putFloat("background_alpha", backgroundAlpha)
            .apply()
    }

    companion object {
        fun load(preferences: android.content.SharedPreferences): MusicVideoCaptionStyle =
            MusicVideoCaptionStyle(
                positionX = preferences.getFloat("position_x", 0.5f),
                positionY = preferences.getFloat("position_y", 0.78f),
                fontSizeSp = preferences.getFloat("font_size_sp", 21f),
                scale = preferences.getFloat("scale", 1f),
                fontFamily = preferences.getInt("font_family", 0),
                textColorArgb = preferences.getInt("text_color", 0xFFFFFFFF.toInt()),
                bold = preferences.getBoolean("bold", true),
                backgroundColorArgb = preferences.getInt(
                    "background_color",
                    0xFF000000.toInt()
                ),
                backgroundAlpha = preferences.getFloat("background_alpha", 0.42f)
            )
    }
}

@Composable
private fun MusicVideoCaptionSettingsOverlay(
    enabled: Boolean,
    translationEnabled: Boolean,
    syncOffsetMs: Long,
    videoResizeMode: Int,
    style: MusicVideoCaptionStyle,
    onEnabledChange: (Boolean) -> Unit,
    onTranslationEnabledChange: (Boolean) -> Unit,
    onSyncOffsetChange: (Long) -> Unit,
    onVideoResizeModeChange: (Int) -> Unit,
    onStyleChange: (MusicVideoCaptionStyle) -> Unit,
    onDismiss: () -> Unit
) {
    var offsetInput by remember(syncOffsetMs) { mutableStateOf(syncOffsetMs.toString()) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.46f)
                .widthIn(min = 320.dp, max = 560.dp)
                .fillMaxHeight()
                .windowInsetsPadding(WindowInsets.displayCutout)
                .clip(
                    RoundedCornerShape(
                        topStart = 24.dp,
                        bottomStart = 24.dp
                    )
                )
                .background(ComposeColor.Black.copy(alpha = 0.68f))
                .clickable { }
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoIconButton(
                    icon = MiuixIcons.Regular.Back,
                    description = stringResource(R.string.common_back),
                    onClick = onDismiss,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    stringResource(R.string.music_video_caption_settings),
                    color = ComposeColor.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                VideoTextButton(
                    text = stringResource(
                        if (enabled) R.string.common_close else R.string.common_open
                    ),
                    onClick = { onEnabledChange(!enabled) },
                    selected = enabled
                )
            }

            CaptionSettingsHeading(stringResource(R.string.music_video_caption_display))
            VideoTextButton(
                text = stringResource(R.string.music_video_caption_translation),
                onClick = { onTranslationEnabledChange(!translationEnabled) },
                selected = translationEnabled
            )

            CaptionSettingsHeading(stringResource(R.string.music_video_video_ratio))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                VideoTextButton(
                    text = stringResource(R.string.music_video_video_ratio_fit),
                    onClick = {
                        onVideoResizeModeChange(AspectRatioFrameLayout.RESIZE_MODE_FIT)
                    },
                    selected = videoResizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT,
                    modifier = Modifier.weight(1f)
                )
                VideoTextButton(
                    text = stringResource(R.string.music_video_video_ratio_zoom),
                    onClick = {
                        onVideoResizeModeChange(AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
                    },
                    selected = videoResizeMode == AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    modifier = Modifier.weight(1f)
                )
            }

            CaptionSettingsHeading(stringResource(R.string.music_video_caption_sync))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                VideoTextButton("-100 ms", { onSyncOffsetChange(syncOffsetMs - 100L) })
                BasicTextField(
                    value = offsetInput,
                    onValueChange = { value ->
                        val filtered = value.filterIndexed { index, char ->
                            char.isDigit() || (char == '-' && index == 0)
                        }
                        offsetInput = filtered
                        filtered.toLongOrNull()?.let(onSyncOffsetChange)
                    },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = ComposeColor.White,
                        fontSize = 15.sp,
                        textAlign = TextAlign.Center
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(ComposeColor.White.copy(alpha = 0.10f))
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                )
                VideoTextButton("+100 ms", { onSyncOffsetChange(syncOffsetMs + 100L) })
            }
            Text(
                stringResource(R.string.music_video_caption_sync_hint),
                color = ComposeColor.White.copy(alpha = 0.58f),
                fontSize = 12.sp
            )

            CaptionSettingsHeading(stringResource(R.string.music_video_caption_custom))
            CaptionSettingsSlider(
                label = stringResource(R.string.music_video_caption_vertical_position),
                value = style.positionY,
                valueRange = 0.08f..0.92f,
                displayValue = "${(style.positionY * 100).roundToInt()}%"
            ) { onStyleChange(style.copy(positionY = it)) }
            CaptionSettingsSlider(
                label = stringResource(R.string.player_lyric_font_size),
                value = style.fontSizeSp,
                valueRange = 14f..40f,
                displayValue = "${style.fontSizeSp.roundToInt()} sp"
            ) { onStyleChange(style.copy(fontSizeSp = it)) }
            CaptionSettingsSlider(
                label = stringResource(R.string.music_video_caption_scale),
                value = style.scale,
                valueRange = 0.75f..1.5f,
                displayValue = "${(style.scale * 100).roundToInt()}%"
            ) { onStyleChange(style.copy(scale = it)) }

            CaptionSettingsHeading(stringResource(R.string.music_video_caption_font))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    R.string.music_video_caption_font_default,
                    R.string.music_video_caption_font_serif,
                    R.string.music_video_caption_font_monospace
                ).forEachIndexed { index, label ->
                    VideoTextButton(
                        text = stringResource(label),
                        onClick = { onStyleChange(style.copy(fontFamily = index)) },
                        selected = style.fontFamily == index
                    )
                }
                VideoTextButton(
                    text = stringResource(R.string.music_video_caption_bold),
                    onClick = { onStyleChange(style.copy(bold = !style.bold)) },
                    selected = style.bold
                )
            }

            CaptionSettingsHeading(stringResource(R.string.music_video_caption_text_color))
            CaptionColorChoices(
                selectedArgb = style.textColorArgb,
                colors = CAPTION_TEXT_COLORS,
                onSelected = { onStyleChange(style.copy(textColorArgb = it)) }
            )
            CaptionSettingsHeading(
                stringResource(R.string.music_video_caption_background_color)
            )
            CaptionColorChoices(
                selectedArgb = style.backgroundColorArgb,
                colors = CAPTION_BACKGROUND_COLORS,
                onSelected = { onStyleChange(style.copy(backgroundColorArgb = it)) }
            )
            CaptionSettingsSlider(
                label = stringResource(R.string.music_video_caption_background_opacity),
                value = style.backgroundAlpha,
                valueRange = 0f..0.9f,
                displayValue = "${(style.backgroundAlpha * 100).roundToInt()}%"
            ) { onStyleChange(style.copy(backgroundAlpha = it)) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                VideoTextButton(
                    stringResource(R.string.common_reset),
                    onClick = { onStyleChange(MusicVideoCaptionStyle()) }
                )
                VideoTextButton(
                    stringResource(R.string.common_confirm),
                    onClick = onDismiss,
                    selected = true,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun CaptionSettingsHeading(text: String) {
    Text(
        text = text,
        color = ComposeColor.White.copy(alpha = 0.86f),
        fontSize = 15.sp,
        fontWeight = FontWeight.Bold
    )
}

@Composable
private fun CaptionSettingsSlider(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(label, color = ComposeColor.White.copy(alpha = 0.82f), modifier = Modifier.weight(1f))
            Text(displayValue, color = ComposeColor.White.copy(alpha = 0.62f))
        }
        Slider(
            value = value.coerceIn(valueRange.start, valueRange.endInclusive),
            onValueChange = onValueChange,
            valueRange = valueRange
        )
    }
}

@Composable
private fun CaptionColorChoices(
    selectedArgb: Int,
    colors: List<Int>,
    onSelected: (Int) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        colors.forEach { argb ->
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(ComposeColor(argb))
                    .border(
                        width = if (argb == selectedArgb) 3.dp else 1.dp,
                        color = if (argb == selectedArgb) {
                            ComposeColor(0xFF4D7CFE)
                        } else {
                            ComposeColor.White.copy(alpha = 0.45f)
                        },
                        shape = RoundedCornerShape(999.dp)
                    )
                    .clickable { onSelected(argb) }
            )
        }
    }
}

@Composable
private fun VideoSurface(
    player: ExoPlayer,
    resizeMode: Int,
    modifier: Modifier
) {
    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = false
                this.resizeMode = resizeMode
                setShutterBackgroundColor(Color.BLACK)
                configureEmbeddedSubtitles()
                this.player = player
            }
        },
        update = {
            it.resizeMode = resizeMode
            it.configureEmbeddedSubtitles()
            it.player = player
        },
        modifier = modifier
    )
}

private fun PlayerView.configureEmbeddedSubtitles() {
    subtitleView?.apply {
        visibility = android.view.View.VISIBLE
        setApplyEmbeddedStyles(true)
        setApplyEmbeddedFontSizes(true)
    }
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
    selected: Boolean = false,
    modifier: Modifier = Modifier
) {
    IconButton(onClick = onClick, modifier = modifier) {
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
    fillVideoBounds: Boolean,
    positionOffset: Offset?,
    style: MusicVideoCaptionStyle,
    showTranslation: Boolean,
    locked: Boolean,
    onPositionOffsetChange: (Offset) -> Unit,
    modifier: Modifier
) {
    // TTML x-bg is accompaniment metadata, not a second subtitle track for the MV player.
    val primary = lyrics.lastOrNull { it.timeMs <= position && it.text.isNotBlank() }
    if (primary == null) return
    BoxWithConstraints(modifier = modifier) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val screenRatio = maxWidth.value / maxHeight.value.coerceAtLeast(1f)
        // Match PlayerView: FIT keeps the subtitle inside the video frame, while ZOOM uses the
        // whole surface because the video itself is cropped to fill it.
        val frameModifier = when {
            fillVideoBounds || videoAspectRatio == null -> Modifier.fillMaxSize()
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
                style = style,
                showTranslation = showTranslation,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (constrainedOffset.x * frameWidthPx - captionSize.width / 2f).roundToInt(),
                            (constrainedOffset.y * frameHeightPx - captionSize.height / 2f).roundToInt()
                        )
                    }
                    .fillMaxWidth((0.82f * style.scale).coerceIn(0.55f, 0.96f))
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
                horizontalAlignment = Alignment.CenterHorizontally
            )
        }
    }
}

@Composable
private fun CaptionBlock(
    primary: LyricLine,
    style: MusicVideoCaptionStyle,
    showTranslation: Boolean,
    modifier: Modifier,
    textAlign: TextAlign,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start
) {
    Column(modifier = modifier, horizontalAlignment = horizontalAlignment) {
        CaptionLine(primary.text, textAlign, style)
        if (showTranslation) {
            CaptionLine(
                text = primary.translation,
                textAlign = textAlign,
                style = style,
                secondary = true
            )
        }
    }
}

@Composable
private fun CaptionLine(
    text: String?,
    textAlign: TextAlign,
    style: MusicVideoCaptionStyle,
    secondary: Boolean = false
) {
    text?.takeIf { it.isNotBlank() }?.let {
        Text(
            it,
            color = ComposeColor(style.textColorArgb).copy(alpha = if (secondary) 0.88f else 1f),
            fontSize = (
                style.fontSizeSp *
                    style.scale *
                    if (secondary) 0.76f else 1f
                ).sp,
            fontWeight = if (!secondary && style.bold) FontWeight.Bold else FontWeight.Medium,
            fontFamily = style.resolvedFontFamily,
            textAlign = textAlign,
            modifier = Modifier
                .wrapContentWidth(if (textAlign == TextAlign.Center) Alignment.CenterHorizontally else Alignment.Start)
                .clip(RoundedCornerShape(5.dp))
                .background(
                    ComposeColor(style.backgroundColorArgb).copy(
                        alpha = style.backgroundAlpha
                    )
                )
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

private fun Long.formatSignedVideoTime(): String {
    val sign = if (this >= 0L) "+" else "−"
    return "[$sign${abs(this).formatVideoTime()}]"
}

private fun Uri.captionSyncPreferenceKey(): String =
    "sync_${toString().hashCode().toUInt().toString(16)}"

private const val MUSIC_VIDEO_CAPTION_PREFERENCES = "music_video_caption_preferences"
private const val MUSIC_VIDEO_CAPTION_TRANSLATION_ENABLED = "translation_enabled"
private const val MUSIC_VIDEO_RESIZE_MODE = "video_resize_mode"

private fun Int.normalizedMusicVideoResizeMode(): Int = when (this) {
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
    else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
}

private val CAPTION_TEXT_COLORS = listOf(
    0xFFFFFFFF.toInt(),
    0xFFFFE082.toInt(),
    0xFF80D8FF.toInt(),
    0xFFA7FFEB.toInt(),
    0xFFFF80AB.toInt()
)

private val CAPTION_BACKGROUND_COLORS = listOf(
    0xFF000000.toInt(),
    0xFF263238.toInt(),
    0xFF1A237E.toInt(),
    0xFF4A148C.toInt(),
    0xFFB71C1C.toInt()
)

private fun openArtistFromVideo(context: Context, artist: String) {
    if (artist.isBlank()) return
    (context as? MusicVideoActivity)?.pauseForArtistNavigation()
    context.startActivity(Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("halcyon://artist/${Uri.encode(artist)}")
    })
}
