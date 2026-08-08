package com.ella.music.player

import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingSimpleBasePlayer
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi

/**
 * Publishes notification and lock-screen metadata without mutating the playback timeline.
 *
 * The wrapped player remains the single source of truth for queue, position and duration. This
 * layer only changes the metadata snapshot observed by MediaSession clients.
 */
@OptIn(UnstableApi::class)
internal class SessionPresentationPlayer(player: Player) : ForwardingSimpleBasePlayer(player) {
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

    private var notificationLyric: NotificationLyric? = null
    private var oplusLyric: OPlusLyric? = null
    private var oplusPublishSequence = 0L

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
        invalidateState()
    }

    fun clearNotificationLyric() {
        if (notificationLyric == null) return
        notificationLyric = null
        invalidateState()
    }

    fun setOplusLyric(songKey: String?, lyricInfoJson: String?) {
        if (songKey == null || lyricInfoJson.isNullOrBlank()) {
            if (oplusLyric == null) return
            oplusLyric = null
            invalidateState()
            return
        }
        if (
            oplusLyric?.songKey == songKey &&
            oplusLyric?.lyricInfoJson == lyricInfoJson
        ) {
            return
        }
        oplusLyric = OPlusLyric(
            songKey = songKey,
            lyricInfoJson = lyricInfoJson,
            publishSequence = ++oplusPublishSequence
        )
        invalidateState()
    }

    override fun getState(): SimpleBasePlayer.State {
        val state = super.getState()
        val currentItem = getPlayer().currentMediaItem ?: return state
        val song = currentItem.toSongFromMediaItemExtras() ?: return state
        val songKey = song.playbackStackKey()
        val baseMetadata = state.currentMetadata
        val metadataBuilder = baseMetadata.buildUpon()
        val extras = Bundle(baseMetadata.extras ?: currentItem.mediaMetadata.extras ?: Bundle.EMPTY).apply {
            markMetadataOnlyPatch(PATCH_REASON_SESSION_PRESENTATION)
            remove(OPlusLyricHandler.OPLUS_LYRIC_INFO_KEY)
            remove(OPlusLyricHandler.OPLUS_RAW_LYRIC_KEY)
            remove(OPLUS_LYRIC_PUBLISH_SEQUENCE_KEY)
        }

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

        oplusLyric
            ?.takeIf { it.songKey == songKey }
            ?.let { lyric ->
                extras.putString(OPlusLyricHandler.OPLUS_LYRIC_INFO_KEY, lyric.lyricInfoJson)
                OPlusLyricPayload.rawLyric(lyric.lyricInfoJson)?.let { rawLyric ->
                    extras.putString(OPlusLyricHandler.OPLUS_RAW_LYRIC_KEY, rawLyric)
                }
                extras.putLong(OPLUS_LYRIC_PUBLISH_SEQUENCE_KEY, lyric.publishSequence)
            }

        val presentationMetadata = metadataBuilder.setExtras(extras).build()
        return state.buildUpon()
            .setPlaylist(state.timeline, state.currentTracks, presentationMetadata)
            .build()
    }

    private companion object {
        const val OPLUS_LYRIC_PUBLISH_SEQUENCE_KEY =
            "com.ella.music.extra.OPLUS_LYRIC_PUBLISH_SEQUENCE"
    }
}
