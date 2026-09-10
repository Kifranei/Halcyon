package com.ella.music.data.model

import androidx.compose.runtime.Immutable

@Immutable
data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val songCount: Int,
    val year: String = "",
    val artAlbumId: Long = id,
    val albumArtist: String = ""
) {
    val yearInt: Int get() = Regex("""\d{4}""").find(year)?.value?.toIntOrNull() ?: 0
    val releaseDateSortKey: Int get() = parseReleaseDateSortKey(year)
}

private val ReleaseDateRegex = Regex("""(\d{4})(?:[-./](\d{1,2})(?:[-./](\d{1,2}))?)?""")

private fun parseReleaseDateSortKey(value: String): Int {
    val match = ReleaseDateRegex.find(value) ?: return 0
    val year = match.groupValues.getOrNull(1)?.toIntOrNull() ?: return 0
    val month = match.groupValues.getOrNull(2)?.toIntOrNull()?.coerceIn(0, 12) ?: 0
    val day = match.groupValues.getOrNull(3)?.toIntOrNull()?.coerceIn(0, 31) ?: 0
    return year * 10_000 + month * 100 + day
}
