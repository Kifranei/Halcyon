package com.ella.music.player

import android.os.Bundle
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.ella.music.data.model.Song

/**
 * Publishes notification/lock-screen metadata and the audible crossfade item without mutating the
 * wrapped playback position or queue ordering. Bridge 4.0 lyric metadata is written to the real
 * current MediaItem so external clients can read its extras directly.
 *
 * The wrapped player remains the single source of truth for queue, position and duration. This
 * layer changes only the state snapshot observed by MediaSession clients.
 */
@OptIn(UnstableApi::class)
internal class SessionPresentationPlayer(
    player: Player,
    private val cachedOplusLyricProvider: (songKey: String) -> String? = { null }
) : ForwardingSimpleBasePlayer(player) {
    private data class NotificationLyric(
        val songKey: String,
        val title: String,
        val secondaryText: String
    )

    private data class OPlusLyric(
        val songKey: String,
        val lyricInfoJson: String,
        val publishSequence: Long
    )

    private data class CrossfadePresentation(
        val targetIndex: Int,
        val startPositionMs: Long,
        val startedAtElapsedRealtimeMs: Long,
        val playbackSpeed: Float
    ) {
        fun currentPositionMs(): Long = startPositionMs +
            ((SystemClock.elapsedRealtime() - startedAtElapsedRealtimeMs) * playbackSpeed).toLong()
    }

    private var notificationLyric: NotificationLyric? = null
    private var oplusLyric: OPlusLyric? = null
    private var oplusPublishSequence = 0L
    private var keepSongIdentityMetadata = false
    private var crossfadePresentation: CrossfadePresentation? = null

    fun presentCrossfadeTarget(targetIndex: Int, positionMs: Long, playbackSpeed: Float) {
        val next = CrossfadePresentation(
            targetIndex = targetIndex,
            startPositionMs = positionMs.coerceAtLeast(0L),
            startedAtElapsedRealtimeMs = SystemClock.elapsedRealtime(),
            playbackSpeed = playbackSpeed.coerceAtLeast(0f)
        )
        crossfadePresentation = next
        invalidateState()
    }

    fun clearCrossfadePresentation() {
        if (crossfadePresentation == null) return
        crossfadePresentation = null
        invalidateState()
    }

    fun setNotificationLyric(songKey: String, title: String?, secondaryText: String?) {
        val next = title?.takeIf(String::isNotBlank)?.let {
            NotificationLyric(
                songKey = songKey,
                title = it,
                secondaryText = secondaryText.orEmpty()
            )
        }
        if (notificationLyric == next) return
        notificationLyric = next
        if (!keepSongIdentityMetadata) invalidateState()
    }

    fun clearNotificationLyric() {
        if (notificationLyric == null) return
        notificationLyric = null
        if (!keepSongIdentityMetadata) invalidateState()
    }

    fun setKeepSongIdentityMetadata(keep: Boolean) {
        if (keepSongIdentityMetadata == keep) return
        keepSongIdentityMetadata = keep
        invalidateState()
    }

    fun setOplusLyric(
        songKey: String?,
        lyricInfoJson: String?,
        forceRepublish: Boolean = false
    ) {
        if (songKey == null || lyricInfoJson.isNullOrBlank()) {
            if (oplusLyric == null) return
            oplusLyric = null
            invalidateState()
            return
        }
        val currentJson = oplusLyric?.takeIf { it.songKey == songKey }?.lyricInfoJson
        val action = OPlusLyricPublishPolicy.actionFor(
            currentLyricInfo = currentJson,
            currentRawLyric = currentJson?.let(OPlusLyricPayload::rawLyric),
            targetLyricInfo = lyricInfoJson,
            targetRawLyric = OPlusLyricPayload.rawLyric(lyricInfoJson),
            force = forceRepublish
        )
        if (action != OPlusLyricPublishAction.Write) return
        oplusLyric = OPlusLyric(
            songKey = songKey,
            lyricInfoJson = lyricInfoJson,
            publishSequence = ++oplusPublishSequence
        )
        invalidateState()
    }

    /**
     * Writes the Bridge 4.0 payload to the real current MediaItem. The presentation overlay still
     * makes the session snapshot immediate, but the item itself must also carry lyricInfo so the
     * MediaSession and notification clients receive the same metadata contract.
     */
    fun replaceCurrentOplusLyricInfo(song: Song?, lyricInfoJson: String?): Boolean {
        val player = getPlayer()
        val index = player.currentMediaItemIndex
        val currentItem = player.currentMediaItem ?: return false
        if (index !in 0 until player.mediaItemCount) return false
        val currentSong = currentItem.toSongFromMediaItemExtras() ?: return false
        if (song != null && !currentSong.isSamePlaybackIdentity(song)) return false

        val currentExtras = currentItem.mediaMetadata.extras ?: Bundle.EMPTY
        val currentJson = currentExtras.getString(OPlusLyricHandler.OPLUS_LYRIC_INFO_KEY)
        val currentRaw = currentExtras.getString(OPlusLyricHandler.OPLUS_RAW_LYRIC_KEY)
        val targetJson = lyricInfoJson?.takeIf { it.isNotBlank() }
        val action = OPlusLyricPublishPolicy.actionFor(
            currentLyricInfo = currentJson,
            currentRawLyric = currentRaw,
            targetLyricInfo = targetJson,
            targetRawLyric = targetJson?.let(OPlusLyricPayload::rawLyric)
        )
        if (action == OPlusLyricPublishAction.None) return false

        val extras = Bundle(currentExtras).apply {
            remove(OPlusLyricHandler.OPLUS_LYRIC_INFO_KEY)
            remove(OPlusLyricHandler.OPLUS_RAW_LYRIC_KEY)
            remove(OPLUS_LYRIC_PUBLISH_SEQUENCE_KEY)
            markMetadataOnlyPatch(PATCH_REASON_SESSION_PRESENTATION)
            if (targetJson != null) {
                putString(OPlusLyricHandler.OPLUS_LYRIC_INFO_KEY, targetJson)
                OPlusLyricPayload.rawLyric(targetJson)?.let { putString(OPlusLyricHandler.OPLUS_RAW_LYRIC_KEY, it) }
            }
        }
        val updatedItem = currentItem.buildUpon()
            .setMediaMetadata(currentItem.mediaMetadata.buildUpon().setExtras(extras).build())
            .build()
        player.replaceMediaItem(index, updatedItem)
        return true
    }

    override fun getState(): SimpleBasePlayer.State {
        val state = super.getState()
        val crossfade = crossfadePresentation
            ?.takeIf { it.targetIndex in 0 until state.timeline.windowCount }
        val currentItem = if (crossfade != null) {
            getPlayer().getMediaItemAt(crossfade.targetIndex)
        } else {
            getPlayer().currentMediaItem
        } ?: return state
        val song = currentItem.toSongFromMediaItemExtras() ?: return state
        val songKey = song.playbackStackKey()
        val baseMetadata = crossfade?.let { currentItem.mediaMetadata } ?: state.currentMetadata
        val metadataBuilder = baseMetadata.buildUpon()
        val extras = Bundle(baseMetadata.extras ?: currentItem.mediaMetadata.extras ?: Bundle.EMPTY).apply {
            markMetadataOnlyPatch(PATCH_REASON_SESSION_PRESENTATION)
            remove(OPlusLyricHandler.OPLUS_LYRIC_INFO_KEY)
            remove(OPlusLyricHandler.OPLUS_RAW_LYRIC_KEY)
            remove(OPLUS_LYRIC_PUBLISH_SEQUENCE_KEY)
        }

        // ColorOS Bridge treats TITLE/ARTIST/DISPLAY_* changes as track-identity updates.
        // Keep those fields on the real song while lyricInfo is the lock-screen lyric channel.
        if (!keepSongIdentityMetadata) {
            notificationLyric
                ?.takeIf { it.songKey == songKey }
                ?.let { lyric ->
                    val secondary = lyric.secondaryText.ifBlank { "${song.title} · ${song.artist}" }
                    metadataBuilder
                        .setTitle(lyric.title)
                        .setDisplayTitle(lyric.title)
                        .setArtist(secondary)
                        .setSubtitle(secondary)
                }
        }

        val overlay = oplusLyric
        val lyricInfoJson = OPlusLyricPublishPolicy.presentationJson(
            songKey = songKey,
            overlaySongKey = overlay?.songKey,
            overlayJson = overlay?.lyricInfoJson,
            cachedJson = cachedOplusLyricProvider(songKey)
        )
        if (lyricInfoJson != null) {
            extras.putString(OPlusLyricHandler.OPLUS_LYRIC_INFO_KEY, lyricInfoJson)
            OPlusLyricPayload.rawLyric(lyricInfoJson)?.let { rawLyric ->
                extras.putString(OPlusLyricHandler.OPLUS_RAW_LYRIC_KEY, rawLyric)
            }
            extras.putLong(
                OPLUS_LYRIC_PUBLISH_SEQUENCE_KEY,
                overlay?.takeIf { it.songKey == songKey }?.publishSequence ?: 0L
            )
        }

        val presentationMetadata = metadataBuilder.setExtras(extras).build()
        val stateBuilder = state.buildUpon()
            .setPlaylist(state.timeline, state.currentTracks, presentationMetadata)
        if (crossfade != null) {
            // The incoming player is already the audible song. Present it immediately instead of
            // leaving clients on the outgoing item until the physical player handoff completes.
            stateBuilder
                .setCurrentMediaItemIndex(crossfade.targetIndex)
                .setContentPositionMs(crossfade.currentPositionMs())
                .setPlaybackState(Player.STATE_READY)
                .setIsLoading(false)
        }
        return stateBuilder.build()
    }

    private companion object {
        const val OPLUS_LYRIC_PUBLISH_SEQUENCE_KEY =
            "com.ella.music.extra.OPLUS_LYRIC_PUBLISH_SEQUENCE"
    }
}
