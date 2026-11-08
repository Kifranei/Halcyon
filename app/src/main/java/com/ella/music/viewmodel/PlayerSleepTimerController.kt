package com.ella.music.viewmodel

import android.os.SystemClock
import com.ella.music.data.model.Song
import com.ella.music.data.model.playlistIdentityKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

internal class PlayerSleepTimerController(
    private val scope: CoroutineScope,
    private val currentSong: () -> Song?,
    private val duration: () -> Long,
    private val currentPosition: () -> Long,
    private val songKeyInQueue: (String) -> Boolean = { true },
    private val onPause: () -> Unit
) {
    private val _sleepTimerEndRealtimeMs = MutableStateFlow<Long?>(null)
    val sleepTimerEndRealtimeMs: StateFlow<Long?> = _sleepTimerEndRealtimeMs.asStateFlow()

    private val _stopAfterCurrentEnabled = MutableStateFlow(false)
    val stopAfterCurrentEnabled: StateFlow<Boolean> = _stopAfterCurrentEnabled.asStateFlow()

    /** Queue identity keys the user picked in the timer sheet; playback pauses once all finish. */
    private val _stopAfterSongKeys = MutableStateFlow<Set<String>>(emptySet())
    val stopAfterSongKeys: StateFlow<Set<String>> = _stopAfterSongKeys.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var stopAfterCurrentSongId: Long? = null
    private var stopAfterCurrentLastPositionMs: Long = -1L

    private var stopAfterSongsCompletedKeys: MutableSet<String> = mutableSetOf()
    private var stopAfterSongsLastSongKey: String? = null
    private var stopAfterSongsLastPositionMs: Long = -1L

    fun start(
        minutes: Int,
        stopAfterCurrentWhenExpired: Boolean = false
    ) {
        sleepTimerJob?.cancel()
        clearStopAfterSongs()
        if (minutes <= 0) {
            _sleepTimerEndRealtimeMs.value = null
            return
        }
        _sleepTimerEndRealtimeMs.value = SystemClock.elapsedRealtime() + minutes * 60_000L
        sleepTimerJob = scope.launch {
            delay(minutes * 60_000L)
            _sleepTimerEndRealtimeMs.value = null
            sleepTimerJob = null
            if (stopAfterCurrentWhenExpired) {
                val current = currentSong()
                if (current != null) {
                    _stopAfterCurrentEnabled.value = true
                    stopAfterCurrentSongId = current.id
                    return@launch
                }
            }
            onPause()
        }
    }

    fun setStopAfterCurrentEnabled(enabled: Boolean) {
        _stopAfterCurrentEnabled.value = enabled
        stopAfterCurrentSongId = if (enabled) currentSong()?.id else null
        stopAfterCurrentLastPositionMs = -1L
        if (enabled) clearStopAfterSongs()
    }

    /**
     * Arms the song-boundary mode: playback pauses after every picked song has finished.
     * Picking songs auto-starts the timer in this mode; an empty selection disarms it.
     * It is mutually exclusive with the minute countdown and pause-after-current, both of
     * which would fire earlier than the picked songs.
     */
    fun setStopAfterSongs(keys: Set<String>) {
        _stopAfterSongKeys.value = keys
        stopAfterSongsCompletedKeys = mutableSetOf()
        stopAfterSongsLastSongKey = null
        stopAfterSongsLastPositionMs = -1L
        if (keys.isNotEmpty()) {
            sleepTimerJob?.cancel()
            sleepTimerJob = null
            _sleepTimerEndRealtimeMs.value = null
            _stopAfterCurrentEnabled.value = false
            stopAfterCurrentSongId = null
        }
    }

    private fun clearStopAfterSongs() {
        _stopAfterSongKeys.value = emptySet()
        stopAfterSongsCompletedKeys = mutableSetOf()
        stopAfterSongsLastSongKey = null
        stopAfterSongsLastPositionMs = -1L
    }

    fun cancel() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerEndRealtimeMs.value = null
        clearStopAfterSongs()
    }

    fun update() {
        updateStopAfterSongs()
        val targetId = stopAfterCurrentSongId ?: return
        val song = currentSong() ?: return
        val total = duration()
        val position = currentPosition()
        // Repeat-one wraps to position 0 with the same song id, so the end-of-track threshold
        // alone would let it play a second pass before stopping. Require the previous tick to
        // have been near the end so a mid-song rewind does not masquerade as a wrap.
        val wrapped = song.id == targetId && total > 0L &&
            stopAfterCurrentLastPositionMs >= total - WRAP_DETECTION_DROP_MS &&
            position < stopAfterCurrentLastPositionMs - WRAP_DETECTION_DROP_MS
        stopAfterCurrentLastPositionMs = position
        if (song.id != targetId || wrapped || (total > 0L && total - position <= STOP_AFTER_CURRENT_THRESHOLD_MS)) {
            stopAfterCurrentSongId = null
            _stopAfterCurrentEnabled.value = false
            onPause()
        }
    }

    private fun updateStopAfterSongs() {
        val targets = _stopAfterSongKeys.value
        if (targets.isEmpty()) return
        // Queue edits can orphan a pick (song removed, queue cleared); drop those keys and
        // disarm rather than leave a timer that can never fire.
        val remaining = targets.filterTo(mutableSetOf(), songKeyInQueue)
        if (remaining.size != targets.size) {
            if (remaining.isEmpty()) {
                clearStopAfterSongs()
                return
            }
            _stopAfterSongKeys.value = remaining
        }
        val song = currentSong() ?: return
        val key = song.playlistIdentityKey()
        val total = duration()
        val position = currentPosition()
        val lastKey = stopAfterSongsLastSongKey
        if (lastKey != null && lastKey != key && lastKey in remaining) {
            // Playback advanced past a picked song, so it has finished.
            stopAfterSongsCompletedKeys += lastKey
        }
        if (key in remaining && total > 0L) {
            val wrapped = lastKey == key &&
                stopAfterSongsLastPositionMs >= total - WRAP_DETECTION_DROP_MS &&
                position < stopAfterSongsLastPositionMs - WRAP_DETECTION_DROP_MS
            if (wrapped || total - position <= STOP_AFTER_CURRENT_THRESHOLD_MS) {
                stopAfterSongsCompletedKeys += key
            }
        }
        stopAfterSongsLastSongKey = key
        stopAfterSongsLastPositionMs = position
        if (stopAfterSongsCompletedKeys.containsAll(remaining)) {
            clearStopAfterSongs()
            onPause()
        }
    }

    fun dispose() {
        sleepTimerJob?.cancel()
    }

    private companion object {
        const val STOP_AFTER_CURRENT_THRESHOLD_MS = 850L
        const val WRAP_DETECTION_DROP_MS = 3_000L
    }
}
