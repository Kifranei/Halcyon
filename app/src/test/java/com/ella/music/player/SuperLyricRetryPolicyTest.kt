package com.ella.music.player

import com.ella.music.data.model.LyricLine
import com.ella.music.data.model.LyricWord
import com.ella.music.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SuperLyricRetryPolicyTest {
    @Test
    fun `retry delay grows exponentially and caps at five minutes`() {
        assertEquals(30_000L, superLyricRetryDelayMs(0))
        assertEquals(30_000L, superLyricRetryDelayMs(1))
        assertEquals(60_000L, superLyricRetryDelayMs(2))
        assertEquals(120_000L, superLyricRetryDelayMs(3))
        assertEquals(240_000L, superLyricRetryDelayMs(4))
        assertEquals(300_000L, superLyricRetryDelayMs(5))
        assertEquals(300_000L, superLyricRetryDelayMs(100))
    }

    @Test
    fun `single line payload keeps legacy words and sends actual duration and position`() {
        val payload = SuperLyricBridge().dataForLine(
            song(),
            LyricLine(
                timeMs = 1_000L,
                text = "Hello world",
                words = listOf(LyricWord("Hello", 1_000L, 1_500L), LyricWord("world", 1_500L, 2_000L)),
                endMs = 2_000L,
                translation = "你好世界"
            ),
            positionMs = 1_750L,
            showTranslation = true
        )

        assertEquals(180_000L, payload.duration)
        assertEquals(1_750L, payload.position)
        assertEquals("Hello world", payload.currentLyric?.text)
        assertEquals("Hello ", payload.currentLyric?.words?.get(0)?.word)
        assertEquals("你好世界", payload.translation?.text)
        assertFalse(payload.hasAllLyrics())
    }

    @Test
    fun `translation toggle stays effective in new api payload`() {
        val payload = SuperLyricBridge().dataForLine(
            song(),
            LyricLine(timeMs = 1_000L, text = "Hello", translation = "你好"),
            positionMs = 1_500L,
            showTranslation = false
        )

        assertFalse(payload.hasTranslation())
        assertEquals("Hello", payload.currentLyric?.text)
    }

    @Test
    fun `pronunciation extras are retained and negative positions are clamped`() {
        val bridge = SuperLyricBridge().apply {
            setSecondaryMode(SuperLyricBridge.SecondaryMode.Pronunciation)
        }
        val payload = bridge.dataForLine(
            null,
            LyricLine(timeMs = 1_000L, text = "ふたり", pronunciation = "futari"),
            positionMs = -1L,
            showTranslation = false
        )

        assertEquals(0L, payload.position)
        assertEquals(0L, payload.duration)
        assertEquals("futari", payload.extra?.getString("pronunciation"))
        assertEquals("futari", payload.extra?.getString("phonetic"))
    }

    private fun song() = Song(
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
