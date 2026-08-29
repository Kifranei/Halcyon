package com.ella.music.data

import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import com.ella.music.data.model.albumIdentityId

object LibraryAlbumAggregator {
    fun toAlbums(songs: List<Song>): List<Album> {
        return songs.toAlbumAccumulators()
            .values
            .map { it.toAlbum(unknownAlbumName = "Unknown Album") }
            .sortedWith(
                compareBy<Album> { it.name.lowercase() }
                    .thenBy { it.artist.lowercase() }
                    .thenBy { it.id }
            )
    }

    fun durationsByAlbumIdentity(songs: List<Song>): Map<Long, Long> {
        val durations = HashMap<Long, Long>()
        songs.forEach { song ->
            val albumId = song.albumIdentityId()
            durations[albumId] = (durations[albumId] ?: 0L) + song.duration
        }
        return durations
    }

    fun representativeSongsByAlbumIdentity(songs: List<Song>): Map<Long, Song?> {
        val representatives = HashMap<Long, Song?>()
        songs.forEach { song ->
            representatives.putIfAbsent(song.albumIdentityId(), song)
        }
        return representatives
    }

    fun toAlbumsForSongs(
        songs: List<Song>,
        libraryAlbums: List<Album>,
        unknownAlbumName: String = "Unknown Album"
    ): List<Album> {
        val albumById = libraryAlbums.associateBy { it.id }
        return songs.toAlbumAccumulators()
            .map { (albumId, accumulator) ->
                albumById[albumId] ?: accumulator.toAlbum(
                    unknownAlbumName = unknownAlbumName
                )
            }
    }

    private fun List<Song>.toAlbumAccumulators(): LinkedHashMap<Long, AlbumAccumulator> {
        val albums = LinkedHashMap<Long, AlbumAccumulator>()
        forEach { song ->
            val albumId = song.albumIdentityId()
            val accumulator = albums.getOrPut(albumId) { AlbumAccumulator(albumId, song) }
            accumulator.add(song)
        }
        return albums
    }

    private class AlbumAccumulator(
        private val albumId: Long,
        private val first: Song
    ) {
        private var count = 0
        private var minYear = ""

        fun add(song: Song) {
            count++
            val year = song.year.takeIf { it.isNotBlank() } ?: return
            if (minYear.isBlank() || year < minYear) minYear = year
        }

        fun toAlbum(unknownAlbumName: String): Album {
            // Album cards must never substitute the track artist for a missing album artist.
            val artist = first.albumArtist.takeIf(LibraryNormalizer::isUsableArtistText).orEmpty()
            return Album(
                id = albumId,
                name = first.album.takeIf(LibraryNormalizer::isUsableAlbumText) ?: unknownAlbumName,
                artist = artist,
                songCount = count,
                year = minYear,
                artAlbumId = first.albumId,
                albumArtist = first.albumArtist.takeIf(LibraryNormalizer::isUsableArtistText).orEmpty()
            )
        }
    }
}
