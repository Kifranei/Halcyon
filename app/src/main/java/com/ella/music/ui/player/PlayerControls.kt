package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ella.music.R
import com.ella.music.data.SettingsManager
import com.ella.music.data.audioQualitySummary
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.ui.components.PlayerQueueListIcon
import kotlinx.coroutines.launch
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text

@Composable
internal fun LandscapeProgressRow(
    currentPosition: Long,
    duration: Long,
    palette: PlayerPalette,
    allowTapSeek: Boolean,
    showTotalDuration: Boolean,
    onSeek: (Float) -> Unit
) {
    var previewProgress by remember { mutableStateOf<Float?>(null) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = formatTime(currentPosition),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                // Keep the real position visible while previewing a seek target.
                color = palette.onBackground.copy(alpha = if (previewProgress == null) 0.72f else 0.48f)
            )
            previewProgress?.let { progress ->
                Text(
                    text = formatTime((duration * progress).toLong()),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = palette.onBackground.copy(alpha = 0.82f),
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
        GlowSeekBar(
            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onSeek = onSeek,
            accent = palette.accent,
            allowTapSeek = allowTapSeek,
            onPreviewProgressChange = { previewProgress = it },
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp)
        )
        Text(
            text = if (showTotalDuration || previewProgress != null) {
                formatTime(duration.coerceAtLeast(0L))
            } else {
                "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}"
            },
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = palette.onBackground.copy(alpha = 0.72f)
        )
    }
}

@Composable
internal fun LandscapeTransportControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    palette: PlayerPalette,
    onCyclePlaybackMode: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    controlHeight: androidx.compose.ui.unit.Dp = 58.dp,
    sideIconSize: androidx.compose.ui.unit.Dp = 30.dp,
    playButtonSize: androidx.compose.ui.unit.Dp = 54.dp,
    playIconSize: androidx.compose.ui.unit.Dp = 34.dp
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(controlHeight),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerTransportIconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.common_previous),
                tint = palette.onBackground.copy(alpha = 0.92f),
                modifier = Modifier.size(sideIconSize)
            )
        }
        Box(
            modifier = Modifier
                .size(playButtonSize)
                .clip(CircleShape)
                .playerNoIndicationClick(onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            CenteredPlayPauseGlyph(
                isPlaying = isPlaying,
                tint = palette.onBackground.copy(alpha = 0.96f),
                modifier = Modifier.size(playIconSize)
            )
        }
        PlayerTransportIconButton(onClick = onNext) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.common_next),
                tint = palette.onBackground.copy(alpha = 0.92f),
                modifier = Modifier.size(sideIconSize)
            )
        }
    }
}

@Composable
internal fun PlayerProgressBlock(
    currentPosition: Long,
    duration: Long,
    audioInfo: AudioInfo?,
    bluetoothDeviceName: String?,
    playbackModeLabel: String? = null,
    palette: PlayerPalette,
    allowTapSeek: Boolean,
    showTotalDuration: Boolean,
    onSeek: (Float) -> Unit
) {
    val context = LocalContext.current
    val isTablet = LocalConfiguration.current.smallestScreenWidthDp >= 600
    val scope = rememberCoroutineScope()
    val settingsManager = remember(context) { SettingsManager.getInstance(context) }
    val savedInfoMode by settingsManager.playerProgressInfoIndex.collectAsState(initial = 0)
    var infoMode by remember { mutableIntStateOf(0) }
    var previewProgress by remember { mutableStateOf<Float?>(null) }
    val infoLabels = remember(audioInfo, bluetoothDeviceName, playbackModeLabel) {
        buildList {
            playbackModeLabel?.takeIf { it.isNotBlank() }?.let(::add) ?: run {
                audioInfo?.let {
                    val quality = audioQualitySummary(it)
                    add(quality.playerCompactText())
                    quality.detailLabel.takeIf { text -> text.isNotBlank() }?.let(::add)
                }
                bluetoothDeviceName?.takeIf { it.isNotBlank() }?.let(::add)
            }
        }.distinct()
    }
    val replayGainLabel = audioInfo?.replayGainDb?.let { gain ->
        stringResource(
            R.string.player_replay_gain_badge,
            String.format(Locale.US, "%+.2f dB", gain)
        )
    }
    androidx.compose.runtime.LaunchedEffect(savedInfoMode, infoLabels.size) {
        infoMode = if (infoLabels.isEmpty()) 0 else savedInfoMode % infoLabels.size
    }
    Column(modifier = Modifier.fillMaxWidth()) {
        GlowSeekBar(
            value = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f,
            onSeek = onSeek,
            accent = palette.accent,
            allowTapSeek = allowTapSeek,
            onPreviewProgressChange = { previewProgress = it },
            modifier = Modifier.fillMaxWidth()
        )
        Box(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatTime(currentPosition),
                    fontSize = 14.sp,
                    // Do not replace the current time: the adjacent label is the seek preview.
                    color = palette.onBackground.copy(alpha = if (previewProgress == null) 0.72f else 0.48f)
                )
                previewProgress?.let { progress ->
                    Text(
                        text = formatTime((duration * progress).toLong()),
                        fontSize = 14.sp,
                        color = palette.onBackground.copy(alpha = 0.82f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isTablet && (infoLabels.isNotEmpty() || replayGainLabel != null)) {
                    val infoText = infoLabels.getOrNull(infoMode % infoLabels.size.coerceAtLeast(1))
                    Text(
                        text = listOfNotNull(
                            infoText,
                            replayGainLabel
                        ).joinToString(" / "),
                        fontSize = 12.sp,
                        color = palette.onBackground.copy(alpha = 0.62f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.onBackground.copy(alpha = 0.10f))
                            .pointerInput(infoLabels, bluetoothDeviceName) {
                                detectTapGestures(
                                    onTap = {
                                        if (infoLabels.size > 1) {
                                            val nextMode = (infoMode + 1) % infoLabels.size
                                            infoMode = nextMode
                                            scope.launch { settingsManager.setPlayerProgressInfoIndex(nextMode) }
                                        }
                                    },
                                    onLongPress = {
                                        if (!bluetoothDeviceName.isNullOrBlank()) {
                                            openSystemOutputSwitcher(context)
                                        }
                                    }
                                )
                            }
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                } else {
                    if (infoLabels.isNotEmpty()) {
                        val infoText = infoLabels[infoMode % infoLabels.size]
                        Text(
                            text = infoText,
                            fontSize = 12.sp,
                            color = palette.onBackground.copy(alpha = 0.62f),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(palette.onBackground.copy(alpha = 0.10f))
                                .pointerInput(infoLabels, bluetoothDeviceName) {
                                    detectTapGestures(
                                        onTap = {
                                            if (infoLabels.size > 1) {
                                                val nextMode = (infoMode + 1) % infoLabels.size
                                                infoMode = nextMode
                                                scope.launch { settingsManager.setPlayerProgressInfoIndex(nextMode) }
                                            }
                                        },
                                        onLongPress = {
                                            if (!bluetoothDeviceName.isNullOrBlank()) {
                                                openSystemOutputSwitcher(context)
                                            }
                                        }
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        )
                    }
                    replayGainLabel?.let { label ->
                    Text(
                        text = label,
                        fontSize = 12.sp,
                        color = palette.onBackground.copy(alpha = 0.72f),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.onBackground.copy(alpha = 0.10f))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    )
                    }
                }
            }
            Text(
                text = if (showTotalDuration || previewProgress != null) {
                    formatTime(duration.coerceAtLeast(0L))
                } else {
                    "-${formatTime((duration - currentPosition).coerceAtLeast(0L))}"
                },
                fontSize = 14.sp,
                color = palette.onBackground.copy(alpha = 0.72f),
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
internal fun PlayerTransportControls(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    palette: PlayerPalette,
    queueExpanded: Boolean,
    playlist: List<Song>,
    currentSongKey: String?,
    queueLocked: Boolean,
    favoriteSongKeys: Set<String> = emptySet(),
    loadSongRating: (Song) -> Int = { 0 },
    ratingRevision: Int = 0,
    onCyclePlaybackMode: () -> Unit,
    onToggleQueueLock: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onToggleQueue: () -> Unit,
    onDismissQueue: () -> Unit,
    onQueueSongClick: (Int) -> Unit,
    onRemoveQueueSong: (Int) -> Unit,
    onMoveQueueSong: (Int, Int) -> Unit,
    onRandomizeQueue: () -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settingsManager = remember { SettingsManager.getInstance(context) }
    val showOutlines by settingsManager.transportButtonOutlines.collectAsState(
        initial = SettingsManager.DEFAULT_TRANSPORT_BUTTON_OUTLINES
    )
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PlayerTransportIconButton(onClick = onCyclePlaybackMode) {
            PlaybackModeIcon(
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                color = palette.onBackground.copy(alpha = 0.92f)
            )
        }
        PlayerTransportIconButton(onClick = onPrevious) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_previous),
                contentDescription = stringResource(R.string.common_previous),
                tint = palette.onBackground.copy(alpha = 0.92f),
                modifier = Modifier.size(30.dp)
            )
        }
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .then(if (showOutlines) Modifier.background(palette.onBackground.copy(alpha = 0.18f)) else Modifier)
                .playerNoIndicationClick(onPlayPause),
            contentAlignment = Alignment.Center
        ) {
            CenteredPlayPauseGlyph(
                isPlaying = isPlaying,
                tint = palette.onBackground.copy(alpha = 0.96f),
                modifier = Modifier.size(if (isPlaying) 34.dp else 36.dp)
            )
        }
        PlayerTransportIconButton(onClick = onNext) {
            Icon(
                painter = painterResource(id = R.drawable.ic_skip_next),
                contentDescription = stringResource(R.string.common_next),
                tint = palette.onBackground.copy(alpha = 0.92f),
                modifier = Modifier.size(30.dp)
            )
        }
        Box(contentAlignment = Alignment.Center) {
            PlayerTransportIconButton(onClick = onToggleQueue) {
                PlayerQueueListIcon(
                    color = palette.onBackground.copy(alpha = 0.92f),
                    modifier = Modifier.size(34.dp)
                )
            }
            PlayerQueueSheet(
                show = queueExpanded,
                playlist = playlist,
                currentSongKey = currentSongKey,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                queueLocked = queueLocked,
                favoriteSongKeys = favoriteSongKeys,
                loadSongRating = loadSongRating,
                ratingRevision = ratingRevision,
                onCyclePlaybackMode = onCyclePlaybackMode,
                onToggleQueueLock = onToggleQueueLock,
                onDismiss = onDismissQueue,
                onSongClick = onQueueSongClick,
                onRemoveSong = onRemoveQueueSong,
                onMoveSong = onMoveQueueSong,
                onRandomizeQueue = onRandomizeQueue,
                onAddQueueToPlaylist = onAddQueueToPlaylist,
                onClearQueue = onClearQueue
            )
        }
    }
}
