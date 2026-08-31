package com.ella.music.plugin.source

import android.content.Context
import com.ella.music.plugin.model.PluginManifest
import com.ella.music.plugin.model.defaultValueString

class PluginConfigStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("lyrico_plugin_config", Context.MODE_PRIVATE)

    fun loadConfig(manifest: PluginManifest): Map<String, String> {
        val result = manifest.configFields.associate { field ->
            field.key to field.defaultValueString()
        }.toMutableMap()
        manifest.configFields.forEach { field ->
            val key = prefKey(manifest.id, field.key)
            if (prefs.contains(key)) {
                result[field.key] = prefs.getString(key, result[field.key]).orEmpty()
            }
        }
        return result
    }

    fun setValue(pluginId: String, key: String, value: String) {
        prefs.edit().putString(prefKey(pluginId, key), value).apply()
    }

    /** Snapshot of every stored plugin config entry, keyed by the raw "<pluginId>:<key>" pref key. */
    fun exportAll(): Map<String, String> =
        prefs.all.entries.mapNotNull { (key, value) ->
            (value as? String)?.let { key to it }
        }.toMap()

    /** Restores a backup snapshot on top of the current values; unrelated entries are kept. */
    fun restoreAll(values: Map<String, String>) {
        if (values.isEmpty()) return
        prefs.edit().apply {
            values.forEach { (key, value) -> putString(key, value) }
        }.apply()
    }

    fun deleteConfig(pluginId: String) {
        val prefix = "$pluginId:"
        val keys = prefs.all.keys.filter { it.startsWith(prefix) }
        if (keys.isEmpty()) return
        prefs.edit().apply {
            keys.forEach(::remove)
        }.apply()
    }

    private fun prefKey(pluginId: String, key: String): String = "$pluginId:$key"
}
