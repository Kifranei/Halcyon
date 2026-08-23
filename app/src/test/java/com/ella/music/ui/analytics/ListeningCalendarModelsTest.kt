package com.ella.music.ui.analytics

import com.ella.music.data.PlaybackHistoryEntry
import com.ella.music.data.model.Song
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningCalendarModelsTest {
    @Test
    fun `history row formats actual listened time instead of track duration`() {
        assertEquals("01:35", formatHistoryListenDuration(95_000L))
        assertEquals("--:--", formatHistoryListenDuration(0L))
    }

    @Test
    fun `day total strictly sums actual listened time`() {
        val playedAt = 1_700_000_000_000L
        val dateKey = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(playedAt))
        val librarySong = song(id = 7, title = "Song", artist = "Artist", album = "Album")
        val history = listOf(
            PlaybackHistoryEntry(
                entryId = "first",
                songId = librarySong.id,
                title = librarySong.title,
                artist = librarySong.artist,
                album = librarySong.album,
                playedAt = playedAt,
                durationMs = 180_000L,
                listenedMs = 65_000L
            ),
            PlaybackHistoryEntry(
                entryId = "second",
                songId = librarySong.id,
                title = librarySong.title,
                artist = librarySong.artist,
                album = librarySong.album,
                playedAt = playedAt + 1_000L,
                durationMs = 180_000L,
                listenedMs = 12_000L
            )
        )

        val aggregate = buildListeningDayAggregates(
            history = history,
            dailyListenMs = mapOf(dateKey to 999_000L),
            libraryById = mapOf(librarySong.id to librarySong),
            libraryByStatsKey = mapOf(librarySong.calendarStatsKey() to librarySong)
        ).values.single()

        assertEquals(77_000L, aggregate.totalListenedMs)
        assertEquals("01:17", formatCalendarTotalListenDuration(aggregate.totalListenedMs))
    }

    @Test
    fun `day total does not fall back to track duration`() {
        val librarySong = song(id = 7, title = "Song", artist = "Artist", album = "Album")
        val history = PlaybackHistoryEntry(
            entryId = "missing-listen-time",
            songId = librarySong.id,
            title = librarySong.title,
            artist = librarySong.artist,
            album = librarySong.album,
            playedAt = 1_700_000_000_000L,
            durationMs = 180_000L,
            listenedMs = 0L
        )

        val aggregate = buildListeningDayAggregates(
            history = listOf(history),
            dailyListenMs = emptyMap(),
            libraryById = mapOf(librarySong.id to librarySong),
            libraryByStatsKey = mapOf(librarySong.calendarStatsKey() to librarySong)
        ).values.single()

        assertEquals(0L, aggregate.totalListenedMs)
        assertEquals("00:00", formatCalendarTotalListenDuration(aggregate.totalListenedMs))
    }

    @Test
    fun `stable metadata wins when MediaStore reuses a historical song id`() {
        val reusedIdSong = song(id = 7, title = "Unrelated", artist = "Other", album = "Other")
        val historicalSong = song(id = 19, title = "Expected", artist = "Artist", album = "Album")
        val history = PlaybackHistoryEntry(
            entryId = "history-entry",
            songId = reusedIdSong.id,
            title = historicalSong.title,
            artist = historicalSong.artist,
            album = historicalSong.album,
            playedAt = 1_700_000_000_000L
        )

        val aggregate = buildListeningDayAggregates(
            history = listOf(history),
            dailyListenMs = emptyMap(),
            libraryById = listOf(reusedIdSong, historicalSong).associateBy(Song::id),
            libraryByStatsKey = listOf(reusedIdSong, historicalSong).associateBy(Song::calendarStatsKey)
        ).values.single()

        assertEquals(historicalSong, aggregate.entries.single().song)
    }

    @Test
    fun `song id remains a fallback for legacy metadata`() {
        val librarySong = song(id = 7, title = "Current", artist = "Artist", album = "Album")
        val history = PlaybackHistoryEntry(
            entryId = "legacy-entry",
            songId = librarySong.id,
            title = "Old title",
            artist = librarySong.artist,
            album = librarySong.album,
            playedAt = 1_700_000_000_000L
        )

        val aggregate = buildListeningDayAggregates(
            history = listOf(history),
            dailyListenMs = emptyMap(),
            libraryById = mapOf(librarySong.id to librarySong),
            libraryByStatsKey = mapOf(librarySong.calendarStatsKey() to librarySong)
        ).values.single()

        assertEquals(librarySong, aggregate.entries.single().song)
    }

    private fun song(id: Long, title: String, artist: String, album: String) = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = id + 100,
        duration = 180_000,
        path = "/music/$id.mp3",
        fileName = "$id.mp3"
    )
}
