package com.ella.music.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.ella.music.R
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.repository.CoverUsage
import com.ella.music.data.repository.MusicRepository
import com.ella.music.ui.components.ArtworkUsage
import com.ella.music.ui.components.DefaultAlbumCover
import com.ella.music.ui.components.ExplicitSongTitle
import com.ella.music.ui.components.RatingStarIcon
import com.ella.music.ui.components.SafeCoverImage
import com.ella.music.ui.components.rememberSongArtworkState
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import sh.calvin.reorderable.DragGestureDetector
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.AddFolder
import top.yukonga.miuix.kmp.icon.extended.Lock
import top.yukonga.miuix.kmp.icon.extended.Unlock
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class QueueEntry(
    val stableKey: String,
    val song: Song
)

internal data class QueueMoveCommit(
    val fromIndex: Int,
    val toIndex: Int
)

internal fun resolveQueueMoveCommit(
    fromIndex: Int?,
    toIndex: Int?,
    queueSize: Int
): QueueMoveCommit? {
    if (fromIndex == null || toIndex == null || fromIndex == toIndex) return null
    if (fromIndex !in 0 until queueSize || toIndex !in 0 until queueSize) return null
    return QueueMoveCommit(fromIndex, toIndex)
}

private fun buildQueueEntries(items: List<Song>): List<QueueEntry> {
    val occurrenceByIdentity = linkedMapOf<String, Int>()
    return items.map { song ->
        val identity = song.playlistIdentityKey()
        val occurrence = (occurrenceByIdentity[identity] ?: 0) + 1
        occurrenceByIdentity[identity] = occurrence
        QueueEntry(
            stableKey = "$identity|queue#$occurrence",
            song = song
        )
    }
}

@Composable
internal fun PlayerQueueMenu(
    playlist: List<Song>,
    currentSongKey: String?,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    queueLocked: Boolean,
    favoriteSongKeys: Set<String> = emptySet(),
    loadSongRating: (Song) -> Int = { 0 },
    ratingRevision: Int = 0,
    onCyclePlaybackMode: () -> Unit,
    onToggleQueueLock: () -> Unit,
    onSongClick: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onRandomizeQueue: () -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var manualPlaylist by remember(playlist) { mutableStateOf(buildQueueEntries(playlist)) }
    var pendingMoveStart by remember(playlist) { mutableStateOf<Int?>(null) }
    var pendingMoveTarget by remember(playlist) { mutableStateOf<Int?>(null) }
    val currentIndex = remember(manualPlaylist, currentSongKey) {
        manualPlaylist.indexOfFirst { it.song.playlistIdentityKey() == currentSongKey }
    }
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) {
            listState.scrollToItem(currentIndex)
        }
    }
    LaunchedEffect(queueLocked) {
        if (queueLocked) {
            pendingMoveStart = null
            pendingMoveTarget = null
        }
    }
    val reorderableLazyListState = rememberReorderableLazyListState(
        lazyListState = listState,
        onMove = { from, to ->
            if (queueLocked) return@rememberReorderableLazyListState
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
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .playerNoIndicationClick(onCyclePlaybackMode),
                contentAlignment = Alignment.Center
            ) {
                QueuePlaybackModeIcon(
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    color = MiuixTheme.colorScheme.primary
                )
            }
            if (playlist.isNotEmpty()) {
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(currentIndex + 1).coerceAtLeast(1)} / ${playlist.size}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (playlist.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .playerNoIndicationClick(onToggleQueueLock),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (queueLocked) MiuixIcons.Regular.Lock else MiuixIcons.Regular.Unlock,
                        contentDescription = stringResource(
                            if (queueLocked) R.string.player_unlock_queue else R.string.player_lock_queue
                        ),
                        tint = if (queueLocked) {
                            MiuixTheme.colorScheme.primary
                        } else {
                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                        },
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (!queueLocked && playlist.size > 1) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .playerNoIndicationClick(onRandomizeQueue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_shuffle),
                            contentDescription = stringResource(R.string.player_randomize_queue),
                            tint = MiuixTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .playerNoIndicationClick(onAddQueueToPlaylist),
                    contentAlignment = Alignment.Center
                ) {
                    com.ella.music.ui.components.AddToPlaylistActionIcon(
                        contentDescription = stringResource(R.string.player_add_to_playlist),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .playerNoIndicationClick {
                            if (currentIndex >= 0) {
                                scope.launch { listState.animateScrollToItem(currentIndex) }
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_my_location),
                        contentDescription = stringResource(R.string.player_locate_current_song),
                        tint = MiuixTheme.colorScheme.primary,
                        modifier = Modifier.size(26.dp)
                    )
                }
                if (!queueLocked) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .playerNoIndicationClick(onClearQueue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_delete),
                            contentDescription = stringResource(R.string.player_clear_queue),
                            tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
        if (playlist.isEmpty()) {
            Text(
                text = stringResource(R.string.player_queue_empty),
                fontSize = 13.sp,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 18.dp)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 420.dp)
            ) {
                itemsIndexed(manualPlaylist, key = { _, item -> item.stableKey }) { index, item ->
                    ReorderableItem(
                        state = reorderableLazyListState,
                        key = item.stableKey
                    ) { isDragging ->
                        val dragHandleModifier = if (queueLocked) {
                            Modifier
                        } else {
                            Modifier.draggableHandle(
                                dragGestureDetector = LongPressDragHandleGestureDetector,
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
                        }
                        val queueSong = item.song
                        val isCurrentSong = queueSong.playlistIdentityKey() == currentSongKey
                        val isFavorite = queueSong.playlistIdentityKey() in favoriteSongKeys
                        val rating by androidx.compose.runtime.produceState(
                            initialValue = 0,
                            queueSong.id,
                            queueSong.dateModified,
                            ratingRevision
                        ) {
                            value = withContext(kotlinx.coroutines.Dispatchers.IO) {
                                loadSongRating(queueSong).coerceIn(0, 5)
                            }
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 60.dp)
                                .zIndex(if (isDragging) 1f else 0f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(
                                    when {
                                        isDragging -> MiuixTheme.colorScheme.primary.copy(alpha = 0.16f)
                                        isCurrentSong -> MiuixTheme.colorScheme.primary.copy(alpha = 0.12f)
                                        else -> Color.Transparent
                                    }
                                )
                                .clickable { onSongClick(index) }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            QueueAlbumArtView(
                                song = queueSong,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ExplicitSongTitle(
                                        title = queueSong.title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isCurrentSong) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isCurrentSong) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (isFavorite) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        HeartIcon(
                                            color = Color(0xFFFF4D6D),
                                            filled = true,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    if (rating > 0) {
                                        Spacer(modifier = Modifier.width(5.dp))
                                        RatingStarIcon(
                                            filled = true,
                                            tint = Color(0xFFFFB703),
                                            modifier = Modifier.size(13.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            text = rating.toString(),
                                            fontSize = 11.sp,
                                            color = Color(0xFFFFB703)
                                        )
                                    }
                                }
                                Text(
                                    text = queueSong.artist,
                                    fontSize = 11.sp,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            if (!queueLocked) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .then(dragHandleModifier)
                                        .size(44.dp)
                                        .clip(RoundedCornerShape(22.dp))
                                        .background(
                                            if (isDragging) MiuixTheme.colorScheme.primary.copy(alpha = 0.14f)
                                            else Color.Transparent
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "\u2630",
                                        fontSize = 15.sp,
                                        color = if (isDragging) {
                                            MiuixTheme.colorScheme.primary
                                        } else {
                                            MiuixTheme.colorScheme.onSurfaceVariantSummary
                                        }
                                    )
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .playerNoIndicationClick { onRemoveSong(index) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_delete),
                                        contentDescription = stringResource(R.string.player_remove_from_queue),
                                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private object LongPressDragHandleGestureDetector : DragGestureDetector {
    override suspend fun PointerInputScope.detect(
        onDragStart: (Offset) -> Unit,
        onDragEnd: () -> Unit,
        onDragCancel: () -> Unit,
        onDrag: (PointerInputChange, Offset) -> Unit
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val longPress = awaitLongPressOrCancellation(down.id)
            if (longPress == null) {
                onDragCancel()
                return@awaitEachGesture
            }
            onDragStart(longPress.position)
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == longPress.id } ?: run {
                    onDragCancel()
                    break
                }
                if (!change.pressed || change.changedToUpIgnoreConsumed()) {
                    onDragEnd()
                    break
                }
                val dragAmount = change.positionChange()
                if (dragAmount != Offset.Zero) {
                    onDrag(change, dragAmount)
                    change.consume()
                }
            }
        }
    }
}

@Composable
private fun QueueAlbumArtView(
    song: Song,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val repository = remember(context) { MusicRepository.getInstance(context) }
    val albumArtUri = remember(song.albumId) {
        if (song.albumId > 0L) android.net.Uri.parse("content://media/external/audio/albumart/${song.albumId}") else null
    }
    val artworkState = rememberSongArtworkState(
        song = song,
        albumArtUri = albumArtUri,
        loadCoverArt = { target -> repository.getCoverArtBitmap(target, 512, CoverUsage.ListThumbnail) },
        usage = ArtworkUsage.ListThumbnail
    )
    val model = artworkState.model
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (model == null) MiuixTheme.colorScheme.surfaceContainer else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (model != null) {
            SafeCoverImage(
                model = model,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop,
                sizePx = 512,
                showDefaultPlaceholder = false
            )
        } else {
            DefaultAlbumCover(modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun QueuePlaybackModeIcon(
    shuffleEnabled: Boolean,
    repeatMode: Int,
    color: Color
) {
    PlaybackModeIcon(
        shuffleEnabled = shuffleEnabled,
        repeatMode = repeatMode,
        color = color,
        modifier = Modifier.size(26.dp)
    )
}
