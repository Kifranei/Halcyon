package com.ella.music.ui.components

import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import org.junit.Assert.assertTrue
import org.junit.Test

class CueTextDecodingTest {
    @Test
    fun utf8CueKeepsChineseAndJapaneseTitles() {
        val cue = "TITLE \"中文アルバム\"\nTRACK 01 AUDIO\nTITLE \"第一首\"\nINDEX 01 00:00:00"

        val decoded = decodeCueText(cue.toByteArray(StandardCharsets.UTF_8))

        assertTrue(decoded.contains("中文アルバム"))
        assertTrue(decoded.contains("第一首"))
    }

    @Test
    fun gb18030CueDecodesChineseTitles() {
        val cue = "TITLE \"测试专辑\"\nTRACK 01 AUDIO\nTITLE \"歌曲标题\"\nINDEX 01 00:00:00"

        val decoded = decodeCueText(cue.toByteArray(Charset.forName("GB18030")))

        assertTrue(decoded.contains("测试专辑"))
        assertTrue(decoded.contains("歌曲标题"))
    }

    @Test
    fun shiftJisCueDecodesJapaneseTitles() {
        val cue = "TITLE \"テストアルバム\"\nTRACK 01 AUDIO\nTITLE \"日本語の曲\"\nINDEX 01 00:00:00"

        val decoded = decodeCueText(cue.toByteArray(Charset.forName("Shift_JIS")))

        assertTrue(decoded.contains("テストアルバム"))
        assertTrue(decoded.contains("日本語の曲"))
    }
}
