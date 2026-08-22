package com.ella.music.ui.player

import com.ella.music.data.model.LyricWord
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicLyricSpacingTest {
    @Test
    fun disablingWordLiftZeroesLiftButDoesNotDisableKaraokeProgress() {
        assertEquals(
            0f,
            appleMusicKaraokeLiftPx(wordLiftEnabled = false, textSizePx = 48f, progress = 1f)
        )
        assertTrue(
            appleMusicKaraokeLiftPx(wordLiftEnabled = true, textSizePx = 48f, progress = 1f) > 0f
        )
    }

    @Test
    fun kanaPronunciationSitsOnTheFirstKanjiWordNotTheLatinPrefix() {
        val words = listOf(
            LyricWord("OK! ", 0L, 400L),
            LyricWord("風が ", 400L, 800L),
            LyricWord("変わっても", 800L, 1_400L)
        )
        assertEquals(
            listOf("", "かぜ", "か"),
            rubiesForTimedWords(words, emptyList(), "かぜがかわっても")
        )
        assertTrue(isInlineRubyPronunciation("かぜが"))
        assertFalse(isInlineRubyPronunciation("ka ku se i READY OK"))
    }

    @Test
    fun kanaRubyMapsOntoMatchingKanjiNotTheFollowingKana() {
        val words = listOf(
            LyricWord("風", 0L, 300L),
            LyricWord("が", 300L, 400L),
            LyricWord("変", 400L, 700L),
            LyricWord("わっても", 700L, 1_400L)
        )
        assertEquals(
            listOf("かぜ", "", "か", ""),
            attachRubyByCorrespondence(words, "かぜがかわっても")
        )
    }

    @Test
    fun consecutiveKanjiSplitReadingsInsteadOfPilingThem() {
        val words = listOf(
            LyricWord("見", 0L, 100L),
            LyricWord("守", 100L, 500L),
            LyricWord("ってくれてるよ", 500L, 1_000L)
        )
        assertEquals(
            listOf("み", "まも", ""),
            attachRubyByCorrespondence(words, "みまも")
        )
        assertEquals(
            listOf("み", "まも", ""),
            attachRubyByCorrespondence(words, "みまもってくれてるよ")
        )
    }

    @Test
    fun appleMusicTimedFuriganaStaysOnEachKanjiSpan() {
        val words = listOf(
            LyricWord("OK! ", 17_601L, 18_114L),
            LyricWord("風", 18_718L, 19_254L),
            LyricWord("が ", 19_254L, 19_459L),
            LyricWord("変", 19_459L, 19_775L),
            LyricWord("わっ", 19_775L, 20_179L),
            LyricWord("て", 20_179L, 20_506L),
            LyricWord("も", 20_506L, 20_913L)
        )
        val ruby = listOf(
            LyricWord("かぜ", 18_718L, 19_254L),
            LyricWord("か", 19_459L, 19_775L)
        )
        assertEquals(
            listOf("", "かぜ", "", "か", "", "", ""),
            rubiesForTimedWords(words, ruby, "かぜか")
        )
    }

    @Test
    fun appleMusicTimedFuriganaDoesNotSwallowTheSecondKanji() {
        val words = listOf(
            LyricWord("みん", 59_292L, 59_493L),
            LyricWord("な", 59_493L, 59_595L),
            LyricWord("を ", 59_595L, 59_806L),
            LyricWord("見", 59_806L, 59_907L),
            LyricWord("守", 59_907L, 60_444L),
            LyricWord("っ", 60_444L, 60_457L),
            LyricWord("てくれてるよ", 60_457L, 63_362L)
        )
        val ruby = listOf(
            LyricWord("み", 59_806L, 59_907L),
            LyricWord("まも", 59_907L, 60_444L)
        )
        assertEquals(
            listOf("", "", "", "み", "まも", "", ""),
            rubiesForTimedWords(words, ruby, "みまも")
        )
    }

    @Test
    fun timedPronunciationWordsStayOnMatchingMainWords() {
        val words = listOf(
            LyricWord("風", 0L, 400L),
            LyricWord("が", 400L, 600L),
            LyricWord("変わって", 600L, 1_000L)
        )
        val ruby = listOf(
            LyricWord("かぜ", 0L, 400L),
            LyricWord("かわ", 600L, 1_000L)
        )
        assertEquals(
            listOf("かぜ", "", "かわ"),
            rubiesForTimedWords(words, ruby, "かぜかわ")
        )
    }

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
    fun initialScrollTargetStartsAtTheCurrentLyricInsteadOfTheFirstRow() {
        assertEquals(
            18,
            resolveAppleMusicLyricsScrollTargetIndex(
                activeLyricIndex = 18,
                activeInterlude = null,
                interludes = emptyList()
            )
        )
    }

    @Test
    fun scrollTargetAccountsForInsertedInterludeRows() {
        val interludes = listOf(
            AppleMusicInterlude(startMs = 0L, endMs = 8_000L, nextLineIndex = 0),
            AppleMusicInterlude(startMs = 40_000L, endMs = 50_000L, nextLineIndex = 5)
        )

        assertEquals(
            7,
            resolveAppleMusicLyricsScrollTargetIndex(
                activeLyricIndex = 5,
                activeInterlude = null,
                interludes = interludes
            )
        )
        assertEquals(
            6,
            resolveAppleMusicLyricsScrollTargetIndex(
                activeLyricIndex = 4,
                activeInterlude = interludes[1],
                interludes = interludes
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
