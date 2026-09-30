package com.ella.music.data.repository

import android.util.AtomicFile
import android.util.JsonReader
import android.util.JsonToken
import com.ella.music.data.model.Album
import com.ella.music.data.model.Song
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal fun songsToLibraryCacheJsonArray(songs: List<Song>): JSONArray {
    val array = JSONArray()
    songs.forEach { song ->
        array.put(
            JSONObject()
                .put("id", song.id)
                .put("title", song.title)
                .put("artist", song.artist)
                .put("album", song.album)
                .put("albumId", song.albumId)
                .put("duration", song.duration)
                .put("path", song.path)
                .put("fileName", song.fileName)
                .put("fileSize", song.fileSize)
                .put("mimeType", song.mimeType)
                .put("dateAdded", song.dateAdded)
                .put("dateModified", song.dateModified)
                .put("trackNumber", song.trackNumber)
                .put("discNumber", song.discNumber)
                .put("albumArtist", song.albumArtist)
                .put("genre", song.genre)
                .put("year", song.year)
                .put("composer", song.composer)
                .put("arranger", song.arranger)
                .put("lyricist", song.lyricist)
                .put("coverUrl", song.coverUrl)
                .put("onlineSource", song.onlineSource)
                .put("onlineId", song.onlineId)
                .put("onlineMvId", song.onlineMvId)
                .put("onlineLyrics", song.onlineLyrics)
                .put("onlineLyricTranslation", song.onlineLyricTranslation)
                .put("onlineLyricPronunciation", song.onlineLyricPronunciation)
        )
    }
    return array
}

internal fun albumsToLibraryCacheJsonArray(albums: List<Album>): JSONArray {
    val array = JSONArray()
    albums.forEach { album ->
        array.put(
            JSONObject()
                .put("id", album.id)
                .put("name", album.name)
                .put("artist", album.artist)
                .put("songCount", album.songCount)
                .put("year", album.year)
                .put("artAlbumId", album.artAlbumId)
                .put("albumArtist", album.albumArtist)
        )
    }
    return array
}

/**
 * Stream the cached library straight off disk with [JsonReader] instead of reading the whole
 * file into a String and building a JSONObject tree. For ~800+ songs the tree form produced a
 * multi-MB string plus thousands of transient objects, contributing to the cold-start GC spike.
 */
internal fun readLibraryCacheSongs(file: File): List<Song> {
    if (!hasLibraryCache(file)) return emptyList()
    val songs = ArrayList<Song>()
    var foundSongsArray = false
    // AtomicFile restores the previous complete snapshot if the process was killed in the
    // middle of replacing the cache. A plain File reader would see a truncated JSON document
    // after a crash (for example from an overlay service), leaving every album view empty.
    AtomicFile(file).openRead().bufferedReader().use { reader ->
        JsonReader(reader).use { json ->
            json.beginObject()
            while (json.hasNext()) {
                if (json.nextName() == "songs") {
                    foundSongsArray = true
                    json.beginArray()
                    while (json.hasNext()) {
                        songs.add(json.readCacheSong())
                    }
                    json.endArray()
                } else {
                    json.skipValue()
                }
            }
            json.endObject()
        }
    }
    check(foundSongsArray) { "Library cache is missing its songs array: ${file.name}" }
    return songs
}

internal fun hasLibraryCache(file: File): Boolean =
    file.exists() || File("${file.path}.bak").exists()

internal fun writeLibraryCacheAtomically(file: File, json: String) =
    writeLibraryCacheAtomically(file) { it.write(json) }

internal fun writeLibraryCacheAtomically(file: File, write: (java.io.Writer) -> Unit) {
    val atomicFile = AtomicFile(file)
    val stream = atomicFile.startWrite()
    try {
        val writer = stream.bufferedWriter(Charsets.UTF_8)
        write(writer)
        writer.flush()
        atomicFile.finishWrite(stream)
    } catch (error: Throwable) {
        atomicFile.failWrite(stream)
        throw error
    }
}

private fun JsonReader.readCacheSong(): Song {
    var id = 0L
    var title = ""
    var artist = ""
    var album = ""
    var albumId = 0L
    var duration = 0L
    var path = ""
    var fileName = ""
    var fileSize = 0L
    var mimeType = ""
    var dateAdded = 0L
    var dateModified = 0L
    var trackNumber = 0
    var discNumber = 0
    var albumArtist = ""
    var genre = ""
    var year = ""
    var composer = ""
    var arranger = ""
    var lyricist = ""
    var coverUrl = ""
    var onlineSource = ""
    var onlineId = ""
    var onlineMvId = ""
    var onlineLyrics = ""
    var onlineLyricTranslation = ""
    var onlineLyricPronunciation = ""
    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = nextLong()
            "title" -> title = nextStringOrEmpty()
            "artist" -> artist = nextStringOrEmpty()
            "album" -> album = nextStringOrEmpty()
            "albumId" -> albumId = nextLong()
            "duration" -> duration = nextLong()
            "path" -> path = nextStringOrEmpty()
            "fileName" -> fileName = nextStringOrEmpty()
            "fileSize" -> fileSize = nextLong()
            "mimeType" -> mimeType = nextStringOrEmpty()
            "dateAdded" -> dateAdded = nextLong()
            "dateModified" -> dateModified = nextLong()
            "trackNumber" -> trackNumber = nextInt()
            "discNumber" -> discNumber = nextInt()
            "albumArtist" -> albumArtist = nextStringOrEmpty()
            "genre" -> genre = nextStringOrEmpty()
            "year" -> year = nextStringOrEmpty()
            "composer" -> composer = nextStringOrEmpty()
            "arranger" -> arranger = nextStringOrEmpty()
            "lyricist" -> lyricist = nextStringOrEmpty()
            "coverUrl" -> coverUrl = nextStringOrEmpty()
            "onlineSource" -> onlineSource = nextStringOrEmpty()
            "onlineId" -> onlineId = nextStringOrEmpty()
            "onlineMvId" -> onlineMvId = nextStringOrEmpty()
            "onlineLyrics" -> onlineLyrics = nextStringOrEmpty()
            "onlineLyricTranslation" -> onlineLyricTranslation = nextStringOrEmpty()
            "onlineLyricPronunciation" -> onlineLyricPronunciation = nextStringOrEmpty()
            else -> skipValue()
        }
    }
    endObject()
    return Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        duration = duration,
        path = path,
        fileName = fileName,
        fileSize = fileSize,
        mimeType = mimeType,
        dateAdded = dateAdded,
        dateModified = dateModified,
        trackNumber = trackNumber,
        discNumber = discNumber,
        albumArtist = albumArtist,
        genre = genre,
        year = year,
        composer = composer,
        arranger = arranger,
        lyricist = lyricist,
        coverUrl = coverUrl,
        onlineSource = onlineSource,
        onlineId = onlineId,
        onlineMvId = onlineMvId,
        onlineLyrics = onlineLyrics,
        onlineLyricTranslation = onlineLyricTranslation,
        onlineLyricPronunciation = onlineLyricPronunciation
    )
}

private fun JsonReader.nextStringOrEmpty(): String {
    if (peek() == JsonToken.NULL) {
        nextNull()
        return ""
    }
    return nextString()
}

/** Write one record at a time so large libraries never allocate a full JSON tree and string. */
internal fun writeLibrarySnapshot(file: File, songs: List<Song>, albums: List<Album>) =
    writeLibraryCacheAtomically(file) { writeLibrarySnapshotJson(it, songs, albums) }

internal fun writeLibrarySnapshotJson(writer: java.io.Writer, songs: List<Song>, albums: List<Album>) {
    writer.write("{\"version\":1,\"songs\":[")
    songs.forEachIndexed { index, song ->
        if (index > 0) writer.write(",")
        writer.write(songsToLibraryCacheJsonArray(listOf(song)).getJSONObject(0).toString())
    }
    writer.write("],\"albums\":[")
    albums.forEachIndexed { index, album ->
        if (index > 0) writer.write(",")
        writer.write(albumsToLibraryCacheJsonArray(listOf(album)).getJSONObject(0).toString())
    }
    writer.write("]}")
}
