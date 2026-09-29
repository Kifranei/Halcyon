package com.ella.music.data.netease

import com.ella.music.data.parser.EllaLyricsParser
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NeteaseLyricsTest {
    private val yrc = "[12000,2400](12000,800,0)Hello (12800,1600,0)world"

    @Test fun yrcUsesAbsoluteWordTimesAndPreservesSpaces() {
        val line = EllaLyricsParser.parse(yrc).lyrics.single()
        assertEquals("Hello world", line.text)
        assertEquals(12000L, line.timeMs)
        assertEquals(12800L, line.words[1].startMs)
        assertEquals(14400L, line.words[1].endMs)
    }

    @Test fun responsePrefersYrcAndItsAlignedCompanions() {
        val root = JSONObject().put("yrc", JSONObject().put("lyric", yrc))
            .put("lrc", JSONObject().put("lyric", "[00:10.00]old timing"))
            .put("ytlrc", JSONObject().put("lyric", "[00:12.00]你好世界"))
            .put("yromalrc", JSONObject().put("lyric", "[00:12.00]hello world"))
        val payload = parseNeteaseLyricsResponse(root)
        val line = mergeOnlineLyricCompanions(EllaLyricsParser.parse(payload.original).lyrics,
            payload.translation, payload.pronunciation).single()
        assertEquals(2, line.words.size)
        assertEquals("你好世界", line.translation)
        assertEquals("hello world", line.pronunciation)
    }

    @Test fun missingYrcFallsBackToLrcWithoutDiscardingTranslationOrRomanization() {
        val root = JSONObject().put("yrc", JSONObject().put("lyric", "unavailable"))
            .put("lrc", JSONObject().put("lyric", "[00:12.00]原文"))
            .put("tlyric", JSONObject().put("lyric", "[00:12.02]translation"))
            .put("romalrc", JSONObject().put("lyric", "[00:12.00]romanization"))
        val payload = parseNeteaseLyricsResponse(root)
        val line = mergeOnlineLyricCompanions(EllaLyricsParser.parse(payload.original).lyrics,
            payload.translation, payload.pronunciation).single()
        assertEquals("原文", line.text)
        assertEquals("translation", line.translation)
        assertEquals("romanization", line.pronunciation)
        assertTrue(line.words.isEmpty())
    }

    @Test fun distantCompanionsAreNotAttachedToAnUnrelatedLine() {
        val line = mergeOnlineLyricCompanions(EllaLyricsParser.parse(yrc).lyrics,
            "[00:30.00]other verse", "").single()
        assertNull(line.translation)
    }
}
