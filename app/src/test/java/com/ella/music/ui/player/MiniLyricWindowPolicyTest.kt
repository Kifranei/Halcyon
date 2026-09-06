package com.ella.music.ui.player

import com.ella.music.data.SettingsManager
import com.ella.music.data.model.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import androidx.compose.ui.unit.dp

class MiniLyricWindowPolicyTest {
    private fun lines(count: Int, withTranslation: Boolean = false, withPronunciation: Boolean = false) =
        (0 until count).map { index ->
            LyricLine(
                timeMs = index * 1_000L,
                text = "line $index",
                translation = if (withTranslation) "translation $index" else null,
                pronunciation = if (withPronunciation) "pronunciation $index" else null
            )
        }

    @Test
    fun topAlignedTranslationWindowShowsPreviousTranslationAndFourRows() {
        val window = buildMiniLyricWindow(
            lyrics = lines(7, withTranslation = true),
            currentIndex = 3,
            showTranslation = true,
            showPronunciation = false,
            verticalAlignment = SettingsManager.PLAYER_MINI_LYRIC_VERTICAL_ALIGN_TOP
        )

        assertEquals(listOf(2, 3, 4, 5), window.map { it.sourceIndex })
        assertFalse(window[0].presentation.showPrimaryText)
        assertTrue(window[0].presentation.showTranslation)
        assertTrue(window[1].presentation.showPrimaryText)
        assertTrue(window[1].presentation.showTranslation)
        assertFalse(window[3].presentation.showTranslation)
    }

    @Test
    fun centeredPronunciationTranslationWindowUsesThreeAsymmetricRows() {
        val window = buildMiniLyricWindow(
            lyrics = lines(7, withTranslation = true, withPronunciation = true),
            currentIndex = 3,
            showTranslation = true,
            showPronunciation = true,
            verticalAlignment = SettingsManager.PLAYER_MINI_LYRIC_VERTICAL_ALIGN_CENTER
        )

        assertEquals(listOf(2, 3, 4), window.map { it.sourceIndex })
        assertFalse(window[0].presentation.showPronunciation)
        assertTrue(window[0].presentation.showTranslation)
        assertTrue(window[1].presentation.showPronunciation)
        assertTrue(window[1].presentation.showTranslation)
        assertTrue(window[2].presentation.showPronunciation)
        assertFalse(window[2].presentation.showTranslation)
    }

    @Test
    fun originalOnlyUsesFiveRowsWhenCenteredAndTopUsesFiveRowsAhead() {
        val centered = buildMiniLyricWindow(
            lyrics = lines(8),
            currentIndex = 3,
            showTranslation = false,
            showPronunciation = false,
            verticalAlignment = SettingsManager.PLAYER_MINI_LYRIC_VERTICAL_ALIGN_CENTER
        )
        val top = buildMiniLyricWindow(
            lyrics = lines(8),
            currentIndex = 3,
            showTranslation = false,
            showPronunciation = false,
            verticalAlignment = SettingsManager.PLAYER_MINI_LYRIC_VERTICAL_ALIGN_TOP
        )

        assertEquals(listOf(1, 2, 3, 4, 5), centered.map { it.sourceIndex })
        assertEquals(listOf(2, 3, 4, 5, 6), top.map { it.sourceIndex })
        assertTrue(top.all { it.presentation.showPrimaryText })
        assertTrue(top.all { !it.presentation.showTranslation && !it.presentation.showPronunciation })
    }

    @Test
    fun previewHeightMatchesTag127Density() {
        val onePart = LyricLine(timeMs = 0L, text = "line")
        val threeParts = LyricLine(
            timeMs = 0L,
            text = "line",
            translation = "translation",
            pronunciation = "pronunciation"
        )

        assertEquals(
            186.dp,
            miniLyricsPreviewHeight(onePart, showTranslation = false, showPronunciation = false)
        )
        assertEquals(
            220.dp,
            miniLyricsPreviewHeight(threeParts, showTranslation = true, showPronunciation = true)
        )
        assertEquals(
            150.dp,
            miniLyricsPreviewHeight(
                onePart,
                showTranslation = false,
                showPronunciation = false,
                compact = true
            )
        )
        assertEquals(
            168.dp,
            miniLyricsPreviewHeight(
                threeParts,
                showTranslation = true,
                showPronunciation = true,
                compact = true
            )
        )
    }

    @Test
    fun playerBottomClearanceUsesASmallFallbackWhenTheGestureBarInsetIsZero() {
        assertEquals(8.dp, PlayerBottomClearanceFallback)
    }
}
