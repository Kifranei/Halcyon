package com.ella.music.ui.player

import com.ella.music.data.model.LyricWord
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicLyricSpacingTest {
    @Test
    fun leadingWordSpacesMoveToPreviousWordForFlushWrappedRows() {
        val words = listOf(
            LyricWord("It's", 0L, 400L),
            LyricWord(" been", 400L, 800L),
            LyricWord(" a", 800L, 1_000L),
            LyricWord(" long", 1_000L, 1_500L)
        )

        assertEquals(
            listOf("It's ", "been ", "a ", "long"),
            words.moveLeadingSpacesToPreviousWord().map { it.text }
        )
    }

    @Test
    fun trailingPaddingLetsFinalLyricReachTheFocusOffset() {
        assertEquals(
            376.dp,
            resolveAppleMusicLyricsTrailingPadding(
                viewportHeight = 600.dp,
                focusOffsetRatio = 0.24f,
                trailingLineHeight = 80.dp,
                minimumBottomPadding = 132.dp
            )
        )
    }

    @Test
    fun trailingPaddingKeepsTheMinimumBottomInset() {
        assertEquals(
            132.dp,
            resolveAppleMusicLyricsTrailingPadding(
                viewportHeight = 200.dp,
                focusOffsetRatio = 0.24f,
                trailingLineHeight = 100.dp,
                minimumBottomPadding = 132.dp
            )
        )
    }

    @Test
    fun focusOffsetIsClampedWhenLyricRowIsTallerThanTheViewport() {
        assertEquals(
            0,
            resolveAppleMusicLyricsFocusOffset(
                viewportHeightPx = 240,
                focusOffsetRatio = 0.12f,
                itemHeightPx = 300
            )
        )
    }

    @Test
    fun focusOffsetUsesThePreferredPositionForNormalRows() {
        assertEquals(
            144,
            resolveAppleMusicLyricsFocusOffset(
                viewportHeightPx = 600,
                focusOffsetRatio = 0.24f,
                itemHeightPx = 80
            )
        )
    }

    @Test
    fun longCjkTimedPhraseIsSplitForSequentialWrappedRows() {
        assertTrue(
            LyricWord("星空下拥抱着快凋零的温存", 0L, 4_000L)
                .shouldSplitForAppleMusicCharacters()
        )
        assertFalse(
            LyricWord("星空", 0L, 800L)
                .shouldSplitForAppleMusicCharacters()
        )
    }

    @Test
    fun minorPlaybackRegressionIsIgnoredButSeekJumpIsAccepted() {
        assertTrue(
            shouldIgnoreMinorPlaybackRegression(
                currentUiPositionMs = 2_000L,
                nextPositionMs = 1_700L,
                isPlaying = true
            )
        )
        assertFalse(
            shouldIgnoreMinorPlaybackRegression(
                currentUiPositionMs = 2_000L,
                nextPositionMs = 800L,
                isPlaying = true
            )
        )
    }
}
