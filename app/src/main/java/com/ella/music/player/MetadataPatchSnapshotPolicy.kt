package com.ella.music.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.ella.music.data.model.Song

internal const val PRESENTATION_METADATA_DISCONTINUITY_GUARD_MS = 1_000L

internal fun isDisplayOnlyMetadataPatchSnapshot(
    isMetadataOnlyPatch: Boolean,
    snapshotSong: Song?,
    currentSong: Song?
): Boolean {
    return isMetadataOnlyPatch &&
        currentSong != null &&
        (snapshotSong == null || snapshotSong.isSamePlaybackIdentity(currentSong))
}

internal fun shouldIgnoreMetadataPatchDiscontinuity(
    reason: Int,
    isMetadataOnlyPatch: Boolean,
    itemSong: Song?,
    currentSong: Song?
): Boolean =
    reason == Player.DISCONTINUITY_REASON_INTERNAL &&
        isMetadataOnlyPatch &&
        itemSong?.isSamePlaybackIdentity(currentSong) == true

internal fun shouldIgnorePresentationMetadataDiscontinuity(
    reason: Int,
    presentationSongKey: String?,
    presentationGuardUntilMs: Long,
    itemSong: Song?,
    currentSong: Song?,
    nowMs: Long
): Boolean {
    if (reason != Player.DISCONTINUITY_REASON_INTERNAL || nowMs >= presentationGuardUntilMs) {
        return false
    }
    val current = currentSong ?: return false
    if (presentationSongKey != current.playbackStackKey()) return false
    return itemSong == null || itemSong.isSamePlaybackIdentity(current)
}

internal fun shouldIgnoreDisplayOnlyTimelineUpdate(
    reason: Int,
    currentItem: MediaItem?,
    currentSong: Song?
): Boolean {
    return shouldIgnoreDisplayOnlyTimelineUpdate(
        reason = reason,
        isMetadataOnlyPatch = currentItem?.isMetadataOnlyPatch() == true,
        itemSong = currentItem?.toSongFromMediaItemExtras(),
        currentSong = currentSong
    )
}

internal fun shouldIgnoreDisplayOnlyTimelineUpdate(
    reason: Int,
    isMetadataOnlyPatch: Boolean,
    itemSong: Song?,
    currentSong: Song?
): Boolean {
    // Artwork and base-session metadata may still swap the current item's MediaMetadata without
    // changing the real playback queue. Depending on the Media3 version this surfaces as either
    // SOURCE_UPDATE or PLAYLIST_CHANGED, so ignore both presentation-only variants.
    if (isMetadataOnlyPatch &&
        (reason == Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE ||
            reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED)
    ) {
        return true
    }
    if (reason != Player.TIMELINE_CHANGE_REASON_SOURCE_UPDATE) return false
    return itemSong?.isSamePlaybackIdentity(currentSong) == true
}
