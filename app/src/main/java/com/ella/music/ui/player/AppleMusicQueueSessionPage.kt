package com.ella.music.ui.player

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.ui.components.DefaultAlbumCover
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.ui.graphics.vector.ImageVector
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Delete

@Composable
internal fun AppleMusicQueueSessionPage(
    song: Song?,
    embeddedCover: Any?,
    playlist: List<Song>,
    currentQueueIndexHint: Int = -1,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    isFavorite: Boolean,
    pagePalette: PlayerPalette,
    fontFamily: FontFamily?,
    compactWindow: Boolean,
    useAppleIcons: Boolean = false,
    coverModifier: Modifier = Modifier,
    artworkPainter: Painter? = null,
    annotation: String = "",
    onArtist: () -> Unit = {},
    onToggleFavorite: () -> Unit,
    onSongInfo: () -> Unit,
    onClearQueue: () -> Unit = {},
    onToggleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSongClick: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onDismissQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()

    var manualPlaylist by remember(queueSnapshotKey(playlist)) {
        mutableStateOf(buildQueueEntries(playlist))
    }
    var pendingMoveStart by remember { mutableStateOf<Int?>(null) }
    var pendingMoveTarget by remember { mutableStateOf<Int?>(null) }

    val currentSongKey = song?.playlistIdentityKey()
    val currentIndex = remember(currentSongKey, currentQueueIndexHint, manualPlaylist) {
        currentQueueIndexHint.takeIf {
            it in manualPlaylist.indices && manualPlaylist[it].song.playlistIdentityKey() == currentSongKey
        } ?: manualPlaylist.indexOfFirst { it.song.playlistIdentityKey() == currentSongKey }
    }

    val initialIndex = remember { currentIndex.coerceAtLeast(0) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initialIndex)

    var hasRenderedFirstTime by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex) {
        if (!hasRenderedFirstTime) {
            hasRenderedFirstTime = true
            return@LaunchedEffect
        }
        if (currentIndex >= 0 && currentIndex < manualPlaylist.size) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (from.index !in manualPlaylist.indices || to.index !in manualPlaylist.indices) return@rememberReorderableLazyListState
            manualPlaylist = manualPlaylist.toMutableList().apply {
                add(to.index, removeAt(from.index))
            }
            if (pendingMoveStart == null) pendingMoveStart = from.index
            pendingMoveTarget = to.index
        }
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp)
    ) {
        Spacer(modifier = Modifier.height(if (compactWindow) 8.dp else 12.dp))

        // 1. Top Mini Current Playing Area (identical to lyrics page header)
        LyricsPlayerHeader(
            song = song,
            embeddedCover = embeddedCover as? Bitmap,
            annotation = annotation,
            activeSinger = null,
            isFavorite = isFavorite,
            onDismissLyrics = onDismissQueue,
            onArtist = onArtist,
            onToggleFavorite = onToggleFavorite,
            onShowMenu = onSongInfo,
            fontFamily = fontFamily,
            coverModifier = coverModifier,
            artworkPainter = artworkPainter,
            useAppleIcons = useAppleIcons
        )

        Spacer(modifier = Modifier.height(14.dp))

        // 2. Queue Section Title ("继续播放" & Counter on left, 4 icon buttons on right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f, fill = false)) {
                Text(
                    text = stringResource(R.string.player_queue_up_next),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = fontFamily,
                    color = pagePalette.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (playlist.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${(currentIndex + 1).coerceAtLeast(1)} / ${playlist.size}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = fontFamily,
                        color = pagePalette.onBackground.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Clear Queue Button
                AppleMusicCircleIconButton(
                    imageVector = MiuixIcons.Regular.Delete,
                    contentDescription = stringResource(R.string.player_clear_queue),
                    pagePalette = pagePalette,
                    onClick = onClearQueue
                )

                // Shuffle Button (icon only)
                AppleMusicCircleIconButton(
                    iconRes = if (shuffleEnabled) R.drawable.ic_nowplaying_shuffleon else R.drawable.ic_nowplaying_shuffle,
                    contentDescription = stringResource(R.string.player_playback_mode_shuffle),
                    active = shuffleEnabled,
                    pagePalette = pagePalette,
                    onClick = onToggleShuffle
                )

                // Repeat Mode Button (icon only)
                val isRepeatActive = repeatMode != Player.REPEAT_MODE_OFF
                val repeatIcon = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> if (isRepeatActive) R.drawable.ic_nowplaying_repeatoneon else R.drawable.ic_nowplaying_repeatone
                    Player.REPEAT_MODE_ALL -> if (isRepeatActive) R.drawable.ic_nowplaying_repeaton else R.drawable.ic_nowplaying_repeat
                    else -> R.drawable.ic_nowplaying_repeat
                }
                val repeatLabel = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> stringResource(R.string.player_playback_mode_repeat_one)
                    Player.REPEAT_MODE_ALL -> stringResource(R.string.player_playback_mode_repeat_all)
                    else -> stringResource(R.string.player_playback_mode_in_order)
                }
                AppleMusicCircleIconButton(
                    iconRes = repeatIcon,
                    contentDescription = repeatLabel,
                    active = isRepeatActive,
                    pagePalette = pagePalette,
                    onClick = onToggleRepeat
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3. Middle Queue List (weight 1f)
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            itemsIndexed(
                items = manualPlaylist,
                key = { _, item -> item.stableKey }
            ) { index, item ->
                ReorderableItem(
                    state = reorderableLazyListState,
                    key = item.stableKey
                ) { isDragging ->
                    val queueSong = item.song
                    val isCurrentSong = index == currentIndex
                    val dragHandleModifier = Modifier.draggableHandle(
                        onDragStopped = {
                            val move = resolveQueueMoveCommit(
                                fromIndex = pendingMoveStart,
                                toIndex = pendingMoveTarget,
                                queueSize = manualPlaylist.size
                            )
                            if (move != null) {
                                onMoveSong(move.fromIndex, move.toIndex)
                            }
                            pendingMoveStart = null
                            pendingMoveTarget = null
                        }
                    )

                    AppleMusicQueueRowItem(
                        song = queueSong,
                        isCurrentSong = isCurrentSong,
                        isDragging = isDragging,
                        pagePalette = pagePalette,
                        fontFamily = fontFamily,
                        dragHandleModifier = dragHandleModifier,
                        onClick = { onSongClick(index) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AppleMusicCircleIconButton(
    contentDescription: String,
    pagePalette: PlayerPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconRes: Int? = null,
    imageVector: ImageVector? = null,
    active: Boolean = false
) {
    val containerColor = if (active) {
        pagePalette.onBackground
    } else {
        pagePalette.onBackground.copy(alpha = 0.12f)
    }
    val contentColor = if (active) {
        if (pagePalette.isLight) Color.White else Color.Black
    } else {
        pagePalette.onBackground
    }

    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(containerColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
        } else if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                modifier = Modifier.size(18.dp),
                tint = contentColor
            )
        }
    }
}

@Composable
private fun AppleMusicQueueRowItem(
    song: Song,
    isCurrentSong: Boolean,
    isDragging: Boolean,
    pagePalette: PlayerPalette,
    fontFamily: FontFamily?,
    dragHandleModifier: Modifier,
    onClick: () -> Unit
) {
    val itemBackground = when {
        isDragging -> pagePalette.onBackground.copy(alpha = 0.20f)
        isCurrentSong -> pagePalette.onBackground.copy(alpha = 0.10f)
        else -> Color.Transparent
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(itemBackground)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QueueAlbumArtView(
            song = song,
            modifier = Modifier.size(44.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp)
        ) {
            Text(
                text = song.title.ifBlank { song.fileName },
                fontSize = 15.sp,
                fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                fontFamily = fontFamily,
                color = pagePalette.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = song.artist,
                    fontSize = 12.sp,
                    fontFamily = fontFamily,
                    color = pagePalette.onBackground.copy(alpha = 0.60f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Box(
            modifier = dragHandleModifier
                .size(36.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_nowplaying_drag_handle),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = pagePalette.onBackground.copy(alpha = 0.40f)
            )
        }
    }
}
