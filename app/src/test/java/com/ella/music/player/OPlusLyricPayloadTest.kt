package com.ella.music.player

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
import com.ella.music.data.SettingsManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OPlusLyricPayloadTest {
    @Test
    fun systemPayloadOnlyContainsPlainOriginalLyric() {
        val song = song()
        val payload = OPlusLyricPayload.build(
            song = song,
            mode = SettingsManager.OPLUS_LYRIC_MODE_SYSTEM,
            lyrics = listOf(
                LyricLine(
                    timeMs = 1_000L,
                    text = "Hello world",
                    words = listOf(
                        LyricWord("Hello", 1_000L, 1_500L),
                        LyricWord("world", 1_500L, 2_200L)
                    ),
                    translation = "你好世界",
                    endMs = 2_200L
                )
            )
        )

        val json = payload ?: error("payload is null")
        assertEquals("[00:01.00]Hello world", OPlusLyricPayload.stringField(json, "lyric"))
        assertEquals(null, OPlusLyricPayload.stringField(json, "rawLyric"))
        assertEquals(null, OPlusLyricPayload.stringField(json, "translationLyric"))
        assertTrue(OPlusLyricPayload.matchesSong(json, song))
        assertTrue(OPlusLyricPayload.matchesMode(json, SettingsManager.OPLUS_LYRIC_MODE_SYSTEM))
    }

    @Test
    fun modulePayloadSplitsNativeLyricWordTimedRawLyricAndTranslation() {
        val song = song()
        val payload = OPlusLyricPayload.build(
            song = song,
            mode = SettingsManager.OPLUS_LYRIC_MODE_MODULE,
            lyrics = listOf(
                LyricLine(
                    timeMs = 1_000L,
                    text = "Hello world",
                    words = listOf(
                        LyricWord("Hello", 1_000L, 1_500L),
                        LyricWord("world", 1_500L, 2_200L)
                    ),
                    translation = "你好世界",
                    endMs = 2_200L
                )
            )
        )

        val json = payload ?: error("payload is null")
        assertEquals("[00:01.00]Hello world", OPlusLyricPayload.stringField(json, "lyric"))
        assertEquals(
            "[00:01.000]Hello [00:01.500]world[00:02.200]\n[00:01.000]你好世界",
            OPlusLyricPayload.stringField(json, "rawLyric")
        )
        assertEquals("[00:01.000]你好世界", OPlusLyricPayload.stringField(json, "translationLyric"))
        assertTrue(OPlusLyricPayload.matchesSong(json, song))
        assertTrue(OPlusLyricPayload.matchesMode(json, SettingsManager.OPLUS_LYRIC_MODE_MODULE))
    }

    @Test
    fun moduleRawLyricFallsBackToPlainTimedLineWhenWordsAreMissing() {
        val payload = OPlusLyricPayload.build(
            song = song(),
            mode = SettingsManager.OPLUS_LYRIC_MODE_MODULE,
            lyrics = listOf(LyricLine(timeMs = 2_345L, text = "Plain line"))
        )

        val json = payload ?: error("payload is null")
        assertEquals("[00:02.34]Plain line", OPlusLyricPayload.stringField(json, "lyric"))
        assertEquals("[00:02.345]Plain line", OPlusLyricPayload.stringField(json, "rawLyric"))
        assertEquals(null, OPlusLyricPayload.stringField(json, "translationLyric"))
    }

    @Test
    fun modulePayloadOmitsPreambleCreditsWithoutMovingFirstSungLine() {
        val song = song().copy(title = "dorothea", artist = "Taylor Swift")
        val payload = OPlusLyricPayload.build(
            song = song,
            mode = SettingsManager.OPLUS_LYRIC_MODE_MODULE,
            lyrics = listOf(
                LyricLine(
                    timeMs = 0L,
                    text = "dorothea - Taylor Swift (泰勒·斯威夫特)",
                    words = listOf(LyricWord("dorothea - Taylor Swift (泰勒·斯威夫特)", 0L, 2_120L)),
                    translation = "TME享有本翻译作品的著作权",
                    endMs = 2_120L
                ),
                LyricLine(
                    timeMs = 2_120L,
                    text = "Lyrics by: Aaron Dessner/Taylor Swift",
                    words = listOf(LyricWord("Lyrics by: Aaron Dessner/Taylor Swift", 2_120L, 4_250L)),
                    endMs = 4_250L
                ),
                LyricLine(
                    timeMs = 4_250L,
                    text = "Composed by: Aaron Dessner/Taylor Swift",
                    words = listOf(LyricWord("Composed by: Aaron Dessner/Taylor Swift", 4_250L, 6_380L)),
                    endMs = 6_380L
                ),
                LyricLine(
                    timeMs = 6_380L,
                    text = "Produced by: Aaron Dessner",
                    words = listOf(LyricWord("Produced by: Aaron Dessner", 6_380L, 8_500L)),
                    endMs = 8_500L
                ),
                LyricLine(
                    timeMs = 8_509L,
                    text = "Hey Dorothea do you ever stop and think about me",
                    words = listOf(
                        LyricWord("Hey ", 8_509L, 8_756L),
                        LyricWord("Dorothea ", 8_756L, 9_790L),
                        LyricWord("do you ever stop and think about me", 9_790L, 13_335L)
                    ),
                    translation = "嘿 多萝西娅 你是否停下过脚步 思念起我",
                    endMs = 13_335L
                )
            )
        )

        val json = payload ?: error("payload is null")
        assertEquals(
            "[00:08.50]Hey Dorothea do you ever stop and think about me",
            OPlusLyricPayload.stringField(json, "lyric")
        )
        val rawLyric = OPlusLyricPayload.stringField(json, "rawLyric")!!
        assertTrue(rawLyric.startsWith("[00:08.509]Hey "))
        assertTrue(rawLyric.contains("[00:08.509]嘿 多萝西娅 你是否停下过脚步 思念起我"))
        assertEquals(
            "[00:08.509]嘿 多萝西娅 你是否停下过脚步 思念起我",
            OPlusLyricPayload.stringField(json, "translationLyric")
        )
        assertFalse(json.contains("Produced by"))
        assertFalse(json.contains("著作权"))
    }

    @Test
    fun modulePayloadOmitsStandaloneEnglishCreditLabels() {
        val payload = OPlusLyricPayload.build(
            song = song(),
            mode = SettingsManager.OPLUS_LYRIC_MODE_MODULE,
            lyrics = listOf(
                LyricLine(timeMs = 1_000L, text = "Lyricist: Taylor Swift"),
                LyricLine(timeMs = 2_000L, text = "Arranger: Aaron Dessner"),
                LyricLine(timeMs = 3_000L, text = "Performer: Taylor Swift"),
                LyricLine(timeMs = 4_000L, text = "Actual lyric")
            )
        )

        val json = payload ?: error("payload is null")
        assertEquals("[00:04.00]Actual lyric", OPlusLyricPayload.stringField(json, "lyric"))
        assertFalse(json.contains("Lyricist"))
        assertFalse(json.contains("Arranger"))
        assertFalse(json.contains("Performer"))
    }

    private fun song(): Song = Song(
        id = 42L,
        title = "Test Song",
        artist = "Test Artist",
        album = "Test Album",
        albumId = 7L,
        duration = 180_000L,
        path = "/music/test.flac",
        fileName = "test.flac"
    )
}
