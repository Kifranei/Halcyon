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

    @Test
    fun cylinderAudioOnlyFillUsesZeroEmptyTop() {
        // Library storage bar: emptyTopFraction = 0 → colored stack spans full body (no free-space glass).
        val fractions = listOf(0.4f, 0.35f, 0.25f)
        val viewHeight = 400f
        val density = 2f
        val empty = 0f

        val y0 = getCylinderSliceCenterY(0, viewHeight, density, fractions.size, fractions, empty)
        val y2 = getCylinderSliceCenterY(2, viewHeight, density, fractions.size, fractions, empty)

        // With decorative defaults padY/ry, full-body colored range should be used (top slice near cylTop).
        val padY = 6f * density
        val cylWidth = 94f * density - 8f * density
        val rx = cylWidth / 2f
        val ry = rx * 0.18f
        val cylTopY = padY + ry
        val cylBotY = viewHeight - padY - ry
        assertTrue("Top slice center should be in upper half of body", y0 < (cylTopY + cylBotY) / 2f)
        assertTrue("Bottom slice center should be in lower half of body", y2 > (cylTopY + cylBotY) / 2f)
        assertEquals(0, getCylinderSliceIndex(y0, viewHeight, density, fractions.size, fractions, empty))
        assertEquals(2, getCylinderSliceIndex(y2, viewHeight, density, fractions.size, fractions, empty))
        // Fractions among audio buckets still sum to ~1 (relative proportions only).
        assertEquals(1f, fractions.sum(), 0.0001f)
    }

}
