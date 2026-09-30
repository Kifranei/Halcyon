package com.ella.music.data

import java.io.File
import java.io.IOException
import java.util.Locale

internal fun nextArtistImageFileName(artist: String, extension: String, existingNames: Collection<String>): String {
    val names = existingNames.mapTo(hashSetOf()) { it.lowercase(Locale.ROOT) }
    val stem = artist.sanitizeExportFileName(fallback = "artist")
    for (index in 0..100_000) {
        val candidate = if (index == 0) "$stem.$extension" else "$stem ($index).$extension"
        if (candidate.lowercase(Locale.ROOT) !in names && "$candidate.source".lowercase(Locale.ROOT) !in names) return candidate
    }
    throw IOException("Artist image filename limit exceeded")
}

internal fun matchingArtistImageFiles(directory: File, artist: String): List<File> {
    val key = normalizeArtistCoverKey(artist.sanitizeExportFileName(fallback = "artist"), ignoreCase = false)
    return directory.listFiles().orEmpty().mapNotNull { file ->
        val match = artistCoverMatch(file.name, ignoreCase = false)
        if (file.isFile && file.length() > 0 && match?.key == key && match.kind == ArtistCoverKind.Image) file to match.order else null
    }.sortedBy { it.second }.map { it.first }
}

internal fun preferredArtistImageUrls(source: String, url: String): List<String> {
    val high = when (source) {
        SettingsManager.ARTIST_IMAGE_SOURCE_LASTFM -> url.replace(Regex("/i/u/(?:avatar\\d+s|\\d+x\\d+s?)/"), "/i/u/770x0/")
        SettingsManager.ARTIST_IMAGE_SOURCE_QQ -> url.replace(Regex("/T001R\\d+x\\d+M000"), "/T001R800x800M000")
        SettingsManager.ARTIST_IMAGE_SOURCE_KUGOU -> url.replace("/softhead/400/", "/softhead/1000/")
        SettingsManager.ARTIST_IMAGE_SOURCE_NETEASE -> url.replace(Regex("([?&]param=)\\d+y\\d+"), "$1" + "1000y1000")
        else -> url
    }
    return listOf(high, url).distinct()
}
