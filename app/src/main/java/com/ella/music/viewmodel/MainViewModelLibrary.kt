package com.ella.music.viewmodel

import com.ella.music.data.NameSplitConfigStore
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.SongPlaybackStats
import com.ella.music.data.matchesArtistName
import com.ella.music.data.model.Album
import com.ella.music.data.model.Artist
import com.ella.music.data.model.Song
import com.ella.music.data.model.UserPlaylist
import com.ella.music.data.model.albumIdentityId
import com.ella.music.data.model.playlistIdentityKey
import com.ella.music.data.splitArtistNames
import com.ella.music.data.tagIdentityKey
import com.ella.music.ui.components.selectMetadataCategoryCoverSong

internal fun buildArtists(
    songs: List<Song>,
    albums: List<Album>,
    includeAlbumArtists: Boolean
): List<Artist> {
    val counts = linkedMapOf<String, ArtistAccumulator>()
    val albumIdsByArtist = mutableMapOf<String, MutableSet<Long>>()

    songs.forEach { song ->
        splitArtistNames(song.artist).forEach { rawName ->
            val key = rawName.tagIdentityKey()
            val accumulator = counts.getOrPut(key) { ArtistAccumulator(rawName) }
            accumulator.songCount += 1
            albumIdsByArtist.getOrPut(key) { mutableSetOf() } += song.albumIdentityId()
        }
        if (includeAlbumArtists) {
            splitArtistNames(song.albumArtist).forEach { rawName ->
                val key = rawName.tagIdentityKey()
                counts.getOrPut(key) { ArtistAccumulator(rawName) }
                albumIdsByArtist.getOrPut(key) { mutableSetOf() } += song.albumIdentityId()
            }
        }
    }

    if (includeAlbumArtists) {
        albums.forEach { album ->
            splitArtistNames(album.albumArtist).forEach { rawName ->
                val key = rawName.tagIdentityKey()
                counts.getOrPut(key) { ArtistAccumulator(rawName) }
                if (album.id > 0L) {
                    albumIdsByArtist.getOrPut(key) { mutableSetOf() } += album.id
                }
            }
        }
    }

    return counts
        .map { (key, accumulator) ->
            Artist(
                name = accumulator.name,
                songCount = accumulator.songCount,
                albumCount = albumIdsByArtist[key]?.size ?: 0
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

internal fun buildMetadataCategoryItems(
    songs: List<Song>,
    type: String
): List<MetadataCategoryItem> {
    val groups = linkedMapOf<String, MetadataCategoryAccumulator>()
    songs.forEach { song ->
        song.metadataCategoryNames(type).forEach { name ->
            val key = name.tagIdentityKey()
            groups.getOrPut(key) { MetadataCategoryAccumulator(name) }.add(song)
        }
    }
    val albumArtistSongsByKey = linkedMapOf<String, MutableList<Song>>()
    if (type in setOf("composer", "arranger", "lyricist")) {
        songs.forEach { song ->
            splitArtistNames(song.albumArtist).forEach { name ->
                albumArtistSongsByKey.getOrPut(name.tagIdentityKey()) { mutableListOf() } += song
            }
        }
    }
    return groups.values
        .map { item ->
            // The cover policy also considers album-artist candidates for person categories.
            // Add those candidates to the role group so the category page follows the same
            // four-level policy as the album detail and global search without rescanning the
            // complete library for every category card.
            val coverCandidates = item.songs + albumArtistSongsByKey[item.name.tagIdentityKey()].orEmpty()
            val coverSong = selectMetadataCategoryCoverSong(coverCandidates, type, item.name)
            MetadataCategoryItem(
                name = item.name,
                songCount = item.songCount,
                albumCount = item.albumIds.size,
                duration = item.duration,
                dateModified = item.dateModified,
                coverAlbumIds = listOfNotNull(coverSong?.albumId?.takeIf { it > 0L }) +
                    item.coverAlbumIds.filterNot { it == coverSong?.albumId },
                representativeSong = coverSong ?: item.firstSong
            )
        }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

private class MetadataCategoryAccumulator(
    val name: String
) {
    var songCount: Int = 0
        private set
    var duration: Long = 0L
        private set
    var dateModified: Long = 0L
        private set
    val albumIds: MutableSet<Long> = linkedSetOf()
    private val coverAlbumIdSet = linkedSetOf<Long>()
    val coverAlbumIds: List<Long>
        get() = coverAlbumIdSet.toList()
    val songs = mutableListOf<Song>()
    var firstSong: Song? = null
        private set
    var representativeSongWithCover: Song? = null
        private set

    fun add(song: Song) {
        songs += song
        songCount += 1
        duration += song.duration
        if (song.dateModified > dateModified) dateModified = song.dateModified
        albumIds += song.albumIdentityId()
        if (song.albumId > 0L && coverAlbumIdSet.size < 3) {
            coverAlbumIdSet += song.albumId
        }
        if (firstSong == null) firstSong = song
        if (representativeSongWithCover == null && song.albumId > 0L) {
            representativeSongWithCover = song
        }
    }
}

internal fun countMetadataCategories(
    songs: List<Song>,
    type: String
): Int {
    val keys = HashSet<String>()
    songs.forEach { song ->
        song.metadataCategoryNames(type).forEach { name ->
            keys += name.tagIdentityKey()
        }
    }
    return keys.size
}

internal fun countMetadataCategories(
    songs: List<Song>,
    types: Collection<String>
): Map<String, Int> {
    val keySets = types.associateWith { HashSet<String>() }
    songs.forEach { song ->
        keySets.forEach { (type, keys) ->
            song.metadataCategoryNames(type).forEach { name ->
                keys += name.tagIdentityKey()
            }
        }
    }
    return keySets.mapValues { (_, keys) -> keys.size }
}

internal fun filterSongsForMetadataCategory(
    songs: List<Song>,
    type: String,
    name: String
): List<Song> {
    val target = name.trim()
    if (target.isBlank()) return emptyList()
    return songs
        .filter { song -> song.metadataCategoryNames(type).any { it.equals(target, ignoreCase = NameSplitConfigStore.tagIgnoreCase) } }
        .sortedWith(
            compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.album }
                .thenBy { if (it.discNumber > 0) it.discNumber else Int.MAX_VALUE }
                .thenBy { if (it.trackNumber > 0) it.trackNumber else Int.MAX_VALUE }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { song -> song.title }
        )
}

internal fun filterSongsForMetadataCategories(
    songs: List<Song>,
    type: String,
    names: Collection<String>
): List<Song> {
    val targetKeys = names
        .asSequence()
        .map(String::tagIdentityKey)
        .filter(String::isNotBlank)
        .toHashSet()
    if (targetKeys.isEmpty()) return emptyList()
    return songs.filter { song ->
        song.metadataCategoryNames(type).any { it.tagIdentityKey() in targetKeys }
    }
}

internal fun filterSongsForArtist(
    songs: List<Song>,
    artistName: String,
    includeAlbumArtist: Boolean
): List<Song> {
    return songs.filter { song ->
        song.artist.matchesArtistName(artistName) ||
            (includeAlbumArtist && song.albumArtist.matchesArtistName(artistName))
    }
}

internal fun containsMetadataCategory(
    songs: List<Song>,
    type: String,
    name: String
): Boolean {
    val target = name.trim()
    if (target.isBlank()) return false
    return songs.any { song ->
        song.metadataCategoryNames(type).any { it.equals(target, ignoreCase = NameSplitConfigStore.tagIgnoreCase) }
    }
}

internal fun String.toFolderFilterList(): List<String> {
    return split('\n', ';', '；')
        .map { it.trim() }
        .filter { it.isNotBlank() }
}

internal fun buildOpenAiRecommendationCandidates(
    library: List<Song>,
    stats: List<SongPlaybackStats>,
    history: List<PlaybackHistoryEntry>,
    maxCandidates: Int = 160
): List<Song> {
    if (library.size <= maxCandidates) return library.distinctBy { it.playlistIdentityKey() }

    val songsById = library.associateBy { it.id }
    val selected = linkedMapOf<String, Song>()

    fun add(song: Song) {
        if (selected.size >= maxCandidates) return
        selected.putIfAbsent(song.playlistIdentityKey(), song)
    }

    history
        .mapNotNull { entry -> songsById[entry.songId] }
        .take(60)
        .forEach(::add)

    stats
        .sortedWith(
            compareByDescending<SongPlaybackStats> { it.playCount }
                .thenByDescending { it.listenedMs }
                .thenByDescending { it.lastPlayedAt }
        )
        .mapNotNull { stat -> songsById[stat.songId] }
        .take(60)
        .forEach(::add)

    stats
        .sortedByDescending { it.lastPlayedAt }
        .mapNotNull { stat -> songsById[stat.songId] }
        .take(40)
        .forEach(::add)

    library
        .sortedByDescending { it.dateModified }
        .take(40)
        .forEach(::add)

    val remaining = maxCandidates - selected.size
    if (remaining > 0) {
        val sortedLibrary = library.sortedWith(
            compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist.ifBlank { it.albumArtist } }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.album }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.title }
        )
        val step = (sortedLibrary.size / remaining.coerceAtLeast(1)).coerceAtLeast(1)
        sortedLibrary.forEachIndexed { index, song ->
            if (selected.size < maxCandidates && index % step == 0) add(song)
        }
    }

    return selected.values.toList().ifEmpty { library.take(maxCandidates) }
}

internal fun buildPlaylistCustomOrder(
    customPlaylists: List<UserPlaylist>,
    currentOrder: List<String>,
    newPlaylistIds: List<String>
): List<String> {
    val customIds = customPlaylists.mapTo(linkedSetOf()) { it.id }
    if (customIds.isEmpty()) return emptyList()

    val newIds = newPlaylistIds
        .filter { it in customIds }
        .distinct()
    return buildList {
        addAll(newIds)
        currentOrder.forEach { id ->
            if (id in customIds && id !in this) add(id)
        }
        customPlaylists
            .sortedWith(
                compareByDescending<UserPlaylist> { it.createdAt }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.name.lowercase() }
                    .thenBy { it.id }
            )
            .forEach { playlist ->
                if (playlist.id !in this) add(playlist.id)
            }
    }
}
