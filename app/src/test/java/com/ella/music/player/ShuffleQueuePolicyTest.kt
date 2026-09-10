package com.ella.music.player

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShuffleQueuePolicyTest {
    @Test
    fun enableShuffleDoesNotImmediatelyRequireQueueMutation() {
        assertTrue(
            shouldDeferShuffleReorder(
                enableShuffle = true,
                previousShuffle = false,
                queueSize = 3,
                hasVirtualQueue = false
            )
        )
    }

    @Test
    fun smallOrVirtualQueuesDoNotDeferShuffleReorder() {
        assertFalse(
            shouldDeferShuffleReorder(
                enableShuffle = true,
                previousShuffle = false,
                queueSize = 1,
                hasVirtualQueue = false
            )
        )
        assertFalse(
            shouldDeferShuffleReorder(
                enableShuffle = true,
                previousShuffle = false,
                queueSize = 3,
                hasVirtualQueue = true
            )
        )
    }

    @Test
    fun pendingShuffleReordersOnNextKeepingCurrentFirst() {
        val songs = songs(4)
        val plan = buildShuffleQueueKeepingCurrent(
            sourceOrder = songs,
            current = songs[1],
            currentIndexHint = 1,
            seed = 42L
        )

        assertEquals(songs[1], plan?.queue?.first())
        assertEquals(0, plan?.currentIndex)
        assertEquals(songs.toSet(), plan?.queue?.toSet())
    }

    @Test
    fun pendingShuffleChoosesDifferentNextWhenPossible() {
        val songs = songs(5)
        val plan = buildShuffleQueueKeepingCurrent(
            sourceOrder = songs,
            current = songs[2],
            currentIndexHint = 2,
            seed = 7L
        )

        assertEquals(songs[2], plan?.queue?.first())
        assertNotEquals(songs[2], plan?.queue?.get(1))
    }

    @Test
    fun shuffleCycleDoesNotStartWithPreviousRoundLastSong() {
        val songs = songs(5)
        val plan = buildShuffleQueueForCycle(
            sourceOrder = songs,
            previousLast = songs.last(),
            seed = 7L
        )

        assertEquals(songs.toSet(), plan?.queue?.toSet())
        assertNotEquals(songs.last(), plan?.queue?.first())
        assertEquals(0, plan?.currentIndex)
    }

    @Test
    fun shuffleCycleReturnsNoPlanWhenEveryOccurrenceIsThePreviousSong() {
        val song = song(1, "/music/same.flac")
        assertNull(buildShuffleQueueForCycle(listOf(song, song), song, seed = 1L))
    }

    @Test
    fun duplicateSongsKeepCurrentIndexHint() {
        val duplicate = song(1, "/music/same.flac")
        val songs = listOf(song(0), duplicate, song(2), duplicate, song(4))
        val plan = buildShuffleQueueKeepingCurrent(
            sourceOrder = songs,
            current = duplicate,
            currentIndexHint = 3,
            seed = 11L
        )

        assertEquals(duplicate, plan?.queue?.first())
        assertEquals(5, plan?.queue?.size)
        assertEquals(1, plan?.queue?.drop(1)?.count { it.isSamePlaybackIdentity(duplicate) })
    }

    @Test
    fun repeatOnePolicyCanSkipUnexpectedShuffleJump() {
        assertNull(
            buildShuffleQueueKeepingCurrent(
                sourceOrder = listOf(song(1)),
                current = song(1),
                currentIndexHint = 0,
                seed = 1L
            )
        )
    }

    @Test
    fun pendingShuffleUserSelectQueueItemClearsNativeShuffleButCanKeepOriginalOrder() {
        val plan = clearPendingShufflePlan(
            hasOriginalOrder = true,
            disableNativeShuffle = true,
            clearOriginalOrder = false
        )

        assertFalse(plan.pending)
        assertFalse(plan.nativeShuffle)
        assertTrue(plan.keepOriginalOrder)
    }

    @Test
    fun pendingShuffleQueueMutationClearsStaleSourceOrder() {
        val plan = clearPendingShufflePlan(
            hasOriginalOrder = true,
            disableNativeShuffle = true,
            clearOriginalOrder = true
        )

        assertFalse(plan.pending)
        assertFalse(plan.nativeShuffle)
        assertFalse(plan.keepOriginalOrder)
    }

    @Test
    fun pendingShuffleDisableShuffleCleansNativeAndOriginalOrder() {
        val action = pendingShuffleReorderAction(
            pending = true,
            shuffleEnabled = false,
            repeatOne = false,
            queueSize = 4,
            hasVirtualQueue = false
        )
        val cleanup = clearPendingShufflePlan(
            hasOriginalOrder = true,
            disableNativeShuffle = true,
            clearOriginalOrder = true
        )

        assertEquals(PendingShuffleReorderAction.Clear, action)
        assertFalse(cleanup.pending)
        assertFalse(cleanup.nativeShuffle)
        assertFalse(cleanup.keepOriginalOrder)
    }

    @Test
    fun repeatOneWithPendingShuffleClearsInsteadOfHangingForever() {
        val action = pendingShuffleReorderAction(
            pending = true,
            shuffleEnabled = true,
            repeatOne = true,
            queueSize = 4,
            hasVirtualQueue = false
        )

        assertEquals(PendingShuffleReorderAction.Clear, action)
    }

    @Test
    fun notificationNativeShuffleCanBeAdoptedAsPendingWhenManagerReconnects() {
        assertTrue(
            shouldAdoptNativeShuffleAsPending(
                appShuffleEnabled = true,
                pending = false,
                nativeShuffleEnabled = true,
                queueSize = 4,
                hasVirtualQueue = false
            )
        )
        assertFalse(
            shouldAdoptNativeShuffleAsPending(
                appShuffleEnabled = true,
                pending = false,
                nativeShuffleEnabled = true,
                queueSize = 1,
                hasVirtualQueue = false
            )
        )
        assertFalse(
            shouldAdoptNativeShuffleAsPending(
                appShuffleEnabled = false,
                pending = false,
                nativeShuffleEnabled = true,
                queueSize = 4,
                hasVirtualQueue = false
            )
        )
    }

    private fun songs(count: Int): List<Song> = (0 until count).map(::song)

    private fun song(id: Int, path: String = "/music/$id.flac"): Song = Song(
        id = id.toLong(),
        title = "Song $id",
        artist = "Artist",
        album = "Album",
        albumId = 1L,
        duration = 180_000L,
        path = path,
        fileName = path.substringAfterLast('/')
    )
}
