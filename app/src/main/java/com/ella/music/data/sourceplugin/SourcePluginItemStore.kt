package com.ella.music.data.sourceplugin

import android.content.Context
import android.util.AtomicFile
import java.io.File
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject

/**
 * Persists verbatim plugin musicItem JSON keyed by onlineId so playback, lyrics and comments
 * can hand the original item back to the plugin (same role rawJson plays in MusicFree search
 * results, except account-source songs live in the library long-term).
 */
internal class SourcePluginItemStore(context: Context, private val pluginId: String) {
    private val file = AtomicFile(File(context.filesDir, "source_plugin_items_$pluginId.json"))
    private val lock = Mutex()
    @Volatile private var cache: LinkedHashMap<String, String>? = null

    suspend fun item(onlineId: String): String? = lock.withLock {
        load()[onlineId]
    }

    suspend fun putAll(items: Map<String, String>) = lock.withLock {
        if (items.isEmpty()) return@withLock
        val map = load()
        items.forEach { (k, v) -> if (k.isNotBlank()) map[k] = v }
        // Bound growth: drop the oldest half once past the cap (search-result churn stays small).
        while (map.size > MAX_ITEMS) {
            val first = map.entries.firstOrNull()?.key ?: break
            map.remove(first)
        }
        persist(map)
    }

    suspend fun clear() = lock.withLock {
        cache = LinkedHashMap()
        file.delete()
    }

    private fun load(): LinkedHashMap<String, String> {
        cache?.let { return it }
        val map = LinkedHashMap<String, String>()
        runCatching {
            val json = JSONObject(file.readFully().toString(Charsets.UTF_8))
            json.keys().forEach { key -> map[key] = json.optString(key) }
        }
        cache = map
        return map
    }

    private fun persist(map: LinkedHashMap<String, String>) {
        val json = JSONObject()
        map.forEach { (k, v) -> json.put(k, v) }
        val stream = file.startWrite()
        try {
            stream.write(json.toString().toByteArray())
            file.finishWrite(stream)
        } catch (e: Exception) {
            file.failWrite(stream)
        }
    }

    private companion object {
        const val MAX_ITEMS = 4_000
    }
}
