package com.ella.music.ui.listmodel

import android.icu.text.Transliterator
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.ui.components.toFastIndexSortableKey
import java.io.File
import org.json.JSONObject

internal object MusicSortKeyNormalizer {
    fun normalize(text: String): String {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return ""
        if (trimmed.isAsciiSortable()) return trimmed.toFastIndexSortableKey()

        MusicSortKeyCache[trimmed]?.let { return it }

        val latin = MusicSortTransliterator.transliterate(trimmed)
        return latin.toFastIndexSortableKey().also { MusicSortKeyCache[trimmed] = it }
    }
}

internal class FastIndexKeyCache(private val maxSize: Int) {
    private val lock = Any()
    private val values = object : LinkedHashMap<String, String>(maxSize, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > maxSize
        }
    }

    operator fun get(key: String): String? = synchronized(lock) {
        values[key]
    }

    operator fun set(key: String, value: String) {
        synchronized(lock) {
            values[key] = value
        }
    }

    fun snapshot(): Map<String, String> = synchronized(lock) {
        values.toMap()
    }

    fun putAll(entries: Map<String, String>) {
        synchronized(lock) {
            entries.forEach { (key, value) -> values[key] = value }
        }
    }
}

internal object FastIndexSectionResolver {
    fun sectionFor(sortKey: String): String =
        sortKey.toFastIndexSection()

    fun sectionForText(text: String): String =
        MusicSortKeyNormalizer.normalize(text).toFastIndexSection()
}

/**
 * Caches the expensive ICU transliteration used to build A-Z sort keys for non-ASCII text.
 * Persisted to disk so large CJK libraries do not rebuild sort keys on every cold launch.
 */
internal object MusicSortKeyCache {
    private const val MaxSize = 8192
    private val lock = Any()
    private val values = FastIndexKeyCache(MaxSize)

    @Volatile private var file: File? = null
    private var loaded = false
    private var dirty = false

    fun configure(storeFile: File) {
        file = storeFile
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        val f = file ?: return
        if (!f.exists()) return
        runCatching {
            val obj = JSONObject(f.readText())
            val loadedValues = buildMap {
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    put(key, obj.optString(key))
                }
            }
            values.putAll(loadedValues)
        }
    }

    operator fun get(key: String): String? = synchronized(lock) {
        ensureLoadedLocked()
        values[key]
    }

    operator fun set(key: String, value: String) {
        synchronized(lock) {
            ensureLoadedLocked()
            if (values[key] != value) {
                values[key] = value
                dirty = true
            }
        }
    }

    fun persist() {
        val f = file ?: return
        synchronized(lock) {
            if (!dirty) return
            runCatching {
                val obj = JSONObject()
                values.snapshot().forEach { (key, value) -> obj.put(key, value) }
                f.writeText(obj.toString())
                dirty = false
            }
        }
    }
}

private fun String.isAsciiSortable(): Boolean =
    all { it.code in 0x20..0x7E }

private object MusicSortTransliterator {
    private const val Rules = "Any-Latin; Latin-ASCII; NFD; [:Nonspacing Mark:] Remove; NFC"

    private val value: Transliterator? by lazy {
        runCatching { Transliterator.getInstance(Rules) }.getOrNull()
    }

    @Synchronized
    fun transliterate(text: String): String =
        runCatching { value?.transliterate(text) ?: text }.getOrDefault(text)
}
