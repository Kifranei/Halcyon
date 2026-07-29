package com.ella.music.data.parser

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.mocharealm.accompanist.lyrics.core.model.ISyncedLine
import com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeLine
import com.mocharealm.accompanist.lyrics.core.model.synced.SyncedLine
import com.mocharealm.accompanist.lyrics.core.parser.AutoParser

internal object AccompanistLyricsParser {
    private val parser = AutoParser()

    fun parse(content: String): LrcParser.LrcResult? {
        if (!parser.canParse(content)) return null
        val syncedLyrics = runCatching { parser.parse(content) }.getOrNull() ?: return null
        val isTtmlFormat = content.contains("<tt", ignoreCase = true) &&
            content.contains("</tt", ignoreCase = true)
        val lines = syncedLyrics.lines
            .mapNotNull { toLyricLine(it, isTtmlFormat) }
            .filterNot { line ->
                val text = line.text.ifBlank { line.backgroundText.orEmpty() }
                text.isBlank() || EllaLyricsParser.isIgnorableRawLyricLine(text)
            }
            .sortedBy { it.timeMs }
            .mergeSameTimestampCompanions()
        if (lines.isEmpty()) return null
        return LrcParser.LrcResult(
            lyrics = lines,
            title = syncedLyrics.title.takeIf { it.isNotBlank() },
            artist = syncedLyrics.artists
                ?.joinToString("/") { it.name }
                ?.takeIf { it.isNotBlank() }
        )
    }

    private fun toLyricLine(line: ISyncedLine, isTtmlFormat: Boolean): LyricLine? {
        return when (line) {
            is KaraokeLine.MainKaraokeLine -> line.toMainLyricLine(isTtmlFormat)
            is KaraokeLine.AccompanimentKaraokeLine -> line.toBackgroundLyricLine(isTtmlFormat)
            is SyncedLine -> line.toPlainLyricLine()
            else -> null
        }
    }

    private fun KaraokeLine.MainKaraokeLine.toMainLyricLine(isTtmlFormat: Boolean): LyricLine? {
        val mainParts = syllables.toTimedTextParts(isTtmlFormat, isBackground = false).withoutElrcAgentPrefix()
        val textParts = mainParts.parts
        val text = textParts.toDisplayText().trimMeaningful()
        if (text.isBlank() || EllaLyricsParser.isPlaceholderOnlyLine(text)) return null
        val background = accompanimentLines?.firstOrNull()
        val rawBgParts = background?.syllables.orEmpty()
            .toTimedTextParts(isTtmlFormat, isBackground = true).withoutElrcAgentPrefix().parts
        // Fix x-bg/accompaniment text that comes as a concatenated Latin blob
        // (e.g. "Andthere'salotofcoolchicksoutthere" from spans with no inter-word spacing).
        // Split into proper words and estimate per-word timing so it animates correctly.
        val (bgFixedText, bgFixedWords) = rawBgParts.fixBackgroundSpacingAndTiming(
            bgStart = background?.start?.toLong(),
            bgEnd = background?.end?.toLong()
        )
        return LyricLine(
            timeMs = start.toLong().coerceAtLeast(0L),
            text = text,
            words = textParts.toLyricWords(),
            translation = translation.takeUsefulSecondaryText(),
            pronunciation = phonetic.takeUsefulSecondaryText(),
            agent = mainParts.agent ?: alignment.toEllaAgent(),
            backgroundText = bgFixedText,
            backgroundWords = bgFixedWords,
            backgroundTranslation = background?.translation.takeUsefulSecondaryText(),
            backgroundStartMs = background?.start?.toLong()?.coerceAtLeast(0L),
            backgroundEndMs = background?.end?.toSafeEndMs(),
            isTtml = isTtmlFormat,
            endMs = end.toSafeEndMs()
        )
    }

    private fun KaraokeLine.AccompanimentKaraokeLine.toBackgroundLyricLine(isTtmlFormat: Boolean): LyricLine? {
        val parsedParts = syllables.toTimedTextParts(isTtmlFormat, isBackground = true).withoutElrcAgentPrefix()
        val rawParts = parsedParts.parts
        val (bgFixedText, bgFixedWords) = rawParts.fixBackgroundSpacingAndTiming(
            bgStart = start.toLong(),
            bgEnd = end.toLong()
        )
        if (bgFixedText.isNullOrBlank() || EllaLyricsParser.isPlaceholderOnlyLine(bgFixedText)) return null
        return LyricLine(
            timeMs = start.toLong().coerceAtLeast(0L),
            text = "",
            backgroundText = bgFixedText,
            backgroundWords = bgFixedWords,
            backgroundTranslation = translation.takeUsefulSecondaryText(),
            backgroundStartMs = start.toLong().coerceAtLeast(0L),
            backgroundEndMs = end.toSafeEndMs(),
            agent = parsedParts.agent ?: alignment.toEllaAgent(),
            isTtml = isTtmlFormat,
            endMs = end.toSafeEndMs()
        )
    }

    private fun SyncedLine.toPlainLyricLine(): LyricLine? {
        val parsedContent = content.withoutElrcAgentPrefix()
        val text = parsedContent.text.trimMeaningful()
        if (text.isBlank() || EllaLyricsParser.isPlaceholderOnlyLine(text)) return null
        return LyricLine(
            timeMs = start.toLong().coerceAtLeast(0L),
            text = text,
            translation = translation.takeUsefulSecondaryText(),
            agent = parsedContent.agent,
            endMs = end.toSafeEndMs()
        )
    }

    private data class TimedTextPart(
        val text: String,
        val startMs: Long,
        val endMs: Long
    )

    private data class AgentTimedTextParts(
        val agent: String?,
        val parts: List<TimedTextPart>
    )

    private data class AgentText(
        val agent: String?,
        val text: String
    )

    private fun List<com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeSyllable>.toTimedTextParts(
        preserveLatinWordSpaces: Boolean,
        isBackground: Boolean = false
    ): List<TimedTextPart> {
        val result = mutableListOf<TimedTextPart>()
        var pendingLeadingSpace = false
        forEachIndexed { index, syllable ->
            val startMs = syllable.start.toLong().coerceAtLeast(0L)
            val endMs = syllable.end.toLong().coerceAtLeast(startMs + 1L)
            val rawText = syllable.content
                .normalizeTimedTokenText(preserveLatinWordSpaces, trimEnd = index == lastIndex)
            if (rawText.isBlank()) {
                if (syllable.content.any(Char::isWhitespace)) {
                    pendingLeadingSpace = true
                }
                return@forEachIndexed
            }
            val previous = getOrNull(index - 1)
            val needsInsertedLeadingSpace =
                pendingLeadingSpace ||
                    (
                        previous != null &&
                                shouldInsertLatinWordSpace(
                                    previous.content,
                                    syllable.content,
                                    previous.content.normalizeTimedTokenText(preserveLatinWordSpaces, trimEnd = false),
                                    rawText,
                                    aggressive = isBackground
                                )
                            )
            val text = if (needsInsertedLeadingSpace && rawText.firstOrNull()?.isWhitespace() != true) {
                " $rawText"
            } else {
                rawText
            }
            pendingLeadingSpace = false
            result += TimedTextPart(text = text, startMs = startMs, endMs = endMs)
        }
        return result
    }

    private fun List<TimedTextPart>.toDisplayText(): String =
        joinToString(separator = "") { it.text }.trimMeaningful()

    private fun List<TimedTextPart>.withoutElrcAgentPrefix(): AgentTimedTextParts {
        if (isEmpty()) return AgentTimedTextParts(agent = null, parts = this)
        val first = first()
        val stripped = first.text.withoutElrcAgentPrefix()
        if (stripped.agent == null) return AgentTimedTextParts(agent = null, parts = this)
        val updated = buildList {
            val firstText = stripped.text
            if (firstText.isNotBlank()) {
                add(first.copy(text = firstText))
            }
            addAll(drop(1))
        }
        return AgentTimedTextParts(agent = stripped.agent, parts = updated)
    }

    private fun List<TimedTextPart>.toLyricWords(): List<LyricWord> =
        mapNotNull { part ->
            part.text.takeIf { it.isNotBlank() }?.let { text ->
                LyricWord(text = text, startMs = part.startMs, endMs = part.endMs)
            }
        }

    private fun List<TimedTextPart>.toBackgroundLyricWords(): List<LyricWord> =
        mapNotNull { part ->
            part.text.normalizeBackgroundAsideText()
                .takeIf { it.isNotBlank() }
                ?.let { text ->
                    LyricWord(text = text, startMs = part.startMs, endMs = part.endMs)
                }
        }

    private fun shouldInsertLatinWordSpace(
        previousRaw: String,
        currentRaw: String,
        previousNormalized: String,
        currentNormalized: String,
        aggressive: Boolean = false
    ): Boolean {
        val hasExplicitWhitespaceBoundary =
            previousRaw.lastOrNull()?.isWhitespace() == true || currentRaw.firstOrNull()?.isWhitespace() == true
        if (!hasExplicitWhitespaceBoundary) {
            // For background/accompaniment lines (x-bg), spans are typically word-level (not syllable-level),
            // so we aggressively insert spaces between adjacent Latin-letter spans even without explicit whitespace.
            if (!aggressive) return false
        }
        if (previousNormalized.lastOrNull()?.isWhitespace() == true) return false
        if (currentNormalized.firstOrNull()?.isWhitespace() == true) return false
        // eLRC authoring commonly keeps an intentional separator before a CJK/Latin boundary
        // (e.g. "よ Day"). Preserve that authored whitespace before applying language heuristics.
        if (hasExplicitWhitespaceBoundary) return true
        val prev = previousNormalized.lastOrNull { !it.isWhitespace() } ?: return false
        val next = currentNormalized.firstOrNull { !it.isWhitespace() } ?: return false
        if (prev.isCjkWordChar() || next.isCjkWordChar()) return false
        return true
    }

    private fun Char.isCjkWordChar(): Boolean {
        val block = Character.UnicodeBlock.of(this)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA ||
            block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }

    private fun String.normalizeTimedTokenText(preserveLatinWordSpaces: Boolean, trimEnd: Boolean): String =
        if (preserveLatinWordSpaces) {
            // Keep a leading separator on timed eLRC text. It carries visible spacing between
            // tokens and is later moved to the preceding render word for stable wrapping.
            replace(Regex("""\s+"""), " ").trimEnd()
        } else if (trimEnd) {
            trimEnd()
        } else {
            this
        }

    private fun Int.toSafeEndMs(): Long? =
        takeIf { it in 0 until Int.MAX_VALUE }?.toLong()

    private fun String.trimMeaningful(): String =
        trim().replace(Regex("""[ \t\r\n]+"""), " ")

    private fun String.withoutElrcAgentPrefix(): AgentText {
        val match = Regex("""^\s*(v[12])\s*[:：]\s*""", RegexOption.IGNORE_CASE).find(this)
            ?: return AgentText(agent = null, text = this)
        return AgentText(
            agent = match.groupValues[1].lowercase(),
            text = removeRange(match.range)
        )
    }

    private fun String?.takeUsefulSecondaryText(): String? =
        this
            ?.trimMeaningful()
            ?.takeIf { it.isNotBlank() && !EllaLyricsParser.isPlaceholderOnlyLine(it) && !EllaLyricsParser.isIgnorableRawLyricLine(it) }

    private fun String.normalizeBackgroundAsideText(): String =
        trimMeaningful()
            .replace(Regex("""^[（(]+\s*"""), "")
            .replace(Regex("""\s*[）)]+$"""), "")
            .trimMeaningful()

    private fun com.mocharealm.accompanist.lyrics.core.model.karaoke.KaraokeAlignment.toEllaAgent(): String? =
        when (name.lowercase()) {
            "start" -> "v1"
            "end" -> "v2"
            else -> null
        }

    private fun List<LyricLine>.mergeSameTimestampCompanions(): List<LyricLine> {
        return groupBy { it.timeMs }
            .values
            .flatMap { group ->
                if (group.size == 1) return@flatMap group
                if (group.shouldKeepIndependentDuetLines()) {
                    return@flatMap group.sortedBy { it.agentSortOrder() }
                }
                // Prefer non-credit lines as primary so credit/metadata lines (e.g. "Composed by:")
                // don't absorb actual lyric lines as translations.
                val primary = group.firstOrNull { it.words.isNotEmpty() && it.text.isUsefulMainText() && !it.text.isCreditLine() }
                    ?: group.firstOrNull { it.text.isUsefulMainText() && !it.text.isCreditLine() }
                    ?: group.firstOrNull { it.words.isNotEmpty() && it.text.isUsefulMainText() }
                    ?: group.firstOrNull { it.text.isUsefulMainText() }
                    ?: group.first()
                val primaryText = primary.text.trimMeaningful()
                val primaryTranslationAsPronunciation = primary.translation
                    ?.takeIf { primaryText.hasCjk() && it.isPronunciationFor(primaryText) }
                    ?.trimMeaningful()
                // Filter out credit/metadata lines (e.g. "Lyrics by:", "Composed by:")
                // from being treated as translation companions
                val companions = group.filter { it !== primary && !it.text.isCreditLine() }
                val pronunciation = companions
                    .firstOrNull { primaryText.hasCjk() && it.text.isPronunciationFor(primaryText) }
                    ?.text
                    ?.trimMeaningful()
                val translation = companions
                    .asSequence()
                    .filter { it.text.isUsefulMainText() }
                    .map { it.text.trimMeaningful() }
                    .filter { it != primaryText && it != pronunciation }
                    .distinct()
                    .joinToString("\n")
                    .takeIf { it.isNotBlank() }
                listOf(
                    primary.copy(
                        translation = primary.translation
                            ?.takeUnless { it == primaryTranslationAsPronunciation }
                            .mergeText(translation),
                        pronunciation = primary.pronunciation ?: pronunciation ?: primaryTranslationAsPronunciation,
                        endMs = group.mapNotNull { it.endMs }.maxOrNull() ?: primary.endMs
                    )
                )
            }
            .sortedBy { it.timeMs }
    }

    private fun List<LyricLine>.shouldKeepIndependentDuetLines(): Boolean =
        mapNotNull { line ->
            line.agent
                ?.trim()
                ?.lowercase()
                ?.takeIf { it in setOf("v1", "v2") && line.text.isUsefulMainText() }
        }.distinct().size >= 2

    private fun LyricLine.agentSortOrder(): Int =
        when (agent?.trim()?.lowercase()) {
            "v1" -> 0
            "v2" -> 1
            else -> 2
        }

    private fun String?.mergeText(extra: String?): String? =
        listOfNotNull(this?.takeIf { it.isNotBlank() }, extra?.takeIf { it.isNotBlank() })
            .distinct()
            .joinToString("\n")
            .takeIf { it.isNotBlank() }

    private fun String.isUsefulMainText(): Boolean =
        trimMeaningful().isNotBlank() && !EllaLyricsParser.isPlaceholderOnlyLine(this)

    private fun String.hasCjk(): Boolean =
        any { char ->
            val block = Character.UnicodeBlock.of(char)
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
                block == Character.UnicodeBlock.HIRAGANA ||
                block == Character.UnicodeBlock.KATAKANA ||
                block == Character.UnicodeBlock.HANGUL_SYLLABLES
        }

    private fun String.isPronunciationFor(primaryText: String): Boolean {
        val text = trimMeaningful()
        if (text.isBlank() || text.hasCjk()) return false
        return primaryText.hasCjk() && text.any { it.isLetter() }
    }

    private fun String.isCreditLine(): Boolean =
        creditLinePattern.containsMatchIn(trimMeaningful())

    // Broader check that also catches "ArtistName:" style metadata lines
    // (e.g. "Adam Levine:", "Taylor Swift:") that don't start with known credit keywords
    private fun String.isCreditOrMetadataLine(): Boolean {
        val trimmed = trimMeaningful()
        if (trimmed.isBlank()) return false
        if (isCreditLine()) return true
        // Match lines like "Adam Levine:" — a name/label ending with colon,
        // short enough to be metadata rather than actual lyrics
        if (metadataLabelColonPattern.containsMatchIn(trimmed)) return true
        return false
    }

    private val creditLinePattern = Regex(
        "^(作词|作曲|编曲|原唱|翻唱|制作|演唱|录音|混音|监制|企划|出品|填词|歌手|歌|曲|词" +
            "|Lyrics|Music|Arrangement|Compos(?:ed|e|er|rd)?|Vocal|Mix|Produce[rd]?)" +
            "\\s*(?:by\\s*)?[：:]",
        RegexOption.IGNORE_CASE
    )

    // Matches lines like "Adam Levine:" or "Taylor Swift feat. Ed Sheeran:"
    // — name-like text followed by colon, typically metadata/credit annotations in TTML.
    // Requires: starts with uppercase letter, contains only label-like content, ends with ":"
    private val metadataLabelColonPattern = Regex(
        "^[A-Z][A-Za-z\u00C0-\u024F\u4e00-\u9fff]+" +  // Name part (Latin/CJK)
            "(?:\\s+[A-Za-z\u00C0-\u024F\u4e00-\u9fff]+)*" +  // Optional more name parts
            "(?:\\s+(?:feat|ft|featuring|with|\\&|and)\\.?\\s+" +  // Optional collaboration
            "[A-Z][A-Za-z\u00C0-\u024F\\s]+)?" +  // Collaborator name
            "\\s*[：:]$"  // Ends with colon
    )

    // ─── Background/accompaniment text post-processing ──────────────────────

    /**
     * Detects when x-bg/accompaniment text comes as a concatenated Latin blob
     * (e.g. "Andthere'salotofcoolchicksoutthere" because TTML spans had no inter-word
     * whitespace). Splits into proper words with estimated per-word timing so the
     * background line renders correctly and animates per-word like v1/v2 lyrics.
     *
     * Returns a pair of (display text, per-word LyricWord list). If the input already
     * looks well-spaced, returns it as-is.
     */
    private fun List<TimedTextPart>.fixBackgroundSpacingAndTiming(
        bgStart: Long?,
        bgEnd: Long?
    ): Pair<String?, List<LyricWord>> {
        val rawDisplay = toDisplayText().normalizeBackgroundAsideText()
            .trimMeaningful().takeIf { it.isNotBlank() } ?: return null to emptyList()

        val rawWords = toBackgroundLyricWords()

        // If the text has reasonable spacing (space-to-letter ratio above threshold),
        // or if it contains CJK characters (which don't use spaces between words),
        // return as-is — nothing to fix.
        if (!rawDisplay.needsLatinWordSplit()) return rawDisplay to rawWords

        // Strategy 1: If we have multiple words with proper per-word text/timing
        // (from separate TTML spans), use them directly. The display text was likely
        // concatenated because spans were adjacent without inter-span whitespace.
        if (rawWords.size > 1) {
            val wordTexts = rawWords.map { it.text.trim() }.filter { it.isNotBlank() }
            if (wordTexts.size > 1) {
                val fixedDisplay = wordTexts.joinToString(" ")
                return fixedDisplay to rawWords
            }
        }

        // Strategy 2: Single span/syllable with all text concatenated.
        // We can't reliably split without a dictionary, so return as-is with
        // at least one LyricWord for rendering.
        if (rawWords.isEmpty()) {
            return rawDisplay to listOf(
                LyricWord(
                    text = rawDisplay,
                    startMs = bgStart ?: 0L,
                    endMs = bgEnd ?: (bgStart ?: 0L) + 3000L
                )
            )
        }
        return rawDisplay to rawWords
    }

    /** Heuristic: does this text look like concatenated Latin words missing spaces? */
    private fun String.needsLatinWordSplit(): Boolean {
        if (this.hasCjk()) return false  // CJK doesn't use spaces between chars

        // Count spaces vs letters. Well-spaced English has ~1 space per 5-6 chars.
        // Concatenated blob has 0 or very few spaces for many letters.
        val spaceCount = count { it == ' ' }
        val letterCount = count { it.isLetter() }

        if (letterCount < 8) return false  // Too short to be a "blob"

        // Ratio check: if fewer than 1 space per ~10 letters, likely needs splitting
        if (spaceCount > 0 && letterCount / spaceCount.toDouble() < 9.0) return false

        // Pattern check: look for lowercase→uppercase transitions without preceding space
        // (e.g. "coolChicks", "callHome")
        var i = 1
        while (i < length) {
            if (this[i].isUpperCase() && this[i - 1].isLowerCase() && (i == 1 || this[i - 2] != ' ')) {
                return true  // Found camelCase-like boundary → definitely needs split
            }
            i++
        }

        // Fallback: long runs of lowercase (>12 chars) with no internal spaces
        val maxLowercaseRun = fold(0) { max, ch -> 
            if (ch.isLowerCase() || ch == '\'') max + 1 else 0 
        }
        if (maxLowercaseRun > 14) return true

        return false
    }

    private fun estimateWordWeight(text: String): Float =
        text.filter { it.isLetterOrDigit() }.length.toFloat().coerceAtLeast(1f)
}
