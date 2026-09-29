package com.ella.music.data.netease

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.parser.EllaLyricsParser
import org.json.JSONObject
import kotlin.math.abs

internal data class NeteaseLyrics(val original: String, val translation: String, val pronunciation: String)

internal fun parseNeteaseLyricsResponse(root: JSONObject): NeteaseLyrics {
    fun lyric(key: String) = root.optJSONObject(key)?.optString("lyric")?.takeUnless { it == "null" }.orEmpty()
    val yrc = lyric("yrc").takeIf { parseNeteaseYrc(it).isNotEmpty() }.orEmpty()
    return NeteaseLyrics(
        original = yrc.ifBlank { lyric("lrc") },
        translation = (if (yrc.isNotBlank()) lyric("ytlrc") else "").ifBlank { lyric("tlyric") },
        pronunciation = (if (yrc.isNotBlank()) lyric("yromalrc") else "").ifBlank { lyric("romalrc") }
    )
}

/** YRC word times are absolute milliseconds, unlike KRC's line-relative offsets. */
internal fun parseNeteaseYrc(raw: String): List<LyricLine> {
    val linePattern = Regex("""^\[(\d+),(\d+)](.*)$""")
    val wordPattern = Regex("""\((\d+),(\d+),\d+\)""")
    return raw.lineSequence().mapNotNull { line ->
        val header = linePattern.matchEntire(line.trim()) ?: return@mapNotNull null
        val start = header.groupValues[1].toLongOrNull() ?: return@mapNotNull null
        val duration = header.groupValues[2].toLongOrNull() ?: return@mapNotNull null
        val content = header.groupValues[3]
        val markers = wordPattern.findAll(content).toList()
        val words = markers.mapIndexedNotNull { index, marker ->
            val time = marker.groupValues[1].toLongOrNull() ?: return@mapIndexedNotNull null
            val length = marker.groupValues[2].toLongOrNull() ?: return@mapIndexedNotNull null
            val text = content.substring(marker.range.last + 1, markers.getOrNull(index + 1)?.range?.first ?: content.length)
            text.takeIf { it.isNotEmpty() }?.let { LyricWord(it, time, time + length) }
        }
        if (words.isEmpty()) null else LyricLine(start, words.joinToString("") { it.text }, words = words, endMs = start + duration)
    }.sortedBy { it.timeMs }.toList()
}

internal fun mergeOnlineLyricCompanions(
    lines: List<LyricLine>, translation: String, pronunciation: String
): List<LyricLine> {
    fun parse(raw: String) = if (raw.isBlank()) emptyList() else EllaLyricsParser.parse(raw).lyrics
    val translations = parse(translation)
    val pronunciations = parse(pronunciation)
    fun match(entries: List<LyricLine>, line: LyricLine): LyricLine? =
        entries.firstOrNull { it.timeMs == line.timeMs } ?: entries.minByOrNull { abs(it.timeMs - line.timeMs) }
            ?.takeIf { abs(it.timeMs - line.timeMs) <= 500L }
    return lines.map { line ->
        val roman = match(pronunciations, line)
        line.copy(
            translation = match(translations, line)?.text?.takeIf(String::isNotBlank) ?: line.translation,
            pronunciation = roman?.text?.takeIf(String::isNotBlank) ?: line.pronunciation,
            pronunciationWords = roman?.words?.takeIf { it.isNotEmpty() } ?: line.pronunciationWords
        )
    }
}
