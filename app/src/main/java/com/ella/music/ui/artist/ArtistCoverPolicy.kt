package com.ella.music.ui.artist

import com.ella.music.data.artistNamesForSong
import com.ella.music.data.matchesArtistName
import com.ella.music.data.model.Song
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey

/**
 * Lower values are preferred. Custom artist assets are resolved by the UI before this fallback
 * policy, so this function only ranks library song artwork.
 */
internal fun artistCoverPriority(song: Song, artistName: String): Int? = when {
    song.albumArtist.isSoleArtist(artistName) -> 0
    song.isSoleArtist(artistName) -> 1
    song.albumArtist.matchesArtistName(artistName) -> 2
    artistNamesForSong(song).any { it.tagIdentityKey() == artistName.tagIdentityKey() } -> 3
    else -> null
}

internal fun selectArtistCoverSong(songs: List<Song>, artistName: String): Song? {
    var selected: Song? = null
    var selectedPriority = Int.MAX_VALUE
    songs.forEach { song ->
        val priority = artistCoverPriority(song, artistName) ?: return@forEach
        if (priority < selectedPriority) {
            selected = song
            selectedPriority = priority
        }
    }
    return selected
}

private fun String.isSoleArtist(artistName: String): Boolean {
    val names = splitArtistNames(this)
    return names.size == 1 && names.single().tagIdentityKey() == artistName.tagIdentityKey()
}

private fun Song.isSoleArtist(artistName: String): Boolean {
    val names = artistNamesForSong(this)
    return names.size == 1 && names.single().tagIdentityKey() == artistName.tagIdentityKey()
}
