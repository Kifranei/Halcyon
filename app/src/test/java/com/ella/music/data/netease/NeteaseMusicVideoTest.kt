package com.ella.music.data.netease

import com.ella.music.data.model.*
import com.ella.music.player.toPlaybackQueueJson
import com.ella.music.player.toPlaybackQueueSongOrNull
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NeteaseMusicVideoTest {
    @Test fun mvSurvivesPlaylistAndQueuePersistenceWithoutChangingSongIdentity() {
        val song = parseNeteaseSong(JSONObject("""{"id":42,"name":"Track","mv":123456}"""))!!
        assertEquals("123456", song.onlineMvId)
        val playlistRoundTrip = song.toPlaylistSong().toJson().toPlaylistSong().toSong()
        assertEquals(song.onlineMvId, playlistRoundTrip.onlineMvId)
        val queueRoundTrip = song.toPlaybackQueueJson().toPlaybackQueueSongOrNull()!!
        assertEquals(song.onlineMvId, queueRoundTrip.onlineMvId)
        assertEquals(song.playlistIdentityKey(), song.copy(onlineMvId = "").playlistIdentityKey())
    }
    @Test fun missingOrZeroMvDoesNotProduceAButton() {
        for (raw in listOf("""{"id":42}""", """{"id":42,"mv":0}""")) {
            assertEquals("", parseNeteaseSong(JSONObject(raw))!!.onlineMvId)
        }
        assertEquals("99", parseNeteaseSong(JSONObject("""{"id":42,"mvid":99}"""))!!.onlineMvId)
    }
}
