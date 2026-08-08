package com.ella.music.player

import androidx.media3.common.Player
import com.ella.music.data.model.Song
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresentationMetadataDiscontinuityPolicyTest {
    @Test
    fun ignoresInternalDiscontinuityForSameSongDuringPresentationGuard() {
        val song = song(1L)

        assertTrue(
            shouldIgnorePresentationMetadataDiscontinuity(
                reason = Player.DISCONTINUITY_REASON_INTERNAL,
                presentationSongKey = song.playbackStackKey(),
                presentationGuardUntilMs = 2_000L,
                itemSong = song,
                currentSong = song,
                nowMs = 1_500L
            )
        )
    }

    @Test
    fun neverIgnoresARealSeek() {
        val song = song(1L)

        assertFalse(
            shouldIgnorePresentationMetadataDiscontinuity(
                reason = Player.DISCONTINUITY_REASON_SEEK,
                presentationSongKey = song.playbackStackKey(),
                presentationGuardUntilMs = 2_000L,
                itemSong = song,
                currentSong = song,
                nowMs = 1_500L
            )
        )
    }

    @Test
    fun expiresAfterPresentationGuardWindow() {
        val song = song(1L)

        assertFalse(
            shouldIgnorePresentationMetadataDiscontinuity(
                reason = Player.DISCONTINUITY_REASON_INTERNAL,
                presentationSongKey = song.playbackStackKey(),
                presentationGuardUntilMs = 2_000L,
                itemSong = song,
                currentSong = song,
                nowMs = 2_000L
            )
        )
    }

    private fun song(id: Long): Song = Song(
        id = id,
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 180_000L,
        path = "/music/song-$id.flac",
        fileName = "song-$id.flac"
    )
}
