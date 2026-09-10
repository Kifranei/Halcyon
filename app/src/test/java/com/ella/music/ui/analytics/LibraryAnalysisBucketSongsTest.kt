package com.ella.music.ui.analytics

import com.ella.music.data.model.Song
import com.ella.music.data.model.AudioInfo
import com.ella.music.ui.search.searchIdentityKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        val analysis = LibraryAnalysis(
            formatBuckets = buckets,
            qualityBuckets = emptyList(),
            sampleRateBuckets = emptyList(),
            bitDepthBuckets = emptyList(),
            totalCount = 2,
            totalSizeBytes = 0L
        )
        assertEquals(listOf(first), analysis.songsForBucket(listOf(first, second), false, "FLAC"))
        assertEquals(first.searchIdentityKey(), buckets.first { it.label == "FLAC" }.songKeys.single())
    }

    @Test
    fun cylinderSliceTouchAndCenterCoordinates() {
        val fractions = listOf(0.5f, 0.3f, 0.2f)
        val viewHeight = 400f
        val density = 2f

        val y0 = getCylinderSliceCenterY(0, viewHeight, density, fractions.size, fractions)
        val y1 = getCylinderSliceCenterY(1, viewHeight, density, fractions.size, fractions)
        val y2 = getCylinderSliceCenterY(2, viewHeight, density, fractions.size, fractions)

        assertTrue("Slice centers must be vertically sequential", y0 < y1 && y1 < y2)

        assertEquals(0, getCylinderSliceIndex(y0, viewHeight, density, fractions.size, fractions))
        assertEquals(1, getCylinderSliceIndex(y1, viewHeight, density, fractions.size, fractions))
        assertEquals(2, getCylinderSliceIndex(y2, viewHeight, density, fractions.size, fractions))

        assertNull("Far above top bounds should return null", getCylinderSliceIndex(-100f, viewHeight, density, fractions.size, fractions))
        assertNull("Far below bottom bounds should return null", getCylinderSliceIndex(600f, viewHeight, density, fractions.size, fractions))
        assertNull("Empty fractions should return null", getCylinderSliceIndex(y0, viewHeight, density, 0, emptyList()))
    }
}
