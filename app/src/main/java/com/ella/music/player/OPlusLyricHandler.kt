package com.ella.music.player

import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.ella.music.data.SettingsManager
import com.ella.music.data.model.Song
import com.ella.music.data.model.shiftedBy
import com.ella.music.data.repository.MusicRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Loads OPlus lyric payloads and publishes them through the session presentation layer. */
internal class OPlusLyricHandler(
    private val settingsManager: SettingsManager,
    private val musicRepository: MusicRepository,
    private val serviceScope: CoroutineScope,
    private val playerProvider: () -> Player?,
    private val onLyricInfoChanged: (Song?, String?, Boolean) -> Unit
) {
    companion object {
        private const val TAG = "PlaybackService"
        private const val TIMING_TAG = "EllaPlaybackTiming"
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

    /**
     * Seeds the presentation overlay/cache and returns a copy of the initial MediaItem carrying
     * the complete Bridge 4.0 payload before MediaSession publishes the playback queue.
     */
    suspend fun prepareInitialOplusLyricInfo(
        mediaItems: List<MediaItem>,
        startIndex: Int
    ): List<MediaItem> {
        if (!colorOsLockScreenLyricEnabled || startIndex !in mediaItems.indices) return mediaItems
        val item = mediaItems[startIndex]
        val song = item.toSongFromMediaItemExtras() ?: return mediaItems
        val deliveryMode = colorOsLockScreenLyricMode
        val songKey = song.oplusLyricCacheKey(deliveryMode)
        val lyricInfoJson = if (lyricInfoCache.containsKey(songKey)) {
            lyricInfoCache[songKey]
        } else {
            try {
                withContext(Dispatchers.IO) {
                    loadOplusLyricInfoJson(song, deliveryMode)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                Log.w(TAG, "Failed to prepare initial OPlus lyricInfo for ${song.title}", error)
                null
            }.also { lyricInfoCache[songKey] = it }
        }

        currentSongKey = songKey
        currentLyricInfoJson = lyricInfoJson
        publishLyricInfo(song, lyricInfoJson)
        if (!lyricInfoJson.isNullOrBlank()) {
            Log.d(TIMING_TAG, "OPlus lyricInfo attached before initial publish mediaId=${song.id}")
            scheduleOplusLyricInfoReapply(songKey)
        }
        return mediaItems.toMutableList().apply {
            this[startIndex] = withOplusLyricInfo(item, lyricInfoJson)
        }
    }

    fun peekLyricInfoJson(playbackSongKey: String): String? {
        if (!colorOsLockScreenLyricEnabled || playbackSongKey.isBlank()) return null
        return lyricInfoCache["${colorOsLockScreenLyricMode}:$playbackSongKey"]
            ?.takeIf { it.isNotBlank() }
    }

    fun refreshCurrentOplusLyricInfo(player: Player? = playerProvider()) {
        val currentPlayer = player
        val song = currentPlayer?.currentMediaItem?.toSongFromMediaItemExtras()

        if (!colorOsLockScreenLyricEnabled) {
            clearCurrentOplusLyricInfo(currentPlayer)
            return
        }
        if (currentPlayer == null || song == null) {
            clearLyricInfoState()
            publishLyricInfo(null, null)
            return
        }

        val deliveryMode = colorOsLockScreenLyricMode
        val songKey = song.oplusLyricCacheKey(deliveryMode)
        if (currentSongKey == songKey) {
            prefetchAdjacentOplusLyricInfo(currentPlayer)
            return
        }

        // Remove the previous track's lyricInfo from the real MediaItem immediately. Bridge 4.0
        // reads the current item directly and must never see the previous song while the new
        // lyric is being loaded asynchronously.
        lyricInfoJob?.cancel()
        lyricInfoReapplyJob?.cancel()
        pendingSongKey = songKey
        currentSongKey = songKey
        currentLyricInfoJson = null
        publishLyricInfo(song, null)

        if (lyricInfoCache.containsKey(songKey)) {
            currentLyricInfoJson = lyricInfoCache[songKey]
            pendingSongKey = null
            publishLyricInfo(song, currentLyricInfoJson)
            scheduleOplusLyricInfoReapply(songKey)
            prefetchAdjacentOplusLyricInfo(currentPlayer)
            return
        }
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
                publishLyricInfo(latestSong, lyricInfoJson)
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
        publishLyricInfo(song, null)
    }

    private fun publishLyricInfo(
        song: Song?,
        lyricInfoJson: String?,
        forceRepublish: Boolean = false
    ) {
        onLyricInfoChanged(song, lyricInfoJson, forceRepublish)
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
                publishLyricInfo(song, currentLyricInfoJson, forceRepublish = true)
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

    private fun withOplusLyricInfo(item: MediaItem, lyricInfoJson: String?): MediaItem {
        val extras = Bundle(item.mediaMetadata.extras ?: Bundle.EMPTY).apply {
            remove(OPLUS_LYRIC_INFO_KEY)
            remove(OPLUS_RAW_LYRIC_KEY)
            if (!lyricInfoJson.isNullOrBlank()) {
                putString(OPLUS_LYRIC_INFO_KEY, lyricInfoJson)
                OPlusLyricPayload.rawLyric(lyricInfoJson)?.let { putString(OPLUS_RAW_LYRIC_KEY, it) }
            }
        }
        return item.buildUpon()
            .setMediaMetadata(item.mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
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
