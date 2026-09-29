package com.ella.music.data.repository

import com.ella.music.data.model.Song
import java.io.StringWriter
import java.io.Writer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class LibrarySnapshotStreamingTest {
    private fun song(id: Long) = Song(id, "Title \"$id\"\n中文", "Artist", "Album", 1, 1000, "/$id.flac", "$id.flac")

    @Test fun streamingPreservesEscapingAndFields() {
        val writer = StringWriter()
        writeLibrarySnapshotJson(writer, listOf(song(1)), emptyList())
        val result = JSONObject(writer.toString())
        assertEquals(1, result.getInt("version"))
        assertEquals(song(1).title, result.getJSONArray("songs").getJSONObject(0).getString("title"))
        assertEquals(0, result.getJSONArray("albums").length())
    }

    @Test fun tenThousandSongsAreWrittenInBoundedChunks() {
        var largestWrite = 0
        var totalChars = 0L
        val sink = object : Writer() {
            override fun write(buffer: CharArray, offset: Int, length: Int) {
                largestWrite = maxOf(largestWrite, length)
                totalChars += length
            }
            override fun flush() = Unit
            override fun close() = Unit
        }
        writeLibrarySnapshotJson(sink, (1L..10_000L).map(::song), emptyList())
        assertTrue(totalChars > 1_000_000)
        assertTrue(largestWrite < 4096)
    }
}
