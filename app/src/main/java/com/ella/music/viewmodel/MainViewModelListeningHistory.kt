package com.ella.music.viewmodel

import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.lastfm.LastFmTrack
import com.ella.music.data.model.Song
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps local and Last.fm records as two sources until presentation time.  This avoids copying
 * remote history into the local database and lets the source selector remain reversible.
 */
internal fun mergePlaybackHistorySources(
    local: List<PlaybackHistoryEntry>,
    remote: List<PlaybackHistoryEntry>
): List<PlaybackHistoryEntry> {
    val retained = ArrayList<PlaybackHistoryEntry>(local.size + remote.size)
    val occupiedBuckets = HashSet<String>(local.size * 3)

    fun bucketKey(entry: PlaybackHistoryEntry, bucket: Long): String =
        "${entry.historyFingerprint()}@$bucket"

    local.sortedByDescending(PlaybackHistoryEntry::playedAt).forEach { entry ->
        retained += entry
        val baseBucket = entry.playedAt / HISTORY_DEDUP_WINDOW_MS
        (-1L..1L).forEach { offset -> occupiedBuckets += bucketKey(entry, baseBucket + offset) }
    }
    remote.sortedByDescending(PlaybackHistoryEntry::playedAt).forEach { entry ->
        val baseBucket = entry.playedAt / HISTORY_DEDUP_WINDOW_MS
        if (bucketKey(entry, baseBucket) !in occupiedBuckets) {
            retained += entry
            (-1L..1L).forEach { offset -> occupiedBuckets += bucketKey(entry, baseBucket + offset) }
        }
    }
    return retained.sortedByDescending(PlaybackHistoryEntry::playedAt)
}

internal fun estimateLastFmDailyListenMs(
    history: List<LastFmTrack>,
    librarySongs: List<Song>,
    existingHistory: List<PlaybackHistoryEntry> = emptyList()
): Map<String, Long> {
    if (history.isEmpty()) return emptyMap()
    val durationByFingerprint = librarySongs.associate { song ->
        song.historyFingerprint() to song.duration.coerceAtLeast(0L)
    }
    val occupiedBuckets = HashSet<String>(existingHistory.size * 3)
    existingHistory.forEach { entry ->
        val bucket = entry.playedAt / HISTORY_DEDUP_WINDOW_MS
        (-1L..1L).forEach { offset ->
            occupiedBuckets += "${entry.historyFingerprint()}@${bucket + offset}"
        }
    }
    return buildMap {
        history.forEach { track ->
            val bucket = track.playedAt / HISTORY_DEDUP_WINDOW_MS
            if ("${track.historyFingerprint()}@$bucket" in occupiedBuckets) return@forEach
            val duration = track.durationMs.takeIf { it > 0L }
                ?: durationByFingerprint[track.historyFingerprint()]
                ?: 0L
            if (duration <= 0L) return@forEach
            val date = track.playedAt.toHistoryDateKey()
            if (date.isNotBlank()) {
                put(date, (get(date) ?: 0L) + duration)
            }
        }
    }.toSortedMap()
}

internal fun mergeDailyListenMs(
    local: Map<String, Long>,
    remote: Map<String, Long>
): Map<String, Long> = buildMap {
    local.forEach { (date, duration) -> put(date, duration.coerceAtLeast(0L)) }
    remote.forEach { (date, duration) ->
        put(date, (get(date) ?: 0L) + duration.coerceAtLeast(0L))
    }
}.toSortedMap()

private fun Song.historyFingerprint(): String =
    listOf(title, artist, album).joinToString("|") { it.historyKeyPart() }

private fun LastFmTrack.historyFingerprint(): String =
    listOf(title, artist, album).joinToString("|") { it.historyKeyPart() }

private fun PlaybackHistoryEntry.historyFingerprint(): String =
    listOf(title, artist, album).joinToString("|") { it.historyKeyPart() }

private fun String.historyKeyPart(): String =
    trim().lowercase(Locale.ROOT).replace(Regex("\\s+"), " ")

private fun Long.toHistoryDateKey(): String =
    if (this <= 0L) "" else SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(this))

private const val HISTORY_DEDUP_WINDOW_MS = 120_000L
