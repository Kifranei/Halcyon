package com.ella.music.ui.analytics

import com.ella.music.R
import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

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

    private fun historyEntry(song: Song, playedAt: Long = System.currentTimeMillis()) = ResolvedHistoryEntry(
        entry = PlaybackHistoryEntry(
            songId = song.id,
            title = song.title,
            artist = song.artist,
            album = song.album,
            playedAt = playedAt
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
        assertNotNull(insight?.song)
    }

    @Test
    fun favoriteArtistInsightWithEmptyHistoryReturnsNull() {
        val insight = emptyList<ResolvedHistoryEntry>().favoriteArtistInsight()
        assertNull(insight)
    }
}
