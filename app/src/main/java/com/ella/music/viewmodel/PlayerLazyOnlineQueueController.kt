package com.ella.music.viewmodel

import androidx.media3.common.Player
import com.ella.music.data.model.Song
import com.ella.music.player.ExoPlayerManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class PlayerLazyOnlineQueueController(
    private val scope: CoroutineScope,
    private val playerManager: ExoPlayerManager
) {
    private var queue: LazyOnlineQueue? = null
    private var resolving = false

    fun clear() {
        queue = null
    }

    fun setQueue(
        songs: List<Song>,
        startIndex: Int,
        resolvedStartSong: Song,
        resolver: suspend (Song) -> Song
    ) {
        if (songs.isEmpty()) return
        queue = LazyOnlineQueue(
            songs = songs,
            index = startIndex.coerceIn(songs.indices),
            resolver = resolver
        )
        playerManager.playResolvedFromVirtualQueue(songs, startIndex, resolvedStartSong, shouldPlay = true)
    }

    fun observePlaybackEnd() {
        scope.launch {
            playerManager.playbackState.collect { state ->
                if (state == Player.STATE_ENDED) playOffset(1, shouldPlay = true)
            }
        }
    }

    fun playOffset(offset: Int, shouldPlay: Boolean = playerManager.playWhenReady.value): Boolean {
        val currentQueue = queue ?: return false
        return playIndex(currentQueue.index + offset, shouldPlay)
    }

    fun playIndex(index: Int, shouldPlay: Boolean = playerManager.playWhenReady.value): Boolean {
        val currentQueue = queue ?: return false
        if (index !in currentQueue.songs.indices || resolving) return false
        resolving = true
        scope.launch {
            runCatching {
                val resolved = currentQueue.resolver(currentQueue.songs[index])
                currentQueue.index = index
                playerManager.playResolvedFromVirtualQueue(
                    currentQueue.songs,
                    index,
                    resolved,
                    shouldPlay = shouldPlay
                )
            }
            resolving = false
        }
        return true
    }
}
