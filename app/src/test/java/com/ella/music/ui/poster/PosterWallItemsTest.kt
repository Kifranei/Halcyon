package com.ella.music.ui.poster

import com.ella.music.data.model.Song
import org.junit.Assert.*
import org.junit.Test

class PosterWallItemsTest {
    private fun song(id: Long, source: String? = null) = Song(id, "Track $id", "Artist", "Album", 0, 100000, "/music/$id.flac", "$id.flac", playbackSourceKey = source)

    @Test fun refreshingChangesTheWallWithoutChangingQueueOccurrencesOrSearchResults() {
        val queue = listOf(song(1), song(2), song(3), song(2, "playlist:b"))
        val original = buildPosterWallItems(queue, "queue", "")
        var previous = original
        var seed = 0
        repeat(12) {
            seed = nextPosterWallShuffleSeed(previous, seed)
            val next = rearrangePosterWallItems(original, seed)
            assertNotEquals(previous.map { it.key }, next.map { it.key })
            assertEquals(original.toSet(), next.toSet())
            next.forEach { assertEquals(it.sourceIndex, resolvePosterQueueIndex(queue, it.song, it.sourceIndex)) }
            previous = next
            assertEquals(next, rearrangePosterWallItems(original, seed))
        }
        val filtered = buildPosterWallItems(queue, "queue", "Track 2")
        val shuffled = rearrangePosterWallItems(filtered, nextPosterWallShuffleSeed(filtered, 0))
        assertEquals(listOf(3, 1), shuffled.map { it.sourceIndex })
        assertEquals(filtered, rearrangePosterWallItems(filtered, 0))
        assertTrue(rearrangePosterWallItems(emptyList(), 1).isEmpty())
        assertEquals(original.take(1), rearrangePosterWallItems(original.take(1), 1))
    }

    @Test fun searchingKeepsOriginalQueueIndicesAndDuplicateOccurrences() {
        val queue = listOf(song(1), song(2), song(3), song(2))
        val items = buildPosterWallItems(queue, "queue", "  track 2  ")
        assertEquals(listOf(1, 3), items.map { it.sourceIndex })
        assertEquals(2, items.map { it.key }.distinct().size)
        assertEquals(3, resolvePosterQueueIndex(queue, items.last().song, items.last().sourceIndex))
    }

    @Test fun aReorderedQueueFindsTheSamePlaybackSourceOccurrence() {
        val first = song(2, "album:a")
        val second = song(2, "playlist:b")
        assertEquals(2, resolvePosterQueueIndex(listOf(second, song(3), first), first, 0))
        assertEquals(-1, resolvePosterQueueIndex(listOf(second), first, 0))
    }

    @Test fun albumArtistAndFileSearchUseTheSameSongSnapshot() {
        val songs = listOf(song(1), song(2).copy(artist = "Mili"), song(3).copy(album = "Moon"))
        assertEquals(listOf(1), buildPosterWallItems(songs, "library", "MILI").map { it.sourceIndex })
        assertEquals(listOf(2), buildPosterWallItems(songs, "library", "Moon").map { it.sourceIndex })
        assertEquals(listOf(0), buildPosterWallItems(songs, "library", "1.flac").map { it.sourceIndex })
    }
}
