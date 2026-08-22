package com.ella.music.ui.components

import com.ella.music.data.model.AudioInfo
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.splitArtistNames
import com.ella.music.data.splitGenreNames
import com.ella.music.ui.analytics.formatLabel
import com.ella.music.ui.analytics.qualityLabel
import com.ella.music.ui.navigation.Screen
import com.ella.music.viewmodel.extractYear
import com.ella.music.viewmodel.parentFolderPath

internal enum class SongInfoJump {
    Path,
    Directory,
    Format,
    Bitrate,
    Artist,
    Album,
    AlbumArtist,
    Genre,
    Year,
    Composer,
    Arranger,
    Lyricist
}

internal fun songInfoJumpChoices(jump: SongInfoJump, value: String): List<String> {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return emptyList()
    return when (jump) {
        SongInfoJump.Artist, SongInfoJump.AlbumArtist, SongInfoJump.Composer,
        SongInfoJump.Arranger, SongInfoJump.Lyricist -> splitArtistNames(trimmed)
        SongInfoJump.Genre -> splitGenreNames(trimmed)
        else -> listOf(trimmed)
    }.map { it.trim() }.filter { it.isNotBlank() }.distinct()
}

internal fun songInfoJumpRoute(
    jump: SongInfoJump,
    song: Song,
    audioInfo: AudioInfo? = null,
    chosenValue: String = ""
): String? {
    val chosen = chosenValue.trim()
    return when (jump) {
        SongInfoJump.Path -> song.parentFolderPath()?.let {
            Screen.MetadataCategoryDetail.createRoute("folder", it)
        }
        SongInfoJump.Directory -> song.parentFolderPath()?.let {
            Screen.FolderDetail.createRoute(it)
        }
        SongInfoJump.Format -> audioInfo?.let {
            Screen.LibraryAnalysis.createBucketRoute(quality = false, label = formatLabel(song, it))
        }
        SongInfoJump.Bitrate -> audioInfo?.let {
            Screen.LibraryAnalysis.createBucketRoute(quality = true, label = qualityLabel(song, it))
        }
        SongInfoJump.Artist, SongInfoJump.AlbumArtist -> chosen.takeIf { it.isNotBlank() }?.let {
            Screen.ArtistDetail.createRoute(it)
        }
        SongInfoJump.Album -> song.albumIdentityId().takeIf { it > 0L }?.let {
            Screen.AlbumDetail.createRoute(it)
        }
        SongInfoJump.Genre -> chosen.takeIf { it.isNotBlank() }?.let {
            Screen.MetadataCategoryDetail.createRoute("genre", it)
        }
        SongInfoJump.Year -> (chosen.extractYear() ?: chosen.takeIf { it.isNotBlank() })?.let {
            Screen.MetadataCategoryDetail.createRoute("year", it)
        }
        SongInfoJump.Composer -> chosen.takeIf { it.isNotBlank() }?.let {
            Screen.MetadataCategoryDetail.createRoute("composer", it)
        }
        SongInfoJump.Arranger -> chosen.takeIf { it.isNotBlank() }?.let {
            Screen.MetadataCategoryDetail.createRoute("arranger", it)
        }
        SongInfoJump.Lyricist -> chosen.takeIf { it.isNotBlank() }?.let {
            Screen.MetadataCategoryDetail.createRoute("lyricist", it)
        }
    }
}
