package com.ella.music.player

import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.model.shiftedBy
import com.ella.music.data.repository.MusicRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Loads OPlus lyric payloads and publishes them through the session presentation layer. */
internal class OPlusLyricHandler(
    private val settingsManager: SettingsManager,
    private val musicRepository: MusicRepository,
    private val serviceScope: CoroutineScope,
    private val playerProvider: () -> Player?,
    private val onLyricInfoChanged: (Song?, String?) -> Unit
) {
    companion object {
        private const val TAG = "PlaybackService"
        const val OPLUS_LYRIC_INFO_KEY = "lyricInfo"
        const val OPLUS_RAW_LYRIC_KEY = OPlusLyricPayload.RAW_LYRIC_INFO_KEY
    }

    private var lyricInfoJob: Job? = null
    private var lyricInfoReapplyJob: Job? = null
    private var pendingSongKey: String? = null
    private var currentSongKey: String? = null
    private var currentLyricInfoJson: String? = null
    private val prefetchJobs = mutableMapOf<String, Job>()
    private val lyricInfoCache = object : LinkedHashMap<String, String?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>): Boolean = size > 24
    }

    @Volatile
    var colorOsLockScreenLyricEnabled = false

    @Volatile
    var colorOsLockScreenLyricMode = SettingsManager.OPLUS_LYRIC_MODE_SYSTEM

    fun refreshCurrentOplusLyricInfo(player: Player? = playerProvider()) {
        val currentPlayer = player
        val song = currentPlayer?.currentMediaItem?.toSongFromMediaItemExtras()

        if (!colorOsLockScreenLyricEnabled) {
            clearCurrentOplusLyricInfo(currentPlayer)
            return
        }
        if (currentPlayer == null || song == null) {
            clearLyricInfoState()
            onLyricInfoChanged(null, null)
            return
        }

        val deliveryMode = colorOsLockScreenLyricMode
        val songKey = song.oplusLyricCacheKey(deliveryMode)
        if (currentSongKey == songKey) {
            prefetchAdjacentOplusLyricInfo(currentPlayer)
            return
        }

        if (lyricInfoCache.containsKey(songKey)) {
            currentSongKey = songKey
            currentLyricInfoJson = lyricInfoCache[songKey]
            onLyricInfoChanged(song, currentLyricInfoJson)
            scheduleOplusLyricInfoReapply(songKey)
            prefetchAdjacentOplusLyricInfo(currentPlayer)
            return
        }
        if (pendingSongKey == songKey) return

        lyricInfoJob?.cancel()
        pendingSongKey = songKey
        lyricInfoJob = serviceScope.launch {
            try {
                val lyricInfoJson = runCatching {
                    loadOplusLyricInfoJson(song, deliveryMode)
                }.getOrElse { error ->
                    Log.w(TAG, "Failed to prepare OPlus lyricInfo for ${song.title}", error)
                    null
                }

                val latestPlayer = playerProvider() ?: return@launch
                val latestSong = latestPlayer.currentMediaItem?.toSongFromMediaItemExtras() ?: return@launch
                if (latestSong.oplusLyricCacheKey(deliveryMode) != songKey) return@launch
                if (colorOsLockScreenLyricMode != deliveryMode) return@launch

                currentSongKey = songKey
                currentLyricInfoJson = lyricInfoJson
                lyricInfoCache[songKey] = lyricInfoJson
                onLyricInfoChanged(latestSong, lyricInfoJson)
                scheduleOplusLyricInfoReapply(songKey)
                prefetchAdjacentOplusLyricInfo(latestPlayer)
            } finally {
                if (pendingSongKey == songKey) pendingSongKey = null
            }
        }
    }

    fun clearCurrentOplusLyricInfo(player: Player? = playerProvider()) {
        val song = player?.currentMediaItem?.toSongFromMediaItemExtras()
        clearLyricInfoState()
        onLyricInfoChanged(song, null)
    }

    private fun scheduleOplusLyricInfoReapply(songKey: String) {
        lyricInfoReapplyJob?.cancel()
        if (currentLyricInfoJson.isNullOrBlank()) return

        lyricInfoReapplyJob = serviceScope.launch {
            for (delayMs in OPlusLyricPublishPolicy.COMPAT_REAPPLY_DELAYS_MS) {
                delay(delayMs)
                if (!colorOsLockScreenLyricEnabled || currentSongKey != songKey) return@launch
                val player = playerProvider() ?: return@launch
                val song = player.currentMediaItem?.toSongFromMediaItemExtras() ?: return@launch
                if (song.oplusLyricCacheKey(colorOsLockScreenLyricMode) != songKey) return@launch
                onLyricInfoChanged(song, currentLyricInfoJson)
            }
        }
    }

    private fun clearLyricInfoState() {
        lyricInfoJob?.cancel()
        lyricInfoReapplyJob?.cancel()
        cancelPrefetchJobs()
        pendingSongKey = null
        currentSongKey = null
        currentLyricInfoJson = null
    }

    private fun cancelPrefetchJobs() {
        prefetchJobs.values.forEach(Job::cancel)
        prefetchJobs.clear()
    }

    private fun prefetchAdjacentOplusLyricInfo(player: Player? = playerProvider()) {
        val currentPlayer = player ?: return
        if (!colorOsLockScreenLyricEnabled || currentPlayer.mediaItemCount < 2) return
        val deliveryMode = colorOsLockScreenLyricMode

        for (targetIndex in currentPlayer.oplusLyricPrefetchIndices()) {
            val targetSong = currentPlayer.getMediaItemAt(targetIndex).toSongFromMediaItemExtras() ?: continue
            val targetSongKey = targetSong.oplusLyricCacheKey(deliveryMode)
            if (lyricInfoCache.containsKey(targetSongKey) || prefetchJobs.containsKey(targetSongKey)) continue

            lateinit var prefetchJob: Job
            prefetchJob = serviceScope.launch(start = CoroutineStart.LAZY) {
                try {
                    lyricInfoCache[targetSongKey] = runCatching {
                        loadOplusLyricInfoJson(targetSong, deliveryMode)
                    }.getOrElse { error ->
                        Log.w(TAG, "Failed to prefetch OPlus lyricInfo for ${targetSong.title}", error)
                        null
                    }
                } finally {
                    if (prefetchJobs[targetSongKey] === prefetchJob) prefetchJobs.remove(targetSongKey)
                }
            }
            prefetchJobs[targetSongKey] = prefetchJob
            prefetchJob.start()
        }
    }

    @OptIn(UnstableApi::class)
    private fun Player.oplusLyricPrefetchIndices(): List<Int> {
        val currentIndex = currentMediaItemIndex
        if (currentIndex == C.INDEX_UNSET || mediaItemCount <= 1) return emptyList()
        val previousIndex = when {
            currentIndex - 1 >= 0 -> currentIndex - 1
            repeatMode == Player.REPEAT_MODE_ALL -> mediaItemCount - 1
            else -> null
        }
        val nextIndex = when {
            currentIndex + 1 < mediaItemCount -> currentIndex + 1
            repeatMode == Player.REPEAT_MODE_ALL -> 0
            else -> null
        }
        return listOfNotNull(previousIndex, nextIndex)
            .filter { it != currentIndex }
            .distinct()
    }

    private suspend fun loadOplusLyricInfoJson(song: Song, mode: Int): String? {
        val sourceMode = settingsManager.lyricSourceMode.first()
        val offsetMs = settingsManager.lyricOffsetOverrides.first()[song.oplusLyricOffsetKey()] ?: 0L
        return musicRepository.getLyrics(song, sourceMode)
            .shiftedBy(offsetMs)
            .let { lyrics -> OPlusLyricPayload.build(song, lyrics, mode) }
    }

    private fun Song.oplusLyricOffsetKey(): String = when {
        onlineSource.isNotBlank() || onlineId.isNotBlank() -> "online:$onlineSource:$onlineId:$path"
        path.isNotBlank() -> "path:$path"
        else -> "id:$id"
    }

    private fun Song.oplusLyricCacheKey(mode: Int): String = "$mode:${playbackStackKey()}"
}
