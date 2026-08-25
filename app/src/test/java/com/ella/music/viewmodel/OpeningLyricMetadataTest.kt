package com.ella.music.viewmodel

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OpeningLyricMetadataTest {
    private val song = Song(
        id = 1L,
        title = "Song",
        artist = "Artist",
        album = "Album",
        albumId = 2L,
        duration = 180_000L,
        path = "/Music/Folder/song.flac",
        fileName = "song.flac",
        genre = "Rock",
        year = "2026",
        composer = "Composer",
        arranger = "Arranger",
        lyricist = "Lyricist"
    )

    @Test
    fun insertsOneTimedOpeningLineBeforeTheFirstRealLyric() {
        val lyrics = listOf(LyricLine(timeMs = 8_000L, text = "First"))
        val result = lyrics.withOpeningMetadataLine(song, "<艺术家> - <歌曲名>")

        assertEquals("Artist - Song", result.first().text)
        assertEquals(0L, result.first().timeMs)
        assertEquals(8_000L, result.first().endMs)
        assertEquals(8_000L, result.first().words.single().endMs)
        assertEquals(true, result.first().isOpeningMetadata)
        assertEquals(lyrics.first(), result[1])
    }

    @Test
    fun doesNotInsertWhenLyricsAlreadyStartAtZero() {
        val lyrics = listOf(LyricLine(timeMs = 0L, text = "First"))
        assertSame(lyrics, lyrics.withOpeningMetadataLine(song, "<歌曲名>"))
    }

    @Test
    fun supportsEveryDocumentedMetadataToken() {
        assertEquals(
            "Song Artist Album Rock 2026 Folder Composer Lyricist Arranger",
            renderOpeningLyricTemplate(
                "<歌曲名> <艺术家> <专辑> <流派> <年份> <文件夹> <作曲家> <作词家> <编曲家>",
                song
            )
        )
    }
}
