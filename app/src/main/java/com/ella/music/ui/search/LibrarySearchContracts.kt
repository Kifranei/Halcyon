package com.ella.music.ui.search

import com.ella.music.R
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.UserPlaylist
import com.ella.music.viewmodel.MetadataCategoryItem

internal fun SearchFilter.labelRes(): Int = when (this) {
    SearchFilter.All -> R.string.library_search_all
    SearchFilter.Songs -> R.string.library_search_songs
    SearchFilter.MusicVideos -> R.string.library_search_filter_mv
    SearchFilter.Artists -> R.string.library_search_artists
    SearchFilter.Albums -> R.string.library_search_albums
    SearchFilter.Playlists -> R.string.library_search_playlists
    SearchFilter.Folders -> R.string.library_search_folders
    SearchFilter.Composers -> R.string.library_search_composers
    SearchFilter.Arrangers -> R.string.library_search_arrangers
    SearchFilter.Lyricists -> R.string.library_search_lyricists
    SearchFilter.Lyrics -> R.string.library_search_lyrics
    SearchFilter.Genres -> R.string.library_search_genres
    SearchFilter.Years -> R.string.library_search_years
}

internal fun String.searchLabelRes(): Int = when (this) {
    "folder" -> R.string.library_search_folders
    "composer" -> R.string.library_search_composers
    "arranger" -> R.string.library_search_arrangers
    "lyricist" -> R.string.library_search_lyricists
    "genre" -> R.string.library_search_genres
    "year" -> R.string.library_search_years
    else -> R.string.library_search_all
}

internal sealed interface SearchActionTarget {
    val title: String

    data class AlbumTarget(val album: Album) : SearchActionTarget {
        override val title: String = album.name
    }

    data class ArtistTarget(val artist: Artist) : SearchActionTarget {
        override val title: String = artist.name
    }

    data class PlaylistTarget(val playlist: UserPlaylist) : SearchActionTarget {
        override val title: String = playlist.name
    }

    data class CategoryTarget(val type: String, val item: MetadataCategoryItem) : SearchActionTarget {
        override val title: String = item.name.substringAfterLast('/').ifBlank { item.name }
    }
}
