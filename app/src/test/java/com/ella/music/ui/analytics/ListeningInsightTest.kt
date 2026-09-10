package com.ella.music.ui.analytics

import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

class ListeningInsightTest {
    private fun song(id: Long, title: String, artist: String, album: String = "Test Album") = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = 100L + id,
        duration = 180_000L,
        path = "/music/$title.flac",
        fileName = "$title.flac"
    )

    private fun historyEntry(
        song: Song,
        playedAt: Long = System.currentTimeMillis(),
        listenedMs: Long = 60_000L
    ) = ResolvedHistoryEntry(
        entry = PlaybackHistoryEntry(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            playedAt = playedAt,
            listenedMs = listenedMs
        ),
        song = song
    )

    @Test
    fun favoriteArtistInsightReturnsMostPlayedArtist() {
        val songA1 = song(1, "Song 1", "Taylor Swift")
        val songA2 = song(2, "Song 2", "Taylor Swift")
        val songB1 = song(3, "Song 3", "Ed Sheeran")

        val entries = listOf(
            historyEntry(songA1),
            historyEntry(songA2),
            historyEntry(songA1),
            historyEntry(songB1)
        )

        val insight = entries.favoriteArtistInsight()
        assertNotNull(insight)
        assertEquals(R.string.analytics_month_favorite_artist, insight?.labelRes)
        assertEquals("Taylor Swift", insight?.title)
        assertEquals(3, insight?.playCount)
        assertEquals(180_000L, insight?.listenedMs)
        assertNotNull(insight?.song)
    }

    @Test
    fun favoriteArtistInsightsReturnsReplayRankedArtistsWithListenTime() {
        val artistA = song(1, "A", "Artist A")
        val artistB = song(2, "B", "Artist B")
        val entries = listOf(
            historyEntry(artistA, listenedMs = 120_000L),
            historyEntry(artistB, listenedMs = 300_000L),
            historyEntry(artistA, listenedMs = 60_000L)
        )

        val insights = entries.favoriteArtistInsights()

        assertEquals(listOf("Artist B", "Artist A"), insights.map { it.title })
        assertEquals(300_000L, insights.first().listenedMs)
        assertEquals(180_000L, insights[1].listenedMs)
    }

    @Test
    fun favoriteArtistInsightWithEmptyHistoryReturnsNull() {
        val insight = emptyList<ResolvedHistoryEntry>().favoriteArtistInsight()
        assertNull(insight)
    }

    @Test
    fun replayMonthTabsAreOrderedFromOldestToCurrent() {
        val now = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 13)
        }

        val tabs = buildReplayMonthTabs(count = 3, now = now)

        assertEquals(listOf(2, 1, 0), tabs.map { it.offsetFromCurrent })
        assertEquals(2026, tabs.last().year)
    }

    @Test
    fun replayMonthTabsDefaultCoversJanuaryThroughCurrentMonth() {
        val now = Calendar.getInstance().apply {
            set(Calendar.YEAR, 2026)
            set(Calendar.MONTH, Calendar.SEPTEMBER)
            set(Calendar.DAY_OF_MONTH, 13)
        }

        val tabs = buildReplayMonthTabs(now = now)

        assertEquals(9, tabs.size)
        assertEquals(8, tabs.first().offsetFromCurrent)
        assertEquals(0, tabs.last().offsetFromCurrent)
        assertEquals(2026, tabs.first().year)
        assertEquals(2026, tabs.last().year)
    }
}
