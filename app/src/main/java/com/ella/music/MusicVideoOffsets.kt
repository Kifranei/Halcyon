package com.ella.music

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.Locale

/** LunaBeat MV offset sidecar. Values are lyric-time corrections in seconds. */
internal data class MusicVideoOffsets(private val valuesMs: Map<String, Long>) {
    fun forSource(source: Uri): Long {
        return forFileName(source.path.orEmpty())
    }

    fun forFileName(fileName: String): Long {
        val key = fileName.substringAfterLast('/').lowercase(Locale.ROOT)
        return valuesMs[key] ?: 0L
    }
}

internal object MusicVideoOffsetsParser {
    fun parse(json: String): MusicVideoOffsets {
        val root = Json.parseToJsonElement(json).jsonObject
        val offsets = root["offsets"]?.jsonObject.orEmpty()
        val values = buildMap {
            offsets.forEach { (name, value) ->
                val seconds = value.jsonPrimitive.content.toDoubleOrNull()
                if (seconds != null && seconds.isFinite()) {
                    put(name.substringAfterLast('/').lowercase(Locale.ROOT), (seconds * 1_000.0).toLong())
                }
            }
        }
        return MusicVideoOffsets(values)
    }

    fun loadForSource(context: Context, source: Uri, importedJson: String = ""): MusicVideoOffsets {
        if (importedJson.isNotBlank()) return runCatching { parse(importedJson) }.getOrDefault(MusicVideoOffsets(emptyMap()))
        val text = runCatching {
            when (source.scheme?.lowercase(Locale.ROOT)) {
                "file" -> File(source.path.orEmpty()).parentFile
                    ?.resolve("mv_offsets.json")
                    ?.takeIf(File::isFile)
                    ?.readText()
                else -> null
            }
        }.getOrNull()
        return text?.let(::parse) ?: MusicVideoOffsets(emptyMap())
    }
}
