package com.ella.music.player

import com.ella.music.data.model.Song
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
}
