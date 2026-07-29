package com.ella.music.data.parser

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.shiftedBy
import kotlin.math.abs

internal object EllaLyricsParser {
    private val lrcTimePattern = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,6}))?]""")
    private val lrcMetaPattern = Regex("""\[(ti|ar|al|by|offset|re|ve):\s*(.*)]""", RegexOption.IGNORE_CASE)
    private val timedWordMarkerPattern = Regex("""<([^>]+)>|\[([^\]]+)]""")
    private val backgroundLinePattern = Regex("""^\[bg:\s*(.*)]$""", RegexOption.IGNORE_CASE)
    private val lyricifySyllablePattern = Regex("""(.*?)\((\d+),(\d+)\)""")
    private val lyricifyAttributePattern = Regex("""^\[(\d+)]""")
    private val timestampOnlyPattern = Regex("""\d+(?::\d{1,2}){1,2}(?:[.:]\d{1,6})?""")

    fun parse(content: String, ignoreHeaderTags: Boolean = false): LrcParser.LrcResult {
        parseTtml(content)?.let { return it }
        if (lyricifySyllablePattern.containsMatchIn(content)) {
            parseLyricify(content)?.let { return it }
        }
        return parseLrc(content, ignoreHeaderTags)
    }

    private fun parseLrc(content: String, ignoreHeaderTags: Boolean = false): LrcParser.LrcResult {
        val lines = mutableListOf<LyricLine>()
        var title: String? = null
        var artist: String? = null
        var album: String? = null
        var offset = 0L
        var companionTargetIndexes = emptyList<Int>()

        content.lines().forEach { raw ->
            val line = raw.trim()
            if (line.isBlank()) {
                companionTargetIndexes = emptyList()
                return@forEach
            }

            lrcMetaPattern.matchEntire(line)?.let { match ->
                when (match.groupValues[1].lowercase()) {
                    "ti" -> title = match.groupValues[2].trim()
                    "ar" -> artist = match.groupValues[2].trim()
                    "al" -> album = match.groupValues[2].trim()
                    "offset" -> offset = match.groupValues[2].trim().toLongOrNull() ?: 0L
                }
                companionTargetIndexes = emptyList()
                return@forEach
            }
            parseTimestampOnlyLine(line)?.let { endMs ->
                lines.applyLineEnd(companionTargetIndexes, endMs)
                companionTargetIndexes = emptyList()
                return@forEach
            }
            val withoutTimes = lrcTimePattern.replace(line, "").trim()
            val hasTimestamps = withoutTimes != line.trim()
            if (hasTimestamps && isPlaceholderOnlyLine(withoutTimes)) {
                val endMs = parseLrcTime(lrcTimePattern.findAll(line).last().groupValues)
                if (endMs > 0L) {
                    lines.applyLineEnd(companionTargetIndexes, endMs)
                }
                companionTargetIndexes = emptyList()
                return@forEach
            }
            if (hasTimestamps && withoutTimes.isBlank()) {
                companionTargetIndexes = emptyList()
                return@forEach
            }
            if (lrcGenericMetaPattern.matches(line)) {
                companionTargetIndexes = emptyList()
                return@forEach
            }
            if (ignoreHeaderTags && isHeaderTagLine(line)) {
                companionTargetIndexes = emptyList()
                return@forEach
            }

            val parsed = parseLrcLine(line)
            if (parsed.isNotEmpty()) {
                val firstIndex = lines.size
                lines += parsed
                companionTargetIndexes = (firstIndex until lines.size).toList()
            } else if (!lines.appendUntimedTranslation(companionTargetIndexes, line)) {
                companionTargetIndexes = emptyList()
            }
        }

        return LrcParser.LrcResult(
            lyrics = mergeCompanionLines(lines)
                .shiftedBy(-offset),
            title = title,
            artist = artist,
            album = album,
            offset = offset
        )
    }

    private fun parseTimestampOnlyLine(line: String): Long? {
        val times = lrcTimePattern.findAll(line).toList()
        if (times.isEmpty()) return null
        var cursor = 0
        times.forEach { match ->
            if (line.substring(cursor, match.range.first).isNotBlank()) return null
            cursor = match.range.last + 1
        }
        if (line.substring(cursor).isNotBlank()) return null
        return parseLrcTime(times.last().groupValues)
    }

    private fun String?.mergeLyricCompanionText(text: String?): String? =
        listOfNotNull(this?.takeIf { it.isNotBlank() }, text?.takeIf { it.isNotBlank() })
            .distinct()
            .joinToString("\n")
            .takeIf { it.isNotBlank() }

    private fun MutableList<LyricLine>.applyLineEnd(indexes: List<Int>, endMs: Long) {
        indexes.forEach { index ->
            val line = getOrNull(index) ?: return@forEach
            if (endMs > line.timeMs) {
                this[index] = line.copy(endMs = line.endMs ?: endMs)
            }
        }
    }

    private fun MutableList<LyricLine>.appendUntimedTranslation(indexes: List<Int>, rawLine: String): Boolean {
        if (indexes.isEmpty()) return false
        val (_, content) = rawLine.extractLrcAgent()
        val text = content.cleanLyricSecondaryText()
        if (text.isIgnorableLyricText()) return false
        indexes.forEach { index ->
            val line = getOrNull(index) ?: return@forEach
            this[index] = line.copy(
                translation = line.translation.mergeLyricCompanionText(text)
            )
        }
        return true
    }

    private fun parseLrcLine(line: String): List<LyricLine> {
        backgroundLinePattern.matchEntire(line)?.let { match ->
            val content = match.groupValues[1].trim()
            val words = parseEnhancedWords(content, 0L)
            val text = if (words.isNotEmpty()) words.joinLyricText() else content.cleanLyricText()
            if (text.isIgnorableLyricText()) return emptyList()
            return listOf(
                LyricLine(
                    timeMs = words.firstOrNull()?.startMs ?: 0L,
                    text = "",
                    backgroundText = text,
                    backgroundWords = words,
                    endMs = words.lastOrNull()?.endMs
                )
            )
        }

        val leadingTimes = line.leadingLrcTimeMatches()
        if (leadingTimes.isEmpty()) return emptyList()

        val contentStart = leadingTimes.last().range.last + 1
        val taggedContent = line.substring(contentStart).trim()
        val embeddedBackground = backgroundLinePattern.matchEntire(taggedContent)
        val rawContent = embeddedBackground?.groupValues?.get(1)?.trim() ?: taggedContent
        if (rawContent.isBlank()) return emptyList()

        val (agent, content) = rawContent.extractLrcAgent().let { (agent, content) ->
            if (agent != null) {
                agent to content
            } else {
                content.extractEnhancedLrcAgent()
            }
        }

        return leadingTimes.mapNotNull { timeMatch ->
            val start = parseLrcTime(timeMatch.groupValues)
            val words = parseEnhancedWords(content, start)
            val text = if (words.isNotEmpty()) words.joinLyricText() else content.cleanLyricText()
            if (text.isIgnorableLyricText()) return@mapNotNull null
            if (embeddedBackground != null) {
                return@mapNotNull LyricLine(
                    timeMs = words.firstOrNull()?.startMs ?: start,
                    text = "",
                    backgroundText = text,
                    backgroundWords = words.toDisplayWords(text),
                    agent = agent,
                    endMs = words.lastOrNull()?.endMs
                )
            }
            LyricLine(
                timeMs = start,
                text = text,
                words = words.toDisplayWords(text),
                agent = agent,
                endMs = words.lastOrNull()?.endMs
            )
        }
    }

    private fun String.extractLrcAgent(): Pair<String?, String> {
        Regex("""^(v[12])\s*[:：]\s*(.*)$""", RegexOption.IGNORE_CASE)
            .matchEntire(this)
            ?.let { match ->
                return match.groupValues[1].lowercase() to match.groupValues[2].trim()
            }
        Regex("""^\[(v[12])]\s*(.*)$""", RegexOption.IGNORE_CASE)
            .matchEntire(this)
            ?.let { match ->
                return match.groupValues[1].lowercase() to match.groupValues[2].trim()
            }
        return null to this
    }

    private fun String.extractEnhancedLrcAgent(): Pair<String?, String> {
        val match = Regex(
            """^(\s*(?:<\d{1,3}:\d{2}(?:\.\d{1,3})?>|\[\d{1,3}:\d{2}(?:\.\d{1,3})?])\s*)(v[12])\s*[:：]\s*""",
            RegexOption.IGNORE_CASE
        ).find(this) ?: return null to this
        return match.groupValues[2].lowercase() to match.groupValues[1] + substring(match.range.last + 1)
    }

    private fun parseEnhancedWords(content: String, lineStartMs: Long): List<LyricWord> {
        val markers = timedWordMarkerPattern.findAll(content)
            .mapNotNull { match ->
                val time = match.groupValues.getOrNull(1).orEmpty()
                    .ifBlank { match.groupValues.getOrNull(2).orEmpty() }
                    .trim()
                if (!time.isTimestampLike()) return@mapNotNull null
                TimedMarker(match.range.first, match.range.last + 1, time.parseFlexibleTime().toLong())
            }
            .toList()
        if (markers.isEmpty()) return emptyList()

        val relativeTiming = markers.first().timeMs < lineStartMs &&
            abs(markers.first().timeMs - lineStartMs) > 2_000L
        val words = mutableListOf<LyricWord>()
        var activeStart = if (relativeTiming) 0L else lineStartMs
        var textStart = 0
        var pendingLeadingSpace = false

        markers.forEach { marker ->
            if (marker.timeMs < activeStart) return@forEach
            val rawText = content.substring(textStart, marker.startIndex)
            var text = rawText.cleanTimedLyricSegment()
            if (text.isBlank() && rawText.any(Char::isWhitespace)) {
                pendingLeadingSpace = true
            } else if (pendingLeadingSpace && text.firstOrNull()?.isWhitespace() != true) {
                text = " $text"
                pendingLeadingSpace = false
            }
            if (text.isNotBlank()) {
                words += LyricWord(
                    text = text,
                    startMs = activeStart,
                    endMs = marker.timeMs.coerceAtLeast(activeStart + 120L)
                )
            }
            activeStart = marker.timeMs
            textStart = marker.endIndex
        }

        var tail = content.substring(textStart).cleanTimedLyricSegment()
        if (pendingLeadingSpace && tail.isNotBlank() && tail.firstOrNull()?.isWhitespace() != true) {
            tail = " $tail"
        }
        tail.takeIf { it.isNotBlank() }?.let {
            words += LyricWord(
                text = it,
                startMs = activeStart,
                endMs = activeStart + estimateDuration(it)
            )
        }

        if (words.isEmpty()) return emptyList()

        return if (relativeTiming) {
            words.map { it.copy(startMs = it.startMs + lineStartMs, endMs = it.endMs + lineStartMs) }
        } else {
            words
        }
    }

    private data class TimedMarker(
        val startIndex: Int,
        val endIndex: Int,
        val timeMs: Long
    )

    private fun parseLyricify(content: String): LrcParser.LrcResult? {
        val parsed = content.lines()
            .mapNotNull { raw ->
                val line = raw.trim()
                if (line.isBlank() || lrcMetaPattern.matches(line)) return@mapNotNull null

                val attr = lyricifyAttributePattern.find(line)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val real = lyricifyAttributePattern.replace(line, "")
                val words = lyricifySyllablePattern.findAll(real)
                    .mapNotNull { match ->
                        val text = match.groupValues[1]
                        val start = match.groupValues[2].toLongOrNull() ?: return@mapNotNull null
                        val duration = match.groupValues[3].toLongOrNull() ?: return@mapNotNull null
                        LyricWord(text, start, start + duration)
                    }
                    .toList()
                if (words.isEmpty()) return@mapNotNull null

                val isBackground = attr != null && attr !in 0..5
                val agent = if (attr == 2 || attr == 5 || attr == 8) "v2" else "v1"
                val text = words.joinLyricText()
                if (text.isIgnorableLyricText()) return@mapNotNull null

                if (isBackground) {
                    LyricLine(
                        timeMs = words.first().startMs,
                        text = "",
                        backgroundText = text,
                        backgroundWords = words.toDisplayWords(text),
                        agent = agent,
                        isTtml = true,
                        endMs = words.last().endMs
                    )
                } else {
                    LyricLine(
                        timeMs = words.first().startMs,
                        text = text,
                        words = words.toDisplayWords(text),
                        agent = agent,
                        isTtml = true,
                        endMs = words.last().endMs
                    )
                }
            }
        return LrcParser.LrcResult(lyrics = attachBackgroundLines(parsed)).takeIf { it.lyrics.isNotEmpty() }
    }

    private fun String.leadingLrcTimeMatches(): List<MatchResult> {
        val result = mutableListOf<MatchResult>()
        var cursor = 0
        lrcTimePattern.findAll(this).forEach { match ->
            if (substring(cursor, match.range.first).isNotBlank()) return result
            result += match
            cursor = match.range.last + 1
        }
        return result
    }

    private fun mergeCompanionLines(lines: List<LyricLine>): List<LyricLine> {
        val merged = lines
            .sortedBy { it.timeMs }
            .groupBy { it.timeMs }
            .values
            .flatMap { group ->
                if (group.size == 1) return@flatMap listOf(group.first())
                if (group.shouldKeepIndependentDuetLines()) {
                    return@flatMap group.sortedBy { it.agentSortOrder() }
                }
                val hasRomanizedCompanion = group.size >= 3 && group.any { it.text.isPronunciationLine() }
                val primary = if (hasRomanizedCompanion) {
                    group.firstOrNull { it.text.cleanLyricText().hasCjk() && it.text.isUsefulMainText() }
                } else {
                    val cjkCandidates = group.filter { it.text.cleanLyricText().hasCjk() && it.text.isUsefulMainText() }
                    if (cjkCandidates.size >= 2) {
                        cjkCandidates.firstOrNull {
                            val t = it.text.cleanLyricText()
                            t.hasJapaneseKana() && !t.isLyricCreditLine()
                        } ?: cjkCandidates.firstOrNull { it.text.cleanLyricText().hasJapaneseKana() }
                    } else null
                } ?: group.firstOrNull { it.text.isUsefulMainText() } ?: group.first()
                val primaryText = primary.text.cleanLyricText()
                val pronunciation = group
                    .takeIf { it.size >= 3 && primaryText.hasCjk() }
                    ?.firstOrNull { it !== primary && it.text.isPronunciationLine() }
                val translationCandidates = group
                    .asSequence()
                    .filter { it !== primary && it !== pronunciation }
                    .map { it.text.cleanLyricText() }
                    .filter { it.isUsefulMainText() && it != primaryText }
                    .toList()
                val preferredTranslation = translationCandidates
                    .firstOrNull { primaryText.hasCjk() && it.hasCjk() }
                    ?: translationCandidates.firstOrNull()
                val translation = (listOfNotNull(preferredTranslation) + translationCandidates)
                    .distinct()
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
                listOf(
                    primary.copy(
                        translation = primary.translation.mergeLyricCompanionText(translation),
                        pronunciation = primary.pronunciation ?: pronunciation?.text?.cleanLyricText(),
                        pronunciationWords = primary.pronunciationWords.ifEmpty { pronunciation?.words.orEmpty() },
                        endMs = primary.endMs ?: group.mapNotNull { it.endMs }.maxOrNull()
                    )
                )
            }
        val timeMerged = attachBackgroundLines(merged)
        return mergeNearbyCompanionLines(timeMerged)
    }

    private fun mergeNearbyCompanionLines(lines: List<LyricLine>): List<LyricLine> {
        // Only exact timestamp groups are reliable lyric companions. A previous 500 ms
        // proximity heuristic turned independently timed credits such as "词：" and "曲："
        // into a primary line plus translation, and could also swallow closely sung lyrics.
        // Exact groups have already been merged above, so preserve every remaining line.
        return lines
    }

    private fun List<LyricLine>.shouldKeepIndependentDuetLines(): Boolean =
        mapNotNull { line ->
            line.agent
                ?.trim()
                ?.takeIf { it.isNotBlank() && line.text.isUsefulMainText() }
        }.distinct().size >= 2

    private fun LyricLine.agentSortOrder(): Int =
        when (agent?.trim()?.lowercase()) {
            "v1" -> 0
            "v2" -> 1
            else -> 2
        }

    private fun attachBackgroundLines(lines: List<LyricLine>): List<LyricLine> {
        val result = mutableListOf<LyricLine>()
        lines.sortedBy { it.timeMs }.forEach { line ->
            if (line.text.isBlank() && !line.backgroundText.isNullOrBlank()) {
                val targetIndex = result.indexOfLast { abs(it.timeMs - line.timeMs) <= 350L }
                if (targetIndex >= 0) {
                    val target = result[targetIndex]
                    result[targetIndex] = target.copy(
                        backgroundText = target.backgroundText ?: line.backgroundText,
                        backgroundWords = target.backgroundWords.ifEmpty { line.backgroundWords },
                        backgroundTranslation = target.backgroundTranslation
                            ?: line.backgroundTranslation
                            ?: line.backgroundText.takeIf {
                                !target.backgroundText.isNullOrBlank() && it != target.backgroundText
                            },
                        backgroundStartMs = target.backgroundStartMs ?: line.backgroundStartMs ?: line.timeMs,
                        backgroundEndMs = target.backgroundEndMs ?: line.backgroundEndMs ?: line.endMs,
                        endMs = listOfNotNull(target.endMs, line.endMs).maxOrNull()
                    )
                } else {
                    result += line
                }
            } else {
                result += line
            }
        }
        return result
    }

    private fun parseLrcTime(groups: List<String>): Long {
        val minutes = groups[1].toLongOrNull() ?: 0L
        val seconds = groups[2].toLongOrNull() ?: 0L
        val millisRaw = groups.getOrNull(3).orEmpty()
        val millis = when (millisRaw.length) {
            0 -> 0L
            1 -> millisRaw.toLongOrNull()?.times(100) ?: 0L
            2 -> millisRaw.toLongOrNull()?.times(10) ?: 0L
            else -> millisRaw.take(3).toLongOrNull() ?: 0L
        }
        return minutes * 60_000 + seconds * 1000 + millis
    }

    private fun String.isTimestampLike(): Boolean = timestampOnlyPattern.matches(trim().replace(',', '.'))

    fun isPlaceholderOnlyLine(line: String): Boolean =
        lrcTimePattern.replace(line.cleanLyricText(), "")
            .replace(Regex("""\s+"""), "")
            .let { it == "//" || it == "／／" }

    fun isIgnorableRawLyricLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return true
        if (lrcGenericMetaPattern.matches(trimmed)) return true
        val withoutTimes = lrcTimePattern.replace(trimmed, "").trim()
        if (isPlaceholderOnlyLine(trimmed)) return true
        return withoutTimes != trimmed && (
            withoutTimes.isBlank() ||
                isPlaceholderOnlyLine(withoutTimes)
            )
    }

    fun isHeaderTagLine(line: String): Boolean {
        val trimmed = line.trim()
        val withoutTimes = lrcTimePattern.replace(trimmed, "").trim()
        val content = withoutTimes.takeIf { it.isNotBlank() } ?: trimmed
        return content.startsWith("[kana:", ignoreCase = true) ||
            content.startsWith("[trans:", ignoreCase = true) ||
            content.startsWith("[roma:", ignoreCase = true)
    }

    private fun String.cleanTimedLyricSegment(): String =
        replace(timedWordMarkerPattern) { match ->
                val time = match.groupValues.getOrNull(1).orEmpty()
                    .ifBlank { match.groupValues.getOrNull(2).orEmpty() }
                    .trim()
                if (time.isTimestampLike()) "" else match.value
            }
            .decodeHtmlCompat()
            .replace(Regex("""[ \t\r\n]+"""), " ")

    private fun String.isUsefulMainText(): Boolean = isNotBlank() && !isMusicSymbolOnly()

    private fun String.hasJapaneseKana(): Boolean =
        any {
            val block = Character.UnicodeBlock.of(it)
            block == Character.UnicodeBlock.HIRAGANA || block == Character.UnicodeBlock.KATAKANA
        }

    private val creditPrefixPattern = Regex(
        "^(作词|作詞|作曲|编曲|編曲|原唱|翻唱|制作|製作|演唱|录音|錄音|混音|监制|監製|企划|企劃|出品|填词|填詞|歌手|歌|曲|词|詞|Lyrics|Music|Arrangement|Compose[rd]?|Vocal|Mix|Produce[rd]?)[：:]",
        RegexOption.IGNORE_CASE
    )

    private fun String.isLyricCreditLine(): Boolean =
        isLyricExtraInfoLine(this)

    fun isLyricExtraInfoLine(line: String): Boolean =
        creditPrefixPattern.containsMatchIn(
            lrcTimePattern.replace(line.trim(), "").trim()
        )

    private fun String.isPronunciationLine(): Boolean {
        val text = cleanLyricText()
        if (text.isBlank() || text.hasCjk() || text.isMusicSymbolOnly()) return false
        val letters = text.count { it.isLetter() }
        return letters >= 2 && text.all {
            it.isLetter() ||
                it.isWhitespace() ||
                it in "-'`.:,;!?/()[]{}" ||
                it in setOf('‘', '’', '“', '”', 'ʼ', '・', '·')
        }
    }
}
