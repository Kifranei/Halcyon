package com.ella.music.data.parser

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import java.io.StringReader
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import kotlin.math.abs

private val unknownTtmlAgentIdPattern = Regex("""v\d+|agent\d+""", RegexOption.IGNORE_CASE)

internal fun parseTtml(content: String): LrcParser.LrcResult? {
    if (!content.contains("<tt", ignoreCase = true)) return null
    return runCatching {
        val document = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isIgnoringComments = true
            isCoalescing = true
            trySetFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            trySetFeature("http://xml.org/sax/features/external-general-entities", false)
            trySetFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }.newDocumentBuilder().parse(InputSource(StringReader(content.preformatTtml())))

        val root = document.documentElement
        val metadata = parseTtmlMetadata(root)
        val agentInfo = parseAgentInfo(root)
        val translations = parseTimedTextMap(root, "translations", "translation")
        val transliterations = parseTransliterations(root)
        val forceLineTiming = root.attr("itunes:timing")
            .ifBlank { root.attr("timing") }
            .equals("Line", ignoreCase = true)
        val paragraphs = root.allElements()
            .filter { it.localTagName() == "p" }

        val lines = paragraphs.mapNotNull { p ->
            val start = p.attr("begin").parseTtmlTime() ?: return@mapNotNull null
            val end = p.attr("end").parseTtmlTime()
            val key = p.attr("itunes:key").ifBlank { p.attr("key") }
            val rawAgent = p.attr("ttm:agent").ifBlank { p.attr("agent") }
            val agentIds = rawAgent.toTtmlAgentIds()
            val displayAgentName = agentIds.resolveTtmlAgentNames(agentInfo)
            val words = mutableListOf<LyricWord>()
            val rubyPronunciationWords = mutableListOf<LyricWord>()
            val collectedText = collectTtmlMainText(p, words, end, rubyPronunciationWords).cleanLyricText()
            val text = if (
                collectedText.isNotBlank() &&
                !collectedText.contains(' ') &&
                !collectedText.hasCjk() &&
                words.size > 1
            ) {
                words.joinToString(" ") { it.text.cleanLyricText() }.cleanLyricText()
            } else {
                collectedText
            }
            val displayText = text.takeUnless { it.isIgnorableLyricText() }.orEmpty()
            val displayWords = words.toTtmlDisplayWords(
                lineText = displayText,
                lineStart = start,
                lineEnd = end,
                forceLineTiming = forceLineTiming
            )
            val inlineTranslation = p.childrenElements()
                .firstOrNull { it.hasRole("x-translation") && !it.hasRole("x-bg") }
                ?.textContent
                ?.cleanLyricSecondaryText()
            val bg = p.childrenElements()
                .firstOrNull { it.hasRole("x-bg") }
                ?.parseTtmlBackground(end, translations[key])
            val linePronunciation = p.childrenElements()
                .firstOrNull { it.hasAnyRole("x-roman", "x-romanization") }
                ?.textContent
                ?.cleanLyricSecondaryText()
            val transliteration = transliterations[key]
            val pronunciationWords = when {
                transliteration?.words?.isNotEmpty() == true -> transliteration.words.alignPronunciationWords(displayWords, text)
                rubyPronunciationWords.isNotEmpty() -> rubyPronunciationWords
                else -> emptyList()
            }
            val pronunciation = linePronunciation
                ?: transliteration?.text?.takeUsefulSecondaryText()
                ?: rubyPronunciationWords.joinLyricText().takeIf { it.isNotBlank() }
                ?: pronunciationWords.joinLyricText().takeIf { it.isNotBlank() }

            if (displayText.isBlank() && bg == null) return@mapNotNull null

            LyricLine(
                timeMs = start,
                text = displayText,
                words = displayWords,
                translation = inlineTranslation?.takeUsefulSecondaryText() ?: translations[key]?.splitAppleTranslation()?.first,
                pronunciation = pronunciation?.takeUsefulSecondaryText(),
                pronunciationWords = pronunciationWords.toDisplayWords(pronunciation.orEmpty()),
                agent = agentIds.firstOrNull() ?: rawAgent.takeIf(String::isNotBlank),
                agentName = displayAgentName,
                backgroundText = bg?.text,
                backgroundWords = bg?.words.orEmpty().toDisplayWords(bg?.text.orEmpty()),
                backgroundTranslation = bg?.translation,
                backgroundStartMs = bg?.startMs,
                backgroundEndMs = bg?.endMs,
                isTtml = true,
                endMs = end
            )
        }

        LrcParser.LrcResult(
            lyrics = assignTtmlAgentSides(lines.sortedBy { it.timeMs }, agentInfo),
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album
        )
    }.getOrNull()?.takeIf { it.lyrics.isNotEmpty() }
}

private data class TtmlMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null
)

private fun parseTtmlMetadata(root: Element): TtmlMetadata {
    var title: String? = null
    var artist: String? = null
    var album: String? = null

    root.allElements().forEach { element ->
        when (element.localTagName()) {
            "title" -> if (title == null) title = element.textContent.takeUsefulText()
            "meta" -> {
                val key = element.attr("key").trim()
                val value = element.attr("value")
                    .ifBlank { element.textContent.orEmpty() }
                    .takeUsefulText()
                    ?: return@forEach
                when (key) {
                    "musicName" -> title = title ?: value
                    "artists" -> artist = artist ?: value
                    "album" -> album = album ?: value
                }
            }
        }
    }

    return TtmlMetadata(title = title, artist = artist, album = album)
}

private data class TtmlAgentInfo(
    /** ttm:agent type: person / organization / group / other (lower-cased; may be blank). */
    val type: String,
    val name: String?
)

private fun DocumentBuilderFactory.trySetFeature(name: String, value: Boolean) {
    runCatching { setFeature(name, value) }
}

private fun parseAgentInfo(root: Element): Map<String, TtmlAgentInfo> {
    return root.allElements()
        .filter { it.localTagName() == "agent" }
        .mapNotNull { agent ->
            val id = agent.attr("xml:id").ifBlank { agent.attr("id") }.takeIf(String::isNotBlank)
                ?: return@mapNotNull null
            id to TtmlAgentInfo(
                type = agent.attr("type").lowercase(),
                name = agent.displayName()
            )
        }
        .toMap()
}

private fun String.toTtmlAgentIds(): List<String> =
    split(Regex("""[\s,;]+"""))
        .map { it.trim().trimStart('#') }
        .filter { it.isNotBlank() }
        .distinct()

private fun List<String>.resolveTtmlAgentNames(agentInfo: Map<String, TtmlAgentInfo>): String? {
    val names = mapNotNull { id ->
        agentInfo[id]?.name
            ?: id.takeUnless { unknownTtmlAgentIdPattern.matches(it) }
    }
        .map { it.trim() }
        .filter { it.isNotBlank() && !it.isMusicSymbolOnly() }
        .distinct()
    return names.takeIf { it.isNotEmpty() }?.joinToString("/")
}

/**
 * Assign each TTML line a display side (left = "v1", right = "v2") from its agent type, per the
 * AMLL convention:
 *  - group  -> always left
 *  - other  -> always right
 *  - person / organization (or untyped) -> the first one is left, then every switch to a
 *    different solo/organization agent flips to the opposite of the previous one; repeats of the
 *    same agent keep the current side. Group/other lines don't disturb that running side.
 * Lines must already be in playback (time) order.
 */
private fun assignTtmlAgentSides(
    lines: List<LyricLine>,
    agentInfo: Map<String, TtmlAgentInfo>
): List<LyricLine> {
    var prevSoloId: String? = null
    var prevSoloSide: String? = null
    return lines.map { line ->
        val id = line.agent
        val side = when (agentInfo[id]?.type) {
            "group" -> "v1"
            "other" -> "v2"
            else -> {
                val resolved = when {
                    prevSoloSide == null -> "v1"
                    id == prevSoloId -> prevSoloSide
                    else -> if (prevSoloSide == "v1") "v2" else "v1"
                }
                prevSoloId = id
                prevSoloSide = resolved
                resolved
            }
        }
        line.copy(agent = side)
    }
}

private fun parseTimedTextMap(root: Element, containerTag: String, itemTag: String): Map<String, String> {
    val result = mutableMapOf<String, String>()
    root.allElements()
        .filter { it.localTagName() == containerTag }
        .flatMap { it.childrenElements() }
        .filter { it.localTagName() == itemTag }
        .flatMap { it.childrenElements() }
        .filter { it.localTagName() == "text" }
        .plus(
            root.allElements()
                .filter { it.localTagName() == itemTag }
                .flatMap { it.childrenElements() }
                .filter { it.localTagName() == "text" }
        )
        .forEach { text ->
            val key = text.attr("for").ifBlank { return@forEach }
            val value = text.textContent.cleanLyricSecondaryText()
            if (value.isNotBlank()) result.putIfAbsent(key, value)
        }
    return result
}

private fun parseTransliterations(root: Element): Map<String, TtmlPronunciation> {
    val result = mutableMapOf<String, TtmlPronunciation>()
    root.allElements()
        .filter { it.localTagName() == "transliteration" }
        .flatMap { it.childrenElements() }
        .filter { it.localTagName() == "text" }
        .forEach { text ->
            val key = text.attr("for").ifBlank { return@forEach }
            val words = text.childrenElements()
                .filter { it.localTagName() == "span" }
                .mapNotNull { span ->
                    val value = span.textContent
                        .removeBackgroundParentheses()
                        .cleanLyricText()
                        .takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    val start = span.attr("begin").parseTtmlTime()
                    val end = span.attr("end").parseTtmlTime()
                    LyricWord(
                        text = value,
                        startMs = start ?: 0L,
                        endMs = end ?: start?.plus(estimateDuration(value)) ?: 0L
                    )
                }
            val plainText = text.textContent
                .removeBackgroundParentheses()
                .cleanLyricSecondaryText()
                .takeUsefulSecondaryText()
            if (!plainText.isNullOrBlank() || words.isNotEmpty()) {
                result[key] = TtmlPronunciation(
                    text = plainText.orEmpty(),
                    words = words
                )
            }
        }
    return result
}

private fun collectTtmlMainText(
    element: Element,
    words: MutableList<LyricWord>,
    fallbackEnd: Long?,
    pronunciationWords: MutableList<LyricWord> = mutableListOf()
): String {
    val builder = StringBuilder()
    element.childNodes.toNodeList().forEach { node ->
        when (node.nodeType) {
            Node.TEXT_NODE -> builder.append(node.nodeValue.orEmpty().withoutFormattingWhitespace())
            Node.ELEMENT_NODE -> {
                val child = node as? Element ?: return@forEach
                if (child.hasRole("x-translation") || child.hasRole("x-bg") || child.hasAnyRole("x-roman", "x-romanization")) {
                    return@forEach
                }
                when (child.rubyMode()) {
                    "container" -> {
                        val ruby = child.parseRubyTtml(fallbackEnd)
                        if (ruby.text.isNotBlank()) {
                            builder.append(ruby.text)
                            words += ruby.words
                            pronunciationWords += ruby.pronunciationWords
                        }
                        return@forEach
                    }
                    "textContainer", "text" -> {
                        pronunciationWords += child.collectRubyPronunciationWords(fallbackEnd)
                        return@forEach
                    }
                }
                val wordCountBefore = words.size
                val nested = collectTtmlMainText(child, words, fallbackEnd, pronunciationWords)
                val nestedAddedTimedWords = words.size > wordCountBefore
                val begin = child.attr("begin").parseTtmlTime()
                if (begin != null && nested.isNotBlank() && !nestedAddedTimedWords) {
                    words += LyricWord(
                        text = nested,
                        startMs = begin,
                        endMs = child.attr("end").parseTtmlTime()
                            ?: fallbackEnd
                            ?: begin + estimateDuration(nested)
                    )
                }
                builder.append(nested)
            }
        }
    }
    return builder.toString()
}

private data class TtmlRuby(
    val text: String,
    val words: List<LyricWord>,
    val pronunciationWords: List<LyricWord>
)

private fun Element.parseRubyTtml(fallbackEnd: Long?): TtmlRuby {
    val pronunciationWords = collectRubyPronunciationWords(fallbackEnd)
    val baseText = childrenElements()
        .filter { it.rubyMode() == "base" }
        .joinToString("") { it.textContent.orEmpty() }
        .cleanLyricText()
        .ifBlank {
            childNodes.toNodeList()
                .mapNotNull { node ->
                    when (node.nodeType) {
                        Node.TEXT_NODE -> node.nodeValue.orEmpty().withoutFormattingWhitespace()
                        Node.ELEMENT_NODE -> {
                            val child = node as? Element ?: return@mapNotNull null
                            child.takeUnless { it.rubyMode() in setOf("textContainer", "text") }
                                ?.textContent
                                .orEmpty()
                        }
                        else -> null
                    }
                }
                .joinToString("")
                .cleanLyricText()
        }
    if (baseText.isBlank()) return TtmlRuby("", emptyList(), pronunciationWords)

    val begin = attr("begin").parseTtmlTime() ?: pronunciationWords.minOfOrNull { it.startMs }
    val end = attr("end").parseTtmlTime()
        ?: pronunciationWords.maxOfOrNull { it.endMs }
        ?: fallbackEnd
        ?: begin?.plus(estimateDuration(baseText))
    val words = if (begin != null && end != null) {
        listOf(LyricWord(baseText, begin, end))
    } else {
        emptyList()
    }
    return TtmlRuby(baseText, words, pronunciationWords)
}

private fun Element.collectRubyPronunciationWords(fallbackEnd: Long?): List<LyricWord> {
    val result = mutableListOf<LyricWord>()
    fun visit(element: Element) {
        val mode = element.rubyMode()
        if (mode == "text") {
            val value = element.textContent.cleanLyricText()
            val begin = element.attr("begin").parseTtmlTime()
            if (value.isNotBlank() && begin != null) {
                result += LyricWord(
                    text = value,
                    startMs = begin,
                    endMs = element.attr("end").parseTtmlTime()
                        ?: fallbackEnd
                        ?: begin + estimateDuration(value)
                )
            }
        }
        element.childrenElements().forEach(::visit)
    }
    visit(this)
    return result
}

private fun Element.parseTtmlBackground(fallbackEnd: Long?, fallbackTranslation: String?): TtmlBackground {
    val words = mutableListOf<LyricWord>()
    val translation = childrenElements()
        .firstOrNull { it.hasRole("x-translation") }
        ?.textContent
        ?.cleanLyricSecondaryText()
        ?.takeUsefulSecondaryText()
        ?: fallbackTranslation?.splitAppleTranslation()?.second
    val text = collectTtmlMainText(this, words, fallbackEnd)
        .removeBackgroundParentheses()
        .cleanLyricText()
    val cleanedWords = words
        .map { it.copy(text = it.text.removeBackgroundParentheses()) }
        .filter { it.text.isNotBlank() }
    // If the collected text has no spaces but we have multiple words, the spans were
    // likely adjacent without inter-span whitespace. Rebuild the display text by
    // joining the individual word texts with spaces so it renders correctly.
    val displayText = if (cleanedWords.size > 1 && text.isNotBlank() && !text.hasCjk() && !text.contains(' ')) {
        cleanedWords.joinToString(" ") { it.text.cleanLyricText() }.cleanLyricText()
    } else {
        text
    }
    val bgStart = attr("begin").parseTtmlTime() ?: cleanedWords.minOfOrNull { it.startMs }
    val bgEnd = attr("end").parseTtmlTime() ?: cleanedWords.maxOfOrNull { it.endMs } ?: fallbackEnd
    // When x-bg has no inner timed spans but has overall begin/end timing,
    // create estimated per-word timing so x-bg animates per-word like v1/v2.
    val effectiveWords = if (cleanedWords.isEmpty() && displayText.isNotBlank() && bgStart != null && bgEnd != null) {
        displayText.estimateTtmlBackgroundWords(bgStart, bgEnd)
    } else {
        cleanedWords
    }
    return TtmlBackground(
        text = displayText,
        words = effectiveWords,
        translation = translation,
        startMs = bgStart,
        endMs = bgEnd
    )
}

private fun String.estimateTtmlBackgroundWords(startMs: Long, endMs: Long): List<LyricWord> {
    val cleaned = cleanLyricText()
    if (cleaned.isBlank()) return emptyList()
    val duration = (endMs - startMs).coerceAtLeast(cleaned.length * 120L)
    // For CJK text, split per character; for Latin text, split per word
    val segments = if (cleaned.hasCjk()) {
        cleaned.chunked(1)
    } else {
        Regex("""\S+\s*""").findAll(cleaned).map { it.value }.toList()
    }
    if (segments.isEmpty()) return emptyList()
    val totalWeight = segments.sumOf { estimateWordWeight(it) }.coerceAtLeast(1.0)
    var cursorMs = startMs
    return segments.mapNotNull { segment ->
        val weight = estimateWordWeight(segment)
        val segDuration = (duration * weight / totalWeight).toLong().coerceAtLeast(120L)
        val segStart = cursorMs
        val segEnd = cursorMs + segDuration
        cursorMs = segEnd
        val displayText = segment.trim()
        if (displayText.isNotBlank()) {
            LyricWord(text = segment, startMs = segStart, endMs = segEnd)
        } else null
    }.filter { it.text.isNotBlank() }
}

private fun estimateWordWeight(text: String): Double =
    text.cleanLyricText().let { cleaned ->
        if (cleaned.hasCjk()) cleaned.length.toDouble()
        else cleaned.split(Regex("""\s+""")).filter { it.isNotBlank() }.size.toDouble().coerceAtLeast(1.0)
    }

private data class TtmlBackground(
    val text: String,
    val words: List<LyricWord>,
    val translation: String?,
    val startMs: Long?,
    val endMs: Long?
)

private data class TtmlPronunciation(
    val text: String,
    val words: List<LyricWord>
)

private fun List<LyricWord>.alignPronunciationWords(
    mainWords: List<LyricWord>,
    mainText: String
): List<LyricWord> {
    if (isEmpty() || mainWords.isEmpty()) return emptyList()

    if (size == mainWords.size) {
        return mainWords.mapIndexed { index, word -> word.copy(text = this[index].text) }
    }

    val byOverlap = mainWords.mapNotNull { word ->
        val match = maxByOrNull { ruby ->
            val overlap = minOf(word.endMs, ruby.endMs) - maxOf(word.startMs, ruby.startMs)
            overlap.coerceAtLeast(0L)
        }?.takeIf { ruby ->
            minOf(word.endMs, ruby.endMs) > maxOf(word.startMs, ruby.startMs)
        }
        match?.let { word.copy(text = it.text) }
    }
    if (byOverlap.size == mainWords.size) return byOverlap

    val cjkWordIndices = mainWords
        .mapIndexedNotNull { index, word -> index.takeIf { word.text.hasCjk() } }
    if (cjkWordIndices.size == size) {
        val result = MutableList(mainWords.size) { index -> mainWords[index].copy(text = "") }
        cjkWordIndices.forEachIndexed { rubyIndex, wordIndex ->
            result[wordIndex] = mainWords[wordIndex].copy(text = this[rubyIndex].text)
        }
        return result.filter { it.text.isNotBlank() }
    }

    val cjkCharCount = mainText.count { it.isCjkChar() }
    if (cjkCharCount == size && mainWords.size == 1) {
        val word = mainWords.first()
        val duration = (word.endMs - word.startMs).coerceAtLeast(size * 120L)
        return mapIndexed { index, ruby ->
            val start = word.startMs + duration * index / size
            val end = word.startMs + duration * (index + 1) / size
            LyricWord(ruby.text, start, end)
        }
    }

    return emptyList()
}

private fun String.parseTtmlTime(): Long? {
    if (isBlank()) return null
    return trim().parseFlexibleTime().toLong()
}

private fun String.preformatTtml(): String =
    // Move trailing whitespace (spaces) from inside spans to between spans,
    // but leave XML indentation alone so it cannot become visible lyric whitespace.
    replace(Regex("""[ \t]+</span>\s*<span"""), "</span> <span")
        .replace(",</span><span", ",</span> <span")

private fun String.takeUsefulText(): String? =
    cleanLyricText().takeIf { !it.isIgnorableLyricText() }

private fun String.takeUsefulSecondaryText(): String? =
    cleanLyricSecondaryText().takeIf { !it.isIgnorableLyricText() }

private fun String.splitAppleTranslation(): Pair<String?, String?> {
    val text = cleanLyricSecondaryText()
    if (!text.endsWith('）')) return text.takeUsefulSecondaryText() to null
    val start = text.lastIndexOf('（')
    if (start < 0) return text.takeUsefulSecondaryText() to null
    val main = text.substring(0, start).takeUsefulSecondaryText()
    val bg = text.substring(start + 1, text.length - 1).takeUsefulSecondaryText()
    return main to bg
}

private fun String.removeBackgroundParentheses(): String =
    cleanLyricText()
        .replace(Regex("""^[（(]+\s*"""), "")
        .replace(Regex("""\s*[）)]+$"""), "")
        .replace(Regex("""(?<=\s)[（(]+"""), "")
        .replace(Regex("""[）)]+(?=\s|$)"""), "")
        .cleanLyricText()

private fun String.withoutFormattingWhitespace(): String =
    if (isBlank()) {
        // Preserve at least one space from whitespace-only text nodes that contain
        // regular spaces on the same line. Newlines/tabs denote XML formatting indentation.
        if (none { it == '\n' || it == '\r' || it == '\t' } && any { it == ' ' || it == '\u00A0' }) " " else ""
    } else {
        this
    }

private fun List<LyricWord>.toTtmlDisplayWords(
    lineText: String,
    lineStart: Long,
    lineEnd: Long?,
    forceLineTiming: Boolean
): List<LyricWord> {
    if (lineText.isBlank()) return emptyList()
    if (forceLineTiming) return emptyList()
    val displayWords = toDisplayWords(lineText).moveTrailingSpacesToFollowingWord()
    val onlyWord = displayWords.singleOrNull() ?: return displayWords
    val sameText = onlyWord.text.cleanLyricText() == lineText.cleanLyricText()
    val sameStart = abs(onlyWord.startMs - lineStart) <= 25L
    val sameEnd = lineEnd == null || abs(onlyWord.endMs - lineEnd) <= 25L
    return if (sameText && sameStart && sameEnd) emptyList() else displayWords
}

private fun List<LyricWord>.moveTrailingSpacesToFollowingWord(): List<LyricWord> {
    var pendingSpace = ""
    return map { word ->
        val textWithoutTrailingSpace = word.text.trimEnd(Char::isWhitespace)
        val trailingSpace = word.text.substring(textWithoutTrailingSpace.length)
        val adjusted = word.copy(text = pendingSpace + textWithoutTrailingSpace)
        pendingSpace = trailingSpace
        adjusted
    }
}

private fun Element.attr(name: String): String {
    getAttribute(name).takeIf { it.isNotBlank() }?.let { return it }
    attributes ?: return ""
    for (index in 0 until attributes.length) {
        val item = attributes.item(index)
        if (item.nodeName == name || item.nodeName.substringAfter(':') == name.substringAfter(':')) {
            return item.nodeValue.orEmpty()
        }
    }
    return ""
}

private fun Element.displayName(): String? {
    val attrName = attr("name")
        .ifBlank { attr("ttm:name") }
        .ifBlank { attr("xml:name") }
    val textName = textContent
        .orEmpty()
        .replace(Regex("""[ \t\r\n]+"""), " ")
        .trim()
    return attrName
        .ifBlank { textName }
        .takeIf { it.isNotBlank() && !it.isMusicSymbolOnly() }
}

private fun Element.hasRole(role: String): Boolean =
    attr("role") == role || attr("ttm:role") == role

private fun Element.hasAnyRole(vararg roles: String): Boolean =
    roles.any { hasRole(it) }

private fun Element.rubyMode(): String =
    attr("tts:ruby").ifBlank { attr("ruby") }

private fun Element.localTagName(): String = tagName.substringAfter(':')

private fun Element.childrenElements(): List<Element> =
    childNodes.toNodeList().mapNotNull { it as? Element }

private fun Element.allElements(): List<Element> {
    val result = mutableListOf<Element>()
    fun visit(element: Element) {
        result += element
        element.childrenElements().forEach(::visit)
    }
    visit(this)
    return result
}

private fun org.w3c.dom.NodeList.toNodeList(): List<Node> =
    List(length) { item(it) }
