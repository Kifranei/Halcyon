package com.ella.music.player

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OPlusExternalLyricProtocolTest {
    @Test
    fun trackKeyNormalizesTitleAndArtist() {
        val song = song(title = "  Remember Our Summer ", artist = "FrogMonster  蛙蛙")
        assertEquals("remember our summer|frogmonster 蛙蛙", OPlusExternalLyricProtocol.trackKey(song))
    }

    @Test
    fun trackKeyFallsBackToTitleWhenArtistIsBlank() {
        val song = song(title = "Interlude", artist = "  ")
        assertEquals("interlude", OPlusExternalLyricProtocol.trackKey(song))
        assertEquals(OPlusExternalLyricProtocol.MATCH_POLICY_TITLE_ONLY, OPlusExternalLyricProtocol.matchPolicy(song))
    }

    @Test
    fun mediaIdPrefersOnlineIdentityThenLocalId() {
        val online = song().copy(onlineSource = "netease", onlineId = "12345")
        assertEquals("netease:12345", OPlusExternalLyricProtocol.mediaId(online))
        assertEquals("9", OPlusExternalLyricProtocol.mediaId(song(id = 9L)))
    }

    @Test
    fun sessionBumpsGenerationOnSongChangeAndDedupesLyricReady() {
        val session = OPlusExternalLyricSession()
        assertTrue(session.onSong("song-a"))
        assertEquals(1L, session.generation)
        assertTrue(session.shouldSendLyricReady("song-a", """{"lyric":"[00:01.00]Hi"}""", force = false))
        assertFalse(session.shouldSendLyricReady("song-a", """{"lyric":"[00:01.00]Hi"}""", force = false))
        assertTrue(session.shouldSendLyricReady("song-a", """{"lyric":"[00:01.00]Hi"}""", force = true))
        assertTrue(session.onSong("song-b"))
        assertEquals(2L, session.generation)
        assertTrue(session.shouldSendLyricReady("song-b", """{"lyric":"[00:02.00]New"}""", force = false))
    }

    @Test
    fun protocolConstantsMatchBridgeDirectV4() {
        assertEquals(4, OPlusExternalLyricProtocol.PROTOCOL_VERSION)
        assertEquals(
            "io.github.andrealtb.lockscreenlyrics.action.EXTERNAL_LYRIC_DIRECT_V4",
            OPlusExternalLyricProtocol.ACTION_DIRECT_V4
        )
        assertEquals("lyricprovider/halcyon", OPlusExternalLyricProtocol.SOURCE)
        assertEquals("com.ella.music", OPlusExternalLyricProtocol.PLAYER_PACKAGE)
        assertEquals("com.android.systemui", OPlusExternalLyricProtocol.SYSTEM_UI_PACKAGE)
    }

    private fun song(
        id: Long = 1L,
        title: String = "Song",
        artist: String = "Artist"
    ): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = "Album",
        albumId = 1L,
        duration = 180_000L,
        path = "/music/song.flac",
        fileName = "song.flac"
    )
}
