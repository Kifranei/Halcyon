package com.ella.music.ui.player

import androidx.compose.ui.text.style.TextAlign
import com.ella.music.data.lyrics.LyricsSidecarConverter
import com.ella.music.data.lyrics.LyricsSidecarFormat
import com.ella.music.data.parser.LrcParser
import com.ella.music.plugin.source.PluginLyricsPayloadType
import com.ella.music.plugin.source.PluginLyricsResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TtmlTextAlignmentTest {
    @Test
    fun savingLrcAsExternalTtmlKeepsTheSelectedAlignment() {
        val source = """
            [00:01.00]<00:01.00>Hello <00:01.50>world<00:02.50>
            [00:01.00]你好世界
            [00:03.00]Next line
        """.trimIndent()
        val before = LrcParser.parse(source).lyrics
        val saved = LyricsSidecarConverter.convert(source, LyricsSidecarFormat.TTML)
        val after = LrcParser.parse(saved).lyrics

        assertEquals(before.map { it.text }, after.map { it.text })
        assertEquals(before.map { it.translation }, after.map { it.translation })
        assertTrue(after.all { it.isTtml })
        for (alignment in listOf(TextAlign.Center, TextAlign.Start, TextAlign.End)) {
            assertEquals(
                before.map { it.duetTextAlign(alignment) },
                after.map { it.duetTextAlign(alignment) }
            )
        }
    }

    @Test
    fun existingExternalTtmlWithoutSingersUsesTheSelectedAlignment() {
        // This is also the shape of sidecars written before the parser fix; no re-export needed.
        val lines = LrcParser.parse(ttml("""
            <p begin="1s" end="2s">First line</p>
            <p begin="3s" end="4s" ttm:agent="  ">Second line</p>
        """.trimIndent())).lyrics

        assertEquals(2, lines.size)
        assertEquals(listOf(null, null), lines.map { it.agent })
        assertTrue(lines.all { it.duetTextAlign(TextAlign.Center) == TextAlign.Center })
    }

    @Test
    fun savingMatchedDuetKeepsSidesAndUnassignedLinesDoNotSwitchSingers() {
        val original = ttml("""
            <p begin="1s" end="2s">Unassigned opening</p>
            <p begin="3s" end="4s" ttm:agent="alice">Alice</p>
            <p begin="5s" end="6s">Unassigned line</p>
            <p begin="7s" end="8s" ttm:agent="alice">Still Alice</p>
            <p begin="9s" end="10s" ttm:agent="bob">Bob</p>
            <p begin="11s" end="12s" ttm:agent="all">Together</p>
            <p begin="13s" end="14s" ttm:agent="echo">Echo</p>
            <p begin="15s" end="16s" ttm:agent="bob">Still Bob</p>
            <p begin="17s" end="18s" ttm:agent="alice">Alice again</p>
        """.trimIndent(), """
            <ttm:agent xml:id="alice" type="person"><ttm:name>Alice</ttm:name></ttm:agent>
            <ttm:agent xml:id="bob" type="person"><ttm:name>Bob</ttm:name></ttm:agent>
            <ttm:agent xml:id="all" type="group"/>
            <ttm:agent xml:id="echo" type="other"/>
        """.trimIndent())
        val result = PluginLyricsResult(
            tags = emptyMap(), original = emptyList(), translated = null, romanization = null,
            payloadType = PluginLyricsPayloadType.RAW_TTML, rawTtml = original
        )
        val saved = LyricsSidecarConverter.fromMatchedLyrics(
            LyricsSidecarFormat.TTML, "[00:01.00]Unassigned opening", result
        )
        val lines = LrcParser.parse(saved).lyrics

        assertEquals(original, saved)
        assertEquals(listOf(null, "v1", null, "v1", "v2", "v1", "v2", "v2", "v1"), lines.map { it.agent })
        assertEquals(
            listOf(TextAlign.Center, TextAlign.Start, TextAlign.Center, TextAlign.Start,
                TextAlign.End, TextAlign.Start, TextAlign.End, TextAlign.End, TextAlign.Start),
            lines.map { it.duetTextAlign(TextAlign.Center) }
        )
        assertEquals("Alice", lines[1].agentName)
        assertEquals("Bob", lines[4].agentName)
    }

    private fun ttml(paragraphs: String, agents: String = "") = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata">
          <head><metadata>$agents</metadata></head>
          <body><div>$paragraphs</div></body>
        </tt>
    """.trimIndent().trim()
}
