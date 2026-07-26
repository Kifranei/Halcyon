package com.ella.music.data.lastfm

import android.content.Context
import android.content.Intent
import android.util.AtomicFile
import android.util.Log
import com.ella.music.data.AppLogStore
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/**
 * Owns Last.fm authorization state, the locally cached full history and the durable scrobble
 * outbox.  The cache keeps the listening calendar usable offline; the outbox prevents a short
 * network outage from silently losing a finished listen.
 */
class LastFmHistoryStore private constructor(private val context: Context) {
    private val secureStore = LastFmSecureStore.getInstance(context)
    private val api = LastFmApi()
    private val historyFile = AtomicFile(File(context.filesDir, HISTORY_FILE_NAME))
    private val outboxFile = AtomicFile(File(context.filesDir, OUTBOX_FILE_NAME))
    private val mutex = Mutex()

    val credentials: StateFlow<LastFmCredentials> = secureStore.credentials
    private val _history = MutableStateFlow(loadHistory())
    val history: StateFlow<List<LastFmTrack>> = _history.asStateFlow()
    private val _pendingScrobbles = MutableStateFlow(loadOutbox())
    val pendingScrobbles: StateFlow<List<LastFmPendingScrobble>> = _pendingScrobbles.asStateFlow()
    private val _syncStatus = MutableStateFlow<LastFmSyncStatus>(
        if (_history.value.isEmpty()) LastFmSyncStatus.Idle
        else LastFmSyncStatus.Complete(_history.value.size, 0L)
    )
    val syncStatus: StateFlow<LastFmSyncStatus> = _syncStatus.asStateFlow()

    val playbackHistory: List<PlaybackHistoryEntry>
        get() = _history.value.map(LastFmTrack::toPlaybackHistoryEntry)

    fun updateAppCredentials(apiKey: String, sharedSecret: String) {
        val previous = credentials.value
        val changed = previous.apiKey.trim() != apiKey.trim() ||
            previous.sharedSecret.trim() != sharedSecret.trim()
        secureStore.updateAppCredentials(apiKey, sharedSecret)
        // A cache/outbox belongs to the authenticated account *and* its Last.fm application.
        // Never surface one account's cached listens, or submit its queued plays, after the
        // application credentials have been changed.
        if (changed) clearRemoteAccountData()
    }

    suspend fun beginAuthorization(): Intent = mutex.withLock {
        val credentials = credentials.value
        require(credentials.hasAppCredentials) { "请先填写 Last.fm API Key 和 Shared Secret" }
        val token = api.requestAuthorizationToken(credentials)
        secureStore.setPendingToken(token)
        api.authorizationIntent(credentials.apiKey, token)
    }

    /** Call this after the browser returns from the Last.fm authorization page. */
    suspend fun completeAuthorizationAndSync() = mutex.withLock {
        val credentials = credentials.value
        require(credentials.hasAppCredentials) { "请先填写 Last.fm API Key 和 Shared Secret" }
        val token = credentials.pendingToken.takeIf(String::isNotBlank)
            ?: error("没有待完成的 Last.fm 授权")
        val session = api.finishAuthorization(credentials, token)
        secureStore.saveSession(session)
        syncHistoryLocked()
        flushPendingLocked()
    }

    suspend fun syncAllHistory() = mutex.withLock {
        syncHistoryLocked()
    }

    suspend fun updateNowPlaying(song: Song) = mutex.withLock {
        val credentials = credentials.value
        if (!credentials.isAuthorized) return@withLock
        val track = song.toLastFmPendingScrobble(startedAt = System.currentTimeMillis()) ?: return@withLock
        runCatching { api.updateNowPlaying(credentials, track) }
            .onFailure { error ->
                AppLogStore.warn(context, "LastFmNowPlaying", "Failed to update now playing: ${song.title}", error)
            }
    }

    suspend fun enqueueScrobble(song: Song, startedAt: Long) = mutex.withLock {
        // An outbox is for temporary network failures of an already authenticated account, not
        // a retroactive log for a user who has not chosen an account yet.  Otherwise selecting
        // the Last.fm source before authorization could unexpectedly submit old listens later.
        if (!credentials.value.isAuthorized) return@withLock
        val scrobble = song.toLastFmPendingScrobble(startedAt) ?: return@withLock
        if (song.duration in 1L..30_000L) return@withLock
        val updated = (_pendingScrobbles.value + scrobble)
            .distinctBy(LastFmPendingScrobble::id)
            .sortedBy(LastFmPendingScrobble::startedAt)
        _pendingScrobbles.value = updated
        saveOutbox(updated)
        flushPendingLocked()
    }

    suspend fun flushPendingScrobbles() = mutex.withLock {
        flushPendingLocked()
    }

    fun clearAuthorization() {
        secureStore.clearAuthorization()
        clearRemoteAccountData()
    }

    fun clearHistoryCache() {
        _history.value = emptyList()
        historyFile.delete()
        _syncStatus.value = LastFmSyncStatus.Idle
    }

    private fun clearRemoteAccountData() {
        clearHistoryCache()
        _pendingScrobbles.value = emptyList()
        outboxFile.delete()
    }

    private suspend fun syncHistoryLocked() {
        val credentials = credentials.value
        require(credentials.apiKey.isNotBlank()) { "请先填写 Last.fm API Key" }
        require(credentials.username.isNotBlank()) { "请先完成 Last.fm 授权" }

        val received = ArrayList<LastFmTrack>()
        var page = 1
        var totalPages = 1
        var totalTracks = 0
        try {
            while (page <= totalPages) {
                _syncStatus.value = LastFmSyncStatus.Syncing(page, totalPages, received.size)
                val result = api.getRecentTracks(
                    apiKey = credentials.apiKey,
                    username = credentials.username,
                    page = page
                )
                totalPages = result.totalPages.coerceAtLeast(page)
                totalTracks = result.totalTracks.coerceAtLeast(totalTracks)
                received += result.tracks
                // The API uses pagination; a non-advancing response must not spin forever.
                if (result.tracks.isEmpty() || page >= totalPages) break
                page++
            }
            val normalized = received
                .filter { it.playedAt > 0L }
                .distinctBy(LastFmTrack::cacheKey)
                .sortedByDescending(LastFmTrack::playedAt)
            _history.value = normalized
            saveHistory(normalized)
            _syncStatus.value = LastFmSyncStatus.Complete(
                totalTracks = maxOf(totalTracks, normalized.size),
                syncedAt = System.currentTimeMillis()
            )
            AppLogStore.info(context, "LastFmHistory", "Synced ${normalized.size} Last.fm listens for ${credentials.username}")
        } catch (error: Throwable) {
            _syncStatus.value = LastFmSyncStatus.Failed(error.message.orEmpty().ifBlank { "Last.fm 同步失败" })
            AppLogStore.warn(context, "LastFmHistory", "Last.fm history sync failed", error)
            throw error
        }
    }

    private suspend fun flushPendingLocked() {
        val credentials = credentials.value
        if (!credentials.isAuthorized || _pendingScrobbles.value.isEmpty()) return
        var remaining = _pendingScrobbles.value
        while (remaining.isNotEmpty()) {
            val next = remaining.first()
            try {
                val accepted = api.scrobble(credentials, next)
                remaining = remaining.drop(1)
                _pendingScrobbles.value = remaining
                saveOutbox(remaining)
                if (accepted) {
                    appendSuccessfulScrobble(next)
                } else {
                    AppLogStore.warn(
                        context,
                        "LastFmScrobble",
                        "Last.fm ignored scrobble for ${next.track}; it will not be added to the local cache"
                    )
                }
            } catch (error: LastFmApiException) {
                if (error.requiresReauthentication) {
                    secureStore.clearAuthorization()
                }
                AppLogStore.warn(context, "LastFmScrobble", "Last.fm scrobble remains queued: ${next.track}", error)
                // Keep all queued records for transient errors and auth recovery. A later manual
                // sync or a new eligible track retries the queue in its original play order.
                return
            } catch (error: Throwable) {
                AppLogStore.warn(context, "LastFmScrobble", "Last.fm scrobble remains queued: ${next.track}", error)
                return
            }
        }
    }

    /**
     * A successful scrobble is immediately reflected in the cached remote history.  Without
     * this, selecting "Last.fm only" would appear frozen until the user manually ran a full
     * history refresh even though Last.fm had already accepted the play.
     */
    private fun appendSuccessfulScrobble(scrobble: LastFmPendingScrobble) {
        val entry = LastFmTrack(
            title = scrobble.track,
            artist = scrobble.artist,
            album = scrobble.album,
            playedAt = scrobble.startedAt,
            durationMs = scrobble.durationSeconds.coerceAtLeast(0).toLong() * 1_000L
        )
        val updated = (listOf(entry) + _history.value)
            .distinctBy(LastFmTrack::cacheKey)
            .sortedByDescending(LastFmTrack::playedAt)
        _history.value = updated
        saveHistory(updated)
    }

    private fun loadHistory(): List<LastFmTrack> {
        if (!historyFile.baseFile.exists()) return emptyList()
        return runCatching {
            historyFile.openRead().bufferedReader().use { reader ->
                JSONArray(reader.readText()).toLastFmTracks()
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to load Last.fm history cache", error)
            emptyList()
        }
    }

    private fun saveHistory(history: List<LastFmTrack>) {
        writeAtomic(historyFile, history.toHistoryJson().toString(), "Last.fm history")
    }

    private fun loadOutbox(): List<LastFmPendingScrobble> {
        if (!outboxFile.baseFile.exists()) return emptyList()
        return runCatching {
            outboxFile.openRead().bufferedReader().use { reader ->
                JSONArray(reader.readText()).toPendingScrobbles()
            }
        }.getOrElse { error ->
            Log.w(TAG, "Failed to load Last.fm scrobble outbox", error)
            emptyList()
        }
    }

    private fun saveOutbox(scrobbles: List<LastFmPendingScrobble>) {
        writeAtomic(outboxFile, scrobbles.toScrobbleJson().toString(), "Last.fm scrobble outbox")
    }

    private fun writeAtomic(file: AtomicFile, text: String, label: String) {
        runCatching {
            val stream = file.startWrite()
            try {
                stream.write(text.toByteArray(Charsets.UTF_8))
                file.finishWrite(stream)
            } catch (error: Throwable) {
                file.failWrite(stream)
                throw error
            }
        }.onFailure { error ->
            AppLogStore.warn(context, "LastFmStorage", "Failed to save $label", error)
        }
    }

    companion object {
        private const val TAG = "LastFmHistoryStore"
        private const val HISTORY_FILE_NAME = "lastfm_history.json"
        private const val OUTBOX_FILE_NAME = "lastfm_scrobble_outbox.json"

        @Volatile
        private var instance: LastFmHistoryStore? = null

        fun getInstance(context: Context): LastFmHistoryStore =
            instance ?: synchronized(this) {
                instance ?: LastFmHistoryStore(context.applicationContext).also { instance = it }
            }
    }
}

private fun JSONArray.toLastFmTracks(): List<LastFmTrack> = List(length()) { index ->
    optJSONObject(index)?.let { item ->
        LastFmTrack(
            title = item.optString("title"),
            artist = item.optString("artist"),
            album = item.optString("album"),
            playedAt = item.optLong("playedAt"),
            durationMs = item.optLong("durationMs"),
            albumArtUrl = item.optString("albumArtUrl"),
            mbid = item.optString("mbid")
        )
    }
}.filterNotNull().filter { it.title.isNotBlank() && it.artist.isNotBlank() && it.playedAt > 0L }
    .distinctBy(LastFmTrack::cacheKey)
    .sortedByDescending(LastFmTrack::playedAt)

private fun List<LastFmTrack>.toHistoryJson(): JSONArray = JSONArray().apply {
    forEach { track ->
        put(
            JSONObject()
                .put("title", track.title)
                .put("artist", track.artist)
                .put("album", track.album)
                .put("playedAt", track.playedAt)
                .put("durationMs", track.durationMs)
                .put("albumArtUrl", track.albumArtUrl)
                .put("mbid", track.mbid)
        )
    }
}

private fun JSONArray.toPendingScrobbles(): List<LastFmPendingScrobble> = List(length()) { index ->
    optJSONObject(index)?.let { item ->
        LastFmPendingScrobble(
            id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
            artist = item.optString("artist"),
            track = item.optString("track"),
            album = item.optString("album"),
            durationSeconds = item.optInt("durationSeconds"),
            startedAt = item.optLong("startedAt")
        )
    }
}.filterNotNull().filter { it.artist.isNotBlank() && it.track.isNotBlank() && it.startedAt > 0L }
    .distinctBy(LastFmPendingScrobble::id)
    .sortedBy(LastFmPendingScrobble::startedAt)

private fun List<LastFmPendingScrobble>.toScrobbleJson(): JSONArray = JSONArray().apply {
    forEach { item ->
        put(
            JSONObject()
                .put("id", item.id)
                .put("artist", item.artist)
                .put("track", item.track)
                .put("album", item.album)
                .put("durationSeconds", item.durationSeconds)
                .put("startedAt", item.startedAt)
        )
    }
}
