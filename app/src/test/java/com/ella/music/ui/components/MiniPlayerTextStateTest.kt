package com.ella.music.ui.components

import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniPlayerTextStateTest {
    private val song = Song(
        id = 1L,
        title = "Song",
        artist = "Artist",
        album = "Album",
        albumId = 2L,
        duration = 180_000L,
        path = "/music/song.flac",
        fileName = "song.flac"
    )

    @Test
    fun `original-only lyric keeps song metadata as a fixed secondary row`() {
        val state = rememberMiniPlayerTextState(song, lyricText = "Original lyric", lyricTranslation = null)

        assertEquals("Song - Artist", state.secondary)
        assertFalse(state.scrollSecondary)
    }

    @Test
    fun `translated lyric allows the secondary lyric row to animate and scroll`() {
        val state = rememberMiniPlayerTextState(song, lyricText = "Original lyric", lyricTranslation = "Translation")

        assertEquals("Translation", state.secondary)
        assertTrue(state.scrollSecondary)
    }
}
