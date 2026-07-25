package com.ella.music.ui.player

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.ella.music.R
import com.ella.music.data.model.Song
import top.yukonga.miuix.kmp.window.WindowBottomSheet

@Composable
internal fun PlayerQueueSheet(
    show: Boolean,
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
    onDismiss: () -> Unit,
    onSongClick: (Int) -> Unit,
    onRemoveSong: (Int) -> Unit,
    onMoveSong: (Int, Int) -> Unit,
    onRandomizeQueue: () -> Unit,
    onAddQueueToPlaylist: () -> Unit,
    onClearQueue: () -> Unit
) {
    if (!show) return

    WindowBottomSheet(
        show = true,
        enableNestedScroll = false,
        title = stringResource(R.string.player_queue_title),
        onDismissRequest = onDismiss
    ) {
        PlayerQueueMenu(
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
            onSongClick = onSongClick,
            onRemoveSong = onRemoveSong,
            onMoveSong = onMoveSong,
            onRandomizeQueue = onRandomizeQueue,
            onAddQueueToPlaylist = onAddQueueToPlaylist,
            onClearQueue = onClearQueue,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
