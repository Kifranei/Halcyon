package com.ella.music.plugin.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginJsonParserTest {
    private val parser = PluginJsonParser(pluginJson)
    private val fallbackSong = PluginSongSearchResult(
        id = "song-1",
        pluginId = "plugin",
        pluginName = "Plugin",
        title = "Fallback title",
        artist = "Fallback artist",
        album = "Fallback album",
        date = "2024"
    )

    @Test
    fun api4LyricsCandidatesRequireJudgmentTagsAndKeepUsableResults() {
        val candidates = parser.parseLyricsCandidates(
            rawJson = """
                [
                  {
                    "type": "rawPlainLrc",
                    "tags": {"ti": "Missing date", "ar": "Artist", "al": "Album"},
                    "rawPlainLrc": "[00:00.00]ignored"
                  },
                  {
                    "type": "rawPlainLrc",
                    "tags": {"ti": "Accepted", "ar": "Artist", "al": "Album", "date": "2024"},
                    "rawPlainLrc": "[00:00.00]accepted"
                  }
                ]
            """.trimIndent(),
            pluginId = "plugin",
            pluginName = "Plugin",
            fallbackSong = fallbackSong,
            enforceApi4Contract = true
        )

        assertEquals(1, candidates.size)
        assertEquals("Accepted", candidates.single().song.title)
        assertEquals("song-1:lyrics:1", candidates.single().song.id)
        assertEquals("[00:00.00]accepted", candidates.single().lyrics.rawPlainLrc)
    }

    @Test
    fun api4CoverResultsCanUseCoverUrlAsSyntheticId() {
        val results = parser.parseCoverResults(
            rawJson = """
                [{
                  "title": "Song",
                  "artist": "Artist",
                  "album": "Album",
                  "year": "2024",
                  "picUrl": "https://example.test/cover.jpg"
                }]
            """.trimIndent(),
            pluginId = "plugin",
            pluginName = "Plugin",
            enforceApi4Contract = true
        )

        assertEquals(1, results.size)
        assertTrue(results.single().id.startsWith("https://example.test/cover.jpg"))
        assertEquals("2024", results.single().date)
    }
}
