package com.ella.music.player

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class OriginOsMediaIslandLyricsTest {
    private val song = Song(1, "Title", "Artist", "Album", 1, 180_000, "/music/song.flac", "song.flac")

    @After fun clear() = OriginOsMediaIslandLyrics.clear()

    @Test fun publishesWholeOriginalLineWithoutWordOrTranslationUpdates() {
        val line = LyricLine(1000, "  第一行\n歌词\u00a0 ", translation = "Translation")
        OriginOsMediaIslandLyrics.update(song, line)
        val first = OriginOsMediaIslandLyrics.current.value!!
        assertEquals(song.playbackStackKey(), first.songKey)
        assertEquals("第一行 歌词", first.text)
        OriginOsMediaIslandLyrics.update(song, line.copy(
            translation = "Another translation", words = listOf(LyricWord("第一行", 1000, 2000))))
        assertSame(first, OriginOsMediaIslandLyrics.current.value)
    }

    @Test fun acceptsInstrumentalTextAndFallsBackToBackgroundVocal() {
        OriginOsMediaIslandLyrics.update(song, LyricLine(0, "纯音乐，请欣赏"))
        assertEquals("纯音乐，请欣赏", OriginOsMediaIslandLyrics.current.value!!.text)
        OriginOsMediaIslandLyrics.update(song, LyricLine(1000, "  ", backgroundText = "伴唱\n歌词"))
        assertEquals("伴唱 歌词", OriginOsMediaIslandLyrics.current.value!!.text)
    }

    @Test fun emptyMissingAndSyntheticMetadataLinesClearTheLyric() {
        for (line in listOf(null, LyricLine(0, " \n\t"), LyricLine(0, "Title - Artist", isOpeningMetadata = true))) {
            OriginOsMediaIslandLyrics.update(song, LyricLine(0, "Previous lyric"))
            OriginOsMediaIslandLyrics.update(song, line)
            assertNull(OriginOsMediaIslandLyrics.current.value)
        }
        OriginOsMediaIslandLyrics.update(null, LyricLine(0, "No song"))
        assertNull(OriginOsMediaIslandLyrics.current.value)
    }

    @Test fun identicalTextOnAnotherSongCarriesTheNewIdentity() {
        OriginOsMediaIslandLyrics.update(song, LyricLine(0, "Same lyric"))
        val first = OriginOsMediaIslandLyrics.current.value!!
        val nextSong = song.copy(id = 2, path = "/music/next.flac")
        OriginOsMediaIslandLyrics.update(nextSong, LyricLine(0, "Same lyric"))
        assertNotEquals(first, OriginOsMediaIslandLyrics.current.value)
        assertEquals(nextSong.playbackStackKey(), OriginOsMediaIslandLyrics.current.value!!.songKey)
    }
}
