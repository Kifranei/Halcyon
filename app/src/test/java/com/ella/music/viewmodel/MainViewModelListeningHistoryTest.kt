package com.ella.music.viewmodel

import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.PlaybackHistorySource
import com.ella.music.data.lastfm.LastFmTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class MainViewModelListeningHistoryTest {

    @Test
    fun mergedHistoryKeepsTheLocalRecordForTheSameListen() {
        val local = PlaybackHistoryEntry(
            songId = 42L,
            title = "See You Again",
            artist = "Wiz Khalifa",
            album = "Furious 7",
            playedAt = 1_000_000L,
            source = PlaybackHistorySource.LOCAL
        )
        val remoteDuplicate = local.copy(songId = -42L, playedAt = 1_030_000L, source = PlaybackHistorySource.LAST_FM)
        val remoteDistinct = remoteDuplicate.copy(playedAt = 1_300_000L)

        val merged = mergePlaybackHistorySources(listOf(local), listOf(remoteDuplicate, remoteDistinct))

        assertEquals(2, merged.size)
        assertSame(local, merged.single { it.source == PlaybackHistorySource.LOCAL })
        assertEquals(remoteDistinct, merged.single { it.source == PlaybackHistorySource.LAST_FM })
    }

    @Test
    fun combinedDurationDoesNotCountTheSameLocalAndRemoteListenTwice() {
        val local = PlaybackHistoryEntry(
            songId = 42L,
            title = "See You Again",
            artist = "Wiz Khalifa",
            album = "Furious 7",
            playedAt = 1_000_000L
        )
        val remote = LastFmTrack(
            title = "See You Again",
            artist = "Wiz Khalifa",
            album = "Furious 7",
            playedAt = 1_030_000L,
            durationMs = 90_000L
        )

        assertEquals(
            emptyMap<String, Long>(),
            estimateLastFmDailyListenMs(listOf(remote), emptyList(), listOf(local))
        )
    }

    @Test
    fun remoteHistoryUsesAStableEntryIdThatCanBeHiddenLocally() {
        val track = LastFmTrack(
            title = "Bad record",
            artist = "Unknown",
            album = "Broken import",
            playedAt = 1_000_000L
        )

        assertEquals(track.toPlaybackHistoryEntry().entryId, track.toPlaybackHistoryEntry().entryId)
    }

    @Test
    fun mergedHistoryKeepsRepeatedPlaysWithDistinctRecordIds() {
        val first = PlaybackHistoryEntry(
            entryId = "first",
            songId = 42L,
            title = "Repeat",
            artist = "Artist",
            album = "Album",
            playedAt = 1_000_000L
        )
        val second = first.copy(entryId = "second", playedAt = 1_030_000L)

        assertEquals(2, mergePlaybackHistorySources(listOf(first, second), emptyList()).size)
    }
}
