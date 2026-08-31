package com.ella.music.player

import androidx.media3.common.Player
import android.util.JsonReader
import android.util.JsonToken
import com.ella.music.data.model.Song
import java.io.StringReader
import org.json.JSONArray
import org.json.JSONObject

internal data class SavedQueue(
    val songs: List<Song>,
    val index: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean,
    val speed: Float,
    val pitch: Float,
    val queueLocked: Boolean
)

/**
 * Resolve the saved queue occurrence that was actually playing.
 *
 * A queue may contain the same playback identity more than once, so an
 * identity-only indexOfFirst() would incorrectly restore or highlight the first
 * duplicate instead of the persisted occurrence.
 */
internal fun SavedQueue.indexForCurrentSong(song: Song?): Int {
    val persistedIndex = index.takeIf { it in songs.indices }
    if (song == null) return persistedIndex ?: -1

    val persistedSong = persistedIndex?.let(songs::get)
    if (persistedSong != null &&
        persistedSong.isSamePlaybackIdentity(song) &&
        (song.playbackSourceKey == null || persistedSong.playbackSourceKey == song.playbackSourceKey)
    ) {
        return persistedIndex
    }

    val sourceMatch = songs.indexOfFirst {
        it.isSamePlaybackIdentity(song) &&
            (song.playbackSourceKey == null || it.playbackSourceKey == song.playbackSourceKey)
    }
    return sourceMatch.takeIf { it >= 0 }
        ?: songs.indexOfFirst { it.isSamePlaybackIdentity(song) }.takeIf { it >= 0 }
        ?: persistedIndex
        ?: -1
}

internal data class PlaybackStateSnapshot(
    val index: Int,
    val positionMs: Long,
    val repeatMode: Int,
    val shuffle: Boolean,
    val speed: Float,
    val pitch: Float,
    val queueLocked: Boolean
) {
    fun toJson(): JSONObject = JSONObject()
        .put("index", index)
        .put("positionMs", positionMs)
        .put("repeatMode", repeatMode)
        .put("shuffle", shuffle)
        .put("speed", speed)
        .put("pitch", pitch)
        .put("queueLocked", queueLocked)
}

internal data class PendingPlaylist(
    val songs: List<Song>,
    val startIndex: Int,
    val honorShuffle: Boolean = true,
    val resetQueueLock: Boolean = true
)

internal fun shouldHydrateSavedQueue(
    savedSongCount: Int,
    controllerMediaItemCount: Int,
    savedCurrentIndex: Int
): Boolean = savedSongCount > 0 &&
    controllerMediaItemCount > 0 &&
    (savedSongCount == controllerMediaItemCount || savedCurrentIndex >= 0)

internal fun playbackQueueJson(snapshot: PlaybackStateSnapshot, songs: List<Song>): JSONObject =
    JSONObject()
        .put("index", snapshot.index)
        .put("positionMs", snapshot.positionMs)
        .put("repeatMode", snapshot.repeatMode)
        .put("shuffle", snapshot.shuffle)
        .put("speed", snapshot.speed)
        .put("pitch", snapshot.pitch)
        .put("queueLocked", snapshot.queueLocked)
        .put("songs", JSONArray().apply {
            songs.forEach { song -> put(song.toPlaybackQueueJson()) }
        })

internal fun parseSavedQueue(rawQueue: String, rawState: String?): SavedQueue? =
    runCatching {
        val state = rawState?.let { runCatching { JSONObject(it) }.getOrNull() }
        var payloadIndex = 0
        var payloadPositionMs = 0L
        var payloadRepeatMode = Player.REPEAT_MODE_OFF
        var payloadShuffle = false
        var payloadSpeed = 1f
        var payloadPitch = 1f
        var payloadQueueLocked = false
        var songs = emptyList<Song>()
        var parsedIndexOffset = 0

        JsonReader(StringReader(rawQueue)).use { json ->
            json.beginObject()
            while (json.hasNext()) {
                when (json.nextName()) {
                    "index" -> payloadIndex = json.nextIntSafe()
                    "positionMs" -> payloadPositionMs = json.nextLongSafe()
                    "repeatMode" -> payloadRepeatMode = json.nextIntSafe(Player.REPEAT_MODE_OFF)
                    "shuffle" -> payloadShuffle = json.nextBooleanSafe()
                    "speed" -> payloadSpeed = json.nextDoubleSafe(1.0).toFloat()
                    "pitch" -> payloadPitch = json.nextDoubleSafe(1.0).toFloat()
                    "queueLocked" -> payloadQueueLocked = json.nextBooleanSafe()
                    "songs" -> {
                        val targetIndex = state?.optInt("index", payloadIndex) ?: payloadIndex
                        val parsed = json.readPlaybackQueueSongWindow(targetIndex)
                        songs = parsed.songs
                        parsedIndexOffset = parsed.indexOffset
                    }
                    else -> json.skipValue()
                }
            }
            json.endObject()
        }

        if (songs.isEmpty()) return@runCatching null
        val index = ((state?.optInt("index", payloadIndex) ?: payloadIndex) - parsedIndexOffset)
            .coerceIn(0, songs.lastIndex)
        SavedQueue(
            songs = songs,
            index = index,
            positionMs = state?.optLong("positionMs", payloadPositionMs) ?: payloadPositionMs,
            repeatMode = state?.optInt("repeatMode", payloadRepeatMode) ?: payloadRepeatMode,
            shuffle = state?.optBoolean("shuffle", payloadShuffle) ?: payloadShuffle,
            speed = (state?.optDouble("speed", payloadSpeed.toDouble()) ?: payloadSpeed.toDouble()).toFloat(),
            pitch = (state?.optDouble("pitch", payloadPitch.toDouble()) ?: payloadPitch.toDouble()).toFloat(),
            queueLocked = state?.optBoolean("queueLocked", payloadQueueLocked) ?: payloadQueueLocked
        )
    }.getOrNull()

internal fun Song.toPlaybackQueueJson(): JSONObject = JSONObject()
    .put("id", id)
    .put("title", title)
    .put("artist", artist)
    .put("album", album)
    .put("albumId", albumId)
    .put("duration", duration)
    .put("path", path)
    .put("fileName", fileName)
    .put("fileSize", fileSize)
    .put("mimeType", mimeType)
    .put("dateAdded", dateAdded)
    .put("dateModified", dateModified)
    .put("trackNumber", trackNumber)
    .put("discNumber", discNumber)
    .put("albumArtist", albumArtist)
    .put("genre", genre)
    .put("year", year)
    .put("composer", composer)
    .put("arranger", arranger)
    .put("lyricist", lyricist)
    .put("coverUrl", coverUrl)
    .put("onlineSource", onlineSource)
    .put("onlineId", onlineId)
    .apply {
        playbackSourceKey?.let { put("playbackSourceKey", it) }
    }

internal fun JSONObject.toPlaybackQueueSongOrNull(): Song? {
    val path = optString("path").takeIf { it.isNotBlank() } ?: return null
    return Song(
        id = optLong("id", path.hashCode().toLong()),
        title = optString("title").ifBlank { optString("fileName").ifBlank { path.substringAfterLast('/') } },
        artist = optString("artist").ifBlank { "Unknown" },
        album = optString("album").ifBlank { "Music" },
        albumId = optLong("albumId", 0L),
        duration = optLong("duration", 0L),
        path = path,
        fileName = optString("fileName").ifBlank { path.substringAfterLast('/') },
        fileSize = optLong("fileSize", 0L),
        mimeType = optString("mimeType"),
        dateAdded = optLong("dateAdded", 0L),
        dateModified = optLong("dateModified", 0L),
        trackNumber = optInt("trackNumber", 0),
        discNumber = optInt("discNumber", 0),
        albumArtist = optString("albumArtist"),
        genre = optString("genre"),
        year = optString("year"),
        composer = optString("composer"),
        arranger = optString("arranger"),
        lyricist = optString("lyricist"),
        coverUrl = optString("coverUrl"),
        onlineSource = optString("onlineSource"),
        onlineId = optString("onlineId"),
        playbackSourceKey = if (has("playbackSourceKey")) {
            optString("playbackSourceKey")
        } else {
            null
        }
    )
}

private data class ParsedQueueWindow(
    val songs: List<Song>,
    val indexOffset: Int
)

private fun JsonReader.readPlaybackQueueSongWindow(targetIndex: Int): ParsedQueueWindow {
    val safeTarget = targetIndex.coerceAtLeast(0)
    if (safeTarget >= LARGE_LIBRARY_SAFE_MODE_THRESHOLD) {
        val start = (safeTarget - LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE / 2).coerceAtLeast(0)
        val endExclusive = start + LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE
        val songs = ArrayList<Song>(LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE)
        var index = 0
        beginArray()
        while (hasNext()) {
            if (index in start until endExclusive) {
                readPlaybackQueueSongOrNull()?.let(songs::add)
            } else {
                skipValue()
            }
            index++
        }
        endArray()
        return ParsedQueueWindow(songs, start)
    }

    // Keep the whole queue for normal libraries. Read just enough extra entries to form a safe
    // window if the stored payload turns out to exceed the large-library threshold.
    val prefixLimit = LARGE_LIBRARY_SAFE_MODE_THRESHOLD + LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE / 2
    val songs = ArrayList<Song>(prefixLimit)
    var index = 0
    beginArray()
    while (hasNext()) {
        if (index < prefixLimit) {
            readPlaybackQueueSongOrNull()?.let(songs::add)
        } else {
            skipValue()
        }
        index++
    }
    endArray()
    if (index <= LARGE_LIBRARY_SAFE_MODE_THRESHOLD) {
        return ParsedQueueWindow(songs, indexOffset = 0)
    }
    val start = (safeTarget - LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE / 2)
        .coerceIn(0, (index - LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE).coerceAtLeast(0))
    return ParsedQueueWindow(
        songs = songs.subList(start, minOf(start + LARGE_LIBRARY_SAFE_MODE_QUEUE_SIZE, songs.size)).toList(),
        indexOffset = start
    )
}

private fun JsonReader.readPlaybackQueueSongOrNull(): Song? {
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
    var playbackSourceKey: String? = null
    var hasPlaybackSourceKey = false

    beginObject()
    while (hasNext()) {
        when (nextName()) {
            "id" -> id = nextLongSafe()
            "title" -> title = nextStringOrEmpty()
            "artist" -> artist = nextStringOrEmpty()
            "album" -> album = nextStringOrEmpty()
            "albumId" -> albumId = nextLongSafe()
            "duration" -> duration = nextLongSafe()
            "path" -> path = nextStringOrEmpty()
            "fileName" -> fileName = nextStringOrEmpty()
            "fileSize" -> fileSize = nextLongSafe()
            "mimeType" -> mimeType = nextStringOrEmpty()
            "dateAdded" -> dateAdded = nextLongSafe()
            "dateModified" -> dateModified = nextLongSafe()
            "trackNumber" -> trackNumber = nextIntSafe()
            "discNumber" -> discNumber = nextIntSafe()
            "albumArtist" -> albumArtist = nextStringOrEmpty()
            "genre" -> genre = nextStringOrEmpty()
            "year" -> year = nextStringOrEmpty()
            "composer" -> composer = nextStringOrEmpty()
            "arranger" -> arranger = nextStringOrEmpty()
            "lyricist" -> lyricist = nextStringOrEmpty()
            "coverUrl" -> coverUrl = nextStringOrEmpty()
            "onlineSource" -> onlineSource = nextStringOrEmpty()
            "onlineId" -> onlineId = nextStringOrEmpty()
            "playbackSourceKey" -> {
                hasPlaybackSourceKey = true
                playbackSourceKey = nextStringOrNull()
            }
            else -> skipValue()
        }
    }
    endObject()

    if (path.isBlank()) return null
    val resolvedFileName = fileName.ifBlank { path.substringAfterLast('/') }
    return Song(
        id = id.takeIf { it != 0L } ?: path.hashCode().toLong(),
        title = title.ifBlank { resolvedFileName },
        artist = artist.ifBlank { "Unknown" },
        album = album.ifBlank { "Music" },
        albumId = albumId,
        duration = duration,
        path = path,
        fileName = resolvedFileName,
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
        playbackSourceKey = if (hasPlaybackSourceKey) playbackSourceKey else null
    )
}

private fun JsonReader.nextStringOrEmpty(): String {
    if (peek() == JsonToken.NULL) {
        nextNull()
        return ""
    }
    return runCatching { nextString() }.getOrDefault("")
}

private fun JsonReader.nextStringOrNull(): String? {
    if (peek() == JsonToken.NULL) {
        nextNull()
        return null
    }
    return runCatching { nextString() }.getOrNull()
}

private fun JsonReader.nextIntSafe(default: Int = 0): Int =
    when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            default
        }
        JsonToken.NUMBER, JsonToken.STRING -> runCatching { nextInt() }.getOrDefault(default)
        else -> {
            skipValue()
            default
        }
    }

private fun JsonReader.nextLongSafe(default: Long = 0L): Long =
    when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            default
        }
        JsonToken.NUMBER, JsonToken.STRING -> runCatching { nextLong() }.getOrDefault(default)
        else -> {
            skipValue()
            default
        }
    }

private fun JsonReader.nextDoubleSafe(default: Double = 0.0): Double =
    when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            default
        }
        JsonToken.NUMBER, JsonToken.STRING -> runCatching { nextDouble() }.getOrDefault(default)
        else -> {
            skipValue()
            default
        }
    }

private fun JsonReader.nextBooleanSafe(default: Boolean = false): Boolean =
    when (peek()) {
        JsonToken.NULL -> {
            nextNull()
            default
        }
        JsonToken.BOOLEAN -> nextBoolean()
        JsonToken.STRING -> runCatching { nextString().toBooleanStrictOrNull() ?: default }.getOrDefault(default)
        else -> {
            skipValue()
            default
        }
    }
