package com.ella.music.viewmodel

import android.os.SystemClock
import com.ella.music.data.model.Song
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
    private val onPause: () -> Unit
) {
    private val _sleepTimerEndRealtimeMs = MutableStateFlow<Long?>(null)
    val sleepTimerEndRealtimeMs: StateFlow<Long?> = _sleepTimerEndRealtimeMs.asStateFlow()

    private val _stopAfterCurrentEnabled = MutableStateFlow(false)
    val stopAfterCurrentEnabled: StateFlow<Boolean> = _stopAfterCurrentEnabled.asStateFlow()

    private var sleepTimerJob: Job? = null
    private var stopAfterCurrentSongId: Long? = null
    private var stopAfterCurrentLastPositionMs: Long = -1L

    fun start(
        minutes: Int,
        stopAfterCurrentWhenExpired: Boolean = false
    ) {
        sleepTimerJob?.cancel()
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
    }

    fun cancel() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _sleepTimerEndRealtimeMs.value = null
    }

    fun update() {
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

    fun dispose() {
        sleepTimerJob?.cancel()
    }

    private companion object {
        const val STOP_AFTER_CURRENT_THRESHOLD_MS = 850L
        const val WRAP_DETECTION_DROP_MS = 3_000L
    }
}
