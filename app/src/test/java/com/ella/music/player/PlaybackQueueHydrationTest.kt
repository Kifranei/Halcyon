package com.ella.music.player

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackQueueHydrationTest {
    @Test
    fun presentationPlayerWithOneItemRestoresThePersistedFullQueue() {
        assertTrue(
            shouldHydrateSavedQueue(
                savedSongCount = 12,
                controllerMediaItemCount = 1,
                savedCurrentIndex = 5
            )
        )
    }

    @Test
    fun matchingControllerTimelineRestoresThePersistedQueue() {
        assertTrue(
            shouldHydrateSavedQueue(
                savedSongCount = 12,
                controllerMediaItemCount = 12,
                savedCurrentIndex = -1
            )
        )
    }

    @Test
    fun unrelatedStaleQueueIsNotApplied() {
        assertFalse(
            shouldHydrateSavedQueue(
                savedSongCount = 12,
                controllerMediaItemCount = 3,
                savedCurrentIndex = -1
            )
        )
    }

    @Test
    fun persistedQueueIndexWinsWhenTheCurrentSongIsDuplicated() {
        val duplicate = song()
        val saved = SavedQueue(
            songs = listOf(song(id = 1L, title = "Before", path = "/music/before.flac"), duplicate, duplicate, song(id = 4L, title = "After", path = "/music/after.flac")),
            index = 2,
            positionMs = 0L,
            repeatMode = 0,
            shuffle = false,
            speed = 1f,
            pitch = 1f,
            queueLocked = false
        )

        assertEquals(2, saved.indexForCurrentSong(duplicate))
    }

    @Test
    fun persistedQueueIndexFallsBackToMatchingOccurrenceWhenCursorIsStale() {
        val duplicate = song()
        val saved = SavedQueue(
            songs = listOf(duplicate, song(id = 2L, title = "Other", path = "/music/other.flac"), duplicate),
            index = 1,
            positionMs = 0L,
            repeatMode = 0,
            shuffle = false,
            speed = 1f,
            pitch = 1f,
            queueLocked = false
        )

        assertEquals(0, saved.indexForCurrentSong(duplicate))
    }

    @Test
    fun movingAnItemKeepsTheCurrentQueueOccurrence() {
        assertEquals(
            2,
            adjustedQueueIndexAfterMove(
                currentIndex = 1,
                fromIndex = 1,
                toIndex = 2,
                queueSize = 4
            )
        )
        assertEquals(
            0,
            adjustedQueueIndexAfterMove(
                currentIndex = 1,
                fromIndex = 0,
                toIndex = 2,
                queueSize = 4
            )
        )
        assertEquals(
            2,
            adjustedQueueIndexAfterMove(
                currentIndex = 1,
                fromIndex = 3,
                toIndex = 0,
                queueSize = 4
            )
        )
    }

    private fun song(
        id: Long = 3L,
        title: String = "Duplicate",
        path: String = "/music/duplicate.flac"
    ): Song = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 1_000L,
        path = path,
        fileName = "duplicate.flac"
    )
}
