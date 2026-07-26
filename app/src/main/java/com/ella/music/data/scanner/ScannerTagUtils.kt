package com.ella.music.data.scanner

import android.media.MediaMetadataRetriever
import com.ella.music.data.LibraryNormalizer
import com.ella.music.data.SettingsManager
import com.ella.music.data.looksLikeNeteaseKeyValue
import com.ella.music.data.metadata.AudioTagInfo
import com.ella.music.data.parser.LrcParser

internal fun isMissingTag(value: String?, fileName: String? = null): Boolean {
    return LibraryNormalizer.isMissingTag(value, fileName)
}

internal fun isMissingArtistTag(value: String?): Boolean {
    return LibraryNormalizer.isMissingArtistTag(value)
}

internal fun isMissingAlbumTag(value: String?, fileName: String? = null): Boolean {
    return LibraryNormalizer.isMissingAlbumTag(value, fileName)
}

internal fun firstNonBlank(vararg values: String?): String? =
    values.firstOrNull { !it.isNullOrBlank() }

internal fun AudioTagInfo.customTagValue(vararg keys: String): String? {
    keys.forEach { key ->
        customTags.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }
            ?.value
            ?.firstOrNull { it.isNotBlank() }
            ?.let { return it }
    }
    return null
}

internal fun AudioTagInfo.replayGainForMode(mode: Int): Float? {
    fun trackGain(): Float? =
        replayGainTrackGain?.parseReplayGain()
            ?: customTagValue("R128_TRACK_GAIN")?.parseR128Gain()

    fun albumGain(): Float? =
        replayGainAlbumGain?.parseReplayGain()
            ?: customTagValue("R128_ALBUM_GAIN")?.parseR128Gain()

    return when (mode.coerceIn(SettingsManager.REPLAY_GAIN_OFF, SettingsManager.REPLAY_GAIN_AUTO)) {
        SettingsManager.REPLAY_GAIN_TRACK -> trackGain()
        SettingsManager.REPLAY_GAIN_ALBUM -> albumGain()
        SettingsManager.REPLAY_GAIN_AUTO -> albumGain() ?: trackGain()
        else -> null
    }
}

internal fun Map<String, List<String>>.flattenForSearch(): String =
    entries.asSequence()
        .filterNot { (key, _) -> key.isIgnoredSearchTagKey() }
        .flatMap { (key, values) ->
            sequence {
                yield(key)
                values.forEach { value ->
                    val text = value.cleanTagText()
                    if (text.isNotBlank() && !text.looksLikeNeteaseKeyValue()) yield(text)
                }
            }
        }
        .distinct()
        .take(80)
        .joinToString(" ")

internal fun String.isIgnoredSearchTagKey(): Boolean {
    val normalized = trim().lowercase()
    return normalized in setOf(
        "apic",
        "covr",
        "picture",
        "metadata_block_picture",
        "unsyncedlyrics",
        "uslt",
        "lyrics",
        "lyric",
        "syncedlyrics",
        "replaygain_track_gain",
        "replaygain_track_peak",
        "replaygain_album_gain",
        "replaygain_album_peak",
        "replaygain_reference_loudness"
    )
}

internal fun String.parseReplayGain(): Float? {
    return Regex("([+-]?[0-9]+(?:\\.[0-9]+)?)")
        .find(this)
        ?.groupValues
        ?.getOrNull(1)
        ?.toFloatOrNull()
}

internal fun String.parseR128Gain(): Float? {
    val raw = trim().toFloatOrNull() ?: return parseReplayGain()
    return raw / 256f
}

internal fun ratingStarsFromTagValues(vararg values: String?): Int {
    return values
        .flatMap { value -> value.orEmpty().split(';', '\n') }
        .mapNotNull { it.parseRatingStars() }
        .maxOrNull()
        ?.coerceIn(0, 5)
        ?: 0
}

internal fun String.parseRatingStars(): Int? {
    val text = cleanTagText()
    if (text.isBlank()) return null

    val filledStars = text.count { it == '★' || it == '⭐' }
    if (filledStars > 0) return filledStars.coerceIn(0, 5)

    val numeric = Regex("""([0-9]+(?:\.[0-9]+)?)""")
        .find(text)
        ?.groupValues
        ?.getOrNull(1)
        ?.toFloatOrNull()
        ?: return null

    return when {
        numeric <= 0f -> 0
        numeric <= 1f -> kotlin.math.round(numeric * 5f).toInt()
        numeric <= 5f -> kotlin.math.round(numeric).toInt()
        numeric <= 100f -> kotlin.math.round(numeric / 20f).toInt()
        numeric <= 255f -> kotlin.math.round(numeric / 255f * 5f).toInt()
        else -> null
    }?.coerceIn(0, 5)
}

internal fun String.isUsableSynchronizedLyrics(): Boolean {
    if (isBlank()) return false
    return LrcParser.parse(this).lyrics.any { it.text.trim().isNotBlank() }
}

internal fun String.extractPrefixedNeteaseCommentKey(): String {
    val text = cleanTagText()
    return text.takeIf {
        neteaseCommentPrefixRegex.containsMatchIn(it) &&
            it.looksLikeNeteaseKeyValue()
    }.orEmpty()
}

private val neteaseCommentPrefixRegex = Regex(
    """^\s*163\s+key\s*\(\s*don't\s+modify\s*\)\s*:""",
    RegexOption.IGNORE_CASE
)

/**
 * Keep the full release date from tags instead of dropping it to its year.  Library year
 * categories extract the first four digits separately, so preserving month/day here keeps
 * album headers and sort order accurate without splitting the year category.
 */
internal fun String.normalizeReleaseDate(): String {
    val value = trim()
    val match = Regex("""(\d{4})(?:[-./](\d{1,2})(?:[-./](\d{1,2}))?)?""").find(value)
        ?: return value
    val year = match.groupValues[1]
    val month = match.groupValues.getOrNull(2).orEmpty()
    val day = match.groupValues.getOrNull(3).orEmpty()
    return buildString {
        append(year)
        if (month.isNotBlank()) append("-").append(month.padStart(2, '0'))
        if (day.isNotBlank()) append("-").append(day.padStart(2, '0'))
    }
}

internal fun Int.normalizedTrackNumber(): Int =
    if (this > 1000) this % 1000 else this

internal fun Int.normalizedDiscNumber(): Int =
    if (this >= 1000) this / 1000 else 0

internal fun String.normalizedTrackNumberFromTag(): Int =
    substringBefore('/').trim().toIntOrNull()?.normalizedTrackNumber() ?: 0

internal fun String.normalizedDiscNumberFromTag(): Int =
    substringBefore('/').trim().toIntOrNull() ?: 0

internal fun String.normalizedFolderPath(): String? {
    val normalized = trim().replace('\\', '/').trimEnd('/')
    return normalized.takeIf { it.isNotBlank() }?.lowercase()
}

internal fun String.isAllowedByFolderFilters(
    includeFolders: List<String>,
    excludeFolders: List<String>
): Boolean {
    val normalizedPath = replace('\\', '/').lowercase()
    val included = includeFolders.isEmpty() || includeFolders.any { folder ->
        normalizedPath == folder || normalizedPath.startsWith("$folder/")
    }
    if (!included) return false

    return excludeFolders.none { folder ->
        normalizedPath == folder || normalizedPath.startsWith("$folder/")
    }
}

internal inline fun <T> MediaMetadataRetriever.useCompat(block: (MediaMetadataRetriever) -> T): T {
    return try {
        block(this)
    } finally {
        release()
    }
}

internal fun String.cleanTagText(): String =
    trim('\uFEFF', '\u0000', ' ', '\t', '\r', '\n')
        .replace(Regex("""\s+"""), " ")
