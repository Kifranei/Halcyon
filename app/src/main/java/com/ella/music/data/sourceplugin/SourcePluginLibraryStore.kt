package com.ella.music.data.sourceplugin

import android.content.Context
import android.util.AtomicFile
import com.ella.music.R
import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.toPlaylistSong
import com.ella.music.data.model.toSong
import com.ella.music.data.model.toJson
import com.ella.music.data.model.toUserPlaylist
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Per-plugin library snapshot: favorites + account playlists synced through the plugin, cached
 * in an AtomicFile like NeteaseLibraryStore does. Also owns served-quality memory, cloud recent
 * plays and the favorite toggle for songs of this source.
 */
class SourcePluginLibraryStore internal constructor(
    private val context: Context,
    internal val config: SourcePluginConfig,
    private val accountStore: SourcePluginAccountStore,
    private val client: SourcePluginClient
) {
    private val lock = Mutex()
    private val itemStore = SourcePluginItemStore(context, config.id)
    private val mutablePlaylists = MutableStateFlow<List<UserPlaylist>>(emptyList())
    private val mutableFavorites = MutableStateFlow<List<Song>>(emptyList())
    private val mutableStatus = MutableStateFlow("")
    private val mutableStreamInfo = MutableStateFlow<Map<String, SourcePluginStreamInfo>>(emptyMap())
    private val mutableRecentPlays = MutableStateFlow<List<SourcePluginRecentPlay>>(emptyList())
    val playlists = mutablePlaylists.asStateFlow()
    val favorites = mutableFavorites.asStateFlow()
    val status = mutableStatus.asStateFlow()
    internal val streamInfo = mutableStreamInfo.asStateFlow()
    internal val recentPlays = mutableRecentPlays.asStateFlow()
    @Volatile private var recentPlaysFetchedAt = 0L
    @Volatile private var recentPlaysUserKey = ""
    private var snapshotUserKey = ""
    private var loaded = false
    private var favoritePlaylistId = ""

    val pluginId: String get() = config.id

    private fun account(): SourcePluginAccount? = accountStore.account(config.id)
    private fun loginRequired(): Boolean = "none" !in config.methods
    private fun signedIn(): Boolean = !loginRequired() || account() != null
    private fun userKey(): String = account()?.userId?.ifBlank { "anonymous" } ?: "anonymous"

    suspend fun refresh(force: Boolean): List<Song> = withContext(Dispatchers.IO) {
        lock.withLock {
            if (!signedIn()) { clearMemory(); return@withLock emptyList() }
            val key = userKey()
            if (snapshotUserKey != key) {
                clearMemory(); snapshotUserKey = key
                readCache(key)?.let(::apply)
            }
            if (!force && loaded) return@withLock mutableFavorites.value
            try {
                val result = client.collections(config)
                val extras = buildList {
                    if (config.hasCapability("daily")) {
                        runCatching { client.getDailySongs(config) }.getOrNull()
                            ?.takeIf { it.isNotEmpty() }?.let { songs ->
                                add(
                                    UserPlaylist(
                                        id = "remote:sourceplugin:${config.id}:${key}:daily_songs",
                                        name = context.getString(R.string.source_plugin_daily_recommend),
                                        songs = songs.map { it.toPlaylistSong() },
                                        createdAt = System.currentTimeMillis(),
                                        updatedAt = System.currentTimeMillis(),
                                        remoteSource = sourcePluginSourceKey(config.id),
                                        remoteServerId = key,
                                        remotePlaylistId = "daily_songs"
                                    )
                                )
                            }
                        runCatching { client.getDailyPlaylists(config) }.getOrNull()?.let(::addAll)
                    }
                    if (config.hasCapability("radio")) {
                        val seed = mutableRecentPlays.value.firstOrNull()?.song
                            ?: result.favorites.firstOrNull()
                        if (seed != null) {
                            runCatching { client.getRadio(config, seed) }.getOrNull()
                                ?.takeIf { it.isNotEmpty() }?.let { songs ->
                                    add(
                                        UserPlaylist(
                                            id = "remote:sourceplugin:${config.id}:${key}:radio",
                                            name = context.getString(R.string.source_plugin_radio),
                                            songs = songs.map { it.toPlaylistSong() },
                                            createdAt = System.currentTimeMillis(),
                                            updatedAt = System.currentTimeMillis(),
                                            remoteSource = sourcePluginSourceKey(config.id),
                                            remoteServerId = key,
                                            remotePlaylistId = "radio"
                                        )
                                    )
                                }
                        }
                    }
                }
                val merged = result.copy(playlists = result.playlists + extras)
                client.persistItems(config, result.items)
                val json = JSONObject().put("userKey", key)
                    .put("favoritePlaylistId", result.favoritePlaylistId)
                    .put("favorites", JSONArray().apply { result.favorites.forEach { put(it.toPlaylistSong().toJson()) } })
                    .put("playlists", JSONArray().apply { merged.playlists.forEach { put(it.toJson()) } })
                val cache = cache(key)
                val stream = cache.startWrite()
                try { stream.write(json.toString().toByteArray()); cache.finishWrite(stream) }
                catch (e: Exception) { cache.failWrite(stream); throw e }
                apply(merged)
                mutableStatus.value = ""
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                mutableStatus.value = context.getString(R.string.source_plugin_sync_failed)
                if (!loaded) throw error
            }
            mutableFavorites.value
        }
    }

    suspend fun login(cookie: String) = withContext(Dispatchers.IO) {
        client.login(config, cookie)
        lock.withLock { clearMemory() }
    }

    suspend fun loginQr(result: JSONObject) = withContext(Dispatchers.IO) {
        client.saveQrLogin(config, result)
        lock.withLock { clearMemory() }
    }

    suspend fun logout() = withContext(Dispatchers.IO) {
        lock.withLock {
            client.logout(config)
            clearMemory()
            cacheFiles().forEach { it.delete() }
            itemStore.clear()
            mutableStreamInfo.value = emptyMap()
        }
    }

    /** Resolves playback through the plugin and remembers the served tier for the quality badge. */
    suspend fun resolveStream(song: Song): SourcePluginStreamInfo {
        val resolved = client.getMediaSource(config, song)
        mutableStreamInfo.update { current ->
            val next = current + (song.onlineId to resolved)
            if (next.size > 64) next.entries.drop(next.size - 64).associate { it.key to it.value } else next
        }
        rememberServedQuality(song.onlineId, resolved)
        return resolved
    }

    fun audioInfoFor(songId: String): AudioInfo? =
        (mutableStreamInfo.value[songId] ?: servedQualityFromCache(songId))
            ?.let { info ->
                val rawType = info.qualityLabel.ifBlank { info.mimeType }
                val type = rawType.substringAfterLast('/').substringBefore(';').trim().lowercase()
                AudioInfo(
                    format = type.uppercase().ifBlank { info.qualityLabel.ifBlank { "Audio" } },
                    bitRate = info.bitRate.coerceAtLeast(0),
                    sampleRate = info.sampleRate.coerceAtLeast(0),
                    bitDepth = if (type in setOf("flac", "alac", "wav")) 16 else 0,
                    channels = if (type.isNotBlank()) 2 else 0
                )
            }

    private val servedQualityPrefs by lazy { context.getSharedPreferences("source_plugin_served_quality", Context.MODE_PRIVATE) }

    private fun rememberServedQuality(songId: String, info: SourcePluginStreamInfo) {
        servedQualityPrefs.edit()
            .putString("${config.id}:$songId", listOf(info.qualityLabel, info.mimeType, info.bitRate, info.sampleRate).joinToString("|"))
            .apply()
    }

    private fun servedQualityFromCache(songId: String): SourcePluginStreamInfo? {
        val raw = servedQualityPrefs.getString("${config.id}:$songId", null) ?: return null
        val parts = raw.split("|")
        return SourcePluginStreamInfo(
            url = "",
            qualityLabel = parts.getOrElse(0) { "" },
            mimeType = parts.getOrElse(1) { "" },
            bitRate = parts.getOrElse(2) { "0" }.toIntOrNull() ?: 0,
            sampleRate = parts.getOrElse(3) { "0" }.toIntOrNull() ?: 0
        ).also { info -> mutableStreamInfo.update { it + (songId to info) } }
    }

    fun clearServedQuality() {
        servedQualityPrefs.edit().apply {
            servedQualityPrefs.all.keys.filter { it.startsWith("${config.id}:") }.forEach { remove(it) }
        }.apply()
    }

    suspend fun toggleFavorite(song: Song) {
        if (song.onlineSource != sourcePluginSourceKey(config.id)) return
        if (!config.hasCapability("setFavorite")) return
        refresh(false)
        val next = favorites.value.none { it.onlineId == song.onlineId }
        client.setFavorite(config, song.onlineId, next)
        refresh(true)
    }

    suspend fun lyrics(song: Song): Song {
        return try {
            client.getLyric(config, song)
        } catch (cancel: CancellationException) { throw cancel } catch (_: Exception) { song }
    }

    suspend fun itemJson(song: Song): String = client.itemJson(config, song)

    suspend fun refreshRecentPlays(force: Boolean = false) = withContext(Dispatchers.IO) {
        if (!config.hasCapability("recentPlays") || !signedIn()) {
            mutableRecentPlays.value = emptyList(); recentPlaysUserKey = ""; recentPlaysFetchedAt = 0L
            return@withContext
        }
        val key = userKey()
        val now = System.currentTimeMillis()
        if (!force && recentPlaysUserKey == key && now - recentPlaysFetchedAt < RECENT_PLAYS_TTL_MS) return@withContext
        runCatching { client.getRecentPlays(config) }.onSuccess { plays ->
            mutableRecentPlays.value = plays
            recentPlaysUserKey = key
            recentPlaysFetchedAt = now
        }
    }

    private fun apply(value: SourcePluginCollections) {
        favoritePlaylistId = value.favoritePlaylistId
        mutableFavorites.value = value.favorites
        mutablePlaylists.value = value.playlists
        loaded = true
    }

    private fun clearMemory() {
        mutableRecentPlays.value = emptyList(); recentPlaysUserKey = ""; recentPlaysFetchedAt = 0L
        mutablePlaylists.value = emptyList(); mutableFavorites.value = emptyList()
        mutableStatus.value = ""; loaded = false; snapshotUserKey = ""; favoritePlaylistId = ""
    }

    private fun cache(userKey: String) =
        AtomicFile(File(context.filesDir, "source_plugin_collections_${config.id}_$userKey.json"))
    private fun cacheFiles(): List<File> =
        context.filesDir.listFiles { f -> f.name.startsWith("source_plugin_collections_${config.id}") }.orEmpty().toList()

    private fun readCache(userKey: String): SourcePluginCollections? = runCatching {
        val json = JSONObject(cache(userKey).readFully().toString(Charsets.UTF_8))
        require(json.getString("userKey") == userKey)
        val playlists = json.getJSONArray("playlists")
        val favorites = json.getJSONArray("favorites")
        SourcePluginCollections(
            List(favorites.length()) { favorites.getJSONObject(it).toPlaylistSong().toSong() },
            List(playlists.length()) { playlists.getJSONObject(it).toUserPlaylist() },
            json.optString("favoritePlaylistId"),
            emptyMap()
        )
    }.getOrNull()

    companion object {
        private const val RECENT_PLAYS_TTL_MS = 90_000L
    }
}
