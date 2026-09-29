package com.ella.music.data.netease

import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import org.json.JSONObject

internal const val NETEASE_HISTORY_SOURCE = "netease"

internal data class NeteaseRecentPlay(val song: Song, val playedAt: Long)

/** Parses /play-record/song/list: data.list[] = { resourceId, playTime, resourceType, data: <song> }. */
internal fun parseNeteaseRecentPlays(root: JSONObject): List<NeteaseRecentPlay> {
    val rows = root.optJSONObject("data")?.optJSONArray("list") ?: return emptyList()
    return (0 until rows.length()).mapNotNull { index ->
        val row = rows.optJSONObject(index) ?: return@mapNotNull null
        val type = row.optString("resourceType")
        if (type.isNotBlank() && !type.equals("SONG", ignoreCase = true)) return@mapNotNull null
        val playedAt = row.optLong("playTime").takeIf { it > 0L } ?: return@mapNotNull null
        val song = row.optJSONObject("data")?.let(::parseNeteaseSong) ?: return@mapNotNull null
        NeteaseRecentPlay(song, playedAt)
    }.sortedByDescending { it.playedAt }
}

/** Deterministic id: the same cloud listen maps to the same entry across refreshes and restarts. */
internal fun NeteaseRecentPlay.toPlaybackHistoryEntry(): PlaybackHistoryEntry = PlaybackHistoryEntry(
    entryId = "netease:${song.onlineId}:$playedAt",
    songId = song.id,
    title = song.title,
    artist = song.artist,
    album = song.album,
    playedAt = playedAt,
    durationMs = song.duration,
    source = NETEASE_HISTORY_SOURCE,
    playCounted = true,
    onlineSource = NETEASE_SOURCE,
    onlineId = song.onlineId,
    coverUrl = song.coverUrl
)

/**
 * Rebuilds a playable NetEase [Song] from a history record, for listens of songs that are neither
 * in the favourites library nor in the fetched cloud history (or when another library is active).
 */
internal fun PlaybackHistoryEntry.toNeteaseHistorySong(): Song? {
    if (onlineSource != NETEASE_SOURCE || mediaUri.isNotBlank()) return null
    val id = onlineId.toLongOrNull()?.takeIf { it > 0L } ?: return null
    return Song(
        id = -id, title = title, artist = artist, album = album, albumId = 0L, duration = durationMs,
        path = "$NETEASE_SCHEME://song/$id", fileName = title, mimeType = "",
        coverUrl = coverUrl, onlineSource = NETEASE_SOURCE, onlineId = id.toString()
    )
}
