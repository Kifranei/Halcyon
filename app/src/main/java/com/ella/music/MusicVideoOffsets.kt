package com.ella.music

import android.content.Context
import android.net.Uri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import kotlin.math.roundToLong

/** LunaBeat MV delay data, normalized to milliseconds. */
internal data class MusicVideoOffsets(private val valuesMs: Map<String, Long>) {
    fun forSource(source: Uri, fallbackFileNames: Iterable<String> = emptyList()): Long {
        return forFileNames(
            buildList {
                add(source.path.orEmpty())
                add(source.lastPathSegment.orEmpty())
                add(source.toString())
                addAll(fallbackFileNames)
            }
        )
    }

    fun forFileName(fileName: String): Long {
        return offsetKeyAliases(fileName).firstNotNullOfOrNull(valuesMs::get) ?: 0L
    }

    fun forFileNames(fileNames: Iterable<String>): Long {
        fileNames.forEach { fileName ->
            offsetKeyAliases(fileName).firstNotNullOfOrNull(valuesMs::get)?.let { return it }
        }
        return 0L
    }
}

internal object MusicVideoOffsetsParser {
    fun parse(json: String): MusicVideoOffsets {
        val document = Json.parseToJsonElement(json)
        val root = document as? JsonObject
        val defaultUnit = root?.get("unit")?.primitiveText().orEmpty()
        val entries = root?.get("offsets")
            ?: root?.get("data")
            ?: root?.get("items")
            ?: document
        val values = buildMap {
            parseOffsetEntries(entries, defaultUnit).forEach { (name, valueMs) ->
                offsetKeyAliases(name).forEach { alias ->
                    putIfAbsent(alias, valueMs)
                }
            }
        }
        require(values.isNotEmpty()) { "No usable MV offsets were found" }
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
        return text
            ?.let { runCatching { parse(it) }.getOrNull() }
            ?: MusicVideoOffsets(emptyMap())
    }

    private fun parseOffsetEntries(element: JsonElement, defaultUnit: String): List<Pair<String, Long>> =
        when (element) {
            is JsonArray -> element.mapNotNull { item ->
                val entry = item as? JsonObject ?: return@mapNotNull null
                val name = listOf("fileName", "filename", "file", "path", "uri", "video", "name")
                    .firstNotNullOfOrNull { key -> entry[key].primitiveText()?.takeIf(String::isNotBlank) }
                    ?: return@mapNotNull null
                parseOffsetValue(entry, defaultUnit)?.let { name to it }
            }
            is JsonObject -> element.mapNotNull { (name, value) ->
                if (name in METADATA_KEYS) return@mapNotNull null
                parseOffsetValue(value, defaultUnit)?.let { name to it }
            }
            else -> emptyList()
        }

    private fun parseOffsetValue(element: JsonElement, defaultUnit: String): Long? {
        if (element is JsonObject) {
            listOf("offsetMs", "delayMs", "milliseconds", "ms").forEach { key ->
                element[key].finiteNumber()?.let { return it.roundToLong() }
            }
            listOf("offsetSeconds", "delaySeconds", "seconds").forEach { key ->
                element[key].finiteNumber()?.let { return (it * 1_000.0).roundToLong() }
            }
            listOf("offset", "delay", "value").forEach { key ->
                element[key].finiteNumber()?.let { return convertToMilliseconds(it, defaultUnit) }
            }
            return null
        }
        return element.finiteNumber()?.let { convertToMilliseconds(it, defaultUnit) }
    }

    private fun convertToMilliseconds(value: Double, unit: String): Long {
        val multiplier = when (unit.trim().lowercase(Locale.ROOT)) {
            "ms", "millisecond", "milliseconds" -> 1.0
            else -> 1_000.0
        }
        return (value * multiplier).roundToLong()
    }

    private fun JsonElement?.finiteNumber(): Double? =
        primitiveText()?.toDoubleOrNull()?.takeIf(Double::isFinite)

    private fun JsonElement?.primitiveText(): String? =
        runCatching { this?.jsonPrimitive?.content }.getOrNull()

    private val METADATA_KEYS = setOf("version", "unit", "format", "createdAt", "updatedAt")
}

/** Converts the audio clock to the MV clock. A positive delay keeps the MV at frame zero first. */
internal fun musicVideoSyncPositionMs(audioPositionMs: Long, mvDelayMs: Long): Long =
    audioPositionMs - mvDelayMs

private fun offsetKeyAliases(rawName: String): List<String> {
    val decoded = runCatching {
        URLDecoder.decode(rawName.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrDefault(rawName).substringBefore('?').substringBefore('#')
    val fileName = decoded
        .replace('\\', '/')
        .substringAfterLast('/')
        .substringAfterLast(':')
        .trim()
        .lowercase(Locale.ROOT)
    if (fileName.isBlank()) return emptyList()
    val stem = fileName.substringBeforeLast('.', fileName)
    val withoutMvSuffix = stem.replace(Regex("""(?:[ _\-–—]+mv)$""", RegexOption.IGNORE_CASE), "")
    return listOf(fileName, stem, withoutMvSuffix).filter(String::isNotBlank).distinct()
}
