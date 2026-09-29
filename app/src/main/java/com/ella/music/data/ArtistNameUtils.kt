package com.ella.music.data

import com.ella.music.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object NameSplitConfigStore {
    private val _revision = MutableStateFlow(0)

    /**
     * Bumped after split/protect/case settings have been applied to this store (#675). The store
     * fields are plain vars, so UI groupings must key `remember`/`produceState` on this value or
     * they keep results computed with the previous (or cold-start empty) rules.
     */
    val revision: StateFlow<Int> = _revision.asStateFlow()

    fun notifyChanged() {
        _revision.update { it + 1 }
    }

    @Volatile
    var artistCustomSeparators: List<String> = emptyList()

    @Volatile
    var artistProtectedNames: List<String> = emptyList()

    @Volatile
    var genreCustomSeparators: List<String> = emptyList()

    @Volatile
    var genreProtectedNames: List<String> = emptyList()

    @Volatile
    var tagIgnoreCase: Boolean = false

    @Volatile
    var parseFeaturedArtists: Boolean = false
}

// Compiled separator regexes are reused across calls; building them per call was a
// hot spot when grouping the whole library (artist/genre/composer/lyricist screens).
private val separatorRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
private val protectedNameRegexCache = java.util.concurrent.ConcurrentHashMap<String, Regex>()
private val featuredArtistMarker = Regex(
    "(?i)(?:^|[\\s(\\[{])(?:feat\\.?|ft\\.?|featuring)\\s+(.+)$"
)

fun splitArtistNames(value: String): List<String> {
    return splitNames(
        value = value,
        symbolSeparatorPatterns = emptyList(),
        wordSeparatorPatterns = emptyList(),
        customSeparators = NameSplitConfigStore.artistCustomSeparators,
        protectedNames = NameSplitConfigStore.artistProtectedNames,
        unknownValues = setOf("<unknown>")
    )
}

/**
 * Returns the ARTIST tag artists and, when enabled, artists declared after a title's feat./ft.
 * marker. ARTIST-tag artists stay first so a duplicate title credit cannot change display names.
 */
fun artistNamesForSong(song: Song, includeFeaturedArtists: Boolean = NameSplitConfigStore.parseFeaturedArtists): List<String> {
    val names = splitArtistNames(song.artist).toMutableList()
    if (includeFeaturedArtists) names += extractFeaturedArtistNames(song.title)
    return names
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(java.util.Locale.ROOT) }
}

fun extractFeaturedArtistNames(title: String): List<String> {
    val featuredPart = featuredArtistMarker.find(title)?.groupValues?.getOrNull(1)
        ?.substringBeforeAny(')', ']', '}')
        ?.trim()
        .orEmpty()
    if (featuredPart.isBlank()) return emptyList()
    return splitArtistNames(featuredPart)
        .map(String::trim)
        .filter(String::isNotBlank)
        .distinctBy { it.lowercase(java.util.Locale.ROOT) }
}

private fun String.substringBeforeAny(vararg delimiters: Char): String {
    var end = length
    delimiters.forEach { delimiter ->
        val index = indexOf(delimiter)
        if (index >= 0) end = minOf(end, index)
    }
    return substring(0, end)
}

fun splitGenreNames(value: String): List<String> {
    return splitNames(
        value = value,
        symbolSeparatorPatterns = emptyList(),
        wordSeparatorPatterns = emptyList(),
        customSeparators = NameSplitConfigStore.genreCustomSeparators,
        protectedNames = NameSplitConfigStore.genreProtectedNames,
        unknownValues = setOf("<unknown>")
    )
}

fun String.matchesArtistName(artistName: String): Boolean {
    val target = artistName.trim()
    if (target.isBlank()) return false
    return splitArtistNames(this).any { it.equals(target, ignoreCase = NameSplitConfigStore.tagIgnoreCase) }
}

fun String.matchesGenreName(genreName: String): Boolean {
    val target = genreName.trim()
    if (target.isBlank()) return false
    return splitGenreNames(this).any { it.equals(target, ignoreCase = NameSplitConfigStore.tagIgnoreCase) }
}

fun String.tagIdentityKey(): String =
    if (NameSplitConfigStore.tagIgnoreCase) trim().lowercase() else trim()

fun parseNameSplitSetting(value: String): List<String> {
    return value
        .lines()
        .flatMap { line -> line.split('\t') }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
}

/**
 * Separator variant of [parseNameSplitSetting] (#675): besides one entry per line / tab, a line
 * made only of whitespace-separated symbol tokens (e.g. `; , /`) is read as several separators.
 * Lines containing letters or digits (`feat.`, `x`, `and`) stay one literal entry, so existing
 * one-per-line values keep their meaning. Protected names keep [parseNameSplitSetting] because
 * names legitimately contain spaces.
 */
fun parseNameSeparatorSetting(value: String): List<String> {
    return value
        .lines()
        .flatMap { line -> line.split('\t') }
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .flatMap { entry ->
            val tokens = entry.split(whitespaceRegex).filter { it.isNotBlank() }
            if (tokens.size > 1 && tokens.all { token -> token.none { it.isLetterOrDigit() } }) {
                tokens
            } else {
                listOf(entry)
            }
        }
        .distinctBy { it.lowercase() }
}

private val whitespaceRegex = Regex("""\s+""")

private fun splitNames(
    value: String,
    symbolSeparatorPatterns: List<String>,
    wordSeparatorPatterns: List<String>,
    customSeparators: List<String>,
    protectedNames: List<String>,
    unknownValues: Set<String>
): List<String> {
    val normalized = value
        .replace("（", "(")
        .replace("）", ")")
        .trim()
    if (normalized.isBlank()) return emptyList()

    val protectedMap = linkedMapOf<String, String>()
    var protectedText = normalized
    protectedNames
        .filter { it.isNotBlank() }
        .sortedByDescending { it.length }
        .forEachIndexed { index, name ->
            val token = "\uE000${index}\uE000"
            val regex = protectedNameRegexCache.getOrPut(name) {
                Regex(Regex.escape(name), RegexOption.IGNORE_CASE)
            }
            if (regex.containsMatchIn(protectedText)) {
                protectedMap[token] = name
                protectedText = regex.replace(protectedText, token)
            }
        }

    val separatorRegex = separatorRegexFor(symbolSeparatorPatterns, wordSeparatorPatterns, customSeparators)
        ?: return listOf(normalized)

    return protectedText
        .split(separatorRegex)
        .map { raw ->
            protectedMap.entries.fold(raw.trim()) { current, (token, name) ->
                current.replace(token, name)
            }.trim()
        }
        .filter { item ->
            item.isNotBlank() && item.lowercase() !in unknownValues
        }
        .distinctBy { it.tagIdentityKey() }
}

private fun separatorRegexFor(
    symbolSeparatorPatterns: List<String>,
    wordSeparatorPatterns: List<String>,
    customSeparators: List<String>
): Regex? {
    val symbolParts = symbolSeparatorPatterns + customSeparators
        .filter { it.isNotBlank() }
        .map { separatorPattern(it) }
    val alternatives = buildList {
        if (symbolParts.isNotEmpty()) {
            add("""\s*(?:${symbolParts.joinToString("|")})\s*""")
        }
        if (wordSeparatorPatterns.isNotEmpty()) {
            add("""\s+(?:${wordSeparatorPatterns.joinToString("|")})\s+""")
        }
    }
    if (alternatives.isEmpty()) return null
    val pattern = alternatives.joinToString("|")
    return separatorRegexCache.getOrPut(pattern) {
        Regex(pattern, RegexOption.IGNORE_CASE)
    }
}

/**
 * Escaped separator pattern. When a separator starts/ends with a letter or digit of a
 * space-delimited script (e.g. `and`, `x`, `feat.`), that edge must sit on a word boundary so
 * `and` splits "A and B" but never "Sandra" (#675). CJK/Thai separators such as `和` keep matching
 * inline because those scripts do not put spaces between names.
 */
private fun separatorPattern(separator: String): String {
    val escaped = Regex.escape(separator)
    val leading = if (separator.first().needsWordBoundary()) """(?<![\p{L}\p{N}])""" else ""
    val trailing = if (separator.last().needsWordBoundary()) """(?![\p{L}\p{N}])""" else ""
    return leading + escaped + trailing
}

private fun Char.needsWordBoundary(): Boolean {
    if (!isLetterOrDigit()) return false
    if (Character.isIdeographic(code)) return false
    return when (Character.UnicodeScript.of(code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.THAI -> false
        else -> true
    }
}
