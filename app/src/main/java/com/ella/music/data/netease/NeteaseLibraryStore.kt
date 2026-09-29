package com.ella.music.data.netease

import android.content.Context
import android.util.AtomicFile
import com.ella.music.data.model.*
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import com.ella.music.data.model.AudioInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Account-scoped online snapshots; local playlists are never overwritten. */
class NeteaseLibraryStore private constructor(private val context: Context) {
    private val accounts = NeteaseAccountStore.getInstance(context)
    private val client = CatClawNeteaseClient(context)
    private val lock = Mutex()
    private val mutablePlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    private val mutableFavorites = MutableStateFlow<List<Song>>(emptyList())
    private val mutableStatus = MutableStateFlow("")
    private val mutableStreamInfo = MutableStateFlow<Map<String, NeteaseStreamInfo>>(emptyMap())
    private val mutableRecentPlays = MutableStateFlow<List<NeteaseRecentPlay>>(emptyList())
    /** Cloud listening history of the signed-in account; empty when signed out. */
    internal val recentPlays = mutableRecentPlays.asStateFlow()
    @Volatile private var recentPlaysFetchedAt = 0L
    @Volatile private var recentPlaysUserId = 0L
    /** Served quality per song id, updated whenever the player resolves a stream. */
    internal val streamInfo = mutableStreamInfo.asStateFlow()
    val playlists = mutablePlaylists.asStateFlow()
    val favorites = mutableFavorites.asStateFlow()
    val status = mutableStatus.asStateFlow()
    private var snapshotUserId = 0L
    private var loaded = false
    private var metadataNeedsRefresh = false
    private var favoritePlaylistId = ""

    suspend fun refresh(force: Boolean): List<Song> = withContext(Dispatchers.IO) {
        lock.withLock {
            val account = accounts.account.value
            if (!account.loggedIn) { clearMemory(); return@withLock emptyList() }
            if (snapshotUserId != account.userId) {
                clearMemory(); snapshotUserId = account.userId
                readCache(account.userId)?.let(::apply)
            }
            if (!force && loaded && !metadataNeedsRefresh) return@withLock mutableFavorites.value
            try {
                val result = client.collections(account)
                if (accounts.account.value != account) return@withLock emptyList()
                val json = JSONObject().put("metadataVersion", 1).put("userId", account.userId)
                    .put("favoritePlaylistId", result.favoritePlaylistId)
                    .put("favorites", JSONArray().apply { result.favorites.forEach { put(it.toPlaylistSong().toJson()) } })
                    .put("playlists", JSONArray().apply { result.playlists.forEach { put(it.toJson()) } })
                val cache = cache(account.userId)
                val stream = cache.startWrite()
                try { stream.write(json.toString().toByteArray()); cache.finishWrite(stream) }
                catch (e: Exception) { cache.failWrite(stream); throw e }
                apply(result)
                metadataNeedsRefresh = false
                mutableStatus.value = ""
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                // Do not replace a usable snapshot with a failed/partial response.
                mutableStatus.value = if (error is NeteaseApiException && error.code in setOf(301, 302))
                    context.getString(com.ella.music.R.string.netease_session_expired)
                else context.getString(com.ella.music.R.string.netease_sync_failed)
                if (!loaded) throw error
            }
            mutableFavorites.value
        }
    }
    suspend fun login(cookie: String, userId: Long = 0) = withContext(Dispatchers.IO) {
        val account = client.login(cookie, userId)
        lock.withLock { accounts.save(account); clearMemory() }
    }
    suspend fun logout() = withContext(Dispatchers.IO) {
        lock.withLock {
            val userId = accounts.account.value.userId
            accounts.clear(); clearMemory()
            if (userId > 0) {
                cache(userId).delete()
                File(context.filesDir, "remote_library_netease_$userId.json").delete()
            }
        }
    }
    suspend fun streamUrl(songId: String): String {
        val account = accounts.account.value
        if (!account.loggedIn) throw IOException(context.getString(com.ella.music.R.string.netease_session_expired))
        val resolved = client.resolveStream(songId, account.cookie, com.ella.music.data.SettingsManager.getInstance(context).neteaseQuality.first())
        mutableStreamInfo.update { current ->
            val next = current + (songId to resolved)
            if (next.size > 64) next.entries.drop(next.size - 64).associate { it.key to it.value } else next
        }
        rememberServedQuality(songId, resolved)
        return resolved.url
    }
    fun audioInfoFor(songId: String): AudioInfo? =
        (mutableStreamInfo.value[songId] ?: servedQualityFromCache(songId))?.let(::neteaseStreamAudioInfo)

    /**
     * A cached stream is replayed without resolving a URL, so the served tier is remembered per
     * cache key; otherwise the quality badge would fall back to a generic label after a restart.
     */
    private val servedQualityPrefs by lazy { context.getSharedPreferences("netease_served_quality", Context.MODE_PRIVATE) }

    private fun rememberServedQuality(songId: String, info: NeteaseStreamInfo) {
        servedQualityPrefs.edit()
            .putString(NeteaseStreamCache.cacheKey(songId), listOf(info.level, info.type, info.bitRate, info.sampleRate).joinToString("|"))
            .apply()
    }

    private fun servedQualityFromCache(songId: String): NeteaseStreamInfo? {
        val raw = servedQualityPrefs.getString(NeteaseStreamCache.cacheKey(songId), null) ?: return null
        val parts = raw.split('|')
        if (parts.size < 4) return null
        return NeteaseStreamInfo("", parts[0], parts[1], parts[2].toIntOrNull() ?: 0, parts[3].toIntOrNull() ?: 0)
            .also { info -> mutableStreamInfo.update { it + (songId to info) } }
    }

    fun clearServedQuality() { servedQualityPrefs.edit().clear().apply() }
    suspend fun toggleFavorite(song: Song) {
        if (song.onlineSource != NETEASE_SOURCE) return
        refresh(false)
        val account = accounts.account.value
        check(account.loggedIn)
        val liked = favoritePlaylistId.takeIf { it.isNotBlank() }
            ?: throw IOException("NetEase favorites playlist unavailable")
        val next = favorites.value.none { it.onlineId == song.onlineId }
        client.setFavorite(liked, song.onlineId, next, account.cookie)
        refresh(true)
    }
    suspend fun lyrics(song: Song): Song {
        val account = accounts.account.value
        if (!account.loggedIn) return song
        return try {
            val lyrics = client.lyrics(song.onlineId, account.cookie)
            song.copy(onlineLyrics = lyrics.original, onlineLyricTranslation = lyrics.translation, onlineLyricPronunciation = lyrics.pronunciation)
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { song }
    }
    private fun apply(value: NeteaseCollections) {
        favoritePlaylistId = value.favoritePlaylistId
        mutableFavorites.value = value.favorites
        mutablePlaylists.value = value.playlists
        loaded = true
    }
    /** Refreshes cloud history at most once per [RECENT_PLAYS_TTL_MS] unless [force]. Failures keep the last list. */
    suspend fun refreshRecentPlays(force: Boolean = false) = withContext(Dispatchers.IO) {
        val account = accounts.account.value
        if (!account.loggedIn) {
            mutableRecentPlays.value = emptyList(); recentPlaysUserId = 0L; recentPlaysFetchedAt = 0L
            return@withContext
        }
        val now = System.currentTimeMillis()
        if (!force && recentPlaysUserId == account.userId && now - recentPlaysFetchedAt < RECENT_PLAYS_TTL_MS) return@withContext
        runCatching { client.recentPlays(account.cookie) }.onSuccess { plays ->
            mutableRecentPlays.value = plays
            recentPlaysUserId = account.userId
            recentPlaysFetchedAt = now
        }
    }

    private fun clearMemory() {
        mutableRecentPlays.value = emptyList(); recentPlaysUserId = 0L; recentPlaysFetchedAt = 0L
        mutablePlaylists.value = emptyList(); mutableFavorites.value = emptyList()
        mutableStatus.value = ""; loaded = false; snapshotUserId = 0L; favoritePlaylistId = ""
    }
    private fun cache(id: Long) = AtomicFile(File(context.filesDir, "netease_collections_$id.json"))
    private fun readCache(id: Long): NeteaseCollections? = runCatching {
        val json = JSONObject(cache(id).readFully().toString(Charsets.UTF_8))
        require(json.getLong("userId") == id)
        metadataNeedsRefresh = json.optInt("metadataVersion") < 1
        val playlists = json.getJSONArray("playlists")
        val favorites = json.getJSONArray("favorites")
        NeteaseCollections(List(favorites.length()) { favorites.getJSONObject(it).toPlaylistSong().toSong() },
            List(playlists.length()) { playlists.getJSONObject(it).toUserPlaylist() }, json.optString("favoritePlaylistId"))
    }.getOrNull()
    companion object {
        private const val RECENT_PLAYS_TTL_MS = 90_000L
        @Volatile private var instance: NeteaseLibraryStore? = null
        fun getInstance(context: Context): NeteaseLibraryStore = instance ?: synchronized(this) {
            instance ?: NeteaseLibraryStore(context.applicationContext).also { instance = it }
        }
    }
}
