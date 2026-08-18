package com.ella.music.plugin.source

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.parser.LrcParser
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

class PluginJsonParser(
    private val json: Json
) {
    fun parseSongResults(rawJson: String, pluginId: String, pluginName: String): List<PluginSongSearchResult> =
        parseSongResultItems(rawJson, pluginId, pluginName, requireId = true)

    fun parseCoverResults(
        rawJson: String,
        pluginId: String,
        pluginName: String,
        enforceApi4Contract: Boolean = false
    ): List<PluginSongSearchResult> = parseSongResultItems(
        rawJson = rawJson,
        pluginId = pluginId,
        pluginName = pluginName,
        requireId = false,
        enforceCoverJudgmentMetadata = enforceApi4Contract
    )

    private fun parseSongResultItems(
        rawJson: String,
        pluginId: String,
        pluginName: String,
        requireId: Boolean,
        enforceCoverJudgmentMetadata: Boolean = false
    ): List<PluginSongSearchResult> {
        val root = json.parseToJsonElement(rawJson)
        val items = when (root) {
            is JsonArray -> root
            is JsonObject -> root.array("items", "results", "songs", "data") ?: JsonArray(emptyList())
            else -> JsonArray(emptyList())
        }
        return items.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject ?: return@mapIndexedNotNull null
            val coverUrl = obj.string("picUrl", "coverUrl", "cover_url", "artworkUrl").orEmpty()
            val id = obj.string("id", "songId", "trackId")
                ?: if (requireId) return@mapIndexedNotNull null else coverUrl.ifBlank {
                    "$pluginId:cover:$index"
                }
            val title = obj.string("title", "name", "songName").orEmpty()
            val artist = obj.string("artist", "artists", "singer").orEmpty()
            val album = obj.string("album", "albumName").orEmpty()
            val date = obj.string("year", "date", "releaseDate", "release_date").orEmpty()
            if (enforceCoverJudgmentMetadata && listOf(title, artist, album, date, coverUrl).any(String::isBlank)) {
                return@mapIndexedNotNull null
            }
            PluginSongSearchResult(
                id = id,
                pluginId = pluginId,
                pluginName = pluginName,
                title = title,
                artist = artist,
                album = album,
                duration = obj.long("duration", "durationMs", "duration_ms") ?: 0L,
                date = date,
                trackNumber = obj.string("trackNumber", "trackerNumber", "track_number").orEmpty(),
                picUrl = coverUrl,
                fields = obj.stringMap("fields", "metadata").orEmpty(),
                internal = obj.stringMap("internal").orEmpty()
                    .filter { (key, value) -> key.isNotBlank() && key.length <= 64 && value.length <= 4096 }
                    .entries.take(64).associate { it.key to it.value }
            )
        }
    }

    fun parseLyricsCandidates(
        rawJson: String,
        pluginId: String,
        pluginName: String,
        fallbackSong: PluginSongSearchResult,
        enforceApi4Contract: Boolean = false
    ): List<PluginLyricsCandidateResult> {
        val root = json.parseToJsonElement(rawJson)
        if (root is JsonNull) return emptyList()
        val candidateElements = when (root) {
            is JsonArray -> root.toList()
            is JsonObject -> root.array("items", "results", "candidates")?.toList() ?: listOf(root)
            else -> listOf(root)
        }
        return candidateElements.mapIndexedNotNull { index, element ->
            val obj = element as? JsonObject
            val tags = obj?.stringMap("tags").orEmpty()
            if (enforceApi4Contract && listOf(tags["ti"], tags["ar"], tags["al"], tags["date"])
                    .any { it.isNullOrBlank() }
            ) {
                return@mapIndexedNotNull null
            }
            val lyrics = parseLyrics(element.toString()) ?: return@mapIndexedNotNull null
            val candidateId = if (candidateElements.size == 1) {
                fallbackSong.id
            } else {
                "${fallbackSong.id}:lyrics:$index"
            }
            PluginLyricsCandidateResult(
                song = fallbackSong.copy(
                    id = candidateId,
                    title = tags["ti"] ?: fallbackSong.title,
                    artist = tags["ar"] ?: fallbackSong.artist,
                    album = tags["al"] ?: fallbackSong.album,
                    date = tags["date"] ?: fallbackSong.date
                ),
                lyrics = lyrics
            )
        }
    }

    fun parseLyrics(rawJson: String): PluginLyricsResult? =
        parseLyricsElement(json.parseToJsonElement(rawJson))

    private fun parseLyricsElement(root: JsonElement): PluginLyricsResult? {
        if (root is JsonArray) {
            // API 4 permits getLyrics to return multiple candidate payloads. Halcyon's picker
            // currently selects one result, so keep the first usable candidate instead of
            // treating the whole array as an invalid lyric object.
            return root.firstNotNullOfOrNull { parseLyricsElement(it) }
        }
        if (root is JsonNull) return null
        if (root is JsonPrimitive) {
            val lrc = root.contentOrNull.orEmpty()
            return lrc.takeIf { it.isNotBlank() }?.let {
                PluginLyricsResult(
                    tags = emptyMap(),
                    original = emptyList(),
                    translated = null,
                    romanization = null,
                    payloadType = PluginLyricsPayloadType.RAW_PLAIN_LRC,
                    rawPlainLrc = it
                )
            }
        }
        val obj = root as? JsonObject ?: return null
        if (obj.boolean("notFound") == true) return null
        val tags = obj.stringMap("tags").orEmpty()
        val payloadType = obj.primitiveString("type")?.toPayloadType() ?: PluginLyricsPayloadType.STRUCTURED
        val rawPlain = obj.primitiveString("rawPlainLrc", "raw_plain_lrc", "plainLrc", "plain_lrc", "lrc", "originalLrc", "original_lrc").orEmpty()
        val rawOriginal = obj.primitiveString("original").orEmpty()
        val rawVerbatim = obj.primitiveString("rawVerbatimLrc", "raw_verbatim_lrc").orEmpty()
        val rawEnhanced = obj.primitiveString("rawEnhancedLrc", "raw_enhanced_lrc").orEmpty()
        val rawTtml = obj.primitiveString("rawTtml", "raw_ttml").orEmpty()
        val rawMulti = obj.primitiveString("rawMultiPersonEnhancedLrc", "raw_multi_person_enhanced_lrc").orEmpty()
        if (payloadType != PluginLyricsPayloadType.STRUCTURED) {
            val plain = rawPlain.ifBlank { rawOriginal }
            val hasRaw = when (payloadType) {
                PluginLyricsPayloadType.RAW_PLAIN_LRC -> plain.isNotBlank()
                PluginLyricsPayloadType.RAW_VERBATIM_LRC -> rawVerbatim.isNotBlank()
                PluginLyricsPayloadType.RAW_ENHANCED_LRC -> rawEnhanced.isNotBlank()
                PluginLyricsPayloadType.RAW_TTML -> rawTtml.isNotBlank()
                PluginLyricsPayloadType.RAW_MULTI_PERSON_ENHANCED_LRC -> rawMulti.isNotBlank()
                PluginLyricsPayloadType.STRUCTURED -> false
            }
            if (!hasRaw) return null
            return PluginLyricsResult(tags, emptyList(), null, null, payloadType, plain, rawVerbatim, rawEnhanced, rawTtml, rawMulti)
        }
        val original = obj.array("original", "lines").parseWordLines()
        if (original.isEmpty()) return null
        return PluginLyricsResult(
            tags = tags,
            original = original,
            translated = obj.array("translated", "translation", "translations").parseTextLines().takeIf { it.isNotEmpty() },
            romanization = obj.array("romanization", "romanized", "roma").parseTextLines().takeIf { it.isNotEmpty() }
        )
    }
}

enum class PluginLyricsRenderFormat {
    AUTO,
    WORD_LRC,
    ENHANCED_LRC,
    PLAIN_LRC,
    TTML
}

data class PluginLyricsRenderOptions(
    val format: PluginLyricsRenderFormat = PluginLyricsRenderFormat.AUTO,
    val includeTranslation: Boolean = true,
    val includeRomanization: Boolean = true
)

fun PluginLyricsResult.toEmbeddedLyricsText(
    options: PluginLyricsRenderOptions = PluginLyricsRenderOptions()
): String {
    val format = effectiveRenderFormat(options.format)
    directRawFor(format, options)?.let { raw ->
        return if (format == PluginLyricsRenderFormat.TTML) raw.trim() else raw.stripLrcMetadataTags()
    }
    val lines = toLyricLines()
    if (lines.isEmpty()) return ""
    val rendered = when (format) {
        PluginLyricsRenderFormat.TTML -> lines.renderTtml(tags, options)
        PluginLyricsRenderFormat.WORD_LRC -> lines.renderWordLrc(tags, options)
        PluginLyricsRenderFormat.ENHANCED_LRC -> lines.renderEnhancedLrc(tags, options)
        PluginLyricsRenderFormat.PLAIN_LRC,
        PluginLyricsRenderFormat.AUTO -> lines.renderPlainLrc(tags, options)
    }.trim()
    return if (format == PluginLyricsRenderFormat.TTML) rendered else rendered.stripLrcMetadataTags()
}

/**
 * Matches a full-line LRC ID/metadata tag such as `[ti:..]`, `[ar:..]`, `[al:..]`,
 * `[by:..]`, `[offset:0]`, `[length:..]`, `[language:..]`. The key starts with a
 * letter, which distinguishes these from timestamp tags like `[00:12.34]` (digit start),
 * so timed lyric/translation/romanization lines are preserved.
 */
private val LRC_METADATA_TAG = Regex("""^\[[A-Za-z][A-Za-z0-9 _\-]*:[^\]]*]$""")

/** Drops meaningless LRC metadata header lines so they are not written into a song's lyrics. */
private fun String.stripLrcMetadataTags(): String =
    lineSequence()
        .filterNot { LRC_METADATA_TAG.matches(it.trim()) }
        .joinToString("\n")
        .trim()

fun PluginLyricsResult.defaultRenderFormat(): PluginLyricsRenderFormat =
    effectiveRenderFormat(PluginLyricsRenderFormat.AUTO)

private fun PluginLyricsResult.effectiveRenderFormat(requested: PluginLyricsRenderFormat): PluginLyricsRenderFormat {
    if (requested != PluginLyricsRenderFormat.AUTO) return requested
    return when {
        rawTtml.isNotBlank() -> PluginLyricsRenderFormat.TTML
        rawEnhancedLrc.isNotBlank() || rawMultiPersonEnhancedLrc.isNotBlank() -> PluginLyricsRenderFormat.ENHANCED_LRC
        rawVerbatimLrc.isNotBlank() -> PluginLyricsRenderFormat.WORD_LRC
        original.any { it.words.size > 1 } -> PluginLyricsRenderFormat.ENHANCED_LRC
        else -> PluginLyricsRenderFormat.PLAIN_LRC
    }
}

private fun PluginLyricsResult.directRawFor(
    format: PluginLyricsRenderFormat,
    options: PluginLyricsRenderOptions
): String? {
    if (!options.includeTranslation || !options.includeRomanization) return null
    return when (format) {
        PluginLyricsRenderFormat.TTML -> rawTtml.takeIf { it.isNotBlank() }
        PluginLyricsRenderFormat.ENHANCED_LRC -> rawEnhancedLrc
            .ifBlank { rawMultiPersonEnhancedLrc }
            .takeIf { it.isNotBlank() }
        PluginLyricsRenderFormat.WORD_LRC -> rawVerbatimLrc.takeIf { it.isNotBlank() }
        PluginLyricsRenderFormat.PLAIN_LRC -> rawPlainLrc.takeIf { it.isNotBlank() }
        PluginLyricsRenderFormat.AUTO -> null
    }
}

private fun PluginLyricsResult.toLyricLines(): List<LyricLine> {
    if (payloadType != PluginLyricsPayloadType.STRUCTURED) {
        val raw = rawTtml
            .ifBlank { rawEnhancedLrc }
            .ifBlank { rawMultiPersonEnhancedLrc }
            .ifBlank { rawVerbatimLrc }
            .ifBlank { rawPlainLrc }
        return raw.takeIf { it.isNotBlank() }
            ?.let { LrcParser.parse(it).lyrics }
            .orEmpty()
    }
    return original.sortedBy { it.start }.mapNotNull { line ->
        val text = line.text
        if (text.isBlank()) return@mapNotNull null
        val translation = translated.nearestLine(line.start)?.text?.trim()?.takeIf { it.isNotBlank() }
        val roman = romanization.nearestLine(line.start)
        LyricLine(
            timeMs = line.start,
            text = text,
            words = line.words.map { LyricWord(it.text, it.start, it.end) },
            translation = translation,
            pronunciation = roman?.text?.trim()?.takeIf { it.isNotBlank() },
            pronunciationWords = roman?.words.orEmpty().map { LyricWord(it.text, it.start, it.end) },
            endMs = line.end
        )
    }
}

private fun List<LyricLine>.renderPlainLrc(
    tags: Map<String, String>,
    options: PluginLyricsRenderOptions
): String = buildString {
    sortedBy { it.timeMs }.forEach { line ->
        val text = line.text.trim()
        if (text.isBlank()) return@forEach
        appendLine("${line.timeMs.lrcTimestamp()}$text")
        appendCompanions(line, options)
    }
}

private fun List<LyricLine>.renderEnhancedLrc(
    tags: Map<String, String>,
    options: PluginLyricsRenderOptions
): String = buildString {
    sortedBy { it.timeMs }.forEach { line ->
        val text = line.text.trim()
        if (text.isBlank()) return@forEach
        val words = line.words.takeIf { it.size > 1 }
        if (words == null) {
            appendLine("${line.timeMs.lrcTimestamp()}$text")
        } else {
            append(line.timeMs.lrcTimestamp())
            words.forEach { word ->
                append(word.startMs.enhancedTimestamp())
                append(word.text)
            }
            val end = line.endMs ?: words.lastOrNull()?.endMs
            if (end != null) append(end.enhancedTimestamp())
            appendLine()
        }
        appendCompanions(line, options)
    }
}

private fun List<LyricLine>.renderWordLrc(
    tags: Map<String, String>,
    options: PluginLyricsRenderOptions
): String = buildString {
    sortedBy { it.timeMs }.forEach { line ->
        val text = line.text.trim()
        if (text.isBlank()) return@forEach
        val words = line.words.takeIf { it.size > 1 }
        if (words == null) {
            appendLine("${line.timeMs.lrcTimestamp()}$text")
        } else {
            words.forEach { word ->
                append(word.startMs.lrcTimestamp())
                append(word.text)
            }
            val end = line.endMs ?: words.lastOrNull()?.endMs
            if (end != null) append(end.lrcTimestamp())
            appendLine()
        }
        appendCompanions(line, options)
    }
}

private fun List<LyricLine>.renderTtml(
    tags: Map<String, String>,
    options: PluginLyricsRenderOptions
): String = buildString {
    appendLine("""<?xml version="1.0" encoding="utf-8"?>""")
    appendLine("""<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata" xmlns:itunes="http://music.apple.com/lyric-ttml-internal">""")
    if (tags.isNotEmpty()) {
        appendLine("  <head>")
        appendLine("    <metadata>")
        tags["ti"]?.takeIf { it.isNotBlank() }?.let { appendLine("""      <amll:meta xmlns:amll="http://www.example.com/ns/amll" key="musicName" value="${it.escapeXml()}"/>""") }
        tags["ar"]?.takeIf { it.isNotBlank() }?.let { appendLine("""      <amll:meta xmlns:amll="http://www.example.com/ns/amll" key="artists" value="${it.escapeXml()}"/>""") }
        tags["al"]?.takeIf { it.isNotBlank() }?.let { appendLine("""      <amll:meta xmlns:amll="http://www.example.com/ns/amll" key="album" value="${it.escapeXml()}"/>""") }
        appendLine("    </metadata>")
        appendLine("  </head>")
    }
    appendLine("  <body>")
    appendLine("    <div>")
    sortedBy { it.timeMs }.forEachIndexed { index, line ->
        val text = line.text.trim()
        if (text.isBlank()) return@forEachIndexed
        val end = line.endMs ?: line.words.lastOrNull()?.endMs ?: (line.timeMs + 2_000L)
        append("""      <p begin="${line.timeMs.ttmlTimestamp()}" end="${end.ttmlTimestamp()}" itunes:key="L$index">""")
        val words = line.words.takeIf { it.size > 1 }
        if (words == null) {
            append(text.escapeXml())
        } else {
            words.forEach { word ->
                append("""<span begin="${word.startMs.ttmlTimestamp()}" end="${word.endMs.ttmlTimestamp()}">${word.text.escapeXml()}</span>""")
            }
        }
        if (options.includeRomanization) {
            val roman = line.pronunciation?.takeIf { it.isNotBlank() }
            if (roman != null) append("""<span ttm:role="x-romanization">${roman.escapeXml()}</span>""")
        }
        if (options.includeTranslation) {
            val translation = line.translation?.takeIf { it.isNotBlank() }
            if (translation != null) append("""<span ttm:role="x-translation">${translation.escapeXml()}</span>""")
        }
        appendLine("</p>")
    }
    appendLine("    </div>")
    appendLine("  </body>")
    append("</tt>")
}

private fun StringBuilder.appendCompanions(line: LyricLine, options: PluginLyricsRenderOptions) {
    if (options.includeRomanization) {
        line.pronunciation?.trim()?.takeIf { it.isNotBlank() }?.let { roman ->
            appendLine("${line.timeMs.lrcTimestamp()}$roman")
        }
    }
    if (options.includeTranslation) {
        line.translation?.trim()?.takeIf { it.isNotBlank() }?.let { translation ->
            appendLine("${line.timeMs.lrcTimestamp()}$translation")
        }
    }
}

fun PluginSongSearchResult.toPluginSongRequest(): PluginSongRequest =
    PluginSongRequest(
        id = id,
        title = title,
        artist = artist,
        album = album,
        date = date,
        duration = duration,
        sourceId = pluginId,
        pluginId = pluginId,
        fields = fields,
        internal = internal
    )

private fun List<PluginLyricsLine>?.nearestLine(start: Long): PluginLyricsLine? =
    this?.minByOrNull { kotlin.math.abs(it.start - start) }
        ?.takeIf { kotlin.math.abs(it.start - start) <= 1500L }

private fun Long.lrcTimestamp(): String {
    val minutes = this / 60000
    val seconds = (this % 60000) / 1000
    val centis = (this % 1000) / 10
    return "[%02d:%02d.%02d]".format(minutes, seconds, centis)
}

private fun Long.enhancedTimestamp(): String = lrcTimestamp().replace('[', '<').replace(']', '>')

private fun Long.ttmlTimestamp(): String {
    val hours = this / 3_600_000
    val minutes = (this % 3_600_000) / 60_000
    val seconds = (this % 60_000) / 1000
    val millis = this % 1000
    return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
}

private fun String.escapeXml(): String =
    replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

private fun String.toPayloadType(): PluginLyricsPayloadType? = when (trim()) {
    "structured", "STRUCTURED" -> PluginLyricsPayloadType.STRUCTURED
    "rawPlainLrc", "raw_plain_lrc", "RAW_PLAIN_LRC", "plainLrc", "plain_lrc", "lrc" -> PluginLyricsPayloadType.RAW_PLAIN_LRC
    "rawVerbatimLrc", "raw_verbatim_lrc", "RAW_VERBATIM_LRC" -> PluginLyricsPayloadType.RAW_VERBATIM_LRC
    "rawEnhancedLrc", "raw_enhanced_lrc", "RAW_ENHANCED_LRC" -> PluginLyricsPayloadType.RAW_ENHANCED_LRC
    "rawTtml", "raw_ttml", "RAW_TTML", "ttml" -> PluginLyricsPayloadType.RAW_TTML
    "rawMultiPersonEnhancedLrc", "raw_multi_person_enhanced_lrc", "RAW_MULTI_PERSON_ENHANCED_LRC" -> PluginLyricsPayloadType.RAW_MULTI_PERSON_ENHANCED_LRC
    else -> null
}

private fun JsonArray?.parseWordLines(): List<PluginLyricsLine> = this?.mapNotNull { element ->
    val line = element as? JsonArray ?: return@mapNotNull null
    val start = line.longAt(0) ?: return@mapNotNull null
    val end = line.longAt(1) ?: start
    val wordsArray = line.arrayAt(2)
    val text = line.stringAt(2)
    val words = when {
        wordsArray != null -> wordsArray.mapNotNull { wordElement ->
            val word = wordElement as? JsonArray ?: return@mapNotNull null
            PluginLyricsWord(
                start = word.longAt(0) ?: start,
                end = word.longAt(1) ?: end,
                text = word.stringAt(2).orEmpty()
            ).takeIf { it.text.isNotEmpty() }
        }
        !text.isNullOrEmpty() -> listOf(PluginLyricsWord(start, end, text))
        else -> emptyList()
    }
    PluginLyricsLine(start, end, words).takeIf { words.isNotEmpty() }
}.orEmpty()

private fun JsonArray?.parseTextLines(): List<PluginLyricsLine> = this?.mapNotNull { element ->
    val line = element as? JsonArray ?: return@mapNotNull null
    val start = line.longAt(0) ?: return@mapNotNull null
    val end = line.longAt(1) ?: start
    val text = line.stringAt(2).orEmpty()
    PluginLyricsLine(start, end, listOf(PluginLyricsWord(start, end, text))).takeIf { text.isNotBlank() }
}.orEmpty()

private fun JsonArray.longAt(index: Int): Long? =
    (getOrNull(index) as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }

private fun JsonArray.stringAt(index: Int): String? = (getOrNull(index) as? JsonPrimitive)?.contentOrNull
private fun JsonArray.arrayAt(index: Int): JsonArray? = getOrNull(index) as? JsonArray
private fun JsonObject.boolean(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull
private fun JsonObject.array(vararg keys: String): JsonArray? = keys.firstNotNullOfOrNull { this[it] as? JsonArray }
private fun JsonObject.primitiveString(vararg keys: String): String? = keys.firstNotNullOfOrNull { (this[it] as? JsonPrimitive)?.contentOrNull }
private fun JsonObject.long(vararg keys: String): Long? =
    keys.firstNotNullOfOrNull { key -> (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() } }

private fun JsonObject.string(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
    when (val value = this[key]) {
        is JsonPrimitive -> value.contentOrNull
        is JsonArray -> value.joinToString("/") { item ->
            when (item) {
                is JsonPrimitive -> item.contentOrNull.orEmpty()
                is JsonObject -> item.string("name", "title", "value").orEmpty()
                else -> ""
            }
        }.takeIf { it.isNotBlank() }
        else -> null
    }
}

private fun JsonObject.stringMap(vararg keys: String): Map<String, String>? {
    val obj = keys.firstNotNullOfOrNull { this[it] as? JsonObject } ?: return null
    return obj.mapNotNull { (key, value) ->
        val text = when (value) {
            is JsonPrimitive -> value.contentOrNull
            else -> value.toString()
        }
        text?.takeIf { it.isNotBlank() }?.let { key to it }
    }.toMap()
}
