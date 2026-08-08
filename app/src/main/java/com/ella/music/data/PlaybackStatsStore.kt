package com.ella.music.data

import android.content.Context
import android.util.AtomicFile
import android.util.Log
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

data class SongPlaybackStats(
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playCount: Int,
    val listenedMs: Long,
    val lastPlayedAt: Long
)

data class PlaybackHistoryEntry(
    /** Stable identity for one listen; unlike a song id it also distinguishes repeated plays. */
    val entryId: String = UUID.randomUUID().toString(),
    val songId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val playedAt: Long,
    /** Cached duration keeps remote history useful even when the song is not in this library. */
    val durationMs: Long = 0L,
    /** Actual accumulated playback for this listen, kept with the record rather than a day bucket. */
    val listenedMs: Long = 0L,
    /** Origin is persisted so local deletion and local hiding of remote records are deterministic. */
    val source: String = PlaybackHistorySource.LOCAL,
    /** Guards play-count/scrobble updates when more than one playback observer sees one session. */
    val playCounted: Boolean = false
)

object PlaybackHistorySource {
    const val LOCAL = "local"
    const val LAST_FM = "lastfm"
}

class PlaybackStatsStore private constructor(context: Context) {
    private val statsFile = AtomicFile(File(context.applicationContext.filesDir, "playback_stats.json"))
    private val historyFile = AtomicFile(File(context.applicationContext.filesDir, "playback_history.json"))
    private val dailyStatsFile = AtomicFile(File(context.applicationContext.filesDir, "playback_daily_stats.json"))
    private val activePlaybackSessionFile = AtomicFile(
        File(context.applicationContext.filesDir, "playback_active_session.json")
    )
    private val hiddenRemoteHistoryFile = AtomicFile(File(context.applicationContext.filesDir, "hidden_remote_history.json"))
    private val persistenceMutex = Mutex()
    private val _stats = MutableStateFlow<List<SongPlaybackStats>>(emptyList())
    val stats: StateFlow<List<SongPlaybackStats>> = _stats.asStateFlow()
    private val _history = MutableStateFlow<List<PlaybackHistoryEntry>>(emptyList())
    val history: StateFlow<List<PlaybackHistoryEntry>> = _history.asStateFlow()
    private val _hiddenRemoteHistoryEntryIds = MutableStateFlow(loadHiddenRemoteHistoryEntryIds())
    val hiddenRemoteHistoryEntryIds: StateFlow<Set<String>> = _hiddenRemoteHistoryEntryIds.asStateFlow()
    private val _dailyListenMs = MutableStateFlow<Map<String, Long>>(emptyMap())
    val dailyListenMs: StateFlow<Map<String, Long>> = _dailyListenMs.asStateFlow()
    private var activePlaybackSession: ActivePlaybackSession? = loadActivePlaybackSession()

    init {
        loadStats()
        loadHistory()
        loadDailyStats()
        migrateLegacyHistoryListenDurations()
    }

    /** Adds a recent-listening session as soon as playback actually starts. */
    suspend fun recordRecent(song: Song): String = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        persistenceMutex.withLock {
            val active = activePlaybackSession
            val activeEntry = active
                ?.takeIf {
                    it.songId == song.id && now - it.lastTouchedAtMs in 0..ACTIVE_SESSION_IDLE_TIMEOUT_MS
                }
                ?.let { session ->
                    _history.value.firstOrNull {
                        it.entryId == session.entryId &&
                            it.songId == song.id &&
                            it.source == PlaybackHistorySource.LOCAL
                    }
            }
            if (activeEntry != null) {
                activePlaybackSession = active.copy(lastTouchedAtMs = now)
                saveActivePlaybackSession(activePlaybackSession)
                return@withLock activeEntry.entryId
            }
            val entry = PlaybackHistoryEntry(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                album = song.album,
                playedAt = now,
                durationMs = song.duration.coerceAtLeast(0L)
            )
            val updatedHistory = (listOf(entry) + _history.value).deduplicateHistory()
            activePlaybackSession = ActivePlaybackSession(song.id, entry.entryId, now)
            saveActivePlaybackSession(activePlaybackSession)
            publishLocked(_stats.value, updatedHistory)
            entry.entryId
        }
    }

    /** Increments play count once for the supplied playback session. */
    suspend fun recordPlay(song: Song, historyEntryId: String? = null): Boolean = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        persistenceMutex.withLock {
            val targetEntry = historyEntryId
                ?.let { id -> _history.value.firstOrNull { it.entryId == id && it.songId == song.id } }
            if (targetEntry?.playCounted == true) return@withLock false
            val updatedStats = updateStatsLocked(song) { current ->
                current.copy(playCount = current.playCount + 1, lastPlayedAt = now)
            }
            val updatedHistory = if (targetEntry == null) {
                _history.value
            } else {
                _history.value.map { entry ->
                    if (entry.entryId == targetEntry.entryId) entry.copy(playCounted = true) else entry
                }
            }
            activePlaybackSession = activePlaybackSession
                ?.takeIf { it.entryId == targetEntry?.entryId }
                ?.copy(lastTouchedAtMs = now)
                ?: activePlaybackSession
            saveActivePlaybackSession(activePlaybackSession)
            publishLocked(updatedStats, updatedHistory)
            true
        }
    }

    suspend fun addListenTime(
        song: Song,
        listenedMs: Long,
        historyEntryId: String? = null
    ) = withContext(Dispatchers.IO) {
        if (listenedMs <= 0) return@withContext
        val now = System.currentTimeMillis()
        persistenceMutex.withLock {
            val updatedStats = updateStatsLocked(song) { current ->
                current.copy(
                    listenedMs = current.listenedMs + listenedMs,
                    lastPlayedAt = now
                )
            }
            val targetEntryId = historyEntryId ?: _history.value
                .firstOrNull { it.source == PlaybackHistorySource.LOCAL && it.songId == song.id }
                ?.entryId
            val updatedHistory = _history.value.map { entry ->
                if (entry.entryId == targetEntryId) {
                    entry.copy(listenedMs = entry.listenedMs + listenedMs)
                } else {
                    entry
                }
            }
            if (targetEntryId != null && activePlaybackSession?.entryId == targetEntryId) {
                activePlaybackSession = activePlaybackSession?.copy(lastTouchedAtMs = now)
                saveActivePlaybackSession(activePlaybackSession)
            }
            publishLocked(updatedStats, updatedHistory)
        }
    }

    suspend fun exportJson(librarySongs: List<Song> = emptyList()): JSONObject = withContext(Dispatchers.IO) {
        JSONObject()
            .put("stats", statsToJson(_stats.value))
            .put("history", historyToJson(_history.value))
            .put("dailyListenMs", dailyStatsToJson(_dailyListenMs.value))
            .put("sessions", historyToSollinSessions(_history.value, _stats.value, librarySongs))
    }

    suspend fun restoreJson(payload: JSONObject) = withContext(Dispatchers.IO) {
        if (payload.has("sessions")) {
            restoreSollinSessions(payload.optJSONArray("sessions") ?: JSONArray())
            return@withContext
        }
        val stats = payload.optJSONArray("stats")?.toStatsList().orEmpty()
        val history = payload.optJSONArray("history")?.toHistoryList().orEmpty()
        val daily = payload.optJSONObject("dailyListenMs")?.toDailyStatsMap().orEmpty()

        persistenceMutex.withLock {
            publishLocked(stats, history.assignLegacyListenDurations(daily))
        }
    }

    private fun updateStatsLocked(
        song: Song,
        transform: (SongPlaybackStats) -> SongPlaybackStats
    ): List<SongPlaybackStats> {
        val current = _stats.value.associateBy { it.songId }.toMutableMap()
        val existing = current[song.id] ?: SongPlaybackStats(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            playCount = 0,
            listenedMs = 0L,
            lastPlayedAt = 0L
        )
        current[song.id] = transform(
            existing.copy(
                title = song.title,
                artist = song.artist,
                album = song.album
            )
        )
        val sorted = current.values.sortedByDescending { it.lastPlayedAt }
        return sorted
    }

    suspend fun removeHistoryEntry(entry: PlaybackHistoryEntry) = withContext(Dispatchers.IO) {
        persistenceMutex.withLock {
            val updatedHistory = _history.value.filterNot { it.entryId == entry.entryId }
            if (updatedHistory.size == _history.value.size) return@withLock
            if (activePlaybackSession?.entryId == entry.entryId) {
                activePlaybackSession = null
                saveActivePlaybackSession(null)
            }
            publishLocked(_stats.value, updatedHistory)
        }
    }

    /** Hides one remote cache record locally without modifying the user's Last.fm account. */
    suspend fun hideRemoteHistoryEntry(entryId: String) = withContext(Dispatchers.IO) {
        if (entryId.isBlank()) return@withContext
        persistenceMutex.withLock {
            val updated = _hiddenRemoteHistoryEntryIds.value + entryId
            if (updated == _hiddenRemoteHistoryEntryIds.value) return@withLock
            saveHiddenRemoteHistoryEntryIds(updated)
            _hiddenRemoteHistoryEntryIds.value = updated
        }
    }

    private fun publishLocked(
        stats: List<SongPlaybackStats>,
        history: List<PlaybackHistoryEntry>
    ) {
        val sortedHistory = history.deduplicateHistory()
        val daily = sortedHistory.toDailyListenMs()
        save(stats)
        saveHistory(sortedHistory)
        saveDailyStats(daily)
        _stats.value = stats
        _history.value = sortedHistory
        _dailyListenMs.value = daily
    }

    private fun loadStats() {
        if (!statsFile.baseFile.exists()) return
        runCatching {
            val array = statsFile.openRead().bufferedReader().use { JSONArray(it.readText()) }
            _stats.value = array.toStatsList()
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to load playback stats", it)
        }
    }

    private fun loadHistory() {
        if (!historyFile.baseFile.exists()) return
        runCatching {
            val array = historyFile.openRead().bufferedReader().use { JSONArray(it.readText()) }
            _history.value = array.toHistoryList()
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to load playback history", it)
        }
    }

    private fun loadDailyStats() {
        if (!dailyStatsFile.baseFile.exists()) return
        runCatching {
            val payload = dailyStatsFile.openRead().bufferedReader().use { JSONObject(it.readText()) }
            _dailyListenMs.value = payload.toDailyStatsMap().toSortedMap()
        }.onFailure {
            Log.w("PlaybackStatsStore", "Failed to load daily playback stats", it)
        }
    }

    private fun save(stats: List<SongPlaybackStats>) {
        writeAtomic(statsFile, statsToJson(stats).toString(), "playback stats")
    }

    private fun loadHiddenRemoteHistoryEntryIds(): Set<String> {
        if (!hiddenRemoteHistoryFile.baseFile.exists()) return emptySet()
        return runCatching {
            hiddenRemoteHistoryFile.openRead().bufferedReader().use { reader ->
                JSONArray(reader.readText())
                    .let { array -> List(array.length()) { index -> array.optString(index) } }
                    .filter(String::isNotBlank)
                    .toSet()
            }
        }.getOrElse { error ->
            Log.w("PlaybackStatsStore", "Failed to load hidden remote history", error)
            emptySet()
        }
    }

    private fun loadActivePlaybackSession(): ActivePlaybackSession? {
        if (!activePlaybackSessionFile.baseFile.exists()) return null
        return runCatching {
            activePlaybackSessionFile.openRead().bufferedReader().use { reader ->
                val payload = JSONObject(reader.readText())
                ActivePlaybackSession(
                    songId = payload.optLong("songId"),
                    entryId = payload.optString("entryId"),
                    lastTouchedAtMs = payload.optLong("lastTouchedAtMs")
                ).takeIf { it.songId != 0L && it.entryId.isNotBlank() }
            }
        }.getOrElse { error ->
            Log.w("PlaybackStatsStore", "Failed to load active playback session", error)
            null
        }
    }

    private fun saveActivePlaybackSession(session: ActivePlaybackSession?) {
        if (session == null) {
            runCatching { activePlaybackSessionFile.baseFile.delete() }
            return
        }
        writeAtomic(
            activePlaybackSessionFile,
            JSONObject()
                .put("songId", session.songId)
                .put("entryId", session.entryId)
                .put("lastTouchedAtMs", session.lastTouchedAtMs)
                .toString(),
            "active playback session"
        )
    }

    private fun saveHistory(history: List<PlaybackHistoryEntry>) {
        writeAtomic(historyFile, historyToJson(history).toString(), "playback history")
    }

    private fun saveDailyStats(dailyStats: Map<String, Long>) {
        writeAtomic(dailyStatsFile, dailyStatsToJson(dailyStats).toString(), "daily playback stats")
    }

    private fun saveHiddenRemoteHistoryEntryIds(entryIds: Set<String>) {
        writeAtomic(
            hiddenRemoteHistoryFile,
            JSONArray(entryIds.sorted()).toString(),
            "hidden remote history"
        )
    }

    private fun writeAtomic(file: AtomicFile, payload: String, label: String) {
        runCatching {
            val stream = file.startWrite()
            try {
                stream.write(payload.toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (error: Throwable) {
                file.failWrite(stream)
                throw error
            }
        }.onFailure { Log.w("PlaybackStatsStore", "Failed to save $label", it) }
    }

    private fun migrateLegacyHistoryListenDurations() {
        val history = _history.value
        if (history.isEmpty() || history.any { it.listenedMs > 0L } || _dailyListenMs.value.isEmpty()) return
        val migrated = history.groupBy { it.playedAt.toDateKey() }
            .flatMap { (date, entries) ->
                val dayListenMs = _dailyListenMs.value[date] ?: return@flatMap entries
                val each = dayListenMs / entries.size.coerceAtLeast(1)
                var remainder = dayListenMs % entries.size.coerceAtLeast(1)
                entries.map { entry ->
                    val share = each + if (remainder-- > 0L) 1L else 0L
                    entry.copy(listenedMs = share)
                }
            }
            .deduplicateHistory()
        _history.value = migrated
        saveHistory(migrated)
    }

    private fun statsToJson(stats: List<SongPlaybackStats>): JSONArray {
        val array = JSONArray()
        stats.forEach { stat ->
            array.put(
                JSONObject()
                    .put("songId", stat.songId)
                    .put("title", stat.title)
                    .put("artist", stat.artist)
                    .put("album", stat.album)
                    .put("playCount", stat.playCount)
                    .put("listenedMs", stat.listenedMs)
                    .put("lastPlayedAt", stat.lastPlayedAt)
            )
        }
        return array
    }

    private fun historyToJson(history: List<PlaybackHistoryEntry>): JSONArray {
        val array = JSONArray()
        history.forEach { entry ->
            array.put(
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
        return array
    }

    private fun dailyStatsToJson(dailyStats: Map<String, Long>): JSONObject {
        val payload = JSONObject()
        dailyStats.forEach { (date, listenedMs) ->
            payload.put(date, listenedMs)
        }
        return payload
    }

    private fun historyToSollinSessions(
        history: List<PlaybackHistoryEntry>,
        stats: List<SongPlaybackStats>,
        librarySongs: List<Song>
    ): JSONArray {
        val statBySong = stats.associateBy { it.songId }
        val libraryById = librarySongs.associateBy { it.id }
        val libraryByFingerprint = librarySongs.associateBy { it.statsFingerprint() }
        val array = JSONArray()
        history.forEach { entry ->
            val stat = statBySong[entry.songId]
            val song = libraryById[entry.songId] ?: libraryByFingerprint[entry.statsFingerprint()]
            val averagePlayedMs = entry.listenedMs.takeIf { it > 0L } ?: stat?.let {
                if (it.playCount > 0) it.listenedMs / it.playCount else it.listenedMs
            } ?: 0L
            val durationMs = song?.duration ?: entry.durationMs
            val playedMs = averagePlayedMs
                .takeIf { it > 0L }
                ?: durationMs.takeIf { it > 0L }
                ?: DEFAULT_SOLIN_SESSION_PLAYED_MS
            val endedAtMs = entry.playedAt
            val startedAtMs = (endedAtMs - playedMs).coerceAtLeast(1L)
            array.put(
                JSONObject()
                    .put("uid", entry.entryId)
                    .put("songId", entry.songId)
                    .put("title", entry.title)
                    .put("artist", entry.artist)
                    .put("album", entry.album)
                    .put("albumKey", song?.let { "id:${it.albumId.takeIf { id -> id > 0L } ?: it.albumIdentityId()}" }.orEmpty())
                    .put("durationMs", durationMs)
                    .put("playedMs", playedMs)
                    .put("startedAtMs", startedAtMs)
                    .put("endedAtMs", endedAtMs)
                    .put("dayBucket", endedAtMs.toDayBucket())
                    .put("cover", song?.statsCoverUri().orEmpty())
            )
        }
        return array
    }

    private fun JSONArray.toStatsList(): List<SongPlaybackStats> =
        List(length()) { index ->
            val item = getJSONObject(index)
            SongPlaybackStats(
                songId = item.getLong("songId"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                playCount = item.optInt("playCount"),
                listenedMs = item.optLong("listenedMs"),
                lastPlayedAt = item.optLong("lastPlayedAt")
            )
        }.sortedByDescending { it.lastPlayedAt }

    private fun JSONArray.toHistoryList(): List<PlaybackHistoryEntry> =
        List(length()) { index ->
            val item = getJSONObject(index)
            PlaybackHistoryEntry(
                entryId = item.optString("entryId").ifBlank {
                    "legacy:${item.optLong("songId")}:${item.optLong("playedAt")}:$index"
                },
                songId = item.optLong("songId"),
                title = item.optString("title"),
                artist = item.optString("artist"),
                album = item.optString("album"),
                playedAt = item.optLong("playedAt"),
                durationMs = item.optLong("durationMs").coerceAtLeast(0L),
                listenedMs = item.optLong("listenedMs").coerceAtLeast(0L),
                source = item.optString("source", PlaybackHistorySource.LOCAL),
                playCounted = item.optBoolean("playCounted", false)
            )
        }.filter { it.playedAt > 0L }
            .sortedByDescending { it.playedAt }

    private fun JSONObject.toDailyStatsMap(): Map<String, Long> {
        val parsed = mutableMapOf<String, Long>()
        keys().forEach { key ->
            parsed[key] = optLong(key)
        }
        return parsed
    }

    private fun restoreSollinSessions(sessions: JSONArray) {
        val history = mutableListOf<PlaybackHistoryEntry>()
        val aggregates = linkedMapOf<String, SollinAggregate>()
        val daily = mutableMapOf<String, Long>()

        for (index in 0 until sessions.length()) {
            val item = sessions.optJSONObject(index) ?: continue
            val songId = item.optLong("songId")
            val title = item.optString("title")
            val artist = item.optString("artist")
            val album = item.optString("album")
            val startedAt = item.optLong("startedAtMs")
            val endedAt = item.optLong("endedAtMs").takeIf { it > 0L } ?: startedAt
            val playedMs = item.optLong("playedMs").coerceAtLeast(0L)
            val key = if (songId != 0L) "id:$songId" else "$title|$artist|$album"

            if (endedAt > 0L) {
                history += PlaybackHistoryEntry(
                    entryId = item.optString("uid").ifBlank { UUID.randomUUID().toString() },
                    songId = songId,
                    title = title,
                    artist = artist,
                    album = album,
                    playedAt = endedAt,
                    durationMs = item.optLong("durationMs").coerceAtLeast(0L),
                    listenedMs = playedMs
                )
            }

            val aggregate = aggregates.getOrPut(key) {
                SollinAggregate(songId, title, artist, album)
            }
            aggregate.playCount += 1
            aggregate.listenedMs += playedMs
            aggregate.lastPlayedAt = maxOf(aggregate.lastPlayedAt, endedAt)

            val dayKey = item.optInt("dayBucket")
                .takeIf { it > 0 }
                ?.toDateKeyFromBucket()
                ?: endedAt.toDateKey()
            daily[dayKey] = (daily[dayKey] ?: 0L) + playedMs
        }

        val stats = aggregates.values.map { aggregate ->
            SongPlaybackStats(
                songId = aggregate.songId,
                title = aggregate.title,
                artist = aggregate.artist,
                album = aggregate.album,
                playCount = aggregate.playCount,
                listenedMs = aggregate.listenedMs,
                lastPlayedAt = aggregate.lastPlayedAt
            )
        }.sortedByDescending { it.lastPlayedAt }

        val sortedHistory = history.deduplicateHistory()

        _stats.value = stats
        _history.value = sortedHistory
        _dailyListenMs.value = sortedHistory.toDailyListenMs().ifEmpty { daily.toSortedMap() }
        save(stats)
        saveHistory(sortedHistory)
        saveDailyStats(_dailyListenMs.value)
    }

    private fun Long.toDateKey(): String {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = this
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun List<PlaybackHistoryEntry>.deduplicateHistory(): List<PlaybackHistoryEntry> =
        distinctBy(PlaybackHistoryEntry::entryId).sortedByDescending(PlaybackHistoryEntry::playedAt)

    private fun List<PlaybackHistoryEntry>.toDailyListenMs(): Map<String, Long> =
        filter { it.listenedMs > 0L }
            .groupBy { it.playedAt.toDateKey() }
            .mapValues { (_, entries) -> entries.sumOf(PlaybackHistoryEntry::listenedMs) }
            .toSortedMap()

    private fun List<PlaybackHistoryEntry>.assignLegacyListenDurations(
        dailyListenMs: Map<String, Long>
    ): List<PlaybackHistoryEntry> {
        if (none { it.listenedMs <= 0L } || dailyListenMs.isEmpty()) return this
        return groupBy { it.playedAt.toDateKey() }.flatMap { (date, entries) ->
            val dayListenMs = dailyListenMs[date] ?: return@flatMap entries
            val each = dayListenMs / entries.size.coerceAtLeast(1)
            var remainder = dayListenMs % entries.size.coerceAtLeast(1)
            entries.map { entry ->
                if (entry.listenedMs > 0L) entry else entry.copy(
                    listenedMs = each + if (remainder-- > 0L) 1L else 0L
                )
            }
        }.deduplicateHistory()
    }

    private fun Long.toDayBucket(): Int {
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = this
        val year = calendar.get(java.util.Calendar.YEAR)
        val month = calendar.get(java.util.Calendar.MONTH) + 1
        val day = calendar.get(java.util.Calendar.DAY_OF_MONTH)
        return year * 10000 + month * 100 + day
    }

    private fun Int.toDateKeyFromBucket(): String {
        val year = this / 10000
        val month = (this / 100) % 100
        val day = this % 100
        return "%04d-%02d-%02d".format(year, month, day)
    }

    private fun Song.statsCoverUri(): String =
        coverUrl.takeIf { it.isNotBlank() }
            ?: albumId.takeIf { it > 0L }?.let { "content://media/external/audio/albumart/$it" }
            ?: ""

    private fun Song.statsFingerprint(): String =
        listOf(title, artist, album).joinToString("|") { it.statsKeyPart() }

    private fun PlaybackHistoryEntry.statsFingerprint(): String =
        listOf(title, artist, album).joinToString("|") { it.statsKeyPart() }

    private fun String.statsKeyPart(): String =
        trim().lowercase().replace(Regex("\\s+"), " ")

    private data class SollinAggregate(
        val songId: Long,
        val title: String,
        val artist: String,
        val album: String,
        var playCount: Int = 0,
        var listenedMs: Long = 0L,
        var lastPlayedAt: Long = 0L
    )

    private data class ActivePlaybackSession(
        val songId: Long,
        val entryId: String,
        val lastTouchedAtMs: Long
    )

    companion object {
        private const val DEFAULT_SOLIN_SESSION_PLAYED_MS = 60_000L
        private const val ACTIVE_SESSION_IDLE_TIMEOUT_MS = 30_000L

        @Volatile
        private var instance: PlaybackStatsStore? = null

        fun getInstance(context: Context): PlaybackStatsStore {
            return instance ?: synchronized(this) {
                instance ?: PlaybackStatsStore(context.applicationContext).also { instance = it }
            }
        }
    }
}
