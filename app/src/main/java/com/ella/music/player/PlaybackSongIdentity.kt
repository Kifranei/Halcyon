package com.ella.music.player

import com.ella.music.data.model.Song

internal fun Song?.isSamePlaybackIdentity(other: Song?): Boolean {
    if (this == null || other == null) return this == other
    if (path.isNotBlank() && other.path.isNotBlank()) {
        return path == other.path
    }
    if (onlineSource.isNotBlank() || onlineId.isNotBlank() || other.onlineSource.isNotBlank() || other.onlineId.isNotBlank()) {
        return onlineSource == other.onlineSource &&
            onlineId == other.onlineId &&
            path == other.path
    }
    if (id > 0L && other.id > 0L) return id == other.id
    return title == other.title && artist == other.artist && album == other.album && duration == other.duration
}

/**
 * Map a MediaController item onto the app playlist at [currentIndex].
 *
 * After [ExoPlayerManager.setPlaylist], Binder can still report the outgoing item (or index 0 of
 * the previous queue) for a frame. Prefer the playlist slot whenever identities differ so the
 * mini player / cover do not stay on the previous album until a second tap.
 */
internal fun resolveControllerPlaylistSong(
    currentIndex: Int,
    playlistSize: Int,
    itemSong: Song?,
    playlistSong: Song?
): Song? {
    if (currentIndex in 0 until playlistSize) {
        return itemSong?.takeIf { it.isSamePlaybackIdentity(playlistSong) } ?: playlistSong
    }
    return itemSong ?: playlistSong
}

internal fun Song.playbackStackKey(): String = when {
    path.isNotBlank() -> "path:$path"
    onlineSource.isNotBlank() || onlineId.isNotBlank() -> "online:$onlineSource:$onlineId"
    id > 0L -> "id:$id"
    else -> "title:$title|artist:$artist|album:$album"
}

internal fun Song.notificationArtworkKey(): String =
    "${id}:${path}:${dateModified}:${fileSize}:${coverUrl}"
