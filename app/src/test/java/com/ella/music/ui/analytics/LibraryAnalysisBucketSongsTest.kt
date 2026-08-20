package com.ella.music.ui.analytics

import com.ella.music.data.model.Song
import com.ella.music.data.model.AudioInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryAnalysisBucketSongsTest {
    private fun song(id: Long, title: String) = Song(
        id = id, title = title, artist = "Artist", album = "Album", albumId = 1L,
        duration = 1_000L, path = "/music/$title.flac", fileName = "$title.flac"
    )

    @Test
    fun bucketsIncludeKeysAndFilterSongs() {
        val first = song(1, "first")
        val second = song(2, "second")
        val rows = listOf(
            SongWithInfo(first, AudioInfo(format = "FLAC")),
            SongWithInfo(second, AudioInfo(format = "MP3"))
        )
        val buckets = rows.toBuckets { it.info.format }
        val analysis = LibraryAnalysis(buckets, emptyList(), 2, 0L)
        assertEquals(listOf(first), analysis.songsForBucket(listOf(first, second), false, "FLAC"))
        assertEquals(first.searchIdentityKey(), buckets.first { it.label == "FLAC" }.songKeys.single())
    }
}
