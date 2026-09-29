package com.ella.music.data.repository

import com.ella.music.data.metadata.AudioTagInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicLyricsSelectionTest {
    @Test
    fun richerLyricsTagWinsOverUnsyncedLyricsTag() {
        val rich = "[00:01.000]Hello\n[00:01.000]你好"
        val plain = "[00:01.00]Hello"
        val tags = AudioTagInfo(
            lyrics = plain,
            customTags = linkedMapOf(
                "UNSYNCEDLYRICS" to listOf(plain),
                "LYRICS" to listOf(rich)
            )
        )

        assertEquals(rich, tags.embeddedLyricsContent(preferTtml = false))
    }

    @Test
    fun richerLyricsTagAlsoWinsForTtml() {
        val rich = "<tt><body><p begin=\"1s\">Hello</p><p begin=\"1s\">你好</p></body></tt>"
        val plain = "<tt><body><p begin=\"1s\">Hello</p></body></tt>"
        val tags = AudioTagInfo(
            customTags = linkedMapOf(
                "UNSYNCEDLYRICS" to listOf(plain),
                "LYRICS" to listOf(rich)
            )
        )

        assertEquals(rich, tags.embeddedLyricsContent(preferTtml = true))
    }

    @Test
    fun dualLyricsCoexistAndSelectRespectively() {
        val plainLrc = "[00:01.00]Hello Standard Car Player"
        val ttml = "<tt xmlns=\"http://www.w3.org/ns/ttml\"><body><p begin=\"1s\">Hello TTML</p></body></tt>"
        val tags = AudioTagInfo(
            lyrics = plainLrc,
            ttmlLyrics = ttml
        )

        // When requesting TTML, the ttmlLyrics is extracted
        assertEquals(ttml, tags.embeddedLyricsContent(preferTtml = true))
        // When requesting plain/standard LRC, the lyrics is extracted without conflict
        assertEquals(plainLrc, tags.embeddedLyricsContent(preferTtml = false))
    }

    @Test
    fun txxxPrefixMatchesTtmlTag() {
        val plainLrc = "[00:01.00]Hello"
        val ttml = "<tt><body><p begin=\"1s\">Hello</p></body></tt>"
        val tags = AudioTagInfo(
            lyrics = plainLrc,
            customTags = mapOf("TXXX/TTMLLYRIC" to listOf(ttml))
        )

        assertEquals(ttml, tags.embeddedLyricsContent(preferTtml = true))
        assertEquals(plainLrc, tags.embeddedLyricsContent(preferTtml = false))
    }

    @Test
    fun normalizedTagNameStripsTxxxAndItunesPrefixes() {
        assertEquals("TTMLLYRIC", "TXXX/TTMLLYRIC".normalizedTagName())
        assertEquals("TTMLLYRIC", "TXXX:TTMLLYRIC".normalizedTagName())
        assertEquals("TTMLLYRIC", "TXXX TTMLLYRIC".normalizedTagName())
        assertEquals("LYRICS", "----:com.apple.iTunes:Lyrics".normalizedTagName())
    }
}
