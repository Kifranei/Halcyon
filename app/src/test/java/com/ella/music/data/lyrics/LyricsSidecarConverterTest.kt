package com.ella.music.data.lyrics

import com.ella.music.data.parser.LrcParser
import com.ella.music.plugin.source.PluginLyricsLine
import com.ella.music.plugin.source.PluginLyricsPayloadType
import com.ella.music.plugin.source.PluginLyricsRenderFormat
import com.ella.music.plugin.source.PluginLyricsRenderOptions
import com.ella.music.plugin.source.PluginLyricsResult
import com.ella.music.plugin.source.PluginLyricsWord
import com.ella.music.plugin.source.toEmbeddedLyricsText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsSidecarConverterTest {

    private val enhancedLrcWithTranslation = """
        [00:01.00]<00:01.00>Hello <00:01.50>to the <00:02.00>world<00:02.50>
        [00:01.00]你好世界
        [00:03.00]Walk with me
        [00:03.00]和我一起走
        [00:06.00]Last line here
    """.trimIndent()

    private val wordTtml = """
        <tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body><div>
        <p begin="00:00:01.000" end="00:00:02.500"><span begin="00:00:01.000" end="00:00:01.500">Hello </span><span begin="00:00:01.500" end="00:00:02.500">world</span><span ttm:role="x-translation">你好世界</span></p>
        <p begin="00:00:03.000" end="00:00:05.000">Walk with me<span ttm:role="x-translation">和我一起走</span></p>
        </div></body></tt>
    """.trimIndent()

    @Test
    fun lrcConvertsToTtmlThatTheAppParsesBack() {
        val ttml = LyricsSidecarConverter.convert(enhancedLrcWithTranslation, LyricsSidecarFormat.TTML)

        assertTrue(LyricsSidecarConverter.looksLikeTtml(ttml))
        val lines = LrcParser.parse(ttml).lyrics
        assertEquals(3, lines.size)
        assertTrue(lines.all { it.isTtml })

        val first = lines[0]
        assertEquals(1_000L, first.timeMs)
        assertEquals("Hello to the world", first.text)
        assertEquals("你好世界", first.translation)
        assertTrue(first.words.size >= 3)
        assertTrue(first.words.any { it.startMs == 1_500L })

        val second = lines[1]
        assertEquals(3_000L, second.timeMs)
        assertEquals("Walk with me", second.text)
        assertEquals("和我一起走", second.translation)
        // Line-timed LRC lines end where the next line starts.
        assertEquals(6_000L, second.endMs)

        assertEquals("Last line here", lines[2].text)
        assertNull(lines[2].translation)
    }

    @Test
    fun ttmlConvertsToEnhancedLrcWithTranslationLines() {
        val lrc = LyricsSidecarConverter.convert(wordTtml, LyricsSidecarFormat.LRC)

        assertFalse(LyricsSidecarConverter.looksLikeTtml(lrc))
        assertTrue(lrc.contains("<00:01.50>"))
        assertTrue(lrc.lines().contains("[00:01.00]你好世界"))
        assertTrue(lrc.lines().contains("[00:03.00]和我一起走"))

        val lines = LrcParser.parse(lrc).lyrics
        assertEquals(2, lines.size)
        assertFalse(lines.any { it.isTtml })
        assertEquals("Hello world", lines[0].text)
        assertEquals("你好世界", lines[0].translation)
        assertTrue(lines[0].words.size >= 2)
        assertTrue(lines[0].words.any { it.startMs == 1_500L })
        assertEquals(3_000L, lines[1].timeMs)
        assertEquals("Walk with me", lines[1].text)
        assertEquals("和我一起走", lines[1].translation)
    }

    @Test
    fun lineTimedLyricsBecomePlainLrc() {
        val lrc = LyricsSidecarConverter.linesToLrc(
            LrcParser.parse("[00:03.00]Walk with me\n[00:03.00]和我一起走").lyrics
        )

        assertEquals("[00:03.00]Walk with me\n[00:03.00]和我一起走", lrc)
    }

    @Test
    fun sameKindInputIsWrittenVerbatim() {
        assertEquals(
            enhancedLrcWithTranslation,
            LyricsSidecarConverter.convert("\n$enhancedLrcWithTranslation\n", LyricsSidecarFormat.LRC)
        )
        assertEquals(wordTtml, LyricsSidecarConverter.convert(wordTtml, LyricsSidecarFormat.TTML))
        assertEquals("", LyricsSidecarConverter.convert("  ", LyricsSidecarFormat.TTML))
    }

    @Test
    fun matchedStructuredResultKeepsWordTimingWhenSavedAsTtmlFromPlainPreview() {
        val result = PluginLyricsResult(
            tags = emptyMap(),
            original = listOf(
                PluginLyricsLine(
                    start = 1_000L,
                    end = 2_500L,
                    words = listOf(
                        PluginLyricsWord(1_000L, 1_500L, "Hello "),
                        PluginLyricsWord(1_500L, 2_500L, "world")
                    )
                )
            ),
            translated = listOf(
                PluginLyricsLine(1_000L, 2_500L, listOf(PluginLyricsWord(1_000L, 2_500L, "你好世界")))
            ),
            romanization = null
        )
        val options = PluginLyricsRenderOptions(format = PluginLyricsRenderFormat.PLAIN_LRC)
        val preview = result.toEmbeddedLyricsText(options)

        val ttml = LyricsSidecarConverter.fromMatchedLyrics(
            target = LyricsSidecarFormat.TTML,
            previewText = preview,
            result = result,
            options = options
        )

        val line = LrcParser.parse(ttml).lyrics.single()
        assertTrue(line.isTtml)
        assertEquals("Hello world", line.text)
        assertTrue(line.words.size >= 2)
        assertEquals("你好世界", line.translation)

        // Same kind as the preview: the previewed LRC is saved unchanged.
        assertEquals(
            preview,
            LyricsSidecarConverter.fromMatchedLyrics(LyricsSidecarFormat.LRC, preview, result, options)
        )
    }

    @Test
    fun rawTtmlSourceIsWrittenAsIsAndCanBeSavedAsEnhancedLrc() {
        val result = PluginLyricsResult(
            tags = emptyMap(),
            original = emptyList(),
            translated = null,
            romanization = null,
            payloadType = PluginLyricsPayloadType.RAW_TTML,
            rawTtml = wordTtml
        )
        val lrcPreviewOptions = PluginLyricsRenderOptions(format = PluginLyricsRenderFormat.PLAIN_LRC)
        val lrcPreview = result.toEmbeddedLyricsText(lrcPreviewOptions)
        assertEquals(
            wordTtml,
            LyricsSidecarConverter.fromMatchedLyrics(LyricsSidecarFormat.TTML, lrcPreview, result, lrcPreviewOptions)
        )

        val ttmlOptions = PluginLyricsRenderOptions(format = PluginLyricsRenderFormat.TTML)
        val ttmlPreview = result.toEmbeddedLyricsText(ttmlOptions)
        val lrc = LyricsSidecarConverter.fromMatchedLyrics(LyricsSidecarFormat.LRC, ttmlPreview, result, ttmlOptions)
        val lines = LrcParser.parse(lrc).lyrics
        assertFalse(LyricsSidecarConverter.looksLikeTtml(lrc))
        assertTrue(lines[0].words.size >= 2)
        assertEquals("你好世界", lines[0].translation)
    }

    @Test
    fun editedPreviewIsConvertedInsteadOfReRenderingTheResult() {
        val result = PluginLyricsResult(
            tags = emptyMap(),
            original = emptyList(),
            translated = null,
            romanization = null,
            payloadType = PluginLyricsPayloadType.RAW_TTML,
            rawTtml = wordTtml
        )
        val edited = "[00:03.00]Walk with me\n[00:03.00]和我一起走"

        val ttml = LyricsSidecarConverter.fromMatchedLyrics(
            target = LyricsSidecarFormat.TTML,
            previewText = edited,
            result = result,
            previewEdited = true
        )

        val line = LrcParser.parse(ttml).lyrics.single()
        assertEquals("Walk with me", line.text)
        assertEquals("和我一起走", line.translation)
    }

    @Test
    fun sidecarPathHelpers() {
        assertEquals("Song.Name.lrc", LyricsSidecarPaths.sidecarFileName("Song.Name.flac", LyricsSidecarFormat.LRC))
        assertEquals("track.ttml", LyricsSidecarPaths.sidecarFileName("track", LyricsSidecarFormat.TTML))
        assertEquals("primary:Music/A", LyricsSidecarPaths.storagePathToDocumentId("/storage/emulated/0/Music/A"))
        assertEquals("primary:Music", LyricsSidecarPaths.storagePathToDocumentId("/sdcard/Music/"))
        assertEquals("1234-ABCD:Music", LyricsSidecarPaths.storagePathToDocumentId("/storage/1234-ABCD/Music"))
        assertNull(LyricsSidecarPaths.storagePathToDocumentId("/data/user/0/com.ella.music/files/a"))
        assertEquals("1234-ABCD:Music", LyricsSidecarPaths.parentDocumentId("1234-ABCD:Music/a.flac"))
        assertEquals("1234-ABCD:", LyricsSidecarPaths.parentDocumentId("1234-ABCD:a.flac"))
        assertEquals("primary:a.lrc", LyricsSidecarPaths.childDocumentId("primary:", "a.lrc"))
        assertEquals("primary:Music/a.lrc", LyricsSidecarPaths.childDocumentId("primary:Music", "a.lrc"))
        assertTrue(LyricsSidecarPaths.treeCovers("primary:Music", "primary:Music/A"))
        assertTrue(LyricsSidecarPaths.treeCovers("primary:", "primary:Music"))
        assertFalse(LyricsSidecarPaths.treeCovers("primary:Music", "primary:Musical"))
    }
}
