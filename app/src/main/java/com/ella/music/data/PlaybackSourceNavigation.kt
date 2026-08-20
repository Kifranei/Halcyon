package com.ella.music.data

import android.content.Context
import com.ella.music.data.model.playlistIdentityKey
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject

/** Bridges queue surfaces that live inside the resident player to the app navigation host. */
internal object PlaybackSourceNavigation {
    private const val PREFS = "ella_playback_source"
    private const val KEY_QUEUE_SOURCE = "queue_source"
    private const val KEY_SONG_SOURCES = "song_sources"
    private const val KEY_ACTIVE_SCREEN = "active_screen"

    private val _sourceKey = MutableStateFlow<String?>(null)
    val sourceKey = _sourceKey.asStateFlow()

    private val _requests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requests = _requests.asSharedFlow()

    private val songSources = linkedMapOf<String, String>()
    private var activeScreenKey: String? = null
    private var prefsReady = false
    private var appContext: Context? = null

    fun attach(context: Context) {
        if (prefsReady) return
        appContext = context.applicationContext
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        _sourceKey.value = prefs.getString(KEY_QUEUE_SOURCE, null)
        activeScreenKey = prefs.getString(KEY_ACTIVE_SCREEN, null)
        prefs.getString(KEY_SONG_SOURCES, null)
            ?.takeIf { it.isNotBlank() }
            ?.let { raw ->
                runCatching {
                    val json = JSONObject(raw)
                    json.keys().forEach { key ->
                        json.optString(key).takeIf { it.isNotBlank() }?.let { songSources[key] = it }
                    }
                }
            }
        prefsReady = true
    }

    fun updateSource(key: String?) {
        _sourceKey.value = key
        persist()
    }

    fun setActiveScreen(key: String?) {
        activeScreenKey = key?.takeIf { it.isNotBlank() }
        persist()
    }

    fun clearActiveScreen(key: String?) {
        if (activeScreenKey == key) {
            activeScreenKey = null
            persist()
        }
    }

    fun activeScreen(): String? = activeScreenKey

    fun recordSongSource(songKey: String, sourceKey: String?) {
        val resolved = sourceKey?.takeIf { it.isNotBlank() } ?: activeScreenKey ?: return
        if (songKey.isBlank()) return
        songSources[songKey] = resolved
        persist()
    }

    fun recordSongSources(sources: Map<String, String>) {
        if (sources.isEmpty()) return
        sources.forEach { (songKey, sourceKey) ->
            if (songKey.isBlank() || sourceKey.isBlank()) return@forEach
            songSources[songKey] = sourceKey
        }
        persist()
    }

    fun sourceForSong(songKey: String): String? =
        songSources[songKey] ?: _sourceKey.value

    fun request() {
        if (resolvedSourceKey() != null) _requests.tryEmit(Unit)
    }

    fun resolvedSourceKey(songKey: String? = null): String? {
        if (!songKey.isNullOrBlank()) {
            songSources[songKey]?.let { return it }
        }
        return _sourceKey.value
    }

    private fun persist() {
        val context = appContext ?: return
        val json = JSONObject()
        songSources.entries.toList().takeLast(400).forEach { (key, value) ->
            json.put(key, value)
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_QUEUE_SOURCE, _sourceKey.value)
            .putString(KEY_ACTIVE_SCREEN, activeScreenKey)
            .putString(KEY_SONG_SOURCES, json.toString())
            .apply()
    }
}

internal fun playbackSourcesForSongs(
    groups: Iterable<Pair<String, Iterable<com.ella.music.data.model.Song>>>
): Map<String, String> {
    val result = linkedMapOf<String, String>()
    groups.forEach { (source, songs) ->
        if (source.isBlank()) return@forEach
        songs.forEach { song ->
            val key = song.playlistIdentityKey()
            if (key.isNotBlank()) result.putIfAbsent(key, source)
        }
    }
    return result
}

internal fun shouldUseHomeSourceForRecentSong(
    queueSongKeys: Collection<String>,
    recentSongKeys: Collection<String>,
    clickedSongKey: String
): Boolean {
    if (clickedSongKey.isBlank() || clickedSongKey in queueSongKeys) return false
    return queueSongKeys.isEmpty() || recentSongKeys.none { it in queueSongKeys }
}
