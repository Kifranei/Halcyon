package com.ella.music.data.parser

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.shiftedBy
import java.io.StringReader
import java.math.BigDecimal
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import kotlin.math.abs
import kotlin.math.roundToLong

private val unknownTtmlAgentIdPattern = Regex("""v\d+|agent\d+""", RegexOption.IGNORE_CASE)

/** TTML offset-time: `12.5s`, `500ms`, `1.5m`, `1h`, `15f`, `100t`. */
private val ttmlOffsetTimePattern = Regex("""^(\d+(?:\.\d*)?|\.\d+)\s*(h|ms|m|s|f|t)$""", RegexOption.IGNORE_CASE)

/** TTML SMPTE clock-time with frames: `hh:mm:ss:ff(.sub)`. */
private val ttmlSmpteTimePattern = Regex("""^(\d+):(\d+):(\d+):(\d+)(?:\.(\d+))?$""")

/** Clock-time `hh:mm:ss(.fff)` plus the lenient `mm:ss(.fff)` / `ss(.fff)` forms Apple Music uses. */
private val ttmlClockTimePattern = Regex("""^(?:(?:(\d+):)?(\d+):)?(\d*)(?:\.(\d*))?$""")

private val ttmlUnitlessNumberPattern = Regex("""^[+-]?\d+(?:\.\d+)?$""")

/** Tolerance used when deciding whether a container's children are timed relative to it. */
private const val TTML_CONTAINER_TIME_TOLERANCE_MS = 50L

/**
 * Parses a TTML time expression into milliseconds, or null when [raw] is blank or not a valid
 * time expression.
 *
 * Supports clock time (`hh:mm:ss(.fff)`, plus lenient `mm:ss(.fff)` and `ss(.fff)`), SMPTE
 * `hh:mm:ss:ff(.sub)` using [frameRate], and offset time with the metrics `h`, `m`, `s`, `ms`,
 * `f` (frames at [frameRate]) and `t` (ticks at [tickRate]). Fractions of a millisecond are
 * truncated for clock/`h`/`m`/`s`/`ms` values (matching the historic parser) and rounded for
 * frame/tick based values.
 */
internal fun parseTtmlTimeExpression(
    raw: String,
    frameRate: Double = 30.0,
    tickRate: Double = 1.0,
    subFrameRate: Double = 1.0
): Long? {
    val value = raw.trim().replace(',', '.')
    if (value.isEmpty()) return null

    ttmlOffsetTimePattern.matchEntire(value)?.let { match ->
        val count = match.groupValues[1].toBigDecimalOrNull() ?: return null
        return when (match.groupValues[2].lowercase()) {
            "h" -> count.multiply(BigDecimal(3_600_000L)).toLong()
            "m" -> count.multiply(BigDecimal(60_000L)).toLong()
            "s" -> count.multiply(BigDecimal(1_000L)).toLong()
            "ms" -> count.toLong()
            "f" -> if (frameRate > 0.0) (count.toDouble() * 1000.0 / frameRate).roundToLong() else null
            "t" -> if (tickRate > 0.0) (count.toDouble() * 1000.0 / tickRate).roundToLong() else null
            else -> null
        }
    }

    ttmlSmpteTimePattern.matchEntire(value)?.let { match ->
        if (frameRate <= 0.0) return null
        val hours = match.groupValues[1].toLongOrNull() ?: return null
        val minutes = match.groupValues[2].toLongOrNull() ?: return null
        val seconds = match.groupValues[3].toLongOrNull() ?: return null
        val frames = match.groupValues[4].toLongOrNull() ?: return null
        val subFrames = match.groupValues[5].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
        val frameValue = frames.toDouble() + if (subFrameRate > 0.0) subFrames / subFrameRate else 0.0
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L +
            (frameValue * 1000.0 / frameRate).roundToLong()
    }

    ttmlClockTimePattern.matchEntire(value)?.let { match ->
        val secondsText = match.groupValues[3]
        val fractionText = match.groupValues[4]
        if (secondsText.isEmpty() && fractionText.isEmpty()) return null
        val hours = match.groupValues[1].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
        val minutes = match.groupValues[2].takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
        val seconds = secondsText.takeIf { it.isNotEmpty() }?.toLongOrNull() ?: 0L
        val fractionMs = fractionText.takeIf { it.isNotEmpty() }
            ?.padEnd(3, '0')
            ?.take(3)
            ?.toLongOrNull()
            ?: 0L
        return hours * 3_600_000L + minutes * 60_000L + seconds * 1_000L + fractionMs
    }

    return null
}

/** Document-wide timing parameters (`ttp:*`) plus the time base of the element being parsed. */
private data class TtmlClock(
    val frameRate: Double = 30.0,
    val subFrameRate: Double = 1.0,
    val tickRate: Double = 1.0,
    /** Absolute begin of the enclosing time container(s), added to every parsed time. */
    val baseMs: Long = 0L
) {
    /** Parses [value] without applying [baseMs]. */
    fun raw(value: String): Long? = parseTtmlTimeExpression(value, frameRate, tickRate, subFrameRate)

    /** Parses [value] and converts it to an absolute media time using [baseMs]. */
    fun at(value: String): Long? = raw(value)?.plus(baseMs)
}

private fun Element.ttmlClock(): TtmlClock {
    val baseFrameRate = attr("ttp:frameRate").trim().toDoubleOrNull()?.takeIf { it > 0.0 } ?: 30.0
    val multiplier = attr("ttp:frameRateMultiplier").trim()
        .split(Regex("""\s+"""))
        .mapNotNull { it.toDoubleOrNull() }
        .takeIf { it.size == 2 && it[0] > 0.0 && it[1] > 0.0 }
        ?.let { it[0] / it[1] }
        ?: 1.0
    return TtmlClock(
        frameRate = baseFrameRate * multiplier,
        subFrameRate = attr("ttp:subFrameRate").trim().toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0,
        tickRate = attr("ttp:tickRate").trim().toDoubleOrNull()?.takeIf { it > 0.0 } ?: 1.0
    )
}

private fun Element.timeAttr(name: String, clock: TtmlClock): Long? = clock.at(attr(name))

/**
 * Parses a file-level lyric offset. Unitless numbers are milliseconds (like LRC `[offset:]`);
 * anything else must be a (optionally signed) TTML time expression.
 */
private fun String.parseTtmlFileOffsetMs(clock: TtmlClock): Long? {
    val value = trim()
    if (value.isEmpty()) return null
    if (ttmlUnitlessNumberPattern.matches(value)) {
        return value.toBigDecimalOrNull()?.toDouble()?.roundToLong()
    }
    val negative = value.startsWith('-')
    val magnitude = clock.raw(value.removePrefix("-").removePrefix("+")) ?: return null
    return if (negative) -magnitude else magnitude
}

/**
 * Sum of the `begin` of every `body` / `div` ancestor whose children are timed relative to it
 * (TTML `par` time container semantics). Containers whose children already carry absolute
 * times (Apple Music / AMLL exports put the first line's absolute begin on each `div`) are
 * ignored, so the common case keeps its historic timing.
 */
private fun Element.ttmlContainerBaseMs(clock: TtmlClock, cache: MutableMap<Element, Long>): Long {
    var total = 0L
    var node: Node? = parentNode
    while (node is Element) {
        val container: Element = node
        val tag = container.localTagName()
        if (tag == "body" || tag == "div") {
            total += cache.getOrPut(container) { container.relativeContainerBeginMs(clock) }
        }
        node = container.parentNode
    }
    return total
}

private fun Element.relativeContainerBeginMs(clock: TtmlClock): Long {
    val begin = clock.raw(attr("begin")) ?: return 0L
    if (begin <= 0L) return 0L
    val timedDescendants = allElements()
        .drop(1)
        .filter { it.localTagName() == "p" || it.localTagName() == "span" }
    val childBegins = timedDescendants.mapNotNull { clock.raw(it.attr("begin")) }
    val minChildBegin = childBegins.minOrNull() ?: return begin
    // A child starting before its container is impossible with absolute times: must be relative.
    if (minChildBegin < begin - TTML_CONTAINER_TIME_TOLERANCE_MS) return begin
    // A child ending after (container end - container begin) is impossible with relative times.
    val containerEnd = clock.raw(attr("end"))
    val maxChildEnd = timedDescendants.mapNotNull { clock.raw(it.attr("end")) }.maxOrNull()
        ?: childBegins.maxOrNull()
        ?: minChildBegin
    if (containerEnd != null && maxChildEnd > containerEnd - begin + TTML_CONTAINER_TIME_TOLERANCE_MS) return 0L
    // Apple Music / AMLL pattern: the container begins exactly with its first line (absolute).
    if (minChildBegin <= begin + TTML_CONTAINER_TIME_TOLERANCE_MS) return 0L
    return begin
}

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
        val clock = root.ttmlClock()
        val metadata = parseTtmlMetadata(root)
        // Positive offsets make lyrics appear earlier, matching LRC `[offset:]`.
        val fileOffsetMs = (metadata.offset ?: root.attr("offset").takeIf { it.isNotBlank() })
            ?.parseTtmlFileOffsetMs(clock)
            ?: 0L
        val agentInfo = parseAgentInfo(root)
        val translations = parseTimedTextMap(root, "translations", "translation")
        val transliterations = parseTransliterations(root, clock)
        val forceLineTiming = root.attr("itunes:timing")
            .ifBlank { root.attr("timing") }
            .equals("Line", ignoreCase = true)
        val paragraphs = root.allElements()
            .filter { it.localTagName() == "p" }
        val containerBaseCache = HashMap<Element, Long>()

        val lines = paragraphs.mapNotNull { p ->
            val lineClock = clock.copy(baseMs = p.ttmlContainerBaseMs(clock, containerBaseCache))
            val explicitStart = p.timeAttr("begin", lineClock)
            val end = p.timeAttr("end", lineClock)
            val key = p.attr("itunes:key").ifBlank { p.attr("key") }
            val rawAgent = p.attr("ttm:agent").ifBlank { p.attr("agent") }
            val agentIds = rawAgent.toTtmlAgentIds()
            val displayAgentName = agentIds.resolveTtmlAgentNames(agentInfo)
            val words = mutableListOf<LyricWord>()
            val rubyPronunciationWords = mutableListOf<LyricWord>()
            val collectedText = collectTtmlMainText(p, words, end, lineClock, rubyPronunciationWords).cleanLyricText()
            val bg = p.childrenElements()
                .firstOrNull { it.hasRole("x-bg") }
                ?.parseTtmlBackground(end, translations[key], lineClock)
            // A <p> without a usable begin inherits the first timed span; otherwise it is skipped.
            val start = explicitStart
                ?: words.minOfOrNull { it.startMs }
                ?: bg?.startMs
                ?: return@mapNotNull null
            val text = if (
                collectedText.isNotBlank() &&
                p.getAttribute("xml:space") != "preserve" &&
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
            val inlinePronunciationElement = p.allElements()
                .firstOrNull { it.hasAnyRole("x-roman", "x-romanization") }
            val linePronunciation = inlinePronunciationElement
                ?.textContent
                ?.cleanLyricSecondaryText()
            val inlinePronunciationWords = inlinePronunciationElement
                ?.collectTimedPronunciationWords(end, lineClock)
            val transliteration = transliterations[key]
            val pronunciationWords = when {
                transliteration?.words?.isNotEmpty() == true ->
                    transliteration.words.shiftedWordsBy(lineClock.baseMs).alignPronunciationWords(
                        mainWords = displayWords,
                        mainText = displayText,
                        lineStart = start,
                        lineEnd = end
                    )
                inlinePronunciationWords?.isNotEmpty() == true ->
                    inlinePronunciationWords.alignPronunciationWords(
                        mainWords = displayWords,
                        mainText = displayText,
                        lineStart = start,
                        lineEnd = end
                    )
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
                pronunciationWords = if (pronunciationWords.any { it.endMs > it.startMs }) {
                    pronunciationWords
                } else {
                    pronunciationWords.toDisplayWords(pronunciation.orEmpty())
                },
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
            lyrics = assignTtmlAgentSides(lines.sortedBy { it.timeMs }, agentInfo)
                .shiftedBy(-fileOffsetMs),
            title = metadata.title,
            artist = metadata.artist,
            album = metadata.album,
            offset = fileOffsetMs
        )
    }.getOrNull()?.takeIf { it.lyrics.isNotEmpty() }
}

private fun List<LyricWord>.shiftedWordsBy(offsetMs: Long): List<LyricWord> =
    if (offsetMs == 0L) this else map { it.copy(startMs = it.startMs + offsetMs, endMs = it.endMs + offsetMs) }

private data class TtmlMetadata(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    /** Raw `offset` metadata value (AMLL / Lyricify `<meta key="offset" value="..."/>`). */
    val offset: String? = null
)

private fun parseTtmlMetadata(root: Element): TtmlMetadata {
    var title: String? = null
    var artist: String? = null
    var album: String? = null
    var offset: String? = null

    root.allElements().forEach { element ->
        when (element.localTagName()) {
            "title" -> if (title == null) title = element.textContent.takeUsefulText()
            "meta" -> {
                val key = element.attr("key").trim()
                if (key.equals("offset", ignoreCase = true)) {
                    if (offset == null) {
                        offset = element.attr("value")
                            .ifBlank { element.textContent.orEmpty() }
                            .trim()
                            .takeIf { it.isNotEmpty() }
                    }
                    return@forEach
                }
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

    return TtmlMetadata(title = title, artist = artist, album = album, offset = offset)
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
 * Lines without an agent follow the user's alignment and don't change the running solo side.
 * Lines must already be in playback (time) order.
 */
private fun assignTtmlAgentSides(
    lines: List<LyricLine>,
    agentInfo: Map<String, TtmlAgentInfo>
): List<LyricLine> {
    var prevSoloId: String? = null
    var prevSoloSide: String? = null
    return lines.map { line ->
        // A TTML sidecar converted from LRC has no singer assignment. Do not invent a left
        // singer for it: that would override centered lyrics merely by changing file format.
        val id = line.agent?.takeIf { it.isNotBlank() } ?: return@map line
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

private fun parseTransliterations(root: Element, clock: TtmlClock): Map<String, TtmlPronunciation> {
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
                    val start = span.timeAttr("begin", clock)
                    val end = span.timeAttr("end", clock)
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
    clock: TtmlClock,
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
                        val ruby = child.parseRubyTtml(fallbackEnd, clock)
                        if (ruby.text.isNotBlank()) {
                            builder.append(ruby.text)
                            words += ruby.words
                            pronunciationWords += ruby.pronunciationWords
                        }
                        return@forEach
                    }
                    "textContainer", "text" -> {
                        pronunciationWords += child.collectRubyPronunciationWords(fallbackEnd, clock)
                        return@forEach
                    }
                }
                val wordCountBefore = words.size
                val nested = collectTtmlMainText(child, words, fallbackEnd, clock, pronunciationWords)
                val nestedAddedTimedWords = words.size > wordCountBefore
                val begin = child.timeAttr("begin", clock)
                if (begin != null && nested.isNotBlank() && !nestedAddedTimedWords) {
                    words += LyricWord(
                        text = nested,
                        startMs = begin,
                        endMs = child.timeAttr("end", clock)
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

private fun Element.parseRubyTtml(fallbackEnd: Long?, clock: TtmlClock): TtmlRuby {
    val pronunciationWords = collectRubyPronunciationWords(fallbackEnd, clock)
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

    val begin = timeAttr("begin", clock) ?: pronunciationWords.minOfOrNull { it.startMs }
    val end = timeAttr("end", clock)
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

private fun Element.collectRubyPronunciationWords(fallbackEnd: Long?, clock: TtmlClock): List<LyricWord> {
    val result = mutableListOf<LyricWord>()
    fun visit(element: Element) {
        val mode = element.rubyMode()
        if (mode == "text") {
            val value = element.textContent.cleanLyricText()
            val begin = element.timeAttr("begin", clock)
            if (value.isNotBlank() && begin != null) {
                result += LyricWord(
                    text = value,
                    startMs = begin,
                    endMs = element.timeAttr("end", clock)
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

/**
 * Some Apple Music TTML providers put timed romanization directly inside an `x-roman` role
 * span instead of the metadata `transliterations` table.  Keeping those timestamps is
 * important for CJK lines whose main text is one long span: a plain concatenated romanization
 * cannot tell which reading belongs below which character.
 */
private fun Element.collectTimedPronunciationWords(fallbackEnd: Long?, clock: TtmlClock): List<LyricWord> {
    val timedSpans = allElements()
        .filter { it.localTagName() == "span" && it.timeAttr("begin", clock) != null }
        .filter { candidate ->
            candidate.allElements().drop(1).none { it.timeAttr("begin", clock) != null }
        }
    val candidates = if (timedSpans.isNotEmpty()) timedSpans else listOf(this)
    return candidates.mapNotNull { span ->
        val value = span.textContent
            .orEmpty()
            .cleanLyricSecondaryText()
            .takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val begin = span.timeAttr("begin", clock) ?: return@mapNotNull null
        LyricWord(
            text = value,
            startMs = begin,
            endMs = span.timeAttr("end", clock)
                ?: fallbackEnd
                ?: begin + estimateDuration(value)
        )
    }
}

private fun Element.parseTtmlBackground(
    fallbackEnd: Long?,
    fallbackTranslation: String?,
    clock: TtmlClock
): TtmlBackground {
    val words = mutableListOf<LyricWord>()
    val translation = childrenElements()
        .firstOrNull { it.hasRole("x-translation") }
        ?.textContent
        ?.cleanLyricSecondaryText()
        ?.takeUsefulSecondaryText()
        ?: fallbackTranslation?.splitAppleTranslation()?.second
    val text = collectTtmlMainText(this, words, fallbackEnd, clock)
        .removeBackgroundParentheses()
        .cleanLyricText()
    val cleanedWords = words
        .map { word ->
            val cleaned = word.text.removeBackgroundParentheses()
            if (cleaned.endsWith('-') && cleaned.length > 1) {
                word.copy(text = cleaned.dropLast(1).trimEnd() + " - ")
            } else {
                word.copy(text = cleaned)
            }
        }
        .filter { it.text.isNotBlank() }
    // If the collected text has no spaces but we have multiple words, the spans were
    // likely adjacent without inter-span whitespace. Rebuild the display text by
    // joining the individual word texts with spaces so it renders correctly.
    val rawDisplayText = if (cleanedWords.size > 1 && text.isNotBlank() && !text.hasCjk() && !text.contains(' ')) {
        cleanedWords.joinToString(" ") { it.text.cleanLyricText() }.cleanLyricText()
    } else {
        text
    }
    val displayText = rawDisplayText.replace(Regex("""(\S+)-\s*(\S+)"""), "$1 - $2")
    val bgStart = timeAttr("begin", clock) ?: cleanedWords.minOfOrNull { it.startMs }
    val bgEnd = timeAttr("end", clock) ?: cleanedWords.maxOfOrNull { it.endMs } ?: fallbackEnd
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

private fun List<LyricWord>.expandSyllableWords(mainText: String): List<LyricWord> {
    val hasSyllableSpans = any { word ->
        word.text.trim().contains(' ') && word.text.any { it in 'a'..'z' || it in 'A'..'Z' }
    }
    if (!hasSyllableSpans) return this

    return flatMap { word ->
        val rawTokens = word.text.trim().split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (rawTokens.size <= 1) return@flatMap listOf(word)

        val duration = (word.endMs - word.startMs).coerceAtLeast(rawTokens.size.toLong())
        rawTokens.mapIndexed { index, token ->
            val cleanToken = token.trim { !it.isLetterOrDigit() && it != '\'' }
            val start = word.startMs + duration * index / rawTokens.size
            val end = if (index == rawTokens.lastIndex) {
                word.endMs
            } else {
                word.startMs + duration * (index + 1) / rawTokens.size
            }
            LyricWord(
                text = cleanToken.ifBlank { token },
                startMs = start,
                endMs = end.coerceAtLeast(start + 1L)
            )
        }
    }
}

private fun List<LyricWord>.alignPronunciationWords(
    mainWords: List<LyricWord>,
    mainText: String,
    lineStart: Long? = null,
    lineEnd: Long? = null
): List<LyricWord> {
    if (isEmpty()) return emptyList()
    val expandedWords = expandSyllableWords(mainText)
    val timedWords = expandedWords.filter { it.endMs > it.startMs }
    if (timedWords.isEmpty()) {
        if (mainWords.size == expandedWords.size) {
            return mainWords.mapIndexed { index, word -> word.copy(text = expandedWords[index].text) }
        }
        val kanjiWordIndices = mainWords.mapIndexedNotNull { index, word ->
            index.takeIf { word.text.any(Char::isKanjiOrHangul) }
        }
        if (kanjiWordIndices.size == expandedWords.size) {
            return kanjiWordIndices.mapIndexed { rubyIndex, wordIndex ->
                mainWords[wordIndex].copy(text = expandedWords[rubyIndex].text)
            }
        }
        return expandedWords
    }

    // When a line has one timing span for the whole phrase, toTtmlDisplayWords intentionally
    // omits that redundant span. Recreate it as an alignment anchor so each timed reading can
    // still be projected onto its CJK/Hangul character (the same midpoint mapping used by LunaBeat).
    val effectiveMainWords = if (mainWords.isEmpty() && timedWords.isNotEmpty() && mainText.isNotBlank()) {
        val start = lineStart ?: timedWords.minOfOrNull { it.startMs } ?: return expandedWords
        val end = lineEnd
            ?: timedWords.maxOfOrNull { it.endMs }
            ?: (start + estimateDuration(mainText))
        listOf(LyricWord(mainText, start, end.coerceAtLeast(start + 1L)))
    } else {
        mainWords
    }
    if (effectiveMainWords.isEmpty()) return expandedWords

    val characterSlots = effectiveMainWords.flatMap { word ->
        val characters = word.text.toList()
        if (characters.isEmpty()) return@flatMap emptyList()
        val duration = (word.endMs - word.startMs).coerceAtLeast(characters.size.toLong())
        characters.mapIndexedNotNull { index, character ->
            if (!character.isKanjiOrHangul()) return@mapIndexedNotNull null
            val slotStart = word.startMs + duration * index / characters.size
            val slotEnd = if (index == characters.lastIndex) {
                word.endMs
            } else {
                word.startMs + duration * (index + 1) / characters.size
            }
            LyricWord(character.toString(), slotStart, slotEnd.coerceAtLeast(slotStart + 1L))
        }
    }
    if (characterSlots.isEmpty()) {
        return if (mainText.any(Char::isKanjiOrHangul)) expandedWords else emptyList()
    }

    // Filter out English tokens that match words in mainText
    val englishTokensInMain = Regex("""[A-Za-z0-9']+""").findAll(mainText)
        .map { it.value.lowercase() }
        .toSet()
    val phoneticWords = timedWords.filter { word ->
        val clean = word.text.lowercase().trim { !it.isLetterOrDigit() && it != '\'' }
        clean !in englishTokensInMain
    }

    if (phoneticWords.size == characterSlots.size) {
        return characterSlots.mapIndexed { index, slot ->
            LyricWord(
                text = phoneticWords[index].text,
                startMs = slot.startMs,
                endMs = slot.endMs
            )
        }
    }

    val used = BooleanArray(characterSlots.size)
    var lastSlotIndex = -1
    val mapped = phoneticWords.mapNotNull { pronunciation ->
        val midpoint = pronunciation.startMs +
            (pronunciation.endMs - pronunciation.startMs).coerceAtLeast(1L) / 2L
        val forwardCandidates = characterSlots.indices.filter { index ->
            !used[index] && index >= lastSlotIndex
        }
        val candidates = forwardCandidates.ifEmpty {
            characterSlots.indices.filterNot { used[it] }
        }
        val best = candidates.minWithOrNull(
            compareBy<Int> { index ->
                if (ttmlRangesOverlap(characterSlots[index], pronunciation)) 0 else 1
            }.thenBy { index ->
                abs(
                    characterSlots[index].startMs +
                        (characterSlots[index].endMs - characterSlots[index].startMs) / 2L - midpoint
                )
            }.thenBy { index -> abs(characterSlots[index].startMs - pronunciation.startMs) }
        ) ?: return@mapNotNull null
        used[best] = true
        lastSlotIndex = best
        pronunciation.copy(
            startMs = characterSlots[best].startMs,
            endMs = characterSlots[best].endMs
        )
    }
    return mapped.takeIf { it.size == phoneticWords.size } ?: expandedWords
}


private fun ttmlRangesOverlap(first: LyricWord, second: LyricWord): Boolean =
    minOf(first.endMs, second.endMs) > maxOf(first.startMs, second.startMs)

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
