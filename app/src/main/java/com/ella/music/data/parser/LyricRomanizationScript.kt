package com.ella.music.data.parser

/**
 * Script tests that decide whether a Latin-script lyric row is a *reading* of a CJK row or a
 * translation / a lyric of its own.
 *
 * The distinction used to rest on "does the row carry a tone mark", which is wrong for every
 * Latin-script language that also uses acutes and graves.  A Vietnamese lyric paired with a
 * Chinese translation ("Khi màn đêm vừa buông" / "当夜幕降临") matched that rule, so the sung
 * Vietnamese line was demoted to ruby text above the translation.  ConePlayer avoids the whole
 * problem by only ever *producing* a transliteration for Han / kana / Hangul source text, so
 * mirror that: a reading may only use the letters a romanization system actually emits, and for
 * a Han primary it must also decompose into pinyin or romaji syllables.
 */

/**
 * Non-ASCII letters Hanyu Pinyin, Hepburn/Nihon-shiki romaji and McCune-Reischauer romaja can
 * emit.  Everything else — `đ ơ ư ă`, hook-above, dot-below and tilde vowels, Cyrillic, Greek —
 * belongs to a language being sung, not to a reading.
 */
private val romanizationLetters = (
    // Pinyin tone marks over a e i o u ü, plus the syllabic nasals.
    "āáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜüńňǹḿ" +
        // Romaji long vowels: macron (already above) and the circumflex of Nihon-shiki.
        "âêîôû" +
        // McCune-Reischauer breve vowels for Korean.
        "ŏŭ"
    ).toSet()

/**
 * True when every non-ASCII letter is one a romanization system can emit.  This is the decisive
 * gate: Vietnamese, Turkish, Cyrillic or Greek rows fail it on their first native letter.
 */
internal fun String.usesOnlyRomanizationLetters(): Boolean = all { char ->
    !char.isLetter() || char.code < 0x80 || char.lowercaseChar() in romanizationLetters
}

/** Pinyin/romaji readings are compared syllable-by-syllable, so drop the diacritics first. */
private fun Char.toRomanizationBaseLetter(): Char = when (lowercaseChar()) {
    'ā', 'á', 'ǎ', 'à', 'â' -> 'a'
    'ē', 'é', 'ě', 'è', 'ê' -> 'e'
    'ī', 'í', 'ǐ', 'ì', 'î' -> 'i'
    'ō', 'ó', 'ǒ', 'ò', 'ô', 'ŏ' -> 'o'
    'ū', 'ú', 'ǔ', 'ù', 'û', 'ŭ' -> 'u'
    'ǖ', 'ǘ', 'ǚ', 'ǜ', 'ü' -> 'v'
    'ń', 'ň', 'ǹ' -> 'n'
    'ḿ' -> 'm'
    else -> lowercaseChar()
}

private fun String.toRomanizationBase(): String =
    filter { it.isLetter() }.map { it.toRomanizationBaseLetter() }.joinToString("")

private val pinyinSyllablePattern = Regex(
    "^(zh|ch|sh|[bpmfdtnlgkhjqxrzcsyw])?" +
        "(a|ai|an|ang|ao|e|ei|en|eng|er|i|ia|ian|iang|iao|ie|in|ing|iong|iu|o|ong|ou|" +
        "u|ua|uai|uan|uang|ue|ui|un|uo|v|van|ve|vn)r?$"
)

private val standalonePinyinSyllables = setOf("n", "ng", "m", "hm", "hng", "e", "o")

/** Romaji onsets, longest first so the matcher never stops at a prefix of a digraph. */
private val romajiOnsets = listOf(
    "kky", "ggy", "ssh", "tch", "ccr", "nny", "hhy", "bby", "ppy", "mmy", "rry",
    "ky", "gy", "sh", "ch", "ts", "ny", "hy", "by", "py", "my", "ry", "dz",
    "kk", "gg", "ss", "zz", "tt", "dd", "pp", "bb", "cc", "ff", "jj",
    "k", "g", "s", "z", "t", "d", "n", "h", "b", "p", "m", "y", "r", "w", "f", "j", "v"
)

private fun String.isRomajiToken(): Boolean {
    val base = toRomanizationBase()
    if (base.isEmpty()) return false
    var index = 0
    var sawMora = false
    while (index < base.length) {
        // A syllabic ん is the only consonant a romaji mora may end on.
        if (base[index] == 'n' && (index == base.lastIndex || base[index + 1] !in "aiueoy")) {
            index++
            sawMora = true
            continue
        }
        val onset = romajiOnsets.firstOrNull { base.startsWith(it, index) }.orEmpty()
        var cursor = index + onset.length
        val vowelStart = cursor
        while (cursor < base.length && base[cursor] in "aiueo") cursor++
        // No vowel after the onset means this is not a romaji mora at all.
        if (cursor == vowelStart || cursor - vowelStart > 3) return false
        index = cursor
        sawMora = true
    }
    return sawMora
}

private fun String.isPinyinToken(): Boolean {
    val base = toRomanizationBase()
    if (base.isEmpty()) return false
    return base in standalonePinyinSyllables || pinyinSyllablePattern.matches(base)
}

private const val READING_TOKEN_RATIO = 0.7f

private fun String.readingTokens(): List<String> =
    split(Regex("""[\s'’·・\-]+""")).filter { token -> token.any(Char::isLetter) }

/**
 * True when at least [READING_TOKEN_RATIO] of the tokens read as pinyin or as romaji.  Latin
 * rows that merely share a few accents with pinyin — Vietnamese above all — fall far below that,
 * because `khi`, `vừa` and `buông` are not syllables either system can produce.
 */
internal fun String.looksLikeCjkReading(): Boolean {
    if (!usesOnlyRomanizationLetters()) return false
    val tokens = readingTokens()
    if (tokens.isEmpty()) return false
    val pinyinHits = tokens.count { it.isPinyinToken() }
    val romajiHits = tokens.count { it.isRomajiToken() }
    val threshold = tokens.size * READING_TOKEN_RATIO
    return pinyinHits >= threshold || romajiHits >= threshold
}

/**
 * True when the row carries a script a reading can annotate.
 *
 * The test is deliberately "is there anything here that is not Latin" rather than a CJK
 * allow-list: Arabic, Cyrillic, Thai and Hangul rows all benefit from a per-word transliteration
 * sitting over the glyph. What must never happen is ruby over a Latin-script lyric, because then
 * the small row is not a reading of the large one — it is the song, shrunk to 8 pt.
 */
internal fun String.needsPhoneticAnnotation(): Boolean {
    var index = 0
    while (index < length) {
        val codePoint = codePointAt(index)
        index += Character.charCount(codePoint)
        if (!Character.isLetter(codePoint)) continue
        if (Character.UnicodeScript.of(codePoint) != Character.UnicodeScript.LATIN) return true
    }
    return false
}
