package com.ella.music.data

import android.content.Context
import org.json.JSONObject

/** Persistent user preferences stored outside DataStore; caches and playback state are excluded. */
internal object AuxiliarySettingsBackup {
    private enum class Type { Boolean, Int, Long, Float }
    private data class Store(
        val key: String,
        val name: String,
        val types: Map<String, Type>,
        val syncOffsets: Boolean = false
    ) {
        fun type(key: String): Type? = types[key] ?: Type.Long.takeIf {
            syncOffsets && Regex("sync_[0-9a-f]{1,8}").matches(key)
        }
    }

    private val stores = listOf(
        Store("folder_display_settings_json", "folder_display", mapOf("size" to Type.Int, "width" to Type.Int)),
        Store("music_video_caption_settings_json", "music_video_caption_preferences", mapOf(
            "captions_enabled" to Type.Boolean, "translation_enabled" to Type.Boolean,
            "video_resize_mode" to Type.Int, "position_x" to Type.Float, "position_y" to Type.Float,
            "font_size_sp" to Type.Float, "scale" to Type.Float, "font_family" to Type.Int,
            "text_color" to Type.Int, "bold" to Type.Boolean,
            "background_color" to Type.Int, "background_alpha" to Type.Float
        ), syncOffsets = true),
        Store("app_update_settings_json", "app_update", mapOf("prereleases" to Type.Boolean)),
        Store("playback_widget_settings_json", "playback_widget", mapOf("safe_layout" to Type.Boolean))
    )

    fun schema(): Map<String, SettingsBackupValueType> = stores.associate { it.key to SettingsBackupValueType.STRING }

    fun export(context: Context): JSONObject = JSONObject().apply {
        stores.forEach { store ->
            val values = JSONObject()
            context.applicationContext.getSharedPreferences(store.name, Context.MODE_PRIVATE).all.forEach { (key, value) ->
                if (store.type(key)?.accepts(value) == true) values.put(key, value)
            }
            put(store.key, values.toString())
        }
    }

    fun restore(context: Context, payload: JSONObject) {
        stores.forEach { store ->
            val raw = payload.opt(store.key) as? String ?: return@forEach
            val values = runCatching { JSONObject(raw) }.getOrNull() ?: return@forEach
            val editor = context.applicationContext.getSharedPreferences(store.name, Context.MODE_PRIVATE).edit()
            var restored = false
            values.keys().forEach entry@ { key ->
                val type = store.type(key) ?: return@entry
                val value = values.opt(key)
                if (!type.accepts(value)) return@entry
                when (type) {
                    Type.Boolean -> editor.putBoolean(key, value as Boolean)
                    Type.Int -> editor.putInt(key, (value as Number).toInt())
                    Type.Long -> editor.putLong(key, (value as Number).toLong())
                    Type.Float -> editor.putFloat(key, (value as Number).toFloat())
                }
                restored = true
            }
            if (restored) {
                editor.apply()
                if (store.name == "folder_display") com.ella.music.ui.folder.FolderDisplayStore.refresh(context)
                if (store.name == "playback_widget") com.ella.music.player.PlaybackWidgetUpdater.updateAll(context)
            }
        }
    }

    private fun Type.accepts(value: Any?): Boolean = when (this) {
        Type.Boolean -> value is Boolean
        Type.Int -> SettingsBackupValueType.INT.accepts(value)
        Type.Long -> value is Number && runCatching {
            java.math.BigDecimal(value.toString()).longValueExact()
        }.isSuccess
        Type.Float -> value is Number && value.toFloat().isFinite()
    }
}
