package com.ella.music.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * The immediate "recently played" feed is deliberately persisted separately from listening
 * statistics.  A user can therefore clear the feed without changing the analytics calendar or
 * the per-song play totals.
 */
class RecentPlaybackStore private constructor(context: Context) {
    private val historyFile = AtomicFile(
        File(context.applicationContext.filesDir, "recent_playback.json")
    )
    private val lock = Any()
    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<PlaybackHistoryEntry>> = _history.asStateFlow()

    /** Migrates existing installs once, while preserving an intentional empty feed. */
    fun migrateFromHistoryIfNeeded(existingHistory: List<PlaybackHistoryEntry>) {
        synchronized(lock) {
            if (historyFile.baseFile.exists() || existingHistory.isEmpty()) return
            publish(existingHistory)
        }
    }

    fun add(entry: PlaybackHistoryEntry) {
        synchronized(lock) {
            if (_history.value.any { it.entryId == entry.entryId }) return
            publish(listOf(entry) + _history.value)
        }
    }

    fun merge(entries: List<PlaybackHistoryEntry>) {
        if (entries.isEmpty()) return
        synchronized(lock) {
            val existingIds = _history.value.asSequence()
                .map(PlaybackHistoryEntry::entryId)
                .toHashSet()
            val additions = entries
                .filter { it.entryId.isNotBlank() && it.playedAt > 0L }
                .distinctBy(PlaybackHistoryEntry::entryId)
                .filterNot { it.entryId in existingIds }
            if (additions.isNotEmpty()) publish(_history.value + additions)
        }
    }

    fun remove(entryId: String) {
        if (entryId.isBlank()) return
        synchronized(lock) {
            val updated = _history.value.filterNot { it.entryId == entryId }
            if (updated.size != _history.value.size) publish(updated)
        }
    }

    fun removeAll(entryIds: Set<String>) {
        if (entryIds.isEmpty()) return
        synchronized(lock) {
            val updated = _history.value.filterNot { it.entryId in entryIds }
            if (updated.size != _history.value.size) publish(updated)
        }
    }

    fun clear() {
        synchronized(lock) {
            if (_history.value.isNotEmpty()) publish(emptyList())
        }
    }

    fun replace(entries: List<PlaybackHistoryEntry>) {
        synchronized(lock) {
            publish(entries)
        }
    }

    private fun publish(entries: List<PlaybackHistoryEntry>) {
        val sorted = entries
            .filter { it.entryId.isNotBlank() && it.playedAt > 0L }
            .distinctBy(PlaybackHistoryEntry::entryId)
            .sortedByDescending(PlaybackHistoryEntry::playedAt)
        writeAtomic(historyToJson(sorted).toString())
        _history.value = sorted
    }

    private fun loadHistory(): List<PlaybackHistoryEntry> {
        if (!historyFile.baseFile.exists()) return emptyList()
        return runCatching {
            historyFile.openRead().bufferedReader().use { reader ->
                JSONArray(reader.readText()).toHistoryList()
            }
        }.onFailure {
            Log.w("RecentPlaybackStore", "Failed to load recent playback history", it)
        }.getOrDefault(emptyList())
    }

    private fun writeAtomic(payload: String) {
        runCatching {
            val stream = historyFile.startWrite()
            try {
                stream.write(payload.toByteArray(Charsets.UTF_8))
                historyFile.finishWrite(stream)
            } catch (error: Throwable) {
                historyFile.failWrite(stream)
                throw error
            }
        }.onFailure {
            Log.w("RecentPlaybackStore", "Failed to save recent playback history", it)
        }
    }

    private fun historyToJson(history: List<PlaybackHistoryEntry>): JSONArray = JSONArray().apply {
        history.forEach { entry ->
            put(
                JSONObject()
                    .put("entryId", entry.entryId)
                    .put("songId", entry.songId)
                    .put("title", entry.title)
                    .put("artist", entry.artist)
                    .put("album", entry.album)
                    .put("playedAt", entry.playedAt)
                    .put("durationMs", entry.durationMs)
                    .put("listenedMs", entry.listenedMs)
                    .put("source", entry.source)
                    .put("playCounted", entry.playCounted)
            )
        }
    }

    private fun JSONArray.toHistoryList(): List<PlaybackHistoryEntry> =
        List(length()) { index ->
            val item = getJSONObject(index)
            PlaybackHistoryEntry(
                entryId = item.optString("entryId"),
                songId = item.optLong("songId"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                playedAt = item.optLong("playedAt"),
                durationMs = item.optLong("durationMs").coerceAtLeast(0L),
                listenedMs = item.optLong("listenedMs").coerceAtLeast(0L),
                source = item.optString("source", PlaybackHistorySource.LOCAL),
                playCounted = if (item.has("playCounted")) item.optBoolean("playCounted") else true
            )
        }
            .filter { it.entryId.isNotBlank() && it.playedAt > 0L }
            .distinctBy(PlaybackHistoryEntry::entryId)
            .sortedByDescending(PlaybackHistoryEntry::playedAt)

    companion object {
        @Volatile
        private var instance: RecentPlaybackStore? = null

        fun getInstance(context: Context): RecentPlaybackStore {
            return instance ?: synchronized(this) {
                instance ?: RecentPlaybackStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
