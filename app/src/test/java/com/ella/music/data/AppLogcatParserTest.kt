package com.ella.music.data

import com.ella.music.data.lx.looksLikeHtmlDocument
import com.ella.music.data.lx.looksLikeJsonDocument
import com.ella.music.data.lx.pickLxScriptUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppLogcatParserTest {
    @Test
    fun parsesThreadTimeAndContinuationLines() {
        val dump = """
            08-29 18:15:38.326 12899 12954 D EllaPlaybackTiming: artwork load start
            java.io.IOException: boom
            	at com.ella.music.Foo.bar(Foo.kt:1)
            08-29 18:15:38.400 12899 12954 E PlayerError: Playback failed
        """.trimIndent()

        val entries = AppLogcatParser.parseDump(dump, "12899")
        assertEquals(2, entries.size)
        assertEquals("DEBUG", entries[0].level)
        assertEquals("EllaPlaybackTiming", entries[0].tag)
        assertEquals("artwork load start", entries[0].message)
        assertTrue(entries[0].detail.orEmpty().contains("IOException"))
        assertEquals("ERROR", entries[1].level)
        assertEquals("PlayerError", entries[1].tag)
    }

    @Test
    fun htmlSourceIsRejectedByHeuristic() {
        assertTrue(looksLikeHtmlDocument("<!DOCTYPE html><html><body>hi</body></html>"))
        assertTrue(looksLikeHtmlDocument("<html lang=\"zh\"><head></head></html>"))
        assertTrue(!looksLikeHtmlDocument("const sources = { kw: true }"))
    }

    @Test
    fun htmlCatalogPrefersLxScriptOverBootstrap() {
        val html = """
            <html><script src="https://lib.baomitu.com/twitter-bootstrap/4.6.1/js/bootstrap.min.js"></script>
            https://raw.githubusercontent.com/pdone/lx-music-source/main/sixyin/latest.js
            https://raw.githubusercontent.com/pdone/lx-music-source/main/juhe/latest.js
            </html>
        """.trimIndent()
        val picked = pickLxScriptUrl(html, "https://awaw.cc/post/lx-music-source")
        assertTrue(picked.orEmpty().contains("lx-music-source"))
        assertTrue(picked.orEmpty().endsWith("latest.js"))
    }

    @Test
    fun qingMusicJsonLooksLikeJsonNotHtml() {
        val json = """{"lines":[{"id":"kw","searchApi":"fetchSearchMusic","detailApi":"fetchMusicDetail"}]}"""
        assertTrue(looksLikeJsonDocument(json))
        assertTrue(!looksLikeHtmlDocument(json))
    }
}
