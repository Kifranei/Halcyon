package com.ella.music.ui.player

import com.ella.music.data.model.Song
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueSnapshotKeyTest {
    private fun song(path: String, source: String? = null) = Song(
        id = path.hashCode().toLong(),
        title = path,
        artist = "artist",
        album = "album",
        albumId = 1L,
        duration = 1_000L,
        path = path,
        fileName = path,
        playbackSourceKey = source
    )

    @Test
    fun queueKeyIncludesOrderAndOccurrenceSource() {
        val first = song("/music/a.flac", "folder:a")
        val second = song("/music/b.flac", "folder:b")

        assertNotEquals(queueSnapshotKey(listOf(first, second)), queueSnapshotKey(listOf(second, first)))
        assertNotEquals(queueSnapshotKey(listOf(first)), queueSnapshotKey(listOf(first.copy(playbackSourceKey = "album:a"))))
        assertEquals(queueSnapshotKey(listOf(first, second)), queueSnapshotKey(listOf(first, second)))
    }
}
