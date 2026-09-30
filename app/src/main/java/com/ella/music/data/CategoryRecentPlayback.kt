package com.ella.music.data

import com.ella.music.data.model.Song

internal fun latestCategoryPlayback(history: List<PlaybackHistoryEntry>, categoryKey: String): PlaybackHistoryEntry? =
    history.asSequence().filter { it.categorySourceKey == categoryKey && it.mediaUri.isBlank() }
        .maxByOrNull { it.playedAt }

internal fun categoryPlaybackSongIndex(songs: List<Song>, entry: PlaybackHistoryEntry): Int {
    val online = entry.onlineSource.isNotBlank() && entry.onlineId.isNotBlank()
    if (online) return songs.indexOfFirst { it.onlineSource == entry.onlineSource && it.onlineId == entry.onlineId }
    val idIndex = songs.indexOfFirst { entry.songId > 0 && it.id == entry.songId }
    if (idIndex >= 0) return idIndex
    return songs.indexOfFirst { it.title == entry.title && it.artist == entry.artist && it.album == entry.album }
}
