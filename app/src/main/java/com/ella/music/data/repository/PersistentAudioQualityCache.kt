package com.ella.music.data.repository

import com.ella.music.data.model.AudioInfo
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.*
import org.json.JSONObject

/** Metadata stamps are part of every key; unchanged files survive screen changes and restarts. */
internal class PersistentAudioQualityCache(private val file: File?) {
    private val values = ConcurrentHashMap<String, AudioInfo>()
    private val lock = Any()
    private val fileLock = Any()
    @Volatile private var loaded = false
    private var generation = 0L
    private var writer: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private fun load() {
        if (loaded) return
        synchronized(lock) {
            if (loaded) return
            runCatching {
                val source = file?.takeIf { it.exists() } ?: return@runCatching
                val root = JSONObject(source.readText())
                if (root.optInt("version") != 1) return@runCatching
                val rows = root.getJSONObject("rows")
                for (key in rows.keys()) {
                    val row = rows.getJSONObject(key)
                    values[key] = AudioInfo(row.getString("format"), row.optInt("bitRate"),
                        row.optInt("sampleRate"), row.optInt("bitDepth"), row.optInt("channels"))
                }
            }
            loaded = true
        }
    }
    fun get(key: String): AudioInfo? { load(); return values[key] }
    fun put(key: String, value: AudioInfo) {
        load()
        values[key] = value.copy(replayGainDb = null)
        changed()
    }
    fun invalidate(prefix: String) {
        load()
        values.keys.removeIf { it.startsWith(prefix) }
        changed()
    }
    private fun changed() = synchronized(lock) {
        generation++
        if (file != null && writer == null) writer = scope.launch {
            while (isActive) {
                delay(1_000)
                val version = synchronized(lock) { generation }
                flush()
                val done = synchronized(lock) {
                    if (generation == version) { writer = null; true } else false
                }
                if (done) return@launch
            }
        }
    }
    /** Atomic replacement; readers never see a partially written cache. */
    internal fun flush() {
        load()
        val target = file ?: return
        synchronized(fileLock) {
            runCatching {
                val rows = JSONObject()
                values.toMap().filterValues { it.sampleRate > 0 || it.bitDepth > 0 || it.bitRate > 0 }.forEach { (key, info) -> rows.put(key, JSONObject()
                    .put("format", info.format).put("bitRate", info.bitRate).put("sampleRate", info.sampleRate)
                    .put("bitDepth", info.bitDepth).put("channels", info.channels)) }
                target.parentFile?.mkdirs()
                val temporary = File(target.parentFile, target.name + ".tmp")
                temporary.writeText(JSONObject().put("version", 1).put("rows", rows).toString())
                Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
