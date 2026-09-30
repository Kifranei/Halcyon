package com.ella.music.player

import com.ella.music.data.model.Song
import com.ella.music.data.LxSourceConfig
import com.ella.music.data.MusicFreePluginConfig
import com.ella.music.data.netease.NeteasePlaybackProvider
import java.io.StringWriter
import java.io.Writer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlaybackQueueStreamingTest {
    private val snapshot = PlaybackStateSnapshot(1, 1234L, 2, true, 1f, 1f, false)
    private val song = Song(1L, "Quoted \"title\"\n", "Artist", "Album", 2L, 3000L, "/music/a.flac", "a.flac")

    @Test fun streamingRetainsQueueOrderDuplicatesAndState() {
        val writer = StringWriter()
        writePlaybackQueue(writer, snapshot, listOf(song, song))
        val json = JSONObject(writer.toString())
        assertEquals(1, json.getInt("index"))
        assertEquals(1234L, json.getLong("positionMs"))
        assertTrue(json.getBoolean("shuffle"))
        val songs = json.getJSONArray("songs")
        assertEquals(2, songs.length())
        assertEquals(song.title, songs.getJSONObject(1).getString("title"))
        assertEquals(song.path, songs.getJSONObject(0).getString("path"))
    }

    @Test fun tenThousandSongsAreWrittenInBoundedChunks() {
        var maxChunk = 0
        var total = 0L
        val writer = object : Writer() {
            override fun write(chars: CharArray, offset: Int, length: Int) {
                maxChunk = maxOf(maxChunk, length)
                total += length
            }
            override fun flush() {}
            override fun close() {}
        }
        writePlaybackQueue(writer, snapshot, List(10000) { song })
        assertTrue(total > 1_000_000L)
        assertTrue("No whole-queue string allocation", maxChunk < 2048)
    }

    @Test fun officialPlaybackReloadIgnoresPluginAndOnlineQualityChanges() {
        val first = neteasePlaybackReloadConfig(NeteasePlaybackProvider.Official, "standard", "auto", null, null)
        val unrelatedChanges = neteasePlaybackReloadConfig(
            NeteasePlaybackProvider.Official, "standard", "lossless",
            LxSourceConfig("lx", "", "LX", "updated script"),
            MusicFreePluginConfig("mf", "", "MusicFree", "updated script")
        )
        assertEquals(first, unrelatedChanges)
        assertNotEquals(first, neteasePlaybackReloadConfig(NeteasePlaybackProvider.Official, "lossless", "auto", null, null))
    }

    @Test fun pluginPlaybackReloadTracksOnlyItsSelectedSourceAndQuality() {
        val lx = LxSourceConfig("lx", "", "LX", "script")
        val mf = MusicFreePluginConfig("mf", "", "MusicFree", "script")
        val first = neteasePlaybackReloadConfig(NeteasePlaybackProvider.Lx, "standard", "auto", lx, mf)
        assertEquals(first, neteasePlaybackReloadConfig(NeteasePlaybackProvider.Lx, "lossless", "auto", lx, mf.copy(script = "other update")))
        assertNotEquals(first, neteasePlaybackReloadConfig(NeteasePlaybackProvider.Lx, "standard", "lossless", lx, mf))
        assertNotEquals(first, neteasePlaybackReloadConfig(NeteasePlaybackProvider.Lx, "standard", "auto", lx.copy(script = "updated"), mf))
        assertNotEquals(first, neteasePlaybackReloadConfig(NeteasePlaybackProvider.MusicFree, "standard", "auto", lx, mf))
        val musicFree = neteasePlaybackReloadConfig(NeteasePlaybackProvider.MusicFree, "standard", "auto", lx, mf)
        assertEquals(musicFree, neteasePlaybackReloadConfig(NeteasePlaybackProvider.MusicFree, "lossless", "auto", lx.copy(script = "other update"), mf))
        assertNotEquals(musicFree, neteasePlaybackReloadConfig(NeteasePlaybackProvider.MusicFree, "standard", "auto", lx, mf.copy(script = "updated")))
    }
}
