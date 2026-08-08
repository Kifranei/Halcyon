package com.ella.music.viewmodel

import com.ella.music.data.PlaybackStatsStore
import com.ella.music.data.model.Song

internal class PlayerPlaybackStatsTracker(
    private val playbackStatsStore: PlaybackStatsStore,
    private val minPlaybackStatsListenMs: Long = 20_000L,
    private val onPlayCounted: (Song) -> Unit = {},
    private val onLastFmScrobbleEligible: (Song, Long) -> Unit = { _, _ -> }
) {
    private var statsSongId: Long? = null
    private var statsSong: Song? = null
    private var playCountedSongId: Long? = null
    private var recentRecordedSongId: Long? = null
    private var recentRecordedEntryId: String? = null
    private var scrobbleQueuedSongId: Long? = null
    private var pendingListenMs = 0L
    private var sessionListenMs = 0L
    private var lastFmListenMs = 0L
    private var lastStatsTickMs = 0L
    private var songStartedAtWallClockMs = 0L

    suspend fun update(
        nowMs: Long,
        song: Song?,
        isPlaying: Boolean
    ) {
        val songId = song?.id

        if (songId != statsSongId) {
            flush()
            statsSongId = songId
            statsSong = song
            playCountedSongId = null
            recentRecordedSongId = null
            recentRecordedEntryId = null
            scrobbleQueuedSongId = null
            lastFmListenMs = 0L
            sessionListenMs = 0L
            songStartedAtWallClockMs = if (song == null) 0L else System.currentTimeMillis()
            lastStatsTickMs = nowMs
            return
        }

        if (song != null && isPlaying) {
            if (recentRecordedSongId != song.id) {
                recentRecordedEntryId = playbackStatsStore.recordRecent(song)
                recentRecordedSongId = song.id
            }
            val elapsedMs = if (lastStatsTickMs > 0L) {
                (nowMs - lastStatsTickMs).coerceIn(0L, 1500L)
            } else {
                0L
            }
            if (lastStatsTickMs > 0L) {
                pendingListenMs += elapsedMs
                sessionListenMs += elapsedMs
                lastFmListenMs += elapsedMs
            }
            if (playCountedSongId != song.id && sessionListenMs >= minPlaybackStatsListenMs) {
                val counted = playbackStatsStore.recordPlay(song, recentRecordedEntryId)
                if (counted) onPlayCounted(song)
                playCountedSongId = song.id
            }
            if (
                scrobbleQueuedSongId != song.id &&
                lastFmListenMs >= song.lastFmScrobbleThresholdMs()
            ) {
                onLastFmScrobbleEligible(song, songStartedAtWallClockMs)
                scrobbleQueuedSongId = song.id
            }
            if (pendingListenMs >= 5000L) {
                playbackStatsStore.addListenTime(song, pendingListenMs, recentRecordedEntryId)
                pendingListenMs = 0L
            }
        } else {
            flush()
        }
        lastStatsTickMs = nowMs
    }

    fun takePendingFlush(): PlayerPlaybackStatsPendingFlush? {
        val song = statsSong
        val listenedMs = pendingListenMs
        pendingListenMs = 0L
        return if (song != null && recentRecordedSongId == song.id && listenedMs > 0L) {
            PlayerPlaybackStatsPendingFlush(song, recentRecordedEntryId, listenedMs)
        } else {
            null
        }
    }

    private suspend fun flush() {
        val song = statsSong
        if (song != null && recentRecordedSongId == song.id && pendingListenMs > 0L) {
            playbackStatsStore.addListenTime(song, pendingListenMs, recentRecordedEntryId)
        }
        pendingListenMs = 0L
    }
}

/** Last.fm accepts a track after 50% or four minutes, whichever comes first; sub-30s tracks skip it. */
private fun Song.lastFmScrobbleThresholdMs(): Long = when {
    duration in 1L..30_000L -> Long.MAX_VALUE
    duration > 30_000L -> minOf(duration / 2L, 240_000L)
    else -> 240_000L
}

internal data class PlayerPlaybackStatsPendingFlush(
    val song: Song,
    val historyEntryId: String?,
    val listenedMs: Long
)
