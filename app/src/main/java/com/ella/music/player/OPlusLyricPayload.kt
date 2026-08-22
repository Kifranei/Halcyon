package com.ella.music.player

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
import com.ella.music.data.SettingsManager
import java.util.Locale

internal object OPlusLyricPayload {
    const val RAW_LYRIC_INFO_KEY = "rawLyric"
    const val TRANSLATION_LYRIC_INFO_KEY = "translationLyric"
    private const val OPLUS_PREAMBLE_MAX_TIME_MS = 15_000L
    private const val OPLUS_TITLE_CREDIT_MAX_TIME_MS = 5_000L
    private val preambleCreditPattern = Regex(
        """^(?:lyrics?\s+by|lyricist|written\s+by|composed\s+by|composer|produced\s+by|producer|arranged\s+by|arranger|performed\s+by|performer|作词|作曲|编曲|制作人|演唱)\s*[:：]?""",
        RegexOption.IGNORE_CASE
    )
    private val titleArtistSeparatorPattern = Regex("""\s[-–—]\s""")

    fun build(song: Song, lyrics: List<LyricLine>, mode: Int = SettingsManager.OPLUS_LYRIC_MODE_MODULE): String? {
        return if (mode == SettingsManager.OPLUS_LYRIC_MODE_SYSTEM) {
            buildSystemPayload(song, lyrics)
        } else {
            buildModulePayload(song, lyrics)
        }
    }

    fun buildSystemPayload(song: Song, lyrics: List<LyricLine>): String? {
        val lrc = lyrics.toOplusLrc().takeIf { it.isNotBlank() } ?: return null
        return buildJsonObject(
            "songName" to song.title,
            "artist" to song.artist,
            "songId" to song.oplusLyricSongId(),
            "lyric" to lrc
        )
    }

    fun buildModulePayload(song: Song, lyrics: List<LyricLine>): String? {
        val moduleLyrics = lyrics.withoutOplusPreamble(song)
        val lrc = moduleLyrics.toOplusLrc().takeIf { it.isNotBlank() } ?: return null
        val rawLyric = moduleLyrics.toOplusRawLyric().takeIf { it.isNotBlank() } ?: lrc
        val translationLyric = moduleLyrics.toOplusTranslationLyric().takeIf { it.isNotBlank() }
        val fields = mutableListOf(
            "songName" to song.title,
            "artist" to song.artist,
            "songId" to song.oplusLyricSongId(),
            "lyric" to lrc,
            RAW_LYRIC_INFO_KEY to rawLyric
        )
        if (translationLyric != null) {
            fields += TRANSLATION_LYRIC_INFO_KEY to translationLyric
        }
        return buildJsonObject(*fields.toTypedArray())
    }

    private fun List<LyricLine>.withoutOplusPreamble(song: Song): List<LyricLine> {
        val filtered = filterNot { it.isOplusPreambleLine(song) }
        return filtered.takeIf { it.isNotEmpty() } ?: this
    }

    private fun LyricLine.isOplusPreambleLine(song: Song): Boolean {
        val primary = primaryOplusTextOrNull() ?: return false
        val startMs = rawLyricStartMs()
        if (startMs > OPLUS_PREAMBLE_MAX_TIME_MS) return false

        val normalized = primary.trim()
        val lower = normalized.lowercase(Locale.ROOT)
        if (preambleCreditPattern.containsMatchIn(normalized)) return true
        if (
            lower.contains("copyright") ||
            lower.contains("all rights reserved") ||
            normalized.contains("版权所有") ||
            normalized.contains("著作权") ||
            normalized.contains("未经许可") ||
            normalized.contains("未经授权")
        ) {
            return true
        }

        if (startMs > OPLUS_TITLE_CREDIT_MAX_TIME_MS || !titleArtistSeparatorPattern.containsMatchIn(normalized)) {
            return false
        }
        val lineKey = normalized.oplusIdentityKey()
        val titleKey = song.title.oplusIdentityKey()
        val artistKey = song.artist.oplusIdentityKey()
        return titleKey.length >= 2 &&
            artistKey.length >= 2 &&
            lineKey.contains(titleKey) &&
            lineKey.contains(artistKey)
    }

    private fun String.oplusIdentityKey(): String =
        lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

    fun matchesMode(rawJson: String, mode: Int): Boolean {
        val hasRawLyric = rawLyric(rawJson) != null
        val hasTranslationLyric = stringField(rawJson, TRANSLATION_LYRIC_INFO_KEY)?.isNotBlank() == true
        return if (mode == SettingsManager.OPLUS_LYRIC_MODE_SYSTEM) {
            !hasRawLyric && !hasTranslationLyric
        } else {
            hasRawLyric
        }
    }

    fun matchesSong(rawJson: String, song: Song): Boolean {
        val songId = stringField(rawJson, "songId")?.takeIf { it.isNotBlank() }
        return when {
            songId != null -> songId == song.oplusLyricSongId()
            else -> {
                val songName = stringField(rawJson, "songName")
                val artist = stringField(rawJson, "artist")
                songName == song.title && artist == song.artist
            }
        }
    }

    fun rawLyric(rawJson: String): String? =
        stringField(rawJson, RAW_LYRIC_INFO_KEY)?.takeIf { it.isNotBlank() }

    fun hasTranslation(rawJson: String?): Boolean =
        rawJson?.let { stringField(it, TRANSLATION_LYRIC_INFO_KEY) }?.isNotBlank() == true

    internal fun stringField(rawJson: String, name: String): String? {
        val key = "\"${name.escapeJsonString()}\""
        val keyIndex = rawJson.indexOf(key)
        if (keyIndex < 0) return null
        var index = rawJson.indexOf(':', keyIndex + key.length)
        if (index < 0) return null
        index++
        while (index < rawJson.length && rawJson[index].isWhitespace()) index++
        if (index >= rawJson.length || rawJson[index] != '"') return null
        return rawJson.parseJsonString(index)
    }

    private fun List<LyricLine>.toOplusLrc(): String {
        return mapNotNull { line ->
            val primaryText = line.primaryOplusTextOrNull() ?: return@mapNotNull null
            line.timeMs.coerceAtLeast(0L) to primaryText
        }
            .sortedBy { it.first }
            .joinToString("\n") { (timeMs, text) ->
                "${timeMs.toOplusLrcTimestamp(precision = TimestampPrecision.Centi)}$text"
            }
    }

    private fun List<LyricLine>.toOplusTranslationLyric(): String {
        return mapNotNull { line ->
            val translation = line.translation.toOplusLrcTextOrNull()
                ?.takeIf { it != line.primaryOplusTextOrNull() }
                ?: return@mapNotNull null
            line.rawLyricStartMs() to translation
        }
            .sortedBy { it.first }
            .joinToString("\n") { (timeMs, text) ->
                "${timeMs.toOplusLrcTimestamp(precision = TimestampPrecision.Milli)}$text"
            }
    }

    private fun List<LyricLine>.toOplusRawLyric(): String {
        return mapNotNull { line ->
            val main = line.toOplusRawMainLine() ?: return@mapNotNull null
            val translation = line.translation.toOplusLrcTextOrNull()
                ?.takeIf { it != line.primaryOplusTextOrNull() }
                ?: return@mapNotNull main
            val translationLine =
                "${line.rawLyricStartMs().toOplusLrcTimestamp(precision = TimestampPrecision.Milli)}$translation"
            "$main\n$translationLine"
        }
            .joinToString("\n")
    }

    private fun LyricLine.toOplusRawMainLine(): String? {
        val primaryText = primaryOplusTextOrNull() ?: return null
        val rawWords = primaryOplusWords()
            .withLineSpacing(primaryText)
            .asSequence()
            .mapNotNull { word ->
                val text = word.text.toOplusRawWordTextOrNull() ?: return@mapNotNull null
                val startMs = word.startMs.coerceAtLeast(0L)
                val endMs = word.endMs.coerceAtLeast(startMs + 1L)
                TimedText(text = text, startMs = startMs, endMs = endMs)
            }
            .sortedBy { it.startMs }
            .toList()
        if (rawWords.isEmpty()) {
            return "${timeMs.coerceAtLeast(0L).toOplusLrcTimestamp(precision = TimestampPrecision.Milli)}$primaryText"
        }

        val builder = StringBuilder(primaryText.length + rawWords.size * 14)
        rawWords.forEach { word ->
            builder
                .append(word.startMs.toOplusLrcTimestamp(precision = TimestampPrecision.Milli))
                .append(word.text)
        }
        val lineEndMs = listOfNotNull(endMs, rawWords.maxOfOrNull { it.endMs })
            .maxOrNull()
            ?.takeIf { it > rawWords.last().startMs }
        if (lineEndMs != null) {
            builder.append(lineEndMs.toOplusLrcTimestamp(precision = TimestampPrecision.Milli))
        }
        return builder.toString()
    }

    private fun LyricLine.rawLyricStartMs(): Long =
        primaryOplusWords().minOfOrNull { it.startMs }?.coerceAtLeast(0L) ?: timeMs.coerceAtLeast(0L)

    private fun LyricLine.primaryOplusTextOrNull(): String? =
        text.toOplusLrcTextOrNull()
            ?: backgroundText.toOplusLrcTextOrNull()

    private fun LyricLine.primaryOplusWords(): List<LyricWord> =
        if (text.toOplusLrcTextOrNull() != null) words else backgroundWords

    private fun String?.toOplusLrcTextOrNull(): String? {
        return this
            ?.lineSequence()
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            ?.joinToString(" ")
            ?.stripInlineOplusLrcTimestamps(trim = true)
            ?.takeIf { it.isNotBlank() }
    }

    private fun String.toOplusRawWordTextOrNull(): String? {
        return stripInlineOplusLrcTimestamps(trim = false)
            .replace(Regex("""[\r\n\t]+"""), " ")
            .takeIf { it.isNotBlank() }
    }

    private fun String.stripInlineOplusLrcTimestamps(trim: Boolean): String {
        fun String.isOplusTimestampMarker(): Boolean {
            return matches(Regex("""\d{1,3}:\d{1,2}(?:[.:]\d{1,6})?"""))
        }

        val stripped = replace(Regex("""\[([^\]]+)]|<([^>]+)>""")) { match ->
            val marker = match.groupValues.getOrNull(1).orEmpty()
                .ifBlank { match.groupValues.getOrNull(2).orEmpty() }
                .trim()
                .replace(',', '.')
            if (marker.isOplusTimestampMarker()) "" else match.value
        }
            .replace(Regex("""[ \t\r\n]+"""), " ")
        return if (trim) stripped.trim() else stripped
    }

    private fun Long.toOplusLrcTimestamp(precision: TimestampPrecision): String {
        val safeMs = coerceAtLeast(0L)
        val minutes = safeMs / 60_000L
        val seconds = (safeMs % 60_000L) / 1_000L
        return when (precision) {
            TimestampPrecision.Centi -> {
                val centiseconds = (safeMs % 1_000L) / 10L
                "[%02d:%02d.%02d]".format(Locale.US, minutes, seconds, centiseconds)
            }
            TimestampPrecision.Milli -> {
                val milliseconds = safeMs % 1_000L
                "[%02d:%02d.%03d]".format(Locale.US, minutes, seconds, milliseconds)
            }
        }
    }

    private fun List<LyricWord>.withLineSpacing(lineText: String): List<LyricWord> {
        if (isEmpty() || lineText.isBlank() || !lineText.any { it.isWhitespace() }) return this

        val result = mutableListOf<LyricWord>()
        var cursor = 0

        forEachIndexed { index, word ->
            val start = lineText.indexOf(word.text, startIndex = cursor)
            if (start < 0) {
                result += word
                return@forEachIndexed
            }

            val end = start + word.text.length
            val nextText = getOrNull(index + 1)?.text
            val nextStart = if (nextText != null) lineText.indexOf(nextText, startIndex = end) else -1
            val suffix = when {
                nextStart > end -> lineText.substring(end, nextStart)
                nextText == null && end < lineText.length -> lineText.substring(end)
                else -> ""
            }
            result += word.copy(text = word.text + suffix)
            cursor = end + suffix.length
        }

        return result.takeIf { it.size == size } ?: this
    }

    private fun Song.oplusLyricSongId(): String = when {
        onlineSource.isNotBlank() && onlineId.isNotBlank() -> "$onlineSource:$onlineId"
        id > 0L -> id.toString()
        path.isNotBlank() -> path
        else -> "$title|$artist|$album"
    }

    private fun buildJsonObject(vararg fields: Pair<String, String>): String =
        fields.joinToString(prefix = "{", postfix = "}") { (name, value) ->
            "\"${name.escapeJsonString()}\":\"${value.escapeJsonString()}\""
        }

    private fun String.escapeJsonString(): String {
        val out = StringBuilder(length + 16)
        forEach { char ->
            when (char) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> {
                    if (char.code < 0x20) {
                        out.append("\\u%04x".format(Locale.US, char.code))
                    } else {
                        out.append(char)
                    }
                }
            }
        }
        return out.toString()
    }

    private fun String.parseJsonString(startQuoteIndex: Int): String? {
        if (startQuoteIndex !in indices || this[startQuoteIndex] != '"') return null
        val out = StringBuilder()
        var index = startQuoteIndex + 1
        while (index < length) {
            val char = this[index++]
            when (char) {
                '"' -> return out.toString()
                '\\' -> {
                    if (index >= length) return null
                    when (val escaped = this[index++]) {
                        '"', '\\', '/' -> out.append(escaped)
                        'b' -> out.append('\b')
                        'f' -> out.append('\u000C')
                        'n' -> out.append('\n')
                        'r' -> out.append('\r')
                        't' -> out.append('\t')
                        'u' -> {
                            if (index + 4 > length) return null
                            val hex = substring(index, index + 4)
                            out.append(hex.toIntOrNull(16)?.toChar() ?: return null)
                            index += 4
                        }
                        else -> return null
                    }
                }
                else -> out.append(char)
            }
        }
        return null
    }

    private enum class TimestampPrecision {
        Centi,
        Milli
    }

    private data class TimedText(
        val text: String,
        val startMs: Long,
        val endMs: Long
    )
}
