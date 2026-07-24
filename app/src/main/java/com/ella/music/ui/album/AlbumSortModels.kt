package com.ella.music.ui.album

import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.formatPlaybackDuration
import com.ella.music.ui.components.toFastIndexSection
import com.ella.music.ui.listmodel.MusicSortKeyNormalizer

internal enum class AlbumSortMode(val labelRes: Int) {
    Name(R.string.album_sort_name),
    Artist(R.string.album_sort_artist),
    SongCount(R.string.playlist_sort_song_count),
    Duration(R.string.playlist_song_sort_duration),
    YearAsc(R.string.playlist_song_sort_year_asc),
    YearDesc(R.string.playlist_song_sort_year_desc),
    NameDesc(R.string.album_sort_name),
    ArtistDesc(R.string.album_sort_artist),
    SongCountAsc(R.string.playlist_sort_song_count),
    DurationAsc(R.string.playlist_song_sort_duration)
}

internal fun AlbumSortMode.isDescending(): Boolean = when (this) {
    AlbumSortMode.YearDesc,
    AlbumSortMode.NameDesc,
    AlbumSortMode.ArtistDesc,
    AlbumSortMode.SongCount,
    AlbumSortMode.Duration -> true
    else -> false
}

internal fun Album.summaryForSort(context: android.content.Context, sortMode: AlbumSortMode, duration: Long): String {
    if (sortMode == AlbumSortMode.Artist || sortMode == AlbumSortMode.ArtistDesc) {
        return buildList {
            albumArtist.ifBlank { artist }.trim().takeIf { it.isNotBlank() }?.let(::add)
            if (year.isNotBlank()) add(year)
            add(context.getString(R.string.song_count, songCount))
        }.joinToString(" · ")
    }
    val first = if (sortMode == AlbumSortMode.Duration || sortMode == AlbumSortMode.DurationAsc) {
        duration.formatAlbumDuration()
    } else {
        context.getString(R.string.song_count, songCount)
    }
    return buildList {
        add(first)
        if (year.isNotBlank()) add(year)
        val artistText = albumArtist.trim()
        if (artistText.isNotBlank()) add(artistText)
    }.joinToString(" · ")
}

private fun Long.formatAlbumDuration(): String {
    return formatPlaybackDuration()
}

internal fun Album.indexLetter(sortMode: AlbumSortMode): String {
    val source = if (sortMode == AlbumSortMode.Artist || sortMode == AlbumSortMode.ArtistDesc) albumArtist.ifBlank { artist } else name
    return source.musicSortKey().toFastIndexSection()
}

internal fun String.musicSortKey(): String {
    return MusicSortKeyNormalizer.normalize(this)
}
